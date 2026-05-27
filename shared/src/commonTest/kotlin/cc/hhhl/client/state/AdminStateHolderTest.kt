@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.hhhl.client.state

import cc.hhhl.client.model.AdminAbuseReport
import cc.hhhl.client.model.AdminAnnouncementSummary
import cc.hhhl.client.model.AdminInstanceSettings
import cc.hhhl.client.model.AdminOverview
import cc.hhhl.client.model.AdminRoleSummary
import cc.hhhl.client.model.AdminUserSummary
import cc.hhhl.client.api.AdminApi
import cc.hhhl.client.api.AdminApiResult
import cc.hhhl.client.repository.AdminRepository
import cc.hhhl.client.repository.AdminRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class AdminStateHolderTest {
    @Test
    fun unauthorizedRefreshRequiresRelogin() = runTest {
        val holder = AdminStateHolder(
            repository = fakeRepository(
                overviewResult = AdminRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertFalse(holder.state.value.isPermissionDenied)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun permissionDeniedRefreshKeepsUserOnAdminScreen() = runTest {
        val holder = AdminStateHolder(
            repository = fakeRepository(
                overviewResult = AdminRepositoryResult.Error(
                    message = "当前登录缺少此功能权限，请检查应用授权或账号权限",
                    isPermissionDenied = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertTrue(holder.state.value.isPermissionDenied)
        assertEquals("当前登录缺少此功能权限，请检查应用授权或账号权限", holder.state.value.errorMessage)
    }

    @Test
    fun editAnnouncementSavesUpdatedDraftBackToList() = runTest {
        val original = sampleAnnouncement()
        val holder = AdminStateHolder(
            repository = fakeRepository(
                overviewResult = AdminRepositoryResult.Success(
                    AdminOverview(
                        announcements = listOf(original),
                    ),
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.editAnnouncement(original)
        holder.updateAnnouncementDraft("新标题", "新内容")
        holder.createAnnouncement()
        advanceUntilIdle()

        val updated = holder.state.value.announcements.single()
        assertEquals("新标题", updated.title)
        assertEquals("新内容", updated.text)
        assertNull(holder.state.value.editingAnnouncementId)
        assertEquals("公告已更新", holder.state.value.actionMessage)
    }

    @Test
    fun cancelEditClearsAnnouncementDraft() = runTest {
        val original = sampleAnnouncement()
        val holder = AdminStateHolder(
            repository = fakeRepository(),
            scope = TestScope(testScheduler),
        )

        holder.editAnnouncement(original)
        holder.cancelAnnouncementEdit()

        assertNull(holder.state.value.editingAnnouncementId)
        assertTrue(holder.state.value.announcementTitleDraft.isEmpty())
        assertTrue(holder.state.value.announcementTextDraft.isEmpty())
    }

    private fun fakeRepository(
        overviewResult: AdminRepositoryResult<AdminOverview> = AdminRepositoryResult.Success(
            AdminOverview(
                users = listOf(sampleUser()),
                reports = listOf(sampleReport()),
                roles = listOf(sampleRole()),
                announcements = listOf(sampleAnnouncement()),
                instance = sampleInstance(),
            ),
        ),
        userRolesResult: AdminRepositoryResult<List<AdminRoleSummary>> = AdminRepositoryResult.Success(
            listOf(sampleRole()),
        ),
        resolveReportResult: AdminRepositoryResult<Unit> = AdminRepositoryResult.Success(Unit),
        createAnnouncementResult: AdminRepositoryResult<AdminAnnouncementSummary?> = AdminRepositoryResult.Success(
            sampleAnnouncement(),
        ),
        updateAnnouncementResult: AdminRepositoryResult<Unit> = AdminRepositoryResult.Success(Unit),
        deleteAnnouncementResult: AdminRepositoryResult<Unit> = AdminRepositoryResult.Success(Unit),
    ): AdminRepository {
        return object : AdminRepository(tokenProvider = { "token-123" }, api = unusedAdminApi()) {
            override suspend fun overview(userQuery: String): AdminRepositoryResult<AdminOverview> = overviewResult

            override suspend fun searchUsers(query: String): AdminRepositoryResult<List<AdminUserSummary>> {
                return overviewResult.usersOrEmpty()
            }

            override suspend fun loadReports(): AdminRepositoryResult<List<AdminAbuseReport>> {
                return overviewResult.reportsOrEmpty()
            }

            override suspend fun loadRoles(): AdminRepositoryResult<List<AdminRoleSummary>> {
                return overviewResult.rolesOrEmpty()
            }

            override suspend fun loadUserRoles(userId: String): AdminRepositoryResult<List<AdminRoleSummary>> {
                return userRolesResult
            }

            override suspend fun loadAnnouncements(): AdminRepositoryResult<List<AdminAnnouncementSummary>> {
                return overviewResult.announcementsOrEmpty()
            }

            override suspend fun loadInstanceSettings(): AdminRepositoryResult<AdminInstanceSettings> {
                return overviewResult.instanceOrDefault()
            }

            override suspend fun resolveReport(reportId: String, forward: Boolean): AdminRepositoryResult<Unit> {
                return resolveReportResult
            }

            override suspend fun createAnnouncement(
                title: String,
                text: String,
            ): AdminRepositoryResult<AdminAnnouncementSummary?> {
                return createAnnouncementResult
            }

            override suspend fun updateAnnouncement(
                announcementId: String,
                title: String,
                text: String,
            ): AdminRepositoryResult<Unit> {
                return updateAnnouncementResult
            }

            override suspend fun deleteAnnouncement(announcementId: String): AdminRepositoryResult<Unit> {
                return deleteAnnouncementResult
            }
        }
    }
}

private fun unusedAdminApi(): AdminApi {
    return object : AdminApi {
        override suspend fun searchUsers(
            token: String,
            query: String,
            limit: Int,
        ): AdminApiResult<List<AdminUserSummary>> = error("Unused fake API")

        override suspend fun loadReports(
            token: String,
            limit: Int,
        ): AdminApiResult<List<AdminAbuseReport>> = error("Unused fake API")

        override suspend fun resolveReport(
            token: String,
            reportId: String,
            forward: Boolean,
        ): AdminApiResult<Unit> = error("Unused fake API")

        override suspend fun loadRoles(token: String): AdminApiResult<List<AdminRoleSummary>> {
            return error("Unused fake API")
        }

        override suspend fun loadUserRoles(
            token: String,
            userId: String,
        ): AdminApiResult<List<AdminRoleSummary>> = error("Unused fake API")

        override suspend fun loadAnnouncements(token: String): AdminApiResult<List<AdminAnnouncementSummary>> {
            return error("Unused fake API")
        }

        override suspend fun createAnnouncement(
            token: String,
            title: String,
            text: String,
        ): AdminApiResult<AdminAnnouncementSummary?> = error("Unused fake API")

        override suspend fun updateAnnouncement(
            token: String,
            announcementId: String,
            title: String,
            text: String,
        ): AdminApiResult<Unit> = error("Unused fake API")

        override suspend fun deleteAnnouncement(
            token: String,
            announcementId: String,
        ): AdminApiResult<Unit> = error("Unused fake API")

        override suspend fun loadInstanceSettings(token: String): AdminApiResult<AdminInstanceSettings> {
            return error("Unused fake API")
        }
    }
}

private fun AdminRepositoryResult<AdminOverview>.usersOrEmpty(): AdminRepositoryResult<List<AdminUserSummary>> {
    return when (this) {
        is AdminRepositoryResult.Success -> AdminRepositoryResult.Success(value.users)
        AdminRepositoryResult.Unauthorized -> AdminRepositoryResult.Unauthorized
        is AdminRepositoryResult.Error -> AdminRepositoryResult.Error(
            message = message,
            isEndpointUnavailable = isEndpointUnavailable,
            isPermissionDenied = isPermissionDenied,
        )
    }
}

private fun AdminRepositoryResult<AdminOverview>.reportsOrEmpty(): AdminRepositoryResult<List<AdminAbuseReport>> {
    return when (this) {
        is AdminRepositoryResult.Success -> AdminRepositoryResult.Success(value.reports)
        AdminRepositoryResult.Unauthorized -> AdminRepositoryResult.Unauthorized
        is AdminRepositoryResult.Error -> this.toSameError()
    }
}

private fun AdminRepositoryResult<AdminOverview>.rolesOrEmpty(): AdminRepositoryResult<List<AdminRoleSummary>> {
    return when (this) {
        is AdminRepositoryResult.Success -> AdminRepositoryResult.Success(value.roles)
        AdminRepositoryResult.Unauthorized -> AdminRepositoryResult.Unauthorized
        is AdminRepositoryResult.Error -> this.toSameError()
    }
}

private fun AdminRepositoryResult<AdminOverview>.announcementsOrEmpty(): AdminRepositoryResult<List<AdminAnnouncementSummary>> {
    return when (this) {
        is AdminRepositoryResult.Success -> AdminRepositoryResult.Success(value.announcements)
        AdminRepositoryResult.Unauthorized -> AdminRepositoryResult.Unauthorized
        is AdminRepositoryResult.Error -> this.toSameError()
    }
}

private fun AdminRepositoryResult<AdminOverview>.instanceOrDefault(): AdminRepositoryResult<AdminInstanceSettings> {
    return when (this) {
        is AdminRepositoryResult.Success -> AdminRepositoryResult.Success(value.instance ?: AdminInstanceSettings())
        AdminRepositoryResult.Unauthorized -> AdminRepositoryResult.Unauthorized
        is AdminRepositoryResult.Error -> this.toSameError()
    }
}

private fun AdminRepositoryResult.Error.toSameError(): AdminRepositoryResult.Error {
    return AdminRepositoryResult.Error(
        message = message,
        isEndpointUnavailable = isEndpointUnavailable,
        isPermissionDenied = isPermissionDenied,
    )
}

private fun sampleUser(): AdminUserSummary {
    return AdminUserSummary(
        id = "user-1",
        username = "alice",
        displayName = "Alice",
    )
}

private fun sampleReport(): AdminAbuseReport {
    return AdminAbuseReport(
        id = "report-1",
        comment = "spam",
        reporterName = "Bob",
        targetUserId = "user-1",
        targetUserName = "Alice",
    )
}

private fun sampleRole(): AdminRoleSummary {
    return AdminRoleSummary(
        id = "role-1",
        name = "Moderator",
        isModeratorRole = true,
    )
}

private fun sampleAnnouncement(): AdminAnnouncementSummary {
    return AdminAnnouncementSummary(
        id = "ann-1",
        title = "Notice",
        text = "Hello",
    )
}

private fun sampleInstance(): AdminInstanceSettings {
    return AdminInstanceSettings(
        name = "HHHL",
        version = "2026.5",
        enableRegistration = true,
    )
}
