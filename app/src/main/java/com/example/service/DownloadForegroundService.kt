package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.notifications.AppNotificationManager

class DownloadForegroundService : Service() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val ACTION_STOP = "com.tamalmitra.ytubestudio.action.STOP_DOWNLOAD_FG"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Downloading media..."
        val openIntent = PendingIntent.getActivity(
            this,
            30,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AppNotificationManager.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notif_ytmax)
            .setContentTitle("YTube Studio Downloader")
            .setContentText("Processing: $title")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    AppNotificationManager.NOTIF_ID_DOWNLOAD_FG,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(AppNotificationManager.NOTIF_ID_DOWNLOAD_FG, notification)
            }
        }.onFailure {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
