package com.example.gecko

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.QualityOption
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebRequestError
import java.io.File

data class BrowserTabState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val loadProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

data class GeckoMediaMetadataState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "YouTube Audio",
    val artist: String = "YTube Studio Stream",
    val artworkUrl: String? = null,
    val durationSec: Double = 0.0,
    val positionSec: Double = 0.0
)

data class UBlockStatus(
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = true,
    val version: String = UBlockOriginUpdater.BUNDLED_VERSION,
    val isUpdating: Boolean = false,
    val statusDetail: String = "Initializing uBlock Origin..."
)

data class LiveQualityState(
    val currentQuality: String = "Auto",
    val resolvedQuality: String = "",
    val availableQualities: List<String> = emptyList(),
    val videoUrl: String = ""
)

sealed interface GeckoEvent {
    data class BlockedExternalNavigation(val url: String) : GeckoEvent
    data class AdBlocked(val countDelta: Long) : GeckoEvent
    data class LoginCompleted(val url: String) : GeckoEvent
    data class OpenVideoInMobileStream(val mobileUrl: String) : GeckoEvent
    data class OpenUrlInDesktopStudio(val studioUrl: String) : GeckoEvent
    data class InPageDownloadRequested(val url: String, val title: String, val cookiesTxt: String) : GeckoEvent
    data class FileUploadRequested(
        val mimeTypes: Array<String>,
        val allowMultiple: Boolean,
        val onResult: (Array<Uri>?) -> Unit
    ) : GeckoEvent
}

class GeckoEngineManager(private val context: Context) {

    companion object {
        private const val TAG = "GeckoEngineManager"
        const val HOME_STREAM_URL = "https://m.youtube.com/"
        const val HOME_STUDIO_URL = "https://studio.youtube.com/"
        const val GOOGLE_LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://m.youtube.com/"

        // Strict Desktop PC Firefox User-Agent for studio.youtube.com
        private const val DESKTOP_FIREFOX_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"

        private val ALLOWED_EXACT_OR_SUBDOMAIN_HOSTS = setOf(
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "googlevideo.com",
            "ytimg.com",
            "ggpht.com",
            "accounts.google.com",
            "accounts.youtube.com",
            "myaccount.google.com",
            "gds.google.com",
            "consent.youtube.com",
            "consent.google.com",
            "policies.google.com",
            "gstatic.com",
            "googleusercontent.com"
        )

        private val STUDIO_AUTH_OR_INFRA_HOSTS = setOf(
            "studio.youtube.com",
            "accounts.google.com",
            "accounts.youtube.com",
            "myaccount.google.com",
            "gds.google.com",
            "consent.youtube.com",
            "consent.google.com",
            "policies.google.com",
            "gstatic.com",
            "googleusercontent.com",
            "googlevideo.com",
            "ytimg.com",
            "ggpht.com"
        )
    }

    val uBlockUpdater = UBlockOriginUpdater(context.applicationContext)

    @Volatile
    private var runtime: GeckoRuntime? = null
    private var nativePort: WebExtension.Port? = null
    private var uBlockExtension: WebExtension? = null
    private var pendingFileCallback: ((Array<Uri>?) -> Unit)? = null

    val streamSession: GeckoSession by lazy { createStreamSession() }
    val studioSession: GeckoSession by lazy { createStudioSession() }
    private var authSession: GeckoSession? = null

    private var activeMediaSession: MediaSession? = null

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    private val _streamState = MutableStateFlow(
        BrowserTabState(currentUrl = HOME_STREAM_URL, pageTitle = "YouTube")
    )
    val streamState: StateFlow<BrowserTabState> = _streamState.asStateFlow()

    private val _studioState = MutableStateFlow(
        BrowserTabState(currentUrl = HOME_STUDIO_URL, pageTitle = "YouTube Studio")
    )
    val studioState: StateFlow<BrowserTabState> = _studioState.asStateFlow()

    private val _mediaState = MutableStateFlow(GeckoMediaMetadataState())
    val mediaState: StateFlow<GeckoMediaMetadataState> = _mediaState.asStateFlow()

    private val _uBlockState = MutableStateFlow(UBlockStatus())
    val uBlockState: StateFlow<UBlockStatus> = _uBlockState.asStateFlow()

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _liveQualityState = MutableStateFlow(LiveQualityState())
    val liveQualityState: StateFlow<LiveQualityState> = _liveQualityState.asStateFlow()

    @Volatile
    var latestCookiesTxt: String = ""
        private set

    private val _isLoggedInLikely = MutableStateFlow(false)
    val isLoggedInLikely: StateFlow<Boolean> = _isLoggedInLikely.asStateFlow()

    private val _events = MutableSharedFlow<GeckoEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    private var cachedAdBlockEnabled = true
    private var cachedBgAudioEnabled = true
    private var cachedQuality = QualityOption.AUTO
    private var cachedStudioZoomPercent = 85

    /**
     * Must be invoked synchronously on the Main Thread (in the main UI process) BEFORE any
     * GeckoView.setSession() call so GeckoAppShell.getApplicationContext() is guaranteed non-null
     * and SessionAccessibility static initialization never throws ExceptionInInitializerError.
     */
    fun ensureInitializedSynchronously(initialDohUrl: String): GeckoRuntime {
        val rt = getOrCreateRuntime(initialDohUrl)
        // Open only the active Stream session at boot; Studio loads lazily on demand to halve cold-start load time
        openStreamHome(rt)
        _isEngineReady.value = true
        return rt
    }

    fun ensureStudioOpened() {
        val rt = runtime ?: return
        openStudioHome(rt)
    }

    fun getOrCreateRuntime(initialDohUrl: String): GeckoRuntime {
        runtime?.let { return it }

        // Balanced content blocking: prevents post-login Google/YouTube cookie stalls & avoids
        // double-filtering every request alongside uBlock Origin
        val contentBlocking = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .build()

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .contentBlocking(contentBlocking)
            .aboutConfigEnabled(false)
            .consoleOutput(false)

        if (initialDohUrl.isNotBlank()) {
            runCatching {
                settingsBuilder.trustedRecursiveResolverUri(initialDohUrl)
                settingsBuilder.defaultRecursiveResolverUri(initialDohUrl)
                settingsBuilder.trustedRecursiveResolverMode(
                    GeckoRuntimeSettings.TRR_MODE_FIRST
                )
            }.onFailure {
                Log.w(TAG, "DoH runtime builder option failed, falling back", it)
            }
        }

        val created = GeckoRuntime.create(context.applicationContext, settingsBuilder.build())
        runtime = created
        configureWebExtensionController(created)
        installBuiltInCompanionExtension(created)
        ensureUBlockOriginInstalled(created)
        return created
    }

    private fun configureWebExtensionController(rt: GeckoRuntime) {
        rt.webExtensionController.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                val isUBlock = extension.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID
                return GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(
                        isUBlock, // isPermissionsGranted
                        isUBlock, // isPrivateModeGranted
                        false     // isTechnicalAndInteractionDataGranted
                    )
                )
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny> {
                val decision = if (extension.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                    AllowOrDeny.ALLOW
                } else {
                    AllowOrDeny.DENY
                }
                return GeckoResult.fromValue(decision)
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny> {
                val decision = if (extension.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                    AllowOrDeny.ALLOW
                } else {
                    AllowOrDeny.DENY
                }
                return GeckoResult.fromValue(decision)
            }
        })
    }

    private fun ensureUBlockOriginInstalled(rt: GeckoRuntime) {
        rt.webExtensionController.list().accept(
            { installedList ->
                val existing = installedList?.firstOrNull {
                    it.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID
                }
                if (existing != null) {
                    onUBlockExtensionReady(rt, existing, "uBlock Origin active (Mozilla signed)")
                } else {
                    installBundledUBlockOrigin(rt)
                }
            },
            {
                installBundledUBlockOrigin(rt)
            }
        )
    }

    private fun installBundledUBlockOrigin(rt: GeckoRuntime) {
        runCatching {
            val xpiFile = uBlockUpdater.extractBundledXpi()
            installUBlockFromXpiFile(rt, xpiFile, UBlockOriginUpdater.BUNDLED_VERSION)
        }.onFailure { err ->
            Log.e(TAG, "Failed to extract bundled uBlock Origin XPI", err)
            _uBlockState.update {
                it.copy(
                    isInstalled = false,
                    statusDetail = "uBlock Origin fallback active (${err.message ?: "init error"})"
                )
            }
        }
    }

    fun installUBlockFromXpiFile(
        rt: GeckoRuntime,
        xpiFile: File,
        expectedVersion: String,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        val fileUri = Uri.fromFile(xpiFile).toString()
        rt.webExtensionController.install(fileUri).accept(
            { ext ->
                if (ext != null) {
                    val ver = ext.metaData.version.takeIf { it.isNotBlank() } ?: expectedVersion
                    onUBlockExtensionReady(rt, ext, "uBlock Origin v$ver active (gorhill/uBlock)")
                    onComplete?.invoke(Result.success(ver))
                } else {
                    val msg = "uBlock Origin installation returned null extension"
                    _uBlockState.update { it.copy(isUpdating = false, statusDetail = msg) }
                    onComplete?.invoke(Result.failure(IllegalStateException(msg)))
                }
            },
            { err ->
                Log.e(TAG, "GeckoView failed to install uBlock Origin XPI", err)
                val msg = err?.message ?: "XPI install error"
                _uBlockState.update {
                    it.copy(
                        isUpdating = false,
                        statusDetail = "uBlock Origin install warning: $msg"
                    )
                }
                onComplete?.invoke(Result.failure(err ?: IllegalStateException(msg)))
            }
        )
    }

    private fun onUBlockExtensionReady(rt: GeckoRuntime, ext: WebExtension, detail: String) {
        uBlockExtension = ext
        val version = ext.metaData.version.takeIf { it.isNotBlank() } ?: UBlockOriginUpdater.BUNDLED_VERSION
        if (cachedAdBlockEnabled) {
            rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
        } else {
            rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
        }
        _uBlockState.value = UBlockStatus(
            isInstalled = true,
            isEnabled = cachedAdBlockEnabled,
            version = version,
            isUpdating = false,
            statusDetail = detail
        )
    }

    fun setUBlockUpdating(updating: Boolean, detail: String? = null) {
        _uBlockState.update {
            it.copy(
                isUpdating = updating,
                statusDetail = detail ?: it.statusDetail
            )
        }
    }

    fun setUBlockEnabled(enabled: Boolean) {
        cachedAdBlockEnabled = enabled
        val rt = runtime
        val ext = uBlockExtension
        if (rt != null && ext != null) {
            if (enabled) {
                rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
            } else {
                rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
            }
        }
        _uBlockState.update {
            it.copy(
                isEnabled = enabled,
                statusDetail = if (enabled) {
                    "uBlock Origin v${it.version} active (gorhill/uBlock)"
                } else {
                    "uBlock Origin paused"
                }
            )
        }
    }

    fun applyDohSettings(dohUrl: String) {
        val rt = runtime ?: return
        runCatching {
            if (dohUrl.isBlank()) {
                rt.settings.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF)
            } else {
                rt.settings.setTrustedRecursiveResolverUri(dohUrl)
                rt.settings.setDefaultRecursiveResolverUri(dohUrl)
                rt.settings.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_FIRST)
            }
        }.onFailure {
            Log.w(TAG, "Unable to update DoH settings dynamically", it)
        }
    }

    fun updateExtensionSettings(
        adBlockEnabled: Boolean,
        backgroundAudioEnabled: Boolean,
        preferredQuality: QualityOption,
        studioZoomPercent: Int = cachedStudioZoomPercent
    ) {
        cachedAdBlockEnabled = adBlockEnabled
        cachedBgAudioEnabled = backgroundAudioEnabled
        cachedQuality = preferredQuality
        cachedStudioZoomPercent = studioZoomPercent

        setUBlockEnabled(adBlockEnabled)
        runCatching {
            if (streamSession.isOpen) {
                streamSession.setActive(true)
            }
        }

        val msg = JSONObject().apply {
            put("type", "updateSettings")
            put("adBlockEnabled", adBlockEnabled)
            put("backgroundAudioEnabled", backgroundAudioEnabled)
            put("preferredQuality", preferredQuality.label.lowercase())
            put("studioZoomPercent", studioZoomPercent)
        }
        runCatching { nativePort?.postMessage(msg) }
    }

    fun onAppForegrounded(isStudioTabActive: Boolean = false) {
        runCatching {
            if (streamSession.isOpen) streamSession.setActive(!isStudioTabActive || cachedBgAudioEnabled)
            if (studioSession.isOpen) studioSession.setActive(isStudioTabActive)
        }
    }

    fun onActiveTabChanged(isStreamTab: Boolean, isStudioTab: Boolean, backgroundAudioEnabled: Boolean) {
        cachedBgAudioEnabled = backgroundAudioEnabled
        if (isStudioTab) {
            ensureStudioOpened()
        }
        runCatching {
            if (streamSession.isOpen) {
                streamSession.setActive(isStreamTab || backgroundAudioEnabled)
            }
            if (studioSession.isOpen) {
                studioSession.setActive(isStudioTab)
            }
        }
    }

    fun onAppBackgrounded(backgroundAudioEnabled: Boolean) {
        runCatching {
            if (streamSession.isOpen) {
                streamSession.setActive(backgroundAudioEnabled)
                if (!backgroundAudioEnabled) {
                    pauseLiveMedia()
                }
            }
            if (studioSession.isOpen) {
                studioSession.setActive(false)
            }
        }
    }

    fun applyManualQuality(quality: QualityOption) {
        cachedQuality = quality
        val msg = JSONObject().apply {
            put("type", "setQuality")
            put("quality", quality.label.lowercase())
        }
        runCatching { nativePort?.postMessage(msg) }
    }

    fun requestQualityStateRefresh() {
        val msg = JSONObject().apply { put("type", "queryQuality") }
        runCatching { nativePort?.postMessage(msg) }
    }

    fun requestCookiesRefresh() {
        val msg = JSONObject().apply { put("type", "exportCookies") }
        runCatching { nativePort?.postMessage(msg) }
    }

    fun exitFullScreen() {
        runCatching {
            if (streamSession.isOpen) streamSession.exitFullScreen()
            if (studioSession.isOpen) studioSession.exitFullScreen()
        }
        _isFullScreen.value = false
    }

    fun applyStudioZoom(zoomPercent: Int) {
        val clamped = zoomPercent.coerceIn(50, 130)
        cachedStudioZoomPercent = clamped
        updateExtensionSettings(
            adBlockEnabled = cachedAdBlockEnabled,
            backgroundAudioEnabled = cachedBgAudioEnabled,
            preferredQuality = cachedQuality,
            studioZoomPercent = clamped
        )
    }

    private fun installBuiltInCompanionExtension(rt: GeckoRuntime) {
        rt.webExtensionController
            .ensureBuiltIn(
                "resource://android/assets/extensions/tubedeck_adblock/",
                "tubedeck-adblock@tubedeck.app"
            )
            .accept(
                { extension ->
                    extension?.setMessageDelegate(
                        object : WebExtension.MessageDelegate {
                            override fun onConnect(port: WebExtension.Port) {
                                nativePort = port
                                port.setDelegate(object : WebExtension.PortDelegate {
                                    override fun onPortMessage(
                                        message: Any,
                                        port: WebExtension.Port
                                    ) {
                                        val json = message as? JSONObject ?: return
                                        when (json.optString("type")) {
                                            "adBlocked" -> {
                                                _events.tryEmit(GeckoEvent.AdBlocked(1L))
                                            }
                                            "qualityState" -> {
                                                val current = json.optString("currentQuality", "Auto")
                                                val resolved = json.optString("resolvedQuality", "")
                                                val url = json.optString("videoUrl", "")
                                                val arr = json.optJSONArray("availableQualities")
                                                val levels = buildList {
                                                    if (arr != null) {
                                                        for (i in 0 until arr.length()) {
                                                            val item = arr.optString(i)
                                                            if (item.isNotBlank()) add(item)
                                                        }
                                                    }
                                                }
                                                _liveQualityState.value = LiveQualityState(
                                                    currentQuality = current.ifBlank { "Auto" },
                                                    resolvedQuality = resolved,
                                                    availableQualities = levels,
                                                    videoUrl = url
                                                )
                                            }
                                            "cookiesExport" -> {
                                                val txt = json.optString("cookiesTxt", "")
                                                if (txt.isNotBlank()) {
                                                    latestCookiesTxt = txt
                                                }
                                            }
                                            "openDownloadSheet" -> {
                                                val url = json.optString("url", "")
                                                val title = json.optString("title", "")
                                                val cookiesTxt = json.optString("cookiesTxt", "")
                                                if (cookiesTxt.isNotBlank()) {
                                                    latestCookiesTxt = cookiesTxt
                                                }
                                                if (url.isNotBlank()) {
                                                    _events.tryEmit(
                                                        GeckoEvent.InPageDownloadRequested(
                                                            url = url,
                                                            title = title,
                                                            cookiesTxt = cookiesTxt
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    override fun onDisconnect(port: WebExtension.Port) {
                                        if (nativePort == port) nativePort = null
                                    }
                                })
                                updateExtensionSettings(
                                    cachedAdBlockEnabled,
                                    cachedBgAudioEnabled,
                                    cachedQuality,
                                    cachedStudioZoomPercent
                                )
                                requestCookiesRefresh()
                            }
                        },
                        "tubedeck"
                    )
                },
                { error ->
                    Log.e(TAG, "Failed to install built-in companion enhancer extension", error)
                }
            )
    }

    private fun createStreamSession(): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .displayMode(GeckoSessionSettings.DISPLAY_MODE_STANDALONE)
            .usePrivateMode(false)
            .build()

        val session = GeckoSession(settings)
        attachCommonDelegates(session, isStudio = false, stateFlow = _streamState)
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                activeMediaSession = mediaSession
                _mediaState.update { it.copy(isActive = true) }
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                if (activeMediaSession == mediaSession) {
                    activeMediaSession = null
                }
                _mediaState.update { it.copy(isActive = false, isPlaying = false) }
            }

            override fun onMetadata(
                session: GeckoSession,
                mediaSession: MediaSession,
                meta: MediaSession.Metadata
            ) {
                activeMediaSession = mediaSession
                _mediaState.update {
                    it.copy(
                        isActive = true,
                        title = meta.title?.takeIf { t -> t.isNotBlank() } ?: _streamState.value.pageTitle,
                        artist = meta.artist?.takeIf { a -> a.isNotBlank() } ?: "YTube Studio Stream"
                    )
                }
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                activeMediaSession = mediaSession
                _mediaState.update { it.copy(isActive = true, isPlaying = true) }
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                _mediaState.update { it.copy(isPlaying = false) }
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                _mediaState.update { it.copy(isActive = false, isPlaying = false) }
            }

            override fun onPositionState(
                session: GeckoSession,
                mediaSession: MediaSession,
                state: MediaSession.PositionState
            ) {
                _mediaState.update {
                    it.copy(
                        durationSec = state.duration,
                        positionSec = state.position
                    )
                }
            }
        }
        return session
    }

    private fun createStudioSession(): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .userAgentOverride(DESKTOP_FIREFOX_UA)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER)
            .usePrivateMode(false)
            .build()

        val session = GeckoSession(settings)
        attachCommonDelegates(session, isStudio = true, stateFlow = _studioState)
        return session
    }

    fun getOrCreateAuthSession(rt: GeckoRuntime): GeckoSession {
        authSession?.let { return it }
        val settings = GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .usePrivateMode(false)
            .build()
        val session = GeckoSession(settings)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri
                if (isAllowedHost(uri)) {
                    // Only fire LoginCompleted when the actual destination HOST is YouTube/Studio,
                    // NOT when accounts.google.com contains continue=https://m.youtube.com/ in its query string!
                    val host = runCatching { Uri.parse(uri).host?.lowercase() }.getOrNull().orEmpty()
                    if (host == "m.youtube.com" || host == "www.youtube.com" || host == "youtube.com" || host == "studio.youtube.com") {
                        _isLoggedInLikely.value = true
                        _events.tryEmit(GeckoEvent.LoginCompleted(uri))
                    }
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
                _events.tryEmit(GeckoEvent.BlockedExternalNavigation(uri))
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }
        session.open(rt)
        session.loadUri(GOOGLE_LOGIN_URL)
        authSession = session
        return session
    }

    fun closeAuthSession() {
        authSession?.close()
        authSession = null
    }

    fun openStreamHome(rt: GeckoRuntime) {
        if (!streamSession.isOpen) {
            streamSession.open(rt)
            streamSession.loadUri(HOME_STREAM_URL)
        }
    }

    fun openStudioHome(rt: GeckoRuntime) {
        if (!studioSession.isOpen) {
            studioSession.open(rt)
            studioSession.loadUri(HOME_STUDIO_URL)
        }
    }

    fun loadStudioUrl(url: String) {
        ensureStudioOpened()
        if (isStudioOrAuthHost(url)) {
            studioSession.loadUri(url)
        } else if (isYouTubeWatchOrChannelUrl(url)) {
            _events.tryEmit(GeckoEvent.OpenVideoInMobileStream(toMobileYouTubeUrl(url)))
        }
    }

    fun loadStreamUrl(url: String) {
        if (isAllowedHost(url)) {
            streamSession.loadUri(toMobileYouTubeUrl(url))
        }
    }

    fun playPauseLiveMedia() {
        val ms = activeMediaSession ?: return
        if (_mediaState.value.isPlaying) {
            ms.pause()
        } else {
            ms.play()
        }
    }

    fun pauseLiveMedia() {
        activeMediaSession?.pause()
    }

    fun playLiveMedia() {
        activeMediaSession?.play()
    }

    fun stopLiveMedia() {
        activeMediaSession?.stop()
    }

    fun deliverFilePickerResult(uris: Array<Uri>?) {
        pendingFileCallback?.invoke(uris)
        pendingFileCallback = null
    }

    fun clearAllSessionCookies() {
        runtime?.storageController?.clearData(
            org.mozilla.geckoview.StorageController.ClearFlags.ALL
        )
        _isLoggedInLikely.value = false
    }

    private fun attachCommonDelegates(
        session: GeckoSession,
        isStudio: Boolean,
        stateFlow: MutableStateFlow<BrowserTabState>
    ) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri

                if (!isAllowedHost(uri)) {
                    _events.tryEmit(GeckoEvent.BlockedExternalNavigation(uri))
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (isStudio) {
                    // Hardcoded PC/Desktop Studio: ANY YouTube video/watch/shorts/channel link
                    // redirects automatically to the Mobile View YouTube Stream tab!
                    if (isYouTubeWatchOrChannelUrl(uri)) {
                        val mobileUrl = toMobileYouTubeUrl(uri)
                        _events.tryEmit(GeckoEvent.OpenVideoInMobileStream(mobileUrl))
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    // Keep Studio session strictly on studio.youtube.com & Google auth/assets
                    if (!isStudioOrAuthHost(uri)) {
                        _events.tryEmit(GeckoEvent.OpenVideoInMobileStream(toMobileYouTubeUrl(uri)))
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                } else {
                    // Mobile Stream tab: redirect studio.youtube.com links into Desktop Studio tab
                    if (isStudioHost(uri)) {
                        _events.tryEmit(GeckoEvent.OpenUrlInDesktopStudio(uri))
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String
            ): GeckoResult<GeckoSession> {
                if (isStudio) {
                    if (isYouTubeWatchOrChannelUrl(uri)) {
                        _events.tryEmit(GeckoEvent.OpenVideoInMobileStream(toMobileYouTubeUrl(uri)))
                    } else if (isStudioOrAuthHost(uri)) {
                        session.loadUri(uri)
                    } else if (!isAllowedHost(uri)) {
                        _events.tryEmit(GeckoEvent.BlockedExternalNavigation(uri))
                    } else {
                        _events.tryEmit(GeckoEvent.OpenVideoInMobileStream(toMobileYouTubeUrl(uri)))
                    }
                } else {
                    if (isStudioHost(uri)) {
                        _events.tryEmit(GeckoEvent.OpenUrlInDesktopStudio(uri))
                    } else if (isAllowedHost(uri)) {
                        session.loadUri(toMobileYouTubeUrl(uri))
                    } else {
                        _events.tryEmit(GeckoEvent.BlockedExternalNavigation(uri))
                    }
                }
                return GeckoResult.fromValue(null)
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                val safeUrl = url ?: return
                if (safeUrl.contains("studio.youtube.com/channel/") ||
                    safeUrl.contains("youtube.com/feed/subscriptions")
                ) {
                    _isLoggedInLikely.value = true
                }
                stateFlow.update { it.copy(currentUrl = safeUrl) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                stateFlow.update { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                stateFlow.update { it.copy(canGoForward = canGoForward) }
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String>? {
                return null
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                stateFlow.update { it.copy(isLoading = true, currentUrl = url) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                stateFlow.update { it.copy(isLoading = false, loadProgress = 100) }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                stateFlow.update { it.copy(loadProgress = progress) }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val fallback = if (isStudio) "YouTube Studio" else "YouTube"
                stateFlow.update { it.copy(pageTitle = title?.takeIf { t -> t.isNotBlank() } ?: fallback) }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                _isFullScreen.value = fullScreen
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(
                session: GeckoSession,
                event: ContentBlocking.BlockEvent
            ) {
                _events.tryEmit(GeckoEvent.AdBlocked(1L))
            }
        }

        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val mimeTypes = prompt.mimeTypes?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
                val allowMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
                pendingFileCallback = { uris ->
                    if (uris.isNullOrEmpty()) {
                        result.complete(prompt.dismiss())
                    } else if (allowMultiple) {
                        result.complete(prompt.confirm(context, uris))
                    } else {
                        result.complete(prompt.confirm(context, uris.first()))
                    }
                }
                _events.tryEmit(
                    GeckoEvent.FileUploadRequested(
                        mimeTypes = mimeTypes,
                        allowMultiple = allowMultiple,
                        onResult = { uris -> deliverFilePickerResult(uris) }
                    )
                )
                return result
            }
        }
    }

    fun isAllowedHost(rawUrl: String): Boolean {
        if (rawUrl.startsWith("about:") || rawUrl.startsWith("blob:") || rawUrl.startsWith("data:")) {
            return true
        }
        val host = runCatching { Uri.parse(rawUrl).host?.lowercase() }.getOrNull() ?: return false
        return ALLOWED_EXACT_OR_SUBDOMAIN_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun isStudioHost(rawUrl: String): Boolean {
        val host = runCatching { Uri.parse(rawUrl).host?.lowercase() }.getOrNull() ?: return false
        return host == "studio.youtube.com"
    }

    private fun isStudioOrAuthHost(rawUrl: String): Boolean {
        if (rawUrl.startsWith("about:") || rawUrl.startsWith("blob:") || rawUrl.startsWith("data:")) {
            return true
        }
        val host = runCatching { Uri.parse(rawUrl).host?.lowercase() }.getOrNull() ?: return false
        return STUDIO_AUTH_OR_INFRA_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun isYouTubeWatchOrChannelUrl(rawUrl: String): Boolean {
        val parsed = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        val host = parsed.host?.lowercase() ?: return false
        if (host == "studio.youtube.com" || host == "accounts.youtube.com" || host == "consent.youtube.com") {
            return false
        }
        if (host == "youtu.be") return true
        if (host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com" || host == "music.youtube.com") {
            val path = parsed.path?.lowercase().orEmpty()
            // Never intercept internal sign-in or redirect helper endpoints
            if (path.startsWith("/signin") || path.startsWith("/account") || path.startsWith("/logout")) {
                return false
            }
            return true
        }
        return false
    }

    fun toMobileYouTubeUrl(rawUrl: String): String {
        val parsed = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return HOME_STREAM_URL
        val host = parsed.host?.lowercase() ?: return HOME_STREAM_URL
        return when (host) {
            "youtu.be" -> {
                val videoId = parsed.pathSegments.firstOrNull().orEmpty()
                if (videoId.isBlank()) {
                    HOME_STREAM_URL
                } else {
                    val builder = Uri.parse("https://m.youtube.com/watch").buildUpon()
                        .appendQueryParameter("v", videoId)
                    parsed.queryParameterNames.forEach { key ->
                        if (key != "v") {
                            parsed.getQueryParameter(key)?.let { value ->
                                builder.appendQueryParameter(key, value)
                            }
                        }
                    }
                    builder.build().toString()
                }
            }
            "www.youtube.com", "youtube.com" -> {
                parsed.buildUpon().authority("m.youtube.com").build().toString()
            }
            else -> rawUrl
        }
    }
}
