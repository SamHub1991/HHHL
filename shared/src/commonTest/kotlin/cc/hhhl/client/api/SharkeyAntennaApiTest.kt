package cc.hhhl.client.api

import cc.hhhl.client.model.AntennaDraft
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

class SharkeyAntennaApiTest {
    @Test
    fun loadAntennasPostsJsonToAntennasListEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAntennaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondAntennas()
            },
        )

        val result = api.loadAntennas(token = "token-123")

        assertIs<AntennaLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/antennas/list", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        val antenna = result.antennas.single()
        assertEquals("antenna-1", antenna.id)
        assertEquals("AGI", antenna.name)
        assertEquals("all", antenna.source)
        assertEquals("AGI / LLM", antenna.keywordPreview)
        assertEquals(true, antenna.hasUnreadNote)
        assertEquals("2026-05-25 15:00", antenna.createdAtLabel)
    }

    @Test
    fun loadAntennaNotesPostsJsonToAntennasNotesEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAntennaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondNotes()
            },
        )

        val result = api.loadAntennaNotes(
            token = "token-123",
            antennaId = "antenna-1",
            limit = 20,
            untilId = "note-old",
        )

        assertIs<AntennaNotesLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/antennas/notes", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""antennaId":"antenna-1""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"note-old""""))
        assertEquals("note-1", result.notes.single().id)
    }

    @Test
    fun createAntennaPostsDraftToCreateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAntennaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondAntenna()
            },
        )

        val result = api.createAntenna("token-123", sampleDraft())

        assertIs<AntennaMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/antennas/create", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""name":"AGI""""))
        assertTrue(body.contains(""""src":"all""""))
        assertTrue(body.contains(""""keywords":[["AGI"],["LLM"]]"""))
        assertTrue(body.contains(""""excludeBots":true"""))
        assertEquals("antenna-1", result.antenna.id)
    }

    @Test
    fun updateAntennaPostsDraftToUpdateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAntennaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondAntenna()
            },
        )

        val result = api.updateAntenna("token-123", "antenna-1", sampleDraft())

        assertIs<AntennaMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/antennas/update", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""antennaId":"antenna-1""""))
        assertTrue(body.contains(""""name":"AGI""""))
        assertTrue(body.contains(""""isActive":true"""))
    }

    @Test
    fun deleteAntennaPostsIdToDeleteEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAntennaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.deleteAntenna("token-123", "antenna-1")

        assertIs<AntennaActionResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/antennas/delete", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""antennaId":"antenna-1""""))
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyAntennaApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<AntennaLoadResult.Unauthorized>(api.loadAntennas("expired"))
        assertIs<AntennaNotesLoadResult.Unauthorized>(
            api.loadAntennaNotes("expired", antennaId = "antenna-1", limit = 20),
        )
    }

    @Test
    fun blankAntennaIdDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyAntennaApi(
            client = testClient {
                error("network should not be called for blank antenna id")
            },
        )

        val notesResult = api.loadAntennaNotes("token-123", antennaId = " ", limit = 20)
        val updateResult = api.updateAntenna(
            token = "token-123",
            antennaId = " ",
            draft = AntennaDraft(name = "AGI", keywords = listOf(listOf("AGI"))),
        )
        val deleteResult = api.deleteAntenna("token-123", antennaId = " ")

        assertIs<AntennaNotesLoadResult.ServerError>(notesResult)
        assertIs<AntennaMutationResult.ServerError>(updateResult)
        assertIs<AntennaActionResult.ServerError>(deleteResult)
    }

    private fun MockRequestHandleScope.respondAntennas(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "antenna-1",
                    "createdAt": "2026-05-25T07:00:00.000Z",
                    "name": "AGI",
                    "keywords": [["AGI"], ["LLM"]],
                    "excludeKeywords": [],
                    "src": "all",
                    "userListId": null,
                    "users": [],
                    "caseSensitive": false,
                    "localOnly": false,
                    "excludeBots": true,
                    "withReplies": false,
                    "withFile": false,
                    "isActive": true,
                    "hasUnreadNote": true,
                    "notify": false,
                    "excludeNotesInSensitiveChannel": true
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondAntenna(): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "antenna-1",
                  "createdAt": "2026-05-25T07:00:00.000Z",
                  "name": "AGI",
                  "keywords": [["AGI"], ["LLM"]],
                  "excludeKeywords": [],
                  "src": "all",
                  "userListId": null,
                  "users": [],
                  "caseSensitive": false,
                  "localOnly": false,
                  "excludeBots": true,
                  "withReplies": false,
                  "withFile": false,
                  "isActive": true,
                  "hasUnreadNote": true,
                  "notify": false,
                  "excludeNotesInSensitiveChannel": true
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun sampleDraft(): AntennaDraft {
        return AntennaDraft(
            name = "AGI",
            source = "all",
            keywords = listOf(listOf("AGI"), listOf("LLM")),
            excludeBots = true,
            excludeNotesInSensitiveChannel = true,
        )
    }

    private fun MockRequestHandleScope.respondNotes(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "note-1",
                    "createdAt": "2026-05-25T00:12:34.000Z",
                    "text": "hello from antenna",
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
