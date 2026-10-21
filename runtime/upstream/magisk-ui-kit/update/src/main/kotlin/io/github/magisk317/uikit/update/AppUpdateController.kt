package io.github.magisk317.uikit.update

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * UI presentation state for in-app updates.
 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Checking(val userInitiated: Boolean) : UpdateUiState
    data class Available(
        val manifest: UpdateManifest,
        val currentVersionCode: Int,
        val currentVersionName: String,
        val force: Boolean,
        val userInitiated: Boolean,
    ) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    data class ReadyToInstall(val file: File, val force: Boolean = false) : UpdateUiState
    data class Failed(val reason: String, val userInitiated: Boolean = false) : UpdateUiState
    data class UpToDate(val userInitiated: Boolean = false) : UpdateUiState
}

/**
 * Update coordinator managing check, download, install, and post-installation relaunch.
 */
class AppUpdateController(
    context: Context,
    private val checker: AppUpdateChecker,
    private val downloader: AppUpdateDownloader,
    private val fileProviderAuthority: String = "${context.packageName}.files",
    private val getSkippedVersion: () -> Int = {
        val ctx = context.applicationContext ?: context
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SKIPPED_VERSION_CODE, 0)
    },
    private val saveSkippedVersion: (Int) -> Unit = { versionCode ->
        val ctx = context.applicationContext ?: context
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SKIPPED_VERSION_CODE, versionCode)
            .apply()
    },
    private val onForceUpdateDetected: suspend () -> Unit = {},
) {
    companion object {
        private const val TAG = "AppUpdateController"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val AUTO_RETRY_INTERVAL_MS = 10_000L
        private const val PREFS_NAME = "uikit_update_prefs"
        private const val KEY_SKIPPED_VERSION_CODE = "skipped_version_code"
    }

    private val appContext = context.applicationContext ?: context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _hasUpdate = MutableStateFlow(false)
    val hasUpdate: StateFlow<Boolean> = _hasUpdate.asStateFlow()

    private var pendingManifest: UpdateManifest? = null
    private var lastFailureWasNetwork = false
    private var lastAutoRetryAt = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkBack()
        }
    }

    init {
        runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(networkCallback)
        }.onFailure { Log.w(TAG, "register network callback failed: ${it.message}") }
    }

    private fun onNetworkBack() {
        if (!lastFailureWasNetwork) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAutoRetryAt < AUTO_RETRY_INTERVAL_MS) return
        lastAutoRetryAt = now
        Log.i(TAG, "Network restored, auto re-checking update")
        check(userInitiated = false)
    }

    val currentVersionCode: Int get() = checker.currentVersionCode()
    val currentVersionName: String get() = checker.currentVersionName()

    fun check(userInitiated: Boolean = false) {
        val s = _state.value
        if (s is UpdateUiState.Checking || s is UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Checking(userInitiated)
        scope.launch {
            _state.value = when (val result = checker.check(skippedVersionCode = getSkippedVersion())) {
                is UpdateCheckResult.Available -> {
                    pendingManifest = result.manifest
                    lastFailureWasNetwork = false
                    _hasUpdate.value = true
                    if (result.force) {
                        runCatching { onForceUpdateDetected() }
                    }
                    UpdateUiState.Available(
                        manifest = result.manifest,
                        currentVersionCode = result.currentVersionCode,
                        currentVersionName = result.currentVersionName,
                        force = result.force,
                        userInitiated = userInitiated,
                    )
                }
                is UpdateCheckResult.UpToDate -> {
                    pendingManifest = null
                    lastFailureWasNetwork = false
                    _hasUpdate.value = false
                    UpdateUiState.UpToDate(userInitiated)
                }
                is UpdateCheckResult.Failed -> {
                    lastFailureWasNetwork = result.network
                    UpdateUiState.Failed(result.reason, userInitiated)
                }
            }
        }
    }

    fun download() {
        val manifest = pendingManifest ?: return
        val isForce = manifest.force
        _state.value = UpdateUiState.Downloading(0f)
        scope.launch {
            downloader.download(manifest).collect { ds ->
                _state.value = when (ds) {
                    is DownloadState.Running -> UpdateUiState.Downloading(ds.progress)
                    is DownloadState.Done -> UpdateUiState.ReadyToInstall(ds.file, force = isForce)
                    is DownloadState.Failed -> UpdateUiState.Failed(ds.reason, userInitiated = true)
                }
            }
        }
    }

    fun skip() {
        val s = _state.value
        if (s is UpdateUiState.Available) {
            saveSkippedVersion(s.manifest.versionCode)
        }
        pendingManifest = null
        _state.value = UpdateUiState.Idle
    }

    fun retry() {
        if (pendingManifest != null) download() else check(userInitiated = true)
    }

    fun dismiss() {
        val s = _state.value
        if (s is UpdateUiState.Available && s.force) {
            // Force-update dialogs do not permit normal dismissal
            return
        }
        if (_state.value !is UpdateUiState.Downloading) {
            pendingManifest = null
            _state.value = UpdateUiState.Idle
        }
    }

    fun install(context: Context) {
        val s = _state.value
        if (s !is UpdateUiState.ReadyToInstall) return
        val ctx = context.applicationContext ?: context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ctx.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.fromParts("package", ctx.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(intent) }
                return
            }
        }
        val uri = runCatching {
            FileProvider.getUriForFile(ctx, fileProviderAuthority, s.file)
        }.getOrElse {
            _state.value = UpdateUiState.Failed(
                ctx.getString(R.string.uikit_update_err_uri_failed, it.message ?: ""),
                userInitiated = true,
            )
            return
        }

        // Schedule an exact alarm to relaunch the application upon package update replacement
        runCatching {
            val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (launchIntent != null) {
                val pendingIntent = PendingIntent.getActivity(
                    ctx,
                    10086,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
                )
                val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val triggerAt = System.currentTimeMillis() + 4500L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            }
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching { ctx.startActivity(intent) }.isSuccess
        if (!ok) {
            _state.value = UpdateUiState.Failed(
                ctx.getString(R.string.uikit_update_err_intent_failed, s.file.name),
                userInitiated = true,
            )
        }
    }
}
