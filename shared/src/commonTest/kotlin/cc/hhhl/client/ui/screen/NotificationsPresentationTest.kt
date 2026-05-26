package cc.hhhl.client.ui.screen

import cc.hhhl.client.model.NotificationFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationsPresentationTest {
    @Test
    fun primaryFiltersKeepCommonNotificationViewsVisible() {
        assertEquals(
            listOf(
                NotificationFilter.All,
                NotificationFilter.Mentions,
                NotificationFilter.Reactions,
            ),
            notificationPrimaryFilters(),
        )
    }

    @Test
    fun overflowFiltersKeepSecondaryNotificationViewsReachable() {
        assertEquals(
            listOf(
                NotificationFilter.SpecialCare,
                NotificationFilter.Follows,
                NotificationFilter.System,
            ),
            notificationOverflowFilters(),
        )
    }

    @Test
    fun visibleFiltersExposeEveryNotificationViewInline() {
        assertEquals(NotificationFilter.entries, notificationVisiblePrimaryFilters())
    }

    @Test
    fun notificationFiltersExposeSpecialCareView() {
        assertEquals("特别关心", NotificationFilter.SpecialCare.label)
        assertEquals(true, NotificationFilter.SpecialCare in notificationVisiblePrimaryFilters())
    }

    @Test
    fun notificationSummaryActionKeepsMarkAllReadAvailable() {
        val actions = notificationSummaryActions(
            notificationCount = 3,
            isMarkingAllRead = false,
            onMarkAllAsRead = {},
        )

        assertEquals(listOf("全部已读"), actions.map { it.label })
        assertEquals(listOf(true), actions.map { it.enabled })
    }
}
