package cc.hhhl.client.repository

import cc.hhhl.client.api.UserNotesApi
import cc.hhhl.client.api.UserNotesLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class UserNotesRepositoryTest {
    @Test
    fun refreshUsesTokenAndUserIdProviders() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = UserNotesRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                calls = calls,
                result = UserNotesLoadResult.Success(listOf(FakeData.timeline[0])),
            ),
        )

        val result = repository.refresh()

        assertIs<UserNotesRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall("token-123", "user-1", null)), calls)
        assertEquals(listOf(FakeData.timeline[0]), result.notes)
    }

    @Test
    fun loadMoreUsesLastNoteIdAsUntilIdAndDeduplicates() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val repository = UserNotesRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                result = UserNotesLoadResult.Success(listOf(first, second, first)),
            ),
        )

        val result = repository.loadMore(currentNotes = listOf(first))

        assertIs<UserNotesRepositoryResult.Success>(result)
        assertEquals(listOf(first, second), result.notes)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserNotesRepository(
            tokenProvider = { null },
            userIdProvider = { "user-1" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = UserNotesLoadResult.Success(emptyList()),
            ),
        )

        assertIs<UserNotesRepositoryResult.Unauthorized>(repository.refresh())
        assertEquals(0, calls)
    }

    @Test
    fun missingUserIdReturnsErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserNotesRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = UserNotesLoadResult.Success(emptyList()),
            ),
        )

        assertEquals(
            UserNotesRepositoryResult.Error("无法读取当前账号"),
            repository.refresh(),
        )
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: UserNotesLoadResult,
        onCall: () -> Unit = {},
    ): UserNotesApi {
        return object : UserNotesApi {
            override suspend fun loadUserNotes(
                token: String,
                userId: String,
                limit: Int,
                untilId: String?,
            ): UserNotesLoadResult {
                onCall()
                calls.add(ApiCall(token, userId, untilId))
                return result
            }
        }
    }

    private data class ApiCall(
        val token: String,
        val userId: String,
        val untilId: String?,
    )
}
