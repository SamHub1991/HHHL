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

    data class Unrenote(override val noteId: String) : NoteActionRequest

    data class Delete(override val noteId: String) : NoteActionRequest

    data class Report(
        override val noteId: String,
        val userId: String,
        val comment: String = "客户端举报帖子",
    ) : NoteActionRequest

    data class Mute(override val noteId: String) : NoteActionRequest

    data class Unmute(override val noteId: String) : NoteActionRequest

    data class MuteRenotes(
        override val noteId: String,
        val userId: String,
    ) : NoteActionRequest

    data class UnmuteRenotes(
        override val noteId: String,
        val userId: String,
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
            ?: (request as? NoteActionRequest.MuteRenotes)
                ?.userId
                ?.takeIf { it.isNotBlank() }
            ?: (request as? NoteActionRequest.UnmuteRenotes)
                ?.userId
                ?.takeIf { it.isNotBlank() }
            ?: if (request is NoteActionRequest.Report) {
                return NoteActionRepositoryResult.Error("无法举报帖子")
            } else if (request is NoteActionRequest.MuteRenotes || request is NoteActionRequest.UnmuteRenotes) {
                return NoteActionRepositoryResult.Error("无法操作转发静音")
            } else {
                null
            }

        val result = when (request) {
            is NoteActionRequest.React -> api.likeNote(token, noteId, request.reaction)
            is NoteActionRequest.DeleteReaction -> api.deleteReaction(token, noteId)
            is NoteActionRequest.Favorite -> api.createFavorite(token, noteId)
            is NoteActionRequest.Unfavorite -> api.deleteFavorite(token, noteId)
            is NoteActionRequest.VotePoll -> api.votePoll(token, noteId, request.choice)
            is NoteActionRequest.Renote -> api.createRenote(token, noteId)
            is NoteActionRequest.Unrenote -> api.deleteRenote(token, noteId)
            is NoteActionRequest.Delete -> api.deleteNote(token, noteId)
            is NoteActionRequest.Report -> api.reportNote(token, userId.orEmpty(), noteId, request.comment)
            is NoteActionRequest.Mute -> api.muteNote(token, noteId)
            is NoteActionRequest.Unmute -> api.unmuteNote(token, noteId)
            is NoteActionRequest.MuteRenotes -> api.muteRenotes(token, userId.orEmpty())
            is NoteActionRequest.UnmuteRenotes -> api.unmuteRenotes(token, userId.orEmpty())
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
        is NoteActionRequest.Unrenote -> "已取消转发"
        is NoteActionRequest.Delete -> "已删除"
        is NoteActionRequest.Report -> "已提交举报"
        is NoteActionRequest.Mute -> "已静音帖子"
        is NoteActionRequest.Unmute -> "已取消帖子静音"
        is NoteActionRequest.MuteRenotes -> "已静音此用户转发"
        is NoteActionRequest.UnmuteRenotes -> "已取消转发静音"
    }
