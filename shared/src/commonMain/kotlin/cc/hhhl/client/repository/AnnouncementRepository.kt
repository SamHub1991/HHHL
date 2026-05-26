package cc.hhhl.client.repository

import cc.hhhl.client.api.AnnouncementApi
import cc.hhhl.client.api.AnnouncementLoadResult
import cc.hhhl.client.api.AnnouncementReadResult
import cc.hhhl.client.api.AnnouncementShowResult
import cc.hhhl.client.api.SharkeyAnnouncementApi
import cc.hhhl.client.model.Announcement

open class AnnouncementRepository(
    private val tokenProvider: () -> String?,
    private val api: AnnouncementApi = SharkeyAnnouncementApi(),
) {
    open suspend fun refresh(): AnnouncementsRepositoryResult {
        return loadAnnouncements(
            currentAnnouncements = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMore(
        currentAnnouncements: List<Announcement>,
    ): AnnouncementsRepositoryResult {
        return loadAnnouncements(
            currentAnnouncements = currentAnnouncements,
            untilId = currentAnnouncements.lastOrNull()?.id,
        )
    }

    open suspend fun show(announcementId: String): AnnouncementRepositoryResult {
        val cleanAnnouncementId = announcementId.trim()
        if (cleanAnnouncementId.isEmpty()) {
            return AnnouncementRepositoryResult.Error("无法读取公告")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AnnouncementRepositoryResult.Unauthorized

        return when (val result = api.showAnnouncement(token, cleanAnnouncementId)) {
            is AnnouncementShowResult.Success -> AnnouncementRepositoryResult.Success(result.announcement)
            AnnouncementShowResult.Unauthorized -> AnnouncementRepositoryResult.Unauthorized
            is AnnouncementShowResult.NetworkError -> {
                AnnouncementRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AnnouncementShowResult.ServerError -> AnnouncementRepositoryResult.Error(result.message)
        }
    }

    open suspend fun markRead(announcementId: String): AnnouncementReadRepositoryResult {
        val cleanAnnouncementId = announcementId.trim()
        if (cleanAnnouncementId.isEmpty()) {
            return AnnouncementReadRepositoryResult.Error("无法读取公告")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AnnouncementReadRepositoryResult.Unauthorized

        return when (val result = api.markRead(token, cleanAnnouncementId)) {
            AnnouncementReadResult.Success -> AnnouncementReadRepositoryResult.Success
            AnnouncementReadResult.Unauthorized -> AnnouncementReadRepositoryResult.Unauthorized
            is AnnouncementReadResult.NetworkError -> {
                AnnouncementReadRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AnnouncementReadResult.ServerError -> AnnouncementReadRepositoryResult.Error(result.message)
        }
    }

    private suspend fun loadAnnouncements(
        currentAnnouncements: List<Announcement>,
        untilId: String?,
    ): AnnouncementsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AnnouncementsRepositoryResult.Unauthorized

        return when (
            val result = api.loadAnnouncements(
                token = token,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
            )
        ) {
            is AnnouncementLoadResult.Success -> AnnouncementsRepositoryResult.Success(
                announcements = (currentAnnouncements + result.announcements).distinctBy { it.id },
                endReached = result.announcements.isEmpty(),
            )
            AnnouncementLoadResult.Unauthorized -> AnnouncementsRepositoryResult.Unauthorized
            is AnnouncementLoadResult.NetworkError -> {
                AnnouncementsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AnnouncementLoadResult.ServerError -> AnnouncementsRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface AnnouncementsRepositoryResult {
    data class Success(
        val announcements: List<Announcement>,
        val endReached: Boolean = false,
    ) : AnnouncementsRepositoryResult

    data object Unauthorized : AnnouncementsRepositoryResult

    data class Error(val message: String) : AnnouncementsRepositoryResult
}

sealed interface AnnouncementRepositoryResult {
    data class Success(val announcement: Announcement) : AnnouncementRepositoryResult

    data object Unauthorized : AnnouncementRepositoryResult

    data class Error(val message: String) : AnnouncementRepositoryResult
}

sealed interface AnnouncementReadRepositoryResult {
    data object Success : AnnouncementReadRepositoryResult

    data object Unauthorized : AnnouncementReadRepositoryResult

    data class Error(val message: String) : AnnouncementReadRepositoryResult
}
