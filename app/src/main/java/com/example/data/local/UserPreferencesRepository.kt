package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.QualityOption
import com.example.gecko.UBlockOriginUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ytube_studio_prefs")

data class UserPrefs(
    val activeDnsId: String = "system",
    val activeDnsName: String = "System Default DNS",
    val activeDohUrl: String = "",
    val autoDnsEnabled: Boolean = true,
    val adBlockEnabled: Boolean = true,
    val autoUpdateExtensions: Boolean = true,
    val lastExtensionCheckMs: Long = 0L,
    val backgroundAudioEnabled: Boolean = true,
    val preferredQuality: QualityOption = QualityOption.AUTO,
    val studioDesktopZoomPercent: Int = 85,
    val keepScreenAwakeUpload: Boolean = true,
    val adsBlockedTotal: Long = 0L,
    val darkThemeEnabled: Boolean = true,
    val ytDlpVersion: String = "Bundled 0.18.1",
    val uBlockVersion: String = UBlockOriginUpdater.BUNDLED_VERSION,
    val disabledExtensionIds: Set<String> = emptySet(),
    val studioProEnabled: Boolean = true,
    val studioProAutoCollapseSidebar: Boolean = true,
    val studioProFocusMode: Boolean = false,
    val studioProSeoHudEnabled: Boolean = true,
    val studioProPrivacyBlur: Boolean = false,
    val studioProCustomTemplate: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private val bootPrefs = context.getSharedPreferences("ytube_studio_boot_prefs", Context.MODE_PRIVATE)

    private object Keys {
        val ACTIVE_DNS_ID = stringPreferencesKey("active_dns_id")
        val ACTIVE_DNS_NAME = stringPreferencesKey("active_dns_name")
        val ACTIVE_DOH_URL = stringPreferencesKey("active_doh_url")
        val AUTO_DNS_ENABLED = booleanPreferencesKey("auto_dns_enabled")
        val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val AUTO_UPDATE_EXTENSIONS = booleanPreferencesKey("auto_update_extensions")
        val LAST_EXTENSION_CHECK_MS = longPreferencesKey("last_extension_check_ms")
        val BACKGROUND_AUDIO_ENABLED = booleanPreferencesKey("background_audio_enabled")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val STUDIO_ZOOM_PERCENT = intPreferencesKey("studio_zoom_percent")
        val KEEP_AWAKE_UPLOAD = booleanPreferencesKey("keep_awake_upload")
        val ADS_BLOCKED_TOTAL = longPreferencesKey("ads_blocked_total")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val YTDLP_VERSION = stringPreferencesKey("ytdlp_version")
        val UBLOCK_VERSION = stringPreferencesKey("ublock_version")
        val DISABLED_EXTENSION_IDS = stringSetPreferencesKey("disabled_extension_ids")
        val STUDIO_PRO_ENABLED = booleanPreferencesKey("studio_pro_enabled")
        val STUDIO_PRO_AUTO_COLLAPSE = booleanPreferencesKey("studio_pro_auto_collapse")
        val STUDIO_PRO_FOCUS_MODE = booleanPreferencesKey("studio_pro_focus_mode")
        val STUDIO_PRO_SEO_HUD = booleanPreferencesKey("studio_pro_seo_hud")
        val STUDIO_PRO_PRIVACY_BLUR = booleanPreferencesKey("studio_pro_privacy_blur")
        val STUDIO_PRO_CUSTOM_TEMPLATE = stringPreferencesKey("studio_pro_custom_template")
    }

    fun getBootDohUrlSync(): String {
        return bootPrefs.getString("active_doh_url", "") ?: ""
    }

    val prefsFlow: Flow<UserPrefs> = context.dataStore.data.map { prefs ->
        val qualityName = prefs[Keys.PREFERRED_QUALITY] ?: QualityOption.AUTO.name
        UserPrefs(
            activeDnsId = prefs[Keys.ACTIVE_DNS_ID] ?: "system",
            activeDnsName = prefs[Keys.ACTIVE_DNS_NAME] ?: "System Default DNS",
            activeDohUrl = prefs[Keys.ACTIVE_DOH_URL] ?: "",
            autoDnsEnabled = prefs[Keys.AUTO_DNS_ENABLED] ?: true,
            adBlockEnabled = prefs[Keys.ADBLOCK_ENABLED] ?: true,
            autoUpdateExtensions = prefs[Keys.AUTO_UPDATE_EXTENSIONS] ?: true,
            lastExtensionCheckMs = prefs[Keys.LAST_EXTENSION_CHECK_MS] ?: 0L,
            backgroundAudioEnabled = prefs[Keys.BACKGROUND_AUDIO_ENABLED] ?: true,
            preferredQuality = runCatching { QualityOption.valueOf(qualityName) }.getOrDefault(QualityOption.AUTO),
            studioDesktopZoomPercent = prefs[Keys.STUDIO_ZOOM_PERCENT] ?: 85,
            keepScreenAwakeUpload = prefs[Keys.KEEP_AWAKE_UPLOAD] ?: true,
            adsBlockedTotal = prefs[Keys.ADS_BLOCKED_TOTAL] ?: 0L,
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
            ytDlpVersion = prefs[Keys.YTDLP_VERSION] ?: "Bundled 0.18.1",
            uBlockVersion = prefs[Keys.UBLOCK_VERSION] ?: UBlockOriginUpdater.BUNDLED_VERSION,
            disabledExtensionIds = prefs[Keys.DISABLED_EXTENSION_IDS] ?: emptySet(),
            studioProEnabled = prefs[Keys.STUDIO_PRO_ENABLED] ?: true,
            studioProAutoCollapseSidebar = prefs[Keys.STUDIO_PRO_AUTO_COLLAPSE] ?: true,
            studioProFocusMode = prefs[Keys.STUDIO_PRO_FOCUS_MODE] ?: false,
            studioProSeoHudEnabled = prefs[Keys.STUDIO_PRO_SEO_HUD] ?: true,
            studioProPrivacyBlur = prefs[Keys.STUDIO_PRO_PRIVACY_BLUR] ?: false,
            studioProCustomTemplate = prefs[Keys.STUDIO_PRO_CUSTOM_TEMPLATE] ?: ""
        )
    }

    suspend fun setActiveDns(id: String, name: String, dohUrl: String) {
        bootPrefs.edit().putString("active_doh_url", dohUrl).apply()
        context.dataStore.edit {
            it[Keys.ACTIVE_DNS_ID] = id
            it[Keys.ACTIVE_DNS_NAME] = name
            it[Keys.ACTIVE_DOH_URL] = dohUrl
        }
    }

    suspend fun setAutoDnsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DNS_ENABLED] = enabled }
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ADBLOCK_ENABLED] = enabled }
    }

    suspend fun setAutoUpdateExtensions(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE_EXTENSIONS] = enabled }
    }

    suspend fun setLastExtensionCheckMs(timestampMs: Long) {
        context.dataStore.edit { it[Keys.LAST_EXTENSION_CHECK_MS] = timestampMs }
    }

    suspend fun setBackgroundAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_AUDIO_ENABLED] = enabled }
    }

    suspend fun setPreferredQuality(quality: QualityOption) {
        context.dataStore.edit { it[Keys.PREFERRED_QUALITY] = quality.name }
    }

    suspend fun setStudioZoomPercent(percent: Int) {
        context.dataStore.edit { it[Keys.STUDIO_ZOOM_PERCENT] = percent.coerceIn(50, 130) }
    }

    suspend fun setKeepScreenAwakeUpload(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_AWAKE_UPLOAD] = enabled }
    }

    suspend fun incrementAdsBlocked(delta: Long = 1L) {
        context.dataStore.edit {
            val current = it[Keys.ADS_BLOCKED_TOTAL] ?: 0L
            it[Keys.ADS_BLOCKED_TOTAL] = current + delta
        }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setYtDlpVersion(version: String) {
        context.dataStore.edit { it[Keys.YTDLP_VERSION] = version }
    }

    suspend fun setUBlockVersion(version: String) {
        context.dataStore.edit { it[Keys.UBLOCK_VERSION] = version }
    }

    suspend fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DISABLED_EXTENSION_IDS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                current.remove(extensionId)
            } else {
                current.add(extensionId)
            }
            prefs[Keys.DISABLED_EXTENSION_IDS] = current
        }
    }

    suspend fun setStudioProEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STUDIO_PRO_ENABLED] = enabled }
    }

    suspend fun setStudioProAutoCollapseSidebar(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STUDIO_PRO_AUTO_COLLAPSE] = enabled }
    }

    suspend fun setStudioProFocusMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STUDIO_PRO_FOCUS_MODE] = enabled }
    }

    suspend fun setStudioProSeoHudEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STUDIO_PRO_SEO_HUD] = enabled }
    }

    suspend fun setStudioProPrivacyBlur(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STUDIO_PRO_PRIVACY_BLUR] = enabled }
    }

    suspend fun setStudioProCustomTemplate(template: String) {
        context.dataStore.edit { it[Keys.STUDIO_PRO_CUSTOM_TEMPLATE] = template }
    }
}
