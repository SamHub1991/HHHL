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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SharkeyAccountRecoveryApiTest {
    @Test
    fun requestsPasswordReset() = runTest {
        val api = SharkeyAccountRecoveryApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/request-reset-password", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("""{"username":"alice","email":"alice@example.com"}""", (request.body as TextContent).text)
                respond("{}", HttpStatusCode.NoContent, jsonHeaders)
            },
        )

        val result = api.requestPasswordReset("@alice", "alice@example.com")

        assertIs<AccountRecoveryResult.Success>(result)
    }

    @Test
    fun completesPasswordReset() = runTest {
        val api = SharkeyAccountRecoveryApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/reset-password", request.url.toString())
                assertEquals("""{"token":"reset-token","password":"new-password"}""", (request.body as TextContent).text)
                respond("{}", HttpStatusCode.OK, jsonHeaders)
            },
        )

        val result = api.resetPassword("reset-token", "new-password")

        assertIs<AccountRecoveryResult.Success>(result)
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
