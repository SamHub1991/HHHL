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

class SharkeyNoteRepliesApiTest {
    @Test
    fun loadsRepliesFromNotesRepliesEndpoint() = runTest {
        val api = SharkeyNoteRepliesApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/replies", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"reply-old""""))

                respondReplyArray()
            },
        )

        val result = api.loadReplies(
            token = "token-123",
            noteId = "note-1",
            limit = 20,
            untilId = "reply-old",
        )

        assertIs<NoteRepliesLoadResult.Success>(result)
        val reply = result.replies.single()
        assertEquals("reply-1", reply.id)
        assertEquals("Bob", reply.author.displayName)
        assertEquals("reply text", reply.text)
    }

    @Test
    fun loadsChildrenFromNotesChildrenEndpoint() = runTest {
        val api = SharkeyNoteRepliesApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/children", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"reply-1""""))
                assertTrue(body.contains(""""limit":12"""))
                assertTrue(body.contains(""""untilId":"child-old""""))

                respondReplyArray()
            },
        )

        val result = api.loadChildren(
            token = "token-123",
            noteId = "reply-1",
            limit = 12,
            untilId = "child-old",
        )

        assertIs<NoteRepliesLoadResult.Success>(result)
        assertEquals("reply-1", result.replies.single().id)
    }


    @Test
    fun mapsUnauthorizedRepliesResponse() = runTest {
        val api = SharkeyNoteRepliesApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<NoteRepliesLoadResult.Unauthorized>(
            api.loadReplies("expired", "note-1", 20),
        )
    }

    @Test
    fun blankNoteIdDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyNoteRepliesApi(
            client = testClient {
                error("network should not be called for blank note id")
            },
        )

        assertIs<NoteRepliesLoadResult.ServerError>(
            api.loadReplies("token-123", " ", 20),
        )
    }

    private fun MockRequestHandleScope.respondReplyArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "reply-1",
                    "createdAt": "2026-05-25T02:30:00.000Z",
                    "text": "reply text",
                    "visibility": "public",
                    "renoteCount": 0,
                    "repliesCount": 0,
                    "reactions": {},
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
