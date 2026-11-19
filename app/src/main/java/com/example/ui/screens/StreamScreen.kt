package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.UserPrefs
import com.example.data.model.QualityOption
import com.example.data.model.RecordingState
import com.example.gecko.BrowserTabState
import com.example.gecko.LiveQualityState
import com.example.gecko.UBlockStatus
import com.example.ui.components.GeckoSessionHost
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.SignalCrimson
import org.mozilla.geckoview.GeckoSession

@Composable
fun StreamScreen(
    session: GeckoSession,
    isEngineReady: Boolean,
    isFullScreen: Boolean,
    tabState: BrowserTabState,
    prefs: UserPrefs,
    uBlockStatus: UBlockStatus,
    liveQualityState: LiveQualityState,
    recordingState: RecordingState,
    unreadNotifications: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onOpenQualityMenu: () -> Unit,
    onSelectQuality: (QualityOption) -> Unit,
    onToggleBackgroundAudio: (Boolean) -> Unit,
    onToggleRecording: () -> Unit,
    onOpenDownloadSheet: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (!isFullScreen) {
        // Hardcoded locked YouTube control header — ZERO URL/address bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        enabled = tabState.canGoBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stream_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (tabState.canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(
                        onClick = onForward,
                        enabled = tabState.canGoForward,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stream_forward_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (tabState.canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(
                        onClick = onHome,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stream_home_button")
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = "YouTube Home")
                    }

                    IconButton(
                        onClick = onReload,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stream_reload_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload YouTube")
                    }

                    // uBlock Origin Shield Pill
                    val shieldColor by animateColorAsState(
                        if (prefs.adBlockEnabled) ElectricMint else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "shieldColor"
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (prefs.adBlockEnabled) ElectricMint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, shieldColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onToggleAdBlock(!prefs.adBlockEnabled) }
                            .testTag("stream_adblock_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = "uBlock Origin", tint = shieldColor, modifier = Modifier.size(15.dp))
                            Text(
                                text = if (prefs.adBlockEnabled) {
                                    if (uBlockStatus.isInstalled) "uBO" else "SHIELD"
                                } else {
                                    "OFF"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = shieldColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Live Manual Resolution Selector (backed by wrappedJSObject player API)
                    Box {
                        val liveLabel = liveQualityState.currentQuality
                        val pillLabel = when {
                            prefs.preferredQuality == QualityOption.AUTO && liveLabel != "Auto" -> "Auto ($liveLabel)"
                            liveLabel != "Auto" && !liveLabel.equals(prefs.preferredQuality.label, ignoreCase = true) ->
                                "${prefs.preferredQuality.label} → $liveLabel"
                            else -> prefs.preferredQuality.label
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    onOpenQualityMenu()
                                    qualityMenuExpanded = true
                                }
                                .testTag("stream_quality_selector")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.HighQuality,
                                    contentDescription = "Resolution",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = pillLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = qualityMenuExpanded,
                            onDismissRequest = { qualityMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                enabled = false,
                                text = {
                                    Text(
                                        text = if (liveQualityState.availableQualities.isNotEmpty()) {
                                            "Playing: ${liveQualityState.currentQuality} • Max: ${liveQualityState.availableQualities.first()}"
                                        } else {
                                            "Playing: ${liveQualityState.currentQuality}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricMint,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                onClick = {}
                            )
                            QualityOption.entries.forEach { option ->
                                val hasKnownList = liveQualityState.availableQualities.isNotEmpty()
                                val isAvailable = option == QualityOption.AUTO ||
                                    !hasKnownList ||
                                    liveQualityState.availableQualities.any { it.equals(option.label, ignoreCase = true) }
                                val isPlayingNow = option != QualityOption.AUTO &&
                                    liveQualityState.currentQuality.equals(option.label, ignoreCase = true)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (option == prefs.preferredQuality) "✓ ${option.label}" else option.label,
                                                fontWeight = if (option == prefs.preferredQuality) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isAvailable) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                }
                                            )
                                            if (isPlayingNow) {
                                                Text(
                                                    text = "LIVE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ElectricMint,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else if (!isAvailable) {
                                                Text(
                                                    text = "(fallback)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        qualityMenuExpanded = false
                                        onSelectQuality(option)
                                    }
                                )
                            }
                        }
                    }

                    // Background Audio Lock Toggle
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (prefs.backgroundAudioEnabled) SignalCrimson.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (prefs.backgroundAudioEnabled) SignalCrimson.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onToggleBackgroundAudio(!prefs.backgroundAudioEnabled) }
                            .testTag("stream_bg_audio_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Headphones,
                                contentDescription = "Background Audio",
                                tint = if (prefs.backgroundAudioEnabled) SignalCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "BG",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (prefs.backgroundAudioEnabled) SignalCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Screen / Video Recorder Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (recordingState.isRecording) SignalCrimson else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (recordingState.isRecording) SignalCrimson else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onToggleRecording() }
                            .testTag("stream_record_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (recordingState.isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                contentDescription = if (recordingState.isRecording) "Stop Recording" else "Record Video",
                                tint = if (recordingState.isRecording) MaterialTheme.colorScheme.onPrimary else SignalCrimson,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (recordingState.isRecording) {
                                    formatDurationMs(recordingState.elapsedSeconds * 1000L)
                                } else {
                                    "REC"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (recordingState.isRecording) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Download MP3 / Video Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onOpenDownloadSheet() }
                            .testTag("stream_download_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Download MP3 or Video",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "MP3/DL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Notification Bell
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stream_notifications_button")
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

                if (tabState.isLoading && tabState.loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { tabState.loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = SignalCrimson,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    )
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
