package cc.hhhl.client.state

import cc.hhhl.client.model.User
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeCompletionStateHolderTest {
    @Test
    fun staleMentionResultDoesNotOverwriteNewerQuery() = runTest {
        val oldResult = CompletableDeferred<DiscoverRepositoryResult>()
        val newResult = CompletableDeferred<DiscoverRepositoryResult>()
        val holder = ComposeCompletionStateHolder(
            repository = fakeRepository(
                searchUsersProvider = { query ->
                    when (query) {
                        "old" -> oldResult.await()
                        "new" -> newResult.await()
                        else -> DiscoverRepositoryResult.UserSuccess(emptyList())
                    }
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.request(ComposeCompletionKind.Mention, "old")
        advanceTimeBy(220)
        runCurrent()
        holder.request(ComposeCompletionKind.Mention, "new")
        advanceTimeBy(220)
        runCurrent()

        newResult.complete(DiscoverRepositoryResult.UserSuccess(listOf(user("new-user", "New"))))
        advanceUntilIdle()
        oldResult.complete(DiscoverRepositoryResult.UserSuccess(listOf(user("old-user", "Old"))))
        advanceUntilIdle()

        assertEquals("new", holder.state.value.query)
        assertEquals(listOf("new-user"), holder.state.value.users.map { it.id })
    }

    @Test
    fun staleHashtagErrorDoesNotOverwriteClosedCompletion() = runTest {
        val trendsResult = CompletableDeferred<DiscoverRepositoryResult>()
        val holder = ComposeCompletionStateHolder(
            repository = fakeRepository(loadTrendsProvider = { trendsResult.await() }),
            scope = TestScope(testScheduler),
        )

        holder.request(ComposeCompletionKind.Hashtag, "ko")
        runCurrent()
        holder.request(null, "")
        trendsResult.complete(DiscoverRepositoryResult.Error("network error"))
        advanceUntilIdle()

        assertEquals(null, holder.state.value.activeKind)
        assertEquals(null, holder.state.value.errorMessage)
        assertEquals(emptyList(), holder.state.value.hashtags)
    }

    private fun fakeRepository(
        searchUsersProvider: suspend (String) -> DiscoverRepositoryResult = {
            DiscoverRepositoryResult.UserSuccess(emptyList())
        },
        loadTrendsProvider: suspend () -> DiscoverRepositoryResult = {
            DiscoverRepositoryResult.TrendSuccess(emptyList())
        },
    ): DiscoverRepository {
        return object : DiscoverRepository(tokenProvider = { "token" }) {
            override suspend fun searchUsers(
                query: String,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                return searchUsersProvider(query)
            }

            override suspend fun loadTrends(): DiscoverRepositoryResult {
                return loadTrendsProvider()
            }
        }
    }

    private fun user(id: String, name: String): User {
        return User(
            id = id,
            displayName = name,
            username = name.lowercase(),
            avatarInitial = name.first().toString(),
        )
    }
}
