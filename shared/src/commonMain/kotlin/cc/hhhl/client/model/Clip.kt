package cc.hhhl.client.model

enum class ClipListKind(val label: String) {
    Owned("我的"),
    Favorites("收藏"),
}

data class Clip(
    val id: String,
    val name: String,
    val description: String,
    val owner: User,
    val ownerId: String,
    val isPublic: Boolean,
    val isFavorited: Boolean,
    val favoritedCount: Int,
    val notesCount: Int,
    val createdAtLabel: String = "",
    val lastClippedAtLabel: String = "",
) {
    val visibilityLabel: String
        get() = if (isPublic) "公开" else "私密"
}
