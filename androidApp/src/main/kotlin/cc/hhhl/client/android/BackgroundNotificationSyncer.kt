package cc.hhhl.client.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.hhhl.client.api.NotificationLoadResult
import cc.hhhl.client.api.SharkeyNotificationApi
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.cache.ChatUnreadSnapshot
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatUserConversationRepositoryResult
import cc.hhhl.client.repository.TimelineRepository
import cc.hhhl.client.repository.TimelineRepositoryResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class BackgroundNotificationSyncResult {
    Success,
    Retry,
}

class BackgroundNotificationSyncer(
    private val context: Context,
) {
    suspend fun sync(): BackgroundNotificationSyncResult {
        val settings = AndroidBackgroundNotificationStore(context)
        if (!settings.isBackgroundSyncEnabled()) return BackgroundNotificationSyncResult.Success

        val session = AndroidAuthTokenStore(context)
            .readAccountSessions()
            .firstOrNull { it.current }
            ?: return BackgroundNotificationSyncResult.Success
        val token = session.token.trim()
        if (token.isEmpty()) return BackgroundNotificationSyncResult.Success

        val specialCareUserIds = AndroidSpecialCareStore(context).loadSpecialCareUserIds(session.id)
        val specialCareEnabled = settings.isSpecialCareEnabled()
        val seenIds = settings.loadSeenIds()
        val chatUnreadStore = AndroidChatUnreadStore(context)

        return runCatching {
            coroutineScope {
                val notificationsDeferred = async {
                    SharkeyNotificationApi().loadNotifications(token, limit = 20)
                }
                val chatDeferred = async {
                    ChatRepository(
                        tokenProvider = { token },
                        currentUserIdProvider = { session.user?.id },
                    ).refresh()
                }
                val userChatDeferred = async {
                    ChatRepository(
                        tokenProvider = { token },
                        currentUserIdProvider = { session.user?.id },
                    ).refreshUserConversations()
                }
                val specialCareTimelineDeferred = async {
                    if (specialCareEnabled && specialCareUserIds.isNotEmpty()) {
                        TimelineRepository(tokenProvider = { token }).refresh(TimelineKind.Home)
                    } else {
                        null
                    }
                }

                val events = mutableListOf<BackgroundNotificationEvent>()
                when (val result = notificationsDeferred.await()) {
                    is NotificationLoadResult.Success -> {
                        events += result.notifications
                            .filter { !it.isRead }
                            .filter { "notification:${it.id}" !in seenIds }
                            .map { item ->
                                val isSpecialCare = item.actor.id in specialCareUserIds
                                BackgroundNotificationEvent(
                                    id = "notification:${item.id}",
                                    title = if (isSpecialCare) {
                                        "特别关心 · ${item.actor.displayName}"
                                    } else {
                                        item.actor.displayName
                                    },
                                    text = item.notePreviewText?.takeIf { it.isNotBlank() } ?: item.text,
                                    specialCare = isSpecialCare,
                                )
                            }
                    }
                    NotificationLoadResult.Unauthorized -> return@coroutineScope BackgroundNotificationSyncResult.Success
                    is NotificationLoadResult.NetworkError -> return@coroutineScope BackgroundNotificationSyncResult.Retry
                    is NotificationLoadResult.ServerError -> Unit
                }

                when (val result = chatDeferred.await()) {
                    is ChatRepositoryResult.Success -> {
                        val roomLatestMarkers = result.rooms.associate { room ->
                            room.id to room.unreadMarker()
                        }
                        val mergedUnread = chatUnreadStore.merge(
                            session.id,
                            ChatUnreadSnapshot(
                                roomCounts = result.rooms
                                    .filter { it.unreadCount > 0 }
                                    .associate { it.id to it.unreadCount },
                            ),
                            roomLatestMarkers = roomLatestMarkers,
                        )
                        events += result.rooms
                            .filter { it.unreadCount > 0 }
                            .filter { room ->
                                roomEventId(
                                    roomId = room.id,
                                    marker = roomLatestMarkers[room.id].orEmpty(),
                                    unreadCount = room.unreadCount,
                                ) !in seenIds
                            }
                            .map { room ->
                                val isSpecialCare = room.owner.id in specialCareUserIds
                                val unreadCount = mergedUnread.roomCounts[room.id] ?: room.unreadCount
                                BackgroundNotificationEvent(
                                    id = roomEventId(
                                        roomId = room.id,
                                        marker = roomLatestMarkers[room.id].orEmpty(),
                                        unreadCount = room.unreadCount,
                                    ),
                                    title = if (isSpecialCare) "特别关心聊天室 · ${room.name}" else room.name,
                                    text = "${unreadCount.coerceAtLeast(0)} 条未读消息",
                                    specialCare = isSpecialCare,
                                )
                            }
                    }
                    ChatRepositoryResult.Unauthorized -> return@coroutineScope BackgroundNotificationSyncResult.Success
                    is ChatRepositoryResult.Error -> Unit
                }

                when (val result = userChatDeferred.await()) {
                    is ChatUserConversationRepositoryResult.Success -> {
                        val userLatestMarkers = result.conversations.associate { conversation ->
                            conversation.user.id to conversation.latestMessage.unreadMarker()
                        }
                        val mergedUnread = chatUnreadStore.merge(
                            session.id,
                            ChatUnreadSnapshot(
                                userCounts = result.conversations
                                    .filter { it.unreadCount > 0 }
                                    .associate { it.user.id to it.unreadCount },
                            ),
                            userLatestMarkers = userLatestMarkers,
                        )
                        events += result.conversations
                            .filter { it.unreadCount > 0 }
                            .filter { conversation ->
                                userEventId(
                                    userId = conversation.user.id,
                                    marker = userLatestMarkers[conversation.user.id].orEmpty(),
                                    unreadCount = conversation.unreadCount,
                                ) !in seenIds
                            }
                            .map { conversation ->
                                val isSpecialCare = conversation.user.id in specialCareUserIds
                                val unreadCount = mergedUnread.userCounts[conversation.user.id] ?: conversation.unreadCount
                                BackgroundNotificationEvent(
                                    id = userEventId(
                                        userId = conversation.user.id,
                                        marker = userLatestMarkers[conversation.user.id].orEmpty(),
                                        unreadCount = conversation.unreadCount,
                                    ),
                                    title = if (isSpecialCare) {
                                        "特别关心 · ${conversation.user.displayName}"
                                    } else {
                                        conversation.user.displayName
                                    },
                                    text = conversation.latestMessage?.text?.takeIf { it.isNotBlank() }
                                        ?: "${unreadCount.coerceAtLeast(0)} 条未读私聊",
                                    specialCare = isSpecialCare,
                                )
                            }
                    }
                    ChatUserConversationRepositoryResult.Unauthorized -> return@coroutineScope BackgroundNotificationSyncResult.Success
                    is ChatUserConversationRepositoryResult.Error -> Unit
                }

                when (val result = specialCareTimelineDeferred.await()) {
                    is TimelineRepositoryResult.Success -> {
                        events += result.notes
                            .filter { it.author.id in specialCareUserIds }
                            .filter { "special-note:${it.id}" !in seenIds }
                            .map { note ->
                                BackgroundNotificationEvent(
                                    id = "special-note:${note.id}",
                                    title = "特别关心 · ${note.author.displayName}",
                                    text = note.text.ifBlank { "发布了新帖子" },
                                    specialCare = true,
                                )
                            }
                    }
                    TimelineRepositoryResult.Unauthorized -> return@coroutineScope BackgroundNotificationSyncResult.Success
                    is TimelineRepositoryResult.Error,
                    null -> Unit
                }

                val visibleEvents = events
                    .filter { !it.specialCare || specialCareEnabled }
                    .distinctBy { it.id }
                    .sortedByDescending { it.specialCare }
                    .take(MAX_NOTIFICATIONS_PER_SYNC)
                if (visibleEvents.isNotEmpty()) {
                    BackgroundNotificationPublisher(context).publish(visibleEvents)
                    settings.saveSeenIds((visibleEvents.map { it.id } + seenIds).take(200).toSet())
                }
                BackgroundNotificationSyncResult.Success
            }
        }.getOrElse {
            BackgroundNotificationSyncResult.Retry
        }
    }

    private companion object {
        const val MAX_NOTIFICATIONS_PER_SYNC = 5
    }
}

private data class BackgroundNotificationEvent(
    val id: String,
    val title: String,
    val text: String,
    val specialCare: Boolean,
)

private fun ChatRoom.unreadMarker(): String {
    return latestMessageMarker.ifBlank { latestMessageAtLabel }
}

private fun ChatMessage?.unreadMarker(): String {
    val message = this ?: return ""
    return message.id.ifBlank { message.createdAt.ifBlank { message.createdAtLabel } }
}

private fun roomEventId(
    roomId: String,
    marker: String,
    unreadCount: Int,
): String {
    return "room:$roomId:${marker.ifBlank { unreadCount.toString() }}"
}

private fun userEventId(
    userId: String,
    marker: String,
    unreadCount: Int,
): String {
    return "user:$userId:${marker.ifBlank { unreadCount.toString() }}"
}

private class BackgroundNotificationPublisher(
    private val context: Context,
) {
    fun publish(events: List<BackgroundNotificationEvent>) {
        if (!canPostNotifications()) return
        ensureChannels(context)
        val notificationManager = NotificationManagerCompat.from(context)
        events.forEach { event ->
            val channelId = if (event.specialCare) SPECIAL_CARE_CHANNEL_ID else MESSAGE_CHANNEL_ID
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(event.title)
                .setContentText(event.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.text))
                .setPriority(if (event.specialCare) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build()
            notifySafely(notificationManager, event.id.hashCode(), notification)
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun notifySafely(
        manager: NotificationManagerCompat,
        notificationId: Int,
        notification: android.app.Notification,
    ) {
        if (!canPostNotifications()) return
        runCatching {
            manager.notify(notificationId, notification)
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

fun ensureChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            MESSAGE_CHANNEL_ID,
            "后台消息",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "聊天和通知的后台提醒"
        },
    )
    manager.createNotificationChannel(
        NotificationChannel(
            SPECIAL_CARE_CHANNEL_ID,
            "特别关心",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "特别关心用户的后台提醒"
        },
    )
    manager.createNotificationChannel(
        NotificationChannel(
            REALTIME_SERVICE_CHANNEL_ID,
            "实时同步",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持实时消息同步"
        },
    )
}

const val MESSAGE_CHANNEL_ID = "hhhl_background_messages"
const val SPECIAL_CARE_CHANNEL_ID = "hhhl_special_care_messages"
const val REALTIME_SERVICE_CHANNEL_ID = "hhhl_realtime_sync"
