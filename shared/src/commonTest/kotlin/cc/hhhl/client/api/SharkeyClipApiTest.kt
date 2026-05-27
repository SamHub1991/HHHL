package cc.hhhl.client.api

import cc.hhhl.client.model.ClipListKind
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

class SharkeyClipApiTest {
    @Test
    fun loadOwnedClipsPostsJsonToClipsListEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondClips()
            },
        )

        val result = api.loadClips(token = "token-123", kind = ClipListKind.Owned)

        assertIs<ClipLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/clips/list", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        val clip = result.clips.single()
        assertEquals("clip-1", clip.id)
        assertEquals("收藏的长文", clip.name)
        assertEquals("Alice", clip.owner.displayName)
        assertEquals(12, clip.notesCount)
        assertEquals(3, clip.favoritedCount)
        assertEquals("2026-05-25 16:00", clip.createdAtLabel)
        assertEquals("2026-05-25 17:00", clip.lastClippedAtLabel)
    }

    @Test
    fun loadFavoriteClipsUsesMyFavoritesEndpoint() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                respondClips()
            },
        )

        api.loadClips(token = "token-123", kind = ClipListKind.Favorites)

        assertEquals(listOf("https://dc.hhhl.cc/api/clips/my-favorites"), paths)
    }

    @Test
    fun loadClipNotesPostsJsonToClipsNotesEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondNotes()
            },
        )

        val result = api.loadClipNotes(
            token = "token-123",
            clipId = "clip-1",
            limit = 20,
            untilId = "note-old",
        )

        assertIs<ClipNotesLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/clips/notes", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""clipId":"clip-1""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"note-old""""))
        assertEquals("note-1", result.notes.single().id)
    }

    @Test
    fun loadNoteClipsPostsJsonToNotesClipsEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondClips()
            },
        )

        val result = api.loadNoteClips(token = "token-123", noteId = "note-1")

        assertIs<ClipLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/notes/clips", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""noteId":"note-1""""))
        assertEquals("clip-1", result.clips.single().id)
    }

    @Test
    fun createClipPostsJsonToCreateEndpointAndMapsClip() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondClip()
            },
        )

        val result = api.createClip(
            token = "token-123",
            name = "阅读清单",
            description = "长文和资料",
            isPublic = true,
        )

        assertIs<ClipCreateResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/clips/create", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""name":"阅读清单""""))
        assertTrue(body.contains(""""description":"长文和资料""""))
        assertTrue(body.contains(""""isPublic":true"""))
        assertEquals("clip-1", result.clip.id)
        assertEquals("收藏的长文", result.clip.name)
    }

    @Test
    fun updateClipPostsJsonToUpdateEndpointAndMapsClip() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondClip()
            },
        )

        val result = api.updateClip(
            token = "token-123",
            clipId = "clip-1",
            name = "资料",
            description = "更新后的描述",
            isPublic = false,
        )

        assertIs<ClipUpdateResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/clips/update", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""clipId":"clip-1""""))
        assertTrue(body.contains(""""name":"资料""""))
        assertTrue(body.contains(""""description":"更新后的描述""""))
        assertTrue(body.contains(""""isPublic":false"""))
        assertEquals("clip-1", result.clip.id)
    }

    @Test
    fun deleteClipPostsClipIdToDeleteEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.deleteClip(token = "token-123", clipId = "clip-1")

        assertIs<ClipActionResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/clips/delete", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""clipId":"clip-1""""))
    }

    @Test
    fun favoritesAndUnfavoritesClipUsingClipId() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""clipId":"clip-1""""))
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<ClipActionResult.Success>(api.favoriteClip("token-123", "clip-1"))
        assertIs<ClipActionResult.Success>(api.unfavoriteClip("token-123", "clip-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/clips/favorite",
                "https://dc.hhhl.cc/api/clips/unfavorite",
            ),
            paths,
        )
    }

    @Test
    fun addsAndRemovesNoteUsingClipIdAndNoteId() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyClipApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""clipId":"clip-1""""))
                assertTrue(body.contains(""""noteId":"note-1""""))
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<ClipActionResult.Success>(
            api.addNoteToClip(token = "token-123", clipId = "clip-1", noteId = "note-1"),
        )
        assertIs<ClipActionResult.Success>(
            api.removeNoteFromClip(token = "token-123", clipId = "clip-1", noteId = "note-1"),
        )

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/clips/add-note",
                "https://dc.hhhl.cc/api/clips/remove-note",
            ),
            paths,
        )
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyClipApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<ClipLoadResult.Unauthorized>(api.loadClips("expired", ClipListKind.Owned))
        assertIs<ClipNotesLoadResult.Unauthorized>(
            api.loadClipNotes("expired", clipId = "clip-1", limit = 20),
        )
        assertIs<ClipLoadResult.Unauthorized>(api.loadNoteClips("expired", noteId = "note-1"))
    }

    @Test
    fun blankClipOrNoteIdsDoNotMapToUnauthorized() = runTest {
        val api = SharkeyClipApi(
            client = testClient {
                error("network should not be called for blank local ids")
            },
        )

        val updateResult = api.updateClip(
            token = "token-123",
            clipId = " ",
            name = "收藏",
            description = "",
            isPublic = false,
        )
        val deleteResult = api.deleteClip("token-123", clipId = " ")
        val addResult = api.addNoteToClip("token-123", clipId = "clip-1", noteId = " ")
        val noteClipsResult = api.loadNoteClips("token-123", noteId = " ")

        assertIs<ClipUpdateResult.ServerError>(updateResult)
        assertIs<ClipActionResult.ServerError>(deleteResult)
        assertIs<ClipActionResult.ServerError>(addResult)
        assertIs<ClipLoadResult.ServerError>(noteClipsResult)
    }

    private fun MockRequestHandleScope.respondClips(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "clip-1",
                    "createdAt": "2026-05-25T08:00:00.000Z",
                    "lastClippedAt": "2026-05-25T09:00:00.000Z",
                    "userId": "user-1",
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice",
                      "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                    },
                    "name": "收藏的长文",
                    "description": "值得反复看",
                    "isPublic": true,
                    "favoritedCount": 3,
                    "isFavorited": false,
                    "notesCount": 12
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondClip(): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "clip-1",
                  "createdAt": "2026-05-25T08:00:00.000Z",
                  "lastClippedAt": "2026-05-25T09:00:00.000Z",
                  "userId": "user-1",
                  "user": {
                    "id": "user-1",
                    "username": "alice",
                    "name": "Alice",
                    "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                  },
                  "name": "收藏的长文",
                  "description": "值得反复看",
                  "isPublic": true,
                  "favoritedCount": 3,
                  "isFavorited": false,
                  "notesCount": 12
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondNotes(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "note-1",
                    "createdAt": "2026-05-25T00:12:34.000Z",
                    "text": "hello from clip",
                    "visibility": "home",
                    "renoteCount": 1,
                    "repliesCount": 0,
                    "reactions": {"👍": 2},
                    "files": [],
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
