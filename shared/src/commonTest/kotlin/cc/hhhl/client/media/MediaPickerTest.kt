package cc.hhhl.client.media

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaPickerTest {
    @Test
    fun chatPhotoAndFileEntrancesUseExpectedMimeTypes() {
        val requestedMimeTypes = mutableListOf<String>()
        val picker = object : MediaPicker {
            override fun pickMedia(
                mimeType: String,
                onPicked: (cc.hhhl.client.api.DriveFileUpload) -> Unit,
                onError: (String) -> Unit,
            ) {
                requestedMimeTypes.add(mimeType)
            }
        }

        picker.pickImages(onPicked = {}, onError = {})
        picker.pickFiles(onPicked = {}, onError = {})

        assertEquals(listOf("image/*", "*/*"), requestedMimeTypes)
    }
}
