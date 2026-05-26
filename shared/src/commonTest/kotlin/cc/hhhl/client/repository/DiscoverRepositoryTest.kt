package cc.hhhl.client.repository

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
import cc.hhhl.client.state.DiscoverAdvancedFilters
import cc.hhhl.client.state.DiscoverSearchOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DiscoverRepositoryTest {
    @Test
    fun searchUsesTokenAndQuery() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = DiscoverSearchResult.Success(listOf(FakeData.timeline[0])),
            ),
        )

        val result = repository.search("Sharkey")

        assertIs<DiscoverRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall("notes", "token-123", "Sharkey", null)), calls)
        assertEquals(listOf(FakeData.timeline[0]), result.notes)
    }

    @Test
    fun loadMoreUsesLastNoteIdAndDeduplicates() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = DiscoverSearchResult.Success(listOf(first, second, first)),
            ),
        )

        val result = repository.loadMore("Sharkey", currentNotes = listOf(first))

        assertIs<DiscoverRepositoryResult.Success>(result)
        assertEquals(listOf(first, second), result.notes)
    }

    @Test
    fun emptyLoadMorePageMarksEndReached() = runTest {
        val first = FakeData.timeline[0]
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = DiscoverSearchResult.Success(emptyList()),
            ),
        )

        val result = repository.loadMore("Sharkey", currentNotes = listOf(first))

        assertIs<DiscoverRepositoryResult.Success>(result)
        assertEquals(listOf(first), result.notes)
        assertTrue(result.endReached)
    }

    @Test
    fun blankQueryReturnsErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = DiscoverSearchResult.Success(emptyList()),
            ),
        )

        assertEquals(
            DiscoverRepositoryResult.Error("请输入关键词"),
            repository.search(" "),
        )
        assertEquals(0, calls)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = DiscoverRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = DiscoverSearchResult.Success(emptyList()),
            ),
        )

        assertIs<DiscoverRepositoryResult.Unauthorized>(repository.search("Sharkey"))
        assertEquals(0, calls)
    }

    @Test
    fun unavailableSearchReturnsFriendlyMessage() = runTest {
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = DiscoverSearchResult.ServerError(
                    statusCode = 400,
                    message = "Search of notes unavailable.",
                ),
            ),
        )

        assertEquals(
            DiscoverRepositoryResult.Error("实例未启用帖子搜索"),
            repository.search("Sharkey"),
        )
    }

    @Test
    fun searchUsersUsesTokenAndQuery() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                userResult = DiscoverUserSearchResult.Success(listOf(FakeData.me)),
            ),
        )

        val result = repository.searchUsers("Alice")

        assertEquals(DiscoverRepositoryResult.UserSuccess(listOf(FakeData.me)), result)
        assertEquals(listOf(ApiCall("users", "token-123", "Alice", null, "combined", null)), calls)
    }

    @Test
    fun searchUsersPassesOriginFilter() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                userResult = DiscoverUserSearchResult.Success(listOf(FakeData.me)),
            ),
        )

        repository.searchUsers("Alice", DiscoverAdvancedFilters(origin = DiscoverSearchOrigin.Remote))

        assertEquals(listOf(ApiCall("users", "token-123", "Alice", null, "remote", null)), calls)
    }

    @Test
    fun searchFiltersNotesByAuthorAndDate() = runTest {
        val matching = FakeData.timeline[0].copy(
            createdAt = "2026-05-10T03:00:00.000Z",
            author = FakeData.timeline[0].author.copy(username = "alice", host = "example.social"),
        )
        val otherUser = FakeData.timeline[1].copy(
            createdAt = "2026-05-10T03:00:00.000Z",
            author = FakeData.timeline[1].author.copy(username = "bob", host = "example.social"),
        )
        val older = FakeData.timeline[2].copy(
            createdAt = "2026-04-30T03:00:00.000Z",
            author = FakeData.timeline[2].author.copy(username = "alice", host = "example.social"),
        )
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = DiscoverSearchResult.Success(listOf(matching, otherUser, older)),
            ),
        )

        val result = repository.search(
            query = "Sharkey",
            filters = DiscoverAdvancedFilters(
                username = "@alice",
                domain = "example.social",
                sinceDate = "2026-05-01",
                untilDate = "2026-05-26",
            ),
        )

        assertIs<DiscoverRepositoryResult.Success>(result)
        assertEquals(listOf(matching), result.notes)
    }

    @Test
    fun searchUsersAppliesVisibleUsernameAndDomainFilters() = runTest {
        val localAlice = FakeData.me.copy(username = "alice", host = "example.social")
        val remoteAlice = FakeData.me.copy(id = "user-2", username = "alice", host = "remote.example")
        val repository = DiscoverRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                userResult = DiscoverUserSearchResult.Success(listOf(localAlice, remoteAlice)),
            ),
        )

        val result = repository.searchUsers(
            query = "Alice",
            filters = DiscoverAdvancedFilters(username = "alice", domain = "example.social"),
        )

        assertEquals(DiscoverRepositoryResult.UserSuccess(listOf(localAlice)), result)
    }

    @Test
    fun loadTrendsDoesNotRequireToken() = runTest {
        val trend = TrendingHashtag(tag = "AI", chart = listOf(1, 2, 3), usersCount = 6)
        val calls = mutableListOf<ApiCall>()
        val repository = DiscoverRepository(
            tokenProvider = { null },
            api = fakeApi(
                calls = calls,
                trendResult = DiscoverTrendResult.Success(listOf(trend)),
            ),
        )

        val result = repository.loadTrends()

        assertEquals(DiscoverRepositoryResult.TrendSuccess(listOf(trend)), result)
        assertEquals(listOf(ApiCall("trends", null, "", null, null, null)), calls)
    }

    @Test
    fun loadFederationDoesNotRequireTokenAndUsesOffset() = runTest {
        val first = sampleFederationInstance("instance-1")
        val second = sampleFederationInstance("instance-2")
        val calls = mutableListOf<ApiCall>()
        val repository = DiscoverRepository(
            tokenProvider = { null },
            api = fakeApi(
                calls = calls,
                federationResult = DiscoverFederationResult.Success(listOf(second)),
            ),
        )

        val result = repository.loadFederation(
            currentInstances = listOf(first),
            filters = DiscoverAdvancedFilters(domain = "example.social"),
        )

        assertEquals(DiscoverRepositoryResult.FederationSuccess(listOf(first, second)), result)
        assertEquals(listOf(ApiCall("federation", null, "", "1", null, "example.social")), calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: DiscoverSearchResult = DiscoverSearchResult.Success(emptyList()),
        userResult: DiscoverUserSearchResult = DiscoverUserSearchResult.Success(emptyList()),
        trendResult: DiscoverTrendResult = DiscoverTrendResult.Success(emptyList()),
        federationResult: DiscoverFederationResult = DiscoverFederationResult.Success(emptyList()),
        onCall: () -> Unit = {},
    ): DiscoverApi {
        return object : DiscoverApi {
            override suspend fun searchNotes(
                token: String,
                query: String,
                limit: Int,
                untilId: String?,
            ): DiscoverSearchResult {
                onCall()
                calls.add(ApiCall("notes", token, query, untilId, null, null))
                return result
            }

            override suspend fun searchUsers(
                token: String,
                query: String,
                limit: Int,
                origin: String,
            ): DiscoverUserSearchResult {
                onCall()
                calls.add(ApiCall("users", token, query, null, origin, null))
                return userResult
            }

            override suspend fun loadTrendingHashtags(): DiscoverTrendResult {
                onCall()
                calls.add(ApiCall("trends", null, "", null, null, null))
                return trendResult
            }

            override suspend fun loadFederationInstances(
                limit: Int,
                offset: Int,
                host: String?,
            ): DiscoverFederationResult {
                onCall()
                calls.add(ApiCall("federation", null, "", offset.toString(), null, host))
                return federationResult
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

    private data class ApiCall(
        val kind: String,
        val token: String?,
        val query: String,
        val untilId: String?,
        val origin: String? = null,
        val host: String? = null,
    )
}
