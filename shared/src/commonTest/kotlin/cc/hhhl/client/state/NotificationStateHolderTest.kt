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
    fun markAllAsReadClearsNotificationsAndStoresMessage() = runTest {
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
        assertEquals(emptyList(), holder.state.value.notifications)
        assertEquals(0, holder.state.value.unreadCount)
        assertEquals("通知已全部标记为已读", holder.state.value.message)
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

    private fun fakeRepository(
        refreshResult: NotificationRepositoryResult,
        loadMoreResult: NotificationRepositoryResult = refreshResult,
        markAllResult: NotificationRepositoryResult = NotificationRepositoryResult.AllRead,
        onRefresh: (NotificationFilter) -> Unit = {},
        onLoadMore: () -> Unit = {},
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
                return loadMoreResult
            }

            override suspend fun markAllAsRead(): NotificationRepositoryResult {
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
