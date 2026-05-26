package cc.hhhl.client.state

import cc.hhhl.client.model.Announcement
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
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val detailErrorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class AnnouncementStateHolder(
    private val repository: AnnouncementRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AnnouncementUiState())
    val state: StateFlow<AnnouncementUiState> = mutableState

    fun refresh() {
        if (state.value.isLoading) return

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
            applyListResult(repository.refresh(), loadingMore = false)
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

        mutableState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyListResult(repository.loadMore(current.announcements), loadingMore = true)
        }
    }

    fun openAnnouncement(announcementId: String) {
        if (announcementId.isBlank() || state.value.isLoadingDetail) return

        mutableState.update {
            it.copy(
                isLoadingDetail = true,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.show(announcementId)) {
                is AnnouncementRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        selectedAnnouncement = result.announcement,
                        isLoadingDetail = false,
                        detailErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AnnouncementRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AnnouncementRepositoryResult.Error -> mutableState.update {
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

    private fun applyListResult(
        result: AnnouncementsRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is AnnouncementsRepositoryResult.Success -> mutableState.update {
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
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is AnnouncementsRepositoryResult.Error -> mutableState.update {
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
