package cc.hhhl.client.media

/**
 * iOS 平台的图片处理器实现
 * 使用 CoreGraphics 进行图片压缩和裁剪
 */
class IOSImageProcessor : ImageProcessor {
    
    override suspend fun compressImage(
        imageData: ByteArray,
        contentType: String,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int,
    ): ImageProcessResult {
        // iOS 实现需要使用 CoreGraphics
        // 这里提供一个占位实现，实际项目中需要调用 iOS 原生 API
        // 暂时返回原始数据，实际应该使用 UIImage 和 CoreGraphics 处理
        
        // TODO: 实现 iOS 平台的图片压缩
        // 需要使用 UIImage(data:) 加载图片
        // 使用 UIGraphicsImageRenderer 进行缩放
        // 使用 UIImage.jpegData() 或 UIImage.pngData() 压缩
        
        return ImageProcessResult(
            bytes = imageData,
            contentType = contentType,
            width = maxWidth,
            height = maxHeight,
        )
    }
    
    override suspend fun cropToSquare(
        imageData: ByteArray,
        contentType: String,
        size: Int,
    ): ImageProcessResult {
        // iOS 实现需要使用 CoreGraphics
        // 这里提供一个占位实现，实际项目中需要调用 iOS 原生 API
        // 暂时返回原始数据，实际应该使用 UIImage 和 CoreGraphics 处理
        
        // TODO: 实现 iOS 平台的图片裁剪
        // 需要使用 UIImage(data:) 加载图片
        // 使用 UIGraphicsImageRenderer 进行裁剪和缩放
        // 使用 UIImage.jpegData() 或 UIImage.pngData() 压缩
        
        return ImageProcessResult(
            bytes = imageData,
            contentType = contentType,
            width = size,
            height = size,
        )
    }
}
