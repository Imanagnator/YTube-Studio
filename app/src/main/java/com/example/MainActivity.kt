package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DownloadStatus
import com.example.data.model.AppTab
import com.example.data.model.PlaybackSource
import com.example.gecko.GeckoEngineManager
import com.example.service.ScreenRecordService
import com.example.ui.TubeDeckViewModel
import com.example.ui.components.DownloadPickerBottomSheet
import com.example.ui.components.InAppGoogleLoginDialog
import com.example.ui.components.MiniAudioPlayerBar
import com.example.ui.components.NotificationsBottomSheet
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.StreamScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.TubeDeckTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TubeDeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Synchronously initialize GeckoRuntime & sessions BEFORE Compose attaches any GeckoView
        viewModel.ensureGeckoReadySynchronously()

        setContent {
            val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
            TubeDeckTheme(darkTheme = prefs.darkThemeEnabled) {
                TubeDeckAppRoot(
                    viewModel = viewModel,
                    onKeepScreenAwake = { awake ->
                        if (awake) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    },
                    onFullScreenChanged = { fullScreen ->
                        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        if (fullScreen) {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            insetsController.hide(WindowInsetsCompat.Type.systemBars())
                        } else {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            insetsController.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }
}

@Composable
fun TubeDeckAppRoot(
    viewModel: TubeDeckViewModel,
    onKeepScreenAwake: (Boolean) -> Unit,
    onFullScreenChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isEngineReady by viewModel.isEngineReady.collectAsStateWithLifecycle()
    val uBlockState by viewModel.uBlockState.collectAsStateWithLifecycle()
    val managedExtensions by viewModel.managedExtensions.collectAsStateWithLifecycle()
    val isUpdatingAllExtensions by viewModel.isUpdatingAllExtensions.collectAsStateWithLifecycle()
    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
    val liveQualityState by viewModel.liveQualityState.collectAsStateWithLifecycle()
    val dynamicDnsStatus by viewModel.dynamicDnsStatus.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedInLikely.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val activePlayback by viewModel.activePlayback.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val dnsProviders by viewModel.allDnsProviders.collectAsStateWithLifecycle()
    val dnsBenchmarks by viewModel.dnsBenchmarks.collectAsStateWithLifecycle()
    val isBenchmarkingDns by viewModel.isBenchmarkingDns.collectAsStateWithLifecycle()
    val videoProbeState by viewModel.videoProbeState.collectAsStateWithLifecycle()
    val downloadSheetUrl by viewModel.downloadSheetUrl.collectAsStateWithLifecycle()
    val showAuthDialog by viewModel.showAuthDialog.collectAsStateWithLifecycle()
    val authDialogUrl by viewModel.authDialogUrl.collectAsStateWithLifecycle()
    val authDialogTitle by viewModel.authDialogTitle.collectAsStateWithLifecycle()
    val showNotificationsSheet by viewModel.showNotificationsSheet.collectAsStateWithLifecycle()
    val showDownloadSheet by viewModel.showDownloadSheet.collectAsStateWithLifecycle()
    val isUpdatingYtDlp by viewModel.isUpdatingYtDlp.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // Pending screen recording config while waiting for MediaProjection / Mic permission
    var pendingRec1080p by remember { mutableStateOf(true) }
    var pendingRecWithMic by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val width = if (pendingRec1080p) 1080 else 720
            val height = if (pendingRec1080p) 1920 else 1280
            val startIntent = Intent(context, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenRecordService.EXTRA_WIDTH, width)
                putExtra(ScreenRecordService.EXTRA_HEIGHT, height)
                putExtra(ScreenRecordService.EXTRA_MIC_AUDIO, pendingRecWithMic)
            }
            ContextCompat.startForegroundService(context, startIntent)
            viewModel.notifyRecordingStarted()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingRecWithMic = granted
        val projManager = context.getSystemService(MediaProjectionManager::class.java)
        mediaProjectionLauncher.launch(projManager.createScreenCaptureIntent())
    }

    fun triggerScreenRecordToggle(res1080p: Boolean, withMic: Boolean) {
        if (recordingState.isRecording) {
            context.startService(
                Intent(context, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP)
            )
            return
        }
        pendingRec1080p = res1080p
        pendingRecWithMic = withMic
        if (withMic && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            val projManager = context.getSystemService(MediaProjectionManager::class.java)
            mediaProjectionLauncher.launch(projManager.createScreenCaptureIntent())
        }
    }

    // Native SAF File Picker for YouTube Studio video/thumbnail/subtitle uploads
    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        viewModel.geckoEngine.deliverFilePickerResult(uri?.let { arrayOf(it) })
    }

    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        viewModel.geckoEngine.deliverFilePickerResult(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            if (msg.startsWith("Downloading ") || msg.startsWith("Queued ") || msg.startsWith("Retrying ")) {
                val res = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = "View",
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    viewModel.selectTab(AppTab.PROFILE)
                }
            } else {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.filePickerEvents.collect { req ->
            val primaryMimes = req.mimeTypes.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
            val launched = runCatching {
                if (req.allowMultiple) {
                    multiFilePickerLauncher.launch(primaryMimes)
                } else {
                    singleFilePickerLauncher.launch(primaryMimes)
                }
            }.recoverCatching {
                // Retry with wildcard "*/*" if OEM picker rejects specific MIME filters
                if (req.allowMultiple) {
                    multiFilePickerLauncher.launch(arrayOf("*/*"))
                } else {
                    singleFilePickerLauncher.launch(arrayOf("*/*"))
                }
            }
            if (launched.isFailure) {
                viewModel.geckoEngine.deliverFilePickerResult(null)
            }
        }
    }

    // Keep screen awake during fullscreen video playback or Studio uploads
    DisposableEffect(selectedTab, prefs.keepScreenAwakeUpload, isFullScreen) {
        onKeepScreenAwake(isFullScreen || (selectedTab == AppTab.STUDIO && prefs.keepScreenAwakeUpload))
        onDispose { onKeepScreenAwake(false) }
    }

    // Automatic sensor-landscape + immersive system bars when entering/exiting video fullscreen
    DisposableEffect(isFullScreen) {
        onFullScreenChanged(isFullScreen)
        onDispose {
            if (isFullScreen) onFullScreenChanged(false)
        }
    }

    // Exit fullscreen video first on Back press
    BackHandler(enabled = isFullScreen) {
        viewModel.exitFullScreen()
    }

    // Proper BackHandler for custom tab & in-session web history navigation
    BackHandler(
        enabled = !isFullScreen && when (selectedTab) {
            AppTab.STREAM -> streamState.canGoBack
            AppTab.STUDIO -> studioState.canGoBack
            AppTab.PROFILE -> true
        }
    ) {
        when (selectedTab) {
            AppTab.STREAM -> viewModel.navigateStreamBack()
            AppTab.STUDIO -> viewModel.navigateStudioBack()
            AppTab.PROFILE -> viewModel.selectTab(AppTab.STREAM)
        }
    }

    val activeDownloadsCount = remember(downloads) {
        downloads.count {
            it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.PROCESSING
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = if (isFullScreen) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail && !isFullScreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        if (selectedTab != AppTab.PROFILE || activePlayback.source != PlaybackSource.OFFLINE_MEDIA) {
                            MiniAudioPlayerBar(
                                playback = activePlayback,
                                onTogglePlayPause = viewModel::toggleActivePlayPause,
                                onCycleSpeed = viewModel::cycleOfflinePlaybackSpeed,
                                onStop = viewModel::stopActivePlayback
                            )
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = selectedTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        val icon = when (tab) {
                                            AppTab.STREAM -> if (selected) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircle
                                            AppTab.STUDIO -> if (selected) Icons.Filled.VideoSettings else Icons.Outlined.VideoSettings
                                            AppTab.PROFILE -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
                                        }
                                        if (tab == AppTab.PROFILE && activeDownloadsCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(containerColor = SignalCrimson) {
                                                        Text(activeDownloadsCount.coerceAtMost(99).toString())
                                                    }
                                                }
                                            ) {
                                                Icon(icon, contentDescription = tab.label)
                                            }
                                        } else {
                                            Icon(icon, contentDescription = tab.label)
                                        }
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SignalCrimson,
                                        selectedTextColor = SignalCrimson,
                                        indicatorColor = SignalCrimson.copy(alpha = 0.14f)
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !isFullScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    val icon = when (tab) {
                                        AppTab.STREAM -> if (selected) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircle
                                        AppTab.STUDIO -> if (selected) Icons.Filled.VideoSettings else Icons.Outlined.VideoSettings
                                        AppTab.PROFILE -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
                                    }
                                    if (tab == AppTab.PROFILE && activeDownloadsCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = SignalCrimson) {
                                                    Text(activeDownloadsCount.coerceAtMost(99).toString())
                                                }
                                            }
                                        ) {
                                            Icon(icon, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (useNavRail && !isFullScreen && (selectedTab != AppTab.PROFILE || activePlayback.source != PlaybackSource.OFFLINE_MEDIA)) {
                        MiniAudioPlayerBar(
                            playback = activePlayback,
                            onTogglePlayPause = viewModel::toggleActivePlayPause,
                            onCycleSpeed = viewModel::cycleOfflinePlaybackSpeed,
                            onStop = viewModel::stopActivePlayback
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            AppTab.STREAM -> {
                                StreamScreen(
                                    session = viewModel.geckoEngine.streamSession,
                                    isEngineReady = isEngineReady,
                                    isFullScreen = isFullScreen,
                                    tabState = streamState,
                                    prefs = prefs,
                                    uBlockStatus = uBlockState,
                                    liveQualityState = liveQualityState,
                                    recordingState = recordingState,
                                    unreadNotifications = unreadCount,
                                    onBack = viewModel::navigateStreamBack,
                                    onForward = viewModel::navigateStreamForward,
                                    onHome = viewModel::loadStreamHome,
                                    onReload = viewModel::reloadStream,
                                    onToggleAdBlock = viewModel::toggleAdBlock,
                                    onOpenQualityMenu = viewModel::requestQualityRefresh,
                                    onSelectQuality = viewModel::setPreferredQuality,
                                    onToggleBackgroundAudio = viewModel::toggleBackgroundAudio,
                                    onToggleRecording = { triggerScreenRecordToggle(res1080p = true, withMic = false) },
                                    onOpenDownloadSheet = viewModel::openDownloadSheetForCurrentStream,
                                    onOpenNotifications = viewModel::openNotificationsSheet
                                )
                            }

                            AppTab.STUDIO -> {
                                StudioScreen(
                                    session = viewModel.geckoEngine.studioSession,
                                    isEngineReady = isEngineReady,
                                    isFullScreen = isFullScreen,
                                    tabState = studioState,
                                    prefs = prefs,
                                    shortcuts = viewModel.studioShortcuts,
                                    unreadNotifications = unreadCount,
                                    onBack = viewModel::navigateStudioBack,
                                    onReload = viewModel::reloadStudio,
                                    onSelectShortcut = viewModel::openStudioShortcut,
                                    onToggleKeepAwake = viewModel::toggleKeepScreenAwakeUpload,
                                    onOpenNotifications = viewModel::openNotificationsSheet
                                )
                            }

                            AppTab.PROFILE -> {
                                ProfileScreen(
                                    prefs = prefs,
                                    isLoggedIn = isLoggedIn,
                                    unreadNotifications = unreadCount,
                                    activePlayback = activePlayback,
                                    dnsProviders = dnsProviders,
                                    dnsBenchmarks = dnsBenchmarks,
                                    dynamicDnsStatus = dynamicDnsStatus,
                                    isBenchmarkingDns = isBenchmarkingDns,
                                    downloads = downloads,
                                    recordingState = recordingState,
                                    uBlockStatus = uBlockState,
                                    managedExtensions = managedExtensions,
                                    isUpdatingAllExtensions = isUpdatingAllExtensions,
                                    isUpdatingYtDlp = isUpdatingYtDlp,
                                    hasNotificationPermission = hasNotifPermission,
                                    onRequestNotificationPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    onOpenNotifications = viewModel::openNotificationsSheet,
                                    onInAppSignIn = viewModel::openInAppGoogleLogin,
                                    onClearSessions = viewModel::clearBrowserSessions,
                                    onToggleAutoDns = viewModel::toggleAutoDns,
                                    onBenchmarkAllDns = viewModel::benchmarkAllDns,
                                    onConnectDns = viewModel::connectDns,
                                    onConnectFastestDns = viewModel::connectFastestDns,
                                    onAddCustomDns = viewModel::addCustomDns,
                                    onDeleteCustomDns = viewModel::deleteCustomDns,
                                    onPlayDownload = viewModel::playDownloadedMedia,
                                    onPlayAllAudio = viewModel::playAllAudioDownloads,
                                    onTogglePlayPause = viewModel::toggleActivePlayPause,
                                    onSeekTo = viewModel::seekOfflineTo,
                                    onSeekBy = viewModel::seekOfflineBy,
                                    onSkipNext = viewModel::skipOfflineNext,
                                    onSkipPrevious = viewModel::skipOfflinePrevious,
                                    onToggleShuffle = viewModel::toggleOfflineShuffle,
                                    onCycleRepeat = viewModel::cycleOfflineRepeatMode,
                                    onCycleSpeed = viewModel::cycleOfflinePlaybackSpeed,
                                    onSetSleepTimer = viewModel::setSleepTimerMinutes,
                                    onStopPlayback = viewModel::stopActivePlayback,
                                    onCancelDownload = viewModel::cancelDownload,
                                    onRetryDownload = viewModel::retryDownload,
                                    onDeleteDownload = viewModel::deleteDownload,
                                    onStartOrStopRecording = { res1080p, mic ->
                                        triggerScreenRecordToggle(res1080p, mic)
                                    },
                                    onToggleAdBlock = viewModel::toggleAdBlock,
                                    onToggleAutoUpdateExtensions = viewModel::toggleAutoUpdateExtensions,
                                    onUpdateAllExtensions = { viewModel.updateAllExtensionsAndEngines(silent = false) },
                                    onToggleManagedExtension = viewModel::toggleManagedExtension,
                                    onUpdateSingleExtension = viewModel::updateSingleExtension,
                                    onToggleBackgroundAudio = viewModel::toggleBackgroundAudio,
                                    onSelectDefaultQuality = viewModel::setPreferredQuality,
                                    onToggleDarkTheme = viewModel::toggleDarkTheme,
                                    onUpdateYtDlp = viewModel::updateYtDlpEngine
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadSheet) {
        val currentUrl = downloadSheetUrl.ifBlank { GeckoEngineManager.HOME_STREAM_URL }
        DownloadPickerBottomSheet(
            probeState = videoProbeState,
            initialUrl = currentUrl,
            onDismiss = viewModel::closeDownloadSheet,
            onRetryProbe = { url -> viewModel.probeVideoUrl(url) },
            onStartDownload = viewModel::startDownload
        )
    }

    if (showNotificationsSheet) {
        NotificationsBottomSheet(
            notifications = notifications,
            onDismiss = viewModel::closeNotificationsSheet,
            onClearAll = viewModel::clearAllNotifications
        )
    }

    if (showAuthDialog) {
        val rt = viewModel.getRuntime()
        if (rt != null) {
            val authSession = remember(rt, authDialogUrl) {
                viewModel.geckoEngine.getOrCreateAuthSession(rt, authDialogUrl)
            }
            InAppGoogleLoginDialog(
                authSession = authSession,
                title = authDialogTitle,
                targetUrl = authDialogUrl,
                onDismiss = viewModel::closeInAppGoogleLogin
            )
        }
    }
}
