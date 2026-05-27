package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserList(
    val id: String,
    val name: String,
    val createdBy: String,
    val userIds: List<String>,
    val isPublic: Boolean,
    val isLiked: Boolean = false,
    val likedCount: Int = 0,
    val createdAtLabel: String = "",
) {
    val memberCount: Int
        get() = userIds.size
}

@Immutable
data class UserListDraft(
    val name: String,
    val isPublic: Boolean = false,
)
