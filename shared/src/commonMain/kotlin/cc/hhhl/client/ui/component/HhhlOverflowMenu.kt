package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlOverflowMenuButtonHeight = HhhlControlMinHeight
internal val HhhlOverflowMenuButtonMinWidth = HhhlControlMinWidth
internal val HhhlOverflowMenuButtonSize = HhhlOverflowMenuButtonMinWidth
internal val HhhlOverflowMenuIconSize = 18.dp
internal val HhhlOverflowMenuMinWidth = 168.dp
internal val HhhlOverflowMenuMaxWidth = 240.dp
internal val HhhlOverflowMenuOffsetX = 0.dp
internal val HhhlOverflowMenuOffsetY = 6.dp
val HhhlDropdownMenuMaxHeight = 320.dp

data class HhhlOverflowMenuAction(
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun HhhlOverflowMenu(
    actions: List<HhhlOverflowMenuAction>,
    enabled: Boolean = true,
    label: String = "更多操作",
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .width(HhhlOverflowMenuButtonMinWidth)
                .height(HhhlOverflowMenuButtonHeight)
                .clip(RoundedCornerShape(HhhlControlCornerRadius))
                .background(
                    when {
                        !enabled || actions.isEmpty() -> {
                            LocalHhhlColors.current.inputBackground.copy(alpha = 0.56f)
                        }
                        else -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.72f)
                    },
                )
                .clickable(enabled = enabled && actions.isNotEmpty()) { expanded = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = if (enabled && actions.isNotEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    LocalHhhlColors.current.subtleText
                },
                modifier = Modifier.size(HhhlOverflowMenuIconSize),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = HhhlOverflowMenuOffsetX, y = HhhlOverflowMenuOffsetY),
            modifier = Modifier.widthIn(
                min = HhhlOverflowMenuMinWidth,
                max = HhhlOverflowMenuMaxWidth,
            ).heightIn(max = HhhlDropdownMenuMaxHeight),
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            color = if (action.destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
