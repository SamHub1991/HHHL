package cc.hhhl.client.state

import cc.hhhl.client.model.Page
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun fakeRepository(
        pagesResult: PagesRepositoryResult,
        pageResult: PageRepositoryResult = PageRepositoryResult.Success(samplePage("page-1")),
        actionResult: PageActionRepositoryResult = PageActionRepositoryResult.Success,
        onRefreshPages: (PageListKind) -> Unit = {},
        onShowPage: (String) -> Unit = {},
        onLikePage: (String) -> Unit = {},
        onUnlikePage: (String) -> Unit = {},
    ): PageRepository {
        return sequenceRepository(
            pagesResults = listOf(pagesResult),
            pageResult = pageResult,
            actionResult = actionResult,
            onRefreshPages = onRefreshPages,
            onShowPage = onShowPage,
            onLikePage = onLikePage,
            onUnlikePage = onUnlikePage,
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
                return pageResult
            }

            override suspend fun likePage(pageId: String): PageActionRepositoryResult {
                onLikePage(pageId)
                return actionResult
            }

            override suspend fun unlikePage(pageId: String): PageActionRepositoryResult {
                onUnlikePage(pageId)
                return actionResult
            }
        }
    }
}
