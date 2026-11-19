package com.example

import android.app.Application
import com.example.data.local.TubeDeckDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.downloader.MediaDownloadManager
import com.example.gecko.GeckoEngineManager
import com.example.network.DnsAndSpeedEngine
import com.example.notifications.AppNotificationManager

class TubeDeckApplication : Application() {

    val database: TubeDeckDatabase by lazy { TubeDeckDatabase.getInstance(this) }
    val prefsRepo: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val notificationManager: AppNotificationManager by lazy {
        AppNotificationManager(this, database.notificationDao())
    }
    val geckoEngine: GeckoEngineManager by lazy { GeckoEngineManager(this) }
    val dnsAndSpeedEngine: DnsAndSpeedEngine by lazy {
        DnsAndSpeedEngine(this)
    }
    val downloadManager: MediaDownloadManager by lazy {
        MediaDownloadManager(this, database.downloadDao(), notificationManager, prefsRepo)
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager.ensureChannels()
    }
}
