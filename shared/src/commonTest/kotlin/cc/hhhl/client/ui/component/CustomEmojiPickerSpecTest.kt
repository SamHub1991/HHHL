package cc.hhhl.client.ui.component

import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.EmojiCategory
import cc.hhhl.client.model.commonEmojiCategories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomEmojiPickerSpecTest {
    @Test
    fun groupsEmojiByCategoryAndKeepsRecentFirst() {
        val sections = customEmojiPickerSections(
            customEmojis = listOf(
                customEmoji("blobcat", category = "cat"),
                customEmoji("party", category = "reaction"),
                customEmoji("wave", category = "reaction"),
            ),
            recentEmojiCodes = listOf(":party:"),
        )

        assertEquals(listOf("最近", "cat", "reaction"), sections.map { it.label })
        assertEquals(listOf("party"), sections[0].emojis.map { it.name })
        assertEquals(listOf("wave"), sections[2].emojis.map { it.name })
    }

    @Test
    fun searchesNameAliasCategoryAndReactionCode() {
        val emojis = listOf(
            customEmoji("blobcat", category = "cat", aliases = listOf("neko")),
            customEmoji("party", category = "reaction"),
        )

        assertEquals(listOf("blobcat"), customEmojiPickerSections(emojis, query = "neko").single().emojis.map { it.name })
        assertEquals(listOf("blobcat"), customEmojiPickerSections(emojis, query = ":blob").single().emojis.map { it.name })
        assertEquals(listOf("party"), customEmojiPickerSections(emojis, query = "reaction").single().emojis.map { it.name })
    }

    @Test
    fun filtersSensitiveAndBlankEmoji() {
        val sections = customEmojiPickerSections(
            customEmojis = listOf(
                customEmoji("secret", isSensitive = true),
                customEmoji("", category = "bad"),
                customEmoji("ok"),
            ),
        )

        assertEquals(listOf("ok"), sections.single().emojis.map { it.name })
    }

    @Test
    fun pickerResultInsertsReactionCode() {
        assertEquals(":blobcat:", customEmojiPickerResultText(customEmoji("blobcat")))
    }

    @Test
    fun unicodePickerKeepsAndroidStyleCategories() {
        val labels = unicodeEmojiPickerSections().map { it.label }

        assertTrue("表情" in labels)
        assertTrue("动物与自然" in labels)
        assertTrue("旗帜" in labels)
        assertTrue(commonEmojiCategories.flatMap { it.emojis }.distinct().size > 3500)
    }

    @Test
    fun unicodePickerSearchesAcrossCategoriesAndCapsEachSection() {
        val sections = unicodeEmojiPickerSections(
            query = "😀",
            categories = listOf(
                EmojiCategory(key = "face", label = "表情", emojis = listOf("😀", "😀", "😃")),
                EmojiCategory(key = "other", label = "其他", emojis = listOf("🔥")),
            ),
            maxPerCategory = 1,
        )

        assertEquals(listOf(UnicodeEmojiPickerSection("face", "表情", listOf("😀"))), sections)
    }

    @Test
    fun unicodePickerDeduplicatesAcrossCategoriesInDisplayOrder() {
        val sections = unicodeEmojiPickerSections(
            categories = listOf(
                EmojiCategory(key = "first", label = "第一类", emojis = listOf("😀", "🔥")),
                EmojiCategory(key = "second", label = "第二类", emojis = listOf("🔥", "🎉")),
            ),
        )

        assertEquals(
            listOf(
                UnicodeEmojiPickerSection("first", "第一类", listOf("😀", "🔥")),
                UnicodeEmojiPickerSection("second", "第二类", listOf("🎉")),
            ),
            sections,
        )
    }

    private fun customEmoji(
        name: String,
        category: String? = null,
        aliases: List<String> = emptyList(),
        isSensitive: Boolean = false,
    ): CustomEmoji {
        return CustomEmoji(
            name = name,
            category = category,
            url = "https://dc.hhhl.cc/emoji/$name.webp",
            aliases = aliases,
            localOnly = true,
            isSensitive = isSensitive,
        )
    }
}
