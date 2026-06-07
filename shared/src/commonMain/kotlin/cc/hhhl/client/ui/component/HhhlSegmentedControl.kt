package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cc.hhhl.client.theme.LocalHhhlColors

@Composable
fun HhhlSegmentedControl(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = isHhhlDarkSurface()
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .shadow(
                elevation = if (isDarkSurface) 1.dp else 2.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(shape)
            .background(hhhlNeutralControlContainerColor(selected = false).copy(alpha = if (isDarkSurface) 0.88f else 0.82f))
            .border(1.dp, colors.border.copy(alpha = if (isDarkSurface) 0.32f else 0.30f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun HhhlAnimatedSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badgeCounts: List<Int> = emptyList(),
    itemBaseHeight: Dp = 42.dp,
) {
    if (labels.isEmpty()) return
    val colors = LocalHhhlColors.current
    val isDarkSurface = isHhhlDarkSurface()
    val outerShape = RoundedCornerShape(23.dp)
    val sliderShape = RoundedCornerShape(19.dp)
    val itemHeight = scaledSegmentedControlHeight(itemBaseHeight)
    val selectedContainerColor = hhhlNeutralControlContainerColor(selected = true)
    val sliderContainerColor = selectedContainerColor.copy(alpha = if (isDarkSurface) 0.92f else 0.88f)
    val safeSelectedIndex = selectedIndex.coerceIn(0, labels.lastIndex)

    BoxWithConstraints(
        modifier = modifier
            .height(itemHeight + 4.dp)
            .shadow(
                elevation = if (isDarkSurface) 1.dp else 2.dp,
                shape = outerShape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(outerShape)
            .background(hhhlNeutralControlContainerColor(selected = false).copy(alpha = if (isDarkSurface) 0.88f else 0.82f))
            .border(1.dp, colors.border.copy(alpha = if (isDarkSurface) 0.34f else 0.30f), outerShape)
            .padding(3.dp),
    ) {
        val itemWidth = maxWidth / labels.size
        val sliderOffset by animateDpAsState(
            targetValue = itemWidth * safeSelectedIndex,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "hhhl-segmented-slider-offset",
        )

        Box(
            modifier = Modifier
                .zIndex(0f)
                .offset(x = sliderOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = if (isDarkSurface) 0.dp else 1.dp,
                    shape = sliderShape,
                    clip = false,
                )
                .clip(sliderShape)
                .background(sliderContainerColor)
                .border(
                    width = 1.dp,
                    color = hhhlNeutralControlBorderColor(selected = true).copy(alpha = if (isDarkSurface) 0.34f else 0.22f),
                    shape = sliderShape,
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                HhhlAnimatedSegmentedItem(
                    label = label,
                    selected = index == safeSelectedIndex,
                    badgeCount = badgeCounts.getOrNull(index) ?: 0,
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f),
                    itemBaseHeight = itemBaseHeight,
                )
            }
        }
    }
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
    val colors = LocalHhhlColors.current
    val itemHeight = scaledSegmentedControlHeight(42.dp)
    val selectedContainerColor = hhhlNeutralControlContainerColor(selected = true)
    val selectedColor = if (selectedUsesPrimary) {
        hhhlReadableOnControlColor(selectedContainerColor, colors.accent)
    } else {
        hhhlReadableOnControlColor(selectedContainerColor, colors.textPrimary)
    }
    Row(
        modifier = modifier
            .height(itemHeight)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier.background(selectedContainerColor)
                else Modifier.background(Color.Transparent),
            )
            .border(
                width = 1.dp,
                color = if (selected) hhhlNeutralControlBorderColor(selected = true) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) selectedColor else colors.textMuted,
            style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
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
    minWidth: Dp = 58.dp,
) {
    val colors = LocalHhhlColors.current
    val itemHeight = scaledSegmentedControlHeight(38.dp)
    val containerColor = hhhlNeutralControlContainerColor(selected = selected)
    Row(
        modifier = modifier
            .height(itemHeight)
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(1.dp, hhhlNeutralControlBorderColor(selected = selected), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) hhhlReadableOnControlColor(containerColor, colors.accent) else colors.textMuted,
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 17.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun HhhlAnimatedSegmentedItem(
    label: String,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemBaseHeight: Dp = 42.dp,
) {
    val colors = LocalHhhlColors.current
    val itemHeight = scaledSegmentedControlHeight(itemBaseHeight)
    val selectedContainerColor = hhhlNeutralControlContainerColor(selected = true)
    val targetTextColor = if (selected) {
        hhhlReadableOnControlColor(selectedContainerColor, colors.textPrimary)
    } else {
        colors.textMuted
    }
    val textColor = animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "hhhl-segmented-text-color",
    )

    Row(
        modifier = modifier
            .height(itemHeight)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = textColor.value,
            style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        HhhlControlBadge(count = badgeCount, selected = selected)
    }
}

@Composable
private fun HhhlControlBadge(
    count: Int,
    selected: Boolean,
) {
    if (count <= 0) return
    val colors = LocalHhhlColors.current
    val containerColor = if (selected) {
        colors.accentSoft.copy(alpha = if (isHhhlDarkSurface()) 0.86f else 0.78f)
    } else {
        hhhlNeutralControlContainerColor(selected = false)
    }
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    val badgeHeight = 18.dp + ((fontScale - 1f) * 8f).dp
    val badgeMinWidth = 18.dp + ((fontScale - 1f) * 8f).dp
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .padding(start = 6.dp)
            .height(badgeHeight)
            .widthIn(min = badgeMinWidth)
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (selected) {
                    colors.focusRing.copy(alpha = if (isHhhlDarkSurface()) 0.54f else 0.34f)
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
            color = if (selected) hhhlReadableOnControlColor(containerColor, colors.accent) else colors.textMuted,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun hhhlNeutralControlContainerColor(selected: Boolean): Color {
    val isDarkSurface = isHhhlDarkSurface()
    val colors = LocalHhhlColors.current
    return when {
        selected -> colors.buttonSelectedBackground
        isDarkSurface -> colors.inputBackground.copy(alpha = 0.36f)
        else -> colors.inputBackground.copy(alpha = 0.58f)
    }
}

@Composable
fun hhhlNeutralControlBorderColor(selected: Boolean): Color {
    val isDarkSurface = isHhhlDarkSurface()
    val colors = LocalHhhlColors.current
    return when {
        selected -> colors.focusRing.copy(alpha = if (isDarkSurface) 0.46f else 0.30f)
        isDarkSurface -> colors.border.copy(alpha = 0.24f)
        else -> colors.border.copy(alpha = 0.22f)
    }
}

@Composable
private fun isHhhlDarkSurface(): Boolean {
    return LocalHhhlColors.current.pageBackground.luminance() < 0.18f
}

@Composable
private fun scaledSegmentedControlHeight(base: Dp): Dp {
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    return base + ((fontScale - 1f) * 12f).dp
}
