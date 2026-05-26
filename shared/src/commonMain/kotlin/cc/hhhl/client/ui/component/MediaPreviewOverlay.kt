package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage

@Composable
fun MediaPreviewOverlay(
    session: MediaPreviewSession,
    onDismiss: () -> Unit,
    onSessionChanged: (MediaPreviewSession) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val item = session.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${session.selectedIndex + 1}/${session.items.size} · ${item.type.ifBlank { "文件" }}",
                        color = Color(0xFF9AA0A6),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { onOpenExternal(item.openUrl) }) {
                    Text("外部打开", color = Color.White)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val previewUrl = item.previewUrl
                if (previewUrl != null && item.isImage) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = item.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
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
                TextButton(
                    onClick = { onSessionChanged(session.previous()) },
                    enabled = session.canGoPrevious,
                ) {
                    Text("上一张")
                }
                TextButton(
                    onClick = { onSessionChanged(session.next()) },
                    enabled = session.canGoNext,
                ) {
                    Text("下一张")
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewFallback(item: MediaPreviewItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = if (item.isSensitive) "敏感内容" else item.label,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (item.previewUrl == null) "此文件不自动预览，可外部打开" else item.type.ifBlank { "文件" },
            color = Color(0xFF9AA0A6),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFF202327)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.type.substringBefore('/').ifBlank { "file" },
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
