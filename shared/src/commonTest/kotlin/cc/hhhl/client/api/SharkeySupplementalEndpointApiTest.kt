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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SharkeySupplementalEndpointApiTest {
    @Test
    fun loadsActivityPubObjectFromApGetEndpoint() = runTest {
        val api = SharkeySupplementalEndpointApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/ap/get", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(
                    """{"i":"token-123","uri":"https://remote.example/users/alice","expandCollectionItems":true,"expandCollectionLimit":20,"allowAnonymous":false}""",
                    (request.body as TextContent).text,
                )
                respond("""{"type":"Person","id":"https://remote.example/users/alice"}""", HttpStatusCode.OK, jsonHeaders)
            },
        )

        val result = api.loadActivityPubObject(
            token = "token-123",
            query = ActivityPubGetQuery(
                uri = "https://remote.example/users/alice",
                expandCollectionItems = true,
                expandCollectionLimit = 20,
            ),
        )

        val success = assertIs<SupplementalResult.Success<*>>(result)
        val json = assertIs<JsonObject>(success.value)
        assertEquals("Person", json["type"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun fetchesRssWithoutAuthToken() = runTest {
        val api = SharkeySupplementalEndpointApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/fetch-rss", request.url.toString())
                assertEquals("""{"url":"https://example.com/feed.xml"}""", (request.body as TextContent).text)
                respond(
                    """{"type":"rss","title":"Feed","items":[{"title":"Item","media":[{"url":"https://example.com/a.png"}]}]}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            },
        )

        val result = api.fetchRss("https://example.com/feed.xml")

        val success = assertIs<SupplementalResult.Success<*>>(result)
        val feed = assertIs<cc.hhhl.client.model.RssFeed>(success.value)
        assertEquals("Feed", feed.title)
        assertEquals("Item", feed.items.single().title)
        assertEquals("https://example.com/a.png", feed.items.single().media.single().url)
    }

    @Test
    fun loadsRetentionWithoutJsonPayload() = runTest {
        val api = SharkeySupplementalEndpointApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/retention", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertFalse(request.body is TextContent)
                respond(
                    """[{"createdAt":"2026-05-01T00:00:00.000Z","users":12,"data":{"1":8}}]""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            },
        )

        val result = api.loadRetention()

        val success = assertIs<SupplementalResult.Success<*>>(result)
        val records = assertIs<List<*>>(success.value)
        assertEquals(1, records.size)
    }

    @Test
    fun pushesPageEventWithVarPayload() = runTest {
        val api = SharkeySupplementalEndpointApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/page-push", request.url.toString())
                assertEquals(
                    """{"i":"token-123","pageId":"page-1","event":"submit","var":{"answer":"ok"}}""",
                    (request.body as TextContent).text,
                )
                respond("{}", HttpStatusCode.NoContent, jsonHeaders)
            },
        )

        val result = api.pushPageEvent(
            token = "token-123",
            pageId = "page-1",
            event = "submit",
            value = JsonObject(mapOf("answer" to JsonPrimitive("ok"))),
        )

        assertIs<SupplementalActionResult.Success>(result)
    }

    @Test
    fun readsDriveUsageFromDriveEndpoint() = runTest {
        val api = SharkeySupplementalEndpointApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/drive", request.url.toString())
                assertEquals("""{"i":"token-123"}""", (request.body as TextContent).text)
                respond("""{"capacity":1048576,"usage":1024}""", HttpStatusCode.OK, jsonHeaders)
            },
        )

        val result = api.loadDriveUsage("token-123")

        val success = assertIs<SupplementalResult.Success<*>>(result)
        val usage = assertIs<cc.hhhl.client.model.DriveUsage>(success.value)
        assertEquals(1048576.0, usage.capacity)
        assertEquals(1024.0, usage.usage)
    }

    private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
