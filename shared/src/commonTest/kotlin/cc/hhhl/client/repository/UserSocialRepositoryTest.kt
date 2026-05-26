package cc.hhhl.client.repository

import cc.hhhl.client.api.UserSocialApi
import cc.hhhl.client.api.UserSocialLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.UserSocialItem
import cc.hhhl.client.model.UserSocialKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class UserSocialRepositoryTest {
    @Test
    fun refreshUsesTokenUserIdAndKind() = runTest {
        val calls = mutableListOf<ApiCall>()
        val item = UserSocialItem("rel-1", FakeData.me)
        val repository = UserSocialRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = UserSocialLoadResult.Success(listOf(item)),
            ),
        )

        val result = repository.refresh("user-1", UserSocialKind.Following)

        assertIs<UserSocialRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall("token-123", "user-1", UserSocialKind.Following, null)), calls)
        assertEquals(listOf(item), result.items)
    }

    @Test
    fun loadMoreUsesLastRelationshipIdAndDeduplicates() = runTest {
        val first = UserSocialItem("rel-1", FakeData.me)
        val second = UserSocialItem("rel-2", FakeData.me.copy(id = "u1", username = "alice"))
        val repository = UserSocialRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = UserSocialLoadResult.Success(listOf(first, second, first)),
            ),
        )

        val result = repository.loadMore(
            userId = "user-1",
            kind = UserSocialKind.Followers,
            currentItems = listOf(first),
        )

        assertIs<UserSocialRepositoryResult.Success>(result)
        assertEquals(listOf(first, second), result.items)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserSocialRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = UserSocialLoadResult.Success(emptyList()),
            ),
        )

        assertIs<UserSocialRepositoryResult.Unauthorized>(
            repository.refresh("user-1", UserSocialKind.Following),
        )
        assertEquals(0, calls)
    }

    @Test
    fun blankUserIdReturnsErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserSocialRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = UserSocialLoadResult.Success(emptyList()),
            ),
        )

        assertEquals(
            UserSocialRepositoryResult.Error("无法读取用户"),
            repository.refresh(" ", UserSocialKind.Following),
        )
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: UserSocialLoadResult = UserSocialLoadResult.Success(emptyList()),
        onCall: () -> Unit = {},
    ): UserSocialApi {
        return object : UserSocialApi {
            override suspend fun loadUsers(
                token: String,
                userId: String,
                kind: UserSocialKind,
                limit: Int,
                untilId: String?,
            ): UserSocialLoadResult {
                onCall()
                calls.add(ApiCall(token, userId, kind, untilId))
                return result
            }
        }
    }

    private data class ApiCall(
        val token: String,
        val userId: String,
        val kind: UserSocialKind,
        val untilId: String?,
    )
}
