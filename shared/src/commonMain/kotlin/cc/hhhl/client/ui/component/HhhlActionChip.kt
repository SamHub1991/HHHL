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
import androidx.compose.ui.graphics.Brush
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
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlControlCornerRadius = 11.dp
internal val HhhlControlMinHeight = 30.dp
internal val HhhlControlMinWidth = 34.dp
internal val HhhlIconActionCornerRadius = 13.dp
internal val HhhlIconActionIdleElevation = 0.dp
internal val HhhlIconActionEmphasizedElevation = 0.dp
internal val HhhlIconActionDarkIdleElevation = 2.dp
internal val HhhlIconActionDarkEmphasizedElevation = 4.dp
internal val HhhlControlHighlightAlpha = 0.08f
internal val HhhlActionChipMinHeight = HhhlControlMinHeight
internal val HhhlActionChipHorizontalPadding = 11.dp
internal val HhhlActionChipVerticalPadding = 4.dp
internal val HhhlActionChipMaxWidth = 184.dp
internal val HhhlIconActionButtonSize = 34.dp
internal val HhhlIconActionButtonIconSize = 18.dp
internal val HhhlTextButtonMinHeight = 32.dp
internal val HhhlTextButtonHorizontalPadding = 12.dp
internal val HhhlTextButtonVerticalPadding = 6.dp
internal val HhhlTextButtonCornerRadius = 13.dp

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
        !enabled -> colors.chipBackground.withMultipliedAlpha(if (isDarkSurface) 0.55f else 0.62f)
        emphasized -> colors.chipSelectedBackground
        else -> colors.chipBackground
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "chip-container",
    )
    val targetContentColor = when {
        !enabled -> colors.textMuted
        emphasized -> hhhlReadableOnControlColor(targetContainerColor, colors.accent)
        else -> colors.textPrimary
    }
    val borderColor by animateColorAsState(
        targetValue = when {
            emphasized -> colors.focusRing.copy(alpha = if (isDarkSurface) 0.54f else 0.34f)
            isDarkSurface -> colors.border.copy(alpha = 0.30f)
            else -> colors.border.copy(alpha = 0.36f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "chip-border",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            isDarkSurface && emphasized -> HhhlIconActionDarkIdleElevation
            isDarkSurface -> HhhlIconActionIdleElevation
            emphasized -> HhhlIconActionIdleElevation
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        containerColor,
                        containerColor.withMultipliedAlpha(if (isDarkSurface) 0.82f else 0.90f),
                    ),
                ),
            )
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
            style = MaterialTheme.typography.labelMedium,
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
        !enabled -> colors.buttonBackground.withMultipliedAlpha(if (isDarkSurface) 0.55f else 0.62f)
        destructive -> colors.danger.copy(alpha = 0.10f)
        emphasized -> colors.buttonSelectedBackground
        else -> colors.buttonBackground
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
            emphasized -> hhhlReadableOnControlColor(targetContainerColor, colors.accent)
            else -> colors.textPrimary
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-button-content",
    )
    val resolvedBorderColor by animateColorAsState(
        targetValue = borderColor ?: when {
            destructive -> colors.danger.copy(alpha = 0.22f)
            emphasized -> colors.focusRing.copy(alpha = if (isDarkSurface) 0.54f else 0.34f)
            !enabled -> colors.border.copy(alpha = 0.18f)
            isDarkSurface -> colors.border.copy(alpha = 0.30f)
            else -> colors.border.copy(alpha = 0.36f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-button-border",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            isDarkSurface && emphasized -> HhhlIconActionDarkIdleElevation
            isDarkSurface -> HhhlIconActionIdleElevation
            emphasized -> HhhlIconActionIdleElevation
            else -> 0.dp
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        resolvedContainerColor,
                        resolvedContainerColor.withMultipliedAlpha(if (isDarkSurface) 0.84f else 0.92f),
                    ),
                ),
            )
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
        !enabled -> colors.buttonBackground.withMultipliedAlpha(if (isDarkSurface) 0.55f else 0.62f)
        emphasized -> colors.buttonSelectedBackground
        else -> colors.buttonBackground
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "icon-action-container",
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.textMuted
            emphasized -> hhhlReadableOnControlColor(targetContainerColor, colors.accent)
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        containerColor,
                        containerColor.withMultipliedAlpha(if (isDarkSurface) 0.82f else 0.90f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = if (emphasized) colors.focusRing.copy(alpha = if (isDarkSurface) 0.54f else 0.32f) else colors.border.copy(alpha = if (isDarkSurface) 0.34f else 0.28f),
                shape = RoundedCornerShape(HhhlIconActionCornerRadius),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(7.dp),
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

private fun Color.withMultipliedAlpha(multiplier: Float): Color {
    return copy(alpha = (alpha * multiplier).coerceIn(0f, 1f))
}
