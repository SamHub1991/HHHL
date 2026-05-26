package cc.hhhl.client.model

enum class PageListKind(val label: String) {
    Featured("精选"),
    Mine("我的"),
}

data class Page(
    val id: String,
    val title: String,
    val name: String,
    val summary: String,
    val author: User,
    val userId: String,
    val blocks: List<PageBlock>,
    val likedCount: Int,
    val isLiked: Boolean,
    val createdAtLabel: String = "",
    val updatedAtLabel: String = "",
) {
    val pathLabel: String
        get() = "/@${author.username}/pages/$name"
}

data class PageBlock(
    val id: String,
    val type: String,
    val text: String,
)
