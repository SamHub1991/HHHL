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
            listOf("AI"),
            actions.map { it.label },
        )
        assertEquals(listOf(true), actions.map { it.enabled })
        assertEquals(
            listOf("时间线速览", "互动建议", "过滤建议"),
            actions.first().children.map { it.label },
        )
    }
}
