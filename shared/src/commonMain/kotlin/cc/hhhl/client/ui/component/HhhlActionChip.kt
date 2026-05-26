package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlControlCornerRadius = 8.dp
internal val HhhlControlMinHeight = 30.dp
internal val HhhlControlMinWidth = 34.dp
internal val HhhlActionChipMinHeight = HhhlControlMinHeight
internal val HhhlActionChipHorizontalPadding = 8.dp
internal val HhhlActionChipVerticalPadding = 5.dp
internal val HhhlActionChipMaxWidth = 184.dp

@Composable
fun HhhlActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val containerColor = when {
        !enabled -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.56f)
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.72f)
    }

    Row(
        modifier = modifier
            .widthIn(min = HhhlControlMinWidth, max = HhhlActionChipMaxWidth)
            .clip(RoundedCornerShape(HhhlControlCornerRadius))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = HhhlActionChipMinHeight)
            .padding(
                horizontal = HhhlActionChipHorizontalPadding,
                vertical = HhhlActionChipVerticalPadding,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> LocalHhhlColors.current.subtleText
                emphasized -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onBackground
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
