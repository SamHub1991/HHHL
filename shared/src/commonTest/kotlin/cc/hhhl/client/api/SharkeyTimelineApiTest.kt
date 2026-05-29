package cc.hhhl.client.api

import cc.hhhl.client.model.NoteVisibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharkeyTimelineApiTest {
    @Test
    fun loadsHomeTimelineFromTimelineEndpoint() = runTest {
        val api = SharkeyTimelineApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/timeline", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"note-old""""))

                respondNoteArray()
            },
        )

        val result = api.loadTimeline(
            kind = TimelineKind.Home,
            token = "token-123",
            limit = 20,
            untilId = "note-old",
        )

        assertIs<TimelineLoadResult.Success>(result)
        assertEquals(1, result.notes.size)
    }

    @Test
    fun selectsLocalAndGlobalTimelineEndpoints() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyTimelineApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient { request ->
                paths.add(request.url.encodedPath)
                respondNoteArray()
            },
        )

        api.loadTimeline(TimelineKind.Local, "token-123", 20)
        api.loadTimeline(TimelineKind.Global, "token-123", 20)
        api.loadTimeline(TimelineKind.Social, "token-123", 20)
        api.loadTimeline(TimelineKind.Bubble, "token-123", 20)
        api.loadTimeline(TimelineKind.Featured, "token-123", 20)

        assertEquals(
            listOf(
                "/api/notes/local-timeline",
                "/api/notes/global-timeline",
                "/api/notes/hybrid-timeline",
                "/api/notes/bubble-timeline",
                "/api/notes/featured",
            ),
            paths,
        )
    }

    @Test
    fun loadsPollRecommendationsFromPollRecommendationEndpoint() = runTest {
        val api = SharkeyTimelineApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/polls/recommendation", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals("token-123", body.getValue("i").jsonPrimitive.content)
                assertEquals(20, body.getValue("limit").jsonPrimitive.int)
                assertEquals(40, body.getValue("offset").jsonPrimitive.int)
                assertEquals(true, body.getValue("expired").jsonPrimitive.boolean)
                respondNoteArray()
            },
        )

        val result = api.loadPollRecommendations(
            token = "token-123",
            limit = 20,
            offset = 40,
            expired = true,
        )

        assertIs<TimelineLoadResult.Success>(result)
        assertEquals(1, result.notes.size)
    }

    @Test
    fun loadsFollowingNotesFromFollowingEndpoint() = runTest {
        val api = SharkeyTimelineApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/following", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains("\"i\":\"token-123\""))
                assertTrue(body.contains("\"list\":\"mutuals\""))
                assertTrue(body.contains("\"limit\":20"))
                assertTrue(body.contains("\"untilId\":\"note-old\""))
                assertTrue(body.contains("\"filesOnly\":true"))
                assertTrue(body.contains("\"includeNonPublic\":true"))
                assertTrue(body.contains("\"includeReplies\":true"))
                assertTrue(body.contains("\"includeQuotes\":true"))
                assertTrue(body.contains("\"includeBots\":false"))
                respondNoteArray()
            },
        )

        val result = api.loadFollowingNotes(
            token = "token-123",
            limit = 20,
            untilId = "note-old",
            list = FollowingNotesListKind.Mutuals,
            filesOnly = true,
            includeNonPublic = true,
            includeReplies = true,
            includeQuotes = true,
            includeBots = false,
        )

        assertIs<TimelineLoadResult.Success>(result)
        assertEquals(1, result.notes.size)
    }

    @Test
    fun mapsTimelineNoteJsonToDomainModel() = runTest {
        val api = SharkeyTimelineApi(
            client = testClient { respondNoteArray() },
        )

        val result = api.loadTimeline(TimelineKind.Home, "token-123", 20)

        assertIs<TimelineLoadResult.Success>(result)
        val note = result.notes.single()
        assertEquals("note-1", note.id)
        assertEquals("Alice", note.author.displayName)
        assertEquals("alice", note.author.username)
        assertEquals("A", note.author.avatarInitial)
        assertEquals("https://dc.hhhl.cc/avatar.webp", note.author.avatarUrl)
        assertEquals("hello from Sharkey", note.text)
        assertEquals("2026-05-25 08:12", note.createdAtLabel)
        assertEquals(NoteVisibility.Home, note.visibility)
        assertEquals("content warning", note.cw)
        assertEquals(2, note.replyCount)
        assertEquals(3, note.renoteCount)
        assertEquals(7, note.reactionCount)
        assertEquals(listOf("👍" to 4, ":blobcat@.:" to 3), note.reactions.map { it.reaction to it.count })
        assertEquals("👍", note.myReaction)
        assertEquals(true, note.isFavorited)
        assertEquals(listOf("截图里的替代说明"), note.media.map { it.description })
        assertEquals(listOf("image/png"), note.media.map { it.type })
        assertEquals(listOf("https://dc.hhhl.cc/files/image.png"), note.media.map { it.url })
        assertEquals(listOf("https://dc.hhhl.cc/thumb.webp"), note.media.map { it.thumbnailUrl })
        assertEquals(listOf(false), note.media.map { it.isSensitive })
        assertEquals(false, note.poll?.multiple)
        assertEquals("2026-05-26 08:00", note.poll?.expiresAtLabel)
        assertEquals(listOf("A" to 4, "B" to 2), note.poll?.choices?.map { it.text to it.votes })
        assertEquals(listOf(false, true), note.poll?.choices?.map { it.isVoted })
    }

    @Test
    fun mapsUnauthorizedTimelineResponseToUnauthorizedResult() = runTest {
        val api = SharkeyTimelineApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<TimelineLoadResult.Unauthorized>(api.loadTimeline(TimelineKind.Home, "expired", 20))
    }

    @Test
    fun mapsForbiddenTimelineResponseToServerErrorInsteadOfRelogin() = runTest {
        val api = SharkeyTimelineApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"policy denied"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadTimeline(TimelineKind.Global, "token-123", 20)

        assertIs<TimelineLoadResult.ServerError>(result)
        assertEquals(403, result.statusCode)
    }

    @Test
    fun mapsQuoteRenoteToQuotedNoteCard() = runTest {
        val api = SharkeyTimelineApi(
            client = testClient {
                respond(
                    content = """
                        [
                          {
                            "id": "quote-1",
                            "createdAt": "2026-05-25T00:12:34.000Z",
                            "text": "quoted comment",
                            "user": {
                              "id": "user-quote",
                              "username": "bob",
                              "name": "Bob"
                            },
                            "renote": {
                              "id": "quoted-note",
                              "createdAt": "2026-05-24T00:12:34.000Z",
                              "text": "original text",
                              "user": {
                                "id": "user-original",
                                "username": "alice",
                                "name": "Alice"
                              }
                            }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadTimeline(TimelineKind.Home, "token-123", 20)

        assertIs<TimelineLoadResult.Success>(result)
        val note = result.notes.single()
        assertEquals("quote-1", note.id)
        assertEquals("quoted comment", note.text)
        assertEquals("Bob", note.author.displayName)
        assertEquals("quoted-note", note.quotedNote?.id)
        assertEquals("original text", note.quotedNote?.text)
        assertEquals("Alice", note.quotedNote?.author?.displayName)
    }

    private fun MockRequestHandleScope.respondNoteArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "note-1",
                    "createdAt": "2026-05-25T00:12:34.000Z",
                    "text": "hello from Sharkey",
                    "cw": "content warning",
                    "visibility": "home",
                    "renoteCount": 3,
                    "repliesCount": 2,
                    "reactions": {
                      "👍": 4,
                      ":blobcat@.:" : 3
                    },
                    "myReaction": "👍",
                    "isFavorited": true,
                    "poll": {
                      "multiple": false,
                      "expiresAt": "2026-05-26T00:00:00.000Z",
                      "choices": [
                        {"text": "A", "votes": 4, "isVoted": false},
                        {"text": "B", "votes": 2, "isVoted": true}
                      ]
                    },
                    "files": [
                      {
                        "id": "file-1",
                        "name": "image.png",
                        "comment": "截图里的替代说明",
                        "type": "image/png",
                        "url": "https://dc.hhhl.cc/files/image.png",
                        "thumbnailUrl": "https://dc.hhhl.cc/thumb.webp"
                      }
                    ],
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice",
                      "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
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
