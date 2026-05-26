package cc.hhhl.client.api

import cc.hhhl.client.model.NoteVisibility
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

class SharkeyComposeApiTest {
    @Test
    fun createNotePostsTextVisibilityAndCwToNotesCreate() = runTest {
        val api = SharkeyComposeApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/create", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""text":"hello""""))
                assertTrue(body.contains(""""visibility":"home""""))
                assertTrue(body.contains(""""cw":"spoiler""""))
                assertTrue(body.contains(""""replyId":"note-parent""""))
                assertTrue(body.contains(""""renoteId":"note-quote""""))
                assertTrue(body.contains(""""fileIds":["file-1","file-2"]"""))
                assertTrue(body.contains(""""channelId":"channel-1""""))
                assertTrue(
                    body.contains(
                        """"poll":{"choices":["A","B"],"multiple":true,"expiresAt":"2026-05-26T00:00:00.000Z"}""",
                    ),
                )

                respond(
                    content = """{"createdNote":{"id":"note-created"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.createNote(
            token = "token-123",
            draft = ComposeDraft(
                text = "hello",
                visibility = NoteVisibility.Home,
                cw = "spoiler",
                replyId = "note-parent",
                renoteId = "note-quote",
                channelId = "channel-1",
                fileIds = listOf("file-1", "file-2"),
                poll = ComposePollDraft(
                    choices = listOf("A", "B"),
                    multiple = true,
                    expiresAt = "2026-05-26T00:00:00.000Z",
                ),
            ),
        )

        assertEquals(ComposeCreateResult.Success("note-created"), result)
    }

    @Test
    fun createSpecifiedNotePostsVisibleUserIds() = runTest {
        val api = SharkeyComposeApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""visibility":"specified""""))
                assertTrue(body.contains(""""visibleUserIds":["user-1","user-2"]"""))

                respond(
                    content = """{"createdNote":{"id":"note-created"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.createNote(
            token = "token-123",
            draft = ComposeDraft(
                text = "secret",
                visibility = NoteVisibility.Specified,
                visibleUserIds = listOf(" user-1 ", "user-2", "user-1", ""),
            ),
        )

        assertEquals(ComposeCreateResult.Success("note-created"), result)
    }

    @Test
    fun createNoteMapsUnauthorizedResponse() = runTest {
        val api = SharkeyComposeApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<ComposeCreateResult.Unauthorized>(
            api.createNote("expired", ComposeDraft(text = "hello")),
        )
    }

    @Test
    fun createNoteUsesServerProvidedErrorMessage() = runTest {
        val api = SharkeyComposeApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Too many poll choices"}}""",
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.createNote("token-123", ComposeDraft(text = "hello"))

        assertEquals(ComposeCreateResult.ServerError(400, "Too many poll choices"), result)
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
