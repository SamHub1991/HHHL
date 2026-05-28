package cc.hhhl.client.repository

import cc.hhhl.client.api.NoteDetailApi
import cc.hhhl.client.api.NoteDetailLoadResult
import cc.hhhl.client.api.NoteDetailNotesResult
import cc.hhhl.client.api.NoteActionOnlyResult
import cc.hhhl.client.api.NoteReactionUsersResult
import cc.hhhl.client.api.NoteStateResult
import cc.hhhl.client.api.NoteTranslationResult
import cc.hhhl.client.api.NoteVersionsResult
import cc.hhhl.client.api.SharkeyNoteDetailApi
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteReactionUser
import cc.hhhl.client.model.NoteState
import cc.hhhl.client.model.NoteTranslation
import cc.hhhl.client.model.NoteVersion

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

    open suspend fun loadConversation(noteId: String): NoteDetailNotesRepositoryResult {
        return loadNotes(noteId) { token, cleanNoteId -> api.loadConversation(token, cleanNoteId) }
    }

    open suspend fun loadRenotes(noteId: String): NoteDetailNotesRepositoryResult {
        return loadNotes(noteId) { token, cleanNoteId -> api.loadRenotes(token, cleanNoteId) }
    }

    open suspend fun loadReactionUsers(noteId: String): NoteReactionUsersRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteReactionUsersRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteReactionUsersRepositoryResult.Error("无法打开帖子")

        return when (val result = api.loadReactionUsers(token, cleanNoteId)) {
            is NoteReactionUsersResult.Success -> NoteReactionUsersRepositoryResult.Success(result.users)
            NoteReactionUsersResult.Unauthorized -> NoteReactionUsersRepositoryResult.Unauthorized
            is NoteReactionUsersResult.NetworkError -> NoteReactionUsersRepositoryResult.Error("无法连接服务器：${result.message}")
            is NoteReactionUsersResult.ServerError -> NoteReactionUsersRepositoryResult.Error(result.message)
        }
    }

    open suspend fun translate(noteId: String): NoteTranslationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteTranslationRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteTranslationRepositoryResult.Error("无法打开帖子")

        return when (val result = api.translate(token, cleanNoteId)) {
            is NoteTranslationResult.Success -> NoteTranslationRepositoryResult.Success(result.translation)
            NoteTranslationResult.Unauthorized -> NoteTranslationRepositoryResult.Unauthorized
            is NoteTranslationResult.NetworkError -> NoteTranslationRepositoryResult.Error("无法连接服务器：${result.message}")
            is NoteTranslationResult.ServerError -> NoteTranslationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun loadState(noteId: String): NoteStateRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteStateRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteStateRepositoryResult.Error("无法打开帖子")

        return when (val result = api.loadState(token, cleanNoteId)) {
            is NoteStateResult.Success -> NoteStateRepositoryResult.Success(result.state)
            NoteStateResult.Unauthorized -> NoteStateRepositoryResult.Unauthorized
            is NoteStateResult.NetworkError -> NoteStateRepositoryResult.Error("无法连接服务器：${result.message}")
            is NoteStateResult.ServerError -> NoteStateRepositoryResult.Error(result.message)
        }
    }

    open suspend fun loadVersions(noteId: String): NoteVersionsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteVersionsRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteVersionsRepositoryResult.Error("无法打开帖子")

        return when (val result = api.loadVersions(token, cleanNoteId)) {
            is NoteVersionsResult.Success -> NoteVersionsRepositoryResult.Success(result.versions)
            NoteVersionsResult.Unauthorized -> NoteVersionsRepositoryResult.Unauthorized
            is NoteVersionsResult.NetworkError -> NoteVersionsRepositoryResult.Error("无法连接服务器：${result.message}")
            is NoteVersionsResult.ServerError -> NoteVersionsRepositoryResult.Error(result.message)
        }
    }

    open suspend fun refreshPollRecommendation(noteId: String): NoteSimpleActionRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteSimpleActionRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteSimpleActionRepositoryResult.Error("无法打开帖子")

        return when (val result = api.refreshPollRecommendation(token, cleanNoteId)) {
            NoteActionOnlyResult.Success -> NoteSimpleActionRepositoryResult.Success
            NoteActionOnlyResult.Unauthorized -> NoteSimpleActionRepositoryResult.Unauthorized
            is NoteActionOnlyResult.NetworkError -> NoteSimpleActionRepositoryResult.Error("无法连接服务器：${result.message}")
            is NoteActionOnlyResult.ServerError -> NoteSimpleActionRepositoryResult.Error(result.message)
        }
    }

    private suspend fun loadNotes(
        noteId: String,
        block: suspend (String, String) -> NoteDetailNotesResult,
    ): NoteDetailNotesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteDetailNotesRepositoryResult.Unauthorized
        val cleanNoteId = noteId.takeIf { it.isNotBlank() }
            ?: return NoteDetailNotesRepositoryResult.Error("无法打开帖子")

        return when (val result = block(token, cleanNoteId)) {
            is NoteDetailNotesResult.Success -> NoteDetailNotesRepositoryResult.Success(result.notes)
            NoteDetailNotesResult.Unauthorized -> NoteDetailNotesRepositoryResult.Unauthorized
            is NoteDetailNotesResult.NetworkError -> NoteDetailNotesRepositoryResult.Error("无法连接服务器：${result.message}")
            is NoteDetailNotesResult.ServerError -> NoteDetailNotesRepositoryResult.Error(result.message)
        }
    }
}

sealed interface NoteDetailRepositoryResult {
    data class Success(val note: Note) : NoteDetailRepositoryResult

    data object Unauthorized : NoteDetailRepositoryResult

    data class Error(val message: String) : NoteDetailRepositoryResult
}

sealed interface NoteDetailNotesRepositoryResult {
    data class Success(val notes: List<Note>) : NoteDetailNotesRepositoryResult
    data object Unauthorized : NoteDetailNotesRepositoryResult
    data class Error(val message: String) : NoteDetailNotesRepositoryResult
}

sealed interface NoteReactionUsersRepositoryResult {
    data class Success(val users: List<NoteReactionUser>) : NoteReactionUsersRepositoryResult
    data object Unauthorized : NoteReactionUsersRepositoryResult
    data class Error(val message: String) : NoteReactionUsersRepositoryResult
}

sealed interface NoteTranslationRepositoryResult {
    data class Success(val translation: NoteTranslation) : NoteTranslationRepositoryResult
    data object Unauthorized : NoteTranslationRepositoryResult
    data class Error(val message: String) : NoteTranslationRepositoryResult
}

sealed interface NoteStateRepositoryResult {
    data class Success(val state: NoteState) : NoteStateRepositoryResult
    data object Unauthorized : NoteStateRepositoryResult
    data class Error(val message: String) : NoteStateRepositoryResult
}

sealed interface NoteVersionsRepositoryResult {
    data class Success(val versions: List<NoteVersion>) : NoteVersionsRepositoryResult
    data object Unauthorized : NoteVersionsRepositoryResult
    data class Error(val message: String) : NoteVersionsRepositoryResult
}

sealed interface NoteSimpleActionRepositoryResult {
    data object Success : NoteSimpleActionRepositoryResult
    data object Unauthorized : NoteSimpleActionRepositoryResult
    data class Error(val message: String) : NoteSimpleActionRepositoryResult
}
