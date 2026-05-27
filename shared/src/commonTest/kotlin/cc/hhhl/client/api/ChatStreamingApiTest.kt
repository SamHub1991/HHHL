package cc.hhhl.client.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ChatStreamingApiTest {
    @Test
    fun parsesChatRoomMessageEvent() {
        val event = parseSharkeyStreamingChatEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-room-room-1",
                "type": "message",
                "body": {
                  "id": "message-1",
                  "createdAt": "2026-05-25T01:23:45.000Z",
                  "toRoomId": "room-1",
                  "text": "实时消息",
                  "fromUser": {
                    "id": "user-1",
                    "username": "alice",
                    "name": "Alice"
                  },
                  "reactions": [
                    {
                      "reaction": "❤️",
                      "user": {
                        "id": "user-2",
                        "username": "bob"
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        val messageEvent = assertIs<ChatStreamingEvent.MessageReceived>(event)
        assertEquals("message-1", messageEvent.message.id)
        assertEquals("room-1", messageEvent.message.roomId)
        assertEquals("实时消息", messageEvent.message.text)
        assertEquals("Alice", messageEvent.message.fromUser.displayName)
        assertEquals(1, messageEvent.message.reactionCount)
    }

    @Test
    fun ignoresNonChatMessageEvents() {
        val event = parseSharkeyStreamingChatEvent(
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

        assertNull(event)
    }

    @Test
    fun transportUnauthorizedDetectionOnlyAcceptsExplicitAuthFailures() {
        assertEquals(true, "HTTP 401 Unauthorized".isUnauthorizedStreamingTransportError())
        assertEquals(true, "Invalid token".isUnauthorizedStreamingTransportError())
        assertEquals(false, "HTTP 403 Forbidden".isUnauthorizedStreamingTransportError())
        assertEquals(false, "authentication handshake failed".isUnauthorizedStreamingTransportError())
        assertEquals(false, "connection reset by peer".isUnauthorizedStreamingTransportError())
    }
}
