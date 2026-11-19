package com.example.downloader

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadRecord
import com.example.data.local.DownloadStatus
import com.example.data.local.MediaCategory
import com.example.data.local.NotificationCategory
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.DownloadFormatOption
import com.example.data.model.VideoMetadataPreview
import com.example.notifications.AppNotificationManager
import com.example.service.DownloadForegroundService
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MediaDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val notificationManager: AppNotificationManager,
    private val prefsRepo: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val activeDownloadCount = AtomicInteger(0)
    private val cancelledIds = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var initialized = false

    fun cleanupInterruptedOnStartup() {
        scope.launch {
            runCatching { downloadDao.resetInterruptedDownloads() }
        }
    }

    companion object {
        private const val TAG = "MediaDownloadManager"
    }

    suspend fun ensureInitialized(): Result<Unit> = withContext(Dispatchers.IO) {
        if (initialized) return@withContext Result.success(Unit)
        initMutex.withLock {
            if (initialized) return@withLock Result.success(Unit)
            runCatching {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                initialized = true
                val ver = runCatching { YoutubeDL.getInstance().version(context.applicationContext) }
                    .getOrNull()
                if (!ver.isNullOrBlank()) {
                    prefsRepo.setYtDlpVersion(ver)
                }
            }.onFailure {
                Log.e(TAG, "Failed to initialize yt-dlp / ffmpeg", it)
            }
        }
    }

    private fun prepareCookiesFile(cookiesTxt: String?): File? {
        if (cookiesTxt.isNullOrBlank()) return null
        return runCatching {
            val file = File(context.cacheDir, "yt_session_cookies.txt")
            file.writeText(cookiesTxt)
            file
        }.getOrNull()
    }

    suspend fun probeVideoInfo(url: String, cookiesTxt: String? = null): Result<VideoMetadataPreview> = withContext(Dispatchers.IO) {
        val initRes = ensureInitialized()
        if (initRes.isFailure) {
            return@withContext Result.failure(
                initRes.exceptionOrNull() ?: IllegalStateException("Download engine failed to initialize")
            )
        }
        runCatching {
            val cookiesFile = prepareCookiesFile(cookiesTxt)
            val info = if (cookiesFile != null && cookiesFile.exists()) {
                val req = YoutubeDLRequest(url).apply {
                    addOption("--cookies", cookiesFile.absolutePath)
                }
                YoutubeDL.getInstance().getInfo(req)
            } else {
                YoutubeDL.getInstance().getInfo(url)
            }
            val heights = info.formats
                ?.mapNotNull { it.height.takeIf { h -> h > 0 } }
                ?.distinct()
                ?.sortedDescending()
                ?: listOf(1080, 720, 480, 360)

            VideoMetadataPreview(
                url = url,
                title = info.title ?: "YouTube Video",
                uploader = info.uploader ?: "YouTube Creator",
                durationSeconds = info.duration.toLong().coerceAtLeast(0L),
                thumbnailUrl = info.thumbnail,
                availableHeights = heights.ifEmpty { listOf(1080, 720, 480, 360) }
            )
        }
    }

    fun enqueueDownload(
        url: String,
        formatOption: DownloadFormatOption,
        titleOverride: String? = null,
        thumbnailUrl: String? = null,
        cookiesTxt: String? = null
    ) {
        scope.launch {
            val category = if (formatOption.isAudioOnly) MediaCategory.AUDIO else MediaCategory.VIDEO
            val cleanTitle = (titleOverride?.takeIf { it.isNotBlank() } ?: "YouTube Media")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(80)

            val recordId = downloadDao.insert(
                DownloadRecord(
                    title = cleanTitle,
                    sourceUrl = url,
                    formatLabel = formatOption.label,
                    extension = formatOption.extension,
                    category = category,
                    status = DownloadStatus.QUEUED,
                    thumbnailUrl = thumbnailUrl
                )
            )

            activeDownloadCount.incrementAndGet()
            runCatching {
                context.startForegroundService(
                    Intent(context, DownloadForegroundService::class.java).apply {
                        putExtra(DownloadForegroundService.EXTRA_TITLE, cleanTitle)
                    }
                )
            }.onFailure { err ->
                Log.w(TAG, "Foreground service start skipped; continuing download in worker scope", err)
            }

            val initRes = ensureInitialized()
            if (initRes.isFailure) {
                val msg = initRes.exceptionOrNull()?.message ?: "Engine init failed"
                downloadDao.markFailed(recordId, DownloadStatus.FAILED, msg)
                notificationManager.cancelDownloadNotification(recordId)
                notificationManager.logAndNotify(
                    title = "Download failed",
                    message = "$cleanTitle: $msg",
                    category = NotificationCategory.DOWNLOAD
                )
                stopFgIfDone()
                return@launch
            }

            val workDir = File(context.cacheDir, "ytdl_work_$recordId").apply { mkdirs() }
            val processId = "tubedeck_dl_$recordId"

            try {
                downloadDao.updateProgress(recordId, DownloadStatus.DOWNLOADING, 0, -1L)
                notificationManager.postDownloadProgress(recordId, cleanTitle, 0, indeterminate = true)

                val cookiesFile = prepareCookiesFile(cookiesTxt)
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    if (cookiesFile != null && cookiesFile.exists()) {
                        addOption("--cookies", cookiesFile.absolutePath)
                    }
                    addOption("-o", "${workDir.absolutePath}/%(title).70s.%(ext)s")

                    when (formatOption) {
                        DownloadFormatOption.MP3_320 -> {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "320K")
                            addOption("--embed-thumbnail")
                            addOption("--add-metadata")
                        }
                        DownloadFormatOption.MP3_192 -> {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "192K")
                            addOption("--add-metadata")
                        }
                        DownloadFormatOption.M4A_BEST -> {
                            addOption("-x")
                            addOption("--audio-format", "m4a")
                            addOption("--audio-quality", "0")
                            addOption("--add-metadata")
                        }
                        DownloadFormatOption.OPUS_BEST -> {
                            addOption("-x")
                            addOption("--audio-format", "opus")
                            addOption("--audio-quality", "0")
                        }
                        DownloadFormatOption.FLAC_BEST -> {
                            addOption("-x")
                            addOption("--audio-format", "flac")
                        }
                        DownloadFormatOption.MP4_2160 -> {
                            addOption("-f", "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=2160]+bestaudio/best")
                            addOption("--merge-output-format", "mp4")
                        }
                        DownloadFormatOption.MP4_1080 -> {
                            addOption("-f", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best")
                            addOption("--merge-output-format", "mp4")
                        }
                        DownloadFormatOption.MP4_720 -> {
                            addOption("-f", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best")
                            addOption("--merge-output-format", "mp4")
                        }
                        DownloadFormatOption.MP4_480 -> {
                            addOption("-f", "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best")
                            addOption("--merge-output-format", "mp4")
                        }
                        DownloadFormatOption.MP4_360 -> {
                            addOption("-f", "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360]/best")
                            addOption("--merge-output-format", "mp4")
                        }
                    }
                }

                var lastNotifiedPct = -5
                YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
                    val pct = progress.toInt().coerceIn(0, 100)
                    val status = if (pct >= 99) DownloadStatus.PROCESSING else DownloadStatus.DOWNLOADING
                    scope.launch {
                        downloadDao.updateProgress(recordId, status, pct, etaInSeconds)
                    }
                    if (pct - lastNotifiedPct >= 5 || pct == 100) {
                        lastNotifiedPct = pct
                        notificationManager.postDownloadProgress(recordId, cleanTitle, pct, indeterminate = false)
                    }
                }

                val outputFile = workDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0L }
                    ?.maxByOrNull { it.lastModified() }
                    ?: error("Downloaded output file was not created")

                val exportedUri = exportToMediaStore(
                    sourceFile = outputFile,
                    isAudio = formatOption.isAudioOnly,
                    extension = outputFile.extension.ifBlank { formatOption.extension }
                )

                val internalCopyDir = File(context.filesDir, "downloads").apply { mkdirs() }
                val finalLocalFile = File(internalCopyDir, "${recordId}_${outputFile.name}")
                outputFile.copyTo(finalLocalFile, overwrite = true)

                val size = finalLocalFile.length()
                downloadDao.markCompleted(
                    id = recordId,
                    status = DownloadStatus.COMPLETED,
                    filePath = finalLocalFile.absolutePath,
                    contentUri = exportedUri?.toString(),
                    fileSize = size
                )
                notificationManager.cancelDownloadNotification(recordId)
                notificationManager.logAndNotify(
                    title = "Download complete",
                    message = "${outputFile.nameWithoutExtension} (${formatOption.badge}) saved",
                    category = NotificationCategory.DOWNLOAD,
                    actionTarget = "downloads"
                )
            } catch (e: Exception) {
                if (cancelledIds.remove(recordId)) {
                    Log.i(TAG, "Download $recordId cancelled by user")
                    downloadDao.markFailed(
                        id = recordId,
                        status = DownloadStatus.CANCELLED,
                        error = "Cancelled by user"
                    )
                    notificationManager.cancelDownloadNotification(recordId)
                } else {
                    Log.e(TAG, "Download error", e)
                    downloadDao.markFailed(
                        id = recordId,
                        status = DownloadStatus.FAILED,
                        error = e.message ?: "Download failed"
                    )
                    notificationManager.cancelDownloadNotification(recordId)
                    notificationManager.logAndNotify(
                        title = "Download failed",
                        message = "$cleanTitle: ${e.message ?: "Unknown error"}",
                        category = NotificationCategory.DOWNLOAD
                    )
                }
            } finally {
                cancelledIds.remove(recordId)
                workDir.deleteRecursively()
                stopFgIfDone()
            }
        }
    }

    fun cancelDownload(recordId: Long) {
        cancelledIds.add(recordId)
        scope.launch {
            runCatching { YoutubeDL.getInstance().destroyProcessById("tubedeck_dl_$recordId") }
            downloadDao.markFailed(recordId, DownloadStatus.CANCELLED, "Cancelled by user")
            notificationManager.cancelDownloadNotification(recordId)
        }
    }

    suspend fun deleteDownload(record: DownloadRecord) = withContext(Dispatchers.IO) {
        record.filePath?.let { path ->
            runCatching { File(path).delete() }
        }
        record.contentUri?.let { uriStr ->
            if (uriStr.startsWith("content://")) {
                runCatching { context.contentResolver.delete(Uri.parse(uriStr), null, null) }
            }
        }
        downloadDao.deleteById(record.id)
    }

    suspend fun updateYtDlpEngine(): Result<String> = withContext(Dispatchers.IO) {
        val initRes = ensureInitialized()
        if (initRes.isFailure) {
            return@withContext Result.failure(
                initRes.exceptionOrNull() ?: IllegalStateException("Engine init failed")
            )
        }
        runCatching {
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                context.applicationContext,
                YoutubeDL.UpdateChannel._STABLE
            )
            val version = YoutubeDL.getInstance().version(context.applicationContext) ?: "Latest"
            prefsRepo.setYtDlpVersion(version)
            val message = when (status) {
                YoutubeDL.UpdateStatus.DONE -> "Updated yt-dlp engine to $version"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp engine is up to date ($version)"
                else -> "yt-dlp status: $version"
            }
            notificationManager.logAndNotify(
                title = "Downloader Engine",
                message = message,
                category = NotificationCategory.SYSTEM
            )
            message
        }
    }

    private fun exportToMediaStore(sourceFile: File, isAudio: Boolean, extension: String): Uri? {
        return runCatching {
            val mimeType = when (extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "opus" -> "audio/opus"
                "flac" -> "audio/flac"
                "mp4" -> "video/mp4"
                else -> if (isAudio) "audio/*" else "video/*"
            }
            val collection = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativeDir = if (isAudio) {
                "${Environment.DIRECTORY_MUSIC}/YTubeStudio"
            } else {
                "${Environment.DIRECTORY_MOVIES}/YTubeStudio"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            itemUri
        }.getOrNull()
    }

    private fun stopFgIfDone() {
        val remaining = activeDownloadCount.decrementAndGet().coerceAtLeast(0)
        if (remaining == 0) {
            runCatching {
                context.stopService(Intent(context, DownloadForegroundService::class.java))
            }
        }
    }
}
