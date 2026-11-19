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

data class UBlockReleaseInfo(
    val version: String,
    val assetName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

class UBlockOriginUpdater(private val context: Context) {

    companion object {
        private const val TAG = "UBlockOriginUpdater"
        const val UBLOCK_EXTENSION_ID = "uBlock0@raymondhill.net"
        const val BUNDLED_ASSET_PATH = "extensions/ublock_origin.xpi"
        const val BUNDLED_VERSION = "1.76.4"
        private const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/gorhill/uBlock/releases/latest"

        fun isNewerVersion(remote: String, installed: String): Boolean {
            val rParts = remote.removePrefix("v").split(".", "-", "_").mapNotNull { it.toIntOrNull() }
            val iParts = installed.removePrefix("v").split(".", "-", "_").mapNotNull { it.toIntOrNull() }
            if (rParts.isEmpty() || iParts.isEmpty()) return remote != installed
            val maxLen = maxOf(rParts.size, iParts.size)
            for (idx in 0 until maxLen) {
                val r = rParts.getOrElse(idx) { 0 }
                val i = iParts.getOrElse(idx) { 0 }
                if (r != i) return r > i
            }
            return false
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun extractBundledXpi(): File {
        val dir = File(context.filesDir, "extensions").apply { mkdirs() }
        val target = File(dir, "ublock_origin_bundled.xpi")
        if (!target.exists() || target.length() == 0L) {
            context.assets.open(BUNDLED_ASSET_PATH).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return target
    }

    suspend fun fetchLatestGitHubRelease(): Result<UBlockReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "YTubeStudio-Android/1.0.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("GitHub API returned HTTP ${response.code}")
                }
                val body = response.body?.string() ?: error("Empty GitHub release response")
                val json = JSONObject(body)
                val tagName = json.optString("tag_name").trim().removePrefix("v")
                val releaseNotes = json.optString("body", "").take(220)
                val assets = json.optJSONArray("assets") ?: error("No release assets found")

                var chosenName: String? = null
                var chosenUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".firefox.signed.xpi", ignoreCase = true) && url.startsWith("https://")) {
                        chosenName = name
                        chosenUrl = url
                        break
                    }
                }
                // Fallback to any .xpi asset if signed naming changes
                if (chosenUrl == null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        if (name.endsWith(".xpi", ignoreCase = true) && url.startsWith("https://")) {
                            chosenName = name
                            chosenUrl = url
                            break
                        }
                    }
                }

                if (chosenUrl.isNullOrBlank() || chosenName.isNullOrBlank()) {
                    error("No Firefox .xpi package found in gorhill/uBlock release $tagName")
                }

                UBlockReleaseInfo(
                    version = tagName.ifBlank { "latest" },
                    assetName = chosenName,
                    downloadUrl = chosenUrl,
                    releaseNotes = releaseNotes
                )
            }
        }.onFailure {
            Log.e(TAG, "Failed to query gorhill/uBlock GitHub release", it)
        }
    }

    suspend fun downloadXpiRelease(release: UBlockReleaseInfo): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "extensions").apply { mkdirs() }
            val tempFile = File(dir, "ublock_download.tmp")
            val finalFile = File(dir, "ublock_${release.version.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.xpi")

            val request = Request.Builder()
                .url(release.downloadUrl)
                .header("User-Agent", "YTubeStudio-Android/1.0.0")
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

            if (tempFile.length() < 50_000L) {
                tempFile.delete()
                error("Downloaded uBlock XPI appears incomplete")
            }
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            finalFile
        }.onFailure {
            Log.e(TAG, "Failed to download uBlock Origin XPI", it)
        }
    }
}
