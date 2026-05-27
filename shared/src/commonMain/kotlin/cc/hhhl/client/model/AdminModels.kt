package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class AdminUserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val host: String? = null,
    val avatarUrl: String? = null,
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,
    val isSuspended: Boolean = false,
    val isSilenced: Boolean = false,
    val createdAtLabel: String = "",
    val notesCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
)

@Immutable
data class AdminAbuseReport(
    val id: String,
    val comment: String,
    val reporterName: String,
    val targetUserId: String,
    val targetUserName: String,
    val assigneeName: String? = null,
    val resolved: Boolean = false,
    val createdAtLabel: String = "",
)

@Immutable
data class AdminRoleSummary(
    val id: String,
    val name: String,
    val description: String = "",
    val isModeratorRole: Boolean = false,
    val isAdministratorRole: Boolean = false,
    val usersCount: Int = 0,
)

@Immutable
data class AdminAnnouncementSummary(
    val id: String,
    val title: String,
    val text: String,
    val icon: String = "info",
    val display: String = "normal",
    val isActive: Boolean = true,
    val createdAtLabel: String = "",
    val updatedAtLabel: String = "",
)

@Immutable
data class AdminInstanceSettings(
    val name: String = "",
    val description: String = "",
    val maintainerName: String = "",
    val maintainerEmail: String = "",
    val tosUrl: String = "",
    val repositoryUrl: String = "",
    val version: String = "",
    val enableRegistration: Boolean? = null,
    val emailRequiredForSignup: Boolean? = null,
)

@Immutable
data class AdminOverview(
    val users: List<AdminUserSummary> = emptyList(),
    val reports: List<AdminAbuseReport> = emptyList(),
    val roles: List<AdminRoleSummary> = emptyList(),
    val announcements: List<AdminAnnouncementSummary> = emptyList(),
    val instance: AdminInstanceSettings? = null,
)
