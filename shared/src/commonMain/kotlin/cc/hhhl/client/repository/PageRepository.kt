package cc.hhhl.client.repository

import cc.hhhl.client.api.PageApi
import cc.hhhl.client.api.PageActionResult
import cc.hhhl.client.api.PageLoadResult
import cc.hhhl.client.api.PageShowResult
import cc.hhhl.client.api.SharkeyPageApi
import cc.hhhl.client.model.Page
import cc.hhhl.client.model.PageListKind

open class PageRepository(
    private val tokenProvider: () -> String?,
    private val api: PageApi = SharkeyPageApi(),
) {
    open suspend fun refreshPages(kind: PageListKind): PagesRepositoryResult {
        return loadPages(
            kind = kind,
            currentPages = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMorePages(
        kind: PageListKind,
        currentPages: List<Page>,
    ): PagesRepositoryResult {
        return loadPages(
            kind = kind,
            currentPages = currentPages,
            untilId = currentPages.lastOrNull()?.id,
        )
    }

    open suspend fun showPage(pageId: String): PageRepositoryResult {
        val cleanPageId = pageId.trim()
        if (cleanPageId.isEmpty()) {
            return PageRepositoryResult.Error("无法读取页面")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return PageRepositoryResult.Unauthorized

        return when (val result = api.showPage(token, cleanPageId)) {
            is PageShowResult.Success -> PageRepositoryResult.Success(result.page)
            PageShowResult.Unauthorized -> PageRepositoryResult.Unauthorized
            is PageShowResult.NetworkError -> {
                PageRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is PageShowResult.ServerError -> PageRepositoryResult.Error(result.message)
        }
    }

    open suspend fun likePage(pageId: String): PageActionRepositoryResult {
        return performPageAction(pageId) { token, cleanPageId ->
            api.likePage(token, cleanPageId)
        }
    }

    open suspend fun unlikePage(pageId: String): PageActionRepositoryResult {
        return performPageAction(pageId) { token, cleanPageId ->
            api.unlikePage(token, cleanPageId)
        }
    }

    private suspend fun loadPages(
        kind: PageListKind,
        currentPages: List<Page>,
        untilId: String?,
    ): PagesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return PagesRepositoryResult.Unauthorized

        return when (
            val result = api.loadPages(
                token = token,
                kind = kind,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
            )
        ) {
            is PageLoadResult.Success -> PagesRepositoryResult.Success(
                pages = (currentPages + result.pages).distinctBy { it.id },
                endReached = result.pages.isEmpty(),
            )
            PageLoadResult.Unauthorized -> PagesRepositoryResult.Unauthorized
            is PageLoadResult.NetworkError -> {
                PagesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is PageLoadResult.ServerError -> PagesRepositoryResult.Error(result.message)
        }
    }

    private suspend fun performPageAction(
        pageId: String,
        action: suspend (String, String) -> PageActionResult,
    ): PageActionRepositoryResult {
        val cleanPageId = pageId.trim()
        if (cleanPageId.isEmpty()) {
            return PageActionRepositoryResult.Error("无法读取页面")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return PageActionRepositoryResult.Unauthorized

        return when (val result = action(token, cleanPageId)) {
            PageActionResult.Success -> PageActionRepositoryResult.Success
            PageActionResult.Unauthorized -> PageActionRepositoryResult.Unauthorized
            is PageActionResult.NetworkError -> {
                PageActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is PageActionResult.ServerError -> PageActionRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface PagesRepositoryResult {
    data class Success(
        val pages: List<Page>,
        val endReached: Boolean = false,
    ) : PagesRepositoryResult

    data object Unauthorized : PagesRepositoryResult

    data class Error(val message: String) : PagesRepositoryResult
}

sealed interface PageRepositoryResult {
    data class Success(val page: Page) : PageRepositoryResult

    data object Unauthorized : PageRepositoryResult

    data class Error(val message: String) : PageRepositoryResult
}

sealed interface PageActionRepositoryResult {
    data object Success : PageActionRepositoryResult

    data object Unauthorized : PageActionRepositoryResult

    data class Error(val message: String) : PageActionRepositoryResult
}
