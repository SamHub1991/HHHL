package cc.hhhl.client.ui.component

import cc.hhhl.client.model.DriveFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DriveFilePreviewSpecTest {
    @Test
    fun imageFileUsesThumbnailForPreviewAndOriginalForOpening() {
        val file = driveFile(
            type = "image/webp",
            url = "https://dc.hhhl.cc/files/original.webp",
            thumbnailUrl = "https://dc.hhhl.cc/files/thumb.webp",
        )

        val spec = driveFilePreviewSpec(file)

        assertEquals("https://dc.hhhl.cc/files/thumb.webp", spec.previewUrl)
        assertEquals("https://dc.hhhl.cc/files/original.webp", spec.openUrl)
        assertEquals("avatar.webp", spec.label)
        assertEquals("IMG", spec.placeholderLabel)
    }

    @Test
    fun sensitiveImageKeepsOpenUrlButDoesNotAutoPreview() {
        val file = driveFile(
            type = "image/png",
            url = "https://dc.hhhl.cc/files/original.png",
            thumbnailUrl = "https://dc.hhhl.cc/files/thumb.png",
            isSensitive = true,
        )

        val spec = driveFilePreviewSpec(file)

        assertNull(spec.previewUrl)
        assertEquals("https://dc.hhhl.cc/files/original.png", spec.openUrl)
        assertEquals("敏感内容", spec.label)
        assertEquals("LOCK", spec.placeholderLabel)
    }

    @Test
    fun nonImageFileDoesNotAutoPreviewButCanOpenUrl() {
        val file = driveFile(
            name = "report.pdf",
            type = "application/pdf",
            url = "https://dc.hhhl.cc/files/report.pdf",
            thumbnailUrl = "https://dc.hhhl.cc/files/report-thumb.webp",
        )

        val spec = driveFilePreviewSpec(file)

        assertNull(spec.previewUrl)
        assertEquals("https://dc.hhhl.cc/files/report.pdf", spec.openUrl)
        assertEquals("report.pdf", spec.label)
        assertEquals("PDF", spec.placeholderLabel)
    }

    private fun driveFile(
        name: String = "avatar.webp",
        type: String,
        url: String?,
        thumbnailUrl: String?,
        isSensitive: Boolean = false,
    ): DriveFile {
        return DriveFile(
            id = "file-1",
            name = name,
            type = type,
            url = url,
            thumbnailUrl = thumbnailUrl,
            comment = null,
            size = 1024,
            isSensitive = isSensitive,
        )
    }
}
