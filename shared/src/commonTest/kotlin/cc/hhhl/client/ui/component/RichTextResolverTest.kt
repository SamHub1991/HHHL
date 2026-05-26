package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class RichTextResolverTest {
    @Test
    fun parsesUrlsMentionsHashtagsAndCustomEmojiIntoSegments() {
        val segments = parseRichText(
            text = "Hi @alice check https://dc.hhhl.cc/notes/abc #Sharkey :blobcat:",
            emojiUrls = mapOf(":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp"),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("Hi "),
                RichTextSegment.Mention("@alice", "alice"),
                RichTextSegment.Text(" check "),
                RichTextSegment.Url("https://dc.hhhl.cc/notes/abc"),
                RichTextSegment.Text(" "),
                RichTextSegment.Hashtag("#Sharkey", "Sharkey"),
                RichTextSegment.Text(" "),
                RichTextSegment.Emoji(":blobcat:", "https://dc.hhhl.cc/emoji/blobcat.webp"),
            ),
            segments,
        )
    }

    @Test
    fun trimsTrailingPunctuationFromUrlsAndKeepsItAsText() {
        val segments = parseRichText(
            text = "read https://dc.hhhl.cc/@alice, then @bob.",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("read "),
                RichTextSegment.Url("https://dc.hhhl.cc/@alice"),
                RichTextSegment.Text(", then "),
                RichTextSegment.Mention("@bob", "bob"),
                RichTextSegment.Text("."),
            ),
            segments,
        )
    }

    @Test
    fun parsesRemoteMentionAsSingleMention() {
        val segments = parseRichText(
            text = "hello @alice@remote.example.",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("hello "),
                RichTextSegment.Mention("@alice@remote.example", "alice@remote.example"),
                RichTextSegment.Text("."),
            ),
            segments,
        )
    }

    @Test
    fun doesNotParseMentionsOrHashtagsInsideWords() {
        val segments = parseRichText(
            text = "email a@b.com and tag#notlink but parse #ok",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("email a@b.com and tag#notlink but parse "),
                RichTextSegment.Hashtag("#ok", "ok"),
            ),
            segments,
        )
    }

    @Test
    fun parsesCustomEmojiWithSharedBoundedCandidateRules() {
        val longCode = ":${"a".repeat(120)}:"
        val segments = parseRichText(
            text = "keep $longCode then :party@.:",
            emojiUrls = mapOf(":party@.:" to "https://dc.hhhl.cc/emoji/party.webp"),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("keep $longCode then "),
                RichTextSegment.Emoji(":party@.:", "https://dc.hhhl.cc/emoji/party.webp"),
            ),
            segments,
        )
    }
}
