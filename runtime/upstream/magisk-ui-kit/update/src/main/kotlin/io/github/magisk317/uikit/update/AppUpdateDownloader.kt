package io.github.magisk317.uikit.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface DownloadState {
    data class Running(val progress: Float) : DownloadState
    data class Done(val file: File) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * In-app update file downloader handling background HTTP streaming and progress emission.
 */
class AppUpdateDownloader(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private const val TAG = "AppUpdateDownloader"
        private const val DIR_NAME = "updates"
        private const val PROGRESS_EMIT_INTERVAL_MS = 200L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    private val appContext = context.applicationContext ?: context

    fun download(manifest: UpdateManifest): Flow<DownloadState> = flow {
        emit(DownloadState.Running(0f))
        val dir = File(appContext.getExternalFilesDir(null), DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) {
            emit(DownloadState.Failed(appContext.getString(R.string.uikit_update_err_cannot_create_dir)))
            return@flow
        }
        val fileName = "update-${manifest.versionName}-${manifest.versionCode}.apk"
        val target = File(dir, fileName)
        val tmp = File(dir, "${fileName}.part")
        if (target.exists()) target.delete()
        if (tmp.exists()) tmp.delete()

        try {
            val request = Request.Builder().url(manifest.apkUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadState.Failed(appContext.getString(R.string.uikit_update_err_download_http, response.code)))
                    return@use
                }
                val body = response.body
                val total = body.contentLength()
                var downloaded = 0L
                var lastEmitAt = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS) {
                                lastEmitAt = now
                                emit(DownloadState.Running(if (total > 0) downloaded.toFloat() / total else -1f))
                            }
                        }
                    }
                }
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    emit(DownloadState.Failed(appContext.getString(R.string.uikit_update_err_save_failed)))
                    return@use
                }
                if (manifest.md5.isNotBlank() && !target.md5().equals(manifest.md5, ignoreCase = true)) {
                    target.delete()
                    emit(DownloadState.Failed(appContext.getString(R.string.uikit_update_err_md5_mismatch)))
                    return@use
                }
                Log.i(TAG, "downloaded ${target.name} (${target.length()} bytes)")
                emit(DownloadState.Done(target))
            }
        } catch (e: IOException) {
            tmp.delete()
            Log.w(TAG, "download failed: ${e.message}")
            val msg = e.message ?: appContext.getString(R.string.uikit_update_err_network)
            emit(DownloadState.Failed(msg))
        }
    }.flowOn(Dispatchers.IO)

    private fun File.md5(): String {
        val digest = MessageDigest.getInstance("MD5")
        inputStream().use { fis ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = fis.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
