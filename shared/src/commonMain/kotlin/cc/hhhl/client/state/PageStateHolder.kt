package cc.hhhl.client.state

import cc.hhhl.client.model.Page
import cc.hhhl.client.model.PageDraft
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
    val isSavingPage: Boolean = false,
    val isDeletingPage: Boolean = false,
    val editingPageId: String? = null,
    val editingDraft: PageDraft? = null,
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
    private var pagesRequestId = 0
    private var detailRequestId = 0
    private var saveRequestId = 0

    fun refreshPages(kind: PageListKind = state.value.selectedKind) {
        if (state.value.isLoadingPages) return
        val requestId = ++pagesRequestId

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
                requestId = requestId,
                kind = kind,
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
        val requestId = ++pagesRequestId
        val kind = current.selectedKind

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyPagesResult(
                result = repository.loadMorePages(kind, current.pages),
                loadingMore = true,
                requestId = requestId,
                kind = kind,
            )
        }
    }

    fun openPage(pageId: String) {
        if (pageId.isBlank()) return

        openPageDetail(expectedPageId = pageId) { repository.showPage(pageId) }
    }

    fun openPageByPath(
        username: String,
        name: String,
    ) {
        if (username.isBlank() || name.isBlank()) return

        openPageDetail(expectedPageId = null) { repository.showPageByPath(username, name) }
    }

    private fun openPageDetail(
        expectedPageId: String?,
        load: suspend () -> PageRepositoryResult,
    ) {
        val requestId = ++detailRequestId
        mutableState.update {
            it.copy(
                selectedPage = expectedPageId?.let { pageId -> it.selectedPage?.takeIf { page -> page.id == pageId } },
                isLoadingDetail = true,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = load()) {
                is PageRepositoryResult.Success -> mutableState.update {
                    if (
                        requestId != detailRequestId ||
                        (expectedPageId != null && result.page.id != expectedPageId)
                    ) return@update it
                    it.copy(
                        selectedPage = result.page,
                        isLoadingDetail = false,
                        detailErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                PageRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != detailRequestId) return@update it
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is PageRepositoryResult.Error -> mutableState.update {
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
        saveRequestId += 1
        mutableState.update {
            it.copy(
                selectedPage = null,
                isLoadingDetail = false,
                detailErrorMessage = null,
                editingPageId = null,
                editingDraft = null,
                isSavingPage = false,
                isDeletingPage = false,
                requiresRelogin = false,
            )
        }
    }

    fun startCreatingPage() {
        detailRequestId += 1
        saveRequestId += 1
        mutableState.update {
            it.copy(
                selectedPage = null,
                editingPageId = null,
                editingDraft = PageDraft(),
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun startEditingSelectedPage() {
        val page = state.value.selectedPage ?: return
        mutableState.update {
            it.copy(
                editingPageId = page.id,
                editingDraft = page.toDraft(),
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun cancelEditingPage() {
        saveRequestId += 1
        mutableState.update {
            it.copy(
                editingPageId = null,
                editingDraft = null,
                isSavingPage = false,
                detailErrorMessage = null,
            )
        }
    }

    fun updateDraft(draft: PageDraft) {
        mutableState.update {
            it.copy(editingDraft = draft, detailErrorMessage = null)
        }
    }

    fun saveEditingPage() {
        val current = state.value
        val draft = current.editingDraft ?: return
        if (current.isSavingPage || !draft.canSubmit) return
        val editingPageId = current.editingPageId
        val requestId = ++saveRequestId

        mutableState.update {
            it.copy(isSavingPage = true, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (editingPageId == null) {
                repository.createPage(draft)
            } else {
                repository.updatePage(editingPageId, draft)
            }
            applySaveResult(
                result = result,
                created = editingPageId == null,
                requestId = requestId,
                editingPageId = editingPageId,
            )
        }
    }

    fun deleteSelectedPage() {
        val page = state.value.selectedPage ?: return
        if (state.value.isDeletingPage) return

        mutableState.update {
            it.copy(isDeletingPage = true, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            when (val result = repository.deletePage(page.id)) {
                PageActionRepositoryResult.Success -> mutableState.update { current ->
                    val deletedSelectedPage = current.selectedPage?.id == page.id
                    current.copy(
                        pages = current.pages.filterNot { it.id == page.id },
                        selectedPage = current.selectedPage?.takeIf { it.id != page.id },
                        editingPageId = current.editingPageId?.takeIf { it != page.id },
                        editingDraft = if (current.editingPageId == page.id) null else current.editingDraft,
                        isDeletingPage = false,
                        detailErrorMessage = if (deletedSelectedPage) null else current.detailErrorMessage,
                        requiresRelogin = false,
                    )
                }
                PageActionRepositoryResult.Unauthorized -> mutableState.update { current ->
                    val deletingSelectedPage = current.selectedPage?.id == page.id
                    current.copy(
                        isDeletingPage = false,
                        detailErrorMessage = if (deletingSelectedPage) "登录已失效，请重新登录" else current.detailErrorMessage,
                        requiresRelogin = true,
                    )
                }
                is PageActionRepositoryResult.Error -> mutableState.update { current ->
                    val deletingSelectedPage = current.selectedPage?.id == page.id
                    current.copy(
                        isDeletingPage = false,
                        detailErrorMessage = if (deletingSelectedPage) result.message else current.detailErrorMessage,
                        requiresRelogin = false,
                    )
                }
            }
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
        requestId: Int,
        kind: PageListKind,
    ) {
        when (result) {
            is PagesRepositoryResult.Success -> mutableState.update {
                if (requestId != pagesRequestId || it.selectedKind != kind) return@update it
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
                if (requestId != pagesRequestId || it.selectedKind != kind) return@update it
                it.copy(
                    isLoadingPages = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is PagesRepositoryResult.Error -> mutableState.update {
                if (requestId != pagesRequestId || it.selectedKind != kind) return@update it
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

    private fun applySaveResult(
        result: PageRepositoryResult,
        created: Boolean,
        requestId: Int,
        editingPageId: String?,
    ) {
        when (result) {
            is PageRepositoryResult.Success -> mutableState.update { current ->
                if (
                    requestId != saveRequestId ||
                    current.editingPageId != editingPageId ||
                    current.editingDraft == null
                ) return@update current
                val exists = current.pages.any { it.id == result.page.id }
                val nextPages = when {
                    exists -> current.pages.map { if (it.id == result.page.id) result.page else it }
                    created && current.selectedKind != PageListKind.Mine -> listOf(result.page)
                    else -> listOf(result.page) + current.pages
                }
                current.copy(
                    pages = nextPages,
                    selectedPage = result.page,
                    editingPageId = null,
                    editingDraft = null,
                    isSavingPage = false,
                    selectedKind = PageListKind.Mine,
                    detailErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            PageRepositoryResult.Unauthorized -> mutableState.update {
                if (requestId != saveRequestId || it.editingPageId != editingPageId) return@update it
                it.copy(
                    isSavingPage = false,
                    detailErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is PageRepositoryResult.Error -> mutableState.update {
                if (requestId != saveRequestId || it.editingPageId != editingPageId) return@update it
                it.copy(
                    isSavingPage = false,
                    detailErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
