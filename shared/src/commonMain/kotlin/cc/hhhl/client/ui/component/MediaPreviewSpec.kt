package cc.hhhl.client.ui.component

import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.NoteMedia

data class MediaPreviewItem(
    val id: String,
    val label: String,
    val type: String,
    val typeLabel: String,
    val typeBadge: String,
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
        typeLabel = mediaTypeDisplayName(media.type),
        typeBadge = mediaTypeBadge(media.type),
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
        typeLabel = mediaTypeDisplayName(file.type, file.name),
        typeBadge = mediaTypeBadge(file.type, file.name),
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

fun mediaTypeDisplayName(
    type: String,
    fileName: String? = null,
): String {
    val cleanType = type.trim().lowercase()
    val extension = fileName.fileExtension()
    return when {
        cleanType.startsWith("image/") -> "图片"
        cleanType.startsWith("video/") -> "视频"
        cleanType.startsWith("audio/") -> "音频"
        cleanType == "application/pdf" || extension == "pdf" -> "PDF 文档"
        cleanType in wordMimeTypes || extension in setOf("doc", "docx") -> "Word 文档"
        cleanType in sheetMimeTypes || extension in setOf("xls", "xlsx", "csv") -> "表格"
        cleanType in slideMimeTypes || extension in setOf("ppt", "pptx") -> "演示文稿"
        cleanType.startsWith("text/") || extension in setOf("txt", "md", "log") -> "文本"
        cleanType == "application/json" || extension == "json" -> "JSON"
        cleanType in archiveMimeTypes || extension in setOf("zip", "rar", "7z", "tar", "gz") -> "压缩包"
        cleanType == "application/vnd.android.package-archive" || extension == "apk" -> "Android 安装包"
        cleanType.isBlank() || cleanType == "application/octet-stream" -> extension.uppercaseOrFile()
        else -> cleanType.substringAfter('/').substringBefore(';').takeIf { it.length in 1..10 }?.uppercase() ?: "文件"
    }
}

fun mediaTypeBadge(
    type: String,
    fileName: String? = null,
): String {
    val cleanType = type.trim().lowercase()
    val extension = fileName.fileExtension()
    return when {
        cleanType.startsWith("image/") -> "IMG"
        cleanType.startsWith("video/") -> "VID"
        cleanType.startsWith("audio/") -> "AUD"
        cleanType == "application/pdf" || extension == "pdf" -> "PDF"
        cleanType in wordMimeTypes || extension in setOf("doc", "docx") -> "DOC"
        cleanType in sheetMimeTypes || extension in setOf("xls", "xlsx", "csv") -> "XLS"
        cleanType in slideMimeTypes || extension in setOf("ppt", "pptx") -> "PPT"
        cleanType.startsWith("text/") || extension in setOf("txt", "md", "log") -> "TXT"
        cleanType == "application/json" || extension == "json" -> "JSON"
        cleanType in archiveMimeTypes || extension in setOf("zip", "rar", "7z", "tar", "gz") -> "ZIP"
        cleanType == "application/vnd.android.package-archive" || extension == "apk" -> "APK"
        else -> extension.uppercaseOrFile()
    }
}

private fun String?.fileExtension(): String {
    return this
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.takeIf { it.length in 1..6 }
        .orEmpty()
}

private fun String.uppercaseOrFile(): String = takeIf { it.isNotBlank() }?.uppercase() ?: "FILE"

private val wordMimeTypes = setOf(
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

private val sheetMimeTypes = setOf(
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)

private val slideMimeTypes = setOf(
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
)

private val archiveMimeTypes = setOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-rar-compressed",
    "application/x-7z-compressed",
    "application/gzip",
)
