package cc.hhhl.client.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AutomationRuleDraftParserTest {
    @Test
    fun parsesAiRuleDraftJsonIntoEnabledRuleByDefault() {
        val result = parseAutomationRuleDraft(
            """
            {
              "name": "有人问我时提醒",
              "trigger": "ChatAttention",
              "conditionMode": "All",
              "conditions": [
                { "type": "AiSemantic", "value": "对方在问我一个需要回复的问题", "enabled": true }
              ],
              "actionMode": "Sequential",
              "actions": [
                {
                  "type": "SystemNotification",
                  "titleTemplate": "需要回复",
                  "bodyTemplate": "{{sender.name}}：{{message.text}}",
                  "enabled": true,
                  "failurePolicy": "Continue"
                }
              ],
              "ignoreOwnMessages": true,
              "cooldownSeconds": 120
            }
            """.trimIndent(),
        )

        val rule = assertNotNull(result.rule)
        assertEquals(true, rule.enabled)
        assertEquals("有人问我时提醒", rule.name)
        assertEquals(AutomationTrigger.ChatAttention, rule.trigger)
        assertEquals(AutomationConditionType.AiSemantic, rule.conditions.single().type)
        assertEquals(AutomationActionType.SystemNotification, rule.actions.single().type)
        assertEquals(120, rule.cooldownSeconds)
        assertEquals(12, rule.maxExecutionsPer30Seconds)
    }

    @Test
    fun riskyAiDraftGetsDefaultLoopProtection() {
        val result = parseAutomationRuleDraft(
            """
            {
              "name": "转发聊天室消息",
              "trigger": "ChatMessage",
              "conditions": [
                { "roomName": "总部" }
              ],
              "actions": [
                { "type": "ForwardToRoom", "targetId": "目标聊天室", "bodyTemplate": "{{event.body}}" }
              ]
            }
            """.trimIndent(),
        )

        val rule = assertNotNull(result.rule)
        assertEquals(300, rule.cooldownSeconds)
        assertEquals(2, rule.maxExecutionsPer30Seconds)
        assertEquals(true, rule.ignoreOwnMessages)
    }

    @Test
    fun infersHumanEntityFieldsIntoConditions() {
        val result = parseAutomationRuleDraft(
            """
            {
              "name": "总部频道图片提醒",
              "trigger": "TimelineNote",
              "conditions": [
                { "channelName": "总部" },
                { "userNames": "张三，李四" },
                { "messageType": "image" }
              ],
              "actions": [
                { "type": "SystemNotification", "bodyTemplate": "{{sender.name}}：{{message.text}}" }
              ]
            }
            """.trimIndent(),
        )

        val rule = assertNotNull(result.rule)
        assertEquals(AutomationTrigger.TimelineNote, rule.trigger)
        assertEquals(AutomationConditionType.ChannelNameContains, rule.conditions[0].type)
        assertEquals("总部", rule.conditions[0].value)
        assertEquals(AutomationConditionType.SenderNameContains, rule.conditions[1].type)
        assertEquals("张三，李四", rule.conditions[1].value)
        assertEquals(AutomationConditionType.MessageType, rule.conditions[2].type)
        assertEquals("image", rule.conditions[2].value)
    }
}
