package cc.hhhl.client.media

import cc.hhhl.client.api.DriveFileUpload

/**
 * 媒体选择器接口
 * 提供从相册选择图片、拍照等媒体操作
 */
interface MediaPicker {
    /**
     * 选择媒体文件（支持多选）
     */
    fun pickMedia(
        mimeType: String,
        onPicked: (DriveFileUpload) -> Unit,
        onError: (String) -> Unit,
    )

    /**
     * 选择单张图片（平台实现可使用单选启动器）
     */
    fun pickSingleImage(
        onPicked: (DriveFileUpload) -> Unit,
        onError: (String) -> Unit,
    ) {
        pickMedia(MediaPickerMimeTypes.Images, onPicked, onError)
    }

    /**
     * 选择多张图片（默认委托给 pickMedia，平台实现可使用多选启动器）
     */
    fun pickImages(
        onPicked: (DriveFileUpload) -> Unit,
        onError: (String) -> Unit,
    ) {
        pickMedia(MediaPickerMimeTypes.Images, onPicked, onError)
    }

    /**
     * 选择文件
     */
    fun pickFiles(
        onPicked: (DriveFileUpload) -> Unit,
        onError: (String) -> Unit,
    ) {
        pickMedia(MediaPickerMimeTypes.Any, onPicked, onError)
    }

    /**
     * 拍照
     */
    fun takePhoto(
        onPicked: (DriveFileUpload) -> Unit,
        onError: (String) -> Unit,
    ) {
        onError("拍照功能未实现")
    }
}

object MediaPickerMimeTypes {
    const val Images = "image/*"
    const val Any = "*/*"
}
