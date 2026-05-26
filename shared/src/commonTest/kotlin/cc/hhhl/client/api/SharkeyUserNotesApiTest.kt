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

class SharkeyUserNotesApiTest {
    @Test
    fun loadsUserNotesFromUsersNotesEndpoint() = runTest {
        val api = SharkeyUserNotesApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/users/notes", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-1""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"note-old""""))

                respondNoteArray()
            },
        )

        val result = api.loadUserNotes(
            token = "token-123",
            userId = "user-1",
            limit = 20,
            untilId = "note-old",
        )

        assertIs<UserNotesLoadResult.Success>(result)
        val note = result.notes.single()
        assertEquals("note-1", note.id)
        assertEquals("Alice", note.author.displayName)
        assertEquals("alice", note.author.username)
        assertEquals("profile note", note.text)
        assertEquals("2026-05-25 09:22", note.createdAtLabel)
    }

    @Test
    fun mapsUnauthorizedUserNotesResponse() = runTest {
        val api = SharkeyUserNotesApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<UserNotesLoadResult.Unauthorized>(
            api.loadUserNotes("expired", "user-1", 20),
        )
    }

    @Test
    fun blankUserIdDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyUserNotesApi(
            client = testClient {
                error("network should not be called for blank user id")
            },
        )

        assertIs<UserNotesLoadResult.ServerError>(
            api.loadUserNotes("token-123", " ", 20),
        )
    }

    private fun MockRequestHandleScope.respondNoteArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "note-1",
                    "createdAt": "2026-05-25T01:22:33.000Z",
                    "text": "profile note",
                    "visibility": "public",
                    "renoteCount": 1,
                    "repliesCount": 2,
                    "reactions": {
                      "👍": 3
                    },
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
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
