package cc.hhhl.client.repository

import cc.hhhl.client.api.NotificationApi
import cc.hhhl.client.api.NotificationActionResult
import cc.hhhl.client.api.NotificationLoadResult
import cc.hhhl.client.api.SharkeyNotificationApi
import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationItem

open class NotificationRepository(
    private val tokenProvider: () -> String?,
    private val api: NotificationApi = SharkeyNotificationApi(),
) {
    open suspend fun refresh(): NotificationRepositoryResult {
        return refresh(NotificationFilter.All)
    }

    open suspend fun refresh(filter: NotificationFilter): NotificationRepositoryResult {
        return load(
            filter = filter,
            currentNotifications = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMore(
        currentNotifications: List<NotificationItem>,
        filter: NotificationFilter = NotificationFilter.All,
    ): NotificationRepositoryResult {
        return load(
            filter = filter,
            currentNotifications = currentNotifications,
            untilId = currentNotifications.lastOrNull()?.id,
        )
    }

    open suspend fun markAllAsRead(): NotificationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NotificationRepositoryResult.Unauthorized

        return when (val result = api.markAllAsRead(token)) {
            NotificationActionResult.Success -> NotificationRepositoryResult.AllRead
            NotificationActionResult.Unauthorized -> NotificationRepositoryResult.Unauthorized
            is NotificationActionResult.NetworkError -> {
                NotificationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is NotificationActionResult.ServerError -> NotificationRepositoryResult.Error(result.message)
        }
    }

    private suspend fun load(
        filter: NotificationFilter,
        currentNotifications: List<NotificationItem>,
        untilId: String?,
    ): NotificationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return NotificationRepositoryResult.Unauthorized

        return when (
            val result = api.loadNotifications(
                token = token,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
                includeTypes = filter.includedTypes,
            )
        ) {
            is NotificationLoadResult.Success -> NotificationRepositoryResult.Success(
                notifications = mergeNotificationPage(currentNotifications, result.notifications),
                endReached = result.notifications.isEmpty(),
            )
            NotificationLoadResult.Unauthorized -> NotificationRepositoryResult.Unauthorized
            is NotificationLoadResult.NetworkError -> {
                NotificationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is NotificationLoadResult.ServerError -> NotificationRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

internal fun mergeNotificationPage(
    currentNotifications: List<NotificationItem>,
    loadedNotifications: List<NotificationItem>,
): List<NotificationItem> {
    if (loadedNotifications.isEmpty()) return currentNotifications
    val seenIds = currentNotifications.mapTo(mutableSetOf()) { it.id }
    val newItems = loadedNotifications.filter { seenIds.add(it.id) }
    if (newItems.isEmpty()) return currentNotifications
    return currentNotifications + newItems
}

sealed interface NotificationRepositoryResult {
    data object AllRead : NotificationRepositoryResult

    data class Success(
        val notifications: List<NotificationItem>,
        val endReached: Boolean = false,
    ) : NotificationRepositoryResult

    data object Unauthorized : NotificationRepositoryResult

    data class Error(val message: String) : NotificationRepositoryResult
}
