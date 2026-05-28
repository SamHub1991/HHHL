package cc.hhhl.client.ui.component

import cc.hhhl.client.model.CustomEmoji
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomEmojiResolverTest {
    @Test
    fun mapsLocalAndSharkeyLocalReactionCodesToEmojiUrl() {
        val urls = customEmojiUrlMap(
            listOf(
                customEmoji("blobcat", "https://dc.hhhl.cc/emoji/blobcat.webp"),
            ),
        )

        assertEquals("https://dc.hhhl.cc/emoji/blobcat.webp", urls[":blobcat:"])
        assertEquals("https://dc.hhhl.cc/emoji/blobcat.webp", urls[":blobcat@.:"])
    }

    @Test
    fun ignoresSensitiveEmojiForInlineRendering() {
        val urls = customEmojiUrlMap(
            listOf(
                customEmoji("secret", "https://dc.hhhl.cc/emoji/secret.webp", isSensitive = true),
            ),
        )

        assertNull(urls[":secret:"])
        assertNull(urls[":secret@.:"])
    }

    @Test
    fun parsesTextIntoPlainAndCustomEmojiSegments() {
        val segments = parseCustomEmojiText(
            text = "hello :blobcat: and :party@.:!",
            emojiUrls = mapOf(
                ":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp",
                ":party@.:" to "https://dc.hhhl.cc/emoji/party.webp",
            ),
        )

        assertEquals(
            listOf(
                CustomEmojiTextSegment.Text("hello "),
                CustomEmojiTextSegment.Emoji(":blobcat:", "https://dc.hhhl.cc/emoji/blobcat.webp"),
                CustomEmojiTextSegment.Text(" and "),
                CustomEmojiTextSegment.Emoji(":party@.:", "https://dc.hhhl.cc/emoji/party.webp"),
                CustomEmojiTextSegment.Text("!"),
            ),
            segments,
        )
    }

    @Test
    fun keepsUnknownOrMalformedEmojiCodesAsText() {
        val segments = parseCustomEmojiText(
            text = "keep :unknown: and :broken plus :bad space:",
            emojiUrls = mapOf(":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp"),
        )

        assertEquals(listOf(CustomEmojiTextSegment.Text("keep :unknown: and :broken plus :bad space:")), segments)
        assertTrue(segments.single() is CustomEmojiTextSegment.Text)
    }

    @Test
    fun malformedEmojiCandidateDoesNotSwallowFollowingValidEmoji() {
        val segments = parseCustomEmojiText(
            text = "bad :broken plus :blobcat: and :unknown: then :party@.:",
            emojiUrls = mapOf(
                ":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp",
                ":party@.:" to "https://dc.hhhl.cc/emoji/party.webp",
            ),
        )

        assertEquals(
            listOf(
                CustomEmojiTextSegment.Text("bad :broken plus "),
                CustomEmojiTextSegment.Emoji(":blobcat:", "https://dc.hhhl.cc/emoji/blobcat.webp"),
                CustomEmojiTextSegment.Text(" and :unknown: then "),
                CustomEmojiTextSegment.Emoji(":party@.:", "https://dc.hhhl.cc/emoji/party.webp"),
            ),
            segments,
        )
    }

    @Test
    fun parsesOnlyBoundedEmojiCodesWithValidCodeCharacters() {
        val longCode = ":${"a".repeat(120)}:"
        val segments = parseCustomEmojiText(
            text = "$longCode :blob-cat_1@.:",
            emojiUrls = mapOf(":blob-cat_1@.:" to "https://dc.hhhl.cc/emoji/blob.webp"),
        )

        assertEquals(
            listOf(
                CustomEmojiTextSegment.Text("$longCode "),
                CustomEmojiTextSegment.Emoji(":blob-cat_1@.:", "https://dc.hhhl.cc/emoji/blob.webp"),
            ),
            segments,
        )
    }

    @Test
    fun capsCustomEmojiTextSegmentsAndKeepsTail() {
        val text = buildString {
            repeat(220) { index -> append(":e$index: ") }
            append("tail")
        }
        val emojiUrls = (0 until 220).associate { index -> ":e$index:" to "https://dc.hhhl.cc/emoji/e$index.webp" }

        val segments = parseCustomEmojiText(text = text, emojiUrls = emojiUrls)

        assertTrue(segments.size <= 162)
        assertEquals(text, segments.joinToString(separator = "") { it.displayValue() })
        assertTrue(segments.last() is CustomEmojiTextSegment.Text)
    }

    private fun customEmoji(
        name: String,
        url: String,
        isSensitive: Boolean = false,
    ): CustomEmoji {
        return CustomEmoji(
            name = name,
            category = null,
            url = url,
            aliases = emptyList(),
            localOnly = true,
            isSensitive = isSensitive,
        )
    }
}

private fun CustomEmojiTextSegment.displayValue(): String {
    return when (this) {
        is CustomEmojiTextSegment.Text -> value
        is CustomEmojiTextSegment.Emoji -> code
    }
}
