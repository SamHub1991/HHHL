package cc.hhhl.client.automation

import cc.hhhl.client.ai.AiBridge
import cc.hhhl.client.ai.AiBridgeResult
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.User
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

private object FailingSemanticAiBridge : AiBridge {
    override suspend fun evaluateSemanticCondition(prompt: String, eventText: String): AiBridgeResult {
        return AiBridgeResult.Error("AI 自动化未启用")
    }

    override suspend fun generateAutomationText(prompt: String, eventText: String): AiBridgeResult {
        return AiBridgeResult.Success("不会执行")
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

private class RecordingChatRepository : ChatRepository(tokenProvider = { "token" }) {
    var lastRoomId: String? = null
    var lastUserId: String? = null
    var lastText: String? = null
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
        lastReplyId = replyId
        lastQuoteId = quoteId
        return ChatMessageRepositoryResult.Created(fakeMessage(roomId, text))
    }

    override suspend fun sendUserMessage(
        userId: String,
        text: String,
        fileId: String?,
        replyId: String?,
        quoteId: String?,
    ): ChatMessageRepositoryResult {
        lastUserId = userId
        lastText = text
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
