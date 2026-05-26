package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlTextInputSingleLineMinHeight = 44.dp
internal val HhhlTextInputMultiLineMinHeight = 88.dp
internal val HhhlTextInputCornerRadius = 10.dp
internal val HhhlTextInputHorizontalPadding = 12.dp
internal val HhhlTextInputVerticalPadding = 9.dp

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
) {
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
            textStyle = textStyle.copy(
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    LocalHhhlColors.current.subtleText
                },
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(
                            minHeight = minHeight ?: if (singleLine || minLines <= 1) {
                                HhhlTextInputSingleLineMinHeight
                            } else {
                                HhhlTextInputMultiLineMinHeight
                            },
                        )
                        .background(
                            color = LocalHhhlColors.current.inputBackground.copy(
                                alpha = if (enabled) 0.78f else 0.46f,
                            ),
                            shape = RoundedCornerShape(HhhlTextInputCornerRadius),
                        )
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
