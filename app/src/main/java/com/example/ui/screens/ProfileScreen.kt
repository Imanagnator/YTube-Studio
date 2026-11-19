package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.local.DownloadRecord
import com.example.data.local.DownloadStatus
import com.example.data.local.MediaCategory
import com.example.data.local.UserPrefs
import com.example.data.model.ActivePlaybackState
import com.example.data.model.DnsBenchmarkResult
import com.example.data.model.DnsProvider
import com.example.data.model.QualityOption
import com.example.data.model.RecordingState
import com.example.gecko.GeckoEngineManager
import com.example.gecko.UBlockStatus
import com.example.network.DynamicDnsStatus
import com.example.ui.components.AddCustomDnsDialog
import com.example.ui.components.EnhancedDownloadItemCard
import com.example.ui.components.OfflineMediaPlayerCard
import com.example.ui.components.ScreenRecorderQuickCard
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.StudioAmber

enum class ProfileSection(val label: String) {
    DOWNLOADS("Media & DL"),
    NETWORK("DNS"),
    SHIELD("Shield & App")
}

@Composable
fun ProfileScreen(
    prefs: UserPrefs,
    isLoggedIn: Boolean,
    unreadNotifications: Int,
    activePlayback: ActivePlaybackState,
    dnsProviders: List<DnsProvider>,
    dnsBenchmarks: Map<String, DnsBenchmarkResult>,
    dynamicDnsStatus: DynamicDnsStatus,
    isBenchmarkingDns: Boolean,
    downloads: List<DownloadRecord>,
    recordingState: RecordingState,
    uBlockStatus: UBlockStatus,
    isUpdatingYtDlp: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotifications: () -> Unit,
    onInAppSignIn: () -> Unit,
    onClearSessions: () -> Unit,
    onToggleAutoDns: (Boolean) -> Unit,
    onBenchmarkAllDns: () -> Unit,
    onConnectDns: (DnsProvider) -> Unit,
    onConnectFastestDns: () -> Unit,
    onAddCustomDns: (String, String, String) -> Unit,
    onDeleteCustomDns: (String) -> Unit,
    onPlayDownload: (DownloadRecord) -> Unit,
    onPlayAllAudio: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onStopPlayback: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onRetryDownload: (DownloadRecord) -> Unit,
    onDeleteDownload: (DownloadRecord) -> Unit,
    onStartOrStopRecording: (resolution1080p: Boolean, withMic: Boolean) -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleAutoUpdateExtensions: (Boolean) -> Unit,
    onUpdateUBlockFromGitHub: () -> Unit,
    onToggleBackgroundAudio: (Boolean) -> Unit,
    onSelectDefaultQuality: (QualityOption) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onUpdateYtDlp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeSection by rememberSaveable { mutableStateOf(ProfileSection.DOWNLOADS) }
    var showCustomDnsDialog by remember { mutableStateOf(false) }
    var librarySearchQuery by rememberSaveable { mutableStateOf("") }
    var downloadFilter by rememberSaveable { mutableStateOf<MediaCategory?>(null) }
    var record1080p by rememberSaveable { mutableStateOf(true) }
    var recordWithMic by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Command Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (activeSection) {
                                ProfileSection.DOWNLOADS -> "Media & Downloads"
                                ProfileSection.NETWORK -> "DNS & Network"
                                ProfileSection.SHIELD -> "YTube Studio"
                            },
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${prefs.activeDnsName} • uBlock Origin v${uBlockStatus.version}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricMint
                        )
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("profile_notifications_button")
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
                            Icon(Icons.Filled.Notifications, contentDescription = "Notification Center")
                        }
                    }
                }
            }

            // Section Selector Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileSection.entries.forEach { section ->
                        val selected = activeSection == section
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) SignalCrimson else MaterialTheme.colorScheme.surfaceContainer,
                            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { activeSection = section }
                                .testTag("profile_section_${section.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val icon = when (section) {
                                    ProfileSection.DOWNLOADS -> Icons.Filled.LibraryMusic
                                    ProfileSection.NETWORK -> Icons.Filled.Dns
                                    ProfileSection.SHIELD -> Icons.Filled.Shield
                                }
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = section.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            when (activeSection) {
                ProfileSection.NETWORK -> {
                    // DNS Switcher + Dynamic Auto-DNS Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Dns, contentDescription = null, tint = CyanTelemetry)
                                        Column {
                                            Text("DNS-over-HTTPS (DoH)", style = MaterialTheme.typography.titleLarge)
                                            Text(
                                                text = "Active: ${prefs.activeDnsName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricMint
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { showCustomDnsDialog = true },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("add_custom_dns_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add custom DoH DNS")
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                // Dynamic Auto-DNS Failover Control Panel
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (prefs.autoDnsEnabled) CyanTelemetry.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(
                                        1.dp,
                                        if (prefs.autoDnsEnabled) CyanTelemetry.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Dynamic Auto-DNS Failover",
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                if (dynamicDnsStatus.isChecking) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(12.dp),
                                                        strokeWidth = 1.8.dp,
                                                        color = CyanTelemetry
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (prefs.autoDnsEnabled) {
                                                    dynamicDnsStatus.statusLine
                                                } else {
                                                    "Paused • Manual server selection active"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (prefs.autoDnsEnabled) CyanTelemetry else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (prefs.autoDnsEnabled && !dynamicDnsStatus.lastSwitchReason.isNullOrBlank()) {
                                                Text(
                                                    text = "Last switch: ${dynamicDnsStatus.lastSwitchReason}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Switch(
                                            checked = prefs.autoDnsEnabled,
                                            onCheckedChange = onToggleAutoDns,
                                            modifier = Modifier.testTag("auto_dns_switch")
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onBenchmarkAllDns,
                                        enabled = !isBenchmarkingDns,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("test_all_dns_button")
                                    ) {
                                        if (isBenchmarkingDns) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Probing...")
                                        } else {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Test All DNS")
                                        }
                                    }
                                    Button(
                                        onClick = onConnectFastestDns,
                                        enabled = !isBenchmarkingDns,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricMint,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("connect_fastest_dns_button")
                                    ) {
                                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Connect Best")
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    dnsProviders.forEach { provider ->
                                        val isConnected = prefs.activeDnsId == provider.id
                                        val bench = dnsBenchmarks[provider.id]
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isConnected) ElectricMint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isConnected) ElectricMint else MaterialTheme.colorScheme.outline
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("dns_card_${provider.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = provider.name,
                                                            style = MaterialTheme.typography.titleSmall
                                                        )
                                                        if (provider.isAdBlocking) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = CyanTelemetry.copy(alpha = 0.16f)
                                                            ) {
                                                                Text(
                                                                    text = "AD-FREE",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = CyanTelemetry,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = "${provider.primaryIp} • ${provider.description}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (bench != null) {
                                                        Text(
                                                            text = if (bench.reachable) {
                                                                "Latency: ${bench.latencyMs} ms • Jitter: ${bench.jitterMs} ms"
                                                            } else {
                                                                "Unreachable on current network"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = when {
                                                                !bench.reachable -> MaterialTheme.colorScheme.error
                                                                bench.latencyMs < 50 -> ElectricMint
                                                                bench.latencyMs < 130 -> StudioAmber
                                                                else -> SignalCrimson
                                                            }
                                                        )
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (provider.isCustom && !isConnected) {
                                                        IconButton(onClick = { onDeleteCustomDns(provider.id) }) {
                                                            Icon(
                                                                Icons.Filled.DeleteOutline,
                                                                contentDescription = "Delete custom DNS",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    if (isConnected) {
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = ElectricMint.copy(alpha = 0.18f)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.CheckCircle,
                                                                    contentDescription = null,
                                                                    tint = ElectricMint,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Text(
                                                                    "ACTIVE",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = ElectricMint,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        OutlinedButton(
                                                            onClick = { onConnectDns(provider) },
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                            modifier = Modifier.testTag("connect_dns_${provider.id}")
                                                        ) {
                                                            Text("Connect")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ProfileSection.DOWNLOADS -> {
                    val completedItems = downloads.filter { it.status == DownloadStatus.COMPLETED }
                    val activeQueueItems = downloads.filter {
                        it.status == DownloadStatus.QUEUED ||
                            it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.PROCESSING
                    }
                    val audioCount = completedItems.count { it.category == MediaCategory.AUDIO }
                    val videoCount = completedItems.count { it.category != MediaCategory.AUDIO }
                    val totalBytes = completedItems.sumOf { it.fileSizeBytes }

                    // 1. Full Embedded Audio & Video Media Player Deck
                    item {
                        OfflineMediaPlayerCard(
                            playback = activePlayback,
                            completedAudioCount = audioCount,
                            completedVideoCount = videoCount,
                            totalLibraryBytes = totalBytes,
                            onPlayAllAudio = onPlayAllAudio,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeekTo = onSeekTo,
                            onSeekBy = onSeekBy,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                            onCycleSpeed = onCycleSpeed,
                            onSetSleepTimer = onSetSleepTimer,
                            onStop = onStopPlayback
                        )
                    }

                    // 2. Active Download Queue (only shown when downloads are running/queued)
                    if (activeQueueItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "Active Downloads (${activeQueueItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = CyanTelemetry
                            )
                        }
                        items(activeQueueItems, key = { "active_${it.id}" }) { item ->
                            EnhancedDownloadItemCard(
                                record = item,
                                isCurrentlyPlaying = false,
                                isActivelyPlayingAudio = false,
                                onPlay = {},
                                onCancel = { onCancelDownload(item.id) },
                                onRetry = { onRetryDownload(item) },
                                onDelete = { onDeleteDownload(item) },
                                onShare = {}
                            )
                        }
                    }

                    // 4. Library Search & Category Filter Chips
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (downloads.size >= 3 || librarySearchQuery.isNotBlank()) {
                                OutlinedTextField(
                                    value = librarySearchQuery,
                                    onValueChange = { librarySearchQuery = it },
                                    placeholder = { Text("Search downloads & recordings...") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (librarySearchQuery.isNotBlank()) {
                                            IconButton(onClick = { librarySearchQuery = "" }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("library_search_input")
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = downloadFilter == null,
                                    onClick = { downloadFilter = null },
                                    label = { Text("All (${downloads.size})") }
                                )
                                FilterChip(
                                    selected = downloadFilter == MediaCategory.AUDIO,
                                    onClick = { downloadFilter = MediaCategory.AUDIO },
                                    label = { Text("Audio / MP3 ($audioCount)") }
                                )
                                FilterChip(
                                    selected = downloadFilter == MediaCategory.VIDEO,
                                    onClick = { downloadFilter = MediaCategory.VIDEO },
                                    label = { Text("Videos") }
                                )
                                FilterChip(
                                    selected = downloadFilter == MediaCategory.RECORDING,
                                    onClick = { downloadFilter = MediaCategory.RECORDING },
                                    label = { Text("Recordings") }
                                )
                            }
                        }
                    }

                    val filtered = downloads
                        .filter { it !in activeQueueItems }
                        .filter { downloadFilter == null || it.category == downloadFilter }
                        .filter {
                            librarySearchQuery.isBlank() ||
                                it.title.contains(librarySearchQuery.trim(), ignoreCase = true) ||
                                it.formatLabel.contains(librarySearchQuery.trim(), ignoreCase = true)
                        }

                    if (filtered.isEmpty() && activeQueueItems.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Filled.Headphones,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No downloaded media yet", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Tap the Download button on any YouTube video in the Stream tab to save MP3 audio or MP4 video offline.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { item ->
                            val isSelectedTrack = activePlayback.mediaItemId == item.id
                            EnhancedDownloadItemCard(
                                record = item,
                                isCurrentlyPlaying = isSelectedTrack,
                                isActivelyPlayingAudio = isSelectedTrack && activePlayback.isPlaying,
                                onPlay = {
                                    if (isSelectedTrack) onTogglePlayPause() else onPlayDownload(item)
                                },
                                onCancel = { onCancelDownload(item.id) },
                                onRetry = { onRetryDownload(item) },
                                onDelete = { onDeleteDownload(item) },
                                onShare = {
                                    val rawUri = item.contentUri
                                    if (rawUri.isNullOrBlank() || !rawUri.startsWith("content://")) {
                                        Toast.makeText(
                                            context,
                                            "Saved internally; media store share URI unavailable",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@EnhancedDownloadItemCard
                                    }
                                    val shareUri = Uri.parse(rawUri)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (item.category == MediaCategory.AUDIO) "audio/*" else "video/*"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(shareIntent, "Share ${item.title}"))
                                    }
                                }
                            )
                        }
                    }

                    // 5. Screen Recorder Quick Card
                    item {
                        ScreenRecorderQuickCard(
                            recordingState = recordingState,
                            record1080p = record1080p,
                            recordWithMic = recordWithMic,
                            onSelect1080p = { record1080p = it },
                            onToggleMic = { recordWithMic = !recordWithMic },
                            onStartOrStop = { onStartOrStopRecording(record1080p, recordWithMic) }
                        )
                    }
                }

                ProfileSection.SHIELD -> {
                    // Account & Login Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(SignalCrimson.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = SignalCrimson)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isLoggedIn) "YouTube Session Active" else "Sign in to YouTube & Studio",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "In-app login shares cookies with Stream & Desktop Studio.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onInAppSignIn,
                                        colors = ButtonDefaults.buttonColors(containerColor = SignalCrimson),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("in_app_login_button")
                                    ) {
                                        Text("In-App Sign In")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                CustomTabsIntent.Builder().build().launchUrl(
                                                    context,
                                                    Uri.parse(GeckoEngineManager.GOOGLE_LOGIN_URL)
                                                )
                                            }.onFailure {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(GeckoEngineManager.GOOGLE_LOGIN_URL))
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("external_browser_login_button")
                                    ) {
                                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Native Browser")
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Note: External browser login won't sync cookies to GeckoView.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onClearSessions) {
                                        Text("Clear Data", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // Shield, Background Audio, Quality & Theme Toggles
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Player & Shield Controls", style = MaterialTheme.typography.titleLarge)

                                SettingToggleRow(
                                    icon = Icons.Filled.Security,
                                    title = "uBlock Origin AdBlock (Mozilla)",
                                    subtitle = uBlockStatus.statusDetail,
                                    checked = prefs.adBlockEnabled,
                                    onCheckedChange = onToggleAdBlock,
                                    tag = "setting_adblock_switch"
                                )

                                SettingToggleRow(
                                    icon = Icons.Filled.AutoMode,
                                    title = "Auto-Update Extensions",
                                    subtitle = "Automatically check gorhill/uBlock GitHub releases every 24h",
                                    checked = prefs.autoUpdateExtensions,
                                    onCheckedChange = onToggleAutoUpdateExtensions,
                                    tag = "setting_autoupdate_ext_switch"
                                )

                                SettingToggleRow(
                                    icon = Icons.Filled.Headphones,
                                    title = "Background Audio Playback",
                                    subtitle = "Keep YouTube audio playing with notification controls when minimized",
                                    checked = prefs.backgroundAudioEnabled,
                                    onCheckedChange = onToggleBackgroundAudio,
                                    tag = "setting_bgaudio_switch"
                                )

                                SettingToggleRow(
                                    icon = Icons.Filled.DarkMode,
                                    title = "Obsidian Studio Dark Theme",
                                    subtitle = "High-contrast dark interface across app controls",
                                    checked = prefs.darkThemeEnabled,
                                    onCheckedChange = onToggleDarkTheme,
                                    tag = "setting_theme_switch"
                                )

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.HighQuality, contentDescription = null, tint = CyanTelemetry, modifier = Modifier.size(20.dp))
                                        Text("Default Stream Resolution", style = MaterialTheme.typography.titleSmall)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        QualityOption.entries.forEach { quality ->
                                            FilterChip(
                                                selected = prefs.preferredQuality == quality,
                                                onClick = { onSelectDefaultQuality(quality) },
                                                label = { Text(quality.label) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // yt-dlp Engine & Notification Permission Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Engine & System Permissions", style = MaterialTheme.typography.titleLarge)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("uBlock Origin (gorhill/uBlock)", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "v${uBlockStatus.version} • Signed Mozilla XPI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricMint
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onUpdateUBlockFromGitHub,
                                        enabled = !uBlockStatus.isUpdating,
                                        modifier = Modifier.testTag("update_ublock_github_button")
                                    ) {
                                        if (uBlockStatus.isUpdating) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (uBlockStatus.isUpdating) "Updating..." else "GitHub Update")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("yt-dlp + FFmpeg Extractor", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Version: ${prefs.ytDlpVersion}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricMint
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onUpdateYtDlp,
                                        enabled = !isUpdatingYtDlp,
                                        modifier = Modifier.testTag("update_ytdlp_button")
                                    ) {
                                        if (isUpdatingYtDlp) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isUpdatingYtDlp) "Updating..." else "Update Engine")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("System Notifications", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = if (hasNotificationPermission) "Granted • Alerts active" else "Permission needed on Android 13+",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (hasNotificationPermission) ElectricMint else StudioAmber
                                        )
                                    }
                                    if (!hasNotificationPermission) {
                                        Button(onClick = onRequestNotificationPermission) {
                                            Text("Allow")
                                        }
                                    } else {
                                        OutlinedButton(onClick = onOpenNotifications) {
                                            Text("Open Log")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // About & Author Credit Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = SignalCrimson)
                                    Column {
                                        Text("About YTube Studio", style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            text = "v${BuildConfig.VERSION_NAME} • Open-Source Release",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricMint
                                        )
                                    }
                                }
                                Text(
                                    text = "Created & maintained by ${BuildConfig.APP_AUTHOR}. Licensed under ${BuildConfig.APP_LICENSE}. Powered by Mozilla GeckoView, uBlock Origin, yt-dlp, FFmpeg, and AndroidX Media3.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Author: ${BuildConfig.APP_AUTHOR}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.APP_REPO_URL))
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("about_github_repo_button")
                                    ) {
                                        Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("GitHub Repo")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showCustomDnsDialog) {
        AddCustomDnsDialog(
            onDismiss = { showCustomDnsDialog = false },
            onAdd = onAddCustomDns
        )
    }
}

@Composable
private fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SignalCrimson, modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
