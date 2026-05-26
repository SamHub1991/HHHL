package cc.hhhl.client.state

import cc.hhhl.client.model.Page
import cc.hhhl.client.model.PageListKind
import cc.hhhl.client.repository.PageActionRepositoryResult
import cc.hhhl.client.repository.PageRepository
import cc.hhhl.client.repository.PageRepositoryResult
import cc.hhhl.client.repository.PagesRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PageUiState(
    val selectedKind: PageListKind = PageListKind.Featured,
    val pages: List<Page> = emptyList(),
    val selectedPage: Page? = null,
    val isLoadingPages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isChangingLike: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val detailErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class PageStateHolder(
    private val repository: PageRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(PageUiState())
    val state: StateFlow<PageUiState> = mutableState

    fun refreshPages(kind: PageListKind = state.value.selectedKind) {
        if (state.value.isLoadingPages) return

        mutableState.update {
            it.copy(
                selectedKind = kind,
                pages = if (it.selectedKind == kind) it.pages else emptyList(),
                isLoadingPages = true,
                isLoadingMore = false,
                endReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyPagesResult(
                result = repository.refreshPages(kind),
                loadingMore = false,
            )
        }
    }

    fun selectKind(kind: PageListKind) {
        if (state.value.selectedKind == kind && state.value.pages.isNotEmpty()) return
        refreshPages(kind)
    }

    fun loadMore() {
        val current = state.value
        if (
            current.isLoadingPages ||
            current.isLoadingMore ||
            current.pages.isEmpty() ||
            current.endReached
        ) {
            return
        }

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyPagesResult(
                result = repository.loadMorePages(current.selectedKind, current.pages),
                loadingMore = true,
            )
        }
    }

    fun openPage(pageId: String) {
        if (pageId.isBlank() || state.value.isLoadingDetail) return

        mutableState.update {
            it.copy(
                isLoadingDetail = true,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.showPage(pageId)) {
                is PageRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        selectedPage = result.page,
                        isLoadingDetail = false,
                        detailErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                PageRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is PageRepositoryResult.Error -> mutableState.update {
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
                selectedPage = null,
                isLoadingDetail = false,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun toggleLikeSelectedPage() {
        val page = state.value.selectedPage ?: return
        if (state.value.isChangingLike) return

        mutableState.update {
            it.copy(isChangingLike = true, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (page.isLiked) {
                repository.unlikePage(page.id)
            } else {
                repository.likePage(page.id)
            }
            applyLikeResult(page, result)
        }
    }

    private fun applyPagesResult(
        result: PagesRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is PagesRepositoryResult.Success -> mutableState.update {
                it.copy(
                    pages = result.pages,
                    isLoadingPages = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            PagesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingPages = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is PagesRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoadingPages = if (loadingMore) it.isLoadingPages else false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyLikeResult(
        originalPage: Page,
        result: PageActionRepositoryResult,
    ) {
        when (result) {
            PageActionRepositoryResult.Success -> mutableState.update { current ->
                val nowLiked = !originalPage.isLiked
                val delta = if (nowLiked) 1 else -1
                current.copy(
                    pages = current.pages.map { page ->
                        if (page.id == originalPage.id) {
                            page.copy(
                                isLiked = nowLiked,
                                likedCount = (page.likedCount + delta).coerceAtLeast(0),
                            )
                        } else {
                            page
                        }
                    },
                    selectedPage = current.selectedPage?.takeIf { it.id == originalPage.id }?.copy(
                        isLiked = nowLiked,
                        likedCount = (current.selectedPage.likedCount + delta).coerceAtLeast(0),
                    ) ?: current.selectedPage,
                    isChangingLike = false,
                    detailErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            PageActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isChangingLike = false,
                    detailErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is PageActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isChangingLike = false,
                    detailErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
