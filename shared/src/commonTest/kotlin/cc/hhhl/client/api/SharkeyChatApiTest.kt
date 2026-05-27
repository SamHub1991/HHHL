package cc.hhhl.client.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SharkeyChatApiTest {
    @Test
    fun loadsJoiningRoomsFromChatRoomsJoiningEndpoint() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                when (request.url.toString()) {
                    "https://dc.hhhl.cc/api/chat/rooms/joining" -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(ContentType.Application.Json, request.body.contentType)
                        val body = (request.body as TextContent).text
                        assertTrue(body.contains(""""i":"token-123""""))
                        assertTrue(body.contains(""""limit":30"""))
                        assertTrue(body.contains(""""untilId":"membership-old""""))
                        respondJoiningRooms()
                    }
                    "https://dc.hhhl.cc/api/chat/history" -> respondUnreadChatHistory()
                    else -> error("Unexpected request ${request.url}")
                }
            },
        )

        val result = api.loadJoiningRooms(
            token = "token-123",
            limit = 30,
            untilId = "membership-old",
        )

        assertIs<ChatRoomLoadResult.Success>(result)
        val room = result.rooms.single()
        assertEquals("membership-1", room.membershipId)
        assertEquals("room-1", room.id)
        assertEquals("AGI 讨论", room.name)
        assertEquals("open", room.joinMode)
        assertEquals(12, room.memberCount)
        assertEquals("Alice", room.owner.displayName)
        assertEquals(5, room.unreadCount)
        assertEquals("2026-05-25 10:24", room.latestMessageAtLabel)
    }

    @Test
    fun fallsBackToChatHistoryUnreadStateWhenJoiningRoomsHaveNoCount() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                when (request.url.toString()) {
                    "https://dc.hhhl.cc/api/chat/rooms/joining" -> respondJoiningRoomsWithoutUnreadCount()
                    "https://dc.hhhl.cc/api/chat/history" -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(ContentType.Application.Json, request.body.contentType)
                        val body = (request.body as TextContent).text
                        assertTrue(body.contains(""""i":"token-123""""))
                        assertTrue(body.contains(""""limit":100"""))
                        assertTrue(body.contains(""""room":true"""))
                        respondUnreadChatHistory()
                    }
                    else -> error("Unexpected request ${request.url}")
                }
            },
        )

        val result = api.loadJoiningRooms(token = "token-123", limit = 30)

        assertIs<ChatRoomLoadResult.Success>(result)
        assertEquals(3, result.rooms.single().unreadCount)
    }

    @Test
    fun mapsUnauthorizedJoiningRoomsResponse() = runTest {
        val api = SharkeyChatApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<ChatRoomLoadResult.Unauthorized>(
            api.loadJoiningRooms("expired", 30),
        )
    }

    @Test
    fun loadsRoomMessagesFromRoomTimelineEndpoint() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/chat/messages/room-timeline", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""roomId":"room-1""""))
                assertTrue(body.contains(""""limit":40"""))
                assertTrue(body.contains(""""untilId":"message-old""""))
                respondRoomMessages()
            },
        )

        val result = api.loadRoomMessages(
            token = "token-123",
            roomId = "room-1",
            limit = 40,
            untilId = "message-old",
        )

        assertIs<ChatMessageLoadResult.Success>(result)
        val message = result.messages.single()
        assertEquals("message-1", message.id)
        assertEquals("room-1", message.roomId)
        assertEquals("Alice", message.fromUser.displayName)
        assertEquals("你好，HHHL", message.text)
        assertEquals("2026-05-25 09:23", message.createdAtLabel)
        assertEquals(1, message.reactionCount)
        assertEquals("Bob", message.reactions.single().users.single().displayName)
        assertEquals("reply-1", message.reply?.id)
        assertEquals("Carol: 上一条", message.reply?.let { reference ->
            val author = reference.fromUser?.displayName ?: ""
            "$author: ${reference.text}"
        })
        assertEquals("quote-1", message.quote?.id)
        assertEquals("file-1", message.file?.id)
        assertEquals("image.png", message.file?.name)
    }

    @Test
    fun createsRoomMessageFromCreateToRoomEndpoint() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/chat/messages/create-to-room", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""toRoomId":"room-1""""))
                assertTrue(body.contains(""""text":"发一条消息""""))
                respondCreatedRoomMessage()
            },
        )

        val result = api.createRoomMessage(
            token = "token-123",
            roomId = "room-1",
            text = "发一条消息",
        )

        assertIs<ChatMessageCreateResult.Success>(result)
        assertEquals("message-created", result.message.id)
        assertEquals("发一条消息", result.message.text)
    }

    @Test
    fun createsRoomMessageWithFileId() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/chat/messages/create-to-room", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""toRoomId":"room-1""""))
                assertTrue(body.contains(""""text":"配图""""))
                assertTrue(body.contains(""""fileId":"file-1""""))
                respondCreatedRoomMessage()
            },
        )

        val result = api.createRoomMessage(
            token = "token-123",
            roomId = "room-1",
            text = "配图",
            fileId = "file-1",
        )

        assertIs<ChatMessageCreateResult.Success>(result)
    }

    @Test
    fun createsRoomMessageWithFileIds() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/chat/messages/create-to-room", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""toRoomId":"room-1""""))
                assertTrue(body.contains(""""text":"多图""""))
                assertTrue(body.contains(""""fileId":"file-1""""))
                assertTrue(body.contains(""""fileIds":["file-1","file-2"]"""))
                respondCreatedRoomMessage()
            },
        )

        val result = api.createRoomMessage(
            token = "token-123",
            roomId = "room-1",
            text = "多图",
            fileIds = listOf("file-1", " file-2 ", "file-1"),
        )

        assertIs<ChatMessageCreateResult.Success>(result)
    }

    @Test
    fun createsRoomMessageWithReplyAndQuoteIds() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/chat/messages/create-to-room", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""toRoomId":"room-1""""))
                assertTrue(body.contains(""""text":"接一下""""))
                assertTrue(body.contains(""""replyId":"reply-1""""))
                assertTrue(body.contains(""""quoteId":"quote-1""""))
                respondCreatedRoomMessage()
            },
        )

        val result = api.createRoomMessage(
            token = "token-123",
            roomId = "room-1",
            text = "接一下",
            replyId = " reply-1 ",
            quoteId = " quote-1 ",
        )

        assertIs<ChatMessageCreateResult.Success>(result)
    }

    @Test
    fun reactsToChatMessageFromReactEndpoint() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/chat/messages/react", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""messageId":"message-1""""))
                assertTrue(body.contains(""""reaction":"❤️""""))
                respond("", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.reactToMessage(
            token = "token-123",
            messageId = "message-1",
            reaction = "❤️",
        )

        assertIs<ChatMessageReactionResult.Success>(result)
    }

    @Test
    fun unreactsFromChatMessageFromUnreactEndpoint() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/chat/messages/unreact", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""messageId":"message-1""""))
                assertTrue(body.contains(""""reaction":"❤️""""))
                respond("", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.unreactToMessage(
            token = "token-123",
            messageId = "message-1",
            reaction = "❤️",
        )

        assertIs<ChatMessageReactionResult.Success>(result)
    }

    @Test
    fun loadsRoomMembersFromMembersEndpoint() = runTest {
        val api = SharkeyChatApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/chat/rooms/members", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""roomId":"room-1""""))
                assertTrue(body.contains(""""limit":30"""))
                assertTrue(body.contains(""""untilId":"membership-old""""))
                respondRoomMembers()
            },
        )

        val result = api.loadRoomMembers(
            token = "token-123",
            roomId = "room-1",
            limit = 30,
            untilId = "membership-old",
        )

        assertIs<ChatRoomMemberLoadResult.Success>(result)
        val member = result.members.single()
        assertEquals("membership-member-1", member.membershipId)
        assertEquals("room-1", member.roomId)
        assertEquals("Alice", member.user.displayName)
        assertEquals("alice", member.user.username)
        assertEquals("2026-05-25 10:00", member.joinedAtLabel)
    }

    private fun MockRequestHandleScope.respondJoiningRooms(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "membership-1",
                    "createdAt": "2026-05-25T00:00:00.000Z",
                    "userId": "user-me",
                    "roomId": "room-1",
                    "unreadCount": 2,
                    "room": {
                      "id": "room-1",
                      "createdAt": "2026-05-25T00:00:00.000Z",
                      "ownerId": "user-1",
                      "owner": {
                        "id": "user-1",
                        "username": "alice",
                        "name": "Alice"
                      },
                      "name": "AGI 讨论",
                      "description": "聊 AGI 和 Sharkey",
                      "joinMode": "open",
                      "memberLimit": 100,
                      "memberCount": 12,
                      "isJoined": true,
                      "isMuted": false,
                      "unreadMessagesCount": 5,
                      "lastMessage": {
                        "id": "message-latest",
                        "createdAt": "2026-05-25T02:24:00.000Z"
                      }
                    }
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondJoiningRoomsWithoutUnreadCount(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "membership-1",
                    "createdAt": "2026-05-25T00:00:00.000Z",
                    "userId": "user-me",
                    "roomId": "room-1",
                    "room": {
                      "id": "room-1",
                      "owner": {
                        "id": "user-1",
                        "username": "alice",
                        "name": "Alice"
                      },
                      "name": "AGI 讨论",
                      "description": "聊 AGI 和 Sharkey",
                      "joinMode": "open",
                      "memberCount": 12,
                      "isMuted": false
                    }
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondUnreadChatHistory(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "message-1",
                    "toRoomId": "room-1",
                    "isRead": false
                  },
                  {
                    "id": "message-2",
                    "toRoomId": "room-1",
                    "isRead": false
                  },
                  {
                    "id": "message-3",
                    "toRoomId": "room-1",
                    "isRead": false
                  },
                  {
                    "id": "message-read",
                    "toRoomId": "room-1",
                    "isRead": true
                  },
                  {
                    "id": "message-other",
                    "toRoomId": "room-2",
                    "isRead": false
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondRoomMessages(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "message-1",
                    "createdAt": "2026-05-25T01:23:45.000Z",
                    "fromUserId": "user-1",
                    "fromUser": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
                    },
                    "toRoomId": "room-1",
                    "text": "你好，HHHL",
                    "replyId": "reply-1",
                    "reply": {
                      "id": "reply-1",
                      "fromUser": {
                        "id": "user-3",
                        "username": "carol",
                        "name": "Carol"
                      },
                      "text": "上一条",
                      "file": null
                    },
                    "quoteId": "quote-1",
                    "quote": {
                      "id": "quote-1",
                      "fromUser": {
                        "id": "user-4",
                        "username": "dave",
                        "name": "Dave"
                      },
                      "text": "引用内容",
                      "file": null
                    },
                    "fileId": "file-1",
                    "file": {
                      "id": "file-1",
                      "createdAt": "2026-05-25T01:23:00.000Z",
                      "name": "image.png",
                      "type": "image/png",
                      "md5": "abc",
                      "size": 1024,
                      "isSensitive": false,
                      "blurhash": null,
                      "properties": {},
                      "url": "https://dc.hhhl.cc/files/image.png",
                      "thumbnailUrl": null,
                      "comment": null,
                      "folderId": null,
                      "userId": "user-1"
                    },
                    "reactions": [
                      {
                        "reaction": "❤️",
                        "user": {
                          "id": "user-2",
                          "username": "bob",
                          "name": "Bob"
                        }
                      }
                    ]
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondCreatedRoomMessage(): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "message-created",
                  "createdAt": "2026-05-25T01:24:00.000Z",
                  "fromUserId": "user-me",
                  "fromUser": {
                    "id": "user-me",
                    "username": "me",
                    "name": "Me"
                  },
                  "toRoomId": "room-1",
                  "text": "发一条消息",
                  "fileId": null,
                  "file": null,
                  "reactions": []
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondRoomMembers(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "membership-member-1",
                    "createdAt": "2026-05-25T02:00:00.000Z",
                    "userId": "user-1",
                    "roomId": "room-1",
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
                    },
                    "room": null
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
