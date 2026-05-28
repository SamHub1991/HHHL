package cc.hhhl.client.repository

import cc.hhhl.client.api.NotificationApi
import cc.hhhl.client.api.NotificationActionResult
import cc.hhhl.client.api.NotificationLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class NotificationRepositoryTest {
    @Test
    fun refreshLoadsNotificationsWithToken() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NotificationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = NotificationLoadResult.Success(listOf(FakeData.notifications[0])),
            ),
        )

        val result = repository.refresh()

        assertIs<NotificationRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall("token-123", null)), calls)
        assertEquals(listOf(FakeData.notifications[0]), result.notifications)
    }

    @Test
    fun refreshPassesSelectedFilterTypesToApi() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NotificationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = NotificationLoadResult.Success(emptyList()),
            ),
        )

        repository.refresh(NotificationFilter.Reactions)

        assertEquals(
            listOf(
                ApiCall(
                    token = "token-123",
                    untilId = null,
                    includeTypes = listOf(
                        NotificationType.Reaction,
                        NotificationType.ReactionGrouped,
                        NotificationType.Renote,
                        NotificationType.RenoteGrouped,
                    ),
                ),
            ),
            calls,
        )
    }

    @Test
    fun loadMoreUsesLastNotificationIdAndDeduplicates() = runTest {
        val first = FakeData.notifications[0]
        val second = FakeData.notifications[1]
        val repository = NotificationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = NotificationLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMore(listOf(first))

        assertIs<NotificationRepositoryResult.Success>(result)
        assertEquals(listOf(first, second), result.notifications)
    }

    @Test
    fun mergeNotificationPageReusesCurrentListWhenPageOnlyDuplicates() {
        val first = FakeData.notifications[0]
        val current = listOf(first)

        val result = mergeNotificationPage(current, listOf(first.copy(text = "duplicate")))

        assertTrue(result === current)
        assertEquals(listOf(first), result)
    }

    @Test
    fun emptyLoadMorePageMarksEndReached() = runTest {
        val first = FakeData.notifications[0]
        val repository = NotificationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = NotificationLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.loadMore(listOf(first))

        assertIs<NotificationRepositoryResult.Success>(result)
        assertEquals(listOf(first), result.notifications)
        assertTrue(result.endReached)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = NotificationRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = NotificationLoadResult.Success(emptyList()),
            ),
        )

        assertIs<NotificationRepositoryResult.Unauthorized>(repository.refresh())
        assertEquals(0, calls)
    }

    @Test
    fun markAllAsReadUsesToken() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NotificationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = NotificationLoadResult.Success(emptyList()),
                markAllResult = NotificationActionResult.Success,
            ),
        )

        val result = repository.markAllAsRead()

        assertEquals(NotificationRepositoryResult.AllRead, result)
        assertEquals(listOf(ApiCall("markAll", "token-123")), calls)
    }

    @Test
    fun sendTestNotificationUsesToken() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NotificationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = NotificationLoadResult.Success(emptyList()),
                markAllResult = NotificationActionResult.Success,
            ),
        )

        val result = repository.sendTestNotification()

        assertEquals(NotificationRepositoryResult.ActionSuccess, result)
        assertEquals(listOf(ApiCall("test", "token-123")), calls)
    }

    @Test
    fun createNotificationUsesTokenAndBody() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NotificationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = NotificationLoadResult.Success(emptyList()),
                markAllResult = NotificationActionResult.Success,
            ),
        )

        val result = repository.createNotification(body = "回来看看新消息", header = "HHHL 提醒")

        assertEquals(NotificationRepositoryResult.ActionSuccess, result)
        assertEquals(listOf(ApiCall("create:回来看看新消息", "token-123")), calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: NotificationLoadResult,
        markAllResult: NotificationActionResult = NotificationActionResult.Success,
        onCall: () -> Unit = {},
    ): NotificationApi {
        return object : NotificationApi {
            override suspend fun loadNotifications(
                token: String,
                limit: Int,
                untilId: String?,
                includeTypes: List<NotificationType>,
            ): NotificationLoadResult {
                onCall()
                calls.add(ApiCall(token, untilId, includeTypes))
                return result
            }

            override suspend fun markAllAsRead(token: String): NotificationActionResult {
                calls.add(ApiCall("markAll", token))
                return markAllResult
            }

            override suspend fun flush(token: String): NotificationActionResult {
                calls.add(ApiCall("flush", token))
                return markAllResult
            }

            override suspend fun createNotification(
                token: String,
                body: String,
                header: String?,
                icon: String?,
            ): NotificationActionResult {
                calls.add(ApiCall("create:$body", token))
                return markAllResult
            }

            override suspend fun sendTestNotification(token: String): NotificationActionResult {
                calls.add(ApiCall("test", token))
                return markAllResult
            }
        }
    }

    private data class ApiCall(
        val token: String,
        val untilId: String?,
        val includeTypes: List<NotificationType> = emptyList(),
    )
}
