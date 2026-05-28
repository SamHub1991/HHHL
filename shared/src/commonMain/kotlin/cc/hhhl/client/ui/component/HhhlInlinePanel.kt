package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (emphasized) {
        colors.chipSelectedBackground
    } else {
        colors.inputBackground.copy(alpha = if (isDarkSurface) 0.40f else 0.52f)
    }
    val borderColor = when {
        emphasized -> colors.focusRing.copy(alpha = if (isDarkSurface) 0.46f else 0.30f)
        else -> colors.border.copy(alpha = if (isDarkSurface) 0.32f else 0.28f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        containerColor,
                        containerColor.withMultipliedAlpha(if (isDarkSurface) 0.80f else 0.90f),
                    ),
                ),
            )
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

private fun Color.withMultipliedAlpha(multiplier: Float): Color {
    return copy(alpha = (alpha * multiplier).coerceIn(0f, 1f))
}
