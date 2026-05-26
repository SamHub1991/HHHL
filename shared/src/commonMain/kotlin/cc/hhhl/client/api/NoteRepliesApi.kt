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

interface NoteRepliesApi {
    suspend fun loadReplies(
        token: String,
        noteId: String,
        limit: Int,
        untilId: String? = null,
    ): NoteRepliesLoadResult

    suspend fun loadChildren(
        token: String,
        noteId: String,
        limit: Int,
        untilId: String? = null,
    ): NoteRepliesLoadResult
}

sealed interface NoteRepliesLoadResult {
    data class Success(val replies: List<Note>) : NoteRepliesLoadResult

    data object Unauthorized : NoteRepliesLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : NoteRepliesLoadResult

    data class NetworkError(val message: String) : NoteRepliesLoadResult
}

class SharkeyNoteRepliesApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultNoteRepliesClient(),
) : NoteRepliesApi {
    override suspend fun loadReplies(
        token: String,
        noteId: String,
        limit: Int,
        untilId: String?,
    ): NoteRepliesLoadResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteRepliesLoadResult.Unauthorized
        if (cleanNoteId.isEmpty()) {
            return NoteRepliesLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择帖子",
            )
        }

        return try {
            val response = client.post(apiUrl("notes", "replies")) {
                contentType(ContentType.Application.Json)
                setBody(
                    NoteRepliesRequest(
                        i = cleanToken,
                        noteId = cleanNoteId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> NoteRepliesLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> NoteRepliesLoadResult.Unauthorized
                else -> NoteRepliesLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteRepliesLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadChildren(
        token: String,
        noteId: String,
        limit: Int,
        untilId: String?,
    ): NoteRepliesLoadResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteRepliesLoadResult.Unauthorized
        if (cleanNoteId.isEmpty()) {
            return NoteRepliesLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择帖子",
            )
        }

        return try {
            val response = client.post(apiUrl("notes", "children")) {
                contentType(ContentType.Application.Json)
                setBody(
                    NoteChildrenRequest(
                        i = cleanToken,
                        noteId = cleanNoteId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        showQuotes = false,
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> NoteRepliesLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> NoteRepliesLoadResult.Unauthorized
                else -> NoteRepliesLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteRepliesLoadResult.NetworkError(error.message ?: "网络请求失败")
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
private data class NoteRepliesRequest(
    val i: String,
    val noteId: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class NoteChildrenRequest(
    val i: String,
    val noteId: String,
    val limit: Int,
    val untilId: String? = null,
    val showQuotes: Boolean = false,
)

private fun defaultNoteRepliesClient(): HttpClient {
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
