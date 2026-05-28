package cc.hhhl.client.cache

import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationCacheCodecTest {
    @Test
    fun encodeAndDecodeTrimNotificationSnapshots() {
        val snapshot = NotificationCacheSnapshot(
            notifications = (0 until 260).map { index -> notification("n$index") },
            chatAttentionNotifications = (0 until 260).map { index ->
                notification("c$index", type = NotificationType.Reply)
            },
            specialCareNotifications = (0 until 260).map { index -> notification("s$index", isSpecialCare = true) },
        )

        val decoded = NotificationCacheCodec.decode(NotificationCacheCodec.encode(snapshot))

        assertEquals(240, decoded.notifications.size)
        assertEquals("n0", decoded.notifications.first().id)
        assertEquals("n239", decoded.notifications.last().id)
        assertEquals(240, decoded.chatAttentionNotifications.size)
        assertEquals("c0", decoded.chatAttentionNotifications.first().id)
        assertEquals("c239", decoded.chatAttentionNotifications.last().id)
        assertEquals(240, decoded.specialCareNotifications.size)
        assertEquals("s0", decoded.specialCareNotifications.first().id)
        assertEquals("s239", decoded.specialCareNotifications.last().id)
    }

    private fun notification(
        id: String,
        type: NotificationType = NotificationType.Mention,
        isSpecialCare: Boolean = false,
    ): NotificationItem {
        return NotificationItem(
            id = id,
            type = type,
            actor = User(
                id = "user-$id",
                displayName = "User $id",
                username = "user$id",
                avatarInitial = "U",
            ),
            text = "hello $id",
            createdAtLabel = "刚刚",
            createdAtEpochMillis = id.hashCode().toLong(),
            isSpecialCare = isSpecialCare,
        )
    }
}
