package cc.hhhl.client.repository

import cc.hhhl.client.api.AnnouncementApi
import cc.hhhl.client.api.AnnouncementLoadResult
import cc.hhhl.client.api.AnnouncementReadResult
import cc.hhhl.client.api.AnnouncementShowResult
import cc.hhhl.client.model.Announcement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AnnouncementRepositoryTest {
    @Test
    fun refreshUsesTokenAndLoadsAnnouncements() = runTest {
        val announcements = listOf(sampleAnnouncement("ann-1"))
        val calls = mutableListOf<ListCall>()
        val repository = AnnouncementRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                listCalls = calls,
                listResult = AnnouncementLoadResult.Success(announcements),
            ),
        )

        val result = repository.refresh()

        assertIs<AnnouncementsRepositoryResult.Success>(result)
        assertEquals(listOf(ListCall("token-123", null)), calls)
        assertEquals(announcements, result.announcements)
    }

    @Test
    fun loadMoreUsesLastAnnouncementIdAndDeduplicates() = runTest {
        val first = sampleAnnouncement("ann-1")
        val second = sampleAnnouncement("ann-2")
        val calls = mutableListOf<ListCall>()
        val repository = AnnouncementRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                listCalls = calls,
                listResult = AnnouncementLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMore(currentAnnouncements = listOf(first))

        assertIs<AnnouncementsRepositoryResult.Success>(result)
        assertEquals(listOf(ListCall("token-123", first.id)), calls)
        assertEquals(listOf(first, second), result.announcements)
    }

    @Test
    fun showUsesTokenAndAnnouncementId() = runTest {
        val calls = mutableListOf<ShowCall>()
        val announcement = sampleAnnouncement("ann-1")
        val repository = AnnouncementRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                showCalls = calls,
                showResult = AnnouncementShowResult.Success(announcement),
            ),
        )

        val result = repository.show("ann-1")

        assertIs<AnnouncementRepositoryResult.Success>(result)
        assertEquals(listOf(ShowCall("token-123", "ann-1")), calls)
        assertEquals(announcement, result.announcement)
    }

    @Test
    fun markReadUsesTokenAndAnnouncementId() = runTest {
        val calls = mutableListOf<ReadCall>()
        val repository = AnnouncementRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(readCalls = calls),
        )

        val result = repository.markRead("ann-1")

        assertIs<AnnouncementReadRepositoryResult.Success>(result)
        assertEquals(listOf(ReadCall("token-123", "ann-1")), calls)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = AnnouncementRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<AnnouncementsRepositoryResult.Unauthorized>(repository.refresh())
        assertIs<AnnouncementRepositoryResult.Unauthorized>(repository.show("ann-1"))
        assertIs<AnnouncementReadRepositoryResult.Unauthorized>(repository.markRead("ann-1"))
        assertEquals(0, calls)
    }

    private fun fakeApi(
        listCalls: MutableList<ListCall> = mutableListOf(),
        showCalls: MutableList<ShowCall> = mutableListOf(),
        readCalls: MutableList<ReadCall> = mutableListOf(),
        listResult: AnnouncementLoadResult = AnnouncementLoadResult.Success(emptyList()),
        showResult: AnnouncementShowResult = AnnouncementShowResult.Success(sampleAnnouncement("ann-1")),
        readResult: AnnouncementReadResult = AnnouncementReadResult.Success,
        onCall: () -> Unit = {},
    ): AnnouncementApi {
        return object : AnnouncementApi {
            override suspend fun loadAnnouncements(
                token: String,
                limit: Int,
                untilId: String?,
            ): AnnouncementLoadResult {
                onCall()
                listCalls.add(ListCall(token, untilId))
                return listResult
            }

            override suspend fun showAnnouncement(
                token: String,
                announcementId: String,
            ): AnnouncementShowResult {
                onCall()
                showCalls.add(ShowCall(token, announcementId))
                return showResult
            }

            override suspend fun markRead(
                token: String,
                announcementId: String,
            ): AnnouncementReadResult {
                onCall()
                readCalls.add(ReadCall(token, announcementId))
                return readResult
            }
        }
    }

    private data class ListCall(
        val token: String,
        val untilId: String?,
    )

    private data class ShowCall(
        val token: String,
        val announcementId: String,
    )

    private data class ReadCall(
        val token: String,
        val announcementId: String,
    )
}

fun sampleAnnouncement(id: String): Announcement {
    return Announcement(
        id = id,
        title = "维护通知",
        text = "今晚维护",
        imageUrl = "https://dc.hhhl.cc/files/maintenance.webp",
        icon = "warning",
        display = "banner",
        needConfirmationToRead = true,
        silence = false,
        confetti = false,
        forYou = true,
        isRead = false,
        createdAtLabel = "2026-05-25 06:00",
        updatedAtLabel = "",
    )
}
