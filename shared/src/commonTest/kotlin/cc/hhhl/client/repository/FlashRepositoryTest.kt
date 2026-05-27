package cc.hhhl.client.repository

import cc.hhhl.client.api.FlashApi
import cc.hhhl.client.api.FlashActionResult
import cc.hhhl.client.api.FlashLoadResult
import cc.hhhl.client.api.FlashMutationResult
import cc.hhhl.client.api.FlashShowResult
import cc.hhhl.client.model.Flash
import cc.hhhl.client.model.FlashDraft
import cc.hhhl.client.model.FlashListKind
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class FlashRepositoryTest {
    @Test
    fun refreshFlashesUsesTokenAndKind() = runTest {
        val flashes = listOf(sampleFlash("flash-1"))
        val calls = mutableListOf<FlashCall>()
        val repository = FlashRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                flashCalls = calls,
                flashResult = FlashLoadResult.Success(flashes),
            ),
        )

        val result = repository.refreshFlashes(FlashListKind.Mine)

        assertIs<FlashesRepositoryResult.Success>(result)
        assertEquals(listOf(FlashCall("token-123", FlashListKind.Mine, null, 0)), calls)
        assertEquals(flashes, result.flashes)
    }

    @Test
    fun loadMoreFeaturedUsesOffsetAndDeduplicates() = runTest {
        val first = sampleFlash("flash-1")
        val second = sampleFlash("flash-2")
        val calls = mutableListOf<FlashCall>()
        val repository = FlashRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                flashCalls = calls,
                flashResult = FlashLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMoreFlashes(FlashListKind.Featured, currentFlashes = listOf(first))

        assertIs<FlashesRepositoryResult.Success>(result)
        assertEquals(listOf(FlashCall("token-123", FlashListKind.Featured, null, 1)), calls)
        assertEquals(listOf(first, second), result.flashes)
    }

    @Test
    fun loadMoreMineUsesLastFlashId() = runTest {
        val first = sampleFlash("flash-1")
        val second = sampleFlash("flash-2")
        val calls = mutableListOf<FlashCall>()
        val repository = FlashRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                flashCalls = calls,
                flashResult = FlashLoadResult.Success(listOf(second)),
            ),
        )

        val result = repository.loadMoreFlashes(FlashListKind.Mine, currentFlashes = listOf(first))

        assertIs<FlashesRepositoryResult.Success>(result)
        assertEquals(listOf(FlashCall("token-123", FlashListKind.Mine, first.id, 0)), calls)
        assertEquals(listOf(first, second), result.flashes)
    }

    @Test
    fun showFlashUsesTokenAndFlashId() = runTest {
        val calls = mutableListOf<ShowCall>()
        val flash = sampleFlash("flash-1")
        val repository = FlashRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                showCalls = calls,
                showResult = FlashShowResult.Success(flash),
            ),
        )

        val result = repository.showFlash("flash-1")

        assertIs<FlashRepositoryResult.Success>(result)
        assertEquals(listOf(ShowCall("token-123", "flash-1")), calls)
        assertEquals(flash, result.flash)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = FlashRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<FlashesRepositoryResult.Unauthorized>(repository.refreshFlashes(FlashListKind.Featured))
        assertIs<FlashRepositoryResult.Unauthorized>(repository.showFlash("flash-1"))
        assertEquals(0, calls)
    }

    @Test
    fun likeAndUnlikeFlashUseTokenAndFlashId() = runTest {
        val calls = mutableListOf<ActionCall>()
        val repository = FlashRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                actionResult = FlashActionResult.Success,
            ),
        )

        assertEquals(FlashActionRepositoryResult.Success, repository.likeFlash("flash-1"))
        assertEquals(FlashActionRepositoryResult.Success, repository.unlikeFlash("flash-1"))
        assertEquals(
            listOf(
                ActionCall("like", "token-123", "flash-1"),
                ActionCall("unlike", "token-123", "flash-1"),
            ),
            calls,
        )
    }

    @Test
    fun createUpdateAndDeleteUseTokenDraftAndFlashId() = runTest {
        val calls = mutableListOf<MutationCall>()
        val actionCalls = mutableListOf<ActionCall>()
        val flash = sampleFlash("flash-1")
        val draft = FlashDraft(
            title = "新 Play",
            summary = "摘要",
            script = "Ui:render([])",
            visibility = "private",
            permissions = listOf("read:account"),
        )
        val repository = FlashRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                mutationCalls = calls,
                actionCalls = actionCalls,
                mutationResult = FlashMutationResult.Success(flash),
                actionResult = FlashActionResult.Success,
            ),
        )

        assertIs<FlashRepositoryResult.Success>(repository.createFlash(draft))
        assertIs<FlashRepositoryResult.Success>(repository.updateFlash("flash-1", draft))
        assertEquals(FlashActionRepositoryResult.Success, repository.deleteFlash("flash-1"))
        assertEquals(
            listOf(
                MutationCall("create", "token-123", null, draft),
                MutationCall("update", "token-123", "flash-1", draft),
            ),
            calls,
        )
        assertEquals(listOf(ActionCall("delete", "token-123", "flash-1")), actionCalls)
    }

    private fun fakeApi(
        flashCalls: MutableList<FlashCall> = mutableListOf(),
        showCalls: MutableList<ShowCall> = mutableListOf(),
        actionCalls: MutableList<ActionCall> = mutableListOf(),
        mutationCalls: MutableList<MutationCall> = mutableListOf(),
        flashResult: FlashLoadResult = FlashLoadResult.Success(emptyList()),
        showResult: FlashShowResult = FlashShowResult.Success(sampleFlash("flash-1")),
        actionResult: FlashActionResult = FlashActionResult.Success,
        mutationResult: FlashMutationResult = FlashMutationResult.Success(sampleFlash("flash-1")),
        onCall: () -> Unit = {},
    ): FlashApi {
        return object : FlashApi {
            override suspend fun loadFlashes(
                token: String,
                kind: FlashListKind,
                limit: Int,
                untilId: String?,
                offset: Int,
            ): FlashLoadResult {
                onCall()
                flashCalls.add(FlashCall(token, kind, untilId, offset))
                return flashResult
            }

            override suspend fun showFlash(
                token: String,
                flashId: String,
            ): FlashShowResult {
                onCall()
                showCalls.add(ShowCall(token, flashId))
                return showResult
            }

            override suspend fun likeFlash(
                token: String,
                flashId: String,
            ): FlashActionResult {
                onCall()
                actionCalls.add(ActionCall("like", token, flashId))
                return actionResult
            }

            override suspend fun unlikeFlash(
                token: String,
                flashId: String,
            ): FlashActionResult {
                onCall()
                actionCalls.add(ActionCall("unlike", token, flashId))
                return actionResult
            }

            override suspend fun createFlash(
                token: String,
                draft: FlashDraft,
            ): FlashMutationResult {
                onCall()
                mutationCalls.add(MutationCall("create", token, null, draft))
                return mutationResult
            }

            override suspend fun updateFlash(
                token: String,
                flashId: String,
                draft: FlashDraft,
            ): FlashMutationResult {
                onCall()
                mutationCalls.add(MutationCall("update", token, flashId, draft))
                return mutationResult
            }

            override suspend fun deleteFlash(
                token: String,
                flashId: String,
            ): FlashActionResult {
                onCall()
                actionCalls.add(ActionCall("delete", token, flashId))
                return actionResult
            }
        }
    }

    private data class FlashCall(
        val token: String,
        val kind: FlashListKind,
        val untilId: String?,
        val offset: Int,
    )

    private data class ShowCall(
        val token: String,
        val flashId: String,
    )

    private data class ActionCall(
        val action: String,
        val token: String,
        val flashId: String,
    )

    private data class MutationCall(
        val action: String,
        val token: String,
        val flashId: String?,
        val draft: FlashDraft,
    )
}

fun sampleFlash(id: String): Flash {
    return Flash(
        id = id,
        title = "互动名片",
        summary = "一个 Sharkey Play 示例",
        script = "Ui:render([Ui:C:text({text: \"Hello HHHL\"})])",
        visibility = "public",
        author = User("user-1", "Alice", "alice", "A"),
        userId = "user-1",
        likedCount = 3,
        isLiked = true,
        createdAtLabel = "2026-05-25 06:00",
        updatedAtLabel = "2026-05-25 07:00",
    )
}
