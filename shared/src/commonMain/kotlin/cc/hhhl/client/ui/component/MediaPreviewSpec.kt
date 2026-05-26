package cc.hhhl.client.ui.component

import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.NoteMedia

data class MediaPreviewItem(
    val id: String,
    val label: String,
    val type: String,
    val openUrl: String,
    val previewUrl: String?,
    val isImage: Boolean,
    val isSensitive: Boolean,
)

data class MediaPreviewSession(
    val items: List<MediaPreviewItem>,
    val selectedIndex: Int,
) {
    val current: MediaPreviewItem
        get() = items[selectedIndex]

    val canGoPrevious: Boolean
        get() = selectedIndex > 0

    val canGoNext: Boolean
        get() = selectedIndex < items.lastIndex

    fun previous(): MediaPreviewSession = copy(selectedIndex = (selectedIndex - 1).coerceAtLeast(0))

    fun next(): MediaPreviewSession = copy(selectedIndex = (selectedIndex + 1).coerceAtMost(items.lastIndex))
}

fun mediaPreviewItem(media: NoteMedia): MediaPreviewItem {
    val openUrl = media.url?.takeIf { it.isNotBlank() }
        ?: media.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: ""
    val isImage = media.type.startsWith("image/")
    return MediaPreviewItem(
        id = media.id,
        label = when {
            media.description.isNotBlank() -> media.description
            media.isSensitive -> "敏感内容"
            isImage -> "图片"
            media.type.startsWith("video/") -> "视频"
            media.type.startsWith("audio/") -> "音频"
            else -> "附件"
        },
        type = media.type,
        openUrl = openUrl,
        previewUrl = when {
            media.isSensitive -> null
            isImage -> media.thumbnailUrl?.takeIf { it.isNotBlank() } ?: media.url?.takeIf { it.isNotBlank() }
            media.type.startsWith("video/") -> media.thumbnailUrl?.takeIf { it.isNotBlank() }
            else -> null
        },
        isImage = isImage,
        isSensitive = media.isSensitive,
    )
}

fun mediaPreviewItem(file: DriveFile): MediaPreviewItem {
    val openUrl = file.url?.takeIf { it.isNotBlank() }
        ?: file.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: ""
    val isImage = file.type.startsWith("image/")
    return MediaPreviewItem(
        id = file.id,
        label = when {
            file.isSensitive -> "敏感内容"
            file.name.isNotBlank() -> file.name
            isImage -> "图片"
            file.type.startsWith("video/") -> "视频"
            file.type.startsWith("audio/") -> "音频"
            else -> "附件"
        },
        type = file.type,
        openUrl = openUrl,
        previewUrl = when {
            file.isSensitive -> null
            isImage -> file.thumbnailUrl?.takeIf { it.isNotBlank() } ?: file.url?.takeIf { it.isNotBlank() }
            file.type.startsWith("video/") -> file.thumbnailUrl?.takeIf { it.isNotBlank() }
            else -> null
        },
        isImage = isImage,
        isSensitive = file.isSensitive,
    )
}

fun noteMediaPreviewSession(
    media: List<NoteMedia>,
    selectedId: String,
): MediaPreviewSession {
    return buildMediaPreviewSession(
        items = media.map(::mediaPreviewItem),
        selectedId = selectedId,
    )
}

fun driveFileMediaPreviewSession(
    files: List<DriveFile>,
    selectedId: String,
): MediaPreviewSession {
    return buildMediaPreviewSession(
        items = files.map(::mediaPreviewItem),
        selectedId = selectedId,
    )
}

private fun buildMediaPreviewSession(
    items: List<MediaPreviewItem>,
    selectedId: String,
): MediaPreviewSession {
    val openableItems = items.filter { it.openUrl.isNotBlank() }
    val selectedIndex = openableItems.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
    return MediaPreviewSession(
        items = openableItems,
        selectedIndex = selectedIndex,
    )
}
