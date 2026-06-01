package cc.hhhl.client.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
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
    private const val WATCHDOG_DELAY_SECONDS = 120L
    private const val RECOVERY_ALARM_REQUEST_CODE = 22017

    fun apply(
        context: Context,
        enabled: Boolean,
    ) {
        if (enabled) {
            schedule(context)
            syncNow(context)
            if (RealtimeNotificationService.start(context)) {
                scheduleRecoveryAlarm(context, WATCHDOG_DELAY_SECONDS)
            } else {
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
            if (RealtimeNotificationService.tryStart(context)) {
                scheduleRecoveryAlarm(context, WATCHDOG_DELAY_SECONDS)
            } else {
                syncSoon(context)
            }
        }
    }

    fun watchdog(context: Context) {
        if (AndroidBackgroundNotificationStore(context.applicationContext).isBackgroundSyncEnabled()) {
            if (RealtimeNotificationService.tryStart(context)) {
                scheduleRecoveryAlarm(context, WATCHDOG_DELAY_SECONDS)
            } else {
                syncSoon(context)
            }
        } else {
            cancelRecoveryAlarm(context)
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
        scheduleRecoveryAlarm(context, RECOVERY_DELAY_SECONDS)
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        workManager.cancelUniqueWork(RECOVERY_WORK_NAME)
        cancelRecoveryAlarm(context)
    }

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    private fun scheduleRecoveryAlarm(context: Context, delaySeconds: Long) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = recoveryPendingIntent(appContext, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(delaySeconds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelRecoveryAlarm(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(recoveryPendingIntent(appContext, PendingIntent.FLAG_NO_CREATE) ?: return)
    }

    private fun recoveryPendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context.applicationContext, BackgroundNotificationRecoveryReceiver::class.java)
            .setAction(ACTION_BACKGROUND_NOTIFICATION_RECOVERY)
        val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(
            context.applicationContext,
            RECOVERY_ALARM_REQUEST_CODE,
            intent,
            flags or immutableFlag,
        )
    }
}

internal const val ACTION_BACKGROUND_NOTIFICATION_RECOVERY = "cc.hhhl.client.android.BACKGROUND_NOTIFICATION_RECOVERY"
