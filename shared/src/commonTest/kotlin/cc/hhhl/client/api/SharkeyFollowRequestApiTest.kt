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

class SharkeyFollowRequestApiTest {
    @Test
    fun loadReceivedRequestsPostsToListEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyFollowRequestApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondRequests()
            },
        )

        val result = api.loadReceived("token-123", limit = 20, untilId = "req-old")

        assertIs<FollowRequestLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/following/requests/list", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"req-old""""))
        val item = result.requests.single()
        assertEquals("req-1", item.id)
        assertEquals("follower-1", item.user.id)
        assertEquals("申请者", item.user.displayName)
    }

    @Test
    fun acceptAndRejectPostUserIdToActionEndpoints() = runTest {
        val calls = mutableListOf<String>()
        val api = SharkeyFollowRequestApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                calls.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"follower-1""""))
                respondOk()
            },
        )

        assertIs<FollowRequestActionResult.Success>(api.accept("token-123", "follower-1"))
        assertIs<FollowRequestActionResult.Success>(api.reject("token-123", "follower-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/following/requests/accept",
                "https://dc.hhhl.cc/api/following/requests/reject",
            ),
            calls,
        )
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyFollowRequestApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<FollowRequestLoadResult.Unauthorized>(api.loadReceived("expired", 20))
        assertIs<FollowRequestActionResult.Unauthorized>(api.accept("expired", "follower-1"))
    }

    @Test
    fun blankUserIdActionDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyFollowRequestApi(
            client = testClient {
                error("network should not be called for blank user id")
            },
        )

        assertIs<FollowRequestActionResult.ServerError>(api.accept("token-123", " "))
    }

    private fun MockRequestHandleScope.respondRequests(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "req-1",
                    "follower": {
                      "id": "follower-1",
                      "name": "申请者",
                      "username": "alice",
                      "host": null,
                      "avatarUrl": null
                    },
                    "followee": {
                      "id": "me-1",
                      "name": "我",
                      "username": "me",
                      "host": null,
                      "avatarUrl": null
                    }
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondOk(): HttpResponseData {
        return respond(
            content = "null",
            status = HttpStatusCode.NoContent,
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
