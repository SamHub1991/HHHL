package cc.hhhl.client.state

import cc.hhhl.client.api.UserSocialApi
import cc.hhhl.client.api.UserSocialLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.UserSocialItem
import cc.hhhl.client.model.UserSocialKind
import cc.hhhl.client.repository.UserSocialRepository
import cc.hhhl.client.repository.UserSocialRepositoryResult
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class UserSocialStateHolderTest {
    @Test
    fun loadStoresContextAndItems() = runTest {
        val item = UserSocialItem("rel-1", FakeData.me)
        val holder = UserSocialStateHolder(
            repository = fakeRepository(UserSocialRepositoryResult.Success(listOf(item))),
            scope = TestScope(testScheduler),
        )

        holder.load(
            userId = "user-1",
            kind = UserSocialKind.Following,
            displayName = "Alice",
        )
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals("user-1", holder.state.value.userId)
        assertEquals(UserSocialKind.Following, holder.state.value.kind)
        assertEquals("Alice", holder.state.value.displayName)
        assertEquals(listOf(item), holder.state.value.items)
    }

    @Test
    fun loadMoreAppendsItems() = runTest {
        val first = UserSocialItem("rel-1", FakeData.me)
        val second = UserSocialItem("rel-2", FakeData.me.copy(id = "u1", username = "alice"))
        val holder = UserSocialStateHolder(
            repository = fakeRepository(
                refreshResult = UserSocialRepositoryResult.Success(listOf(first)),
                loadMoreResult = UserSocialRepositoryResult.Success(listOf(first, second)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load("user-1", UserSocialKind.Followers, "Alice")
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertEquals(listOf(first, second), holder.state.value.items)
    }

    @Test
    fun unauthorizedLoadMarksRelogin() = runTest {
        val holder = UserSocialStateHolder(
            repository = fakeRepository(UserSocialRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.load("user-1", UserSocialKind.Following, null)
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulReloadClearsReloginAfterUnauthorized() = runTest {
        val item = UserSocialItem("rel-1", FakeData.me)
        val holder = UserSocialStateHolder(
            repository = sequenceRepository(
                UserSocialRepositoryResult.Unauthorized,
                UserSocialRepositoryResult.Success(listOf(item)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load("user-1", UserSocialKind.Following, null)
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.load("user-1", UserSocialKind.Following, null)
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(item), holder.state.value.items)
    }

    @Test
    fun unfollowRemovesLocalFollowingItem() = runTest {
        val item = UserSocialItem("rel-1", FakeData.me.copy(id = "user-2"))
        val holder = UserSocialStateHolder(
            repository = fakeRepository(UserSocialRepositoryResult.Success(listOf(item))),
            relationshipRepository = fakeRelationshipRepository(),
            scope = TestScope(testScheduler),
        )

        holder.load("user-1", UserSocialKind.Following, null)
        advanceUntilIdle()
        holder.unfollow("user-2")
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.items)
        assertEquals("已取消关注", holder.state.value.message)
    }

    @Test
    fun reportKeepsItemAndShowsMessage() = runTest {
        val item = UserSocialItem("rel-1", FakeData.me.copy(id = "user-2"))
        val holder = UserSocialStateHolder(
            repository = fakeRepository(UserSocialRepositoryResult.Success(listOf(item))),
            relationshipRepository = fakeRelationshipRepository(),
            scope = TestScope(testScheduler),
        )

        holder.load("user-1", UserSocialKind.Followers, null)
        advanceUntilIdle()
        holder.reportUser("user-2")
        advanceUntilIdle()

        assertEquals(listOf(item), holder.state.value.items)
        assertEquals("已提交举报", holder.state.value.message)
    }

    private fun fakeRepository(
        refreshResult: UserSocialRepositoryResult,
        loadMoreResult: UserSocialRepositoryResult = refreshResult,
    ): UserSocialRepository {
        return object : UserSocialRepository(
            tokenProvider = { "token-123" },
            api = object : UserSocialApi {
                override suspend fun loadUsers(
                    token: String,
                    userId: String,
                    kind: UserSocialKind,
                    limit: Int,
                    untilId: String?,
                ): UserSocialLoadResult = UserSocialLoadResult.Success(emptyList())
            },
        ) {
            override suspend fun refresh(
                userId: String,
                kind: UserSocialKind,
            ): UserSocialRepositoryResult {
                return refreshResult
            }

            override suspend fun loadMore(
                userId: String,
                kind: UserSocialKind,
                currentItems: List<UserSocialItem>,
            ): UserSocialRepositoryResult {
                return loadMoreResult
            }
        }
    }

    private fun sequenceRepository(
        vararg refreshResults: UserSocialRepositoryResult,
    ): UserSocialRepository {
        var index = 0
        return object : UserSocialRepository(
            tokenProvider = { "token-123" },
            api = object : UserSocialApi {
                override suspend fun loadUsers(
                    token: String,
                    userId: String,
                    kind: UserSocialKind,
                    limit: Int,
                    untilId: String?,
                ): UserSocialLoadResult = UserSocialLoadResult.Success(emptyList())
            },
        ) {
            override suspend fun refresh(
                userId: String,
                kind: UserSocialKind,
            ): UserSocialRepositoryResult {
                val result = refreshResults[index.coerceAtMost(refreshResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun loadMore(
                userId: String,
                kind: UserSocialKind,
                currentItems: List<UserSocialItem>,
            ): UserSocialRepositoryResult {
                return refreshResults.last()
            }
        }
    }

    private fun fakeRelationshipRepository(
        result: UserRelationshipRepositoryResult = UserRelationshipRepositoryResult.Success,
    ): UserRelationshipRepository {
        return object : UserRelationshipRepository(tokenProvider = { "token-123" }) {
            override suspend fun unfollow(userId: String): UserRelationshipRepositoryResult = result

            override suspend fun mute(userId: String): UserRelationshipRepositoryResult = result

            override suspend fun block(userId: String): UserRelationshipRepositoryResult = result

            override suspend fun reportUser(
                userId: String,
                comment: String,
            ): UserRelationshipRepositoryResult = result
        }
    }
}
