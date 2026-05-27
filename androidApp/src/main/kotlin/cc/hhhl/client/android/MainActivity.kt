package cc.hhhl.client.android

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import cc.hhhl.client.HhhlApp
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.auth.MiAuthCallback
import cc.hhhl.client.media.MediaPicker
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var authCallbackSession by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureImageLoader()
        consumeAuthCallback(intent)
        setContent {
            val authTokenStore = remember { AndroidAuthTokenStore(applicationContext) }
            val themeStore = remember { AndroidThemeStore(applicationContext) }
            val displayPreferenceStore = remember { AndroidDisplayPreferenceStore(applicationContext) }
            val recentReactionStore = remember { AndroidRecentReactionStore(applicationContext) }
            val specialCareStore = remember { AndroidSpecialCareStore(applicationContext) }
            val composeDraftStore = remember { AndroidComposeDraftStore(applicationContext) }
            val chatMessageCache = remember { AndroidChatMessageCache(applicationContext) }
            val chatUnreadStore = remember { AndroidChatUnreadStore(applicationContext) }
            val notificationCache = remember { AndroidNotificationCache(applicationContext) }
            val notificationReadStore = remember { AndroidNotificationReadStore(applicationContext) }
            val timelineCache = remember { AndroidTimelineCache(applicationContext) }
            val backgroundNotificationStore = remember { AndroidBackgroundNotificationStore(applicationContext) }
            var backgroundNotificationsEnabled by remember {
                mutableStateOf(backgroundNotificationStore.isBackgroundSyncEnabled())
            }
            var specialCareBackgroundNotificationsEnabled by remember {
                mutableStateOf(backgroundNotificationStore.isSpecialCareEnabled())
            }
            var systemBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
            val coroutineScope = rememberCoroutineScope()
            var onMediaPicked by remember { mutableStateOf<((DriveFileUpload) -> Unit)?>(null) }
            var onMediaError by remember { mutableStateOf<((String) -> Unit)?>(null) }
            val mediaLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetMultipleContents(),
            ) { uris ->
                if (uris.isEmpty()) return@rememberLauncherForActivityResult
                val picked = onMediaPicked
                val failed = onMediaError
                coroutineScope.launch {
                    uris.forEach { uri ->
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                applicationContext.contentResolver.readDriveFileUpload(uri)
                            }
                        }
                        result
                            .onSuccess { upload -> picked?.invoke(upload) }
                            .onFailure { error -> failed?.invoke(error.message ?: "无法读取所选文件") }
                    }
                }
            }
            val mediaPicker = remember {
                MediaPicker { mimeType, onPicked, onError ->
                    onMediaPicked = onPicked
                    onMediaError = onError
                    mediaLauncher.launch(mimeType.ifBlank { "*/*" })
                }
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted && backgroundNotificationsEnabled) {
                    BackgroundNotificationScheduler.syncNow(applicationContext)
                }
            }
            LaunchedEffect(backgroundNotificationsEnabled) {
                BackgroundNotificationScheduler.apply(applicationContext, backgroundNotificationsEnabled)
            }
            BackHandler(enabled = systemBackHandler != null) {
                val handled = systemBackHandler?.invoke() == true
                if (!handled) {
                    moveTaskToBack(false)
                }
            }
            HhhlApp(
                openUrl = ::openUrl,
                downloadUrl = ::downloadUrl,
                mediaPicker = mediaPicker,
                authCallbackSession = authCallbackSession,
                authTokenStore = authTokenStore,
                themeStore = themeStore,
                displayPreferenceStore = displayPreferenceStore,
                recentReactionStore = recentReactionStore,
                specialCareStore = specialCareStore,
                composeDraftStore = composeDraftStore,
                chatMessageCache = chatMessageCache,
                chatUnreadStore = chatUnreadStore,
                notificationCache = notificationCache,
                notificationReadStore = notificationReadStore,
                timelineCache = timelineCache,
                backgroundNotificationsEnabled = backgroundNotificationsEnabled,
                specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
                onBackgroundNotificationsChanged = { enabled ->
                    backgroundNotificationsEnabled = enabled
                    backgroundNotificationStore.setBackgroundSyncEnabled(enabled)
                    BackgroundNotificationScheduler.apply(applicationContext, enabled)
                    if (enabled && shouldRequestNotificationPermission()) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onSpecialCareBackgroundNotificationsChanged = { enabled ->
                    specialCareBackgroundNotificationsEnabled = enabled
                    backgroundNotificationStore.setSpecialCareEnabled(enabled)
                    if (backgroundNotificationsEnabled) {
                        BackgroundNotificationScheduler.syncNow(applicationContext)
                    }
                },
                onBackHandlerChanged = { handler ->
                    systemBackHandler = handler
                },
                onAuthCallbackConsumed = { authCallbackSession = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAuthCallback(intent)
    }

    private fun consumeAuthCallback(intent: Intent?) {
        val session = MiAuthCallback.parseSession(intent?.dataString)
        if (session != null) {
            authCallbackSession = session
        }
    }

    private fun configureImageLoader() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.2)
                        .strongReferencesEnabled(true)
                        .weakReferencesEnabled(true)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("coil_image_cache"))
                        .maxSizePercent(0.02)
                        .cleanupCoroutineContext(Dispatchers.IO)
                        .build()
                }
                .build()
        }
    }

    private fun openUrl(url: String) {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
        )
    }

    private fun downloadUrl(url: String, label: String, mimeType: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        runCatching {
            val request = DownloadManager.Request(Uri.parse(cleanUrl))
                .setTitle(label.toDownloadTitle(cleanUrl))
                .setDescription("HHHL")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            mimeType.trim().takeIf { it.isNotEmpty() }?.let(request::setMimeType)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                label.toDownloadFileName(cleanUrl, mimeType),
            )
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }.onSuccess {
            Toast.makeText(this, "已开始下载", Toast.LENGTH_SHORT).show()
        }.onFailure {
            openUrl(cleanUrl)
        }
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
    }
}

private fun String.toDownloadTitle(url: String): String {
    return trim().takeIf { it.isNotBlank() && it != "附件" && it != "图片" }
        ?: Uri.parse(url).lastPathSegment?.substringBefore('?')?.takeIf { it.isNotBlank() }
        ?: "HHHL 附件"
}

private fun String.toDownloadFileName(url: String, mimeType: String): String {
    val fromUrl = Uri.parse(url).lastPathSegment
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() && "." in it }
    val base = fromUrl ?: toDownloadTitle(url).withExtensionForMime(mimeType)
    return base
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .take(96)
        .ifBlank { "hhhl_attachment" }
}

private fun String.withExtensionForMime(mimeType: String): String {
    val clean = trim().ifBlank { "hhhl_attachment" }
    if ("." in clean.substringAfterLast('/')) return clean
    val extension = when (mimeType.substringBefore(';').lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "audio/mpeg" -> "mp3"
        "application/pdf" -> "pdf"
        else -> "bin"
    }
    return "$clean.$extension"
}
