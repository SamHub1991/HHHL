package cc.hhhl.client.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundNotificationScheduler {
    private const val PERIODIC_WORK_NAME = "hhhl_background_message_sync"
    private const val ONE_TIME_WORK_NAME = "hhhl_background_message_sync_now"
    private const val RECOVERY_WORK_NAME = "hhhl_background_message_sync_recovery"
    private const val RECOVERY_DELAY_SECONDS = 10L

    fun apply(
        context: Context,
        enabled: Boolean,
    ) {
        if (enabled) {
            schedule(context)
            syncNow(context)
            if (!RealtimeNotificationService.start(context)) {
                syncSoon(context)
            }
        } else {
            cancel(context)
            RealtimeNotificationService.stop(context)
        }
    }

    fun restoreIfEnabled(context: Context) {
        if (AndroidBackgroundNotificationStore(context.applicationContext).isBackgroundSyncEnabled()) {
            schedule(context)
            syncNow(context)
            if (!RealtimeNotificationService.tryStart(context)) {
                syncSoon(context)
            }
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
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun syncSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackgroundNotificationWorker>()
            .setInitialDelay(RECOVERY_DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RECOVERY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        workManager.cancelUniqueWork(RECOVERY_WORK_NAME)
    }

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
