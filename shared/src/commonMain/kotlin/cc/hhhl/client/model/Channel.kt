package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

enum class ChannelListKind(val label: String) {
    Featured("精选"),
    Followed("关注"),
    Favorites("收藏"),
    Owned("我的"),
}

@Immutable
data class Channel(
    val id: String,
    val name: String,
    val description: String,
    val color: String,
    val userId: String?,
    val bannerUrl: String?,
    val pinnedNoteIds: List<String>,
    val pinnedNotes: List<Note>,
    val isArchived: Boolean,
    val isSensitive: Boolean,
    val allowRenoteToExternal: Boolean,
    val isFollowing: Boolean,
    val isFavorited: Boolean,
    val hasUnreadNote: Boolean,
    val usersCount: Int,
    val notesCount: Int,
    val createdAtLabel: String = "",
    val lastNotedAtLabel: String = "",
) {
    val statusLabel: String
        get() = when {
            isArchived -> "已归档"
            isSensitive -> "敏感"
            hasUnreadNote -> "有新动态"
            else -> "频道"
    }
}

@Immutable
data class ChannelDraft(
    val name: String,
    val description: String = "",
    val color: String = "#40c057",
    val bannerId: String? = null,
    val isArchived: Boolean = false,
    val isSensitive: Boolean = false,
    val allowRenoteToExternal: Boolean = true,
)
