package cc.hhhl.client.repository

import cc.hhhl.client.api.AdminApi
import cc.hhhl.client.api.AdminApiResult
import cc.hhhl.client.api.SharkeyAdminApi
import cc.hhhl.client.model.AdminAbuseReport
import cc.hhhl.client.model.AdminAnnouncementSummary
import cc.hhhl.client.model.AdminInstanceSettings
import cc.hhhl.client.model.AdminOverview
import cc.hhhl.client.model.AdminRoleSummary
import cc.hhhl.client.model.AdminUserSummary

open class AdminRepository(
    private val tokenProvider: () -> String?,
    private val api: AdminApi = SharkeyAdminApi(),
) {
    open suspend fun overview(userQuery: String = ""): AdminRepositoryResult<AdminOverview> {
        val users = searchUsers(userQuery)
        val reports = loadReports()
        val roles = loadRoles()
        val announcements = loadAnnouncements()
        val instance = loadInstanceSettings()

        val firstAuthFailure = listOf(users, reports, roles, announcements, instance)
            .firstOrNull { it is AdminRepositoryResult.Unauthorized }
        if (firstAuthFailure != null) return AdminRepositoryResult.Unauthorized

        val firstHardError = listOf(users, reports, roles, announcements, instance)
            .filterIsInstance<AdminRepositoryResult.Error>()
            .firstOrNull { !it.isEndpointUnavailable }
        if (firstHardError != null) return firstHardError

        return AdminRepositoryResult.Success(
            AdminOverview(
                users = users.successValue().orEmpty(),
                reports = reports.successValue().orEmpty(),
                roles = roles.successValue().orEmpty(),
                announcements = announcements.successValue().orEmpty(),
                instance = instance.successValue(),
            ),
        )
    }

    open suspend fun searchUsers(query: String): AdminRepositoryResult<List<AdminUserSummary>> {
        return withToken { token ->
            api.searchUsers(token = token, query = query.trim(), limit = DEFAULT_LIST_LIMIT)
        }
    }

    open suspend fun loadReports(): AdminRepositoryResult<List<AdminAbuseReport>> {
        return withToken { token ->
            api.loadReports(token = token, limit = DEFAULT_LIST_LIMIT)
        }
    }

    open suspend fun resolveReport(reportId: String, forward: Boolean = false): AdminRepositoryResult<Unit> {
        if (reportId.isBlank()) return AdminRepositoryResult.Error("无法处理举报")
        return withToken { token ->
            api.resolveReport(token = token, reportId = reportId.trim(), forward = forward)
        }
    }

    open suspend fun loadRoles(): AdminRepositoryResult<List<AdminRoleSummary>> {
        return withToken { token -> api.loadRoles(token) }
    }

    open suspend fun loadUserRoles(userId: String): AdminRepositoryResult<List<AdminRoleSummary>> {
        if (userId.isBlank()) return AdminRepositoryResult.Error("无法读取用户角色")
        return withToken { token -> api.loadUserRoles(token = token, userId = userId.trim()) }
    }

    open suspend fun loadAnnouncements(): AdminRepositoryResult<List<AdminAnnouncementSummary>> {
        return withToken { token -> api.loadAnnouncements(token) }
    }

    open suspend fun createAnnouncement(title: String, text: String): AdminRepositoryResult<AdminAnnouncementSummary?> {
        if (title.isBlank() || text.isBlank()) return AdminRepositoryResult.Error("公告标题和内容不能为空")
        return withToken { token ->
            api.createAnnouncement(token = token, title = title.trim(), text = text.trim())
        }
    }

    open suspend fun updateAnnouncement(
        announcementId: String,
        title: String,
        text: String,
    ): AdminRepositoryResult<Unit> {
        if (announcementId.isBlank() || title.isBlank() || text.isBlank()) {
            return AdminRepositoryResult.Error("公告标题和内容不能为空")
        }
        return withToken { token ->
            api.updateAnnouncement(
                token = token,
                announcementId = announcementId.trim(),
                title = title.trim(),
                text = text.trim(),
            )
        }
    }

    open suspend fun deleteAnnouncement(announcementId: String): AdminRepositoryResult<Unit> {
        if (announcementId.isBlank()) return AdminRepositoryResult.Error("无法删除公告")
        return withToken { token ->
            api.deleteAnnouncement(token = token, announcementId = announcementId.trim())
        }
    }

    open suspend fun loadInstanceSettings(): AdminRepositoryResult<AdminInstanceSettings> {
        return withToken { token -> api.loadInstanceSettings(token) }
    }

    private suspend fun <T> withToken(
        request: suspend (String) -> AdminApiResult<T>,
    ): AdminRepositoryResult<T> {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AdminRepositoryResult.Unauthorized

        return when (val result = request(token)) {
            is AdminApiResult.Success -> AdminRepositoryResult.Success(result.value)
            AdminApiResult.Unauthorized -> AdminRepositoryResult.Unauthorized
            is AdminApiResult.NetworkError -> AdminRepositoryResult.Error("无法连接服务器：${result.message}")
            is AdminApiResult.ServerError -> AdminRepositoryResult.Error(
                message = result.message,
                isEndpointUnavailable = result.statusCode == 404,
                isPermissionDenied = result.statusCode == 403,
            )
        }
    }

    private fun <T> AdminRepositoryResult<T>.successValue(): T? {
        return (this as? AdminRepositoryResult.Success<T>)?.value
    }

    private companion object {
        const val DEFAULT_LIST_LIMIT = 30
    }
}

sealed interface AdminRepositoryResult<out T> {
    data class Success<T>(val value: T) : AdminRepositoryResult<T>
    data object Unauthorized : AdminRepositoryResult<Nothing>
    data class Error(
        val message: String,
        val isEndpointUnavailable: Boolean = false,
        val isPermissionDenied: Boolean = false,
    ) : AdminRepositoryResult<Nothing>
}
