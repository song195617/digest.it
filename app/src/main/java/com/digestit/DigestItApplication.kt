package com.digestit

import android.app.Application
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DigestItApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        // Install BEFORE super.attachBaseContext() so we catch Hilt init crashes too
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.processing_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of content transcription and processing"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
