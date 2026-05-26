package cc.hhhl.client.state

import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.repository.NotificationRepository
import cc.hhhl.client.repository.NotificationRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val selectedFilter: NotificationFilter = NotificationFilter.All,
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMarkingAllRead: Boolean = false,
    val unreadCount: Int = 0,
    val endReached: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class NotificationStateHolder(
    private val repository: NotificationRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = mutableState
    private var remoteNotifications: List<NotificationItem> = emptyList()
    private var specialCareNotifications: List<NotificationItem> = emptyList()

    fun refresh() {
        if (state.value.isLoading) return
        if (state.value.selectedFilter == NotificationFilter.SpecialCare) {
            mutableState.update {
                it.copy(
                    notifications = specialCareNotifications,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = true,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                isLoading = true,
                endReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyResult(repository.refresh(state.value.selectedFilter), loadingMore = false)
        }
    }

    fun selectFilter(filter: NotificationFilter) {
        if (state.value.selectedFilter == filter && state.value.notifications.isNotEmpty()) return
        if (state.value.isLoading) return

        if (filter == NotificationFilter.SpecialCare) {
            mutableState.update {
                it.copy(
                    selectedFilter = filter,
                    notifications = specialCareNotifications,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = true,
                    message = null,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                selectedFilter = filter,
                notifications = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                endReached = false,
                message = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyResult(repository.refresh(filter), loadingMore = false)
        }
    }

    fun loadMore() {
        val current = state.value
        if (
            current.isLoading ||
            current.isLoadingMore ||
            current.notifications.isEmpty() ||
            current.endReached ||
            current.selectedFilter == NotificationFilter.SpecialCare
        ) {
            return
        }

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyResult(
                repository.loadMore(
                    currentNotifications = current.notifications,
                    filter = current.selectedFilter,
                ),
                loadingMore = true,
            )
        }
    }

    fun markAllAsRead() {
        val current = state.value
        if (current.isLoading || current.isMarkingAllRead || current.notifications.isEmpty()) return

        mutableState.update {
            it.copy(
                isMarkingAllRead = true,
                message = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyResult(repository.markAllAsRead(), loadingMore = false)
        }
    }

    fun dismissFollowRequestNotification(actorUserId: String) {
        if (actorUserId.isBlank()) return

        mutableState.update {
            val removedCount = it.notifications.count { notification ->
                notification.actor.id == actorUserId &&
                    notification.type == NotificationType.FollowRequestReceived
            }
            it.copy(
                notifications = it.notifications.filterNot { notification ->
                    notification.actor.id == actorUserId &&
                        notification.type == NotificationType.FollowRequestReceived
                },
                unreadCount = (it.unreadCount - removedCount).coerceAtLeast(0),
                requiresRelogin = false,
            )
        }
    }

    fun addSpecialCareNotification(notification: NotificationItem) {
        val result = insertSpecialCareNotification(
            current = specialCareNotifications,
            notification = notification,
            limit = MAX_LOCAL_SPECIAL_CARE_NOTIFICATIONS,
        )
        if (!result.inserted) return
        specialCareNotifications = result.notifications

        mutableState.update {
            val visibleNotifications = if (it.selectedFilter == NotificationFilter.SpecialCare) {
                result.notifications
            } else {
                it.notifications
            }
            it.copy(
                notifications = visibleNotifications,
                unreadCount = it.unreadCount + 1,
                requiresRelogin = false,
            )
        }
    }

    private fun applyResult(
        result: NotificationRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            NotificationRepositoryResult.AllRead -> mutableState.update {
                it.copy(
                    notifications = emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    unreadCount = 0,
                    endReached = true,
                    message = "通知已全部标记为已读",
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is NotificationRepositoryResult.Success -> mutableState.update {
                remoteNotifications = result.notifications
                it.copy(
                    notifications = if (it.selectedFilter == NotificationFilter.SpecialCare) {
                        specialCareNotifications
                    } else {
                        remoteNotifications
                    },
                    isLoading = false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    unreadCount = if (!loadingMore && it.selectedFilter == NotificationFilter.All) {
                        result.notifications.size
                    } else {
                        it.unreadCount
                    },
                    endReached = result.endReached,
                    message = null,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            NotificationRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is NotificationRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = if (loadingMore) it.isLoading else false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private companion object {
        const val MAX_LOCAL_SPECIAL_CARE_NOTIFICATIONS = 80
    }
}

internal data class SpecialCareNotificationInsertResult(
    val notifications: List<NotificationItem>,
    val inserted: Boolean,
)

internal fun insertSpecialCareNotification(
    current: List<NotificationItem>,
    notification: NotificationItem,
    limit: Int,
): SpecialCareNotificationInsertResult {
    if (limit <= 0 || notification.id.isBlank() || current.any { it.id == notification.id }) {
        return SpecialCareNotificationInsertResult(current, inserted = false)
    }
    val next = (listOf(notification.copy(isSpecialCare = true)) + current)
        .take(limit)
    return SpecialCareNotificationInsertResult(next, inserted = true)
}
