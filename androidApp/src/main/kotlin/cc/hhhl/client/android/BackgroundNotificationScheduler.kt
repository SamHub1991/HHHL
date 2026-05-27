package cc.hhhl.client.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundNotificationScheduler {
    private const val PERIODIC_WORK_NAME = "hhhl_background_message_sync"
    private const val ONE_TIME_WORK_NAME = "hhhl_background_message_sync_now"

    fun apply(
        context: Context,
        enabled: Boolean,
    ) {
        if (enabled) {
            RealtimeNotificationService.start(context)
            schedule(context)
            syncNow(context)
        } else {
            cancel(context)
            RealtimeNotificationService.stop(context)
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackgroundNotificationWorker>(
            15,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackgroundNotificationWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
