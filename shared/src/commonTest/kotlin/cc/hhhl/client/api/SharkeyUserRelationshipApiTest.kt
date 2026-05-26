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

class SharkeyUserRelationshipApiTest {
    @Test
    fun loadsRelationFromUsersRelationEndpoint() = runTest {
        val api = SharkeyUserRelationshipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/users/relation", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-1""""))
                respondRelation()
            },
        )

        val result = api.loadRelation("token-123", "user-1")

        assertIs<UserRelationshipLoadResult.Success>(result)
        assertEquals(true, result.relationship.isFollowing)
        assertEquals(true, result.relationship.isMuted)
        assertEquals(false, result.relationship.isBlocking)
    }

    @Test
    fun followsUserFromFollowingCreateEndpoint() = runTest {
        val api = SharkeyUserRelationshipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/following/create", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-1""""))
                respondOk()
            },
        )

        assertIs<UserRelationshipResult.Success>(
            api.follow("token-123", "user-1"),
        )
    }

    @Test
    fun unfollowsUserFromFollowingDeleteEndpoint() = runTest {
        val api = SharkeyUserRelationshipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/following/delete", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-1""""))
                respondOk()
            },
        )

        assertIs<UserRelationshipResult.Success>(
            api.unfollow("token-123", "user-1"),
        )
    }

    @Test
    fun mutesAndUnmutesUserFromMuteEndpoints() = runTest {
        val calls = mutableListOf<String>()
        val api = SharkeyUserRelationshipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                calls.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-1""""))
                respondOk()
            },
        )

        assertIs<UserRelationshipResult.Success>(api.mute("token-123", "user-1"))
        assertIs<UserRelationshipResult.Success>(api.unmute("token-123", "user-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/mute/create",
                "https://dc.hhhl.cc/api/mute/delete",
            ),
            calls,
        )
    }

    @Test
    fun blocksAndUnblocksUserFromBlockingEndpoints() = runTest {
        val calls = mutableListOf<String>()
        val api = SharkeyUserRelationshipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                calls.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-1""""))
                respondOk()
            },
        )

        assertIs<UserRelationshipResult.Success>(api.block("token-123", "user-1"))
        assertIs<UserRelationshipResult.Success>(api.unblock("token-123", "user-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/blocking/create",
                "https://dc.hhhl.cc/api/blocking/delete",
            ),
            calls,
        )
    }

    @Test
    fun loadsMutedUsersFromMuteListEndpoint() = runTest {
        val api = SharkeyUserRelationshipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/mute/list", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"mute-old""""))
                respondRelationshipList("mute-1", "mutee")
            },
        )

        val result = api.loadMutedUsers("token-123", limit = 20, untilId = "mute-old")

        assertIs<UserRelationshipListResult.Success>(result)
        val entry = result.entries.single()
        assertEquals("mute-1", entry.id)
        assertEquals("user-1", entry.user.id)
        assertEquals("Alice", entry.user.displayName)
        assertEquals("2026-05-25 09:23", entry.createdAtLabel)
    }

    @Test
    fun loadsBlockedUsersFromBlockingListEndpoint() = runTest {
        val api = SharkeyUserRelationshipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/blocking/list", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""limit":30"""))
                respondRelationshipList("block-1", "blockee")
            },
        )

        val result = api.loadBlockedUsers("token-123")

        assertIs<UserRelationshipListResult.Success>(result)
        assertEquals("block-1", result.entries.single().id)
        assertEquals("alice", result.entries.single().user.username)
    }

    @Test
    fun mapsUnauthorizedRelationshipResponse() = runTest {
        val api = SharkeyUserRelationshipApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<UserRelationshipResult.Unauthorized>(
            api.follow("expired", "user-1"),
        )
    }

    @Test
    fun mapsForbiddenRelationshipActionToServerErrorInsteadOfRelogin() = runTest {
        val api = SharkeyUserRelationshipApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"blocked by policy"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            },
        )

        assertEquals(
            UserRelationshipResult.ServerError(
                statusCode = 403,
                message = "blocked by policy",
            ),
            api.follow("token-123", "user-1"),
        )
    }

    @Test
    fun mapsForbiddenRelationshipLoadToServerErrorInsteadOfRelogin() = runTest {
        val api = SharkeyUserRelationshipApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"blocked by policy"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            },
        )

        assertEquals(
            UserRelationshipLoadResult.ServerError(
                statusCode = 403,
                message = "blocked by policy",
            ),
            api.loadRelation("token-123", "user-1"),
        )
    }

    @Test
    fun blankUserIdDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyUserRelationshipApi(
            client = testClient {
                error("network should not be called for blank user id")
            },
        )

        assertIs<UserRelationshipLoadResult.ServerError>(api.loadRelation("token-123", " "))
        assertIs<UserRelationshipResult.ServerError>(api.follow("token-123", " "))
    }

    private fun MockRequestHandleScope.respondRelation(): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "user-1",
                  "isFollowing": true,
                  "isFollowed": false,
                  "hasPendingFollowRequestFromYou": false,
                  "hasPendingFollowRequestToYou": false,
                  "isMuted": true,
                  "isBlocking": false,
                  "isBlocked": false
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondOk(): HttpResponseData {
        return respond(
            content = "{}",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondRelationshipList(
        entryId: String,
        userField: String,
    ): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "$entryId",
                    "createdAt": "2026-05-25T01:23:45.000Z",
                    "$userField": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice",
                      "description": "hello",
                      "avatarUrl": "https://dc.hhhl.cc/avatar.webp",
                      "followersCount": 10,
                      "followingCount": 2,
                      "notesCount": 4
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
