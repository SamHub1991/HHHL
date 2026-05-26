package cc.hhhl.client.repository

import cc.hhhl.client.api.NoteActionApi
import cc.hhhl.client.api.NoteActionApiResult
import cc.hhhl.client.api.SharkeyNoteActionApi

sealed interface NoteActionRequest {
    val noteId: String

    data class React(
        override val noteId: String,
        val reaction: String = DEFAULT_REACTION,
    ) : NoteActionRequest

    data class DeleteReaction(override val noteId: String) : NoteActionRequest

    data class Favorite(override val noteId: String) : NoteActionRequest

    data class Unfavorite(override val noteId: String) : NoteActionRequest

    data class VotePoll(
        override val noteId: String,
        val choice: Int,
    ) : NoteActionRequest

    data class Renote(override val noteId: String) : NoteActionRequest

    data class Delete(override val noteId: String) : NoteActionRequest

    data class Report(
        override val noteId: String,
        val userId: String,
        val comment: String = "客户端举报帖子",
    ) : NoteActionRequest

    companion object {
        const val DEFAULT_REACTION = "❤️"
    }
}

open class NoteActionRepository(
    private val tokenProvider: () -> String?,
    private val api: NoteActionApi = SharkeyNoteActionApi(),
) {
    open suspend fun perform(request: NoteActionRequest): NoteActionRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NoteActionRepositoryResult.Unauthorized
        val noteId = request.noteId.takeIf { it.isNotBlank() }
            ?: return NoteActionRepositoryResult.Error("无法操作帖子")
        val userId = (request as? NoteActionRequest.Report)
            ?.userId
            ?.takeIf { it.isNotBlank() }
            ?: if (request is NoteActionRequest.Report) {
                return NoteActionRepositoryResult.Error("无法举报帖子")
            } else {
                null
            }

        val result = when (request) {
            is NoteActionRequest.React -> api.createReaction(token, noteId, request.reaction)
            is NoteActionRequest.DeleteReaction -> api.deleteReaction(token, noteId)
            is NoteActionRequest.Favorite -> api.createFavorite(token, noteId)
            is NoteActionRequest.Unfavorite -> api.deleteFavorite(token, noteId)
            is NoteActionRequest.VotePoll -> api.votePoll(token, noteId, request.choice)
            is NoteActionRequest.Renote -> api.createRenote(token, noteId)
            is NoteActionRequest.Delete -> api.deleteNote(token, noteId)
            is NoteActionRequest.Report -> api.reportNote(token, userId.orEmpty(), noteId, request.comment)
        }

        return when (result) {
            NoteActionApiResult.Success -> NoteActionRepositoryResult.Success(request.successMessage)
            NoteActionApiResult.Unauthorized -> NoteActionRepositoryResult.Unauthorized
            is NoteActionApiResult.NetworkError -> {
                NoteActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is NoteActionApiResult.ServerError -> NoteActionRepositoryResult.Error(result.message)
        }
    }
}

sealed interface NoteActionRepositoryResult {
    data class Success(val message: String) : NoteActionRepositoryResult

    data object Unauthorized : NoteActionRepositoryResult

    data class Error(val message: String) : NoteActionRepositoryResult
}

private val NoteActionRequest.successMessage: String
    get() = when (this) {
        is NoteActionRequest.React -> "已发送反应"
        is NoteActionRequest.DeleteReaction -> "已取消反应"
        is NoteActionRequest.Favorite -> "已收藏"
        is NoteActionRequest.Unfavorite -> "已取消收藏"
        is NoteActionRequest.VotePoll -> "已投票"
        is NoteActionRequest.Renote -> "已转发"
        is NoteActionRequest.Delete -> "已删除"
        is NoteActionRequest.Report -> "已提交举报"
    }
