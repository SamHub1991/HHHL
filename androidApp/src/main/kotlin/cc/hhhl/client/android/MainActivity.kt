package cc.hhhl.client.android

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognizerIntent
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
import cc.hhhl.client.media.MediaPicker
import cc.hhhl.client.media.SpeechTextInput
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.notification.ChatNoiseReductionSettings
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var authCallbackSession by mutableStateOf<String?>(null)
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureImageLoader()
        consumeAuthCallback(intent)
        consumeSharedText(intent)
        setContent {
            val authTokenStore = remember { AndroidAuthTokenStore(applicationContext) }
            val themeStore = remember { AndroidThemeStore(applicationContext) }
            val displayPreferenceStore = remember { AndroidDisplayPreferenceStore(applicationContext) }
            val recentReactionStore = remember { AndroidRecentReactionStore(applicationContext) }
            val specialCareStore = remember { AndroidSpecialCareStore(applicationContext) }
            val automationStore = remember { AndroidAutomationStore(applicationContext) }
            val aiStore = remember { AndroidAiStore(applicationContext) }
            val composeDraftStore = remember { AndroidComposeDraftStore(applicationContext) }
            val favoriteMessageStore = remember { AndroidFavoriteMessageStore(applicationContext) }
            val chatMessageCache = remember { AndroidChatMessageCache(applicationContext) }
            val chatUnreadStore = remember { AndroidChatUnreadStore(applicationContext) }
            val notificationCache = remember { AndroidNotificationCache(applicationContext) }
            val notificationReadStore = remember { AndroidNotificationReadStore(applicationContext) }
            val timelineCache = remember { AndroidTimelineCache(applicationContext) }
            val releaseNotesStore = remember { AndroidReleaseNotesStore(applicationContext) }
            val backgroundNotificationStore = remember { AndroidBackgroundNotificationStore(applicationContext) }
            val appUpdateManager = remember { AndroidAppUpdateManager(applicationContext) }
            var backgroundNotificationsEnabled by remember {
                mutableStateOf(backgroundNotificationStore.isBackgroundSyncEnabled())
            }
            var specialCareBackgroundNotificationsEnabled by remember {
                mutableStateOf(backgroundNotificationStore.isSpecialCareEnabled())
            }
            var chatNoiseReductionSettings by remember {
                mutableStateOf(backgroundNotificationStore.loadChatNoiseReductionSettings())
            }
            var systemBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
            val coroutineScope = rememberCoroutineScope()
            var onMediaPicked by remember { mutableStateOf<((DriveFileUpload) -> Unit)?>(null) }
            var onMediaError by remember { mutableStateOf<((String) -> Unit)?>(null) }
            var onSpeechResult by remember { mutableStateOf<((String) -> Unit)?>(null) }
            var onSpeechError by remember { mutableStateOf<((String) -> Unit)?>(null) }
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
            val speechLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val success = result.resultCode == Activity.RESULT_OK
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                val resultCallback = onSpeechResult
                val errorCallback = onSpeechError
                onSpeechResult = null
                onSpeechError = null
                if (success && text.isNotBlank()) {
                    resultCallback?.invoke(text)
                } else {
                    errorCallback?.invoke("没有识别到语音内容")
                }
            }
            val mediaPicker = remember {
                MediaPicker { mimeType, onPicked, onError ->
                    onMediaPicked = onPicked
                    onMediaError = onError
                    runCatching {
                        mediaLauncher.launch(mimeType.ifBlank { "*/*" })
                    }.onFailure {
                        onMediaPicked = null
                        onMediaError = null
                        onError("无法打开系统文件选择器")
                    }
                }
            }
            val speechTextInput = remember {
                SpeechTextInput { prompt, onResult, onError ->
                    onSpeechResult = onResult
                    onSpeechError = onError
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    runCatching {
                        speechLauncher.launch(intent)
                    }.onFailure {
                        onSpeechResult = null
                        onSpeechError = null
                        onError("无法打开系统语音输入")
                    }
                }
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (backgroundNotificationsEnabled) {
                    BackgroundNotificationScheduler.apply(applicationContext, enabled = true)
                    if (granted) {
                        BackgroundNotificationScheduler.syncNow(applicationContext)
                    }
                }
            }
            LaunchedEffect(backgroundNotificationsEnabled, specialCareBackgroundNotificationsEnabled) {
                if (backgroundNotificationsEnabled && shouldRequestNotificationPermission()) {
                    runCatching {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            LaunchedEffect(backgroundNotificationsEnabled) {
                BackgroundNotificationScheduler.apply(applicationContext, backgroundNotificationsEnabled)
            }
            LaunchedEffect(Unit) {
                launch(Dispatchers.IO) {
                    when (val result = appUpdateManager.checkForUpdates()) {
                        is AppUpdateCheckResult.UpdateAvailable -> {
                            appUpdateManager.downloadUpdate(result.update)
                            appUpdateManager.notifyUpdateAvailable(result.update)
                        }
                        is AppUpdateCheckResult.NoUpdate,
                        is AppUpdateCheckResult.Error,
                            -> Unit
                    }
                }
            }
            BackHandler(enabled = systemBackHandler != null) {
                val handled = systemBackHandler?.invoke() == true
                if (!handled) {
                    moveTaskToBack(false)
                }
            }
            HhhlApp(
                openUrl = ::openUrl,
                shareUrl = ::shareUrl,
                downloadUrl = ::downloadUrl,
                mediaPicker = mediaPicker,
                speechTextInput = speechTextInput,
                authCallbackSession = authCallbackSession,
                authTokenStore = authTokenStore,
                themeStore = themeStore,
                displayPreferenceStore = displayPreferenceStore,
                recentReactionStore = recentReactionStore,
                specialCareStore = specialCareStore,
                automationStore = automationStore,
                aiStore = aiStore,
                composeDraftStore = composeDraftStore,
                favoriteMessageStore = favoriteMessageStore,
                chatMessageCache = chatMessageCache,
                chatUnreadStore = chatUnreadStore,
                notificationCache = notificationCache,
                notificationReadStore = notificationReadStore,
                timelineCache = timelineCache,
                backgroundNotificationsEnabled = backgroundNotificationsEnabled,
                specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
                chatNoiseReductionSettings = chatNoiseReductionSettings,
                onBackgroundNotificationsChanged = { enabled ->
                    backgroundNotificationsEnabled = enabled
                    backgroundNotificationStore.setBackgroundSyncEnabled(enabled)
                    BackgroundNotificationScheduler.apply(applicationContext, enabled)
                    if (enabled && shouldRequestNotificationPermission()) {
                        runCatching {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                },
                onSpecialCareBackgroundNotificationsChanged = { enabled ->
                    specialCareBackgroundNotificationsEnabled = enabled
                    backgroundNotificationStore.setSpecialCareEnabled(enabled)
                    if (backgroundNotificationsEnabled) {
                        BackgroundNotificationScheduler.syncNow(applicationContext)
                    }
                },
                onChatNoiseReductionSettingsChanged = { settings: ChatNoiseReductionSettings ->
                    val normalized = settings.normalized
                    chatNoiseReductionSettings = normalized
                    backgroundNotificationStore.saveChatNoiseReductionSettings(normalized)
                    if (backgroundNotificationsEnabled) {
                        BackgroundNotificationScheduler.syncNow(applicationContext)
                    }
                },
                onSpecialCareUsersChanged = {
                    if (backgroundNotificationsEnabled && specialCareBackgroundNotificationsEnabled) {
                        BackgroundNotificationScheduler.syncNow(applicationContext)
                    }
                },
                onSpecialCareSystemNotification = { notification ->
                    coroutineScope.launch(Dispatchers.IO) {
                        publishChatAttentionSystemNotification(notification)
                    }
                },
                onAutomationSystemNotification = { title, body ->
                    publishAutomationSystemNotification(title, body)
                },
                onAiQueueChanged = {
                    AiBackgroundScheduler.syncNow(applicationContext)
                },
                initialSharedText = sharedText,
                onInitialSharedTextConsumed = { sharedText = null },
                appVersionName = BuildConfig.VERSION_NAME,
                releaseNotesStore = releaseNotesStore,
                onCheckForUpdates = { report ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val message = when (val result = appUpdateManager.checkForUpdates()) {
                            is AppUpdateCheckResult.UpdateAvailable -> {
                                appUpdateManager.notifyUpdateAvailable(result.update)
                                when (val download = appUpdateManager.downloadUpdate(result.update)) {
                                    is AppUpdateDownloadResult.Started -> {
                                        if (download.alreadyEnqueued) {
                                            "发现新版本 ${result.update.versionName}，下载任务已存在"
                                        } else {
                                            "发现新版本 ${result.update.versionName}，已开始自动下载"
                                        }
                                    }
                                    is AppUpdateDownloadResult.Error -> download.message
                                }
                            }
                            is AppUpdateCheckResult.NoUpdate -> "当前已是最新版本 ${result.currentVersion}"
                            is AppUpdateCheckResult.Error -> result.message
                        }
                        withContext(Dispatchers.Main) {
                            report(message)
                        }
                    }
                },
                onOpenBatteryOptimizationSettings = ::openBatteryOptimizationSettings,
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
        consumeSharedText(intent)
    }

    override fun onResume() {
        super.onResume()
        AndroidAppUpdateManager(applicationContext).retryPendingInstall()
        BackgroundNotificationScheduler.restoreIfEnabled(applicationContext)
    }

    private fun consumeAuthCallback(intent: Intent?) {
        val session = intent?.data?.parseMiAuthSession()
        if (session != null) {
            authCallbackSession = session
        }
    }

    private fun consumeSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: return
        sharedText = text.takeIf { it.isNotBlank() }
        intent.action = null
    }

    private fun Uri.parseMiAuthSession(): String? {
        if (scheme != "hhhl" || host != "miauth") return null
        return getQueryParameter("session")?.takeIf { it.isNotBlank() }
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
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)))
        }.onFailure {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, cleanUrl)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, "分享帖子"))
        }.onFailure {
            Toast.makeText(this, "无法打开分享面板", Toast.LENGTH_SHORT).show()
        }
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

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "当前系统无需单独设置电池优化", Toast.LENGTH_SHORT).show()
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val packageUri = Uri.parse("package:$packageName")
        val alreadyIgnored = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        val primaryIntent = if (alreadyIgnored) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = packageUri
            }
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = packageUri
            }
        }
        val opened = runCatching {
            startActivity(primaryIntent)
            true
        }.getOrDefault(false)
        if (!opened) {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }.onFailure {
                Toast.makeText(this, "无法打开电池优化设置，请在系统设置里允许 HHHL 后台运行", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun publishChatAttentionSystemNotification(notification: NotificationItem) {
        val settings = AndroidBackgroundNotificationStore(applicationContext)
        if (!settings.isBackgroundSyncEnabled()) return
        if (notification.isSpecialCare && !settings.isSpecialCareEnabled()) return
        if (shouldRequestNotificationPermission()) return
        val eventId = notification.systemNotificationEventId()
        if (!settings.claimSeenIdGroup(notification.notificationSeenIds())) return

        BackgroundNotificationPublisher(applicationContext).publish(
            listOf(
                BackgroundNotificationEvent(
                    id = eventId,
                    title = "${notification.chatAttentionSystemTitlePrefix()} · ${notification.actor.displayName}",
                    text = notification.notePreviewText?.takeIf { it.isNotBlank() }
                        ?: notification.text.ifBlank { "有新的聊天提醒" },
                    specialCare = notification.isSpecialCare,
                    avatarMode = NotificationAvatarMode.UserAvatar(
                        notification.actor.avatarUrl,
                        notification.actor.avatarInitial,
                    ),
                    createdAtEpochMillis = notification.createdAtEpochMillis.takeIf { it > 0L }
                        ?: System.currentTimeMillis(),
                    cacheNotification = notification.takeIf { it.isSpecialCare }?.copy(isSpecialCare = true),
                ),
            ),
        )
        if (notification.isSpecialCare) {
            AndroidAuthTokenStore(applicationContext)
                .readAccountSessions()
                .firstOrNull { it.current }
                ?.let { session ->
                    applicationContext.cacheSpecialCareNotifications(
                        accountId = session.id,
                        notifications = listOf(notification.copy(isSpecialCare = true)),
                    )
                }
        }
    }

    private fun publishAutomationSystemNotification(title: String, body: String): Boolean {
        if (shouldRequestNotificationPermission()) return false
        val cleanTitle = title.trim().ifBlank { "HHHL 自动化" }
        val cleanBody = body.trim().ifBlank { "自动化规则已执行" }
        BackgroundNotificationPublisher(applicationContext).publish(
            listOf(
                BackgroundNotificationEvent(
                    id = "automation:${cleanTitle.hashCode()}:${cleanBody.hashCode()}:${System.currentTimeMillis()}",
                    title = cleanTitle,
                    text = cleanBody,
                    specialCare = false,
                    avatarMode = NotificationAvatarMode.AppIcon,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    cacheNotification = null,
                ),
            ),
        )
        return true
    }
}

private fun NotificationItem.chatAttentionSystemTitlePrefix(): String {
    val cleanText = text.trim()
    return when {
        cleanText.startsWith("有人 @ 你") -> "有人 @ 你"
        cleanText.startsWith("有人回复你") -> "有人回复你"
        cleanText.startsWith("有人引用你") -> "有人引用你"
        else -> "特别关心"
    }
}

private fun String.toDownloadTitle(url: String): String {
    val genericLabels = setOf("附件", "图片", "视频", "音频", "敏感内容", "文件")
    return trim().takeIf { it.isNotBlank() && it !in genericLabels }
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
    val extension = when (mimeType.substringBefore(';').trim().lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/bmp" -> "bmp"
        "image/svg+xml" -> "svg"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/x-matroska" -> "mkv"
        "video/x-msvideo" -> "avi"
        "video/mpeg" -> "mpg"
        "audio/mpeg" -> "mp3"
        "audio/mp4" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg" -> "ogg"
        "audio/wav" -> "wav"
        "audio/webm" -> "webm"
        "audio/flac" -> "flac"
        "application/pdf" -> "pdf"
        "application/json" -> "json"
        "application/ld+json" -> "json"
        "application/xml" -> "xml"
        "application/zip", "application/x-zip-compressed" -> "zip"
        "application/vnd.rar", "application/x-rar-compressed" -> "rar"
        "application/x-7z-compressed" -> "7z"
        "application/gzip" -> "gz"
        "application/x-tar" -> "tar"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.ms-powerpoint" -> "ppt"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        "application/vnd.android.package-archive" -> "apk"
        "text/plain" -> "txt"
        "text/markdown" -> "md"
        "text/csv" -> "csv"
        "text/html" -> "html"
        "text/css" -> "css"
        "text/javascript" -> "js"
        "text/xml" -> "xml"
        else -> "bin"
    }
    return "$clean.$extension"
}
