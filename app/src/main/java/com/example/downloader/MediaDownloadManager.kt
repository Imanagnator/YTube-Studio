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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private data class ExtractionStrategy(
    val label: String,
    val playerClients: String,
    val useCookies: Boolean
)

class MediaDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val notificationManager: AppNotificationManager,
    private val prefsRepo: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val updateMutex = Mutex()
    private val activeDownloadCount = AtomicInteger(0)
    private val cancelledIds = ConcurrentHashMap.newKeySet<Long>()
    private val autoUpdatedThisSession = AtomicBoolean(false)

    @Volatile
    private var initialized = false

    fun cleanupInterruptedOnStartup() {
        scope.launch {
            runCatching { downloadDao.resetInterruptedDownloads() }
        }
    }

    companion object {
        private const val TAG = "MediaDownloadManager"
        private val VIDEO_ID_REGEX = Regex(
            "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|live/|embed/)|youtu\\.be/)([A-Za-z0-9_-]{11})"
        )

        fun normalizeYouTubeUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            val match = VIDEO_ID_REGEX.find(trimmed)
            val videoId = match?.groupValues?.getOrNull(1)
            return if (!videoId.isNullOrBlank()) {
                "https://www.youtube.com/watch?v=$videoId"
            } else {
                trimmed
            }
        }

        fun extractCleanError(error: Throwable?): String {
            val raw = error?.message?.trim().orEmpty()
            if (raw.isBlank()) return "Download failed — tap Update Engine in Shield & App"
            val errorLine = raw.lineSequence()
                .map { it.trim() }
                .lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
            val cleaned = (errorLine ?: raw.lineSequence().firstOrNull { it.isNotBlank() } ?: raw)
                .removePrefix("ERROR:")
                .trim()
            return cleaned.take(200)
        }
    }

    suspend fun ensureInitialized(): Result<Unit> = withContext(Dispatchers.IO) {
        if (initialized) return@withContext Result.success(Unit)
        initMutex.withLock {
            if (initialized) return@withLock Result.success(Unit)
            runCatching {
                YoutubeDL.getInstance().init(context.applicationContext)
                runCatching { FFmpeg.getInstance().init(context.applicationContext) }
                    .onFailure { Log.w(TAG, "FFmpeg init warning (non-fatal for direct streams)", it) }
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
        val hasValidCookieLines = cookiesTxt.lineSequence().any {
            val line = it.trim()
            line.isNotEmpty() && !line.startsWith("#") && line.contains('\t')
        }
        if (!hasValidCookieLines) return null
        return runCatching {
            val file = File(context.cacheDir, "yt_session_cookies.txt")
            file.writeText(cookiesTxt)
            file
        }.getOrNull()
    }

    private fun buildStrategies(hasCookies: Boolean): List<ExtractionStrategy> = buildList {
        // 1. Android VR + Mobile Web + TV clients bypass YouTube's Deno/JS-runtime requirement
        add(ExtractionStrategy("android_vr/mweb/tv", "android_vr,mweb,tv", useCookies = false))
        // 2. Authenticated Mobile/TV/Web-Embedded when signed in (for age-gated / bot-checked videos)
        if (hasCookies) {
            add(ExtractionStrategy("cookies+mweb/tv", "mweb,tv,web_embedded", useCookies = true))
        }
        // 3. iOS + Android + Web Embedded clean fallback
        add(ExtractionStrategy("ios/android/web_embedded", "ios,android,web_embedded", useCookies = false))
        // 4. Default yt-dlp client negotiation (benefits from latest nightly updates)
        add(ExtractionStrategy("default", "default,-web_creator", useCookies = hasCookies))
    }

    private fun applyCommonOptions(
        request: YoutubeDLRequest,
        strategy: ExtractionStrategy,
        cookiesFile: File?
    ) {
        request.addOption("--no-playlist")
        request.addOption("--no-check-certificates")
        request.addOption("--force-ipv4")
        request.addOption("--geo-bypass")
        request.addOption("--socket-timeout", "20")
        request.addOption("--retries", "3")
        request.addOption("--fragment-retries", "3")
        request.addOption("--extractor-args", "youtube:player_client=${strategy.playerClients}")
        if (strategy.useCookies && cookiesFile != null && cookiesFile.exists()) {
            request.addOption("--cookies", cookiesFile.absolutePath)
        }
    }

    private suspend fun trySelfHealUpdateOnce(): Boolean {
        if (!autoUpdatedThisSession.compareAndSet(false, true)) return false
        return updateMutex.withLock {
            runCatching {
                Log.i(TAG, "yt-dlp extraction failed; attempting automatic nightly engine update...")
                val status = runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        context.applicationContext,
                        YoutubeDL.UpdateChannel._NIGHTLY
                    )
                }.getOrElse {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        context.applicationContext,
                        YoutubeDL.UpdateChannel._STABLE
                    )
                }
                val ver = YoutubeDL.getInstance().version(context.applicationContext)
                if (!ver.isNullOrBlank()) {
                    prefsRepo.setYtDlpVersion(ver)
                }
                status == YoutubeDL.UpdateStatus.DONE
            }.getOrDefault(false)
        }
    }

    suspend fun probeVideoInfo(url: String, cookiesTxt: String? = null): Result<VideoMetadataPreview> = withContext(Dispatchers.IO) {
        val initRes = ensureInitialized()
        if (initRes.isFailure) {
            return@withContext Result.failure(
                IllegalStateException(extractCleanError(initRes.exceptionOrNull()))
            )
        }

        val cleanUrl = normalizeYouTubeUrl(url)
        val cookiesFile = prepareCookiesFile(cookiesTxt)
        val strategies = buildStrategies(hasCookies = cookiesFile != null)

        suspend fun runProbeStrategies(): Result<VideoMetadataPreview> {
            var lastError: Throwable? = null
            for (strategy in strategies) {
                val attempt = runCatching {
                    val req = YoutubeDLRequest(cleanUrl).apply {
                        applyCommonOptions(this, strategy, cookiesFile)
                    }
                    val info = YoutubeDL.getInstance().getInfo(req)
                    val heights = info.formats
                        ?.mapNotNull { it.height.takeIf { h -> h > 0 } }
                        ?.distinct()
                        ?.sortedDescending()
                        ?: listOf(1080, 720, 480, 360)

                    VideoMetadataPreview(
                        url = cleanUrl,
                        title = info.title ?: "YouTube Video",
                        uploader = info.uploader ?: "YouTube Creator",
                        durationSeconds = info.duration.toLong().coerceAtLeast(0L),
                        thumbnailUrl = info.thumbnail,
                        availableHeights = heights.ifEmpty { listOf(1080, 720, 480, 360) }
                    )
                }
                if (attempt.isSuccess) return attempt
                lastError = attempt.exceptionOrNull()
                Log.w(TAG, "Probe strategy [${strategy.label}] failed: ${extractCleanError(lastError)}")
            }
            return Result.failure(IllegalStateException(extractCleanError(lastError)))
        }

        val firstPass = runProbeStrategies()
        if (firstPass.isSuccess) return@withContext firstPass

        // Self-heal: auto-update yt-dlp to latest nightly once and retry cleanly
        if (trySelfHealUpdateOnce()) {
            val secondPass = runProbeStrategies()
            if (secondPass.isSuccess) return@withContext secondPass
            return@withContext secondPass
        }
        firstPass
    }

    fun enqueueDownload(
        url: String,
        formatOption: DownloadFormatOption,
        titleOverride: String? = null,
        thumbnailUrl: String? = null,
        cookiesTxt: String? = null
    ) {
        scope.launch {
            val cleanUrl = normalizeYouTubeUrl(url)
            val category = if (formatOption.isAudioOnly) MediaCategory.AUDIO else MediaCategory.VIDEO
            val cleanTitle = (titleOverride?.takeIf { it.isNotBlank() } ?: "YouTube Media")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(80)

            val recordId = downloadDao.insert(
                DownloadRecord(
                    title = cleanTitle,
                    sourceUrl = cleanUrl,
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
                val msg = extractCleanError(initRes.exceptionOrNull())
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
                val strategies = buildStrategies(hasCookies = cookiesFile != null)

                fun buildDownloadRequest(strategy: ExtractionStrategy): YoutubeDLRequest {
                    return YoutubeDLRequest(cleanUrl).apply {
                        applyCommonOptions(this, strategy, cookiesFile)
                        addOption("--no-mtime")
                        addOption("--no-part")
                        addOption("-o", "${workDir.absolutePath}/%(title).70s.%(ext)s")

                        when (formatOption) {
                            DownloadFormatOption.MP3_320 -> {
                                addOption("-f", "bestaudio/best")
                                addOption("-x")
                                addOption("--audio-format", "mp3")
                                addOption("--audio-quality", "320K")
                                addOption("--add-metadata")
                            }
                            DownloadFormatOption.MP3_192 -> {
                                addOption("-f", "bestaudio/best")
                                addOption("-x")
                                addOption("--audio-format", "mp3")
                                addOption("--audio-quality", "192K")
                                addOption("--add-metadata")
                            }
                            DownloadFormatOption.M4A_BEST -> {
                                addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
                                addOption("-x")
                                addOption("--audio-format", "m4a")
                                addOption("--audio-quality", "0")
                                addOption("--add-metadata")
                            }
                            DownloadFormatOption.OPUS_BEST -> {
                                addOption("-f", "bestaudio[ext=webm]/bestaudio/best")
                                addOption("-x")
                                addOption("--audio-format", "opus")
                                addOption("--audio-quality", "0")
                            }
                            DownloadFormatOption.FLAC_BEST -> {
                                addOption("-f", "bestaudio/best")
                                addOption("-x")
                                addOption("--audio-format", "flac")
                            }
                            DownloadFormatOption.MP4_2160 -> {
                                addOption("-f", "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=2160]+bestaudio/best[height<=2160]/best")
                                addOption("--merge-output-format", "mp4")
                            }
                            DownloadFormatOption.MP4_1080 -> {
                                addOption("-f", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
                                addOption("--merge-output-format", "mp4")
                            }
                            DownloadFormatOption.MP4_720 -> {
                                addOption("-f", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]/best")
                                addOption("--merge-output-format", "mp4")
                            }
                            DownloadFormatOption.MP4_480 -> {
                                addOption("-f", "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480]/best")
                                addOption("--merge-output-format", "mp4")
                            }
                            DownloadFormatOption.MP4_360 -> {
                                addOption("-f", "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360]/best")
                                addOption("--merge-output-format", "mp4")
                            }
                        }
                    }
                }

                suspend fun runDownloadPass(): Throwable? {
                    var lastErr: Throwable? = null
                    for (strategy in strategies) {
                        if (cancelledIds.contains(recordId)) return IllegalStateException("Cancelled")
                        workDir.listFiles()?.forEach { it.deleteRecursively() }
                        var lastNotifiedPct = -5
                        val attempt = runCatching {
                            val req = buildDownloadRequest(strategy)
                            YoutubeDL.getInstance().execute(req, processId) { progress, etaInSeconds, _ ->
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
                        }
                        if (attempt.isSuccess) {
                            val hasFile = workDir.listFiles()?.any { it.isFile && it.length() > 0L } == true
                            if (hasFile) return null
                            lastErr = IllegalStateException("Downloaded file was empty")
                        } else {
                            lastErr = attempt.exceptionOrNull()
                            if (cancelledIds.contains(recordId)) return lastErr
                            Log.w(TAG, "Download strategy [${strategy.label}] failed: ${extractCleanError(lastErr)}")
                        }
                    }
                    return lastErr
                }

                var failure = runDownloadPass()
                if (failure != null && !cancelledIds.contains(recordId)) {
                    if (trySelfHealUpdateOnce()) {
                        failure = runDownloadPass()
                    }
                }
                if (failure != null) throw failure

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
                    val cleanErr = extractCleanError(e)
                    Log.e(TAG, "Download error: $cleanErr", e)
                    downloadDao.markFailed(
                        id = recordId,
                        status = DownloadStatus.FAILED,
                        error = cleanErr
                    )
                    notificationManager.cancelDownloadNotification(recordId)
                    notificationManager.logAndNotify(
                        title = "Download failed",
                        message = "$cleanTitle: $cleanErr",
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

    suspend fun updateYtDlpEngine(silent: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        val initRes = ensureInitialized()
        if (initRes.isFailure) {
            return@withContext Result.failure(
                initRes.exceptionOrNull() ?: IllegalStateException("Engine init failed")
            )
        }
        updateMutex.withLock {
            runCatching {
                val status = runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        context.applicationContext,
                        YoutubeDL.UpdateChannel._NIGHTLY
                    )
                }.getOrElse {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        context.applicationContext,
                        YoutubeDL.UpdateChannel._STABLE
                    )
                }
                val version = YoutubeDL.getInstance().version(context.applicationContext) ?: "Latest"
                prefsRepo.setYtDlpVersion(version)
                autoUpdatedThisSession.set(true)
                val message = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "Updated yt-dlp nightly engine to $version"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp engine is up to date ($version)"
                    else -> "yt-dlp status: $version"
                }
                if (!silent || status == YoutubeDL.UpdateStatus.DONE) {
                    notificationManager.logAndNotify(
                        title = "Downloader Engine",
                        message = message,
                        category = NotificationCategory.SYSTEM,
                        postSystemNotification = !silent
                    )
                }
                message
            }
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
