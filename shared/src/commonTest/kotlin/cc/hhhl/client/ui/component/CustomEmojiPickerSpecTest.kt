package cc.hhhl.client.ui.component

import cc.hhhl.client.model.CustomEmoji
import kotlin.test.Test
import kotlin.test.assertEquals

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
