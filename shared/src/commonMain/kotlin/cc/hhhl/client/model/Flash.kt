package cc.hhhl.client.model

enum class FlashListKind(val label: String) {
    Featured("精选"),
    Mine("我的"),
    Liked("喜欢"),
}

data class Flash(
    val id: String,
    val title: String,
    val summary: String,
    val script: String,
    val visibility: String,
    val author: User,
    val userId: String,
    val likedCount: Int,
    val isLiked: Boolean,
    val createdAtLabel: String = "",
    val updatedAtLabel: String = "",
) {
    val visibilityLabel: String
        get() = when (visibility) {
            "public" -> "公开"
            "private" -> "私密"
            else -> visibility.ifBlank { "未知" }
        }

    val scriptPreview: String
        get() = script.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString("\n")
}
