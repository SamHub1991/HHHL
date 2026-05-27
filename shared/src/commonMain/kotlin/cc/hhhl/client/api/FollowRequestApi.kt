package cc.hhhl.client.api

import cc.hhhl.client.model.FollowRequest
import cc.hhhl.client.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

interface FollowRequestApi {
    suspend fun loadReceived(
        token: String,
        limit: Int,
        untilId: String? = null,
    ): FollowRequestLoadResult

    suspend fun accept(
        token: String,
        userId: String,
    ): FollowRequestActionResult

    suspend fun reject(
        token: String,
        userId: String,
    ): FollowRequestActionResult
}

sealed interface FollowRequestLoadResult {
    data class Success(val requests: List<FollowRequest>) : FollowRequestLoadResult

    data object Unauthorized : FollowRequestLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : FollowRequestLoadResult

    data class NetworkError(val message: String) : FollowRequestLoadResult
}

sealed interface FollowRequestActionResult {
    data object Success : FollowRequestActionResult

    data object Unauthorized : FollowRequestActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : FollowRequestActionResult

    data class NetworkError(val message: String) : FollowRequestActionResult
}

class SharkeyFollowRequestApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultFollowRequestClient(),
) : FollowRequestApi {
    override suspend fun loadReceived(
        token: String,
        limit: Int,
        untilId: String?,
    ): FollowRequestLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return FollowRequestLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("following", "requests", "list")) {
                contentType(ContentType.Application.Json)
                setBody(
                    FollowRequestListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return FollowRequestLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> FollowRequestLoadResult.Success(
                    response.body<List<FollowRequestDto>>().map { it.toDomainRequest() },
                )
                HttpStatusCode.Unauthorized -> FollowRequestLoadResult.Unauthorized
                else -> FollowRequestLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            FollowRequestLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun accept(
        token: String,
        userId: String,
    ): FollowRequestActionResult {
        return postAction(arrayOf("following", "requests", "accept"), token, userId)
    }

    override suspend fun reject(
        token: String,
        userId: String,
    ): FollowRequestActionResult {
        return postAction(arrayOf("following", "requests", "reject"), token, userId)
    }

    private suspend fun postAction(
        endpoint: Array<String>,
        token: String,
        userId: String,
    ): FollowRequestActionResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) return FollowRequestActionResult.Unauthorized
        if (cleanUserId.isEmpty()) {
            return FollowRequestActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择用户",
            )
        }

        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(FollowRequestActionRequest(i = cleanToken, userId = cleanUserId))
            }

            when {
                response.status.value in 200..299 -> FollowRequestActionResult.Success
                response.isSharkeyUnauthorized() -> FollowRequestActionResult.Unauthorized
                else -> FollowRequestActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            FollowRequestActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

@Serializable
private data class FollowRequestListRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class FollowRequestActionRequest(
    val i: String,
    val userId: String,
)

@Serializable
private data class FollowRequestDto(
    val id: String,
    val follower: FollowRequestUserDto,
) {
    fun toDomainRequest(): FollowRequest {
        return FollowRequest(
            id = id,
            user = follower.toDomainUser(),
        )
    }
}

@Serializable
private data class FollowRequestUserDto(
    val id: String,
    val name: String? = null,
    val username: String = "",
    val host: String? = null,
    val avatarUrl: String? = null,
) {
    fun toDomainUser(): User {
        val displayName = name?.takeIf { it.isNotBlank() } ?: username.ifBlank { id }
        return User(
            id = id,
            displayName = displayName,
            username = if (host.isNullOrBlank()) username else "$username@$host",
            avatarInitial = displayName.firstOrNull()?.uppercase() ?: "?",
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        )
    }
}

@Serializable
private data class FollowRequestErrorEnvelope(
    val error: FollowRequestErrorDto? = null,
)

@Serializable
private data class FollowRequestErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultFollowRequestClient(): HttpClient {
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
