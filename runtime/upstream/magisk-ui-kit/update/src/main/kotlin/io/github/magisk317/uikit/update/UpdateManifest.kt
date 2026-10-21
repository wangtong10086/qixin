package io.github.magisk317.uikit.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * In-app update manifest model.
 */
@Serializable
data class UpdateManifest(
    @SerialName("versionCode") val versionCode: Int = 0,
    @SerialName("versionName") val versionName: String = "",
    /** Download URL of the new APK package. */
    @SerialName("apkUrl") val apkUrl: String = "",
    /** Release notes / changelog text. */
    @SerialName("releaseNotes") val releaseNotes: String = "",
    /** Whether this update is mandatory/forced. */
    @SerialName("force") val force: Boolean = false,
    /** Minimum supported versionCode; builds below this are force-updated. */
    @SerialName("minSupportedVersionCode") val minSupportedVersionCode: Int = 0,
    /** Optional MD5 checksum for package verification. */
    @SerialName("md5") val md5: String = "",
)

/** Result of checking for updates. */
sealed interface UpdateCheckResult {
    data class Available(
        val manifest: UpdateManifest,
        val currentVersionCode: Int,
        val currentVersionName: String,
        val force: Boolean,
    ) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult
    data class Failed(val reason: String, val network: Boolean = false) : UpdateCheckResult
}
