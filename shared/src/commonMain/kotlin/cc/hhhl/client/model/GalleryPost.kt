package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

enum class GalleryListKind(val label: String) {
    Featured("精选"),
    Popular("热门"),
    Recent("最新"),
    Mine("我的"),
    Liked("喜欢"),
}

@Immutable
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
    val isPublic: Boolean = true,
    val likedCount: Int,
    val isLiked: Boolean,
    val createdAtLabel: String = "",
    val updatedAtLabel: String = "",
) {
    val imageCountLabel: String
        get() = "${files.size} 张"
}

@Immutable
data class GalleryPostDraft(
    val title: String,
    val description: String = "",
    val fileIds: List<String> = emptyList(),
    val isSensitive: Boolean = false,
    val isPublic: Boolean = true,
)
