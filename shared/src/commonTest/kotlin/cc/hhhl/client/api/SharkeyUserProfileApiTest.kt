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

class SharkeyUserProfileApiTest {
    @Test
    fun loadsUserProfileFromUsersShowEndpoint() = runTest {
        val api = SharkeyUserProfileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/users/show", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-1""""))

                respondProfile()
            },
        )

        val result = api.loadProfile("token-123", "user-1")

        assertIs<UserProfileLoadResult.Success>(result)
        assertEquals("user-1", result.user.id)
        assertEquals("Alice", result.user.displayName)
        assertEquals("alice", result.user.username)
        assertEquals("A", result.user.avatarInitial)
        assertEquals("bio text", result.user.bio)
        assertEquals(11, result.user.followingCount)
        assertEquals(22, result.user.followersCount)
        assertEquals(33, result.user.notesCount)
        assertEquals(listOf("pin-1"), result.user.pinnedNotes.map { it.id })
        assertEquals("Pinned profile note", result.user.pinnedNotes.first().text)
        assertEquals("https://dc.hhhl.cc/banner.webp", result.user.bannerUrl)
    }

    @Test
    fun loadsUserProfileByUsernameFromUsersShowEndpoint() = runTest {
        val api = SharkeyUserProfileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/users/show", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""username":"alice""""))
                assertTrue(!body.contains(""""userId""""))

                respondProfile()
            },
        )

        val result = api.loadProfileByUsername("token-123", " @alice ")

        assertIs<UserProfileLoadResult.Success>(result)
        assertEquals("user-1", result.user.id)
    }

    @Test
    fun loadsRemoteUserProfileByUsernameAndHost() = runTest {
        val api = SharkeyUserProfileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""username":"alice""""))
                assertTrue(body.contains(""""host":"remote.example""""))

                respondProfile()
            },
        )

        val result = api.loadProfileByUsername("token-123", "alice@remote.example")

        assertIs<UserProfileLoadResult.Success>(result)
    }

    @Test
    fun updatesProfileFromIUpdateEndpoint() = runTest {
        val api = SharkeyUserProfileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/update", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""name":"Alice New""""))
                assertTrue(body.contains(""""description":"new bio""""))
                assertTrue(body.contains(""""avatarId":"avatar-file""""))
                assertTrue(body.contains(""""bannerId":"banner-file""""))

                respondProfile(
                    name = "Alice New",
                    description = "new bio",
                )
            },
        )

        val result = api.updateProfile(
            token = " token-123 ",
            draft = UserProfileUpdateDraft(
                name = " Alice New ",
                description = " new bio ",
                avatarId = " avatar-file ",
                bannerId = " banner-file ",
            ),
        )

        assertIs<UserProfileUpdateResult.Success>(result)
        assertEquals("Alice New", result.user.displayName)
        assertEquals("new bio", result.user.bio)
    }

    @Test
    fun mapsUnauthorizedUserProfileResponse() = runTest {
        val api = SharkeyUserProfileApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<UserProfileLoadResult.Unauthorized>(api.loadProfile("expired", "user-1"))
    }

    @Test
    fun blankUserInputsDoNotMapToUnauthorized() = runTest {
        val api = SharkeyUserProfileApi(
            client = testClient {
                error("network should not be called for blank user input")
            },
        )

        assertIs<UserProfileLoadResult.ServerError>(api.loadProfile("token-123", " "))
        assertIs<UserProfileLoadResult.ServerError>(api.loadProfileByUsername("token-123", " @ "))
    }

    private fun MockRequestHandleScope.respondProfile(
        name: String = "Alice",
        description: String = "bio text",
    ): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "user-1",
                  "username": "alice",
                  "name": "$name",
                  "description": "$description",
                  "followersCount": 22,
                  "followingCount": 11,
                  "notesCount": 33,
                  "avatarUrl": "https://dc.hhhl.cc/avatar.webp",
                  "bannerUrl": "https://dc.hhhl.cc/banner.webp",
                  "pinnedNotes": [
                    {
                      "id": "pin-1",
                      "createdAt": "2026-05-25T10:00:00.000Z",
                      "text": "Pinned profile note",
                      "visibility": "public",
                      "user": {
                        "id": "user-1",
                        "username": "alice",
                        "name": "Alice"
                      },
                      "repliesCount": 1,
                      "renoteCount": 2,
                      "reactions": {
                        "👍": 3
                      },
                      "files": []
                    }
                  ]
                }
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
