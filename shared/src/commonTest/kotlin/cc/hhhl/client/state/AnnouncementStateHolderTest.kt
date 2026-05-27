package cc.hhhl.client.state

import cc.hhhl.client.repository.AnnouncementDeleteRepositoryResult
import cc.hhhl.client.repository.AnnouncementDraft
import cc.hhhl.client.repository.AnnouncementMutationRepositoryResult
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

    @Test
    fun managementCreateUpdateAndDeleteMutateAnnouncementList() = runTest {
        val first = sampleAnnouncement("ann-1")
        val created = sampleAnnouncement("ann-2").copy(title = "新公告")
        val updated = first.copy(title = "更新公告")
        val holder = AnnouncementStateHolder(
            repository = fakeRepository(
                listResult = AnnouncementsRepositoryResult.Success(listOf(first)),
                adminListResult = AnnouncementsRepositoryResult.Success(listOf(first)),
                createResult = AnnouncementMutationRepositoryResult.Success(created),
                updateResult = AnnouncementMutationRepositoryResult.Success(updated),
                deleteResult = AnnouncementDeleteRepositoryResult.Success,
            ),
            scope = TestScope(testScheduler),
        )

        holder.enterManagement()
        advanceUntilIdle()
        assertTrue(holder.state.value.isManaging)
        assertEquals(listOf(first), holder.state.value.announcements)

        holder.createAnnouncement(AnnouncementDraft("新公告", "内容"))
        advanceUntilIdle()
        assertEquals("新公告", holder.state.value.announcements.first().title)

        holder.updateAnnouncement("ann-1", AnnouncementDraft("更新公告", "内容"))
        advanceUntilIdle()
        assertEquals("更新公告", holder.state.value.announcements.first { it.id == "ann-1" }.title)

        holder.deleteAnnouncement("ann-1")
        advanceUntilIdle()
        assertEquals(listOf("ann-2"), holder.state.value.announcements.map { it.id })
    }

    private fun fakeRepository(
        listResult: AnnouncementsRepositoryResult,
        adminListResult: AnnouncementsRepositoryResult = listResult,
        showResult: AnnouncementRepositoryResult = AnnouncementRepositoryResult.Success(sampleAnnouncement("ann-1")),
        readResult: AnnouncementReadRepositoryResult = AnnouncementReadRepositoryResult.Success,
        createResult: AnnouncementMutationRepositoryResult = AnnouncementMutationRepositoryResult.Success(
            sampleAnnouncement("ann-2"),
        ),
        updateResult: AnnouncementMutationRepositoryResult = AnnouncementMutationRepositoryResult.Success(
            sampleAnnouncement("ann-1"),
        ),
        deleteResult: AnnouncementDeleteRepositoryResult = AnnouncementDeleteRepositoryResult.Success,
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

                override suspend fun loadAdminAnnouncements(
                    token: String,
                    limit: Int,
                ): cc.hhhl.client.api.AnnouncementAdminLoadResult {
                    return cc.hhhl.client.api.AnnouncementAdminLoadResult.Success(emptyList())
                }

                override suspend fun createAnnouncement(
                    token: String,
                    title: String,
                    text: String,
                    icon: String,
                    display: String,
                ): cc.hhhl.client.api.AnnouncementMutationResult {
                    return cc.hhhl.client.api.AnnouncementMutationResult.Success(sampleAnnouncement("ann-2"))
                }

                override suspend fun updateAnnouncement(
                    token: String,
                    announcementId: String,
                    title: String,
                    text: String,
                    icon: String,
                    display: String,
                ): cc.hhhl.client.api.AnnouncementMutationResult {
                    return cc.hhhl.client.api.AnnouncementMutationResult.Success(sampleAnnouncement("ann-1"))
                }

                override suspend fun deleteAnnouncement(
                    token: String,
                    announcementId: String,
                ): cc.hhhl.client.api.AnnouncementDeleteResult {
                    return cc.hhhl.client.api.AnnouncementDeleteResult.Success
                }
            },
        ) {
            override suspend fun refresh(): AnnouncementsRepositoryResult = listResult

            override suspend fun refreshAdmin(): AnnouncementsRepositoryResult = adminListResult

            override suspend fun show(announcementId: String): AnnouncementRepositoryResult {
                onShow(announcementId)
                return showResult
            }

            override suspend fun markRead(announcementId: String): AnnouncementReadRepositoryResult = readResult

            override suspend fun createAnnouncement(draft: AnnouncementDraft): AnnouncementMutationRepositoryResult {
                return createResult
            }

            override suspend fun updateAnnouncement(
                announcementId: String,
                draft: AnnouncementDraft,
            ): AnnouncementMutationRepositoryResult {
                return updateResult
            }

            override suspend fun deleteAnnouncement(announcementId: String): AnnouncementDeleteRepositoryResult {
                return deleteResult
            }
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

                override suspend fun loadAdminAnnouncements(
                    token: String,
                    limit: Int,
                ): cc.hhhl.client.api.AnnouncementAdminLoadResult {
                    return cc.hhhl.client.api.AnnouncementAdminLoadResult.Success(emptyList())
                }

                override suspend fun createAnnouncement(
                    token: String,
                    title: String,
                    text: String,
                    icon: String,
                    display: String,
                ): cc.hhhl.client.api.AnnouncementMutationResult {
                    return cc.hhhl.client.api.AnnouncementMutationResult.Success(sampleAnnouncement("ann-2"))
                }

                override suspend fun updateAnnouncement(
                    token: String,
                    announcementId: String,
                    title: String,
                    text: String,
                    icon: String,
                    display: String,
                ): cc.hhhl.client.api.AnnouncementMutationResult {
                    return cc.hhhl.client.api.AnnouncementMutationResult.Success(sampleAnnouncement("ann-1"))
                }

                override suspend fun deleteAnnouncement(
                    token: String,
                    announcementId: String,
                ): cc.hhhl.client.api.AnnouncementDeleteResult {
                    return cc.hhhl.client.api.AnnouncementDeleteResult.Success
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
