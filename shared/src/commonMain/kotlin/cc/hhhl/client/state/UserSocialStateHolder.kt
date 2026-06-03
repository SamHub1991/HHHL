package cc.hhhl.client.state

import cc.hhhl.client.model.UserSocialItem
import cc.hhhl.client.model.UserSocialKind
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import cc.hhhl.client.repository.UserSocialRepository
import cc.hhhl.client.repository.UserSocialRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserSocialUiState(
    val userId: String? = null,
    val kind: UserSocialKind = UserSocialKind.Following,
    val displayName: String? = null,
    val items: List<UserSocialItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRelationshipChanging: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val requiresRelogin: Boolean = false,
)

class UserSocialStateHolder(
    private val repository: UserSocialRepository,
    private val relationshipRepository: UserRelationshipRepository? = null,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(UserSocialUiState())
    val state: StateFlow<UserSocialUiState> = mutableState
    private var listRequestId = 0
    private var relationshipActionRequestId = 0

    fun load(
        userId: String,
        kind: UserSocialKind,
        displayName: String?,
    ) {
        if (state.value.isLoading && state.value.userId == userId && state.value.kind == kind) return
        val requestId = nextListRequestId()
        relationshipActionRequestId += 1

        mutableState.update {
            it.copy(
                userId = userId,
                kind = kind,
                displayName = displayName,
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                isRelationshipChanging = false,
                endReached = false,
                errorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyResult(
                result = repository.refresh(userId, kind),
                loadingMore = false,
                userId = userId,
                kind = kind,
                requestId = requestId,
            )
        }
    }

    fun loadMore() {
        val current = state.value
        val userId = current.userId ?: return
        if (
            current.isLoading ||
            current.isLoadingMore ||
            current.items.isEmpty() ||
            current.endReached
        ) {
            return
        }

        val kind = current.kind
        val currentItems = current.items
        val requestId = nextListRequestId()

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, message = null, requiresRelogin = false)
        }

        scope.launch {
            applyResult(
                result = repository.loadMore(userId, kind, currentItems),
                loadingMore = true,
                userId = userId,
                kind = kind,
                requestId = requestId,
            )
        }
    }

    fun unfollow(userId: String) {
        val repository = relationshipRepository ?: return
        performRelationshipAction(
            userId = userId,
            action = { cleanUserId -> repository.unfollow(cleanUserId) },
            successMessage = "已取消关注",
            removeLocalItem = true,
        )
    }

    fun mute(userId: String) {
        val repository = relationshipRepository ?: return
        performRelationshipAction(
            userId = userId,
            action = { cleanUserId -> repository.mute(cleanUserId) },
            successMessage = "已静音",
            removeLocalItem = false,
        )
    }

    fun block(userId: String) {
        val repository = relationshipRepository ?: return
        performRelationshipAction(
            userId = userId,
            action = { cleanUserId -> repository.block(cleanUserId) },
            successMessage = "已屏蔽",
            removeLocalItem = true,
        )
    }

    fun reportUser(userId: String) {
        val repository = relationshipRepository ?: return
        performRelationshipAction(
            userId = userId,
            action = { cleanUserId -> repository.reportUser(cleanUserId) },
            successMessage = "已提交举报",
            removeLocalItem = false,
        )
    }

    private fun performRelationshipAction(
        userId: String,
        action: suspend (String) -> UserRelationshipRepositoryResult,
        successMessage: String,
        removeLocalItem: Boolean,
    ) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty() || state.value.isRelationshipChanging) return
        val current = state.value
        val ownerUserId = current.userId ?: return
        val kind = current.kind
        val requestId = ++relationshipActionRequestId

        mutableState.update {
            it.copy(
                isRelationshipChanging = true,
                errorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyRelationshipResult(
                requestId = requestId,
                ownerUserId = ownerUserId,
                kind = kind,
                userId = cleanUserId,
                result = action(cleanUserId),
                successMessage = successMessage,
                removeLocalItem = removeLocalItem,
            )
        }
    }

    private fun applyResult(
        result: UserSocialRepositoryResult,
        loadingMore: Boolean,
        userId: String,
        kind: UserSocialKind,
        requestId: Int,
    ) {
        if (!isCurrentListRequest(userId, kind, requestId)) return
        when (result) {
            is UserSocialRepositoryResult.Success -> mutableState.update {
                it.copy(
                    items = result.items,
                    isLoading = false,
                    isLoadingMore = false,
                    isRelationshipChanging = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    message = null,
                    requiresRelogin = false,
                )
            }
            UserSocialRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isRelationshipChanging = false,
                    errorMessage = "登录已失效，请重新登录",
                    message = null,
                    requiresRelogin = true,
                )
            }
            is UserSocialRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = if (loadingMore) it.isLoading else false,
                    isLoadingMore = false,
                    isRelationshipChanging = false,
                    errorMessage = result.message,
                    message = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun nextListRequestId(): Int {
        listRequestId += 1
        return listRequestId
    }

    private fun isCurrentListRequest(
        userId: String,
        kind: UserSocialKind,
        requestId: Int,
    ): Boolean {
        val current = state.value
        return requestId == listRequestId && current.userId == userId && current.kind == kind
    }

    private fun applyRelationshipResult(
        requestId: Int,
        ownerUserId: String,
        kind: UserSocialKind,
        userId: String,
        result: UserRelationshipRepositoryResult,
        successMessage: String,
        removeLocalItem: Boolean,
    ) {
        if (!isCurrentRelationshipAction(requestId, ownerUserId, kind)) return
        when (result) {
            UserRelationshipRepositoryResult.Success -> mutableState.update {
                it.copy(
                    items = if (removeLocalItem) {
                        it.items.filterNot { item -> item.user.id == userId }
                    } else {
                        it.items
                    },
                    isRelationshipChanging = false,
                    errorMessage = null,
                    message = successMessage,
                    requiresRelogin = false,
                )
            }
            UserRelationshipRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = "登录已失效，请重新登录",
                    message = null,
                    requiresRelogin = true,
                )
            }
            is UserRelationshipRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isRelationshipChanging = false,
                    errorMessage = result.message,
                    message = null,
                    requiresRelogin = false,
                )
            }
            is UserRelationshipRepositoryResult.RelationLoaded -> mutableState.update {
                it.copy(isRelationshipChanging = false, requiresRelogin = false)
            }
        }
    }

    private fun isCurrentRelationshipAction(
        requestId: Int,
        ownerUserId: String,
        kind: UserSocialKind,
    ): Boolean {
        val current = state.value
        return requestId == relationshipActionRequestId && current.userId == ownerUserId && current.kind == kind
    }
}
