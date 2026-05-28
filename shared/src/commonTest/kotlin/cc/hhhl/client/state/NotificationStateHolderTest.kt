package cc.hhhl.client.state

import cc.hhhl.client.api.NotificationLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.repository.NotificationRepository
import cc.hhhl.client.repository.NotificationRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationStateHolderTest {
    @Test
    fun refreshStoresLoadedNotifications() = runTest {
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(listOf(FakeData.notifications[0])),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(FakeData.notifications[0]), holder.state.value.notifications)
        assertEquals(1, holder.state.value.unreadCount)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun unauthorizedRefreshMarksRelogin() = runTest {
        val holder = NotificationStateHolder(
            repository = fakeRepository(NotificationRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
        assertTrue(holder.state.value.requiresRelogin)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val notification = FakeData.notifications[0]
        val holder = NotificationStateHolder(
            repository = sequenceRepository(
                NotificationRepositoryResult.Unauthorized,
                NotificationRepositoryResult.Success(listOf(notification)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(notification), holder.state.value.notifications)
    }

    @Test
    fun loadMoreStoresEndReached() = runTest {
        val first = FakeData.notifications[0]
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(listOf(first)),
                loadMoreResult = NotificationRepositoryResult.Success(
                    notifications = listOf(first),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.loadMore()
        advanceUntilIdle()

        assertTrue(holder.state.value.endReached)
    }

    @Test
    fun selectFilterRefreshesAndClearsOldNotifications() = runTest {
        val calls = mutableListOf<NotificationFilter>()
        val first = FakeData.notifications[0]
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(listOf(first)),
                onRefresh = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.selectFilter(NotificationFilter.Mentions)
        assertTrue(holder.state.value.isLoading)
        assertEquals(NotificationFilter.Mentions, holder.state.value.selectedFilter)
        assertEquals(emptyList(), holder.state.value.notifications)
        advanceUntilIdle()

        assertEquals(listOf(NotificationFilter.All, NotificationFilter.Mentions), calls)
        assertEquals(listOf(first), holder.state.value.notifications)
        assertEquals(1, holder.state.value.unreadCount)
    }

    @Test
    fun filteredRefreshMergesIntoRemoteSourceAndKeepsTotalUnread() = runTest {
        val remote = FakeData.notifications[0].copy(id = "remote-all")
        val mention = FakeData.notifications[1].copy(
            id = "remote-mention",
            type = NotificationType.Mention,
        )
        val holder = NotificationStateHolder(
            repository = sequenceRepository(
                NotificationRepositoryResult.Success(listOf(remote)),
                NotificationRepositoryResult.Success(listOf(mention)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.selectFilter(NotificationFilter.Mentions)
        advanceUntilIdle()

        assertEquals(listOf("remote-mention"), holder.state.value.notifications.map { it.id })
        assertEquals(2, holder.state.value.unreadCount)
    }

    @Test
    fun loadMoreDoesNothingAfterEndReached() = runTest {
        val first = FakeData.notifications[0]
        val calls = mutableListOf<String>()
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(
                    notifications = listOf(first),
                    endReached = true,
                ),
                loadMoreResult = NotificationRepositoryResult.Success(listOf(first)),
                onLoadMore = { calls.add("loadMore") },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.loadMore()
        advanceUntilIdle()

        assertEquals(emptyList(), calls)
    }

    @Test
    fun markAllAsReadKeepsVisibleNotificationsAndStoresMessage() = runTest {
        val first = FakeData.notifications[0]
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(listOf(first)),
                markAllResult = NotificationRepositoryResult.AllRead,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.markAllAsRead()
        assertTrue(holder.state.value.isMarkingAllRead)
        advanceUntilIdle()

        assertFalse(holder.state.value.isMarkingAllRead)
        assertEquals(listOf(first.id), holder.state.value.notifications.map { it.id })
        assertTrue(holder.state.value.notifications.single().isRead)
        assertEquals(0, holder.state.value.unreadCount)
        assertEquals("通知已全部标记为已读", holder.state.value.message)
    }

    @Test
    fun markNotificationReadUpdatesVisibleItemAndUnreadCountOnce() = runTest {
        val first = FakeData.notifications[0]
        val second = FakeData.notifications[1]
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(listOf(first, second)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.markNotificationRead(first.id)
        holder.markNotificationRead(first.id)

        assertTrue(holder.state.value.notifications.first { it.id == first.id }.isRead)
        assertEquals(1, holder.state.value.unreadCount)
    }

    @Test
    fun sendTestNotificationStoresSuccessMessage() = runTest {
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.sendTestNotification()
        assertTrue(holder.state.value.isMarkingAllRead)
        advanceUntilIdle()

        assertFalse(holder.state.value.isMarkingAllRead)
        assertEquals("测试通知已发送", holder.state.value.message)
    }

    @Test
    fun createReminderNotificationStoresSuccessMessage() = runTest {
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.createLocalReminderNotification()
        assertTrue(holder.state.value.isMarkingAllRead)
        advanceUntilIdle()

        assertFalse(holder.state.value.isMarkingAllRead)
        assertEquals("提醒通知已发送", holder.state.value.message)
    }

    @Test
    fun dismissFollowRequestNotificationRemovesMatchingActorRequestsOnly() = runTest {
        val actor = FakeData.me
        val followRequest = NotificationItem(
            id = "follow-request-1",
            type = NotificationType.FollowRequestReceived,
            actor = actor,
            text = "请求关注你",
            createdAtLabel = "刚刚",
        )
        val accepted = NotificationItem(
            id = "accepted-1",
            type = NotificationType.FollowRequestAccepted,
            actor = actor,
            text = "接受了你的关注请求",
            createdAtLabel = "刚刚",
        )
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(listOf(followRequest, accepted)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.dismissFollowRequestNotification(actor.id)

        assertEquals(listOf(accepted), holder.state.value.notifications)
        assertEquals(1, holder.state.value.unreadCount)
    }

    @Test
    fun dismissFollowRequestNotificationAlsoUpdatesRemoteSourceList() = runTest {
        val actor = FakeData.me
        val followRequest = NotificationItem(
            id = "follow-request-remote",
            type = NotificationType.FollowRequestReceived,
            actor = actor,
            text = "请求关注你",
            createdAtLabel = "刚刚",
        )
        val nextRemote = FakeData.notifications[0].copy(id = "remote-next")
        val holder = NotificationStateHolder(
            repository = sequenceRepository(
                NotificationRepositoryResult.Success(listOf(followRequest, nextRemote)),
                NotificationRepositoryResult.Success(listOf(nextRemote)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.dismissFollowRequestNotification(actor.id)
        holder.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("remote-next"), holder.state.value.notifications.map { it.id })
    }

    @Test
    fun dismissFollowRequestNotificationUpdatesUnreadWhenRequestIsNotVisible() = runTest {
        val actor = FakeData.me
        val followRequest = NotificationItem(
            id = "follow-request-hidden",
            type = NotificationType.FollowRequestReceived,
            actor = actor,
            text = "请求关注你",
            createdAtLabel = "刚刚",
        )
        val mention = FakeData.notifications[1].copy(
            id = "visible-mention",
            type = NotificationType.Mention,
        )
        val holder = NotificationStateHolder(
            repository = sequenceRepository(
                NotificationRepositoryResult.Success(listOf(followRequest, mention)),
                NotificationRepositoryResult.Success(listOf(mention)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.selectFilter(NotificationFilter.Mentions)
        advanceUntilIdle()
        holder.dismissFollowRequestNotification(actor.id)

        assertEquals(listOf("visible-mention"), holder.state.value.notifications.map { it.id })
        assertEquals(1, holder.state.value.unreadCount)
    }

    @Test
    fun dismissFollowRequestNotificationClearsReloginAfterUnauthorized() = runTest {
        val actor = FakeData.me
        val followRequest = NotificationItem(
            id = "follow-request-2",
            type = NotificationType.FollowRequestReceived,
            actor = actor,
            text = "请求关注你",
            createdAtLabel = "刚刚",
        )
        val holder = NotificationStateHolder(
            repository = sequenceRepository(
                NotificationRepositoryResult.Unauthorized,
                NotificationRepositoryResult.Success(listOf(followRequest)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refresh()
        advanceUntilIdle()
        holder.dismissFollowRequestNotification(actor.id)

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(emptyList(), holder.state.value.notifications)
    }

    @Test
    fun insertSpecialCareNotificationMarksAndPrependsNewNotificationOnly() {
        val first = FakeData.notifications[0].copy(id = "special-1")
        val second = FakeData.notifications[1].copy(id = "special-2")

        val inserted = insertSpecialCareNotification(
            current = listOf(first.copy(isSpecialCare = true)),
            notification = second,
            limit = 2,
        )
        val duplicate = insertSpecialCareNotification(
            current = inserted.notifications,
            notification = second.copy(text = "duplicate"),
            limit = 2,
        )

        assertTrue(inserted.inserted)
        assertEquals(listOf("special-2", "special-1"), inserted.notifications.map { it.id })
        assertTrue(inserted.notifications.first().isSpecialCare)
        assertFalse(duplicate.inserted)
        assertEquals(inserted.notifications, duplicate.notifications)
    }

    @Test
    fun addSpecialCareNotificationDoesNotIncrementUnreadForDuplicate() {
        val notification = FakeData.notifications[0].copy(id = "special-1")
        val holder = NotificationStateHolder(
            repository = fakeRepository(NotificationRepositoryResult.Success(emptyList())),
            scope = TestScope(),
        )

        holder.addSpecialCareNotification(notification)
        holder.addSpecialCareNotification(notification.copy(text = "duplicate"))

        assertEquals(1, holder.state.value.unreadCount)
    }

    @Test
    fun addChatAttentionNotificationShowsInMentionsFilterWithoutMarkingSpecialCare() {
        val notification = FakeData.notifications[0].copy(
            id = "chat-mention-1",
            type = NotificationType.Mention,
            text = "有人 @ 你 · 在聊天中发来了新消息",
            chatUserId = "chat-user-1",
            chatMessageId = "chat-message-1",
        )
        val holder = NotificationStateHolder(
            repository = fakeRepository(NotificationRepositoryResult.Success(emptyList())),
            scope = TestScope(),
        )

        holder.addChatAttentionNotification(notification)
        holder.selectFilter(NotificationFilter.Mentions)

        assertEquals(listOf("chat-mention-1"), holder.state.value.notifications.map { it.id })
        assertFalse(holder.state.value.notifications.single().isSpecialCare)
        assertEquals(1, holder.state.value.unreadCount)
        assertEquals(0, holder.state.value.specialCareUnreadCount)
    }

    @Test
    fun allFilterMergesChatAttentionAheadOfOlderRemoteNotifications() = runTest {
        val remote = FakeData.notifications[0].copy(
            id = "remote-older",
            createdAtEpochMillis = 1_000L,
        )
        val chatAttention = FakeData.notifications[1].copy(
            id = "chat-reply-new",
            type = NotificationType.Reply,
            text = "有人回复你 · 在聊天中发来了新消息",
            createdAtEpochMillis = 2_000L,
            chatUserId = "chat-user-2",
            chatMessageId = "chat-message-2",
        )
        val holder = NotificationStateHolder(
            repository = fakeRepository(NotificationRepositoryResult.Success(listOf(remote))),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.addChatAttentionNotification(chatAttention)

        assertEquals(listOf("chat-reply-new", "remote-older"), holder.state.value.notifications.map { it.id })
        assertEquals(2, holder.state.value.unreadCount)
    }

    @Test
    fun loadMoreUsesRemoteNotificationsAsPagingBaseWhenAllFilterContainsLocalChatAttention() = runTest {
        val remote = FakeData.notifications[0].copy(id = "remote-base")
        var loadMoreIds: List<String> = emptyList()
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(listOf(remote)),
                loadMoreResult = NotificationRepositoryResult.Success(listOf(remote)),
                onLoadMoreNotifications = { notifications -> loadMoreIds = notifications.map { it.id } },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.addChatAttentionNotification(
            FakeData.notifications[1].copy(
                id = "chat-local-load-more",
                type = NotificationType.Mention,
                chatUserId = "chat-user-load-more",
                chatMessageId = "chat-message-load-more",
            ),
        )
        holder.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("remote-base"), loadMoreIds)
    }

    @Test
    fun refreshDerivesSpecialCareNotificationsFromRemoteActors() = runTest {
        val specialCareUser = FakeData.notifications[0].actor
        val specialCareNotification = FakeData.notifications[0].copy(id = "remote-special")
        val regularNotification = FakeData.notifications[1].copy(id = "remote-regular")
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                NotificationRepositoryResult.Success(listOf(specialCareNotification, regularNotification)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateSpecialCareUsers(setOf(specialCareUser.id))
        holder.refresh()
        advanceUntilIdle()
        holder.selectFilter(NotificationFilter.SpecialCare)

        assertEquals(listOf("remote-special"), holder.state.value.notifications.map { it.id })
        assertTrue(holder.state.value.notifications.single().isSpecialCare)
        assertEquals(1, holder.state.value.specialCareUnreadCount)
        assertEquals(2, holder.state.value.unreadCount)
    }

    @Test
    fun remoteDerivedSpecialCareNotificationsDoNotDoubleCountUnread() = runTest {
        val specialCareNotification = FakeData.notifications[0].copy(id = "remote-special-2")
        val holder = NotificationStateHolder(
            repository = fakeRepository(NotificationRepositoryResult.Success(listOf(specialCareNotification))),
            scope = TestScope(testScheduler),
        )

        holder.updateSpecialCareUsers(setOf(specialCareNotification.actor.id))
        holder.refresh()
        advanceUntilIdle()

        assertEquals(1, holder.state.value.specialCareUnreadCount)
        assertEquals(1, holder.state.value.unreadCount)
    }

    @Test
    fun mergeRemoteSpecialCareNotificationsKeepsExistingLocalItemsAndMarksRemoteItems() {
        val specialCareUser = FakeData.notifications[0].actor
        val remote = FakeData.notifications[0].copy(id = "remote-special-3", actor = specialCareUser)
        val local = FakeData.notifications[1].copy(id = "special-local-merge", isSpecialCare = true)

        val merged = mergeRemoteSpecialCareNotifications(
            current = listOf(local),
            remote = listOf(remote),
            specialCareUserIds = setOf(specialCareUser.id),
            limit = 10,
        )

        assertEquals(listOf("remote-special-3", "special-local-merge"), merged.map { it.id })
        assertTrue(merged.first().isSpecialCare)
    }

    @Test
    fun specialCareNotificationsAreSortedByNewestTimestamp() {
        val specialCareUser = FakeData.notifications[0].actor
        val olderLocal = FakeData.notifications[0].copy(
            id = "special-older-local",
            actor = specialCareUser,
            createdAtEpochMillis = 1_000L,
        )
        val newestRemote = FakeData.notifications[1].copy(
            id = "special-newest-remote",
            actor = specialCareUser,
            createdAtEpochMillis = 3_000L,
        )
        val middleRemote = FakeData.notifications[2].copy(
            id = "special-middle-remote",
            actor = specialCareUser,
            createdAtEpochMillis = 2_000L,
        )

        val merged = mergeRemoteSpecialCareNotifications(
            current = listOf(olderLocal.copy(isSpecialCare = true)),
            remote = listOf(middleRemote, newestRemote),
            specialCareUserIds = setOf(specialCareUser.id),
            limit = 10,
        )

        assertEquals(
            listOf("special-newest-remote", "special-middle-remote", "special-older-local"),
            merged.map { it.id },
        )
    }

    @Test
    fun refreshAllKeepsLocalSpecialCareUnreadInTotalUnreadCount() = runTest {
        val remote = FakeData.notifications[0].copy(id = "remote-1")
        val specialCare = FakeData.notifications[1].copy(id = "special-local-0")
        val holder = NotificationStateHolder(
            repository = fakeRepository(NotificationRepositoryResult.Success(listOf(remote))),
            scope = TestScope(testScheduler),
        )

        holder.addSpecialCareNotification(specialCare)
        holder.refresh()
        advanceUntilIdle()

        assertEquals(listOf("special-local-0", "remote-1"), holder.state.value.notifications.map { it.id })
        assertEquals(2, holder.state.value.unreadCount)
        assertEquals(1, holder.state.value.specialCareUnreadCount)
    }

    @Test
    fun selectSpecialCareShowsLocalNotificationsWithoutRefreshingRepository() {
        val calls = mutableListOf<NotificationFilter>()
        val notification = FakeData.notifications[0].copy(id = "special-local-1")
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(emptyList()),
                onRefresh = { calls.add(it) },
            ),
            scope = TestScope(),
        )

        holder.addSpecialCareNotification(notification)
        holder.selectFilter(NotificationFilter.SpecialCare)

        assertEquals(NotificationFilter.SpecialCare, holder.state.value.selectedFilter)
        assertEquals(listOf("special-local-1"), holder.state.value.notifications.map { it.id })
        assertEquals(1, holder.state.value.specialCareNotificationCount)
        assertEquals(1, holder.state.value.specialCareUnreadCount)
        assertEquals(emptyList(), calls)
        assertTrue(holder.state.value.endReached)
    }

    @Test
    fun markSpecialCareNotificationReadUpdatesSpecialCareUnreadCount() {
        val notification = FakeData.notifications[0].copy(id = "special-local-2")
        val holder = NotificationStateHolder(
            repository = fakeRepository(NotificationRepositoryResult.Success(emptyList())),
            scope = TestScope(),
        )

        holder.addSpecialCareNotification(notification)
        holder.selectFilter(NotificationFilter.SpecialCare)
        holder.markNotificationRead(notification.id)

        assertTrue(holder.state.value.notifications.single().isRead)
        assertEquals(0, holder.state.value.specialCareUnreadCount)
        assertEquals(0, holder.state.value.unreadCount)
    }

    @Test
    fun markAllAsReadKeepsSpecialCareItemsButClearsTheirUnreadCount() = runTest {
        val notification = FakeData.notifications[0].copy(id = "special-local-3")
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(emptyList()),
                markAllResult = NotificationRepositoryResult.AllRead,
            ),
            scope = TestScope(testScheduler),
        )

        holder.addSpecialCareNotification(notification)
        holder.selectFilter(NotificationFilter.SpecialCare)
        holder.markAllAsRead()
        advanceUntilIdle()

        assertEquals(listOf("special-local-3"), holder.state.value.notifications.map { it.id })
        assertTrue(holder.state.value.notifications.single().isRead)
        assertEquals(1, holder.state.value.specialCareNotificationCount)
        assertEquals(0, holder.state.value.specialCareUnreadCount)
        assertEquals(0, holder.state.value.unreadCount)
    }

    @Test
    fun streamingReadAllSyncMarksVisibleAndSpecialCareNotificationsReadWithoutCallingRepository() = runTest {
        var markAllCalls = 0
        val remote = FakeData.notifications[0].copy(id = "remote-stream-read")
        val specialCare = FakeData.notifications[1].copy(id = "special-stream-read")
        val holder = NotificationStateHolder(
            repository = fakeRepository(
                refreshResult = NotificationRepositoryResult.Success(listOf(remote)),
                onMarkAll = { markAllCalls += 1 },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.addSpecialCareNotification(specialCare)
        holder.syncAllReadFromStreaming()
        advanceUntilIdle()

        assertEquals(0, markAllCalls)
        assertTrue(holder.state.value.notifications.all { it.isRead })
        assertEquals(0, holder.state.value.unreadCount)
        assertEquals(0, holder.state.value.specialCareUnreadCount)
    }

    @Test
    fun refreshQuietlyUpdatesAllUnreadCountAndAllFilterList() = runTest {
        val first = FakeData.notifications[0].copy(id = "remote-a")
        val second = FakeData.notifications[1].copy(id = "remote-b")
        val holder = NotificationStateHolder(
            repository = sequenceRepository(
                NotificationRepositoryResult.Success(listOf(first)),
                NotificationRepositoryResult.Success(listOf(second, first)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.refreshQuietly()
        advanceUntilIdle()

        assertEquals(listOf("remote-b", "remote-a"), holder.state.value.notifications.map { it.id })
        assertEquals(2, holder.state.value.unreadCount)
    }

    @Test
    fun refreshQuietlyKeepsSpecialCareViewVisibleWhileUpdatingTotalUnread() = runTest {
        val remote = FakeData.notifications[0].copy(id = "remote-c")
        val specialCare = FakeData.notifications[1].copy(id = "special-local-4")
        val holder = NotificationStateHolder(
            repository = sequenceRepository(
                NotificationRepositoryResult.Success(listOf(remote)),
                NotificationRepositoryResult.Success(listOf(remote)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.addSpecialCareNotification(specialCare)
        holder.selectFilter(NotificationFilter.SpecialCare)
        holder.refreshQuietly()
        advanceUntilIdle()

        assertEquals(listOf("special-local-4"), holder.state.value.notifications.map { it.id })
        assertEquals(2, holder.state.value.unreadCount)
        assertEquals(1, holder.state.value.specialCareUnreadCount)
    }

    private fun fakeRepository(
        refreshResult: NotificationRepositoryResult,
        loadMoreResult: NotificationRepositoryResult = refreshResult,
        markAllResult: NotificationRepositoryResult = NotificationRepositoryResult.AllRead,
        onRefresh: (NotificationFilter) -> Unit = {},
        onLoadMore: () -> Unit = {},
        onLoadMoreNotifications: (List<NotificationItem>) -> Unit = {},
        onMarkAll: () -> Unit = {},
    ): NotificationRepository {
        return object : NotificationRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.NotificationApi {
                override suspend fun loadNotifications(
                    token: String,
                    limit: Int,
                    untilId: String?,
                    includeTypes: List<NotificationType>,
                ): NotificationLoadResult = NotificationLoadResult.Success(emptyList())

                override suspend fun markAllAsRead(token: String): cc.hhhl.client.api.NotificationActionResult {
                    return cc.hhhl.client.api.NotificationActionResult.Success
                }

                override suspend fun flush(token: String): cc.hhhl.client.api.NotificationActionResult {
                    return cc.hhhl.client.api.NotificationActionResult.Success
                }

                override suspend fun createNotification(
                    token: String,
                    body: String,
                    header: String?,
                    icon: String?,
                ): cc.hhhl.client.api.NotificationActionResult {
                    return cc.hhhl.client.api.NotificationActionResult.Success
                }

                override suspend fun sendTestNotification(token: String): cc.hhhl.client.api.NotificationActionResult {
                    return cc.hhhl.client.api.NotificationActionResult.Success
                }
            },
        ) {
            override suspend fun refresh(): NotificationRepositoryResult {
                onRefresh(NotificationFilter.All)
                return refreshResult
            }

            override suspend fun refresh(filter: NotificationFilter): NotificationRepositoryResult {
                onRefresh(filter)
                return refreshResult
            }

            override suspend fun loadMore(
                currentNotifications: List<cc.hhhl.client.model.NotificationItem>,
                filter: NotificationFilter,
            ): NotificationRepositoryResult {
                onLoadMore()
                onLoadMoreNotifications(currentNotifications)
                return loadMoreResult
            }

            override suspend fun markAllAsRead(): NotificationRepositoryResult {
                onMarkAll()
                return markAllResult
            }
        }
    }

    private fun sequenceRepository(
        vararg refreshResults: NotificationRepositoryResult,
    ): NotificationRepository {
        var index = 0
        return object : NotificationRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.NotificationApi {
                override suspend fun loadNotifications(
                    token: String,
                    limit: Int,
                    untilId: String?,
                    includeTypes: List<NotificationType>,
                ): NotificationLoadResult = NotificationLoadResult.Success(emptyList())

                override suspend fun markAllAsRead(token: String): cc.hhhl.client.api.NotificationActionResult {
                    return cc.hhhl.client.api.NotificationActionResult.Success
                }

                override suspend fun flush(token: String): cc.hhhl.client.api.NotificationActionResult {
                    return cc.hhhl.client.api.NotificationActionResult.Success
                }

                override suspend fun createNotification(
                    token: String,
                    body: String,
                    header: String?,
                    icon: String?,
                ): cc.hhhl.client.api.NotificationActionResult {
                    return cc.hhhl.client.api.NotificationActionResult.Success
                }

                override suspend fun sendTestNotification(token: String): cc.hhhl.client.api.NotificationActionResult {
                    return cc.hhhl.client.api.NotificationActionResult.Success
                }
            },
        ) {
            override suspend fun refresh(): NotificationRepositoryResult {
                val result = refreshResults[index.coerceAtMost(refreshResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun refresh(filter: NotificationFilter): NotificationRepositoryResult {
                val result = refreshResults[index.coerceAtMost(refreshResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun loadMore(
                currentNotifications: List<cc.hhhl.client.model.NotificationItem>,
                filter: NotificationFilter,
            ): NotificationRepositoryResult {
                return refreshResults.last()
            }

            override suspend fun markAllAsRead(): NotificationRepositoryResult {
                return NotificationRepositoryResult.AllRead
            }
        }
    }
}
