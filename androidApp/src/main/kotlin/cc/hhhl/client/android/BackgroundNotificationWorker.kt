package cc.hhhl.client.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackgroundNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return when (BackgroundNotificationSyncer(applicationContext).sync()) {
            BackgroundNotificationSyncResult.Success -> Result.success()
            BackgroundNotificationSyncResult.Retry -> Result.retry()
        }
    }
}
