package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class DriveFile(
    val id: String,
    val name: String,
    val type: String,
    val url: String?,
    val thumbnailUrl: String?,
    val comment: String?,
    val size: Long,
    val isSensitive: Boolean,
    val createdAtLabel: String = "",
    val folderId: String? = null,
)

@Immutable
data class DriveFileDetails(
    val file: DriveFile,
    val attachedNotes: List<Note> = emptyList(),
)

enum class DriveFileTypeFilter(
    val label: String,
) {
    All("全部"),
    Image("图片"),
    Video("视频"),
    Audio("音频"),
    Document("文档"),
    Other("其他"),
}

fun DriveFile.matchesTypeFilter(filter: DriveFileTypeFilter): Boolean {
    return when (filter) {
        DriveFileTypeFilter.All -> true
        DriveFileTypeFilter.Image -> type.startsWith("image/")
        DriveFileTypeFilter.Video -> type.startsWith("video/")
        DriveFileTypeFilter.Audio -> type.startsWith("audio/")
        DriveFileTypeFilter.Document -> isDriveDocumentType()
        DriveFileTypeFilter.Other -> {
            !type.startsWith("image/") &&
                !type.startsWith("video/") &&
                !type.startsWith("audio/") &&
                !isDriveDocumentType()
        }
    }
}

private fun DriveFile.isDriveDocumentType(): Boolean {
    val cleanType = type.lowercase()
    val cleanName = name.lowercase()
    return cleanType in documentMimeTypes ||
        cleanType.startsWith("text/") ||
        documentExtensions.any { cleanName.endsWith(it) }
}

private val documentMimeTypes = setOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/rtf",
    "application/json",
)

private val documentExtensions = listOf(
    ".pdf",
    ".doc",
    ".docx",
    ".xls",
    ".xlsx",
    ".ppt",
    ".pptx",
    ".txt",
    ".md",
    ".rtf",
    ".json",
    ".csv",
)

@Immutable
data class DriveFolder(
    val id: String,
    val name: String,
    val parentId: String?,
    val foldersCount: Int,
    val filesCount: Int,
    val createdAtLabel: String = "",
)
