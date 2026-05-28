package cc.hhhl.client.android

import android.Manifest
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
            val asset = release.assets.firstOrNull { asset -> asset.isAndroidPackage }
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
        val intent = Intent(context, AppUpdateReceiver::class.java).apply {
            action = ACTION_DOWNLOAD_APP_UPDATE
            setPackage(context.packageName)
            putExtra(EXTRA_VERSION_NAME, update.versionName)
            putExtra(EXTRA_RELEASE_NAME, update.releaseName)
            putExtra(EXTRA_APK_NAME, update.apkName)
            putExtra(EXTRA_APK_URL, update.apkUrl)
            putExtra(EXTRA_SIZE_BYTES, update.sizeBytes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            update.versionName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.dc_icon)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.dc_icon))
            .setContentTitle("发现新版本 ${update.versionName}")
            .setContentText("点击下载并覆盖安装，缓存和本地数据会保留")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${update.releaseName.ifBlank { "HHHL" }} 已可更新。点击后下载 APK，下载完成会打开系统安装确认。",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
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
            val request = DownloadManager.Request(Uri.parse(cleanUrl))
                .setTitle("HHHL ${update.versionName}")
                .setDescription("下载完成后会打开系统安装确认")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setMimeType(APK_MIME_TYPE)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            val downloadId = downloadManager().enqueue(request)
            AndroidAppUpdateStore(context).savePendingDownload(
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

    fun installDownloadedUpdate(downloadId: Long): Boolean {
        val store = AndroidAppUpdateStore(context)
        val pending = store.readPendingDownload() ?: return false
        if (pending.downloadId != downloadId) return false
        if (!isDownloadSuccessful(downloadId)) return false
        val apk = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), pending.fileName)
        if (!apk.exists()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return openUnknownAppSourcesSettings()
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
            store.clearPendingDownload()
            true
        }.getOrDefault(false)
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
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager().query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return false
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return false
            return cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL
        }
        return false
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
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_app_update"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_FILE_NAME = "file_name"
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
    data class Started(val downloadId: Long) : AppUpdateDownloadResult

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

@Serializable
private data class GithubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L,
) {
    val isAndroidPackage: Boolean
        get() = name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()
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
internal const val EXTRA_VERSION_NAME = "version_name"
internal const val EXTRA_RELEASE_NAME = "release_name"
internal const val EXTRA_APK_NAME = "apk_name"
internal const val EXTRA_APK_URL = "apk_url"
internal const val EXTRA_SIZE_BYTES = "size_bytes"

private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/WPXSCode/HHHL/releases/latest"
private const val UPDATE_CHANNEL_ID = "hhhl_app_updates"
private const val UPDATE_NOTIFICATION_ID = 42100
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
