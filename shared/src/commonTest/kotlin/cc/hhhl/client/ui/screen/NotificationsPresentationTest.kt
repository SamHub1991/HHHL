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
            isMarkingAllRead = false,
            onSendTestNotification = {},
            onSendReminderNotification = {},
        )

        assertEquals(listOf("测试通知", "提醒自己"), actions.map { it.label })
        assertEquals(listOf(true, true), actions.map { it.enabled })
    }

    @Test
    fun markAllReadActionDoesNotDependOnVisibleNotificationCount() {
        assertEquals(
            true,
            notificationMarkAllReadEnabled(isLoading = false, isMarkingAllRead = false),
        )
        assertEquals(
            false,
            notificationMarkAllReadEnabled(isLoading = true, isMarkingAllRead = false),
        )
        assertEquals(
            false,
            notificationMarkAllReadEnabled(isLoading = false, isMarkingAllRead = true),
        )
    }

    @Test
    fun notificationNotePreviewNormalizesLegacyRawRichText() {
        assertEquals(
            "CW docs 👍",
            "$[fg.color=ff0000 CW] [docs](https://dc.hhhl.cc) ${'$'}{unicode 1f44d}"
                .normalizeNotificationNotePreviewText(),
        )
    }
}
