package cc.hhhl.client.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking

object AiBackgroundScheduler {
    private const val ONE_TIME_WORK_NAME = "hhhl_ai_queue"

    fun syncNow(context: Context) {
        val appContext = context.applicationContext
        val sessions = runCatching { runBlocking { AndroidAuthTokenStore(appContext).readAccountSessions() } }
            .getOrDefault(emptyList())
        val accountId = sessions.firstOrNull { it.current }?.id ?: "default"
        val settings = AndroidAiStore(appContext).read(accountId).settings
        val request = OneTimeWorkRequestBuilder<AiBackgroundWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (settings.wifiOnlyBackground) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    )
                    .build(),
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
