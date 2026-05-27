package cc.hhhl.client.model

data class EmojiCategory(
    val key: String,
    val label: String,
    val emojis: List<String>,
)

val commonReactionOptions: List<String> = listOf(
    "❤️", "👍", "👎", "😂", "🤣", "🥰", "😍", "😭",
    "😆", "😮", "😢", "😡", "🤔", "👀", "🙏", "👏",
    "🙌", "👌", "🤝", "💪", "🔥", "✨", "🎉", "💯",
    "✅", "❌", "⭐", "🌟", "🚀", "💔", "🥹", "😎",
)

private val emojiGroupLabelOverrides: Map<String, String> = mapOf(
    "smileys-and-emotion" to "表情",
    "people-and-body" to "人物与身体",
    "component" to "修饰",
    "animals-and-nature" to "动物与自然",
    "food-and-drink" to "食物与饮品",
    "travel-and-places" to "地点与出行",
    "activities" to "活动",
    "objects" to "物品",
    "symbols" to "符号",
    "flags" to "旗帜",
)

private fun buildAndroidEmojiCategories(): List<EmojiCategory> {
    return androidEmojiCatalogRows
        .groupBy { it.groupKey }
        .map { (groupKey, rows) ->
            val seen = LinkedHashSet<String>()
            val emojis = rows
                .asSequence()
                .flatMap { row -> row.emojis.split(' ').asSequence() }
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filter(seen::add)
                .toList()
            EmojiCategory(
                key = groupKey,
                label = emojiGroupLabelOverrides[groupKey] ?: rows.first().groupLabel,
                emojis = emojis,
            )
        }
}

val commonEmojiCategories: List<EmojiCategory> = buildAndroidEmojiCategories()

val commonEmojiOptions: List<String> = commonEmojiCategories
    .flatMap { it.emojis }
    .distinct()
