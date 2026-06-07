package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cc.hhhl.client.theme.LocalHhhlColors
import coil3.compose.AsyncImage

@Composable
fun MediaPreviewOverlay(
    session: MediaPreviewSession,
    onDismiss: () -> Unit,
    onSessionChanged: (MediaPreviewSession) -> Unit,
    onOpenExternal: (String) -> Unit,
    onDownload: (MediaPreviewItem) -> Unit = {},
    onShare: (String) -> Unit = {},
) {
    val colors = LocalHhhlColors.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val item = session.currentOrNull
    if (item == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.overlayScrim),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaPreviewIconButton(
                    icon = Icons.Filled.Close,
                    label = "关闭",
                    onClick = onDismiss,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        color = colors.textInverse,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${session.selectedIndex + 1}/${session.items.size} · ${item.typeLabel}",
                        color = colors.textInverse.copy(alpha = 0.64f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MediaPreviewIconButton(
                    icon = Icons.Filled.Download,
                    label = "保存",
                    onClick = { onDownload(item) },
                )
                MediaPreviewIconButton(
                    icon = Icons.Filled.Link,
                    label = "复制链接",
                    onClick = { clipboardManager.setText(AnnotatedString(item.openUrl)) },
                )
                MediaPreviewIconButton(
                    icon = Icons.Filled.Share,
                    label = "分享",
                    onClick = { onShare(item.openUrl) },
                )
                MediaPreviewIconButton(
                    icon = Icons.Filled.OpenInNew,
                    label = "打开",
                    onClick = { onOpenExternal(item.openUrl) },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val previewUrl = item.previewUrl
                if (previewUrl != null && item.isImage) {
                    ZoomablePreviewImage(
                        imageUrl = previewUrl,
                        label = item.label,
                    )
                } else {
                    MediaPreviewFallback(item = item)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaPreviewPagerButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "上一张",
                    onClick = { onSessionChanged(session.previous()) },
                    enabled = session.canGoPrevious,
                )
                MediaPreviewPagerButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = "下一张",
                    onClick = { onSessionChanged(session.next()) },
                    enabled = session.canGoNext,
                )
            }
        }
    }
}

@Composable
private fun ZoomablePreviewImage(
    imageUrl: String,
    label: String,
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1.01f) {
            Offset.Zero
        } else {
            offset + panChange
        }
    }

    LaunchedEffect(imageUrl) {
        scale = 1f
        offset = Offset.Zero
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = label,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageUrl, scale) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.4f
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(transformState),
    )
}

@Composable
private fun MediaPreviewIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(HhhlIconActionCornerRadius)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(colors.surfaceElevated.copy(alpha = if (enabled) 0.18f else 0.08f))
            .border(1.dp, colors.textInverse.copy(alpha = if (enabled) 0.12f else 0.06f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.textInverse.copy(alpha = if (enabled) 0.92f else 0.36f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MediaPreviewPagerButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val colors = LocalHhhlColors.current
    HhhlTextButton(
        onClick = onClick,
        enabled = enabled,
        containerColor = colors.surfaceElevated.copy(alpha = if (enabled) 0.18f else 0.08f),
        borderColor = colors.textInverse.copy(alpha = if (enabled) 0.12f else 0.06f),
        contentColor = colors.textInverse.copy(alpha = if (enabled) 0.92f else 0.36f),
        modifier = Modifier.widthIn(min = 0.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label)
    }
}

@Composable
private fun MediaPreviewFallback(item: MediaPreviewItem) {
    val colors = LocalHhhlColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = if (item.isSensitive) "敏感内容" else item.label,
            color = colors.textInverse,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (item.previewUrl == null) "此文件不自动预览，可外部打开" else item.typeLabel,
            color = colors.textInverse.copy(alpha = 0.64f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colors.mediaBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.typeBadge,
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
