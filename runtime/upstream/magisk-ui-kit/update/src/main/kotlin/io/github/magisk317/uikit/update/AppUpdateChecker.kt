package io.github.magisk317.uikit.update

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_SECONDS = 10L
private const val READ_TIMEOUT_SECONDS = 15L
private const val TAG_CI_SOURCE = "GitLabCiArtifactSource"

/**
 * Terminal outcome of a single update source query.
 *
 * - [Available]: the source holds a newer build and produced a manifest for it.
 * - [UpToDate]: the current install already satisfies this source (terminal state,
 *   iteration stops: e.g. running the newest CI build must not fall through to a
 *   release or a lower channel).
 * - [Unavailable]: the source cannot answer right now (no artifact, API failure,
 *   unresolvable versionCode); iteration falls through to the next source.
 */
sealed interface SourceResult {
    data class Available(val manifest: UpdateManifest) : SourceResult
    data object UpToDate : SourceResult
    data object Unavailable : SourceResult
}

/**
 * Abstract source for fetching update manifests.
 */
interface UpdateSource {
    suspend fun fetchManifest(currentVersionCode: Int): SourceResult
}

/**
 * Update source directly querying update-manifest.json over HTTP(S).
 */
class ManifestUrlUpdateSource(
    private val url: String,
    private val client: OkHttpClient = AppUpdateChecker.defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UpdateSource {
    override suspend fun fetchManifest(currentVersionCode: Int): SourceResult = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@use SourceResult.Unavailable
                }
                SourceResult.Available(json.decodeFromString<UpdateManifest>(resp.body.string()))
            }
        }.getOrDefault(SourceResult.Unavailable)
    }
}

/**
 * Update source querying GitLab CI Artifacts for CI tracks (mandatory update).
 * Resolves artifacts using GitLab's direct raw artifact endpoints:
 * https://gitlab.com/{projectPath}/-/jobs/artifacts/{branch}/raw/{artifactPath}?job={jobName}
 *
 * Version axis: the project-scoped pipeline iid, which is exactly what the release
 * pipeline injects as versionCode (see resolve_play_release.sh), so CI builds and
 * official releases compare on one single axis.
 *
 * Version name: the staged artifact path is a stable copy that carries no
 * version information. CI stages the version string embedded in the APK
 * name (e.g. 0.3.3-20261017_171158) as a plain-text sidecar (see
 * [versionFilePath]), which is preferred. Older pipelines without the
 * sidecar fall back to the version catalog at the pipeline commit plus
 * the short sha (see [versionCatalogPath]).
 */
class GitLabCiArtifactUpdateSource(
    private val projectPath: String, // e.g. "magisk3171/XposedSmsCode"
    private val branch: String = "beta",
    private val jobName: String = "android:build",
    private val artifactPath: String = "app/build/outputs/apk/github/release/app-github-release.apk",
    private val currentCommitHash: String = "",
    /** Repo-relative path of the version catalog used to resolve the real versionName. */
    private val versionCatalogPath: String = "gradle/libs.versions.toml",
    /** Repo-relative path of the sidecar file staging the APK's embedded build timestamp. */
    private val versionFilePath: String = "ci-dist/version.txt",
    private val client: OkHttpClient = AppUpdateChecker.defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UpdateSource {
    override suspend fun fetchManifest(currentVersionCode: Int): SourceResult = withContext(Dispatchers.IO) {
        val encodedPath = runCatching { URLEncoder.encode(projectPath, "UTF-8") }.getOrDefault(projectPath.replace("/", "%2F"))
        val pipelineUrl = "https://gitlab.com/api/v4/projects/$encodedPath/pipelines?ref=$branch&status=success&per_page=1"
        runCatching {
            val req = Request.Builder()
                .url(pipelineUrl)
                .header("User-Agent", "AppUpdateChecker")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use SourceResult.Unavailable
                val array = json.parseToJsonElement(resp.body.string()).jsonArray
                if (array.isEmpty()) return@use SourceResult.Unavailable
                val latestPipeline = array[0].jsonObject
                val latestSha = latestPipeline["sha"]?.jsonPrimitive?.content.orEmpty()
                // Use the project-scoped pipeline iid (not the global id, which overflows Int
                // and matches the versionCode floor Google Play assigns via CI_PIPELINE_IID).
                val pipelineIid = latestPipeline["iid"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                if (currentCommitHash.isNotBlank() && latestSha.startsWith(currentCommitHash.take(8))) {
                    Log.i(TAG_CI_SOURCE, "Running latest CI build $latestSha")
                    return@use SourceResult.UpToDate
                }

                // Same version axis fallback: a build without a matching commit hash
                // (e.g. installed from GitHub with an unknown hash) is still up to
                // date once its versionCode reaches the latest pipeline iid.
                if (pipelineIid in 1..currentVersionCode) {
                    Log.i(TAG_CI_SOURCE, "Current versionCode $currentVersionCode >= pipeline iid $pipelineIid")
                    return@use SourceResult.UpToDate
                }
                if (pipelineIid <= 0) return@use SourceResult.Unavailable

                val pipelineId = latestPipeline["id"]?.jsonPrimitive?.content.orEmpty()
                if (pipelineId.isBlank() || !hasDownloadableArtifact(encodedPath, pipelineId)) {
                    Log.w(TAG_CI_SOURCE, "No downloadable artifact for job $jobName in pipeline $pipelineId")
                    return@use SourceResult.Unavailable
                }

                var commitTitle = ""
                runCatching {
                    val commitUrl = "https://gitlab.com/api/v4/projects/$encodedPath/repository/commits/$latestSha"
                    val cReq = Request.Builder().url(commitUrl).header("User-Agent", "AppUpdateChecker").get().build()
                    client.newCall(cReq).execute().use { cResp ->
                        if (cResp.isSuccessful) {
                            val cObj = json.parseToJsonElement(cResp.body.string()).jsonObject
                            commitTitle = cObj["title"]?.jsonPrimitive?.content.orEmpty()
                        }
                    }
                }

                // The staged artifact path and the artifact zip name carry no
                // version information. Prefer the sidecar file staging the
                // APK's embedded build timestamp; fall back to the version
                // catalog at the pipeline commit for older pipelines.
                val sidecarVersionName = fetchSidecarVersionName()
                val catalogVersionName =
                    if (sidecarVersionName.isBlank()) fetchCatalogVersionName(encodedPath, latestSha) else ""
                val downloadUrl = "https://gitlab.com/$projectPath/-/jobs/artifacts/$branch/raw/$artifactPath?job=$jobName"
                val shortSha = latestSha.take(8)
                val notes = buildString {
                    if (commitTitle.isNotBlank()) {
                        append(commitTitle)
                        append("\n\n")
                    }
                    append("Commit: ").append(shortSha).append(" (").append(branch).append(")")
                }

                val versionName = when {
                    sidecarVersionName.isNotBlank() -> sidecarVersionName
                    catalogVersionName.isNotBlank() -> "$catalogVersionName-$shortSha"
                    else -> "ci-$shortSha"
                }
                SourceResult.Available(
                    UpdateManifest(
                        versionCode = pipelineIid,
                        versionName = versionName,
                        apkUrl = downloadUrl,
                        releaseNotes = notes,
                        force = true,
                        minSupportedVersionCode = pipelineIid,
                    ),
                )
            }
        }.getOrDefault(SourceResult.Unavailable)
    }

    // A pipeline can be success while its build job never ran (rules skip) or
    // its artifacts already expired; the artifact URL would then 404 and the
    // force prompt would nag with an undownloadable update. Probe the pipeline
    // jobs and skip this source when the artifact is not actually there.
    private suspend fun hasDownloadableArtifact(encodedPath: String, pipelineId: String): Boolean =
        withContext(Dispatchers.IO) {
            val jobsUrl = "https://gitlab.com/api/v4/projects/$encodedPath/pipelines/$pipelineId/jobs?per_page=100"
            runCatching {
                val jobsReq = Request.Builder()
                    .url(jobsUrl)
                    .header("User-Agent", "AppUpdateChecker")
                    .get()
                    .build()
                client.newCall(jobsReq).execute().use { jobsResp ->
                    if (!jobsResp.isSuccessful) return@use false
                    json.parseToJsonElement(jobsResp.body.string()).jsonArray.any { element ->
                        val job = element.jsonObject
                        job["name"]?.jsonPrimitive?.content == jobName &&
                            job["artifacts_file"]?.jsonObject
                                ?.get("filename")?.jsonPrimitive?.content?.isNotBlank() == true
                    }
                }
            }.getOrDefault(false)
        }

    /**
     * Resolve the real versionName of a CI build from the version catalog at
     * the pipeline commit. The staged artifact (e.g. ci-dist/app-ci.apk) and
     * the artifact zip name carry no version information, and GitLab exposes
     * no per-file artifact listing, so the catalog at [sha] is the
     * authoritative source. Returns "" when the catalog cannot be read, and
     * callers fall back to the sha-only "ci-<sha>" form.
     */
    private suspend fun fetchCatalogVersionName(encodedPath: String, sha: String): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedCatalogPath = runCatching { URLEncoder.encode(versionCatalogPath, "UTF-8") }
                    .getOrDefault(versionCatalogPath.replace("/", "%2F"))
                val fileUrl = "https://gitlab.com/api/v4/projects/$encodedPath/repository/files/" +
                    "$encodedCatalogPath/raw?ref=$sha"
                val req = Request.Builder()
                    .url(fileUrl)
                    .header("User-Agent", "AppUpdateChecker")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use ""
                    Regex("^versionName\\s*=\\s*\"([^\"]+)\"", RegexOption.MULTILINE)
                        .find(resp.body.string())
                        ?.groupValues?.get(1)
                        ?.trim()
                        .orEmpty()
                }
            }.getOrDefault("")
        }

    /**
     * Read the sidecar file that CI stages next to the copied APK. The APK
     * name embeds the build timestamp (e.g. 0.3.3-20261017_171158) but the
     * staged artifact path is a stable copy and GitLab exposes no per-file
     * artifact listing, so the version string is staged as a plain-text
     * sidecar. Returns "" when the sidecar is missing (older pipelines) or
     * malformed, and callers fall back to the catalog + short sha form.
     */
    private suspend fun fetchSidecarVersionName(): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileUrl = "https://gitlab.com/$projectPath/-/jobs/artifacts/$branch/raw/" +
                    "$versionFilePath?job=$jobName"
                val req = Request.Builder()
                    .url(fileUrl)
                    .header("User-Agent", "AppUpdateChecker")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use ""
                    resp.body.string()
                        .trim()
                        .takeIf { Regex("^[\\w.+-]+-\\d{8}_\\d{6}$").matches(it) }
                        .orEmpty()
                }
            }.getOrDefault("")
        }
}

/**
 * Update source querying GitLab Releases API (e.g. magisk3171/XposedSmsCode).
 * Automatically resolves architecture-matching APKs and release notes.
 *
 * Version axis: the iid of the pipeline that built the release tag, matching the
 * versionCode injected into release APK/AAB artifacts. Legacy releases that
 * predate the iid axis cannot be resolved and report [SourceResult.Unavailable]
 * instead of fabricating a second version axis.
 */
class GitLabReleaseUpdateSource(
    private val projectPath: String, // e.g. "magisk3171/XposedSmsCode"
    private val client: OkHttpClient = AppUpdateChecker.defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UpdateSource {
    override suspend fun fetchManifest(currentVersionCode: Int): SourceResult = withContext(Dispatchers.IO) {
        val encodedPath = runCatching { URLEncoder.encode(projectPath, "UTF-8") }.getOrDefault(projectPath.replace("/", "%2F"))
        val apiUrl = "https://gitlab.com/api/v4/projects/$encodedPath/releases/permalink/latest"
        runCatching {
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "AppUpdateChecker")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use SourceResult.Unavailable
                val root = json.parseToJsonElement(resp.body.string()).jsonObject
                val tagName = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
                val body = root["description"]?.jsonPrimitive?.content.orEmpty()
                val assetsObj = root["assets"]?.jsonObject
                val links = assetsObj?.get("links")?.jsonArray.orEmpty()

                val commitObj = root["commit"]?.jsonObject
                val commitTitle = commitObj?.get("title")?.jsonPrimitive?.content.orEmpty()
                val finalNotes = if (commitTitle.isNotBlank()) {
                    if (body.isNotBlank() && body != commitTitle) "$commitTitle\n\n$body" else commitTitle
                } else {
                    body
                }

                val cleanVersionName = tagName.removePrefix("v")
                val parsedCode = resolvePipelineIidForTag(encodedPath, tagName, client, json)
                    ?: return@use SourceResult.Unavailable

                var chosenApkUrl = ""
                val supportedAbis = Build.SUPPORTED_ABIS.toList()
                var bestMatchScore = -1

                for (item in links) {
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    val downloadUrl = obj["direct_asset_url"]?.jsonPrimitive?.content
                        ?: obj["url"]?.jsonPrimitive?.content.orEmpty()
                    if (!name.endsWith(".apk")) continue

                    var score = 0
                    for ((idx, abi) in supportedAbis.withIndex()) {
                        if (name.contains(abi, ignoreCase = true)) {
                            score = 100 - idx
                            break
                        }
                    }
                    if (score == 0 && (name.contains("universal", ignoreCase = true) || name.contains("all", ignoreCase = true))) {
                        score = 10
                    }
                    if (score > bestMatchScore || chosenApkUrl.isEmpty()) {
                        bestMatchScore = score
                        chosenApkUrl = downloadUrl
                    }
                }

                if (chosenApkUrl.isNotBlank()) {
                    val isCiOrTest = tagName.contains("-test") || tagName.contains("-ci")
                    SourceResult.Available(
                        UpdateManifest(
                            versionCode = parsedCode,
                            versionName = cleanVersionName,
                            apkUrl = chosenApkUrl,
                            releaseNotes = finalNotes,
                            force = isCiOrTest,
                            minSupportedVersionCode = if (isCiOrTest) parsedCode else 0,
                        ),
                    )
                } else {
                    SourceResult.Unavailable
                }
            }
        }.getOrDefault(SourceResult.Unavailable)
    }
}

/**
 * Update source querying GitHub Releases API (e.g. magisk317/XposedSmsCode).
 *
 * GitHub release bodies/assets carry no versionCode, so the iid is resolved from
 * the GitLab pipeline that built the same tag ([gitlabProjectPath]); this keeps
 * the GitHub mirror on the exact same version axis as the GitLab source. When
 * the iid cannot be resolved the source reports [SourceResult.Unavailable]
 * rather than inventing a second axis.
 */
class GitHubReleaseUpdateSource(
    private val repository: String, // e.g. "magisk317/XposedSmsCode"
    private val gitlabProjectPath: String, // e.g. "magisk3171/XposedSmsCode"
    private val client: OkHttpClient = AppUpdateChecker.defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UpdateSource {
    override suspend fun fetchManifest(currentVersionCode: Int): SourceResult = withContext(Dispatchers.IO) {
        val encodedGitlabPath = runCatching { URLEncoder.encode(gitlabProjectPath, "UTF-8") }
            .getOrDefault(gitlabProjectPath.replace("/", "%2F"))
        val apiUrl = "https://api.github.com/repos/$repository/releases/latest"
        runCatching {
            val req = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "AppUpdateChecker")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use SourceResult.Unavailable
                val root = json.parseToJsonElement(resp.body.string()).jsonObject
                val tagName = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
                val body = root["body"]?.jsonPrimitive?.content.orEmpty()
                val assets = root["assets"]?.jsonArray.orEmpty()

                val commitObj = root["commit"]?.jsonObject
                val commitTitle = commitObj?.get("title")?.jsonPrimitive?.content.orEmpty()
                val finalNotes = if (commitTitle.isNotBlank()) {
                    if (body.isNotBlank() && body != commitTitle) "$commitTitle\n\n$body" else commitTitle
                } else {
                    body
                }

                val cleanVersionName = tagName.removePrefix("v")
                val parsedCode = resolvePipelineIidForTag(encodedGitlabPath, tagName, client, json)
                    ?: return@use SourceResult.Unavailable

                var chosenApkUrl = ""
                val supportedAbis = Build.SUPPORTED_ABIS.toList()

                var bestMatchScore = -1
                for (item in assets) {
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    val downloadUrl = obj["browser_download_url"]?.jsonPrimitive?.content.orEmpty()
                    if (!name.endsWith(".apk")) continue

                    var score = 0
                    for ((idx, abi) in supportedAbis.withIndex()) {
                        if (name.contains(abi, ignoreCase = true)) {
                            score = 100 - idx
                            break
                        }
                    }
                    if (score == 0 && (name.contains("universal", ignoreCase = true) || name.contains("all", ignoreCase = true))) {
                        score = 10
                    }
                    if (score > bestMatchScore || chosenApkUrl.isEmpty()) {
                        bestMatchScore = score
                        chosenApkUrl = downloadUrl
                    }
                }

                if (chosenApkUrl.isNotBlank()) {
                    val isCiOrTest = tagName.contains("-test") || tagName.contains("-ci")
                    SourceResult.Available(
                        UpdateManifest(
                            versionCode = parsedCode,
                            versionName = cleanVersionName,
                            apkUrl = chosenApkUrl,
                            releaseNotes = finalNotes,
                            force = isCiOrTest,
                            minSupportedVersionCode = if (isCiOrTest) parsedCode else 0,
                        ),
                    )
                } else {
                    SourceResult.Unavailable
                }
            }
        }.getOrDefault(SourceResult.Unavailable)
    }
}

/**
 * Resolve the versionCode of a release tag: the project-scoped iid of the
 * pipeline that built it. This is the single version axis shared by CI builds,
 * release APKs and Play AABs (all inject CI_PIPELINE_IID as versionCode).
 * Returns null when no successful pipeline exists for the tag (legacy releases
 * built before the iid axis, or an API failure).
 */
private suspend fun resolvePipelineIidForTag(
    encodedProjectPath: String,
    tagName: String,
    client: OkHttpClient,
    json: Json,
): Int? = withContext(Dispatchers.IO) {
    if (tagName.isBlank()) return@withContext null
    val encodedTag = runCatching { URLEncoder.encode(tagName, "UTF-8") }.getOrDefault(tagName)
    val pipelinesUrl = "https://gitlab.com/api/v4/projects/$encodedProjectPath/pipelines?ref=$encodedTag&status=success&per_page=1"
    runCatching {
        val req = Request.Builder()
            .url(pipelinesUrl)
            .header("User-Agent", "AppUpdateChecker")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val array = json.parseToJsonElement(resp.body.string()).jsonArray
            array.firstOrNull()?.jsonObject
                ?.get("iid")?.jsonPrimitive?.content?.toIntOrNull()
                ?.takeIf { it > 0 }
        }
    }.getOrNull()
}

/**
 * In-app update checker engine.
 */
class AppUpdateChecker(
    context: Context,
    private val sources: List<UpdateSource>,
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private const val TAG = "AppUpdateChecker"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    private val appContext = context.applicationContext ?: context

    fun currentVersionCode(): Int = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionCode
        }
    }.getOrDefault(0)

    fun currentVersionName(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    suspend fun check(skippedVersionCode: Int = 0): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentCode = currentVersionCode()
        for (source in sources) {
            val result = runCatching { source.fetchManifest(currentCode) }
                .getOrElse {
                    Log.w(TAG, "Source fetch failed: ${it.message}")
                    SourceResult.Unavailable
                }
            when (result) {
                is SourceResult.UpToDate -> return@withContext UpdateCheckResult.UpToDate
                is SourceResult.Unavailable -> {
                    Log.w(TAG, "Source unavailable, trying next source")
                    continue
                }
                is SourceResult.Available -> {
                    val manifest = result.manifest
                    val force = manifest.force ||
                        (manifest.minSupportedVersionCode > 0 && currentCode < manifest.minSupportedVersionCode)
                    if (!force && manifest.versionCode <= currentCode) {
                        return@withContext UpdateCheckResult.UpToDate
                    }
                    if (!force && manifest.versionCode == skippedVersionCode) {
                        Log.i(TAG, "Version ${manifest.versionCode} skipped by user")
                        return@withContext UpdateCheckResult.UpToDate
                    }
                    return@withContext UpdateCheckResult.Available(
                        manifest = manifest,
                        currentVersionCode = currentCode,
                        currentVersionName = currentVersionName(),
                        force = force,
                    )
                }
            }
        }
        UpdateCheckResult.Failed(appContext.getString(R.string.uikit_update_err_no_source), network = true)
    }
}
