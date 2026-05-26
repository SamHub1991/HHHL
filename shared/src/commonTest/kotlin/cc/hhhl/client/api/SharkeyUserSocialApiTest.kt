package cc.hhhl.client.api

import cc.hhhl.client.model.UserSocialKind
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

class SharkeyUserSocialApiTest {
    @Test
    fun loadsFollowingFromUsersFollowingEndpoint() = runTest {
        val api = SharkeyUserSocialApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/users/following", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-1""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"rel-old""""))

                respondFollowingArray()
            },
        )

        val result = api.loadUsers(
            token = "token-123",
            userId = "user-1",
            kind = UserSocialKind.Following,
            limit = 20,
            untilId = "rel-old",
        )

        assertIs<UserSocialLoadResult.Success>(result)
        val item = result.items.single()
        assertEquals("rel-1", item.id)
        assertEquals("target-1", item.user.id)
        assertEquals("Alice", item.user.displayName)
        assertEquals("alice", item.user.username)
        assertEquals("bio text", item.user.bio)
    }

    @Test
    fun loadsFollowersFromUsersFollowersEndpoint() = runTest {
        val api = SharkeyUserSocialApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/users/followers", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)

                respondFollowersArray()
            },
        )

        val result = api.loadUsers(
            token = "token-123",
            userId = "user-1",
            kind = UserSocialKind.Followers,
            limit = 20,
        )

        assertIs<UserSocialLoadResult.Success>(result)
        val item = result.items.single()
        assertEquals("rel-2", item.id)
        assertEquals("follower-1", item.user.id)
        assertEquals("Bob", item.user.displayName)
        assertEquals("bob", item.user.username)
    }

    @Test
    fun mapsUnauthorizedSocialResponse() = runTest {
        val api = SharkeyUserSocialApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<UserSocialLoadResult.Unauthorized>(
            api.loadUsers("expired", "user-1", UserSocialKind.Following, 20),
        )
    }

    @Test
    fun blankUserIdDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyUserSocialApi(
            client = testClient {
                error("network should not be called for blank user id")
            },
        )

        assertIs<UserSocialLoadResult.ServerError>(
            api.loadUsers("token-123", " ", UserSocialKind.Following, 20),
        )
    }

    private fun MockRequestHandleScope.respondFollowingArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "rel-1",
                    "createdAt": "2026-05-25T03:00:00.000Z",
                    "followee": {
                      "id": "target-1",
                      "username": "alice",
                      "name": "Alice",
                      "description": "bio text",
                      "followersCount": 22,
                      "followingCount": 11,
                      "notesCount": 33,
                      "isFollowing": true
                    }
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondFollowersArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "rel-2",
                    "createdAt": "2026-05-25T03:00:00.000Z",
                    "follower": {
                      "id": "follower-1",
                      "username": "bob",
                      "name": "Bob",
                      "description": "",
                      "followersCount": 5,
                      "followingCount": 6,
                      "notesCount": 7,
                      "isFollowing": false
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
