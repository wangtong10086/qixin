package io.github.wangtong10086.qixin.service

import android.content.AttributionSource
import android.os.*
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Android 16 external-provider API is the supported root path without an AMS app record. */
internal class RootRuntimeConnection {
    private val authority = "com.xiaomi.xmsf.qixin.runtime"
    private val token = Binder()
    private val managerType = Class.forName("android.app.IActivityManager")
    private val manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
    private var acquired = false
    private fun invoke(method: Method, receiver: Any?, vararg arguments: Any?): Any? =
        try { method.invoke(receiver, *arguments) } catch (error: InvocationTargetException) { throw error.targetException }

    fun acquire(): IBinder {
        check(Process.myUid() == 0)
        val holder = invoke(managerType.getMethod("getContentProviderExternal", String::class.java,
            Int::class.javaPrimitiveType, IBinder::class.java, String::class.java), manager,
            authority, 0, token, "QixinRootManager") ?: throw IOException("栖信推送运行时尚未安装")
        acquired = true
        try {
            val provider = holder.javaClass.getField("provider").get(holder)
            val source = AttributionSource.Builder(0).setPackageName("android").build()
            val response = invoke(Class.forName("android.content.IContentProvider").getMethod("call",
                AttributionSource::class.java, String::class.java, String::class.java, String::class.java, Bundle::class.java),
                provider, source, authority, "management", null, null) as Bundle
            return response.getBinder("runtime") ?: throw IOException("运行时未返回管理接口")
        } catch (error: Exception) {
            release()
            throw error
        }
    }
    fun release() {
        if (acquired) {
            invoke(managerType.getMethod("removeContentProviderExternalAsUser", String::class.java, IBinder::class.java,
                Int::class.javaPrimitiveType), manager, authority, token, 0)
            acquired = false
        }
    }
}
