package cc.hhhl.client.presentation

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.ui.component.richTextPreviewPlainText

fun notificationLineText(notification: NotificationItem): String {
    val previewText = notification.text.toRichTextPreviewText()
    return listOf(notification.actor.displayName, previewText)
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
}

fun chatMessageBodyText(message: ChatMessage): String {
    return message.text
        .toRichTextPreviewText()
        .takeIf { it.isNotBlank() }
        ?: message.file?.name?.trim().takeIf { !it.isNullOrEmpty() }
        ?: "[附件消息]"
}

fun notePreviewText(
    note: Note,
    fallback: String = "无文本内容",
): String {
    return notePreviewText(
        text = note.text,
        cw = note.cw,
        fallback = fallback,
    )
}

fun notePreviewText(
    text: String,
    cw: String?,
    fallback: String = "无文本内容",
): String {
    return cw.toRichTextPreviewText().takeIf { it.isNotBlank() }
        ?: text.toRichTextPreviewText().takeIf { it.isNotBlank() }
        ?: fallback
}

fun richTextPlainPreviewText(text: String?): String {
    return text.toRichTextPreviewText()
}

private fun String?.toRichTextPreviewText(): String {
    val clean = this?.trim().orEmpty()
    if (clean.isEmpty()) return ""
    return richTextPreviewPlainText(clean).ifBlank { clean }
}

fun String.truncateRichTextPreviewText(maxChars: Int?): String {
    val limit = maxChars?.takeIf { it > 0 } ?: return this
    if (length <= limit) return this
    val safeLimit = safeRichTextPreviewCutIndex(limit).coerceIn(0, limit)
    return take(safeLimit).trimEnd() + "..."
}

private fun String.safeRichTextPreviewCutIndex(limit: Int): Int {
    val cut = limit.coerceIn(0, length)
    openMfmTokenStartAtCut(cut)?.let { return it }
    val scanStart = (cut - MAX_RICH_TEXT_PREVIEW_BACKTRACK_CHARS).coerceAtLeast(0)
    var index = cut - 1
    while (index >= scanStart) {
        if (this[index] == '[' && markdownLinkEndAtOrAfter(index, cut)) return index
        index -= 1
    }
    val emojiStart = lastIndexOf(':', startIndex = (cut - 1).coerceAtLeast(0))
    if (
        emojiStart >= 0 &&
        cut - emojiStart <= MAX_RICH_TEXT_PREVIEW_BACKTRACK_CHARS &&
        isPotentialCustomEmojiStart(emojiStart)
    ) {
        val emojiEnd = indexOf(':', startIndex = emojiStart + 1)
        if (emojiEnd < 0 || emojiEnd >= cut) return emojiStart
    }
    return cut
}

private fun String.openMfmTokenStartAtCut(cut: Int): Int? {
    val scanStart = (cut - MAX_RICH_TEXT_PREVIEW_MFM_BACKTRACK_CHARS).coerceAtLeast(0)
    val stack = mutableListOf<PreviewMfmToken>()
    var index = scanStart
    var quote: Char? = null
    var escaped = false
    while (index < cut) {
        val char = this[index]
        if (stack.isNotEmpty()) {
            if (escaped) {
                escaped = false
                index += 1
                continue
            }
            val activeQuote = quote
            if (activeQuote != null) {
                when (char) {
                    '\\' -> escaped = true
                    activeQuote -> quote = null
                }
                index += 1
                continue
            }
            if (char == '"' || char == '\'') {
                quote = char
                index += 1
                continue
            }
        } else {
            escaped = false
            quote = null
        }
        when {
            startsWith("$[", index) -> {
                stack.add(PreviewMfmToken(start = index, closeChar = ']'))
                if (stack.size > MAX_RICH_TEXT_PREVIEW_MFM_NESTING_DEPTH) return stack.first().start
                index += 2
                continue
            }
            startsWith("\${", index) -> {
                stack.add(PreviewMfmToken(start = index, closeChar = '}'))
                if (stack.size > MAX_RICH_TEXT_PREVIEW_MFM_NESTING_DEPTH) return stack.first().start
                index += 2
                continue
            }
        }
        if (stack.isNotEmpty() && char == stack.last().closeChar) {
            stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) {
                escaped = false
                quote = null
            }
        }
        index += 1
    }
    return stack.firstOrNull()?.start
}

private data class PreviewMfmToken(
    val start: Int,
    val closeChar: Char,
)

private fun String.markdownLinkEndAtOrAfter(start: Int, cut: Int): Boolean {
    val labelEnd = findMarkdownLinkLabelEnd(start) ?: return false
    if (getOrNull(labelEnd + 1) != '(') return false
    val urlEnd = findMarkdownLinkUrlEnd(labelEnd + 2, cut) ?: return false
    return urlEnd >= cut
}

private fun String.findMarkdownLinkLabelEnd(start: Int): Int? {
    var index = start + 1
    var escaped = false
    var depth = 0
    while (index < length && index - start <= MAX_MARKDOWN_LINK_LABEL_CHARS) {
        val char = this[index]
        if (escaped) {
            escaped = false
            index += 1
            continue
        }
        when (char) {
            '\\' -> escaped = true
            '[' -> depth += 1
            ']' -> {
                if (depth == 0) return index
                depth -= 1
            }
        }
        index += 1
    }
    return null
}

private fun String.findMarkdownLinkUrlEnd(start: Int, cut: Int): Int? {
    var index = start
    var escaped = false
    var depth = 0
    val scanEnd = minOf(length, cut + MAX_RICH_TEXT_PREVIEW_BACKTRACK_CHARS, start + MAX_MARKDOWN_LINK_URL_CHARS + 1)
    while (index < scanEnd) {
        val char = this[index]
        if (escaped) {
            escaped = false
            index += 1
            continue
        }
        when (char) {
            '\\' -> escaped = true
            '(' -> depth += 1
            ')' -> {
                if (depth == 0) return index
                depth -= 1
            }
            '\n', '\r' -> return null
        }
        index += 1
    }
    return null
}

private fun String.isPotentialCustomEmojiStart(index: Int): Boolean {
    val next = getOrNull(index + 1) ?: return false
    return next.isLetterOrDigit() || next == '_' || next == '-' || next == '.' || next == '@'
}

private const val MAX_RICH_TEXT_PREVIEW_BACKTRACK_CHARS = 96
private const val MAX_RICH_TEXT_PREVIEW_MFM_BACKTRACK_CHARS = 4_096
private const val MAX_RICH_TEXT_PREVIEW_MFM_NESTING_DEPTH = 16
private const val MAX_MARKDOWN_LINK_LABEL_CHARS = 256
private const val MAX_MARKDOWN_LINK_URL_CHARS = 2_048
