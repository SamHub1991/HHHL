package cc.hhhl.client.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.hhhl.client.api.NotificationLoadResult
import cc.hhhl.client.api.SharkeyNotificationApi
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.cache.ChatUnreadSnapshot
import cc.hhhl.client.cache.NotificationCacheSnapshot
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatUserConversationRepositoryResult
import cc.hhhl.client.repository.TimelineRepository
import cc.hhhl.client.repository.TimelineRepositoryResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.URL
import java.time.Instant

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
                val chatRepository = ChatRepository(
                    tokenProvider = { token },
                    currentUserIdProvider = { session.user?.id },
                )
                val notificationsDeferred = async {
                    SharkeyNotificationApi().loadNotifications(token, limit = 20)
                }
                val chatDeferred = async {
                    chatRepository.refresh()
                }
                val userChatDeferred = async {
                    chatRepository.refreshUserConversations()
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
                                val createdAtEpochMillis = item.createdAtEpochMillis.takeIf { it > 0L }
                                    ?: System.currentTimeMillis()
                                BackgroundNotificationEvent(
                                    id = "notification:${item.id}",
                                    title = if (isSpecialCare) {
                                        "特别关心 · ${item.actor.displayName}"
                                    } else {
                                        item.actor.displayName
                                    },
                                    text = item.notePreviewText?.takeIf { it.isNotBlank() } ?: item.text,
                                    specialCare = isSpecialCare,
                                    avatarMode = if (item.isAppNotification()) {
                                        NotificationAvatarMode.AppIcon
                                    } else if (isSpecialCare) {
                                        NotificationAvatarMode.UserAvatar(item.actor.avatarUrl, item.actor.avatarInitial)
                                    } else {
                                        NotificationAvatarMode.Transparent
                                    },
                                    createdAtEpochMillis = createdAtEpochMillis,
                                    cacheNotification = item.takeIf { isSpecialCare }?.copy(
                                        isSpecialCare = true,
                                        createdAtEpochMillis = createdAtEpochMillis,
                                    ),
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
                        val unreadRooms = result.rooms
                            .filter { it.unreadCount > 0 }
                        for (room in unreadRooms) {
                            val latestSpecialCareMessage = if (specialCareEnabled && specialCareUserIds.isNotEmpty()) {
                                chatRepository.latestSpecialCareRoomMessage(room.id, specialCareUserIds)
                            } else {
                                null
                            }
                            val actor = latestSpecialCareMessage?.fromUser ?: room.owner
                            val isSpecialCare = latestSpecialCareMessage != null || room.owner.id in specialCareUserIds
                            val latestMarker = latestSpecialCareMessage?.unreadMarker()
                                ?: roomLatestMarkers[room.id].orEmpty()
                            val latestPreview = latestSpecialCareMessage?.text?.takeIf { it.isNotBlank() }
                            val latestCreatedAtLabel = latestSpecialCareMessage?.createdAtLabel
                                ?.takeIf { it.isNotBlank() }
                                ?: room.latestMessageAtLabel.ifBlank { "刚刚" }
                            val unreadCount = mergedUnread.roomCounts[room.id] ?: room.unreadCount
                            val eventId = roomEventId(
                                roomId = room.id,
                                marker = latestMarker,
                                unreadCount = room.unreadCount,
                            )
                            if (eventId in seenIds) continue
                            val createdAtEpochMillis = latestSpecialCareMessage?.createdAt.toEpochMillisOrNow()
                            events += BackgroundNotificationEvent(
                                id = eventId,
                                title = if (isSpecialCare) "特别关心 · ${actor.displayName}" else room.name,
                                text = latestPreview ?: "${unreadCount.coerceAtLeast(0)} 条未读消息",
                                specialCare = isSpecialCare,
                                avatarMode = if (isSpecialCare) {
                                    NotificationAvatarMode.UserAvatar(actor.avatarUrl, actor.avatarInitial)
                                } else {
                                    NotificationAvatarMode.Transparent
                                },
                                createdAtEpochMillis = createdAtEpochMillis,
                                cacheNotification = if (isSpecialCare) {
                                    NotificationItem(
                                        id = "special-care-room-${room.id}-$latestMarker",
                                        type = NotificationType.App,
                                        actor = actor,
                                        text = "在聊天室 ${room.name} 发来了新消息",
                                        createdAtLabel = latestCreatedAtLabel,
                                        createdAtEpochMillis = createdAtEpochMillis,
                                        notePreviewText = latestPreview ?: "${unreadCount.coerceAtLeast(0)} 条未读消息",
                                        isSpecialCare = true,
                                        chatRoomId = room.id,
                                        chatMessageId = latestSpecialCareMessage?.id?.takeIf { it.isNotBlank() },
                                    )
                                } else {
                                    null
                                },
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
                                val latestMessage = conversation.latestMessage
                                val createdAtEpochMillis = latestMessage?.createdAt.toEpochMillisOrNow()
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
                                    avatarMode = if (isSpecialCare) {
                                        NotificationAvatarMode.UserAvatar(conversation.user.avatarUrl, conversation.user.avatarInitial)
                                    } else {
                                        NotificationAvatarMode.Transparent
                                    },
                                    createdAtEpochMillis = createdAtEpochMillis,
                                    cacheNotification = if (isSpecialCare) {
                                        NotificationItem(
                                            id = "special-care-chat-${conversation.user.id}-${latestMessage.unreadMarker().ifBlank { unreadCount.toString() }}",
                                            type = NotificationType.App,
                                            actor = conversation.user,
                                            text = "在聊天中发来了新消息",
                                            createdAtLabel = latestMessage?.createdAtLabel?.ifBlank { "刚刚" } ?: "刚刚",
                                            createdAtEpochMillis = createdAtEpochMillis,
                                            notePreviewText = latestMessage?.text?.takeIf { it.isNotBlank() }
                                                ?: "${unreadCount.coerceAtLeast(0)} 条未读私聊",
                                            isSpecialCare = true,
                                            chatUserId = conversation.user.id,
                                            chatMessageId = latestMessage?.id?.takeIf { it.isNotBlank() },
                                        )
                                    } else {
                                        null
                                    },
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
                                val createdAtEpochMillis = note.createdAt.toEpochMillisOrNow()
                                BackgroundNotificationEvent(
                                    id = "special-note:${note.id}",
                                    title = "特别关心 · ${note.author.displayName}",
                                    text = note.text.ifBlank { "发布了新帖子" },
                                    specialCare = true,
                                    avatarMode = NotificationAvatarMode.UserAvatar(
                                        note.author.avatarUrl,
                                        note.author.avatarInitial,
                                    ),
                                    createdAtEpochMillis = createdAtEpochMillis,
                                    cacheNotification = NotificationItem(
                                        id = "special-care-note-${note.id}",
                                        type = NotificationType.Note,
                                        actor = note.author,
                                        text = "发布了新帖子",
                                        createdAtLabel = note.createdAtLabel.ifBlank { "刚刚" },
                                        createdAtEpochMillis = createdAtEpochMillis,
                                        noteId = note.id,
                                        notePreviewText = note.text.takeIf { it.isNotBlank() } ?: note.cw,
                                        isSpecialCare = true,
                                    ),
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
                    .sortedWith(
                        compareByDescending<BackgroundNotificationEvent> { if (it.specialCare) 1 else 0 }
                            .thenByDescending { it.createdAtEpochMillis },
                    )
                    .take(MAX_NOTIFICATIONS_PER_SYNC)
                if (visibleEvents.isNotEmpty()) {
                    BackgroundNotificationPublisher(context).publish(visibleEvents)
                    context.cacheSpecialCareNotifications(
                        accountId = session.id,
                        notifications = visibleEvents.mapNotNull { it.cacheNotification },
                    )
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

internal data class BackgroundNotificationEvent(
    val id: String,
    val title: String,
    val text: String,
    val specialCare: Boolean,
    val avatarMode: NotificationAvatarMode,
    val createdAtEpochMillis: Long,
    val cacheNotification: NotificationItem? = null,
)

internal fun Context.cacheSpecialCareNotifications(
    accountId: String,
    notifications: List<NotificationItem>,
) {
    val cleanAccountId = accountId.trim()
    if (cleanAccountId.isBlank() || notifications.isEmpty()) return
    val cache = AndroidNotificationCache(this)
    val snapshot = cache.read(cleanAccountId)
    val previousNotificationsById = snapshot.specialCareNotifications.associateBy { it.id }
    val nextSpecialCareNotifications = (
        notifications.map { notification ->
            notification.copy(
                isSpecialCare = true,
                isRead = previousNotificationsById[notification.id]?.isRead == true,
            )
        } +
            snapshot.specialCareNotifications
        )
        .distinctBy { it.id }
        .sortedByDescending { it.createdAtEpochMillis }
        .take(MAX_CACHED_SPECIAL_CARE_NOTIFICATIONS)
    cache.write(
        cleanAccountId,
        NotificationCacheSnapshot(
            notifications = snapshot.notifications,
            specialCareNotifications = nextSpecialCareNotifications,
        ),
    )
}

internal sealed interface NotificationAvatarMode {
    data object Transparent : NotificationAvatarMode

    data object AppIcon : NotificationAvatarMode

    data class UserAvatar(
        val avatarUrl: String?,
        val fallbackInitial: String,
    ) : NotificationAvatarMode
}

private fun cc.hhhl.client.model.NotificationItem.isAppNotification(): Boolean {
    return actor.id == "system" || type in appNotificationTypes
}

private val appNotificationTypes = setOf(
    NotificationType.PollEnded,
    NotificationType.RoleAssigned,
    NotificationType.ChatRoomInvitation,
    NotificationType.AchievementEarned,
    NotificationType.ExportCompleted,
    NotificationType.ImportCompleted,
    NotificationType.Login,
    NotificationType.CreateToken,
    NotificationType.App,
    NotificationType.Edited,
    NotificationType.ScheduledNoteFailed,
    NotificationType.ScheduledNotePosted,
    NotificationType.SharedAccessGranted,
    NotificationType.SharedAccessRevoked,
    NotificationType.SharedAccessLogin,
    NotificationType.Test,
)

private fun ChatRoom.unreadMarker(): String {
    return latestMessageMarker.ifBlank { latestMessageAtLabel }
}

private fun ChatMessage?.unreadMarker(): String {
    val message = this ?: return ""
    return message.id.ifBlank { message.createdAt.ifBlank { message.createdAtLabel } }
}

private fun String?.toEpochMillisOrNow(): Long {
    val clean = this?.trim().orEmpty()
    if (clean.isBlank()) return System.currentTimeMillis()
    return runCatching { Instant.parse(clean).toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse("${clean}Z").toEpochMilli() }.getOrNull()
        ?: System.currentTimeMillis()
}

private suspend fun ChatRepository.latestSpecialCareRoomMessage(
    roomId: String,
    specialCareUserIds: Set<String>,
): ChatMessage? {
    if (roomId.isBlank() || specialCareUserIds.isEmpty()) return null
    return when (val result = refreshMessages(roomId)) {
        is ChatMessageRepositoryResult.Success -> result.messages
            .asReversed()
            .firstOrNull { message -> message.fromUser.id in specialCareUserIds }
        is ChatMessageRepositoryResult.Created,
        is ChatMessageRepositoryResult.Deleted,
        is ChatMessageRepositoryResult.Error,
        ChatMessageRepositoryResult.ReactionUpdated,
        ChatMessageRepositoryResult.Unauthorized,
        -> null
    }
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

internal class BackgroundNotificationPublisher(
    private val context: Context,
) {
    fun publish(events: List<BackgroundNotificationEvent>) {
        if (!canPostNotifications()) return
        ensureChannels(context)
        val notificationManager = NotificationManagerCompat.from(context)
        events.forEach { event ->
            val channelId = if (event.specialCare) SPECIAL_CARE_CHANNEL_ID else MESSAGE_CHANNEL_ID
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.dc_icon)
                .setLargeIcon(notificationAvatar(event.avatarMode))
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

    private fun notificationAvatar(mode: NotificationAvatarMode): Bitmap {
        return when (mode) {
            NotificationAvatarMode.AppIcon -> appNotificationAvatar()
            NotificationAvatarMode.Transparent -> transparentNotificationAvatar()
            is NotificationAvatarMode.UserAvatar -> userNotificationAvatar(mode)
        }
    }

    private fun appNotificationAvatar(): Bitmap {
        return BitmapFactory.decodeResource(context.resources, R.drawable.dc_icon)
            ?: transparentNotificationAvatar()
    }

    private fun transparentNotificationAvatar(): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
        }
    }

    private fun userNotificationAvatar(mode: NotificationAvatarMode.UserAvatar): Bitmap {
        mode.avatarUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { url -> loadRemoteAvatar(url) }
            ?.let { return it }
        return initialNotificationAvatar(mode.fallbackInitial)
    }

    private fun loadRemoteAvatar(url: String): Bitmap? {
        return runCatching {
            URL(url).openStream().use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }

    private fun initialNotificationAvatar(initial: String): Bitmap {
        val cleanInitial = initial.trim().take(1).ifBlank { "?" }
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 25, 28)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, background)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bounds = Rect()
        textPaint.getTextBounds(cleanInitial, 0, cleanInitial.length, bounds)
        canvas.drawText(cleanInitial, size / 2f, size / 2f - bounds.exactCenterY(), textPaint)
        return bitmap
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

private const val MAX_CACHED_SPECIAL_CARE_NOTIFICATIONS = 240
