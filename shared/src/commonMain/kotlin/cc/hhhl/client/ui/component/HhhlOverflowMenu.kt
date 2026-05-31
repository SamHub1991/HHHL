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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
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
    val children: List<HhhlOverflowMenuAction> = emptyList(),
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
    var submenuActions by remember { mutableStateOf<List<HhhlOverflowMenuAction>>(emptyList()) }
    var submenuLabel by remember { mutableStateOf("") }
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
            onDismissRequest = {
                expanded = false
                submenuActions = emptyList()
                submenuLabel = ""
            },
            offset = DpOffset(x = HhhlOverflowMenuOffsetX, y = HhhlOverflowMenuOffsetY),
            modifier = Modifier
                .widthIn(
                    min = HhhlOverflowMenuMinWidth,
                    max = HhhlOverflowMenuMaxWidth,
                ),
        ) {
            if (submenuActions.isEmpty()) {
                actions.forEach { action ->
                    HhhlOverflowMenuItem(
                        action = action,
                        onOpenSubmenu = { childLabel, childActions ->
                            submenuLabel = childLabel
                            submenuActions = childActions
                        },
                        onCloseMenu = {
                            expanded = false
                            submenuActions = emptyList()
                            submenuLabel = ""
                        },
                    )
                }
            } else {
                HhhlOverflowMenuSubmenuHeader(
                    label = submenuLabel,
                    onBack = {
                        submenuActions = emptyList()
                        submenuLabel = ""
                    },
                )
                submenuActions.forEach { action ->
                    HhhlOverflowMenuItem(
                        action = action,
                        onOpenSubmenu = { _, _ -> },
                        onCloseMenu = {
                            expanded = false
                            submenuActions = emptyList()
                            submenuLabel = ""
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HhhlOverflowMenuItem(
    action: HhhlOverflowMenuAction,
    onOpenSubmenu: (String, List<HhhlOverflowMenuAction>) -> Unit,
    onCloseMenu: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val itemContainerColor = when {
        action.destructive -> colors.danger.copy(alpha = 0.07f)
        else -> Color.Transparent
    }
    val hasChildren = action.children.isNotEmpty()
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
                            tint = if (action.destructive) colors.danger else colors.textSecondary,
                            modifier = Modifier.size(HhhlOverflowMenuItemIconSize),
                        )
                    }
                }
                Text(
                    text = action.label,
                    color = if (action.destructive) colors.danger else colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
                if (hasChildren) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        enabled = action.enabled,
        destructive = action.destructive,
        onClick = {
            if (hasChildren) {
                onOpenSubmenu(action.label, action.children)
            } else {
                onCloseMenu()
                action.onClick()
            }
        },
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(itemContainerColor),
    )
}

@Composable
private fun HhhlOverflowMenuSubmenuHeader(
    label: String,
    onBack: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlDropdownMenuItem(
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
        },
        onClick = onBack,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
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

private fun defaultOverflowMenuIcon(label: String): ImageVector {
    val trimmedLabel = label.trim()
    when {
        trimmedLabel.startsWith("当前：") -> return defaultOverflowMenuIcon(trimmedLabel.removePrefix("当前："))
        trimmedLabel.startsWith("排序：") -> return sortOverflowMenuIcon(trimmedLabel.removePrefix("排序："))
        trimmedLabel.startsWith("排序改为 ") -> return sortOverflowMenuIcon(trimmedLabel.removePrefix("排序改为 "))
        trimmedLabel.startsWith("类型：") -> return typeFilterOverflowMenuIcon(trimmedLabel.removePrefix("类型："))
        trimmedLabel.startsWith("只看") -> return typeFilterOverflowMenuIcon(trimmedLabel.removePrefix("只看"))
        trimmedLabel.startsWith("外观 ·") -> return Icons.Filled.Palette
    }
    val normalizedLabel = trimmedLabel.normalizedOverflowMenuLabel()
    return when (normalizedLabel) {
        "详情", "打开详情", "查看资料" -> Icons.AutoMirrored.Outlined.Article
        "回复" -> Icons.AutoMirrored.Outlined.Reply
        "引用" -> Icons.Outlined.FormatQuote
        "复制内容" -> Icons.Outlined.ContentCopy
        "复制" -> Icons.Outlined.ContentCopy
        "复制链接" -> Icons.Outlined.Link
        "嵌入" -> Icons.Outlined.Code
        "分享" -> Icons.Outlined.Share
        "AI 总结", "AI 回复草稿", "总结通知", "待处理", "优先级" -> Icons.Filled.AutoAwesome
        "收藏", "收藏/取消收藏", "收藏信息" -> Icons.Outlined.StarBorder
        "设为特别关心", "取消特别关心" -> Icons.Outlined.FavoriteBorder
        "回应", "回应处理中" -> Icons.Outlined.AddReaction
        "关注", "关注用户" -> Icons.Outlined.PersonAdd
        "关注请求", "邀请成员", "创建邀请码" -> Icons.Outlined.PersonAdd
        "取消关注", "移除成员", "拒绝", "撤销令牌", "删除邀请码" -> Icons.Outlined.PersonRemove
        "便签", "添加到剪辑", "移出剪辑", "归档" -> Icons.Outlined.Bookmark
        "隐藏帖子列表", "取消敏感", "停用", "停用 Webhook" -> Icons.Outlined.VisibilityOff
        "敏感" -> Icons.Filled.Visibility
        "Mute note", "静音", "取消静音", "静音聊天室" -> Icons.AutoMirrored.Outlined.VolumeOff
        "用户" -> Icons.Outlined.Person
        "屏蔽", "取消屏蔽", "拉黑", "取消拉黑", "退出登录", "退出聊天室", "登出共享访问" -> Icons.Outlined.Block
        "清空", "删除", "确认删除", "删除中", "删除作品", "删除聊天室", "删除选项", "移除附件", "移除投票" -> Icons.Outlined.Delete
        "删除 Webhook" -> Icons.Outlined.Delete
        "举报", "举报用户" -> Icons.Outlined.Report
        "刷新", "刷新中", "刷新列表", "刷新动态", "刷新资料", "刷新消息", "刷新成员", "刷新全部关注",
        "刷新关注", "同步中", "同步列表中", "同步动态中", "同步天线中", "同步剪辑中", "同步趋势中", "同步实例中",
        "处理中", "上传中" -> Icons.Filled.Refresh
        "全部已读" -> Icons.Filled.DoneAll
        "编辑", "编辑作品", "编辑聊天室", "编辑 Webhook", "改名", "名称 A-Z", "名称 Z-A" -> Icons.Filled.Edit
        "搜索", "搜索消息" -> Icons.Filled.Search
        "过滤设置", "排序", "类型", "全部词", "任一词", "精确短语" -> Icons.Outlined.FilterList
        "关系管理" -> Icons.Filled.Settings
        "外观" -> Icons.Filled.Palette
        "公告", "测试通知", "提醒自己", "通知", "提及", "互动", "系统", "特别关心" -> Icons.Filled.Notifications
        "启用", "启用 Webhook", "测试", "测试 Webhook" -> Icons.Filled.PlayArrow
        "Flash", "网页版运行" -> Icons.Filled.PlayArrow
        "内容警告" -> Icons.Filled.Visibility
        "关闭内容警告" -> Icons.Outlined.VisibilityOff
        "添加投票" -> Icons.Filled.Poll
        "添加附件", "更换附件" -> Icons.Filled.AttachFile
        "最近文件流", "文件夹", "目录" -> Icons.Filled.Folder
        "返回目录", "返回上级", "移到根目录", "首页" -> Icons.Filled.Home
        "帖子", "社交", "本地", "联合", "全局", "气泡", "精选", "公开" -> Icons.Filled.Forum
        "用户", "用户列表", "全部来源", "远程" -> Icons.Outlined.Person
        "话题", "热门", "最新", "喜欢" -> Icons.Outlined.StarBorder
        "角色" -> Icons.Filled.Settings
        "趋势" -> Icons.Filled.AutoAwesome
        "联邦" -> Icons.Filled.Folder
        "我的", "私密" -> Icons.Filled.Visibility
        "频道", "聊天" -> Icons.Filled.Forum
        "天线", "剪辑", "页面", "文档" -> Icons.AutoMirrored.Outlined.Article
        "图库", "图片" -> Icons.Filled.Image
        "Play" -> Icons.Filled.PlayArrow
        "文件" -> Icons.Filled.AttachFile
        "音频", "视频", "其他" -> Icons.Filled.Folder
        "最新", "最早", "大小 ↓", "大小 ↑" -> Icons.Filled.Tune
        "成就" -> Icons.Outlined.StarBorder
        else -> Icons.Filled.MoreVert
    }
}

private fun sortOverflowMenuIcon(label: String): ImageVector {
    return when (label.trim()) {
        "名称 A-Z", "名称 Z-A" -> Icons.Filled.Edit
        "最新", "最早", "大小 ↓", "大小 ↑" -> Icons.Filled.Tune
        else -> Icons.Filled.Tune
    }
}

private fun typeFilterOverflowMenuIcon(label: String): ImageVector {
    return when (label.trim()) {
        "全部" -> Icons.Outlined.FilterList
        "图片" -> Icons.Filled.Image
        "视频", "音频", "其他" -> Icons.Filled.Folder
        "文档" -> Icons.AutoMirrored.Outlined.Article
        else -> Icons.Outlined.FilterList
    }
}

private fun String.normalizedOverflowMenuLabel(): String {
    val trimmed = trim()
    val withoutStatePrefix = trimmed
        .removePrefix("当前：")
        .removePrefix("排序：")
        .removePrefix("排序改为 ")
        .removePrefix("类型：")
        .removePrefix("只看")
    val withoutAppearanceSummary = withoutStatePrefix.substringBefore(" · ")
    return withoutAppearanceSummary.substringBefore('：').ifBlank { withoutAppearanceSummary }
}
