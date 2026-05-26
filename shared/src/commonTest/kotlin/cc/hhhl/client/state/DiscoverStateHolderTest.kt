package cc.hhhl.client.state

import cc.hhhl.client.api.DiscoverApi
import cc.hhhl.client.api.DiscoverFederationResult
import cc.hhhl.client.api.DiscoverSearchResult
import cc.hhhl.client.api.DiscoverTrendResult
import cc.hhhl.client.api.DiscoverUserSearchResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverStateHolderTest {
    @Test
    fun updateQueryStoresDraftQuery() {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(DiscoverRepositoryResult.Success(emptyList())),
            scope = TestScope(),
        )

        holder.updateQuery("Sharkey")

        assertEquals("Sharkey", holder.state.value.query)
    }

    @Test
    fun updateQueryClearsStaleResultsAndPagination() = runTest {
        val note = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                DiscoverRepositoryResult.Success(
                    notes = listOf(note),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        holder.updateQuery("Misskey")

        assertEquals("Misskey", holder.state.value.query)
        assertEquals(emptyList(), holder.state.value.notes)
        assertFalse(holder.state.value.hasSearched)
        assertFalse(holder.state.value.endReached)
    }

    @Test
    fun updateFiltersStoresFiltersAndClearsResults() {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(DiscoverRepositoryResult.Success(listOf(FakeData.timeline[0]))),
            scope = TestScope(),
        )

        holder.updateQuery("Sharkey")
        holder.updateFilters(
            DiscoverAdvancedFilters(
                origin = DiscoverSearchOrigin.Local,
                username = "alice",
                domain = "example.social",
                sinceDate = "2026-05-01",
                untilDate = "2026-05-26",
            ),
        )

        assertEquals(DiscoverSearchOrigin.Local, holder.state.value.filters.origin)
        assertEquals("alice", holder.state.value.filters.username)
        assertEquals("example.social", holder.state.value.filters.domain)
        assertFalse(holder.state.value.hasSearched)
    }

    @Test
    fun searchStoresResults() = runTest {
        val note = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(DiscoverRepositoryResult.Success(listOf(note))),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        assertTrue(holder.state.value.isSearching)
        advanceUntilIdle()

        assertFalse(holder.state.value.isSearching)
        assertTrue(holder.state.value.hasSearched)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun loadMoreAppendsResults() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.Success(listOf(first)),
                loadMoreResult = DiscoverRepositoryResult.Success(listOf(first, second)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertEquals(listOf(first, second), holder.state.value.notes)
    }

    @Test
    fun loadMoreStoresEndReached() = runTest {
        val first = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.Success(listOf(first)),
                loadMoreResult = DiscoverRepositoryResult.Success(
                    notes = listOf(first),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        holder.loadMore()
        advanceUntilIdle()

        assertTrue(holder.state.value.endReached)
    }

    @Test
    fun loadMoreDoesNothingAfterEndReached() = runTest {
        val first = FakeData.timeline[0]
        val calls = mutableListOf<String>()
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.Success(
                    notes = listOf(first),
                    endReached = true,
                ),
                loadMoreResult = DiscoverRepositoryResult.Success(listOf(first)),
                onLoadMore = { calls.add("loadMore") },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        holder.loadMore()
        advanceUntilIdle()

        assertEquals(emptyList(), calls)
    }

    @Test
    fun selectingUsersModeSearchesUsersAndClearsNotes() = runTest {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.UserSuccess(listOf(FakeData.me)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.selectMode(DiscoverSearchMode.Users)
        holder.updateQuery("Alice")
        holder.search()
        advanceUntilIdle()

        assertEquals(DiscoverSearchMode.Users, holder.state.value.selectedMode)
        assertEquals(listOf(FakeData.me), holder.state.value.users)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun openHashtagSearchesNotesForHashtagQuery() = runTest {
        val calls = mutableListOf<String>()
        val note = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.Success(listOf(note)),
                onSearchNotes = { query -> calls.add(query) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openHashtag("Sharkey")
        advanceUntilIdle()

        assertEquals(DiscoverSearchMode.Notes, holder.state.value.selectedMode)
        assertEquals("#Sharkey", holder.state.value.query)
        assertEquals(listOf("#Sharkey"), calls)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun openMentionSearchesUsersForUsername() = runTest {
        val calls = mutableListOf<String>()
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.UserSuccess(listOf(FakeData.me)),
                onSearchUsers = { query -> calls.add(query) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openMention("alice")
        advanceUntilIdle()

        assertEquals(DiscoverSearchMode.Users, holder.state.value.selectedMode)
        assertEquals("alice", holder.state.value.query)
        assertEquals(listOf("alice"), calls)
        assertEquals(listOf(FakeData.me), holder.state.value.users)
    }

    @Test
    fun disabledNoteSearchSelectsUsersModeAndAvoidsNoteSearch() = runTest {
        val calls = mutableListOf<String>()
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.UserSuccess(listOf(FakeData.me)),
                onSearchNotes = { calls.add("notes") },
                onSearchUsers = { calls.add("users") },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Alice")
        holder.updateCapabilities(canSearchNotes = false)
        holder.search()
        advanceUntilIdle()

        assertEquals(DiscoverSearchMode.Users, holder.state.value.selectedMode)
        assertEquals(listOf("users"), calls)
        assertEquals(listOf(FakeData.me), holder.state.value.users)
    }

    @Test
    fun selectingTrendsModeLoadsTrendingHashtags() = runTest {
        val trend = TrendingHashtag(tag = "AI", chart = listOf(1, 3, 8), usersCount = 12)
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.TrendSuccess(listOf(trend)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateCapabilities(canSearchNotes = true, canTrend = true)
        holder.selectMode(DiscoverSearchMode.Trends)
        assertTrue(holder.state.value.isSearching)
        advanceUntilIdle()

        assertEquals(DiscoverSearchMode.Trends, holder.state.value.selectedMode)
        assertEquals(listOf(trend), holder.state.value.trends)
        assertFalse(holder.state.value.isSearching)
    }

    @Test
    fun cannotSelectTrendsModeWhenTrendsAreDisabled() {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(DiscoverRepositoryResult.Success(emptyList())),
            scope = TestScope(),
        )

        holder.updateCapabilities(canSearchNotes = true, canTrend = false)
        holder.selectMode(DiscoverSearchMode.Trends)

        assertEquals(DiscoverSearchMode.Notes, holder.state.value.selectedMode)
        assertEquals("实例未启用趋势", holder.state.value.errorMessage)
    }

    @Test
    fun selectingFederationModeLoadsFederationInstances() = runTest {
        val instance = sampleFederationInstance("instance-1")
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.FederationSuccess(listOf(instance)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateCapabilities(
            canSearchNotes = true,
            canTrend = true,
            canViewFederation = true,
        )
        holder.selectMode(DiscoverSearchMode.Federation)
        advanceUntilIdle()

        assertEquals(DiscoverSearchMode.Federation, holder.state.value.selectedMode)
        assertEquals(listOf(instance), holder.state.value.federationInstances)
    }

    @Test
    fun cannotSelectFederationModeWhenFederationIsDisabled() {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(DiscoverRepositoryResult.Success(emptyList())),
            scope = TestScope(),
        )

        holder.updateCapabilities(canSearchNotes = true, canViewFederation = false)
        holder.selectMode(DiscoverSearchMode.Federation)

        assertEquals(DiscoverSearchMode.Notes, holder.state.value.selectedMode)
        assertEquals("实例未启用联邦浏览", holder.state.value.errorMessage)
    }

    @Test
    fun cannotSelectNotesModeWhenNoteSearchIsDisabled() {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(DiscoverRepositoryResult.Success(emptyList())),
            scope = TestScope(),
        )

        holder.updateCapabilities(canSearchNotes = false)
        holder.selectMode(DiscoverSearchMode.Notes)

        assertEquals(DiscoverSearchMode.Users, holder.state.value.selectedMode)
        assertFalse(holder.state.value.canSearchNotes)
    }

    @Test
    fun unauthorizedSearchMarksRelogin() = runTest {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(DiscoverRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val note = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = sequenceRepository(
                DiscoverRepositoryResult.Unauthorized,
                DiscoverRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.search()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun updatingQueryClearsReloginAfterUnauthorized() = runTest {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(DiscoverRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.updateQuery("Sharkey 2")

        assertFalse(holder.state.value.requiresRelogin)
    }

    @Test
    fun applyNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val note = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = sequenceRepository(
                DiscoverRepositoryResult.Success(listOf(note)),
                DiscoverRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        holder.search()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(1, holder.state.value.notes.single().reactions.single { it.reaction == "👍" }.count)
    }

    private fun fakeRepository(
        searchResult: DiscoverRepositoryResult,
        loadMoreResult: DiscoverRepositoryResult = searchResult,
        onLoadMore: () -> Unit = {},
        onSearchNotes: (String) -> Unit = {},
        onSearchUsers: (String) -> Unit = {},
    ): DiscoverRepository {
        return object : DiscoverRepository(
            tokenProvider = { "token-123" },
            api = object : DiscoverApi {
                override suspend fun searchNotes(
                    token: String,
                    query: String,
                    limit: Int,
                    untilId: String?,
                ): DiscoverSearchResult = DiscoverSearchResult.Success(emptyList())

                override suspend fun searchUsers(
                    token: String,
                    query: String,
                    limit: Int,
                    origin: String,
                ): DiscoverUserSearchResult = DiscoverUserSearchResult.Success(emptyList())

                override suspend fun loadTrendingHashtags(): DiscoverTrendResult {
                    return DiscoverTrendResult.Success(emptyList())
                }

                override suspend fun loadFederationInstances(
                    limit: Int,
                    offset: Int,
                    host: String?,
                ): DiscoverFederationResult {
                    return DiscoverFederationResult.Success(emptyList())
                }
            },
        ) {
            override suspend fun search(
                query: String,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                onSearchNotes(query)
                return searchResult
            }

            override suspend fun loadMore(
                query: String,
                currentNotes: List<Note>,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                onLoadMore()
                return loadMoreResult
            }

            override suspend fun searchUsers(
                query: String,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                onSearchUsers(query)
                return searchResult
            }

            override suspend fun loadTrends(): DiscoverRepositoryResult {
                return searchResult
            }

            override suspend fun loadFederation(
                currentInstances: List<FederationInstance>,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                return searchResult
            }
        }
    }

    private fun sampleFederationInstance(id: String): FederationInstance {
        return FederationInstance(
            id = id,
            host = "$id.example",
            name = id,
            softwareName = "sharkey",
            softwareVersion = "2025.5.2",
            usersCount = 10,
            notesCount = 20,
            followingCount = 1,
            followersCount = 2,
            isNotResponding = false,
            isSuspended = false,
            isBlocked = false,
            isSilenced = false,
        )
    }

    private fun sequenceRepository(
        vararg searchResults: DiscoverRepositoryResult,
    ): DiscoverRepository {
        var index = 0
        return object : DiscoverRepository(
            tokenProvider = { "token-123" },
            api = object : DiscoverApi {
                override suspend fun searchNotes(
                    token: String,
                    query: String,
                    limit: Int,
                    untilId: String?,
                ): DiscoverSearchResult = DiscoverSearchResult.Success(emptyList())

                override suspend fun searchUsers(
                    token: String,
                    query: String,
                    limit: Int,
                    origin: String,
                ): DiscoverUserSearchResult = DiscoverUserSearchResult.Success(emptyList())

                override suspend fun loadTrendingHashtags(): DiscoverTrendResult {
                    return DiscoverTrendResult.Success(emptyList())
                }

                override suspend fun loadFederationInstances(
                    limit: Int,
                    offset: Int,
                    host: String?,
                ): DiscoverFederationResult {
                    return DiscoverFederationResult.Success(emptyList())
                }
            },
        ) {
            override suspend fun search(
                query: String,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                val result = searchResults[index.coerceAtMost(searchResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun loadMore(
                query: String,
                currentNotes: List<Note>,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                return searchResults.last()
            }

            override suspend fun searchUsers(
                query: String,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                val result = searchResults[index.coerceAtMost(searchResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun loadTrends(): DiscoverRepositoryResult {
                val result = searchResults[index.coerceAtMost(searchResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun loadFederation(
                currentInstances: List<FederationInstance>,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                val result = searchResults[index.coerceAtMost(searchResults.lastIndex)]
                index += 1
                return result
            }
        }
    }
}
