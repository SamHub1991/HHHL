package cc.hhhl.client.repository

import cc.hhhl.client.api.NoteDetailApi
import cc.hhhl.client.api.NoteDetailLoadResult
import cc.hhhl.client.api.SharkeyNoteDetailApi
import cc.hhhl.client.model.Note

open class NoteDetailRepository(
    private val tokenProvider: () -> String?,
    private val api: NoteDetailApi = SharkeyNoteDetailApi(),
) {
    open suspend fun load(noteId: String): NoteDetailRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteDetailRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteDetailRepositoryResult.Error("无法打开帖子")

        return when (val result = api.loadNote(token, cleanNoteId)) {
            is NoteDetailLoadResult.Success -> NoteDetailRepositoryResult.Success(result.note)
            NoteDetailLoadResult.Unauthorized -> NoteDetailRepositoryResult.Unauthorized
            is NoteDetailLoadResult.NetworkError -> {
                NoteDetailRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is NoteDetailLoadResult.ServerError -> NoteDetailRepositoryResult.Error(result.message)
        }
    }
}

sealed interface NoteDetailRepositoryResult {
    data class Success(val note: Note) : NoteDetailRepositoryResult

    data object Unauthorized : NoteDetailRepositoryResult

    data class Error(val message: String) : NoteDetailRepositoryResult
}
