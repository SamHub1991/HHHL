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
