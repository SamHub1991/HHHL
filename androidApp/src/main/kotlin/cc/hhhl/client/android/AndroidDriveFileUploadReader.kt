package cc.hhhl.client.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import cc.hhhl.client.api.DriveFileUpload
import java.io.ByteArrayOutputStream

private const val MAX_SELECTED_MEDIA_BYTES = 25L * 1024L * 1024L

internal fun ContentResolver.readDriveFileUpload(uri: Uri): DriveFileUpload {
    val bytes = readBytes(uri)
    if (bytes.isEmpty()) {
        throw IllegalArgumentException("文件内容为空")
    }

    return DriveFileUpload(
        bytes = bytes,
        fileName = displayName(uri)
            ?.sanitizeFileName()
            ?.takeIf { it.isNotBlank() }
            ?: "attachment",
        contentType = getType(uri) ?: "application/octet-stream",
    )
}

private fun ContentResolver.readBytes(uri: Uri): ByteArray {
    val input = openInputStream(uri) ?: throw IllegalArgumentException("无法读取所选文件")
    return input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            totalBytes += read
            if (totalBytes > MAX_SELECTED_MEDIA_BYTES) {
                throw IllegalArgumentException("文件超过 25MB，请从 Drive 上传更大的文件")
            }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}

private fun ContentResolver.displayName(uri: Uri): String? {
    return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    } ?: uri.lastPathSegment
}

private fun String.sanitizeFileName(): String {
    return trim()
        .replace('\\', '_')
        .replace('/', '_')
        .replace('\r', '_')
        .replace('\n', '_')
}
