package cc.hhhl.client.state

import cc.hhhl.client.repository.AnnouncementReadRepositoryResult
import cc.hhhl.client.repository.AnnouncementRepository
import cc.hhhl.client.repository.AnnouncementRepositoryResult
import cc.hhhl.client.repository.AnnouncementsRepositoryResult
import cc.hhhl.client.repository.sampleAnnouncement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementStateHolderTest {
    @Test
    fun refreshStoresAnnouncements() = runTest {
        val announcement = sampleAnnouncement("ann-1")
        val holder = AnnouncementStateHolder(
            repository = fakeRepository(
                listResult = AnnouncementsRepositoryResult.Success(listOf(announcement)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(announcement), holder.state.value.announcements)
    }

    @Test
    fun openAnnouncementLoadsDetail() = runTest {
        val announcement = sampleAnnouncement("ann-1")
        val calls = mutableListOf<String>()
        val holder = AnnouncementStateHolder(
            repository = fakeRepository(
                listResult = AnnouncementsRepositoryResult.Success(listOf(announcement)),
                showResult = AnnouncementRepositoryResult.Success(announcement),
                onShow = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openAnnouncement("ann-1")
        assertTrue(holder.state.value.isLoadingDetail)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingDetail)
        assertEquals(announcement, holder.state.value.selectedAnnouncement)
        assertEquals(listOf("ann-1"), calls)
    }

    @Test
    fun markReadUpdatesListAndSelectedAnnouncement() = runTest {
        val announcement = sampleAnnouncement("ann-1")
        val holder = AnnouncementStateHolder(
            repository = fakeRepository(
                listResult = AnnouncementsRepositoryResult.Success(listOf(announcement)),
                showResult = AnnouncementRepositoryResult.Success(announcement),
                readResult = AnnouncementReadRepositoryResult.Success,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.openAnnouncement("ann-1")
        advanceUntilIdle()
        holder.markRead("ann-1")
        assertTrue(holder.state.value.pendingAnnouncementIds.contains("ann-1"))
        advanceUntilIdle()

        assertFalse(holder.state.value.pendingAnnouncementIds.contains("ann-1"))
        assertTrue(holder.state.value.announcements.single().isRead)
        assertEquals(true, holder.state.value.selectedAnnouncement?.isRead)
    }

    @Test
    fun unauthorizedLoadMarksRelogin() = runTest {
        val holder = AnnouncementStateHolder(
            repository = fakeRepository(
                listResult = AnnouncementsRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val announcement = sampleAnnouncement("ann-1")
        val holder = AnnouncementStateHolder(
            repository = sequenceRepository(
                AnnouncementsRepositoryResult.Unauthorized,
                AnnouncementsRepositoryResult.Success(listOf(announcement)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(announcement), holder.state.value.announcements)
    }

    @Test
    fun closeDetailClearsReloginAfterUnauthorizedMarkRead() = runTest {
        val announcement = sampleAnnouncement("ann-1")
        val holder = AnnouncementStateHolder(
            repository = fakeRepository(
                listResult = AnnouncementsRepositoryResult.Success(listOf(announcement)),
                showResult = AnnouncementRepositoryResult.Success(announcement),
                readResult = AnnouncementReadRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.openAnnouncement("ann-1")
        advanceUntilIdle()
        holder.markRead("ann-1")
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.closeDetail()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(null, holder.state.value.selectedAnnouncement)
    }

    private fun fakeRepository(
        listResult: AnnouncementsRepositoryResult,
        showResult: AnnouncementRepositoryResult = AnnouncementRepositoryResult.Success(sampleAnnouncement("ann-1")),
        readResult: AnnouncementReadRepositoryResult = AnnouncementReadRepositoryResult.Success,
        onShow: (String) -> Unit = {},
    ): AnnouncementRepository {
        return object : AnnouncementRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.AnnouncementApi {
                override suspend fun loadAnnouncements(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.AnnouncementLoadResult {
                    return cc.hhhl.client.api.AnnouncementLoadResult.Success(emptyList())
                }

                override suspend fun showAnnouncement(
                    token: String,
                    announcementId: String,
                ): cc.hhhl.client.api.AnnouncementShowResult {
                    return cc.hhhl.client.api.AnnouncementShowResult.Success(sampleAnnouncement("ann-1"))
                }

                override suspend fun markRead(
                    token: String,
                    announcementId: String,
                ): cc.hhhl.client.api.AnnouncementReadResult {
                    return cc.hhhl.client.api.AnnouncementReadResult.Success
                }
            },
        ) {
            override suspend fun refresh(): AnnouncementsRepositoryResult = listResult

            override suspend fun show(announcementId: String): AnnouncementRepositoryResult {
                onShow(announcementId)
                return showResult
            }

            override suspend fun markRead(announcementId: String): AnnouncementReadRepositoryResult = readResult
        }
    }

    private fun sequenceRepository(
        vararg listResults: AnnouncementsRepositoryResult,
    ): AnnouncementRepository {
        var index = 0
        return object : AnnouncementRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.AnnouncementApi {
                override suspend fun loadAnnouncements(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.AnnouncementLoadResult {
                    return cc.hhhl.client.api.AnnouncementLoadResult.Success(emptyList())
                }

                override suspend fun showAnnouncement(
                    token: String,
                    announcementId: String,
                ): cc.hhhl.client.api.AnnouncementShowResult {
                    return cc.hhhl.client.api.AnnouncementShowResult.Success(sampleAnnouncement("ann-1"))
                }

                override suspend fun markRead(
                    token: String,
                    announcementId: String,
                ): cc.hhhl.client.api.AnnouncementReadResult {
                    return cc.hhhl.client.api.AnnouncementReadResult.Success
                }
            },
        ) {
            override suspend fun refresh(): AnnouncementsRepositoryResult {
                val result = listResults[index.coerceAtMost(listResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun show(announcementId: String): AnnouncementRepositoryResult {
                return AnnouncementRepositoryResult.Success(sampleAnnouncement("ann-1"))
            }

            override suspend fun markRead(announcementId: String): AnnouncementReadRepositoryResult {
                return AnnouncementReadRepositoryResult.Success
            }
        }
    }
}
