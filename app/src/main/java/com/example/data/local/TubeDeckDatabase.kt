package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class TubeDeckConverters {
    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.FAILED)

    @TypeConverter
    fun fromMediaCategory(value: MediaCategory): String = value.name

    @TypeConverter
    fun toMediaCategory(value: String): MediaCategory =
        runCatching { MediaCategory.valueOf(value) }.getOrDefault(MediaCategory.AUDIO)

    @TypeConverter
    fun fromNotificationCategory(value: NotificationCategory): String = value.name

    @TypeConverter
    fun toNotificationCategory(value: String): NotificationCategory =
        runCatching { NotificationCategory.valueOf(value) }.getOrDefault(NotificationCategory.SYSTEM)
}

@Database(
    entities = [
        DownloadRecord::class,
        AppNotificationEntity::class,
        CustomDnsEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(TubeDeckConverters::class)
abstract class TubeDeckDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun notificationDao(): NotificationDao
    abstract fun customDnsDao(): CustomDnsDao

    companion object {
        @Volatile
        private var INSTANCE: TubeDeckDatabase? = null

        fun getInstance(context: Context): TubeDeckDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TubeDeckDatabase::class.java,
                    "tubedeck.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
