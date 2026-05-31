package cc.hhhl.client.ui.screen

import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.model.InstanceCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineTabsSpecTest {
    @Test
    fun timelineTabsIncludeSocialAndAvailableBubbleTimeline() {
        assertEquals(
            listOf(
                TimelineKind.Home,
                TimelineKind.Social,
                TimelineKind.Local,
                TimelineKind.Global,
                TimelineKind.Bubble,
                TimelineKind.Featured,
                TimelineKind.Mentions,
            ),
            availableTimelineKinds(
                InstanceCapabilities(
                    localTimelineAvailable = true,
                    globalTimelineAvailable = true,
                    bubbleTimelineAvailable = true,
                ),
            ),
        )
    }

    @Test
    fun timelineTabsRespectInstanceTimelineCapabilities() {
        assertEquals(
            listOf(TimelineKind.Home, TimelineKind.Social, TimelineKind.Featured, TimelineKind.Mentions),
            availableTimelineKinds(
                InstanceCapabilities(
                    localTimelineAvailable = false,
                    globalTimelineAvailable = false,
                    bubbleTimelineAvailable = false,
                ),
            ),
        )
    }

    @Test
    fun timelinePrimaryTabsKeepHighFrequencyViewsVisible() {
        val available = listOf(
            TimelineKind.Home,
            TimelineKind.Social,
            TimelineKind.Local,
            TimelineKind.Global,
            TimelineKind.Bubble,
            TimelineKind.Featured,
            TimelineKind.Mentions,
        )

        assertEquals(
            listOf(TimelineKind.Home, TimelineKind.Social, TimelineKind.Local),
            timelinePrimaryKinds(available),
        )
        assertEquals(
            listOf(TimelineKind.Global, TimelineKind.Bubble, TimelineKind.Featured, TimelineKind.Mentions),
            timelineOverflowKinds(available),
        )
    }

    @Test
    fun timelineVisibleTabsIncludeSelectedOverflowKind() {
        val available = listOf(
            TimelineKind.Home,
            TimelineKind.Social,
            TimelineKind.Local,
            TimelineKind.Global,
            TimelineKind.Featured,
        )

        assertEquals(
            listOf(TimelineKind.Global, TimelineKind.Home, TimelineKind.Social, TimelineKind.Local),
            timelineVisibleKinds(
                availableKinds = available,
                selectedKind = TimelineKind.Global,
            ),
        )
    }

    @Test
    fun timelineSummaryKeepsRefreshInOverflow() {
        val actions = timelineSummaryActions(
            isRefreshing = false,
            onSearch = {},
            onRefresh = {},
        )

        assertEquals(listOf("搜索", "刷新"), actions.map { it.label })
        assertEquals(listOf(true, true), actions.map { it.enabled })
    }

    @Test
    fun timelineSummaryKeepsAiInSameOverflowWhenEnabled() {
        val actions = timelineSummaryActions(
            isRefreshing = true,
            onSearch = {},
            onRefresh = {},
            aiEnabled = true,
            aiActionEnabled = true,
            onAiDigest = {},
            onAiReplyOpportunities = {},
            onAiFilterSuggestions = {},
        )

        assertEquals(listOf("搜索", "刷新中", "AI"), actions.map { it.label })
        assertEquals(listOf(true, false, true), actions.map { it.enabled })
        assertEquals(listOf("时间线速览", "互动建议", "过滤建议"), actions.last().children.map { it.label })
    }
}
