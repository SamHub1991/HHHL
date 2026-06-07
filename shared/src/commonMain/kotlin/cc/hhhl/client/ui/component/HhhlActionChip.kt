package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlControlCornerRadius = 13.dp
internal val HhhlControlMinHeight = 40.dp
internal val HhhlControlMinWidth = 44.dp
internal val HhhlIconActionCornerRadius = 999.dp
internal val HhhlIconActionIdleElevation = 0.dp
internal val HhhlIconActionEmphasizedElevation = 0.dp
internal val HhhlIconActionDarkIdleElevation = 0.dp
internal val HhhlIconActionDarkEmphasizedElevation = 0.dp
internal val HhhlControlHighlightAlpha = 0.08f
internal val HhhlActionChipMinHeight = HhhlControlMinHeight
internal val HhhlActionChipHorizontalPadding = 15.dp
internal val HhhlActionChipVerticalPadding = 6.dp
internal val HhhlActionChipMaxWidth = 200.dp
internal val HhhlIconActionButtonSize = 42.dp
internal val HhhlIconActionButtonIconSize = 20.dp
internal val HhhlTextButtonMinHeight = 40.dp
internal val HhhlTextButtonHorizontalPadding = 16.dp
internal val HhhlTextButtonVerticalPadding = 8.dp
internal val HhhlTextButtonCornerRadius = 999.dp

@Composable
fun HhhlActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val minHeight = scaledControlMinHeight(HhhlActionChipMinHeight)
    val targetContainerColor = when {
        !enabled -> Color.Transparent
        emphasized -> colors.accent.copy(alpha = if (isDarkSurface) 0.08f else 0.06f)
        else -> Color.Transparent
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "chip-container",
    )
    val targetContentColor = when {
        !enabled -> colors.textMuted
        emphasized -> colors.accent
        else -> colors.textPrimary
    }
    val borderColor by animateColorAsState(
        targetValue = when {
            emphasized -> colors.focusRing.copy(alpha = if (isDarkSurface) 0.28f else 0.22f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160),
        label = "chip-border",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            else -> HhhlIconActionIdleElevation
        },
        animationSpec = tween(durationMillis = 160),
        label = "chip-elevation",
    )

    Row(
        modifier = modifier
            .widthIn(min = HhhlControlMinWidth, max = HhhlActionChipMaxWidth)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(HhhlControlCornerRadius),
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(RoundedCornerShape(HhhlControlCornerRadius))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(HhhlControlCornerRadius))
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = minHeight)
            .padding(
                horizontal = HhhlActionChipHorizontalPadding,
                vertical = HhhlActionChipVerticalPadding,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = targetContentColor,
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 18.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
fun HhhlTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val minHeight = scaledControlMinHeight(HhhlTextButtonMinHeight)
    val targetContainerColor = containerColor ?: when {
        else -> Color.Transparent
    }
    val resolvedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "text-button-container",
    )
    val resolvedContentColor by animateColorAsState(
        targetValue = contentColor ?: when {
            !enabled -> colors.textMuted
            destructive -> colors.danger
            emphasized -> colors.accent
            else -> colors.textPrimary
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-button-content",
    )
    val resolvedBorderColor by animateColorAsState(
        targetValue = borderColor ?: when {
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-button-border",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            else -> HhhlIconActionIdleElevation
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-button-elevation",
    )
    val shape = RoundedCornerShape(HhhlTextButtonCornerRadius)

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(shape)
            .background(resolvedContainerColor)
            .border(1.dp, resolvedBorderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = HhhlTextButtonHorizontalPadding,
                vertical = HhhlTextButtonVerticalPadding,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun scaledControlMinHeight(base: Dp): Dp {
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    return base + ((fontScale - 1f) * 14f).dp
}

@Composable
fun HhhlIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val targetContainerColor = when {
        !enabled -> Color.Transparent
        emphasized -> colors.accent.copy(alpha = if (isDarkSurface) 0.08f else 0.06f)
        else -> Color.Transparent
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "icon-action-container",
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textMuted
            emphasized -> colors.accent
            else -> colors.textPrimary
        },
        animationSpec = tween(durationMillis = 160),
        label = "icon-action-color",
    )

    Row(
        modifier = modifier
            .size(HhhlIconActionButtonSize)
            .shadow(
                elevation = when {
                    !enabled -> 0.dp
                    isDarkSurface && emphasized -> HhhlIconActionDarkEmphasizedElevation
                    isDarkSurface -> HhhlIconActionDarkIdleElevation
                    emphasized -> HhhlIconActionEmphasizedElevation
                    else -> HhhlIconActionIdleElevation
                },
                shape = RoundedCornerShape(HhhlIconActionCornerRadius),
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(RoundedCornerShape(HhhlIconActionCornerRadius))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (emphasized) colors.focusRing.copy(alpha = if (isDarkSurface) 0.22f else 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(HhhlIconActionCornerRadius),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(HhhlIconActionButtonIconSize),
        )
    }
}

internal fun hhhlReadableOnControlColor(
    containerColor: Color,
    softContentColor: Color,
): Color {
    if (containerColor.alpha < 0.55f) return softContentColor
    return if (containerColor.luminance() < 0.48f) Color.White else Color(0xFF0F1419)
}
