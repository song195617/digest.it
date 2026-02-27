package com.digestit.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.digestit.R
import com.digestit.domain.model.ProcessingStatus
import com.digestit.domain.usecase.GetJobStatusUseCase
import com.digestit.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class JobPollingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val getJobStatus: GetJobStatusUseCase
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_STATUS = "status"
        const val KEY_PROGRESS = "progress"
        const val KEY_STEP = "current_step"
        const val KEY_EPISODE_ID = "episode_id"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 3_000L
        private const val MAX_POLLS = 400 // 20 minutes max
    }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        setForeground(createForegroundInfo("Initializing…", 0f))

        repeat(MAX_POLLS) { attempt ->
            val job = try {
                getJobStatus(jobId)
            } catch (e: Exception) {
                if (runAttemptCount < 3) return Result.retry()
                return Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
            }

            setProgress(
                workDataOf(
                    KEY_STATUS to job.status.name,
                    KEY_PROGRESS to job.progress,
                    KEY_STEP to job.currentStep
                )
            )
            setForeground(createForegroundInfo(job.currentStep, job.progress))

            when (job.status) {
                ProcessingStatus.COMPLETED -> {
                    showCompletionNotification(job.episodeId)
                    return Result.success(
                        workDataOf(KEY_EPISODE_ID to job.episodeId)
                    )
                }
                ProcessingStatus.FAILED -> {
                    showErrorNotification(job.errorMessage)
                    return Result.failure(
                        workDataOf("error" to (job.errorMessage ?: "Processing failed"))
                    )
                }
                else -> delay(POLL_INTERVAL_MS)
            }
        }
        return Result.retry()
    }

    private fun createForegroundInfo(step: String, progress: Float): ForegroundInfo {
        val notification = buildNotification(step, (progress * 100).toInt(), indeterminate = progress == 0f)
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(step: String, progressPct: Int, indeterminate: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
            .setContentTitle(context.getString(R.string.processing_notification_title))
            .setContentText(step)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setProgress(100, progressPct, indeterminate)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun showCompletionNotification(episodeId: String?) {
        val notification = NotificationCompat.Builder(
            context, context.getString(R.string.notification_channel_id)
        )
            .setContentTitle("处理完成")
            .setContentText("内容已转录完毕，点击查看")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID + 1, notification)
        nm.cancel(NOTIFICATION_ID)
    }

    private fun showErrorNotification(errorMessage: String?) {
        val notification = NotificationCompat.Builder(
            context, context.getString(R.string.notification_channel_id)
        )
            .setContentTitle("处理失败")
            .setContentText(errorMessage ?: "发生未知错误")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID + 2, notification)
        nm.cancel(NOTIFICATION_ID)
    }
}
