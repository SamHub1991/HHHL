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

class SharkeyAchievementApiTest {
    @Test
    fun loadAchievementsPostsToUsersAchievementsEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAchievementApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondAchievements()
            },
        )

        val result = api.loadAchievements("token-123", userId = "user-1")

        assertIs<AchievementLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/users/achievements", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""userId":"user-1""""))
        val unlocked = result.achievements.first()
        assertEquals("notes1", unlocked.name)
        assertEquals("第一篇帖子", unlocked.title)
        assertEquals("2026-05-25 18:00", unlocked.unlockedAtLabel)
        assertTrue(result.achievements.any { it.name == "viewAchievements3min" && !it.isUnlocked })
    }

    @Test
    fun claimAchievementPostsToClaimEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAchievementApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond("", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<AchievementClaimResult.Success>(
            api.claimAchievement("token-123", "viewAchievements3min"),
        )

        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/i/claim-achievement", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""name":"viewAchievements3min""""))
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyAchievementApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<AchievementLoadResult.Unauthorized>(
            api.loadAchievements("expired", userId = "user-1"),
        )
    }

    @Test
    fun loadAchievementsAcceptsNumericUnlockedAtTimestamp() = runTest {
        val api = SharkeyAchievementApi(
            client = testClient {
                respond(
                    content = """
                        [
                          {
                            "name": "tutorialCompleted",
                            "unlockedAt": 1779462849660
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadAchievements("token-123", userId = "user-1")

        assertIs<AchievementLoadResult.Success>(result)
        val unlocked = result.achievements.first()
        assertEquals("tutorialCompleted", unlocked.name)
        assertEquals("2026-05-22 23:14", unlocked.unlockedAtLabel)
    }

    private fun MockRequestHandleScope.respondAchievements(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "name": "notes1",
                    "unlockedAt": "2026-05-25T10:00:00.000Z"
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
