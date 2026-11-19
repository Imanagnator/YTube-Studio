package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppNotificationEntity
import com.example.data.local.NotificationCategory
import com.example.data.local.NotificationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppNotificationManager(
    private val context: Context,
    private val notificationDao: NotificationDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_PLAYBACK = "tubedeck_playback"
        const val CHANNEL_DOWNLOADS = "tubedeck_downloads"
        const val CHANNEL_RECORDING = "tubedeck_recording"
        const val CHANNEL_NETWORK = "tubedeck_network"

        const val NOTIF_ID_BG_PLAYBACK = 1001
        const val NOTIF_ID_OFFLINE_AUDIO = 1002
        const val NOTIF_ID_SCREEN_RECORD = 1003
        const val NOTIF_ID_DOWNLOAD_FG = 1004
    }

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_PLAYBACK,
                "Background Audio & Media",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active audio playback controls for YouTube and offline MP3s"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads & Conversions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "MP3, audio, and video download progress and completions"
                setShowBadge(true)
            },
            NotificationChannel(
                CHANNEL_RECORDING,
                "Screen Recorder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active screen and audio capture indicator"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_NETWORK,
                "DNS & Network Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "DNS benchmark, DoH connection, and auto-failover alerts"
                setShowBadge(true)
            }
        )
        manager.createNotificationChannels(channels)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun logAndNotify(
        title: String,
        message: String,
        category: NotificationCategory,
        postSystemNotification: Boolean = true,
        actionTarget: String? = null
    ) {
        scope.launch {
            notificationDao.insert(
                AppNotificationEntity(
                    title = title,
                    message = message,
                    category = category,
                    actionTarget = actionTarget
                )
            )
        }

        if (!postSystemNotification || !hasNotificationPermission()) return

        val channelId = when (category) {
            NotificationCategory.PLAYBACK -> CHANNEL_PLAYBACK
            NotificationCategory.DOWNLOAD -> CHANNEL_DOWNLOADS
            NotificationCategory.RECORDING -> CHANNEL_RECORDING
            NotificationCategory.NETWORK, NotificationCategory.SYSTEM -> CHANNEL_NETWORK
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            category.ordinal,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_ytmax)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100_000).toInt() + 2000, notification)
        }
    }

    fun postDownloadProgress(downloadId: Long, title: String, progress: Int, indeterminate: Boolean) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notif_ytmax)
            .setContentTitle(title)
            .setContentText(if (indeterminate) "Preparing stream..." else "Downloading • $progress%")
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(3000 + downloadId.toInt(), notification)
        }
    }

    fun cancelDownloadNotification(downloadId: Long) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(3000 + downloadId.toInt())
        }
    }
}
