package cc.hhhl.client.api

import cc.hhhl.client.model.Note
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

interface UserNotesApi {
    suspend fun loadUserNotes(
        token: String,
        userId: String,
        limit: Int,
        untilId: String? = null,
    ): UserNotesLoadResult
}

sealed interface UserNotesLoadResult {
    data class Success(val notes: List<Note>) : UserNotesLoadResult

    data object Unauthorized : UserNotesLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserNotesLoadResult

    data class NetworkError(val message: String) : UserNotesLoadResult
}

class SharkeyUserNotesApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultUserNotesClient(),
) : UserNotesApi {
    override suspend fun loadUserNotes(
        token: String,
        userId: String,
        limit: Int,
        untilId: String?,
    ): UserNotesLoadResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) return UserNotesLoadResult.Unauthorized
        if (cleanUserId.isEmpty()) {
            return UserNotesLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择用户",
            )
        }

        return try {
            val response = client.post(apiUrl("users", "notes")) {
                contentType(ContentType.Application.Json)
                setBody(
                    UserNotesRequest(
                        i = cleanToken,
                        userId = cleanUserId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> UserNotesLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> UserNotesLoadResult.Unauthorized
                else -> UserNotesLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserNotesLoadResult.NetworkError(error.message ?: "网络请求失败")
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

private suspend fun io.ktor.client.statement.HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

@Serializable
private data class UserNotesRequest(
    val i: String,
    val userId: String,
    val limit: Int,
    val untilId: String? = null,
)

private fun defaultUserNotesClient(): HttpClient {
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
