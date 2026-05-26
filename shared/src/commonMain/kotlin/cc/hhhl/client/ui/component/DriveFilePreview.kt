package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.theme.LocalHhhlColors
import coil3.compose.AsyncImage

data class DriveFilePreviewSpec(
    val previewUrl: String?,
    val openUrl: String?,
    val label: String,
    val placeholderLabel: String,
)

fun driveFilePreviewSpec(file: DriveFile): DriveFilePreviewSpec {
    val openUrl = file.url?.takeIf { it.isNotBlank() }
        ?: file.thumbnailUrl?.takeIf { it.isNotBlank() }
    val previewUrl = when {
        file.isSensitive -> null
        !file.type.startsWith("image/") -> null
        else -> file.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: file.url?.takeIf { it.isNotBlank() }
    }
    return DriveFilePreviewSpec(
        previewUrl = previewUrl,
        openUrl = openUrl,
        label = when {
            file.isSensitive -> "敏感内容"
            file.name.isNotBlank() -> file.name
            file.type.startsWith("image/") -> "图片"
            file.type.startsWith("video/") -> "视频"
            file.type.startsWith("audio/") -> "音频"
            else -> "附件"
        },
        placeholderLabel = driveFilePreviewPlaceholder(file),
    )
}

private fun driveFilePreviewPlaceholder(file: DriveFile): String {
    if (file.isSensitive) return "LOCK"
    return when {
        file.type.startsWith("image/") -> "IMG"
        file.type.startsWith("video/") -> "VID"
        file.type.startsWith("audio/") -> "AUD"
        else -> file.name.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.length in 1..5 }
            ?.uppercase()
            ?: "FILE"
    }
}

@Composable
fun DriveFilePreview(
    file: DriveFile,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {},
) {
    val spec = driveFilePreviewSpec(file)
    var imageLoaded by remember(spec.previewUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LocalHhhlColors.current.mediaBackground)
            .then(
                spec.openUrl?.let { openUrl ->
                    Modifier.clickable { onOpenUrl(openUrl) }
                } ?: Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        spec.previewUrl?.let { previewUrl ->
            AsyncImage(
                model = previewUrl,
                contentDescription = spec.label,
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!imageLoaded) {
            Text(
                text = spec.placeholderLabel,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
