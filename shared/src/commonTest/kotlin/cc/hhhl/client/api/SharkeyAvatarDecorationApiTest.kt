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

class SharkeyAvatarDecorationApiTest {
    @Test
    fun loadsAvatarDecorationsFromGetAvatarDecorationsEndpoint() = runTest {
        val api = SharkeyAvatarDecorationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/get-avatar-decorations", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                assertEquals("""{"i":"token-123"}""", (request.body as TextContent).text)
                respondDecorations()
            },
        )

        val result = api.load(" token-123 ")

        assertIs<AvatarDecorationLoadResult.Success>(result)
        val decoration = result.decorations.single()
        assertEquals("decoration-1", decoration.id)
        assertEquals("https://dc.hhhl.cc/decorations/1.png", decoration.url)
        assertEquals(12f, decoration.angle)
        assertEquals(true, decoration.flipH)
        assertEquals(0.1f, decoration.offsetX)
        assertEquals(-0.2f, decoration.offsetY)
    }

    @Test
    fun mapsUnauthorizedAvatarDecorationResponse() = runTest {
        val api = SharkeyAvatarDecorationApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<AvatarDecorationLoadResult.Unauthorized>(api.load("expired"))
    }

    private fun MockRequestHandleScope.respondDecorations(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "decoration-1",
                    "url": "https://dc.hhhl.cc/decorations/1.png",
                    "angle": 12,
                    "flipH": true,
                    "offsetX": 0.1,
                    "offsetY": -0.2
                  },
                  {
                    "id": "broken",
                    "url": ""
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
