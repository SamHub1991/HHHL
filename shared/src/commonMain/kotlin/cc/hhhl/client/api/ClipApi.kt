package cc.hhhl.client.api

import cc.hhhl.client.model.Clip
import cc.hhhl.client.model.ClipListKind
import cc.hhhl.client.model.Note
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

interface ClipApi {
    suspend fun loadClips(
        token: String,
        kind: ClipListKind,
    ): ClipLoadResult

    suspend fun loadClipNotes(
        token: String,
        clipId: String,
        limit: Int,
        untilId: String? = null,
    ): ClipNotesLoadResult

    suspend fun createClip(
        token: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): ClipCreateResult

    suspend fun updateClip(
        token: String,
        clipId: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): ClipUpdateResult

    suspend fun deleteClip(
        token: String,
        clipId: String,
    ): ClipActionResult

    suspend fun favoriteClip(
        token: String,
        clipId: String,
    ): ClipActionResult

    suspend fun unfavoriteClip(
        token: String,
        clipId: String,
    ): ClipActionResult

    suspend fun addNoteToClip(
        token: String,
        clipId: String,
        noteId: String,
    ): ClipActionResult

    suspend fun removeNoteFromClip(
        token: String,
        clipId: String,
        noteId: String,
    ): ClipActionResult
}

sealed interface ClipLoadResult {
    data class Success(val clips: List<Clip>) : ClipLoadResult

    data object Unauthorized : ClipLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ClipLoadResult

    data class NetworkError(val message: String) : ClipLoadResult
}

sealed interface ClipNotesLoadResult {
    data class Success(val notes: List<Note>) : ClipNotesLoadResult

    data object Unauthorized : ClipNotesLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ClipNotesLoadResult

    data class NetworkError(val message: String) : ClipNotesLoadResult
}

sealed interface ClipCreateResult {
    data class Success(val clip: Clip) : ClipCreateResult

    data object Unauthorized : ClipCreateResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ClipCreateResult

    data class NetworkError(val message: String) : ClipCreateResult
}

sealed interface ClipUpdateResult {
    data class Success(val clip: Clip) : ClipUpdateResult

    data object Unauthorized : ClipUpdateResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ClipUpdateResult

    data class NetworkError(val message: String) : ClipUpdateResult
}

sealed interface ClipActionResult {
    data object Success : ClipActionResult

    data object Unauthorized : ClipActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ClipActionResult

    data class NetworkError(val message: String) : ClipActionResult
}

class SharkeyClipApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultClipClient(),
) : ClipApi {
    override suspend fun loadClips(
        token: String,
        kind: ClipListKind,
    ): ClipLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ClipLoadResult.Unauthorized

        return try {
            val endpoint = when (kind) {
                ClipListKind.Owned -> arrayOf("clips", "list")
                ClipListKind.Favorites -> arrayOf("clips", "my-favorites")
            }
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(ClipsRequest(i = cleanToken))
            }

            if (response.isSharkeyUnauthorized()) return ClipLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ClipLoadResult.Success(
                    response.body<List<ClipDto>>().map { it.toDomainClip() },
                )
                HttpStatusCode.Unauthorized -> ClipLoadResult.Unauthorized
                else -> ClipLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ClipLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadClipNotes(
        token: String,
        clipId: String,
        limit: Int,
        untilId: String?,
    ): ClipNotesLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ClipNotesLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("clips", "notes")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ClipNotesRequest(
                        i = cleanToken,
                        clipId = clipId.trim(),
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ClipNotesLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ClipNotesLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> ClipNotesLoadResult.Unauthorized
                else -> ClipNotesLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ClipNotesLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun favoriteClip(
        token: String,
        clipId: String,
    ): ClipActionResult {
        return postClipAction(token, clipId, "favorite")
    }

    override suspend fun createClip(
        token: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): ClipCreateResult {
        val cleanToken = token.trim()
        val cleanName = name.trim()
        if (cleanToken.isEmpty()) return ClipCreateResult.Unauthorized

        return try {
            val response = client.post(apiUrl("clips", "create")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ClipCreateRequest(
                        i = cleanToken,
                        name = cleanName,
                        description = description.trim(),
                        isPublic = isPublic,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ClipCreateResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ClipCreateResult.Success(
                    response.body<ClipDto>().toDomainClip(),
                )
                HttpStatusCode.Unauthorized -> ClipCreateResult.Unauthorized
                else -> ClipCreateResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ClipCreateResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun unfavoriteClip(
        token: String,
        clipId: String,
    ): ClipActionResult {
        return postClipAction(token, clipId, "unfavorite")
    }

    override suspend fun updateClip(
        token: String,
        clipId: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): ClipUpdateResult {
        val cleanToken = token.trim()
        val cleanClipId = clipId.trim()
        val cleanName = name.trim()
        if (cleanToken.isEmpty()) return ClipUpdateResult.Unauthorized
        if (cleanClipId.isEmpty()) {
            return ClipUpdateResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择剪辑",
            )
        }

        return try {
            val response = client.post(apiUrl("clips", "update")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ClipUpdateRequest(
                        i = cleanToken,
                        clipId = cleanClipId,
                        name = cleanName,
                        description = description.trim(),
                        isPublic = isPublic,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ClipUpdateResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ClipUpdateResult.Success(
                    response.body<ClipDto>().toDomainClip(),
                )
                HttpStatusCode.Unauthorized -> ClipUpdateResult.Unauthorized
                else -> ClipUpdateResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ClipUpdateResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun deleteClip(
        token: String,
        clipId: String,
    ): ClipActionResult {
        return postClipAction(token, clipId, "delete")
    }

    override suspend fun addNoteToClip(
        token: String,
        clipId: String,
        noteId: String,
    ): ClipActionResult {
        return postClipNoteAction(token, clipId, noteId, "add-note")
    }

    override suspend fun removeNoteFromClip(
        token: String,
        clipId: String,
        noteId: String,
    ): ClipActionResult {
        return postClipNoteAction(token, clipId, noteId, "remove-note")
    }

    private suspend fun postClipAction(
        token: String,
        clipId: String,
        action: String,
    ): ClipActionResult {
        val cleanToken = token.trim()
        val cleanClipId = clipId.trim()
        if (cleanToken.isEmpty()) return ClipActionResult.Unauthorized
        if (cleanClipId.isEmpty()) {
            return ClipActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择剪辑",
            )
        }

        return try {
            val response = client.post(apiUrl("clips", action)) {
                contentType(ContentType.Application.Json)
                setBody(ClipActionRequest(i = cleanToken, clipId = cleanClipId))
            }

            when {
                response.status.value in 200..299 -> ClipActionResult.Success
                response.isSharkeyUnauthorized() -> ClipActionResult.Unauthorized
                else -> ClipActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ClipActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postClipNoteAction(
        token: String,
        clipId: String,
        noteId: String,
        action: String,
    ): ClipActionResult {
        val cleanToken = token.trim()
        val cleanClipId = clipId.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return ClipActionResult.Unauthorized
        if (cleanClipId.isEmpty()) {
            return ClipActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择剪辑",
            )
        }
        if (cleanNoteId.isEmpty()) {
            return ClipActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择帖子",
            )
        }

        return try {
            val response = client.post(apiUrl("clips", action)) {
                contentType(ContentType.Application.Json)
                setBody(
                    ClipNoteActionRequest(
                        i = cleanToken,
                        clipId = cleanClipId,
                        noteId = cleanNoteId,
                    ),
                )
            }

            when {
                response.status.value in 200..299 -> ClipActionResult.Success
                response.isSharkeyUnauthorized() -> ClipActionResult.Unauthorized
                else -> ClipActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ClipActionResult.NetworkError(error.message ?: "网络请求失败")
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
private data class ClipsRequest(
    val i: String,
)

@Serializable
private data class ClipNotesRequest(
    val i: String,
    val clipId: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class ClipCreateRequest(
    val i: String,
    val name: String,
    val description: String,
    val isPublic: Boolean,
)

@Serializable
private data class ClipUpdateRequest(
    val i: String,
    val clipId: String,
    val name: String,
    val description: String,
    val isPublic: Boolean,
)

@Serializable
private data class ClipActionRequest(
    val i: String,
    val clipId: String,
)

@Serializable
private data class ClipNoteActionRequest(
    val i: String,
    val clipId: String,
    val noteId: String,
)

@Serializable
private data class ClipDto(
    val id: String,
    val createdAt: String = "",
    val lastClippedAt: String = "",
    val userId: String = "",
    val user: SharkeyUserSummaryDto,
    val name: String = "",
    val description: String? = null,
    val isPublic: Boolean = false,
    val favoritedCount: Int = 0,
    val isFavorited: Boolean = false,
    val notesCount: Int = 0,
) {
    fun toDomainClip(): Clip {
        return Clip(
            id = id,
            name = name,
            description = description.orEmpty(),
            owner = user.toDomainUser(),
            ownerId = userId.ifBlank { user.id },
            isPublic = isPublic,
            isFavorited = isFavorited,
            favoritedCount = favoritedCount,
            notesCount = notesCount,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            lastClippedAtLabel = lastClippedAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class ClipErrorEnvelope(
    val error: ClipErrorDto? = null,
)

@Serializable
private data class ClipErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}


private fun defaultClipClient(): HttpClient {
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
