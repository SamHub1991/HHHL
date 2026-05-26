package cc.hhhl.client.state

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.UserRelationshipListEntry
import cc.hhhl.client.repository.UserRelationshipListRepositoryResult
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RelationshipManagementStateHolderTest {
    @Test
    fun refreshLoadsMutedUsersByDefault() = runTest {
        val entry = relationshipEntry("mute-1", "user-1")
        val holder = holder(
            mutedResult = UserRelationshipListRepositoryResult.Success(
                entries = listOf(entry),
                endReached = true,
            ),
        )

        holder.refresh()

        assertEquals(listOf(entry), holder.state.value.mutedUsers)
        assertEquals(true, holder.state.value.mutedEndReached)
        assertEquals(false, holder.state.value.isLoading)
    }

    @Test
    fun selectBlockedTabLoadsBlockedUsers() = runTest {
        val entry = relationshipEntry("block-1", "user-2")
        val holder = holder(
            blockedResult = UserRelationshipListRepositoryResult.Success(
                entries = listOf(entry),
                endReached = false,
            ),
        )

        holder.selectTab(RelationshipManagementTab.Blocked)

        assertEquals(RelationshipManagementTab.Blocked, holder.state.value.selectedTab)
        assertEquals(listOf(entry), holder.state.value.blockedUsers)
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

        holder.refresh()
        holder.removeRelationship("user-1")

        assertEquals(emptyList(), holder.state.value.mutedUsers)
        assertEquals("已取消静音", holder.state.value.message)
    }

    private fun TestScope.holder(
        mutedResult: UserRelationshipListRepositoryResult = UserRelationshipListRepositoryResult.Success(emptyList(), true),
        blockedResult: UserRelationshipListRepositoryResult = UserRelationshipListRepositoryResult.Success(emptyList(), true),
        mutationResult: UserRelationshipRepositoryResult = UserRelationshipRepositoryResult.Success,
    ): RelationshipManagementStateHolder {
        val repository = object : UserRelationshipRepository(tokenProvider = { "token" }) {
            override suspend fun loadMutedUsers(
                currentEntries: List<UserRelationshipListEntry>,
            ): UserRelationshipListRepositoryResult = mutedResult

            override suspend fun loadBlockedUsers(
                currentEntries: List<UserRelationshipListEntry>,
            ): UserRelationshipListRepositoryResult = blockedResult

            override suspend fun unmute(userId: String): UserRelationshipRepositoryResult = mutationResult

            override suspend fun unblock(userId: String): UserRelationshipRepositoryResult = mutationResult
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
