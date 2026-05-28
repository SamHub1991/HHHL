package cc.hhhl.client.api

import cc.hhhl.client.model.AppCreateInput
import cc.hhhl.client.model.MiAuthTokenInput
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

class SharkeyAppAuthorizationApiTest {
    @Test
    fun loadCurrentAppPostsWithoutBody() = runTest {
        val api = SharkeyAppAuthorizationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEndpoint(request, "/api/app/current")
                assertEquals(null, request.headers[HttpHeaders.Authorization])
                assertEquals(false, request.body is TextContent)
                respondApp()
            },
        )

        val result = api.loadCurrentApp()

        val success = assertIs<AppAuthorizationResult.Success<*>>(result)
        val app = success.value as cc.hhhl.client.model.AuthorizedApp
        assertEquals("app-1", app.id)
        assertEquals("HHHL Mobile", app.name)
        assertEquals(listOf("read:account", "write:notes"), app.permissions)
        assertEquals("secret-1", app.secret)
        assertEquals(true, app.isAuthorized)
    }

    @Test
    fun showAppPostsAppId() = runTest {
        val api = SharkeyAppAuthorizationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEndpoint(request, "/api/app/show")
                assertEquals(ContentType.Application.Json, request.body.contentType)
                assertEquals("""{"appId":"app-1"}""", (request.body as TextContent).text)
                respondApp()
            },
        )

        assertIs<AppAuthorizationResult.Success<*>>(api.showApp(" app-1 "))
    }

    @Test
    fun createAppPostsCleanedPayload() = runTest {
        val api = SharkeyAppAuthorizationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEndpoint(request, "/api/app/create")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""name":"HHHL Mobile""""))
                assertTrue(body.contains(""""description":"Mobile client""""))
                assertTrue(body.contains(""""permission":["read:account","write:notes"]"""))
                assertTrue(body.contains(""""callbackUrl":"hhhl://miauth""""))
                respondApp()
            },
        )

        val result = api.createApp(
            AppCreateInput(
                name = " HHHL Mobile ",
                description = " Mobile client ",
                permissions = listOf(" read:account ", "write:notes", "read:account"),
                callbackUrl = " hhhl://miauth ",
            ),
        )

        assertIs<AppAuthorizationResult.Success<*>>(result)
    }

    @Test
    fun loadMyAppsUsesBearerTokenAndPagingBody() = runTest {
        val api = SharkeyAppAuthorizationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEndpoint(request, "/api/my/apps")
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""offset":0"""))
                assertTrue(!body.contains(""""i""""))
                respondJson("""[${sampleAppJson()}]""")
            },
        )

        val result = api.loadMyApps(" token-123 ", limit = 500, offset = -10)

        val success = assertIs<AppAuthorizationResult.Success<*>>(result)
        val apps = success.value as List<*>
        assertEquals(1, apps.size)
    }

    @Test
    fun generatesAndShowsAuthSession() = runTest {
        val calls = mutableListOf<String>()
        val api = SharkeyAppAuthorizationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                calls.add(request.url.encodedPath)
                when (request.url.encodedPath) {
                    "/api/auth/session/generate" -> {
                        assertEquals("""{"appSecret":"secret-1"}""", (request.body as TextContent).text)
                        respondJson("""{"token":"session-token","url":"https://dc.hhhl.cc/auth/session"}""")
                    }
                    "/api/auth/session/show" -> {
                        assertEquals("""{"token":"session-token"}""", (request.body as TextContent).text)
                        respondJson("""{"id":"session-1","token":"session-token","app":${sampleAppJson()}}""")
                    }
                    else -> error("Unexpected endpoint ${request.url.encodedPath}")
                }
            },
        )

        assertIs<AppAuthorizationResult.Success<*>>(api.generateAuthSession(" secret-1 "))
        assertIs<AppAuthorizationResult.Success<*>>(api.showAuthSession(" session-token "))
        assertEquals(listOf("/api/auth/session/generate", "/api/auth/session/show"), calls)
    }

    @Test
    fun fetchesAuthSessionUserKey() = runTest {
        val api = SharkeyAppAuthorizationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEndpoint(request, "/api/auth/session/userkey")
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""appSecret":"secret-1""""))
                assertTrue(body.contains(""""token":"session-token""""))
                respondJson(
                    """
                        {
                          "accessToken":"access-1",
                          "user":{"id":"user-1","username":"alice","name":"Alice","avatarUrl":"https://example.com/a.png"}
                        }
                    """.trimIndent(),
                )
            },
        )

        val result = api.fetchAuthSessionUserKey("secret-1", "session-token")

        val success = assertIs<AppAuthorizationResult.Success<*>>(result)
        val userKey = success.value as cc.hhhl.client.model.AuthSessionUserKey
        assertEquals("access-1", userKey.accessToken)
        assertEquals("Alice", userKey.user.displayName)
    }

    @Test
    fun acceptsAuthSessionWithBearerToken() = runTest {
        val api = SharkeyAppAuthorizationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEndpoint(request, "/api/auth/accept")
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                assertEquals("""{"token":"session-token"}""", (request.body as TextContent).text)
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertEquals(
            AppAuthorizationActionResult.Success,
            api.acceptAuthSession("token-123", "session-token"),
        )
    }

    @Test
    fun generatesMiAuthTokenWithRequiredNullSession() = runTest {
        val api = SharkeyAppAuthorizationApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEndpoint(request, "/api/miauth/gen-token")
                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""session":null"""))
                assertTrue(body.contains(""""permission":["read:account","write:notes"]"""))
                assertTrue(body.contains(""""name":"HHHL Mobile""""))
                respondJson("""{"token":"generated-token"}""")
            },
        )

        val result = api.generateMiAuthToken(
            token = " token-123 ",
            input = MiAuthTokenInput(
                session = null,
                permissions = listOf(" read:account ", "write:notes", "read:account"),
                name = " HHHL Mobile ",
            ),
        )

        val success = assertIs<AppAuthorizationResult.Success<*>>(result)
        assertEquals("generated-token", success.value)
    }

    private fun assertEndpoint(
        request: HttpRequestData,
        encodedPath: String,
    ) {
        assertEquals("https://dc.hhhl.cc$encodedPath", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
    }

    private fun MockRequestHandleScope.respondApp(): HttpResponseData {
        return respondJson(sampleAppJson())
    }

    private fun MockRequestHandleScope.respondJson(content: String): HttpResponseData {
        return respond(
            content = content,
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

        fun sampleAppJson(): String {
            return """
                {
                  "id":"app-1",
                  "name":"HHHL Mobile",
                  "callbackUrl":"hhhl://miauth",
                  "permission":["read:account","write:notes"],
                  "secret":"secret-1",
                  "isAuthorized":true
                }
            """.trimIndent()
        }
    }
}
