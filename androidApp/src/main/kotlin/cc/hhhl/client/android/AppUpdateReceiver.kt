package cc.hhhl.client.android

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DOWNLOAD_APP_UPDATE) {
            startUpdateDownload(context, intent)
        }
    }

    private fun startUpdateDownload(context: Context, intent: Intent) {
        val update = AppUpdateInfo(
            versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty(),
            releaseName = intent.getStringExtra(EXTRA_RELEASE_NAME).orEmpty(),
            releaseUrl = "",
            apkName = intent.getStringExtra(EXTRA_APK_NAME).orEmpty(),
            apkUrl = intent.getStringExtra(EXTRA_APK_URL).orEmpty(),
            sizeBytes = intent.getLongExtra(EXTRA_SIZE_BYTES, 0L),
        )
        when (val result = AndroidAppUpdateManager(context.applicationContext).downloadUpdate(update)) {
            is AppUpdateDownloadResult.Started -> {
                Toast.makeText(context, "已开始下载更新", Toast.LENGTH_SHORT).show()
            }
            is AppUpdateDownloadResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class AppUpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            openDownloadedUpdate(context, intent)
        }
    }

    private fun openDownloadedUpdate(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return
        val opened = AndroidAppUpdateManager(context.applicationContext).installDownloadedUpdate(downloadId)
        if (!opened) {
            Toast.makeText(context, "更新包下载完成，但无法打开安装器", Toast.LENGTH_SHORT).show()
        }
    }
}
