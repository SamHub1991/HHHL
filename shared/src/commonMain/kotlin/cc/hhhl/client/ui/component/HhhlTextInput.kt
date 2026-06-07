package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlTextInputSingleLineMinHeight = 52.dp
internal val HhhlTextInputMultiLineMinHeight = 112.dp
internal val HhhlTextInputCornerRadius = 17.dp
internal val HhhlTextInputHorizontalPadding = 17.dp
internal val HhhlTextInputVerticalPadding = 13.dp

@Composable
fun HhhlTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    leading: (@Composable () -> Unit)? = null,
    minHeight: Dp? = null,
    horizontalPadding: Dp = HhhlTextInputHorizontalPadding,
    verticalPadding: Dp = HhhlTextInputVerticalPadding,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    var lastExternalValue by remember { mutableStateOf(value) }
    var latestLocalText by remember { mutableStateOf(value) }

    LaunchedEffect(value, focused) {
        val shouldSyncExternalValue = shouldSyncHhhlTextInputExternalValue(
            focused = focused,
            externalValue = value,
            latestLocalText = latestLocalText,
            lastExternalValue = lastExternalValue,
        )
        if (value != lastExternalValue) {
            lastExternalValue = value
        }
        if (shouldSyncExternalValue) {
            if (textFieldValue.text != value) {
                textFieldValue = TextFieldValue(
                    text = value,
                    selection = if (focused) {
                        textFieldValue.selection.constrainToTextLength(value.length)
                    } else {
                        TextRange(value.length)
                    },
                )
            }
            latestLocalText = value
        }
    }

    val colors = LocalHhhlColors.current
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    val defaultMinHeight = if (singleLine || minLines <= 1) {
        HhhlTextInputSingleLineMinHeight
    } else {
        HhhlTextInputMultiLineMinHeight
    }
    val resolvedMinHeight = minHeight ?: defaultMinHeight + ((fontScale - 1f) * 16f).dp
    val shape = RoundedCornerShape(HhhlTextInputCornerRadius)
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val baseContainer = if (isDarkSurface) {
        neutralDarkInputContainer(
            pageBackground = colors.pageBackground,
            surface = colors.surface,
        )
    } else {
        colors.inputBackground.blendWith(colors.surfaceElevated, 0.34f)
    }
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> baseContainer.copy(alpha = if (isDarkSurface) 0.46f else 0.52f).asOpaqueOver(colors.pageBackground)
            focused -> baseContainer.copy(alpha = if (isDarkSurface) 0.92f else 0.92f).asOpaqueOver(colors.pageBackground)
            else -> baseContainer.copy(alpha = if (isDarkSurface) 0.72f else 0.72f).asOpaqueOver(colors.pageBackground)
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-input-container",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused && isDarkSurface -> neutralDarkInputBorder(
                surface = colors.surface,
                surfaceElevated = colors.surfaceElevated,
                textMuted = colors.textMuted,
            ).copy(alpha = 0.86f)
            focused -> colors.inputFocusedBorder.copy(alpha = 0.72f)
            else -> colors.inputBorder.copy(alpha = if (isDarkSurface) 0.58f else 0.48f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-input-border",
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        if (label != null) {
            Text(
                text = label,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
        }
        BasicTextField(
            value = textFieldValue,
            onValueChange = { nextValue ->
                val textChanged = nextValue.text != textFieldValue.text
                textFieldValue = nextValue
                latestLocalText = nextValue.text
                if (textChanged && nextValue.text != lastExternalValue) {
                    onValueChange(nextValue.text)
                }
            },
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            textStyle = textStyle.copy(
                color = if (enabled) {
                    colors.textPrimary
                } else {
                    colors.textMuted
                },
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .defaultMinSize(
                            minHeight = resolvedMinHeight,
                        )
                        .background(containerColor, shape)
                        .border(1.dp, borderColor, shape)
                        .padding(
                            horizontal = horizontalPadding,
                            vertical = verticalPadding,
                        ),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading?.invoke()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (textFieldValue.text.isBlank()) {
                            Text(
                                text = placeholder,
                                color = colors.textMuted,
                                style = textStyle,
                                maxLines = if (singleLine) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

internal fun shouldSyncHhhlTextInputExternalValue(
    focused: Boolean,
    externalValue: String,
    latestLocalText: String,
    lastExternalValue: String,
): Boolean {
    val externalValueChanged = externalValue != lastExternalValue
    val hasPendingLocalEdit = latestLocalText != lastExternalValue
    return !focused ||
        externalValue == latestLocalText ||
        (externalValueChanged && (!hasPendingLocalEdit || externalValue.isEmpty()))
}

private fun TextRange.constrainToTextLength(textLength: Int): TextRange {
    val constrainedStart = start.coerceIn(0, textLength)
    val constrainedEnd = end.coerceIn(0, textLength)
    return TextRange(constrainedStart, constrainedEnd)
}

private fun Color.blendWith(other: Color, otherRatio: Float): Color {
    val ratio = otherRatio.coerceIn(0f, 1f)
    val selfRatio = 1f - ratio
    return Color(
        red = red * selfRatio + other.red * ratio,
        green = green * selfRatio + other.green * ratio,
        blue = blue * selfRatio + other.blue * ratio,
        alpha = alpha * selfRatio + other.alpha * ratio,
    )
}

private fun Color.asOpaqueOver(background: Color): Color {
    val sourceAlpha = alpha.coerceIn(0f, 1f)
    val backgroundAlpha = background.alpha.coerceIn(0f, 1f)
    val outputAlpha = sourceAlpha + backgroundAlpha * (1f - sourceAlpha)
    if (outputAlpha <= 0f) return Color.Transparent
    return Color(
        red = (red * sourceAlpha + background.red * backgroundAlpha * (1f - sourceAlpha)) / outputAlpha,
        green = (green * sourceAlpha + background.green * backgroundAlpha * (1f - sourceAlpha)) / outputAlpha,
        blue = (blue * sourceAlpha + background.blue * backgroundAlpha * (1f - sourceAlpha)) / outputAlpha,
        alpha = 1f,
    )
}

private fun neutralDarkInputContainer(
    pageBackground: Color,
    surface: Color,
): Color {
    return pageBackground.blendWith(Color.White, 0.075f)
        .blendWith(surface, 0.18f)
        .desaturate(0.80f)
}

private fun neutralDarkInputBorder(
    surface: Color,
    surfaceElevated: Color,
    textMuted: Color,
): Color {
    return surfaceElevated.blendWith(surface, 0.26f)
        .blendWith(textMuted, 0.24f)
        .desaturate(0.50f)
}

private fun Color.desaturate(amount: Float): Color {
    val ratio = amount.coerceIn(0f, 1f)
    val grey = red * 0.299f + green * 0.587f + blue * 0.114f
    return Color(
        red = red * (1f - ratio) + grey * ratio,
        green = green * (1f - ratio) + grey * ratio,
        blue = blue * (1f - ratio) + grey * ratio,
        alpha = alpha,
    )
}
