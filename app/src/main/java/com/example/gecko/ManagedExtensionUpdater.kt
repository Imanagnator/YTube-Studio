package com.example.gecko

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

enum class ExtensionCategory(val label: String) {
    STREAM("YouTube Stream"),
    STUDIO("YT Studio"),
    BOTH("Stream & Studio")
}

enum class ExtensionSource {
    GITHUB_UBLOCK,
    MOZILLA_AMO
}

data class ManagedExtensionSpec(
    val id: String,
    val key: String,
    val name: String,
    val description: String,
    val category: ExtensionCategory,
    val source: ExtensionSource,
    val amoSlug: String? = null,
    val bundledAssetPath: String? = null,
    val bundledVersion: String? = null
)

data class ManagedExtensionRelease(
    val version: String,
    val downloadUrl: String,
    val sourceLabel: String
)

data class ManagedExtensionState(
    val spec: ManagedExtensionSpec,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = true,
    val version: String = spec.bundledVersion ?: "Pending",
    val isUpdating: Boolean = false,
    val statusDetail: String = if (spec.bundledAssetPath != null) {
        "Initializing bundled XPI..."
    } else {
        "Ready to auto-install from Mozilla AMO"
    }
)

object ManagedExtensionsCatalog {
    val UBLOCK_SPEC = ManagedExtensionSpec(
        id = UBlockOriginUpdater.UBLOCK_EXTENSION_ID,
        key = "ublock_origin",
        name = "uBlock Origin",
        description = "Blocks YouTube ads, trackers & banners (gorhill/uBlock)",
        category = ExtensionCategory.BOTH,
        source = ExtensionSource.GITHUB_UBLOCK,
        bundledAssetPath = UBlockOriginUpdater.BUNDLED_ASSET_PATH,
        bundledVersion = UBlockOriginUpdater.BUNDLED_VERSION
    )

    val SPONSORBLOCK_SPEC = ManagedExtensionSpec(
        id = "sponsorBlocker@ajay.app",
        key = "sponsorblock",
        name = "SponsorBlock",
        description = "Auto-skips sponsor segments, intros, outros & self-promos",
        category = ExtensionCategory.STREAM,
        source = ExtensionSource.MOZILLA_AMO,
        amoSlug = "sponsorblock"
    )

    val RETURN_DISLIKE_SPEC = ManagedExtensionSpec(
        id = "{762f9885-5a13-4abd-9c77-433dcd38b8fd}",
        key = "return_youtube_dislike",
        name = "Return YouTube Dislike",
        description = "Restores public dislike counts on YouTube videos",
        category = ExtensionCategory.STREAM,
        source = ExtensionSource.MOZILLA_AMO,
        amoSlug = "return-youtube-dislikes"
    )

    val DEARROW_SPEC = ManagedExtensionSpec(
        id = "deArrow@ajay.app",
        key = "dearrow",
        name = "DeArrow",
        description = "Replaces clickbait titles & thumbnails with clean previews",
        category = ExtensionCategory.STREAM,
        source = ExtensionSource.MOZILLA_AMO,
        amoSlug = "dearrow"
    )

    val YOUTUBE_NONSTOP_SPEC = ManagedExtensionSpec(
        id = "{0d7cafdd-501c-49ca-8ebb-e3341caaa55e}",
        key = "youtube_nonstop",
        name = "YouTube NonStop",
        description = "Dismisses 'Continue watching?' pauses during long playback",
        category = ExtensionCategory.STREAM,
        source = ExtensionSource.MOZILLA_AMO,
        amoSlug = "youtube-nonstop"
    )

    val ENHANCER_YT_SPEC = ManagedExtensionSpec(
        id = "enhancerforyoutube@maximerf.addons.mozilla.org",
        key = "enhancer_for_youtube",
        name = "Enhancer for YouTube",
        description = "Volume booster, custom playback speeds & cinema controls",
        category = ExtensionCategory.STREAM,
        source = ExtensionSource.MOZILLA_AMO,
        amoSlug = "enhancer-for-youtube"
    )

    val VIDIQ_STUDIO_SPEC = ManagedExtensionSpec(
        id = "firefox@vid.io",
        key = "vidiq_vision",
        name = "vidIQ Vision for Studio",
        description = "Creator SEO scorecard, keyword/tag inspector & Studio tools",
        category = ExtensionCategory.STUDIO,
        source = ExtensionSource.MOZILLA_AMO,
        amoSlug = "vidiq-vision-youtube"
    )

    val ALL_SPECS: List<ManagedExtensionSpec> = listOf(
        UBLOCK_SPEC,
        SPONSORBLOCK_SPEC,
        RETURN_DISLIKE_SPEC,
        DEARROW_SPEC,
        YOUTUBE_NONSTOP_SPEC,
        ENHANCER_YT_SPEC,
        VIDIQ_STUDIO_SPEC
    )

    val ALL_EXTENSION_IDS: Set<String> = ALL_SPECS.map { it.id }.toSet()

    fun findById(id: String): ManagedExtensionSpec? = ALL_SPECS.firstOrNull { it.id == id }
}

class ManagedExtensionUpdater(
    private val context: Context,
    private val uBlockUpdater: UBlockOriginUpdater
) {
    companion object {
        private const val TAG = "ManagedExtensionUpdater"
        private const val AMO_API_BASE = "https://addons.mozilla.org/api/v5/addons/addon/"
        private const val USER_AGENT = "YTubeStudio-Android/1.0.1"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchLatestRelease(spec: ManagedExtensionSpec): Result<ManagedExtensionRelease> =
        withContext(Dispatchers.IO) {
            when (spec.source) {
                ExtensionSource.GITHUB_UBLOCK -> {
                    uBlockUpdater.fetchLatestGitHubRelease().map { rel ->
                        ManagedExtensionRelease(
                            version = rel.version,
                            downloadUrl = rel.downloadUrl,
                            sourceLabel = "GitHub (gorhill/uBlock)"
                        )
                    }
                }
                ExtensionSource.MOZILLA_AMO -> fetchLatestAmoRelease(spec)
            }
        }

    private fun fetchLatestAmoRelease(spec: ManagedExtensionSpec): Result<ManagedExtensionRelease> =
        runCatching {
            val slug = spec.amoSlug ?: error("Missing Mozilla AMO slug for ${spec.name}")
            val request = Request.Builder()
                .url("$AMO_API_BASE$slug/")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Mozilla AMO returned HTTP ${response.code}")
                }
                val body = response.body?.string() ?: error("Empty AMO response")
                val json = JSONObject(body)
                val currentVer = json.optJSONObject("current_version")
                    ?: error("No current_version in AMO metadata for $slug")
                val version = currentVer.optString("version").trim().ifBlank { "latest" }
                val fileObj = currentVer.optJSONObject("file")
                    ?: error("No signed XPI file in AMO metadata for $slug")
                val url = fileObj.optString("url").trim()
                if (!url.startsWith("https://") || !url.contains(".xpi", ignoreCase = true)) {
                    error("Invalid signed XPI URL for $slug")
                }
                ManagedExtensionRelease(
                    version = version,
                    downloadUrl = url,
                    sourceLabel = "Mozilla AMO"
                )
            }
        }.onFailure {
            Log.e(TAG, "Failed to query Mozilla AMO for ${spec.name}", it)
        }

    suspend fun downloadXpi(
        spec: ManagedExtensionSpec,
        release: ManagedExtensionRelease
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "extensions").apply { mkdirs() }
            val safeVer = release.version.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val tempFile = File(dir, "${spec.key}_download.tmp")
            val finalFile = File(dir, "${spec.key}_${safeVer}.xpi")

            // Clean older cached XPIs for this extension to save storage
            dir.listFiles()?.forEach { existing ->
                if (existing.name.startsWith("${spec.key}_") && existing.name != finalFile.name) {
                    runCatching { existing.delete() }
                }
            }

            val request = Request.Builder()
                .url(release.downloadUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed with HTTP ${response.code}")
                }
                val stream = response.body?.byteStream() ?: error("Empty XPI stream")
                tempFile.outputStream().use { out ->
                    stream.copyTo(out)
                }
            }

            if (tempFile.length() < 4_000L) {
                tempFile.delete()
                error("Downloaded ${spec.name} XPI appears incomplete")
            }
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            finalFile
        }.onFailure {
            Log.e(TAG, "Failed to download XPI for ${spec.name}", it)
        }
    }
}
