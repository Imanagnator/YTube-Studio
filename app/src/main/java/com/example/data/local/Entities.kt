package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class MediaCategory {
    AUDIO,
    VIDEO,
    RECORDING
}

@Entity(tableName = "downloads")
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val sourceUrl: String,
    val formatLabel: String,
    val extension: String,
    val category: MediaCategory,
    val status: DownloadStatus,
    val progressPercent: Int = 0,
    val etaSeconds: Long = -1L,
    val fileSizeBytes: Long = 0L,
    val filePath: String? = null,
    val contentUri: String? = null,
    val thumbnailUrl: String? = null,
    val errorMessage: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

enum class NotificationCategory {
    PLAYBACK,
    DOWNLOAD,
    RECORDING,
    NETWORK,
    SYSTEM
}

@Entity(tableName = "notifications")
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val message: String,
    val category: NotificationCategory,
    val isRead: Boolean = false,
    val actionTarget: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_dns")
data class CustomDnsEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryIp: String,
    val dohUrl: String,
    val createdAtMs: Long = System.currentTimeMillis()
)
