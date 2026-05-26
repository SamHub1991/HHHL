package cc.hhhl.client.model

enum class GalleryListKind(val label: String) {
    Featured("精选"),
    Popular("热门"),
    Recent("最新"),
    Mine("我的"),
    Liked("喜欢"),
}

data class GalleryPost(
    val id: String,
    val title: String,
    val description: String,
    val author: User,
    val userId: String,
    val fileIds: List<String>,
    val files: List<DriveFile>,
    val tags: List<String>,
    val isSensitive: Boolean,
    val likedCount: Int,
    val isLiked: Boolean,
    val createdAtLabel: String = "",
    val updatedAtLabel: String = "",
) {
    val imageCountLabel: String
        get() = "${files.size} 张"
}
