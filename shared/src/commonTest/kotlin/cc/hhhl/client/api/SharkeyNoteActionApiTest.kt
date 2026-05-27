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

class SharkeyNoteActionApiTest {
    @Test
    fun createsReactionFromReactionsCreateEndpoint() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient { request ->
                assertEndpoint(request, "/api/notes/reactions/create")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                assertTrue(body.contains(""""reaction":"👍""""))
                respondOk()
            },
        )

        assertIs<NoteActionApiResult.Success>(
            api.createReaction("token-123", "note-1", "👍"),
        )
    }

    @Test
    fun deletesReactionFromReactionsDeleteEndpoint() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient { request ->
                assertEndpoint(request, "/api/notes/reactions/delete")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                respondOk()
            },
        )

        assertIs<NoteActionApiResult.Success>(
            api.deleteReaction("token-123", "note-1"),
        )
    }

    @Test
    fun createsFavoriteFromFavoritesCreateEndpoint() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient { request ->
                assertEndpoint(request, "/api/notes/favorites/create")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                respondOk()
            },
        )

        assertIs<NoteActionApiResult.Success>(
            api.createFavorite("token-123", "note-1"),
        )
    }

    @Test
    fun deletesFavoriteFromFavoritesDeleteEndpoint() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient { request ->
                assertEndpoint(request, "/api/notes/favorites/delete")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                respondOk()
            },
        )

        assertIs<NoteActionApiResult.Success>(
            api.deleteFavorite("token-123", "note-1"),
        )
    }

    @Test
    fun votesPollFromPollVoteEndpoint() = runTest {
        val api = SharkeyNoteActionApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient { request ->
                assertEndpoint(request, "/api/notes/polls/vote")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                assertTrue(body.contains(""""choice":1"""))
                respondOk()
            },
        )

        assertIs<NoteActionApiResult.Success>(
            api.votePoll("token-123", "note-1", choice = 1),
        )
    }

    @Test
    fun createsRenoteFromNotesCreateEndpoint() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient { request ->
                assertEndpoint(request, "/api/notes/create")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""renoteId":"note-1""""))
                respondOk()
            },
        )

        assertIs<NoteActionApiResult.Success>(
            api.createRenote("token-123", "note-1"),
        )
    }

    @Test
    fun deletesNoteFromNotesDeleteEndpoint() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient { request ->
                assertEndpoint(request, "/api/notes/delete")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                respondOk()
            },
        )

        assertIs<NoteActionApiResult.Success>(
            api.deleteNote("token-123", "note-1"),
        )
    }

    @Test
    fun mutesNoteFromThreadMutingCreateEndpoint() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient { request ->
                assertEndpoint(request, "/api/notes/thread-muting/create")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                respondOk()
            },
        )

        assertIs<NoteActionApiResult.Success>(
            api.muteNote("token-123", "note-1"),
        )
    }

    @Test
    fun mapsUnauthorizedActionResponse() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<NoteActionApiResult.Unauthorized>(
            api.createReaction("expired", "note-1", "👍"),
        )
    }

    @Test
    fun mapsServerProvidedActionErrorMessage() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"You cannot react to this note"}}""",
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.createReaction("token-123", "note-1", "👍")

        assertEquals(
            NoteActionApiResult.ServerError(
                statusCode = 400,
                message = "You cannot react to this note",
            ),
            result,
        )
    }

    @Test
    fun mapsForbiddenActionResponseToServerErrorInsteadOfRelogin() = runTest {
        val api = SharkeyNoteActionApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"You cannot delete this note"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            },
        )

        assertEquals(
            NoteActionApiResult.ServerError(
                statusCode = 403,
                message = "You cannot delete this note",
            ),
            api.deleteNote("token-123", "note-1"),
        )
    }

    private fun assertEndpoint(
        request: HttpRequestData,
        encodedPath: String,
    ) {
        assertEquals("https://dc.hhhl.cc$encodedPath", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
    }

    private fun MockRequestHandleScope.respondOk(): HttpResponseData {
        return respond(
            content = "{}",
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
