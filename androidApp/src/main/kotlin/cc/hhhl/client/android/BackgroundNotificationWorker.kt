package cc.hhhl.client.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackgroundNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val result = BackgroundNotificationSyncer(applicationContext).sync()
        if (AndroidBackgroundNotificationStore(applicationContext).isBackgroundSyncEnabled()) {
            if (!RealtimeNotificationService.tryStart(applicationContext)) {
                BackgroundNotificationScheduler.syncSoon(applicationContext)
            }
        }
        return when (result) {
            BackgroundNotificationSyncResult.Success -> Result.success()
            BackgroundNotificationSyncResult.Retry -> Result.retry()
        }
    }
}
