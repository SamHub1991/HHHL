package cc.hhhl.client.api

import cc.hhhl.client.model.FlashListKind
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

class SharkeyFlashApiTest {
    @Test
    fun loadFeaturedFlashesPostsToFeaturedEndpointWithOffset() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyFlashApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondFlashes()
            },
        )

        val result = api.loadFlashes(
            token = "token-123",
            kind = FlashListKind.Featured,
            limit = 20,
            offset = 40,
        )

        assertIs<FlashLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/flash/featured", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""offset":40"""))
        val flash = result.flashes.single()
        assertEquals("flash-1", flash.id)
        assertEquals("互动名片", flash.title)
        assertEquals("Alice", flash.author.displayName)
        assertEquals("public", flash.visibility)
        assertEquals(3, flash.likedCount)
        assertEquals(true, flash.isLiked)
        assertEquals("2026-05-25 14:00", flash.createdAtLabel)
    }

    @Test
    fun loadMineAndLikedUseExpectedEndpoints() = runTest {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val api = SharkeyFlashApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                bodies.add((request.body as TextContent).text)
                if (request.url.toString().endsWith("/flash/my-likes")) {
                    respondLikedFlashes()
                } else {
                    respondFlashes()
                }
            },
        )

        api.loadFlashes("token-123", FlashListKind.Mine, limit = 20, untilId = "flash-old")
        api.loadFlashes("token-123", FlashListKind.Liked, limit = 20)

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/flash/my",
                "https://dc.hhhl.cc/api/flash/my-likes",
            ),
            paths,
        )
        assertTrue(bodies.first().contains(""""untilId":"flash-old""""))
    }

    @Test
    fun showFlashPostsFlashIdToShowEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyFlashApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondFlash()
            },
        )

        val result = api.showFlash("token-123", flashId = "flash-1")

        assertIs<FlashShowResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/flash/show", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""flashId":"flash-1""""))
        assertEquals("flash-1", result.flash.id)
    }

    @Test
    fun likesAndUnlikesFlashUsingFlashId() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyFlashApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""flashId":"flash-1""""))
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<FlashActionResult.Success>(api.likeFlash("token-123", flashId = "flash-1"))
        assertIs<FlashActionResult.Success>(api.unlikeFlash("token-123", flashId = "flash-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/flash/like",
                "https://dc.hhhl.cc/api/flash/unlike",
            ),
            paths,
        )
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyFlashApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<FlashLoadResult.Unauthorized>(api.loadFlashes("expired", FlashListKind.Featured, 20))
        assertIs<FlashShowResult.Unauthorized>(api.showFlash("expired", flashId = "flash-1"))
    }

    @Test
    fun blankFlashIdActionDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyFlashApi(
            client = testClient {
                error("network should not be called for blank flash id")
            },
        )

        assertIs<FlashActionResult.ServerError>(api.likeFlash("token-123", " "))
    }

    private fun MockRequestHandleScope.respondFlashes(): HttpResponseData {
        return respond(
            content = "[${flashJson()}]",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondLikedFlashes(): HttpResponseData {
        return respond(
            content = """[{"id":"like-1","flash":${flashJson()}}]""",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondFlash(): HttpResponseData {
        return respond(
            content = flashJson(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun flashJson(): String {
        return """
            {
              "id": "flash-1",
              "createdAt": "2026-05-25T06:00:00.000Z",
              "updatedAt": "2026-05-25T07:00:00.000Z",
              "userId": "user-1",
              "user": {
                "id": "user-1",
                "username": "alice",
                "name": "Alice",
                "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
              },
              "title": "互动名片",
              "summary": "一个 Sharkey Play 示例",
              "script": "Ui:render([Ui:C:text({text: \"Hello HHHL\"})])",
              "visibility": "public",
              "likedCount": 3,
              "isLiked": true
            }
        """.trimIndent()
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
