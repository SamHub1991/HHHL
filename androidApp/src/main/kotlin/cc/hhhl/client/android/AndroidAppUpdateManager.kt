package cc.hhhl.client.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

class AndroidAppUpdateManager(
    private val context: Context,
) {
    fun checkForUpdates(): AppUpdateCheckResult {
        if (!checkInFlight.compareAndSet(false, true)) {
            return AppUpdateCheckResult.Error("正在检查更新，请稍后")
        }
        return try {
            runCatching {
                val release = loadLatestRelease()
                    ?: return AppUpdateCheckResult.Error("没有找到 GitHub Release")
                val asset = release.selectAndroidPackage()
                    ?: return AppUpdateCheckResult.Error("最新 Release 没有 APK 安装包")
                val checksum = asset.sha256Digest
                    ?: release.sha256ForAsset(asset.name)
                    ?: release.checksumAssetFor(asset.name)?.let { checksumAsset ->
                        runCatching { loadTextUrl(checksumAsset.downloadUrl, CHECKSUM_REQUEST_LABEL).parseSha256(asset.name) }
                            .getOrNull()
                    }
                    ?: ""
                val update = AppUpdateInfo(
                    versionName = release.cleanVersionName,
                    releaseName = release.name.ifBlank { release.tagName },
                    releaseUrl = release.htmlUrl,
                    apkName = asset.name.ifBlank { defaultUpdateFileName(release.cleanVersionName) },
                    apkUrl = asset.downloadUrl,
                    sizeBytes = asset.size,
                    sha256 = checksum,
                )
                if (update.versionName.isBlank() || !isVersionNewer(update.versionName, BuildConfig.VERSION_NAME)) {
                    AppUpdateCheckResult.NoUpdate(BuildConfig.VERSION_NAME)
                } else {
                    AppUpdateCheckResult.UpdateAvailable(update)
                }
            }.getOrElse { error ->
                AppUpdateCheckResult.Error(error.toUpdateErrorMessage())
            }
        } finally {
            checkInFlight.set(false)
        }
    }

    fun notifyUpdateAvailable(update: AppUpdateInfo): Boolean {
        if (!canPostNotifications()) return false
        ensureUpdateChannel()
        val downloadIntent = Intent(context, AppUpdateReceiver::class.java).apply {
            action = ACTION_DOWNLOAD_APP_UPDATE
            setPackage(context.packageName)
            putExtra(EXTRA_VERSION_NAME, update.versionName)
            putExtra(EXTRA_RELEASE_NAME, update.releaseName)
            putExtra(EXTRA_APK_NAME, update.apkName)
            putExtra(EXTRA_APK_URL, update.apkUrl)
            putExtra(EXTRA_SIZE_BYTES, update.sizeBytes)
            putExtra(EXTRA_SHA256, update.sha256)
        }
        val downloadPendingIntent = PendingIntent.getBroadcast(
            context,
            update.versionName.hashCode(),
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.dc_icon)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.dc_icon))
            .setContentTitle("发现新版本 ${update.versionName}")
            .setContentText("将自动下载，完成后会打开系统安装确认")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${update.releaseName.ifBlank { "HHHL" }} 已可更新。HHHL 会自动下载 APK，下载完成后会打开系统安装确认；如果没有开始或下载失败，可点“下载或安装”。",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.dc_icon, "下载或安装", downloadPendingIntent)
            .build()
        return notifyUpdateSafely(notification)
    }

    @SuppressLint("MissingPermission")
    private fun notifyUpdateSafely(notification: android.app.Notification): Boolean {
        if (!canPostNotifications()) return false
        return runCatching {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun downloadUpdate(update: AppUpdateInfo): AppUpdateDownloadResult {
        if (!downloadInFlight.compareAndSet(false, true)) {
            return AppUpdateDownloadResult.Error("更新下载正在进行，请稍后")
        }
        return try {
            runCatching {
                val cleanUrl = update.apkUrl.trim()
                if (cleanUrl.isBlank()) return AppUpdateDownloadResult.Error("APK 下载地址为空")
                if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
                    return AppUpdateDownloadResult.Error("APK 下载地址必须使用 HTTPS")
                }
                val fileName = update.safeApkFileName()
                val store = AndroidAppUpdateStore(context)
                reusePendingDownload(update, fileName, store)?.let { return@runCatching it }
                store.clearPendingDownload()
                val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                if (destination.exists() && !destination.delete()) {
                    return AppUpdateDownloadResult.Error("无法清理旧安装包，请稍后重试")
                }
                val request = DownloadManager.Request(Uri.parse(cleanUrl))
                    .setTitle("HHHL ${update.versionName}")
                    .setDescription("下载完成后会打开系统安装确认")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setMimeType(APK_MIME_TYPE)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                val downloadId = downloadManager().enqueue(request)
                store.savePendingDownload(
                    PendingAppUpdateDownload(
                        downloadId = downloadId,
                        versionName = update.versionName,
                        fileName = fileName,
                        sha256 = update.sha256,
                        sizeBytes = update.sizeBytes,
                    ),
                )
                AppUpdateDownloadResult.Started(downloadId)
            }.getOrElse { error ->
                AppUpdateDownloadResult.Error(error.message ?: "下载更新失败")
            }
        } finally {
            downloadInFlight.set(false)
        }
    }

    fun notifyInstallReady(downloadId: Long): Boolean {
        if (!canPostNotifications()) return false
        ensureUpdateChannel()
        val installIntent = Intent(context, AppUpdateReceiver::class.java).apply {
            action = ACTION_INSTALL_DOWNLOADED_APP_UPDATE
            setPackage(context.packageName)
            putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val installPendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.hashCode(),
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.dc_icon)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.dc_icon))
            .setContentTitle("更新包已下载")
            .setContentText("点击打开系统安装确认")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(installPendingIntent)
            .addAction(R.drawable.dc_icon, "打开安装", installPendingIntent)
            .build()
        return notifyUpdateSafely(notification)
    }

    fun reopenAfterInstallIfRequested(): Boolean {
        val store = AndroidAppUpdateStore(context)
        if (!store.shouldReopenAfterInstall(BuildConfig.VERSION_NAME)) return false
        store.clearPendingDownload()
        store.clearInstallRequest()
        return launchMainActivity() || notifyUpdateInstalled()
    }

    fun isPendingUpdateDownload(downloadId: Long): Boolean {
        return AndroidAppUpdateStore(context).readPendingDownload()?.downloadId == downloadId
    }

    fun installDownloadedUpdate(downloadId: Long): Boolean {
        return installDownloadedUpdateResult(downloadId).isOpened
    }

    fun installDownloadedUpdateResult(downloadId: Long): AppUpdateInstallResult {
        val store = AndroidAppUpdateStore(context)
        val pending = store.readPendingDownload() ?: return AppUpdateInstallResult.NotReady
        if (pending.downloadId != downloadId) return AppUpdateInstallResult.NotReady
        return installPendingUpdate(store, pending, openSettingsIfNeeded = true)
    }

    fun retryPendingInstall(): Boolean {
        val store = AndroidAppUpdateStore(context)
        val pending = store.readPendingDownload() ?: return false
        if (store.hasInstallRequestFor(pending.versionName)) return false
        return installPendingUpdate(store, pending, openSettingsIfNeeded = false).isOpened
    }

    private fun installPendingUpdate(
        store: AndroidAppUpdateStore,
        pending: PendingAppUpdateDownload,
        openSettingsIfNeeded: Boolean,
    ): AppUpdateInstallResult {
        val downloadId = pending.downloadId
        if (!isDownloadSuccessful(downloadId)) return AppUpdateInstallResult.NotReady
        val apk = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), pending.fileName)
        if (!apk.exists()) return AppUpdateInstallResult.NotReady
        validateDownloadedApk(apk, pending)?.let { reason ->
            apk.delete()
            store.clearPendingDownload()
            store.clearInstallRequest()
            notifyUpdateFailure(reason)
            return AppUpdateInstallResult.InvalidPackage(reason)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return if (openSettingsIfNeeded && openUnknownAppSourcesSettings()) {
                AppUpdateInstallResult.PermissionSettingsOpened
            } else {
                AppUpdateInstallResult.Error("需要允许 HHHL 安装未知来源应用")
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            store.markInstallRequested(pending.versionName)
            AppUpdateInstallResult.InstallOpened
        }.getOrElse {
            store.clearInstallRequest()
            AppUpdateInstallResult.Error("无法打开系统安装界面")
        }
    }

    private fun reusePendingDownload(
        update: AppUpdateInfo,
        fileName: String,
        store: AndroidAppUpdateStore,
    ): AppUpdateDownloadResult? {
        val pending = store.readPendingDownload() ?: return null
        if (pending.versionName != update.versionName || pending.fileName != fileName) return null
        if (update.sha256.isNotBlank() && pending.sha256.isNotBlank() && pending.sha256 != update.sha256) return null
        return when (downloadStatus(pending.downloadId)) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED,
                -> AppUpdateDownloadResult.Started(pending.downloadId, alreadyEnqueued = true)
            DownloadManager.STATUS_SUCCESSFUL -> {
                if (store.hasInstallRequestFor(pending.versionName)) {
                    notifyInstallReady(pending.downloadId)
                } else {
                    when (val installResult = installPendingUpdate(store, pending, openSettingsIfNeeded = true)) {
                        AppUpdateInstallResult.InstallOpened,
                        AppUpdateInstallResult.PermissionSettingsOpened,
                        AppUpdateInstallResult.NotReady,
                            -> Unit
                        is AppUpdateInstallResult.InvalidPackage -> return AppUpdateDownloadResult.Error(installResult.message)
                        is AppUpdateInstallResult.Error -> return AppUpdateDownloadResult.Error(installResult.message)
                    }
                }
                AppUpdateDownloadResult.Started(pending.downloadId, alreadyEnqueued = true)
            }
            else -> {
                store.clearPendingDownload()
                null
            }
        }
    }

    private fun launchMainActivity(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun notifyUpdateInstalled(): Boolean {
        if (!canPostNotifications()) return false
        ensureUpdateChannel()
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            UPDATE_INSTALLED_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.dc_icon)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.dc_icon))
            .setContentTitle("HHHL 已更新")
            .setContentText("如果没有自动打开，点击这里进入新版")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()
        return notifyUpdateSafely(notification)
    }

    private fun notifyUpdateFailure(message: String): Boolean {
        if (!canPostNotifications()) return false
        ensureUpdateChannel()
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            UPDATE_FAILED_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.dc_icon)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.dc_icon))
            .setContentTitle("更新包校验失败")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()
        return notifyUpdateSafely(notification)
    }

    private fun openUnknownAppSourcesSettings(): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun loadLatestRelease(): GithubRelease? {
        return updateJson.decodeFromString<GithubRelease>(loadTextUrl(GITHUB_LATEST_RELEASE_URL, RELEASE_REQUEST_LABEL))
    }

    private fun loadTextUrl(url: String, label: String): String {
        var lastError: Throwable? = null
        repeat(UPDATE_HTTP_RETRY_COUNT) { attempt ->
            try {
                return loadTextUrlOnce(url, label)
            } catch (error: UpdateHttpException) {
                if (!error.isRetryable) throw error
                lastError = error
            } catch (error: IOException) {
                lastError = error
            }
            if (attempt < UPDATE_HTTP_RETRY_COUNT - 1) {
                Thread.sleep(UPDATE_HTTP_RETRY_BACKOFF_MS * (attempt + 1L))
            }
        }
        throw lastError ?: IOException("连接失败")
    }

    private fun loadTextUrlOnce(url: String, label: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", if (label == RELEASE_REQUEST_LABEL) "application/vnd.github+json" else "text/plain")
            setRequestProperty("User-Agent", "HHHL-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                throw UpdateHttpException(
                    statusCode = code,
                    detail = body.take(160).ifBlank { null },
                    label = label,
                )
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun isDownloadSuccessful(downloadId: Long): Boolean {
        return downloadStatus(downloadId) == DownloadManager.STATUS_SUCCESSFUL
    }

    private fun downloadStatus(downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager().query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return null
            return cursor.getInt(statusIndex)
        }
        return null
    }

    private fun downloadManager(): DownloadManager {
        return context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private fun validateDownloadedApk(apk: File, pending: PendingAppUpdateDownload): String? {
        if (pending.sizeBytes > 0L && apk.length() != pending.sizeBytes) {
            return "更新包大小校验失败"
        }
        if (pending.sha256.isNotBlank() && !apk.sha256Hex().equals(pending.sha256, ignoreCase = true)) {
            return "更新包 SHA-256 校验失败"
        }
        val archiveInfo = context.packageManager.getPackageArchiveInfoCompat(apk)
            ?: return "无法读取更新包信息"
        if (archiveInfo.packageName != BuildConfig.APPLICATION_ID) {
            return "更新包包名不匹配"
        }
        val archiveVersion = archiveInfo.versionName.orEmpty()
        if (archiveVersion.isBlank()) return "更新包版本信息缺失"
        if (archiveVersion != pending.versionName && !isVersionNewer(archiveVersion, BuildConfig.VERSION_NAME)) {
            return "更新包版本不正确"
        }
        val installedInfo = context.packageManager.getInstalledPackageInfoCompat(context.packageName)
            ?: return "无法读取当前应用签名"
        val installedSignatures = installedInfo.signingCertificateDigests()
        val archiveSignatures = archiveInfo.signingCertificateDigests()
        if (installedSignatures.isEmpty() || archiveSignatures.isEmpty()) return "更新包签名信息缺失"
        if (installedSignatures != archiveSignatures) return "更新包签名与当前应用不一致"
        return null
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureUpdateChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL_ID,
                "软件更新",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "发现新版安装包时提醒"
            },
        )
    }

    private companion object {
        val checkInFlight = AtomicBoolean(false)
        val downloadInFlight = AtomicBoolean(false)
    }
}

class AndroidAppUpdateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun savePendingDownload(download: PendingAppUpdateDownload) {
        preferences.edit()
            .putLong(KEY_DOWNLOAD_ID, download.downloadId)
            .putString(KEY_VERSION_NAME, download.versionName)
            .putString(KEY_FILE_NAME, download.fileName)
            .putString(KEY_SHA256, download.sha256)
            .putLong(KEY_SIZE_BYTES, download.sizeBytes)
            .apply()
    }

    fun readPendingDownload(): PendingAppUpdateDownload? {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val versionName = preferences.getString(KEY_VERSION_NAME, null)?.takeIf { it.isNotBlank() }
        val fileName = preferences.getString(KEY_FILE_NAME, null)?.takeIf { it.isNotBlank() }
        val sha256 = preferences.getString(KEY_SHA256, null).orEmpty()
        val sizeBytes = preferences.getLong(KEY_SIZE_BYTES, 0L)
        if (downloadId <= 0L || versionName == null || fileName == null) return null
        return PendingAppUpdateDownload(downloadId, versionName, fileName, sha256, sizeBytes)
    }

    fun clearPendingDownload() {
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_VERSION_NAME)
            .remove(KEY_FILE_NAME)
            .remove(KEY_SHA256)
            .remove(KEY_SIZE_BYTES)
            .apply()
    }

    fun markInstallRequested(versionName: String) {
        preferences.edit()
            .putString(KEY_INSTALL_REQUESTED_VERSION_NAME, versionName)
            .apply()
    }

    fun shouldReopenAfterInstall(currentVersionName: String): Boolean {
        val requestedVersion = preferences.getString(KEY_INSTALL_REQUESTED_VERSION_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return requestedVersion == currentVersionName
    }

    fun hasInstallRequestFor(versionName: String): Boolean {
        val requestedVersion = preferences.getString(KEY_INSTALL_REQUESTED_VERSION_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return requestedVersion == versionName
    }

    fun clearInstallRequest() {
        preferences.edit()
            .remove(KEY_INSTALL_REQUESTED_VERSION_NAME)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_app_update"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_SHA256 = "sha256"
        const val KEY_SIZE_BYTES = "size_bytes"
        const val KEY_INSTALL_REQUESTED_VERSION_NAME = "install_requested_version_name"
    }
}

data class AppUpdateInfo(
    val versionName: String,
    val releaseName: String,
    val releaseUrl: String,
    val apkName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String = "",
)

data class PendingAppUpdateDownload(
    val downloadId: Long,
    val versionName: String,
    val fileName: String,
    val sha256: String = "",
    val sizeBytes: Long = 0L,
)

sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val update: AppUpdateInfo) : AppUpdateCheckResult

    data class NoUpdate(val currentVersion: String) : AppUpdateCheckResult

    data class Error(val message: String) : AppUpdateCheckResult
}

sealed interface AppUpdateDownloadResult {
    data class Started(
        val downloadId: Long,
        val alreadyEnqueued: Boolean = false,
    ) : AppUpdateDownloadResult

    data class Error(val message: String) : AppUpdateDownloadResult
}

sealed interface AppUpdateInstallResult {
    data object InstallOpened : AppUpdateInstallResult

    data object PermissionSettingsOpened : AppUpdateInstallResult

    data object NotReady : AppUpdateInstallResult

    data class InvalidPackage(val message: String) : AppUpdateInstallResult

    data class Error(val message: String) : AppUpdateInstallResult
}

private val AppUpdateInstallResult.isOpened: Boolean
    get() = this is AppUpdateInstallResult.InstallOpened || this is AppUpdateInstallResult.PermissionSettingsOpened

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubReleaseAsset> = emptyList(),
) {
    val cleanVersionName: String
        get() = tagName.trim().removePrefix("v").removePrefix("V")
}

private fun GithubRelease.selectAndroidPackage(): GithubReleaseAsset? {
    val version = cleanVersionName
    val candidates = assets.filter { asset -> asset.isAndroidPackage }
    if (candidates.isEmpty()) return null
    val expectedName = defaultUpdateFileName(version)
    return candidates.firstOrNull { asset -> asset.name.equals(expectedName, ignoreCase = true) }
        ?: candidates
            .filterNot { asset -> asset.isUnsafeUpdateArtifact }
            .maxWithOrNull(compareBy<GithubReleaseAsset> { asset -> asset.releaseArtifactScore(version) }.thenBy { it.size })
        ?: candidates.maxByOrNull { asset -> asset.size }
}

private fun GithubRelease.checksumAssetFor(assetName: String): GithubReleaseAsset? {
    val cleanAssetName = assetName.trim()
    if (cleanAssetName.isEmpty()) return null
    val checksumNames = setOf(
        "$cleanAssetName.sha256",
        "$cleanAssetName.sha256.txt",
        "SHA256SUMS",
        "sha256sums.txt",
    )
    return assets.firstOrNull { asset ->
        asset.downloadUrl.isNotBlank() && asset.name in checksumNames
    }
}

private fun GithubRelease.sha256ForAsset(assetName: String): String? {
    return body.parseSha256(assetName)
}

@Serializable
private data class GithubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L,
    val digest: String = "",
) {
    val isAndroidPackage: Boolean
        get() = name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()

    val sha256Digest: String?
        get() = digest.trim()
            .removePrefix("sha256:")
            .takeIf { it.isSha256Hex() }

    val isUnsafeUpdateArtifact: Boolean
        get() {
            val cleanName = name.lowercase()
            return "debug" in cleanName ||
                "unsigned" in cleanName ||
                "unaligned" in cleanName ||
                "test" in cleanName
        }

    fun releaseArtifactScore(version: String): Int {
        val cleanName = name.lowercase()
        val cleanVersion = version.lowercase()
        var score = 0
        if ("hhhl" in cleanName) score += 8
        if (cleanVersion.isNotBlank() && cleanVersion in cleanName) score += 8
        if ("release" in cleanName) score += 4
        if ("universal" in cleanName) score += 2
        return score
    }
}

private fun String.parseSha256(assetName: String): String? {
    val cleanAssetName = assetName.trim()
    lineSequence().forEach { line ->
        val cleanLine = line.trim()
        if (cleanLine.isEmpty()) return@forEach
        val firstToken = cleanLine.substringBefore(' ').substringBefore('\t')
        if (firstToken.isSha256Hex() && (cleanAssetName.isEmpty() || cleanAssetName in cleanLine || cleanLine == firstToken)) {
            return firstToken
        }
        val afterEquals = cleanLine.substringAfter('=', missingDelimiterValue = "").trim()
        if (afterEquals.isSha256Hex() && (cleanAssetName.isEmpty() || cleanAssetName in cleanLine)) return afterEquals
    }
    return trim().takeIf { it.isSha256Hex() }
}

private fun String.isSha256Hex(): Boolean {
    return length == 64 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

private fun Throwable.toUpdateErrorMessage(): String {
    return when (this) {
        is UpdateHttpException -> message ?: "更新服务器返回错误"
        is SocketTimeoutException -> "检查更新超时，请稍后重试"
        is UnknownHostException -> "无法连接 GitHub，请检查网络或稍后重试"
        is SSLException -> "更新连接被中断，请稍后重试"
        is IOException -> if (message?.contains("connection closed", ignoreCase = true) == true) {
            "更新连接被中断，请稍后重试"
        } else {
            "无法连接更新服务器：${message ?: "网络请求失败"}"
        }
        else -> message ?: "检查更新失败"
    }
}

private class UpdateHttpException(
    val statusCode: Int,
    detail: String?,
    label: String,
) : IOException(
    buildString {
        append(label)
        append("返回 ")
        append(statusCode)
        detail?.let {
            append("：")
            append(it)
        }
    },
) {
    val isRetryable: Boolean = statusCode == 408 || statusCode == 429 || statusCode in 500..599
}

@Suppress("DEPRECATION")
private fun PackageManager.getPackageArchiveInfoCompat(apk: File): PackageInfo? {
    return getPackageArchiveInfo(apk.absolutePath, packageInfoSignatureFlags())
}

@Suppress("DEPRECATION")
private fun PackageManager.getInstalledPackageInfoCompat(packageName: String): PackageInfo? {
    return runCatching { getPackageInfo(packageName, packageInfoSignatureFlags()) }.getOrNull()
}

@Suppress("DEPRECATION")
private fun packageInfoSignatureFlags(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.signingCertificateDigests(): Set<String> {
    val certificates: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.apkContentsSigners ?: emptyArray()
    } else {
        signatures ?: emptyArray()
    }
    return certificates.mapTo(LinkedHashSet()) { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .toHexString()
    }
}

private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun AppUpdateInfo.safeApkFileName(): String {
    val cleanName = apkName.substringAfterLast('/').substringBefore('?')
        .replace(Regex("""[\\/:*?\"<>|]"""), "_")
        .take(96)
        .ifBlank { defaultUpdateFileName(versionName) }
    return if (cleanName.endsWith(".apk", ignoreCase = true)) cleanName else "$cleanName.apk"
}

private fun defaultUpdateFileName(versionName: String): String {
    return "HHHL-${versionName.ifBlank { "update" }}.apk"
}

private fun isVersionNewer(remote: String, current: String): Boolean {
    val remoteParts = remote.versionParts()
    val currentParts = current.versionParts()
    if (remoteParts.isEmpty() || currentParts.isEmpty()) return remote.trim() != current.trim()
    val maxSize = maxOf(remoteParts.size, currentParts.size)
    for (index in 0 until maxSize) {
        val left = remoteParts.getOrElse(index) { 0 }
        val right = currentParts.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun String.versionParts(): List<Int> {
    return trim()
        .removePrefix("v")
        .removePrefix("V")
        .split(Regex("[^0-9]+"))
        .mapNotNull { it.toIntOrNull() }
}

private val updateJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal const val ACTION_DOWNLOAD_APP_UPDATE = "cc.hhhl.client.android.action.DOWNLOAD_APP_UPDATE"
internal const val ACTION_INSTALL_DOWNLOADED_APP_UPDATE = "cc.hhhl.client.android.action.INSTALL_DOWNLOADED_APP_UPDATE"
internal const val EXTRA_VERSION_NAME = "version_name"
internal const val EXTRA_RELEASE_NAME = "release_name"
internal const val EXTRA_APK_NAME = "apk_name"
internal const val EXTRA_APK_URL = "apk_url"
internal const val EXTRA_SIZE_BYTES = "size_bytes"
internal const val EXTRA_SHA256 = "sha256"

private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/WPXSCode/HHHL/releases/latest"
private const val RELEASE_REQUEST_LABEL = "GitHub Release"
private const val CHECKSUM_REQUEST_LABEL = "更新校验文件"
private const val UPDATE_HTTP_RETRY_COUNT = 3
private const val UPDATE_HTTP_RETRY_BACKOFF_MS = 350L
private const val UPDATE_CHANNEL_ID = "hhhl_app_updates"
private const val UPDATE_NOTIFICATION_ID = 42100
private const val UPDATE_INSTALLED_NOTIFICATION_ID = 42101
private const val UPDATE_FAILED_NOTIFICATION_ID = 42102
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
