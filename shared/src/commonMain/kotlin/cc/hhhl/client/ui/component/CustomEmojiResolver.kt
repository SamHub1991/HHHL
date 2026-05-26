package cc.hhhl.client.ui.component

import androidx.compose.runtime.compositionLocalOf
import cc.hhhl.client.model.CustomEmoji

val LocalCustomEmojiUrls = compositionLocalOf<Map<String, String>> { emptyMap() }

sealed interface CustomEmojiTextSegment {
    data class Text(val value: String) : CustomEmojiTextSegment

    data class Emoji(
        val code: String,
        val url: String,
    ) : CustomEmojiTextSegment
}

fun customEmojiUrlMap(emojis: List<CustomEmoji>): Map<String, String> {
    return emojis
        .asSequence()
        .filter { !it.isSensitive }
        .filter { it.name.isNotBlank() && it.url.isNotBlank() }
        .flatMap { emoji ->
            sequenceOf(
                ":${emoji.name}:" to emoji.url,
                ":${emoji.name}@.:" to emoji.url,
            )
        }
        .distinctBy { it.first }
        .toMap()
}

internal data class CustomEmojiMatch(
    val code: String,
    val url: String,
    val end: Int,
)

fun parseCustomEmojiText(
    text: String,
    emojiUrls: Map<String, String>,
): List<CustomEmojiTextSegment> {
    if (text.isEmpty() || emojiUrls.isEmpty()) {
        return listOf(CustomEmojiTextSegment.Text(text))
    }

    val segments = mutableListOf<CustomEmojiTextSegment>()
    var index = 0
    while (index < text.length) {
        val start = text.indexOf(':', startIndex = index)
        if (start < 0) {
            segments.addText(text.substring(index))
            break
        }
        if (start > index) {
            segments.addText(text.substring(index, start))
        }
        val emoji = parseCustomEmojiMatch(text, start, emojiUrls)
        if (emoji == null) {
            val end = text.indexOf(':', startIndex = start + 1)
            if (end < 0) {
                segments.addText(text.substring(start))
                break
            }
            segments.addText(text.substring(start, end + 1))
            index = end + 1
        } else {
            segments.add(CustomEmojiTextSegment.Emoji(code = emoji.code, url = emoji.url))
            index = emoji.end
        }
    }
    return segments.ifEmpty { listOf(CustomEmojiTextSegment.Text("")) }
}

internal fun parseCustomEmojiMatch(
    text: String,
    start: Int,
    emojiUrls: Map<String, String>,
): CustomEmojiMatch? {
    if (emojiUrls.isEmpty() || text.getOrNull(start) != ':') return null
    var end = start + 1
    while (end < text.length && end - start <= MaxCustomEmojiCodeLength) {
        val current = text[end]
        if (current == ':') {
            if (end == start + 1) return null
            val code = text.substring(start, end + 1)
            val url = emojiUrls[code] ?: return null
            return CustomEmojiMatch(code = code, url = url, end = end + 1)
        }
        if (!current.isCustomEmojiCodeChar()) return null
        end += 1
    }
    return null
}

private fun Char.isCustomEmojiCodeChar(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-' || this == '.' || this == '@'
}

private const val MaxCustomEmojiCodeLength = 96

private fun MutableList<CustomEmojiTextSegment>.addText(value: String) {
    if (value.isEmpty()) return
    val last = lastOrNull()
    if (last is CustomEmojiTextSegment.Text) {
        this[lastIndex] = last.copy(value = last.value + value)
    } else {
        add(CustomEmojiTextSegment.Text(value))
    }
}
