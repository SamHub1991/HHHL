package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlOverflowMenuButtonHeight = HhhlControlMinHeight
internal val HhhlOverflowMenuButtonMinWidth = HhhlControlMinWidth
internal val HhhlOverflowMenuButtonSize = HhhlOverflowMenuButtonMinWidth
internal val HhhlOverflowMenuIconSize = 19.dp
internal val HhhlOverflowMenuMinWidth = 184.dp
internal val HhhlOverflowMenuMaxWidth = 240.dp
internal val HhhlOverflowMenuOffsetX = 0.dp
internal val HhhlOverflowMenuOffsetY = 6.dp
internal val HhhlOverflowMenuItemIconSlotWidth = 20.dp
internal val HhhlOverflowMenuItemIconSize = 18.dp
val HhhlDropdownMenuMaxHeight = 320.dp

data class HhhlOverflowMenuAction(
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
    val icon: ImageVector? = defaultOverflowMenuIcon(label),
)

@Composable
fun HhhlOverflowMenu(
    actions: List<HhhlOverflowMenuAction>,
    enabled: Boolean = true,
    label: String = "更多操作",
    buttonContainerColor: Color? = null,
    iconTint: Color? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val targetButtonContainerColor = buttonContainerColor ?: when {
        !enabled || actions.isEmpty() -> {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.045f else 0.018f)
        }
        else -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.08f else 0.018f)
    }
    val resolvedButtonContainerColor by animateColorAsState(
        targetValue = targetButtonContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "overflow-button-container",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            !isDarkSurface && expanded -> HhhlIconActionEmphasizedElevation
            !isDarkSurface -> HhhlIconActionIdleElevation
            expanded -> HhhlIconActionDarkEmphasizedElevation
            else -> HhhlIconActionDarkIdleElevation
        },
        animationSpec = tween(durationMillis = 160),
        label = "overflow-elevation",
    )
    val resolvedIconTint = iconTint ?: if (enabled && actions.isNotEmpty()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        LocalHhhlColors.current.subtleText
    }
    Box {
        Box(
            modifier = Modifier
                .width(HhhlOverflowMenuButtonMinWidth)
                .height(HhhlOverflowMenuButtonHeight)
                .shadow(elevation, RoundedCornerShape(HhhlIconActionCornerRadius), clip = false)
                .clip(RoundedCornerShape(HhhlIconActionCornerRadius))
                .background(resolvedButtonContainerColor)
                .border(
                    width = 1.dp,
                    color = if (expanded) {
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.34f else 0.20f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.18f else 0.045f)
                    },
                    shape = RoundedCornerShape(HhhlIconActionCornerRadius),
                )
                .clickable(enabled = enabled && actions.isNotEmpty()) { expanded = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = resolvedIconTint,
                modifier = Modifier.size(HhhlOverflowMenuIconSize),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = HhhlOverflowMenuOffsetX, y = HhhlOverflowMenuOffsetY),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .widthIn(
                    min = HhhlOverflowMenuMinWidth,
                    max = HhhlOverflowMenuMaxWidth,
                )
                .heightIn(max = HhhlDropdownMenuMaxHeight),
        ) {
            actions.forEach { action ->
                val itemContainerColor = when {
                    action.destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
                    else -> Color.Transparent
                }
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.width(HhhlOverflowMenuItemIconSlotWidth),
                                contentAlignment = Alignment.Center,
                            ) {
                                action.icon?.let { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (action.destructive) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(HhhlOverflowMenuItemIconSize),
                                    )
                                }
                            }
                            Text(
                                text = action.label,
                                color = if (action.destructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                            )
                        }
                    },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(itemContainerColor),
                )
            }
        }
    }
}

private fun defaultOverflowMenuIcon(label: String): ImageVector? {
    return when (label) {
        "详情", "打开详情", "查看资料" -> Icons.AutoMirrored.Outlined.Article
        "复制内容" -> Icons.Outlined.ContentCopy
        "复制链接" -> Icons.Outlined.Link
        "嵌入" -> Icons.Outlined.Code
        "分享" -> Icons.Outlined.Share
        "收藏", "收藏/取消收藏" -> Icons.Outlined.StarBorder
        "设为特别关心", "取消特别关心" -> Icons.Outlined.FavoriteBorder
        "回应", "回应处理中" -> Icons.Outlined.AddReaction
        "关注", "关注用户" -> Icons.Outlined.PersonAdd
        "取消关注" -> Icons.Outlined.PersonRemove
        "便签", "添加到剪辑" -> Icons.Outlined.Bookmark
        "隐藏帖子列表" -> Icons.Outlined.VisibilityOff
        "Mute note", "静音" -> Icons.AutoMirrored.Outlined.VolumeOff
        "用户" -> Icons.Outlined.Person
        "拉黑", "取消拉黑" -> Icons.Outlined.Block
        "举报" -> Icons.Outlined.Report
        else -> null
    }
}
