package cc.hhhl.client.media

/**
 * iOS 平台的图片处理器工厂
 * iOS 平台暂不支持图片裁剪，返回 null，降级为直接上传原图
 */
actual fun createImageProcessor(): ImageProcessor? = null
