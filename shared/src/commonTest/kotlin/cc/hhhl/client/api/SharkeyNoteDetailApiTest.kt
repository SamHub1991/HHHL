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
import cc.hhhl.client.model.NoteVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SharkeyNoteDetailApiTest {
    @Test
    fun loadsNoteDetailFromNotesShowEndpoint() = runTest {
        val api = SharkeyNoteDetailApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/show", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))

                respondNote()
            },
        )

        val result = api.loadNote(
            token = "token-123",
            noteId = "note-1",
        )

        assertIs<NoteDetailLoadResult.Success>(result)
        assertEquals("note-1", result.note.id)
        assertEquals("Alice", result.note.author.displayName)
        assertEquals("detail note", result.note.text)
        assertEquals(NoteVisibility.Specified, result.note.visibility)
        assertEquals(listOf("user-2"), result.note.visibleUserIds)
        assertEquals(true, result.note.localOnly)
        assertEquals("likeOnly", result.note.reactionAcceptance)
        assertEquals(5, result.note.reactionCount)
    }

    @Test
    fun mapsUnauthorizedNoteDetailResponse() = runTest {
        val api = SharkeyNoteDetailApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<NoteDetailLoadResult.Unauthorized>(api.loadNote("expired", "note-1"))
    }

    @Test
    fun blankNoteIdDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyNoteDetailApi(
            client = testClient {
                error("network should not be called for blank note id")
            },
        )

        assertIs<NoteDetailLoadResult.ServerError>(api.loadNote("token-123", " "))
    }

    @Test
    fun refreshesPollFromPollsRefreshEndpoint() = runTest {
        val api = SharkeyNoteDetailApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/polls/refresh", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            },
        )

        assertIs<NoteActionOnlyResult.Success>(
            api.refreshPollRecommendation("token-123", "note-1"),
        )
    }

    @Test
    fun loadsNoteStateFromNotesStateEndpoint() = runTest {
        val api = SharkeyNoteDetailApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/state", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                respond(
                    content = """
                        {
                          "isFavorited": true,
                          "isMutedThread": true,
                          "isMutedNote": true,
                          "isRenoted": true
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadState("token-123", "note-1")

        assertIs<NoteStateResult.Success>(result)
        assertEquals(true, result.state.isFavorited)
        assertEquals(true, result.state.isMutedThread)
        assertEquals(true, result.state.isMutedNote)
        assertEquals(true, result.state.isRenoted)
    }

    private fun MockRequestHandleScope.respondNote(): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "note-1",
                  "createdAt": "2026-05-25T02:10:00.000Z",
                  "text": "detail note",
                  "visibility": "specified",
                  "visibleUserIds": ["user-2"],
                  "localOnly": true,
                  "reactionAcceptance": "likeOnly",
                  "renoteCount": 4,
                  "repliesCount": 3,
                  "reactions": {
                    "👍": 5
                  },
                  "user": {
                    "id": "user-1",
                    "username": "alice",
                    "name": "Alice"
                  }
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
