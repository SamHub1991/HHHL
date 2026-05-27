package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

enum class PageListKind(val label: String) {
    Featured("精选"),
    Mine("我的"),
}

@Immutable
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

    fun toDraft(): PageDraft {
        return PageDraft(
            title = title,
            name = name,
            summary = summary,
            content = blocks.joinToString("\n\n") { it.text }.trim(),
            visibility = PageVisibility.Public,
        )
    }
}

@Immutable
data class PageBlock(
    val id: String,
    val type: String,
    val text: String,
)

enum class PageVisibility(val apiValue: String, val label: String) {
    Public("public", "公开"),
    Followers("followers", "关注者"),
    Specified("specified", "指定"),
}

@Immutable
data class PageDraft(
    val title: String = "",
    val name: String = "",
    val summary: String = "",
    val content: String = "",
    val visibility: PageVisibility = PageVisibility.Public,
    val fileIds: List<String> = emptyList(),
) {
    val canSubmit: Boolean
        get() = title.isNotBlank() && name.isNotBlank()
}
