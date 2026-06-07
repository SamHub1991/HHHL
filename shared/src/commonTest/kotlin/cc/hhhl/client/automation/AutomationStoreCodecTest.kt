package cc.hhhl.client.automation

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import cc.hhhl.client.state.ChatAttentionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class AutomationStoreCodecTest {
    @Test
    fun encodeAndDecodeTrimAutomationSnapshot() {
        val snapshot = AutomationSnapshot(
            rules = (0 until 120).map { index ->
                AutomationRule(
                    id = "rule-$index",
                    name = "Rule $index",
                    trigger = AutomationTrigger.ChatMessage,
                    conditions = listOf(
                        AutomationCondition(
                            id = "condition-$index",
                            type = AutomationConditionType.MessageContains,
                            value = "hello",
                        ),
                    ),
                    actions = listOf(
                        AutomationAction(
                            id = "action-$index",
                            type = AutomationActionType.AddLog,
                            bodyTemplate = "{{sender.name}}: {{message.text}}",
                        ),
                    ),
                )
            },
            logs = (0 until 200).map { index ->
                AutomationExecutionLog(
                    id = "log-$index",
                    ruleId = "rule-$index",
                    ruleName = "Rule $index",
                    eventId = "event-$index",
                    eventLabel = "聊天消息",
                    actionLabel = "写入日志",
                    message = "message $index",
                    success = true,
                    createdAtEpochMillis = index.toLong(),
                    eventSnapshot = AutomationEvent(
                        id = "event-$index",
                        trigger = AutomationTrigger.ChatMessage,
                        messageText = "hello $index",
                    ).snapshot(),
                    actionSnapshot = AutomationAction(
                        id = "action-$index",
                        type = AutomationActionType.AddLog,
                        bodyTemplate = "hello $index",
                    ),
                )
            },
            debugRecords = (0 until 260).map { index ->
                AutomationRuleDebugRecord(
                    id = "debug-$index",
                    ruleId = "rule-$index",
                    ruleName = "Rule $index",
                    eventId = "event-$index",
                    eventLabel = "聊天消息",
                    eventSummary = "message $index",
                    matched = index % 2 == 0,
                    reason = "reason $index",
                    conditionResults = listOf(
                        AutomationConditionDebugResult(
                            conditionId = "condition-$index",
                            conditionLabel = "消息包含",
                            expectedValue = "hello",
                            actualValue = "hello world",
                            matched = true,
                            message = "已命中",
                        ),
                    ),
                    resolvedEntities = listOf("聊天室 Room -> room-$index"),
                    createdAtEpochMillis = index.toLong(),
                )
            },
            executedEvents = (0 until 2100).map { index ->
                AutomationExecutedEvent(
                    key = "account-1:rule-$index:chat-message:message-$index",
                    ruleId = "rule-$index",
                    eventKey = "chat-message:message-$index",
                    eventId = "event-$index",
                    createdAtEpochMillis = index.toLong(),
                )
            },
        )

        val decoded = AutomationStoreCodec.decode(AutomationStoreCodec.encode(snapshot))

        assertEquals(120, decoded.rules.size)
        assertEquals("rule-0", decoded.rules.first().id)
        assertEquals("rule-119", decoded.rules.last().id)
        assertEquals(AutomationStoreCodec.MAX_LOGS, decoded.logs.size)
        assertEquals("log-0", decoded.logs.first().id)
        assertEquals("log-159", decoded.logs.last().id)
        assertEquals("hello 0", decoded.logs.first().eventSnapshot?.messageText)
        assertEquals(AutomationActionType.AddLog, decoded.logs.first().actionSnapshot?.type)
        assertEquals(AutomationStoreCodec.MAX_DEBUG_RECORDS, decoded.debugRecords.size)
        assertEquals("debug-0", decoded.debugRecords.first().id)
        assertEquals("debug-239", decoded.debugRecords.last().id)
        assertEquals(AutomationStoreCodec.MAX_EXECUTED_EVENTS, decoded.executedEvents.size)
        assertEquals("account-1:rule-0:chat-message:message-0", decoded.executedEvents.first().key)
        assertEquals("account-1:rule-1999:chat-message:message-1999", decoded.executedEvents.last().key)
    }

    @Test
    fun renderTemplateUsesEventVariables() {
        val event = AutomationEvent(
            id = "event-1",
            trigger = AutomationTrigger.ChatMessage,
            sourceKind = "room",
            senderUserId = "user-1",
            senderName = "Alice",
            roomId = "room-1",
            messageText = "hello",
            chatMessageId = "message-1",
            senderUsername = "alice",
            senderHost = "example.com",
            noteId = "note-1",
            channelId = "channel-1",
            messageType = "text,file,image",
            attachments = listOf(
                AutomationAttachment(
                    id = "file-1",
                    name = "demo.png",
                    type = "image/png",
                    url = "https://example.com/demo.png",
                    thumbnailUrl = "https://example.com/demo-thumb.png",
                    size = 12345,
                ),
            ),
        )

        val rendered = renderAutomationTemplate(
            template = "{{sender.mention}} @ {{room.id}} / {{message.id}} / {{message.type}} / {{attachment.count}} / {{attachment.name}} / {{attachment.url}} / {{note.link}} / {{channel.link}}: {{message.text}}",
            event = event,
        )

        assertEquals("@alice@example.com @ room-1 / message-1 / text,file,image / 1 / demo.png / https://example.com/demo.png / https://dc.hhhl.cc/notes/note-1 / https://dc.hhhl.cc/channels/channel-1: hello", rendered)
    }

    @Test
    fun chatMessageAttachmentBecomesAutomationAttachment() {
        val message = ChatMessage(
            id = "message-image",
            roomId = "room-1",
            fromUser = User(id = "alice", displayName = "Alice", username = "alice", avatarInitial = "A"),
            text = "看图",
            file = DriveFile(
                id = "file-image",
                name = "image.png",
                type = "image/png",
                url = "https://example.com/image.png",
                thumbnailUrl = "https://example.com/image-thumb.png",
                comment = "截图",
                size = 2048,
                isSensitive = false,
            ),
            createdAtLabel = "刚刚",
        )

        val event = message.toAutomationChatEvent(roomId = "room-1")

        assertEquals("text,file,image", event.messageType)
        assertEquals(1, event.attachments.size)
        assertEquals("file-image", event.attachments.single().id)
        assertEquals("image.png", event.attachments.single().name)
        assertEquals("https://example.com/image.png", event.attachments.single().url)
    }

    @Test
    fun chatAttentionKindDetectsMentionOfCurrentUser() {
        val currentUser = User(
            id = "me",
            displayName = "Me",
            username = "sunxiaochuan",
            avatarInitial = "M",
        )
        val message = ChatMessage(
            id = "message-mention",
            roomId = "room-1",
            fromUser = User(id = "alice", displayName = "Alice", username = "alice", avatarInitial = "A"),
            text = "@sunxiaochuan 帮我总结下上下文",
            createdAtLabel = "刚刚",
        )

        val attentionKind = message.automationChatAttentionKind(currentUser = currentUser)
        val event = message.toAutomationChatEvent(
            roomId = "room-1",
            attentionKind = attentionKind?.name.orEmpty(),
            currentUser = currentUser,
        )

        assertEquals(ChatAttentionKind.Mention, attentionKind)
        assertEquals(AutomationTrigger.ChatAttention, event.trigger)
        assertEquals("Mention", event.attentionKind)
    }
}
