package cc.hhhl.client.media

import cc.hhhl.client.api.DriveFileUpload

fun interface MediaPicker {
    fun pickMedia(
        mimeType: String,
        onPicked: (DriveFileUpload) -> Unit,
        onError: (String) -> Unit,
    )

    fun pickImages(
        onPicked: (DriveFileUpload) -> Unit,
        onError: (String) -> Unit,
    ) {
        pickMedia(MediaPickerMimeTypes.Images, onPicked, onError)
    }

    fun pickFiles(
        onPicked: (DriveFileUpload) -> Unit,
        onError: (String) -> Unit,
    ) {
        pickMedia(MediaPickerMimeTypes.Any, onPicked, onError)
    }
}

object MediaPickerMimeTypes {
    const val Images = "image/*"
    const val Any = "*/*"
}
