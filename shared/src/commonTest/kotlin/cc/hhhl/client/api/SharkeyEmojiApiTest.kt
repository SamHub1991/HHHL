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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SharkeyEmojiApiTest {
    @Test
    fun loadEmojisPostsToEmojisEndpointAndMapsResponse() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyEmojiApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondEmojis()
            },
        )

        val result = api.loadEmojis()

        assertIs<EmojiLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/emojis", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(false, request.body is TextContent)
        val emoji = result.emojis.single()
        assertEquals("blobcat", emoji.name)
        assertEquals(":blobcat:", emoji.reactionCode)
        assertEquals("cat", emoji.category)
        assertEquals("https://dc.hhhl.cc/emoji/blobcat.webp", emoji.url)
        assertEquals(listOf("blob", "cat"), emoji.aliases)
        assertEquals(false, emoji.isSensitive)
        assertEquals(true, emoji.localOnly)
    }

    @Test
    fun mapsServerErrorToServerErrorResult() = runTest {
        val api = SharkeyEmojiApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"temporarily unavailable"}}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadEmojis()

        assertIs<EmojiLoadResult.ServerError>(result)
        assertEquals(503, result.statusCode)
        assertEquals("temporarily unavailable", result.message)
    }

    @Test
    fun loadEmojiPostsNameToEmojiEndpointAndMapsResponse() = runTest {
        val api = SharkeyEmojiApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/emoji", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                assertEquals("{\"name\":\"blobcat\"}", (request.body as TextContent).text)
                respondSingleEmoji()
            },
        )

        val result = api.loadEmoji(":blobcat:")

        assertIs<EmojiDetailLoadResult.Success>(result)
        assertEquals("blobcat", result.emoji.name)
        assertEquals("https://dc.hhhl.cc/emoji/blobcat.webp", result.emoji.url)
    }

    @Test
    fun loadEmojiMapsNotFound() = runTest {
        val api = SharkeyEmojiApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"No such emoji."}}""",
                    status = HttpStatusCode.NotFound,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<EmojiDetailLoadResult.NotFound>(api.loadEmoji("missing"))
    }

    private fun MockRequestHandleScope.respondEmojis(): HttpResponseData {
        return respond(
            content = """
                {
                  "emojis": [
                    {
                      "aliases": ["blob", "cat"],
                      "name": "blobcat",
                      "category": "cat",
                      "url": "https://dc.hhhl.cc/emoji/blobcat.webp",
                      "localOnly": true,
                      "isSensitive": false,
                      "roleIdsThatCanBeUsedThisEmojiAsReaction": []
                    }
                  ]
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondSingleEmoji(): HttpResponseData {
        return respond(
            content = """
                {
                  "aliases": ["blob", "cat"],
                  "name": "blobcat",
                  "category": "cat",
                  "url": "https://dc.hhhl.cc/emoji/blobcat.webp",
                  "localOnly": true,
                  "isSensitive": false
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
