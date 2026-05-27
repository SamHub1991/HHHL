package cc.hhhl.client.api

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
