package cc.hhhl.client.api

import cc.hhhl.client.model.InstanceEndpointInfo
import cc.hhhl.client.model.InstanceOnlineUsers
import cc.hhhl.client.model.InstanceServerInfo
import cc.hhhl.client.model.InstanceStats
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

    @Test
    fun loadsStatsWithoutJsonPayload() = runTest {
        val api = SharkeyInstanceMetaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/stats", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertFalse(request.body is TextContent)
                respond(
                    content = """
                        {
                          "notesCount": 100,
                          "originalNotesCount": 90,
                          "usersCount": 30,
                          "originalUsersCount": 25,
                          "reactionsCount": 400,
                          "instances": 2,
                          "driveUsageLocal": 1024,
                          "driveUsageRemote": 2048
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadStats()

        val success = assertIs<InstanceAuxiliaryLoadResult.Success<InstanceStats>>(result)
        assertEquals(100, success.value.notesCount)
        assertEquals(2048, success.value.driveUsageRemote)
    }

    @Test
    fun loadsOnlineUsersWithoutJsonPayload() = runTest {
        val api = SharkeyInstanceMetaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/get-online-users-count", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertFalse(request.body is TextContent)
                respond(
                    content = """{"count":12,"countAcrossNetwork":34}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadOnlineUsers()

        val success = assertIs<InstanceAuxiliaryLoadResult.Success<InstanceOnlineUsers>>(result)
        assertEquals(12, success.value.count)
        assertEquals(34, success.value.countAcrossNetwork)
    }

    @Test
    fun loadsServerInfoWithoutJsonPayload() = runTest {
        val api = SharkeyInstanceMetaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/server-info", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertFalse(request.body is TextContent)
                respond(
                    content = """
                        {
                          "machine": "x64",
                          "cpu": { "model": "cpu", "cores": 8 },
                          "mem": { "total": 4096 },
                          "fs": { "total": 8192, "used": 2048 }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadServerInfo()

        val success = assertIs<InstanceAuxiliaryLoadResult.Success<InstanceServerInfo>>(result)
        assertEquals("x64", success.value.machine)
        assertEquals(8, success.value.cpuCores)
    }

    @Test
    fun pingsInstanceWithoutJsonPayload() = runTest {
        val api = SharkeyInstanceMetaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/ping", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertFalse(request.body is TextContent)
                respond(
                    content = """{"pong":1710000000000}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.ping()

        val success = assertIs<InstanceAuxiliaryLoadResult.Success<Long>>(result)
        assertEquals(1710000000000, success.value)
    }

    @Test
    fun loadsEndpointListWithoutJsonPayload() = runTest {
        val api = SharkeyInstanceMetaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/endpoints", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertFalse(request.body is TextContent)
                respond(
                    content = """["meta","notes/create"]""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadEndpoints()

        val success = assertIs<InstanceAuxiliaryLoadResult.Success<List<String>>>(result)
        assertEquals(listOf("meta", "notes/create"), success.value)
    }

    @Test
    fun loadsEndpointInfoWithEndpointPayload() = runTest {
        val api = SharkeyInstanceMetaApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/endpoint", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                assertEquals("{\"endpoint\":\"notes/create\"}", (request.body as TextContent).text)
                respond(
                    content = """{"params":[{"name":"text","type":"string"}]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadEndpointInfo("notes/create")

        val success = assertIs<InstanceAuxiliaryLoadResult.Success<InstanceEndpointInfo?>>(result)
        assertEquals("text", success.value?.params?.first()?.name)
        assertEquals("string", success.value?.params?.first()?.type)
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
