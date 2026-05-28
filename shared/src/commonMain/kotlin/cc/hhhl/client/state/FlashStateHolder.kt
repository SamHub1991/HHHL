package cc.hhhl.client.state

import cc.hhhl.client.model.Flash
import cc.hhhl.client.model.FlashDraft
import cc.hhhl.client.model.FlashListKind
import cc.hhhl.client.model.toDraft
import cc.hhhl.client.repository.FlashActionRepositoryResult
import cc.hhhl.client.repository.FlashRepository
import cc.hhhl.client.repository.FlashRepositoryResult
import cc.hhhl.client.repository.FlashesRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlashUiState(
    val selectedKind: FlashListKind = FlashListKind.Featured,
    val flashes: List<Flash> = emptyList(),
    val selectedFlash: Flash? = null,
    val isLoadingFlashes: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isChangingLike: Boolean = false,
    val isSavingDraft: Boolean = false,
    val isDeletingFlash: Boolean = false,
    val draftMode: FlashDraftMode? = null,
    val draft: FlashDraft = FlashDraft(),
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val detailErrorMessage: String? = null,
    val draftErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

enum class FlashDraftMode {
    Create,
    Edit,
}

class FlashStateHolder(
    private val repository: FlashRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(FlashUiState())
    val state: StateFlow<FlashUiState> = mutableState
    private var flashesRequestId = 0
    private var detailRequestId = 0

    fun refreshFlashes(kind: FlashListKind = state.value.selectedKind) {
        if (state.value.isLoadingFlashes) return
        val requestId = ++flashesRequestId

        mutableState.update {
            it.copy(
                selectedKind = kind,
                flashes = if (it.selectedKind == kind) it.flashes else emptyList(),
                isLoadingFlashes = true,
                isLoadingMore = false,
                endReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyFlashesResult(
                result = repository.refreshFlashes(kind),
                loadingMore = false,
                requestId = requestId,
                kind = kind,
            )
        }
    }

    fun selectKind(kind: FlashListKind) {
        if (state.value.selectedKind == kind && state.value.flashes.isNotEmpty()) return
        refreshFlashes(kind)
    }

    fun loadMore() {
        val current = state.value
        if (
            current.isLoadingFlashes ||
            current.isLoadingMore ||
            current.flashes.isEmpty() ||
            current.endReached
        ) {
            return
        }
        val requestId = ++flashesRequestId
        val kind = current.selectedKind

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyFlashesResult(
                result = repository.loadMoreFlashes(kind, current.flashes),
                loadingMore = true,
                requestId = requestId,
                kind = kind,
            )
        }
    }

    fun openFlash(flashId: String) {
        if (flashId.isBlank()) return
        val requestId = ++detailRequestId

        mutableState.update {
            it.copy(
                selectedFlash = it.selectedFlash?.takeIf { flash -> flash.id == flashId },
                isLoadingDetail = true,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.showFlash(flashId)) {
                is FlashRepositoryResult.Success -> mutableState.update {
                    if (requestId != detailRequestId || result.flash.id != flashId) return@update it
                    it.copy(
                        selectedFlash = result.flash,
                        isLoadingDetail = false,
                        detailErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                FlashRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != detailRequestId) return@update it
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is FlashRepositoryResult.Error -> mutableState.update {
                    if (requestId != detailRequestId) return@update it
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun closeDetail() {
        detailRequestId += 1
        mutableState.update {
            it.copy(
                selectedFlash = null,
                isLoadingDetail = false,
                detailErrorMessage = null,
                draftMode = null,
                draft = FlashDraft(),
                draftErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun startCreatingFlash() {
        mutableState.update {
            it.copy(
                draftMode = FlashDraftMode.Create,
                draft = FlashDraft(),
                draftErrorMessage = null,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun startEditingSelectedFlash() {
        val flash = state.value.selectedFlash ?: return
        mutableState.update {
            it.copy(
                draftMode = FlashDraftMode.Edit,
                draft = flash.toDraft(),
                draftErrorMessage = null,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateDraft(draft: FlashDraft) {
        mutableState.update {
            it.copy(
                draft = draft,
                draftErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun cancelDraft() {
        mutableState.update {
            it.copy(
                draftMode = null,
                draft = FlashDraft(),
                draftErrorMessage = null,
                isSavingDraft = false,
                requiresRelogin = false,
            )
        }
    }

    fun saveDraft() {
        val current = state.value
        val mode = current.draftMode ?: return
        if (current.isSavingDraft) return

        mutableState.update {
            it.copy(isSavingDraft = true, draftErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            val result = when (mode) {
                FlashDraftMode.Create -> repository.createFlash(current.draft)
                FlashDraftMode.Edit -> {
                    val flashId = current.selectedFlash?.id.orEmpty()
                    repository.updateFlash(flashId, current.draft)
                }
            }
            applySaveResult(mode, result)
        }
    }

    fun deleteSelectedFlash() {
        val flash = state.value.selectedFlash ?: return
        if (state.value.isDeletingFlash) return

        mutableState.update {
            it.copy(isDeletingFlash = true, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyDeleteResult(flash.id, repository.deleteFlash(flash.id))
        }
    }

    fun toggleLikeSelectedFlash() {
        val flash = state.value.selectedFlash ?: return
        if (state.value.isChangingLike) return

        mutableState.update {
            it.copy(isChangingLike = true, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (flash.isLiked) {
                repository.unlikeFlash(flash.id)
            } else {
                repository.likeFlash(flash.id)
            }
            applyLikeResult(flash, result)
        }
    }

    private fun applyFlashesResult(
        result: FlashesRepositoryResult,
        loadingMore: Boolean,
        requestId: Int,
        kind: FlashListKind,
    ) {
        when (result) {
            is FlashesRepositoryResult.Success -> mutableState.update {
                if (requestId != flashesRequestId || it.selectedKind != kind) return@update it
                it.copy(
                    flashes = result.flashes,
                    isLoadingFlashes = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            FlashesRepositoryResult.Unauthorized -> mutableState.update {
                if (requestId != flashesRequestId || it.selectedKind != kind) return@update it
                it.copy(
                    isLoadingFlashes = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FlashesRepositoryResult.Error -> mutableState.update {
                if (requestId != flashesRequestId || it.selectedKind != kind) return@update it
                it.copy(
                    isLoadingFlashes = if (loadingMore) it.isLoadingFlashes else false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyLikeResult(
        originalFlash: Flash,
        result: FlashActionRepositoryResult,
    ) {
        when (result) {
            FlashActionRepositoryResult.Success -> mutableState.update { current ->
                val nowLiked = !originalFlash.isLiked
                val delta = if (nowLiked) 1 else -1
                current.copy(
                    flashes = current.flashes.map { flash ->
                        if (flash.id == originalFlash.id) {
                            flash.copy(
                                isLiked = nowLiked,
                                likedCount = (flash.likedCount + delta).coerceAtLeast(0),
                            )
                        } else {
                            flash
                        }
                    },
                    selectedFlash = current.selectedFlash?.takeIf { it.id == originalFlash.id }?.copy(
                        isLiked = nowLiked,
                        likedCount = (current.selectedFlash.likedCount + delta).coerceAtLeast(0),
                    ) ?: current.selectedFlash,
                    isChangingLike = false,
                    detailErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            FlashActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isChangingLike = false,
                    detailErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FlashActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isChangingLike = false,
                    detailErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applySaveResult(
        mode: FlashDraftMode,
        result: FlashRepositoryResult,
    ) {
        when (result) {
            is FlashRepositoryResult.Success -> mutableState.update { current ->
                val updatedFlashes = when (mode) {
                    FlashDraftMode.Create -> listOf(result.flash) + current.flashes.filterNot { it.id == result.flash.id }
                    FlashDraftMode.Edit -> current.flashes.map { flash ->
                        if (flash.id == result.flash.id) result.flash else flash
                    }
                }
                current.copy(
                    flashes = updatedFlashes,
                    selectedFlash = result.flash,
                    isSavingDraft = false,
                    draftMode = null,
                    draft = FlashDraft(),
                    draftErrorMessage = null,
                    detailErrorMessage = null,
                    selectedKind = if (mode == FlashDraftMode.Create) FlashListKind.Mine else current.selectedKind,
                    requiresRelogin = false,
                )
            }
            FlashRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isSavingDraft = false,
                    draftErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FlashRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isSavingDraft = false,
                    draftErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyDeleteResult(
        flashId: String,
        result: FlashActionRepositoryResult,
    ) {
        when (result) {
            FlashActionRepositoryResult.Success -> mutableState.update { current ->
                current.copy(
                    flashes = current.flashes.filterNot { it.id == flashId },
                    selectedFlash = null,
                    isDeletingFlash = false,
                    detailErrorMessage = null,
                    draftMode = null,
                    draft = FlashDraft(),
                    draftErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            FlashActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isDeletingFlash = false,
                    detailErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FlashActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isDeletingFlash = false,
                    detailErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
