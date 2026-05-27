package cc.hhhl.client.repository

import cc.hhhl.client.api.PageApi
import cc.hhhl.client.api.PageActionResult
import cc.hhhl.client.api.PageLoadResult
import cc.hhhl.client.api.PageMutationResult
import cc.hhhl.client.api.PageShowResult
import cc.hhhl.client.model.Page
import cc.hhhl.client.model.PageBlock
import cc.hhhl.client.model.PageDraft
import cc.hhhl.client.model.PageListKind
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class PageRepositoryTest {
    @Test
    fun refreshPagesUsesTokenAndKind() = runTest {
        val pages = listOf(samplePage("page-1"))
        val calls = mutableListOf<PageCall>()
        val repository = PageRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                pageCalls = calls,
                pageResult = PageLoadResult.Success(pages),
            ),
        )

        val result = repository.refreshPages(PageListKind.Mine)

        assertIs<PagesRepositoryResult.Success>(result)
        assertEquals(listOf(PageCall("token-123", PageListKind.Mine, null)), calls)
        assertEquals(pages, result.pages)
    }

    @Test
    fun loadMorePagesUsesLastPageIdAndDeduplicates() = runTest {
        val first = samplePage("page-1")
        val second = samplePage("page-2")
        val calls = mutableListOf<PageCall>()
        val repository = PageRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                pageCalls = calls,
                pageResult = PageLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMorePages(PageListKind.Featured, currentPages = listOf(first))

        assertIs<PagesRepositoryResult.Success>(result)
        assertEquals(listOf(PageCall("token-123", PageListKind.Featured, first.id)), calls)
        assertEquals(listOf(first, second), result.pages)
    }

    @Test
    fun showPageUsesTokenAndPageId() = runTest {
        val calls = mutableListOf<ShowCall>()
        val page = samplePage("page-1")
        val repository = PageRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                showCalls = calls,
                showResult = PageShowResult.Success(page),
            ),
        )

        val result = repository.showPage("page-1")

        assertIs<PageRepositoryResult.Success>(result)
        assertEquals(listOf(ShowCall("token-123", "page-1")), calls)
        assertEquals(page, result.page)
    }

    @Test
    fun showPageByPathUsesTokenUsernameAndName() = runTest {
        val calls = mutableListOf<ShowPathCall>()
        val page = samplePage("page-1")
        val repository = PageRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                showPathCalls = calls,
                showResult = PageShowResult.Success(page),
            ),
        )

        val result = repository.showPageByPath("@alice", "guide")

        assertIs<PageRepositoryResult.Success>(result)
        assertEquals(listOf(ShowPathCall("token-123", "alice", "guide")), calls)
        assertEquals(page, result.page)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = PageRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<PagesRepositoryResult.Unauthorized>(repository.refreshPages(PageListKind.Featured))
        assertIs<PageRepositoryResult.Unauthorized>(repository.showPage("page-1"))
        assertEquals(0, calls)
    }

    @Test
    fun likeAndUnlikePageUseTokenAndPageId() = runTest {
        val calls = mutableListOf<ActionCall>()
        val repository = PageRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                actionResult = PageActionResult.Success,
            ),
        )

        assertEquals(PageActionRepositoryResult.Success, repository.likePage("page-1"))
        assertEquals(PageActionRepositoryResult.Success, repository.unlikePage("page-1"))
        assertEquals(
            listOf(
                ActionCall("like", "token-123", "page-1"),
                ActionCall("unlike", "token-123", "page-1"),
            ),
            calls,
        )
    }

    @Test
    fun createUpdateAndDeleteUseTokenAndDraft() = runTest {
        val calls = mutableListOf<ActionCall>()
        val draft = PageDraft(title = "Title", name = "title", content = "body")
        val page = samplePage("page-1")
        val repository = PageRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                mutationResult = PageMutationResult.Success(page),
                actionResult = PageActionResult.Success,
            ),
        )

        assertIs<PageRepositoryResult.Success>(repository.createPage(draft))
        assertIs<PageRepositoryResult.Success>(repository.updatePage("page-1", draft))
        assertEquals(PageActionRepositoryResult.Success, repository.deletePage("page-1"))
        assertEquals(
            listOf(
                ActionCall("create", "token-123", ""),
                ActionCall("update", "token-123", "page-1"),
                ActionCall("delete", "token-123", "page-1"),
            ),
            calls,
        )
    }

    private fun fakeApi(
        pageCalls: MutableList<PageCall> = mutableListOf(),
        showCalls: MutableList<ShowCall> = mutableListOf(),
        showPathCalls: MutableList<ShowPathCall> = mutableListOf(),
        actionCalls: MutableList<ActionCall> = mutableListOf(),
        pageResult: PageLoadResult = PageLoadResult.Success(emptyList()),
        showResult: PageShowResult = PageShowResult.Success(samplePage("page-1")),
        actionResult: PageActionResult = PageActionResult.Success,
        mutationResult: PageMutationResult = PageMutationResult.Success(samplePage("page-1")),
        onCall: () -> Unit = {},
    ): PageApi {
        return object : PageApi {
            override suspend fun loadPages(
                token: String,
                kind: PageListKind,
                limit: Int,
                untilId: String?,
            ): PageLoadResult {
                onCall()
                pageCalls.add(PageCall(token, kind, untilId))
                return pageResult
            }

            override suspend fun showPage(
                token: String,
                pageId: String,
            ): PageShowResult {
                onCall()
                showCalls.add(ShowCall(token, pageId))
                return showResult
            }

            override suspend fun showPageByPath(
                token: String,
                username: String,
                name: String,
            ): PageShowResult {
                onCall()
                showPathCalls.add(ShowPathCall(token, username, name))
                return showResult
            }

            override suspend fun likePage(
                token: String,
                pageId: String,
            ): PageActionResult {
                onCall()
                actionCalls.add(ActionCall("like", token, pageId))
                return actionResult
            }

            override suspend fun unlikePage(
                token: String,
                pageId: String,
            ): PageActionResult {
                onCall()
                actionCalls.add(ActionCall("unlike", token, pageId))
                return actionResult
            }

            override suspend fun createPage(
                token: String,
                draft: PageDraft,
            ): PageMutationResult {
                onCall()
                actionCalls.add(ActionCall("create", token, ""))
                return mutationResult
            }

            override suspend fun updatePage(
                token: String,
                pageId: String,
                draft: PageDraft,
            ): PageMutationResult {
                onCall()
                actionCalls.add(ActionCall("update", token, pageId))
                return mutationResult
            }

            override suspend fun deletePage(
                token: String,
                pageId: String,
            ): PageActionResult {
                onCall()
                actionCalls.add(ActionCall("delete", token, pageId))
                return actionResult
            }
        }
    }

    private data class PageCall(
        val token: String,
        val kind: PageListKind,
        val untilId: String?,
    )

    private data class ShowCall(
        val token: String,
        val pageId: String,
    )

    private data class ShowPathCall(
        val token: String,
        val username: String,
        val name: String,
    )

    private data class ActionCall(
        val action: String,
        val token: String,
        val pageId: String,
    )
}

fun samplePage(id: String): Page {
    return Page(
        id = id,
        title = "HHHL 指南",
        name = "guide",
        summary = "欢迎来到 HHHL",
        author = User("user-1", "Alice", "alice", "A"),
        userId = "user-1",
        blocks = listOf(PageBlock("block-1", "text", "第一段")),
        likedCount = 4,
        isLiked = true,
        createdAtLabel = "2026-05-25 06:00",
        updatedAtLabel = "2026-05-25 07:00",
    )
}
