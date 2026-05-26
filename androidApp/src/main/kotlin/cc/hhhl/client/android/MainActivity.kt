package cc.hhhl.client.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
            val timelineCache = remember { AndroidTimelineCache(applicationContext) }
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
            HhhlApp(
                openUrl = ::openUrl,
                mediaPicker = mediaPicker,
                authCallbackSession = authCallbackSession,
                authTokenStore = authTokenStore,
                themeStore = themeStore,
                displayPreferenceStore = displayPreferenceStore,
                recentReactionStore = recentReactionStore,
                specialCareStore = specialCareStore,
                timelineCache = timelineCache,
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
}
