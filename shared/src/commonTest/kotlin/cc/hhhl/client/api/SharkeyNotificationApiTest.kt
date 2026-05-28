package cc.hhhl.client.api

import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.NotificationFilter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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

class SharkeyNotificationApiTest {
    @Test
    fun loadsNotificationsFromINotificationsEndpoint() = runTest {
        val api = SharkeyNotificationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/notifications", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"old-notification""""))
                respondNotificationArray()
            },
        )

        val result = api.loadNotifications(
            token = "token-123",
            limit = 20,
            untilId = "old-notification",
        )

        assertIs<NotificationLoadResult.Success>(result)
        assertEquals(2, result.notifications.size)
    }

    @Test
    fun loadNotificationsCanIncludeFilteredTypes() = runTest {
        val api = SharkeyNotificationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""includeTypes":["mention","reply","quote"]"""))
                respondNotificationArray()
            },
        )

        val result = api.loadNotifications(
            token = "token-123",
            limit = 20,
            includeTypes = NotificationFilter.Mentions.includedTypes,
        )

        assertIs<NotificationLoadResult.Success>(result)
    }

    @Test
    fun mapsNotificationJsonToDomainItems() = runTest {
        val api = SharkeyNotificationApi(client = testClient { respondNotificationArray() })

        val result = api.loadNotifications("token-123", 20)

        assertIs<NotificationLoadResult.Success>(result)
        val reaction = result.notifications[0]
        assertEquals("notification-1", reaction.id)
        assertEquals(NotificationType.Reaction, reaction.type)
        assertEquals("Alice", reaction.actor.displayName)
        assertEquals("alice", reaction.actor.username)
        assertEquals("对你的帖子做出了反应 👍", reaction.text)
        assertEquals("2026-05-25 08:12", reaction.createdAtLabel)
        assertEquals("note-1", reaction.noteId)
        assertEquals("这是一条 CW", reaction.notePreviewText)

        val follow = result.notifications[1]
        assertEquals(NotificationType.Follow, follow.type)
        assertEquals("关注了你", follow.text)
        assertEquals(null, follow.noteId)
    }

    @Test
    fun mapsReadStateFromNotificationJson() = runTest {
        val api = SharkeyNotificationApi(
            client = testClient {
                respond(
                    content = """
                        [
                          {
                            "id": "notification-read",
                            "createdAt": "2026-05-25T00:12:34.000Z",
                            "type": "follow",
                            "isRead": true,
                            "user": {
                              "id": "user-1",
                              "username": "alice",
                              "name": "Alice"
                            }
                          },
                          {
                            "id": "notification-legacy-read",
                            "createdAt": "2026-05-25T00:13:34.000Z",
                            "type": "follow",
                            "read": true,
                            "user": {
                              "id": "user-2",
                              "username": "bob",
                              "name": "Bob"
                            }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadNotifications("token-123", 20)

        assertIs<NotificationLoadResult.Success>(result)
        assertTrue(result.notifications[0].isRead)
        assertTrue(result.notifications[1].isRead)
    }

    @Test
    fun mapsAdditionalSharkeyNotificationTypes() = runTest {
        val api = SharkeyNotificationApi(client = testClient { respondRichNotificationArray() })

        val result = api.loadNotifications("token-123", 20)

        assertIs<NotificationLoadResult.Success>(result)
        val notifications = result.notifications

        assertEquals(NotificationType.Quote, notifications[0].type)
        assertEquals("引用了你的帖子", notifications[0].text)
        assertEquals("note-quote", notifications[0].noteId)

        assertEquals(NotificationType.FollowRequestAccepted, notifications[1].type)
        assertEquals("接受了你的关注请求：欢迎", notifications[1].text)

        assertEquals(NotificationType.App, notifications[2].type)
        assertEquals("公告：维护完成", notifications[2].text)

        assertEquals(NotificationType.ReactionGrouped, notifications[3].type)
        assertEquals("Alice 等 2 人对你的帖子做出了反应 👍 ❤️", notifications[3].text)
        assertEquals("note-grouped", notifications[3].noteId)

        assertEquals(NotificationType.ScheduledNoteFailed, notifications[4].type)
        assertEquals("定时帖子发布失败：quota exceeded", notifications[4].text)
    }

    @Test
    fun mapsUnauthorizedNotificationResponse() = runTest {
        val api = SharkeyNotificationApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<NotificationLoadResult.Unauthorized>(api.loadNotifications("expired", 20))
    }

    @Test
    fun mapsServerErrorMessageFromErrorEnvelope() = runTest {
        val api = SharkeyNotificationApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Notifications unavailable"}}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadNotifications("token-123", 20)

        assertIs<NotificationLoadResult.ServerError>(result)
        assertEquals("Notifications unavailable", result.message)
    }

    @Test
    fun marksAllNotificationsAsRead() = runTest {
        val api = SharkeyNotificationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notifications/mark-all-as-read", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                )
            },
        )

        assertIs<NotificationActionResult.Success>(api.markAllAsRead("token-123"))
    }

    @Test
    fun flushesNotificationsWithoutJsonPayload() = runTest {
        val api = SharkeyNotificationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notifications/flush", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<NotificationActionResult.Success>(api.flush("token-123"))
    }

    @Test
    fun createsNotificationWithBodyAndOptionalHeader() = runTest {
        val api = SharkeyNotificationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notifications/create", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as TextContent).text
                assertTrue(body.contains("\"i\":\"token-123\""))
                assertTrue(body.contains("\"body\":\"回来看看新消息\""))
                assertTrue(body.contains("\"header\":\"HHHL 提醒\""))
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<NotificationActionResult.Success>(
            api.createNotification(
                token = "token-123",
                body = "回来看看新消息",
                header = "HHHL 提醒",
            ),
        )
    }

    @Test
    fun sendsTestNotificationWithoutExtraPayload() = runTest {
        val api = SharkeyNotificationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notifications/test-notification", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<NotificationActionResult.Success>(api.sendTestNotification("token-123"))
    }

    private fun MockRequestHandleScope.respondRichNotificationArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "notification-quote",
                    "createdAt": "2026-05-25T00:30:00.000Z",
                    "type": "quote",
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
                    },
                    "note": {
                      "id": "note-quote"
                    }
                  },
                  {
                    "id": "notification-follow-accepted",
                    "createdAt": "2026-05-25T00:31:00.000Z",
                    "type": "followRequestAccepted",
                    "message": "欢迎",
                    "user": {
                      "id": "user-2",
                      "username": "bob",
                      "name": "Bob"
                    }
                  },
                  {
                    "id": "notification-app",
                    "createdAt": "2026-05-25T00:32:00.000Z",
                    "type": "app",
                    "header": "公告",
                    "body": "维护完成",
                    "icon": null
                  },
                  {
                    "id": "notification-reaction-grouped",
                    "createdAt": "2026-05-25T00:33:00.000Z",
                    "type": "reaction:grouped",
                    "note": {
                      "id": "note-grouped"
                    },
                    "reactions": [
                      {
                        "reaction": "👍",
                        "user": {
                          "id": "user-1",
                          "username": "alice",
                          "name": "Alice"
                        }
                      },
                      {
                        "reaction": "❤️",
                        "user": {
                          "id": "user-2",
                          "username": "bob",
                          "name": "Bob"
                        }
                      }
                    ]
                  },
                  {
                    "id": "notification-scheduled-failed",
                    "createdAt": "2026-05-25T00:34:00.000Z",
                    "type": "scheduledNoteFailed",
                    "reason": "quota exceeded"
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondNotificationArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "notification-1",
                    "createdAt": "2026-05-25T00:12:34.000Z",
                    "type": "reaction",
                    "reaction": "👍",
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice",
                      "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                    },
                    "note": {
                      "id": "note-1",
                      "text": "这是一条被互动的动态内容",
                      "cw": "这是一条 CW"
                    }
                  },
                  {
                    "id": "notification-2",
                    "createdAt": "2026-05-25T00:10:00.000Z",
                    "type": "follow",
                    "user": {
                      "id": "user-2",
                      "username": "bob",
                      "name": "Bob"
                    }
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
