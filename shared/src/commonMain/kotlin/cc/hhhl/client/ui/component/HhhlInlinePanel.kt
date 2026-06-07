package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

@Composable
fun HhhlInlinePanel(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val shape = RoundedCornerShape(18.dp)
    val rawContainerColor = if (emphasized) {
        colors.chipSelectedBackground
    } else {
        colors.surfaceElevated.copy(alpha = if (isDarkSurface) 0.72f else 0.80f)
    }
    val containerColor = rawContainerColor.asOpaqueOver(colors.pageBackground)
    val borderColor = when {
        emphasized -> colors.focusRing.copy(alpha = if (isDarkSurface) 0.46f else 0.30f)
        else -> colors.border.copy(alpha = if (isDarkSurface) 0.34f else 0.34f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
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
