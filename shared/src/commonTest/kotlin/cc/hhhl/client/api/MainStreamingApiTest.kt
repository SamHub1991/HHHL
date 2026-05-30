package cc.hhhl.client.api

import cc.hhhl.client.model.NotificationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainStreamingApiTest {
    @Test
    fun parsesMainUnreadNotificationEvent() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "main",
                "type": "unreadNotification",
                "body": {}
              }
            }
            """.trimIndent(),
        )

        assertEquals(MainStreamingEvent.UnreadNotification, event)
    }

    @Test
    fun parsesMainNotificationEventAsUnreadNotification() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "main",
                "type": "notification",
                "body": {
                  "id": "notification-1"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(MainStreamingEvent.UnreadNotification, event)
    }

    @Test
    fun parsesMainNotificationBody() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "main",
                "type": "notification",
                "body": {
                  "id": "notification-1",
                  "createdAt": "2026-05-27T10:00:00.000Z",
                  "type": "quote",
                  "isRead": false,
                  "user": {
                    "id": "user-1",
                    "username": "alice",
                    "name": "Alice"
                  },
                  "note": {
                    "id": "note-1",
                    "text": "quoted text"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val notificationEvent = event as MainStreamingEvent.NotificationReceived
        assertEquals("notification-1", notificationEvent.notification.id)
        assertEquals(NotificationType.Quote, notificationEvent.notification.type)
        assertEquals("Alice", notificationEvent.notification.actor.displayName)
        assertEquals("quoted text", notificationEvent.notification.notePreviewText)
    }

    @Test
    fun parsesMainNewChatMessageEvent() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "main",
                "type": "newChatMessage",
                "body": {}
              }
            }
            """.trimIndent(),
        )

        assertEquals(MainStreamingEvent.NewChatMessage, event)
    }

    @Test
    fun parsesMainChatMessageAliasEvent() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "main",
                "type": "chatMessage",
                "body": {}
              }
            }
            """.trimIndent(),
        )

        assertEquals(MainStreamingEvent.NewChatMessage, event)
    }

    @Test
    fun parsesTimelineNoteEvent() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "timeline-local",
                "type": "note",
                "body": {
                  "id": "note-1"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(MainStreamingEvent.TimelineNote(TimelineKind.Local), event)
    }

    @Test
    fun parsesTimelineNoteBody() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "timeline-home",
                "type": "note",
                "body": {
                  "id": "note-1",
                  "createdAt": "2026-05-27T10:00:00.000Z",
                  "text": "hello",
                  "channelId": "channel-1",
                  "channel": { "id": "channel-1", "name": "总部频道" },
                  "user": {
                    "id": "user-1",
                    "username": "alice",
                    "name": "Alice",
                    "avatarUrl": "https://example.com/avatar.png"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val timelineEvent = event as MainStreamingEvent.TimelineNote
        assertEquals(TimelineKind.Home, timelineEvent.kind)
        assertEquals("note-1", timelineEvent.note?.id)
        assertEquals("user-1", timelineEvent.note?.author?.id)
        assertEquals("hello", timelineEvent.note?.text)
        assertEquals("channel-1", timelineEvent.note?.channelId)
        assertEquals("总部频道", timelineEvent.note?.channelName)
    }

    @Test
    fun ignoresNonNoteTimelineEvents() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "timeline-global",
                "type": "reaction",
                "body": {}
              }
            }
            """.trimIndent(),
        )

        assertNull(event)
    }

    @Test
    fun ignoresOtherChannelEvents() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-room-room-1",
                "type": "message",
                "body": {}
              }
            }
            """.trimIndent(),
        )

        assertNull(event)
    }
}
