package cc.hhhl.client.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriveFileModelTest {
    @Test
    fun typeFilterMatchesMediaDocumentsAndOtherFiles() {
        assertTrue(file(type = "image/png").matchesTypeFilter(DriveFileTypeFilter.Image))
        assertTrue(file(type = "video/mp4").matchesTypeFilter(DriveFileTypeFilter.Video))
        assertTrue(file(type = "audio/mpeg").matchesTypeFilter(DriveFileTypeFilter.Audio))
        assertTrue(file(type = "application/pdf").matchesTypeFilter(DriveFileTypeFilter.Document))
        assertTrue(file(name = "readme.md", type = "").matchesTypeFilter(DriveFileTypeFilter.Document))
        assertTrue(file(type = "application/octet-stream").matchesTypeFilter(DriveFileTypeFilter.Other))
    }

    @Test
    fun specificTypeFiltersRejectUnrelatedFiles() {
        val image = file(type = "image/png")

        assertFalse(image.matchesTypeFilter(DriveFileTypeFilter.Audio))
        assertFalse(image.matchesTypeFilter(DriveFileTypeFilter.Document))
        assertFalse(image.matchesTypeFilter(DriveFileTypeFilter.Other))
        assertTrue(image.matchesTypeFilter(DriveFileTypeFilter.All))
    }

    private fun file(
        name: String = "file.bin",
        type: String,
    ): DriveFile {
        return DriveFile(
            id = "file-1",
            name = name,
            type = type,
            url = null,
            thumbnailUrl = null,
            comment = null,
            size = 1,
            isSensitive = false,
        )
    }
}
