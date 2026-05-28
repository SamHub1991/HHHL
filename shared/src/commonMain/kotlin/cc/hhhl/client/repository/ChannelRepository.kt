package cc.hhhl.client.repository

import cc.hhhl.client.api.ChannelApi
import cc.hhhl.client.api.ChannelActionResult
import cc.hhhl.client.api.ChannelLoadResult
import cc.hhhl.client.api.ChannelMutationResult
import cc.hhhl.client.api.ChannelTimelineLoadResult
import cc.hhhl.client.api.SharkeyChannelApi
import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelDefaultColorHex
import cc.hhhl.client.model.ChannelDraft
import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.Note

open class ChannelRepository(
    private val tokenProvider: () -> String?,
    private val api: ChannelApi = SharkeyChannelApi(),
) {
    open suspend fun refreshChannels(kind: ChannelListKind): ChannelsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChannelsRepositoryResult.Unauthorized

        return when (
            val result = api.loadChannels(
                token = token,
                kind = kind,
                limit = DEFAULT_PAGE_SIZE,
                untilId = null,
            )
        ) {
            is ChannelLoadResult.Success -> ChannelsRepositoryResult.Success(result.channels)
            ChannelLoadResult.Unauthorized -> ChannelsRepositoryResult.Unauthorized
            is ChannelLoadResult.NetworkError -> {
                ChannelsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChannelLoadResult.ServerError -> ChannelsRepositoryResult.Error(result.message)
        }
    }

    open suspend fun refreshTimeline(channelId: String): ChannelTimelineRepositoryResult {
        return loadTimeline(
            channelId = channelId,
            currentNotes = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMoreTimeline(
        channelId: String,
        currentNotes: List<Note>,
    ): ChannelTimelineRepositoryResult {
        return loadTimeline(
            channelId = channelId,
            currentNotes = currentNotes,
            untilId = currentNotes.lastOrNull()?.id,
        )
    }

    open suspend fun followChannel(channelId: String): ChannelActionRepositoryResult {
        return performChannelAction(channelId) { token, cleanChannelId ->
            api.followChannel(token, cleanChannelId)
        }
    }

    open suspend fun unfollowChannel(channelId: String): ChannelActionRepositoryResult {
        return performChannelAction(channelId) { token, cleanChannelId ->
            api.unfollowChannel(token, cleanChannelId)
        }
    }

    open suspend fun favoriteChannel(channelId: String): ChannelActionRepositoryResult {
        return performChannelAction(channelId) { token, cleanChannelId ->
            api.favoriteChannel(token, cleanChannelId)
        }
    }

    open suspend fun unfavoriteChannel(channelId: String): ChannelActionRepositoryResult {
        return performChannelAction(channelId) { token, cleanChannelId ->
            api.unfavoriteChannel(token, cleanChannelId)
        }
    }

    open suspend fun createChannel(draft: ChannelDraft): ChannelMutationRepositoryResult {
        val normalizedDraft = draft.normalized()
        if (normalizedDraft.name.isEmpty()) {
            return ChannelMutationRepositoryResult.Error("请输入频道名称")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChannelMutationRepositoryResult.Unauthorized

        return mapMutationResult(api.createChannel(token, normalizedDraft))
    }

    open suspend fun updateChannel(
        channelId: String,
        draft: ChannelDraft,
    ): ChannelMutationRepositoryResult {
        val cleanChannelId = channelId.trim()
        val normalizedDraft = draft.normalized()
        if (cleanChannelId.isEmpty()) {
            return ChannelMutationRepositoryResult.Error("无法读取频道")
        }
        if (normalizedDraft.name.isEmpty()) {
            return ChannelMutationRepositoryResult.Error("请输入频道名称")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChannelMutationRepositoryResult.Unauthorized

        return mapMutationResult(api.updateChannel(token, cleanChannelId, normalizedDraft))
    }

    open suspend fun archiveChannel(channel: Channel): ChannelMutationRepositoryResult {
        return updateChannel(
            channelId = channel.id,
            draft = channel.toDraft().copy(isArchived = true),
        )
    }

    private suspend fun loadTimeline(
        channelId: String,
        currentNotes: List<Note>,
        untilId: String?,
    ): ChannelTimelineRepositoryResult {
        val cleanChannelId = channelId.trim()
        if (cleanChannelId.isEmpty()) {
            return ChannelTimelineRepositoryResult.Error("无法读取频道")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChannelTimelineRepositoryResult.Unauthorized

        return when (
            val result = api.loadChannelTimeline(
                token = token,
                channelId = cleanChannelId,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
                withRenotes = true,
                withFiles = false,
            )
        ) {
            is ChannelTimelineLoadResult.Success -> ChannelTimelineRepositoryResult.Success(
                notes = currentNotes.appendDistinctBy(result.notes) { it.id },
                endReached = result.notes.isEmpty(),
            )
            ChannelTimelineLoadResult.Unauthorized -> ChannelTimelineRepositoryResult.Unauthorized
            is ChannelTimelineLoadResult.NetworkError -> {
                ChannelTimelineRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChannelTimelineLoadResult.ServerError -> ChannelTimelineRepositoryResult.Error(result.message)
        }
    }

    private suspend fun performChannelAction(
        channelId: String,
        action: suspend (String, String) -> ChannelActionResult,
    ): ChannelActionRepositoryResult {
        val cleanChannelId = channelId.trim()
        if (cleanChannelId.isEmpty()) {
            return ChannelActionRepositoryResult.Error("无法读取频道")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChannelActionRepositoryResult.Unauthorized

        return when (val result = action(token, cleanChannelId)) {
            ChannelActionResult.Success -> ChannelActionRepositoryResult.Success
            ChannelActionResult.Unauthorized -> ChannelActionRepositoryResult.Unauthorized
            is ChannelActionResult.NetworkError -> {
                ChannelActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChannelActionResult.ServerError -> ChannelActionRepositoryResult.Error(result.message)
        }
    }

    private fun mapMutationResult(result: ChannelMutationResult): ChannelMutationRepositoryResult {
        return when (result) {
            is ChannelMutationResult.Success -> ChannelMutationRepositoryResult.Success(result.channel)
            ChannelMutationResult.Unauthorized -> ChannelMutationRepositoryResult.Unauthorized
            is ChannelMutationResult.NetworkError -> {
                ChannelMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChannelMutationResult.ServerError -> ChannelMutationRepositoryResult.Error(result.message)
        }
    }

    private fun ChannelDraft.normalized(): ChannelDraft {
        return copy(
            name = name.trim(),
            description = description.trim(),
            color = color.trim().ifBlank { ChannelDefaultColorHex },
            bannerId = bannerId?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun Channel.toDraft(): ChannelDraft {
        return ChannelDraft(
            name = name,
            description = description,
            color = color,
            isArchived = isArchived,
            isSensitive = isSensitive,
            allowRenoteToExternal = allowRenoteToExternal,
        )
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface ChannelsRepositoryResult {
    data class Success(val channels: List<Channel>) : ChannelsRepositoryResult

    data object Unauthorized : ChannelsRepositoryResult

    data class Error(val message: String) : ChannelsRepositoryResult
}

sealed interface ChannelTimelineRepositoryResult {
    data class Success(
        val notes: List<Note>,
        val endReached: Boolean = false,
    ) : ChannelTimelineRepositoryResult

    data object Unauthorized : ChannelTimelineRepositoryResult

    data class Error(val message: String) : ChannelTimelineRepositoryResult
}

sealed interface ChannelActionRepositoryResult {
    data object Success : ChannelActionRepositoryResult

    data object Unauthorized : ChannelActionRepositoryResult

    data class Error(val message: String) : ChannelActionRepositoryResult
}

sealed interface ChannelMutationRepositoryResult {
    data class Success(val channel: Channel) : ChannelMutationRepositoryResult

    data object Unauthorized : ChannelMutationRepositoryResult

    data class Error(val message: String) : ChannelMutationRepositoryResult
}
