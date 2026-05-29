package cc.hhhl.client.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface AccountRecoveryApi {
    suspend fun requestPasswordReset(username: String, email: String): AccountRecoveryResult

    suspend fun resetPassword(token: String, password: String): AccountRecoveryResult
}

sealed interface AccountRecoveryResult {
    data object Success : AccountRecoveryResult
    data class ServerError(val statusCode: Int, val message: String) : AccountRecoveryResult
    data class NetworkError(val message: String) : AccountRecoveryResult
}

class SharkeyAccountRecoveryApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultAccountRecoveryClient(),
) : AccountRecoveryApi {
    override suspend fun requestPasswordReset(username: String, email: String): AccountRecoveryResult {
        val cleanUsername = username.trim().removePrefix("@")
        val cleanEmail = email.trim()
        if (cleanUsername.isEmpty() || cleanEmail.isEmpty()) {
            return AccountRecoveryResult.ServerError(400, "用户名和邮箱不能为空")
        }

        return postAction(
            endpoint = listOf("request-reset-password"),
            body = RequestPasswordResetRequest(username = cleanUsername, email = cleanEmail),
        )
    }

    override suspend fun resetPassword(token: String, password: String): AccountRecoveryResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty() || password.isEmpty()) {
            return AccountRecoveryResult.ServerError(400, "重置 token 和新密码不能为空")
        }

        return postAction(
            endpoint = listOf("reset-password"),
            body = ResetPasswordRequest(token = cleanToken, password = password),
        )
    }

    private suspend inline fun <reified B : Any> postAction(
        endpoint: List<String>,
        body: B,
    ): AccountRecoveryResult {
        return try {
            val response = client.post(apiUrl(endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> AccountRecoveryResult.Success
                else -> AccountRecoveryResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AccountRecoveryResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(endpoint: List<String>): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint.toTypedArray())
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

private fun defaultAccountRecoveryClient(): HttpClient {
    return HttpClient {
        installDefaultHttpTimeouts()
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}

@Serializable
private data class RequestPasswordResetRequest(
    val username: String,
    val email: String,
)

@Serializable
private data class ResetPasswordRequest(
    val token: String,
    val password: String,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}
