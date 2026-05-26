package cc.hhhl.client.state

import cc.hhhl.client.model.Flash
import cc.hhhl.client.model.FlashListKind
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
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val detailErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class FlashStateHolder(
    private val repository: FlashRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(FlashUiState())
    val state: StateFlow<FlashUiState> = mutableState

    fun refreshFlashes(kind: FlashListKind = state.value.selectedKind) {
        if (state.value.isLoadingFlashes) return

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

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyFlashesResult(
                result = repository.loadMoreFlashes(current.selectedKind, current.flashes),
                loadingMore = true,
            )
        }
    }

    fun openFlash(flashId: String) {
        if (flashId.isBlank() || state.value.isLoadingDetail) return

        mutableState.update {
            it.copy(
                isLoadingDetail = true,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.showFlash(flashId)) {
                is FlashRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        selectedFlash = result.flash,
                        isLoadingDetail = false,
                        detailErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                FlashRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is FlashRepositoryResult.Error -> mutableState.update {
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
        mutableState.update {
            it.copy(
                selectedFlash = null,
                isLoadingDetail = false,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
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
    ) {
        when (result) {
            is FlashesRepositoryResult.Success -> mutableState.update {
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
                it.copy(
                    isLoadingFlashes = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FlashesRepositoryResult.Error -> mutableState.update {
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
}
