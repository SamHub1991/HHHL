package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

enum class FlashListKind(val label: String) {
    Featured("精选"),
    Mine("我的"),
    Liked("喜欢"),
}

@Immutable
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
    val permissions: List<String> = emptyList(),
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

@Immutable
data class FlashDraft(
    val title: String = "",
    val summary: String = "",
    val script: String = "",
    val visibility: String = "public",
    val permissions: List<String> = emptyList(),
) {
    val trimmed: FlashDraft
        get() = copy(
            title = title.trim(),
            summary = summary.trim(),
            script = script.trim(),
            visibility = visibility.trim().ifBlank { "public" },
            permissions = permissions.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        )
}

fun Flash.toDraft(): FlashDraft {
    return FlashDraft(
        title = title,
        summary = summary,
        script = script,
        visibility = visibility.ifBlank { "public" },
        permissions = permissions,
    )
}
