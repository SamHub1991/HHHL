package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

@Composable
fun HhhlSegmentedControl(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(hhhlNeutralControlContainerColor(selected = false))
            .border(1.dp, hhhlNeutralControlBorderColor(selected = false), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun HhhlSegmentedItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    selectedUsesPrimary: Boolean = false,
) {
    val selectedColor = if (selectedUsesPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (selected) hhhlNeutralControlContainerColor(selected = true)
                else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (selected) hhhlNeutralControlBorderColor(selected = true) else Color.Transparent,
                shape = RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) selectedColor else LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HhhlControlBadge(count = badgeCount, selected = selected)
    }
}

@Composable
fun HhhlFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: androidx.compose.ui.unit.Dp = 58.dp,
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(16.dp))
            .background(hhhlNeutralControlContainerColor(selected = selected))
            .border(1.dp, hhhlNeutralControlBorderColor(selected = selected), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HhhlControlBadge(
    count: Int,
    selected: Boolean,
) {
    if (count <= 0) return
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .padding(start = 6.dp)
            .height(18.dp)
            .widthIn(min = 18.dp)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isHhhlDarkSurface()) 0.20f else 0.16f)
                } else {
                    hhhlNeutralControlContainerColor(selected = false)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isHhhlDarkSurface()) 0.22f else 0.14f)
                } else {
                    hhhlNeutralControlBorderColor(selected = false)
                },
                shape = shape,
            )
            .padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = if (selected) MaterialTheme.colorScheme.primary else LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun hhhlNeutralControlContainerColor(selected: Boolean): Color {
    val isDarkSurface = isHhhlDarkSurface()
    return when {
        selected && isDarkSurface -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        selected -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        isDarkSurface -> MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
        else -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.66f)
    }
}

@Composable
fun hhhlNeutralControlBorderColor(selected: Boolean): Color {
    val isDarkSurface = isHhhlDarkSurface()
    return when {
        selected && isDarkSurface -> Color.White.copy(alpha = 0.10f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        isDarkSurface -> Color.White.copy(alpha = 0.055f)
        else -> LocalHhhlColors.current.divider.copy(alpha = 0.26f)
    }
}

@Composable
private fun isHhhlDarkSurface(): Boolean {
    return MaterialTheme.colorScheme.background.luminance() < 0.18f
}
