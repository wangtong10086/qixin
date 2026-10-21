package io.github.wangtong10086.qixin.service

import android.content.*
import android.os.*
import io.github.magisk317.mipush.manager.api.*
import io.github.wangtong10086.qixin.core.MiPushRecord
import org.json.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.*

/** Local Binder transport shared by root CLI and the authenticated GUI socket. */
class RuntimeClient(private val context: Context) {
    private var connection: RootRuntimeConnection? = null
    private var lastImportedEventId = 0L
    private var remote: IManagerRuntimeService? = null

    @Synchronized private fun service(): IManagerRuntimeService {
        remote?.let { if (it.asBinder().isBinderAlive) return it }
        disconnect()
        val link = RootRuntimeConnection()
        val binder = link.acquire()
        connection = link
        return IManagerRuntimeService.Stub.asInterface(binder).also { remote = it }
    }

    @Synchronized private fun disconnect() {
        connection?.release()
        connection = null
        remote = null
    }

    fun status(): JSONObject {
        try {
            val remote = service()
            val hello = remote.handshake(ManagerProtocol.MAJOR, ManagerProtocol.MINOR)
            check(hello.compatibilityReason == null) { "运行时协议不兼容：${hello.compatibilityReason}" }
            val state = remote.connectionSnapshot
            return JSONObject().put("connected", true).put("version", hello.runtimeVersionName)
                .put("connectionState", state.connectionState.lowercase(java.util.Locale.ROOT)).put("registeredApps", state.registeredPackageCount)
                .put("received", state.downstreamMessageCount).put("delivered", state.deliveredToAppCount)
                .put("duplicates", state.duplicateMessageCount).put("moduleHooked", state.moduleHooked)
                .put("identityHooked", state.identityHooked).put("lastReadAt", state.lastReadAliveAtMs)
                .put("lastConnectedAt", state.connectedAtMs)
        } catch (error: IOException) {
            return JSONObject().put("connected", false).put("error", error.message)
        } catch (error: RemoteException) {
            disconnect()
            return JSONObject().put("connected", false).put("error", error.javaClass.simpleName)
        }
    }

    fun application(name: String): JSONObject {
        val app = service().getApplicationDetail(name, true)
            ?: throw IllegalArgumentException("运行时未找到此应用")
        return JSONObject().put("package", app.packageName).put("type", app.type)
            .put("blocked", app.blocked).put("clickFallback", app.clickFallbackEnabled)
            .put("notificationOnRegister", app.notificationOnRegister)
            .put("registeredType", app.registeredType)
    }

    @Synchronized fun updateApplication(request: JSONObject): JSONObject {
        val name = request.getString("package")
        val before = application(name)
        check(listOf("type", "blocked", "clickFallback", "notificationOnRegister").all {
            before.get(it) == request.getJSONObject("expected").get(it)
        }) { "应用配置已更新，请重新加载" }
        val next = request.getJSONObject("settings")
        val type = next.getInt("type")
        require(type in listOf(0, 2, 3)) { "无效注册策略" }
        val argument = listOf(type, next.getBoolean("blocked"), false, false,
            next.getBoolean("notificationOnRegister"), next.getBoolean("clickFallback")).joinToString(",")
        write(ManagerProtocol.WRITE_OP_UPDATE_APPLICATION, name, argument)
        return application(name)
    }

    fun reconnect(): JSONObject = write(ManagerProtocol.WRITE_OP_XMPP_RECONNECT)

    fun replay(name: String, eventId: Long): JSONObject {
        val result = service().executeWrite(ManagerWriteRequestDto(requestId = UUID.randomUUID().toString(),
            operation = ManagerProtocol.WRITE_OP_MOCK_MESSAGE, packageName = name, userId = 0, eventId = eventId))
        check(result.status == ManagerProtocol.WRITE_STATUS_SUCCESS) { result.details }
        return JSONObject().put("localReplay", true).put("details", result.details)
    }

    fun exportLogs(): JSONObject {
        val report = service().exportRuntimeLogs()
        check(report.success) { report.details }
        val fd = requireNotNull(report.parcelFileDescriptor) { "运行时没有返回日志文件" }
        val output = java.nio.file.Path.of("/data/adb/qixin/exports")
        java.nio.file.Files.createDirectories(output)
        val target = output.resolve("runtime-${System.currentTimeMillis()}.zip")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            java.nio.file.Files.copy(input, target)
        }
        return JSONObject().put("path", target.toString()).put("details", report.details)
    }

    fun channels(name: String): JSONObject {
        val items = JSONArray()
        var page: String? = null
        do {
            val result = service().getNotificationChannelPage(ManagerNotificationChannelQueryDto(
                packageName = name, userId = 0, pageToken = page))
            result.items.forEach { items.put(JSONObject().put("id", it.id).put("name", it.name)
                .put("importance", it.importance).put("enabled", it.enabled)) }
            page = result.nextPageToken
        } while (page != null)
        return JSONObject().put("channels", items)
    }

    fun importHistory(history: History, config: JSONObject) {
        val remote = service()
        var page: String? = null
        val now = System.currentTimeMillis()
        do {
            val result = remote.getApplicationPage(ManagerApplicationQueryDto(userId = 0, includeSystemApps = true, pageToken = page))
            result.items.filter { it.registeredType != 0 }.forEach { app ->
                history.registration(JSONObject().put("package", app.packageName).put("channel", "mipush")
                    .put("state", MiPushRecord.registered(app.registeredType)).put("registeredType", app.registeredType)
                    .put("blocked", app.blocked).put("source", "xmsf-runtime").put("observedAt", now)
                    .put("businessUpload", "unknown"))
            }
            page = result.nextPageToken
        } while (page != null)
        var lastId: Long? = null
        var newest = lastImportedEventId
        while (true) {
            val events = remote.getEventPage(ManagerEventQueryDto(userId = 0, pageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE, lastId = lastId)).items
            if (events.isEmpty()) break
            for (event in events) {
                if (event.id <= lastImportedEventId) continue
                if (event.receiveDateMs >= now - 7L * 86400000) history.append(JSONObject()
                    .put("package", event.packageName).put("channel", "mipush").put("stage", MiPushRecord.stage(event.type))
                    .put("source", "xmsf-cache").put("sourceId", event.id).put("time", event.receiveDateMs)
                    .put("result", MiPushRecord.result(event.result)), config)
                newest = maxOf(newest, event.id)
            }
            val oldest = events.last()
            if (oldest.id <= lastImportedEventId || oldest.receiveDateMs < now - 7L * 86400000) break
            lastId = oldest.id
        }
        lastImportedEventId = newest
    }

    private fun write(operation: String, name: String = "", argument: String = ""): JSONObject {
        val result = service().executeWrite(ManagerWriteRequestDto(requestId = UUID.randomUUID().toString(),
            operation = operation, packageName = name, userId = 0, argument = argument))
        check(result.status == ManagerProtocol.WRITE_STATUS_SUCCESS) { "运行时拒绝操作：${result.details}" }
        return JSONObject().put("applied", true).put("details", result.details)
    }
}
