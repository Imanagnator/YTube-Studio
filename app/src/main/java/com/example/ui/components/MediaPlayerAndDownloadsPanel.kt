package com.example.ui.components

import android.text.format.Formatter
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.data.local.DownloadRecord
import com.example.data.local.DownloadStatus
import com.example.data.local.MediaCategory
import com.example.data.model.ActivePlaybackState
import com.example.data.model.PlaybackSource
import com.example.data.model.RecordingState
import com.example.service.OfflineAudioService
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.StudioAmber

@Composable
fun OfflineMediaPlayerCard(
    playback: ActivePlaybackState,
    completedAudioCount: Int,
    completedVideoCount: Int,
    totalLibraryBytes: Long,
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
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sleepMenuExpanded by remember { mutableStateOf(false) }
    var audioOnlyVideoMode by rememberSaveable { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (playback.source != PlaybackSource.NONE) SignalCrimson.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("media_player_deck")
    ) {
        if (playback.source == PlaybackSource.NONE) {
            // Clean Idle Player Deck + Library Storage Summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SignalCrimson.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SignalCrimson.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.LibraryMusic,
                                contentDescription = null,
                                tint = SignalCrimson,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text("Offline Media Player", style = MaterialTheme.typography.titleMedium)
                            val sizeText = Formatter.formatShortFileSize(context, totalLibraryBytes)
                            Text(
                                text = "$completedAudioCount Audio • $completedVideoCount Video • $sizeText",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricMint
                            )
                        }
                    }

                    if (completedAudioCount > 0) {
                        Button(
                            onClick = onPlayAllAudio,
                            colors = ButtonDefaults.buttonColors(containerColor = SignalCrimson),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("play_all_audio_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap any downloaded MP3, MP4, or Screen Recording below to play offline with queue, speed control, video view, and sleep timer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Active Audio / Video Now-Playing Deck
            Column(modifier = Modifier.padding(16.dp)) {
                // Embedded Hardware-Accelerated Video Surface for MP4 & Screen Recordings
                if (playback.source == PlaybackSource.OFFLINE_MEDIA && playback.isVideo && !audioOnlyVideoMode) {
                    val naturalRatio = if (playback.videoWidth > 0 && playback.videoHeight > 0) {
                        (playback.videoWidth.toFloat() / playback.videoHeight.toFloat()).coerceIn(0.65f, 2.0f)
                    } else {
                        16f / 9f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(naturalRatio)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .clickable { onTogglePlayPause() }
                            .testTag("offline_video_surface")
                    ) {
                        OfflineExoVideoSurface(
                            mediaItemId = playback.mediaItemId,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clickable { audioOnlyVideoMode = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Headphones,
                                    contentDescription = "Audio only mode",
                                    tint = ElectricMint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Audio Mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Header Metadata Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (playback.isVideo) SignalCrimson.copy(alpha = 0.18f)
                                else ElectricMint.copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!playback.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = playback.artworkUrl,
                                contentDescription = "Media artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = if (playback.isVideo) Icons.Filled.VideoFile else Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = if (playback.isVideo) SignalCrimson else ElectricMint,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = if (playback.source == PlaybackSource.OFFLINE_MEDIA) {
                                    ElectricMint.copy(alpha = 0.18f)
                                } else {
                                    SignalCrimson.copy(alpha = 0.18f)
                                }
                            ) {
                                Text(
                                    text = when {
                                        playback.source == PlaybackSource.GECKO_STREAM -> "LIVE STREAM"
                                        playback.isVideo -> "OFFLINE VIDEO"
                                        else -> "OFFLINE AUDIO"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (playback.source == PlaybackSource.OFFLINE_MEDIA) ElectricMint else SignalCrimson,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (playback.queueSize > 1) {
                                Text(
                                    text = "${playback.queueIndex + 1} / ${playback.queueSize}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = playback.title.ifBlank { "Now Playing" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = playback.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (playback.isVideo && audioOnlyVideoMode) {
                        IconButton(
                            onClick = { audioOnlyVideoMode = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Filled.Videocam,
                                contentDescription = "Show video",
                                tint = CyanTelemetry
                            )
                        }
                    }

                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_stop_button")
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close player",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Interactive Seek Slider for Offline Media
                if (playback.source == PlaybackSource.OFFLINE_MEDIA && playback.durationMs > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val displayPos = if (isScrubbing) scrubPositionMs else playback.positionMs.toFloat()
                    Slider(
                        value = displayPos.coerceIn(0f, playback.durationMs.toFloat()),
                        onValueChange = { value ->
                            isScrubbing = true
                            scrubPositionMs = value
                        },
                        onValueChangeFinished = {
                            onSeekTo(scrubPositionMs.toLong())
                            isScrubbing = false
                        },
                        valueRange = 0f..playback.durationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = SignalCrimson,
                            activeTrackColor = SignalCrimson,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_seek_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMs(displayPos.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDurationMs(playback.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Primary Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playback.source == PlaybackSource.OFFLINE_MEDIA) {
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_shuffle_button")
                        ) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playback.shuffleEnabled) ElectricMint else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_prev_button")
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                        }
                        IconButton(
                            onClick = { onSeekBy(-10_000L) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_rewind_10")
                        ) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = SignalCrimson,
                        modifier = Modifier
                            .size(54.dp)
                            .clickable { onTogglePlayPause() }
                            .testTag("player_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    if (playback.source == PlaybackSource.OFFLINE_MEDIA) {
                        IconButton(
                            onClick = { onSeekBy(10_000L) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_forward_10")
                        ) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
                        }
                        IconButton(
                            onClick = onSkipNext,
                            enabled = playback.hasNext,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_next_button")
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                tint = if (playback.hasNext) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                        IconButton(
                            onClick = onCycleRepeat,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_repeat_button")
                        ) {
                            val icon = if (playback.repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Filled.RepeatOne
                            } else {
                                Icons.Filled.Repeat
                            }
                            Icon(
                                icon,
                                contentDescription = "Repeat mode",
                                tint = if (playback.repeatMode != Player.REPEAT_MODE_OFF) ElectricMint else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Utility Chips: Playback Speed + Sleep Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playback.source == PlaybackSource.OFFLINE_MEDIA) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .clickable { onCycleSpeed() }
                                .testTag("player_speed_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Filled.Speed, contentDescription = null, tint = CyanTelemetry, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "${playback.playbackSpeed}x Speed",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Box {
                        val timerActive = playback.sleepTimerRemainingMs > 0L
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (timerActive) StudioAmber.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (timerActive) StudioAmber.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .clickable { sleepMenuExpanded = true }
                                .testTag("player_sleep_timer_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = if (timerActive) StudioAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (timerActive) {
                                        "Sleep ${formatDurationMs(playback.sleepTimerRemainingMs)}"
                                    } else {
                                        "Sleep Timer"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (timerActive) StudioAmber else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = sleepMenuExpanded,
                            onDismissRequest = { sleepMenuExpanded = false }
                        ) {
                            listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "60 minutes").forEach { (mins, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        sleepMenuExpanded = false
                                        onSetSleepTimer(mins)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineExoVideoSurface(
    mediaItemId: Long?,
    modifier: Modifier = Modifier
) {
    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }

    DisposableEffect(mediaItemId) {
        val tv = textureViewRef.value
        val player = OfflineAudioService.activePlayer
        if (tv != null && player != null) {
            player.setVideoTextureView(tv)
        }
        onDispose {
            val currentTv = textureViewRef.value
            val currentPlayer = OfflineAudioService.activePlayer
            if (currentTv != null && currentPlayer != null) {
                currentPlayer.clearVideoTextureView(currentTv)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { tv ->
                textureViewRef.value = tv
                OfflineAudioService.activePlayer?.setVideoTextureView(tv)
            }
        },
        update = { tv ->
            textureViewRef.value = tv
            OfflineAudioService.activePlayer?.setVideoTextureView(tv)
        },
        modifier = modifier
    )
}

@Composable
fun ScreenRecorderQuickCard(
    recordingState: RecordingState,
    record1080p: Boolean,
    recordWithMic: Boolean,
    onSelect1080p: (Boolean) -> Unit,
    onToggleMic: () -> Unit,
    onStartOrStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Video & Screen Recorder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Saves MP4 directly to Movies/YTubeStudio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onStartOrStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recordingState.isRecording) MaterialTheme.colorScheme.error else SignalCrimson
                    ),
                    modifier = Modifier.testTag("profile_record_toggle")
                ) {
                    Icon(
                        imageVector = if (recordingState.isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (recordingState.isRecording) "Stop (${recordingState.elapsedSeconds}s)" else "Record")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = record1080p,
                    onClick = { onSelect1080p(true) },
                    label = { Text("1080p FHD") }
                )
                FilterChip(
                    selected = !record1080p,
                    onClick = { onSelect1080p(false) },
                    label = { Text("720p HD") }
                )
                FilterChip(
                    selected = recordWithMic,
                    onClick = onToggleMic,
                    label = { Text(if (recordWithMic) "Mic: ON" else "Mic: OFF") }
                )
            }
        }
    }
}

@Composable
fun EnhancedDownloadItemCard(
    record: DownloadRecord,
    isCurrentlyPlaying: Boolean,
    isActivelyPlayingAudio: Boolean,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrentlyPlaying) SignalCrimson.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (isCurrentlyPlaying) SignalCrimson else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = record.status == DownloadStatus.COMPLETED) { onPlay() }
            .testTag("download_item_${record.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (record.category == MediaCategory.AUDIO) ElectricMint.copy(alpha = 0.15f)
                            else SignalCrimson.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!record.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = record.thumbnailUrl,
                            contentDescription = "Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (record.category == MediaCategory.AUDIO) Icons.Filled.AudioFile else Icons.Filled.VideoFile,
                            contentDescription = null,
                            tint = if (record.category == MediaCategory.AUDIO) ElectricMint else SignalCrimson
                        )
                    }
                    if (isCurrentlyPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isActivelyPlayingAudio) Icons.Filled.GraphicEq else Icons.Filled.Pause,
                                contentDescription = "Currently selected",
                                tint = ElectricMint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isCurrentlyPlaying) SignalCrimson else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaText = when (record.status) {
                        DownloadStatus.COMPLETED -> {
                            val sizeStr = if (record.fileSizeBytes > 0) {
                                " • ${Formatter.formatShortFileSize(context, record.fileSizeBytes)}"
                            } else ""
                            "${record.formatLabel}$sizeStr"
                        }
                        DownloadStatus.DOWNLOADING -> {
                            val etaStr = if (record.etaSeconds > 0) " • ETA ${record.etaSeconds}s" else ""
                            "${record.formatLabel} • ${record.progressPercent}%$etaStr"
                        }
                        DownloadStatus.PROCESSING -> "Converting / muxing via FFmpeg..."
                        DownloadStatus.QUEUED -> "Queued..."
                        DownloadStatus.FAILED -> record.errorMessage ?: "Download failed"
                        DownloadStatus.CANCELLED -> "Cancelled"
                    }
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (record.status) {
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING -> CyanTelemetry
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when (record.status) {
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onPlay, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(
                                imageVector = if (isCurrentlyPlaying && isActivelyPlayingAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = ElectricMint
                            )
                        }
                        IconButton(onClick = onShare, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DownloadStatus.QUEUED,
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.PROCESSING -> {
                        IconButton(onClick = onCancel, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    }
                    DownloadStatus.FAILED,
                    DownloadStatus.CANCELLED -> {
                        if (!record.sourceUrl.startsWith("screen://")) {
                            IconButton(onClick = onRetry, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Retry download", tint = CyanTelemetry)
                            }
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }

            if (record.status == DownloadStatus.DOWNLOADING || record.status == DownloadStatus.PROCESSING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (record.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = ElectricMint,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}
