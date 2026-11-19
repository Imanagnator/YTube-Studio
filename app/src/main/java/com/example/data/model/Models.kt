package com.example.data.model

import androidx.compose.runtime.Immutable

enum class AppTab(val label: String) {
    STREAM("Stream"),
    STUDIO("YT Studio"),
    PROFILE("Media")
}

enum class QualityOption(val label: String, val ytQualityKey: String, val heightPx: Int) {
    AUTO("Auto", "auto", 0),
    UHD_4K("2160p", "hd2160", 2160),
    QHD("1440p", "hd1440", 1440),
    FHD("1080p", "hd1080", 1080),
    HD("720p", "hd720", 720),
    SD_480("480p", "large", 480),
    SD_360("360p", "medium", 360),
    LOW_240("240p", "small", 240),
    LOW_144("144p", "tiny", 144)
}

enum class DownloadFormatOption(
    val id: String,
    val label: String,
    val extension: String,
    val isAudioOnly: Boolean,
    val badge: String
) {
    MP3_320("mp3_320", "MP3 High (320 kbps)", "mp3", true, "320K"),
    MP3_192("mp3_192", "MP3 Standard (192 kbps)", "mp3", true, "192K"),
    M4A_BEST("m4a_best", "M4A AAC (Original)", "m4a", true, "AAC"),
    OPUS_BEST("opus_best", "Opus Audio (Best)", "opus", true, "OPUS"),
    FLAC_BEST("flac_best", "FLAC Lossless", "flac", true, "FLAC"),
    MP4_2160("mp4_2160", "MP4 4K (2160p)", "mp4", false, "4K"),
    MP4_1080("mp4_1080", "MP4 Full HD (1080p)", "mp4", false, "1080p"),
    MP4_720("mp4_720", "MP4 HD (720p)", "mp4", false, "720p"),
    MP4_480("mp4_480", "MP4 SD (480p)", "mp4", false, "480p"),
    MP4_360("mp4_360", "MP4 Compact (360p)", "mp4", false, "360p")
}

@Immutable
data class DnsProvider(
    val id: String,
    val name: String,
    val primaryIp: String,
    val dohUrl: String,
    val description: String,
    val isAdBlocking: Boolean = false,
    val isCustom: Boolean = false
)

@Immutable
data class DnsBenchmarkResult(
    val providerId: String,
    val latencyMs: Long,
    val jitterMs: Long,
    val reachable: Boolean,
    val testedAtMs: Long = System.currentTimeMillis()
)

@Immutable
data class StudioShortcut(
    val id: String,
    val label: String,
    val url: String
)

enum class PlaybackSource {
    NONE,
    GECKO_STREAM,
    OFFLINE_MEDIA
}

@Immutable
data class ActivePlaybackState(
    val source: PlaybackSource = PlaybackSource.NONE,
    val title: String = "",
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val mediaItemId: Long? = null,
    val isVideo: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val sleepTimerRemainingMs: Long = 0L
)

@Immutable
data class VideoMetadataPreview(
    val url: String,
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val availableHeights: List<Int>
)

sealed interface VideoProbeUiState {
    data object Idle : VideoProbeUiState
    data class Loading(val url: String) : VideoProbeUiState
    data class Ready(val preview: VideoMetadataPreview) : VideoProbeUiState
    data class Error(val message: String, val url: String) : VideoProbeUiState
}

@Immutable
data class RecordingState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val resolutionLabel: String = "1080p",
    val captureMicAudio: Boolean = true,
    val lastSavedPath: String? = null
)
