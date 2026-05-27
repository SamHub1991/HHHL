package cc.hhhl.client.repository

import cc.hhhl.client.api.AnnouncementApi
import cc.hhhl.client.api.AnnouncementAdminLoadResult
import cc.hhhl.client.api.AnnouncementDeleteResult
import cc.hhhl.client.api.AnnouncementLoadResult
import cc.hhhl.client.api.AnnouncementMutationResult
import cc.hhhl.client.api.AnnouncementReadResult
import cc.hhhl.client.api.AnnouncementShowResult
import cc.hhhl.client.api.SharkeyAnnouncementApi
import cc.hhhl.client.model.Announcement

data class AnnouncementDraft(
    val title: String,
    val text: String,
    val icon: String = "info",
    val display: String = "normal",
)

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

    open suspend fun refreshAdmin(): AnnouncementsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AnnouncementsRepositoryResult.Unauthorized

        return when (val result = api.loadAdminAnnouncements(token = token, limit = ADMIN_PAGE_SIZE)) {
            is AnnouncementAdminLoadResult.Success -> AnnouncementsRepositoryResult.Success(
                announcements = result.announcements,
                endReached = true,
            )
            AnnouncementAdminLoadResult.Unauthorized -> AnnouncementsRepositoryResult.Unauthorized
            is AnnouncementAdminLoadResult.NetworkError -> {
                AnnouncementsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AnnouncementAdminLoadResult.ServerError -> AnnouncementsRepositoryResult.Error(result.message)
        }
    }

    open suspend fun createAnnouncement(draft: AnnouncementDraft): AnnouncementMutationRepositoryResult {
        val cleanDraft = draft.cleaned()
        val validationError = cleanDraft.validationError()
        if (validationError != null) return AnnouncementMutationRepositoryResult.Error(validationError)
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AnnouncementMutationRepositoryResult.Unauthorized

        return mapMutationResult(
            api.createAnnouncement(
                token = token,
                title = cleanDraft.title,
                text = cleanDraft.text,
                icon = cleanDraft.icon,
                display = cleanDraft.display,
            ),
        )
    }

    open suspend fun updateAnnouncement(
        announcementId: String,
        draft: AnnouncementDraft,
    ): AnnouncementMutationRepositoryResult {
        val cleanAnnouncementId = announcementId.trim()
        val cleanDraft = draft.cleaned()
        if (cleanAnnouncementId.isEmpty()) return AnnouncementMutationRepositoryResult.Error("无法读取公告")
        val validationError = cleanDraft.validationError()
        if (validationError != null) return AnnouncementMutationRepositoryResult.Error(validationError)
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AnnouncementMutationRepositoryResult.Unauthorized

        return mapMutationResult(
            api.updateAnnouncement(
                token = token,
                announcementId = cleanAnnouncementId,
                title = cleanDraft.title,
                text = cleanDraft.text,
                icon = cleanDraft.icon,
                display = cleanDraft.display,
            ),
        )
    }

    open suspend fun deleteAnnouncement(announcementId: String): AnnouncementDeleteRepositoryResult {
        val cleanAnnouncementId = announcementId.trim()
        if (cleanAnnouncementId.isEmpty()) return AnnouncementDeleteRepositoryResult.Error("无法删除公告")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AnnouncementDeleteRepositoryResult.Unauthorized

        return when (val result = api.deleteAnnouncement(token, cleanAnnouncementId)) {
            AnnouncementDeleteResult.Success -> AnnouncementDeleteRepositoryResult.Success
            AnnouncementDeleteResult.Unauthorized -> AnnouncementDeleteRepositoryResult.Unauthorized
            is AnnouncementDeleteResult.NetworkError -> {
                AnnouncementDeleteRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AnnouncementDeleteResult.ServerError -> AnnouncementDeleteRepositoryResult.Error(result.message)
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
                announcements = currentAnnouncements.appendDistinctBy(result.announcements) { it.id },
                endReached = result.announcements.isEmpty(),
            )
            AnnouncementLoadResult.Unauthorized -> AnnouncementsRepositoryResult.Unauthorized
            is AnnouncementLoadResult.NetworkError -> {
                AnnouncementsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AnnouncementLoadResult.ServerError -> AnnouncementsRepositoryResult.Error(result.message)
        }
    }

    private fun mapMutationResult(result: AnnouncementMutationResult): AnnouncementMutationRepositoryResult {
        return when (result) {
            is AnnouncementMutationResult.Success -> AnnouncementMutationRepositoryResult.Success(result.announcement)
            AnnouncementMutationResult.Unauthorized -> AnnouncementMutationRepositoryResult.Unauthorized
            is AnnouncementMutationResult.NetworkError -> {
                AnnouncementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AnnouncementMutationResult.ServerError -> AnnouncementMutationRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val ADMIN_PAGE_SIZE = 50
    }
}

private fun AnnouncementDraft.cleaned(): AnnouncementDraft {
    return copy(
        title = title.trim(),
        text = text.trim(),
        icon = icon.trim().ifBlank { "info" },
        display = display.trim().ifBlank { "normal" },
    )
}

private fun AnnouncementDraft.validationError(): String? {
    return when {
        title.isBlank() -> "请输入公告标题"
        text.isBlank() -> "请输入公告内容"
        else -> null
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

sealed interface AnnouncementMutationRepositoryResult {
    data class Success(val announcement: Announcement?) : AnnouncementMutationRepositoryResult

    data object Unauthorized : AnnouncementMutationRepositoryResult

    data class Error(val message: String) : AnnouncementMutationRepositoryResult
}

sealed interface AnnouncementDeleteRepositoryResult {
    data object Success : AnnouncementDeleteRepositoryResult

    data object Unauthorized : AnnouncementDeleteRepositoryResult

    data class Error(val message: String) : AnnouncementDeleteRepositoryResult
}
