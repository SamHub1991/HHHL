package cc.hhhl.client.state

import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelDraft
import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.ChannelRepository
import cc.hhhl.client.repository.ChannelActionRepositoryResult
import cc.hhhl.client.repository.ChannelMutationRepositoryResult
import cc.hhhl.client.repository.ChannelTimelineRepositoryResult
import cc.hhhl.client.repository.ChannelsRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelUiState(
    val selectedKind: ChannelListKind = ChannelListKind.Featured,
    val channels: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val notes: List<Note> = emptyList(),
    val isLoadingChannels: Boolean = false,
    val isLoadingTimeline: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isChangingFollow: Boolean = false,
    val isChangingFavorite: Boolean = false,
    val isMutatingChannel: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val timelineErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class ChannelStateHolder(
    private val repository: ChannelRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = mutableState

    fun refreshChannels(kind: ChannelListKind = state.value.selectedKind) {
        if (state.value.isLoadingChannels) return

        val previousState = state.value
        val previousSelectedId = if (previousState.selectedKind == kind) {
            previousState.selectedChannel?.id
        } else {
            null
        }

        mutableState.update {
            it.copy(
                selectedKind = kind,
                notes = if (it.selectedKind == kind) it.notes else emptyList(),
                isLoadingChannels = true,
                isLoadingMore = false,
                endReached = false,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.refreshChannels(kind)) {
                is ChannelsRepositoryResult.Success -> {
                    val selected = result.channels.firstOrNull { it.id == previousSelectedId }
                        ?: result.channels.firstOrNull()
                    mutableState.update {
                        it.copy(
                            channels = result.channels,
                            selectedChannel = selected,
                            notes = if (selected == null) emptyList() else it.notes,
                            isLoadingChannels = false,
                            errorMessage = null,
                            requiresRelogin = false,
                        )
                    }
                    selected?.let { channel -> loadTimeline(channel.id, clearNotes = state.value.notes.isEmpty()) }
                }
                ChannelsRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingChannels = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ChannelsRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isLoadingChannels = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun selectKind(kind: ChannelListKind) {
        if (state.value.selectedKind == kind && state.value.channels.isNotEmpty()) return
        refreshChannels(kind)
    }

    fun selectChannel(channel: Channel) {
        if (state.value.selectedChannel?.id == channel.id && state.value.notes.isNotEmpty()) return

        mutableState.update {
            it.copy(
                selectedChannel = channel,
                notes = emptyList(),
                isLoadingTimeline = true,
                isLoadingMore = false,
                endReached = false,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyTimelineResult(
                result = repository.refreshTimeline(channel.id),
                loadingMore = false,
            )
        }
    }

    fun refreshTimeline() {
        val channel = state.value.selectedChannel ?: return
        if (state.value.isLoadingTimeline) return
        loadTimeline(channel.id, clearNotes = false)
    }

    fun loadMore() {
        val current = state.value
        val channelId = current.selectedChannel?.id ?: return
        if (
            current.isLoadingTimeline ||
            current.isLoadingMore ||
            current.notes.isEmpty() ||
            current.endReached
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMore = true,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyTimelineResult(
                result = repository.loadMoreTimeline(channelId, current.notes),
                loadingMore = true,
            )
        }
    }

    fun applyNoteMutation(mutation: NoteLocalMutation) {
        mutableState.update {
            it.copy(
                channels = it.channels.map { channel ->
                    channel.copy(pinnedNotes = channel.pinnedNotes.applyNoteLocalMutation(mutation))
                },
                selectedChannel = it.selectedChannel?.copy(
                    pinnedNotes = it.selectedChannel.pinnedNotes.applyNoteLocalMutation(mutation),
                ),
                notes = it.notes.applyNoteLocalMutation(mutation),
                requiresRelogin = false,
            )
        }
    }

    fun toggleFollowSelectedChannel() {
        val channel = state.value.selectedChannel ?: return
        if (state.value.isChangingFollow) return

        mutableState.update {
            it.copy(
                isChangingFollow = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val result = if (channel.isFollowing) {
                repository.unfollowChannel(channel.id)
            } else {
                repository.followChannel(channel.id)
            }
            applyFollowResult(channel, result)
        }
    }

    fun toggleFavoriteSelectedChannel() {
        val channel = state.value.selectedChannel ?: return
        if (state.value.isChangingFavorite) return

        mutableState.update {
            it.copy(
                isChangingFavorite = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val result = if (channel.isFavorited) {
                repository.unfavoriteChannel(channel.id)
            } else {
                repository.favoriteChannel(channel.id)
            }
            applyFavoriteResult(channel, result)
        }
    }

    fun createChannel(draft: ChannelDraft) {
        if (draft.name.trim().isEmpty() || state.value.isMutatingChannel) return

        mutableState.update {
            it.copy(
                isMutatingChannel = true,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.createChannel(draft.copy(name = draft.name.trim()))) {
                is ChannelMutationRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        channels = listOf(result.channel) + current.channels.filterNot { it.id == result.channel.id },
                        selectedChannel = result.channel,
                        notes = emptyList(),
                        isMutatingChannel = false,
                        isLoadingTimeline = false,
                        isLoadingMore = false,
                        endReached = false,
                        errorMessage = null,
                        timelineErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                ChannelMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingChannel = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ChannelMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingChannel = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun updateSelectedChannel(draft: ChannelDraft) {
        val channel = state.value.selectedChannel ?: return
        if (draft.name.trim().isEmpty() || state.value.isMutatingChannel) return

        mutableState.update {
            it.copy(
                isMutatingChannel = true,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (
                val result = repository.updateChannel(
                    channelId = channel.id,
                    draft = draft.copy(name = draft.name.trim()),
                )
            ) {
                is ChannelMutationRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        channels = current.channels.map { item ->
                            if (item.id == result.channel.id) result.channel else item
                        },
                        selectedChannel = current.selectedChannel?.let {
                            if (it.id == result.channel.id) result.channel else it
                        },
                        isMutatingChannel = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                ChannelMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingChannel = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ChannelMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingChannel = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun archiveSelectedChannel() {
        val channel = state.value.selectedChannel ?: return
        if (state.value.isMutatingChannel) return

        mutableState.update {
            it.copy(
                isMutatingChannel = true,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.archiveChannel(channel)) {
                is ChannelMutationRepositoryResult.Success -> mutableState.update { current ->
                    val remaining = current.channels.filterNot { it.id == result.channel.id }
                    current.copy(
                        channels = remaining,
                        selectedChannel = remaining.firstOrNull(),
                        notes = emptyList(),
                        isMutatingChannel = false,
                        isLoadingTimeline = false,
                        isLoadingMore = false,
                        endReached = false,
                        errorMessage = null,
                        timelineErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                ChannelMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingChannel = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ChannelMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingChannel = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun loadTimeline(
        channelId: String,
        clearNotes: Boolean,
    ) {
        mutableState.update {
            it.copy(
                notes = if (clearNotes) emptyList() else it.notes,
                isLoadingTimeline = true,
                isLoadingMore = false,
                endReached = false,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyTimelineResult(
                result = repository.refreshTimeline(channelId),
                loadingMore = false,
            )
        }
    }

    private fun applyTimelineResult(
        result: ChannelTimelineRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is ChannelTimelineRepositoryResult.Success -> mutableState.update {
                it.copy(
                    notes = result.notes,
                    isLoadingTimeline = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    timelineErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChannelTimelineRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingTimeline = false,
                    isLoadingMore = false,
                    timelineErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChannelTimelineRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoadingTimeline = if (loadingMore) it.isLoadingTimeline else false,
                    isLoadingMore = false,
                    timelineErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyFollowResult(
        originalChannel: Channel,
        result: ChannelActionRepositoryResult,
    ) {
        when (result) {
            ChannelActionRepositoryResult.Success -> mutableState.update { current ->
                val nowFollowing = !originalChannel.isFollowing
                val delta = if (nowFollowing) 1 else -1
                val selected = current.selectedChannel?.takeIf { it.id == originalChannel.id }
                val updatedSelected = selected?.copy(
                        isFollowing = nowFollowing,
                        usersCount = (selected.usersCount + delta).coerceAtLeast(0),
                    )
                current.copy(
                    channels = current.channels.map { channel ->
                        if (channel.id == originalChannel.id) {
                            channel.copy(
                                isFollowing = nowFollowing,
                                usersCount = (channel.usersCount + delta).coerceAtLeast(0),
                            )
                        } else {
                            channel
                        }
                    },
                    selectedChannel = updatedSelected ?: current.selectedChannel,
                    isChangingFollow = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChannelActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isChangingFollow = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChannelActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isChangingFollow = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyFavoriteResult(
        originalChannel: Channel,
        result: ChannelActionRepositoryResult,
    ) {
        when (result) {
            ChannelActionRepositoryResult.Success -> mutableState.update { current ->
                val nowFavorited = !originalChannel.isFavorited
                val selected = current.selectedChannel?.takeIf { it.id == originalChannel.id }
                val updatedSelected = selected?.copy(isFavorited = nowFavorited)
                current.copy(
                    channels = current.channels.map { channel ->
                        if (channel.id == originalChannel.id) {
                            channel.copy(isFavorited = nowFavorited)
                        } else {
                            channel
                        }
                    },
                    selectedChannel = updatedSelected ?: current.selectedChannel,
                    isChangingFavorite = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChannelActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isChangingFavorite = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChannelActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isChangingFavorite = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
