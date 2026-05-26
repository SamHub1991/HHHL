package cc.hhhl.client.model

data class Antenna(
    val id: String,
    val name: String,
    val source: String,
    val keywords: List<List<String>>,
    val excludeKeywords: List<List<String>>,
    val userListId: String?,
    val users: List<String>,
    val caseSensitive: Boolean,
    val localOnly: Boolean,
    val excludeBots: Boolean,
    val withReplies: Boolean,
    val withFile: Boolean,
    val isActive: Boolean,
    val hasUnreadNote: Boolean,
    val notify: Boolean,
    val excludeNotesInSensitiveChannel: Boolean,
    val createdAtLabel: String = "",
) {
    val keywordPreview: String
        get() = keywords
            .mapNotNull { group -> group.filter { it.isNotBlank() }.joinToString(" ") }
            .filter { it.isNotBlank() }
            .joinToString(" / ")
            .ifBlank { "未设置关键词" }
}

data class AntennaDraft(
    val name: String,
    val source: String = "all",
    val keywords: List<List<String>> = emptyList(),
    val excludeKeywords: List<List<String>> = emptyList(),
    val userListId: String? = null,
    val users: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
    val localOnly: Boolean = false,
    val excludeBots: Boolean = false,
    val withReplies: Boolean = false,
    val withFile: Boolean = false,
    val isActive: Boolean = true,
    val notify: Boolean = false,
    val excludeNotesInSensitiveChannel: Boolean = true,
)
