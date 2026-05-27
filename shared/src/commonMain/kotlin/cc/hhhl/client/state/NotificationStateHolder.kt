package cc.hhhl.client.state

import cc.hhhl.client.cache.NoopNotificationCache
import cc.hhhl.client.cache.NotificationCache
import cc.hhhl.client.cache.NotificationCacheSnapshot
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
    val specialCareNotificationCount: Int = 0,
    val specialCareUnreadCount: Int = 0,
    val endReached: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

interface NotificationReadStore {
    fun loadReadNotificationIds(accountId: String): Set<String>

    fun saveReadNotificationIds(accountId: String, notificationIds: Set<String>)

    fun clearAccount(accountId: String)
}

object NoopNotificationReadStore : NotificationReadStore {
    override fun loadReadNotificationIds(accountId: String): Set<String> = emptySet()

    override fun saveReadNotificationIds(accountId: String, notificationIds: Set<String>) = Unit

    override fun clearAccount(accountId: String) = Unit
}

class NotificationStateHolder(
    private val repository: NotificationRepository,
    private val readStore: NotificationReadStore = NoopNotificationReadStore,
    private val notificationCache: NotificationCache = NoopNotificationCache,
    private val accountId: String? = null,
    private val scope: CoroutineScope,
) {
    private val restoredReadNotificationIds = restoreReadNotificationIds()
    private val cachedSnapshot = restoreCachedSnapshot()
    private val cachedRemoteNotifications = cachedSnapshot.notifications.withLocalReadState(restoredReadNotificationIds)
    private val cachedSpecialCareNotifications =
        cachedSnapshot.specialCareNotifications.withLocalReadState(restoredReadNotificationIds)
    private val mutableState = MutableStateFlow(
        NotificationUiState(
            notifications = cachedRemoteNotifications,
            unreadCount = cachedRemoteNotifications.countUnread() + cachedSpecialCareNotifications.countUnread(),
            specialCareNotificationCount = cachedSpecialCareNotifications.size,
            specialCareUnreadCount = cachedSpecialCareNotifications.countUnread(),
            endReached = cachedRemoteNotifications.isNotEmpty(),
        )
    )
    val state: StateFlow<NotificationUiState> = mutableState
    private var readNotificationIds: Set<String> = restoredReadNotificationIds
    private var remoteNotifications: List<NotificationItem> = cachedRemoteNotifications
    private var specialCareNotifications: List<NotificationItem> = cachedSpecialCareNotifications
    private var specialCareUserIds: Set<String> = emptySet()
    private var isQuietRefreshing: Boolean = false

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
                ).withSpecialCareCounts()
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

    fun refreshQuietly() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || current.isMarkingAllRead || isQuietRefreshing) return
        isQuietRefreshing = true

        scope.launch {
            try {
                applyQuietRefreshResult(repository.refresh(NotificationFilter.All))
            } finally {
                isQuietRefreshing = false
            }
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
                ).withSpecialCareCounts()
            }
            return
        }

        mutableState.update {
            val cachedNotifications = notificationsForFilter(filter)
            it.copy(
                selectedFilter = filter,
                notifications = cachedNotifications,
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

    fun syncAllReadFromStreaming() {
        val current = state.value
        if (current.unreadCount == 0 && current.specialCareUnreadCount == 0) return
        mutableState.update {
            remoteNotifications = remoteNotifications.markAllRead()
            specialCareNotifications = specialCareNotifications.markAllRead()
            readNotificationIds = readNotificationIds +
                remoteNotifications.mapTo(LinkedHashSet(remoteNotifications.size)) { notification -> notification.id } +
                specialCareNotifications.mapTo(LinkedHashSet(specialCareNotifications.size)) { notification -> notification.id }
            persistReadNotificationIds()
            persistNotificationCache()
            it.copy(
                notifications = when (it.selectedFilter) {
                    NotificationFilter.SpecialCare -> specialCareNotifications
                    else -> it.notifications.markAllRead()
                },
                unreadCount = 0,
                message = null,
                errorMessage = null,
                requiresRelogin = false,
            ).withSpecialCareCounts()
        }
    }

    fun markNotificationRead(notificationId: String) {
        val cleanId = notificationId.takeIf { it.isNotBlank() } ?: return

        mutableState.update { current ->
            val visibleNotification = current.notifications.firstOrNull { it.id == cleanId }
            if (visibleNotification?.isRead == true) return@update current

            readNotificationIds = readNotificationIds.plus(cleanId)
            remoteNotifications = remoteNotifications.markNotificationRead(cleanId)
            specialCareNotifications = specialCareNotifications.markNotificationRead(cleanId)
            persistReadNotificationIds()
            persistNotificationCache()

            val visibleNotifications = current.notifications.markNotificationRead(cleanId)
            val unreadDelta = if (visibleNotification != null && !visibleNotification.isRead) 1 else 0
            current.copy(
                notifications = visibleNotifications,
                unreadCount = (current.unreadCount - unreadDelta).coerceAtLeast(0),
                message = null,
                requiresRelogin = false,
            ).withSpecialCareCounts()
        }
    }

    fun dismissFollowRequestNotification(actorUserId: String) {
        if (actorUserId.isBlank()) return

        mutableState.update {
            remoteNotifications = remoteNotifications.filterNot { notification ->
                notification.actor.id == actorUserId &&
                    notification.type == NotificationType.FollowRequestReceived
            }
            specialCareNotifications = specialCareNotifications.filterNot { notification ->
                notification.actor.id == actorUserId &&
                    notification.type == NotificationType.FollowRequestReceived
            }
            persistNotificationCache()
            val totalUnreadCount = totalUnreadCount()
            it.copy(
                notifications = it.notifications.filterNot { notification ->
                    notification.actor.id == actorUserId &&
                        notification.type == NotificationType.FollowRequestReceived
                },
                unreadCount = totalUnreadCount,
                requiresRelogin = false,
            ).withSpecialCareCounts()
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
        persistNotificationCache()

        mutableState.update {
            val visibleNotifications = if (it.selectedFilter == NotificationFilter.SpecialCare) {
                result.notifications
            } else {
                it.notifications
            }
            it.copy(
                notifications = visibleNotifications,
                unreadCount = totalUnreadCount(),
                requiresRelogin = false,
            ).withSpecialCareCounts()
        }
    }

    fun updateSpecialCareUsers(userIds: Set<String>) {
        specialCareUserIds = userIds.cleanUserIds()
        syncRemoteSpecialCareNotifications()
    }

    private fun applyResult(
        result: NotificationRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            NotificationRepositoryResult.AllRead -> mutableState.update {
                remoteNotifications = remoteNotifications.markAllRead()
                specialCareNotifications = specialCareNotifications.markAllRead()
                readNotificationIds = readNotificationIds +
                    remoteNotifications.mapTo(LinkedHashSet(remoteNotifications.size)) { notification -> notification.id } +
                    specialCareNotifications.mapTo(LinkedHashSet(specialCareNotifications.size)) { notification -> notification.id }
                persistReadNotificationIds()
                persistNotificationCache()
                it.copy(
                    notifications = when (it.selectedFilter) {
                        NotificationFilter.SpecialCare -> specialCareNotifications
                        else -> it.notifications.markAllRead()
                    },
                    isLoading = false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    unreadCount = 0,
                    endReached = it.endReached,
                    message = "通知已全部标记为已读",
                    errorMessage = null,
                    requiresRelogin = false,
                ).withSpecialCareCounts()
            }
            is NotificationRepositoryResult.Success -> mutableState.update {
                val loadedNotifications = result.notifications.withLocalReadState(readNotificationIds)
                remoteNotifications = when (it.selectedFilter) {
                    NotificationFilter.All -> loadedNotifications
                    NotificationFilter.SpecialCare -> remoteNotifications
                    else -> remoteNotifications.mergeNotificationRefresh(loadedNotifications)
                }
                syncRemoteSpecialCareNotifications(updateState = false)
                persistNotificationCache()
                val totalUnreadCount = totalUnreadCount()
                it.copy(
                    notifications = when (it.selectedFilter) {
                        NotificationFilter.All -> remoteNotifications
                        NotificationFilter.SpecialCare -> specialCareNotifications
                        else -> loadedNotifications
                    },
                    isLoading = false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    unreadCount = totalUnreadCount,
                    endReached = result.endReached,
                    message = null,
                    errorMessage = null,
                    requiresRelogin = false,
                ).withSpecialCareCounts()
            }
            NotificationRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                ).withSpecialCareCounts()
            }
            is NotificationRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = if (loadingMore) it.isLoading else false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                ).withSpecialCareCounts()
            }
        }
    }

    private fun applyQuietRefreshResult(result: NotificationRepositoryResult) {
        when (result) {
            is NotificationRepositoryResult.Success -> mutableState.update {
                remoteNotifications = result.notifications.withLocalReadState(readNotificationIds)
                syncRemoteSpecialCareNotifications(updateState = false)
                persistNotificationCache()
                val visibleNotifications = when (it.selectedFilter) {
                    NotificationFilter.SpecialCare -> specialCareNotifications
                    NotificationFilter.All -> remoteNotifications
                    else -> it.notifications
                }
                it.copy(
                    notifications = visibleNotifications,
                    unreadCount = totalUnreadCount(),
                    message = null,
                    errorMessage = null,
                    requiresRelogin = false,
                ).withSpecialCareCounts()
            }
            NotificationRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                ).withSpecialCareCounts()
            }
            NotificationRepositoryResult.AllRead,
            is NotificationRepositoryResult.Error,
            -> Unit
        }
    }

    private fun NotificationUiState.withSpecialCareCounts(): NotificationUiState {
        return copy(
            specialCareNotificationCount = specialCareNotifications.size,
            specialCareUnreadCount = specialCareNotifications.countUnread(),
        )
    }

    private fun syncRemoteSpecialCareNotifications(updateState: Boolean = true) {
        if (specialCareUserIds.isEmpty() || remoteNotifications.isEmpty()) return
        val syncedNotifications = mergeRemoteSpecialCareNotifications(
            current = specialCareNotifications,
            remote = remoteNotifications,
            specialCareUserIds = specialCareUserIds,
            limit = MAX_LOCAL_SPECIAL_CARE_NOTIFICATIONS,
        )
        if (syncedNotifications === specialCareNotifications) return
        specialCareNotifications = syncedNotifications
        persistNotificationCache()
        if (!updateState) return
        mutableState.update {
            it.copy(
                notifications = if (it.selectedFilter == NotificationFilter.SpecialCare) {
                    specialCareNotifications
                } else {
                    it.notifications
                },
                unreadCount = totalUnreadCount(),
                requiresRelogin = false,
            ).withSpecialCareCounts()
        }
    }

    private fun totalUnreadCount(): Int {
        val remoteUnreadIds = remoteNotifications
            .asSequence()
            .filterNot { it.isRead }
            .mapTo(LinkedHashSet()) { it.id }
        val localSpecialCareUnreadCount = specialCareNotifications.count { notification ->
            !notification.isRead && notification.id !in remoteUnreadIds
        }
        return remoteUnreadIds.size + localSpecialCareUnreadCount
    }

    private fun restoreReadNotificationIds(): Set<String> {
        val cleanAccountId = accountId?.takeIf { it.isNotBlank() } ?: return emptySet()
        return runCatching { readStore.loadReadNotificationIds(cleanAccountId).cleanNotificationIds() }
            .getOrDefault(emptySet())
    }

    private fun restoreCachedSnapshot(): NotificationCacheSnapshot {
        val cleanAccountId = accountId?.takeIf { it.isNotBlank() } ?: return NotificationCacheSnapshot()
        return runCatching { notificationCache.read(cleanAccountId) }
            .getOrDefault(NotificationCacheSnapshot())
    }

    private fun persistReadNotificationIds() {
        val cleanAccountId = accountId?.takeIf { it.isNotBlank() } ?: return
        val retainedIds = readNotificationIds
            .asSequence()
            .filter { it.isNotBlank() }
            .take(MAX_LOCAL_READ_NOTIFICATION_IDS)
            .toSet()
        readNotificationIds = retainedIds
        runCatching { readStore.saveReadNotificationIds(cleanAccountId, retainedIds) }
    }

    private fun persistNotificationCache() {
        val cleanAccountId = accountId?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            notificationCache.write(
                cleanAccountId,
                NotificationCacheSnapshot(
                    notifications = remoteNotifications.take(MAX_LOCAL_CACHED_NOTIFICATIONS),
                    specialCareNotifications = specialCareNotifications.take(MAX_LOCAL_CACHED_NOTIFICATIONS),
                ),
            )
        }
    }

    private fun notificationsForFilter(filter: NotificationFilter): List<NotificationItem> {
        return when (filter) {
            NotificationFilter.All -> remoteNotifications
            NotificationFilter.SpecialCare -> specialCareNotifications
            else -> remoteNotifications.filter { notification -> notification.type in filter.includedTypes }
        }
    }

    private companion object {
        const val MAX_LOCAL_SPECIAL_CARE_NOTIFICATIONS = 80
        const val MAX_LOCAL_READ_NOTIFICATION_IDS = 1000
        const val MAX_LOCAL_CACHED_NOTIFICATIONS = 240
    }
}

private fun Set<String>.cleanNotificationIds(): Set<String> {
    return mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
}

private fun Set<String>.cleanUserIds(): Set<String> {
    return mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
}

private fun List<NotificationItem>.markNotificationRead(notificationId: String): List<NotificationItem> {
    val index = indexOfFirst { it.id == notificationId }
    if (index < 0 || this[index].isRead) return this
    val next = toMutableList()
    next[index] = this[index].copy(isRead = true)
    return next
}

private fun List<NotificationItem>.withLocalReadState(
    readNotificationIds: Set<String>,
): List<NotificationItem> {
    if (readNotificationIds.isEmpty()) return this
    var changed = false
    val next = map { notification ->
        if (!notification.isRead && notification.id in readNotificationIds) {
            changed = true
            notification.copy(isRead = true)
        } else {
            notification
        }
    }
    return if (changed) next else this
}

private fun List<NotificationItem>.markAllRead(): List<NotificationItem> {
    var changed = false
    val next = map { notification ->
        if (!notification.isRead) {
            changed = true
            notification.copy(isRead = true)
        } else {
            notification
        }
    }
    return if (changed) next else this
}

private fun List<NotificationItem>.countUnread(): Int {
    return count { !it.isRead }
}

private fun List<NotificationItem>.mergeNotificationRefresh(
    loadedNotifications: List<NotificationItem>,
): List<NotificationItem> {
    if (isEmpty() || loadedNotifications.isEmpty()) return loadedNotifications.ifEmpty { this }
    val loadedIds = loadedNotifications.mapTo(LinkedHashSet(loadedNotifications.size)) { it.id }
    return loadedNotifications + filterNot { it.id in loadedIds }
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

internal fun mergeRemoteSpecialCareNotifications(
    current: List<NotificationItem>,
    remote: List<NotificationItem>,
    specialCareUserIds: Set<String>,
    limit: Int,
): List<NotificationItem> {
    if (limit <= 0 || remote.isEmpty() || specialCareUserIds.isEmpty()) return current
    val currentById = current.associateBy { it.id }
    val remoteSpecialCare = remote
        .asSequence()
        .filter { notification -> notification.id.isNotBlank() && notification.actor.id in specialCareUserIds }
        .map { notification ->
            val previous = currentById[notification.id]
            notification.copy(
                isSpecialCare = true,
                isRead = previous?.isRead == true || notification.isRead,
            )
        }
        .toList()
    if (remoteSpecialCare.isEmpty()) return current

    val remoteSpecialCareIds = remoteSpecialCare.mapTo(LinkedHashSet(remoteSpecialCare.size)) { it.id }
    val next = (remoteSpecialCare + current.filterNot { it.id in remoteSpecialCareIds })
        .distinctBy { it.id }
        .take(limit)
    return if (next == current) current else next
}
