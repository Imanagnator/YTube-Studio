package com.example.gecko

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
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
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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

data class StudioProMetrics(
    val isEditorPage: Boolean = false,
    val videoId: String = "",
    val titleLen: Int = -1,
    val descLen: Int = -1,
    val hasChapters: Boolean = false,
    val hashtagCount: Int = 0,
    val tagCount: Int = 0,
    val tagsCharCount: Int = 0,
    val hasCustomThumb: Boolean = false,
    val seoScore: Int = -1,
    val topTip: String = "Open any video in Studio to inspect live SEO"
)

data class ActivePopupState(
    val id: Long,
    val session: GeckoSession,
    val title: String,
    val currentUrl: String,
    val isGoogleFlow: Boolean,
    val canGoBack: Boolean = false,
    val isLoading: Boolean = true,
    val depth: Int = 1
)

sealed interface GeckoEvent {
    data class BlockedExternalNavigation(val url: String) : GeckoEvent
    data class AdBlocked(val countDelta: Long) : GeckoEvent
    data class LoginCompleted(val url: String) : GeckoEvent
    data class OpenAuthPopup(val url: String, val title: String) : GeckoEvent
    data class OpenVideoInMobileStream(val mobileUrl: String) : GeckoEvent
    data class OpenUrlInDesktopStudio(val studioUrl: String) : GeckoEvent
    data class InPageDownloadRequested(val url: String, val title: String, val cookiesTxt: String) : GeckoEvent
    data class StudioProToast(val message: String) : GeckoEvent
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
            "googleusercontent.com",
            "googleapis.com",
            "clients.google.com",
            "clients6.google.com",
            "upload.video.google.com",
            "www.google.com"
        )

        private val STUDIO_AUTH_OR_INFRA_HOSTS = setOf(
            "studio.youtube.com",
            "upload.youtube.com",
            "upload.video.google.com",
            "accounts.google.com",
            "accounts.youtube.com",
            "myaccount.google.com",
            "gds.google.com",
            "consent.youtube.com",
            "consent.google.com",
            "policies.google.com",
            "gstatic.com",
            "googleusercontent.com",
            "googleapis.com",
            "clients.google.com",
            "clients6.google.com",
            "www.google.com",
            "googlevideo.com",
            "ytimg.com",
            "ggpht.com"
        )

        // Trusted domains for installed Mozilla extensions (vidIQ login/signup, SponsorBlock, DeArrow, RYD, Enhancer)
        private val EXTENSION_TRUSTED_HOSTS = setOf(
            "vidiq.com",
            "vid.io",
            "ajay.app",
            "returnyoutubedislike.com",
            "mrfdev.com",
            "auth0.com",
            "challenges.cloudflare.com",
            "hcaptcha.com",
            "recaptcha.net",
            "appleid.apple.com",
            "firebaseapp.com"
        )
    }

    val uBlockUpdater = UBlockOriginUpdater(context.applicationContext)
    val extensionUpdater = ManagedExtensionUpdater(context.applicationContext, uBlockUpdater)

    @Volatile
    private var runtime: GeckoRuntime? = null
    private var nativePort: WebExtension.Port? = null
    private var studioProPort: WebExtension.Port? = null
    private var uBlockExtension: WebExtension? = null
    private val installedExtensions = ConcurrentHashMap<String, WebExtension>()
    private var disabledExtensionIdsCache: Set<String> = emptySet()
    private var pendingFileCallback: ((Array<Uri>?) -> Unit)? = null
    @Volatile
    private var isAwaitingFilePicker: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var hasReloadedForFirstUBlock = false

    val streamSession: GeckoSession by lazy { createStreamSession() }
    val studioSession: GeckoSession by lazy { createStudioSession() }
    private val popupStack = ArrayDeque<ActivePopupState>()
    private var nextPopupId = 1L

    private val _activePopup = MutableStateFlow<ActivePopupState?>(null)
    val activePopup: StateFlow<ActivePopupState?> = _activePopup.asStateFlow()

    private val _studioProMetrics = MutableStateFlow(StudioProMetrics())
    val studioProMetrics: StateFlow<StudioProMetrics> = _studioProMetrics.asStateFlow()

    private var cachedStudioProEnabled = true
    private var cachedStudioProAutoCollapse = true
    private var cachedStudioProFocusMode = false
    private var cachedStudioProSeoHud = true
    private var cachedStudioProPrivacyBlur = false
    private var cachedStudioProCustomTemplate = ""

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

    private val _managedExtensions = MutableStateFlow(
        ManagedExtensionsCatalog.ALL_SPECS.map { ManagedExtensionState(it) }
    )
    val managedExtensions: StateFlow<List<ManagedExtensionState>> = _managedExtensions.asStateFlow()

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

        // ACCEPT_NON_TRACKERS lets YouTube upload endpoints & extension sign-in domains (e.g. vidIQ)
        // share auth cookies seamlessly with studio.youtube.com while blocking known ad trackers.
        val contentBlocking = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            .build()

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .extensionsProcessEnabled(true)
            .extensionsWebAPIEnabled(true)
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
        cleanupOldStagedUploads()
        configureWebExtensionController(created)
        installBuiltInCompanionExtension(created)
        installBuiltInStudioProExtension(created)
        ensureUBlockOriginInstalled(created)
        return created
    }

    private fun configureWebExtensionController(rt: GeckoRuntime) {
        rt.webExtensionController.setExtensionProcessDelegate(
            object : WebExtensionController.ExtensionProcessDelegate {
                override fun onDisabledProcessSpawning() {
                    Log.w(TAG, "Extension process spawning was disabled after crash — re-enabling automatically")
                    runCatching { rt.webExtensionController.enableExtensionProcessSpawning() }
                }
            }
        )
        rt.webExtensionController.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                val isAllowed = ManagedExtensionsCatalog.ALL_EXTENSION_IDS.contains(extension.id)
                return GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(
                        isAllowed, // isPermissionsGranted
                        isAllowed, // isPrivateModeGranted
                        false      // isTechnicalAndInteractionDataGranted
                    )
                )
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny> {
                val decision = if (ManagedExtensionsCatalog.ALL_EXTENSION_IDS.contains(extension.id)) {
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
                val decision = if (ManagedExtensionsCatalog.ALL_EXTENSION_IDS.contains(extension.id)) {
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
                val list = installedList.orEmpty()
                list.forEach { ext ->
                    attachExtensionTabDelegate(rt, ext)
                    val spec = ManagedExtensionsCatalog.findById(ext.id)
                    if (spec != null) {
                        installedExtensions[ext.id] = ext
                        if (ext.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                            onUBlockExtensionReady(rt, ext, "uBlock Origin active (Mozilla signed)")
                        } else {
                            val enabled = !disabledExtensionIdsCache.contains(ext.id)
                            if (enabled) {
                                rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
                            } else {
                                rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
                            }
                            val ver = ext.metaData.version.takeIf { it.isNotBlank() } ?: "Installed"
                            updateManagedExtensionState(ext.id) {
                                it.copy(
                                    isInstalled = true,
                                    isEnabled = enabled,
                                    version = ver,
                                    isUpdating = false,
                                    statusDetail = if (enabled) "v$ver active (Mozilla signed)" else "v$ver paused"
                                )
                            }
                        }
                    }
                }
                if (!installedExtensions.containsKey(UBlockOriginUpdater.UBLOCK_EXTENSION_ID)) {
                    installBundledUBlockOrigin(rt)
                }
            },
            {
                installBundledUBlockOrigin(rt)
            }
        )
    }

    /**
     * Connects browser.tabs.create() and browser.runtime.openOptionsPage() from ANY installed
     * Mozilla WebExtension (e.g., vidIQ Vision Log in / Sign up, SponsorBlock options, uBlock dashboard)
     * directly to our synced in-app popup modal while keeping the extension's tab handle alive.
     */
    private fun attachExtensionTabDelegate(rt: GeckoRuntime, ext: WebExtension) {
        ext.setTabDelegate(object : WebExtension.TabDelegate {
            override fun onNewTab(
                source: WebExtension,
                createDetails: WebExtension.CreateTabDetails
            ): GeckoResult<GeckoSession> {
                val targetUrl = createDetails.url.orEmpty()
                val extName = source.metaData.name.orEmpty()
                val title = if (targetUrl.isNotBlank()) {
                    resolveExtensionPopupTitle(targetUrl, extName)
                } else {
                    extName.ifBlank { "Extension Account" }
                }
                val popupSession = openSyncedPopupSession(
                    rt = rt,
                    initialUrl = targetUrl,
                    title = title,
                    isGoogleFlow = false,
                    autoLoadInitialUrl = false,
                    sourceExtension = source
                )
                return GeckoResult.fromValue(popupSession)
            }

            override fun onOpenOptionsPage(source: WebExtension) {
                val optionsUrl = source.metaData.optionsPageUrl
                val extName = source.metaData.name.orEmpty()
                if (!optionsUrl.isNullOrBlank()) {
                    openSyncedPopupSession(
                        rt = rt,
                        initialUrl = optionsUrl,
                        title = "${extName.ifBlank { "Extension" }} Settings",
                        isGoogleFlow = false,
                        autoLoadInitialUrl = true,
                        sourceExtension = source
                    )
                }
            }
        })
    }

    private fun installBundledUBlockOrigin(rt: GeckoRuntime) {
        runCatching {
            val xpiFile = uBlockUpdater.extractBundledXpi()
            installUBlockFromXpiFile(rt, xpiFile, UBlockOriginUpdater.BUNDLED_VERSION, isFirstBootInstall = true)
        }.onFailure { err ->
            Log.e(TAG, "Failed to extract bundled uBlock Origin XPI", err)
            _uBlockState.update {
                it.copy(
                    isInstalled = false,
                    statusDetail = "Will auto-install from Mozilla AMO (${err.message ?: "init error"})"
                )
            }
            updateManagedExtensionState(UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                it.copy(
                    isInstalled = false,
                    isUpdating = false,
                    statusDetail = "Waiting for Mozilla AMO auto-install..."
                )
            }
        }
    }

    fun installUBlockFromXpiFile(
        rt: GeckoRuntime,
        xpiFile: File,
        expectedVersion: String,
        isFirstBootInstall: Boolean = false,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        val fileUri = Uri.fromFile(xpiFile).toString()
        rt.webExtensionController.install(fileUri, WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { ext ->
                if (ext != null) {
                    val ver = ext.metaData.version.takeIf { it.isNotBlank() } ?: expectedVersion
                    onUBlockExtensionReady(
                        rt = rt,
                        ext = ext,
                        detail = "v$ver active (Mozilla signed)",
                        triggerFirstReload = isFirstBootInstall
                    )
                    onComplete?.invoke(Result.success(ver))
                } else {
                    val msg = "uBlock Origin install returned null"
                    _uBlockState.update { it.copy(isInstalled = false, isUpdating = false, statusDetail = msg) }
                    updateManagedExtensionState(UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                        it.copy(isInstalled = false, isUpdating = false, statusDetail = msg)
                    }
                    onComplete?.invoke(Result.failure(IllegalStateException(msg)))
                }
            },
            { err ->
                Log.e(TAG, "GeckoView failed to install uBlock Origin XPI", err)
                val msg = err?.message ?: "XPI install error"
                _uBlockState.update {
                    it.copy(
                        isInstalled = false,
                        isUpdating = false,
                        statusDetail = "Will retry via Mozilla AMO ($msg)"
                    )
                }
                updateManagedExtensionState(UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                    it.copy(
                        isInstalled = false,
                        isUpdating = false,
                        statusDetail = "Will retry via Mozilla AMO ($msg)"
                    )
                }
                onComplete?.invoke(Result.failure(err ?: IllegalStateException(msg)))
            }
        )
    }

    private fun onUBlockExtensionReady(
        rt: GeckoRuntime,
        ext: WebExtension,
        detail: String,
        triggerFirstReload: Boolean = false
    ) {
        attachExtensionTabDelegate(rt, ext)
        uBlockExtension = ext
        installedExtensions[ext.id] = ext
        val version = ext.metaData.version.takeIf { it.isNotBlank() } ?: UBlockOriginUpdater.BUNDLED_VERSION
        if (cachedAdBlockEnabled) {
            rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER).accept(
                { enabledExt ->
                    if (enabledExt != null) {
                        uBlockExtension = enabledExt
                        installedExtensions[enabledExt.id] = enabledExt
                    }
                },
                { err -> Log.w(TAG, "uBlock enable returned warning", err) }
            )
            rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.APP)
        } else {
            rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER).accept(
                { disabledExt ->
                    if (disabledExt != null) {
                        uBlockExtension = disabledExt
                        installedExtensions[disabledExt.id] = disabledExt
                    }
                },
                { err -> Log.w(TAG, "uBlock disable returned warning", err) }
            )
        }
        _uBlockState.value = UBlockStatus(
            isInstalled = true,
            isEnabled = cachedAdBlockEnabled,
            version = version,
            isUpdating = false,
            statusDetail = detail
        )
        updateManagedExtensionState(UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
            it.copy(
                isInstalled = true,
                isEnabled = cachedAdBlockEnabled,
                version = version,
                isUpdating = false,
                statusDetail = detail
            )
        }
        if (triggerFirstReload && cachedAdBlockEnabled && !hasReloadedForFirstUBlock) {
            hasReloadedForFirstUBlock = true
            reloadActiveStreamForAdBlock(delayMs = 1_800L)
        }
    }

    fun reloadActiveStreamForAdBlock(delayMs: Long = 1_200L) {
        mainHandler.postDelayed({
            runCatching {
                if (streamSession.isOpen && !_mediaState.value.isPlaying) {
                    streamSession.reload()
                }
            }
        }, delayMs)
    }

    fun setUBlockUpdating(updating: Boolean, detail: String? = null) {
        _uBlockState.update {
            it.copy(
                isUpdating = updating,
                statusDetail = detail ?: it.statusDetail
            )
        }
        updateManagedExtensionState(UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
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
        val detail = if (enabled) {
            "uBlock Origin v${_uBlockState.value.version} active (gorhill/uBlock)"
        } else {
            "uBlock Origin paused"
        }
        _uBlockState.update {
            it.copy(isEnabled = enabled, statusDetail = detail)
        }
        updateManagedExtensionState(UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
            it.copy(isEnabled = enabled, statusDetail = detail)
        }
    }

    fun applyDisabledExtensionIds(disabledIds: Set<String>) {
        disabledExtensionIdsCache = disabledIds
        val rt = runtime
        ManagedExtensionsCatalog.ALL_SPECS.forEach { spec ->
            if (spec.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) return@forEach
            val enabled = !disabledIds.contains(spec.id)
            val ext = installedExtensions[spec.id]
            if (rt != null && ext != null) {
                if (enabled) {
                    rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
                } else {
                    rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
                }
            }
            updateManagedExtensionState(spec.id) { state ->
                state.copy(
                    isEnabled = enabled,
                    statusDetail = if (state.isInstalled) {
                        if (enabled) "v${state.version} active (Mozilla signed)" else "v${state.version} paused"
                    } else {
                        if (enabled) state.statusDetail else "Disabled by user"
                    }
                )
            }
        }
    }

    fun setManagedExtensionUpdating(extensionId: String, updating: Boolean, detail: String? = null) {
        if (extensionId == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
            setUBlockUpdating(updating, detail)
            return
        }
        updateManagedExtensionState(extensionId) {
            it.copy(
                isUpdating = updating,
                statusDetail = detail ?: it.statusDetail
            )
        }
    }

    fun setManagedExtensionEnabled(extensionId: String, enabled: Boolean) {
        if (extensionId == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
            setUBlockEnabled(enabled)
            return
        }
        disabledExtensionIdsCache = if (enabled) {
            disabledExtensionIdsCache - extensionId
        } else {
            disabledExtensionIdsCache + extensionId
        }
        val rt = runtime
        val ext = installedExtensions[extensionId]
        if (rt != null && ext != null) {
            if (enabled) {
                rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
            } else {
                rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
            }
        }
        updateManagedExtensionState(extensionId) { state ->
            state.copy(
                isEnabled = enabled,
                statusDetail = if (state.isInstalled) {
                    if (enabled) "v${state.version} active (Mozilla signed)" else "v${state.version} paused"
                } else {
                    if (enabled) "Installing from Mozilla AMO..." else "Disabled by user"
                }
            )
        }
    }

    /**
     * Installs an officially Mozilla-signed extension directly from its HTTPS download URL
     * (identical to Firefox for Android / Fenix's production AddonManager install path).
     */
    fun installManagedExtensionFromUrl(
        rt: GeckoRuntime,
        spec: ManagedExtensionSpec,
        downloadUrl: String,
        expectedVersion: String,
        enableAfterInstall: Boolean = true,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        installManagedExtensionUri(
            rt = rt,
            spec = spec,
            installUri = downloadUrl,
            installMethod = WebExtensionController.INSTALLATION_METHOD_MANAGER,
            expectedVersion = expectedVersion,
            enableAfterInstall = enableAfterInstall,
            onComplete = onComplete
        )
    }

    fun installManagedExtensionFromXpi(
        rt: GeckoRuntime,
        spec: ManagedExtensionSpec,
        xpiFile: File,
        expectedVersion: String,
        enableAfterInstall: Boolean = true,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        val fileUri = Uri.fromFile(xpiFile).toString()
        installManagedExtensionUri(
            rt = rt,
            spec = spec,
            installUri = fileUri,
            installMethod = WebExtensionController.INSTALLATION_METHOD_FROM_FILE,
            expectedVersion = expectedVersion,
            enableAfterInstall = enableAfterInstall,
            onComplete = onComplete
        )
    }

    private fun installManagedExtensionUri(
        rt: GeckoRuntime,
        spec: ManagedExtensionSpec,
        installUri: String,
        installMethod: String,
        expectedVersion: String,
        enableAfterInstall: Boolean,
        onComplete: ((Result<String>) -> Unit)?
    ) {
        rt.webExtensionController.install(installUri, installMethod).accept(
            { ext ->
                if (ext != null) {
                    val ver = ext.metaData.version.takeIf { it.isNotBlank() } ?: expectedVersion
                    if (spec.id == UBlockOriginUpdater.UBLOCK_EXTENSION_ID) {
                        onUBlockExtensionReady(
                            rt = rt,
                            ext = ext,
                            detail = "v$ver active (Mozilla signed)",
                            triggerFirstReload = true
                        )
                    } else {
                        attachExtensionTabDelegate(rt, ext)
                        installedExtensions[spec.id] = ext
                        if (enableAfterInstall) {
                            rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER).accept(
                                { updated -> if (updated != null) installedExtensions[spec.id] = updated },
                                {}
                            )
                            rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.APP)
                        } else {
                            rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER).accept(
                                { updated -> if (updated != null) installedExtensions[spec.id] = updated },
                                {}
                            )
                        }
                        updateManagedExtensionState(spec.id) {
                            it.copy(
                                isInstalled = true,
                                isEnabled = enableAfterInstall,
                                version = ver,
                                isUpdating = false,
                                statusDetail = if (enableAfterInstall) {
                                    "v$ver active (Mozilla signed)"
                                } else {
                                    "v$ver installed (paused)"
                                }
                            )
                        }
                    }
                    onComplete?.invoke(Result.success(ver))
                } else {
                    val msg = "${spec.name} install returned null"
                    updateManagedExtensionState(spec.id) { it.copy(isUpdating = false, statusDetail = msg) }
                    onComplete?.invoke(Result.failure(IllegalStateException(msg)))
                }
            },
            { err ->
                val msg = err?.message ?: "XPI install error"
                Log.e(TAG, "GeckoView failed to install ${spec.name} via $installMethod", err)
                updateManagedExtensionState(spec.id) {
                    it.copy(isUpdating = false, statusDetail = "Install warning: $msg")
                }
                onComplete?.invoke(Result.failure(err ?: IllegalStateException(msg)))
            }
        )
    }

    private fun updateManagedExtensionState(
        extensionId: String,
        transform: (ManagedExtensionState) -> ManagedExtensionState
    ) {
        _managedExtensions.update { list ->
            list.map { item -> if (item.spec.id == extensionId) transform(item) else item }
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
        val rt = runtime
        runCatching {
            if (streamSession.isOpen) {
                streamSession.setActive(!isStudioTabActive || cachedBgAudioEnabled)
                rt?.webExtensionController?.setTabActive(streamSession, !isStudioTabActive)
            }
            if (studioSession.isOpen) {
                studioSession.setActive(isStudioTabActive)
                rt?.webExtensionController?.setTabActive(studioSession, isStudioTabActive)
            }
        }
    }

    fun onActiveTabChanged(isStreamTab: Boolean, isStudioTab: Boolean, backgroundAudioEnabled: Boolean) {
        cachedBgAudioEnabled = backgroundAudioEnabled
        if (isStudioTab) {
            ensureStudioOpened()
        }
        val rt = runtime
        runCatching {
            if (streamSession.isOpen) {
                streamSession.setActive(isStreamTab || backgroundAudioEnabled)
                rt?.webExtensionController?.setTabActive(streamSession, isStreamTab)
            }
            if (studioSession.isOpen) {
                studioSession.setActive(isStudioTab)
                rt?.webExtensionController?.setTabActive(studioSession, isStudioTab)
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
            // Keep Studio active while the Android system File Picker is open for video/thumbnail uploads!
            if (studioSession.isOpen && !isAwaitingFilePicker) {
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

    fun triggerStudioUploadDialog() {
        val studioProMsg = JSONObject().apply {
            put("type", "studioProAction")
            put("action", "triggerUpload")
        }
        runCatching { studioProPort?.postMessage(studioProMsg) }
        val legacyMsg = JSONObject().apply { put("type", "triggerStudioUpload") }
        runCatching { nativePort?.postMessage(legacyMsg) }
    }

    fun updateStudioProConfig(
        enabled: Boolean,
        autoCollapseSidebar: Boolean,
        focusMode: Boolean,
        seoHudEnabled: Boolean,
        privacyBlur: Boolean,
        customTemplate: String
    ) {
        cachedStudioProEnabled = enabled
        cachedStudioProAutoCollapse = autoCollapseSidebar
        cachedStudioProFocusMode = focusMode
        cachedStudioProSeoHud = seoHudEnabled
        cachedStudioProPrivacyBlur = privacyBlur
        cachedStudioProCustomTemplate = customTemplate

        val msg = JSONObject().apply {
            put("type", "updateStudioProConfig")
            put("enabled", enabled)
            put("autoCollapseSidebar", autoCollapseSidebar)
            put("focusMode", focusMode)
            put("seoHudEnabled", seoHudEnabled)
            put("privacyBlur", privacyBlur)
            put("customTemplate", customTemplate)
        }
        runCatching { studioProPort?.postMessage(msg) }
    }

    fun sendStudioProAction(action: String, payload: String = "") {
        val msg = JSONObject().apply {
            put("type", "studioProAction")
            put("action", action)
            put("payload", payload)
        }
        runCatching { studioProPort?.postMessage(msg) }
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

    private fun installBuiltInStudioProExtension(rt: GeckoRuntime) {
        rt.webExtensionController
            .ensureBuiltIn(
                "resource://android/assets/extensions/ytube_studio_pro/",
                "ytube-studio-pro@tamalmitra.app"
            )
            .accept(
                { extension ->
                    if (extension != null) {
                        attachExtensionTabDelegate(rt, extension)
                        extension.setMessageDelegate(
                            object : WebExtension.MessageDelegate {
                                override fun onConnect(port: WebExtension.Port) {
                                    studioProPort = port
                                    port.setDelegate(object : WebExtension.PortDelegate {
                                        override fun onPortMessage(
                                            message: Any,
                                            port: WebExtension.Port
                                        ) {
                                            val json = message as? JSONObject ?: return
                                            when (json.optString("type")) {
                                                "studioProMetrics" -> {
                                                    _studioProMetrics.value = StudioProMetrics(
                                                        isEditorPage = json.optBoolean("isEditorPage", false),
                                                        videoId = json.optString("videoId", ""),
                                                        titleLen = json.optInt("titleLen", -1),
                                                        descLen = json.optInt("descLen", -1),
                                                        hasChapters = json.optBoolean("hasChapters", false),
                                                        hashtagCount = json.optInt("hashtagCount", 0),
                                                        tagCount = json.optInt("tagCount", 0),
                                                        tagsCharCount = json.optInt("tagsCharCount", 0),
                                                        hasCustomThumb = json.optBoolean("hasCustomThumb", false),
                                                        seoScore = json.optInt("seoScore", -1),
                                                        topTip = json.optString("topTip", "Open any video in Studio to inspect live SEO")
                                                    )
                                                }
                                                "studioProToast" -> {
                                                    val msg = json.optString("message", "")
                                                    if (msg.isNotBlank()) {
                                                        _events.tryEmit(GeckoEvent.StudioProToast(msg))
                                                    }
                                                }
                                            }
                                        }

                                        override fun onDisconnect(port: WebExtension.Port) {
                                            if (studioProPort == port) studioProPort = null
                                        }
                                    })
                                    updateStudioProConfig(
                                        enabled = cachedStudioProEnabled,
                                        autoCollapseSidebar = cachedStudioProAutoCollapse,
                                        focusMode = cachedStudioProFocusMode,
                                        seoHudEnabled = cachedStudioProSeoHud,
                                        privacyBlur = cachedStudioProPrivacyBlur,
                                        customTemplate = cachedStudioProCustomTemplate
                                    )
                                }
                            },
                            "ytube_studio_pro"
                        )
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to install built-in YTube Studio Pro extension", error)
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

    fun openGoogleSignInPopup(): GeckoSession? {
        val rt = runtime ?: return null
        return openSyncedPopupSession(
            rt = rt,
            initialUrl = GOOGLE_LOGIN_URL,
            title = "Google Sign-In (Synced Session)",
            isGoogleFlow = true,
            autoLoadInitialUrl = true,
            sourceExtension = null
        )
    }

    fun openExtensionPopupUrl(url: String, title: String = resolveExtensionPopupTitle(url)): GeckoSession? {
        val rt = runtime ?: return null
        return openSyncedPopupSession(
            rt = rt,
            initialUrl = url,
            title = title,
            isGoogleFlow = url.contains("accounts.google.com", ignoreCase = true),
            autoLoadInitialUrl = true,
            sourceExtension = null
        )
    }

    /**
     * Creates a real, open GeckoSession for Google Sign-In, WebExtension browser.tabs.create(),
     * or window.open() OAuth popups (such as vidIQ -> Continue with Google), preserving window.opener,
     * postMessage, and shared cookies across the entire app.
     */
    private fun openSyncedPopupSession(
        rt: GeckoRuntime,
        initialUrl: String,
        title: String,
        isGoogleFlow: Boolean,
        autoLoadInitialUrl: Boolean,
        sourceExtension: WebExtension? = null
    ): GeckoSession {
        val settingsBuilder = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
        if (!isGoogleFlow) {
            // Match Desktop Firefox UA for extension auth popups (vidIQ, SponsorBlock, etc.)
            settingsBuilder
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                .userAgentOverride(DESKTOP_FIREFOX_UA)
        } else {
            settingsBuilder.userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        }
        val session = GeckoSession(settingsBuilder.build())
        val popupId = synchronized(popupStack) { nextPopupId++ }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri
                val allowedScheme = uri.startsWith("https://", ignoreCase = true) ||
                    uri.startsWith("http://", ignoreCase = true) ||
                    uri.startsWith("moz-extension://", ignoreCase = true) ||
                    uri.startsWith("about:", ignoreCase = true) ||
                    uri.startsWith("blob:", ignoreCase = true) ||
                    uri.startsWith("data:", ignoreCase = true)
                if (!allowedScheme) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // Auto-complete only for the dedicated Google Sign-In flow when landing back on YouTube/Studio
                val host = runCatching { Uri.parse(uri).host?.lowercase() }.getOrNull().orEmpty()
                if (isGoogleFlow && (host == "m.youtube.com" || host == "www.youtube.com" || host == "youtube.com" || host == "studio.youtube.com")) {
                    _isLoggedInLikely.value = true
                    _events.tryEmit(GeckoEvent.LoginCompleted(uri))
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String
            ): GeckoResult<GeckoSession> {
                // Support nested OAuth popups (e.g. vidIQ Login -> "Continue with Google" popup)
                // while keeping window.opener & postMessage 100% intact!
                val childTitle = if (uri.contains("accounts.google.com", ignoreCase = true)) {
                    "Google Sign-In"
                } else {
                    resolveExtensionPopupTitle(uri, title)
                }
                val child = openSyncedPopupSession(
                    rt = rt,
                    initialUrl = uri,
                    title = childTitle,
                    isGoogleFlow = false,
                    autoLoadInitialUrl = false,
                    sourceExtension = sourceExtension
                )
                return GeckoResult.fromValue(child)
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                val safeUrl = url ?: return
                updatePopupEntry(popupId) { it.copy(currentUrl = safeUrl) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                updatePopupEntry(popupId) { it.copy(canGoBack = canGoBack) }
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                updatePopupEntry(popupId) { it.copy(isLoading = true, currentUrl = url) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                updatePopupEntry(popupId) { it.copy(isLoading = false) }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, pageTitle: String?) {
                if (!pageTitle.isNullOrBlank()) {
                    updatePopupEntry(popupId) { it.copy(title = pageTitle) }
                }
            }

            override fun onCloseRequest(session: GeckoSession) {
                // OAuth popup called window.close() after finishing authentication!
                mainHandler.post { closePopupById(popupId, reloadStudioIfEmpty = !isGoogleFlow) }
            }
        }

        attachFilePromptDelegate(session)

        // Allow extensions to close/update tabs they created via browser.tabs.remove() / update()
        val sessionTabDelegate = object : WebExtension.SessionTabDelegate {
            override fun onCloseTab(
                source: WebExtension?,
                tabSession: GeckoSession
            ): GeckoResult<AllowOrDeny> {
                mainHandler.post { closePopupById(popupId, reloadStudioIfEmpty = true) }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onUpdateTab(
                source: WebExtension,
                tabSession: GeckoSession,
                details: WebExtension.UpdateTabDetails
            ): GeckoResult<AllowOrDeny> {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }
        if (sourceExtension != null) {
            runCatching { session.webExtensionController.setTabDelegate(sourceExtension, sessionTabDelegate) }
        }
        installedExtensions.values.forEach { ext ->
            runCatching { session.webExtensionController.setTabDelegate(ext, sessionTabDelegate) }
        }

        session.open(rt)
        runCatching { rt.webExtensionController.setTabActive(session, true) }

        val entry = ActivePopupState(
            id = popupId,
            session = session,
            title = title,
            currentUrl = initialUrl,
            isGoogleFlow = isGoogleFlow,
            depth = synchronized(popupStack) { popupStack.size + 1 }
        )
        synchronized(popupStack) {
            popupStack.addLast(entry)
            _activePopup.value = entry
        }

        if (autoLoadInitialUrl && initialUrl.isNotBlank()) {
            session.loadUri(initialUrl)
        }
        return session
    }

    private fun updatePopupEntry(popupId: Long, transform: (ActivePopupState) -> ActivePopupState) {
        synchronized(popupStack) {
            val idx = popupStack.indexOfFirst { it.id == popupId }
            if (idx >= 0) {
                val updated = transform(popupStack[idx])
                popupStack[idx] = updated
                if (_activePopup.value?.id == popupId) {
                    _activePopup.value = updated
                }
            }
        }
    }

    fun closePopupById(popupId: Long, reloadStudioIfEmpty: Boolean = false) {
        var closed: ActivePopupState? = null
        var remainingTop: ActivePopupState? = null
        synchronized(popupStack) {
            val idx = popupStack.indexOfFirst { it.id == popupId }
            if (idx >= 0) {
                closed = popupStack.removeAt(idx)
            }
            remainingTop = popupStack.lastOrNull()
            _activePopup.value = remainingTop
        }
        closed?.let { entry ->
            runCatching {
                runtime?.webExtensionController?.setTabActive(entry.session, false)
                entry.session.close()
            }
        }
        val rt = runtime
        if (remainingTop != null) {
            runCatching { rt?.webExtensionController?.setTabActive(remainingTop!!.session, true) }
        } else {
            runCatching {
                if (studioSession.isOpen) {
                    rt?.webExtensionController?.setTabActive(studioSession, true)
                    if (reloadStudioIfEmpty) {
                        studioSession.reload()
                    }
                }
            }
        }
    }

    fun closeTopPopupSession(): Boolean {
        val top = synchronized(popupStack) { popupStack.lastOrNull() } ?: return false
        closePopupById(top.id, reloadStudioIfEmpty = !top.isGoogleFlow)
        return true
    }

    fun closeAllPopupSessions(reloadStudioIfExtension: Boolean = false) {
        val toClose = mutableListOf<ActivePopupState>()
        var hadExtensionPopup = false
        synchronized(popupStack) {
            while (popupStack.isNotEmpty()) {
                val item = popupStack.removeLast()
                if (!item.isGoogleFlow) hadExtensionPopup = true
                toClose.add(item)
            }
            _activePopup.value = null
        }
        toClose.forEach { entry ->
            runCatching {
                runtime?.webExtensionController?.setTabActive(entry.session, false)
                entry.session.close()
            }
        }
        if (reloadStudioIfExtension && hadExtensionPopup && studioSession.isOpen) {
            runCatching {
                runtime?.webExtensionController?.setTabActive(studioSession, true)
                studioSession.reload()
            }
        }
    }

    fun openStreamHome(rt: GeckoRuntime) {
        if (!streamSession.isOpen) {
            streamSession.open(rt)
            runCatching { rt.webExtensionController.setTabActive(streamSession, true) }
            streamSession.loadUri(HOME_STREAM_URL)
        }
    }

    fun openStudioHome(rt: GeckoRuntime) {
        if (!studioSession.isOpen) {
            studioSession.open(rt)
            runCatching { rt.webExtensionController.setTabActive(studioSession, true) }
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
        isAwaitingFilePicker = false
        val cb = pendingFileCallback
        pendingFileCallback = null
        if (cb == null) return

        if (uris.isNullOrEmpty()) {
            cb.invoke(null)
            return
        }

        // Stage picked SAF content:// URIs into our private app cache on a background IO thread
        // and pass native file:// URIs to GeckoView's FilePrompt.confirm().
        // This completely bypasses Android 11-16 Scoped Storage failures in GeckoView's IntentUtils!
        _events.tryEmit(GeckoEvent.StudioProToast("Preparing file for YouTube Studio upload..."))
        ioExecutor.execute {
            val stagedUris = stageUrisToPrivateCache(uris)
            mainHandler.post {
                if (stagedUris.isNotEmpty()) {
                    _events.tryEmit(GeckoEvent.StudioProToast("File ready — starting YouTube Studio upload"))
                    cb.invoke(stagedUris)
                } else {
                    _events.tryEmit(GeckoEvent.StudioProToast("Could not read selected file — try another file"))
                    cb.invoke(null)
                }
            }
        }
    }

    private fun stageUrisToPrivateCache(uris: Array<Uri>): Array<Uri> {
        val stageDir = File(context.cacheDir, "studio_uploads").apply { mkdirs() }
        val result = mutableListOf<Uri>()

        for (uri in uris) {
            if ("file".equals(uri.scheme, ignoreCase = true)) {
                result.add(uri)
                continue
            }
            val displayName = resolveDisplayName(uri)
            val safeName = displayName
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .takeLast(90)
                .ifBlank { "upload_${System.currentTimeMillis()}.mp4" }
            val destFile = File(stageDir, "${System.currentTimeMillis()}_$safeName")

            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                    destFile.exists() && destFile.length() > 0L
                } ?: false
            }.getOrElse { err ->
                Log.e(TAG, "Failed to stage content URI for Studio upload: $uri", err)
                false
            }

            if (copied) {
                result.add(Uri.fromFile(destFile))
            } else {
                // Last-resort fallback to original URI if stream copy failed
                runCatching { destFile.delete() }
                result.add(uri)
            }
        }
        return result.toTypedArray()
    }

    private fun resolveDisplayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        }
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() } ?: "mp4"
        return "studio_upload_${System.currentTimeMillis()}.$ext"
    }

    private fun cleanupOldStagedUploads() {
        ioExecutor.execute {
            runCatching {
                val stageDir = File(context.cacheDir, "studio_uploads")
                if (!stageDir.exists()) return@runCatching
                val cutoff = System.currentTimeMillis() - 12L * 60L * 60L * 1000L
                stageDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
        }
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
                val isNewWindowTarget = request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW

                // Let extension/auth popups targeting a new window pass through to onNewSession()
                // so GeckoView preserves window.opener and postMessage!
                if (isNewWindowTarget && (isExtensionTrustedUrl(uri) || uri == "about:blank" || (isStudio && isStudioOrAuthHost(uri)))) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                // Allow embedded moz-extension:// resources (e.g., vidIQ / SponsorBlock frames)
                if (uri.startsWith("moz-extension://", ignoreCase = true)) {
                    return if (request.hasUserGesture && !isNewWindowTarget) {
                        openExtensionPopupUrl(uri, resolveExtensionPopupTitle(uri))
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    } else {
                        GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }
                }

                if (isExtensionTrustedUrl(uri)) {
                    // User tapped an extension sign-in/signup link (e.g., vidIQ Log in / Sign up)
                    if (request.hasUserGesture || request.isDirectNavigation) {
                        openExtensionPopupUrl(uri, resolveExtensionPopupTitle(uri))
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    // Embedded extension iframe/API frame inside Studio: allow cleanly
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                if (!isAllowedHost(uri)) {
                    _events.tryEmit(GeckoEvent.BlockedExternalNavigation(uri))
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (isStudio) {
                    // Hardcoded PC/Desktop Studio: user-clicked YouTube watch/shorts/channel links
                    // redirect automatically to the Mobile View YouTube Stream tab (keep embedded iframes intact!)
                    if (isYouTubeWatchOrChannelUrl(uri)) {
                        val parsedPath = runCatching { Uri.parse(uri).path?.lowercase().orEmpty() }.getOrDefault("")
                        if (parsedPath.startsWith("/embed/") || parsedPath.startsWith("/upload")) {
                            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                        }
                        if (request.hasUserGesture || request.isDirectNavigation) {
                            val mobileUrl = toMobileYouTubeUrl(uri)
                            _events.tryEmit(GeckoEvent.OpenVideoInMobileStream(mobileUrl))
                            return GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }
                    // Keep Studio session strictly on studio.youtube.com & Google auth/upload infra
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
                val rt = runtime
                if (rt != null && (uri.isBlank() || uri == "about:blank" || isExtensionTrustedUrl(uri) || (isStudio && isStudioOrAuthHost(uri)))) {
                    val popup = openSyncedPopupSession(
                        rt = rt,
                        initialUrl = uri,
                        title = resolveExtensionPopupTitle(uri),
                        isGoogleFlow = false,
                        autoLoadInitialUrl = false,
                        sourceExtension = null
                    )
                    return GeckoResult.fromValue(popup)
                }
                if (isStudio) {
                    if (isYouTubeWatchOrChannelUrl(uri)) {
                        _events.tryEmit(GeckoEvent.OpenVideoInMobileStream(toMobileYouTubeUrl(uri)))
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

        attachFilePromptDelegate(session)
    }

    private fun attachFilePromptDelegate(session: GeckoSession) {
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                // Cleanly dismiss any stale previous picker callback before opening a new one
                pendingFileCallback?.invoke(null)
                pendingFileCallback = null

                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val sanitizedMimes = sanitizeMimeTypesForAndroidPicker(prompt.mimeTypes)
                val allowMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
                isAwaitingFilePicker = true

                pendingFileCallback = { stagedUris ->
                    isAwaitingFilePicker = false
                    runCatching {
                        if (stagedUris.isNullOrEmpty()) {
                            result.complete(prompt.dismiss())
                        } else if (allowMultiple) {
                            result.complete(prompt.confirm(context, stagedUris))
                        } else {
                            result.complete(prompt.confirm(context, stagedUris.first()))
                        }
                    }.onFailure { err ->
                        Log.e(TAG, "FilePrompt confirm failed", err)
                        runCatching { result.complete(prompt.dismiss()) }
                    }
                }
                _events.tryEmit(
                    GeckoEvent.FileUploadRequested(
                        mimeTypes = sanitizedMimes,
                        allowMultiple = allowMultiple,
                        onResult = { uris -> deliverFilePickerResult(uris) }
                    )
                )
                return result
            }
        }
    }

    /**
     * YouTube Studio's file input often sends raw extensions like ".mp4", ".mov",
     * ".mkv", ".srt" which cause Android's ACTION_OPEN_DOCUMENT picker to grey out all files or fail.
     * Convert extensions into valid MIME families and always fall back safely to wildcard MIME.
     */
    private fun sanitizeMimeTypesForAndroidPicker(raw: Array<out String>?): Array<String> {
        if (raw.isNullOrEmpty()) return arrayOf("*/*")
        val normalized = LinkedHashSet<String>()
        var hasUnknownOrWildcard = false

        raw.flatMap { it.split(",") }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .forEach { token ->
                when {
                    token == "*/*" || token == "*" -> hasUnknownOrWildcard = true
                    token.contains("/") -> normalized.add(token)
                    token in setOf(".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".3gp", ".wmv", ".m4v", ".mpeg", ".mpg", ".mpegps") ->
                        normalized.add("video/*")
                    token in setOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp") ->
                        normalized.add("image/*")
                    token in setOf(".mp3", ".m4a", ".wav", ".ogg", ".flac", ".aac") ->
                        normalized.add("audio/*")
                    else -> hasUnknownOrWildcard = true
                }
            }

        return if (hasUnknownOrWildcard || normalized.isEmpty()) {
            arrayOf("*/*")
        } else {
            normalized.toTypedArray()
        }
    }

    fun isExtensionTrustedUrl(rawUrl: String): Boolean {
        if (rawUrl.startsWith("moz-extension://", ignoreCase = true)) return true
        val host = runCatching { Uri.parse(rawUrl).host?.lowercase() }.getOrNull() ?: return false
        return EXTENSION_TRUSTED_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun resolveExtensionPopupTitle(rawUrl: String, fallbackName: String = ""): String {
        val lower = rawUrl.lowercase()
        return when {
            lower.contains("vidiq") || lower.contains("vid.io") -> "vidIQ Account (Synced Session)"
            lower.contains("sponsor") || lower.contains("dearrow") -> "SponsorBlock / DeArrow"
            lower.contains("accounts.google.com") -> "Google Sign-In (Synced Session)"
            lower.contains("moz-extension://") -> fallbackName.ifBlank { "Extension Settings" }
            fallbackName.isNotBlank() -> fallbackName
            else -> "Extension Sign-In (Synced Session)"
        }
    }

    fun isAllowedHost(rawUrl: String): Boolean {
        if (rawUrl.startsWith("about:") || rawUrl.startsWith("blob:") || rawUrl.startsWith("data:") || rawUrl.startsWith("moz-extension://")) {
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
        if (rawUrl.startsWith("about:") || rawUrl.startsWith("blob:") || rawUrl.startsWith("data:") || rawUrl.startsWith("moz-extension://")) {
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
        if (host == "studio.youtube.com" || host == "accounts.youtube.com" || host == "consent.youtube.com" || host == "upload.youtube.com") {
            return false
        }
        if (host == "youtu.be") return true
        if (host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com" || host == "music.youtube.com") {
            val path = parsed.path?.lowercase().orEmpty()
            // Never intercept internal sign-in, upload, embed, or redirect helper endpoints
            if (path.startsWith("/signin") || path.startsWith("/account") || path.startsWith("/logout") ||
                path.startsWith("/upload") || path.startsWith("/embed/")
            ) {
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
