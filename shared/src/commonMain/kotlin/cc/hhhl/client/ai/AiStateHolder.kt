package cc.hhhl.client.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class AiStateHolder(
    private val store: AiStore = NoopAiStore,
    private val accountId: String? = null,
    private val repository: AiRepository = AiRepository(),
    private val onQueueChanged: () -> Unit = {},
    private val scope: CoroutineScope,
) : AiBridge {
    private val mutableState = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = mutableState
    private var nextLocalId = 0

    fun restore() {
        val snapshot = runCatching { store.read(cleanAccountId()) }.getOrDefault(AiSnapshot())
        val sortedTasks = snapshot.tasks.sortedByDescending { task -> task.updatedAtEpochMillis }
        val latestCompletedTask = sortedTasks.firstOrNull { task -> task.status == AiTaskStatus.Completed }
        mutableState.update {
            it.copy(
                settings = snapshot.settings,
                tasks = sortedTasks,
                usage = snapshot.usage.normalizedAiUsage(),
                latestResult = latestCompletedTask?.takeUnless { task -> task.resultConsumed },
                activeTaskId = null,
                isProcessing = false,
                isTestingConnection = false,
                message = null,
                errorMessage = null,
            )
        }
    }

    fun updateSettings(settings: AiSettings) {
        mutableState.update {
            it.copy(
                settings = settings.cleaned(),
                message = "AI 设置已保存",
                errorMessage = null,
            )
        }
        persist()
    }

    fun applyProviderPreset(provider: AiProviderPreset) {
        val current = state.value.settings
        updateSettings(
            current.copy(
                provider = provider,
                baseUrl = provider.defaultBaseUrl.takeIf { it.isNotBlank() } ?: current.baseUrl,
                chatModel = provider.defaultChatModelForSettings().takeIf { it.isNotBlank() } ?: current.chatModel,
                fastModel = provider.defaultFastModelForSettings().takeIf { it.isNotBlank() } ?: current.fastModel,
                longContextModel = provider.defaultLongContextModelForSettings().takeIf { it.isNotBlank() } ?: current.longContextModel,
                visionModel = provider.defaultVisionModel.takeIf { it.isNotBlank() } ?: current.visionModel,
                embeddingModel = provider.defaultEmbeddingModel.takeIf { it.isNotBlank() } ?: current.embeddingModel,
            ),
        )
    }

    fun testConnection() {
        if (state.value.isTestingConnection) return
        mutableState.update { it.copy(isTestingConnection = true, errorMessage = null, message = "正在测试 AI 连接") }
        scope.launch {
            val settings = state.value.settings.copy(enabled = true)
            val prompt = AiPromptBuilder.build(settings, AiTaskKind.ConnectionTest, AiTaskInput())
            when (val result = repository.complete(settings, prompt, model = settings.modelForTask(AiTaskKind.ConnectionTest))) {
                is AiRepositoryResult.Success -> mutableState.update {
                    it.copy(isTestingConnection = false, message = "AI 连接正常：${result.text.take(40)}", errorMessage = null)
                }
                AiRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(isTestingConnection = false, message = null, errorMessage = "AI API Key 无效或权限不足")
                }
                is AiRepositoryResult.Error -> mutableState.update {
                    it.copy(isTestingConnection = false, message = null, errorMessage = result.message)
                }
            }
        }
    }

    fun request(kind: AiTaskKind, input: AiTaskInput): AiTask? {
        return enqueue(kind, input, processImmediately = true)
    }

    fun enqueue(kind: AiTaskKind, input: AiTaskInput, processImmediately: Boolean = true): AiTask? {
        val settings = state.value.settings
        val taskSettings = settings.settingsForTask(kind)
        val validation = taskSettings.validationError(kind)
        if (validation != null) {
            mutableState.update { it.copy(errorMessage = validation, message = null) }
            return null
        }
        val usageResult = state.value.usage.consumeAiRequest(settings)
        if (usageResult.errorMessage != null) {
            mutableState.update {
                it.copy(usage = usageResult.usage, errorMessage = usageResult.errorMessage, message = null)
            }
            persist()
            return null
        }
        val now = nowMillis()
        val task = AiTask(
            id = nextId("ai"),
            accountId = cleanAccountId(),
            kind = kind,
            input = input.compact(taskSettings.maxInputChars),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            usageCharged = true,
        )
        mutableState.update {
            it.copy(
                tasks = (listOf(task) + it.tasks).take(AI_MAX_TASKS),
                usage = usageResult.usage,
                activeTaskId = task.id,
                message = "已加入 AI 队列",
                errorMessage = null,
            )
        }
        persist()
        onQueueChanged()
        if (processImmediately) processNext()
        return task
    }

    fun processNext() {
        if (state.value.isProcessing) return
        val task = state.value.tasks
            .sortedBy { it.createdAtEpochMillis }
            .firstOrNull { it.status == AiTaskStatus.Pending || it.status == AiTaskStatus.Running }
            ?: return
        processTask(task.id)
    }

    fun processTask(taskId: String) {
        if (state.value.isProcessing) return
        val task = state.value.tasks.firstOrNull { it.id == taskId } ?: return
        val settings = state.value.settings
        val taskSettings = settings.settingsForTask(task.kind)
        val validation = taskSettings.validationError(task.kind)
        if (validation != null) {
            markTaskFailed(task.id, validation)
            return
        }
        mutableState.update {
            it.copy(
                tasks = it.tasks.map { current ->
                    if (current.id == task.id) current.copy(
                        status = AiTaskStatus.Running,
                        updatedAtEpochMillis = nowMillis(),
                    ) else current
                },
                activeTaskId = task.id,
                isProcessing = true,
                message = "AI 正在处理：${task.kind.label}",
                errorMessage = null,
            )
        }
        persist()
        scope.launch {
            executeTask(task, settings)
            mutableState.update { it.copy(isProcessing = false) }
            persist()
            processNext()
        }
    }

    suspend fun runBlockingTask(kind: AiTaskKind, input: AiTaskInput): AiRepositoryResult {
        val settings = state.value.settings
        val taskSettings = settings.settingsForTask(kind)
        val validation = taskSettings.validationError(kind)
        if (validation != null) return AiRepositoryResult.Error(validation)
        val usageResult = state.value.usage.consumeAiRequest(settings)
        if (usageResult.errorMessage != null) {
            mutableState.update { it.copy(usage = usageResult.usage, errorMessage = usageResult.errorMessage, message = null) }
            persist()
            return AiRepositoryResult.Error(usageResult.errorMessage)
        }
        mutableState.update { it.copy(usage = usageResult.usage) }
        persist()
        val prompt = AiPromptBuilder.build(taskSettings, kind, input)
        return repository.complete(
            settings = taskSettings,
            prompt = prompt,
            model = taskSettings.modelForTask(kind),
            fileIds = input.fileIds,
        )
    }

    override suspend fun evaluateSemanticCondition(prompt: String, eventText: String): AiBridgeResult {
        return when (val result = runBlockingTask(
            AiTaskKind.AutomationSemanticCondition,
            AiTaskInput(prompt = prompt, automationEventText = eventText),
        )) {
            is AiRepositoryResult.Success -> AiBridgeResult.Success(result.text)
            AiRepositoryResult.Unauthorized -> AiBridgeResult.Error("AI API Key 无效或权限不足")
            is AiRepositoryResult.Error -> AiBridgeResult.Error(result.message)
        }
    }

    override suspend fun generateAutomationText(prompt: String, eventText: String): AiBridgeResult {
        return when (val result = runBlockingTask(
            AiTaskKind.AutomationGeneratedAction,
            AiTaskInput(prompt = prompt, automationEventText = eventText),
        )) {
            is AiRepositoryResult.Success -> AiBridgeResult.Success(result.text)
            AiRepositoryResult.Unauthorized -> AiBridgeResult.Error("AI API Key 无效或权限不足")
            is AiRepositoryResult.Error -> AiBridgeResult.Error(result.message)
        }
    }

    fun consumeLatestResult() {
        val consumedTaskId = state.value.latestResult?.id
        mutableState.update { current ->
            current.copy(
                tasks = if (consumedTaskId == null) {
                    current.tasks
                } else {
                    current.tasks.map { task ->
                        if (task.id == consumedTaskId) task.copy(resultConsumed = true) else task
                    }
                },
                latestResult = null,
                activeTaskId = null,
            )
        }
        if (consumedTaskId != null) persist()
    }

    fun clearFinishedTasks() {
        mutableState.update {
            it.copy(
                tasks = it.tasks.filterNot { task -> task.isFinished },
                latestResult = null,
                activeTaskId = null,
                message = "AI 历史已清理",
                errorMessage = null,
            )
        }
        persist(removeFinishedTasks = true)
    }

    private suspend fun executeTask(task: AiTask, settings: AiSettings) {
        val taskSettings = settings.settingsForTask(task.kind)
        val prompt = AiPromptBuilder.build(taskSettings, task.kind, task.input)
        when (val result = repository.complete(
            settings = taskSettings,
            prompt = prompt,
            model = taskSettings.modelForTask(task.kind),
            fileIds = task.input.fileIds,
        )) {
            is AiRepositoryResult.Success -> markTaskCompleted(task.id, result.text.take(AI_MAX_RESULT_CHARS))
            AiRepositoryResult.Unauthorized -> markTaskFailed(task.id, "AI API Key 无效或权限不足")
            is AiRepositoryResult.Error -> markTaskFailed(task.id, result.message)
        }
    }

    private fun markTaskCompleted(taskId: String, text: String) {
        val now = nowMillis()
        var completed: AiTask? = null
        mutableState.update { current ->
            val tasks = current.tasks.map { task ->
                if (task.id == taskId) {
                    task.copy(
                        status = AiTaskStatus.Completed,
                        resultText = text,
                        errorMessage = "",
                        updatedAtEpochMillis = now,
                        resultConsumed = false,
                    ).also { completed = it }
                } else {
                    task
                }
            }
            current.copy(
                tasks = tasks,
                latestResult = completed,
                activeTaskId = completed?.id ?: current.activeTaskId,
                message = "AI 已完成：${completed?.kind?.label.orEmpty()}",
                errorMessage = null,
            )
        }
        persist()
    }

    private fun markTaskFailed(taskId: String, message: String) {
        val now = nowMillis()
        mutableState.update { current ->
            current.copy(
                tasks = current.tasks.map { task ->
                    if (task.id == taskId) {
                        task.copy(
                            status = AiTaskStatus.Failed,
                            errorMessage = message.take(500),
                            updatedAtEpochMillis = now,
                            retryCount = task.retryCount + 1,
                        )
                    } else {
                        task
                    }
                },
                activeTaskId = taskId,
                message = null,
                errorMessage = message,
            )
        }
        persist()
    }

    private fun persist(removeFinishedTasks: Boolean = false) {
        val current = state.value
        val account = cleanAccountId()
        runCatching {
            store.update(account) { storedSnapshot ->
                val storedTasks = if (removeFinishedTasks) {
                    storedSnapshot.tasks.filterNot { task -> task.isFinished }
                } else {
                    storedSnapshot.tasks
                }
                AiSnapshot(
                    settings = current.settings,
                    tasks = mergeStoredAiTasks(
                        current = storedTasks,
                        updates = current.tasks,
                    ),
                    usage = mergeStoredAiUsage(
                        current = storedSnapshot.usage,
                        update = current.usage,
                    ),
                )
            }
        }
    }

    private fun cleanAccountId(): String = accountId?.trim()?.takeIf { it.isNotEmpty() } ?: "default"

    private fun nextId(prefix: String): String {
        nextLocalId += 1
        return "$prefix-${nowMillis()}-$nextLocalId"
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

interface AiBridge {
    suspend fun evaluateSemanticCondition(prompt: String, eventText: String): AiBridgeResult

    suspend fun generateAutomationText(prompt: String, eventText: String): AiBridgeResult
}

object NoopAiBridge : AiBridge {
    override suspend fun evaluateSemanticCondition(prompt: String, eventText: String): AiBridgeResult {
        return AiBridgeResult.Error("AI 未接入")
    }

    override suspend fun generateAutomationText(prompt: String, eventText: String): AiBridgeResult {
        return AiBridgeResult.Error("AI 未接入")
    }
}

sealed interface AiBridgeResult {
    data class Success(val text: String) : AiBridgeResult
    data class Error(val message: String) : AiBridgeResult
}

private fun AiSettings.validationError(kind: AiTaskKind): String? {
    if (!enabled) return "AI 未启用"
    if (!hasEndpoint) return "请先配置远端 AI 或本地 AI 模型"
    if (serviceMode == AiServiceMode.LocalOnly && supportsCloudAuth && apiKey.isBlank()) return "请先填写 AI API Key"
    if (serviceMode == AiServiceMode.RemoteFirst && !hasServerAiEndpoint && supportsCloudAuth && apiKey.isBlank()) {
        return "请先填写 AI API Key"
    }
    if (
        (kind == AiTaskKind.ChatSummary ||
            kind == AiTaskKind.ChatRecentSummary ||
            kind == AiTaskKind.ChatTodaySummary ||
            kind == AiTaskKind.ChatUnreadSummary ||
            kind == AiTaskKind.ChatImportanceCheck ||
            kind == AiTaskKind.ChatReplyDraft) &&
        !readChatAllowed
    ) {
        return "AI 未获得聊天读取权限"
    }
    if ((kind == AiTaskKind.ChatActionItems || kind == AiTaskKind.ChatDecisionSummary) && !readChatAllowed) {
        return "AI 未获得聊天读取权限"
    }
    if (
        (kind == AiTaskKind.NotificationSummary ||
            kind == AiTaskKind.NotificationFollowUp ||
            kind == AiTaskKind.NotificationPriority) &&
        !readNotificationsAllowed
    ) {
        return "AI 未获得通知读取权限"
    }
    if (
        (kind == AiTaskKind.PostSummary ||
            kind == AiTaskKind.PostReplyDraft ||
            kind == AiTaskKind.ThreadSummary ||
            kind == AiTaskKind.ThreadReplyDraft ||
            kind == AiTaskKind.TimelineDigest ||
            kind == AiTaskKind.TimelineReplyOpportunities ||
            kind == AiTaskKind.TimelineFilterSuggestions) &&
        !readTimelineAllowed
    ) {
        return "AI 未获得帖子读取权限"
    }
    if (kind == AiTaskKind.WorkspaceActionPlan || kind == AiTaskKind.AssistantChat) {
        val hasAnyReadableContext = readTimelineAllowed || readNotificationsAllowed || readChatAllowed || readDraftsAllowed || automationAllowed
        if (!hasAnyReadableContext) return "AI 未获得可用于助手的读取权限"
    }
    if ((kind == AiTaskKind.ProfileSummary || kind == AiTaskKind.ProfileInteractionSuggestions) && !readProfileAllowed) {
        return "AI 未获得资料读取权限"
    }
    if (kind.name.startsWith("Compose") && !readDraftsAllowed) return "AI 未获得草稿读取权限"
    if (kind == AiTaskKind.ComposeFromRecentPosts && !readTimelineAllowed) return "AI 未获得帖子读取权限"
    if (kind.name.startsWith("Automation") && !automationAllowed) return "AI 自动化未启用"
    return null
}

private fun AiAutomationModelConfig.cleaned(): AiAutomationModelConfig {
    return copy(
        baseUrl = baseUrl.trim().take(240),
        apiKey = apiKey.trim().take(1_000),
        model = model.trim().take(160),
    )
}

private fun AiSettings.cleaned(): AiSettings {
    return copy(
        remoteBaseUrl = remoteBaseUrl.trim().take(240).ifBlank { DEFAULT_SERVER_AI_BASE_URL },
        remotePreferredModel = remotePreferredModel.trim().take(160).ifBlank { DEFAULT_SERVER_AI_MODEL },
        baseUrl = baseUrl.trim().take(240),
        apiKey = apiKey.trim().take(1_000),
        chatModel = chatModel.trim().take(160),
        fastModel = fastModel.trim().take(160),
        longContextModel = longContextModel.trim().take(160),
        visionModel = visionModel.trim().take(160),
        embeddingModel = embeddingModel.trim().take(160),
        maxInputChars = maxInputChars.coerceIn(1_000, 40_000),
        maxOutputTokens = maxOutputTokens.coerceIn(64, 4_000),
        dailyRequestLimit = dailyRequestLimit.coerceIn(1, 2_000),
        tonePreference = tonePreference.trim().take(240),
        systemPrompt = systemPrompt.trim().ifBlank { DEFAULT_AI_SYSTEM_PROMPT }.take(2_000),
        automationRuleDraftModel = automationRuleDraftModel.cleaned(),
        assistantMemoryNotes = assistantMemoryNotes
            .map { it.trim().take(240) }
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(20),
    )
}
