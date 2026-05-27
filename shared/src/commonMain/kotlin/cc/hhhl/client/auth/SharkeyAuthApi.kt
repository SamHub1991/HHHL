package cc.hhhl.client.auth

import cc.hhhl.client.api.isSharkeyUnauthorized
import cc.hhhl.client.api.toSharkeyApiErrorMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharkeyAuthApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultClient(),
) : Authenticator {
    override fun buildAuthorizationUrl(session: String): String {
        return buildMiAuthUrl(
            baseUrl = baseUrl,
            session = session,
            appName = "HHHL",
            callbackUrl = "hhhl://miauth",
            permissions = defaultPermissions,
        )
    }

    override suspend fun checkSession(session: String): AuthResult {
        val cleanSession = session.trim()
        if (cleanSession.isEmpty()) {
            return AuthResult.InvalidToken
        }

        return try {
            val response = client.post(apiUrl("miauth", cleanSession, "check")) {
                contentType(ContentType.Application.Json)
                setBody(EmptyRequest())
            }

            if (response.isSharkeyUnauthorized()) return AuthResult.InvalidToken
            when (response.status) {
                HttpStatusCode.OK -> {
                    val auth = response.body<MiAuthCheckResponse>()
                    AuthResult.Success(
                        token = auth.token,
                        user = auth.user.toAuthenticatedUser(),
                    )
                }
                HttpStatusCode.Unauthorized -> AuthResult.InvalidToken
                HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> AuthResult.ServerError(
                    statusCode = response.status.value,
                    message = response.authApiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
                else -> AuthResult.ServerError(
                    statusCode = response.status.value,
                    message = response.authApiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AuthResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun verifyToken(token: String): AuthResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            return AuthResult.InvalidToken
        }

        return try {
            val response = client.post(apiUrl("i")) {
                contentType(ContentType.Application.Json)
                setBody(TokenRequest(cleanToken))
            }

            if (response.isSharkeyUnauthorized()) return AuthResult.InvalidToken
            when (response.status) {
                HttpStatusCode.OK -> {
                    val user = response.body<UserResponse>()
                    AuthResult.Success(
                        token = cleanToken,
                        user = user.toAuthenticatedUser(),
                    )
                }
                HttpStatusCode.Unauthorized -> AuthResult.InvalidToken
                HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> AuthResult.ServerError(
                    statusCode = response.status.value,
                    message = response.authApiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
                else -> AuthResult.ServerError(
                    statusCode = response.status.value,
                    message = response.authApiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AuthResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun signInWithPassword(
        username: String,
        password: String,
        token: String?,
    ): PasswordLoginResult {
        val cleanUsername = username.trim().trimStart('@')
        if (cleanUsername.isEmpty() || password.isEmpty()) {
            return PasswordLoginResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请输入用户名和密码",
            )
        }

        return try {
            val shownUser = showLocalUser(cleanUsername)
                ?: return PasswordLoginResult.ServerError(
                    statusCode = HttpStatusCode.NotFound.value,
                    message = "用户不存在",
                )
            val response = client.post(apiUrl("signin-flow")) {
                contentType(ContentType.Application.Json)
                setBody(
                    SigninFlowRequest(
                        username = shownUser.username,
                        password = password,
                        token = token?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> response.toPasswordLoginResult()
                else -> PasswordLoginResult.ServerError(
                    statusCode = response.status.value,
                    message = response.signInErrorMessage(),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: PasswordLoginServerException) {
            PasswordLoginResult.ServerError(
                statusCode = error.statusCode,
                message = error.displayMessage,
            )
        } catch (error: Throwable) {
            PasswordLoginResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun showLocalUser(username: String): UserResponse? {
        val response = client.post(apiUrl("users", "show")) {
            contentType(ContentType.Application.Json)
            setBody(UserShowRequest(username = username))
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> null
            else -> throw PasswordLoginServerException(
                statusCode = response.status.value,
                displayMessage = response.authApiErrorMessage() ?: "服务器返回 ${response.status.value}",
            )
        }
    }

    private suspend fun io.ktor.client.statement.HttpResponse.toPasswordLoginResult(): PasswordLoginResult {
        val signIn = body<SigninFlowResponse>()
        if (signIn.finished && !signIn.i.isNullOrBlank()) {
            return when (val verified = verifyToken(signIn.i)) {
                is AuthResult.Success -> PasswordLoginResult.Success(
                    token = verified.token,
                    user = verified.user,
                )
                AuthResult.InvalidToken -> PasswordLoginResult.ServerError(
                    statusCode = HttpStatusCode.Unauthorized.value,
                    message = "登录返回的令牌无效",
                )
                is AuthResult.NetworkError -> PasswordLoginResult.NetworkError(verified.message)
                is AuthResult.ServerError -> PasswordLoginResult.ServerError(
                    statusCode = verified.statusCode,
                    message = verified.message,
                )
            }
        }

        return when (signIn.next) {
            "totp" -> PasswordLoginResult.NeedsTotp
            "captcha" -> PasswordLoginResult.CaptchaRequired
            "passkey" -> PasswordLoginResult.PasskeyRequired
            "password" -> PasswordLoginResult.ServerError(
                statusCode = HttpStatusCode.Unauthorized.value,
                message = "密码不正确",
            )
            else -> PasswordLoginResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "登录流程未完成",
            )
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(normalizedBaseUrl())
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    private fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/')

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
        val defaultPermissions = listOf(
            "read:account",
            "write:account",
            "read:chat",
            "write:chat",
            "read:messaging",
            "write:messaging",
            "read:notes",
            "write:notes",
            "read:notifications",
            "write:notifications",
            "write:reactions",
            "write:votes",
            "read:drive",
            "write:drive",
            "read:favorites",
            "write:favorites",
            "read:following",
            "write:following",
            "read:blocks",
            "write:blocks",
            "read:mutes",
            "write:mutes",
            "read:channels",
            "write:channels",
            "read:clips",
            "write:clips",
            "read:clip-favorite",
            "write:clip-favorite",
            "read:gallery",
            "write:gallery",
            "read:gallery-likes",
            "write:gallery-likes",
            "read:pages",
            "write:pages",
            "read:page-likes",
            "write:page-likes",
            "read:flash",
            "write:flash",
            "read:flash-likes",
            "write:flash-likes",
        )

        fun buildMiAuthUrl(
            baseUrl: String = DEFAULT_BASE_URL,
            session: String,
            appName: String,
            callbackUrl: String?,
            permissions: List<String>,
        ): String {
            return URLBuilder(baseUrl.trim().trimEnd('/'))
                .appendPathSegments("miauth", session)
                .apply {
                    parameters.append("name", appName)
                    parameters.append("permission", permissions.joinToString(","))
                    if (!callbackUrl.isNullOrBlank()) {
                        parameters.append("callback", callbackUrl)
                    }
                }
                .buildString()
        }
    }
}

private fun defaultClient(): HttpClient {
    return HttpClient {
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

@Serializable
private class EmptyRequest

@Serializable
private data class TokenRequest(
    val i: String,
)

@Serializable
private data class UserShowRequest(
    val username: String,
)

@Serializable
private data class SigninFlowRequest(
    val username: String,
    val password: String? = null,
    val token: String? = null,
    @SerialName("hcaptcha-response")
    val hcaptchaResponse: String? = null,
    @SerialName("m-captcha-response")
    val mcaptchaResponse: String? = null,
    @SerialName("g-recaptcha-response")
    val recaptchaResponse: String? = null,
    @SerialName("turnstile-response")
    val turnstileResponse: String? = null,
    @SerialName("frc-captcha-solution")
    val friendlyCaptchaResponse: String? = null,
    @SerialName("testcaptcha-response")
    val testcaptchaResponse: String? = null,
)

@Serializable
private data class SigninFlowResponse(
    val finished: Boolean = false,
    val next: String? = null,
    val i: String? = null,
    val id: String? = null,
)

@Serializable
private data class UserResponse(
    val id: String,
    val username: String,
    val name: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
private data class MiAuthCheckResponse(
    val token: String,
    val user: UserResponse,
)

private fun UserResponse.toAuthenticatedUser(): AuthenticatedUser {
    return AuthenticatedUser(
        id = id,
        username = username,
        displayName = name?.takeIf { it.isNotBlank() } ?: username,
        avatarUrl = avatarUrl,
    )
}

private class PasswordLoginServerException(
    val statusCode: Int,
    val displayMessage: String,
) : RuntimeException(displayMessage)

private suspend fun io.ktor.client.statement.HttpResponse.authApiErrorMessage(): String? {
    return bodyAsText().toSharkeyApiErrorMessage(status.value)
}

private suspend fun io.ktor.client.statement.HttpResponse.signInErrorMessage(): String {
    val text = bodyAsText()
    if (
        text.contains("CaptchaError", ignoreCase = true) &&
        text.contains("turnstile", ignoreCase = true)
    ) {
        return "当前实例要求验证码验证，App 暂不支持此密码登录流程，请使用浏览器授权登录"
    }
    return when (text.sharkeyErrorId()) {
        "6cc579cc-885d-43d8-95c2-b8c7fc963280",
        "4362f8dc-731f-4ad8-a694-be5a88922a24" -> "用户不存在"
        "932c904e-9460-45b7-9ce6-7ed33be7eb2c" -> "密码不正确"
        "22d05606-fbcf-421a-a2db-b32610dcfd1b" -> "登录尝试过于频繁，请稍后再试"
        "cdf1235b-ac71-46d4-a3a6-84ccce48df6f" -> "二步验证码不正确"
        "s8dhsj9s-a93j-493j-ja9k-kas9sj20aml2" -> "系统账号不能使用密码登录"
        else -> text.toSharkeyApiErrorMessage(status.value) ?: "服务器返回 ${status.value}"
    }
}

private fun String.sharkeyErrorId(): String? {
    val root = runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    return root["error"]
        ?.let { runCatching { it.jsonObject["id"]?.jsonPrimitive?.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
}
