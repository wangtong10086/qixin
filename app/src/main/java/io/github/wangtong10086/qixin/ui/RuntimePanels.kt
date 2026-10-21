package io.github.wangtong10086.qixin.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wangtong10086.qixin.service.Wire
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException

private suspend fun runtimeRpc(command: String, packageName: String = ""): JSONObject = withContext(Dispatchers.IO) {
    Wire.call(JSONObject().put("command", command).put("package", packageName))
}

@Composable fun RuntimeOverview(active: Boolean) {
    var status by remember { mutableStateOf(JSONObject()) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active) {
        if (active) while (true) {
            try { status = runtimeRpc("runtime.status"); error = "" }
            catch (e: IOException) { error = e.message ?: "读取失败" }
            delay(10000)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("小米推送", style = MaterialTheme.typography.headlineSmall)
        if (!status.optBoolean("connected")) Text(status.optString("error", "等待运行时连接"))
        if (status.optBoolean("connected")) {
            Text("推送连接：" + when (status.optString("connectionState")) { "connected" -> "已连接"; "disconnected" -> "已断开"; "connecting" -> "连接中"; else -> "待确认" })
            Text("${status.optInt("registeredApps")} 个注册应用 · 本次运行下行 ${status.optLong("received")} 次 · 应用投递 ${status.optLong("delivered")} 次")
            Text(if (status.optBoolean("moduleHooked")) "系统通知适配已加载" else "系统通知适配待加载")
            OutlinedButton(onClick = { scope.launch {
                try { runtimeRpc("runtime.reconnect"); error = "已请求重连" }
                catch (e: IOException) { error = e.message ?: "重连失败" }
            } }) { Text("重新连接小米推送") }
        }
        if (error.isNotEmpty()) Text(error)
    }
}

@Composable fun RuntimeApplicationPanel(packageName: String) {
    var open by remember(packageName) { mutableStateOf(false) }
    var original by remember(packageName) { mutableStateOf("") }
    var draft by remember(packageName) { mutableStateOf("") }
    var error by remember(packageName) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun load() { scope.launch {
        busy = true
        try { original = runtimeRpc("runtime.application", packageName).toString(); draft = original; error = "" }
        catch (e: IOException) { error = e.message ?: "读取失败" }
        finally { busy = false }
    } }
    fun dismiss() { if (draft != original) discard = true else open = false }
    OutlinedButton(onClick = { open = true; load() }) { Text("小米推送配置") }
    OutlinedButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }) { Text("通知渠道、声音与振动") }
    if (open) AlertDialog(
        onDismissRequest = { dismiss() }, title = { Text("小米推送配置") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotEmpty()) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = { load() }) { Text("重新加载") } }
            if (draft.isNotEmpty()) {
                val value = JSONObject(draft)
                Text("注册请求", style = MaterialTheme.typography.titleMedium)
                listOf(0 to "询问", 2 to "允许", 3 to "拒绝").forEach { (type, label) ->
                    Row { RadioButton(value.getInt("type") == type, onClick = { draft = JSONObject(draft).put("type", type).toString() }); Text(label, Modifier.padding(top = 12.dp)) }
                }
                listOf("blocked" to "阻止此应用推送", "notificationOnRegister" to "注册时通知", "clickFallback" to "原始点击失败时允许回退").forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Switch(value.getBoolean(key), { draft = JSONObject(draft).put(key, it).toString() }, enabled = !busy) }
                }
                Text("优先使用通知原始目标；回退不能保证进入消息对应页面。", style = MaterialTheme.typography.bodySmall)
            }
        } },
        confirmButton = { Button(enabled = !busy && draft.isNotEmpty() && draft != original, onClick = { scope.launch {
            busy = true
            try {
                val saved = withContext(Dispatchers.IO) { Wire.call(JSONObject().put("command", "runtime.application.set")
                    .put("package", packageName).put("expected", JSONObject(original)).put("settings", JSONObject(draft))) }
                original = saved.toString(); draft = original; open = false
            } catch (e: IOException) { error = e.message ?: "保存失败" }
            finally { busy = false }
        } }) { Text("保存更改") } },
        dismissButton = { TextButton(onClick = { dismiss() }) { Text("取消") } }
    )
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的更改？") },
        confirmButton = { TextButton(onClick = { draft = original; discard = false; open = false }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
