package cc.hhhl.client.ai

import kotlin.test.Test
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
                settings = AiSettings(enabled = true, apiKey = "key"),
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

    override suspend fun complete(
        settings: AiSettings,
        prompt: AiPrompt,
        model: String,
    ): AiRepositoryResult {
        lastPrompt = prompt
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
