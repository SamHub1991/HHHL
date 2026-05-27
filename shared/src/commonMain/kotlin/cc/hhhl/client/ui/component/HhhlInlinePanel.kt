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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val shape = RoundedCornerShape(10.dp)
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.10f else 0.07f)
    } else {
        LocalHhhlColors.current.inputBackground.copy(alpha = if (isDarkSurface) 0.42f else 0.50f)
    }
    val borderColor = when {
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.22f else 0.14f)
        isDarkSurface -> Color.White.copy(alpha = 0.07f)
        else -> LocalHhhlColors.current.divider.copy(alpha = 0.36f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}
