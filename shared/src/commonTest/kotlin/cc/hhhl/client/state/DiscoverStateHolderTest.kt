package cc.hhhl.client.state

import cc.hhhl.client.api.DiscoverApi
import cc.hhhl.client.api.DiscoverFederationActionResult
import cc.hhhl.client.api.DiscoverFederationFollowResult
import cc.hhhl.client.api.DiscoverFederationInstanceResult
import cc.hhhl.client.api.DiscoverFederationResult
import cc.hhhl.client.api.DiscoverFederationStatsResult
import cc.hhhl.client.api.DiscoverNoteSearchOptions
import cc.hhhl.client.api.DiscoverSearchResult
import cc.hhhl.client.api.DiscoverTrendResult
import cc.hhhl.client.api.DiscoverUserSearchResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.FederationStats
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
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
    fun queryChangeInvalidatesPendingSearchResult() = runTest {
        val pending = CompletableDeferred<DiscoverRepositoryResult>()
        val note = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(searchProvider = { pending.await() }),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        runCurrent()
        assertTrue(holder.state.value.isSearching)

        holder.updateQuery("Misskey")
        pending.complete(DiscoverRepositoryResult.Success(listOf(note)))
        advanceUntilIdle()

        assertEquals("Misskey", holder.state.value.query)
        assertFalse(holder.state.value.isSearching)
        assertEquals(emptyList(), holder.state.value.notes)
        assertFalse(holder.state.value.hasSearched)
    }

    @Test
    fun searchExceptionStopsLoadingAndShowsError() = runTest {
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.Success(emptyList()),
                throwOnSearch = true,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()

        assertFalse(holder.state.value.isSearching)
        assertEquals("搜索接口异常", holder.state.value.errorMessage)
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
    fun loadMoreExceptionStopsLoadingAndKeepsResults() = runTest {
        val first = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.Success(listOf(first)),
                throwOnLoadMore = true,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        holder.loadMore()
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertEquals(listOf(first), holder.state.value.notes)
        assertEquals("搜索接口异常", holder.state.value.errorMessage)
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
    fun loadMoreUsesNextUntilIdWhenVisibleResultsAreEmpty() = runTest {
        val calls = mutableListOf<String?>()
        val note = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.Success(
                    notes = emptyList(),
                    nextUntilId = "raw-page-last-id",
                ),
                loadMoreResult = DiscoverRepositoryResult.Success(
                    notes = listOf(note),
                    nextUntilId = note.id,
                ),
                onLoadMore = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateQuery("Sharkey")
        holder.search()
        advanceUntilIdle()
        holder.loadMore()
        advanceUntilIdle()

        assertEquals(listOf<String?>("raw-page-last-id"), calls)
        assertEquals(listOf(note), holder.state.value.notes)
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

        assertEquals(emptyList<String>(), calls)
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
    fun openHashtagCanSearchWhenFullTextNotesSearchIsDisabled() = runTest {
        val calls = mutableListOf<String>()
        val note = FakeData.timeline[0]
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.Success(listOf(note)),
                onSearchNotes = { query -> calls.add(query) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateCapabilities(canSearchNotes = false)
        holder.openHashtag("#签到")
        advanceUntilIdle()

        assertEquals(DiscoverSearchMode.Notes, holder.state.value.selectedMode)
        assertEquals("#签到", holder.state.value.query)
        assertEquals(listOf("#签到"), calls)
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
    fun openingFederationInstanceStoresDetail() = runTest {
        val instance = sampleFederationInstance("instance-1")
        val detail = instance.copy(description = "detail text", maintainerName = "Admin")
        val holder = DiscoverStateHolder(
            repository = fakeRepository(
                searchResult = DiscoverRepositoryResult.FederationInstanceSuccess(detail),
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFederationInstance(instance)
        assertTrue(holder.state.value.isLoadingFederationDetail)
        advanceUntilIdle()

        assertEquals(detail, holder.state.value.selectedFederationInstance)
        assertFalse(holder.state.value.isLoadingFederationDetail)
        assertEquals(null, holder.state.value.federationDetailMessage)
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

    @Test
    fun closingFederationDetailInvalidatesPendingDetailResult() = runTest {
        val pending = CompletableDeferred<DiscoverRepositoryResult>()
        val instance = sampleFederationInstance("one")
        val updated = instance.copy(name = "updated")
        val holder = DiscoverStateHolder(
            repository = fakeRepository(searchProvider = { pending.await() }),
            scope = TestScope(testScheduler),
        )

        holder.openFederationInstance(instance)
        runCurrent()
        assertTrue(holder.state.value.isLoadingFederationDetail)

        holder.closeFederationInstance()
        pending.complete(DiscoverRepositoryResult.FederationInstanceSuccess(updated))
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingFederationDetail)
        assertEquals(null, holder.state.value.selectedFederationInstance)
    }

    private fun fakeRepository(
        searchResult: DiscoverRepositoryResult,
        loadMoreResult: DiscoverRepositoryResult = searchResult,
        onLoadMore: (String?) -> Unit = {},
        onSearchNotes: (String) -> Unit = {},
        onSearchUsers: (String) -> Unit = {},
        throwOnSearch: Boolean = false,
        throwOnLoadMore: Boolean = false,
    ): DiscoverRepository = fakeRepository(
        searchProvider = { searchResult },
        loadMoreProvider = { loadMoreResult },
        onLoadMore = onLoadMore,
        onSearchNotes = onSearchNotes,
        onSearchUsers = onSearchUsers,
        throwOnSearch = throwOnSearch,
        throwOnLoadMore = throwOnLoadMore,
    )

    private fun fakeRepository(
        searchProvider: suspend () -> DiscoverRepositoryResult,
        loadMoreProvider: suspend () -> DiscoverRepositoryResult = searchProvider,
        onLoadMore: (String?) -> Unit = {},
        onSearchNotes: (String) -> Unit = {},
        onSearchUsers: (String) -> Unit = {},
        throwOnSearch: Boolean = false,
        throwOnLoadMore: Boolean = false,
    ): DiscoverRepository {
        return object : DiscoverRepository(
            tokenProvider = { "token-123" },
            api = object : DiscoverApi {
                override suspend fun searchNotes(
                    token: String,
                    query: String,
                    limit: Int,
                    untilId: String?,
                    options: DiscoverNoteSearchOptions,
                ): DiscoverSearchResult = DiscoverSearchResult.Success(emptyList())

                override suspend fun searchNotesByTag(
                    token: String?,
                    tag: String,
                    limit: Int,
                    untilId: String?,
                    options: DiscoverNoteSearchOptions,
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

                override suspend fun loadFederationInstance(host: String): DiscoverFederationInstanceResult {
                    return DiscoverFederationInstanceResult.Unavailable
                }

                override suspend fun loadFederationFollowers(
                    host: String,
                    limit: Int,
                    untilId: String?,
                    includeFollower: Boolean,
                    includeFollowee: Boolean,
                ): DiscoverFederationFollowResult {
                    return DiscoverFederationFollowResult.Success(emptyList())
                }

                override suspend fun loadFederationFollowing(
                    host: String,
                    limit: Int,
                    untilId: String?,
                    includeFollower: Boolean,
                    includeFollowee: Boolean,
                ): DiscoverFederationFollowResult {
                    return DiscoverFederationFollowResult.Success(emptyList())
                }

                override suspend fun loadFederationUsers(
                    host: String,
                    limit: Int,
                    untilId: String?,
                ): DiscoverUserSearchResult {
                    return DiscoverUserSearchResult.Success(emptyList())
                }

                override suspend fun loadFederationStats(limit: Int): DiscoverFederationStatsResult {
                    return DiscoverFederationStatsResult.Success(sampleFederationStats())
                }

                override suspend fun updateFederationInstance(
                    token: String,
                    host: String,
                    isSilenced: Boolean,
                    isSuspended: Boolean,
                ): DiscoverFederationActionResult {
                    return DiscoverFederationActionResult.Unavailable
                }

                override suspend fun updateRemoteUser(
                    token: String,
                    userId: String,
                ): DiscoverFederationActionResult {
                    return DiscoverFederationActionResult.Unavailable
                }
            },
        ) {
            override suspend fun search(
                query: String,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                if (throwOnSearch) throw IllegalStateException("搜索接口异常")
                onSearchNotes(query)
                return searchProvider()
            }

            override suspend fun loadMore(
                query: String,
                currentNotes: List<Note>,
                filters: DiscoverAdvancedFilters,
                untilId: String?,
            ): DiscoverRepositoryResult {
                if (throwOnLoadMore) throw IllegalStateException("搜索接口异常")
                onLoadMore(untilId)
                return loadMoreProvider()
            }

            override suspend fun searchUsers(
                query: String,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                onSearchUsers(query)
                return searchProvider()
            }

            override suspend fun loadTrends(): DiscoverRepositoryResult {
                return searchProvider()
            }

            override suspend fun loadFederation(
                currentInstances: List<FederationInstance>,
                filters: DiscoverAdvancedFilters,
            ): DiscoverRepositoryResult {
                return searchProvider()
            }

            override suspend fun loadFederationInstance(host: String): DiscoverRepositoryResult {
                return searchProvider()
            }

            override suspend fun updateFederationInstance(
                host: String,
                isSilenced: Boolean,
                isSuspended: Boolean,
            ): DiscoverRepositoryResult {
                return searchProvider()
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

    private fun sampleFederationStats(): FederationStats {
        return FederationStats(
            topSubInstances = listOf(sampleFederationInstance("sub-1")),
            otherFollowersCount = 7,
            topPubInstances = listOf(sampleFederationInstance("pub-1")),
            otherFollowingCount = 9,
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
                    options: DiscoverNoteSearchOptions,
                ): DiscoverSearchResult = DiscoverSearchResult.Success(emptyList())

                override suspend fun searchNotesByTag(
                    token: String?,
                    tag: String,
                    limit: Int,
                    untilId: String?,
                    options: DiscoverNoteSearchOptions,
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

                override suspend fun loadFederationInstance(host: String): DiscoverFederationInstanceResult {
                    return DiscoverFederationInstanceResult.Unavailable
                }

                override suspend fun loadFederationFollowers(
                    host: String,
                    limit: Int,
                    untilId: String?,
                    includeFollower: Boolean,
                    includeFollowee: Boolean,
                ): DiscoverFederationFollowResult {
                    return DiscoverFederationFollowResult.Success(emptyList())
                }

                override suspend fun loadFederationFollowing(
                    host: String,
                    limit: Int,
                    untilId: String?,
                    includeFollower: Boolean,
                    includeFollowee: Boolean,
                ): DiscoverFederationFollowResult {
                    return DiscoverFederationFollowResult.Success(emptyList())
                }

                override suspend fun loadFederationUsers(
                    host: String,
                    limit: Int,
                    untilId: String?,
                ): DiscoverUserSearchResult {
                    return DiscoverUserSearchResult.Success(emptyList())
                }

                override suspend fun loadFederationStats(limit: Int): DiscoverFederationStatsResult {
                    return DiscoverFederationStatsResult.Success(sampleFederationStats())
                }

                override suspend fun updateFederationInstance(
                    token: String,
                    host: String,
                    isSilenced: Boolean,
                    isSuspended: Boolean,
                ): DiscoverFederationActionResult {
                    return DiscoverFederationActionResult.Unavailable
                }

                override suspend fun updateRemoteUser(
                    token: String,
                    userId: String,
                ): DiscoverFederationActionResult {
                    return DiscoverFederationActionResult.Unavailable
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
                untilId: String?,
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
