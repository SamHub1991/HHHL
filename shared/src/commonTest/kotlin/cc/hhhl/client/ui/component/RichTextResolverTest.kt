package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun stopsUrlBeforeAdjacentEmoji() {
        val segments = parseRichText(
            text = "回显 https://dc.hhhl.cc/notes/amtbghzudk😺",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("回显 "),
                RichTextSegment.Url("https://dc.hhhl.cc/notes/amtbghzudk"),
                RichTextSegment.Text("😺"),
            ),
            segments,
        )
    }

    @Test
    fun parsesBareHttpUrlsCaseInsensitively() {
        val segments = parseRichText(
            text = "Open HTTPS://dc.hhhl.cc/docs and HTTP://dc.hhhl.cc/@alice.",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("Open "),
                RichTextSegment.Url("HTTPS://dc.hhhl.cc/docs"),
                RichTextSegment.Text(" and "),
                RichTextSegment.Url("HTTP://dc.hhhl.cc/@alice"),
                RichTextSegment.Text("."),
            ),
            segments,
        )
    }

    @Test
    fun trimsFullWidthTrailingPunctuationFromUrlsAndKeepsItAsText() {
        val segments = parseRichText(
            text = "看 https://dc.hhhl.cc/notes/abc。还有 https://dc.hhhl.cc/@alice）",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("看 "),
                RichTextSegment.Url("https://dc.hhhl.cc/notes/abc"),
                RichTextSegment.Text("。还有 "),
                RichTextSegment.Url("https://dc.hhhl.cc/@alice"),
                RichTextSegment.Text("）"),
            ),
            segments,
        )
    }

    @Test
    fun trimsAsciiAngleBracketFromUrlsAndKeepsItAsText() {
        val segments = parseRichText(
            text = "<https://dc.hhhl.cc/notes/abc> next",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("<"),
                RichTextSegment.Url("https://dc.hhhl.cc/notes/abc"),
                RichTextSegment.Text("> next"),
            ),
            segments,
        )
    }

    @Test
    fun keepsBalancedAsciiParenthesesInUrlsAndTrimsUnbalancedPunctuation() {
        val segments = parseRichText(
            text = "see https://dc.hhhl.cc/wiki/a_(b) and (https://dc.hhhl.cc/wiki/a)",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("see "),
                RichTextSegment.Url("https://dc.hhhl.cc/wiki/a_(b)"),
                RichTextSegment.Text(" and ("),
                RichTextSegment.Url("https://dc.hhhl.cc/wiki/a"),
                RichTextSegment.Text(")"),
            ),
            segments,
        )
    }

    @Test
    fun trimsTrailingQuotesFromUrlsAndKeepsThemAsText() {
        val segments = parseRichText(
            text = "open \"https://dc.hhhl.cc/notes/abc\" or 'https://dc.hhhl.cc/notes/def'",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("open \""),
                RichTextSegment.Url("https://dc.hhhl.cc/notes/abc"),
                RichTextSegment.Text("\" or '"),
                RichTextSegment.Url("https://dc.hhhl.cc/notes/def"),
                RichTextSegment.Text("'"),
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
    fun trimsFullWidthTrailingPunctuationFromMentionsAndHashtags() {
        val segments = parseRichText(
            text = "你好 @alice。聊聊（#话题）还有【@bob@remote.example】",
            emojiUrls = emptyMap(),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("你好 "),
                RichTextSegment.Mention("@alice", "alice"),
                RichTextSegment.Text("。聊聊（"),
                RichTextSegment.Hashtag("#话题", "话题"),
                RichTextSegment.Text("）还有【"),
                RichTextSegment.Mention("@bob@remote.example", "bob@remote.example"),
                RichTextSegment.Text("】"),
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

    @Test
    fun capsSegmentCountAndKeepsRemainingText() {
        val text = buildString {
            repeat(220) { index ->
                append("@user")
                append(index)
                append(' ')
            }
            append("tail")
        }

        val segments = parseRichText(text = text, emojiUrls = emptyMap())

        assertTrue(segments.size <= 162)
        assertEquals(text, segments.joinToString(separator = "") { it.displayValue() })
        assertIs<RichTextSegment.Text>(segments.last())
    }
}

private fun RichTextSegment.displayValue(): String {
    return when (this) {
        is RichTextSegment.Text -> value
        is RichTextSegment.Emoji -> code
        is RichTextSegment.Url -> value
        is RichTextSegment.Mention -> value
        is RichTextSegment.Hashtag -> value
    }
}
