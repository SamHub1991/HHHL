package cc.hhhl.client.repository

import cc.hhhl.client.api.ClipApi
import cc.hhhl.client.api.ClipActionResult
import cc.hhhl.client.api.ClipCreateResult
import cc.hhhl.client.api.ClipLoadResult
import cc.hhhl.client.api.ClipNotesLoadResult
import cc.hhhl.client.api.ClipUpdateResult
import cc.hhhl.client.api.SharkeyClipApi
import cc.hhhl.client.model.Clip
import cc.hhhl.client.model.ClipListKind
import cc.hhhl.client.model.Note

open class ClipRepository(
    private val tokenProvider: () -> String?,
    private val api: ClipApi = SharkeyClipApi(),
) {
    open suspend fun refreshClips(kind: ClipListKind): ClipsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ClipsRepositoryResult.Unauthorized

        return when (val result = api.loadClips(token, kind)) {
            is ClipLoadResult.Success -> ClipsRepositoryResult.Success(result.clips)
            ClipLoadResult.Unauthorized -> ClipsRepositoryResult.Unauthorized
            is ClipLoadResult.NetworkError -> {
                ClipsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ClipLoadResult.ServerError -> ClipsRepositoryResult.Error(result.message)
        }
    }

    open suspend fun refreshNotes(clipId: String): ClipNotesRepositoryResult {
        return loadNotes(
            clipId = clipId,
            currentNotes = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMoreNotes(
        clipId: String,
        currentNotes: List<Note>,
    ): ClipNotesRepositoryResult {
        return loadNotes(
            clipId = clipId,
            currentNotes = currentNotes,
            untilId = currentNotes.lastOrNull()?.id,
        )
    }

    open suspend fun favoriteClip(clipId: String): ClipActionRepositoryResult {
        return performClipAction(clipId) { token, cleanClipId ->
            api.favoriteClip(token, cleanClipId)
        }
    }

    open suspend fun createClip(
        name: String,
        description: String,
        isPublic: Boolean,
    ): ClipCreateRepositoryResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return ClipCreateRepositoryResult.Error("请输入剪辑名称")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ClipCreateRepositoryResult.Unauthorized

        return when (
            val result = api.createClip(
                token = token,
                name = cleanName,
                description = description.trim(),
                isPublic = isPublic,
            )
        ) {
            is ClipCreateResult.Success -> ClipCreateRepositoryResult.Success(result.clip)
            ClipCreateResult.Unauthorized -> ClipCreateRepositoryResult.Unauthorized
            is ClipCreateResult.NetworkError -> {
                ClipCreateRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ClipCreateResult.ServerError -> ClipCreateRepositoryResult.Error(result.message)
        }
    }

    open suspend fun unfavoriteClip(clipId: String): ClipActionRepositoryResult {
        return performClipAction(clipId) { token, cleanClipId ->
            api.unfavoriteClip(token, cleanClipId)
        }
    }

    open suspend fun updateClip(
        clipId: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): ClipUpdateRepositoryResult {
        val cleanClipId = clipId.trim()
        val cleanName = name.trim()
        if (cleanClipId.isEmpty()) {
            return ClipUpdateRepositoryResult.Error("无法读取剪辑")
        }
        if (cleanName.isEmpty()) {
            return ClipUpdateRepositoryResult.Error("请输入剪辑名称")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ClipUpdateRepositoryResult.Unauthorized

        return when (
            val result = api.updateClip(
                token = token,
                clipId = cleanClipId,
                name = cleanName,
                description = description.trim(),
                isPublic = isPublic,
            )
        ) {
            is ClipUpdateResult.Success -> ClipUpdateRepositoryResult.Success(result.clip)
            ClipUpdateResult.Unauthorized -> ClipUpdateRepositoryResult.Unauthorized
            is ClipUpdateResult.NetworkError -> {
                ClipUpdateRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ClipUpdateResult.ServerError -> ClipUpdateRepositoryResult.Error(result.message)
        }
    }

    open suspend fun deleteClip(clipId: String): ClipActionRepositoryResult {
        return performClipAction(clipId) { token, cleanClipId ->
            api.deleteClip(token, cleanClipId)
        }
    }

    open suspend fun addNoteToClip(
        clipId: String,
        noteId: String,
    ): ClipActionRepositoryResult {
        return performClipNoteAction(clipId, noteId) { token, cleanClipId, cleanNoteId ->
            api.addNoteToClip(token, cleanClipId, cleanNoteId)
        }
    }

    open suspend fun removeNoteFromClip(
        clipId: String,
        noteId: String,
    ): ClipActionRepositoryResult {
        return performClipNoteAction(clipId, noteId) { token, cleanClipId, cleanNoteId ->
            api.removeNoteFromClip(token, cleanClipId, cleanNoteId)
        }
    }

    private suspend fun loadNotes(
        clipId: String,
        currentNotes: List<Note>,
        untilId: String?,
    ): ClipNotesRepositoryResult {
        val cleanClipId = clipId.trim()
        if (cleanClipId.isEmpty()) {
            return ClipNotesRepositoryResult.Error("无法读取剪辑")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ClipNotesRepositoryResult.Unauthorized

        return when (
            val result = api.loadClipNotes(
                token = token,
                clipId = cleanClipId,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
            )
        ) {
            is ClipNotesLoadResult.Success -> ClipNotesRepositoryResult.Success(
                notes = currentNotes.appendDistinctBy(result.notes) { it.id },
                endReached = result.notes.isEmpty(),
            )
            ClipNotesLoadResult.Unauthorized -> ClipNotesRepositoryResult.Unauthorized
            is ClipNotesLoadResult.NetworkError -> {
                ClipNotesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ClipNotesLoadResult.ServerError -> ClipNotesRepositoryResult.Error(result.message)
        }
    }

    private suspend fun performClipAction(
        clipId: String,
        action: suspend (String, String) -> ClipActionResult,
    ): ClipActionRepositoryResult {
        val cleanClipId = clipId.trim()
        if (cleanClipId.isEmpty()) {
            return ClipActionRepositoryResult.Error("无法读取剪辑")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ClipActionRepositoryResult.Unauthorized

        return when (val result = action(token, cleanClipId)) {
            ClipActionResult.Success -> ClipActionRepositoryResult.Success
            ClipActionResult.Unauthorized -> ClipActionRepositoryResult.Unauthorized
            is ClipActionResult.NetworkError -> {
                ClipActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ClipActionResult.ServerError -> ClipActionRepositoryResult.Error(result.message)
        }
    }

    private suspend fun performClipNoteAction(
        clipId: String,
        noteId: String,
        action: suspend (String, String, String) -> ClipActionResult,
    ): ClipActionRepositoryResult {
        val cleanClipId = clipId.trim()
        val cleanNoteId = noteId.trim()
        if (cleanClipId.isEmpty()) {
            return ClipActionRepositoryResult.Error("无法读取剪辑")
        }
        if (cleanNoteId.isEmpty()) {
            return ClipActionRepositoryResult.Error("无法读取动态")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ClipActionRepositoryResult.Unauthorized

        return when (val result = action(token, cleanClipId, cleanNoteId)) {
            ClipActionResult.Success -> ClipActionRepositoryResult.Success
            ClipActionResult.Unauthorized -> ClipActionRepositoryResult.Unauthorized
            is ClipActionResult.NetworkError -> {
                ClipActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ClipActionResult.ServerError -> ClipActionRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface ClipsRepositoryResult {
    data class Success(val clips: List<Clip>) : ClipsRepositoryResult

    data object Unauthorized : ClipsRepositoryResult

    data class Error(val message: String) : ClipsRepositoryResult
}

sealed interface ClipNotesRepositoryResult {
    data class Success(
        val notes: List<Note>,
        val endReached: Boolean = false,
    ) : ClipNotesRepositoryResult

    data object Unauthorized : ClipNotesRepositoryResult

    data class Error(val message: String) : ClipNotesRepositoryResult
}

sealed interface ClipActionRepositoryResult {
    data object Success : ClipActionRepositoryResult

    data object Unauthorized : ClipActionRepositoryResult

    data class Error(val message: String) : ClipActionRepositoryResult
}

sealed interface ClipCreateRepositoryResult {
    data class Success(val clip: Clip) : ClipCreateRepositoryResult

    data object Unauthorized : ClipCreateRepositoryResult

    data class Error(val message: String) : ClipCreateRepositoryResult
}

sealed interface ClipUpdateRepositoryResult {
    data class Success(val clip: Clip) : ClipUpdateRepositoryResult

    data object Unauthorized : ClipUpdateRepositoryResult

    data class Error(val message: String) : ClipUpdateRepositoryResult
}
