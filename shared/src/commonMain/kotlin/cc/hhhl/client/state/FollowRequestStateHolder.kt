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
    val pendingUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
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

        scope.launch {
            applyListResult(repository.refresh(), loadingMore = false)
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || current.endReached || current.requests.isEmpty()) return

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyListResult(repository.loadMore(current.requests), loadingMore = true)
        }
    }

    fun accept(userId: String) {
        perform(userId) { repository.accept(it) }
    }

    fun reject(userId: String) {
        perform(userId) { repository.reject(it) }
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
    ) {
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

    private fun applyActionResult(
        userId: String,
        result: FollowRequestActionRepositoryResult,
    ) {
        when (result) {
            FollowRequestActionRepositoryResult.Success -> mutableState.update {
                it.copy(
                    requests = it.requests.filterNot { request -> request.user.id == userId },
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
