package cc.hhhl.client.api

import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.NoteVisibility
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

@Serializable
data class ComposeDraft(
    val text: String = "",
    val editId: String? = null,
    val visibility: NoteVisibility = NoteVisibility.Public,
    val visibleUserIds: List<String> = emptyList(),
    val cw: String? = null,
    val replyId: String? = null,
    val renoteId: String? = null,
    val channelId: String? = null,
    val fileIds: List<String> = emptyList(),
    val poll: ComposePollDraft? = null,
    val localOnly: Boolean = false,
    val reactionAcceptance: ComposeReactionAcceptance = ComposeReactionAcceptance.NonSensitiveOnly,
    val scheduleNote: ComposeScheduleDraft? = null,
)

@Serializable
data class ComposePollDraft(
    val choices: List<String> = listOf("", ""),
    val multiple: Boolean = false,
    val expiresAt: String? = null,
)

@Serializable
data class ComposeScheduleDraft(
    val scheduledAt: Long,
)

enum class ComposeReactionAcceptance {
    LikeOnly,
    LikeOnlyForRemote,
    NonSensitiveOnly,
    NonSensitiveOnlyForLocalLikeOnlyForRemote,
}

interface ComposeApi {
    suspend fun createNote(
        token: String,
        draft: ComposeDraft,
    ): ComposeCreateResult

    suspend fun listScheduledNotes(
        token: String,
        limit: Int = 10,
        offset: Int = 0,
    ): ComposeScheduledNotesResult

    suspend fun deleteScheduledNote(
        token: String,
        noteId: String,
    ): ComposeScheduleDeleteResult
}

sealed interface ComposeCreateResult {
    data class Success(val createdNoteId: String?) : ComposeCreateResult

    data object Unauthorized : ComposeCreateResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ComposeCreateResult

    data class NetworkError(val message: String) : ComposeCreateResult
}

sealed interface ComposeScheduledNotesResult {
    data class Success(val notes: List<ComposeScheduledNote>) : ComposeScheduledNotesResult

    data object Unauthorized : ComposeScheduledNotesResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ComposeScheduledNotesResult

    data class NetworkError(val message: String) : ComposeScheduledNotesResult
}

sealed interface ComposeScheduleDeleteResult {
    data object Success : ComposeScheduleDeleteResult

    data object Unauthorized : ComposeScheduleDeleteResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ComposeScheduleDeleteResult

    data class NetworkError(val message: String) : ComposeScheduleDeleteResult
}

data class ComposeScheduledNote(
    val id: String,
    val text: String,
    val cw: String?,
    val scheduledAt: Long?,
    val visibility: NoteVisibility,
    val visibleUserIds: List<String> = emptyList(),
    val replyId: String? = null,
    val renoteId: String? = null,
    val channelId: String? = null,
    val fileIds: List<String> = emptyList(),
    val attachedFiles: List<DriveFile> = emptyList(),
    val poll: ComposePollDraft? = null,
    val localOnly: Boolean = false,
    val reactionAcceptance: ComposeReactionAcceptance = ComposeReactionAcceptance.NonSensitiveOnly,
)

class SharkeyComposeApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultComposeClient(),
) : ComposeApi {
    override suspend fun createNote(
        token: String,
        draft: ComposeDraft,
    ): ComposeCreateResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ComposeCreateResult.Unauthorized

        return try {
            val response = client.post(
                if (draft.editId != null) {
                    apiUrl("notes", "edit")
                } else if (draft.scheduleNote != null) {
                    apiUrl("notes", "schedule", "create")
                } else {
                    apiUrl("notes", "create")
                },
            ) {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateNoteRequest(
                        i = cleanToken,
                        text = draft.text.trim().takeIf { it.isNotEmpty() },
                        editId = draft.editId?.trim()?.takeIf { it.isNotEmpty() },
                        visibility = draft.visibility.toApiValue(),
                        visibleUserIds = draft.visibleUserIds
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .takeIf { draft.visibility == NoteVisibility.Specified && it.isNotEmpty() },
                        cw = draft.cw?.trim()?.takeIf { it.isNotEmpty() },
                        replyId = draft.replyId?.trim()?.takeIf { it.isNotEmpty() },
                        renoteId = draft.renoteId?.trim()?.takeIf { it.isNotEmpty() },
                        channelId = draft.channelId?.trim()?.takeIf { it.isNotEmpty() },
                        fileIds = draft.fileIds
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .takeIf { it.isNotEmpty() },
                        poll = draft.poll?.toRequest(),
                        localOnly = draft.localOnly,
                        reactionAcceptance = draft.reactionAcceptance.toApiValue(),
                        scheduleNote = draft.scheduleNote?.let { CreateScheduleRequest(it.scheduledAt) },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ComposeCreateResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> {
                    val body = response.body<CreateNoteResponse>()
                    ComposeCreateResult.Success(body.createdNote?.id ?: body.id)
                }
                HttpStatusCode.NoContent -> ComposeCreateResult.Success(null)
                HttpStatusCode.Unauthorized -> ComposeCreateResult.Unauthorized
                else -> ComposeCreateResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ComposeCreateResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun listScheduledNotes(
        token: String,
        limit: Int,
        offset: Int,
    ): ComposeScheduledNotesResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ComposeScheduledNotesResult.Unauthorized

        return try {
            val response = client.post(apiUrl("notes", "schedule", "list")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ScheduledNotesListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 50),
                        offset = offset.coerceAtLeast(0),
                    ),
                )
            }
            if (response.isSharkeyUnauthorized()) return ComposeScheduledNotesResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ComposeScheduledNotesResult.Success(
                    response.body<List<ScheduledNoteDto>>().map { it.toModel() },
                )
                HttpStatusCode.Unauthorized -> ComposeScheduledNotesResult.Unauthorized
                else -> ComposeScheduledNotesResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ComposeScheduledNotesResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun deleteScheduledNote(
        token: String,
        noteId: String,
    ): ComposeScheduleDeleteResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return ComposeScheduleDeleteResult.Unauthorized
        if (cleanNoteId.isEmpty()) {
            return ComposeScheduleDeleteResult.ServerError(
                statusCode = 400,
                message = "预约帖子不存在",
            )
        }

        return try {
            val response = client.post(apiUrl("notes", "schedule", "delete")) {
                contentType(ContentType.Application.Json)
                setBody(DeleteScheduledNoteRequest(i = cleanToken, noteId = cleanNoteId))
            }
            if (response.isSharkeyUnauthorized()) return ComposeScheduleDeleteResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.NoContent -> ComposeScheduleDeleteResult.Success
                HttpStatusCode.Unauthorized -> ComposeScheduleDeleteResult.Unauthorized
                else -> ComposeScheduleDeleteResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ComposeScheduleDeleteResult.NetworkError(error.message ?: "网络请求失败")
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
private data class CreateNoteRequest(
    val i: String,
    val text: String? = null,
    val editId: String? = null,
    val visibility: String,
    val visibleUserIds: List<String>? = null,
    val cw: String? = null,
    val replyId: String? = null,
    val renoteId: String? = null,
    val channelId: String? = null,
    val fileIds: List<String>? = null,
    val poll: CreatePollRequest? = null,
    val localOnly: Boolean = false,
    val reactionAcceptance: String? = null,
    val scheduleNote: CreateScheduleRequest? = null,
)

@Serializable
private data class CreatePollRequest(
    val choices: List<String>,
    val multiple: Boolean,
    val expiresAt: String? = null,
)

@Serializable
private data class CreateScheduleRequest(
    val scheduledAt: Long,
)

@Serializable
private data class CreateNoteResponse(
    val createdNote: CreatedNoteDto? = null,
    val id: String? = null,
)

@Serializable
private data class ScheduledNotesListRequest(
    val i: String,
    val limit: Int,
    val offset: Int,
)

@Serializable
private data class DeleteScheduledNoteRequest(
    val i: String,
    val noteId: String,
)

@Serializable
private data class ScheduledNoteDto(
    val id: String? = null,
    val note: ScheduledNoteBodyDto? = null,
    val scheduledAt: Long? = null,
)

@Serializable
private data class ScheduledNoteBodyDto(
    val id: String? = null,
    val createdAt: String? = null,
    val text: String? = null,
    val cw: String? = null,
    val visibility: String? = null,
    val visibleUserIds: List<String>? = null,
    val replyId: String? = null,
    val renoteId: String? = null,
    val channelId: String? = null,
    val fileIds: List<String>? = null,
    val files: List<ScheduledDriveFileDto> = emptyList(),
    val poll: ScheduledPollDto? = null,
    val localOnly: Boolean = false,
    val reactionAcceptance: String? = null,
    val scheduleNote: ComposeScheduleDraft? = null,
)

@Serializable
private data class ScheduledDriveFileDto(
    val id: String,
    val name: String? = null,
    val comment: String? = null,
    val type: String? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val isSensitive: Boolean = false,
    val size: Long? = null,
)

@Serializable
private data class ScheduledPollDto(
    val choices: List<ScheduledPollChoiceDto> = emptyList(),
    val multiple: Boolean = false,
    val expiresAt: String? = null,
)

@Serializable
private data class ScheduledPollChoiceDto(
    val text: String = "",
)

@Serializable
private data class CreatedNoteDto(
    val id: String,
)

@Serializable
private data class ComposeErrorEnvelope(
    val error: ComposeErrorDto? = null,
)

@Serializable
private data class ComposeErrorDto(
    val message: String? = null,
)

private fun NoteVisibility.toApiValue(): String {
    return when (this) {
        NoteVisibility.Public -> "public"
        NoteVisibility.Home -> "home"
        NoteVisibility.Followers -> "followers"
        NoteVisibility.Specified -> "specified"
    }
}

private fun String?.toNoteVisibility(): NoteVisibility {
    return when (this) {
        "home" -> NoteVisibility.Home
        "followers" -> NoteVisibility.Followers
        "specified" -> NoteVisibility.Specified
        else -> NoteVisibility.Public
    }
}

private fun ComposeReactionAcceptance.toApiValue(): String {
    return when (this) {
        ComposeReactionAcceptance.LikeOnly -> "likeOnly"
        ComposeReactionAcceptance.LikeOnlyForRemote -> "likeOnlyForRemote"
        ComposeReactionAcceptance.NonSensitiveOnly -> "nonSensitiveOnly"
        ComposeReactionAcceptance.NonSensitiveOnlyForLocalLikeOnlyForRemote -> {
            "nonSensitiveOnlyForLocalLikeOnlyForRemote"
        }
    }
}

private fun String?.toComposeReactionAcceptance(): ComposeReactionAcceptance {
    return when (this) {
        "likeOnly" -> ComposeReactionAcceptance.LikeOnly
        "likeOnlyForRemote" -> ComposeReactionAcceptance.LikeOnlyForRemote
        "nonSensitiveOnlyForLocalLikeOnlyForRemote" -> {
            ComposeReactionAcceptance.NonSensitiveOnlyForLocalLikeOnlyForRemote
        }
        else -> ComposeReactionAcceptance.NonSensitiveOnly
    }
}

private fun ScheduledNoteDto.toModel(): ComposeScheduledNote {
    val body = note
    return ComposeScheduledNote(
        id = body?.id ?: id.orEmpty(),
        text = body?.text.orEmpty(),
        cw = body?.cw,
        scheduledAt = scheduledAt
            ?: body?.scheduleNote?.scheduledAt
            ?: body?.createdAt?.toApiInstantOrNull()?.toEpochMilliseconds(),
        visibility = body?.visibility.toNoteVisibility(),
        visibleUserIds = body?.visibleUserIds.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        replyId = body?.replyId?.trim()?.takeIf { it.isNotEmpty() },
        renoteId = body?.renoteId?.trim()?.takeIf { it.isNotEmpty() },
        channelId = body?.channelId?.trim()?.takeIf { it.isNotEmpty() },
        fileIds = (
            body?.fileIds.orEmpty().map { it.trim() }.filter { it.isNotEmpty() } +
                body?.files.orEmpty().map { it.id.trim() }.filter { it.isNotEmpty() }
        ).distinct(),
        attachedFiles = body?.files.orEmpty().map { it.toModel() },
        poll = body?.poll?.toModel(),
        localOnly = body?.localOnly == true,
        reactionAcceptance = body?.reactionAcceptance.toComposeReactionAcceptance(),
    )
}

private fun ScheduledDriveFileDto.toModel(): DriveFile {
    return DriveFile(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: id,
        type = type.orEmpty(),
        url = url?.takeIf { it.isNotBlank() },
        thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
        comment = comment?.takeIf { it.isNotBlank() },
        size = size ?: 0L,
        isSensitive = isSensitive,
    )
}

private fun ScheduledPollDto.toModel(): ComposePollDraft? {
    val cleanChoices = choices
        .map { it.text.trim() }
        .filter { it.isNotEmpty() }
    return if (cleanChoices.isEmpty()) {
        null
    } else {
        ComposePollDraft(
            choices = cleanChoices,
            multiple = multiple,
            expiresAt = expiresAt?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}

private fun ComposePollDraft.toRequest(): CreatePollRequest? {
    val cleanChoices = choices
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return if (cleanChoices.size >= 2) {
        CreatePollRequest(
            choices = cleanChoices,
            multiple = multiple,
            expiresAt = expiresAt?.trim()?.takeIf { it.isNotEmpty() },
        )
    } else {
        null
    }
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultComposeClient(): HttpClient {
    return HttpClient {
        installDefaultHttpTimeouts()
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
