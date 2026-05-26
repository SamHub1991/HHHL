package cc.hhhl.client.ui.component

import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.NoteMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaPreviewSpecTest {
    @Test
    fun noteMediaSessionKeepsAllOpenableItemsAndSelectsTappedItem() {
        val media = listOf(
            noteMedia(
                id = "image-1",
                description = "第一张",
                type = "image/webp",
                url = "https://dc.hhhl.cc/files/one.webp",
                thumbnailUrl = "https://dc.hhhl.cc/files/one-thumb.webp",
            ),
            noteMedia(
                id = "video-1",
                description = "片段",
                type = "video/mp4",
                url = "https://dc.hhhl.cc/files/clip.mp4",
                thumbnailUrl = "https://dc.hhhl.cc/files/clip-thumb.webp",
            ),
        )

        val session = noteMediaPreviewSession(media = media, selectedId = "video-1")

        assertEquals(2, session.items.size)
        assertEquals(1, session.selectedIndex)
        assertEquals("片段", session.current.label)
        assertEquals("https://dc.hhhl.cc/files/clip.mp4", session.current.openUrl)
        assertEquals("https://dc.hhhl.cc/files/clip-thumb.webp", session.current.previewUrl)
        assertFalse(session.current.isImage)
        assertTrue(session.canGoPrevious)
        assertFalse(session.canGoNext)
    }

    @Test
    fun sensitiveImageKeepsOpenUrlButDoesNotAutoPreview() {
        val media = noteMedia(
            id = "sensitive-1",
            description = "隐藏图片",
            type = "image/png",
            url = "https://dc.hhhl.cc/files/full.png",
            thumbnailUrl = "https://dc.hhhl.cc/files/thumb.png",
            isSensitive = true,
        )

        val item = mediaPreviewItem(media)

        assertEquals("隐藏图片", item.label)
        assertEquals("https://dc.hhhl.cc/files/full.png", item.openUrl)
        assertNull(item.previewUrl)
        assertTrue(item.isImage)
        assertTrue(item.isSensitive)
    }

    @Test
    fun driveFileSessionUsesFileNamesAndSkipsFilesWithoutUrls() {
        val files = listOf(
            driveFile(
                id = "local-only",
                name = "missing.webp",
                type = "image/webp",
                url = null,
                thumbnailUrl = null,
            ),
            driveFile(
                id = "remote",
                name = "remote.webp",
                type = "image/webp",
                url = "https://dc.hhhl.cc/files/remote.webp",
                thumbnailUrl = "https://dc.hhhl.cc/files/remote-thumb.webp",
            ),
        )

        val session = driveFileMediaPreviewSession(files = files, selectedId = "remote")

        assertEquals(1, session.items.size)
        assertEquals("remote.webp", session.current.label)
        assertEquals("https://dc.hhhl.cc/files/remote.webp", session.current.openUrl)
        assertEquals("https://dc.hhhl.cc/files/remote-thumb.webp", session.current.previewUrl)
        assertTrue(session.current.isImage)
    }

    private fun noteMedia(
        id: String,
        description: String,
        type: String,
        url: String?,
        thumbnailUrl: String?,
        isSensitive: Boolean = false,
    ): NoteMedia {
        return NoteMedia(
            id = id,
            description = description,
            type = type,
            url = url,
            thumbnailUrl = thumbnailUrl,
            isSensitive = isSensitive,
        )
    }

    private fun driveFile(
        id: String,
        name: String,
        type: String,
        url: String?,
        thumbnailUrl: String?,
        isSensitive: Boolean = false,
    ): DriveFile {
        return DriveFile(
            id = id,
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
