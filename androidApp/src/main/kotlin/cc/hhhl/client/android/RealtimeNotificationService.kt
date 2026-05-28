package cc.hhhl.client.android

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cc.hhhl.client.api.MainStreamingEvent
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.repository.MainStreamingRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RealtimeNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastTimelineSyncAt: Long = 0L

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
                        MainStreamingEvent.UnreadNotification -> BackgroundNotificationSyncer(applicationContext).sync()
                        MainStreamingEvent.NewChatMessage -> syncRealtimeChatEvent()
                        is MainStreamingEvent.TimelineNote -> syncTimelineEvent(event)
                        MainStreamingEvent.Unauthorized -> unauthorized = true
                        MainStreamingEvent.ReadAllNotifications,
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

    private suspend fun syncTimelineEvent(event: MainStreamingEvent.TimelineNote) {
        if (publishSpecialCareTimelineNote(event)) return
        val now = System.currentTimeMillis()
        if (now - lastTimelineSyncAt < TIMELINE_SYNC_DEBOUNCE_MS) return
        lastTimelineSyncAt = now
        BackgroundNotificationSyncer(applicationContext).sync()
    }

    private suspend fun syncRealtimeChatEvent() {
        val syncer = BackgroundNotificationSyncer(applicationContext)
        syncer.sync(trigger = BackgroundNotificationSyncTrigger.RealtimeChat)
        delay(CHAT_EVENT_RECHECK_DELAY_MS)
        syncer.sync(trigger = BackgroundNotificationSyncTrigger.RealtimeChat)
    }

    private suspend fun publishSpecialCareTimelineNote(event: MainStreamingEvent.TimelineNote): Boolean {
        val note = event.note ?: return false
        val session = AndroidAuthTokenStore(applicationContext)
            .readAccountSessions()
            .firstOrNull { it.current }
            ?: return false
        val settings = AndroidBackgroundNotificationStore(applicationContext)
        if (!settings.isSpecialCareEnabled()) return true

        val specialCareUserIds = AndroidSpecialCareStore(applicationContext).loadSpecialCareUserIds(session.id)
        if (note.author.id !in specialCareUserIds) return true

        val eventId = "special-note:${note.id}"
        val seenIds = settings.loadSeenIds()
        if (eventId in seenIds) return true

        val createdAtEpochMillis = note.createdAt.toEpochMillisOrNull()
            ?: System.currentTimeMillis()
        val notification = NotificationItem(
            id = "special-care-note-${note.id}",
            type = NotificationType.Note,
            actor = note.author,
            text = "发布了新帖子",
            createdAtLabel = note.createdAtLabel.ifBlank { "刚刚" },
            createdAtEpochMillis = createdAtEpochMillis,
            noteId = note.id,
            notePreviewText = note.text.takeIf { it.isNotBlank() } ?: note.cw,
            isSpecialCare = true,
        )
        BackgroundNotificationPublisher(applicationContext).publish(
            listOf(
                BackgroundNotificationEvent(
                    id = eventId,
                    title = "特别关心 · ${note.author.displayName}",
                    text = note.text.ifBlank { "发布了新帖子" },
                    specialCare = true,
                    avatarMode = NotificationAvatarMode.UserAvatar(
                        note.author.avatarUrl,
                        note.author.avatarInitial,
                    ),
                    createdAtEpochMillis = createdAtEpochMillis,
                    cacheNotification = notification,
                ),
            ),
        )
        applicationContext.cacheSpecialCareNotifications(
            accountId = session.id,
            notifications = listOf(notification),
        )
        settings.saveSeenIds((listOf(eventId) + seenIds).take(MAX_REALTIME_SEEN_IDS).toSet())
        return true
    }

    private fun String.toEpochMillisOrNull(): Long? {
        return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
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
            .setSmallIcon(R.drawable.dc_icon)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.dc_icon))
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
        private const val TIMELINE_SYNC_DEBOUNCE_MS = 2_000L
        private const val CHAT_EVENT_RECHECK_DELAY_MS = 1_500L
        private const val MAX_REALTIME_SEEN_IDS = 200

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
