package cc.hhhl.client.automation

import cc.hhhl.client.ai.AiBridge
import cc.hhhl.client.ai.AiBridgeResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.content.TextContent
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationStateHolderTest {
    @Test
    fun emitExecutesMatchingRuleAndPersistsLog() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Log matches",
                        trigger = AutomationTrigger.ChatMessage,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-1",
                                type = AutomationConditionType.MessageContains,
                                value = "hello",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-1",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "{{sender.name}}: {{message.text}}",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.ChatMessage,
                senderName = "Alice",
                messageText = "hello world",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, holder.state.value.logs.size)
        assertEquals("Alice: hello world", holder.state.value.logs.single().message)
        assertTrue(holder.state.value.logs.single().success)
        assertEquals("Alice: hello world", store.lastSnapshot.logs.single().message)
    }

    @Test
    fun emitIgnoresOwnMessageWhenRuleRequestsIt() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Ignore self",
                        trigger = AutomationTrigger.ChatMessage,
                        ignoreOwnMessages = true,
                        actions = listOf(
                            AutomationAction(
                                id = "action-1",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "{{message.text}}",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.ChatMessage,
                messageText = "self message",
                isFromCurrentUser = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.logs)
    }

    @Test
    fun aiGeneratedWebhookUsesGeneratedTextAsWebhookBody() = runTest {
        var webhookBody = ""
        val executor = AppAutomationActionExecutor(
            chatRepository = cc.hhhl.client.repository.ChatRepository(tokenProvider = { "token" }),
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            aiBridge = FakeAiBridge("AI 生成的回调正文"),
            httpClient = HttpClient(
                MockEngine { request ->
                    webhookBody = (request.body as TextContent).text
                    respond("{}", HttpStatusCode.OK)
                },
            ) {
                install(ContentNegotiation) { json() }
            },
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-ai-webhook",
                type = AutomationActionType.AiGenerateWebhook,
                targetId = "https://example.com/hook",
                titleTemplate = "AI 事件",
                bodyTemplate = "提取可执行摘要",
            ),
            event = AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.Notification,
                senderName = "Alice",
                notificationText = "反馈更新失败",
            ),
            title = "AI 事件",
            body = "原始模板不应作为正文",
        )

        assertTrue(result.success)
        assertTrue(webhookBody.contains("AI 生成的回调正文"))
        assertTrue(webhookBody.contains("AI 事件"))
        assertTrue(webhookBody.contains("event-1"))
    }

    @Test
    fun externalAutomationActionRequiresToolPermission() = runTest {
        val executor = object : AutomationActionExecutor {
            var called = false

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                called = true
                return AutomationActionExecutionResult(true, "sent")
            }
        }
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Forward",
                        trigger = AutomationTrigger.Notification,
                        actions = listOf(
                            AutomationAction(
                                id = "action-1",
                                type = AutomationActionType.Webhook,
                                targetId = "https://example.com/hook",
                                bodyTemplate = "{{notification.text}}",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            executor = executor,
            aiToolPermissionProvider = { false },
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.Notification,
                notificationText = "需要外部回调",
            ),
        )
        advanceUntilIdle()

        assertEquals(false, executor.called)
        assertEquals(false, holder.state.value.logs.single().success)
        assertTrue(holder.state.value.logs.single().message.contains("工具权限"))
    }
}

private class FakeAiBridge(
    private val generatedText: String,
) : AiBridge {
    override suspend fun evaluateSemanticCondition(prompt: String, eventText: String): AiBridgeResult {
        return AiBridgeResult.Success("YES")
    }

    override suspend fun generateAutomationText(prompt: String, eventText: String): AiBridgeResult {
        return AiBridgeResult.Success(generatedText)
    }
}

private class MemoryAutomationStore(
    initialSnapshot: AutomationSnapshot = AutomationSnapshot(),
) : AutomationStore {
    var lastSnapshot: AutomationSnapshot = initialSnapshot
        private set

    override fun read(accountId: String): AutomationSnapshot = lastSnapshot

    override fun write(accountId: String, snapshot: AutomationSnapshot) {
        lastSnapshot = snapshot
    }

    override fun clearAccount(accountId: String) {
        lastSnapshot = AutomationSnapshot()
    }
}
