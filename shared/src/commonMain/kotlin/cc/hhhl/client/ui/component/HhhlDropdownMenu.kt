package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import cc.hhhl.client.theme.LocalHhhlColors

val HhhlDropdownMenuMaxHeight = 380.dp

@Composable
fun HhhlDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    maxHeight: Dp = HhhlDropdownMenuMaxHeight,
    shape: Shape = RoundedCornerShape(20.dp),
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
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, resolvedBorderColor),
        modifier = modifier,
    ) {
        val scrollState = rememberScrollState()
        val canScrollBackward = scrollState.value > 0
        val canScrollForward = scrollState.value < scrollState.maxValue
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                content = content,
            )
            if (canScrollBackward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    resolvedContainerColor,
                                    resolvedContainerColor.copy(alpha = 0f),
                                ),
                            ),
                        ),
                )
            }
            if (canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    resolvedContainerColor.copy(alpha = 0f),
                                    resolvedContainerColor,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.textMuted.copy(alpha = 0.44f)),
                )
            }
        }
    }
}
