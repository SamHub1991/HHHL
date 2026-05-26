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
            listOf(TimelineKind.Home, TimelineKind.Social),
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
        )

        assertEquals(
            listOf(TimelineKind.Home, TimelineKind.Social, TimelineKind.Local),
            timelinePrimaryKinds(available),
        )
        assertEquals(
            listOf(TimelineKind.Global, TimelineKind.Bubble),
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
            onRefresh = {},
        )

        assertEquals(listOf("刷新"), actions.map { it.label })
        assertEquals(listOf(true), actions.map { it.enabled })
    }
}
