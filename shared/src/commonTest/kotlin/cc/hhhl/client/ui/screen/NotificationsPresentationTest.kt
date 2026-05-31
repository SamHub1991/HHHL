package cc.hhhl.client.ui.screen

import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.User
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
                NotificationFilter.Replies,
                NotificationFilter.Quotes,
                NotificationFilter.Achievements,
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
            notificationCount = 2,
            onSendTestNotification = {},
            onSendReminderNotification = {},
        )

        assertEquals(listOf("全部已读", "清空", "AI", "测试通知", "提醒自己"), actions.map { it.label })
        assertEquals(listOf(true, true, false, true, true), actions.map { it.enabled })
        assertEquals(listOf("总结通知", "待处理", "优先级"), actions[2].children.map { it.label })
        assertEquals(true, actions[1].destructive)
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

    @Test
    fun replyNotificationOffersReplyAndReactionQuickActions() {
        val notification = sampleNotification(
            type = NotificationType.Reply,
            noteId = "note-1",
        )

        assertEquals(listOf("回复", "回应"), notificationQuickActionLabels(notification))
    }

    @Test
    fun followNotificationOffersFollowBackQuickAction() {
        val notification = sampleNotification(type = NotificationType.Follow)

        assertEquals(listOf("关注回去"), notificationQuickActionLabels(notification))
    }

    private fun sampleNotification(
        type: NotificationType,
        noteId: String? = null,
    ): NotificationItem {
        return NotificationItem(
            id = "notification-1",
            type = type,
            actor = User(
                id = "user-1",
                displayName = "Alice",
                username = "alice",
                avatarInitial = "A",
            ),
            text = "notification",
            createdAtLabel = "刚刚",
            noteId = noteId,
        )
    }
}
