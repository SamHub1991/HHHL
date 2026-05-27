package cc.hhhl.client.repository

import cc.hhhl.client.api.AdminApi
import cc.hhhl.client.api.AdminApiResult
import cc.hhhl.client.model.AdminAbuseReport
import cc.hhhl.client.model.AdminAnnouncementSummary
import cc.hhhl.client.model.AdminInstanceSettings
import cc.hhhl.client.model.AdminOverview
import cc.hhhl.client.model.AdminRoleSummary
import cc.hhhl.client.model.AdminUserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AdminRepositoryTest {
    @Test
    fun overviewCombinesAvailableAdminSections() = runTest {
        val repository = AdminRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(),
        )

        val result = repository.overview()

        val success = assertIs<AdminRepositoryResult.Success<AdminOverview>>(result)
        assertEquals(listOf(sampleUser()), success.value.users)
        assertEquals(listOf(sampleReport()), success.value.reports)
        assertEquals(listOf(sampleRole()), success.value.roles)
        assertEquals(listOf(sampleAnnouncement()), success.value.announcements)
        assertEquals(sampleInstance(), success.value.instance)
    }

    @Test
    fun missingTokenDoesNotCallApi() = runTest {
        var calls = 0
        val repository = AdminRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<AdminRepositoryResult.Unauthorized>(repository.searchUsers(""))
        assertIs<AdminRepositoryResult.Unauthorized>(repository.loadReports())
        assertEquals(0, calls)
    }

    @Test
    fun unauthorizedSectionMakesOverviewUnauthorized() = runTest {
        val repository = AdminRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(reportResult = AdminApiResult.Unauthorized),
        )

        assertIs<AdminRepositoryResult.Unauthorized>(repository.overview())
    }

    @Test
    fun permissionDeniedSectionStaysErrorInsteadOfUnauthorized() = runTest {
        val repository = AdminRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                reportResult = AdminApiResult.ServerError(
                    statusCode = 403,
                    message = "当前登录缺少此功能权限，请检查应用授权或账号权限",
                ),
            ),
        )

        val result = repository.overview()

        assertEquals(
            AdminRepositoryResult.Error(
                message = "当前登录缺少此功能权限，请检查应用授权或账号权限",
                isPermissionDenied = true,
            ),
            result,
        )
    }

    private fun fakeApi(
        userResult: AdminApiResult<List<AdminUserSummary>> = AdminApiResult.Success(listOf(sampleUser())),
        reportResult: AdminApiResult<List<AdminAbuseReport>> = AdminApiResult.Success(listOf(sampleReport())),
        roleResult: AdminApiResult<List<AdminRoleSummary>> = AdminApiResult.Success(listOf(sampleRole())),
        announcementResult: AdminApiResult<List<AdminAnnouncementSummary>> = AdminApiResult.Success(
            listOf(sampleAnnouncement()),
        ),
        instanceResult: AdminApiResult<AdminInstanceSettings> = AdminApiResult.Success(sampleInstance()),
        onCall: () -> Unit = {},
    ): AdminApi {
        return object : AdminApi {
            override suspend fun searchUsers(
                token: String,
                query: String,
                limit: Int,
            ): AdminApiResult<List<AdminUserSummary>> {
                onCall()
                return userResult
            }

            override suspend fun loadReports(token: String, limit: Int): AdminApiResult<List<AdminAbuseReport>> {
                onCall()
                return reportResult
            }

            override suspend fun resolveReport(
                token: String,
                reportId: String,
                forward: Boolean,
            ): AdminApiResult<Unit> {
                onCall()
                return AdminApiResult.Success(Unit)
            }

            override suspend fun loadRoles(token: String): AdminApiResult<List<AdminRoleSummary>> {
                onCall()
                return roleResult
            }

            override suspend fun loadUserRoles(
                token: String,
                userId: String,
            ): AdminApiResult<List<AdminRoleSummary>> {
                onCall()
                return roleResult
            }

            override suspend fun loadAnnouncements(
                token: String,
            ): AdminApiResult<List<AdminAnnouncementSummary>> {
                onCall()
                return announcementResult
            }

            override suspend fun createAnnouncement(
                token: String,
                title: String,
                text: String,
            ): AdminApiResult<AdminAnnouncementSummary?> {
                onCall()
                return AdminApiResult.Success(sampleAnnouncement())
            }

            override suspend fun updateAnnouncement(
                token: String,
                announcementId: String,
                title: String,
                text: String,
            ): AdminApiResult<Unit> {
                onCall()
                return AdminApiResult.Success(Unit)
            }

            override suspend fun deleteAnnouncement(
                token: String,
                announcementId: String,
            ): AdminApiResult<Unit> {
                onCall()
                return AdminApiResult.Success(Unit)
            }

            override suspend fun loadInstanceSettings(token: String): AdminApiResult<AdminInstanceSettings> {
                onCall()
                return instanceResult
            }
        }
    }
}

private fun sampleUser(): AdminUserSummary {
    return AdminUserSummary(
        id = "user-1",
        username = "alice",
        displayName = "Alice",
        isModerator = true,
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
