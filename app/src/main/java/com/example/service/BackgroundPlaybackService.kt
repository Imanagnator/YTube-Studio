package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R
import com.example.TubeDeckApplication
import com.example.notifications.AppNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BackgroundPlaybackService : Service() {

    companion object {
        const val ACTION_START = "com.tamalmitra.ytubestudio.action.BG_PLAYBACK_START"
        const val ACTION_TOGGLE = "com.tamalmitra.ytubestudio.action.BG_PLAYBACK_TOGGLE"
        const val ACTION_STOP = "com.tamalmitra.ytubestudio.action.BG_PLAYBACK_STOP"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        val app = application as TubeDeckApplication
        mediaSession = MediaSessionCompat(this, "TubeDeckStreamSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    app.geckoEngine.playLiveMedia()
                }

                override fun onPause() {
                    app.geckoEngine.pauseLiveMedia()
                }

                override fun onStop() {
                    app.geckoEngine.stopLiveMedia()
                    stopSelf()
                }
            })
            isActive = true
        }

        serviceScope.launch {
            app.geckoEngine.mediaState.collectLatest { media ->
                updateNotification(
                    title = media.title,
                    artist = media.artist,
                    isPlaying = media.isPlaying
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as TubeDeckApplication
        when (intent?.action) {
            ACTION_TOGGLE -> {
                app.geckoEngine.playPauseLiveMedia()
            }
            ACTION_STOP -> {
                app.geckoEngine.stopLiveMedia()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val state = app.geckoEngine.mediaState.value
        val notification = buildNotification(state.title, state.artist, state.isPlaying)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AppNotificationManager.NOTIF_ID_BG_PLAYBACK,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(AppNotificationManager.NOTIF_ID_BG_PLAYBACK, notification)
        }
        return START_STICKY
    }

    private fun updateNotification(title: String, artist: String, isPlaying: Boolean) {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()
        )
        val notification = buildNotification(title, artist, isPlaying)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager?.notify(AppNotificationManager.NOTIF_ID_BG_PLAYBACK, notification)
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): android.app.Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, BackgroundPlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, BackgroundPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val toggleLabel = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, AppNotificationManager.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notif_ytmax)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .addAction(toggleIcon, toggleLabel, toggleIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    override fun onDestroy() {
        mediaSession.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
