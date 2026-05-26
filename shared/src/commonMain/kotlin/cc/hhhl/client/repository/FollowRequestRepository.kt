package cc.hhhl.client.repository

import cc.hhhl.client.api.FollowRequestActionResult
import cc.hhhl.client.api.FollowRequestApi
import cc.hhhl.client.api.FollowRequestLoadResult
import cc.hhhl.client.api.SharkeyFollowRequestApi
import cc.hhhl.client.model.FollowRequest

open class FollowRequestRepository(
    private val tokenProvider: () -> String?,
    private val api: FollowRequestApi = SharkeyFollowRequestApi(),
) {
    open suspend fun refresh(): FollowRequestsRepositoryResult {
        return loadRequests(currentRequests = emptyList(), untilId = null)
    }

    open suspend fun loadMore(currentRequests: List<FollowRequest>): FollowRequestsRepositoryResult {
        return loadRequests(
            currentRequests = currentRequests,
            untilId = currentRequests.lastOrNull()?.id,
        )
    }

    open suspend fun accept(userId: String): FollowRequestActionRepositoryResult {
        return performAction(userId) { token, cleanUserId -> api.accept(token, cleanUserId) }
    }

    open suspend fun reject(userId: String): FollowRequestActionRepositoryResult {
        return performAction(userId) { token, cleanUserId -> api.reject(token, cleanUserId) }
    }

    private suspend fun loadRequests(
        currentRequests: List<FollowRequest>,
        untilId: String?,
    ): FollowRequestsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return FollowRequestsRepositoryResult.Unauthorized

        return when (val result = api.loadReceived(token = token, limit = PAGE_SIZE, untilId = untilId)) {
            is FollowRequestLoadResult.Success -> FollowRequestsRepositoryResult.Success(
                requests = (currentRequests + result.requests).distinctBy { it.id },
            )
            FollowRequestLoadResult.Unauthorized -> FollowRequestsRepositoryResult.Unauthorized
            is FollowRequestLoadResult.NetworkError -> {
                FollowRequestsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is FollowRequestLoadResult.ServerError -> FollowRequestsRepositoryResult.Error(result.message)
        }
    }

    private suspend fun performAction(
        userId: String,
        action: suspend (String, String) -> FollowRequestActionResult,
    ): FollowRequestActionRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return FollowRequestActionRepositoryResult.Unauthorized
        val cleanUserId = userId.takeIf { it.isNotBlank() }
            ?: return FollowRequestActionRepositoryResult.Error("无法操作关注请求")

        return when (val result = action(token, cleanUserId)) {
            FollowRequestActionResult.Success -> FollowRequestActionRepositoryResult.Success
            FollowRequestActionResult.Unauthorized -> FollowRequestActionRepositoryResult.Unauthorized
            is FollowRequestActionResult.NetworkError -> {
                FollowRequestActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is FollowRequestActionResult.ServerError -> FollowRequestActionRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

sealed interface FollowRequestsRepositoryResult {
    data class Success(val requests: List<FollowRequest>) : FollowRequestsRepositoryResult

    data object Unauthorized : FollowRequestsRepositoryResult

    data class Error(val message: String) : FollowRequestsRepositoryResult
}

sealed interface FollowRequestActionRepositoryResult {
    data object Success : FollowRequestActionRepositoryResult

    data object Unauthorized : FollowRequestActionRepositoryResult

    data class Error(val message: String) : FollowRequestActionRepositoryResult
}
