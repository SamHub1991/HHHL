package cc.hhhl.client.model

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

data class UserListDraft(
    val name: String,
    val isPublic: Boolean = false,
)
