package cc.hhhl.client.auth

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

private suspend fun io.ktor.client.statement.HttpResponse.authApiErrorMessage(): String? {
    return bodyAsText().toSharkeyApiErrorMessage(status.value)
}
