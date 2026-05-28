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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified as isColorSpecified
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
import androidx.compose.ui.unit.isSpecified as isTextUnitSpecified
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import cc.hhhl.client.api.toLocalCompactDateLabel
import cc.hhhl.client.presentation.truncateRichTextPreviewText
import cc.hhhl.client.theme.LocalHhhlColors
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.datetime.Instant

@Composable
fun InlineRichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    accentColor: Color = Color.Unspecified,
    maxChars: Int? = null,
    onOpenUrl: (String) -> Unit = {},
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
) {
    val colors = LocalHhhlColors.current
    val resolvedColor = if (color.isColorSpecified) color else colors.textPrimary
    val resolvedAccentColor = if (accentColor.isColorSpecified) accentColor else colors.accent
    val emojiUrls = LocalCustomEmojiUrls.current
    val displayText = remember(text, maxChars) { text.truncateRichTextPreview(maxChars) }
    if (canRenderPlainRichTextFastPath(displayText, emojiUrls)) {
        Text(
            text = displayText,
            modifier = modifier,
            color = resolvedColor,
            style = style,
        )
        return
    }

    val blocks = remember(displayText) {
        cachedMarkdownBlocks(displayText)
    }
    val animationBudget = RichTextAnimationBudget(MAX_MFM_ANIMATED_SPANS_PER_TEXT)

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
                    color = resolvedColor,
                    accentColor = resolvedAccentColor,
                    animationBudget = animationBudget,
                    onOpenUrl = onOpenUrl,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                )
                is MarkdownBlock.Quote -> InlineQuoteBlock(
                    block = block,
                    emojiUrls = emojiUrls,
                    style = style,
                    color = resolvedColor,
                    accentColor = resolvedAccentColor,
                    animationBudget = animationBudget,
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
                        color = resolvedColor,
                        accentColor = resolvedAccentColor,
                        animationBudget = animationBudget,
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
    mfmStyle: MfmInlineStyle = MfmInlineStyle.Default,
    animationBudget: RichTextAnimationBudget? = null,
) {
    val textColor = mfmStyle.foregroundColor?.toComposeColor() ?: color
    val mfmModifier = Modifier
        .mfmTextModifier(
            mfmStyle = mfmStyle,
            textColor = textColor,
            textLength = text.length,
            animationBudget = animationBudget,
        )
    if (markdownStyle == InlineMarkdownStyle.Rainbow) {
        InlineRainbowText(text = text, style = style.markdownMfm(mfmStyle), modifier = mfmModifier)
        return
    }
    Text(
        text = text,
        color = if (markdownStyle == InlineMarkdownStyle.Blurry) {
            textColor.copy(alpha = 0.48f)
        } else {
            textColor
        },
        style = style.markdown(markdownStyle).markdownMfm(mfmStyle),
        modifier = mfmModifier,
    )
}

@Composable
private fun InlineRainbowText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current.richTextRainbowColors
    Text(
        text = buildAnnotatedString {
            text.forEachIndexed { index, char ->
                withStyle(SpanStyle(color = colors[index % colors.size])) {
                    append(char)
                }
            }
        },
        style = style.markdown(InlineMarkdownStyle.Rainbow),
        modifier = modifier,
    )
}

private fun Modifier.mfmGraphics(
    mfmStyle: MfmInlineStyle,
    extraRotation: Float = 0f,
    extraOffsetX: Float = 0f,
    extraOffsetY: Float = 0f,
    extraScaleX: Float = 1f,
    extraScaleY: Float = 1f,
): Modifier {
    return offset(
        x = ((mfmStyle.positionX + extraOffsetX) * 8).dp,
        y = ((mfmStyle.positionY + extraOffsetY) * 8).dp,
    ).graphicsLayer {
        rotationZ = mfmStyle.rotateDeg + extraRotation
        scaleX = mfmStyle.scaleX * extraScaleX * if (mfmStyle.flipX) -1f else 1f
        scaleY = mfmStyle.scaleY * extraScaleY * if (mfmStyle.flipY) -1f else 1f
        alpha = mfmStyle.opacity
    }
}

@Composable
private fun Modifier.mfmAnimatedGraphics(
    mfmStyle: MfmInlineStyle,
    textLength: Int,
    animationBudget: RichTextAnimationBudget? = null,
): Modifier {
    val boundedStyle = mfmStyle.withBoundedAnimation(textLength, animationBudget)
    if (boundedStyle.effect == MfmEffect.None) return mfmGraphics(boundedStyle)
    val transition = rememberInfiniteTransition(label = "mfm-effect")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = boundedStyle.effectDurationMillis ?: boundedStyle.effect.durationMillis),
            repeatMode = if (boundedStyle.effectAlternate) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = "mfm-effect-phase",
    )
    val directedPhase = if (boundedStyle.effectReverse) 1f - phase else phase
    val wave = sin((directedPhase * 2f * PI).toFloat())
    val absWave = kotlin.math.abs(wave)
    return when (boundedStyle.effect) {
        MfmEffect.Tada -> mfmGraphics(
            mfmStyle = boundedStyle,
            extraRotation = wave * 4f,
            extraScaleX = 1f + absWave * 0.08f,
            extraScaleY = 1f + absWave * 0.08f,
        )
        MfmEffect.Jelly -> mfmGraphics(
            mfmStyle = boundedStyle,
            extraScaleX = 1f + wave * 0.09f,
            extraScaleY = 1f - wave * 0.06f,
        )
        MfmEffect.Twitch -> mfmGraphics(
            mfmStyle = boundedStyle,
            extraOffsetX = if (directedPhase < 0.5f) 0.32f else -0.32f,
            extraOffsetY = if (directedPhase < 0.25f || directedPhase > 0.75f) -0.2f else 0.2f,
        )
        MfmEffect.Shake -> mfmGraphics(mfmStyle = boundedStyle, extraOffsetX = wave * 0.38f)
        MfmEffect.Spin -> mfmGraphics(mfmStyle = boundedStyle, extraRotation = directedPhase * 360f)
        MfmEffect.Jump -> mfmGraphics(mfmStyle = boundedStyle, extraOffsetY = -absWave * 0.48f)
        MfmEffect.Bounce -> mfmGraphics(
            mfmStyle = boundedStyle,
            extraOffsetY = -absWave * 0.3f,
            extraScaleY = 1f + absWave * 0.07f,
        )
        MfmEffect.Sparkle -> mfmGraphics(
            mfmStyle = boundedStyle.copy(opacity = (0.72f + absWave * 0.28f).coerceIn(0.72f, 1f)),
            extraScaleX = 1f + absWave * 0.04f,
            extraScaleY = 1f + absWave * 0.04f,
        )
        MfmEffect.None -> mfmGraphics(boundedStyle)
    }
}

internal fun MfmInlineStyle.withBoundedAnimation(
    textLength: Int,
    animationBudget: RichTextAnimationBudget? = null,
): MfmInlineStyle {
    if (effect == MfmEffect.None || textLength > MAX_MFM_ANIMATED_CHARS) return withoutAnimation()
    if (animationBudget?.tryConsume() == false) return withoutAnimation()
    return this
}

private fun MfmInlineStyle.withoutAnimation(): MfmInlineStyle {
    return if (effect == MfmEffect.None) {
        this
    } else {
        copy(
            effect = MfmEffect.None,
            effectDurationMillis = null,
            effectReverse = false,
            effectAlternate = false,
        )
    }
}

@Composable
private fun InlineActionText(
    text: String,
    style: TextStyle,
    accentColor: Color,
    markdownStyle: InlineMarkdownStyle = InlineMarkdownStyle.Plain,
    mfmStyle: MfmInlineStyle = MfmInlineStyle.Default,
    animationBudget: RichTextAnimationBudget? = null,
    onClick: () -> Unit,
) {
    val textColor = mfmStyle.foregroundColor?.toComposeColor() ?: accentColor
    Text(
        text = text,
        color = textColor,
        style = style.markdown(markdownStyle).markdownMfm(mfmStyle),
        modifier = Modifier
            .mfmTextModifier(
                mfmStyle = mfmStyle,
                textColor = textColor,
                textLength = text.length,
                animationBudget = animationBudget,
            )
            .clickable { onClick() },
    )
}

@Composable
private fun Modifier.mfmTextModifier(
    mfmStyle: MfmInlineStyle,
    textColor: Color,
    textLength: Int,
    animationBudget: RichTextAnimationBudget? = null,
): Modifier {
    val backgroundColor = mfmStyle.backgroundColor?.toComposeColor()
    val borderColor = mfmStyle.borderColor?.toComposeColor() ?: textColor.copy(alpha = 0.42f)
    val shape = RoundedCornerShape(mfmStyle.borderRadiusDp.dp)
    return mfmAnimatedGraphics(mfmStyle = mfmStyle, textLength = textLength, animationBudget = animationBudget)
        .then(
            if (mfmStyle.borderWidthDp > 0f) {
                Modifier
                    .clip(shape)
                    .border(mfmStyle.borderWidthDp.dp, borderColor, shape)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            } else {
                Modifier
            },
        )
        .then(
            if (backgroundColor != null) {
                Modifier
                    .clip(shape)
                    .background(backgroundColor.copy(alpha = 0.22f))
                    .padding(horizontal = 2.dp)
            } else {
                Modifier
            },
        )
}

internal class RichTextAnimationBudget(
    private val maxAnimatedSpans: Int,
) {
    private var usedSpans: Int = 0

    fun tryConsume(): Boolean {
        if (usedSpans >= maxAnimatedSpans) return false
        usedSpans += 1
        return true
    }
}

@Composable
private fun InlineMarkdownFlow(
    spans: List<InlineMarkdownSpan>,
    emojiUrls: Map<String, String>,
    style: TextStyle,
    color: Color,
    accentColor: Color,
    animationBudget: RichTextAnimationBudget,
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
                        val emojiDependencySignature = remember(span.value, emojiUrls) {
                            span.value.richTextEmojiDependencySignature(emojiUrls)
                        }
                        val segments = remember(span.value, emojiUrls, emojiDependencySignature) {
                            cachedRichTextSegments(span.value, emojiUrls, emojiDependencySignature)
                        }
                        InlineRichSegments(
                            segments = segments,
                            style = style,
                            color = color,
                            accentColor = accentColor,
                            markdownStyle = span.style,
                            mfmStyle = span.mfmStyle,
                            animationBudget = animationBudget,
                            onOpenUrl = onOpenUrl,
                            onOpenMention = onOpenMention,
                            onOpenHashtag = onOpenHashtag,
                        )
                    } else {
                        InlinePlainText(span.value, style, color, span.style, span.mfmStyle, animationBudget)
                    }
                }
                is InlineMarkdownSpan.Link -> InlineActionText(
                    text = span.label,
                    style = style,
                    accentColor = accentColor,
                    markdownStyle = InlineMarkdownStyle.Link,
                    mfmStyle = span.mfmStyle,
                    animationBudget = animationBudget,
                    onClick = { onOpenUrl(span.url) },
                )
                is InlineMarkdownSpan.Ruby -> InlineRubyText(
                    span = span,
                    style = style,
                    color = color,
                    animationBudget = animationBudget,
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
    animationBudget: RichTextAnimationBudget,
) {
    val textColor = span.mfmStyle.foregroundColor?.toComposeColor() ?: color
    Column(
        modifier = Modifier.mfmTextModifier(
            mfmStyle = span.mfmStyle,
            textColor = textColor,
            textLength = span.base.length + span.annotation.length,
            animationBudget = animationBudget,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = span.annotation,
            color = textColor.copy(alpha = 0.72f),
            style = style.markdown(InlineMarkdownStyle.Small).markdownMfm(span.mfmStyle),
            maxLines = 1,
        )
        Text(
            text = span.base,
            color = textColor,
            style = style.markdownMfm(span.mfmStyle),
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
    mfmStyle: MfmInlineStyle = MfmInlineStyle.Default,
    animationBudget: RichTextAnimationBudget,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    segments.forEach { segment ->
        when (segment) {
            is RichTextSegment.Text -> InlinePlainText(segment.value, style, color, markdownStyle, mfmStyle, animationBudget)
            is RichTextSegment.Emoji -> InlineRichEmojiImage(segment, mfmStyle, animationBudget)
            is RichTextSegment.Url -> InlineActionText(
                text = segment.value,
                style = style,
                accentColor = accentColor,
                markdownStyle = markdownStyle,
                mfmStyle = mfmStyle,
                animationBudget = animationBudget,
                onClick = { onOpenUrl(segment.value) },
            )
            is RichTextSegment.Mention -> InlineActionText(
                text = segment.value,
                style = style,
                accentColor = accentColor,
                markdownStyle = markdownStyle,
                mfmStyle = mfmStyle,
                animationBudget = animationBudget,
                onClick = { onOpenMention(segment.username) },
            )
            is RichTextSegment.Hashtag -> InlineActionText(
                text = segment.value,
                style = style,
                accentColor = accentColor,
                markdownStyle = markdownStyle,
                mfmStyle = mfmStyle,
                animationBudget = animationBudget,
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
    animationBudget: RichTextAnimationBudget,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    val filledBubbleTone = accentColor == color
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (filledBubbleTone) {
                    color.copy(alpha = 0.12f)
                } else {
                    colors.mediaBackground
                },
            )
            .border(
                width = 1.dp,
                color = if (filledBubbleTone) {
                    color.copy(alpha = 0.14f)
                } else {
                    colors.border
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
            animationBudget = animationBudget,
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
    val colors = LocalHhhlColors.current
    Text(
        text = text,
        color = colors.textSecondary,
        style = style.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.82f))
            .border(1.dp, colors.border.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun InlineRichEmojiImage(
    segment: RichTextSegment.Emoji,
    mfmStyle: MfmInlineStyle = MfmInlineStyle.Default,
    animationBudget: RichTextAnimationBudget,
) {
    val colors = LocalHhhlColors.current
    var imageLoaded by remember(segment.url) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(18.dp)
            .mfmAnimatedGraphics(mfmStyle = mfmStyle, textLength = segment.code.length, animationBudget = animationBudget),
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
                color = mfmStyle.foregroundColor?.toComposeColor() ?: colors.textPrimary,
                style = MaterialTheme.typography.labelSmall.markdownMfm(mfmStyle),
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
        val mfmStyle: MfmInlineStyle = MfmInlineStyle.Default,
    ) : InlineMarkdownSpan

    data class Link(
        val label: String,
        val url: String,
        val mfmStyle: MfmInlineStyle = MfmInlineStyle.Default,
    ) : InlineMarkdownSpan

    data class Ruby(
        val base: String,
        val annotation: String,
        val mfmStyle: MfmInlineStyle = MfmInlineStyle.Default,
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

internal data class MfmInlineStyle(
    val foregroundColor: String? = null,
    val backgroundColor: String? = null,
    val font: MfmFont = MfmFont.Default,
    val rotateDeg: Float = 0f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val opacity: Float = 1f,
    val borderColor: String? = null,
    val borderWidthDp: Float = 0f,
    val borderRadiusDp: Float = 4f,
    val effect: MfmEffect = MfmEffect.None,
    val effectDurationMillis: Int? = null,
    val effectReverse: Boolean = false,
    val effectAlternate: Boolean = false,
) {
    companion object {
        val Default = MfmInlineStyle()
    }
}

internal enum class MfmFont {
    Default,
    Serif,
    Monospace,
    Cursive,
    Fantasy,
    Emoji,
    Math,
}

internal enum class MfmEffect {
    None,
    Tada,
    Jelly,
    Twitch,
    Shake,
    Spin,
    Jump,
    Bounce,
    Sparkle,
}

private val MfmEffect.durationMillis: Int
    get() = when (this) {
        MfmEffect.Tada -> 900
        MfmEffect.Jelly -> 880
        MfmEffect.Twitch -> 220
        MfmEffect.Shake -> 260
        MfmEffect.Spin -> 1_400
        MfmEffect.Jump -> 620
        MfmEffect.Bounce -> 720
        MfmEffect.Sparkle -> 1_100
        MfmEffect.None -> 1_000
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

    val lines = text.lineSequence().toList()
    var lineIndex = 0
    while (lineIndex < lines.size) {
        val line = lines[lineIndex]
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
            lineIndex += 1
            continue
        }
        if (inCodeBlock) {
            code.append(line).append('\n')
            lineIndex += 1
            continue
        }
        val singleLineBlock = parseStaticLineBlock(line)
        if (singleLineBlock != null) {
            flushParagraph()
            flushQuote()
            blocks.add(singleLineBlock)
            lineIndex += 1
            continue
        }
        val multilineMfmBlock = parseMultilineMfmBlock(lines, lineIndex)
        if (multilineMfmBlock != null) {
            val (lineBlock, nextLineIndex) = multilineMfmBlock
            flushParagraph()
            flushQuote()
            blocks.add(lineBlock)
            lineIndex = nextLineIndex
            continue
        }
        if (trimmed.startsWith(">")) {
            flushParagraph()
            quote.append(trimmed.removePrefix(">").trimStart()).append('\n')
            lineIndex += 1
            continue
        }
        flushQuote()
        if (line.isBlank()) {
            flushParagraph()
        } else {
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(line)
        }
        lineIndex += 1
    }

    if (inCodeBlock) {
        blocks.add(MarkdownBlock.Code(code.toString().trimEnd('\n')))
    }
    flushQuote()
    flushParagraph()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(listOf(InlineMarkdownSpan.Text("")))) }
}

internal fun richTextPreviewPlainText(text: String): String {
    if (text.isBlank()) return text.trim()
    return cachedMarkdownBlocks(text)
        .joinToString(separator = "\n") { block ->
            when (block) {
                is MarkdownBlock.Code -> block.value
                is MarkdownBlock.Center -> block.spans.joinToString(separator = "") { span -> span.plainTextValue() }
                is MarkdownBlock.Paragraph -> block.spans.joinToString(separator = "") { span -> span.plainTextValue() }
                is MarkdownBlock.Quote -> block.spans.joinToString(separator = "") { span -> span.plainTextValue() }
            }
        }
        .trim()
}

private fun cachedMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.length > MAX_RICH_TEXT_CACHEABLE_CHARS) return parseMarkdownBlocks(text)
    markdownBlocksCache[text]?.let { return it }
    val parsed = parseMarkdownBlocks(text)
    markdownBlocksCache = markdownBlocksCache.withRichTextCacheEntry(text, parsed)
    return parsed
}

private fun cachedRichTextSegmentsWithoutEmoji(text: String): List<RichTextSegment> {
    if (text.length > MAX_RICH_TEXT_CACHEABLE_CHARS) return parseRichText(text = text, emojiUrls = emptyMap())
    richTextSegmentsCache[text]?.let { return it }
    val parsed = parseRichText(text = text, emojiUrls = emptyMap())
    richTextSegmentsCache = richTextSegmentsCache.withRichTextCacheEntry(text, parsed)
    return parsed
}

internal fun cachedRichTextSegments(
    text: String,
    emojiUrls: Map<String, String>,
): List<RichTextSegment> {
    return cachedRichTextSegments(
        text = text,
        emojiUrls = emojiUrls,
        emojiDependencySignature = text.richTextEmojiDependencySignature(emojiUrls),
    )
}

private fun cachedRichTextSegments(
    text: String,
    emojiUrls: Map<String, String>,
    emojiDependencySignature: String?,
): List<RichTextSegment> {
    if (emojiUrls.isEmpty()) return cachedRichTextSegmentsWithoutEmoji(text)
    if (text.length > MAX_RICH_TEXT_CACHEABLE_CHARS) return parseRichText(text = text, emojiUrls = emojiUrls)
    val emojiSignature = emojiDependencySignature
        ?: return if (text.contains(':')) {
            parseRichText(text = text, emojiUrls = emojiUrls)
        } else {
            cachedRichTextSegmentsWithoutEmoji(text)
        }
    val cacheKey = "$text\u0000$emojiSignature"
    richTextSegmentsCache[cacheKey]?.let { return it }
    val parsed = parseRichText(text = text, emojiUrls = emojiUrls)
    richTextSegmentsCache = richTextSegmentsCache.withRichTextCacheEntry(cacheKey, parsed)
    return parsed
}

internal fun <T> Map<String, T>.withRichTextCacheEntry(key: String, value: T): Map<String, T> {
    val next = LinkedHashMap<String, T>(size + 1)
    forEach { (existingKey, existingValue) ->
        if (existingKey != key) next[existingKey] = existingValue
    }
    next[key] = value
    while (next.size > MAX_RICH_TEXT_CACHE_ENTRIES) {
        val firstKey = next.keys.firstOrNull() ?: return next
        next.remove(firstKey)
    }
    return next
}

internal fun String.richTextEmojiDependencySignature(emojiUrls: Map<String, String>): String? {
    if (isEmpty() || emojiUrls.isEmpty()) return null
    val dependencies = linkedMapOf<String, String>()
    var index = 0
    while (index < length && dependencies.size < MAX_RICH_TEXT_EMOJI_CACHE_DEPENDENCIES) {
        val start = indexOf(':', startIndex = index)
        if (start < 0) break
        val emoji = parseCustomEmojiMatch(this, start, emojiUrls)
        if (emoji == null) {
            index = start + 1
        } else {
            dependencies[emoji.code] = emoji.url
            index = emoji.end
        }
    }
    if (dependencies.isEmpty()) return null
    if (dependencies.size >= MAX_RICH_TEXT_EMOJI_CACHE_DEPENDENCIES) return null
    return dependencies.entries.joinToString(separator = "\u0001") { (code, url) -> "$code\u0002$url" }
}

internal fun parseInlineMarkdown(text: String): List<InlineMarkdownSpan> {
    return parseInlineMarkdown(text, depth = 0)
}

private fun parseInlineMarkdown(text: String, depth: Int): List<InlineMarkdownSpan> {
    if (text.isEmpty()) return listOf(InlineMarkdownSpan.Text(""))
    if (depth > MAX_MFM_NESTING_DEPTH) return listOf(InlineMarkdownSpan.Text(text))

    val spans = mutableListOf<InlineMarkdownSpan>()
    var index = 0
    while (index < text.length) {
        if (spans.size >= MAX_INLINE_MARKDOWN_SPANS) {
            spans.add(InlineMarkdownSpan.Text(text.substring(index)))
            break
        }
        val codeSpan = parseInlineCodeSpan(text, index)
        if (codeSpan != null) {
            spans.add(codeSpan.span)
            index = codeSpan.end
            continue
        }

        val link = parseMarkdownLink(text, index, depth)
        if (link != null) {
            spans.add(link.span)
            index = link.end
            continue
        }

        val htmlSpan = parseInlineHtmlSpan(text, index)
        if (htmlSpan != null) {
            spans.addAll(htmlSpan.spans)
            index = htmlSpan.end
            continue
        }

        if (text.startsWith("$[", index) || text.startsWith("\${", index)) {
            val mfmSpan = parseMfmSpan(text, index, depth)
            if (mfmSpan != null) {
                spans.addAll(mfmSpan.spans)
                index = mfmSpan.end
                continue
            }
            val interpolation = parsePlainMfmInterpolation(text, index)
            if (interpolation != null) {
                spans.add(InlineMarkdownSpan.Text(interpolation.value))
                index = interpolation.end
                continue
            }
            val fallbackEnd = malformedMfmFallbackEnd(text, index)
            if (fallbackEnd > index) {
                spans.add(InlineMarkdownSpan.Text(text.substring(index, fallbackEnd)))
                index = fallbackEnd
                continue
            }
            spans.add(InlineMarkdownSpan.Text(text.substring(index)))
            break
        }

        val marker = markdownMarkerAt(text, index)
        if (marker != null) {
            val end = findClosingMarkdownMarker(text, marker, index + marker.token.length)
            if (end > index + marker.token.length) {
                spans.addAll(
                    parseInlineMarkdown(text.substring(index + marker.token.length, end), depth + 1)
                        .applyMfmStyle(
                            markdownStyle = marker.style,
                            mfmStyle = MfmInlineStyle.Default,
                            richTextEnabled = true,
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

private data class ParsedInlineCodeSpan(
    val span: InlineMarkdownSpan.Text,
    val end: Int,
)

private fun parseInlineCodeSpan(text: String, start: Int): ParsedInlineCodeSpan? {
    if (text[start] != '`') return null
    val code = StringBuilder()
    var index = start + 1
    var escaped = false
    while (index < text.length && index - start <= MAX_INLINE_CODE_CHARS) {
        val char = text[index]
        if (escaped) {
            code.append(char)
            escaped = false
            index += 1
            continue
        }
        when (char) {
            '\\' -> {
                escaped = true
                index += 1
            }
            '`' -> {
                if (code.isEmpty()) return null
                return ParsedInlineCodeSpan(
                    span = InlineMarkdownSpan.Text(
                        value = code.toString(),
                        style = InlineMarkdownStyle.Code,
                        richTextEnabled = false,
                    ),
                    end = index + 1,
                )
            }
            '\n', '\r' -> return null
            else -> {
                code.append(char)
                index += 1
            }
        }
    }
    return null
}

private data class ParsedMarkdownLink(
    val span: InlineMarkdownSpan.Link,
    val end: Int,
)

private fun parseMarkdownLink(text: String, start: Int, depth: Int): ParsedMarkdownLink? {
    if (text[start] != '[') return null
    val labelEnd = findMarkdownLinkLabelEnd(text, start) ?: return null
    if (labelEnd <= start + 1) return null
    if (text.getOrNull(labelEnd + 1) != '(') return null
    val urlStart = labelEnd + 2
    val urlEnd = findMarkdownLinkUrlEnd(text, urlStart) ?: return null
    if (urlEnd <= urlStart) return null
    val url = text.substring(urlStart, urlEnd)
    if (!url.isSafeUrl()) return null
    val label = text.substring(start + 1, labelEnd).renderMarkdownLinkLabel(depth)
    return ParsedMarkdownLink(
        span = InlineMarkdownSpan.Link(
            label = label,
            url = url,
        ),
        end = urlEnd + 1,
    )
}

private fun String.renderMarkdownLinkLabel(depth: Int): String {
    if (isBlank()) return this
    if (depth >= MAX_MFM_NESTING_DEPTH) return this
    return parseInlineMarkdown(this, depth + 1)
        .joinToString(separator = "") { span -> span.plainTextValue() }
        .unescapeMarkdownLinkLabel()
        .ifBlank { this }
}

private fun String.unescapeMarkdownLinkLabel(): String {
    if ('\\' !in this) return this
    val result = StringBuilder(length)
    var escaped = false
    for (char in this) {
        if (escaped) {
            result.append(char)
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else {
            result.append(char)
        }
    }
    if (escaped) result.append('\\')
    return result.toString()
}

private fun findMarkdownLinkLabelEnd(text: String, start: Int): Int? {
    var index = start + 1
    var escaped = false
    var depth = 0
    while (index < text.length && index - start <= MAX_MARKDOWN_LINK_LABEL_CHARS) {
        val char = text[index]
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

private fun findMarkdownLinkUrlEnd(text: String, start: Int): Int? {
    var index = start
    var escaped = false
    var depth = 0
    while (index < text.length && index - start <= MAX_MARKDOWN_LINK_URL_CHARS) {
        val char = text[index]
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
            text.startsWith("$[", index) ||
                text.startsWith("\${", index)
        ) {
            return index
        }
        index += 1
    }
    return -1
}

private data class ParsedStyledSpan(
    val spans: List<InlineMarkdownSpan>,
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
    HtmlInlineRule("code", InlineMarkdownStyle.Code, richTextEnabled = false),
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
    if (tagEnd - start > MAX_INLINE_HTML_TAG_CHARS) return null
    val rawTag = text.substring(start + 1, tagEnd).trim()
    val normalizedTag = rawTag.lowercase()
    if (normalizedTag.startsWith("/")) return null
    parseHtmlImageAlt(rawTag)?.let { alt ->
        return ParsedStyledSpan(listOf(InlineMarkdownSpan.Text(alt)), tagEnd + 1)
    }
    val tag = normalizedTag.substringBefore(' ').removeSuffix("/")
    if (tag == "br") {
        return ParsedStyledSpan(listOf(InlineMarkdownSpan.Text("\n")), tagEnd + 1)
    }
    if (tag == "a") {
        val url = parseHtmlAttribute(rawTag, "href") ?: return null
        if (!url.isSafeUrl()) return null
        val closeToken = "</a>"
        val closeStart = text.indexOf(closeToken, startIndex = tagEnd + 1, ignoreCase = true)
        if (closeStart < 0) return null
        if (closeStart - tagEnd > MAX_INLINE_HTML_BODY_CHARS) return null
        return ParsedStyledSpan(
            spans = listOf(
                InlineMarkdownSpan.Link(
                    label = htmlInlineText(text.substring(tagEnd + 1, closeStart)),
                    url = url,
                ),
            ),
            end = closeStart + closeToken.length,
        )
    }
    if (tag == "ruby") {
        parseHtmlRuby(text, tagEnd + 1)?.let { ruby ->
            return ParsedStyledSpan(ruby.spans, ruby.end)
        }
    }
    val rule = htmlInlineRules[tag] ?: return null
    if (normalizedTag.endsWith("/")) {
        return ParsedStyledSpan(listOf(InlineMarkdownSpan.Text("")), tagEnd + 1)
    }
    val closeToken = "</$tag>"
    val closeStart = text.indexOf(closeToken, startIndex = tagEnd + 1, ignoreCase = true)
    if (closeStart < 0) return null
    if (closeStart - tagEnd > MAX_INLINE_HTML_BODY_CHARS) return null
    return ParsedStyledSpan(
        spans = listOf(
            InlineMarkdownSpan.Text(
                value = rule.textTransform(text.substring(tagEnd + 1, closeStart)),
                style = rule.style,
                richTextEnabled = rule.richTextEnabled,
            ),
        ),
        end = closeStart + closeToken.length,
    )
}

private fun parseMfmSpan(text: String, start: Int, depth: Int): ParsedStyledSpan? {
    val function = parseMfmFunction(text, start) ?: return null
    val rawName = function.rawName
    val name = function.name
    val value = function.value
    val options = function.options
    if (text.startsWith("\${", start) && !name.isSupportedBraceMfmName()) return null
    if (name == "ruby") {
        val parts = value.splitMfmValueTokens(maxTokens = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        val mfmStyle = semanticMfmInlineStyle(options, parts[0].length + parts[1].length)
        return ParsedStyledSpan(
            spans = listOf(InlineMarkdownSpan.Ruby(parts[0], parts[1], mfmStyle)),
            end = function.end,
        )
    }
    if (name == "link") {
        val link = parseMfmLinkValue(value) ?: return null
        val mfmStyle = semanticMfmInlineStyle(options, link.label.length)
        return ParsedStyledSpan(
            spans = listOf(InlineMarkdownSpan.Link(link.label, link.url, mfmStyle)),
            end = function.end,
        )
    }
    if (name == "unixtime") {
        return ParsedStyledSpan(listOf(InlineMarkdownSpan.Text(value.toMfmUnixTimeText() ?: value)), function.end)
    }
    if (name == "unicode") {
        return ParsedStyledSpan(
            listOf(InlineMarkdownSpan.Text(value.toMfmUnicodeText() ?: value)),
            function.end,
        )
    }
    val renderValue = value.unquoteMfmOptionValue()
    val style = when (name) {
        "small" -> InlineMarkdownStyle.Small
        "x2" -> InlineMarkdownStyle.X2
        "x3" -> InlineMarkdownStyle.X3
        "x4" -> InlineMarkdownStyle.X4
        "scale" -> InlineMarkdownStyle.Plain
        "code" -> InlineMarkdownStyle.Code
        "blur", "blurry" -> InlineMarkdownStyle.Blurry
        "rainbow" -> InlineMarkdownStyle.Rainbow
        "plain" -> InlineMarkdownStyle.Plain
        else -> InlineMarkdownStyle.Plain
    }
    val mfmStyle = mfmInlineStyle(name, options, rawName).withoutOverlongEffect(renderValue.length)
    val innerSpans = if (name == "plain" || name == "code") {
        listOf(
            InlineMarkdownSpan.Text(
                value = renderValue,
                style = style,
                richTextEnabled = name != "plain" && name != "code",
                mfmStyle = mfmStyle,
            ),
        )
    } else {
        parseInlineMarkdown(renderValue, depth + 1).applyMfmStyle(
            markdownStyle = style,
            mfmStyle = mfmStyle,
            richTextEnabled = true,
        )
    }
    return ParsedStyledSpan(
        spans = innerSpans,
        end = function.end,
    )
}

private data class MfmFunction(
    val rawName: String,
    val name: String,
    val options: String,
    val value: String,
    val end: Int,
)

private data class PlainMfmInterpolation(
    val value: String,
    val end: Int,
)

private data class MfmSyntax(
    val openToken: String,
    val closeChar: Char,
)

private fun parseMfmFunction(text: String, start: Int): MfmFunction? {
    return parseMfmFunction(text, start, maxEndExclusive = text.length)
}

private fun parseMfmFunction(
    text: String,
    start: Int,
    maxEndExclusive: Int,
): MfmFunction? {
    val syntax = when {
        text.startsWith("$[", start) -> MfmSyntax(openToken = "$[", closeChar = ']')
        text.startsWith("\${", start) -> MfmSyntax(openToken = "\${", closeChar = '}')
        else -> return null
    }
    val closeIndex = findMfmCloseIndex(
        text = text,
        start = start,
        syntax = syntax,
        maxEndExclusive = boundedMfmFunctionEndExclusive(text, start, syntax, maxEndExclusive),
    )
    if (closeIndex <= start + 3) return null
    val body = text.substring(start + syntax.openToken.length, closeIndex)
    val separatorIndex = body.indexOfFirst { it.isWhitespace() }
    if (separatorIndex <= 0 || separatorIndex == body.lastIndex) return null
    val rawName = body.substring(0, separatorIndex).trim()
    val normalizedRawName = rawName.lowercase()
    val name = normalizedRawName.substringBefore('.').substringBefore('=')
    val options = when {
        normalizedRawName.startsWith("$name.") -> rawName.substringAfter('.')
        normalizedRawName.startsWith("$name=") -> "v=" + rawName.substringAfter('=')
        else -> ""
    }
    val value = body.substring(separatorIndex + 1).trim()
    if (name.isBlank() || value.isBlank()) return null
    return MfmFunction(
        rawName = rawName,
        name = name,
        options = options,
        value = value,
        end = closeIndex + 1,
    )
}

private fun parsePlainMfmInterpolation(text: String, start: Int): PlainMfmInterpolation? {
    return parsePlainMfmInterpolation(text, start, maxEndExclusive = text.length)
}

private fun parsePlainMfmInterpolation(
    text: String,
    start: Int,
    maxEndExclusive: Int,
): PlainMfmInterpolation? {
    val syntax = MfmSyntax(openToken = "\${", closeChar = '}')
    if (!text.startsWith(syntax.openToken, start)) return null
    val closeIndex = findMfmCloseIndex(
        text = text,
        start = start,
        syntax = syntax,
        maxEndExclusive = boundedMfmFunctionEndExclusive(text, start, syntax, maxEndExclusive),
    )
    if (closeIndex <= start + syntax.openToken.length) return null
    val body = text.substring(start + syntax.openToken.length, closeIndex).trim()
    if (body.isBlank() || body.any { it.isWhitespace() }) return null
    return PlainMfmInterpolation(
        value = body,
        end = closeIndex + 1,
    )
}

internal fun String.containsValidMfmSyntax(
    maxScanChars: Int = MAX_MFM_SYNTAX_DETECT_SCAN_CHARS,
): Boolean {
    if (isEmpty()) return false
    val scanLimit = minOf(length, maxScanChars.coerceAtLeast(0))
    var index = 0
    while (index < scanLimit) {
        if (startsWith("$[", index)) {
            val function = parseMfmFunction(this, index, maxEndExclusive = scanLimit)
            if (function != null && function.end <= scanLimit) return true
        } else if (startsWith("\${", index)) {
            val function = parseMfmFunction(this, index, maxEndExclusive = scanLimit)
            if (function != null && function.end <= scanLimit && function.name.isSupportedBraceMfmName()) return true
            val interpolation = parsePlainMfmInterpolation(this, index, maxEndExclusive = scanLimit)
            if (interpolation != null && interpolation.end <= scanLimit) return true
        }
        index += 1
    }
    return false
}

private fun findMfmCloseIndex(
    text: String,
    start: Int,
    syntax: MfmSyntax,
    maxEndExclusive: Int = text.length,
): Int {
    val closeStack = mutableListOf(syntax.closeChar)
    var index = start + syntax.openToken.length
    var quote: Char? = null
    var escaped = false
    val endExclusive = minOf(text.length, maxEndExclusive.coerceAtLeast(start + syntax.openToken.length))
    while (index < endExclusive) {
        val char = text[index]
        if (escaped) {
            escaped = false
            index += 1
            continue
        }
        if (quote != null) {
            when (char) {
                '\\' -> escaped = true
                quote -> quote = null
            }
            index += 1
            continue
        }
        if (char == '\'' || char == '"') {
            quote = char
            index += 1
            continue
        }
        when {
            text.startsWith("$[", index) -> {
                closeStack.add(']')
                if (closeStack.size > MAX_MFM_NESTING_DEPTH + 1) return -1
                index += 2
                continue
            }
            text.startsWith("\${", index) -> {
                closeStack.add('}')
                if (closeStack.size > MAX_MFM_NESTING_DEPTH + 1) return -1
                index += 2
                continue
            }
        }
        if (char == closeStack.last()) {
            closeStack.removeAt(closeStack.lastIndex)
            if (closeStack.isEmpty()) return index
            index += 1
            continue
        }
        index += 1
    }
    return -1
}

private fun malformedMfmFallbackEnd(text: String, start: Int): Int {
    val syntax = when {
        text.startsWith("$[", start) -> MfmSyntax(openToken = "$[", closeChar = ']')
        text.startsWith("\${", start) -> MfmSyntax(openToken = "\${", closeChar = '}')
        else -> return -1
    }
    if (text.length - start > MAX_MFM_MALFORMED_FALLBACK_SCAN_CHARS) {
        return start + syntax.openToken.length
    }
    val closeIndex = findMfmCloseIndex(
        text = text,
        start = start,
        syntax = syntax,
        maxEndExclusive = boundedMfmFunctionEndExclusive(text, start, syntax, text.length),
    )
    return if (closeIndex > start) closeIndex + 1 else -1
}

private fun boundedMfmFunctionEndExclusive(
    text: String,
    start: Int,
    syntax: MfmSyntax,
    maxEndExclusive: Int,
): Int {
    val maxBodyEnd = start + syntax.openToken.length + MAX_MFM_FUNCTION_BODY_CHARS + 1
    return minOf(text.length, maxEndExclusive, maxBodyEnd)
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
    parseMfmFunction(trimmed, 0)?.takeIf { it.end == trimmed.length }?.let { function ->
        return when (function.name) {
            "center" -> MarkdownBlock.Center(parseInlineMarkdown(function.value))
            "quote" -> MarkdownBlock.Quote(parseInlineMarkdown(function.value))
            "code" -> MarkdownBlock.Code(function.value)
            else -> null
        }
    }
    return null
}

private fun parseMultilineMfmBlock(
    lines: List<String>,
    startIndex: Int,
): Pair<MarkdownBlock, Int>? {
    val firstLine = lines.getOrNull(startIndex)?.trimStart() ?: return null
    if (!firstLine.startsWithMfmBlockName("center") &&
        !firstLine.startsWithMfmBlockName("quote") &&
        !firstLine.startsWithMfmBlockName("code")
    ) {
        return null
    }
    val block = StringBuilder(firstLine)
    var index = startIndex + 1
    while (index < lines.size && block.length <= MAX_MFM_FUNCTION_BODY_CHARS + 16) {
        block.append('\n').append(lines[index])
        parseStaticLineBlock(block.toString())?.let { parsed ->
            return parsed to (index + 1)
        }
        index += 1
    }
    return null
}

private fun String.startsWithMfmBlockName(name: String): Boolean {
    val prefix = "$[$name"
    if (!startsWith(prefix, ignoreCase = true)) return false
    val next = getOrNull(prefix.length)
    return next == null || next.isWhitespace()
}

private fun parseHtmlRuby(text: String, innerStart: Int): ParsedStyledSpan? {
    val closeToken = "</ruby>"
    val closeStart = text.indexOf(closeToken, startIndex = innerStart, ignoreCase = true)
    if (closeStart < 0) return null
    if (closeStart - innerStart > MAX_INLINE_HTML_BODY_CHARS) return null
    val inner = text.substring(innerStart, closeStart)
    val rtOpen = inner.indexOf("<rt>", ignoreCase = true)
    val rtClose = inner.indexOf("</rt>", ignoreCase = true)
    if (rtOpen <= 0 || rtClose <= rtOpen) return null
    val base = htmlInlineText(inner.substring(0, rtOpen).removeRubyFallbackTags())
    val annotation = htmlInlineText(inner.substring(rtOpen + "<rt>".length, rtClose).removeRubyFallbackTags())
    if (base.isBlank() || annotation.isBlank()) return null
    return ParsedStyledSpan(
        spans = listOf(InlineMarkdownSpan.Ruby(base, annotation)),
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
        else -> return null
    }
    return pattern.find(rawTag)?.groupValues?.getOrNull(2)
}

private fun parseWrappedHtmlLine(text: String, tag: String): String? {
    val openPattern = wrappedHtmlOpenPatterns[tag] ?: return null
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

private fun mfmInlineStyle(
    name: String,
    options: String,
    rawName: String,
): MfmInlineStyle {
    val optionMap = parseMfmOptions(options)
    val scale = parseMfmScale(rawName)
    val isForeground = name.isMfmForegroundName()
    val isBackground = name.isMfmBackgroundName()
    val isRotate = name.isMfmRotateName()
    val isPosition = name.isMfmPositionName()
    val isOpacity = name.isMfmOpacityName()
    val isBorder = name.isMfmBorderName()
    val effect = when (name) {
        "tada" -> MfmEffect.Tada
        "jelly" -> MfmEffect.Jelly
        "twitch" -> MfmEffect.Twitch
        "shake" -> MfmEffect.Shake
        "spin" -> MfmEffect.Spin
        "jump" -> MfmEffect.Jump
        "bounce" -> MfmEffect.Bounce
        "sparkle" -> MfmEffect.Sparkle
        else -> MfmEffect.None
    }
    return MfmInlineStyle(
        foregroundColor = if (isForeground) optionMap.firstMfmColor("color", "colour", "col", "c", "value", "v") else null,
        backgroundColor = if (isBackground) optionMap.firstMfmColor("color", "colour", "col", "c", "value", "v") else null,
        font = if (name == "font") parseMfmFont(options, optionMap) else MfmFont.Default,
        rotateDeg = if (isRotate) {
            optionMap.firstMfmFloat(-360f, 360f, "deg", "d", "x", "value", "v")
                ?: options.toMfmFloat(-360f, 360f)
                ?: 0f
        } else {
            0f
        },
        positionX = if (isPosition) optionMap.firstMfmFloat(-4f, 4f, "x", "left", "l") ?: 0f else 0f,
        positionY = if (isPosition) optionMap.firstMfmFloat(-4f, 4f, "y", "top", "t") ?: 0f else 0f,
        scaleX = scale?.first ?: 1f,
        scaleY = scale?.second ?: 1f,
        flipX = name == "flip" && !optionMap.isMfmTruthy("v", "vertical"),
        flipY = name == "flip" && !optionMap.isMfmTruthy("h", "horizontal"),
        opacity = if (isOpacity) {
            optionMap.firstMfmFloat(0.08f, 1f, "value", "v", "alpha", "a") ?: options.toMfmFloat(0.08f, 1f) ?: 1f
        } else {
            1f
        },
        borderColor = if (isBorder) optionMap.firstMfmColor("color", "colour", "col", "c", "value", "v") else null,
        borderWidthDp = if (isBorder) {
            optionMap.firstMfmFloat(0.5f, 4f, "width", "w") ?: if (optionMap.isMfmTruthy("thick")) 2f else 1f
        } else {
            0f
        },
        borderRadiusDp = if (isBorder) {
            when {
                optionMap["radius"] != null -> optionMap["radius"]?.toMfmFloat(0f, 16f) ?: 4f
                optionMap["r"] != null -> optionMap["r"]?.toMfmFloat(0f, 16f) ?: 4f
                optionMap.isMfmTruthy("circle") -> 16f
                optionMap.isMfmTruthy("nocorner") -> 0f
                else -> 4f
            }
        } else {
            4f
        },
        effect = effect,
        effectDurationMillis = effect.takeIf { it != MfmEffect.None }?.let {
            optionMap.firstMfmAnimationDurationMillis(it.durationMillis, "speed", "duration", "dur", "d")
        },
        effectReverse = effect != MfmEffect.None && (
            optionMap.isMfmTruthy("left", "reverse", "cw")
            ),
        effectAlternate = effect != MfmEffect.None && (
            optionMap.isMfmTruthy("alternate", "alt", "right")
            ),
    )
}

private fun semanticMfmInlineStyle(
    options: String,
    textLength: Int,
): MfmInlineStyle {
    val rawStyle = options.trim()
    if (rawStyle.isBlank()) return MfmInlineStyle.Default
    val normalizedRawStyle = rawStyle.lowercase()
    val styleName = normalizedRawStyle.substringBefore('.').substringBefore('=')
    if (!styleName.isMfmSemanticVisualName()) return MfmInlineStyle.Default
    val styleOptions = when {
        normalizedRawStyle.startsWith("$styleName.") -> rawStyle.substringAfter('.')
        normalizedRawStyle.startsWith("$styleName=") -> "v=" + rawStyle.substringAfter('=')
        else -> ""
    }
    val scale = parseMfmScale(rawStyle)
    val mfmStyle = mfmInlineStyle(styleName, styleOptions, rawStyle).let { style ->
        when {
            scale != null -> style
            styleName == "x2" -> style.merge(InlineMarkdownStyle.X2.toMfmScaleStyle())
            styleName == "x3" -> style.merge(InlineMarkdownStyle.X3.toMfmScaleStyle())
            styleName == "x4" -> style.merge(InlineMarkdownStyle.X4.toMfmScaleStyle())
            else -> style
        }
    }
    return mfmStyle.withoutOverlongEffect(textLength)
}

private fun String.isMfmForegroundName(): Boolean {
    return this == "fg" || this == "foreground" || this == "color" || this == "colour"
}

private fun String.isMfmBackgroundName(): Boolean {
    return this == "bg" || this == "background"
}

private fun String.isMfmRotateName(): Boolean {
    return this == "rotate" || this == "rotatez" || this == "rotation"
}

private fun String.isMfmPositionName(): Boolean {
    return this == "position" || this == "pos" || this == "translate"
}

private fun String.isMfmOpacityName(): Boolean {
    return this == "opacity" || this == "alpha"
}

private fun String.isMfmBorderName(): Boolean {
    return this == "border" || this == "bordered"
}

private fun String.isMfmSemanticVisualName(): Boolean {
    return when (this) {
        "x2",
        "x3",
        "x4",
        "scale",
        "font",
        "flip",
        "tada",
        "jelly",
        "twitch",
        "shake",
        "spin",
        "jump",
        "bounce",
        "sparkle",
        -> true
        else -> isMfmForegroundName() ||
            isMfmBackgroundName() ||
            isMfmRotateName() ||
            isMfmPositionName() ||
            isMfmOpacityName() ||
            isMfmBorderName()
    }
}

private fun String.isSupportedBraceMfmName(): Boolean {
    return when (this) {
        "small",
        "x2",
        "x3",
        "x4",
        "scale",
        "code",
        "blur",
        "blurry",
        "rainbow",
        "plain",
        "ruby",
        "link",
        "unixtime",
        "unicode",
        "font",
        "flip",
        "tada",
        "jelly",
        "twitch",
        "shake",
        "spin",
        "jump",
        "bounce",
        "sparkle",
        -> true
        else -> isMfmForegroundName() ||
            isMfmBackgroundName() ||
            isMfmRotateName() ||
            isMfmPositionName() ||
            isMfmOpacityName() ||
            isMfmBorderName()
    }
}

private fun List<InlineMarkdownSpan>.applyMfmStyle(
    markdownStyle: InlineMarkdownStyle,
    mfmStyle: MfmInlineStyle,
    richTextEnabled: Boolean,
): List<InlineMarkdownSpan> {
    val nonTextMfmStyle = mfmStyle.withMarkdownScale(markdownStyle)
    return map { span ->
        when (span) {
            is InlineMarkdownSpan.Text -> {
                val combinedStyle = combineMarkdownStyle(markdownStyle, span.style)
                span.copy(
                    style = combinedStyle,
                    richTextEnabled = span.richTextEnabled && richTextEnabled,
                    mfmStyle = span.mfmStyle.merge(
                        if (span.style == InlineMarkdownStyle.Plain) {
                            mfmStyle
                        } else {
                            mfmStyle.merge(markdownStyle.toMfmScaleStyle())
                        },
                    ),
                )
            }
            is InlineMarkdownSpan.Link -> span.copy(
                mfmStyle = span.mfmStyle.merge(nonTextMfmStyle),
            )
            is InlineMarkdownSpan.Ruby -> span.copy(
                mfmStyle = span.mfmStyle.merge(nonTextMfmStyle),
            )
        }
    }
}

private fun MfmInlineStyle.withMarkdownScale(markdownStyle: InlineMarkdownStyle): MfmInlineStyle {
    return when (markdownStyle) {
        InlineMarkdownStyle.X2,
        InlineMarkdownStyle.X3,
        InlineMarkdownStyle.X4,
        -> merge(markdownStyle.toMfmScaleStyle())
        else -> this
    }
}

private fun combineMarkdownStyle(
    outer: InlineMarkdownStyle,
    inner: InlineMarkdownStyle,
): InlineMarkdownStyle {
    return if (inner == InlineMarkdownStyle.Plain) outer else inner
}

private fun MfmInlineStyle.merge(outer: MfmInlineStyle): MfmInlineStyle {
    return copy(
        foregroundColor = foregroundColor ?: outer.foregroundColor,
        backgroundColor = backgroundColor ?: outer.backgroundColor,
        font = if (font == MfmFont.Default) outer.font else font,
        rotateDeg = rotateDeg + outer.rotateDeg,
        positionX = positionX + outer.positionX,
        positionY = positionY + outer.positionY,
        scaleX = scaleX * outer.scaleX,
        scaleY = scaleY * outer.scaleY,
        flipX = flipX xor outer.flipX,
        flipY = flipY xor outer.flipY,
        opacity = opacity * outer.opacity,
        borderColor = borderColor ?: outer.borderColor,
        borderWidthDp = if (borderWidthDp > 0f) borderWidthDp else outer.borderWidthDp,
        borderRadiusDp = if (borderWidthDp > 0f) borderRadiusDp else outer.borderRadiusDp,
        effect = if (effect == MfmEffect.None) outer.effect else effect,
        effectDurationMillis = effectDurationMillis ?: outer.effectDurationMillis,
        effectReverse = effectReverse xor outer.effectReverse,
        effectAlternate = effectAlternate || outer.effectAlternate,
    )
}

private fun MfmInlineStyle.withoutOverlongEffect(textLength: Int): MfmInlineStyle {
    return if (effect != MfmEffect.None && textLength > MAX_MFM_ANIMATED_CHARS) {
        copy(
            effect = MfmEffect.None,
            effectDurationMillis = null,
            effectReverse = false,
            effectAlternate = false,
        )
    } else {
        this
    }
}

private fun InlineMarkdownStyle.toMfmScaleStyle(): MfmInlineStyle {
    val scale = when (this) {
        InlineMarkdownStyle.X2 -> 1.35f
        InlineMarkdownStyle.X3 -> 1.7f
        InlineMarkdownStyle.X4 -> 2.0f
        else -> 1f
    }
    return MfmInlineStyle.Default.copy(scaleX = scale, scaleY = scale)
}

private fun parseMfmOptions(options: String): Map<String, String> {
    if (options.isBlank()) return emptyMap()
    val parsed = LinkedHashMap<String, String>()
    for (part in options.splitMfmOptionParts()) {
        if (parsed.size >= MAX_MFM_OPTION_COUNT) break
        val separator = part.indexOf('=')
        val key = if (separator >= 0) part.substring(0, separator).trim().lowercase() else part.trim().lowercase()
        val value = if (separator >= 0) part.substring(separator + 1).trim().unquoteMfmOptionValue() else ""
        if (key.isNotBlank() && key.length <= MAX_MFM_OPTION_KEY_CHARS) {
            parsed[key] = value.ifBlank { "true" }
        }
    }
    return parsed
}

private fun String.splitMfmOptionParts(): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false
    for (char in this) {
        when {
            escaped -> {
                current.appendBounded(char, MAX_MFM_OPTION_CHARS)
                escaped = false
            }
            quote != null && char == '\\' -> {
                current.appendBounded(char, MAX_MFM_OPTION_CHARS)
                escaped = true
            }
            quote != null && char == quote -> {
                current.appendBounded(char, MAX_MFM_OPTION_CHARS)
                quote = null
            }
            quote != null -> current.appendBounded(char, MAX_MFM_OPTION_CHARS)
            char == '\'' || char == '"' -> {
                current.appendBounded(char, MAX_MFM_OPTION_CHARS)
                quote = char
            }
            char == ',' -> {
                parts += current.toString()
                current.clear()
                if (parts.size >= MAX_MFM_OPTION_COUNT) break
            }
            else -> current.appendBounded(char, MAX_MFM_OPTION_CHARS)
        }
    }
    if (current.isNotEmpty() && parts.size < MAX_MFM_OPTION_COUNT) {
        parts += current.toString()
    }
    return parts
}

private fun String.unquoteMfmOptionValue(): String {
    val trimmed = trim()
    if (trimmed.length < 2) return trimmed
    val quote = trimmed.first()
    if ((quote != '"' && quote != '\'') || trimmed.last() != quote) return trimmed
    val body = trimmed.substring(1, trimmed.lastIndex)
    val result = StringBuilder(body.length)
    var escaped = false
    for (char in body) {
        if (escaped) {
            result.append(char)
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else {
            result.append(char)
        }
    }
    if (escaped) result.append('\\')
    return result.toString().trim()
}

private fun String.splitMfmValueTokens(maxTokens: Int = MAX_MFM_VALUE_TOKEN_COUNT): List<String> {
    if (isBlank() || maxTokens <= 0) return emptyList()
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            escaped -> {
                current.appendBounded(char, MAX_MFM_VALUE_TOKEN_CHARS)
                escaped = false
            }
            quote != null && char == '\\' -> escaped = true
            quote != null && char == quote -> quote = null
            quote != null -> current.appendBounded(char, MAX_MFM_VALUE_TOKEN_CHARS)
            char == '\'' || char == '"' -> quote = char
            char.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                    if (tokens.size == maxTokens - 1) {
                        val rest = substring(index + 1).trim().unquoteMfmOptionValue()
                        if (rest.isNotBlank()) tokens += rest.take(MAX_MFM_VALUE_TOKEN_CHARS)
                        return tokens
                    }
                    if (tokens.size >= maxTokens) return tokens
                }
            }
            else -> current.appendBounded(char, MAX_MFM_VALUE_TOKEN_CHARS)
        }
        index += 1
    }
    if (escaped) current.appendBounded('\\', MAX_MFM_VALUE_TOKEN_CHARS)
    if (current.isNotEmpty() && tokens.size < maxTokens) {
        tokens += current.toString()
    }
    return tokens
}

private fun StringBuilder.appendBounded(char: Char, maxLength: Int) {
    if (length < maxLength) append(char)
}

private fun parseMfmScale(rawName: String): Pair<Float, Float>? {
    val normalizedRawName = rawName.lowercase()
    val options = when {
        normalizedRawName.startsWith("scale.") -> parseMfmOptions(rawName.substringAfter('.'))
        normalizedRawName.startsWith("scale=") -> parseMfmOptions("v=" + rawName.substringAfter('='))
        normalizedRawName.startsWith("x2.") -> parseMfmOptions("v=2," + rawName.substringAfter('.'))
        normalizedRawName.startsWith("x3.") -> parseMfmOptions("v=3," + rawName.substringAfter('.'))
        normalizedRawName.startsWith("x4.") -> parseMfmOptions("v=4," + rawName.substringAfter('.'))
        else -> return null
    }
    val uniform = options["v"]?.toMfmFloat(0.25f, 4f)
    val x = options["x"]?.toMfmFloat(0.25f, 4f) ?: uniform ?: 1f
    val y = options["y"]?.toMfmFloat(0.25f, 4f) ?: uniform ?: 1f
    return x to y
}

private fun parseMfmFont(options: String, optionMap: Map<String, String>): MfmFont {
    val family = optionMap["family"]
        ?: optionMap["f"]
        ?: options.splitMfmOptionParts().firstOrNull()?.substringBefore('=')?.unquoteMfmOptionValue()
    return when (family?.lowercase()) {
        "serif" -> MfmFont.Serif
        "monospace" -> MfmFont.Monospace
        "cursive" -> MfmFont.Cursive
        "fantasy" -> MfmFont.Fantasy
        "emoji" -> MfmFont.Emoji
        "math" -> MfmFont.Math
        else -> MfmFont.Default
    }
}

private fun String.toMfmUnicodeText(): String? {
    val tokens = trim()
        .split(Regex("""[\s,]+"""))
        .filter { it.isNotBlank() }
    if (tokens.isEmpty() || tokens.size > MAX_MFM_UNICODE_CODEPOINTS) return null
    val result = StringBuilder(tokens.size)
    for (token in tokens) {
        val hex = token.removePrefix("U+").removePrefix("u+")
        if (hex.length !in 1..6 || hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
        val codePoint = hex.toIntOrNull(radix = 16) ?: return null
        if (!codePoint.isValidUnicodeScalarValue()) return null
        result.appendUnicodeCodePoint(codePoint)
    }
    return result.toString()
}

private fun String.toMfmUnixTimeText(): String? {
    val raw = trim().substringBefore(' ').substringBefore(',').takeIf { it.isNotBlank() } ?: return null
    val value = raw.toLongOrNull() ?: return null
    val epochMillis = when (raw.length) {
        in 1..10 -> value * 1_000L
        in 11..13 -> value
        else -> return null
    }.takeIf { it in 0L..MAX_MFM_UNIXTIME_EPOCH_MILLIS } ?: return null
    return runCatching { Instant.fromEpochMilliseconds(epochMillis).toString().toLocalCompactDateLabel() }.getOrNull()
}

private fun Int.isValidUnicodeScalarValue(): Boolean {
    return this in 0..0x10FFFF && this !in 0xD800..0xDFFF
}

private fun StringBuilder.appendUnicodeCodePoint(codePoint: Int) {
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
        return
    }
    val scalar = codePoint - 0x10000
    append((0xD800 + (scalar shr 10)).toChar())
    append((0xDC00 + (scalar and 0x3FF)).toChar())
}

private fun Map<String, String>.firstMfmColor(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key -> this[key]?.normalizeMfmColor() }
}

private fun Map<String, String>.firstMfmFloat(min: Float, max: Float, vararg keys: String): Float? {
    return keys.firstNotNullOfOrNull { key -> this[key]?.toMfmFloat(min, max) }
}

private fun Map<String, String>.firstMfmAnimationDurationMillis(defaultMillis: Int, vararg keys: String): Int? {
    return keys.firstNotNullOfOrNull { key -> this[key]?.toMfmAnimationDurationMillis(defaultMillis) }
}

private fun Map<String, String>.isMfmTruthy(vararg keys: String): Boolean {
    return keys.any { key ->
        val value = this[key]?.trim()?.lowercase() ?: return@any false
        value.isEmpty() || value == "true" || value == "1" || value == "yes" || value == "y" || value == "on"
    }
}

private data class MfmLinkValue(
    val label: String,
    val url: String,
)

private fun parseMfmLinkValue(value: String): MfmLinkValue? {
    val tokens = value.splitMfmValueTokens(maxTokens = 2)
    if (tokens.size == 2 && tokens[0].isSafeUrl() && tokens[1].isNotBlank()) {
        return MfmLinkValue(label = tokens[1].renderMfmLinkLabel(), url = tokens[0])
    }
    if (tokens.size == 2 && tokens[1].isSafeUrl() && tokens[0].isNotBlank()) {
        return MfmLinkValue(label = tokens[0].renderMfmLinkLabel(), url = tokens[1])
    }
    val lastSpace = value.lastIndexOfAny(charArrayOf(' ', '\t', '\n', '\r'))
    if (lastSpace <= 0 || lastSpace == value.lastIndex) return null
    val label = value.substring(0, lastSpace).trim().unquoteMfmOptionValue()
    val url = value.substring(lastSpace + 1).trim().unquoteMfmOptionValue()
    if (!url.isSafeUrl() || label.isBlank()) return null
    return MfmLinkValue(label = label.renderMfmLinkLabel(), url = url)
}

private fun String.renderMfmLinkLabel(): String {
    if (isBlank()) return this
    return parseInlineMarkdown(this, depth = 1)
        .joinToString(separator = "") { span -> span.plainTextValue() }
        .unescapeMarkdownLinkLabel()
        .ifBlank { this }
}

private fun String.isSafeUrl(): Boolean {
    val clean = trim()
    return clean.startsWith("https://", ignoreCase = true) || clean.startsWith("http://", ignoreCase = true)
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
            last.richTextEnabled == span.richTextEnabled &&
            last.mfmStyle == span.mfmStyle
        ) {
            merged[merged.lastIndex] = last.copy(value = last.value + span.value)
        } else {
            merged.add(span)
        }
    }
    return merged
}

private fun InlineMarkdownSpan.plainTextValue(): String {
    return when (this) {
        is InlineMarkdownSpan.Text -> value
        is InlineMarkdownSpan.Link -> label
        is InlineMarkdownSpan.Ruby -> base + annotation
    }
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

private fun TextStyle.markdownMfm(mfmStyle: MfmInlineStyle): TextStyle {
    return when (mfmStyle.font) {
        MfmFont.Default -> this
        MfmFont.Serif -> copy(fontFamily = FontFamily.Serif)
        MfmFont.Monospace -> copy(fontFamily = FontFamily.Monospace)
        MfmFont.Cursive -> copy(fontFamily = FontFamily.Cursive)
        MfmFont.Fantasy -> copy(fontFamily = FontFamily.Serif)
        MfmFont.Emoji -> copy(fontSize = if (fontSize.isTextUnitSpecified) fontSize * 1.08f else 18.sp)
        MfmFont.Math -> copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
    }
}

private fun TextStyle.scaledText(scale: Float): TextStyle {
    return if (fontSize.isTextUnitSpecified) {
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
        if (text.startsWithHttpUrl(index)) return false
        when (char) {
            '`', '[', '*', '_', '<', '@', '#' -> return false
            ':' -> if (parseCustomEmojiMatch(text, index, emojiUrls) != null) return false
            '$' -> if (text.startsWith("$[", index) || text.startsWith("\${", index)) return false
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
        if (text.startsWithHttpUrl(index)) return true
        when (text[index]) {
            '@', '#' -> return true
            ':' -> if (parseCustomEmojiMatch(text, index, emojiUrls) != null) return true
        }
        index += 1
    }
    return false
}

private fun String.startsWithHttpUrl(index: Int): Boolean {
    return startsWith("https://", startIndex = index, ignoreCase = true) ||
        startsWith("http://", startIndex = index, ignoreCase = true)
}

private val htmlAltAttributePattern = Regex("""(?i)\balt\s*=\s*(['"])(.*?)\1""")
private val htmlAnyTagPattern = Regex("""<[^>]+>""")
private val htmlBreakPattern = Regex("""(?i)<br\s*/?>""")
private val htmlHrefAttributePattern = Regex("""(?i)\bhref\s*=\s*(['"])(.*?)\1""")
private val htmlParagraphPattern = Regex("""(?i)</?p(?:\s+[^>]*)?>""")
private val htmlRubyFallbackPattern = Regex("""(?i)<rp>.*?</rp>""")
private val htmlSpanPattern = Regex("""(?i)</?span(?:\s+[^>]*)?>""")
private val htmlTitleAttributePattern = Regex("""(?i)\btitle\s*=\s*(['"])(.*?)\1""")
private val mfmColorPattern = Regex("""[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}""")
private val wrappedHtmlOpenPatterns = listOf("blockquote", "center", "code", "p", "pre", "quote")
    .associateWith { tag -> Regex("""(?i)^<$tag(?:\s+[^>]*)?>""") }
private var markdownBlocksCache: Map<String, List<MarkdownBlock>> = emptyMap()
private var richTextSegmentsCache: Map<String, List<RichTextSegment>> = emptyMap()
private const val MAX_MFM_NESTING_DEPTH = 16
private const val MAX_MFM_ANIMATED_CHARS = 240
private const val MAX_MFM_ANIMATED_SPANS_PER_TEXT = 3
private const val MAX_MFM_FUNCTION_BODY_CHARS = 4_096
private const val MAX_MFM_MALFORMED_FALLBACK_SCAN_CHARS = 4_096
private const val MAX_INLINE_MARKDOWN_SPANS = 180
private const val MAX_INLINE_CODE_CHARS = 1_024
private const val MAX_MARKDOWN_LINK_LABEL_CHARS = 512
private const val MAX_MARKDOWN_LINK_URL_CHARS = 2_048
private const val MAX_MFM_OPTION_COUNT = 24
private const val MAX_MFM_OPTION_CHARS = 128
private const val MAX_MFM_OPTION_KEY_CHARS = 32
private const val MAX_MFM_VALUE_TOKEN_COUNT = 8
private const val MAX_MFM_VALUE_TOKEN_CHARS = 512
private const val MAX_MFM_UNICODE_CODEPOINTS = 16
private const val MAX_MFM_UNIXTIME_EPOCH_MILLIS = 4_102_444_800_000L
private const val MIN_MFM_ANIMATION_DURATION_MILLIS = 120
private const val MAX_MFM_ANIMATION_DURATION_MILLIS = 5_000
private const val MAX_INLINE_HTML_TAG_CHARS = 512
private const val MAX_INLINE_HTML_BODY_CHARS = 4_096
private const val MAX_RICH_TEXT_CACHE_ENTRIES = 256
private const val MAX_RICH_TEXT_CACHEABLE_CHARS = 8_192
private const val MAX_RICH_TEXT_EMOJI_CACHE_DEPENDENCIES = 64

private fun String.normalizeMfmColor(): String? {
    val clean = trim().removePrefix("#")
    if (!mfmColorPattern.matches(clean)) return null
    return when (clean.length) {
        3,
        4,
        -> clean.flatMap { char -> listOf(char, char) }.joinToString(separator = "")
        else -> clean
    }.lowercase()
}

private fun String.toComposeColor(): Color? {
    val clean = normalizeMfmColor() ?: return null
    val argb = if (clean.length == 6) "FF$clean" else clean
    return runCatching { Color(argb.toLong(16).toULong()) }.getOrNull()
}

private fun String.toMfmFloat(min: Float, max: Float): Float? {
    val value = toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
    return value.coerceIn(min, max)
}

private fun String.toMfmAnimationDurationMillis(defaultMillis: Int): Int? {
    val clean = trim()
    if (clean.endsWith("s", ignoreCase = true)) {
        val seconds = clean.dropLast(1).toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
        return (seconds * 1_000).toInt().coerceIn(MIN_MFM_ANIMATION_DURATION_MILLIS, MAX_MFM_ANIMATION_DURATION_MILLIS)
    }
    val speed = clean.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
    return (defaultMillis / speed).toInt().coerceIn(MIN_MFM_ANIMATION_DURATION_MILLIS, MAX_MFM_ANIMATION_DURATION_MILLIS)
}

internal fun String.truncateRichTextPreview(maxChars: Int?): String {
    return truncateRichTextPreviewText(maxChars)
}

private const val MAX_MFM_SYNTAX_DETECT_SCAN_CHARS = 4_096
