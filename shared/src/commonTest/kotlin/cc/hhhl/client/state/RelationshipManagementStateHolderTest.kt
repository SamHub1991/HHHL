package cc.hhhl.client.state

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.UserRelationshipListEntry
import cc.hhhl.client.repository.UserRelationshipListRepositoryResult
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RelationshipManagementStateHolderTest {
    @Test
    fun refreshKeepsSpecialCareUsersByDefault() = runTest {
        val entry = relationshipEntry("special-care-user-1", "user-1")
        val holder = holder()

        holder.updateSpecialCareUsers(listOf(entry))
        holder.refresh()

        assertEquals(RelationshipManagementTab.SpecialCare, holder.state.value.selectedTab)
        assertEquals(listOf(entry), holder.state.value.specialCareUsers)
        assertEquals(false, holder.state.value.isLoading)
    }

    @Test
    fun selectMutedTabLoadsMutedUsers() = runTest {
        val entry = relationshipEntry("mute-1", "user-1")
        val holder = holder(
            mutedResult = UserRelationshipListRepositoryResult.Success(
                entries = listOf(entry),
                endReached = true,
            ),
        )

        holder.selectTab(RelationshipManagementTab.Muted)

        assertEquals(RelationshipManagementTab.Muted, holder.state.value.selectedTab)
        assertEquals(listOf(entry), holder.state.value.mutedUsers)
        assertEquals(true, holder.state.value.mutedEndReached)
        assertEquals(false, holder.state.value.isLoading)
    }

    @Test
    fun removeSpecialCareRemovesLocalEntry() = runTest {
        val entry = relationshipEntry("special-care-user-2", "user-2")
        val holder = holder()

        holder.updateSpecialCareUsers(listOf(entry))
        holder.removeRelationship("user-2")

        assertEquals(emptyList(), holder.state.value.specialCareUsers)
        assertEquals("已取消特别关心", holder.state.value.message)
    }

    @Test
    fun removeRelationshipUsesSelectedTabAndRemovesLocalEntry() = runTest {
        val entry = relationshipEntry("mute-1", "user-1")
        val holder = holder(
            mutedResult = UserRelationshipListRepositoryResult.Success(
                entries = listOf(entry),
                endReached = true,
            ),
            mutationResult = UserRelationshipRepositoryResult.Success,
        )

        holder.selectTab(RelationshipManagementTab.Muted)
        holder.removeRelationship("user-1")

        assertEquals(emptyList(), holder.state.value.mutedUsers)
        assertEquals("已取消静音", holder.state.value.message)
    }

    @Test
    fun switchingToSpecialCareInvalidatesPendingMutedRefresh() = runTest {
        val mutedResult = CompletableDeferred<UserRelationshipListRepositoryResult>()
        val holder = holder(mutedResult = mutedResult)

        holder.selectTab(RelationshipManagementTab.Muted)
        assertEquals(true, holder.state.value.isLoading)

        holder.selectTab(RelationshipManagementTab.SpecialCare)
        assertEquals(false, holder.state.value.isLoading)

        val entry = relationshipEntry("mute-2", "user-2")
        mutedResult.complete(UserRelationshipListRepositoryResult.Success(listOf(entry), endReached = true))
        advanceUntilIdle()

        assertEquals(RelationshipManagementTab.SpecialCare, holder.state.value.selectedTab)
        assertEquals(emptyList(), holder.state.value.mutedUsers)
    }

    private fun TestScope.holder(
        mutedResult: UserRelationshipListRepositoryResult = UserRelationshipListRepositoryResult.Success(emptyList(), true),
        mutationResult: UserRelationshipRepositoryResult = UserRelationshipRepositoryResult.Success,
    ): RelationshipManagementStateHolder {
        val repository = object : UserRelationshipRepository(tokenProvider = { "token" }) {
            override suspend fun loadMutedUsers(
                currentEntries: List<UserRelationshipListEntry>,
            ): UserRelationshipListRepositoryResult = mutedResult

            override suspend fun unmute(userId: String): UserRelationshipRepositoryResult = mutationResult
        }
        return RelationshipManagementStateHolder(
            repository = repository,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        )
    }

    private fun TestScope.holder(
        mutedResult: CompletableDeferred<UserRelationshipListRepositoryResult>,
        mutationResult: UserRelationshipRepositoryResult = UserRelationshipRepositoryResult.Success,
    ): RelationshipManagementStateHolder {
        val repository = object : UserRelationshipRepository(tokenProvider = { "token" }) {
            override suspend fun loadMutedUsers(
                currentEntries: List<UserRelationshipListEntry>,
            ): UserRelationshipListRepositoryResult = mutedResult.await()

            override suspend fun unmute(userId: String): UserRelationshipRepositoryResult = mutationResult
        }
        return RelationshipManagementStateHolder(
            repository = repository,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        )
    }

    private fun relationshipEntry(id: String, userId: String): UserRelationshipListEntry {
        return UserRelationshipListEntry(
            id = id,
            user = FakeData.me.copy(id = userId),
            createdAtLabel = "2026-05-26 10:00",
        )
    }
}
