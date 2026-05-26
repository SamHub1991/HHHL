package cc.hhhl.client.api

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

class SharkeyDiscoverApiTest {
    @Test
    fun searchesNotesFromNotesSearchEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/search", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""query":"Sharkey""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"note-old""""))

                respondNoteArray()
            },
        )

        val result = api.searchNotes(
            token = "token-123",
            query = "Sharkey",
            limit = 20,
            untilId = "note-old",
        )

        assertIs<DiscoverSearchResult.Success>(result)
        assertEquals("note-1", result.notes.single().id)
        assertEquals("search result", result.notes.single().text)
    }

    @Test
    fun mapsUnauthorizedSearchResponse() = runTest {
        val api = SharkeyDiscoverApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<DiscoverSearchResult.Unauthorized>(
            api.searchNotes("expired", "Sharkey", 20),
        )
    }

    @Test
    fun mapsUnavailableSearchErrorMessage() = runTest {
        val api = SharkeyDiscoverApi(
            client = testClient {
                respond(
                    content = """
                        {
                          "error": {
                            "message": "Search of notes unavailable.",
                            "code": "UNAVAILABLE"
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.searchNotes("token-123", "Sharkey", 20)

        assertIs<DiscoverSearchResult.ServerError>(result)
        assertEquals("Search of notes unavailable.", result.message)
    }

    @Test
    fun searchesUsersFromUsersSearchEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/users/search", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""query":"Alice""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""origin":"remote""""))

                respondUserArray()
            },
        )

        val result = api.searchUsers(
            token = "token-123",
            query = "Alice",
            limit = 20,
            origin = "remote",
        )

        assertIs<DiscoverUserSearchResult.Success>(result)
        val user = result.users.single()
        assertEquals("user-1", user.id)
        assertEquals("Alice", user.displayName)
        assertEquals("alice", user.username)
        assertEquals("remote.example", user.host)
        assertEquals("bio text", user.bio)
    }

    @Test
    fun loadsTrendingHashtagsFromHashtagsTrendEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/hashtags/trend", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                respondTrendArray()
            },
        )

        val result = api.loadTrendingHashtags()

        assertIs<DiscoverTrendResult.Success>(result)
        val trend = result.trends.single()
        assertEquals("AI", trend.tag)
        assertEquals(12, trend.usersCount)
        assertEquals(listOf(1, 3, 8), trend.chart)
    }

    @Test
    fun loadsFederationInstancesFromFederationInstancesEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/federation/instances", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""limit":30"""))
                assertTrue(body.contains(""""offset":10"""))
                assertTrue(body.contains(""""sort":"-users""""))
                assertTrue(body.contains(""""host":"example.social""""))
                respondFederationArray()
            },
        )

        val result = api.loadFederationInstances(limit = 30, offset = 10, host = "example.social")

        assertIs<DiscoverFederationResult.Success>(result)
        val instance = result.instances.single()
        assertEquals("instance-1", instance.id)
        assertEquals("example.social", instance.host)
        assertEquals("Example", instance.name)
        assertEquals("sharkey", instance.softwareName)
        assertEquals(120, instance.usersCount)
        assertEquals(false, instance.isBlocked)
    }

    private fun MockRequestHandleScope.respondNoteArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "note-1",
                    "createdAt": "2026-05-25T03:00:00.000Z",
                    "text": "search result",
                    "visibility": "public",
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
                    }
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondUserArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "user-1",
                    "username": "alice",
                    "host": "remote.example",
                    "name": "Alice",
                    "description": "bio text",
                    "followersCount": 22,
                    "followingCount": 11,
                    "notesCount": 33,
                    "isFollowing": false
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondTrendArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "tag": "AI",
                    "chart": [1, 3, 8],
                    "usersCount": 12
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondFederationArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "instance-1",
                    "firstRetrievedAt": "2026-05-25T00:00:00.000Z",
                    "host": "example.social",
                    "usersCount": 120,
                    "notesCount": 900,
                    "followingCount": 10,
                    "followersCount": 11,
                    "isNotResponding": false,
                    "isSuspended": false,
                    "suspensionState": "none",
                    "isBlocked": false,
                    "softwareName": "sharkey",
                    "softwareVersion": "2025.5.2",
                    "openRegistrations": true,
                    "name": "Example",
                    "description": "federated instance",
                    "maintainerName": null,
                    "maintainerEmail": null,
                    "isSilenced": false,
                    "isMediaSilenced": false,
                    "iconUrl": null,
                    "faviconUrl": null,
                    "themeColor": null,
                    "infoUpdatedAt": null,
                    "latestRequestReceivedAt": null,
                    "rejectReports": false,
                    "rejectQuotes": false,
                    "isBubbled": false,
                    "mandatoryCW": null
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
