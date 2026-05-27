package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import coil3.compose.AsyncImage
import cc.hhhl.client.theme.LocalHhhlColors

@Composable
fun InlineRichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onOpenUrl: (String) -> Unit = {},
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
) {
    val emojiUrls = LocalCustomEmojiUrls.current
    if (canRenderPlainRichTextFastPath(text, emojiUrls)) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            style = style,
        )
        return
    }

    val blocks = remember(text) {
        cachedMarkdownBlocks(text)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> InlineMarkdownFlow(
                    spans = block.spans,
                    emojiUrls = emojiUrls,
                    style = style,
                    color = color,
                    accentColor = accentColor,
                    onOpenUrl = onOpenUrl,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                )
                is MarkdownBlock.Quote -> InlineQuoteBlock(
                    block = block,
                    emojiUrls = emojiUrls,
                    style = style,
                    color = color,
                    accentColor = accentColor,
                    onOpenUrl = onOpenUrl,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                )
                is MarkdownBlock.Code -> InlineCodeBlock(
                    text = block.value,
                    style = style,
                )
                is MarkdownBlock.Center -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    InlineMarkdownFlow(
                        spans = block.spans,
                        emojiUrls = emojiUrls,
                        style = style,
                        color = color,
                        accentColor = accentColor,
                        onOpenUrl = onOpenUrl,
                        onOpenMention = onOpenMention,
                        onOpenHashtag = onOpenHashtag,
                    )
                }
            }
        }
    }
}

@Composable
fun InlineCustomEmojiText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    InlineRichText(
        text = text,
        modifier = modifier,
        style = style,
    )
}

@Composable
private fun InlinePlainText(
    text: String,
    style: TextStyle,
    color: Color,
    markdownStyle: InlineMarkdownStyle = InlineMarkdownStyle.Plain,
) {
    if (markdownStyle == InlineMarkdownStyle.Rainbow) {
        InlineRainbowText(text = text, style = style)
        return
    }
    Text(
        text = text,
        color = if (markdownStyle == InlineMarkdownStyle.Blurry) color.copy(alpha = 0.48f) else color,
        style = style.markdown(markdownStyle),
    )
}

@Composable
private fun InlineRainbowText(
    text: String,
    style: TextStyle,
) {
    val colors = remember {
        listOf(
            Color(0xFFFF2D2D),
            Color(0xFFFF8A00),
            Color(0xFFFFD400),
            Color(0xFF3FE000),
            Color(0xFF00A7FF),
            Color(0xFF7A5CFF),
            Color(0xFFFF4FD8),
        )
    }
    Text(
        text = buildAnnotatedString {
            text.forEachIndexed { index, char ->
                withStyle(SpanStyle(color = colors[index % colors.size])) {
                    append(char)
                }
            }
        },
        style = style.markdown(InlineMarkdownStyle.Rainbow),
    )
}

@Composable
private fun InlineActionText(
    text: String,
    style: TextStyle,
    accentColor: Color,
    markdownStyle: InlineMarkdownStyle = InlineMarkdownStyle.Plain,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = accentColor,
        style = style.markdown(markdownStyle),
        modifier = Modifier.clickable { onClick() },
    )
}

@Composable
private fun InlineMarkdownFlow(
    spans: List<InlineMarkdownSpan>,
    emojiUrls: Map<String, String>,
    style: TextStyle,
    color: Color,
    accentColor: Color,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        spans.forEach { span ->
            when (span) {
                is InlineMarkdownSpan.Text -> {
                    if (span.richTextEnabled && needsRichTextParsing(span.value, emojiUrls)) {
                        val segments = remember(span.value, emojiUrls) {
                            if (emojiUrls.isEmpty()) {
                                cachedRichTextSegmentsWithoutEmoji(span.value)
                            } else {
                                parseRichText(text = span.value, emojiUrls = emojiUrls)
                            }
                        }
                        InlineRichSegments(
                            segments = segments,
                            style = style,
                            color = color,
                            accentColor = accentColor,
                            markdownStyle = span.style,
                            onOpenUrl = onOpenUrl,
                            onOpenMention = onOpenMention,
                            onOpenHashtag = onOpenHashtag,
                        )
                    } else {
                        InlinePlainText(span.value, style, color, span.style)
                    }
                }
                is InlineMarkdownSpan.Link -> InlineActionText(
                    text = span.label,
                    style = style,
                    accentColor = accentColor,
                    markdownStyle = InlineMarkdownStyle.Link,
                    onClick = { onOpenUrl(span.url) },
                )
                is InlineMarkdownSpan.Ruby -> InlineRubyText(
                    span = span,
                    style = style,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun InlineRubyText(
    span: InlineMarkdownSpan.Ruby,
    style: TextStyle,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = span.annotation,
            color = color.copy(alpha = 0.72f),
            style = style.markdown(InlineMarkdownStyle.Small),
            maxLines = 1,
        )
        Text(
            text = span.base,
            color = color,
            style = style,
            maxLines = 1,
        )
    }
}

@Composable
private fun InlineRichSegments(
    segments: List<RichTextSegment>,
    style: TextStyle,
    color: Color,
    accentColor: Color,
    markdownStyle: InlineMarkdownStyle,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    segments.forEach { segment ->
        when (segment) {
            is RichTextSegment.Text -> InlinePlainText(segment.value, style, color, markdownStyle)
            is RichTextSegment.Emoji -> InlineRichEmojiImage(segment)
            is RichTextSegment.Url -> InlineActionText(
                text = segment.value,
                style = style,
                accentColor = accentColor,
                markdownStyle = markdownStyle,
                onClick = { onOpenUrl(segment.value) },
            )
            is RichTextSegment.Mention -> InlineActionText(
                text = segment.value,
                style = style,
                accentColor = accentColor,
                markdownStyle = markdownStyle,
                onClick = { onOpenMention(segment.username) },
            )
            is RichTextSegment.Hashtag -> InlineActionText(
                text = segment.value,
                style = style,
                accentColor = accentColor,
                markdownStyle = markdownStyle,
                onClick = { onOpenHashtag(segment.tag) },
            )
        }
    }
}

@Composable
private fun InlineQuoteBlock(
    block: MarkdownBlock.Quote,
    emojiUrls: Map<String, String>,
    style: TextStyle,
    color: Color,
    accentColor: Color,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    val filledBubbleTone = accentColor == color
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (filledBubbleTone) {
                    color.copy(alpha = 0.12f)
                } else {
                    LocalHhhlColors.current.mediaBackground
                },
            )
            .border(
                width = 1.dp,
                color = if (filledBubbleTone) {
                    color.copy(alpha = 0.14f)
                } else {
                    LocalHhhlColors.current.divider
                },
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        InlineMarkdownFlow(
            spans = block.spans,
            emojiUrls = emojiUrls,
            style = style,
            color = color,
            accentColor = accentColor,
            onOpenUrl = onOpenUrl,
            onOpenMention = onOpenMention,
            onOpenHashtag = onOpenHashtag,
        )
    }
}

@Composable
private fun InlineCodeBlock(
    text: String,
    style: TextStyle,
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = style.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(1.dp, LocalHhhlColors.current.divider.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun InlineRichEmojiImage(segment: RichTextSegment.Emoji) {
    var imageLoaded by remember(segment.url) { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = segment.url,
            contentDescription = segment.code,
            contentScale = ContentScale.Fit,
            onSuccess = { imageLoaded = true },
            onError = { imageLoaded = false },
            modifier = Modifier.fillMaxSize(),
        )
        if (!imageLoaded) {
            Text(
                text = segment.code,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Paragraph(val spans: List<InlineMarkdownSpan>) : MarkdownBlock
    data class Quote(val spans: List<InlineMarkdownSpan>) : MarkdownBlock
    data class Code(val value: String) : MarkdownBlock
    data class Center(val spans: List<InlineMarkdownSpan>) : MarkdownBlock
}

internal sealed interface InlineMarkdownSpan {
    data class Text(
        val value: String,
        val style: InlineMarkdownStyle = InlineMarkdownStyle.Plain,
        val richTextEnabled: Boolean = true,
    ) : InlineMarkdownSpan

    data class Link(
        val label: String,
        val url: String,
    ) : InlineMarkdownSpan

    data class Ruby(
        val base: String,
        val annotation: String,
    ) : InlineMarkdownSpan
}

internal enum class InlineMarkdownStyle {
    Plain,
    Bold,
    Italic,
    Small,
    Sub,
    Sup,
    X2,
    X3,
    X4,
    Code,
    Link,
    Blurry,
    Rainbow,
}

internal fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isEmpty()) return listOf(MarkdownBlock.Paragraph(listOf(InlineMarkdownSpan.Text(""))))

    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    val quote = StringBuilder()
    val code = StringBuilder()
    var inCodeBlock = false

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(parseInlineMarkdown(paragraph.toString())))
            paragraph.clear()
        }
    }
    fun flushQuote() {
        if (quote.isNotEmpty()) {
            blocks.add(MarkdownBlock.Quote(parseInlineMarkdown(quote.toString().trimEnd('\n'))))
            quote.clear()
        }
    }

    text.lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.Code(code.toString().trimEnd('\n')))
                code.clear()
                inCodeBlock = false
            } else {
                flushParagraph()
                flushQuote()
                inCodeBlock = true
            }
            return@forEach
        }
        if (inCodeBlock) {
            code.append(line).append('\n')
            return@forEach
        }
        parseStaticLineBlock(line)?.let { lineBlock ->
            flushParagraph()
            flushQuote()
            blocks.add(lineBlock)
            return@forEach
        }
        if (trimmed.startsWith(">")) {
            flushParagraph()
            quote.append(trimmed.removePrefix(">").trimStart()).append('\n')
            return@forEach
        }
        flushQuote()
        if (line.isBlank()) {
            flushParagraph()
        } else {
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(line)
        }
    }

    if (inCodeBlock) {
        blocks.add(MarkdownBlock.Code(code.toString().trimEnd('\n')))
    }
    flushQuote()
    flushParagraph()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(listOf(InlineMarkdownSpan.Text("")))) }
}

private fun cachedMarkdownBlocks(text: String): List<MarkdownBlock> = parseMarkdownBlocks(text)

private fun cachedRichTextSegmentsWithoutEmoji(text: String): List<RichTextSegment> {
    return parseRichText(text = text, emojiUrls = emptyMap())
}

internal fun parseInlineMarkdown(text: String): List<InlineMarkdownSpan> {
    if (text.isEmpty()) return listOf(InlineMarkdownSpan.Text(""))

    val spans = mutableListOf<InlineMarkdownSpan>()
    var index = 0
    while (index < text.length) {
        val codeEnd = if (text[index] == '`') text.indexOf('`', startIndex = index + 1) else -1
        if (codeEnd > index + 1) {
            spans.add(InlineMarkdownSpan.Text(text.substring(index + 1, codeEnd), InlineMarkdownStyle.Code))
            index = codeEnd + 1
            continue
        }

        val link = parseMarkdownLink(text, index)
        if (link != null) {
            spans.add(link.span)
            index = link.end
            continue
        }

        val htmlSpan = parseInlineHtmlSpan(text, index)
        if (htmlSpan != null) {
            spans.add(htmlSpan.span)
            index = htmlSpan.end
            continue
        }

        val mfmSpan = parseMfmSpan(text, index)
        if (mfmSpan != null) {
            spans.add(mfmSpan.span)
            index = mfmSpan.end
            continue
        }

        val marker = markdownMarkerAt(text, index)
        if (marker != null) {
            val end = findClosingMarkdownMarker(text, marker, index + marker.token.length)
            if (end > index + marker.token.length) {
                spans.add(
                    InlineMarkdownSpan.Text(
                        value = text.substring(index + marker.token.length, end),
                        style = marker.style,
                    ),
                )
                index = end + marker.token.length
                continue
            }
        }

        val next = nextMarkdownSpecial(text, index + 1).takeIf { it >= 0 } ?: text.length
        spans.add(InlineMarkdownSpan.Text(text.substring(index, next)))
        index = next
    }
    return spans.mergeAdjacentText()
}

private data class ParsedMarkdownLink(
    val span: InlineMarkdownSpan.Link,
    val end: Int,
)

private fun parseMarkdownLink(text: String, start: Int): ParsedMarkdownLink? {
    if (text[start] != '[') return null
    val labelEnd = text.indexOf("](", startIndex = start + 1)
    if (labelEnd <= start + 1) return null
    val urlStart = labelEnd + 2
    val urlEnd = text.indexOf(')', startIndex = urlStart)
    if (urlEnd <= urlStart) return null
    val url = text.substring(urlStart, urlEnd)
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    return ParsedMarkdownLink(
        span = InlineMarkdownSpan.Link(
            label = text.substring(start + 1, labelEnd),
            url = url,
        ),
        end = urlEnd + 1,
    )
}

private data class MarkdownMarker(
    val token: String,
    val style: InlineMarkdownStyle,
)

private fun markdownMarkerAt(text: String, index: Int): MarkdownMarker? {
    return when {
        text.startsWith("**", index) -> MarkdownMarker("**", InlineMarkdownStyle.Bold)
        text[index] == '*' && !text.startsWith("**", index) && hasMarkdownMarkerBoundary(text, index) -> {
            MarkdownMarker("*", InlineMarkdownStyle.Italic)
        }
        text[index] == '_' && hasMarkdownMarkerBoundary(text, index) -> MarkdownMarker("_", InlineMarkdownStyle.Italic)
        else -> null
    }
}

private fun findClosingMarkdownMarker(text: String, marker: MarkdownMarker, start: Int): Int {
    var index = text.indexOf(marker.token, startIndex = start)
    while (index >= 0) {
        if (marker.token == "**" || hasMarkdownMarkerBoundary(text, index)) return index
        index = text.indexOf(marker.token, startIndex = index + marker.token.length)
    }
    return -1
}

private fun hasMarkdownMarkerBoundary(text: String, index: Int): Boolean {
    val previous = text.getOrNull(index - 1)
    val next = text.getOrNull(index + 1)
    return !(previous?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true)
}

private fun nextMarkdownSpecial(text: String, start: Int): Int {
    var index = start
    while (index < text.length) {
        if (
            text[index] == '`' ||
            text[index] == '[' ||
            text[index] == '*' ||
            text[index] == '_' ||
            text[index] == '<' ||
            text.startsWith("$[", index)
        ) {
            return index
        }
        index += 1
    }
    return -1
}

private data class ParsedStyledSpan(
    val span: InlineMarkdownSpan,
    val end: Int,
)

private data class HtmlInlineRule(
    val tag: String,
    val style: InlineMarkdownStyle,
    val richTextEnabled: Boolean = true,
    val textTransform: (String) -> String = ::htmlInlineText,
)

private val htmlInlineRules = listOf(
    HtmlInlineRule("small", InlineMarkdownStyle.Small),
    HtmlInlineRule("sub", InlineMarkdownStyle.Sub),
    HtmlInlineRule("sup", InlineMarkdownStyle.Sup),
    HtmlInlineRule("b", InlineMarkdownStyle.Bold),
    HtmlInlineRule("strong", InlineMarkdownStyle.Bold),
    HtmlInlineRule("i", InlineMarkdownStyle.Italic),
    HtmlInlineRule("em", InlineMarkdownStyle.Italic),
    HtmlInlineRule("code", InlineMarkdownStyle.Code),
    HtmlInlineRule("span", InlineMarkdownStyle.Plain),
    HtmlInlineRule("p", InlineMarkdownStyle.Plain, textTransform = { htmlInlineText(it).trim() }),
    HtmlInlineRule("plain", InlineMarkdownStyle.Plain, richTextEnabled = false),
    HtmlInlineRule("blur", InlineMarkdownStyle.Blurry),
    HtmlInlineRule("blurry", InlineMarkdownStyle.Blurry),
).associateBy { it.tag }

private fun parseInlineHtmlSpan(text: String, start: Int): ParsedStyledSpan? {
    if (text[start] != '<') return null
    val tagEnd = text.indexOf('>', startIndex = start + 1)
    if (tagEnd <= start + 1) return null
    val rawTag = text.substring(start + 1, tagEnd).trim()
    val normalizedTag = rawTag.lowercase()
    if (normalizedTag.startsWith("/")) return null
    parseHtmlImageAlt(rawTag)?.let { alt ->
        return ParsedStyledSpan(InlineMarkdownSpan.Text(alt), tagEnd + 1)
    }
    val tag = normalizedTag.substringBefore(' ').removeSuffix("/")
    if (tag == "br") {
        return ParsedStyledSpan(InlineMarkdownSpan.Text("\n"), tagEnd + 1)
    }
    if (tag == "a") {
        val url = parseHtmlAttribute(rawTag, "href") ?: return null
        if (!url.isSafeUrl()) return null
        val closeToken = "</a>"
        val closeStart = text.indexOf(closeToken, startIndex = tagEnd + 1, ignoreCase = true)
        if (closeStart < 0) return null
        return ParsedStyledSpan(
            span = InlineMarkdownSpan.Link(
                label = htmlInlineText(text.substring(tagEnd + 1, closeStart)),
                url = url,
            ),
            end = closeStart + closeToken.length,
        )
    }
    if (tag == "ruby") {
        parseHtmlRuby(text, tagEnd + 1)?.let { ruby ->
            return ParsedStyledSpan(ruby.span, ruby.end)
        }
    }
    val rule = htmlInlineRules[tag] ?: return null
    if (normalizedTag.endsWith("/")) {
        return ParsedStyledSpan(InlineMarkdownSpan.Text(""), tagEnd + 1)
    }
    val closeToken = "</$tag>"
    val closeStart = text.indexOf(closeToken, startIndex = tagEnd + 1, ignoreCase = true)
    if (closeStart < 0) return null
    return ParsedStyledSpan(
        span = InlineMarkdownSpan.Text(
            value = rule.textTransform(text.substring(tagEnd + 1, closeStart)),
            style = rule.style,
            richTextEnabled = rule.richTextEnabled,
        ),
        end = closeStart + closeToken.length,
    )
}

private fun parseMfmSpan(text: String, start: Int): ParsedStyledSpan? {
    val function = parseMfmFunction(text, start) ?: return null
    val rawName = function.rawName
    val name = function.name
    val value = function.value
    if (name == "ruby") {
        val parts = value.split(' ', limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return ParsedStyledSpan(
            span = InlineMarkdownSpan.Ruby(parts[0], parts[1]),
            end = function.end,
        )
    }
    if (name == "link") {
        val link = parseMfmLinkValue(value) ?: return null
        return ParsedStyledSpan(
            span = InlineMarkdownSpan.Link(link.label, link.url),
            end = function.end,
        )
    }
    val style = when (name) {
        "small" -> InlineMarkdownStyle.Small
        "x2" -> InlineMarkdownStyle.X2
        "x3" -> InlineMarkdownStyle.X3
        "x4" -> InlineMarkdownStyle.X4
        "scale" -> parseMfmScaleStyle(rawName)
        "blur", "blurry" -> InlineMarkdownStyle.Blurry
        "rainbow" -> InlineMarkdownStyle.Rainbow
        "font", "fg", "bg", "jelly", "tada", "jump", "bounce", "spin", "shake", "twitch" -> {
            InlineMarkdownStyle.Plain
        }
        "plain" -> InlineMarkdownStyle.Plain
        else -> return null
    }
    return ParsedStyledSpan(
        span = InlineMarkdownSpan.Text(
            value = value.unwrapSingleMfmFunctions(),
            style = style,
            richTextEnabled = name != "plain",
        ),
        end = function.end,
    )
}

private data class MfmFunction(
    val rawName: String,
    val name: String,
    val value: String,
    val end: Int,
)

private fun parseMfmFunction(text: String, start: Int): MfmFunction? {
    if (!text.startsWith("$[", start)) return null
    val closeIndex = findMfmCloseIndex(text, start)
    if (closeIndex <= start + 3) return null
    val body = text.substring(start + 2, closeIndex)
    val separatorIndex = body.indexOfFirst { it.isWhitespace() }
    if (separatorIndex <= 0 || separatorIndex == body.lastIndex) return null
    val rawName = body.substring(0, separatorIndex).lowercase()
    val name = rawName.substringBefore('.')
    val value = body.substring(separatorIndex + 1).trim()
    if (name.isBlank() || value.isBlank()) return null
    return MfmFunction(
        rawName = rawName,
        name = name,
        value = value,
        end = closeIndex + 1,
    )
}

private fun findMfmCloseIndex(text: String, start: Int): Int {
    var depth = 1
    var index = start + 2
    while (index < text.length) {
        if (text.startsWith("$[", index)) {
            depth += 1
            index += 2
            continue
        }
        if (text[index] == ']') {
            depth -= 1
            if (depth == 0) return index
        }
        index += 1
    }
    return -1
}

private fun String.unwrapSingleMfmFunctions(): String {
    var value = trim()
    while (value.startsWith("$[")) {
        val function = parseMfmFunction(value, 0) ?: break
        if (function.end != value.length) break
        value = function.value.trim()
    }
    return value
}

private fun parseStaticLineBlock(line: String): MarkdownBlock? {
    val trimmed = line.trim()
    parseWrappedHtmlLine(trimmed, "center")?.let {
        return MarkdownBlock.Center(parseInlineMarkdown(it))
    }
    parseWrappedHtmlLine(trimmed, "blockquote")?.let {
        return MarkdownBlock.Quote(parseInlineMarkdown(it))
    }
    parseWrappedHtmlLine(trimmed, "p")?.let {
        return MarkdownBlock.Paragraph(parseInlineMarkdown(it))
    }
    parseWrappedHtmlLine(trimmed, "quote")?.let {
        return MarkdownBlock.Quote(parseInlineMarkdown(it))
    }
    parseWrappedHtmlLine(trimmed, "pre")?.let {
        return MarkdownBlock.Code(stripSingleWrappedHtmlTag(it, "code") ?: it)
    }
    if (trimmed.startsWith("$[center ") && trimmed.endsWith("]")) {
        return MarkdownBlock.Center(parseInlineMarkdown(trimmed.removePrefix("$[center ").dropLast(1)))
    }
    if (trimmed.startsWith("$[quote ") && trimmed.endsWith("]")) {
        return MarkdownBlock.Quote(parseInlineMarkdown(trimmed.removePrefix("$[quote ").dropLast(1)))
    }
    if (trimmed.startsWith("$[code ") && trimmed.endsWith("]")) {
        return MarkdownBlock.Code(trimmed.removePrefix("$[code ").dropLast(1))
    }
    return null
}

private fun parseHtmlRuby(text: String, innerStart: Int): ParsedStyledSpan? {
    val closeToken = "</ruby>"
    val closeStart = text.indexOf(closeToken, startIndex = innerStart, ignoreCase = true)
    if (closeStart < 0) return null
    val inner = text.substring(innerStart, closeStart)
    val rtOpen = inner.indexOf("<rt>", ignoreCase = true)
    val rtClose = inner.indexOf("</rt>", ignoreCase = true)
    if (rtOpen <= 0 || rtClose <= rtOpen) return null
    val base = htmlInlineText(inner.substring(0, rtOpen).removeRubyFallbackTags())
    val annotation = htmlInlineText(inner.substring(rtOpen + "<rt>".length, rtClose).removeRubyFallbackTags())
    if (base.isBlank() || annotation.isBlank()) return null
    return ParsedStyledSpan(
        span = InlineMarkdownSpan.Ruby(base, annotation),
        end = closeStart + closeToken.length,
    )
}

private fun parseHtmlImageAlt(rawTag: String): String? {
    val tag = rawTag.lowercase().substringBefore(' ').removeSuffix("/")
    if (tag != "img") return null
    return (parseHtmlAttribute(rawTag, "alt") ?: parseHtmlAttribute(rawTag, "title"))?.decodeHtmlEntities()
}

private fun parseHtmlAttribute(rawTag: String, name: String): String? {
    val pattern = when (name) {
        "alt" -> htmlAltAttributePattern
        "href" -> htmlHrefAttributePattern
        "title" -> htmlTitleAttributePattern
        else -> Regex("""(?i)\b${Regex.escape(name)}\s*=\s*(['"])(.*?)\1""")
    }
    return pattern.find(rawTag)?.groupValues?.getOrNull(2)
}

private fun parseWrappedHtmlLine(text: String, tag: String): String? {
    val openPattern = wrappedHtmlOpenPatterns[tag]
        ?: Regex("""(?i)^<${Regex.escape(tag)}(?:\s+[^>]*)?>""")
    val open = openPattern.find(text) ?: return null
    val closeToken = "</$tag>"
    if (!text.endsWith(closeToken, ignoreCase = true)) return null
    return text.substring(open.range.last + 1, text.length - closeToken.length)
}

private fun htmlInlineText(value: String): String {
    return value
        .replace(htmlBreakPattern, "\n")
        .replace(htmlSpanPattern, "")
        .replace(htmlParagraphPattern, "\n")
        .replace(htmlAnyTagPattern, "")
        .decodeHtmlEntities()
}

private fun stripSingleWrappedHtmlTag(text: String, tag: String): String? {
    return parseWrappedHtmlLine(text.trim(), tag)
}

private fun parseMfmScaleStyle(rawName: String): InlineMarkdownStyle {
    val options = rawName.substringAfter('.', missingDelimiterValue = "")
    val scale = mfmScaleOptionPattern
        .findAll(options)
        .mapNotNull { it.groupValues.getOrNull(1)?.toFloatOrNull() }
        .maxOrNull()
    return when {
        scale == null || scale < 1.25f -> InlineMarkdownStyle.Plain
        scale < 2.5f -> InlineMarkdownStyle.X2
        scale < 3.5f -> InlineMarkdownStyle.X3
        else -> InlineMarkdownStyle.X4
    }
}

private data class MfmLinkValue(
    val label: String,
    val url: String,
)

private fun parseMfmLinkValue(value: String): MfmLinkValue? {
    val urlFirstParts = value.split(' ', limit = 2)
    if (urlFirstParts.size == 2 && urlFirstParts[0].isSafeUrl() && urlFirstParts[1].isNotBlank()) {
        return MfmLinkValue(label = urlFirstParts[1], url = urlFirstParts[0])
    }
    val lastSpace = value.lastIndexOf(' ')
    if (lastSpace <= 0 || lastSpace == value.lastIndex) return null
    val label = value.substring(0, lastSpace)
    val url = value.substring(lastSpace + 1)
    if (!url.isSafeUrl() || label.isBlank()) return null
    return MfmLinkValue(label = label, url = url)
}

private fun String.isSafeUrl(): Boolean {
    return startsWith("https://") || startsWith("http://")
}

private fun String.removeRubyFallbackTags(): String {
    return replace(htmlRubyFallbackPattern, "")
}

private fun String.decodeHtmlEntities(): String {
    return replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}

private fun List<InlineMarkdownSpan>.mergeAdjacentText(): List<InlineMarkdownSpan> {
    val merged = mutableListOf<InlineMarkdownSpan>()
    forEach { span ->
        val last = merged.lastOrNull()
        if (
            span is InlineMarkdownSpan.Text &&
            last is InlineMarkdownSpan.Text &&
            last.style == span.style &&
            last.richTextEnabled == span.richTextEnabled
        ) {
            merged[merged.lastIndex] = last.copy(value = last.value + span.value)
        } else {
            merged.add(span)
        }
    }
    return merged
}

private fun TextStyle.markdown(markdownStyle: InlineMarkdownStyle): TextStyle {
    return when (markdownStyle) {
        InlineMarkdownStyle.Plain -> this
        InlineMarkdownStyle.Bold -> copy(fontWeight = FontWeight.SemiBold)
        InlineMarkdownStyle.Italic -> copy(fontStyle = FontStyle.Italic)
        InlineMarkdownStyle.Small -> scaledText(0.86f)
        InlineMarkdownStyle.Sub -> scaledText(0.78f)
        InlineMarkdownStyle.Sup -> scaledText(0.78f)
        InlineMarkdownStyle.X2 -> scaledText(1.35f)
        InlineMarkdownStyle.X3 -> scaledText(1.7f)
        InlineMarkdownStyle.X4 -> scaledText(2.0f)
        InlineMarkdownStyle.Code -> copy(fontFamily = FontFamily.Monospace)
        InlineMarkdownStyle.Link -> copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start)
        InlineMarkdownStyle.Blurry -> copy(fontStyle = FontStyle.Italic)
        InlineMarkdownStyle.Rainbow -> copy(fontWeight = FontWeight.SemiBold)
    }
}

private fun TextStyle.scaledText(scale: Float): TextStyle {
    return if (fontSize.isSpecified) {
        copy(fontSize = fontSize * scale)
    } else {
        this
    }
}

private fun canRenderPlainRichTextFastPath(
    text: String,
    emojiUrls: Map<String, String>,
): Boolean {
    if (text.isEmpty()) return true
    var index = 0
    var lineCanStartBlock = true
    while (index < text.length) {
        val char = text[index]
        if (text.startsWith("https://", index) || text.startsWith("http://", index)) return false
        when (char) {
            '`', '[', '*', '_', '<', '@', '#' -> return false
            ':' -> if (emojiUrls.isNotEmpty()) return false
            '$' -> if (text.startsWith("$[", index)) return false
            '>' -> if (lineCanStartBlock) return false
        }
        lineCanStartBlock = char == '\n' || (lineCanStartBlock && (char == ' ' || char == '\t'))
        index += 1
    }
    return true
}

private fun needsRichTextParsing(
    text: String,
    emojiUrls: Map<String, String>,
): Boolean {
    var index = 0
    while (index < text.length) {
        if (text.startsWith("https://", index) || text.startsWith("http://", index)) return true
        when (text[index]) {
            '@', '#' -> return true
            ':' -> if (emojiUrls.isNotEmpty()) return true
        }
        index += 1
    }
    return false
}

private val htmlAltAttributePattern = Regex("""(?i)\balt\s*=\s*(['"])(.*?)\1""")
private val htmlAnyTagPattern = Regex("""<[^>]+>""")
private val htmlBreakPattern = Regex("""(?i)<br\s*/?>""")
private val htmlHrefAttributePattern = Regex("""(?i)\bhref\s*=\s*(['"])(.*?)\1""")
private val htmlParagraphPattern = Regex("""(?i)</?p(?:\s+[^>]*)?>""")
private val htmlRubyFallbackPattern = Regex("""(?i)<rp>.*?</rp>""")
private val htmlSpanPattern = Regex("""(?i)</?span(?:\s+[^>]*)?>""")
private val htmlTitleAttributePattern = Regex("""(?i)\btitle\s*=\s*(['"])(.*?)\1""")
private val mfmScaleOptionPattern = Regex("""(?:^|[.,])(?:x|y)=([0-9]+(?:\.[0-9]+)?)""")
private val wrappedHtmlOpenPatterns = listOf("blockquote", "center", "code", "p", "pre", "quote")
    .associateWith { tag -> Regex("""(?i)^<$tag(?:\s+[^>]*)?>""") }
