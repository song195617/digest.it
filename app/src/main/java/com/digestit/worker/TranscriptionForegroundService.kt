package com.digestit.worker

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Placeholder foreground service declared in AndroidManifest.
 * The actual long-running work is performed by JobPollingWorker via WorkManager,
 * which uses ForegroundInfo internally. This service entry is required by the
 * manifest declaration for foregroundServiceType="dataSync".
 */
class TranscriptionForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
}
