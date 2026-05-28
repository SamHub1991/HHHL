package cc.hhhl.client.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class AutomationStoreCodecTest {
    @Test
    fun encodeAndDecodeTrimAutomationSnapshot() {
        val snapshot = AutomationSnapshot(
            rules = (0 until 96).map { index ->
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
                )
            },
        )

        val decoded = AutomationStoreCodec.decode(AutomationStoreCodec.encode(snapshot))

        assertEquals(AutomationStoreCodec.MAX_RULES, decoded.rules.size)
        assertEquals("rule-0", decoded.rules.first().id)
        assertEquals("rule-79", decoded.rules.last().id)
        assertEquals(AutomationStoreCodec.MAX_LOGS, decoded.logs.size)
        assertEquals("log-0", decoded.logs.first().id)
        assertEquals("log-159", decoded.logs.last().id)
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
        )

        val rendered = renderAutomationTemplate(
            template = "{{sender.name}} @ {{room.id}}: {{message.text}}",
            event = event,
        )

        assertEquals("Alice @ room-1: hello", rendered)
    }
}
