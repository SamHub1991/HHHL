package cc.hhhl.client.state

import cc.hhhl.client.model.FlashListKind
import cc.hhhl.client.model.FlashDraft
import cc.hhhl.client.repository.FlashActionRepositoryResult
import cc.hhhl.client.repository.FlashRepository
import cc.hhhl.client.repository.FlashRepositoryResult
import cc.hhhl.client.repository.FlashesRepositoryResult
import cc.hhhl.client.repository.sampleFlash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class FlashStateHolderTest {
    @Test
    fun refreshFlashesStoresFlashes() = runTest {
        val flash = sampleFlash("flash-1")
        val holder = FlashStateHolder(
            repository = fakeRepository(
                flashesResult = FlashesRepositoryResult.Success(listOf(flash)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshFlashes()
        assertTrue(holder.state.value.isLoadingFlashes)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingFlashes)
        assertEquals(listOf(flash), holder.state.value.flashes)
    }

    @Test
    fun selectKindLoadsThatKindFlashes() = runTest {
        val featured = sampleFlash("flash-featured")
        val mine = sampleFlash("flash-mine")
        val calls = mutableListOf<FlashListKind>()
        val holder = FlashStateHolder(
            repository = sequenceRepository(
                flashesResults = listOf(
                    FlashesRepositoryResult.Success(listOf(featured)),
                    FlashesRepositoryResult.Success(listOf(mine)),
                ),
                flashResult = FlashRepositoryResult.Success(mine),
                onRefreshFlashes = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshFlashes()
        advanceUntilIdle()
        holder.selectKind(FlashListKind.Mine)
        advanceUntilIdle()

        assertEquals(listOf(FlashListKind.Featured, FlashListKind.Mine), calls)
        assertEquals(FlashListKind.Mine, holder.state.value.selectedKind)
        assertEquals(listOf(mine), holder.state.value.flashes)
    }

    @Test
    fun openFlashLoadsDetail() = runTest {
        val flash = sampleFlash("flash-1")
        val calls = mutableListOf<String>()
        val holder = FlashStateHolder(
            repository = fakeRepository(
                flashesResult = FlashesRepositoryResult.Success(listOf(flash)),
                flashResult = FlashRepositoryResult.Success(flash),
                onShowFlash = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFlash("flash-1")
        assertTrue(holder.state.value.isLoadingDetail)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingDetail)
        assertEquals(flash, holder.state.value.selectedFlash)
        assertEquals(listOf("flash-1"), calls)
    }

    @Test
    fun openingAnotherFlashInvalidatesOlderDetailResult() = runTest {
        val first = CompletableDeferred<FlashRepositoryResult>()
        val second = CompletableDeferred<FlashRepositoryResult>()
        val firstFlash = sampleFlash("flash-1")
        val secondFlash = sampleFlash("flash-2")
        val holder = FlashStateHolder(
            repository = fakeRepository(
                flashesResult = FlashesRepositoryResult.Success(emptyList()),
                flashResultProvider = { id -> if (id == "flash-1") first.await() else second.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFlash("flash-1")
        runCurrent()
        holder.openFlash("flash-2")
        runCurrent()
        second.complete(FlashRepositoryResult.Success(secondFlash))
        advanceUntilIdle()

        assertEquals(secondFlash, holder.state.value.selectedFlash)
        assertFalse(holder.state.value.isLoadingDetail)

        first.complete(FlashRepositoryResult.Success(firstFlash))
        advanceUntilIdle()

        assertEquals(secondFlash, holder.state.value.selectedFlash)
    }

    @Test
    fun unauthorizedFlashesLoadMarksRelogin() = runTest {
        val holder = FlashStateHolder(
            repository = fakeRepository(
                flashesResult = FlashesRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshFlashes()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val flash = sampleFlash("flash-1")
        val holder = FlashStateHolder(
            repository = sequenceRepository(
                flashesResults = listOf(
                    FlashesRepositoryResult.Unauthorized,
                    FlashesRepositoryResult.Success(listOf(flash)),
                ),
                flashResult = FlashRepositoryResult.Success(flash),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshFlashes()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refreshFlashes()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(flash), holder.state.value.flashes)
    }

    @Test
    fun toggleLikeLikesSelectedFlashAndUpdatesList() = runTest {
        val flash = sampleFlash("flash-1").copy(isLiked = false, likedCount = 3)
        val calls = mutableListOf<String>()
        val holder = FlashStateHolder(
            repository = fakeRepository(
                flashesResult = FlashesRepositoryResult.Success(listOf(flash)),
                flashResult = FlashRepositoryResult.Success(flash),
                actionResult = FlashActionRepositoryResult.Success,
                onLikeFlash = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshFlashes()
        advanceUntilIdle()
        holder.openFlash(flash.id)
        advanceUntilIdle()
        holder.toggleLikeSelectedFlash()
        assertTrue(holder.state.value.isChangingLike)
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingLike)
        assertEquals(listOf(flash.id), calls)
        assertEquals(true, holder.state.value.selectedFlash?.isLiked)
        assertEquals(4, holder.state.value.selectedFlash?.likedCount)
        assertEquals(true, holder.state.value.flashes.single().isLiked)
        assertEquals(4, holder.state.value.flashes.single().likedCount)
    }

    @Test
    fun closeDetailClearsReloginAfterUnauthorizedLike() = runTest {
        val flash = sampleFlash("flash-1")
        val holder = FlashStateHolder(
            repository = fakeRepository(
                flashesResult = FlashesRepositoryResult.Success(listOf(flash)),
                flashResult = FlashRepositoryResult.Success(flash),
                actionResult = FlashActionRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshFlashes()
        advanceUntilIdle()
        holder.openFlash(flash.id)
        advanceUntilIdle()
        holder.toggleLikeSelectedFlash()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.closeDetail()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(null, holder.state.value.selectedFlash)
    }

    @Test
    fun saveCreateDraftAddsFlashAndSelectsMine() = runTest {
        val created = sampleFlash("flash-new")
        val calls = mutableListOf<FlashDraft>()
        val holder = FlashStateHolder(
            repository = fakeRepository(
                flashesResult = FlashesRepositoryResult.Success(emptyList()),
                flashResult = FlashRepositoryResult.Success(created),
                onCreateFlash = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )
        val draft = FlashDraft(title = "新 Play", script = "Ui:render([])")

        holder.startCreatingFlash()
        holder.updateDraft(draft)
        holder.saveDraft()
        advanceUntilIdle()

        assertEquals(listOf(draft), calls)
        assertEquals(FlashListKind.Mine, holder.state.value.selectedKind)
        assertEquals(created, holder.state.value.selectedFlash)
        assertEquals(listOf(created), holder.state.value.flashes)
        assertEquals(null, holder.state.value.draftMode)
    }

    @Test
    fun deleteSelectedFlashRemovesItFromListAndDetail() = runTest {
        val flash = sampleFlash("flash-1")
        val calls = mutableListOf<String>()
        val holder = FlashStateHolder(
            repository = fakeRepository(
                flashesResult = FlashesRepositoryResult.Success(listOf(flash)),
                flashResult = FlashRepositoryResult.Success(flash),
                actionResult = FlashActionRepositoryResult.Success,
                onDeleteFlash = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshFlashes()
        advanceUntilIdle()
        holder.openFlash(flash.id)
        advanceUntilIdle()
        holder.deleteSelectedFlash()
        advanceUntilIdle()

        assertEquals(listOf(flash.id), calls)
        assertEquals(emptyList(), holder.state.value.flashes)
        assertEquals(null, holder.state.value.selectedFlash)
    }

    private fun fakeRepository(
        flashesResult: FlashesRepositoryResult,
        flashResult: FlashRepositoryResult = FlashRepositoryResult.Success(sampleFlash("flash-1")),
        actionResult: FlashActionRepositoryResult = FlashActionRepositoryResult.Success,
        onRefreshFlashes: (FlashListKind) -> Unit = {},
        onShowFlash: (String) -> Unit = {},
        onLikeFlash: (String) -> Unit = {},
        onUnlikeFlash: (String) -> Unit = {},
        onCreateFlash: (FlashDraft) -> Unit = {},
        onUpdateFlash: (String, FlashDraft) -> Unit = { _, _ -> },
        onDeleteFlash: (String) -> Unit = {},
        flashResultProvider: suspend (String) -> FlashRepositoryResult = { flashResult },
    ): FlashRepository {
        return sequenceRepository(
            flashesResults = listOf(flashesResult),
            flashResult = flashResult,
            actionResult = actionResult,
            onRefreshFlashes = onRefreshFlashes,
            onShowFlash = onShowFlash,
            onLikeFlash = onLikeFlash,
            onUnlikeFlash = onUnlikeFlash,
            onCreateFlash = onCreateFlash,
            onUpdateFlash = onUpdateFlash,
            onDeleteFlash = onDeleteFlash,
            flashResultProvider = flashResultProvider,
        )
    }

    private fun sequenceRepository(
        flashesResults: List<FlashesRepositoryResult>,
        flashResult: FlashRepositoryResult,
        actionResult: FlashActionRepositoryResult = FlashActionRepositoryResult.Success,
        onRefreshFlashes: (FlashListKind) -> Unit = {},
        onShowFlash: (String) -> Unit = {},
        onLikeFlash: (String) -> Unit = {},
        onUnlikeFlash: (String) -> Unit = {},
        onCreateFlash: (FlashDraft) -> Unit = {},
        onUpdateFlash: (String, FlashDraft) -> Unit = { _, _ -> },
        onDeleteFlash: (String) -> Unit = {},
        flashResultProvider: suspend (String) -> FlashRepositoryResult = { flashResult },
    ): FlashRepository {
        var flashResultIndex = 0
        return object : FlashRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.FlashApi {
                override suspend fun loadFlashes(
                    token: String,
                    kind: FlashListKind,
                    limit: Int,
                    untilId: String?,
                    offset: Int,
                ): cc.hhhl.client.api.FlashLoadResult {
                    return cc.hhhl.client.api.FlashLoadResult.Success(emptyList())
                }

                override suspend fun showFlash(
                    token: String,
                    flashId: String,
                ): cc.hhhl.client.api.FlashShowResult {
                    return cc.hhhl.client.api.FlashShowResult.Success(sampleFlash("flash-1"))
                }

                override suspend fun likeFlash(
                    token: String,
                    flashId: String,
                ): cc.hhhl.client.api.FlashActionResult {
                    return cc.hhhl.client.api.FlashActionResult.Success
                }

                override suspend fun unlikeFlash(
                    token: String,
                    flashId: String,
                ): cc.hhhl.client.api.FlashActionResult {
                    return cc.hhhl.client.api.FlashActionResult.Success
                }

                override suspend fun createFlash(
                    token: String,
                    draft: FlashDraft,
                ): cc.hhhl.client.api.FlashMutationResult {
                    return cc.hhhl.client.api.FlashMutationResult.Success(sampleFlash("flash-1"))
                }

                override suspend fun updateFlash(
                    token: String,
                    flashId: String,
                    draft: FlashDraft,
                ): cc.hhhl.client.api.FlashMutationResult {
                    return cc.hhhl.client.api.FlashMutationResult.Success(sampleFlash("flash-1"))
                }

                override suspend fun deleteFlash(
                    token: String,
                    flashId: String,
                ): cc.hhhl.client.api.FlashActionResult {
                    return cc.hhhl.client.api.FlashActionResult.Success
                }
            },
        ) {
            override suspend fun refreshFlashes(kind: FlashListKind): FlashesRepositoryResult {
                onRefreshFlashes(kind)
                val result = flashesResults.getOrElse(flashResultIndex) { flashesResults.last() }
                flashResultIndex += 1
                return result
            }

            override suspend fun showFlash(flashId: String): FlashRepositoryResult {
                onShowFlash(flashId)
                return flashResultProvider(flashId)
            }

            override suspend fun likeFlash(flashId: String): FlashActionRepositoryResult {
                onLikeFlash(flashId)
                return actionResult
            }

            override suspend fun unlikeFlash(flashId: String): FlashActionRepositoryResult {
                onUnlikeFlash(flashId)
                return actionResult
            }

            override suspend fun createFlash(draft: FlashDraft): FlashRepositoryResult {
                onCreateFlash(draft)
                return flashResultProvider("create")
            }

            override suspend fun updateFlash(
                flashId: String,
                draft: FlashDraft,
            ): FlashRepositoryResult {
                onUpdateFlash(flashId, draft)
                return flashResultProvider(flashId)
            }

            override suspend fun deleteFlash(flashId: String): FlashActionRepositoryResult {
                onDeleteFlash(flashId)
                return actionResult
            }
        }
    }
}
