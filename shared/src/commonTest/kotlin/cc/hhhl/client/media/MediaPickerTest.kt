package cc.hhhl.client.media

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaPickerTest {
    @Test
    fun chatPhotoAndFileEntrancesUseExpectedMimeTypes() {
        val requestedMimeTypes = mutableListOf<String>()
        val picker = MediaPicker { mimeType, _, _ ->
            requestedMimeTypes.add(mimeType)
        }

        picker.pickImages(onPicked = {}, onError = {})
        picker.pickFiles(onPicked = {}, onError = {})

        assertEquals(listOf("image/*", "*/*"), requestedMimeTypes)
    }
}
