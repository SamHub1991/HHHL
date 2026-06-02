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
import kotlinx.datetime.Clock

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
    private val cachedChatAttentionNotifications =
        cachedSnapshot.chatAttentionNotifications.withLocalReadState(restoredReadNotificationIds)
    private val cachedSpecialCareNotifications =
        cachedSnapshot.specialCareNotifications.withLocalReadState(restoredReadNotificationIds)
    private val mutableState = MutableStateFlow(
        NotificationUiState(
            notifications = cachedRemoteNotifications
                .withLocalChatAttentionNotifications(cachedChatAttentionNotifications)
                .withLocalSpecialCareNotifications(cachedSpecialCareNotifications),
            unreadCount = totalUnreadCount(
                remoteNotifications = cachedRemoteNotifications,
                chatAttentionNotifications = cachedChatAttentionNotifications,
                specialCareNotifications = cachedSpecialCareNotifications,
            ),
            specialCareNotificationCount = cachedSpecialCareNotifications.size,
            specialCareUnreadCount = cachedSpecialCareNotifications.countUnread(),
            endReached = cachedRemoteNotifications.isNotEmpty(),
        )
    )
    val state: StateFlow<NotificationUiState> = mutableState
    private var readNotificationIds: Set<String> = restoredReadNotificationIds
    private var remoteNotifications: List<NotificationItem> = cachedRemoteNotifications
    private var chatAttentionNotifications: List<NotificationItem> = cachedChatAttentionNotifications
    private var specialCareNotifications: List<NotificationItem> = cachedSpecialCareNotifications
    private var specialCareUserIds: Set<String> = emptySet()
    private var isQuietRefreshing: Boolean = false
    private var notificationRequestId: Int = 0
    private var localAllReadCutoffEpochMillis: Long = 0L

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

        val filter = state.value.selectedFilter
        val requestId = nextNotificationRequestId()
        scope.launch {
            applyResult(
                result = repository.refresh(filter),
                loadingMore = false,
                requestFilter = filter,
                requestId = requestId,
            )
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

    fun addStreamingNotification(notification: NotificationItem) {
        val item = listOf(notification).applyLocalReadState().firstOrNull() ?: return
        if (item.id.isBlank()) return
        remoteNotifications = listOf(item) + remoteNotifications.filterNot { it.id == item.id }
        syncRemoteSpecialCareNotifications(updateState = false)
        persistNotificationCache()

        mutableState.update {
            val visibleNotifications = when (it.selectedFilter) {
                NotificationFilter.All -> remoteNotifications
                    .withLocalChatAttentionNotifications(chatAttentionNotifications)
                    .withLocalSpecialCareNotifications(specialCareNotifications)
                NotificationFilter.SpecialCare -> specialCareNotifications
                else -> notificationsForFilter(it.selectedFilter)
            }
            it.copy(
                notifications = visibleNotifications,
                unreadCount = totalUnreadCount(),
                message = null,
                errorMessage = null,
                requiresRelogin = false,
            ).withSpecialCareCounts()
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

        val requestId = nextNotificationRequestId()
        scope.launch {
            applyResult(
                result = repository.refresh(filter),
                loadingMore = false,
                requestFilter = filter,
                requestId = requestId,
            )
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

        val filter = current.selectedFilter
        val remotePageBase = when (filter) {
            NotificationFilter.All -> remoteNotifications
            else -> remoteNotifications.filter { notification -> notification.type in filter.includedTypes }
        }
        val requestId = nextNotificationRequestId()
        scope.launch {
            applyResult(
                repository.loadMore(
                    currentNotifications = remotePageBase,
                    filter = filter,
                ),
                loadingMore = true,
                requestFilter = filter,
                requestId = requestId,
            )
        }
    }

    fun markAllAsRead() {
        val current = state.value
        if (current.isLoading || current.isMarkingAllRead) return
        if (current.unreadCount == 0 && current.specialCareUnreadCount == 0) {
            mutableState.update {
                it.copy(
                    message = "没有未读通知",
                    errorMessage = null,
                    requiresRelogin = false,
                ).withSpecialCareCounts()
            }
            return
        }

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

    fun flush() {
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
            when (val result = repository.flush()) {
                NotificationRepositoryResult.AllRead -> mutableState.update {
                    remoteNotifications = emptyList()
                    chatAttentionNotifications = emptyList()
                    specialCareNotifications = emptyList()
                    persistNotificationCache()
                    it.copy(
                        notifications = emptyList(),
                        isLoading = false,
                        isLoadingMore = false,
                        isMarkingAllRead = false,
                        unreadCount = 0,
                        endReached = true,
                        message = "通知已清空",
                        errorMessage = null,
                        requiresRelogin = false,
                    ).withSpecialCareCounts()
                }
                else -> applyResult(result, loadingMore = false)
            }
        }
    }

    fun sendTestNotification() {
        val current = state.value
        if (current.isLoading || current.isMarkingAllRead) return

        mutableState.update {
            it.copy(
                isMarkingAllRead = true,
                message = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.sendTestNotification()) {
                NotificationRepositoryResult.ActionSuccess -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isMarkingAllRead = false,
                        message = "测试通知已发送",
                        errorMessage = null,
                        requiresRelogin = false,
                    ).withSpecialCareCounts()
                }
                else -> applyResult(result, loadingMore = false)
            }
        }
    }

    fun createLocalReminderNotification(
        body: String = "回来看看新消息",
        header: String = "HHHL 提醒",
        icon: String? = null,
    ) {
        val current = state.value
        if (current.isLoading || current.isMarkingAllRead) return

        mutableState.update {
            it.copy(
                isMarkingAllRead = true,
                message = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.createNotification(body = body, header = header, icon = icon)) {
                NotificationRepositoryResult.ActionSuccess -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isMarkingAllRead = false,
                        message = "提醒通知已发送",
                        errorMessage = null,
                        requiresRelogin = false,
                    ).withSpecialCareCounts()
                }
                else -> applyResult(result, loadingMore = false)
            }
        }
    }

    fun syncAllReadFromStreaming() {
        val current = state.value
        if (current.unreadCount == 0 && current.specialCareUnreadCount == 0) return
        mutableState.update {
            recordLocalAllReadCutoff()
            remoteNotifications = remoteNotifications.markAllRead()
            chatAttentionNotifications = chatAttentionNotifications.markAllRead()
            specialCareNotifications = specialCareNotifications.markAllRead()
            readNotificationIds = readNotificationIds +
                remoteNotifications.mapTo(LinkedHashSet(remoteNotifications.size)) { notification -> notification.id } +
                chatAttentionNotifications.mapTo(LinkedHashSet(chatAttentionNotifications.size)) { notification ->
                    notification.id
                } +
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
            chatAttentionNotifications = chatAttentionNotifications.markNotificationRead(cleanId)
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
            } else if (it.selectedFilter == NotificationFilter.All) {
                remoteNotifications
                    .withLocalChatAttentionNotifications(chatAttentionNotifications)
                    .withLocalSpecialCareNotifications(result.notifications)
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

    fun addChatAttentionNotification(notification: NotificationItem) {
        val result = insertChatAttentionNotification(
            current = chatAttentionNotifications,
            notification = notification,
            limit = MAX_LOCAL_CHAT_ATTENTION_NOTIFICATIONS,
        )
        if (!result.inserted) return
        chatAttentionNotifications = result.notifications
        persistNotificationCache()

        mutableState.update {
            val visibleNotifications = when (it.selectedFilter) {
                NotificationFilter.All -> remoteNotifications
                    .withLocalChatAttentionNotifications(result.notifications)
                    .withLocalSpecialCareNotifications(specialCareNotifications)
                NotificationFilter.Mentions -> (
                    remoteNotifications.filter { item -> item.type in NotificationFilter.Mentions.includedTypes } +
                        result.notifications.filter { item -> item.type in NotificationFilter.Mentions.includedTypes }
                    )
                    .distinctBy { item -> item.id }
                    .sortedByDescending { item -> item.createdAtEpochMillis }
                NotificationFilter.SpecialCare -> it.notifications
                else -> it.notifications
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
        requestFilter: NotificationFilter? = null,
        requestId: Int? = null,
    ) {
        if (requestFilter != null && requestId != null && !isCurrentNotificationRequest(requestFilter, requestId)) {
            return
        }
        when (result) {
            NotificationRepositoryResult.ActionSuccess -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isMarkingAllRead = false,
                    errorMessage = null,
                    requiresRelogin = false,
                ).withSpecialCareCounts()
            }
            NotificationRepositoryResult.AllRead -> mutableState.update {
                recordLocalAllReadCutoff()
                remoteNotifications = remoteNotifications.markAllRead()
                chatAttentionNotifications = chatAttentionNotifications.markAllRead()
                specialCareNotifications = specialCareNotifications.markAllRead()
                readNotificationIds = readNotificationIds +
                    remoteNotifications.mapTo(LinkedHashSet(remoteNotifications.size)) { notification -> notification.id } +
                    chatAttentionNotifications.mapTo(LinkedHashSet(chatAttentionNotifications.size)) { notification ->
                        notification.id
                    } +
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
                val loadedNotifications = result.notifications.applyLocalReadState()
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
                            .withLocalChatAttentionNotifications(chatAttentionNotifications)
                            .withLocalSpecialCareNotifications(specialCareNotifications)
                        NotificationFilter.SpecialCare -> specialCareNotifications
                        else -> notificationsForFilter(it.selectedFilter)
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

    private fun nextNotificationRequestId(): Int {
        notificationRequestId += 1
        return notificationRequestId
    }

    private fun isCurrentNotificationRequest(
        filter: NotificationFilter,
        requestId: Int,
    ): Boolean {
        return requestId == notificationRequestId && state.value.selectedFilter == filter
    }

    private fun applyQuietRefreshResult(result: NotificationRepositoryResult) {
        when (result) {
            is NotificationRepositoryResult.Success -> mutableState.update {
                remoteNotifications = result.notifications.applyLocalReadState()
                syncRemoteSpecialCareNotifications(updateState = false)
                persistNotificationCache()
                val visibleNotifications = when (it.selectedFilter) {
                    NotificationFilter.SpecialCare -> specialCareNotifications
                    NotificationFilter.All -> remoteNotifications
                        .withLocalChatAttentionNotifications(chatAttentionNotifications)
                        .withLocalSpecialCareNotifications(specialCareNotifications)
                    else -> notificationsForFilter(it.selectedFilter)
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
            NotificationRepositoryResult.ActionSuccess,
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
                } else if (it.selectedFilter == NotificationFilter.All) {
                    remoteNotifications
                        .withLocalChatAttentionNotifications(chatAttentionNotifications)
                        .withLocalSpecialCareNotifications(specialCareNotifications)
                } else {
                    notificationsForFilter(it.selectedFilter)
                },
                unreadCount = totalUnreadCount(),
                requiresRelogin = false,
            ).withSpecialCareCounts()
        }
    }

    private fun totalUnreadCount(): Int {
        return totalUnreadCount(
            remoteNotifications = remoteNotifications,
            chatAttentionNotifications = chatAttentionNotifications,
            specialCareNotifications = specialCareNotifications,
        )
    }

    private fun totalUnreadCount(
        remoteNotifications: List<NotificationItem>,
        chatAttentionNotifications: List<NotificationItem>,
        specialCareNotifications: List<NotificationItem>,
    ): Int {
        val remoteUnreadIds = remoteNotifications
            .asSequence()
            .filterNot { it.isRead }
            .mapTo(LinkedHashSet()) { it.id }
        val chatAttentionUnreadCount = chatAttentionNotifications.count { notification ->
            !notification.isRead && notification.id !in remoteUnreadIds
        }
        val localSpecialCareUnreadCount = specialCareNotifications.count { notification ->
            !notification.isRead && notification.id !in remoteUnreadIds
        }
        return remoteUnreadIds.size + chatAttentionUnreadCount + localSpecialCareUnreadCount
    }

    private fun List<NotificationItem>.applyLocalReadState(): List<NotificationItem> {
        val notifications = withLocalReadState(readNotificationIds, localAllReadCutoffEpochMillis)
        rememberReadNotifications(notifications)
        return notifications
    }

    private fun recordLocalAllReadCutoff() {
        val latestKnownNotificationTime = sequenceOf(
            remoteNotifications,
            chatAttentionNotifications,
            specialCareNotifications,
        )
            .flatMap { notifications -> notifications.asSequence() }
            .map { notification -> notification.createdAtEpochMillis }
            .filter { createdAt -> createdAt > 0L }
            .maxOrNull()
            ?: 0L
        localAllReadCutoffEpochMillis = maxOf(
            localAllReadCutoffEpochMillis,
            Clock.System.now().toEpochMilliseconds(),
            latestKnownNotificationTime,
        )
    }

    private fun rememberReadNotifications(notifications: List<NotificationItem>) {
        if (notifications.isEmpty()) return
        val readIds = notifications
            .asSequence()
            .filter { notification -> notification.isRead }
            .map { notification -> notification.id }
            .filter { id -> id.isNotBlank() && id !in readNotificationIds }
            .toSet()
        if (readIds.isEmpty()) return
        readNotificationIds = readNotificationIds + readIds
        persistReadNotificationIds()
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
        val currentSnapshot = NotificationCacheSnapshot(
            notifications = remoteNotifications.take(MAX_LOCAL_CACHED_NOTIFICATIONS),
            chatAttentionNotifications = chatAttentionNotifications.take(MAX_LOCAL_CACHED_NOTIFICATIONS),
            specialCareNotifications = specialCareNotifications.take(MAX_LOCAL_CACHED_NOTIFICATIONS),
        )
        runCatching {
            notificationCache.update(cleanAccountId) { storedSnapshot ->
                currentSnapshot.mergeWithStoredCache(storedSnapshot)
            }
        }
    }

    private fun NotificationCacheSnapshot.mergeWithStoredCache(
        storedSnapshot: NotificationCacheSnapshot,
    ): NotificationCacheSnapshot {
        return NotificationCacheSnapshot(
            notifications = notifications.mergeStoredCacheNotifications(
                storedSnapshot.notifications,
                MAX_LOCAL_CACHED_NOTIFICATIONS,
            ),
            chatAttentionNotifications = chatAttentionNotifications.mergeStoredCacheNotifications(
                storedSnapshot.chatAttentionNotifications,
                MAX_LOCAL_CACHED_NOTIFICATIONS,
            ),
            specialCareNotifications = specialCareNotifications.mergeStoredCacheNotifications(
                storedSnapshot.specialCareNotifications,
                MAX_LOCAL_CACHED_NOTIFICATIONS,
            ),
        )
    }

    private fun List<NotificationItem>.mergeStoredCacheNotifications(
        storedNotifications: List<NotificationItem>,
        limit: Int,
    ): List<NotificationItem> {
        val storedById = storedNotifications.associateBy { it.id }
        return (map { notification ->
            notification.copy(isRead = notification.isRead || storedById[notification.id]?.isRead == true)
        } + storedNotifications)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAtEpochMillis }
            .take(limit)
    }

    private fun notificationsForFilter(filter: NotificationFilter): List<NotificationItem> {
        return when (filter) {
            NotificationFilter.All -> remoteNotifications
                .withLocalChatAttentionNotifications(chatAttentionNotifications)
                .withLocalSpecialCareNotifications(specialCareNotifications)
            NotificationFilter.SpecialCare -> specialCareNotifications
            else -> (
                remoteNotifications.filter { notification -> notification.type in filter.includedTypes } +
                    chatAttentionNotifications.filter { notification -> notification.type in filter.includedTypes }
                )
                .distinctBy { notification -> notification.id }
                .sortedByDescending { notification -> notification.createdAtEpochMillis }
        }
    }

    private companion object {
        const val MAX_LOCAL_CHAT_ATTENTION_NOTIFICATIONS = 120
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
    allReadCutoffEpochMillis: Long = 0L,
): List<NotificationItem> {
    if (readNotificationIds.isEmpty() && allReadCutoffEpochMillis <= 0L) return this
    var changed = false
    val next = map { notification ->
        if (
            !notification.isRead &&
            (notification.id in readNotificationIds || notification.isCoveredByLocalAllReadCutoff(allReadCutoffEpochMillis))
        ) {
            changed = true
            notification.copy(isRead = true)
        } else {
            notification
        }
    }
    return if (changed) next else this
}

private fun NotificationItem.isCoveredByLocalAllReadCutoff(allReadCutoffEpochMillis: Long): Boolean {
    return allReadCutoffEpochMillis > 0L && createdAtEpochMillis > 0L && createdAtEpochMillis <= allReadCutoffEpochMillis
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

private fun List<NotificationItem>.withLocalSpecialCareNotifications(
    specialCareNotifications: List<NotificationItem>,
): List<NotificationItem> {
    if (specialCareNotifications.isEmpty()) return this
    val remoteIds = mapTo(LinkedHashSet(size)) { it.id }
    return (specialCareNotifications.filterNot { it.id in remoteIds } + this)
        .distinctBy { it.id }
}

private fun List<NotificationItem>.withLocalChatAttentionNotifications(
    chatAttentionNotifications: List<NotificationItem>,
): List<NotificationItem> {
    if (chatAttentionNotifications.isEmpty()) return this
    val remoteIds = mapTo(LinkedHashSet(size)) { it.id }
    return (chatAttentionNotifications.filterNot { it.id in remoteIds } + this)
        .distinctBy { it.id }
        .sortedByDescending { it.createdAtEpochMillis }
}

internal data class SpecialCareNotificationInsertResult(
    val notifications: List<NotificationItem>,
    val inserted: Boolean,
)

internal data class ChatAttentionNotificationInsertResult(
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
        .sortedByDescending { it.createdAtEpochMillis }
        .take(limit)
    return SpecialCareNotificationInsertResult(next, inserted = true)
}

internal fun insertChatAttentionNotification(
    current: List<NotificationItem>,
    notification: NotificationItem,
    limit: Int,
): ChatAttentionNotificationInsertResult {
    if (limit <= 0 || notification.id.isBlank() || current.any { it.id == notification.id }) {
        return ChatAttentionNotificationInsertResult(current, inserted = false)
    }
    val next = (listOf(notification.copy(isSpecialCare = false)) + current)
        .sortedByDescending { it.createdAtEpochMillis }
        .take(limit)
    return ChatAttentionNotificationInsertResult(next, inserted = true)
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
        .sortedByDescending { it.createdAtEpochMillis }
        .take(limit)
    return if (next == current) current else next
}
