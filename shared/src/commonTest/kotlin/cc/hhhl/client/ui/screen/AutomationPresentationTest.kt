package cc.hhhl.client.ui.screen

import cc.hhhl.client.automation.AutomationExecutionLog
import cc.hhhl.client.automation.AutomationRuleDebugRecord
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class AutomationPresentationTest {
    @Test
    fun latestAutomationDebugRecordByRuleIdKeepsNewestRecordPerRule() {
        val records = listOf(
            debugRecord(id = "old-a", ruleId = "rule-a", createdAt = 10L),
            debugRecord(id = "new-b", ruleId = "rule-b", createdAt = 30L),
            debugRecord(id = "new-a", ruleId = "rule-a", createdAt = 40L),
            debugRecord(id = "old-b", ruleId = "rule-b", createdAt = 20L),
        )

        val latest = records.latestAutomationDebugRecordByRuleId()

        assertEquals("new-a", latest["rule-a"]?.id)
        assertEquals("new-b", latest["rule-b"]?.id)
    }

    @Test
    fun automationExecutionLogCreatedAtLabelShowsCompactLocalMinute() {
        val label = AutomationExecutionLog(
            id = "log-1",
            ruleId = "rule-1",
            ruleName = "Rule",
            eventId = "event-1",
            eventLabel = "聊天消息",
            actionLabel = "写入日志",
            message = "ok",
            success = true,
            createdAtEpochMillis = 1_784_351_640_000L,
        ).createdAtLabel()

        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}").matches(label))
        assertEquals(
            "",
            AutomationExecutionLog(
                id = "log-empty",
                ruleId = "rule-1",
                ruleName = "Rule",
                eventId = "event-1",
                eventLabel = "聊天消息",
                actionLabel = "写入日志",
                message = "ok",
                success = true,
                createdAtEpochMillis = 0L,
            ).createdAtLabel(),
        )
    }

    private fun debugRecord(
        id: String,
        ruleId: String,
        createdAt: Long,
    ): AutomationRuleDebugRecord {
        return AutomationRuleDebugRecord(
            id = id,
            ruleId = ruleId,
            ruleName = ruleId,
            eventId = "event-$id",
            eventLabel = "聊天消息",
            eventSummary = "hello",
            matched = true,
            reason = "全部条件满足",
            createdAtEpochMillis = createdAt,
        )
    }
}
