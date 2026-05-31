package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class AiResultActionsTest {
    @Test
    fun checklistTextNormalizesCommonBullets() {
        val text = """
            1. 修复聊天输入框
            - [ ] 更新版本日志
            • 验证模拟器
        """.trimIndent()

        assertEquals(
            "- 修复聊天输入框\n- 更新版本日志\n- 验证模拟器",
            aiResultChecklistText(text),
        )
    }

    @Test
    fun mutedWordCandidatePrefersQuotedSuggestion() {
        assertEquals(
            "抽奖广告",
            aiResultMutedWordCandidate("- 建议添加静音词：\"抽奖广告\" 原因：重复刷屏"),
        )
        assertEquals(
            "登录失败",
            aiResultMutedWordCandidate("过滤词：登录失败，因为当前时间线反复出现"),
        )
    }

    @Test
    fun referencedNoteIdMatchesKnownIdsOnly() {
        assertEquals(
            "note-long-123",
            aiResultReferencedNoteId(
                text = "稍后看 [ID: note-long-123]，另一个 note",
                candidateNoteIds = listOf("note", "note-long-123"),
            ),
        )
        assertEquals(
            null,
            aiResultReferencedNoteId("没有提到真实帖子", listOf("note-1")),
        )
    }
}
