package cc.hhhl.client.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelinePresentationTest {
    @Test
    fun timelineAiActionsExposeDigestInteractionAndFilterSuggestions() {
        val actions = timelineAiActions(
            enabled = true,
            isProcessing = false,
            onAiDigest = {},
            onAiReplyOpportunities = {},
            onAiFilterSuggestions = {},
        )

        assertEquals(
            listOf("AI 时间线速览", "AI 互动建议", "AI 过滤建议"),
            actions.map { it.label },
        )
        assertEquals(listOf(true, true, true), actions.map { it.enabled })
    }
}
