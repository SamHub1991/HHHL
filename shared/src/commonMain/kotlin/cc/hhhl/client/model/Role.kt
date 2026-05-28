package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class RoleSummary(
    val id: String,
    val name: String,
    val description: String = "",
    val usersCount: Int = 0,
    val isPublic: Boolean = true,
    val isModerator: Boolean = false,
    val isAdministrator: Boolean = false,
    val color: String? = null,
)
