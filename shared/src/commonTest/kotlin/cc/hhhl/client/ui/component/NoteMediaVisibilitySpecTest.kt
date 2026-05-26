package cc.hhhl.client.ui.component

import cc.hhhl.client.model.NoteMedia
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteMediaVisibilitySpecTest {
    @Test
    fun sensitiveMediaIsHiddenUntilRevealed() {
        val media = NoteMedia(
            id = "media-1",
            description = "",
            type = "image/png",
            isSensitive = true,
        )

        assertFalse(noteMediaVisible(media, revealed = false))
        assertTrue(noteMediaVisible(media, revealed = true))
    }

    @Test
    fun nonSensitiveMediaIsAlwaysVisible() {
        val media = NoteMedia(
            id = "media-1",
            description = "",
            type = "image/png",
            isSensitive = false,
        )

        assertTrue(noteMediaVisible(media, revealed = false))
    }
}
