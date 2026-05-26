package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class ReactionPickerSpecTest {
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
            listOf(ReactionPickerSection("默认", listOf("❤️"))),
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
}
