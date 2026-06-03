package cc.hhhl.client.state

import cc.hhhl.client.model.FavoriteNote
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.FavoriteNoteRepository
import cc.hhhl.client.repository.FavoriteNotesRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class FavoriteNoteUiState(
    val favorites: List<FavoriteNote> = emptyList(),
    val favoriteMessages: List<FavoriteMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingFavoriteMessages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val favoriteMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class FavoriteNoteStateHolder(
    private val repository: FavoriteNoteRepository,
    private val scope: CoroutineScope,
    private val favoriteMessageStore: FavoriteMessageStore = NoopFavoriteMessageStore,
    private val accountIdProvider: () -> String? = { null },
) {
    private val mutableState = MutableStateFlow(FavoriteNoteUiState())
    val state: StateFlow<FavoriteNoteUiState> = mutableState
    private var listRequestId = 0
    private var favoriteMessageRequestId = 0
    private var favoriteMessageRevision = 0
    private var favoriteMessageSaveJob: Job? = null
    private var restoredFavoriteMessageAccountId: String? = null

    fun refresh() {
        if (state.value.isLoading) return

        mutableState.update {
            it.copy(isLoading = true, errorMessage = null, requiresRelogin = false)
        }

        val requestId = nextListRequestId()
        scope.launch {
            applyResult(repository.refresh(), loadingMore = false, requestId = requestId)
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || current.endReached || current.favorites.isEmpty()) return

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        val requestId = nextListRequestId()
        scope.launch {
            applyResult(repository.loadMore(current.favorites), loadingMore = true, requestId = requestId)
        }
    }

    fun restoreFavoriteMessages(force: Boolean = false) {
        val accountId = accountIdProvider()?.takeIf { it.isNotBlank() } ?: return
        if (!force && restoredFavoriteMessageAccountId == accountId && state.value.favoriteMessages.isNotEmpty()) return
        if (state.value.isLoadingFavoriteMessages) return
        restoredFavoriteMessageAccountId = accountId
        val requestId = nextFavoriteMessageRequestId()
        val revision = favoriteMessageRevision
        mutableState.update { it.copy(isLoadingFavoriteMessages = true, favoriteMessage = null) }
        scope.launch {
            val messages = favoriteMessageStore.read(accountId)
            if (requestId != favoriteMessageRequestId || revision != favoriteMessageRevision) return@launch
            mutableState.update {
                it.copy(
                    favoriteMessages = messages,
                    isLoadingFavoriteMessages = false,
                    favoriteMessage = null,
                )
            }
        }
    }

    fun addFavoriteMessage(
        conversationType: FavoriteMessageConversationType,
        conversationId: String,
        conversationTitle: String,
        message: ChatMessage,
    ) {
        val accountId = accountIdProvider()?.takeIf { it.isNotBlank() } ?: return
        val cleanMessageId = message.id.trim().takeIf { it.isNotBlank() } ?: return
        val cleanConversationId = conversationId.trim().takeIf { it.isNotBlank() }
            ?: message.roomId.trim().takeIf { it.isNotBlank() }
            ?: message.toUserId.orEmpty().trim().takeIf { it.isNotBlank() }
            ?: return
        val favorite = FavoriteMessage(
            id = favoriteMessageId(
                accountId = accountId,
                conversationType = conversationType,
                conversationId = cleanConversationId,
                messageId = cleanMessageId,
            ),
            accountId = accountId,
            conversationType = conversationType,
            conversationId = cleanConversationId,
            conversationTitle = conversationTitle.trim().ifBlank { conversationType.label },
            message = message,
            savedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            savedAtLabel = "刚刚",
        )
        val nextMessages = (listOf(favorite) + state.value.favoriteMessages.filterNot { it.id == favorite.id })
            .trimmedFavoriteMessages()
        val revision = nextFavoriteMessageRevision()
        mutableState.update {
            it.copy(
                favoriteMessages = nextMessages,
                favoriteMessage = "已收藏信息",
                isLoadingFavoriteMessages = false,
            )
        }
        restoredFavoriteMessageAccountId = accountId
        saveFavoriteMessages(accountId, nextMessages, revision)
    }

    fun removeFavoriteMessage(favoriteId: String) {
        val accountId = accountIdProvider()?.takeIf { it.isNotBlank() } ?: return
        val nextMessages = state.value.favoriteMessages.filterNot { it.id == favoriteId }
        val revision = nextFavoriteMessageRevision()
        mutableState.update {
            it.copy(
                favoriteMessages = nextMessages,
                favoriteMessage = "已取消收藏信息",
            )
        }
        saveFavoriteMessages(accountId, nextMessages, revision)
    }

    fun clearFavoriteMessage() {
        mutableState.update { it.copy(favoriteMessage = null) }
    }

    fun applyNoteMutation(mutation: NoteLocalMutation) {
        mutableState.update { current ->
            current.copy(
                favorites = when (mutation) {
                    is NoteLocalMutation.Unfavorite -> current.favorites.filterNot {
                        it.note.id == mutation.noteId
                    }
                    else -> current.favorites.mapNotNull { favorite ->
                        listOf(favorite.note)
                            .applyNoteLocalMutation(mutation)
                            .firstOrNull()
                            ?.let { favorite.copy(note = it) }
                    }
                },
                requiresRelogin = false,
            )
        }
    }

    fun addLocalFavorite(note: Note) {
        if (note.id.isBlank()) return
        mutableState.update { current ->
            val favorite = FavoriteNote(
                id = "local-${note.id}",
                createdAtLabel = "刚刚",
                note = note.copy(isFavorited = true),
            )
            current.copy(
                favorites = listOf(favorite) + current.favorites.filterNot { it.note.id == note.id },
                requiresRelogin = false,
            )
        }
    }

    private fun applyResult(
        result: FavoriteNotesRepositoryResult,
        loadingMore: Boolean,
        requestId: Int,
    ) {
        if (requestId != listRequestId) return
        when (result) {
            is FavoriteNotesRepositoryResult.Success -> mutableState.update {
                it.copy(
                    favorites = result.favorites,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = loadingMore && result.favorites.size == it.favorites.size,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            FavoriteNotesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FavoriteNotesRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun nextListRequestId(): Int {
        listRequestId += 1
        return listRequestId
    }

    private fun nextFavoriteMessageRequestId(): Int {
        favoriteMessageRequestId += 1
        return favoriteMessageRequestId
    }

    private fun nextFavoriteMessageRevision(): Int {
        favoriteMessageRevision += 1
        favoriteMessageRequestId += 1
        return favoriteMessageRevision
    }

    private fun saveFavoriteMessages(
        accountId: String,
        messages: List<FavoriteMessage>,
        revision: Int,
    ) {
        val previousSaveJob = favoriteMessageSaveJob
        favoriteMessageSaveJob = scope.launch {
            previousSaveJob?.join()
            if (revision != favoriteMessageRevision) return@launch
            favoriteMessageStore.save(accountId, messages)
        }
    }
}
