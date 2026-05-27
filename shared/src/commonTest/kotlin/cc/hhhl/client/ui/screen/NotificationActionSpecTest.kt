package cc.hhhl.client.ui.screen

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationActionSpecTest {
    @Test
    fun followRequestReceivedNotificationCanBeAcceptedOrRejected() {
        val notification = notification(type = NotificationType.FollowRequestReceived)

        assertTrue(notification.canActOnFollowRequest)
    }

    @Test
    fun ordinaryFollowNotificationsDoNotShowFollowRequestActions() {
        assertFalse(notification(type = NotificationType.Follow).canActOnFollowRequest)
        assertFalse(notification(type = NotificationType.FollowRequestAccepted).canActOnFollowRequest)
        assertFalse(notification(type = NotificationType.Mention).canActOnFollowRequest)
    }

    @Test
    fun chatRoomInvitationNotificationCanOpenChat() {
        assertTrue(notification(type = NotificationType.ChatRoomInvitation).canOpenChat)
        assertTrue(notification(type = NotificationType.App, chatUserId = "user-1").canOpenChat)
        assertFalse(notification(type = NotificationType.Mention).canOpenChat)
    }

    @Test
    fun notificationOnlyShowsNotePreviewWhenTextExists() {
        assertTrue(
            notification(
                type = NotificationType.Reaction,
                notePreviewText = "被互动的动态",
            ).hasNotePreview,
        )
        assertFalse(notification(type = NotificationType.Reaction, notePreviewText = "").hasNotePreview)
        assertFalse(notification(type = NotificationType.Reaction).hasNotePreview)
    }

    @Test
    fun notificationNavigationPrefersRelatedNoteOverActorProfile() {
        val target = notification(
            type = NotificationType.Reaction,
            noteId = "note-1",
        ).navigationTarget

        assertIs<NotificationNavigationTarget.NoteDetail>(target)
        assertEquals("note-1", target.noteId)
    }

    @Test
    fun chatInvitationNavigationTargetsChat() {
        assertEquals(
            NotificationNavigationTarget.Chat,
            notification(type = NotificationType.ChatRoomInvitation).navigationTarget,
        )
    }

    @Test
    fun chatUserNotificationNavigationTargetsUserConversation() {
        val target = notification(
            type = NotificationType.App,
            chatUserId = "user-1",
            chatMessageId = "message-1",
        ).navigationTarget

        assertIs<NotificationNavigationTarget.ChatUser>(target)
        assertEquals("user-1", target.userId)
        assertEquals("message-1", target.messageId)
    }

    @Test
    fun systemNotificationsWithoutRelatedObjectsDoNotOpenSyntheticProfile() {
        assertNull(notification(type = NotificationType.App).navigationTarget)
        assertNull(notification(type = NotificationType.ExportCompleted).navigationTarget)
        assertNull(notification(type = NotificationType.Login).navigationTarget)
    }

    @Test
    fun actorNotificationsNavigateToActorProfile() {
        val target = notification(type = NotificationType.Follow).navigationTarget

        assertIs<NotificationNavigationTarget.UserProfile>(target)
        assertEquals(FakeData.me.id, target.userId)
    }

    private fun notification(
        type: NotificationType,
        notePreviewText: String? = null,
        noteId: String? = null,
        chatUserId: String? = null,
        chatMessageId: String? = null,
    ): NotificationItem {
        return NotificationItem(
            id = "notification-$type",
            type = type,
            actor = FakeData.me,
            text = "通知",
            createdAtLabel = "刚刚",
            noteId = noteId,
            notePreviewText = notePreviewText,
            chatUserId = chatUserId,
            chatMessageId = chatMessageId,
        )
    }
}
