package cc.hhhl.client.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
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

class SharkeyAuthApiTest {
    @Test
    fun buildsMiAuthUrlForTheHHHLInstance() {
        val url = SharkeyAuthApi.buildMiAuthUrl(
            baseUrl = "https://dc.hhhl.cc/",
            session = "session-123",
            appName = "HHHL",
            callbackUrl = "hhhl://miauth",
            permissions = listOf("read:account", "write:notes"),
        )

        assertEquals(
            "https://dc.hhhl.cc/miauth/session-123?name=HHHL&permission=read%3Aaccount%2Cwrite%3Anotes&callback=hhhl%3A%2F%2Fmiauth",
            url,
        )
    }

    @Test
    fun defaultPermissionsCoverEnabledAppModulesWithoutKnownInvalidNames() {
        val permissions = SharkeyAuthApi.defaultPermissions

        listOf(
            "read:chat",
            "write:chat",
            "read:messaging",
            "write:messaging",
            "read:clips",
            "write:clips",
            "read:clip-favorite",
            "write:clip-favorite",
            "read:gallery-likes",
            "write:gallery-likes",
            "read:page-likes",
            "write:page-likes",
            "read:mutes",
            "write:mutes",
        ).forEach { permission ->
            assertTrue(permission in permissions, "$permission should be requested")
        }
        listOf(
            "read:muting",
            "write:muting",
            "read:antenna",
            "write:antenna",
            "read:announcements",
            "write:announcements",
        ).forEach { permission ->
            assertTrue(permission !in permissions, "$permission is not a stable public MiAuth permission")
        }
    }

    @Test
    fun checkMiAuthSessionPostsToSessionCheckAndReturnsAuthenticatedUser() = runTest {
        val api = SharkeyAuthApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/miauth/session-123/check", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                assertEquals("{}", (request.body as TextContent).text)

                respond(
                    content = """
                        {
                          "token": "token-123",
                          "user": {
                            "id": "9s8x",
                            "username": "alice",
                            "name": "Alice",
                            "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

        val result = api.checkSession("session-123")

        assertEquals(
            AuthResult.Success(
                token = "token-123",
                user = AuthenticatedUser(
                    id = "9s8x",
                    username = "alice",
                    displayName = "Alice",
                    avatarUrl = "https://dc.hhhl.cc/avatar.webp",
                ),
            ),
            result,
        )
    }

    @Test
    fun checkMiAuthSessionMapsUnauthorizedResponseToInvalidToken() = runTest {
        val api = SharkeyAuthApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

        assertIs<AuthResult.InvalidToken>(api.checkSession("session-123"))
    }

    @Test
    fun verifyTokenKeepsForbiddenAsServerErrorSoPermissionIssuesDoNotForceLogout() = runTest {
        val api = SharkeyAuthApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient {
                respond(
                    content = """{"error":{"message":"Access denied"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

        val result = api.verifyToken("token-123")

        assertEquals(
            AuthResult.ServerError(
                statusCode = HttpStatusCode.Forbidden.value,
                message = "当前账号没有执行此操作的权限",
            ),
            result,
        )
    }

    @Test
    fun verifyTokenTreatsAuthenticationFailed403AsInvalidToken() = runTest {
        val api = SharkeyAuthApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed. Please ensure your token is correct.","code":"AUTHENTICATION_FAILED"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

        assertIs<AuthResult.InvalidToken>(api.verifyToken("token-123"))
    }

    @Test
    fun verifyTokenPostsToApiIAndReturnsAuthenticatedUserForDiagnostics() = runTest {
        val api = SharkeyAuthApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                assertTrue((request.body as TextContent).text.contains("\"i\":\"token-123\""))

                respond(
                    content = """
                        {
                          "id": "9s8x",
                          "username": "alice",
                          "name": "Alice",
                          "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

        val result = api.verifyToken("token-123")

        assertEquals(
            AuthResult.Success(
                token = "token-123",
                user = AuthenticatedUser(
                    id = "9s8x",
                    username = "alice",
                    displayName = "Alice",
                    avatarUrl = "https://dc.hhhl.cc/avatar.webp",
                ),
            ),
            result,
        )
    }

    @Test
    fun signInWithPasswordUsesSigninFlowAndVerifiesReturnedToken() = runTest {
        val requestedUrls = mutableListOf<String>()
        val api = SharkeyAuthApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient { request ->
                requestedUrls.add(request.url.toString())
                when (request.url.toString()) {
                    "https://dc.hhhl.cc/api/users/show" -> {
                        assertTrue((request.body as TextContent).text.contains("\"username\":\"alice\""))
                        respond(
                            content = """
                                {
                                  "id": "9s8x",
                                  "username": "alice",
                                  "name": "Alice",
                                  "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    "https://dc.hhhl.cc/api/signin-flow" -> {
                        val body = (request.body as TextContent).text
                        assertTrue(body.contains("\"username\":\"alice\""))
                        assertTrue(body.contains("\"password\":\"secret\""))
                        respond(
                            content = """{"finished":true,"id":"9s8x","i":"token-123"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    "https://dc.hhhl.cc/api/i" -> {
                        assertTrue((request.body as TextContent).text.contains("\"i\":\"token-123\""))
                        respond(
                            content = """
                                {
                                  "id": "9s8x",
                                  "username": "alice",
                                  "name": "Alice",
                                  "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    else -> error("Unexpected request ${request.url}")
                }
            },
        )

        val result = api.signInWithPassword("@alice", "secret")

        assertEquals(
            PasswordLoginResult.Success(
                token = "token-123",
                user = AuthenticatedUser(
                    id = "9s8x",
                    username = "alice",
                    displayName = "Alice",
                    avatarUrl = "https://dc.hhhl.cc/avatar.webp",
                ),
            ),
            result,
        )
        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/users/show",
                "https://dc.hhhl.cc/api/signin-flow",
                "https://dc.hhhl.cc/api/i",
            ),
            requestedUrls,
        )
    }

    @Test
    fun signInWithPasswordCanReturnTotpRequirement() = runTest {
        val api = SharkeyAuthApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient { request ->
                when (request.url.toString()) {
                    "https://dc.hhhl.cc/api/users/show" -> respond(
                        content = """{"id":"9s8x","username":"alice","name":"Alice"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    "https://dc.hhhl.cc/api/signin-flow" -> respond(
                        content = """{"finished":false,"next":"totp"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    else -> error("Unexpected request ${request.url}")
                }
            },
        )

        assertIs<PasswordLoginResult.NeedsTotp>(api.signInWithPassword("alice", "secret"))
    }

    @Test
    fun signInWithPasswordMapsTurnstileCaptchaRequirementToClearMessage() = runTest {
        val api = SharkeyAuthApi(
            baseUrl = "https://dc.hhhl.cc",
            client = testClient { request ->
                when (request.url.toString()) {
                    "https://dc.hhhl.cc/api/users/show" -> respond(
                        content = """{"id":"9s8x","username":"alice","name":"Alice"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    "https://dc.hhhl.cc/api/signin-flow" -> respond(
                        content = """{"statusCode":400,"error":"Bad Request","message":"CaptchaError: turnstile-failed: no response provided"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    else -> error("Unexpected request ${request.url}")
                }
            },
        )

        assertEquals(
            PasswordLoginResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "当前实例要求验证码验证，App 暂不支持此密码登录流程，请使用浏览器授权登录",
            ),
            api.signInWithPassword("alice", "secret"),
        )
    }

    private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
