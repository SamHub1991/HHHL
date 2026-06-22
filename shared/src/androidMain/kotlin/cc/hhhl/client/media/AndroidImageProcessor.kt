package cc.hhhl.client.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Android 平台的图片处理器实现
 * 使用 Android 原生的 Bitmap API 进行图片压缩和裁剪
 */
class AndroidImageProcessor : ImageProcessor {
    
    override suspend fun compressImage(
        imageData: ByteArray,
        contentType: String,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int,
    ): ImageProcessResult {
        // 解码图片边界信息，不加载完整图片到内存
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
        
        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        
        if (originalWidth <= 0 || originalHeight <= 0) {
            throw IllegalArgumentException("无法解析图片尺寸")
        }
        
        // 计算缩放比例
        val targetWidth = minOf(originalWidth, maxWidth)
        val targetHeight = minOf(originalHeight, maxHeight)
        
        // 计算采样率，减少内存使用
        val sampleSize = calculateSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)
        
        // 解码图片，使用采样率
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size, decodeOptions)
            ?: throw IllegalArgumentException("无法解码图片")
        
        // 如果尺寸仍然大于目标，进行精确缩放
        val scaledBitmap = if (bitmap.width > targetWidth || bitmap.height > targetHeight) {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            bitmap.recycle()
            scaled
        } else {
            bitmap
        }
        
        // 压缩为指定格式
        val format = when (contentType.lowercase()) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(format, quality, outputStream)
        scaledBitmap.recycle()
        
        val compressedBytes = outputStream.toByteArray()
        val outputContentType = when (format) {
            Bitmap.CompressFormat.PNG -> "image/png"
            Bitmap.CompressFormat.WEBP -> "image/webp"
            else -> "image/jpeg"
        }
        
        return ImageProcessResult(
            bytes = compressedBytes,
            contentType = outputContentType,
            width = targetWidth,
            height = targetHeight,
        )
    }
    
    override suspend fun cropToSquare(
        imageData: ByteArray,
        contentType: String,
        size: Int,
    ): ImageProcessResult {
        // 先解码图片
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            ?: throw IllegalArgumentException("无法解码图片")
        
        // 计算裁剪区域（居中裁剪）
        val minDimension = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - minDimension) / 2
        val top = (bitmap.height - minDimension) / 2
        
        // 裁剪为正方形
        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, minDimension, minDimension)
        bitmap.recycle()
        
        // 缩放到目标尺寸
        val scaledBitmap = if (minDimension != size) {
            val scaled = Bitmap.createScaledBitmap(croppedBitmap, size, size, true)
            croppedBitmap.recycle()
            scaled
        } else {
            croppedBitmap
        }
        
        // 压缩为指定格式
        val format = when (contentType.lowercase()) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(format, ImageProcessor.DEFAULT_QUALITY, outputStream)
        scaledBitmap.recycle()
        
        val compressedBytes = outputStream.toByteArray()
        val outputContentType = when (format) {
            Bitmap.CompressFormat.PNG -> "image/png"
            Bitmap.CompressFormat.WEBP -> "image/webp"
            else -> "image/jpeg"
        }
        
        return ImageProcessResult(
            bytes = compressedBytes,
            contentType = outputContentType,
            width = size,
            height = size,
        )
    }
    
    /**
     * 计算采样率，用于减少内存使用
     * @param width 原始宽度
     * @param height 原始高度
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @return 采样率（2 的幂次）
     */
    private fun calculateSampleSize(
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sampleSize = 1
        if (height > targetHeight || width > targetWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / sampleSize) >= targetHeight && (halfWidth / sampleSize) >= targetWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
}

/**
 * Android 平台的图片处理器工厂
 */
actual fun createImageProcessor(): ImageProcessor? = AndroidImageProcessor()
