package io.github.magisk317.mipush.manager.runtime

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/** Root app_process cannot bind Android services without an AMS application record. */
class QixinRuntimeProvider : ContentProvider() {
    private val endpoint by lazy { ManagerRuntimeEndpoint(requireNotNull(context)).also { it.start() } }
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (Binder.getCallingUid() != 0) throw SecurityException("Root daemon required")
        require(method == "management") { "Unknown method" }
        return Bundle().apply { putBinder("runtime", endpoint.binder) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
