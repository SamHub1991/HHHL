package cc.hhhl.client.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HhhlCheckbox
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlDropdownMenu
import cc.hhhl.client.ui.component.HhhlDropdownMenuItem
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiAssistantFloatingOrb(
    visible: Boolean,
    aiEnabled: Boolean,
    isProcessing: Boolean,
    speechInputAvailable: Boolean,
    autoApprovalSettings: AiAssistantAutoApprovalSettings,
    onVoiceInput: () -> Unit,
    onOpenAssistant: () -> Unit,
    onAutoApprovalSettingsChanged: (AiAssistantAutoApprovalSettings) -> Unit,
    onVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val density = LocalDensity.current
    val colors = LocalHhhlColors.current
    var expanded by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val orbSize = 34.dp
        val visibleEdge = 13.dp
        val sideDragBleed = 3.dp
        val verticalMargin = 18.dp
        val bottomReserved = 92.dp
        val orbSizePx = with(density) { orbSize.toPx() }
        val visibleEdgePx = with(density) { visibleEdge.toPx() }
        val dragBleedPx = with(density) { sideDragBleed.toPx() }
        val verticalMarginPx = with(density) { verticalMargin.toPx() }
        val bottomReservedPx = with(density) { bottomReserved.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minX = -(orbSizePx - visibleEdgePx)
        val maxX = widthPx - visibleEdgePx
        val minDragX = minX - dragBleedPx
        val maxDragX = maxX + dragBleedPx
        val maxY = (heightPx - bottomReservedPx - orbSizePx).coerceAtLeast(verticalMarginPx)

        fun snapToEdge() {
            x = if (x + orbSizePx / 2f < widthPx / 2f) minX else maxX
            y = y.coerceIn(verticalMarginPx, maxY)
        }

        LaunchedEffect(widthPx, heightPx) {
            if (widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
            if (!initialized) {
                x = maxX
                y = (heightPx * 0.36f).coerceIn(verticalMarginPx, maxY)
                initialized = true
            } else {
                x = x.coerceIn(minX, maxX)
                y = y.coerceIn(verticalMarginPx, maxY)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(orbSize)
                .shadow(
                    elevation = if (dragging || expanded) 12.dp else 7.dp,
                    shape = CircleShape,
                    ambientColor = colors.shadow.copy(alpha = 0.40f),
                    spotColor = colors.shadow.copy(alpha = 0.50f),
                )
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onVoiceInput,
                    onLongClick = { expanded = true },
                )
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = {
                            expanded = false
                            dragging = true
                        },
                        onDragCancel = {
                            dragging = false
                            snapToEdge()
                        },
                        onDragEnd = {
                            dragging = false
                            snapToEdge()
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        x = (x + dragAmount.x).coerceIn(minDragX, maxDragX)
                        y = (y + dragAmount.y).coerceIn(verticalMarginPx, maxY)
                    }
                }
                .semantics { contentDescription = "AI 小助手" },
            contentAlignment = Alignment.Center,
        ) {
            AiAssistantOrbBody(
                aiEnabled = aiEnabled,
                isProcessing = isProcessing,
            )
            HhhlDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 250.dp, max = 304.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "AI 小助手",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            isProcessing -> "正在处理上一条指令"
                            !aiEnabled -> "AI 未配置"
                            speechInputAvailable -> "点击光球开始语音输入"
                            else -> "当前设备不支持系统语音输入"
                        },
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HhhlDivider()
                HhhlDropdownMenuItem(
                    text = { Text("语音输入") },
                    enabled = aiEnabled && !isProcessing,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onVoiceInput()
                    },
                )
                HhhlDropdownMenuItem(
                    text = { Text("打开完整助手") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onOpenAssistant()
                    },
                )
                HhhlDivider()
                AiAssistantOrbApprovalItem(
                    checked = autoApprovalSettings.lowRiskEnabled,
                    title = "低风险自动批准",
                    description = "只读、打开页面和草稿动作直接执行",
                    onCheckedChange = { checked ->
                        onAutoApprovalSettingsChanged(autoApprovalSettings.copy(lowRiskEnabled = checked))
                    },
                )
                AiAssistantOrbApprovalItem(
                    checked = autoApprovalSettings.highRiskEnabled,
                    title = "高风险自动批准",
                    description = "发送、发布、删除、清空等动作直接执行",
                    danger = true,
                    onCheckedChange = { checked ->
                        onAutoApprovalSettingsChanged(autoApprovalSettings.copy(highRiskEnabled = checked))
                    },
                )
                HhhlDivider()
                HhhlDropdownMenuItem(
                    text = { Text("隐藏小光球") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onVisibilityChanged(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun AiAssistantOrbBody(
    aiEnabled: Boolean,
    isProcessing: Boolean,
) {
    val colors = LocalHhhlColors.current
    val outerGlow = if (aiEnabled) colors.accent else colors.textMuted
    val coreColor = if (aiEnabled) Color.White else colors.surfaceElevated
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f),
                        coreColor.copy(alpha = 0.94f),
                        outerGlow.copy(alpha = if (isProcessing) 0.58f else 0.32f),
                        colors.surface.copy(alpha = 0.88f),
                    ),
                    center = Offset(10f, 8f),
                    radius = 44f,
                    tileMode = TileMode.Clamp,
                ),
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.72f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 6.dp)
                .size(8.dp)
                .background(Color.White.copy(alpha = 0.88f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 7.dp, bottom = 7.dp)
                .size(if (isProcessing) 10.dp else 7.dp)
                .background(outerGlow.copy(alpha = if (isProcessing) 0.72f else 0.46f), CircleShape),
        )
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = if (aiEnabled) colors.accent.copy(alpha = 0.82f) else colors.textMuted.copy(alpha = 0.76f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun AiAssistantOrbApprovalItem(
    checked: Boolean,
    title: String,
    description: String,
    danger: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlDropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = if (danger) colors.danger else colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HhhlCheckbox(
                    checked = checked,
                    onCheckedChange = null,
                )
            }
        },
        destructive = danger,
        onClick = { onCheckedChange(!checked) },
    )
}
