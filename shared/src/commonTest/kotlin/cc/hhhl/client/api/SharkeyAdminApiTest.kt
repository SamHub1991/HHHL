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

class SharkeyAdminApiTest {
    @Test
    fun searchUsersPostsToAdminEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAdminApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondUsers()
            },
        )

        val result = api.searchUsers("token-123", "alice", 20)

        assertIs<AdminApiResult.Success<*>>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/admin/show-users", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""query":"alice""""))
        assertTrue(body.contains(""""limit":20"""))
    }

    @Test
    fun forbiddenMapsToPermissionErrorInsteadOfUnauthorized() = runTest {
        val api = SharkeyAdminApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Your app does not have the necessary permissions to use this endpoint."}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadReports("token-123", 20)

        val error = assertIs<AdminApiResult.ServerError>(result)
        assertEquals(403, error.statusCode)
        assertEquals("当前登录缺少此功能权限，请检查应用授权或账号权限", error.message)
    }

    @Test
    fun unauthorizedStillMapsToUnauthorized() = runTest {
        val api = SharkeyAdminApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<AdminApiResult.Unauthorized>(api.loadReports("expired", 20))
    }

    private fun MockRequestHandleScope.respondUsers(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "user-1",
                    "username": "alice",
                    "name": "Alice",
                    "createdAt": "2026-05-25T00:00:00.000Z"
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
