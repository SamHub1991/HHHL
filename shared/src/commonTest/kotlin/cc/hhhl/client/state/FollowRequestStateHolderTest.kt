package cc.hhhl.client.state

import cc.hhhl.client.repository.FollowRequestActionRepositoryResult
import cc.hhhl.client.repository.FollowRequestRepository
import cc.hhhl.client.repository.FollowRequestsRepositoryResult
import cc.hhhl.client.repository.sampleFollowRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class FollowRequestStateHolderTest {
    @Test
    fun refreshStoresFollowRequests() = runTest {
        val request = sampleFollowRequest("req-1")
        val holder = FollowRequestStateHolder(
            repository = fakeRepository(
                listResult = FollowRequestsRepositoryResult.Success(listOf(request)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(request), holder.state.value.requests)
    }

    @Test
    fun acceptRemovesRequestAndTracksPendingUser() = runTest {
        val request = sampleFollowRequest("req-1")
        val holder = FollowRequestStateHolder(
            repository = fakeRepository(
                listResult = FollowRequestsRepositoryResult.Success(listOf(request)),
                actionResult = FollowRequestActionRepositoryResult.Success,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.accept(request.user.id)
        assertTrue(holder.state.value.pendingUserIds.contains(request.user.id))
        advanceUntilIdle()

        assertFalse(holder.state.value.pendingUserIds.contains(request.user.id))
        assertTrue(holder.state.value.requests.isEmpty())
    }

    @Test
    fun rejectRemovesRequest() = runTest {
        val request = sampleFollowRequest("req-1")
        val holder = FollowRequestStateHolder(
            repository = fakeRepository(
                listResult = FollowRequestsRepositoryResult.Success(listOf(request)),
                actionResult = FollowRequestActionRepositoryResult.Success,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.reject(request.user.id)
        advanceUntilIdle()

        assertTrue(holder.state.value.requests.isEmpty())
    }

    @Test
    fun unauthorizedLoadMarksRelogin() = runTest {
        val holder = FollowRequestStateHolder(
            repository = fakeRepository(
                listResult = FollowRequestsRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val request = sampleFollowRequest("req-1")
        val holder = FollowRequestStateHolder(
            repository = sequenceRepository(
                FollowRequestsRepositoryResult.Unauthorized,
                FollowRequestsRepositoryResult.Success(listOf(request)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(request), holder.state.value.requests)
    }

    private fun fakeRepository(
        listResult: FollowRequestsRepositoryResult,
        actionResult: FollowRequestActionRepositoryResult = FollowRequestActionRepositoryResult.Success,
    ): FollowRequestRepository {
        return object : FollowRequestRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.FollowRequestApi {
                override suspend fun loadReceived(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.FollowRequestLoadResult {
                    return cc.hhhl.client.api.FollowRequestLoadResult.Success(emptyList())
                }

                override suspend fun accept(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.FollowRequestActionResult {
                    return cc.hhhl.client.api.FollowRequestActionResult.Success
                }

                override suspend fun reject(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.FollowRequestActionResult {
                    return cc.hhhl.client.api.FollowRequestActionResult.Success
                }
            },
        ) {
            override suspend fun refresh(): FollowRequestsRepositoryResult = listResult

            override suspend fun accept(userId: String): FollowRequestActionRepositoryResult = actionResult

            override suspend fun reject(userId: String): FollowRequestActionRepositoryResult = actionResult
        }
    }

    private fun sequenceRepository(
        vararg listResults: FollowRequestsRepositoryResult,
    ): FollowRequestRepository {
        var index = 0
        return object : FollowRequestRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.FollowRequestApi {
                override suspend fun loadReceived(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.FollowRequestLoadResult {
                    return cc.hhhl.client.api.FollowRequestLoadResult.Success(emptyList())
                }

                override suspend fun accept(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.FollowRequestActionResult {
                    return cc.hhhl.client.api.FollowRequestActionResult.Success
                }

                override suspend fun reject(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.FollowRequestActionResult {
                    return cc.hhhl.client.api.FollowRequestActionResult.Success
                }
            },
        ) {
            override suspend fun refresh(): FollowRequestsRepositoryResult {
                val result = listResults[index.coerceAtMost(listResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun accept(userId: String): FollowRequestActionRepositoryResult {
                return FollowRequestActionRepositoryResult.Success
            }

            override suspend fun reject(userId: String): FollowRequestActionRepositoryResult {
                return FollowRequestActionRepositoryResult.Success
            }
        }
    }
}
