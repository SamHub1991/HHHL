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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlControlCornerRadius = 12.dp
internal val HhhlControlMinHeight = 30.dp
internal val HhhlControlMinWidth = 34.dp
internal val HhhlIconActionCornerRadius = 14.dp
internal val HhhlIconActionIdleElevation = 0.dp
internal val HhhlIconActionEmphasizedElevation = 0.dp
internal val HhhlIconActionDarkIdleElevation = 2.dp
internal val HhhlIconActionDarkEmphasizedElevation = 4.dp
internal val HhhlControlHighlightAlpha = 0.08f
internal val HhhlActionChipMinHeight = HhhlControlMinHeight
internal val HhhlActionChipHorizontalPadding = 10.dp
internal val HhhlActionChipVerticalPadding = 4.dp
internal val HhhlActionChipMaxWidth = 184.dp
internal val HhhlIconActionButtonSize = 34.dp
internal val HhhlIconActionButtonIconSize = 18.dp
internal val HhhlTextButtonMinHeight = 32.dp
internal val HhhlTextButtonHorizontalPadding = 12.dp
internal val HhhlTextButtonVerticalPadding = 6.dp
internal val HhhlTextButtonCornerRadius = 14.dp

@Composable
fun HhhlActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val targetContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.045f else 0.035f)
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.12f else 0.055f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.06f else 0.025f)
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "chip-container",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            emphasized && isDarkSurface -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.30f else 0.12f)
            isDarkSurface -> Color.White.copy(alpha = HhhlControlHighlightAlpha)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
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
            .shadow(elevation, RoundedCornerShape(HhhlControlCornerRadius), clip = false)
            .clip(RoundedCornerShape(HhhlControlCornerRadius))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(HhhlControlCornerRadius))
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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val targetContainerColor = containerColor ?: when {
        !enabled -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.04f else 0.03f)
        destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.12f else 0.055f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.06f else 0.025f)
    }
    val resolvedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "text-button-container",
    )
    val resolvedContentColor by animateColorAsState(
        targetValue = contentColor ?: when {
            !enabled -> LocalHhhlColors.current.subtleText
            destructive -> MaterialTheme.colorScheme.error
            emphasized -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onBackground
        },
        animationSpec = tween(durationMillis = 160),
        label = "text-button-content",
    )
    val resolvedBorderColor by animateColorAsState(
        targetValue = borderColor ?: when {
            destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
            emphasized && isDarkSurface -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.30f else 0.12f)
            !enabled && isDarkSurface -> Color.White.copy(alpha = 0.05f)
            !enabled -> LocalHhhlColors.current.divider.copy(alpha = 0.18f)
            isDarkSurface -> Color.White.copy(alpha = HhhlControlHighlightAlpha)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
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
            .defaultMinSize(minHeight = HhhlTextButtonMinHeight)
            .shadow(elevation, shape, clip = false)
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
fun HhhlIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val targetContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.045f else 0.018f)
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.13f else 0.028f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.08f else 0.018f)
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "icon-action-container",
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            !enabled -> LocalHhhlColors.current.subtleText
            emphasized -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onBackground
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
            )
            .clip(RoundedCornerShape(HhhlIconActionCornerRadius))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (emphasized && isDarkSurface) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                } else if (emphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)
                } else if (isDarkSurface) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
                },
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
