package cc.hhhl.client.automation

import cc.hhhl.client.ai.AiBridge
import cc.hhhl.client.ai.AiBridgeImageResult
import cc.hhhl.client.ai.AiBridgeResult
import cc.hhhl.client.ai.AiGeneratedImage
import cc.hhhl.client.ai.AiImageRequestOptions
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepository
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        assertEquals(1, holder.state.value.debugRecords.size)
        assertTrue(holder.state.value.debugRecords.single().matched)
        assertTrue(holder.state.value.debugRecords.single().reason.contains("全部"))
        assertEquals("Log matches", store.lastSnapshot.debugRecords.single().ruleName)
    }

    @Test
    fun emitDoesNotTreatDifferentBlankIdEventsAsDuplicates() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Log every message",
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

        holder.emit(AutomationEvent(id = "", trigger = AutomationTrigger.ChatMessage, messageText = "hello one"))
        holder.emit(AutomationEvent(id = "", trigger = AutomationTrigger.ChatMessage, messageText = "hello two"))
        advanceUntilIdle()

        assertEquals(listOf("hello two", "hello one"), holder.state.value.logs.map { it.message })
        assertEquals(2, store.lastSnapshot.executedEvents.size)
    }

    @Test
    fun emitSkipsPersistedDuplicateChatMessageAcrossRestore() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Log once",
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
                                bodyTemplate = "{{message.id}}: {{message.text}}",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val firstHolder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        firstHolder.restore()

        firstHolder.emit(
            AutomationEvent(
                id = "chat-message:message-1",
                trigger = AutomationTrigger.ChatMessage,
                chatMessageId = "message-1",
                messageText = "hello world",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, store.lastSnapshot.logs.size)
        assertEquals("message-1: hello world", store.lastSnapshot.logs.single().message)
        assertEquals(1, store.lastSnapshot.executedEvents.size)
        assertEquals("chat-message:message-1", store.lastSnapshot.executedEvents.single().eventKey)

        val restoredHolder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        restoredHolder.restore()

        restoredHolder.emit(
            AutomationEvent(
                id = "background-poll:room-1:message-1",
                trigger = AutomationTrigger.ChatMessage,
                chatMessageId = "message-1",
                messageText = "hello world",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, store.lastSnapshot.logs.size)
        assertEquals(1, store.lastSnapshot.executedEvents.size)
        assertTrue(restoredHolder.state.value.debugRecords.first().reason.contains("去重"))
    }

    @Test
    fun emitSkipsDuplicateWhenAnotherHolderClaimsEventFirst() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Log once across holders",
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
                                bodyTemplate = "{{message.id}}: {{message.text}}",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val firstHolder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        val secondHolder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        firstHolder.restore()
        secondHolder.restore()

        val event = AutomationEvent(
            id = "chat-message:message-1",
            trigger = AutomationTrigger.ChatMessage,
            chatMessageId = "message-1",
            messageText = "hello world",
        )
        firstHolder.emit(event)
        advanceUntilIdle()
        secondHolder.emit(event)
        advanceUntilIdle()

        assertEquals(1, store.lastSnapshot.logs.size)
        assertEquals(1, store.lastSnapshot.executedEvents.size)
        assertTrue(secondHolder.state.value.debugRecords.first().reason.contains("其他实例"))
    }

    @Test
    fun persistKeepsExecutedEventsClaimedDuringStoreUpdate() = runTest {
        val externalEventKey = "account-1:rule-external:chat-message:external-message"
        val store = InterleavingAutomationStore(
            initialSnapshot = AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Log once with interleaving",
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
                                bodyTemplate = "{{message.id}}: {{message.text}}",
                            ),
                        ),
                    ),
                ),
            ),
            injectedEvent = AutomationExecutedEvent(
                key = externalEventKey,
                ruleId = "rule-external",
                eventKey = "chat-message:external-message",
                eventId = "external-event",
                createdAtEpochMillis = 1L,
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
                id = "chat-message:message-local",
                trigger = AutomationTrigger.ChatMessage,
                chatMessageId = "message-local",
                messageText = "hello world",
            ),
        )
        advanceUntilIdle()

        assertEquals(
            setOf(externalEventKey, "account-1:rule-1:chat-message:message-local"),
            store.lastSnapshot.executedEvents.map { it.key }.toSet(),
        )
    }
    @Test
    fun failedActionDoesNotPersistDedupeForLaterRetry() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Webhook",
                        trigger = AutomationTrigger.ChatMessage,
                        actions = listOf(
                            AutomationAction(
                                id = "action-1",
                                type = AutomationActionType.Webhook,
                                targetId = "http://10.0.2.2:3000/webhook",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val failingExecutor = object : AutomationActionExecutor {
            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                return AutomationActionExecutionResult(false, "unexpected end of stream")
            }
        }
        val firstHolder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            executor = failingExecutor,
            scope = TestScope(testScheduler),
        )
        firstHolder.restore()

        val event = AutomationEvent(
            id = "chat-message:message-1",
            trigger = AutomationTrigger.ChatMessage,
            chatMessageId = "message-1",
            messageText = "same text",
        )
        firstHolder.emit(event)
        advanceUntilIdle()

        assertEquals(emptyList(), store.lastSnapshot.executedEvents)
        assertTrue(store.lastSnapshot.logs.single().message.contains("unexpected end of stream"))

        val successfulExecutor = object : AutomationActionExecutor {
            var calls = 0

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                calls += 1
                return AutomationActionExecutionResult(true, "sent")
            }
        }
        val secondHolder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            executor = successfulExecutor,
            scope = TestScope(testScheduler),
        )
        secondHolder.restore()
        secondHolder.emit(event)
        advanceUntilIdle()

        assertEquals(1, successfulExecutor.calls)
        assertEquals(1, store.lastSnapshot.executedEvents.size)
    }

    @Test
    fun fullyFailedRiskyActionDoesNotConsumeCooldownForRetry() = runTest {
        val executor = object : AutomationActionExecutor {
            var shouldFail = true
            var calls = 0

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                calls += 1
                return if (shouldFail) {
                    AutomationActionExecutionResult(false, "network failed")
                } else {
                    AutomationActionExecutionResult(true, "sent")
                }
            }
        }
        val holder = AutomationStateHolder(
            store = MemoryAutomationStore(
                AutomationSnapshot(
                    rules = listOf(
                        AutomationRule(
                            id = "rule-risky-retry",
                            name = "Risky retry",
                            trigger = AutomationTrigger.ChatMessage,
                            cooldownSeconds = 30,
                            actions = listOf(
                                AutomationAction(
                                    id = "action-reply",
                                    type = AutomationActionType.ReplyToChat,
                                    bodyTemplate = "{{message.text}}",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            accountId = "account-1",
            executor = executor,
            scope = TestScope(testScheduler),
        )
        holder.restore()
        val event = AutomationEvent(
            id = "event-risky-retry",
            trigger = AutomationTrigger.ChatMessage,
            chatMessageId = "message-risky-retry",
            roomId = "room-1",
            messageText = "retry me",
        )

        holder.emit(event)
        advanceUntilIdle()
        executor.shouldFail = false
        holder.emit(event)
        advanceUntilIdle()

        assertEquals(2, executor.calls)
        assertEquals(true, holder.state.value.logs.first().success)
    }

    @Test
    fun fullyFailedActionDoesNotConsumeBurstLimitForNextEvent() = runTest {
        val executor = object : AutomationActionExecutor {
            var shouldFail = true
            var calls = 0

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                calls += 1
                return if (shouldFail) {
                    AutomationActionExecutionResult(false, "receiver failed")
                } else {
                    AutomationActionExecutionResult(true, "webhook sent")
                }
            }
        }
        val holder = AutomationStateHolder(
            store = MemoryAutomationStore(
                AutomationSnapshot(
                    rules = listOf(
                        AutomationRule(
                            id = "rule-burst-retry",
                            name = "Burst retry",
                            trigger = AutomationTrigger.ChatMessage,
                            maxExecutionsPer30Seconds = 1,
                            actions = listOf(
                                AutomationAction(
                                    id = "action-webhook",
                                    type = AutomationActionType.Webhook,
                                    targetId = "hook-target",
                                    bodyTemplate = "{{message.text}}",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            accountId = "account-1",
            executor = executor,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-burst-fail",
                trigger = AutomationTrigger.ChatMessage,
                chatMessageId = "message-burst-fail",
                messageText = "first",
            ),
        )
        advanceUntilIdle()
        executor.shouldFail = false
        holder.emit(
            AutomationEvent(
                id = "event-burst-success",
                trigger = AutomationTrigger.ChatMessage,
                chatMessageId = "message-burst-success",
                messageText = "second",
            ),
        )
        advanceUntilIdle()

        assertEquals(2, executor.calls)
        assertEquals(true, holder.state.value.logs.first().success)
    }

    @Test
    fun partialFailedRuleKeepsDedupeAfterSuccessfulAction() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-partial",
                        name = "Partial writes",
                        trigger = AutomationTrigger.ChatMessage,
                        actions = listOf(
                            AutomationAction(
                                id = "action-success",
                                type = AutomationActionType.Webhook,
                                targetId = "hook-success",
                            ),
                            AutomationAction(
                                id = "action-fail",
                                type = AutomationActionType.Webhook,
                                targetId = "hook-fail",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val executor = object : AutomationActionExecutor {
            var successfulCalls = 0
            var failedCalls = 0

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                return if (action.id == "action-success") {
                    successfulCalls += 1
                    AutomationActionExecutionResult(true, "sent")
                } else {
                    failedCalls += 1
                    AutomationActionExecutionResult(false, "receiver failed")
                }
            }
        }
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            executor = executor,
            scope = TestScope(testScheduler),
        )
        holder.restore()
        val event = AutomationEvent(
            id = "chat-message:message-partial",
            trigger = AutomationTrigger.ChatMessage,
            chatMessageId = "message-partial",
            messageText = "same text",
        )

        holder.emit(event)
        advanceUntilIdle()
        holder.emit(event)
        advanceUntilIdle()

        assertEquals(1, executor.successfulCalls)
        assertEquals(1, executor.failedCalls)
        assertEquals(1, store.lastSnapshot.executedEvents.size)
        assertEquals("chat-message:message-partial", store.lastSnapshot.executedEvents.single().eventKey)
        assertEquals(2, store.lastSnapshot.logs.size)
        assertTrue(holder.state.value.debugRecords.first().reason.contains("去重"))
    }

    @Test
    fun sameTextDifferentChatMessagesAreNotDeduped() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Log same text",
                        trigger = AutomationTrigger.ChatMessage,
                        actions = listOf(
                            AutomationAction(
                                id = "action-1",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "{{message.id}}: {{message.text}}",
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

        holder.emit(AutomationEvent(id = "chat-message:message-1", trigger = AutomationTrigger.ChatMessage, chatMessageId = "message-1", messageText = "same text"))
        holder.emit(AutomationEvent(id = "chat-message:message-2", trigger = AutomationTrigger.ChatMessage, chatMessageId = "message-2", messageText = "same text"))
        advanceUntilIdle()

        assertEquals(2, store.lastSnapshot.logs.size)
        assertEquals(2, store.lastSnapshot.executedEvents.size)
        assertEquals(
            setOf("chat-message:message-1", "chat-message:message-2"),
            store.lastSnapshot.executedEvents.map { it.eventKey }.toSet(),
        )
    }

    @Test
    fun clearLogsAndDebugRecordsDoNotRestoreStoredEntries() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                logs = listOf(
                    AutomationExecutionLog(
                        id = "log-1",
                        ruleId = "rule-1",
                        ruleName = "Rule",
                        eventId = "event-1",
                        eventLabel = "聊天室消息",
                        actionLabel = "记录日志",
                        message = "old log",
                        success = true,
                        createdAtEpochMillis = 1L,
                    ),
                ),
                debugRecords = listOf(
                    AutomationRuleDebugRecord(
                        id = "debug-1",
                        ruleId = "rule-1",
                        ruleName = "Rule",
                        eventId = "event-1",
                        eventLabel = "聊天室消息",
                        eventSummary = "old debug",
                        matched = true,
                        reason = "old",
                        createdAtEpochMillis = 1L,
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

        holder.clearLogs()
        holder.clearDebugRecords()

        assertEquals(emptyList(), holder.state.value.logs)
        assertEquals(emptyList(), holder.state.value.debugRecords)
        assertEquals(emptyList(), store.lastSnapshot.logs)
        assertEquals(emptyList(), store.lastSnapshot.debugRecords)
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
        assertEquals(1, holder.state.value.debugRecords.size)
        assertEquals(false, holder.state.value.debugRecords.single().matched)
        assertTrue(holder.state.value.debugRecords.single().reason.contains("忽略自己"))
    }

    @Test
    fun canDisableIgnoreOwnMessagesForLoopRiskRule() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-loop-risk",
                        name = "AI reply",
                        trigger = AutomationTrigger.ChatMessage,
                        ignoreOwnMessages = true,
                        actions = listOf(
                            AutomationAction(
                                id = "action-ai-reply",
                                type = AutomationActionType.AiReplyToChat,
                                bodyTemplate = "按上下文回复",
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

        holder.updateRuleIgnoreOwnMessages("rule-loop-risk", false)

        assertEquals(false, holder.state.value.rules.single().ignoreOwnMessages)
        assertEquals(false, store.lastSnapshot.rules.single().ignoreOwnMessages)
        assertEquals(30, holder.state.value.rules.single().cooldownSeconds)
        assertEquals(2, holder.state.value.rules.single().maxExecutionsPer30Seconds)
    }

    @Test
    fun emitIgnoresAiGeneratedEventToPreventLoops() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-ai-loop",
                        name = "Ignore AI generated",
                        trigger = AutomationTrigger.ChatMessage,
                        ignoreOwnMessages = false,
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
                id = "event-ai-generated",
                trigger = AutomationTrigger.ChatMessage,
                messageText = "AI 自动回复内容",
                isAiGenerated = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.logs)
        assertEquals(1, holder.state.value.debugRecords.size)
        assertEquals(false, holder.state.value.debugRecords.single().matched)
        assertTrue(holder.state.value.debugRecords.single().reason.contains("AI 产生"))
    }

    @Test
    fun emitRecordsConditionMismatchForDebugging() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Debug mismatch",
                        trigger = AutomationTrigger.ChatMessage,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-room",
                                type = AutomationConditionType.RoomNameContains,
                                value = "总部",
                            ),
                            AutomationCondition(
                                id = "condition-sender",
                                type = AutomationConditionType.SenderUsername,
                                value = "alice",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-1",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "不应执行",
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
                id = "event-mismatch",
                trigger = AutomationTrigger.ChatMessage,
                roomId = "room-1",
                roomName = "研发聊天室",
                senderUserId = "user-bob",
                senderUsername = "bob",
                senderName = "Bob",
                messageText = "hello",
            ),
        )
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.logs)
        val record = holder.state.value.debugRecords.single()
        assertEquals(false, record.matched)
        assertTrue(record.reason.contains("聊天室名称"))
        assertEquals("研发聊天室", record.conditionResults.single().actualValue)
        assertTrue(record.resolvedEntities.any { it.contains("room-1") })
    }

    @Test
    fun simulateHistoricalEventRecordsMatchWithoutExecutingActions() = runTest {
        val executor = object : AutomationActionExecutor {
            var calls = 0

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                calls += 1
                return AutomationActionExecutionResult(true, "sent")
            }
        }
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-simulate",
                        name = "模拟转发",
                        trigger = AutomationTrigger.ChatMessage,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-keyword",
                                type = AutomationConditionType.MessageContains,
                                value = "hello",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-forward",
                                type = AutomationActionType.ForwardToRoom,
                                targetId = "room-target",
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
            executor = executor,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.simulate(
            AutomationEvent(
                id = "history-message-1",
                trigger = AutomationTrigger.ChatMessage,
                roomId = "room-1",
                roomName = "研发聊天室",
                senderName = "Alice",
                messageText = "hello from history",
            ),
        )
        advanceUntilIdle()

        assertEquals(0, executor.calls)
        assertEquals(emptyList(), holder.state.value.logs)
        val record = holder.state.value.debugRecords.single()
        assertTrue(record.matched)
        assertTrue(record.reason.contains("模拟运行"))
        assertTrue(record.reason.contains("未执行任何动作"))
        assertTrue(record.eventId.startsWith("manual-simulate:"))
        assertTrue(store.lastSnapshot.debugRecords.single().reason.contains("模拟运行"))
    }

    @Test
    fun sourceKindConditionAcceptsChatRoomAlias() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-chat-room-alias",
                        name = "聊天室别名",
                        trigger = AutomationTrigger.ChatMessage,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-source-kind",
                                type = AutomationConditionType.SourceKind,
                                value = "ChatRoom",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-log",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "{{source.kind}}: {{message.text}}",
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
                id = "event-room",
                trigger = AutomationTrigger.ChatMessage,
                sourceKind = "room",
                messageText = "hello room",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, holder.state.value.logs.size)
        assertTrue(holder.state.value.logs.single().success)
        assertEquals("room: hello room", holder.state.value.logs.single().message)
    }

    @Test
    fun chatMessageSupportsRoomNameSenderListAndMessageTypeConditions() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-rich-chat",
                        name = "总部图片",
                        trigger = AutomationTrigger.ChatMessage,
                        conditions = listOf(
                            AutomationCondition("condition-room", AutomationConditionType.RoomNameContains, "总部"),
                            AutomationCondition("condition-senders", AutomationConditionType.SenderUserIds, "user-a,user-b"),
                            AutomationCondition("condition-type", AutomationConditionType.MessageType, "image"),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-log",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "{{room.name}} {{message.type}} {{message.text}}",
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
                id = "event-rich-chat",
                trigger = AutomationTrigger.ChatMessage,
                senderUserId = "user-b",
                roomName = "总部聊天室",
                messageType = "text,image,file",
                messageText = "看图",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, holder.state.value.logs.size)
        assertEquals("总部聊天室 text,image,file 看图", holder.state.value.logs.single().message)
    }

    @Test
    fun timelineNoteSupportsChannelAndMessageTypeConditions() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-channel-note",
                        name = "频道投票",
                        trigger = AutomationTrigger.TimelineNote,
                        conditions = listOf(
                            AutomationCondition("condition-channel", AutomationConditionType.ChannelId, "channel-1"),
                            AutomationCondition("condition-timeline", AutomationConditionType.TimelineKind, "Channel"),
                            AutomationCondition("condition-type", AutomationConditionType.MessageType, "poll"),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-log",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "{{channel.id}} {{timeline.kind}} {{message.type}}",
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
                id = "event-channel-note",
                trigger = AutomationTrigger.TimelineNote,
                channelId = "channel-1",
                timelineKind = "Channel",
                messageType = "text,poll",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, holder.state.value.logs.size)
        assertEquals("channel-1 Channel text,poll", holder.state.value.logs.single().message)
    }

    @Test
    fun aiSemanticConditionFailureWritesLog() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-ai-condition",
                        name = "AI 条件",
                        trigger = AutomationTrigger.ChatAttention,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-ai",
                                type = AutomationConditionType.AiSemantic,
                                value = "判断是否需要回复",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-log",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "不应执行",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            aiBridge = FailingSemanticAiBridge,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-ai-condition",
                trigger = AutomationTrigger.ChatAttention,
                attentionKind = "Mention",
                messageText = "@me 看一下",
            ),
        )
        advanceUntilIdle()

        val log = holder.state.value.logs.single()
        assertEquals(false, log.success)
        assertEquals("条件判断", log.actionLabel)
        assertTrue(log.message.contains("AI 语义条件失败"))
        assertTrue(log.message.contains("AI 自动化未启用"))
    }

    @Test
    fun aiSemanticConditionDebugKeepsRawModelReply() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-ai-debug",
                        name = "AI 调试",
                        trigger = AutomationTrigger.ChatMessage,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-ai-debug",
                                type = AutomationConditionType.AiSemantic,
                                value = "用户在反馈 bug",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-log",
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
            aiBridge = SemanticReplyAiBridge("结论：满足\n对方说设置打不开，属于 bug 反馈。"),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-ai-debug",
                trigger = AutomationTrigger.ChatMessage,
                messageText = "更新后设置打不开",
            ),
        )
        advanceUntilIdle()

        val condition = holder.state.value.debugRecords.single().conditionResults.single()
        assertTrue(condition.matched)
        assertTrue(condition.actualValue.contains("结论：满足"))
        assertTrue(condition.message.contains("AI 返回"))
        assertTrue(holder.state.value.logs.single().success)
    }

    @Test
    fun aiSemanticConditionPassesImageAttachmentsToBridge() = runTest {
        val aiBridge = RecordingAiBridge("RESULT: YES\n图片里有需要处理的信息")
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-ai-image",
                        name = "AI 看图条件",
                        trigger = AutomationTrigger.ChatMessage,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-ai-image",
                                type = AutomationConditionType.AiSemantic,
                                value = "图片里包含错误截图",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-log",
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
            aiBridge = aiBridge,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-ai-image",
                trigger = AutomationTrigger.ChatMessage,
                messageText = "帮我看下这个截图",
                attachments = listOf(
                    AutomationAttachment(
                        id = "image-file",
                        name = "error.png",
                        type = "image/png",
                        description = "错误截图",
                    ),
                    AutomationAttachment(
                        id = "doc-file",
                        name = "readme.txt",
                        type = "text/plain",
                    ),
                    AutomationAttachment(
                        id = "duplicate-image",
                        name = "duplicate.png",
                        type = "IMAGE/PNG",
                    ),
                    AutomationAttachment(
                        id = "image-file",
                        name = "same.png",
                        type = "image/png",
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("image-file", "duplicate-image"), aiBridge.semanticFileIds)
        assertTrue(aiBridge.semanticFileContext.contains("error.png"))
        assertTrue(aiBridge.semanticFileContext.contains("doc-file"))
        assertTrue(aiBridge.semanticFileContext.contains("将作为图片传给 AI"))
        assertTrue(holder.state.value.logs.single().success)
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
    fun aiGeneratedActionPassesImageAttachmentsToBridge() = runTest {
        val aiBridge = RecordingAiBridge(generatedText = "图片显示更新失败")
        val executor = AppAutomationActionExecutor(
            chatRepository = cc.hhhl.client.repository.ChatRepository(tokenProvider = { "token" }),
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            aiBridge = aiBridge,
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-ai-log",
                type = AutomationActionType.AiGenerateLog,
                bodyTemplate = "总结图片里的问题",
            ),
            event = AutomationEvent(
                id = "event-ai-action-image",
                trigger = AutomationTrigger.ChatMessage,
                messageText = "看截图",
                attachments = listOf(
                    AutomationAttachment(
                        id = "image-file",
                        name = "screen.png",
                        type = "image/png",
                    ),
                    AutomationAttachment(
                        id = "video-file",
                        name = "demo.mp4",
                        type = "video/mp4",
                    ),
                ),
            ),
            title = "AI 日志",
            body = "原始日志",
        )

        assertTrue(result.success)
        assertEquals(listOf("image-file"), aiBridge.generatedFileIds)
        assertTrue(aiBridge.generatedFileContext.contains("screen.png"))
        assertTrue(aiBridge.generatedFileContext.contains("demo.mp4"))
    }

    @Test
    fun webhookPayloadIncludesAttachmentMetadataAndVariables() = runTest {
        var webhookBody = ""
        val executor = AppAutomationActionExecutor(
            chatRepository = cc.hhhl.client.repository.ChatRepository(tokenProvider = { "token" }),
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            attachmentAuthHeaderProvider = { mapOf("Authorization" to "Bearer token-123") },
            httpClient = HttpClient(
                MockEngine { request ->
                    webhookBody = (request.body as TextContent).text
                    respond("""{"ok":true}""", HttpStatusCode.OK)
                },
            ) {
                install(ContentNegotiation) { json() }
            },
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-webhook",
                type = AutomationActionType.Webhook,
                targetId = "http://10.0.2.2:3000/hook",
                titleTemplate = "附件消息",
                bodyTemplate = "{{message.text}}",
            ),
            event = AutomationEvent(
                id = "event-attachment",
                trigger = AutomationTrigger.ChatMessage,
                chatMessageId = "message-attachment",
                sourceKind = "room",
                senderName = "Alice",
                senderUsername = "alice",
                roomId = "room-1",
                roomName = "测试聊天室",
                messageText = "看图",
                messageType = "text,file,image",
                attachments = listOf(
                    AutomationAttachment(
                        id = "file-image",
                        name = "demo.png",
                        type = "image/png",
                        url = "https://example.com/demo.png",
                        thumbnailUrl = "https://example.com/demo-thumb.png",
                        description = "测试图片",
                        size = 12345,
                        isSensitive = false,
                    ),
                ),
            ),
            title = "附件消息",
            body = "看图",
        )

        assertTrue(result.success)
        val payload = Json.parseToJsonElement(webhookBody).jsonObject
        assertEquals("text,file,image", payload.getValue("messageType").jsonPrimitive.content)
        assertEquals("1", payload.getValue("attachmentCount").jsonPrimitive.content)
        val attachment = payload.getValue("attachments").jsonArray.single().jsonObject
        assertEquals("file-image", attachment.getValue("id").jsonPrimitive.content)
        assertEquals("demo.png", attachment.getValue("name").jsonPrimitive.content)
        assertEquals("image/png", attachment.getValue("type").jsonPrimitive.content)
        assertEquals("https://example.com/demo.png", attachment.getValue("url").jsonPrimitive.content)
        assertEquals("https://example.com/demo-thumb.png", attachment.getValue("thumbnailUrl").jsonPrimitive.content)
        assertEquals("12345", attachment.getValue("size").jsonPrimitive.content)
        assertEquals("false", attachment.getValue("isSensitive").jsonPrimitive.content)
        val variables = payload.getValue("variables").jsonObject
        assertEquals("1", variables.getValue("attachment.count").jsonPrimitive.content)
        assertEquals("https://example.com/demo.png", variables.getValue("attachment.url").jsonPrimitive.content)
        assertEquals("测试聊天室", variables.getValue("room.name").jsonPrimitive.content)
        val authHeaders = payload.getValue("attachmentAuthHeaders").jsonObject
        assertEquals("Bearer token-123", authHeaders.getValue("Authorization").jsonPrimitive.content)
    }

    @Test
    fun webhookPayloadDoesNotLeakAttachmentAuthHeadersToPrivateLanByDefault() = runTest {
        val payload = webhookPayloadForAttachmentTarget("http://192.168.1.23:3000/hook")

        val authHeaders = payload.getValue("attachmentAuthHeaders").jsonObject
        assertEquals(0, authHeaders.size)
    }

    @Test
    fun webhookPayloadAllowsAttachmentAuthHeadersToPrivateLanWithExplicitOptIn() = runTest {
        val payload = webhookPayloadForAttachmentTarget("http://192.168.1.23:3000/hook?hhhlAttachmentAuth=1")

        val authHeaders = payload.getValue("attachmentAuthHeaders").jsonObject
        assertEquals("Bearer token-123", authHeaders.getValue("Authorization").jsonPrimitive.content)
    }

    private suspend fun webhookPayloadForAttachmentTarget(targetId: String): kotlinx.serialization.json.JsonObject {
        var webhookBody = ""
        val executor = AppAutomationActionExecutor(
            chatRepository = cc.hhhl.client.repository.ChatRepository(tokenProvider = { "token" }),
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            attachmentAuthHeaderProvider = { mapOf("Authorization" to "Bearer token-123") },
            httpClient = HttpClient(
                MockEngine { request ->
                    webhookBody = (request.body as TextContent).text
                    respond("""{"ok":true}""", HttpStatusCode.OK)
                },
            ) {
                install(ContentNegotiation) { json() }
            },
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-webhook",
                type = AutomationActionType.Webhook,
                targetId = targetId,
                titleTemplate = "附件消息",
                bodyTemplate = "{{message.text}}",
            ),
            event = AutomationEvent(
                id = "event-attachment",
                trigger = AutomationTrigger.ChatMessage,
                chatMessageId = "message-attachment",
                sourceKind = "room",
                senderName = "Alice",
                senderUsername = "alice",
                roomId = "room-1",
                roomName = "测试聊天室",
                messageText = "看图",
                messageType = "text,file,image",
                attachments = listOf(
                    AutomationAttachment(
                        id = "file-image",
                        name = "demo.png",
                        type = "image/png",
                        url = "https://example.com/demo.png",
                    ),
                ),
            ),
            title = "附件消息",
            body = "看图",
        )

        assertTrue(result.success)
        return Json.parseToJsonElement(webhookBody).jsonObject
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

    @Test
    fun retryLogReplaysFailedActionSnapshot() = runTest {
        val executor = object : AutomationActionExecutor {
            var shouldFail = true
            var calls = 0

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                calls += 1
                return if (shouldFail) {
                    AutomationActionExecutionResult(false, "network failed")
                } else {
                    AutomationActionExecutionResult(true, "sent ${event.messageText}")
                }
            }
        }
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-retry",
                        name = "Retry forward",
                        trigger = AutomationTrigger.ChatMessage,
                        actions = listOf(
                            AutomationAction(
                                id = "action-forward",
                                type = AutomationActionType.ForwardToRoom,
                                targetId = "room-target",
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
            executor = executor,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-retry",
                trigger = AutomationTrigger.ChatMessage,
                messageText = "hello retry",
            ),
        )
        advanceUntilIdle()

        val failedLog = holder.state.value.logs.single()
        assertEquals(false, failedLog.success)
        assertEquals("hello retry", failedLog.eventSnapshot?.messageText)
        assertEquals(AutomationActionType.ForwardToRoom, failedLog.actionSnapshot?.type)

        executor.shouldFail = false
        holder.retryLog(failedLog.id)
        advanceUntilIdle()

        assertEquals(2, executor.calls)
        val retryLog = holder.state.value.logs.first()
        assertTrue(retryLog.success)
        assertEquals(failedLog.id, retryLog.retryOfLogId)
        assertTrue(retryLog.message.contains("hello retry"))
    }

    @Test
    fun burstLimitBlocksRiskyRuleLoops() = runTest {
        val executor = object : AutomationActionExecutor {
            var calls = 0

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                calls += 1
                return AutomationActionExecutionResult(true, "sent")
            }
        }
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-loop",
                        name = "Loop guard",
                        trigger = AutomationTrigger.ChatMessage,
                        cooldownSeconds = 0,
                        maxExecutionsPer30Seconds = 2,
                        actions = listOf(
                            AutomationAction(
                                id = "action-reply",
                                type = AutomationActionType.ReplyToChat,
                                bodyTemplate = "{{message.text}}",
                                mentionSender = true,
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
            scope = TestScope(testScheduler),
        )
        holder.restore()

        repeat(3) { index ->
            holder.emit(
                AutomationEvent(
                    id = "event-loop-$index",
                    trigger = AutomationTrigger.ChatMessage,
                    roomId = "room-1",
                    chatMessageId = "message-$index",
                    senderUsername = "alice",
                    messageText = "hello $index",
                ),
            )
        }
        advanceUntilIdle()

        assertEquals(2, executor.calls)
        assertEquals(3, holder.state.value.logs.size)
        assertTrue(holder.state.value.logs.first().message.contains("上限"))
        assertTrue(holder.state.value.debugRecords.first().reason.contains("风控"))
    }

    @Test
    fun restoredWebhookRulesWithoutBurstLimitStillDoNotCooldown() = runTest {
        val executor = object : AutomationActionExecutor {
            var calls = 0

            override suspend fun execute(
                action: AutomationAction,
                event: AutomationEvent,
                title: String,
                body: String,
            ): AutomationActionExecutionResult {
                calls += 1
                return AutomationActionExecutionResult(true, "webhook sent")
            }
        }
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-webhook",
                        name = "Webhook",
                        trigger = AutomationTrigger.ChatMessage,
                        actions = listOf(
                            AutomationAction(
                                id = "action-webhook",
                                type = AutomationActionType.Webhook,
                                targetId = "https://example.com/webhook",
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
            executor = executor,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        repeat(3) { index ->
            holder.emit(
                AutomationEvent(
                    id = "event-webhook-$index",
                    trigger = AutomationTrigger.ChatMessage,
                    chatMessageId = "message-webhook-$index",
                    roomId = "room-1",
                    senderUsername = "alice",
                    messageText = "hello $index",
                ),
            )
        }
        advanceUntilIdle()

        assertEquals(3, executor.calls)
        val savedRule = holder.state.value.rules.single()
        assertEquals(0, savedRule.cooldownSeconds)
        assertEquals(0, savedRule.maxExecutionsPer30Seconds)
    }

    @Test
    fun confirmedWebhookDraftDefaultsToBurstLimitWithoutCooldown() = runTest {
        val store = MemoryAutomationStore()
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.addRuleDraft(
            AutomationRule(
                id = "rule-webhook-draft",
                name = "Webhook draft",
                trigger = AutomationTrigger.ChatMessage,
                actions = listOf(
                    AutomationAction(
                        id = "action-webhook-draft",
                        type = AutomationActionType.Webhook,
                        targetId = "host-webhook",
                        bodyTemplate = "{{message.text}}",
                    ),
                ),
            ),
        )

        val rule = holder.state.value.rules.single()
        assertEquals(0, rule.cooldownSeconds)
        assertEquals(12, rule.maxExecutionsPer30Seconds)
        assertEquals(12, store.lastSnapshot.rules.single().maxExecutionsPer30Seconds)
    }

    @Test
    fun aiDraftPreviewRequiresApprovalBeforeRuleIsCreated() = runTest {
        val store = MemoryAutomationStore()
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.previewRuleDraft(
            sourceText = "有人问我就提醒",
            rule = AutomationRule(
                id = "rule-preview",
                name = "提醒问题",
                trigger = AutomationTrigger.ChatAttention,
                conditions = listOf(AutomationCondition("condition-ai", AutomationConditionType.AiSemantic, "有人问我问题")),
                actions = listOf(AutomationAction("action-notify", AutomationActionType.SystemNotification, bodyTemplate = "{{event.body}}")),
            ),
            messages = listOf("用户 Alice -> user-1"),
        )

        assertEquals(emptyList(), holder.state.value.rules)
        assertEquals("提醒问题", holder.state.value.pendingDraftPreview?.rule?.name)
        assertEquals("用户 Alice -> user-1", holder.state.value.pendingDraftPreview?.messages?.single())

        holder.approveRuleDraft()

        assertEquals(1, holder.state.value.rules.size)
        assertEquals(null, holder.state.value.pendingDraftPreview)
        assertEquals("提醒问题", holder.state.value.rules.single().name)
        assertEquals(true, holder.state.value.editorOpen)
    }

    @Test
    fun aiReplyToChatSendsGeneratedReplyWithMentionAndReference() = runTest {
        val chatRepository = RecordingChatRepository()
        val executor = AppAutomationActionExecutor(
            chatRepository = chatRepository,
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            aiBridge = FakeAiBridge("可以，晚点我整理给你"),
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-ai-chat",
                type = AutomationActionType.AiReplyToChat,
                bodyTemplate = "自然回复对方的问题",
                mentionSender = true,
                replyToEvent = true,
            ),
            event = AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.ChatAttention,
                chatMessageId = "message-1",
                sourceKind = "room",
                roomId = "room-1",
                senderUsername = "alice",
                senderName = "Alice",
                messageText = "你能帮我整理一下吗？",
            ),
            title = "聊天回复",
            body = "原始模板",
        )

        assertTrue(result.success)
        assertEquals("room-1", chatRepository.lastRoomId)
        assertEquals("@alice 可以，晚点我整理给你", chatRepository.lastText)
        assertEquals("message-1", chatRepository.lastReplyId)
    }

    @Test
    fun aiForwardToRoomSendsGeneratedRewrite() = runTest {
        val chatRepository = RecordingChatRepository()
        val executor = AppAutomationActionExecutor(
            chatRepository = chatRepository,
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            aiBridge = FakeAiBridge("来自总部的摘要：需要处理更新失败"),
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-ai-forward-room",
                type = AutomationActionType.AiForwardToRoom,
                targetId = "room-target",
                bodyTemplate = "提取重点并按公告格式转发",
            ),
            event = AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.ChatMessage,
                roomName = "总部",
                senderName = "Alice",
                messageText = "更新失败，需要排查日志",
            ),
            title = "AI 转发",
            body = "原始正文不应直接发送",
        )

        assertTrue(result.success)
        assertEquals("room-target", chatRepository.lastRoomId)
        assertEquals("来自总部的摘要：需要处理更新失败", chatRepository.lastText)
    }

    @Test
    fun aiGeneratedChatMessageCanBeMarkedAndIgnoredByAutomation() = runTest {
        val chatRepository = RecordingChatRepository()
        var aiGeneratedMessage: ChatMessage? = null
        val executor = AppAutomationActionExecutor(
            chatRepository = chatRepository,
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            aiBridge = FakeAiBridge("AI 改写后的通知"),
            aiGeneratedChatMessageReporter = { aiGeneratedMessage = it },
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-ai-forward-room",
                type = AutomationActionType.AiForwardToRoom,
                targetId = "room-target",
                bodyTemplate = "摘要后转发",
            ),
            event = AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.ChatMessage,
                messageText = "需要同步给目标聊天室",
            ),
            title = "AI 转发",
            body = "原始正文",
        )

        assertTrue(result.success)
        val generatedEvent = aiGeneratedMessage?.toAutomationChatEvent(
            roomId = "room-target",
            currentUser = User(id = "me", displayName = "Me", username = "me", avatarInitial = "M"),
            isAiGenerated = true,
        )
        assertEquals(true, generatedEvent?.isAiGenerated)

        val loopHolder = AutomationStateHolder(
            store = MemoryAutomationStore(
                AutomationSnapshot(
                    rules = listOf(
                        AutomationRule(
                            id = "rule-loop",
                            name = "避免 AI 循环",
                            trigger = AutomationTrigger.ChatMessage,
                            ignoreOwnMessages = false,
                            conditions = listOf(AutomationCondition("condition-any", AutomationConditionType.MessageContains, "AI")),
                            actions = listOf(AutomationAction("action-log", AutomationActionType.AddLog, bodyTemplate = "{{event.body}}")),
                        ),
                    ),
                ),
            ),
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        loopHolder.restore()

        loopHolder.emit(generatedEvent!!)
        advanceUntilIdle()

        assertEquals(emptyList(), loopHolder.state.value.logs)
        assertTrue(loopHolder.state.value.debugRecords.single().reason.contains("AI 产生的消息"))
    }

    @Test
    fun mentionAttentionRuleGeneratesAndSendsChatReply() = runTest {
        val chatRepository = RecordingChatRepository()
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-mention-ai-reply",
                        name = "被 @ 时 AI 回复",
                        trigger = AutomationTrigger.ChatAttention,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-mention",
                                type = AutomationConditionType.AttentionKind,
                                value = "Mention",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-ai-chat",
                                type = AutomationActionType.AiReplyToChat,
                                bodyTemplate = "总结上下文后自然回复",
                                mentionSender = true,
                                replyToEvent = true,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            executor = AppAutomationActionExecutor(
                chatRepository = chatRepository,
                notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
                aiBridge = FakeAiBridge("我看了上下文，结论是可以继续推进"),
            ),
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "chat-attention:Mention:message-1",
                trigger = AutomationTrigger.ChatAttention,
                chatMessageId = "message-1",
                sourceKind = "room",
                senderUsername = "alice",
                senderName = "Alice",
                roomId = "room-1",
                messageText = "@me 你看下这里怎么处理？",
                attentionKind = "Mention",
            ),
        )
        advanceUntilIdle()

        assertEquals("room-1", chatRepository.lastRoomId)
        assertEquals("@alice 我看了上下文，结论是可以继续推进", chatRepository.lastText)
        assertEquals("message-1", chatRepository.lastReplyId)
        assertTrue(holder.state.value.logs.single().success)
    }

    @Test
    fun aiReplyToChatGeneratesImageUploadsAndSendsAttachment() = runTest {
        val chatRepository = RecordingChatRepository()
        val driveRepository = RecordingDriveFileRepository("generated-image")
        val executor = AppAutomationActionExecutor(
            chatRepository = chatRepository,
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            driveFileRepository = driveRepository,
            aiBridge = RecordingAiBridge(
                generatedText = "IMAGE_PROMPT: 一只白色机械猫坐在雨夜霓虹街道，电影感，高细节",
                imageBytes = byteArrayOf(1, 2, 3, 4),
            ),
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-ai-image-reply",
                type = AutomationActionType.AiReplyToChat,
                bodyTemplate = "如果对方要求生图就生成图片",
                replyToEvent = true,
            ),
            event = AutomationEvent(
                id = "event-image-request",
                trigger = AutomationTrigger.ChatAttention,
                chatMessageId = "message-image-request",
                sourceKind = "room",
                roomId = "room-1",
                messageText = "帮我生成一张白色机械猫图片",
            ),
            title = "聊天回复",
            body = "",
        )

        assertTrue(result.success)
        assertEquals("room-1", chatRepository.lastRoomId)
        assertEquals(listOf("generated-image"), chatRepository.lastFileIds)
        assertEquals("message-image-request", chatRepository.lastReplyId)
        assertEquals("image/png", driveRepository.lastUpload?.contentType)
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), driveRepository.lastUpload?.bytes?.toList())
    }

    @Test
    fun aiReplyToChatParsesImageParametersAndPreservesReplyOptions() = runTest {
        val chatRepository = RecordingChatRepository()
        val driveRepository = RecordingDriveFileRepository("generated-transparent-image")
        val aiBridge = RecordingAiBridge(
            generatedText = """IMAGE_PROMPT: {"prompt":"画一个透明背景的狐狸贴纸","size":"1024x1536","quality":"high","background":"auto","output_format":"png","output_compression":80,"n":2,"transparent":true,"caption":"给你两张候选"}""",
            imageBytes = byteArrayOf(1, 2, 3, 4),
        )
        val executor = AppAutomationActionExecutor(
            chatRepository = chatRepository,
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            driveFileRepository = driveRepository,
            aiBridge = aiBridge,
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-ai-image-params",
                type = AutomationActionType.AiReplyToChat,
                bodyTemplate = "如果对方要求生图就生成图片，保留回复要求",
                mentionSender = true,
                replyToEvent = true,
                quoteEvent = true,
            ),
            event = AutomationEvent(
                id = "event-image-params",
                trigger = AutomationTrigger.ChatAttention,
                chatMessageId = "message-image-params",
                sourceKind = "room",
                roomId = "room-1",
                senderUsername = "alice",
                messageText = "帮我生成两张透明背景高清狐狸贴纸，竖图",
            ),
            title = "聊天回复",
            body = "",
        )

        assertTrue(result.success)
        assertEquals("画一个透明背景的狐狸贴纸", aiBridge.imagePrompt.substringBefore('。'))
        assertTrue(aiBridge.imagePrompt.contains("透明背景"))
        assertEquals("1024x1536", aiBridge.imageOptions.size)
        assertEquals("high", aiBridge.imageOptions.quality)
        assertEquals("auto", aiBridge.imageOptions.background)
        assertEquals("png", aiBridge.imageOptions.outputFormat)
        assertEquals(80, aiBridge.imageOptions.outputCompression)
        assertEquals(2, aiBridge.imageOptions.count)
        assertEquals("@alice 给你两张候选", chatRepository.lastText)
        assertEquals("message-image-params", chatRepository.lastReplyId)
        assertEquals("message-image-params", chatRepository.lastQuoteId)
        assertEquals(listOf("generated-transparent-image"), chatRepository.lastFileIds)
    }

    @Test
    fun aiReplyToChatEditsImageAttachmentUploadsAndSendsAttachment() = runTest {
        val chatRepository = RecordingChatRepository()
        val driveRepository = RecordingDriveFileRepository("edited-image")
        val aiBridge = RecordingAiBridge(
            generatedText = "IMAGE_EDIT_PROMPT: 把图片背景改成赛博朋克城市夜景",
            imageBytes = byteArrayOf(8, 7, 6, 5),
        )
        val sourceBytes = byteArrayOf(1, 2, 3, 4)
        val executor = AppAutomationActionExecutor(
            chatRepository = chatRepository,
            notificationRepository = cc.hhhl.client.repository.NotificationRepository(tokenProvider = { "token" }),
            driveFileRepository = driveRepository,
            aiBridge = aiBridge,
            httpClient = HttpClient(MockEngine { request ->
                assertEquals("https://files.example.com/source.png", request.url.toString())
                respond(
                    content = sourceBytes,
                    status = HttpStatusCode.OK,
                    headers = io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "image/png"),
                )
            }),
        )

        val result = executor.execute(
            action = AutomationAction(
                id = "action-ai-edit-image-reply",
                type = AutomationActionType.AiReplyToChat,
                bodyTemplate = "如果对方要求编辑图片就按要求改图",
                replyToEvent = true,
            ),
            event = AutomationEvent(
                id = "event-image-edit-request",
                trigger = AutomationTrigger.ChatAttention,
                chatMessageId = "message-image-edit-request",
                sourceKind = "room",
                roomId = "room-1",
                messageText = "帮我把这张图改成赛博朋克背景",
                messageType = "text,image",
                attachments = listOf(
                    AutomationAttachment(
                        id = "source-file",
                        name = "source.png",
                        type = "image/png",
                        url = "https://files.example.com/source.png",
                    ),
                ),
            ),
            title = "聊天回复",
            body = "",
        )

        assertTrue(result.success)
        assertEquals("room-1", chatRepository.lastRoomId)
        assertEquals(listOf("edited-image"), chatRepository.lastFileIds)
        assertEquals("message-image-edit-request", chatRepository.lastReplyId)
        assertEquals("把图片背景改成赛博朋克城市夜景", aiBridge.imageEditPrompt)
        assertEquals(sourceBytes.toList(), aiBridge.imageEditSourceBytes.toList())
        assertEquals("image/png", aiBridge.imageEditContentType)
        assertEquals("source.png", aiBridge.imageEditFileName)
        assertEquals(byteArrayOf(8, 7, 6, 5).toList(), driveRepository.lastUpload?.bytes?.toList())
    }
}

private class FakeAiBridge(
    private val generatedText: String,
) : AiBridge {
    override suspend fun evaluateSemanticCondition(
        prompt: String,
        eventText: String,
        fileIds: List<String>,
        fileContext: String,
    ): AiBridgeResult {
        return AiBridgeResult.Success("YES")
    }

    override suspend fun generateAutomationText(
        prompt: String,
        eventText: String,
        fileIds: List<String>,
        fileContext: String,
    ): AiBridgeResult {
        return AiBridgeResult.Success(generatedText)
    }

    override suspend fun generateAutomationImage(
        prompt: String,
        options: AiImageRequestOptions,
    ): AiBridgeImageResult {
        return AiBridgeImageResult.Error("不会执行")
    }

    override suspend fun editAutomationImage(
        prompt: String,
        imageBytes: ByteArray,
        imageContentType: String,
        imageFileName: String,
        options: AiImageRequestOptions,
    ): AiBridgeImageResult {
        return AiBridgeImageResult.Error("不会执行")
    }
}

private object FailingSemanticAiBridge : AiBridge {
    override suspend fun evaluateSemanticCondition(
        prompt: String,
        eventText: String,
        fileIds: List<String>,
        fileContext: String,
    ): AiBridgeResult {
        return AiBridgeResult.Error("AI 自动化未启用")
    }

    override suspend fun generateAutomationText(
        prompt: String,
        eventText: String,
        fileIds: List<String>,
        fileContext: String,
    ): AiBridgeResult {
        return AiBridgeResult.Success("不会执行")
    }

    override suspend fun generateAutomationImage(
        prompt: String,
        options: AiImageRequestOptions,
    ): AiBridgeImageResult {
        return AiBridgeImageResult.Error("不会执行")
    }

    override suspend fun editAutomationImage(
        prompt: String,
        imageBytes: ByteArray,
        imageContentType: String,
        imageFileName: String,
        options: AiImageRequestOptions,
    ): AiBridgeImageResult {
        return AiBridgeImageResult.Error("不会执行")
    }
}

private class SemanticReplyAiBridge(
    private val semanticReply: String,
) : AiBridge {
    override suspend fun evaluateSemanticCondition(
        prompt: String,
        eventText: String,
        fileIds: List<String>,
        fileContext: String,
    ): AiBridgeResult {
        return AiBridgeResult.Success(semanticReply)
    }

    override suspend fun generateAutomationText(
        prompt: String,
        eventText: String,
        fileIds: List<String>,
        fileContext: String,
    ): AiBridgeResult {
        return AiBridgeResult.Success("不会执行")
    }

    override suspend fun generateAutomationImage(
        prompt: String,
        options: AiImageRequestOptions,
    ): AiBridgeImageResult {
        return AiBridgeImageResult.Error("不会执行")
    }

    override suspend fun editAutomationImage(
        prompt: String,
        imageBytes: ByteArray,
        imageContentType: String,
        imageFileName: String,
        options: AiImageRequestOptions,
    ): AiBridgeImageResult {
        return AiBridgeImageResult.Error("不会执行")
    }
}

private class RecordingAiBridge(
    private val semanticReply: String = "RESULT: YES",
    private val generatedText: String = "generated",
    private val imageBytes: ByteArray = byteArrayOf(9, 9, 9),
) : AiBridge {
    var semanticFileIds: List<String> = emptyList()
        private set
    var semanticFileContext: String = ""
        private set
    var generatedFileIds: List<String> = emptyList()
        private set
    var generatedFileContext: String = ""
        private set
    var imagePrompt: String = ""
        private set
    var imageOptions: AiImageRequestOptions = AiImageRequestOptions()
        private set
    var imageEditPrompt: String = ""
        private set
    var imageEditOptions: AiImageRequestOptions = AiImageRequestOptions()
        private set
    var imageEditSourceBytes: ByteArray = byteArrayOf()
        private set
    var imageEditContentType: String = ""
        private set
    var imageEditFileName: String = ""
        private set

    override suspend fun evaluateSemanticCondition(
        prompt: String,
        eventText: String,
        fileIds: List<String>,
        fileContext: String,
    ): AiBridgeResult {
        semanticFileIds = fileIds
        semanticFileContext = fileContext
        return AiBridgeResult.Success(semanticReply)
    }

    override suspend fun generateAutomationText(
        prompt: String,
        eventText: String,
        fileIds: List<String>,
        fileContext: String,
    ): AiBridgeResult {
        generatedFileIds = fileIds
        generatedFileContext = fileContext
        return AiBridgeResult.Success(generatedText)
    }

    override suspend fun generateAutomationImage(
        prompt: String,
        options: AiImageRequestOptions,
    ): AiBridgeImageResult {
        imagePrompt = prompt
        imageOptions = options
        return AiBridgeImageResult.Success(
            images = listOf(
                AiGeneratedImage(
                    bytes = imageBytes,
                    contentType = "image/png",
                    revisedPrompt = prompt,
                ),
            ),
        )
    }

    override suspend fun editAutomationImage(
        prompt: String,
        imageBytes: ByteArray,
        imageContentType: String,
        imageFileName: String,
        options: AiImageRequestOptions,
    ): AiBridgeImageResult {
        imageEditPrompt = prompt
        imageEditOptions = options
        imageEditSourceBytes = imageBytes
        imageEditContentType = imageContentType
        imageEditFileName = imageFileName
        return AiBridgeImageResult.Success(
            images = listOf(
                AiGeneratedImage(
                    bytes = this.imageBytes,
                    contentType = "image/png",
                    revisedPrompt = prompt,
                ),
            ),
        )
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

private class InterleavingAutomationStore(
    initialSnapshot: AutomationSnapshot,
    private val injectedEvent: AutomationExecutedEvent,
) : AutomationStore {
    var lastSnapshot: AutomationSnapshot = initialSnapshot
        private set
    private var injected = false

    override fun read(accountId: String): AutomationSnapshot = lastSnapshot

    override fun write(accountId: String, snapshot: AutomationSnapshot) {
        injectOnce()
        lastSnapshot = snapshot
    }

    override fun update(accountId: String, transform: (AutomationSnapshot) -> AutomationSnapshot): AutomationSnapshot {
        injectOnce()
        val updated = transform(lastSnapshot)
        lastSnapshot = updated
        return updated
    }

    override fun clearAccount(accountId: String) {
        lastSnapshot = AutomationSnapshot()
    }

    private fun injectOnce() {
        if (injected) return
        injected = true
        lastSnapshot = lastSnapshot.copy(
            executedEvents = mergeStoredAutomationExecutedEvents(
                current = lastSnapshot.executedEvents,
                updates = listOf(injectedEvent),
            ),
        )
    }
}

private class RecordingDriveFileRepository(
    private val fileId: String,
) : DriveFileRepository(tokenProvider = { "token" }) {
    var lastUpload: DriveFileUpload? = null
        private set

    override suspend fun upload(upload: DriveFileUpload): DriveFileRepositoryResult {
        lastUpload = upload
        return DriveFileRepositoryResult.Success(
            DriveFile(
                id = fileId,
                name = upload.fileName,
                type = upload.contentType,
                url = "https://example.com/${upload.fileName}",
                thumbnailUrl = null,
                comment = upload.comment,
                size = upload.bytes.size.toLong(),
                isSensitive = false,
            ),
        )
    }
}

private class RecordingChatRepository : ChatRepository(tokenProvider = { "token" }) {
    var lastRoomId: String? = null
    var lastUserId: String? = null
    var lastText: String? = null
    var lastFileIds: List<String> = emptyList()
    var lastReplyId: String? = null
    var lastQuoteId: String? = null

    override suspend fun sendMessage(
        roomId: String,
        text: String,
        fileId: String?,
        fileIds: List<String>,
        replyId: String?,
        quoteId: String?,
    ): ChatMessageRepositoryResult {
        lastRoomId = roomId
        lastText = text
        lastFileIds = fileIds + listOfNotNull(fileId)
        lastReplyId = replyId
        lastQuoteId = quoteId
        return ChatMessageRepositoryResult.Created(fakeMessage(roomId, text))
    }

    override suspend fun sendUserMessage(
        userId: String,
        text: String,
        fileId: String?,
        fileIds: List<String>,
        replyId: String?,
        quoteId: String?,
    ): ChatMessageRepositoryResult {
        lastUserId = userId
        lastText = text
        lastFileIds = fileIds + listOfNotNull(fileId)
        lastReplyId = replyId
        lastQuoteId = quoteId
        return ChatMessageRepositoryResult.Created(fakeMessage("", text))
    }
}

private fun fakeMessage(roomId: String, text: String): ChatMessage {
    return ChatMessage(
        id = "created-message",
        roomId = roomId,
        fromUser = User(id = "me", displayName = "Me", username = "me", avatarInitial = "M"),
        text = text,
        createdAtLabel = "刚刚",
    )
}
