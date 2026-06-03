package cc.hhhl.client.state

import cc.hhhl.client.api.AchievementApi
import cc.hhhl.client.api.AchievementClaimResult
import cc.hhhl.client.api.AchievementLoadResult
import cc.hhhl.client.model.Achievement
import cc.hhhl.client.repository.AchievementClaimRepositoryResult
import cc.hhhl.client.repository.AchievementRepository
import cc.hhhl.client.repository.AchievementRepositoryResult
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
class AchievementStateHolderTest {
    @Test
    fun refreshStoresAchievements() = runTest {
        val achievement = sampleAchievement("first")
        val holder = AchievementStateHolder(
            repository = fakeRepository(
                refreshResult = AchievementRepositoryResult.Success(listOf(achievement)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(achievement), holder.state.value.achievements)
        assertEquals(1, holder.state.value.unlockedCount)
    }

    @Test
    fun forcedRefreshInvalidatesOlderPendingResult() = runTest {
        val first = CompletableDeferred<AchievementRepositoryResult>()
        val second = CompletableDeferred<AchievementRepositoryResult>()
        var callIndex = 0
        val oldAchievement = sampleAchievement("old")
        val newAchievement = sampleAchievement("new")
        val holder = AchievementStateHolder(
            repository = fakeRepository(
                refreshProvider = {
                    callIndex += 1
                    if (callIndex == 1) first.await() else second.await()
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        runCurrent()
        holder.refresh(force = true)
        runCurrent()
        second.complete(AchievementRepositoryResult.Success(listOf(newAchievement)))
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(newAchievement), holder.state.value.achievements)

        first.complete(AchievementRepositoryResult.Success(listOf(oldAchievement)))
        advanceUntilIdle()

        assertEquals(listOf(newAchievement), holder.state.value.achievements)
    }

    @Test
    fun claimMilestoneRefreshesAchievementsAfterSuccess() = runTest {
        val achievement = sampleAchievement("viewAchievements3min")
        val claimedNames = mutableListOf<String>()
        val holder = AchievementStateHolder(
            repository = fakeRepository(
                refreshResult = AchievementRepositoryResult.Success(listOf(achievement)),
                claimResult = AchievementClaimRepositoryResult.Success,
                onClaim = { claimedNames.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.claimViewAchievementsMilestone()
        advanceUntilIdle()

        assertEquals(listOf("viewAchievements3min"), claimedNames)
        assertEquals(listOf(achievement), holder.state.value.achievements)
    }

    @Test
    fun failedClaimMilestoneCanBeRetried() = runTest {
        val achievement = sampleAchievement("viewAchievements3min")
        val claimResults = ArrayDeque(
            listOf(
                AchievementClaimRepositoryResult.Error("网络超时"),
                AchievementClaimRepositoryResult.Success,
            ),
        )
        val claimedNames = mutableListOf<String>()
        val holder = AchievementStateHolder(
            repository = fakeRepository(
                refreshResult = AchievementRepositoryResult.Success(listOf(achievement)),
                claimProvider = { claimResults.removeFirst() },
                onClaim = { claimedNames.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.claimViewAchievementsMilestone()
        advanceUntilIdle()

        assertEquals(listOf("viewAchievements3min"), claimedNames)
        assertEquals("成就领取失败：网络超时", holder.state.value.errorMessage)

        holder.claimViewAchievementsMilestone()
        advanceUntilIdle()

        assertEquals(listOf("viewAchievements3min", "viewAchievements3min"), claimedNames)
        assertEquals(listOf(achievement), holder.state.value.achievements)
        assertEquals(null, holder.state.value.errorMessage)
    }

    private fun fakeRepository(
        refreshResult: AchievementRepositoryResult = AchievementRepositoryResult.Success(emptyList()),
        claimResult: AchievementClaimRepositoryResult = AchievementClaimRepositoryResult.Success,
        refreshProvider: suspend () -> AchievementRepositoryResult = { refreshResult },
        claimProvider: suspend () -> AchievementClaimRepositoryResult = { claimResult },
        onClaim: (String) -> Unit = {},
    ): AchievementRepository {
        return object : AchievementRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = object : AchievementApi {
                override suspend fun loadAchievements(
                    token: String,
                    userId: String,
                ): AchievementLoadResult = AchievementLoadResult.Success(emptyList())

                override suspend fun claimAchievement(
                    token: String,
                    name: String,
                ): AchievementClaimResult = AchievementClaimResult.Success
            },
        ) {
            override suspend fun refresh(): AchievementRepositoryResult = refreshProvider()

            override suspend fun claim(name: String): AchievementClaimRepositoryResult {
                onClaim(name)
                return claimProvider()
            }
        }
    }

    private fun sampleAchievement(name: String): Achievement {
        return Achievement(
            name = name,
            title = name,
            description = "desc",
            unlockedAt = "2026-05-28T00:00:00Z",
        )
    }
}
