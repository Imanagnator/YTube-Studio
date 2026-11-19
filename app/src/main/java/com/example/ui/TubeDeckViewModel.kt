package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.TubeDeckApplication
import com.example.data.local.AppNotificationEntity
import com.example.data.local.CustomDnsEntity
import com.example.data.local.DownloadRecord
import com.example.data.local.DownloadStatus
import com.example.data.local.MediaCategory
import com.example.data.local.NotificationCategory
import com.example.data.local.UserPrefs
import com.example.data.model.ActivePlaybackState
import com.example.data.model.AppTab
import com.example.data.model.DnsBenchmarkResult
import com.example.data.model.DnsProvider
import com.example.data.model.DownloadFormatOption
import com.example.data.model.PlaybackSource
import com.example.data.model.QualityOption
import com.example.data.model.RecordingState
import com.example.data.model.StudioShortcut
import com.example.data.model.VideoProbeUiState
import com.example.gecko.BrowserTabState
import com.example.gecko.GeckoEngineManager
import com.example.gecko.GeckoEvent
import com.example.gecko.LiveQualityState
import com.example.gecko.ManagedExtensionSpec
import com.example.gecko.ManagedExtensionState
import com.example.gecko.ManagedExtensionsCatalog
import com.example.gecko.UBlockOriginUpdater
import com.example.gecko.UBlockStatus
import com.example.network.DnsAndSpeedEngine
import com.example.network.DynamicDnsMonitor
import com.example.network.DynamicDnsStatus
import com.example.service.BackgroundPlaybackService
import com.example.service.OfflineAudioService
import com.example.service.ScreenRecordService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime
import java.io.File

class TubeDeckViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TubeDeckApplication
    val geckoEngine = app.geckoEngine
    private val prefsRepo = app.prefsRepo
    private val dnsEngine = app.dnsAndSpeedEngine
    private val downloadManager = app.downloadManager
    private val notificationManager = app.notificationManager
    private val db = app.database

    private val _selectedTab = MutableStateFlow(AppTab.STREAM)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    val userPrefs: StateFlow<UserPrefs> = prefsRepo.prefsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserPrefs()
    )

    val isEngineReady: StateFlow<Boolean> = geckoEngine.isEngineReady
    val uBlockState: StateFlow<UBlockStatus> = geckoEngine.uBlockState
    val managedExtensions: StateFlow<List<ManagedExtensionState>> = geckoEngine.managedExtensions
    val isFullScreen: StateFlow<Boolean> = geckoEngine.isFullScreen
    val liveQualityState: StateFlow<LiveQualityState> = geckoEngine.liveQualityState
    val streamState: StateFlow<BrowserTabState> = geckoEngine.streamState
    val studioState: StateFlow<BrowserTabState> = geckoEngine.studioState
    val isLoggedInLikely: StateFlow<Boolean> = geckoEngine.isLoggedInLikely
    val recordingState: StateFlow<RecordingState> = ScreenRecordService.recordingState

    val downloads: StateFlow<List<DownloadRecord>> = db.downloadDao().observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val notifications: StateFlow<List<AppNotificationEntity>> = db.notificationDao().observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val unreadNotificationCount: StateFlow<Int> = db.notificationDao().observeUnreadCount().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    val allDnsProviders: StateFlow<List<DnsProvider>> = db.customDnsDao().observeAll()
        .combine(MutableStateFlow(DnsAndSpeedEngine.BUILT_IN_DNS_PROVIDERS)) { customList, builtIn ->
            builtIn + customList.map {
                DnsProvider(
                    id = it.id,
                    name = it.name,
                    primaryIp = it.primaryIp,
                    dohUrl = it.dohUrl,
                    description = "Custom DNS-over-HTTPS resolver",
                    isCustom = true
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DnsAndSpeedEngine.BUILT_IN_DNS_PROVIDERS
        )

    private val _dnsBenchmarks = MutableStateFlow<Map<String, DnsBenchmarkResult>>(emptyMap())
    val dnsBenchmarks: StateFlow<Map<String, DnsBenchmarkResult>> = _dnsBenchmarks.asStateFlow()

    private val _isBenchmarkingDns = MutableStateFlow(false)
    val isBenchmarkingDns: StateFlow<Boolean> = _isBenchmarkingDns.asStateFlow()

    private val _videoProbeState = MutableStateFlow<VideoProbeUiState>(VideoProbeUiState.Idle)
    val videoProbeState: StateFlow<VideoProbeUiState> = _videoProbeState.asStateFlow()

    private val _activePlayback = MutableStateFlow(ActivePlaybackState())
    val activePlayback: StateFlow<ActivePlaybackState> = _activePlayback.asStateFlow()

    private val _showAuthDialog = MutableStateFlow(false)
    val showAuthDialog: StateFlow<Boolean> = _showAuthDialog.asStateFlow()

    private val _showNotificationsSheet = MutableStateFlow(false)
    val showNotificationsSheet: StateFlow<Boolean> = _showNotificationsSheet.asStateFlow()

    private val _showDownloadSheet = MutableStateFlow(false)
    val showDownloadSheet: StateFlow<Boolean> = _showDownloadSheet.asStateFlow()

    private val _downloadSheetUrl = MutableStateFlow(GeckoEngineManager.HOME_STREAM_URL)
    val downloadSheetUrl: StateFlow<String> = _downloadSheetUrl.asStateFlow()

    private val _isUpdatingYtDlp = MutableStateFlow(false)
    val isUpdatingYtDlp: StateFlow<Boolean> = _isUpdatingYtDlp.asStateFlow()

    private val _isUpdatingAllExtensions = MutableStateFlow(false)
    val isUpdatingAllExtensions: StateFlow<Boolean> = _isUpdatingAllExtensions.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _filePickerEvents = MutableSharedFlow<GeckoEvent.FileUploadRequested>(extraBufferCapacity = 8)
    val filePickerEvents = _filePickerEvents.asSharedFlow()

    private var geckoRuntime: GeckoRuntime? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var offlineController: MediaController? = null
    private var offlineTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndAtMs: Long = 0L
    private var probeJob: Job? = null

    private val dynamicDnsMonitor = DynamicDnsMonitor(
        context = app,
        dnsEngine = dnsEngine,
        scope = viewModelScope,
        getProviders = { allDnsProviders.value },
        getActiveDnsId = { userPrefs.value.activeDnsId },
        isAutoDnsEnabled = { userPrefs.value.autoDnsEnabled },
        onBenchmarksUpdated = { results -> _dnsBenchmarks.value = results },
        onSwitchDns = { provider, reason ->
            prefsRepo.setActiveDns(provider.id, provider.name, provider.dohUrl)
            geckoEngine.applyDohSettings(provider.dohUrl)
            notificationManager.logAndNotify(
                title = "Auto-DNS Switched",
                message = reason,
                category = NotificationCategory.NETWORK,
                postSystemNotification = false
            )
            _snackbarEvents.tryEmit("Auto-DNS: ${provider.name}")
        }
    )

    val dynamicDnsStatus: StateFlow<DynamicDnsStatus> = dynamicDnsMonitor.status

    val studioShortcuts = listOf(
        StudioShortcut("dashboard", "Dashboard", "https://studio.youtube.com/"),
        StudioShortcut("upload", "Upload", "https://studio.youtube.com/?upload=1"),
        StudioShortcut("content", "Content", "https://studio.youtube.com/channel/UC/videos/upload"),
        StudioShortcut("analytics", "Analytics", "https://studio.youtube.com/channel/UC/analytics"),
        StudioShortcut("comments", "Comments", "https://studio.youtube.com/channel/UC/comments"),
        StudioShortcut("subtitles", "Subtitles", "https://studio.youtube.com/channel/UC/translations"),
        StudioShortcut("earn", "Earn", "https://studio.youtube.com/channel/UC/monetization")
    )

    init {
        ensureGeckoReadySynchronously()
        downloadManager.cleanupInterruptedOnStartup()
        syncSavedPrefsToGecko()
        observeGeckoEvents()
        observeGeckoMediaSession()
        connectOfflineMediaController()
        dynamicDnsMonitor.start()
        scheduleAutoExtensionUpdate()
    }

    fun ensureGeckoReadySynchronously() {
        val bootDoh = prefsRepo.getBootDohUrlSync()
        geckoRuntime = geckoEngine.ensureInitializedSynchronously(bootDoh)
    }

    fun onAppForegrounded() {
        geckoEngine.onAppForegrounded(isStudioTabActive = _selectedTab.value == AppTab.STUDIO)
    }

    fun onAppBackgrounded() {
        geckoEngine.onAppBackgrounded(userPrefs.value.backgroundAudioEnabled)
    }

    private fun scheduleAutoExtensionUpdate() {
        viewModelScope.launch {
            // Wait 8 seconds after launch so YouTube home & video stream get full bandwidth first
            delay(8_000L)
            val prefs = prefsRepo.prefsFlow.first()
            geckoEngine.applyDisabledExtensionIds(prefs.disabledExtensionIds)

            // 1. Always auto-install any enabled extension that isn't installed yet in GeckoView
            val rt = geckoRuntime
            if (rt != null) {
                val missingSpecs = managedExtensions.value
                    .filter { !it.isInstalled && !prefs.disabledExtensionIds.contains(it.spec.id) }
                    .map { it.spec }
                for (spec in missingSpecs) {
                    installOrUpdateSingleExtensionInternal(rt, spec, silent = true, forceInstallIfMissing = true)
                    delay(1_200L)
                }
            }

            // 2. Full 24h automatic update check for ALL 7 extensions + yt-dlp nightly engine
            if (!prefs.autoUpdateExtensions) return@launch
            val now = System.currentTimeMillis()
            val twentyFourHoursMs = 24L * 60L * 60L * 1000L
            if (prefs.lastExtensionCheckMs > 0L && (now - prefs.lastExtensionCheckMs) < twentyFourHoursMs) {
                return@launch
            }
            updateAllExtensionsAndEngines(silent = true)
        }
    }

    private fun syncSavedPrefsToGecko() {
        viewModelScope.launch {
            val prefs = prefsRepo.prefsFlow.first()
            geckoEngine.applyDohSettings(prefs.activeDohUrl)
            geckoEngine.applyDisabledExtensionIds(prefs.disabledExtensionIds)
            geckoEngine.updateExtensionSettings(
                adBlockEnabled = prefs.adBlockEnabled,
                backgroundAudioEnabled = prefs.backgroundAudioEnabled,
                preferredQuality = prefs.preferredQuality,
                studioZoomPercent = prefs.studioDesktopZoomPercent
            )
        }
    }

    fun getRuntime(): GeckoRuntime? = geckoRuntime

    private fun observeGeckoEvents() {
        viewModelScope.launch {
            geckoEngine.events.collect { event ->
                when (event) {
                    is GeckoEvent.AdBlocked -> {
                        prefsRepo.incrementAdsBlocked(event.countDelta)
                    }
                    is GeckoEvent.BlockedExternalNavigation -> {
                        _snackbarEvents.tryEmit("External link blocked (locked to YouTube)")
                    }
                    is GeckoEvent.OpenVideoInMobileStream -> {
                        geckoEngine.loadStreamUrl(event.mobileUrl)
                        selectTab(AppTab.STREAM)
                        _snackbarEvents.tryEmit("Opened video in Mobile YouTube")
                    }
                    is GeckoEvent.OpenUrlInDesktopStudio -> {
                        geckoEngine.loadStudioUrl(event.studioUrl)
                        selectTab(AppTab.STUDIO)
                    }
                    is GeckoEvent.InPageDownloadRequested -> {
                        _downloadSheetUrl.value = event.url
                        _showDownloadSheet.value = true
                        probeVideoUrl(event.url, event.cookiesTxt.ifBlank { geckoEngine.latestCookiesTxt })
                    }
                    is GeckoEvent.LoginCompleted -> {
                        _showAuthDialog.value = false
                        geckoEngine.closeAuthSession()
                        geckoEngine.streamSession.reload()
                        if (geckoEngine.studioSession.isOpen) {
                            geckoEngine.studioSession.reload()
                        }
                        geckoEngine.requestCookiesRefresh()
                        notificationManager.logAndNotify(
                            title = "Google Account Signed In",
                            message = "YouTube & YT Studio sessions are synced.",
                            category = NotificationCategory.SYSTEM
                        )
                    }
                    is GeckoEvent.FileUploadRequested -> {
                        _filePickerEvents.tryEmit(event)
                    }
                }
            }
        }
    }

    private fun observeGeckoMediaSession() {
        viewModelScope.launch {
            geckoEngine.mediaState.collect { media ->
                // Only mirror GeckoView media when offline Media3 player is not actively playing
                if (_activePlayback.value.source != PlaybackSource.OFFLINE_MEDIA) {
                    if (media.isActive) {
                        _activePlayback.value = ActivePlaybackState(
                            source = PlaybackSource.GECKO_STREAM,
                            title = media.title,
                            subtitle = media.artist,
                            artworkUrl = media.artworkUrl,
                            isPlaying = media.isPlaying,
                            positionMs = (media.positionSec * 1000).toLong(),
                            durationMs = (media.durationSec * 1000).toLong()
                        )
                        if (userPrefs.value.backgroundAudioEnabled && media.isPlaying) {
                            runCatching {
                                ContextCompat.startForegroundService(
                                    app,
                                    Intent(app, BackgroundPlaybackService::class.java).apply {
                                        action = BackgroundPlaybackService.ACTION_START
                                    }
                                )
                            }
                        }
                    } else if (_activePlayback.value.source == PlaybackSource.GECKO_STREAM) {
                        _activePlayback.value = ActivePlaybackState()
                    }
                }
            }
        }
    }

    private fun connectOfflineMediaController() {
        val token = SessionToken(app, ComponentName(app, OfflineAudioService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            offlineController = controller
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncOfflinePlaybackState(controller)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    syncOfflinePlaybackState(controller)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    syncOfflinePlaybackState(controller)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    syncOfflinePlaybackState(controller)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    syncOfflinePlaybackState(controller)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    syncOfflinePlaybackState(controller)
                }
            })
        }, ContextCompat.getMainExecutor(app))
    }

    private fun resolveRecordUri(record: DownloadRecord): Uri? {
        return record.filePath?.let { path ->
            val file = File(path)
            if (file.exists()) Uri.fromFile(file) else null
        } ?: record.contentUri?.let { Uri.parse(it) }
    }

    private fun buildMediaItem(record: DownloadRecord, uri: Uri): MediaItem {
        val extras = android.os.Bundle().apply {
            putBoolean("isVideo", record.category != MediaCategory.AUDIO)
        }
        return MediaItem.Builder()
            .setMediaId(record.id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(record.title)
                    .setArtist("${record.formatLabel} • Offline")
                    .setArtworkUri(record.thumbnailUrl?.let { Uri.parse(it) })
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun syncOfflinePlaybackState(controller: MediaController) {
        val item = controller.currentMediaItem
        if (item == null || controller.playbackState == Player.STATE_IDLE) {
            offlineTickerJob?.cancel()
            if (_activePlayback.value.source == PlaybackSource.OFFLINE_MEDIA) {
                _activePlayback.value = ActivePlaybackState()
            }
            return
        }
        val meta = item.mediaMetadata
        val mediaId = item.mediaId.toLongOrNull()
        val matchedRecord = downloads.value.firstOrNull { it.id == mediaId }
        val isVideo = meta.extras?.getBoolean("isVideo", false)
            ?: (matchedRecord != null && matchedRecord.category != MediaCategory.AUDIO)
        val vSize = controller.videoSize
        val sleepRem = (sleepTimerEndAtMs - System.currentTimeMillis()).coerceAtLeast(0L)

        _activePlayback.update {
            ActivePlaybackState(
                source = PlaybackSource.OFFLINE_MEDIA,
                title = meta.title?.toString() ?: "Downloaded Media",
                subtitle = meta.artist?.toString() ?: "YTube Studio Offline Player",
                artworkUrl = meta.artworkUri?.toString(),
                isPlaying = controller.isPlaying,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
                playbackSpeed = controller.playbackParameters.speed,
                mediaItemId = mediaId,
                isVideo = isVideo,
                videoWidth = vSize.width,
                videoHeight = vSize.height,
                hasNext = controller.hasNextMediaItem(),
                hasPrevious = controller.hasPreviousMediaItem(),
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode,
                queueIndex = controller.currentMediaItemIndex,
                queueSize = controller.mediaItemCount,
                sleepTimerRemainingMs = sleepRem
            )
        }
        if (controller.isPlaying) {
            startOfflineTicker(controller)
        } else {
            offlineTickerJob?.cancel()
        }
    }

    private fun startOfflineTicker(controller: MediaController) {
        offlineTickerJob?.cancel()
        offlineTickerJob = viewModelScope.launch {
            while (controller.isPlaying) {
                val sleepRem = (sleepTimerEndAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                _activePlayback.update {
                    it.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = controller.duration.coerceAtLeast(0L),
                        sleepTimerRemainingMs = sleepRem
                    )
                }
                delay(400L)
            }
        }
    }

    fun playDownloadedMedia(record: DownloadRecord) {
        val controller = offlineController ?: return
        val completedPool = downloads.value.filter { it.status == DownloadStatus.COMPLETED }
        val sameTypeQueue = completedPool.filter {
            if (record.category == MediaCategory.AUDIO) it.category == MediaCategory.AUDIO
            else it.category != MediaCategory.AUDIO
        }
        val playableList = (if (sameTypeQueue.any { it.id == record.id }) sameTypeQueue else listOf(record))
            .mapNotNull { rec -> resolveRecordUri(rec)?.let { uri -> rec to buildMediaItem(rec, uri) } }

        val startIndex = playableList.indexOfFirst { it.first.id == record.id }
        if (startIndex < 0) {
            _snackbarEvents.tryEmit("Media file not found on disk")
            return
        }

        // Pause GeckoView live stream so audio doesn't overlap
        geckoEngine.pauseLiveMedia()
        runCatching {
            app.stopService(Intent(app, BackgroundPlaybackService::class.java))
        }

        controller.setMediaItems(playableList.map { it.second }, startIndex, 0L)
        controller.prepare()
        controller.play()
        syncOfflinePlaybackState(controller)
    }

    fun playAllAudioDownloads() {
        val audioTracks = downloads.value.filter {
            it.status == DownloadStatus.COMPLETED && it.category == MediaCategory.AUDIO
        }
        val first = audioTracks.firstOrNull()
        if (first == null) {
            _snackbarEvents.tryEmit("No completed audio files yet")
            return
        }
        playDownloadedMedia(first)
    }

    fun toggleActivePlayPause() {
        when (_activePlayback.value.source) {
            PlaybackSource.GECKO_STREAM -> geckoEngine.playPauseLiveMedia()
            PlaybackSource.OFFLINE_MEDIA -> {
                offlineController?.let { c ->
                    if (c.isPlaying) c.pause() else c.play()
                }
            }
            PlaybackSource.NONE -> Unit
        }
    }

    fun seekOfflineTo(positionMs: Long) {
        if (_activePlayback.value.source == PlaybackSource.OFFLINE_MEDIA) {
            val target = positionMs.coerceAtLeast(0L)
            offlineController?.seekTo(target)
            _activePlayback.update { it.copy(positionMs = target) }
        }
    }

    fun seekOfflineBy(deltaMs: Long) {
        val controller = offlineController ?: return
        if (_activePlayback.value.source != PlaybackSource.OFFLINE_MEDIA) return
        val maxDur = controller.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, maxDur)
        controller.seekTo(target)
        _activePlayback.update { it.copy(positionMs = target) }
    }

    fun skipOfflineNext() {
        val controller = offlineController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        }
    }

    fun skipOfflinePrevious() {
        val controller = offlineController ?: return
        if (controller.currentPosition > 3500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun toggleOfflineShuffle() {
        val controller = offlineController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        syncOfflinePlaybackState(controller)
    }

    fun cycleOfflineRepeatMode() {
        val controller = offlineController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncOfflinePlaybackState(controller)
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            sleepTimerEndAtMs = 0L
            _activePlayback.update { it.copy(sleepTimerRemainingMs = 0L) }
            _snackbarEvents.tryEmit("Sleep timer turned off")
            return
        }
        val durationMs = minutes * 60_000L
        sleepTimerEndAtMs = System.currentTimeMillis() + durationMs
        _activePlayback.update { it.copy(sleepTimerRemainingMs = durationMs) }
        _snackbarEvents.tryEmit("Sleep timer set for $minutes min")

        sleepTimerJob = viewModelScope.launch {
            while (true) {
                val rem = sleepTimerEndAtMs - System.currentTimeMillis()
                if (rem <= 0L) {
                    sleepTimerEndAtMs = 0L
                    _activePlayback.update { it.copy(sleepTimerRemainingMs = 0L) }
                    when (_activePlayback.value.source) {
                        PlaybackSource.OFFLINE_MEDIA -> offlineController?.pause()
                        PlaybackSource.GECKO_STREAM -> geckoEngine.pauseLiveMedia()
                        PlaybackSource.NONE -> Unit
                    }
                    _snackbarEvents.tryEmit("Sleep timer paused playback")
                    break
                }
                _activePlayback.update { it.copy(sleepTimerRemainingMs = rem) }
                delay(1000L)
            }
        }
    }

    fun cycleOfflinePlaybackSpeed() {
        val controller = offlineController ?: return
        if (_activePlayback.value.source != PlaybackSource.OFFLINE_MEDIA) return
        val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 0.75f)
        val current = controller.playbackParameters.speed
        val next = speeds[(speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f } + 1) % speeds.size]
        controller.setPlaybackSpeed(next)
        _activePlayback.update { it.copy(playbackSpeed = next) }
    }

    fun stopActivePlayback() {
        sleepTimerJob?.cancel()
        sleepTimerEndAtMs = 0L
        when (_activePlayback.value.source) {
            PlaybackSource.GECKO_STREAM -> {
                geckoEngine.stopLiveMedia()
                app.stopService(Intent(app, BackgroundPlaybackService::class.java))
            }
            PlaybackSource.OFFLINE_MEDIA -> {
                offlineController?.stop()
                offlineController?.clearMediaItems()
            }
            PlaybackSource.NONE -> Unit
        }
        _activePlayback.value = ActivePlaybackState()
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        geckoEngine.onActiveTabChanged(
            isStreamTab = tab == AppTab.STREAM,
            isStudioTab = tab == AppTab.STUDIO,
            backgroundAudioEnabled = userPrefs.value.backgroundAudioEnabled
        )
    }

    fun navigateStreamBack() {
        if (streamState.value.canGoBack) geckoEngine.streamSession.goBack()
    }

    fun navigateStreamForward() {
        if (streamState.value.canGoForward) geckoEngine.streamSession.goForward()
    }

    fun reloadStream() = geckoEngine.streamSession.reload()
    fun loadStreamHome() = geckoEngine.loadStreamUrl(GeckoEngineManager.HOME_STREAM_URL)

    fun navigateStudioBack() {
        if (studioState.value.canGoBack) geckoEngine.studioSession.goBack()
    }

    fun reloadStudio() = geckoEngine.studioSession.reload()

    fun openStudioShortcut(shortcut: StudioShortcut) {
        val current = studioState.value.currentUrl
        val channelId = Regex("studio\\.youtube\\.com/channel/([^/?#]+)")
            .find(current)
            ?.groupValues
            ?.getOrNull(1)

        val targetUrl = if (!channelId.isNullOrBlank()) {
            when (shortcut.id) {
                "dashboard" -> "https://studio.youtube.com/channel/$channelId"
                "upload" -> "https://studio.youtube.com/channel/$channelId/videos/upload?d=ud"
                "content" -> "https://studio.youtube.com/channel/$channelId/videos/upload"
                "analytics" -> "https://studio.youtube.com/channel/$channelId/analytics"
                "comments" -> "https://studio.youtube.com/channel/$channelId/comments"
                "subtitles" -> "https://studio.youtube.com/channel/$channelId/translations"
                "earn" -> "https://studio.youtube.com/channel/$channelId/monetization"
                else -> shortcut.url
            }
        } else {
            when (shortcut.id) {
                "upload" -> "https://studio.youtube.com/?upload=1"
                else -> "https://studio.youtube.com/"
            }
        }
        geckoEngine.loadStudioUrl(targetUrl)
    }

    fun setStudioZoomPercent(percent: Int) {
        viewModelScope.launch {
            prefsRepo.setStudioZoomPercent(percent)
            geckoEngine.applyStudioZoom(percent)
        }
    }

    fun toggleKeepScreenAwakeUpload(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setKeepScreenAwakeUpload(enabled) }
    }

    fun toggleAdBlock(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAdBlockEnabled(enabled)
            geckoEngine.updateExtensionSettings(
                adBlockEnabled = enabled,
                backgroundAudioEnabled = userPrefs.value.backgroundAudioEnabled,
                preferredQuality = userPrefs.value.preferredQuality
            )
            _snackbarEvents.tryEmit(if (enabled) "AdBlock Shield enabled" else "AdBlock Shield paused")
        }
    }

    fun toggleBackgroundAudio(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setBackgroundAudioEnabled(enabled)
            geckoEngine.updateExtensionSettings(
                adBlockEnabled = userPrefs.value.adBlockEnabled,
                backgroundAudioEnabled = enabled,
                preferredQuality = userPrefs.value.preferredQuality
            )
            if (!enabled) {
                app.stopService(Intent(app, BackgroundPlaybackService::class.java))
            }
        }
    }

    fun exitFullScreen() {
        geckoEngine.exitFullScreen()
    }

    fun requestQualityRefresh() {
        geckoEngine.requestQualityStateRefresh()
    }

    fun setPreferredQuality(quality: QualityOption) {
        viewModelScope.launch {
            prefsRepo.setPreferredQuality(quality)
            geckoEngine.applyManualQuality(quality)
            _snackbarEvents.tryEmit("Applying ${quality.label} resolution...")
        }
    }

    fun toggleAutoDns(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoDnsEnabled(enabled)
            if (enabled) {
                dynamicDnsMonitor.triggerImmediateOptimization("Auto-DNS enabled")
                _snackbarEvents.tryEmit("Dynamic Auto-DNS enabled")
            } else {
                _snackbarEvents.tryEmit("Manual DNS mode active")
            }
        }
    }

    fun toggleDarkTheme(dark: Boolean) {
        viewModelScope.launch { prefsRepo.setDarkThemeEnabled(dark) }
    }

    fun openInAppGoogleLogin() {
        _showAuthDialog.value = true
    }

    fun closeInAppGoogleLogin() {
        _showAuthDialog.value = false
        geckoEngine.closeAuthSession()
    }

    fun toggleAutoUpdateExtensions(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoUpdateExtensions(enabled)
            _snackbarEvents.tryEmit(
                if (enabled) "Auto-update enabled for all extensions & yt-dlp"
                else "Auto-update paused"
            )
            if (enabled) {
                updateAllExtensionsAndEngines(silent = true)
            }
        }
    }

    fun toggleManagedExtension(extensionId: String, enabled: Boolean) {
        if (extensionId == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
            toggleAdBlock(enabled)
            return
        }
        viewModelScope.launch {
            prefsRepo.setExtensionEnabled(extensionId, enabled)
            geckoEngine.setManagedExtensionEnabled(extensionId, enabled)
            val spec = ManagedExtensionsCatalog.findById(extensionId) ?: return@launch
            val currentState = managedExtensions.value.firstOrNull { it.spec.id == extensionId }
            if (enabled && currentState?.isInstalled != true) {
                val rt = geckoRuntime ?: return@launch
                installOrUpdateSingleExtensionInternal(rt, spec, silent = false, forceInstallIfMissing = true)
            } else {
                _snackbarEvents.tryEmit(
                    if (enabled) "${spec.name} enabled" else "${spec.name} paused"
                )
            }
        }
    }

    fun updateSingleExtension(extensionId: String) {
        val rt = geckoRuntime ?: return
        val spec = ManagedExtensionsCatalog.findById(extensionId) ?: return
        viewModelScope.launch {
            installOrUpdateSingleExtensionInternal(rt, spec, silent = false, forceInstallIfMissing = true)
        }
    }

    fun updateAllExtensionsAndEngines(silent: Boolean = false) {
        if (_isUpdatingAllExtensions.value) return
        val rt = geckoRuntime ?: return
        viewModelScope.launch {
            _isUpdatingAllExtensions.value = true
            var updatedCount = 0
            try {
                val disabledIds = userPrefs.value.disabledExtensionIds
                for (spec in ManagedExtensionsCatalog.ALL_SPECS) {
                    val isEnabled = if (spec.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                        userPrefs.value.adBlockEnabled
                    } else {
                        !disabledIds.contains(spec.id)
                    }
                    if (!isEnabled && managedExtensions.value.firstOrNull { it.spec.id == spec.id }?.isInstalled != true) {
                        continue
                    }
                    val didUpdate = installOrUpdateSingleExtensionInternal(
                        rt = rt,
                        spec = spec,
                        silent = true,
                        forceInstallIfMissing = isEnabled
                    )
                    if (didUpdate) updatedCount++
                }
                prefsRepo.setLastExtensionCheckMs(System.currentTimeMillis())

                // Also update yt-dlp nightly engine cleanly
                val ytdlpRes = downloadManager.updateYtDlpEngine(silent = silent)
                val ytdlpUpdated = ytdlpRes.getOrNull()?.startsWith("Updated", ignoreCase = true) == true
                if (ytdlpUpdated) updatedCount++

                if (!silent) {
                    val msg = if (updatedCount > 0) {
                        "Updated $updatedCount extension/engine component(s)"
                    } else {
                        "All 7 extensions & yt-dlp engine are up to date"
                    }
                    _snackbarEvents.tryEmit(msg)
                } else if (updatedCount > 0) {
                    notificationManager.logAndNotify(
                        title = "Auto-Update Complete",
                        message = "Updated $updatedCount extension/engine package(s) automatically.",
                        category = NotificationCategory.SYSTEM,
                        postSystemNotification = false
                    )
                }
            } finally {
                _isUpdatingAllExtensions.value = false
            }
        }
    }

    private suspend fun installOrUpdateSingleExtensionInternal(
        rt: GeckoRuntime,
        spec: ManagedExtensionSpec,
        silent: Boolean,
        forceInstallIfMissing: Boolean
    ): Boolean {
        val currentState = managedExtensions.value.firstOrNull { it.spec.id == spec.id }
        if (currentState?.isUpdating == true) return false
        val alreadyInstalled = currentState?.isInstalled == true
        if (!alreadyInstalled && !forceInstallIfMissing) return false

        geckoEngine.setManagedExtensionUpdating(
            spec.id,
            true,
            if (alreadyInstalled) "Checking latest ${spec.name}..." else "Fetching ${spec.name} from Mozilla AMO..."
        )

        val releaseRes = geckoEngine.extensionUpdater.fetchLatestRelease(spec)
        if (releaseRes.isFailure) {
            val msg = releaseRes.exceptionOrNull()?.message ?: "Update check failed"
            geckoEngine.setManagedExtensionUpdating(
                spec.id,
                false,
                if (alreadyInstalled) "v${currentState?.version} active" else "Check failed: $msg"
            )
            if (!silent) _snackbarEvents.tryEmit("${spec.name} check failed: $msg")
            return false
        }

        val release = releaseRes.getOrThrow()
        val currentVer = currentState?.version.orEmpty()
        val needsInstall = !alreadyInstalled || UBlockOriginUpdater.isNewerVersion(release.version, currentVer)
        if (!needsInstall) {
            geckoEngine.setManagedExtensionUpdating(spec.id, false, "v$currentVer up to date (${release.sourceLabel})")
            if (!silent) _snackbarEvents.tryEmit("${spec.name} v$currentVer is already latest")
            return false
        }

        geckoEngine.setManagedExtensionUpdating(spec.id, true, "Downloading v${release.version}...")
        val xpiRes = geckoEngine.extensionUpdater.downloadXpi(spec, release)
        if (xpiRes.isFailure) {
            val msg = xpiRes.exceptionOrNull()?.message ?: "Download failed"
            geckoEngine.setManagedExtensionUpdating(
                spec.id,
                false,
                if (alreadyInstalled) "v$currentVer active" else "Download failed: $msg"
            )
            if (!silent) _snackbarEvents.tryEmit("${spec.name} download failed: $msg")
            return false
        }

        val xpiFile = xpiRes.getOrThrow()
        geckoEngine.setManagedExtensionUpdating(spec.id, true, "Installing v${release.version}...")
        val enableAfter = if (spec.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
            userPrefs.value.adBlockEnabled
        } else {
            !userPrefs.value.disabledExtensionIds.contains(spec.id)
        }

        val installDeferred = kotlinx.coroutines.CompletableDeferred<Result<String>>()
        geckoEngine.installManagedExtensionFromXpi(
            rt = rt,
            spec = spec,
            xpiFile = xpiFile,
            expectedVersion = release.version,
            enableAfterInstall = enableAfter
        ) { res ->
            installDeferred.complete(res)
        }

        val installRes = installDeferred.await()
        return installRes.fold(
            onSuccess = { installedVer ->
                if (spec.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                    prefsRepo.setUBlockVersion(installedVer)
                }
                if (!silent) {
                    _snackbarEvents.tryEmit("${spec.name} updated to v$installedVer")
                }
                true
            },
            onFailure = { err ->
                if (!silent) _snackbarEvents.tryEmit("${spec.name} install error: ${err.message}")
                false
            }
        )
    }

    fun clearBrowserSessions() {
        geckoEngine.clearAllSessionCookies()
        geckoEngine.streamSession.loadUri(GeckoEngineManager.HOME_STREAM_URL)
        if (geckoEngine.studioSession.isOpen) {
            geckoEngine.studioSession.loadUri(GeckoEngineManager.HOME_STUDIO_URL)
        }
        _snackbarEvents.tryEmit("YouTube & Studio session data cleared")
    }

    fun benchmarkAllDns() {
        if (_isBenchmarkingDns.value) return
        viewModelScope.launch {
            _isBenchmarkingDns.value = true
            val results = dnsEngine.benchmarkAllProviders(allDnsProviders.value)
            _dnsBenchmarks.value = results
            _isBenchmarkingDns.value = false
            val fastest = results.values.filter { it.reachable }.minByOrNull { it.latencyMs }
            if (fastest != null) {
                val name = allDnsProviders.value.find { it.id == fastest.providerId }?.name ?: fastest.providerId
                notificationManager.logAndNotify(
                    title = "DNS Benchmark Complete",
                    message = "Fastest: $name (${fastest.latencyMs} ms)",
                    category = NotificationCategory.NETWORK,
                    postSystemNotification = false
                )
            }
        }
    }

    fun connectDns(provider: DnsProvider) {
        viewModelScope.launch {
            prefsRepo.setActiveDns(provider.id, provider.name, provider.dohUrl)
            geckoEngine.applyDohSettings(provider.dohUrl)
            _snackbarEvents.tryEmit("Connected DNS: ${provider.name}")
            notificationManager.logAndNotify(
                title = "DNS Connected",
                message = "Active resolver switched to ${provider.name}",
                category = NotificationCategory.NETWORK,
                postSystemNotification = false
            )
        }
    }

    fun connectFastestDns() {
        viewModelScope.launch {
            if (_dnsBenchmarks.value.isEmpty()) {
                _isBenchmarkingDns.value = true
                _dnsBenchmarks.value = dnsEngine.benchmarkAllProviders(allDnsProviders.value)
                _isBenchmarkingDns.value = false
            }
            val fastest = _dnsBenchmarks.value.values.filter { it.reachable }.minByOrNull { it.latencyMs }
            val provider = allDnsProviders.value.find { it.id == fastest?.providerId }
            if (provider != null) {
                connectDns(provider)
            } else {
                _snackbarEvents.tryEmit("No reachable DNS found — check network")
            }
        }
    }

    fun addCustomDns(name: String, primaryIp: String, dohUrl: String) {
        viewModelScope.launch {
            val cleanDoh = dohUrl.trim()
            if (!cleanDoh.startsWith("https://")) {
                _snackbarEvents.tryEmit("Custom DoH URL must start with https://")
                return@launch
            }
            val entity = CustomDnsEntity(
                id = "custom_${System.currentTimeMillis()}",
                name = name.trim().ifBlank { "Custom DoH" },
                primaryIp = primaryIp.trim().ifBlank { "DoH" },
                dohUrl = cleanDoh
            )
            db.customDnsDao().insert(entity)
            _snackbarEvents.tryEmit("Added custom DNS: ${entity.name}")
        }
    }

    fun deleteCustomDns(id: String) {
        viewModelScope.launch { db.customDnsDao().deleteById(id) }
    }

    fun openDownloadSheetForCurrentStream() {
        val url = streamState.value.currentUrl.ifBlank { GeckoEngineManager.HOME_STREAM_URL }
        geckoEngine.requestCookiesRefresh()
        _downloadSheetUrl.value = url
        _showDownloadSheet.value = true
        probeVideoUrl(url, geckoEngine.latestCookiesTxt)
    }

    fun closeDownloadSheet() {
        _showDownloadSheet.value = false
    }

    fun probeVideoUrl(url: String, cookiesTxt: String? = geckoEngine.latestCookiesTxt) {
        if (url.isBlank()) return
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            _videoProbeState.value = VideoProbeUiState.Loading(url)
            val result = downloadManager.probeVideoInfo(url, cookiesTxt)
            _videoProbeState.value = result.fold(
                onSuccess = { VideoProbeUiState.Ready(it) },
                onFailure = {
                    VideoProbeUiState.Error(
                        message = it.message ?: "Could not inspect this YouTube URL",
                        url = url
                    )
                }
            )
        }
    }

    fun startDownload(url: String, format: DownloadFormatOption, title: String?, thumbnailUrl: String?) {
        downloadManager.enqueueDownload(
            url = url,
            formatOption = format,
            titleOverride = title,
            thumbnailUrl = thumbnailUrl,
            cookiesTxt = geckoEngine.latestCookiesTxt
        )
        _showDownloadSheet.value = false
        _snackbarEvents.tryEmit("Queued ${format.badge} download")
    }

    fun cancelDownload(recordId: Long) {
        downloadManager.cancelDownload(recordId)
    }

    fun retryDownload(record: DownloadRecord) {
        if (record.sourceUrl.isBlank() || record.sourceUrl.startsWith("screen://")) return
        val matchedFormat = DownloadFormatOption.entries.firstOrNull {
            it.label.equals(record.formatLabel, ignoreCase = true)
        } ?: if (record.category == MediaCategory.AUDIO) {
            DownloadFormatOption.MP3_320
        } else {
            DownloadFormatOption.MP4_1080
        }
        viewModelScope.launch {
            downloadManager.deleteDownload(record)
            downloadManager.enqueueDownload(
                url = record.sourceUrl,
                formatOption = matchedFormat,
                titleOverride = record.title,
                thumbnailUrl = record.thumbnailUrl,
                cookiesTxt = geckoEngine.latestCookiesTxt
            )
            _snackbarEvents.tryEmit("Retrying ${matchedFormat.badge} download")
        }
    }

    fun deleteDownload(record: DownloadRecord) {
        if (_activePlayback.value.mediaItemId == record.id) {
            stopActivePlayback()
        }
        viewModelScope.launch { downloadManager.deleteDownload(record) }
    }

    fun updateYtDlpEngine() {
        if (_isUpdatingYtDlp.value) return
        viewModelScope.launch {
            _isUpdatingYtDlp.value = true
            val res = downloadManager.updateYtDlpEngine()
            _isUpdatingYtDlp.value = false
            res.fold(
                onSuccess = { _snackbarEvents.tryEmit(it) },
                onFailure = { _snackbarEvents.tryEmit("yt-dlp update failed: ${it.message}") }
            )
        }
    }

    fun updateUBlockOriginFromGitHub(silent: Boolean = false) {
        if (uBlockState.value.isUpdating) return
        val rt = geckoRuntime ?: return
        viewModelScope.launch {
            if (!silent) {
                geckoEngine.setUBlockUpdating(true, "Checking gorhill/uBlock on GitHub...")
            } else {
                geckoEngine.setUBlockUpdating(true, uBlockState.value.statusDetail)
            }
            val releaseRes = geckoEngine.uBlockUpdater.fetchLatestGitHubRelease()
            if (releaseRes.isFailure) {
                val msg = releaseRes.exceptionOrNull()?.message ?: "GitHub check failed"
                geckoEngine.setUBlockUpdating(
                    false,
                    if (silent) "uBlock Origin v${uBlockState.value.version} active" else "GitHub check failed: $msg"
                )
                if (!silent) _snackbarEvents.tryEmit("uBlock GitHub check failed: $msg")
                return@launch
            }

            prefsRepo.setLastExtensionCheckMs(System.currentTimeMillis())
            val release = releaseRes.getOrThrow()
            val currentVer = uBlockState.value.version
            val hasUpdate = !uBlockState.value.isInstalled ||
                UBlockOriginUpdater.isNewerVersion(release.version, currentVer)

            if (!hasUpdate) {
                geckoEngine.setUBlockUpdating(false, "uBlock Origin v$currentVer is up to date")
                if (!silent) _snackbarEvents.tryEmit("uBlock Origin v$currentVer is already up to date")
                return@launch
            }

            geckoEngine.setUBlockUpdating(true, "Downloading uBlock Origin v${release.version} from GitHub...")
            val downloadRes = geckoEngine.uBlockUpdater.downloadXpiRelease(release)
            if (downloadRes.isFailure) {
                val msg = downloadRes.exceptionOrNull()?.message ?: "Download failed"
                geckoEngine.setUBlockUpdating(
                    false,
                    if (silent) "uBlock Origin v$currentVer active" else "Download failed: $msg"
                )
                if (!silent) _snackbarEvents.tryEmit("uBlock download failed: $msg")
                return@launch
            }

            val xpiFile = downloadRes.getOrThrow()
            geckoEngine.setUBlockUpdating(true, "Installing uBlock Origin v${release.version}...")
            geckoEngine.installUBlockFromXpiFile(rt, xpiFile, release.version) { installRes ->
                viewModelScope.launch {
                    installRes.fold(
                        onSuccess = { installedVer ->
                            prefsRepo.setUBlockVersion(installedVer)
                            _snackbarEvents.tryEmit("uBlock Origin auto-updated to v$installedVer")
                            notificationManager.logAndNotify(
                                title = "uBlock Origin Updated",
                                message = "Installed v$installedVer from gorhill/uBlock GitHub releases",
                                category = NotificationCategory.SYSTEM,
                                postSystemNotification = !silent
                            )
                        },
                        onFailure = { err ->
                            if (!silent) _snackbarEvents.tryEmit("uBlock install error: ${err.message}")
                        }
                    )
                }
            }
        }
    }

    fun openNotificationsSheet() {
        _showNotificationsSheet.value = true
        viewModelScope.launch { db.notificationDao().markAllRead() }
    }

    fun closeNotificationsSheet() {
        _showNotificationsSheet.value = false
    }

    fun clearAllNotifications() {
        viewModelScope.launch { db.notificationDao().clearAll() }
    }

    fun notifyRecordingStarted() {
        notificationManager.logAndNotify(
            title = "Screen Recording Active",
            message = "Capturing screen video. Tap Stop in the top bar or notification anytime.",
            category = NotificationCategory.RECORDING,
            postSystemNotification = false
        )
    }

    override fun onCleared() {
        dynamicDnsMonitor.stop()
        sleepTimerJob?.cancel()
        offlineTickerJob?.cancel()
        probeJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
