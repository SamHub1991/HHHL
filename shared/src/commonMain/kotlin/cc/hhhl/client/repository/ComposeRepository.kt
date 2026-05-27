package cc.hhhl.client.repository

import cc.hhhl.client.api.ComposeApi
import cc.hhhl.client.api.ComposeCreateResult
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.ComposeScheduleDeleteResult
import cc.hhhl.client.api.ComposeScheduledNote
import cc.hhhl.client.api.ComposeScheduledNotesResult
import cc.hhhl.client.api.SharkeyComposeApi

open class ComposeRepository(
    private val tokenProvider: () -> String?,
    private val api: ComposeApi = SharkeyComposeApi(),
) {
    open suspend fun send(draft: ComposeDraft): ComposeRepositoryResult {
        if (draft.text.isBlank() && draft.fileIds.isEmpty()) {
            return ComposeRepositoryResult.ValidationError("内容不能为空")
        }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ComposeRepositoryResult.Unauthorized

        return when (val result = api.createNote(token, draft)) {
            is ComposeCreateResult.Success -> ComposeRepositoryResult.Success(result.createdNoteId)
            ComposeCreateResult.Unauthorized -> ComposeRepositoryResult.Unauthorized
            is ComposeCreateResult.NetworkError -> ComposeRepositoryResult.Error("无法连接服务器：${result.message}")
            is ComposeCreateResult.ServerError -> ComposeRepositoryResult.Error(result.message)
        }
    }

    open suspend fun listScheduledNotes(
        limit: Int = 10,
        offset: Int = 0,
    ): ComposeScheduledNotesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ComposeScheduledNotesRepositoryResult.Unauthorized

        return when (val result = api.listScheduledNotes(token, limit, offset)) {
            is ComposeScheduledNotesResult.Success -> ComposeScheduledNotesRepositoryResult.Success(result.notes)
            ComposeScheduledNotesResult.Unauthorized -> ComposeScheduledNotesRepositoryResult.Unauthorized
            is ComposeScheduledNotesResult.NetworkError -> {
                ComposeScheduledNotesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ComposeScheduledNotesResult.ServerError -> ComposeScheduledNotesRepositoryResult.Error(result.message)
        }
    }

    open suspend fun deleteScheduledNote(noteId: String): ComposeScheduleDeleteRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ComposeScheduleDeleteRepositoryResult.Unauthorized

        return when (val result = api.deleteScheduledNote(token, noteId)) {
            ComposeScheduleDeleteResult.Success -> ComposeScheduleDeleteRepositoryResult.Success
            ComposeScheduleDeleteResult.Unauthorized -> ComposeScheduleDeleteRepositoryResult.Unauthorized
            is ComposeScheduleDeleteResult.NetworkError -> {
                ComposeScheduleDeleteRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ComposeScheduleDeleteResult.ServerError -> ComposeScheduleDeleteRepositoryResult.Error(result.message)
        }
    }
}

sealed interface ComposeRepositoryResult {
    data class Success(val createdNoteId: String?) : ComposeRepositoryResult

    data object Unauthorized : ComposeRepositoryResult

    data class ValidationError(val message: String) : ComposeRepositoryResult

    data class Error(val message: String) : ComposeRepositoryResult
}

sealed interface ComposeScheduledNotesRepositoryResult {
    data class Success(val notes: List<ComposeScheduledNote>) : ComposeScheduledNotesRepositoryResult

    data object Unauthorized : ComposeScheduledNotesRepositoryResult

    data class Error(val message: String) : ComposeScheduledNotesRepositoryResult
}

sealed interface ComposeScheduleDeleteRepositoryResult {
    data object Success : ComposeScheduleDeleteRepositoryResult

    data object Unauthorized : ComposeScheduleDeleteRepositoryResult

    data class Error(val message: String) : ComposeScheduleDeleteRepositoryResult
}
