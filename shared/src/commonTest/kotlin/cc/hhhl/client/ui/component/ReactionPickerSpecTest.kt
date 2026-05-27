package cc.hhhl.client.ui.component

import cc.hhhl.client.model.CustomEmoji
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.commonReactionOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class ReactionPickerSpecTest {
    @Test
    fun reactionPickerGridUsesCompactSquareItems() {
        assertEquals(260.dp, HhhlReactionPickerMenuWidth)
        assertEquals(42.dp, HhhlReactionPickerItemSize)
        assertEquals(6.dp, HhhlReactionPickerGridSpacing)
    }

    @Test
    fun groupsDefaultCommonAndCustomReactionsForPicker() {
        val sections = reactionPickerSections(
            reactionOptions = listOf("⭐", "👍", "🎉", ":blobcat:", "🔥", ":party:"),
        )

        assertEquals(
            listOf(
                ReactionPickerSection("默认", listOf("⭐")),
                ReactionPickerSection("常用", listOf("👍", "🎉", "🔥")),
                ReactionPickerSection("自定义", listOf(":blobcat:", ":party:")),
            ),
            sections,
        )
    }

    @Test
    fun pickerSectionsRemoveDuplicatesAndBlankReactions() {
        val sections = reactionPickerSections(
            reactionOptions = listOf("❤️", "", "👍", "❤️", ":blobcat:", ":blobcat:"),
        )

        assertEquals(
            listOf(
                ReactionPickerSection("默认", listOf("❤️")),
                ReactionPickerSection("常用", listOf("👍")),
                ReactionPickerSection("自定义", listOf(":blobcat:")),
            ),
            sections,
        )
    }

    @Test
    fun emptyPickerOptionsFallbackToDefaultLike() {
        assertEquals(
            listOf(
                ReactionPickerSection("默认", listOf(commonReactionOptions.first())),
                ReactionPickerSection("常用", commonReactionOptions.drop(1)),
            ),
            reactionPickerSections(reactionOptions = emptyList()),
        )
    }

    @Test
    fun filtersPickerSectionsByReactionQuery() {
        val sections = reactionPickerSections(
            reactionOptions = listOf("⭐", "👍", "🎉", ":blobcat:", ":party:", "🔥"),
            query = "party",
        )

        assertEquals(
            listOf(ReactionPickerSection("自定义", listOf(":party:"))),
            sections,
        )
    }

    @Test
    fun filteringByEmojiItselfKeepsMatchingCommonReaction() {
        val sections = reactionPickerSections(
            reactionOptions = listOf("⭐", "👍", "🎉", ":blobcat:"),
            query = "👍",
        )

        assertEquals(
            listOf(ReactionPickerSection("常用", listOf("👍"))),
            sections,
        )
    }

    @Test
    fun recentReactionsAreShownBeforeDefaultAndRemovedFromOtherSections() {
        val sections = reactionPickerSections(
            reactionOptions = listOf("⭐", "👍", "🎉", ":blobcat:", ":party:", "🔥"),
            recentReactions = listOf(":party:", "👍"),
        )

        assertEquals(
            listOf(
                ReactionPickerSection("最近", listOf(":party:", "👍")),
                ReactionPickerSection("默认", listOf("⭐")),
                ReactionPickerSection("常用", listOf("🎉", "🔥")),
                ReactionPickerSection("自定义", listOf(":blobcat:")),
            ),
            sections,
        )
    }

    @Test
    fun recentReactionsParticipateInSearchFiltering() {
        val sections = reactionPickerSections(
            reactionOptions = listOf("⭐", "👍", ":blobcat:", ":party:"),
            recentReactions = listOf(":party:", "👍"),
            query = "party",
        )

        assertEquals(
            listOf(ReactionPickerSection("最近", listOf(":party:"))),
            sections,
        )
    }

    @Test
    fun recentDefaultReactionIsNotRepeatedInDefaultSection() {
        val sections = reactionPickerSections(
            reactionOptions = listOf("❤️", "👍", ":blobcat:"),
            recentReactions = listOf("❤️"),
        )

        assertEquals(
            listOf(
                ReactionPickerSection("最近", listOf("❤️")),
                ReactionPickerSection("常用", listOf("👍")),
                ReactionPickerSection("自定义", listOf(":blobcat:")),
            ),
            sections,
        )
    }

    @Test
    fun customEmojiPickerSourceMergesLoadedEmojiAndGlobalUrls() {
        val emojis = customEmojisForReactionPicker(
            reactionOptions = listOf(":party:", ":remote@.:"),
            customEmojis = listOf(
                CustomEmoji(
                    name = "party",
                    category = "reaction",
                    url = "https://dc.hhhl.cc/emoji/party.webp",
                    aliases = emptyList(),
                    localOnly = true,
                    isSensitive = false,
                ),
                CustomEmoji(
                    name = "hidden",
                    category = "bad",
                    url = "https://dc.hhhl.cc/emoji/hidden.webp",
                    aliases = emptyList(),
                    localOnly = true,
                    isSensitive = true,
                ),
            ),
            customEmojiUrls = mapOf(
                ":remote@.:" to "https://dc.hhhl.cc/emoji/remote.webp",
                ":extra:" to "https://dc.hhhl.cc/emoji/extra.webp",
            ),
        )

        assertEquals(listOf("party", "extra", "remote"), emojis.map { it.name })
        assertEquals(listOf("reaction", "实例", "实例"), emojis.map { it.category })
    }
}
