package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal data class HhhlTopBarMetrics(
    val containerHeight: Int,
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val slotMinSize: Int,
    val titleHorizontalPadding: Int,
    val backButtonSize: Int,
    val backIconSize: Int,
    val panelCornerRadius: Int,
    val panelElevation: Int,
)

internal fun hhhlTopBarMetrics(): HhhlTopBarMetrics = HhhlTopBarMetrics(
    containerHeight = 46,
    horizontalPadding = 10,
    verticalPadding = 4,
    slotMinSize = 34,
    titleHorizontalPadding = 4,
    backButtonSize = 34,
    backIconSize = 18,
    panelCornerRadius = 22,
    panelElevation = 6,
)

@Composable
fun HhhlTopBar(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val metrics = hhhlTopBarMetrics()
    val shape = RoundedCornerShape(metrics.panelCornerRadius.dp)
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val borderColor = if (isDarkSurface) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        LocalHhhlColors.current.divider.copy(alpha = 0.40f)
    }
    val containerColor = if (isDarkSurface) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = metrics.horizontalPadding.dp,
                vertical = metrics.verticalPadding.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.containerHeight.dp)
                .shadow(metrics.panelElevation.dp, shape, clip = false)
                .clip(shape)
                .background(containerColor)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.widthIn(min = metrics.slotMinSize.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                navigation?.invoke()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = metrics.titleHorizontalPadding.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                TopBarTitleBlock(
                    title = title,
                    supportingText = supportingText,
                )
            }
            Box(
                modifier = Modifier.widthIn(min = metrics.slotMinSize.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                action?.invoke()
            }
        }
    }
}

@Composable
private fun TopBarTitleBlock(
    title: String,
    supportingText: String?,
) {
    val cleanSupportingText = supportingText?.takeIf { it.isNotBlank() }
    if (cleanSupportingText == null) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = cleanSupportingText,
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HhhlBackButton(
    onClick: () -> Unit,
    label: String = "返回",
) {
    val metrics = hhhlTopBarMetrics()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        animationSpec = tween(durationMillis = 160),
        label = "back-button-container",
    )

    Box(
        modifier = Modifier
            .size(metrics.backButtonSize.dp)
            .shadow(
                elevation = HhhlIconActionIdleElevation,
                shape = RoundedCornerShape(HhhlIconActionCornerRadius),
                clip = false,
            )
            .clip(RoundedCornerShape(HhhlIconActionCornerRadius))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = LocalHhhlColors.current.divider.copy(alpha = 0.38f),
                shape = RoundedCornerShape(HhhlIconActionCornerRadius),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = label,
            modifier = Modifier.size(metrics.backIconSize.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
