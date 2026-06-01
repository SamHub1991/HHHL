package cc.hhhl.client.ai

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AiStateHolderTest {
    @Test
    fun requestProcessesTaskAndPersistsResult() = runTest {
        val store = MemoryAiStore(
            AiSnapshot(
                settings = AiSettings(
                    enabled = true,
                    apiKey = "key",
                    chatModel = "chat-model",
                    fastModel = "fast-model",
                    longContextModel = "long-model",
                ),
            ),
        )
        val repository = FakeAiRepository(AiRepositoryResult.Success("整理完成"))
        var queueChanged = 0
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = repository,
            onQueueChanged = { queueChanged += 1 },
            scope = TestScope(testScheduler),
        )
        holder.restore()

        val task = holder.request(
            AiTaskKind.NotificationPriority,
            AiTaskInput(notifications = listOf(AiNotificationContext("Reply", "Alice", "回复了你"))),
        )
        advanceUntilIdle()

        assertNotNull(task)
        assertEquals(1, queueChanged)
        assertEquals(AiTaskStatus.Completed, holder.state.value.tasks.single().status)
        assertEquals("整理完成", holder.state.value.latestResult?.resultText)
        assertEquals(AiTaskStatus.Completed, store.lastSnapshot.tasks.single().status)
        assertContentEquals(listOf("long-model"), repository.requestedModels)
    }

    @Test
    fun validationBlocksPrivateChatWhenChatReadDisabled() = runTest {
        val store = MemoryAiStore(
            AiSnapshot(
                settings = AiSettings(
                    enabled = true,
                    apiKey = "key",
                    readChatAllowed = false,
                ),
            ),
        )
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("不应调用")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        val task = holder.enqueue(AiTaskKind.ChatSummary, AiTaskInput(chatTitle = "私聊"))

        assertEquals(null, task)
        assertTrue(holder.state.value.errorMessage.orEmpty().contains("聊天读取权限"))
        assertEquals(emptyList(), holder.state.value.tasks)
    }

    @Test
    fun restoreUsesMostRecentlyUpdatedCompletedTaskAsLatestResult() = runTest {
        val store = MemoryAiStore(
            AiSnapshot(
                settings = AiSettings(enabled = true, apiKey = "key"),
                tasks = listOf(
                    AiTask(
                        id = "old-completed",
                        accountId = "account-1",
                        kind = AiTaskKind.PostSummary,
                        input = AiTaskInput(),
                        status = AiTaskStatus.Completed,
                        resultText = "旧结果",
                        updatedAtEpochMillis = 10,
                    ),
                    AiTask(
                        id = "new-completed",
                        accountId = "account-1",
                        kind = AiTaskKind.ThreadSummary,
                        input = AiTaskInput(),
                        status = AiTaskStatus.Completed,
                        resultText = "新结果",
                        updatedAtEpochMillis = 30,
                    ),
                    AiTask(
                        id = "pending",
                        accountId = "account-1",
                        kind = AiTaskKind.NotificationSummary,
                        input = AiTaskInput(),
                        status = AiTaskStatus.Pending,
                        updatedAtEpochMillis = 50,
                    ),
                ),
            ),
        )
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("不应调用")),
            scope = TestScope(testScheduler),
        )

        holder.restore()

        assertEquals(listOf("pending", "new-completed", "old-completed"), holder.state.value.tasks.map { it.id })
        assertEquals("new-completed", holder.state.value.latestResult?.id)
        assertEquals("新结果", holder.state.value.latestResult?.resultText)
    }

    @Test
    fun consumedLatestResultDoesNotReappearAfterRestore() = runTest {
        val store = MemoryAiStore(
            AiSnapshot(
                settings = AiSettings(enabled = true, apiKey = ""),
                tasks = listOf(
                    AiTask(
                        id = "old-completed",
                        accountId = "account-1",
                        kind = AiTaskKind.PostSummary,
                        input = AiTaskInput(),
                        status = AiTaskStatus.Completed,
                        resultText = "旧结果",
                        updatedAtEpochMillis = 10,
                    ),
                    AiTask(
                        id = "completed",
                        accountId = "account-1",
                        kind = AiTaskKind.ChatSummary,
                        input = AiTaskInput(chatTitle = "聊天"),
                        status = AiTaskStatus.Completed,
                        resultText = "聊天总结",
                        updatedAtEpochMillis = 20,
                    ),
                ),
            ),
        )
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("不应调用")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        assertEquals("completed", holder.state.value.latestResult?.id)

        holder.consumeLatestResult()

        assertEquals(null, holder.state.value.latestResult)
        assertTrue(store.lastSnapshot.tasks.first { it.id == "completed" }.resultConsumed)

        val restored = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("不应调用")),
            scope = TestScope(testScheduler),
        )
        restored.restore()

        assertEquals(null, restored.state.value.latestResult)
    }

    @Test
    fun newlyCompletedTaskIsAvailableAsLatestResultEvenAfterOlderResultWasConsumed() = runTest {
        val store = MemoryAiStore(
            AiSnapshot(
                settings = AiSettings(
                    enabled = true,
                    apiKey = "test-token",
                    chatModel = "chat-model",
                    fastModel = "fast-model",
                    longContextModel = "long-model",
                ),
                tasks = listOf(
                    AiTask(
                        id = "old-completed",
                        accountId = "account-1",
                        kind = AiTaskKind.ChatSummary,
                        input = AiTaskInput(chatTitle = "旧聊天"),
                        status = AiTaskStatus.Completed,
                        resultText = "旧结果",
                        updatedAtEpochMillis = 10,
                        resultConsumed = true,
                    ),
                ),
            ),
        )
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("新结果")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        val task = holder.request(AiTaskKind.ChatSummary, AiTaskInput(chatTitle = "新聊天"))
        advanceUntilIdle()

        val completedTask = assertNotNull(task)
        assertEquals(completedTask.id, holder.state.value.latestResult?.id)
        assertEquals("新结果", holder.state.value.latestResult?.resultText)
        assertEquals(false, store.lastSnapshot.tasks.first { it.id == completedTask.id }.resultConsumed)
    }

    @Test
    fun dailyRequestLimitBlocksNewQueuedTasks() = runTest {
        val store = MemoryAiStore(
            AiSnapshot(
                settings = AiSettings(enabled = true, apiKey = "key", dailyRequestLimit = 1),
            ),
        )
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("完成")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        val first = holder.enqueue(AiTaskKind.NotificationSummary, AiTaskInput(notifications = listOf(AiNotificationContext("Reply", "Alice", "1"))), processImmediately = false)
        val second = holder.enqueue(AiTaskKind.NotificationSummary, AiTaskInput(notifications = listOf(AiNotificationContext("Reply", "Alice", "2"))), processImmediately = false)

        assertNotNull(first)
        assertEquals(null, second)
        assertTrue(holder.state.value.errorMessage.orEmpty().contains("上限"))
        assertEquals(1, holder.state.value.tasks.size)
        assertEquals(1, store.lastSnapshot.usage.requestCount)
    }

    @Test
    fun workspaceActionPlanWorksWithNotificationsOnlyPermission() = runTest {
        val store = MemoryAiStore(
            AiSnapshot(
                settings = AiSettings(
                    enabled = true,
                    apiKey = "key",
                    readTimelineAllowed = false,
                    readNotificationsAllowed = true,
                    readChatAllowed = false,
                    readDraftsAllowed = false,
                    automationAllowed = false,
                ),
            ),
        )
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("计划")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        val task = holder.enqueue(
            AiTaskKind.WorkspaceActionPlan,
            AiTaskInput(notifications = listOf(AiNotificationContext("Reply", "Alice", "需要回复"))),
            processImmediately = false,
        )

        assertNotNull(task)
        assertEquals(null, holder.state.value.errorMessage)
        assertEquals(AiTaskKind.WorkspaceActionPlan, holder.state.value.tasks.single().kind)
    }

    @Test
    fun updateSettingsKeepsTrailingSlashWhileEditingBaseUrl() = runTest {
        val holder = AiStateHolder(
            store = MemoryAiStore(),
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("OK")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.updateSettings(AiSettings(enabled = true, apiKey = "key", baseUrl = "https://api.example.com/v1/"))

        assertEquals("https://api.example.com/v1/", holder.state.value.settings.baseUrl)
        assertEquals("https://api.example.com/v1", holder.state.value.settings.cleanBaseUrl)
    }

    @Test
    fun deepSeekProviderPresetUsesProDefaultsForSettings() = runTest {
        val holder = AiStateHolder(
            store = MemoryAiStore(),
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("OK")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.applyProviderPreset(AiProviderPreset.DeepSeek)

        val settings = holder.state.value.settings
        assertEquals("deepseek-v4-pro", settings.chatModel)
        assertEquals("deepseek-v4-flash", settings.fastModel)
        assertEquals("deepseek-v4-pro", settings.longContextModel)
    }

    @Test
    fun runBlockingTaskRoutesModelsByTaskKind() = runTest {
        val repository = FakeAiRepository(AiRepositoryResult.Success("OK"))
        val holder = AiStateHolder(
            store = MemoryAiStore(
                AiSnapshot(
                    settings = AiSettings(
                        enabled = true,
                        apiKey = "key",
                        chatModel = "chat-model",
                        fastModel = "fast-model",
                        longContextModel = "long-model",
                        automationAllowed = true,
                    ),
                ),
            ),
            accountId = "account-1",
            repository = repository,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.runBlockingTask(AiTaskKind.AutomationSemanticCondition, AiTaskInput(prompt = "是否重要"))
        holder.runBlockingTask(AiTaskKind.ChatRecentSummary, AiTaskInput(chatTitle = "项目群"))
        holder.runBlockingTask(AiTaskKind.AssistantChat, AiTaskInput(prompt = "帮我回复"))

        assertContentEquals(listOf("fast-model", "long-model", "chat-model"), repository.requestedModels)
    }

    @Test
    fun automationRuleDraftUsesDefaultModelWhenAutomationModelConfigDisabled() = runTest {
        val repository = FakeAiRepository(AiRepositoryResult.Success("OK"))
        val holder = AiStateHolder(
            store = MemoryAiStore(
                AiSnapshot(
                    settings = AiSettings(
                        enabled = true,
                        apiKey = "default-",
                        chatModel = "chat-model",
                        fastModel = "fast-model",
                        longContextModel = "long-model",
                        automationAllowed = true,
                        automationRuleDraftModel = AiAutomationModelConfig(
                            enabled = false,
                            apiKey = "automation-",
                            model = "automation-model",
                        ),
                    ),
                ),
            ),
            accountId = "account-1",
            repository = repository,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.runBlockingTask(AiTaskKind.AutomationRuleDraft, AiTaskInput(prompt = "新规则"))

        assertContentEquals(listOf("long-model"), repository.requestedModels)
        assertEquals("default-", repository.requestedSettings.single().apiKey)
    }

    @Test
    fun automationRuleDraftUsesAutomationModelConfigWhenEnabled() = runTest {
        val repository = FakeAiRepository(AiRepositoryResult.Success("OK"))
        val holder = AiStateHolder(
            store = MemoryAiStore(
                AiSnapshot(
                    settings = AiSettings(
                        enabled = true,
                        apiKey = "default-",
                        chatModel = "chat-model",
                        fastModel = "fast-model",
                        longContextModel = "long-model",
                        automationAllowed = true,
                        automationRuleDraftModel = AiAutomationModelConfig(
                            enabled = true,
                            provider = AiProviderPreset.DeepSeek,
                            baseUrl = "https://automation.example.com/v1",
                            apiKey = "automation-",
                            model = "automation-rule-model",
                        ),
                    ),
                ),
            ),
            accountId = "account-1",
            repository = repository,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.runBlockingTask(AiTaskKind.AutomationRuleDraft, AiTaskInput(prompt = "新规则"))

        assertContentEquals(listOf("automation-rule-model"), repository.requestedModels)
        assertEquals(AiProviderPreset.DeepSeek, repository.requestedSettings.single().provider)
        assertEquals("https://automation.example.com/v1", repository.requestedSettings.single().baseUrl)
        assertEquals("automation-", repository.requestedSettings.single().apiKey)
    }
    @Test
    fun testConnectionUsesFastModel() = runTest {
        val repository = FakeAiRepository(AiRepositoryResult.Success("OK"))
        val holder = AiStateHolder(
            store = MemoryAiStore(
                AiSnapshot(
                    settings = AiSettings(
                        enabled = true,
                        apiKey = "key",
                        chatModel = "chat-model",
                        fastModel = "fast-model",
                        longContextModel = "long-model",
                    ),
                ),
            ),
            accountId = "account-1",
            repository = repository,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.testConnection()
        advanceUntilIdle()

        assertContentEquals(listOf("fast-model"), repository.requestedModels)
    }

    @Test
    fun updateSettingsPersistsNormalizedAssistantMemory() = runTest {
        val store = MemoryAiStore()
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("OK")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.updateSettings(
            AiSettings(
                enabled = true,
                apiKey = "key",
                assistantMemoryNotes = listOf("  喜欢短回复  ", "", "喜欢短回复", "自动化要确认"),
            ),
        )

        assertEquals(listOf("喜欢短回复", "自动化要确认"), holder.state.value.settings.assistantMemoryNotes)
        assertEquals(listOf("喜欢短回复", "自动化要确认"), store.lastSnapshot.settings.assistantMemoryNotes)
    }

    @Test
    fun updateSettingsPersistsAssistantAutoApprovalFlags() = runTest {
        val store = MemoryAiStore()
        val holder = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("OK")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.updateSettings(
            AiSettings(
                enabled = true,
                apiKey = "key",
                assistantLowRiskAutoApproval = true,
                assistantHighRiskAutoApproval = true,
            ),
        )

        assertTrue(holder.state.value.settings.assistantLowRiskAutoApproval)
        assertTrue(holder.state.value.settings.assistantHighRiskAutoApproval)
        assertTrue(store.lastSnapshot.settings.assistantLowRiskAutoApproval)
        assertTrue(store.lastSnapshot.settings.assistantHighRiskAutoApproval)

        val restored = AiStateHolder(
            store = store,
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("OK")),
            scope = TestScope(testScheduler),
        )
        restored.restore()

        assertTrue(restored.state.value.settings.assistantHighRiskAutoApproval)
    }

    @Test
    fun highRiskAutoApprovalCanStayEnabledWhenLowRiskAutoApprovalIsDisabled() = runTest {
        val holder = AiStateHolder(
            store = MemoryAiStore(),
            accountId = "account-1",
            repository = FakeAiRepository(AiRepositoryResult.Success("OK")),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.updateSettings(
            AiSettings(
                enabled = true,
                apiKey = "key",
                assistantLowRiskAutoApproval = false,
                assistantHighRiskAutoApproval = true,
            ),
        )

        assertEquals(false, holder.state.value.settings.assistantLowRiskAutoApproval)
        assertEquals(true, holder.state.value.settings.assistantHighRiskAutoApproval)
    }
}

private class FakeAiRepository(
    private val result: AiRepositoryResult,
) : AiRepository() {
    var lastPrompt: AiPrompt? = null
    val requestedModels = mutableListOf<String>()
    val requestedSettings = mutableListOf<AiSettings>()

    override suspend fun complete(
        settings: AiSettings,
        prompt: AiPrompt,
        model: String,
    ): AiRepositoryResult {
        lastPrompt = prompt
        requestedModels += model
        requestedSettings += settings
        return result
    }
}

private class MemoryAiStore(
    initialSnapshot: AiSnapshot = AiSnapshot(),
) : AiStore {
    var lastSnapshot: AiSnapshot = initialSnapshot
        private set

    override fun read(accountId: String): AiSnapshot = lastSnapshot

    override fun write(accountId: String, snapshot: AiSnapshot) {
        lastSnapshot = snapshot
    }

    override fun clearAccount(accountId: String) {
        lastSnapshot = AiSnapshot()
    }
}
