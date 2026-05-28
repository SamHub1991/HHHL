package cc.hhhl.client.state

import cc.hhhl.client.model.FollowRequest
import cc.hhhl.client.repository.FollowRequestActionRepositoryResult
import cc.hhhl.client.repository.FollowRequestRepository
import cc.hhhl.client.repository.FollowRequestsRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowRequestUiState(
    val requests: List<FollowRequest> = emptyList(),
    val sentRequests: List<FollowRequest> = emptyList(),
    val pendingUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isLoadingSent: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class FollowRequestStateHolder(
    private val repository: FollowRequestRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(FollowRequestUiState())
    val state: StateFlow<FollowRequestUiState> = mutableState
    private var listRequestId = 0

    fun refresh() {
        if (state.value.isLoading) return

        mutableState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                actionErrorMessage = null,
                requiresRelogin = false,
            )
        }

        val requestId = nextListRequestId()
        scope.launch {
            applyListResult(repository.refresh(), loadingMore = false, requestId = requestId)
            refreshSent()
        }
    }

    fun refreshSent() {
        if (state.value.isLoadingSent) return
        mutableState.update {
            it.copy(isLoadingSent = true, actionErrorMessage = null, requiresRelogin = false)
        }
        scope.launch {
            applySentListResult(repository.refreshSent())
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || current.endReached || current.requests.isEmpty()) return

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        val requestId = nextListRequestId()
        scope.launch {
            applyListResult(repository.loadMore(current.requests), loadingMore = true, requestId = requestId)
        }
    }

    fun accept(userId: String) {
        perform(userId) { repository.accept(it) }
    }

    fun reject(userId: String) {
        perform(userId) { repository.reject(it) }
    }

    fun cancel(userId: String) {
        perform(userId) { repository.cancel(it) }
    }

    private fun perform(
        userId: String,
        action: suspend (String) -> FollowRequestActionRepositoryResult,
    ) {
        if (userId.isBlank() || state.value.pendingUserIds.contains(userId)) return

        mutableState.update {
            it.copy(
                pendingUserIds = it.pendingUserIds + userId,
                actionErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyActionResult(userId, action(userId))
        }
    }

    private fun applyListResult(
        result: FollowRequestsRepositoryResult,
        loadingMore: Boolean,
        requestId: Int,
    ) {
        if (requestId != listRequestId) return
        when (result) {
            is FollowRequestsRepositoryResult.Success -> mutableState.update {
                it.copy(
                    requests = result.requests,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = loadingMore && result.requests.size == it.requests.size,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            FollowRequestsRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FollowRequestsRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applySentListResult(result: FollowRequestsRepositoryResult) {
        when (result) {
            is FollowRequestsRepositoryResult.Success -> mutableState.update {
                it.copy(
                    sentRequests = result.requests,
                    isLoadingSent = false,
                    requiresRelogin = false,
                )
            }
            FollowRequestsRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingSent = false,
                    actionErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FollowRequestsRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoadingSent = false,
                    actionErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun nextListRequestId(): Int {
        listRequestId += 1
        return listRequestId
    }

    private fun applyActionResult(
        userId: String,
        result: FollowRequestActionRepositoryResult,
    ) {
        when (result) {
            FollowRequestActionRepositoryResult.Success -> mutableState.update {
                it.copy(
                    requests = it.requests.filterNot { request -> request.user.id == userId },
                    sentRequests = it.sentRequests.filterNot { request -> request.user.id == userId },
                    pendingUserIds = it.pendingUserIds - userId,
                    actionErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            FollowRequestActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    pendingUserIds = it.pendingUserIds - userId,
                    actionErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FollowRequestActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    pendingUserIds = it.pendingUserIds - userId,
                    actionErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
