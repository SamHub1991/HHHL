package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlOverflowMenuButtonHeight = HhhlControlMinHeight
internal val HhhlOverflowMenuButtonMinWidth = HhhlControlMinWidth
internal val HhhlOverflowMenuButtonSize = HhhlOverflowMenuButtonMinWidth
internal val HhhlOverflowMenuIconSize = 19.dp
private val HhhlOverflowMenuLabeledButtonHeight = 27.dp
private val HhhlOverflowMenuLabeledButtonMinWidth = 31.dp
private val HhhlOverflowMenuLabeledIconSize = 17.dp
internal val HhhlOverflowMenuMinWidth = 184.dp
internal val HhhlOverflowMenuMaxWidth = 240.dp
internal val HhhlOverflowMenuOffsetX = 0.dp
internal val HhhlOverflowMenuOffsetY = 6.dp
internal val HhhlOverflowMenuItemIconSlotWidth = 20.dp
internal val HhhlOverflowMenuItemIconSize = 18.dp

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
    showDisabledState: Boolean = true,
    label: String = "更多操作",
    buttonText: String? = null,
    buttonContainerColor: Color? = null,
    iconTint: Color? = null,
    labeledButtonColumnWidth: Dp = 43.dp,
    labeledButtonWidth: Dp = HhhlOverflowMenuLabeledButtonMinWidth,
    labeledButtonHeight: Dp = HhhlOverflowMenuLabeledButtonHeight,
    labeledButtonIconSize: Dp = HhhlOverflowMenuLabeledIconSize,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val visuallyEnabled = enabled || !showDisabledState
    val targetButtonContainerColor = buttonContainerColor ?: when {
        !visuallyEnabled || actions.isEmpty() -> {
            colors.buttonBackground.withMultipliedAlpha(if (isDarkSurface) 0.55f else 0.62f)
        }
        buttonText != null -> colors.buttonBackground.withMultipliedAlpha(if (isDarkSurface) 0.76f else 0.72f)
        else -> colors.buttonBackground
    }
    val resolvedButtonContainerColor by animateColorAsState(
        targetValue = targetButtonContainerColor,
        animationSpec = tween(durationMillis = 160),
        label = "overflow-button-container",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            !isDarkSurface && expanded -> 1.dp
            !isDarkSurface -> HhhlIconActionIdleElevation
            expanded -> 3.dp
            else -> 1.dp
        },
        animationSpec = tween(durationMillis = 160),
        label = "overflow-elevation",
    )
    val resolvedIconTint = iconTint ?: if (enabled && actions.isNotEmpty()) {
        hhhlReadableOnControlColor(targetButtonContainerColor, colors.textSecondary)
    } else if (visuallyEnabled && actions.isNotEmpty()) {
        hhhlReadableOnControlColor(targetButtonContainerColor, colors.textSecondary)
    } else {
        colors.textMuted
    }
    Box {
        if (buttonText == null) {
            HhhlOverflowMenuButton(
                expanded = expanded,
                enabled = enabled && actions.isNotEmpty(),
                label = label,
                containerColor = resolvedButtonContainerColor,
                iconTint = resolvedIconTint,
                elevation = elevation,
                isDarkSurface = isDarkSurface,
                onClick = { expanded = true },
            )
        } else {
            Column(
                modifier = Modifier.width(labeledButtonColumnWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                HhhlOverflowMenuButton(
                    expanded = expanded,
                    enabled = enabled && actions.isNotEmpty(),
                    label = label,
                    containerColor = resolvedButtonContainerColor,
                    iconTint = resolvedIconTint,
                    elevation = elevation,
                    isDarkSurface = isDarkSurface,
                    minWidth = labeledButtonWidth,
                    height = labeledButtonHeight,
                    iconSize = labeledButtonIconSize,
                    borderAlpha = if (isDarkSurface) 0.20f else 0.16f,
                    onClick = { expanded = true },
                )
                Text(
                    text = buttonText,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
        }
        HhhlDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = HhhlOverflowMenuOffsetX, y = HhhlOverflowMenuOffsetY),
            modifier = Modifier
                .widthIn(
                    min = HhhlOverflowMenuMinWidth,
                    max = HhhlOverflowMenuMaxWidth,
                ),
        ) {
            actions.forEach { action ->
                val itemContainerColor = when {
                    action.destructive -> colors.danger.copy(alpha = 0.07f)
                    else -> Color.Transparent
                }
                HhhlDropdownMenuItem(
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
                                            colors.danger
                                        } else {
                                            colors.textSecondary
                                        },
                                        modifier = Modifier.size(HhhlOverflowMenuItemIconSize),
                                    )
                                }
                            }
                            Text(
                                text = action.label,
                                color = if (action.destructive) {
                                    colors.danger
                                } else {
                                    colors.textPrimary
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                            )
                        }
                    },
                    enabled = action.enabled,
                    destructive = action.destructive,
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

@Composable
private fun HhhlOverflowMenuButton(
    expanded: Boolean,
    enabled: Boolean,
    label: String,
    containerColor: Color,
    iconTint: Color,
    elevation: androidx.compose.ui.unit.Dp,
    isDarkSurface: Boolean,
    minWidth: Dp = HhhlOverflowMenuButtonMinWidth,
    height: Dp = HhhlOverflowMenuButtonHeight,
    iconSize: Dp = HhhlOverflowMenuIconSize,
    borderAlpha: Float? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = Modifier
            .width(minWidth)
            .height(height)
            .shadow(
                elevation = elevation,
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
                color = if (expanded) {
                    colors.focusRing.copy(alpha = if (isDarkSurface) 0.48f else 0.30f)
                } else {
                    colors.border.copy(alpha = borderAlpha ?: if (isDarkSurface) 0.34f else 0.28f)
                },
                shape = RoundedCornerShape(HhhlIconActionCornerRadius),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun Color.withMultipliedAlpha(multiplier: Float): Color {
    return copy(alpha = (alpha * multiplier).coerceIn(0f, 1f))
}

private fun defaultOverflowMenuIcon(label: String): ImageVector? {
    return when (label) {
        "详情", "打开详情", "查看资料" -> Icons.AutoMirrored.Outlined.Article
        "回复" -> Icons.AutoMirrored.Outlined.Reply
        "引用" -> Icons.Outlined.FormatQuote
        "复制内容" -> Icons.Outlined.ContentCopy
        "复制" -> Icons.Outlined.ContentCopy
        "复制链接" -> Icons.Outlined.Link
        "嵌入" -> Icons.Outlined.Code
        "分享" -> Icons.Outlined.Share
        "AI 总结", "AI 回复草稿" -> Icons.Filled.AutoAwesome
        "收藏", "收藏/取消收藏" -> Icons.Outlined.StarBorder
        "设为特别关心", "取消特别关心" -> Icons.Outlined.FavoriteBorder
        "回应", "回应处理中" -> Icons.Outlined.AddReaction
        "关注", "关注用户" -> Icons.Outlined.PersonAdd
        "取消关注" -> Icons.Outlined.PersonRemove
        "便签", "添加到剪辑" -> Icons.Outlined.Bookmark
        "隐藏帖子列表" -> Icons.Outlined.VisibilityOff
        "Mute note", "静音" -> Icons.AutoMirrored.Outlined.VolumeOff
        "用户" -> Icons.Outlined.Person
        "屏蔽", "取消屏蔽", "拉黑", "取消拉黑" -> Icons.Outlined.Block
        "删除" -> Icons.Outlined.Delete
        "举报" -> Icons.Outlined.Report
        else -> null
    }
}
