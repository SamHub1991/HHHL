package cc.hhhl.client.repository

import cc.hhhl.client.api.FollowRequestActionResult
import cc.hhhl.client.api.FollowRequestApi
import cc.hhhl.client.api.FollowRequestLoadResult
import cc.hhhl.client.model.FollowRequest
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class FollowRequestRepositoryTest {
    @Test
    fun refreshUsesTokenAndStoresReceivedRequests() = runTest {
        val calls = mutableListOf<ApiCall>()
        val request = sampleFollowRequest("req-1")
        val repository = FollowRequestRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                loadResult = FollowRequestLoadResult.Success(listOf(request)),
            ),
        )

        val result = repository.refresh()

        assertEquals(FollowRequestsRepositoryResult.Success(listOf(request)), result)
        assertEquals(listOf(ApiCall("load", "token-123", null, null)), calls)
    }

    @Test
    fun loadMoreUsesLastRequestIdAsUntilId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = FollowRequestRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                loadResult = FollowRequestLoadResult.Success(listOf(sampleFollowRequest("req-0"))),
            ),
        )

        repository.loadMore(listOf(sampleFollowRequest("req-2"), sampleFollowRequest("req-1")))

        assertEquals(listOf(ApiCall("load", "token-123", null, "req-1")), calls)
    }

    @Test
    fun acceptAndRejectUseFollowerUserId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = FollowRequestRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls),
        )

        assertEquals(FollowRequestActionRepositoryResult.Success, repository.accept("follower-1"))
        assertEquals(FollowRequestActionRepositoryResult.Success, repository.reject("follower-1"))

        assertEquals(
            listOf(
                ApiCall("accept", "token-123", "follower-1", null),
                ApiCall("reject", "token-123", "follower-1", null),
            ),
            calls,
        )
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = FollowRequestRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<FollowRequestsRepositoryResult.Unauthorized>(repository.refresh())
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        loadResult: FollowRequestLoadResult = FollowRequestLoadResult.Success(emptyList()),
        actionResult: FollowRequestActionResult = FollowRequestActionResult.Success,
        onCall: () -> Unit = {},
    ): FollowRequestApi {
        return object : FollowRequestApi {
            override suspend fun loadReceived(
                token: String,
                limit: Int,
                untilId: String?,
            ): FollowRequestLoadResult {
                onCall()
                calls.add(ApiCall("load", token, null, untilId))
                return loadResult
            }

            override suspend fun accept(
                token: String,
                userId: String,
            ): FollowRequestActionResult {
                onCall()
                calls.add(ApiCall("accept", token, userId, null))
                return actionResult
            }

            override suspend fun reject(
                token: String,
                userId: String,
            ): FollowRequestActionResult {
                onCall()
                calls.add(ApiCall("reject", token, userId, null))
                return actionResult
            }
        }
    }
}

fun sampleFollowRequest(id: String): FollowRequest {
    return FollowRequest(
        id = id,
        user = User(
            id = "follower-$id",
            displayName = "申请者 $id",
            username = "user$id",
            avatarInitial = "申",
        ),
    )
}

private data class ApiCall(
    val action: String,
    val token: String,
    val userId: String?,
    val untilId: String?,
)
