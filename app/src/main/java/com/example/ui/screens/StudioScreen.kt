package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.UserPrefs
import com.example.data.model.StudioShortcut
import com.example.gecko.BrowserTabState
import com.example.ui.components.GeckoSessionHost
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.StudioAmber
import org.mozilla.geckoview.GeckoSession

@Composable
fun StudioScreen(
    session: GeckoSession,
    isEngineReady: Boolean,
    isFullScreen: Boolean,
    tabState: BrowserTabState,
    prefs: UserPrefs,
    shortcuts: List<StudioShortcut>,
    unreadNotifications: Int,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onSelectShortcut: (StudioShortcut) -> Unit,
    onToggleKeepAwake: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (!isFullScreen) {
        // Dedicated Hardcoded Desktop YouTube Studio Command Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            enabled = tabState.canGoBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("studio_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Studio Back",
                                tint = if (tabState.canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(
                            onClick = onReload,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("studio_reload_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload Studio")
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanTelemetry.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, CyanTelemetry.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DesktopWindows,
                                    contentDescription = "Desktop Mode",
                                    tint = CyanTelemetry,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "PC STUDIO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanTelemetry,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Keep Screen Awake during long uploads
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (prefs.keepScreenAwakeUpload) StudioAmber.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (prefs.keepScreenAwakeUpload) StudioAmber.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { onToggleKeepAwake(!prefs.keepScreenAwakeUpload) }
                                .testTag("studio_keep_awake_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ScreenLockPortrait,
                                    contentDescription = "Keep Screen Awake",
                                    tint = if (prefs.keepScreenAwakeUpload) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (prefs.keepScreenAwakeUpload) "AWAKE" else "SLEEP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (prefs.keepScreenAwakeUpload) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifications > 0) {
                                        Badge(containerColor = SignalCrimson) {
                                            Text(unreadNotifications.coerceAtMost(99).toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                            }
                        }
                    }
                }

                // Quick-Nav Studio Shortcuts Bar (Upload, Dashboard, Content, Analytics, Comments, Subtitles, Earn)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    shortcuts.forEach { shortcut ->
                        val isUpload = shortcut.id == "upload"
                        val urlLower = tabState.currentUrl.lowercase()
                        val isActive = when (shortcut.id) {
                            "content" -> urlLower.contains("/videos") && !urlLower.contains("d=ud")
                            "analytics" -> urlLower.contains("/analytics")
                            "comments" -> urlLower.contains("/comments")
                            "subtitles" -> urlLower.contains("/translations")
                            "earn" -> urlLower.contains("/monetization")
                            "dashboard" -> urlLower.contains("studio.youtube.com") &&
                                !urlLower.contains("/videos") &&
                                !urlLower.contains("/analytics") &&
                                !urlLower.contains("/comments") &&
                                !urlLower.contains("/translations") &&
                                !urlLower.contains("/monetization")
                            else -> false
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isUpload -> SignalCrimson
                                isActive -> MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = if (isUpload) null else BorderStroke(
                                1.dp,
                                if (isActive) ElectricMint.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .clickable { onSelectShortcut(shortcut) }
                                .testTag("studio_shortcut_${shortcut.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isUpload) {
                                    Icon(
                                        Icons.Filled.CloudUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = shortcut.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isUpload) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (tabState.isLoading && tabState.loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { tabState.loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = CyanTelemetry,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        }

        GeckoSessionHost(
            session = session,
            isEngineReady = isEngineReady,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
