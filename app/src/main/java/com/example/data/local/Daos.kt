package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord): Long

    @Update
    suspend fun update(record: DownloadRecord)

    @Query("UPDATE downloads SET status = :status, progressPercent = :progress, etaSeconds = :eta WHERE id = :id")
    suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Int, eta: Long)

    @Query("UPDATE downloads SET status = :status, progressPercent = 100, filePath = :filePath, contentUri = :contentUri, fileSizeBytes = :fileSize WHERE id = :id")
    suspend fun markCompleted(id: Long, status: DownloadStatus, filePath: String?, contentUri: String?, fileSize: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Long, status: DownloadStatus, error: String)

    @Query("UPDATE downloads SET status = :failedStatus, errorMessage = :error WHERE status IN ('QUEUED', 'DOWNLOADING', 'PROCESSING')")
    suspend fun resetInterruptedDownloads(
        failedStatus: DownloadStatus = DownloadStatus.FAILED,
        error: String = "Interrupted — tap Retry"
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAtMs DESC LIMIT 100")
    fun observeAll(): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AppNotificationEntity): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

@Dao
interface CustomDnsDao {
    @Query("SELECT * FROM custom_dns ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<CustomDnsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CustomDnsEntity)

    @Query("DELETE FROM custom_dns WHERE id = :id")
    suspend fun deleteById(id: String)
}
