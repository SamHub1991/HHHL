package cc.hhhl.client.state

import cc.hhhl.client.model.Page
import cc.hhhl.client.model.PageDraft
import cc.hhhl.client.model.PageListKind
import cc.hhhl.client.repository.PageActionRepositoryResult
import cc.hhhl.client.repository.PageRepository
import cc.hhhl.client.repository.PageRepositoryResult
import cc.hhhl.client.repository.PagesRepositoryResult
import cc.hhhl.client.repository.samplePage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PageStateHolderTest {
    @Test
    fun refreshPagesStoresPages() = runTest {
        val page = samplePage("page-1")
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Success(listOf(page)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPages()
        assertTrue(holder.state.value.isLoadingPages)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingPages)
        assertEquals(listOf(page), holder.state.value.pages)
    }

    @Test
    fun selectKindLoadsThatKindPages() = runTest {
        val featured = samplePage("page-featured")
        val mine = samplePage("page-mine")
        val calls = mutableListOf<PageListKind>()
        val holder = PageStateHolder(
            repository = sequenceRepository(
                pagesResults = listOf(
                    PagesRepositoryResult.Success(listOf(featured)),
                    PagesRepositoryResult.Success(listOf(mine)),
                ),
                pageResult = PageRepositoryResult.Success(mine),
                onRefreshPages = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPages()
        advanceUntilIdle()
        holder.selectKind(PageListKind.Mine)
        advanceUntilIdle()

        assertEquals(listOf(PageListKind.Featured, PageListKind.Mine), calls)
        assertEquals(PageListKind.Mine, holder.state.value.selectedKind)
        assertEquals(listOf(mine), holder.state.value.pages)
    }

    @Test
    fun openPageLoadsDetail() = runTest {
        val page = samplePage("page-1")
        val calls = mutableListOf<String>()
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Success(listOf(page)),
                pageResult = PageRepositoryResult.Success(page),
                onShowPage = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openPage("page-1")
        assertTrue(holder.state.value.isLoadingDetail)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingDetail)
        assertEquals(page, holder.state.value.selectedPage)
        assertEquals(listOf("page-1"), calls)
    }

    @Test
    fun openingAnotherPageInvalidatesOlderDetailResult() = runTest {
        val first = CompletableDeferred<PageRepositoryResult>()
        val second = CompletableDeferred<PageRepositoryResult>()
        val pageOne = samplePage("page-1")
        val pageTwo = samplePage("page-2")
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Success(emptyList()),
                pageResultProvider = { id -> if (id == "page-1") first.await() else second.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openPage("page-1")
        runCurrent()
        holder.openPage("page-2")
        runCurrent()
        second.complete(PageRepositoryResult.Success(pageTwo))
        advanceUntilIdle()

        assertEquals(pageTwo, holder.state.value.selectedPage)
        assertFalse(holder.state.value.isLoadingDetail)

        first.complete(PageRepositoryResult.Success(pageOne))
        advanceUntilIdle()

        assertEquals(pageTwo, holder.state.value.selectedPage)
    }

    @Test
    fun closeDetailInvalidatesPendingDetailResult() = runTest {
        val pending = CompletableDeferred<PageRepositoryResult>()
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Success(emptyList()),
                pageResultProvider = { pending.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openPage("page-1")
        runCurrent()
        assertTrue(holder.state.value.isLoadingDetail)

        holder.closeDetail()
        pending.complete(PageRepositoryResult.Success(samplePage("page-1")))
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingDetail)
        assertEquals(null, holder.state.value.selectedPage)
    }

    @Test
    fun unauthorizedPagesLoadMarksRelogin() = runTest {
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPages()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val page = samplePage("page-1")
        val holder = PageStateHolder(
            repository = sequenceRepository(
                pagesResults = listOf(
                    PagesRepositoryResult.Unauthorized,
                    PagesRepositoryResult.Success(listOf(page)),
                ),
                pageResult = PageRepositoryResult.Success(page),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPages()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refreshPages()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(page), holder.state.value.pages)
    }

    @Test
    fun toggleLikeLikesSelectedPageAndUpdatesList() = runTest {
        val page = samplePage("page-1").copy(isLiked = false, likedCount = 4)
        val calls = mutableListOf<String>()
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Success(listOf(page)),
                pageResult = PageRepositoryResult.Success(page),
                actionResult = PageActionRepositoryResult.Success,
                onLikePage = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPages()
        advanceUntilIdle()
        holder.openPage(page.id)
        advanceUntilIdle()
        holder.toggleLikeSelectedPage()
        assertTrue(holder.state.value.isChangingLike)
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingLike)
        assertEquals(listOf(page.id), calls)
        assertEquals(true, holder.state.value.selectedPage?.isLiked)
        assertEquals(5, holder.state.value.selectedPage?.likedCount)
        assertEquals(true, holder.state.value.pages.single().isLiked)
        assertEquals(5, holder.state.value.pages.single().likedCount)
    }

    @Test
    fun closeDetailClearsReloginAfterUnauthorizedLike() = runTest {
        val page = samplePage("page-1")
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Success(listOf(page)),
                pageResult = PageRepositoryResult.Success(page),
                actionResult = PageActionRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPages()
        advanceUntilIdle()
        holder.openPage(page.id)
        advanceUntilIdle()
        holder.toggleLikeSelectedPage()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.closeDetail()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(null, holder.state.value.selectedPage)
    }

    @Test
    fun createPageStoresCreatedPageAndSelectsMine() = runTest {
        val page = samplePage("page-created")
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Success(emptyList()),
                pageResult = PageRepositoryResult.Success(page),
            ),
            scope = TestScope(testScheduler),
        )

        holder.startCreatingPage()
        holder.updateDraft(PageDraft(title = "Title", name = "title", content = "body"))
        holder.saveEditingPage()
        advanceUntilIdle()

        assertEquals(page, holder.state.value.selectedPage)
        assertEquals(listOf(page), holder.state.value.pages)
        assertEquals(PageListKind.Mine, holder.state.value.selectedKind)
        assertEquals(null, holder.state.value.editingDraft)
    }

    @Test
    fun cancelEditingInvalidatesPendingSaveResult() = runTest {
        val pending = CompletableDeferred<PageRepositoryResult>()
        val holder = PageStateHolder(
            repository = fakeRepository(
                pagesResult = PagesRepositoryResult.Success(emptyList()),
                pageResultProvider = { pending.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.startCreatingPage()
        holder.updateDraft(PageDraft(title = "Title", name = "title", content = "body"))
        holder.saveEditingPage()
        runCurrent()
        assertTrue(holder.state.value.isSavingPage)

        holder.cancelEditingPage()
        pending.complete(PageRepositoryResult.Success(samplePage("page-created")))
        advanceUntilIdle()

        assertFalse(holder.state.value.isSavingPage)
        assertEquals(null, holder.state.value.selectedPage)
        assertEquals(emptyList(), holder.state.value.pages)
    }

    private fun fakeRepository(
        pagesResult: PagesRepositoryResult,
        pageResult: PageRepositoryResult = PageRepositoryResult.Success(samplePage("page-1")),
        actionResult: PageActionRepositoryResult = PageActionRepositoryResult.Success,
        onRefreshPages: (PageListKind) -> Unit = {},
        onShowPage: (String) -> Unit = {},
        onLikePage: (String) -> Unit = {},
        onUnlikePage: (String) -> Unit = {},
        pageResultProvider: suspend (String) -> PageRepositoryResult = { pageResult },
    ): PageRepository {
        return sequenceRepository(
            pagesResults = listOf(pagesResult),
            pageResult = pageResult,
            actionResult = actionResult,
            onRefreshPages = onRefreshPages,
            onShowPage = onShowPage,
            onLikePage = onLikePage,
            onUnlikePage = onUnlikePage,
            pageResultProvider = pageResultProvider,
        )
    }

    private fun sequenceRepository(
        pagesResults: List<PagesRepositoryResult>,
        pageResult: PageRepositoryResult,
        actionResult: PageActionRepositoryResult = PageActionRepositoryResult.Success,
        onRefreshPages: (PageListKind) -> Unit = {},
        onShowPage: (String) -> Unit = {},
        onLikePage: (String) -> Unit = {},
        onUnlikePage: (String) -> Unit = {},
        pageResultProvider: suspend (String) -> PageRepositoryResult = { pageResult },
    ): PageRepository {
        var pageResultIndex = 0
        return object : PageRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.PageApi {
                override suspend fun loadPages(
                    token: String,
                    kind: PageListKind,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.PageLoadResult {
                    return cc.hhhl.client.api.PageLoadResult.Success(emptyList())
                }

                override suspend fun showPage(
                    token: String,
                    pageId: String,
                ): cc.hhhl.client.api.PageShowResult {
                    return cc.hhhl.client.api.PageShowResult.Success(samplePage("page-1"))
                }

                override suspend fun showPageByPath(
                    token: String,
                    username: String,
                    name: String,
                ): cc.hhhl.client.api.PageShowResult {
                    return cc.hhhl.client.api.PageShowResult.Success(samplePage("page-1"))
                }

                override suspend fun likePage(
                    token: String,
                    pageId: String,
                ): cc.hhhl.client.api.PageActionResult {
                    return cc.hhhl.client.api.PageActionResult.Success
                }

                override suspend fun unlikePage(
                    token: String,
                    pageId: String,
                ): cc.hhhl.client.api.PageActionResult {
                    return cc.hhhl.client.api.PageActionResult.Success
                }

                override suspend fun createPage(
                    token: String,
                    draft: PageDraft,
                ): cc.hhhl.client.api.PageMutationResult {
                    return cc.hhhl.client.api.PageMutationResult.Success(samplePage("page-1"))
                }

                override suspend fun updatePage(
                    token: String,
                    pageId: String,
                    draft: PageDraft,
                ): cc.hhhl.client.api.PageMutationResult {
                    return cc.hhhl.client.api.PageMutationResult.Success(samplePage("page-1"))
                }

                override suspend fun deletePage(
                    token: String,
                    pageId: String,
                ): cc.hhhl.client.api.PageActionResult {
                    return cc.hhhl.client.api.PageActionResult.Success
                }
            },
        ) {
            override suspend fun refreshPages(kind: PageListKind): PagesRepositoryResult {
                onRefreshPages(kind)
                val result = pagesResults.getOrElse(pageResultIndex) { pagesResults.last() }
                pageResultIndex += 1
                return result
            }

            override suspend fun showPage(pageId: String): PageRepositoryResult {
                onShowPage(pageId)
                return pageResultProvider(pageId)
            }

            override suspend fun showPageByPath(
                username: String,
                name: String,
            ): PageRepositoryResult {
                onShowPage("$username/$name")
                return pageResultProvider("$username/$name")
            }

            override suspend fun likePage(pageId: String): PageActionRepositoryResult {
                onLikePage(pageId)
                return actionResult
            }

            override suspend fun unlikePage(pageId: String): PageActionRepositoryResult {
                onUnlikePage(pageId)
                return actionResult
            }

            override suspend fun createPage(draft: PageDraft): PageRepositoryResult {
                return pageResultProvider("create")
            }

            override suspend fun updatePage(
                pageId: String,
                draft: PageDraft,
            ): PageRepositoryResult {
                return pageResultProvider(pageId)
            }

            override suspend fun deletePage(pageId: String): PageActionRepositoryResult {
                return actionResult
            }
        }
    }
}
