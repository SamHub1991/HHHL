package cc.hhhl.client.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.hhhl.client.ai.AiPromptBuilder
import cc.hhhl.client.ai.AiRepository
import cc.hhhl.client.ai.AiRepositoryResult
import cc.hhhl.client.ai.AiSnapshot
import cc.hhhl.client.ai.AiTask
import cc.hhhl.client.ai.AiTaskStatus
import cc.hhhl.client.ai.AiSettings
import cc.hhhl.client.ai.AiUsageWindow
import cc.hhhl.client.ai.mergeStoredAiTasks
import cc.hhhl.client.ai.mergeStoredAiUsage
import cc.hhhl.client.ai.consumeAiRequest
import cc.hhhl.client.ai.modelForTask
import cc.hhhl.client.ai.normalizedAiUsage
import cc.hhhl.client.ai.settingsForTask

class AiBackgroundWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = AndroidAiStore(applicationContext)
        val sessions = AndroidAuthTokenStore(applicationContext).readAccountSessions()
        val accountIds = sessions.map { it.id }.ifEmpty { listOf("default") }
        val sessionById = sessions.associateBy { it.id }
        var retriableFailure = false
        var hasRemainingPendingTasks = false

        accountIds.forEach { accountId ->
            val session = sessionById[accountId]
            val repository = AiRepository(
                remoteTokenProvider = { session?.token },
                remoteBaseUrlProvider = { "https://${session?.host?.ifBlank { "dc.hhhl.cc" } ?: "dc.hhhl.cc"}" },
            )
            val snapshot = store.read(accountId)
            val settings = snapshot.settings
            if (!settings.enabled || !settings.backgroundAllowed) return@forEach
            if (settings.wifiOnlyBackground && !applicationContext.isOnUnmeteredNetwork()) {
                retriableFailure = true
                return@forEach
            }
            var tasks = snapshot.tasks
            var usage = snapshot.usage.normalizedAiUsage()
            val pending = tasks
                .filter { it.status == AiTaskStatus.Pending || it.status == AiTaskStatus.Running }
                .sortedBy { it.createdAtEpochMillis }
                .take(MAX_BACKGROUND_TASKS_PER_RUN)
            pending.forEach { task ->
                var taskForRun = task
                if (!task.usageCharged) {
                    val usageResult = usage.consumeAiRequest(settings)
                    val usageErrorMessage = usageResult.errorMessage
                    if (usageErrorMessage != null) {
                        usage = usageResult.usage
                        tasks = tasks.replaceTask(
                            task.copy(
                                status = AiTaskStatus.Pending,
                                errorMessage = usageErrorMessage,
                                updatedAtEpochMillis = System.currentTimeMillis(),
                            ),
                        )
                        store.writeMergedSnapshot(accountId, settings, tasks, usage)
                        return@forEach
                    }
                    usage = usageResult.usage
                    taskForRun = task.copy(usageCharged = true)
                }
                val now = System.currentTimeMillis()
                tasks = tasks.replaceTask(taskForRun.copy(status = AiTaskStatus.Running, updatedAtEpochMillis = now))
                store.writeMergedSnapshot(accountId, settings, tasks, usage)

                val taskSettings = settings.settingsForTask(taskForRun.kind)
                val prompt = AiPromptBuilder.build(taskSettings, taskForRun.kind, taskForRun.input)
                val nextTask = when (val result = repository.complete(taskSettings, prompt, model = taskSettings.modelForTask(taskForRun.kind))) {
                    is AiRepositoryResult.Success -> taskForRun.copy(
                        status = AiTaskStatus.Completed,
                        resultText = result.text.take(4_000),
                        errorMessage = "",
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    )
                    AiRepositoryResult.Unauthorized -> taskForRun.copy(
                        status = AiTaskStatus.Failed,
                        errorMessage = "AI API Key 无效或权限不足",
                        updatedAtEpochMillis = System.currentTimeMillis(),
                        retryCount = taskForRun.retryCount + 1,
                    )
                    is AiRepositoryResult.Error -> {
                        retriableFailure = true
                        taskForRun.copy(
                            status = if (taskForRun.retryCount < 2) AiTaskStatus.Pending else AiTaskStatus.Failed,
                            errorMessage = result.message,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                            retryCount = taskForRun.retryCount + 1,
                        )
                    }
                }
                tasks = tasks.replaceTask(nextTask)
                store.writeMergedSnapshot(accountId, settings, tasks, usage)
            }
            if (tasks.any { task -> task.status == AiTaskStatus.Pending || task.status == AiTaskStatus.Running }) {
                hasRemainingPendingTasks = true
            }
        }

        if (!retriableFailure && hasRemainingPendingTasks) {
            AiBackgroundScheduler.syncNow(applicationContext)
        }
        return if (retriableFailure) Result.retry() else Result.success()
    }

    private fun AndroidAiStore.writeMergedSnapshot(
        accountId: String,
        fallbackSettings: AiSettings,
        tasks: List<AiTask>,
        usage: AiUsageWindow,
    ) {
        update(accountId) { storedSnapshot ->
            AiSnapshot(
                settings = storedSnapshot.settings.takeUnless { it == AiSettings() } ?: fallbackSettings,
                tasks = mergeStoredAiTasks(
                    current = storedSnapshot.tasks,
                    updates = tasks,
                ),
                usage = mergeStoredAiUsage(
                    current = storedSnapshot.usage,
                    update = usage,
                ),
            )
        }
    }

    private fun List<AiTask>.replaceTask(task: AiTask): List<AiTask> {
        return map { current -> if (current.id == task.id) task else current }
    }

    private fun Context.isOnUnmeteredNetwork(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private companion object {
        const val MAX_BACKGROUND_TASKS_PER_RUN = 8
    }
}
