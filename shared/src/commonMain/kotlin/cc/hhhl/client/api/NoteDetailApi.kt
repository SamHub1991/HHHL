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

interface NoteDetailApi {
    suspend fun loadNote(
        token: String,
        noteId: String,
    ): NoteDetailLoadResult
}

sealed interface NoteDetailLoadResult {
    data class Success(val note: Note) : NoteDetailLoadResult

    data object Unauthorized : NoteDetailLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : NoteDetailLoadResult

    data class NetworkError(val message: String) : NoteDetailLoadResult
}

class SharkeyNoteDetailApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultNoteDetailClient(),
) : NoteDetailApi {
    override suspend fun loadNote(
        token: String,
        noteId: String,
    ): NoteDetailLoadResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteDetailLoadResult.Unauthorized
        if (cleanNoteId.isEmpty()) {
            return NoteDetailLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择帖子",
            )
        }

        return try {
            val response = client.post(apiUrl("notes", "show")) {
                contentType(ContentType.Application.Json)
                setBody(NoteDetailRequest(i = cleanToken, noteId = cleanNoteId))
            }

            when (response.status) {
                HttpStatusCode.OK -> NoteDetailLoadResult.Success(
                    response.body<SharkeyNoteDto>().toDomainNote(),
                )
                HttpStatusCode.Unauthorized -> NoteDetailLoadResult.Unauthorized
                else -> NoteDetailLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteDetailLoadResult.NetworkError(error.message ?: "网络请求失败")
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
private data class NoteDetailRequest(
    val i: String,
    val noteId: String,
)

private fun defaultNoteDetailClient(): HttpClient {
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
