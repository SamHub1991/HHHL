package cc.hhhl.client.repository

import cc.hhhl.client.api.NoteRepliesApi
import cc.hhhl.client.api.NoteRepliesLoadResult
import cc.hhhl.client.api.SharkeyNoteRepliesApi
import cc.hhhl.client.model.Note

open class NoteRepliesRepository(
    private val tokenProvider: () -> String?,
    private val api: NoteRepliesApi = SharkeyNoteRepliesApi(),
) {
    open suspend fun refresh(noteId: String): NoteRepliesRepositoryResult {
        return load(noteId = noteId, currentReplies = emptyList(), untilId = null)
    }

    open suspend fun loadMore(
        noteId: String,
        currentReplies: List<Note>,
    ): NoteRepliesRepositoryResult {
        return load(
            noteId = noteId,
            currentReplies = currentReplies,
            untilId = currentReplies.lastOrNull()?.id,
        )
    }

    open suspend fun loadChildren(
        noteId: String,
        currentChildren: List<Note> = emptyList(),
    ): NoteRepliesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteRepliesRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteRepliesRepositoryResult.Error("无法读取回复")

        return when (
            val result = api.loadChildren(
                token = token,
                noteId = cleanNoteId,
                limit = CHILD_PAGE_SIZE,
                untilId = currentChildren.lastOrNull()?.id,
            )
        ) {
            is NoteRepliesLoadResult.Success -> NoteRepliesRepositoryResult.Success(
                replies = currentChildren.appendDistinctBy(result.replies) { it.id },
            )
            NoteRepliesLoadResult.Unauthorized -> NoteRepliesRepositoryResult.Unauthorized
            is NoteRepliesLoadResult.NetworkError -> {
                NoteRepliesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is NoteRepliesLoadResult.ServerError -> NoteRepliesRepositoryResult.Error(result.message)
        }
    }

    private suspend fun load(
        noteId: String,
        currentReplies: List<Note>,
        untilId: String?,
    ): NoteRepliesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteRepliesRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteRepliesRepositoryResult.Error("无法读取回复")

        return when (val result = api.loadReplies(token, cleanNoteId, DEFAULT_PAGE_SIZE, untilId)) {
            is NoteRepliesLoadResult.Success -> NoteRepliesRepositoryResult.Success(
                replies = currentReplies.appendDistinctBy(result.replies) { it.id },
            )
            NoteRepliesLoadResult.Unauthorized -> NoteRepliesRepositoryResult.Unauthorized
            is NoteRepliesLoadResult.NetworkError -> {
                NoteRepliesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is NoteRepliesLoadResult.ServerError -> NoteRepliesRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val CHILD_PAGE_SIZE = 12
    }
}

sealed interface NoteRepliesRepositoryResult {
    data class Success(val replies: List<Note>) : NoteRepliesRepositoryResult

    data object Unauthorized : NoteRepliesRepositoryResult

    data class Error(val message: String) : NoteRepliesRepositoryResult
}
