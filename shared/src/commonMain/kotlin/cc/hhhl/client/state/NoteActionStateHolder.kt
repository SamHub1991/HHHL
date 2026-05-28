package cc.hhhl.client.state

import cc.hhhl.client.repository.EmojiRepository
import cc.hhhl.client.repository.EmojiRepositoryResult
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.commonReactionOptions
import cc.hhhl.client.repository.NoteActionRepository
import cc.hhhl.client.repository.NoteActionRepositoryResult
import cc.hhhl.client.repository.NoteActionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface RecentReactionStore {
    fun loadRecentReactions(): List<String>

    fun saveRecentReactions(reactions: List<String>)
}

object NoopRecentReactionStore : RecentReactionStore {
    override fun loadRecentReactions(): List<String> = emptyList()

    override fun saveRecentReactions(reactions: List<String>) = Unit
}

data class NoteActionUiState(
    val pendingNoteIds: Set<String> = emptySet(),
    val reactionOptions: List<String> = defaultReactionOptions(),
    val recentReactions: List<String> = emptyList(),
    val customEmojiUrls: Map<String, String> = emptyMap(),
    val customEmojis: List<CustomEmoji> = emptyList(),
    val isLoadingReactionOptions: Boolean = false,
    val reactionOptionsError: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class NoteActionStateHolder(
    private val repository: NoteActionRepository,
    private val emojiRepository: EmojiRepository? = null,
    private val recentReactionStore: RecentReactionStore = NoopRecentReactionStore,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(NoteActionUiState())
    val state: StateFlow<NoteActionUiState> = mutableState
    private var defaultReaction = NoteActionRequest.DEFAULT_REACTION
    private var customReactionOptions = emptyList<String>()
    private var reactionOptionsRequestId = 0

    fun restoreRecentReactions() {
        mutableState.update {
            it.copy(recentReactions = recentReactionStore.loadRecentReactions().sanitizeRecentReactions())
        }
    }

    fun updateDefaultReaction(defaultReaction: String) {
        this.defaultReaction = defaultReaction.trim().takeIf { it.isNotEmpty() }
            ?: NoteActionRequest.DEFAULT_REACTION
        mutableState.update {
            it.copy(reactionOptions = defaultReactionOptions(this.defaultReaction, customReactionOptions))
        }
    }

    fun loadReactionOptions() {
        val repository = emojiRepository ?: return
        if (state.value.isLoadingReactionOptions) return
        val requestId = ++reactionOptionsRequestId

        mutableState.update {
            it.copy(
                isLoadingReactionOptions = true,
                reactionOptionsError = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.loadReactionOptions()) {
                is EmojiRepositoryResult.Success -> {
                    mutableState.update {
                        if (requestId != reactionOptionsRequestId) return@update it
                        customReactionOptions = result.reactionOptions
                        it.copy(
                            reactionOptions = defaultReactionOptions(defaultReaction, customReactionOptions),
                            customEmojiUrls = result.emojiUrls,
                            customEmojis = result.customEmojis,
                            isLoadingReactionOptions = false,
                            reactionOptionsError = null,
                            requiresRelogin = false,
                        )
                    }
                }
                is EmojiRepositoryResult.Error -> mutableState.update {
                    if (requestId != reactionOptionsRequestId) return@update it
                    it.copy(
                        reactionOptions = defaultReactionOptions(defaultReaction, customReactionOptions),
                        isLoadingReactionOptions = false,
                        reactionOptionsError = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun perform(request: NoteActionRequest) {
        val noteId = request.noteId
        if (noteId.isBlank() || state.value.pendingNoteIds.contains(noteId)) return

        mutableState.update {
            it.copy(
                pendingNoteIds = it.pendingNoteIds + noteId,
                message = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyResult(noteId, request, repository.perform(request))
        }
    }

    private fun applyResult(
        noteId: String,
        request: NoteActionRequest,
        result: NoteActionRepositoryResult,
    ) {
        when (result) {
            is NoteActionRepositoryResult.Success -> mutableState.update {
                val recentReactions = it.recentReactions.updatedRecentReactions(request)
                recentReactionStore.saveRecentReactions(recentReactions)
                it.copy(
                    pendingNoteIds = it.pendingNoteIds - noteId,
                    recentReactions = recentReactions,
                    message = result.message,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            NoteActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    pendingNoteIds = it.pendingNoteIds - noteId,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is NoteActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    pendingNoteIds = it.pendingNoteIds - noteId,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}

private fun List<String>.sanitizeRecentReactions(): List<String> {
    return map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(8)
}

private fun List<String>.updatedRecentReactions(request: NoteActionRequest): List<String> {
    val reaction = (request as? NoteActionRequest.React)
        ?.reaction
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return this
    return (listOf(reaction) + this).sanitizeRecentReactions()
}

private fun defaultReactionOptions(
    defaultReaction: String = NoteActionRequest.DEFAULT_REACTION,
    customOptions: List<String> = emptyList(),
): List<String> {
    return (listOf(defaultReaction.trim().takeIf { it.isNotEmpty() } ?: NoteActionRequest.DEFAULT_REACTION) +
        commonReactionOptions +
        customOptions)
        .distinct()
}
