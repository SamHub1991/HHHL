package cc.hhhl.client.api

import cc.hhhl.client.model.NotificationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
    fun parsesMainChatMessageBodyWhenProvided() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "main",
                "type": "chatMessage",
                "body": {
                  "id": "chat-message-1",
                  "createdAt": "2026-05-25T01:23:45.000Z",
                  "toRoomId": "room-1",
                  "text": "@me 主流里的聊天室消息",
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

        val messageEvent = assertIs<MainStreamingEvent.ChatMessageReceived>(event)
        assertEquals("chat-message-1", messageEvent.message.id)
        assertEquals("room-1", messageEvent.message.roomId)
        assertEquals("@me 主流里的聊天室消息", messageEvent.message.text)
        assertEquals("Alice", messageEvent.message.fromUser.displayName)
    }

    @Test
    fun parsesMainWrappedChatMessageBodyWhenProvided() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "main",
                "type": "newChatMessage",
                "body": {
                  "chatMessage": {
                    "id": "main-chat-message-wrapped",
                    "createdAt": "2026-05-25T01:23:45.000Z",
                    "roomId": "room-1",
                    "text": "@me 主流包裹消息",
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

        val messageEvent = assertIs<MainStreamingEvent.ChatMessageReceived>(event)
        assertEquals("main-chat-message-wrapped", messageEvent.message.id)
        assertEquals("room-1", messageEvent.message.roomId)
        assertEquals("@me 主流包裹消息", messageEvent.message.text)
    }

    @Test
    fun parsesMainUnreadChatMessagesBatchBodyWhenProvided() {
        val events = parseSharkeyMainStreamingEvents(
            """
            {
              "type": "channel",
              "body": {
                "id": "main",
                "type": "unreadChatMessages",
                "body": [
                  {
                    "id": "main-chat-message-1",
                    "createdAt": "2026-05-25T01:23:45.000Z",
                    "toRoomId": "room-1",
                    "text": "@me 主流批量第一条",
                    "fromUser": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
                    }
                  },
                  {
                    "id": "main-chat-message-2",
                    "createdAt": "2026-05-25T01:24:45.000Z",
                    "toRoomId": "room-1",
                    "text": "主流批量第二条",
                    "fromUser": {
                      "id": "user-2",
                      "username": "bob",
                      "name": "Bob"
                    },
                    "reply": {
                      "id": "reply-message",
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
        val first = assertIs<MainStreamingEvent.ChatMessageReceived>(events[0])
        val second = assertIs<MainStreamingEvent.ChatMessageReceived>(events[1])
        assertEquals("main-chat-message-1", first.message.id)
        assertEquals("@me 主流批量第一条", first.message.text)
        assertEquals("main-chat-message-2", second.message.id)
        assertEquals("reply-message", second.message.reply?.id)
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
    fun parsesChannelTimelineNoteBody() {
        val event = parseSharkeyMainStreamingEvent(
            """
            {
              "type": "channel",
              "body": {
                "id": "channel:channel-1",
                "type": "note",
                "body": {
                  "id": "note-1",
                  "createdAt": "2026-05-27T10:00:00.000Z",
                  "text": "channel post",
                  "user": {
                    "id": "user-1",
                    "username": "alice",
                    "name": "Alice"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val timelineEvent = event as MainStreamingEvent.TimelineNote
        assertEquals(TimelineKind.Home, timelineEvent.kind)
        assertEquals("Channel", timelineEvent.timelineSource)
        assertEquals("note-1", timelineEvent.note?.id)
        assertEquals("channel-1", timelineEvent.note?.channelId)
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
