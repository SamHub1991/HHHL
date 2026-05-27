package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlTextInputSingleLineMinHeight = 42.dp
internal val HhhlTextInputMultiLineMinHeight = 80.dp
internal val HhhlTextInputCornerRadius = 14.dp
internal val HhhlTextInputHorizontalPadding = 12.dp
internal val HhhlTextInputVerticalPadding = 8.dp

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
    val shape = RoundedCornerShape(HhhlTextInputCornerRadius)
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val containerColor by animateColorAsState(
        targetValue = if (isDarkSurface) {
            when {
                !enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.46f)
                focused -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.82f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
            }
        } else {
            LocalHhhlColors.current.inputBackground.copy(
                alpha = when {
                    !enabled -> 0.52f
                    focused -> 0.96f
                    else -> 0.78f
                },
            )
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-input-container",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.30f else 0.16f)
            isDarkSurface -> Color.White.copy(alpha = 0.08f)
            else -> LocalHhhlColors.current.divider.copy(alpha = 0.58f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-input-border",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            focused && isDarkSurface -> 1.dp
            isDarkSurface -> 0.dp
            focused -> 1.dp
            else -> 0.dp
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-input-elevation",
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        if (label != null) {
            Text(
                text = label,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            textStyle = textStyle.copy(
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    LocalHhhlColors.current.subtleText
                },
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation, shape, clip = false)
                        .clip(shape)
                        .defaultMinSize(
                            minHeight = minHeight ?: if (singleLine || minLines <= 1) {
                                HhhlTextInputSingleLineMinHeight
                            } else {
                                HhhlTextInputMultiLineMinHeight
                            },
                        )
                        .background(color = containerColor, shape = shape)
                        .border(1.dp, borderColor, shape)
                        .padding(
                            horizontal = horizontalPadding,
                            vertical = verticalPadding,
                        ),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading?.invoke()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                color = LocalHhhlColors.current.subtleText,
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
