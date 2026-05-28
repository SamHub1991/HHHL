package cc.hhhl.client.api

import cc.hhhl.client.model.PushRegistrationInput
import cc.hhhl.client.model.PushRegistrationState
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

class SharkeyPushRegistrationApiTest {
    @Test
    fun registersPushSubscriptionWithBearerToken() = runTest {
        val api = SharkeyPushRegistrationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/sw/register", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains("\"endpoint\":\"https://push.example/1\""))
                assertTrue(body.contains("\"auth\":\"auth-key\""))
                assertTrue(body.contains("\"publickey\":\"public-key\""))
                assertTrue(body.contains("\"sendReadMessage\":true"))
                respondRegistration(state = "subscribed", key = "server-key")
            },
        )

        val result = api.register(
            token = "token-123",
            input = PushRegistrationInput(
                endpoint = "https://push.example/1",
                auth = "auth-key",
                publicKey = "public-key",
                sendReadMessage = true,
            ),
        )

        assertIs<PushRegistrationResult.Success>(result)
        assertEquals(PushRegistrationState.Subscribed, result.registration.state)
        assertEquals("server-key", result.registration.key)
    }

    @Test
    fun showsExistingRegistration() = runTest {
        val api = SharkeyPushRegistrationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/sw/show-registration", request.url.toString())
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                assertEquals("{\"endpoint\":\"https://push.example/1\"}", (request.body as TextContent).text)
                respondRegistration(state = null, key = null)
            },
        )

        val result = api.showRegistration("token-123", "https://push.example/1")

        assertIs<PushRegistrationLookupResult.Success>(result)
        assertEquals("user-1", result.registration?.userId)
    }

    @Test
    fun showRegistrationMapsNoContentToMissingRegistration() = runTest {
        val api = SharkeyPushRegistrationApi(
            client = testClient {
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.showRegistration("token-123", "https://push.example/1")

        assertIs<PushRegistrationLookupResult.Success>(result)
        assertEquals(null, result.registration)
    }

    @Test
    fun updatesRegistrationReadMessageFlag() = runTest {
        val api = SharkeyPushRegistrationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/sw/update-registration", request.url.toString())
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                val body = (request.body as TextContent).text
                assertTrue(body.contains("\"endpoint\":\"https://push.example/1\""))
                assertTrue(body.contains("\"sendReadMessage\":false"))
                respondRegistration(state = null, key = null, sendReadMessage = false)
            },
        )

        val result = api.updateRegistration("token-123", "https://push.example/1", sendReadMessage = false)

        assertIs<PushRegistrationResult.Success>(result)
        assertEquals(false, result.registration.sendReadMessage)
    }

    @Test
    fun unregistersWithoutBearerToken() = runTest {
        val api = SharkeyPushRegistrationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/sw/unregister", request.url.toString())
                assertEquals(null, request.headers[HttpHeaders.Authorization])
                assertEquals("{\"endpoint\":\"https://push.example/1\"}", (request.body as TextContent).text)
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<PushRegistrationActionResult.Success>(api.unregister("https://push.example/1"))
    }

    private fun MockRequestHandleScope.respondRegistration(
        state: String?,
        key: String?,
        sendReadMessage: Boolean = true,
    ): HttpResponseData {
        return respond(
            content = buildString {
                append("{")
                if (state != null) append("\"state\":\"").append(state).append("\",")
                if (key != null) append("\"key\":\"").append(key).append("\",")
                append("\"userId\":\"user-1\",")
                append("\"endpoint\":\"https://push.example/1\",")
                append("\"sendReadMessage\":").append(sendReadMessage)
                append("}")
            },
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
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
