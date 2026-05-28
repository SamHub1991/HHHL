package cc.hhhl.client.api

import io.ktor.client.HttpClient
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

interface NoteActionApi {
    suspend fun createReaction(
        token: String,
        noteId: String,
        reaction: String,
    ): NoteActionApiResult

    suspend fun likeNote(
        token: String,
        noteId: String,
        override: String? = null,
    ): NoteActionApiResult

    suspend fun deleteReaction(
        token: String,
        noteId: String,
    ): NoteActionApiResult

    suspend fun createFavorite(
        token: String,
        noteId: String,
    ): NoteActionApiResult

    suspend fun deleteFavorite(
        token: String,
        noteId: String,
    ): NoteActionApiResult

    suspend fun votePoll(
        token: String,
        noteId: String,
        choice: Int,
    ): NoteActionApiResult

    suspend fun createRenote(
        token: String,
        noteId: String,
    ): NoteActionApiResult

    suspend fun deleteRenote(
        token: String,
        noteId: String,
    ): NoteActionApiResult

    suspend fun deleteNote(
        token: String,
        noteId: String,
    ): NoteActionApiResult

    suspend fun reportNote(
        token: String,
        userId: String,
        noteId: String,
        comment: String,
    ): NoteActionApiResult

    suspend fun muteNote(
        token: String,
        noteId: String,
    ): NoteActionApiResult

    suspend fun unmuteNote(
        token: String,
        noteId: String,
    ): NoteActionApiResult

    suspend fun muteRenotes(
        token: String,
        userId: String,
    ): NoteActionApiResult

    suspend fun unmuteRenotes(
        token: String,
        userId: String,
    ): NoteActionApiResult
}

sealed interface NoteActionApiResult {
    data object Success : NoteActionApiResult

    data object Unauthorized : NoteActionApiResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : NoteActionApiResult

    data class NetworkError(val message: String) : NoteActionApiResult
}

class SharkeyNoteActionApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultNoteActionClient(),
) : NoteActionApi {
    override suspend fun createReaction(
        token: String,
        noteId: String,
        reaction: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "reactions", "create"),
            body = ReactionRequest(i = token.trim(), noteId = noteId.trim(), reaction = reaction),
        )
    }

    override suspend fun likeNote(
        token: String,
        noteId: String,
        override: String?,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "like"),
            body = NoteLikeRequest(
                i = token.trim(),
                noteId = noteId.trim(),
                override = override?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    override suspend fun deleteReaction(
        token: String,
        noteId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "reactions", "delete"),
            body = NoteIdRequest(i = token.trim(), noteId = noteId.trim()),
        )
    }

    override suspend fun createFavorite(
        token: String,
        noteId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "favorites", "create"),
            body = NoteIdRequest(i = token.trim(), noteId = noteId.trim()),
        )
    }

    override suspend fun deleteFavorite(
        token: String,
        noteId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "favorites", "delete"),
            body = NoteIdRequest(i = token.trim(), noteId = noteId.trim()),
        )
    }

    override suspend fun votePoll(
        token: String,
        noteId: String,
        choice: Int,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "polls", "vote"),
            body = PollVoteRequest(i = token.trim(), noteId = noteId.trim(), choice = choice),
        )
    }

    override suspend fun createRenote(
        token: String,
        noteId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "create"),
            body = RenoteRequest(i = token.trim(), renoteId = noteId.trim()),
        )
    }

    override suspend fun deleteRenote(
        token: String,
        noteId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "unrenote"),
            body = NoteIdRequest(i = token.trim(), noteId = noteId.trim()),
        )
    }

    override suspend fun deleteNote(
        token: String,
        noteId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "delete"),
            body = NoteIdRequest(i = token.trim(), noteId = noteId.trim()),
        )
    }

    override suspend fun reportNote(
        token: String,
        userId: String,
        noteId: String,
        comment: String,
    ): NoteActionApiResult {
        val cleanComment = comment.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_REPORT_COMMENT
        return postAction(
            endpoint = arrayOf("users", "report-abuse"),
            body = NoteReportRequest(
                i = token.trim(),
                userId = userId.trim(),
                comment = cleanComment,
                noteIds = listOf(noteId.trim()),
            ),
        )
    }

    override suspend fun muteNote(
        token: String,
        noteId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "thread-muting", "create"),
            body = NoteIdRequest(i = token.trim(), noteId = noteId.trim()),
        )
    }

    override suspend fun unmuteNote(
        token: String,
        noteId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("notes", "thread-muting", "delete"),
            body = NoteIdRequest(i = token.trim(), noteId = noteId.trim()),
        )
    }

    override suspend fun muteRenotes(
        token: String,
        userId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("renote-mute", "create"),
            body = UserIdRequest(i = token.trim(), userId = userId.trim()),
        )
    }

    override suspend fun unmuteRenotes(
        token: String,
        userId: String,
    ): NoteActionApiResult {
        return postAction(
            endpoint = arrayOf("renote-mute", "delete"),
            body = UserIdRequest(i = token.trim(), userId = userId.trim()),
        )
    }

    private suspend inline fun <reified T : Any> postAction(
        endpoint: Array<String>,
        body: T,
    ): NoteActionApiResult {
        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            when {
                response.status.value in 200..299 -> NoteActionApiResult.Success
                response.isSharkeyUnauthorized() -> NoteActionApiResult.Unauthorized
                else -> NoteActionApiResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteActionApiResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
        const val DEFAULT_REPORT_COMMENT = "客户端举报帖子"
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

@Serializable
private data class ReactionRequest(
    val i: String,
    val noteId: String,
    val reaction: String,
)

@Serializable
private data class NoteLikeRequest(
    val i: String,
    val noteId: String,
    val override: String? = null,
)

@Serializable
private data class NoteIdRequest(
    val i: String,
    val noteId: String,
)

@Serializable
private data class UserIdRequest(
    val i: String,
    val userId: String,
)

@Serializable
private data class NoteReportRequest(
    val i: String,
    val userId: String,
    val comment: String,
    val noteIds: List<String>,
)

@Serializable
private data class RenoteRequest(
    val i: String,
    val renoteId: String,
)

@Serializable
private data class PollVoteRequest(
    val i: String,
    val noteId: String,
    val choice: Int,
)

private fun defaultNoteActionClient(): HttpClient {
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
