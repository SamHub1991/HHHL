package cc.hhhl.client.state

import cc.hhhl.client.model.Announcement
import cc.hhhl.client.repository.AnnouncementDeleteRepositoryResult
import cc.hhhl.client.repository.AnnouncementDraft
import cc.hhhl.client.repository.AnnouncementMutationRepositoryResult
import cc.hhhl.client.repository.AnnouncementReadRepositoryResult
import cc.hhhl.client.repository.AnnouncementRepository
import cc.hhhl.client.repository.AnnouncementRepositoryResult
import cc.hhhl.client.repository.AnnouncementsRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnnouncementUiState(
    val announcements: List<Announcement> = emptyList(),
    val selectedAnnouncement: Announcement? = null,
    val pendingAnnouncementIds: Set<String> = emptySet(),
    val isManaging: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isLoadingAdmin: Boolean = false,
    val isMutatingAnnouncement: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val detailErrorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val adminErrorMessage: String? = null,
    val adminActionMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class AnnouncementStateHolder(
    private val repository: AnnouncementRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AnnouncementUiState())
    val state: StateFlow<AnnouncementUiState> = mutableState
    private var listRequestId = 0
    private var detailRequestId = 0
    private var adminRequestId = 0

    fun refresh() {
        if (state.value.isLoading) return
        val requestId = ++listRequestId

        mutableState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                endReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyListResult(repository.refresh(), loadingMore = false, requestId = requestId)
        }
    }

    fun loadMore() {
        val current = state.value
        if (
            current.isLoading ||
            current.isLoadingMore ||
            current.announcements.isEmpty() ||
            current.endReached
        ) {
            return
        }
        val requestId = ++listRequestId

        mutableState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyListResult(repository.loadMore(current.announcements), loadingMore = true, requestId = requestId)
        }
    }

    fun openAnnouncement(announcementId: String) {
        if (announcementId.isBlank()) return
        val current = state.value
        if (current.isLoadingDetail && current.selectedAnnouncement?.id == announcementId) return
        val requestId = ++detailRequestId

        mutableState.update {
            it.copy(
                selectedAnnouncement = it.selectedAnnouncement?.takeIf { selected -> selected.id == announcementId },
                isLoadingDetail = true,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.show(announcementId)) {
                is AnnouncementRepositoryResult.Success -> mutableState.update {
                    if (requestId != detailRequestId || result.announcement.id != announcementId) return@update it
                    it.copy(
                        selectedAnnouncement = result.announcement,
                        isLoadingDetail = false,
                        detailErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AnnouncementRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != detailRequestId) return@update it
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AnnouncementRepositoryResult.Error -> mutableState.update {
                    if (requestId != detailRequestId) return@update it
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun closeDetail() {
        detailRequestId += 1
        mutableState.update {
            it.copy(
                selectedAnnouncement = null,
                isLoadingDetail = false,
                detailErrorMessage = null,
                actionErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun enterManagement() {
        listRequestId += 1
        detailRequestId += 1
        mutableState.update {
            it.copy(
                isManaging = true,
                selectedAnnouncement = null,
                detailErrorMessage = null,
                actionErrorMessage = null,
                adminErrorMessage = null,
                adminActionMessage = null,
                requiresRelogin = false,
            )
        }
        if (state.value.announcements.isEmpty()) {
            refreshAdmin()
        }
    }

    fun exitManagement() {
        adminRequestId += 1
        mutableState.update {
            it.copy(
                isManaging = false,
                isLoadingAdmin = false,
                isMutatingAnnouncement = false,
                adminErrorMessage = null,
                adminActionMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun refreshAdmin() {
        if (state.value.isLoadingAdmin) return
        val requestId = ++adminRequestId

        mutableState.update {
            it.copy(
                isManaging = true,
                isLoadingAdmin = true,
                isLoadingMore = false,
                endReached = true,
                adminErrorMessage = null,
                adminActionMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.refreshAdmin()) {
                is AnnouncementsRepositoryResult.Success -> mutableState.update {
                    if (requestId != adminRequestId || !it.isManaging) return@update it
                    it.copy(
                        announcements = result.announcements,
                        isLoadingAdmin = false,
                        endReached = true,
                        adminErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AnnouncementsRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != adminRequestId || !it.isManaging) return@update it
                    it.copy(
                        isLoadingAdmin = false,
                        adminErrorMessage = "当前账号没有公告管理权限",
                        requiresRelogin = false,
                    )
                }
                is AnnouncementsRepositoryResult.Error -> mutableState.update {
                    if (requestId != adminRequestId || !it.isManaging) return@update it
                    it.copy(
                        isLoadingAdmin = false,
                        adminErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun markRead(announcementId: String) {
        if (announcementId.isBlank() || state.value.pendingAnnouncementIds.contains(announcementId)) return

        mutableState.update {
            it.copy(
                pendingAnnouncementIds = it.pendingAnnouncementIds + announcementId,
                actionErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.markRead(announcementId)) {
                AnnouncementReadRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        announcements = current.announcements.map {
                            if (it.id == announcementId) it.copy(isRead = true) else it
                        },
                        selectedAnnouncement = current.selectedAnnouncement?.let {
                            if (it.id == announcementId) it.copy(isRead = true) else it
                        },
                        pendingAnnouncementIds = current.pendingAnnouncementIds - announcementId,
                        actionErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AnnouncementReadRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        pendingAnnouncementIds = it.pendingAnnouncementIds - announcementId,
                        actionErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AnnouncementReadRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        pendingAnnouncementIds = it.pendingAnnouncementIds - announcementId,
                        actionErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun createAnnouncement(draft: AnnouncementDraft) {
        if (state.value.isMutatingAnnouncement) return

        mutableState.update {
            it.copy(
                isMutatingAnnouncement = true,
                adminErrorMessage = null,
                adminActionMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            var shouldRefresh = false
            when (val result = repository.createAnnouncement(draft)) {
                is AnnouncementMutationRepositoryResult.Success -> mutableState.update { current ->
                    shouldRefresh = result.announcement == null
                    current.copy(
                        announcements = listOfNotNull(result.announcement) + current.announcements,
                        isMutatingAnnouncement = false,
                        adminActionMessage = "公告已创建",
                        adminErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AnnouncementMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingAnnouncement = false,
                        adminErrorMessage = "当前账号没有公告管理权限",
                        requiresRelogin = false,
                    )
                }
                is AnnouncementMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingAnnouncement = false,
                        adminErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
            if (shouldRefresh && state.value.isManaging && !state.value.isMutatingAnnouncement) {
                refreshAdmin()
            }
        }
    }

    fun updateAnnouncement(announcementId: String, draft: AnnouncementDraft) {
        if (announcementId.isBlank() || state.value.isMutatingAnnouncement) return

        mutableState.update {
            it.copy(
                isMutatingAnnouncement = true,
                adminErrorMessage = null,
                adminActionMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.updateAnnouncement(announcementId, draft)) {
                is AnnouncementMutationRepositoryResult.Success -> mutableState.update { current ->
                    val updatedAnnouncement = result.announcement
                    current.copy(
                        announcements = if (updatedAnnouncement != null) {
                            current.announcements.map {
                                if (it.id == announcementId) updatedAnnouncement else it
                            }
                        } else {
                            current.announcements.map {
                                if (it.id == announcementId) {
                                    it.copy(
                                        title = draft.title.trim(),
                                        text = draft.text.trim(),
                                        icon = draft.icon.trim().ifBlank { "info" },
                                        display = draft.display.trim().ifBlank { "normal" },
                                    )
                                } else {
                                    it
                                }
                            }
                        },
                        selectedAnnouncement = current.selectedAnnouncement?.let {
                            if (it.id == announcementId) updatedAnnouncement ?: it else it
                        },
                        isMutatingAnnouncement = false,
                        adminActionMessage = "公告已更新",
                        adminErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AnnouncementMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingAnnouncement = false,
                        adminErrorMessage = "当前账号没有公告管理权限",
                        requiresRelogin = false,
                    )
                }
                is AnnouncementMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingAnnouncement = false,
                        adminErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        if (announcementId.isBlank() || state.value.pendingAnnouncementIds.contains(announcementId)) return

        mutableState.update {
            it.copy(
                pendingAnnouncementIds = it.pendingAnnouncementIds + announcementId,
                adminErrorMessage = null,
                adminActionMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.deleteAnnouncement(announcementId)) {
                AnnouncementDeleteRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        announcements = current.announcements.filterNot { it.id == announcementId },
                        selectedAnnouncement = current.selectedAnnouncement?.takeIf { it.id != announcementId },
                        pendingAnnouncementIds = current.pendingAnnouncementIds - announcementId,
                        adminActionMessage = "公告已删除",
                        adminErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AnnouncementDeleteRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        pendingAnnouncementIds = it.pendingAnnouncementIds - announcementId,
                        adminErrorMessage = "当前账号没有公告管理权限",
                        requiresRelogin = false,
                    )
                }
                is AnnouncementDeleteRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        pendingAnnouncementIds = it.pendingAnnouncementIds - announcementId,
                        adminErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun applyListResult(
        result: AnnouncementsRepositoryResult,
        loadingMore: Boolean,
        requestId: Int,
    ) {
        when (result) {
            is AnnouncementsRepositoryResult.Success -> mutableState.update {
                if (requestId != listRequestId || it.isManaging) return@update it
                it.copy(
                    announcements = result.announcements,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            AnnouncementsRepositoryResult.Unauthorized -> mutableState.update {
                if (requestId != listRequestId || it.isManaging) return@update it
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is AnnouncementsRepositoryResult.Error -> mutableState.update {
                if (requestId != listRequestId || it.isManaging) return@update it
                it.copy(
                    isLoading = if (loadingMore) it.isLoading else false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
