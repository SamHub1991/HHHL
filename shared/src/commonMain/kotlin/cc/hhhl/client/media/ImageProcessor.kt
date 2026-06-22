package cc.hhhl.client.media

/**
 * 图片处理器接口
 * 提供跨平台的图片压缩和裁剪功能
 */
interface ImageProcessor {
    /**
     * 压缩图片
     * @param imageData 原始图片字节数据
     * @param contentType 图片 MIME 类型
     * @param maxWidth 最大宽度（像素）
     * @param maxHeight 最大高度（像素）
     * @param quality 压缩质量（0-100）
     * @return 压缩后的图片数据，包含字节数组和新的 content type
     */
    suspend fun compressImage(
        imageData: ByteArray,
        contentType: String,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxHeight: Int = DEFAULT_MAX_HEIGHT,
        quality: Int = DEFAULT_QUALITY,
    ): ImageProcessResult

    /**
     * 裁剪图片为正方形
     * @param imageData 原始图片字节数据
     * @param contentType 图片 MIME 类型
     * @param size 正方形边长（像素）
     * @return 裁剪后的图片数据
     */
    suspend fun cropToSquare(
        imageData: ByteArray,
        contentType: String,
        size: Int = DEFAULT_AVATAR_SIZE,
    ): ImageProcessResult

    companion object {
        /** 头像默认最大宽度 */
        const val DEFAULT_MAX_WIDTH = 512
        /** 头像默认最大高度 */
        const val DEFAULT_MAX_HEIGHT = 512
        /** 头像默认尺寸（正方形） */
        const val DEFAULT_AVATAR_SIZE = 512
        /** 默认压缩质量 */
        const val DEFAULT_QUALITY = 80
    }
}

/**
 * 图片处理结果
 */
data class ImageProcessResult(
    val bytes: ByteArray,
    val contentType: String,
    val width: Int,
    val height: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageProcessResult) return false
        return bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            width == other.width &&
            height == other.height
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * 创建平台特定的图片处理器实例
 * Android 平台返回 AndroidImageProcessor，iOS 平台暂返回 null
 * @return 图片处理器实例，若平台不支持则返回 null
 */
expect fun createImageProcessor(): ImageProcessor?
