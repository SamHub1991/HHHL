package cc.hhhl.client.android

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cc.hhhl.client.api.MainStreamingEvent
import cc.hhhl.client.repository.MainStreamingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RealtimeNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannels(applicationContext)
        startRealtimeForeground()
        scope.launch {
            runRealtimeLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AndroidBackgroundNotificationStore(applicationContext).isBackgroundSyncEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runRealtimeLoop() {
        val settings = AndroidBackgroundNotificationStore(applicationContext)
        while (settings.isBackgroundSyncEnabled()) {
            val session = AndroidAuthTokenStore(applicationContext)
                .readAccountSessions()
                .firstOrNull { it.current }
            val token = session?.token?.trim().orEmpty()
            if (token.isBlank()) {
                delay(RECONNECT_DELAY_MS)
                continue
            }

            var unauthorized = false
            runCatching {
                MainStreamingRepository(tokenProvider = { token }).streamMain().collect { event ->
                    when (event) {
                        MainStreamingEvent.UnreadNotification,
                        MainStreamingEvent.NewChatMessage,
                        -> BackgroundNotificationSyncer(applicationContext).sync()
                        MainStreamingEvent.Unauthorized -> unauthorized = true
                        MainStreamingEvent.ReadAllNotifications,
                        is MainStreamingEvent.TimelineNote,
                        MainStreamingEvent.Connected,
                        MainStreamingEvent.Connecting,
                        MainStreamingEvent.Closed,
                        is MainStreamingEvent.Error,
                        -> Unit
                    }
                }
            }
            if (unauthorized) {
                stopSelf()
                return
            }
            delay(RECONNECT_DELAY_MS)
        }
        stopSelf()
    }

    private fun startRealtimeForeground() {
        val notification = realtimeNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun realtimeNotification(): Notification {
        return NotificationCompat.Builder(applicationContext, REALTIME_SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("HHHL 实时同步中")
            .setContentText("正在实时接收消息和特别关心提醒")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1001
        private const val RECONNECT_DELAY_MS = 3_000L

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, RealtimeNotificationService::class.java)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, RealtimeNotificationService::class.java)
            context.applicationContext.stopService(intent)
        }
    }
}
