package cc.hhhl.client.ui.component

sealed interface RichTextSegment {
    data class Text(val value: String) : RichTextSegment

    data class Emoji(
        val code: String,
        val url: String,
    ) : RichTextSegment

    data class Url(val value: String) : RichTextSegment

    data class Mention(
        val value: String,
        val username: String,
    ) : RichTextSegment

    data class Hashtag(
        val value: String,
        val tag: String,
    ) : RichTextSegment
}

fun parseRichText(
    text: String,
    emojiUrls: Map<String, String>,
): List<RichTextSegment> {
    if (text.isEmpty()) return listOf(RichTextSegment.Text(""))

    val segments = mutableListOf<RichTextSegment>()
    var index = 0
    while (index < text.length) {
        if (segments.size >= MAX_RICH_TEXT_SEGMENTS) {
            segments.addText(text.substring(index))
            break
        }
        val match = nextRichTextMatch(text = text, startIndex = index, emojiUrls = emojiUrls)
        if (match == null) {
            segments.addText(text.substring(index))
            break
        }
        if (match.start > index) {
            segments.addText(text.substring(index, match.start))
        }
        if (segments.size >= MAX_RICH_TEXT_SEGMENTS) {
            segments.addText(text.substring(match.start))
            break
        }
        segments.add(match.segment)
        index = match.end
    }
    return segments.ifEmpty { listOf(RichTextSegment.Text("")) }
}

private const val MAX_RICH_TEXT_SEGMENTS = 160

private data class RichTextMatch(
    val start: Int,
    val end: Int,
    val segment: RichTextSegment,
)

private fun nextRichTextMatch(
    text: String,
    startIndex: Int,
    emojiUrls: Map<String, String>,
): RichTextMatch? {
    var index = startIndex
    while (index < text.length) {
        val match = when {
            text.startsWithHttpUrl(index) -> parseUrl(text, index)
            text[index] == '@' && hasTokenBoundary(text, index) -> parseMention(text, index)
            text[index] == '#' && hasTokenBoundary(text, index) -> parseHashtag(text, index)
            text[index] == ':' && emojiUrls.isNotEmpty() -> parseEmoji(text, index, emojiUrls)
            else -> null
        }
        if (match != null) return match
        index += 1
    }
    return null
}

private fun parseUrl(text: String, start: Int): RichTextMatch? {
    var end = start
    while (end < text.length && !text[end].isWhitespace() && text[end] !in urlTerminatingPunctuation) {
        end += 1
    }
    while (end > start && shouldTrimTrailingUrlChar(text, start, end)) {
        end -= 1
    }
    if (end == start) return null
    return RichTextMatch(start, end, RichTextSegment.Url(text.substring(start, end)))
}

private fun String.startsWithHttpUrl(index: Int): Boolean {
    return startsWith("https://", startIndex = index, ignoreCase = true) ||
        startsWith("http://", startIndex = index, ignoreCase = true)
}

private fun shouldTrimTrailingUrlChar(text: String, start: Int, end: Int): Boolean {
    val char = text[end - 1]
    if (char !in trailingUrlPunctuation) return false
    val opener = pairedUrlOpeners[char] ?: return true
    return text.countUrlChar(start, end, char) > text.countUrlChar(start, end, opener)
}

private fun String.countUrlChar(start: Int, end: Int, char: Char): Int {
    var count = 0
    for (index in start until end) {
        if (this[index] == char) count += 1
    }
    return count
}

private fun parseMention(text: String, start: Int): RichTextMatch? {
    val token = parseMentionToken(text, start) ?: return null
    return RichTextMatch(
        start = start,
        end = token.end,
        segment = RichTextSegment.Mention(
            value = text.substring(start, token.end),
            username = token.value,
        ),
    )
}

private fun parseMentionToken(text: String, markerIndex: Int): MarkerToken? {
    val start = markerIndex + 1
    if (start >= text.length || !text[start].isRichTokenChar()) return null
    var end = start + 1
    var hasHostSeparator = false
    while (end < text.length) {
        val current = text[end]
        if (current.isRichTokenChar()) {
            end += 1
        } else if (
            current == '@' &&
            !hasHostSeparator &&
            end + 1 < text.length &&
            text[end + 1].isRichTokenChar()
        ) {
            hasHostSeparator = true
            end += 1
        } else {
            break
        }
    }
    while (end > start && text[end - 1] in trailingTokenPunctuation) {
        end -= 1
    }
    if (end == start) return null
    return MarkerToken(value = text.substring(start, end), end = end)
}

private fun parseHashtag(text: String, start: Int): RichTextMatch? {
    val token = parseTokenAfterMarker(text, start) ?: return null
    return RichTextMatch(
        start = start,
        end = token.end,
        segment = RichTextSegment.Hashtag(
            value = text.substring(start, token.end),
            tag = token.value,
        ),
    )
}

private fun parseEmoji(
    text: String,
    start: Int,
    emojiUrls: Map<String, String>,
): RichTextMatch? {
    val emoji = parseCustomEmojiMatch(text, start, emojiUrls) ?: return null
    return RichTextMatch(start, emoji.end, RichTextSegment.Emoji(emoji.code, emoji.url))
}

private data class MarkerToken(
    val value: String,
    val end: Int,
)

private fun parseTokenAfterMarker(text: String, markerIndex: Int): MarkerToken? {
    val start = markerIndex + 1
    if (start >= text.length || !text[start].isRichTokenChar()) return null
    var end = start + 1
    while (end < text.length && text[end].isRichTokenChar()) {
        end += 1
    }
    while (end > start && text[end - 1] in trailingTokenPunctuation) {
        end -= 1
    }
    if (end == start) return null
    return MarkerToken(value = text.substring(start, end), end = end)
}

private fun hasTokenBoundary(text: String, markerIndex: Int): Boolean {
    if (markerIndex == 0) return true
    val previous = text[markerIndex - 1]
    return previous.isWhitespace() || previous in tokenLeadingBoundaryPunctuation
}

private fun Char.isRichTokenChar(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-' || this == '.'
}

private fun MutableList<RichTextSegment>.addText(value: String) {
    if (value.isEmpty()) return
    val last = lastOrNull()
    if (last is RichTextSegment.Text) {
        this[lastIndex] = last.copy(value = last.value + value)
    } else {
        add(RichTextSegment.Text(value))
    }
}

private val trailingUrlPunctuation = setOf(
    '.', ',', '!', '?', ':', ';', ')', ']', '}', '>', '"', '\'',
    '。', '，', '！', '？', '：', '；', '、', '）', '】', '》', '」', '』',
)
private val urlTerminatingPunctuation = setOf('>', '。', '，', '！', '？', '：', '；', '、', '）', '】', '》', '」', '』')
private val pairedUrlOpeners = mapOf(
    ')' to '(',
    ']' to '[',
    '}' to '{',
)
private val tokenLeadingBoundaryPunctuation = setOf(
    '(', '[', '{', '<', '"', '\'',
    '（', '【', '《', '「', '『',
)
private val trailingTokenPunctuation = setOf(
    '.', ',', '!', '?', ':', ';',
    '。', '，', '！', '？', '：', '；', '、', '）', '】', '》', '」', '』',
)
