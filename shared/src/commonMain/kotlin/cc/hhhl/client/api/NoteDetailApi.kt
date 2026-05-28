package cc.hhhl.client.api

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteReactionUser
import cc.hhhl.client.model.NoteState
import cc.hhhl.client.model.NoteTranslation
import cc.hhhl.client.model.NoteVersion
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

    suspend fun loadConversation(token: String, noteId: String): NoteDetailNotesResult =
        NoteDetailNotesResult.ServerError(501, "会话接口未实现")

    suspend fun loadRenotes(token: String, noteId: String, limit: Int = 30): NoteDetailNotesResult =
        NoteDetailNotesResult.ServerError(501, "转发列表接口未实现")

    suspend fun loadReactionUsers(token: String, noteId: String, limit: Int = 40): NoteReactionUsersResult =
        NoteReactionUsersResult.ServerError(501, "回应用户接口未实现")

    suspend fun translate(token: String, noteId: String, targetLang: String = "zh-CN"): NoteTranslationResult =
        NoteTranslationResult.ServerError(501, "翻译接口未实现")

    suspend fun loadState(token: String, noteId: String): NoteStateResult =
        NoteStateResult.ServerError(501, "帖子状态接口未实现")

    suspend fun loadVersions(token: String, noteId: String): NoteVersionsResult =
        NoteVersionsResult.ServerError(501, "历史版本接口未实现")

    suspend fun refreshPollRecommendation(token: String, noteId: String): NoteActionOnlyResult =
        NoteActionOnlyResult.ServerError(501, "投票推荐刷新接口未实现")
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

sealed interface NoteDetailNotesResult {
    data class Success(val notes: List<Note>) : NoteDetailNotesResult
    data object Unauthorized : NoteDetailNotesResult
    data class ServerError(val statusCode: Int, val message: String) : NoteDetailNotesResult
    data class NetworkError(val message: String) : NoteDetailNotesResult
}

sealed interface NoteReactionUsersResult {
    data class Success(val users: List<NoteReactionUser>) : NoteReactionUsersResult
    data object Unauthorized : NoteReactionUsersResult
    data class ServerError(val statusCode: Int, val message: String) : NoteReactionUsersResult
    data class NetworkError(val message: String) : NoteReactionUsersResult
}

sealed interface NoteTranslationResult {
    data class Success(val translation: NoteTranslation) : NoteTranslationResult
    data object Unauthorized : NoteTranslationResult
    data class ServerError(val statusCode: Int, val message: String) : NoteTranslationResult
    data class NetworkError(val message: String) : NoteTranslationResult
}

sealed interface NoteStateResult {
    data class Success(val state: NoteState) : NoteStateResult
    data object Unauthorized : NoteStateResult
    data class ServerError(val statusCode: Int, val message: String) : NoteStateResult
    data class NetworkError(val message: String) : NoteStateResult
}

sealed interface NoteVersionsResult {
    data class Success(val versions: List<NoteVersion>) : NoteVersionsResult
    data object Unauthorized : NoteVersionsResult
    data class ServerError(val statusCode: Int, val message: String) : NoteVersionsResult
    data class NetworkError(val message: String) : NoteVersionsResult
}

sealed interface NoteActionOnlyResult {
    data object Success : NoteActionOnlyResult
    data object Unauthorized : NoteActionOnlyResult
    data class ServerError(val statusCode: Int, val message: String) : NoteActionOnlyResult
    data class NetworkError(val message: String) : NoteActionOnlyResult
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

            if (response.isSharkeyUnauthorized()) return NoteDetailLoadResult.Unauthorized

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

    override suspend fun loadConversation(token: String, noteId: String): NoteDetailNotesResult {
        return postNotes(endpoint = arrayOf("notes", "conversation"), token = token, noteId = noteId)
    }

    override suspend fun loadRenotes(token: String, noteId: String, limit: Int): NoteDetailNotesResult {
        return postNotes(endpoint = arrayOf("notes", "renotes"), token = token, noteId = noteId, limit = limit)
    }

    override suspend fun loadReactionUsers(token: String, noteId: String, limit: Int): NoteReactionUsersResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteReactionUsersResult.Unauthorized
        if (cleanNoteId.isEmpty()) return NoteReactionUsersResult.ServerError(400, "请选择帖子")
        return try {
            val response = client.post(apiUrl("notes", "reactions")) {
                contentType(ContentType.Application.Json)
                setBody(NoteDetailListRequest(i = cleanToken, noteId = cleanNoteId, limit = limit.coerceIn(1, 100)))
            }
            if (response.isSharkeyUnauthorized()) return NoteReactionUsersResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> NoteReactionUsersResult.Success(
                    response.body<List<NoteReactionUserDto>>().mapNotNull { it.toDomain() },
                )
                HttpStatusCode.Unauthorized -> NoteReactionUsersResult.Unauthorized
                else -> NoteReactionUsersResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteReactionUsersResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun translate(token: String, noteId: String, targetLang: String): NoteTranslationResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteTranslationResult.Unauthorized
        if (cleanNoteId.isEmpty()) return NoteTranslationResult.ServerError(400, "请选择帖子")
        return try {
            val response = client.post(apiUrl("notes", "translate")) {
                contentType(ContentType.Application.Json)
                setBody(NoteTranslateRequest(i = cleanToken, noteId = cleanNoteId, targetLang = targetLang.trim().ifBlank { "zh-CN" }))
            }
            if (response.isSharkeyUnauthorized()) return NoteTranslationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> NoteTranslationResult.Success(response.body<NoteTranslationDto>().toDomain())
                HttpStatusCode.Unauthorized -> NoteTranslationResult.Unauthorized
                else -> NoteTranslationResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteTranslationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadState(token: String, noteId: String): NoteStateResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteStateResult.Unauthorized
        if (cleanNoteId.isEmpty()) return NoteStateResult.ServerError(400, "请选择帖子")
        return try {
            val response = client.post(apiUrl("notes", "state")) {
                contentType(ContentType.Application.Json)
                setBody(NoteDetailRequest(i = cleanToken, noteId = cleanNoteId))
            }
            if (response.isSharkeyUnauthorized()) return NoteStateResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> NoteStateResult.Success(response.body<NoteStateDto>().toDomain())
                HttpStatusCode.Unauthorized -> NoteStateResult.Unauthorized
                else -> NoteStateResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteStateResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadVersions(token: String, noteId: String): NoteVersionsResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteVersionsResult.Unauthorized
        if (cleanNoteId.isEmpty()) return NoteVersionsResult.ServerError(400, "请选择帖子")
        return try {
            val response = client.post(apiUrl("notes", "versions")) {
                contentType(ContentType.Application.Json)
                setBody(NoteDetailRequest(i = cleanToken, noteId = cleanNoteId))
            }
            if (response.isSharkeyUnauthorized()) return NoteVersionsResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> NoteVersionsResult.Success(
                    response.body<List<NoteVersionDto>>().map { it.toDomain() },
                )
                HttpStatusCode.Unauthorized -> NoteVersionsResult.Unauthorized
                else -> NoteVersionsResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteVersionsResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun refreshPollRecommendation(token: String, noteId: String): NoteActionOnlyResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteActionOnlyResult.Unauthorized
        if (cleanNoteId.isEmpty()) return NoteActionOnlyResult.ServerError(400, "请选择帖子")
        return try {
            val response = client.post(apiUrl("notes", "polls", "refresh")) {
                contentType(ContentType.Application.Json)
                setBody(NoteDetailRequest(i = cleanToken, noteId = cleanNoteId))
            }
            when {
                response.status.value in 200..299 -> NoteActionOnlyResult.Success
                response.isSharkeyUnauthorized() -> NoteActionOnlyResult.Unauthorized
                else -> NoteActionOnlyResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteActionOnlyResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postNotes(
        endpoint: Array<String>,
        token: String,
        noteId: String,
        limit: Int = 30,
    ): NoteDetailNotesResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return NoteDetailNotesResult.Unauthorized
        if (cleanNoteId.isEmpty()) return NoteDetailNotesResult.ServerError(400, "请选择帖子")
        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(NoteDetailListRequest(i = cleanToken, noteId = cleanNoteId, limit = limit.coerceIn(1, 100)))
            }
            if (response.isSharkeyUnauthorized()) return NoteDetailNotesResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> NoteDetailNotesResult.Success(response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() })
                HttpStatusCode.Unauthorized -> NoteDetailNotesResult.Unauthorized
                else -> NoteDetailNotesResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NoteDetailNotesResult.NetworkError(error.message ?: "网络请求失败")
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

@Serializable
private data class NoteDetailListRequest(
    val i: String,
    val noteId: String,
    val limit: Int,
)

@Serializable
private data class NoteTranslateRequest(
    val i: String,
    val noteId: String,
    val targetLang: String,
)

@Serializable
private data class NoteTranslationDto(
    val sourceLang: String? = null,
    val targetLang: String? = null,
    val text: String? = null,
    val translatedText: String? = null,
) {
    fun toDomain(): NoteTranslation = NoteTranslation(
        sourceLang = sourceLang.orEmpty(),
        targetLang = targetLang.orEmpty(),
        text = translatedText?.takeIf { it.isNotBlank() } ?: text.orEmpty(),
    )
}

@Serializable
private data class NoteStateDto(
    val isFavorited: Boolean = false,
    val favorited: Boolean = false,
    val myReaction: String? = null,
    val reaction: String? = null,
    val isMutedThread: Boolean = false,
    val mutedThread: Boolean = false,
    val isMutedNote: Boolean = false,
    val mutedNote: Boolean = false,
    val isRenoteMuted: Boolean = false,
    val renoteMuted: Boolean = false,
    val isRenoted: Boolean = false,
    val renoted: Boolean = false,
) {
    fun toDomain(): NoteState = NoteState(
        isFavorited = isFavorited || favorited,
        myReaction = myReaction?.takeIf { it.isNotBlank() } ?: reaction?.takeIf { it.isNotBlank() },
        isMutedThread = isMutedThread || mutedThread,
        isMutedNote = isMutedNote || mutedNote,
        isRenoteMuted = isRenoteMuted || renoteMuted,
        isRenoted = isRenoted || renoted,
    )
}

@Serializable
private data class NoteReactionUserDto(
    val id: String? = null,
    val type: String? = null,
    val reaction: String? = null,
    val user: SharkeyUserSummaryDto? = null,
) {
    fun toDomain(): NoteReactionUser? {
        val domainUser = user?.toDomainUser() ?: return null
        val domainReaction = reaction?.takeIf { it.isNotBlank() } ?: type?.takeIf { it.isNotBlank() } ?: return null
        return NoteReactionUser(
            id = id?.takeIf { it.isNotBlank() } ?: "${domainUser.id}:$domainReaction",
            reaction = domainReaction,
            user = domainUser,
        )
    }
}

@Serializable
private data class NoteVersionDto(
    val id: String? = null,
    val text: String? = null,
    val cw: String? = null,
    val createdAt: String? = null,
) {
    fun toDomain(): NoteVersion = NoteVersion(
        id = id?.takeIf { it.isNotBlank() } ?: createdAt.orEmpty(),
        text = text.orEmpty(),
        cw = cw?.takeIf { it.isNotBlank() },
        createdAtLabel = createdAt?.toLocalCompactDateLabel().orEmpty(),
    )
}

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
