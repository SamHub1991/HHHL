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

class SharkeyFavoriteNoteApiTest {
    @Test
    fun loadFavoritesPostsToIFavoritesEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyFavoriteNoteApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondFavorites()
            },
        )

        val result = api.loadFavorites("token-123", limit = 20, untilId = "fav-old")

        assertIs<FavoriteNoteLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/i/favorites", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"fav-old""""))
        val favorite = result.favorites.single()
        assertEquals("fav-1", favorite.id)
        assertEquals("2026-05-25 18:00", favorite.createdAtLabel)
        assertEquals("note-1", favorite.note.id)
        assertEquals("收藏的帖子", favorite.note.text)
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyFavoriteNoteApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<FavoriteNoteLoadResult.Unauthorized>(api.loadFavorites("expired", 20))
    }

    private fun MockRequestHandleScope.respondFavorites(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "fav-1",
                    "createdAt": "2026-05-25T10:00:00.000Z",
                    "note": {
                      "id": "note-1",
                      "createdAt": "2026-05-25T09:30:00.000Z",
                      "text": "收藏的帖子",
                      "visibility": "home",
                      "renoteCount": 1,
                      "repliesCount": 2,
                      "reactions": {"👍": 3},
                      "files": [],
                      "user": {
                        "id": "user-1",
                        "username": "alice",
                        "name": "Alice",
                        "avatarUrl": null
                      }
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
