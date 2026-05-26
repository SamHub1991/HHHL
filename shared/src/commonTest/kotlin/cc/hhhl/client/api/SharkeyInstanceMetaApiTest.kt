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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SharkeyInstanceMetaApiTest {
    @Test
    fun loadsMetaFromApiMetaEndpoint() = runTest {
        val api = SharkeyInstanceMetaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/meta", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                assertEquals("{}", (request.body as TextContent).text)
                respondMeta()
            },
        )

        val result = api.loadMeta()

        assertIs<InstanceMetaLoadResult.Success>(result)
        assertEquals("hhhl", result.meta.name)
        assertEquals("期待AGI时代来临", result.meta.description)
        assertEquals("2025.5.2-dev", result.meta.version)
        assertEquals(3000, result.meta.maxNoteTextLength)
        assertEquals(500, result.meta.maxCwLength)
        assertEquals("❤️", result.meta.defaultLike)
        assertFalse(result.meta.capabilities.canSearchNotes)
        assertTrue(result.meta.capabilities.localTimelineAvailable)
        assertTrue(result.meta.capabilities.globalTimelineAvailable)
        assertFalse(result.meta.capabilities.bubbleTimelineAvailable)
        assertTrue(result.meta.capabilities.chatAvailable)
        assertEquals(10, result.meta.capabilities.clipLimit)
        assertEquals(5, result.meta.capabilities.antennaLimit)
        assertEquals(10, result.meta.capabilities.userListLimit)
        assertEquals(50, result.meta.capabilities.userEachUserListsLimit)
        assertEquals(5, result.meta.capabilities.scheduleNoteMax)
    }

    @Test
    fun mapsServerErrorMessageFromErrorEnvelope() = runTest {
        val api = SharkeyInstanceMetaApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"meta unavailable"}}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadMeta()

        assertIs<InstanceMetaLoadResult.ServerError>(result)
        assertEquals("meta unavailable", result.message)
    }

    private fun MockRequestHandleScope.respondMeta(): HttpResponseData {
        return respond(
            content = """
                {
                  "name": "hhhl",
                  "description": "期待AGI时代来临",
                  "version": "2025.5.2-dev",
                  "iconUrl": "/client-assets/icon.png",
                  "themeColor": "#86b300",
                  "maxNoteTextLength": 3000,
                  "maxCwLength": 500,
                  "defaultLike": "❤️",
                  "noteSearchableScope": "global",
                  "policies": {
                    "gtlAvailable": true,
                    "ltlAvailable": true,
                    "btlAvailable": false,
                    "canPublicNote": true,
                    "canSearchNotes": false,
                    "clipLimit": 10,
                    "antennaLimit": 5,
                    "userListLimit": 10,
                    "userEachUserListsLimit": 50,
                    "scheduleNoteMax": 5,
                    "driveCapacityMb": 100,
                    "maxFileSizeMb": 25,
                    "chatAvailability": "available",
                    "canTrend": true,
                    "canViewFederation": true
                  },
                  "features": {
                    "miauth": true
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
