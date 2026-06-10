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
        assertEquals("room-1", messageEvent.source.roomId)
        assertEquals("实时消息", messageEvent.message.text)
        assertEquals("Alice", messageEvent.message.fromUser.displayName)
        assertEquals(1, messageEvent.message.reactionCount)
    }

    @Test
    fun parsesChatRoomMessageAliasEvent() {
        val event = parseSharkeyStreamingChatEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-room-room-1",
                "type": "chatMessage",
                "body": {
                  "id": "message-alias",
                  "createdAt": "2026-05-25T01:23:45.000Z",
                  "toRoomId": "room-1",
                  "text": "@me 别名事件",
                  "fromUser": {
                    "id": "user-1",
                    "username": "alice",
                    "name": "Alice"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val messageEvent = assertIs<ChatStreamingEvent.MessageReceived>(event)
        assertEquals("message-alias", messageEvent.message.id)
        assertEquals("room-1", messageEvent.message.roomId)
        assertEquals("room-1", messageEvent.source.roomId)
        assertEquals("@me 别名事件", messageEvent.message.text)
    }

    @Test
    fun parsesUnreadChatMessagesBatchEvent() {
        val events = parseSharkeyStreamingChatEvents(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-room-room-1",
                "type": "unreadChatMessages",
                "body": [
                  {
                    "id": "message-batch-1",
                    "createdAt": "2026-05-25T01:23:45.000Z",
                    "toRoomId": "room-1",
                    "text": "@me 第一条",
                    "fromUser": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
                    }
                  },
                  {
                    "id": "message-batch-2",
                    "createdAt": "2026-05-25T01:24:45.000Z",
                    "toRoomId": "room-1",
                    "text": "第二条带引用",
                    "fromUser": {
                      "id": "user-2",
                      "username": "bob",
                      "name": "Bob"
                    },
                    "quote": {
                      "id": "quoted-message",
                      "fromUser": {
                        "id": "me",
                        "username": "me",
                        "name": "Me"
                      },
                      "text": "原消息"
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(2, events.size)
        val first = assertIs<ChatStreamingEvent.MessageReceived>(events[0])
        val second = assertIs<ChatStreamingEvent.MessageReceived>(events[1])
        assertEquals("message-batch-1", first.message.id)
        assertEquals("message-batch-2", second.message.id)
        assertEquals("quoted-message", second.message.quote?.id)
        assertEquals("room-1", second.source.roomId)
    }

    @Test
    fun parsesChatMessageDeletedEvent() {
        val event = parseSharkeyStreamingChatEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-room-room-1",
                "type": "chatMessageDeleted",
                "body": {
                  "messageId": "message-deleted",
                  "roomId": "room-1"
                }
              }
            }
            """.trimIndent(),
        )

        val deletedEvent = assertIs<ChatStreamingEvent.MessageDeleted>(event)
        assertEquals("message-deleted", deletedEvent.messageId)
        assertEquals("room-1", deletedEvent.source.roomId)
    }

    @Test
    fun parsesWrappedChatMessageDeletedEvent() {
        val event = parseSharkeyStreamingChatEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-user-user-2",
                "type": "deleted",
                "body": {
                  "chatMessage": {
                    "id": "message-deleted",
                    "toUserId": "user-2"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val deletedEvent = assertIs<ChatStreamingEvent.MessageDeleted>(event)
        assertEquals("message-deleted", deletedEvent.messageId)
        assertEquals("user-2", deletedEvent.source.userId)
    }

    @Test
    fun parsesWrappedChatMessageBodyAndRoomIdAlias() {
        val event = parseSharkeyStreamingChatEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-room-room-1",
                "type": "message",
                "body": {
                  "message": {
                    "id": "message-wrapped",
                    "createdAt": "2026-05-25T01:23:45.000Z",
                    "roomId": "room-1",
                    "text": "包裹后的实时消息",
                    "fromUser": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val messageEvent = assertIs<ChatStreamingEvent.MessageReceived>(event)
        assertEquals("message-wrapped", messageEvent.message.id)
        assertEquals("room-1", messageEvent.message.roomId)
        assertEquals("包裹后的实时消息", messageEvent.message.text)
    }

    @Test
    fun parsesDirectMessageSourceFromChannelIdWhenPayloadDoesNotIncludePeer() {
        val event = parseSharkeyStreamingChatEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-user-user-2",
                "type": "message",
                "body": {
                  "id": "message-2",
                  "createdAt": "2026-05-25T02:23:45.000Z",
                  "text": "私聊实时消息",
                  "fromUser": {
                    "id": "user-2",
                    "username": "bob",
                    "name": "Bob"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val messageEvent = assertIs<ChatStreamingEvent.MessageReceived>(event)
        assertEquals("user-2", messageEvent.source.userId)
        assertEquals("", messageEvent.message.roomId)
        assertEquals("user-2", messageEvent.message.fromUser.id)
        assertEquals(null, messageEvent.message.toUserId)
    }

    @Test
    fun fillsOutgoingDirectMessagePeerFromChannelIdWhenPayloadOmitsToUserId() {
        val event = parseSharkeyStreamingChatEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "chat-user-user-2",
                "type": "message",
                "body": {
                  "id": "message-3",
                  "createdAt": "2026-05-25T02:24:45.000Z",
                  "text": "我发出的私聊实时消息",
                  "fromUser": {
                    "id": "me",
                    "username": "me",
                    "name": "Me"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val messageEvent = assertIs<ChatStreamingEvent.MessageReceived>(event)
        assertEquals("user-2", messageEvent.source.userId)
        assertEquals("user-2", messageEvent.message.toUserId)
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
