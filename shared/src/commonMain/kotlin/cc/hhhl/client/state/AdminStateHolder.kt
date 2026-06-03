package cc.hhhl.client.state

import cc.hhhl.client.model.AdminAbuseReport
import cc.hhhl.client.model.AdminAnnouncementSummary
import cc.hhhl.client.model.AdminInstanceSettings
import cc.hhhl.client.model.AdminRoleSummary
import cc.hhhl.client.model.AdminUserSummary
import cc.hhhl.client.repository.AdminRepository
import cc.hhhl.client.repository.AdminRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminDashboardTab(val label: String) {
    Reports("举报"),
    Users("用户"),
    Roles("角色"),
    Announcements("公告"),
    Instance("实例"),
}

data class AdminDashboardUiState(
    val selectedTab: AdminDashboardTab = AdminDashboardTab.Reports,
    val users: List<AdminUserSummary> = emptyList(),
    val reports: List<AdminAbuseReport> = emptyList(),
    val roles: List<AdminRoleSummary> = emptyList(),
    val announcements: List<AdminAnnouncementSummary> = emptyList(),
    val instance: AdminInstanceSettings? = null,
    val userQuery: String = "",
    val announcementTitleDraft: String = "",
    val announcementTextDraft: String = "",
    val editingAnnouncementId: String? = null,
    val selectedUserRoles: List<AdminRoleSummary> = emptyList(),
    val selectedUserId: String? = null,
    val pendingIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSearchingUsers: Boolean = false,
    val isPermissionDenied: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class AdminStateHolder(
    private val repository: AdminRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AdminDashboardUiState())
    val state: StateFlow<AdminDashboardUiState> = mutableState
    private var overviewRequestId = 0
    private var userSearchRequestId = 0
    private var userRolesRequestId = 0
    private var announcementDraftRevision = 0
    private var announcementMutationRequestId = 0

    fun refresh() {
        if (state.value.isLoading) return
        val requestId = ++overviewRequestId
        val userQuery = state.value.userQuery

        mutableState.update {
            it.copy(
                isLoading = true,
                isPermissionDenied = false,
                errorMessage = null,
                actionMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.overview(userQuery)) {
                is AdminRepositoryResult.Success -> mutableState.update {
                    if (requestId != overviewRequestId) return@update it
                    it.copy(
                        users = result.value.users,
                        reports = result.value.reports,
                        roles = result.value.roles,
                        announcements = result.value.announcements,
                        instance = result.value.instance,
                        isLoading = false,
                        isPermissionDenied = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AdminRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != overviewRequestId) return@update it
                    it.copy(
                        isLoading = false,
                        isPermissionDenied = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AdminRepositoryResult.Error -> mutableState.update {
                    if (requestId != overviewRequestId) return@update it
                    it.copy(
                        isLoading = false,
                        isPermissionDenied = result.isPermissionDenied,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun selectTab(tab: AdminDashboardTab) {
        mutableState.update { it.copy(selectedTab = tab, actionMessage = null, requiresRelogin = false) }
    }

    fun updateUserQuery(query: String) {
        userSearchRequestId += 1
        mutableState.update {
            it.copy(userQuery = query, isSearchingUsers = false, requiresRelogin = false)
        }
    }

    fun searchUsers() {
        if (state.value.isSearchingUsers) return
        val requestId = ++userSearchRequestId
        val query = state.value.userQuery
        mutableState.update { it.copy(isSearchingUsers = true, errorMessage = null, requiresRelogin = false) }
        scope.launch {
            when (val result = repository.searchUsers(query)) {
                is AdminRepositoryResult.Success -> mutableState.update {
                    if (requestId != userSearchRequestId || it.userQuery != query) return@update it
                    it.copy(
                        users = result.value,
                        isSearchingUsers = false,
                        isPermissionDenied = false,
                        requiresRelogin = false,
                    )
                }
                AdminRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != userSearchRequestId || it.userQuery != query) return@update it
                    it.copy(
                        isSearchingUsers = false,
                        isPermissionDenied = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AdminRepositoryResult.Error -> mutableState.update {
                    if (requestId != userSearchRequestId || it.userQuery != query) return@update it
                    it.copy(
                        isSearchingUsers = false,
                        isPermissionDenied = result.isPermissionDenied,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun loadUserRoles(userId: String) {
        if (userId.isBlank()) return
        val requestId = ++userRolesRequestId
        mutableState.update {
            it.copy(
                selectedUserId = userId,
                selectedUserRoles = emptyList(),
                actionMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            when (val result = repository.loadUserRoles(userId)) {
                is AdminRepositoryResult.Success -> mutableState.update {
                    if (requestId != userRolesRequestId || it.selectedUserId != userId) return@update it
                    it.copy(
                        selectedUserRoles = result.value,
                        selectedUserId = userId,
                        isPermissionDenied = false,
                        requiresRelogin = false,
                    )
                }
                AdminRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != userRolesRequestId || it.selectedUserId != userId) return@update it
                    it.copy(
                        isPermissionDenied = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AdminRepositoryResult.Error -> mutableState.update {
                    if (requestId != userRolesRequestId || it.selectedUserId != userId) return@update it
                    it.copy(
                        isPermissionDenied = result.isPermissionDenied,
                        errorMessage = if (result.isPermissionDenied) result.message else it.errorMessage,
                        actionMessage = if (result.isPermissionDenied) null else result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun resolveReport(reportId: String, forward: Boolean = false) {
        val cleanReportId = reportId.trim()
        if (cleanReportId.isBlank() || state.value.pendingIds.contains(cleanReportId)) return
        mutableState.update {
            it.copy(
                pendingIds = it.pendingIds + cleanReportId,
                actionMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            when (val result = repository.resolveReport(cleanReportId, forward = forward)) {
                is AdminRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        reports = it.reports.filterNot { report -> report.id == cleanReportId },
                        pendingIds = it.pendingIds - cleanReportId,
                        actionMessage = if (forward) "已转发并处理举报" else "已处理举报",
                        isPermissionDenied = false,
                        requiresRelogin = false,
                    )
                }
                AdminRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        pendingIds = it.pendingIds - cleanReportId,
                        isPermissionDenied = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AdminRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        pendingIds = it.pendingIds - cleanReportId,
                        isPermissionDenied = result.isPermissionDenied,
                        errorMessage = if (result.isPermissionDenied) result.message else it.errorMessage,
                        actionMessage = if (result.isPermissionDenied) null else result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun updateAnnouncementDraft(title: String, text: String) {
        announcementDraftRevision += 1
        mutableState.update {
            it.copy(
                announcementTitleDraft = title,
                announcementTextDraft = text,
                actionMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun editAnnouncement(announcement: AdminAnnouncementSummary) {
        announcementDraftRevision += 1
        mutableState.update {
            it.copy(
                editingAnnouncementId = announcement.id,
                announcementTitleDraft = announcement.title,
                announcementTextDraft = announcement.text,
                actionMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun cancelAnnouncementEdit() {
        announcementDraftRevision += 1
        mutableState.update {
            it.copy(
                editingAnnouncementId = null,
                announcementTitleDraft = "",
                announcementTextDraft = "",
                actionMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun createAnnouncement() {
        val current = state.value
        if (current.announcementTitleDraft.isBlank() || current.announcementTextDraft.isBlank()) {
            mutableState.update {
                it.copy(
                    actionMessage = "公告标题和内容不能为空",
                    requiresRelogin = false,
                )
            }
            return
        }

        val requestId = ++announcementMutationRequestId
        val draftRevision = announcementDraftRevision
        val editingId = current.editingAnnouncementId
        val title = current.announcementTitleDraft
        val text = current.announcementTextDraft
        scope.launch {
            if (editingId != null) {
                when (
                    val result = repository.updateAnnouncement(
                        announcementId = editingId,
                        title = title,
                        text = text,
                    )
                ) {
                    is AdminRepositoryResult.Success -> mutableState.update {
                        val isCurrentDraft = isCurrentAnnouncementDraftMutation(
                            requestId = requestId,
                            draftRevision = draftRevision,
                            editingId = editingId,
                            title = title,
                            text = text,
                        )
                        it.copy(
                            announcements = it.announcements.map { announcement ->
                                if (announcement.id == editingId) {
                                    announcement.copy(
                                        title = title.trim(),
                                        text = text.trim(),
                                    )
                                } else {
                                    announcement
                                }
                            },
                            announcementTitleDraft = if (isCurrentDraft) "" else it.announcementTitleDraft,
                            announcementTextDraft = if (isCurrentDraft) "" else it.announcementTextDraft,
                            editingAnnouncementId = if (isCurrentDraft) null else it.editingAnnouncementId,
                            actionMessage = if (isCurrentDraft) "公告已更新" else it.actionMessage,
                            isPermissionDenied = false,
                            requiresRelogin = false,
                        )
                    }
                    AdminRepositoryResult.Unauthorized -> mutableState.update {
                        if (!isCurrentAnnouncementDraftMutation(requestId, draftRevision, editingId, title, text)) {
                            return@update it
                        }
                        it.copy(
                            isPermissionDenied = false,
                            errorMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        )
                    }
                    is AdminRepositoryResult.Error -> mutableState.update {
                        if (!isCurrentAnnouncementDraftMutation(requestId, draftRevision, editingId, title, text)) {
                            return@update it
                        }
                        it.copy(
                            isPermissionDenied = result.isPermissionDenied,
                            errorMessage = if (result.isPermissionDenied) result.message else it.errorMessage,
                            actionMessage = if (result.isPermissionDenied) null else result.message,
                            requiresRelogin = false,
                        )
                    }
                }
                return@launch
            }

            when (
                val result = repository.createAnnouncement(
                    title = title,
                    text = text,
                )
            ) {
                is AdminRepositoryResult.Success -> mutableState.update {
                    val isCurrentDraft = isCurrentAnnouncementDraftMutation(
                        requestId = requestId,
                        draftRevision = draftRevision,
                        editingId = null,
                        title = title,
                        text = text,
                    )
                    it.copy(
                        announcements = listOfNotNull(result.value) + it.announcements,
                        announcementTitleDraft = if (isCurrentDraft) "" else it.announcementTitleDraft,
                        announcementTextDraft = if (isCurrentDraft) "" else it.announcementTextDraft,
                        editingAnnouncementId = if (isCurrentDraft) null else it.editingAnnouncementId,
                        actionMessage = if (isCurrentDraft) "公告已创建" else it.actionMessage,
                        isPermissionDenied = false,
                        requiresRelogin = false,
                    )
                }
                AdminRepositoryResult.Unauthorized -> mutableState.update {
                    if (!isCurrentAnnouncementDraftMutation(requestId, draftRevision, null, title, text)) {
                        return@update it
                    }
                    it.copy(
                        isPermissionDenied = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AdminRepositoryResult.Error -> mutableState.update {
                    if (!isCurrentAnnouncementDraftMutation(requestId, draftRevision, null, title, text)) {
                        return@update it
                    }
                    it.copy(
                        isPermissionDenied = result.isPermissionDenied,
                        errorMessage = if (result.isPermissionDenied) result.message else it.errorMessage,
                        actionMessage = if (result.isPermissionDenied) null else result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun isCurrentAnnouncementDraftMutation(
        requestId: Int,
        draftRevision: Int,
        editingId: String?,
        title: String,
        text: String,
    ): Boolean {
        val current = state.value
        return requestId == announcementMutationRequestId &&
            draftRevision == announcementDraftRevision &&
            current.editingAnnouncementId == editingId &&
            current.announcementTitleDraft == title &&
            current.announcementTextDraft == text
    }

    fun deleteAnnouncement(announcementId: String) {
        val cleanAnnouncementId = announcementId.trim()
        if (cleanAnnouncementId.isBlank() || state.value.pendingIds.contains(cleanAnnouncementId)) return
        mutableState.update {
            it.copy(
                pendingIds = it.pendingIds + cleanAnnouncementId,
                actionMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            when (val result = repository.deleteAnnouncement(cleanAnnouncementId)) {
                is AdminRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        announcements = it.announcements.filterNot { announcement -> announcement.id == cleanAnnouncementId },
                        pendingIds = it.pendingIds - cleanAnnouncementId,
                        editingAnnouncementId = it.editingAnnouncementId?.takeUnless { id -> id == cleanAnnouncementId },
                        announcementTitleDraft = if (it.editingAnnouncementId == cleanAnnouncementId) "" else it.announcementTitleDraft,
                        announcementTextDraft = if (it.editingAnnouncementId == cleanAnnouncementId) "" else it.announcementTextDraft,
                        actionMessage = "公告已删除",
                        isPermissionDenied = false,
                        requiresRelogin = false,
                    )
                }
                AdminRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        pendingIds = it.pendingIds - cleanAnnouncementId,
                        isPermissionDenied = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AdminRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        pendingIds = it.pendingIds - cleanAnnouncementId,
                        isPermissionDenied = result.isPermissionDenied,
                        errorMessage = if (result.isPermissionDenied) result.message else it.errorMessage,
                        actionMessage = if (result.isPermissionDenied) null else result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

}
