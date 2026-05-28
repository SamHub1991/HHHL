package cc.hhhl.client.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import cc.hhhl.client.theme.LocalHhhlColors

val HhhlDropdownMenuMaxHeight = 320.dp

@Composable
fun HhhlDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    maxHeight: Dp = HhhlDropdownMenuMaxHeight,
    shape: Shape = RoundedCornerShape(18.dp),
    containerColor: Color? = null,
    borderColor: Color? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalHhhlColors.current
    val resolvedShape = shape
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    val resolvedContainerColor = containerColor ?: if (isDarkSurface) {
        colors.surfaceElevated.copy(alpha = 1f)
    } else {
        colors.surface.copy(alpha = 1f)
    }
    val resolvedBorderColor = borderColor ?: colors.border.copy(alpha = if (isDarkSurface) 0.52f else 0.28f)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        properties = properties,
        shape = resolvedShape,
        containerColor = resolvedContainerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, resolvedBorderColor),
        modifier = modifier.heightIn(max = maxHeight),
        content = content,
    )
}
