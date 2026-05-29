package cc.hhhl.client.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import java.net.HttpURLConnection
import java.net.URL

class AndroidAppUpdateManager(
    private val context: Context,
) {
    fun checkForUpdates(): AppUpdateCheckResult {
        return runCatching {
            val release = loadLatestRelease()
                ?: return AppUpdateCheckResult.Error("没有找到 GitHub Release")
            val asset = release.selectAndroidPackage()
                ?: return AppUpdateCheckResult.Error("最新 Release 没有 APK 安装包")
            val update = AppUpdateInfo(
                versionName = release.cleanVersionName,
                releaseName = release.name.ifBlank { release.tagName },
                releaseUrl = release.htmlUrl,
                apkName = asset.name.ifBlank { defaultUpdateFileName(release.cleanVersionName) },
                apkUrl = asset.downloadUrl,
                sizeBytes = asset.size,
            )
            if (update.versionName.isBlank() || !isVersionNewer(update.versionName, BuildConfig.VERSION_NAME)) {
                AppUpdateCheckResult.NoUpdate(BuildConfig.VERSION_NAME)
            } else {
                AppUpdateCheckResult.UpdateAvailable(update)
            }
        }.getOrElse { error ->
            AppUpdateCheckResult.Error(error.message ?: "检查更新失败")
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
        return runCatching {
            val cleanUrl = update.apkUrl.trim()
            if (cleanUrl.isBlank()) return AppUpdateDownloadResult.Error("APK 下载地址为空")
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
                ),
            )
            AppUpdateDownloadResult.Started(downloadId)
        }.getOrElse { error ->
            AppUpdateDownloadResult.Error(error.message ?: "下载更新失败")
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
        val store = AndroidAppUpdateStore(context)
        val pending = store.readPendingDownload() ?: return false
        if (pending.downloadId != downloadId) return false
        return installPendingUpdate(store, pending, openSettingsIfNeeded = true)
    }

    fun retryPendingInstall(): Boolean {
        val store = AndroidAppUpdateStore(context)
        val pending = store.readPendingDownload() ?: return false
        if (store.hasInstallRequestFor(pending.versionName)) return false
        return installPendingUpdate(store, pending, openSettingsIfNeeded = false)
    }

    private fun installPendingUpdate(
        store: AndroidAppUpdateStore,
        pending: PendingAppUpdateDownload,
        openSettingsIfNeeded: Boolean,
    ): Boolean {
        val downloadId = pending.downloadId
        if (!isDownloadSuccessful(downloadId)) return false
        val apk = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), pending.fileName)
        if (!apk.exists()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return openSettingsIfNeeded && openUnknownAppSourcesSettings()
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
            true
        }.getOrElse {
            store.clearInstallRequest()
            false
        }
    }

    private fun reusePendingDownload(
        update: AppUpdateInfo,
        fileName: String,
        store: AndroidAppUpdateStore,
    ): AppUpdateDownloadResult? {
        val pending = store.readPendingDownload() ?: return null
        if (pending.versionName != update.versionName || pending.fileName != fileName) return null
        return when (downloadStatus(pending.downloadId)) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED,
                -> AppUpdateDownloadResult.Started(pending.downloadId, alreadyEnqueued = true)
            DownloadManager.STATUS_SUCCESSFUL -> {
                if (store.hasInstallRequestFor(pending.versionName)) {
                    notifyInstallReady(pending.downloadId)
                } else {
                    installPendingUpdate(store, pending, openSettingsIfNeeded = true)
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
        val connection = (URL(GITHUB_LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HHHL-Android/${BuildConfig.VERSION_NAME}")
        }
        return connection.inputStream.bufferedReader().use { reader ->
            updateJson.decodeFromString<GithubRelease>(reader.readText())
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
            .apply()
    }

    fun readPendingDownload(): PendingAppUpdateDownload? {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val versionName = preferences.getString(KEY_VERSION_NAME, null)?.takeIf { it.isNotBlank() }
        val fileName = preferences.getString(KEY_FILE_NAME, null)?.takeIf { it.isNotBlank() }
        if (downloadId <= 0L || versionName == null || fileName == null) return null
        return PendingAppUpdateDownload(downloadId, versionName, fileName)
    }

    fun clearPendingDownload() {
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_VERSION_NAME)
            .remove(KEY_FILE_NAME)
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
)

data class PendingAppUpdateDownload(
    val downloadId: Long,
    val versionName: String,
    val fileName: String,
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

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
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

@Serializable
private data class GithubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L,
) {
    val isAndroidPackage: Boolean
        get() = name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()

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

private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/WPXSCode/HHHL/releases/latest"
private const val UPDATE_CHANNEL_ID = "hhhl_app_updates"
private const val UPDATE_NOTIFICATION_ID = 42100
private const val UPDATE_INSTALLED_NOTIFICATION_ID = 42101
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
