package cc.hhhl.client.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
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
import cc.hhhl.client.ai.AiStateHolder
import cc.hhhl.client.automation.AppAutomationActionExecutor
import cc.hhhl.client.automation.AutomationConditionType
import cc.hhhl.client.automation.AutomationEvent
import cc.hhhl.client.automation.AutomationRule
import cc.hhhl.client.automation.AutomationStateHolder
import cc.hhhl.client.automation.AutomationTrigger
import cc.hhhl.client.automation.toAutomationChatEvent
import cc.hhhl.client.automation.toAutomationNotificationEvent
import cc.hhhl.client.automation.toAutomationTimelineEvent
import cc.hhhl.client.api.NotificationLoadResult
import cc.hhhl.client.api.SharkeyNotificationApi
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.cache.ChatUnreadSnapshot
import cc.hhhl.client.cache.NotificationCacheSnapshot
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatUserConversationRepositoryResult
import cc.hhhl.client.repository.ChannelRepository
import cc.hhhl.client.repository.ChannelTimelineRepositoryResult
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.NoteActionRepository
import cc.hhhl.client.repository.NotificationRepository
import cc.hhhl.client.state.ChatAttentionKind
import cc.hhhl.client.repository.TimelineRepository
import cc.hhhl.client.repository.TimelineRepositoryResult
import cc.hhhl.client.repository.UserNotesRepository
import cc.hhhl.client.repository.UserNotesRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

enum class BackgroundNotificationSyncResult {
    Success,
    Retry,
}

enum class BackgroundNotificationSyncTrigger {
    Scheduled,
    RealtimeNotification,
    RealtimeChat,
    RealtimeTimeline,
    PollingSafety,
}

class BackgroundNotificationSyncer(
    private val context: Context,
) {
    suspend fun sync(
        trigger: BackgroundNotificationSyncTrigger = BackgroundNotificationSyncTrigger.Scheduled,
    ): BackgroundNotificationSyncResult {
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
        val currentUser = session.user?.let { user ->
            User(
                id = user.id,
                displayName = user.displayName,
                username = user.username,
                avatarInitial = user.displayName.trim().firstOrNull()?.toString()?.uppercase() ?: "我",
                avatarUrl = user.avatarUrl,
                host = session.host,
            )
        }

        return runCatching {
            coroutineScope {
                val chatRepository = ChatRepository(
                    tokenProvider = { token },
                    currentUserIdProvider = { session.user?.id },
                )
                val automationHolder = backgroundAutomationHolder(
                    accountId = session.id,
                    token = token,
                    chatRepository = chatRepository,
                )
                automationHolder.restore()
                val automationRules = automationHolder.state.value.rules
                val timelinePlan = automationRules.backgroundTimelineAutomationPlan()
                val timelineRepository = TimelineRepository(tokenProvider = { token })
                val channelRepository = ChannelRepository(tokenProvider = { token })
                val automationEvents = mutableListOf<AutomationEvent>()
                val timelineSeenIds = mutableListOf<String>()
                val notificationsDeferred = async {
                    SharkeyNotificationApi().loadNotifications(token, limit = 20)
                }
                val chatDeferred = async {
                    chatRepository.refresh()
                }
                val userChatDeferred = async {
                    chatRepository.refreshUserConversations()
                }
                val homeTimelineDeferred = async {
                    if (timelinePlan.timelineKinds.contains(TimelineKind.Home) || (specialCareEnabled && specialCareUserIds.isNotEmpty())) {
                        timelineRepository.refresh(TimelineKind.Home)
                    } else {
                        null
                    }
                }
                val timelineDeferreds = timelinePlan.timelineKinds
                    .filterNot { it == TimelineKind.Home }
                    .associateWith { kind -> async { timelineRepository.refresh(kind) } }
                val channelTimelineDeferreds = timelinePlan.channelIds
                    .associateWith { channelId -> async { channelRepository.refreshTimeline(channelId) } }
                val userNotesDeferreds = timelinePlan.userIds
                    .associateWith { userId ->
                        async {
                            UserNotesRepository(
                                tokenProvider = { token },
                                userIdProvider = { userId },
                            ).refresh()
                        }
                    }

                val events = mutableListOf<BackgroundNotificationEvent>()
                when (val result = notificationsDeferred.await()) {
                    is NotificationLoadResult.Success -> {
                        val unreadNotifications = result.notifications.filter { !it.isRead }
                        automationEvents += unreadNotifications
                            .filter { it.automationEventId().automationSeenId() !in seenIds }
                            .map { item ->
                                item.copy(isSpecialCare = item.isSpecialCare || item.actor.id in specialCareUserIds)
                                    .toAutomationNotificationEvent()
                            }
                        events += unreadNotifications
                            .filter { it.notificationEventId() !in seenIds }
                            .map { item ->
                                val isSpecialCare = item.actor.id in specialCareUserIds
                                val createdAtEpochMillis = item.createdAtEpochMillis.takeIf { it > 0L }
                                    ?: System.currentTimeMillis()
                                BackgroundNotificationEvent(
                                    id = item.notificationEventId(),
                                    title = if (isSpecialCare) {
                                        "特别关心 · ${item.actor.displayName}"
                                    } else {
                                        item.actor.displayName
                                    },
                                    text = item.notePreviewText?.takeIf { it.isNotBlank() } ?: item.text,
                                    specialCare = isSpecialCare,
                                    gatedBySpecialCareSetting = isSpecialCare,
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
                        val shouldScanSpecialCareRooms = specialCareEnabled && specialCareUserIds.isNotEmpty()
                        val shouldScanAttentionRooms = currentUser != null
                        val shouldScanRealtimeAttentionRooms =
                            trigger == BackgroundNotificationSyncTrigger.RealtimeChat &&
                                (shouldScanSpecialCareRooms || shouldScanAttentionRooms)
                        val unreadRooms = result.rooms.filter { room -> room.unreadCount > 0 }
                        val roomsToCheck = if (shouldScanRealtimeAttentionRooms) {
                            (result.rooms.take(SPECIAL_CARE_ROOM_SCAN_LIMIT) + unreadRooms).distinctBy { it.id }
                        } else {
                            unreadRooms
                        }
                        for (room in roomsToCheck) {
                            val roomMessages = when (val messagesResult = chatRepository.refreshMessages(room.id)) {
                                is ChatMessageRepositoryResult.Success -> messagesResult.messages
                                is ChatMessageRepositoryResult.Created,
                                is ChatMessageRepositoryResult.Deleted,
                                is ChatMessageRepositoryResult.Error,
                                ChatMessageRepositoryResult.ReactionUpdated,
                                ChatMessageRepositoryResult.Unauthorized,
                                    -> emptyList()
                            }
                            val unreadAutomationMessages = roomMessages.recentUnreadAutomationMessages(
                                unreadCount = room.unreadCount,
                                seenIds = seenIds,
                                limit = MAX_AUTOMATION_ROOM_MESSAGES_PER_SYNC,
                            )
                            unreadAutomationMessages.forEach { message ->
                                automationEvents += message.toAutomationChatEvent(
                                    roomId = room.id,
                                    roomName = room.name,
                                    currentUser = currentUser,
                                )
                            }
                            val latestAttention = if (shouldScanAttentionRooms || shouldScanSpecialCareRooms) {
                                roomMessages.latestAttentionMessage(
                                    specialCareUserIds = if (specialCareEnabled) specialCareUserIds else emptySet(),
                                    currentUser = currentUser,
                                )
                            } else {
                                null
                            }
                            latestAttention?.let { attention ->
                                val attentionEvent = attention.message.toAutomationChatEvent(
                                    roomId = room.id,
                                    roomName = room.name,
                                    attentionKind = attention.kind.name,
                                    currentUser = currentUser,
                                ).asBackgroundChatAttentionEvent(attention.kind)
                                if (attentionEvent.id.automationSeenId() !in seenIds) {
                                    automationEvents += attentionEvent
                                }
                            }
                            if (room.unreadCount <= 0 && latestAttention == null) continue
                            val attentionMessage = latestAttention?.message
                            val attentionKind = latestAttention?.kind
                            val actor = attentionMessage?.fromUser ?: room.owner
                            val isAttention = attentionKind != null
                            val isSpecialCare = attentionKind == ChatAttentionKind.SpecialCare
                            val gatedBySpecialCareSetting = attentionKind == ChatAttentionKind.SpecialCare
                            val latestMarker = attentionMessage?.unreadMarker()
                                ?: roomLatestMarkers[room.id].orEmpty()
                            val latestPreview = attentionMessage?.text?.takeIf { it.isNotBlank() }
                            val latestCreatedAtLabel = attentionMessage?.createdAtLabel
                                ?.takeIf { it.isNotBlank() }
                                ?: room.latestMessageAtLabel.ifBlank { "刚刚" }
                            val unreadCount = mergedUnread.roomCounts[room.id] ?: room.unreadCount
                            val eventId = roomEventId(
                                roomId = room.id,
                                marker = latestMarker,
                                unreadCount = room.unreadCount,
                            )
                            if (eventId in seenIds) continue
                            val createdAtEpochMillis = attentionMessage?.createdAt.toEpochMillisOrNow()
                            val titlePrefix = attentionKind?.label
                            events += BackgroundNotificationEvent(
                                id = eventId,
                                title = when {
                                    titlePrefix != null -> "$titlePrefix · ${actor.displayName}"
                                    isSpecialCare -> "特别关心 · ${actor.displayName}"
                                    else -> room.name
                                },
                                text = latestPreview ?: "${unreadCount.coerceAtLeast(0)} 条未读消息",
                                specialCare = isAttention || isSpecialCare,
                                gatedBySpecialCareSetting = gatedBySpecialCareSetting,
                                avatarMode = if (isAttention || isSpecialCare) {
                                    NotificationAvatarMode.UserAvatar(actor.avatarUrl, actor.avatarInitial)
                                } else {
                                    NotificationAvatarMode.Transparent
                                },
                                createdAtEpochMillis = createdAtEpochMillis,
                                cacheNotification = if (isAttention || isSpecialCare) {
                                    NotificationItem(
                                        id = "chat-attention-room-${room.id}-$latestMarker",
                                        type = NotificationType.App,
                                        actor = actor,
                                        text = "${attentionKind?.label ?: "特别关心"} · 在聊天室 ${room.name} 发来了新消息",
                                        createdAtLabel = latestCreatedAtLabel,
                                        createdAtEpochMillis = createdAtEpochMillis,
                                        notePreviewText = latestPreview ?: "${unreadCount.coerceAtLeast(0)} 条未读消息",
                                        isSpecialCare = true,
                                        chatRoomId = room.id,
                                        chatMessageId = attentionMessage?.id?.takeIf { it.isNotBlank() },
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
                        val shouldScanSpecialCareUserChats =
                            trigger == BackgroundNotificationSyncTrigger.RealtimeChat &&
                                specialCareEnabled &&
                                specialCareUserIds.isNotEmpty()
                        events += result.conversations
                            .filter { conversation ->
                                conversation.unreadCount > 0 ||
                                    (
                                        shouldScanSpecialCareUserChats &&
                                            conversation.user.id in specialCareUserIds &&
                                            conversation.latestMessage?.fromUser?.id in specialCareUserIds
                                        )
                            }
                            .filter { conversation ->
                                userEventId(
                                    userId = conversation.user.id,
                                    marker = userLatestMarkers[conversation.user.id].orEmpty(),
                                    unreadCount = conversation.unreadCount,
                                ) !in seenIds
                            }
                            .map { conversation ->
                                val latestMessage = conversation.latestMessage
                                val unreadUserMessages = if (conversation.unreadCount > 1) {
                                    when (val messagesResult = chatRepository.refreshUserMessages(conversation.user.id)) {
                                        is ChatMessageRepositoryResult.Success -> messagesResult.messages.recentUnreadAutomationMessages(
                                            unreadCount = conversation.unreadCount,
                                            seenIds = seenIds,
                                            limit = MAX_AUTOMATION_USER_MESSAGES_PER_SYNC,
                                        )
                                        is ChatMessageRepositoryResult.Created,
                                        is ChatMessageRepositoryResult.Deleted,
                                        is ChatMessageRepositoryResult.Error,
                                        ChatMessageRepositoryResult.ReactionUpdated,
                                        ChatMessageRepositoryResult.Unauthorized,
                                            -> emptyList()
                                    }
                                } else {
                                    emptyList()
                                }
                                unreadUserMessages.forEach { message ->
                                    automationEvents += message.toAutomationChatEvent(
                                        roomId = message.roomId,
                                        directUserId = conversation.user.id,
                                        currentUser = currentUser,
                                    )
                                }
                                val attentionKind = latestMessage.chatAttentionKind(
                                    specialCareUserIds = if (specialCareEnabled) specialCareUserIds else emptySet(),
                                    currentUser = currentUser,
                                )
                                val isSpecialCare = conversation.user.id in specialCareUserIds
                                val gatedBySpecialCareSetting = attentionKind == ChatAttentionKind.SpecialCare ||
                                    (attentionKind == null && isSpecialCare)
                                val unreadCount = mergedUnread.userCounts[conversation.user.id] ?: conversation.unreadCount
                                val createdAtEpochMillis = latestMessage?.createdAt.toEpochMillisOrNow()
                                latestMessage?.let { message ->
                                    val latestEvent = message.toAutomationChatEvent(
                                        roomId = message.roomId,
                                        directUserId = conversation.user.id,
                                        currentUser = currentUser,
                                    )
                                    if (latestEvent.id.automationSeenId() !in seenIds) {
                                        automationEvents += latestEvent
                                    }
                                    attentionKind?.let { kind ->
                                        val attentionEvent = message.toAutomationChatEvent(
                                            roomId = message.roomId,
                                            directUserId = conversation.user.id,
                                            attentionKind = kind.name,
                                            currentUser = currentUser,
                                        ).asBackgroundChatAttentionEvent(kind)
                                        if (attentionEvent.id.automationSeenId() !in seenIds) {
                                            automationEvents += attentionEvent
                                        }
                                    }
                                }
                                BackgroundNotificationEvent(
                                    id = userEventId(
                                        userId = conversation.user.id,
                                        marker = userLatestMarkers[conversation.user.id].orEmpty(),
                                        unreadCount = conversation.unreadCount,
                                    ),
                                    title = attentionKind?.let { "${it.label} · ${conversation.user.displayName}" }
                                        ?: if (isSpecialCare) {
                                            "特别关心 · ${conversation.user.displayName}"
                                        } else {
                                            conversation.user.displayName
                                        },
                                    text = conversation.latestMessage?.text?.takeIf { it.isNotBlank() }
                                        ?: "${unreadCount.coerceAtLeast(0)} 条未读私聊",
                                    specialCare = attentionKind != null || isSpecialCare,
                                    gatedBySpecialCareSetting = gatedBySpecialCareSetting,
                                    avatarMode = if (attentionKind != null || isSpecialCare) {
                                        NotificationAvatarMode.UserAvatar(conversation.user.avatarUrl, conversation.user.avatarInitial)
                                    } else {
                                        NotificationAvatarMode.Transparent
                                    },
                                    createdAtEpochMillis = createdAtEpochMillis,
                                    cacheNotification = if (attentionKind != null || isSpecialCare) {
                                        NotificationItem(
                                            id = "chat-attention-user-${conversation.user.id}-${latestMessage.unreadMarker().ifBlank { unreadCount.toString() }}",
                                            type = NotificationType.App,
                                            actor = conversation.user,
                                            text = "${attentionKind?.label ?: "特别关心"} · 在聊天中发来了新消息",
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

                when (val result = homeTimelineDeferred.await()) {
                    is TimelineRepositoryResult.Success -> {
                        result.notes.backgroundTimelineAutomationEvents(
                            sourceId = "timeline:${TimelineKind.Home.name}",
                            kind = TimelineKind.Home,
                            timelineSource = TimelineKind.Home.name,
                            seenIds = seenIds,
                            currentUser = currentUser,
                            allowLatestOnFirstScan = trigger == BackgroundNotificationSyncTrigger.RealtimeTimeline,
                        ).also { scan ->
                            automationEvents += scan.events
                            timelineSeenIds += scan.seenIds
                        }
                        events += result.notes
                            .filter { it.author.id in specialCareUserIds }
                            .filter { "special-note:${it.id}" !in seenIds }
                            .map { note ->
                                automationEvents += NotificationItem(
                                    id = "special-care-note-${note.id}",
                                    type = NotificationType.Note,
                                    actor = note.author,
                                    text = "发布了新帖子",
                                    createdAtLabel = note.createdAtLabel.ifBlank { "刚刚" },
                                    createdAtEpochMillis = note.createdAt.toEpochMillisOrNow(),
                                    noteId = note.id,
                                    notePreviewText = note.text.takeIf { it.isNotBlank() } ?: note.cw,
                                    isSpecialCare = true,
                                ).toAutomationNotificationEvent()
                                val createdAtEpochMillis = note.createdAt.toEpochMillisOrNow()
                                BackgroundNotificationEvent(
                                    id = "special-note:${note.id}",
                                    title = "特别关心 · ${note.author.displayName}",
                                    text = note.text.ifBlank { "发布了新帖子" },
                                    specialCare = true,
                                    gatedBySpecialCareSetting = true,
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

                for ((kind, deferred) in timelineDeferreds) {
                    when (val result = deferred.await()) {
                        is TimelineRepositoryResult.Success -> {
                            result.notes.backgroundTimelineAutomationEvents(
                                sourceId = "timeline:${kind.name}",
                                kind = kind,
                                timelineSource = kind.name,
                                seenIds = seenIds,
                                currentUser = currentUser,
                                allowLatestOnFirstScan = trigger == BackgroundNotificationSyncTrigger.RealtimeTimeline,
                            ).also { scan ->
                                automationEvents += scan.events
                                timelineSeenIds += scan.seenIds
                            }
                        }
                        TimelineRepositoryResult.Unauthorized -> return@coroutineScope BackgroundNotificationSyncResult.Success
                        is TimelineRepositoryResult.Error -> Unit
                    }
                }

                for ((channelId, deferred) in channelTimelineDeferreds) {
                    when (val result = deferred.await()) {
                        is ChannelTimelineRepositoryResult.Success -> {
                            result.notes.backgroundTimelineAutomationEvents(
                                sourceId = "channel:$channelId",
                                kind = TimelineKind.Home,
                                timelineSource = "Channel",
                                seenIds = seenIds,
                                currentUser = currentUser,
                                allowLatestOnFirstScan = trigger == BackgroundNotificationSyncTrigger.RealtimeTimeline,
                                fallbackChannelId = channelId,
                            ).also { scan ->
                                automationEvents += scan.events
                                timelineSeenIds += scan.seenIds
                            }
                        }
                        ChannelTimelineRepositoryResult.Unauthorized -> return@coroutineScope BackgroundNotificationSyncResult.Success
                        is ChannelTimelineRepositoryResult.Error -> Unit
                    }
                }

                for ((userId, deferred) in userNotesDeferreds) {
                    when (val result = deferred.await()) {
                        is UserNotesRepositoryResult.Success -> {
                            result.notes.backgroundTimelineAutomationEvents(
                                sourceId = "user:$userId",
                                kind = TimelineKind.Home,
                                timelineSource = "User",
                                seenIds = seenIds,
                                currentUser = currentUser,
                                allowLatestOnFirstScan = trigger == BackgroundNotificationSyncTrigger.RealtimeTimeline,
                            ).also { scan ->
                                automationEvents += scan.events
                                timelineSeenIds += scan.seenIds
                            }
                        }
                        UserNotesRepositoryResult.Unauthorized -> return@coroutineScope BackgroundNotificationSyncResult.Success
                        is UserNotesRepositoryResult.Error -> Unit
                    }
                }

                val visibleEvents = events
                    .filter { !it.gatedBySpecialCareSetting || specialCareEnabled }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<BackgroundNotificationEvent> { if (it.specialCare) 1 else 0 }
                            .thenByDescending { it.createdAtEpochMillis },
                    )
                    .take(maxNotificationsPerSync(trigger))
                val automationEventIds = automationEvents.map { it.id }
                if (visibleEvents.isNotEmpty()) {
                    BackgroundNotificationPublisher(context).publish(visibleEvents)
                    context.cacheBackgroundNotificationEvents(
                        accountId = session.id,
                        events = visibleEvents,
                    )
                }
                val handledEventIds = (visibleEvents.map { it.id } + automationEventIds.map { it.automationSeenId() } + timelineSeenIds)
                    .distinct()
                    .take(MAX_SEEN_IDS_PER_SYNC)
                if (handledEventIds.isNotEmpty()) {
                    settings.saveSeenIds(handledEventIds.mergeBackgroundSeenIds(seenIds, MAX_STORED_SEEN_IDS))
                }
                automationEvents.distinctBy { it.id }.forEach { event -> automationHolder.emitNow(event) }
                AiBackgroundScheduler.syncNow(context)
                BackgroundNotificationSyncResult.Success
            }
        }.getOrElse {
            BackgroundNotificationSyncResult.Retry
        }
    }

    private fun List<AutomationRule>.backgroundTimelineAutomationPlan(): BackgroundTimelineAutomationPlan {
        val rules = filter { rule -> rule.enabled && rule.trigger == AutomationTrigger.TimelineNote }
        if (rules.isEmpty()) return BackgroundTimelineAutomationPlan()

        val explicitTimelineKinds = rules.flatMap { rule ->
            rule.conditions
                .filter { condition -> condition.enabled && condition.type == AutomationConditionType.TimelineKind }
                .flatMap { condition -> condition.value.splitAutomationValues() }
                .mapNotNull { value -> value.toTimelineKindOrNull() }
        }
        val hasChannelRule = rules.any { rule ->
            rule.conditions.any { condition ->
                condition.enabled && condition.type == AutomationConditionType.ChannelId && condition.value.isNotBlank()
            }
        }
        val channelIds = rules.flatMap { rule ->
            rule.conditions
                .filter { condition -> condition.enabled && condition.type == AutomationConditionType.ChannelId }
                .flatMap { condition -> condition.value.splitAutomationValues() }
        }.distinct().take(MAX_AUTOMATION_CHANNEL_TIMELINES_PER_SYNC)
        val userIds = rules.flatMap { rule ->
            rule.conditions
                .filter { condition ->
                    condition.enabled &&
                        (condition.type == AutomationConditionType.SenderUserId || condition.type == AutomationConditionType.SenderUserIds)
                }
                .flatMap { condition -> condition.value.splitAutomationValues() }
        }.distinct().take(MAX_AUTOMATION_USER_TIMELINES_PER_SYNC)
        val hasTimelineRule = rules.any { rule ->
            rule.conditions.none { condition ->
                condition.enabled &&
                    condition.value.isNotBlank() &&
                    (condition.type == AutomationConditionType.ChannelId || condition.type == AutomationConditionType.SenderUserId || condition.type == AutomationConditionType.SenderUserIds)
            }
        }
        val timelineKinds = buildList {
            addAll(explicitTimelineKinds)
            if (hasTimelineRule || explicitTimelineKinds.isEmpty() && !hasChannelRule && userIds.isEmpty()) add(TimelineKind.Home)
        }.distinct().take(MAX_AUTOMATION_TIMELINE_SOURCES_PER_SYNC)
        return BackgroundTimelineAutomationPlan(
            timelineKinds = timelineKinds,
            channelIds = channelIds,
            userIds = userIds,
        )
    }

    private companion object {
        const val MAX_NOTIFICATIONS_PER_SYNC = 8
        const val MAX_REALTIME_NOTIFICATIONS_PER_SYNC = 16
        const val MAX_SEEN_IDS_PER_SYNC = 40
        const val MAX_STORED_SEEN_IDS = 200
        const val SPECIAL_CARE_ROOM_SCAN_LIMIT = 8
        const val MAX_AUTOMATION_ROOM_MESSAGES_PER_SYNC = 20
        const val MAX_AUTOMATION_USER_MESSAGES_PER_SYNC = 20
        const val MAX_AUTOMATION_TIMELINE_SOURCES_PER_SYNC = 4
        const val MAX_AUTOMATION_CHANNEL_TIMELINES_PER_SYNC = 4
        const val MAX_AUTOMATION_USER_TIMELINES_PER_SYNC = 4
    }

    private fun maxNotificationsPerSync(trigger: BackgroundNotificationSyncTrigger): Int {
        return when (trigger) {
            BackgroundNotificationSyncTrigger.RealtimeNotification,
            BackgroundNotificationSyncTrigger.PollingSafety -> MAX_REALTIME_NOTIFICATIONS_PER_SYNC
            BackgroundNotificationSyncTrigger.Scheduled,
            BackgroundNotificationSyncTrigger.RealtimeChat,
            BackgroundNotificationSyncTrigger.RealtimeTimeline -> MAX_NOTIFICATIONS_PER_SYNC
        }
    }

    private fun backgroundAutomationHolder(
        accountId: String,
        token: String,
        chatRepository: ChatRepository,
    ): AutomationStateHolder {
        val aiStateHolder = AiStateHolder(
            store = AndroidAiStore(context),
            accountId = accountId,
            onQueueChanged = { AiBackgroundScheduler.syncNow(context) },
            scope = CoroutineScope(Dispatchers.Default),
        ).also { it.restore() }
        return AutomationStateHolder(
            store = AndroidAutomationStore(context),
            accountId = accountId,
            executor = AppAutomationActionExecutor(
                chatRepository = chatRepository,
                notificationRepository = NotificationRepository(tokenProvider = { token }),
                composeRepository = ComposeRepository(tokenProvider = { token }),
                noteActionRepository = NoteActionRepository(tokenProvider = { token }),
                clipboardWriter = { text ->
                    context.writeAutomationClipboardText(text)
                    true
                },
                systemNotificationPublisher = { title, body ->
                    BackgroundNotificationPublisher(context).publish(
                        listOf(
                            BackgroundNotificationEvent(
                                id = "automation:${title.hashCode()}:${body.hashCode()}:${System.currentTimeMillis()}",
                                title = title.ifBlank { "HHHL 自动化" },
                                text = body.ifBlank { "自动化规则已执行" },
                                specialCare = false,
                                avatarMode = NotificationAvatarMode.AppIcon,
                                createdAtEpochMillis = System.currentTimeMillis(),
                            ),
                        ),
                    )
                    true
                },
                aiBridge = aiStateHolder,
            ),
            aiBridge = aiStateHolder,
            aiToolPermissionProvider = { aiStateHolder.state.value.settings.toolsAllowed },
            scope = CoroutineScope(Dispatchers.Default),
        )
    }
}

private const val MAX_BACKGROUND_AUTOMATION_TIMELINE_NOTES_PER_SOURCE = 8

private fun Context.writeAutomationClipboardText(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("HHHL 自动化", text))
}

internal data class BackgroundNotificationEvent(
    val id: String,
    val title: String,
    val text: String,
    val specialCare: Boolean,
    val gatedBySpecialCareSetting: Boolean = specialCare,
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
            chatAttentionNotifications = snapshot.chatAttentionNotifications,
            specialCareNotifications = nextSpecialCareNotifications,
        ),
    )
}

internal fun Context.cacheRemoteNotifications(
    accountId: String,
    notifications: List<NotificationItem>,
) {
    val cleanAccountId = accountId.trim()
    if (cleanAccountId.isBlank() || notifications.isEmpty()) return
    val cache = AndroidNotificationCache(this)
    val snapshot = cache.read(cleanAccountId)
    val previousNotificationsById = snapshot.notifications.associateBy { it.id }
    val nextNotifications = (
        notifications.map { notification ->
            notification.copy(
                isRead = previousNotificationsById[notification.id]?.isRead == true,
            )
        } + snapshot.notifications
        )
        .distinctBy { it.id }
        .sortedByDescending { it.createdAtEpochMillis }
        .take(MAX_CACHED_REMOTE_NOTIFICATIONS)
    cache.write(
        cleanAccountId,
        NotificationCacheSnapshot(
            notifications = nextNotifications,
            chatAttentionNotifications = snapshot.chatAttentionNotifications,
            specialCareNotifications = snapshot.specialCareNotifications,
        ),
    )
}

internal fun Context.cacheBackgroundNotificationEvents(
    accountId: String,
    events: List<BackgroundNotificationEvent>,
) {
    val cleanAccountId = accountId.trim()
    if (cleanAccountId.isBlank() || events.isEmpty()) return
    val cache = AndroidNotificationCache(this)
    val snapshot = cache.read(cleanAccountId)
    val chatAttentionNotifications = events
        .filter { !it.gatedBySpecialCareSetting }
        .mapNotNull { it.cacheNotification }
    val specialCareNotifications = events
        .filter { it.gatedBySpecialCareSetting }
        .mapNotNull { it.cacheNotification }
    if (chatAttentionNotifications.isEmpty() && specialCareNotifications.isEmpty()) return

    cache.write(
        cleanAccountId,
        NotificationCacheSnapshot(
            notifications = snapshot.notifications,
            chatAttentionNotifications = snapshot.chatAttentionNotifications.mergeCachedBackgroundNotifications(
                chatAttentionNotifications,
            ),
            specialCareNotifications = snapshot.specialCareNotifications.mergeCachedBackgroundNotifications(
                specialCareNotifications,
            ),
        ),
    )
}

private fun List<NotificationItem>.mergeCachedBackgroundNotifications(
    incoming: List<NotificationItem>,
): List<NotificationItem> {
    if (incoming.isEmpty()) return this
    val previousNotificationsById = associateBy { it.id }
    return (
        incoming.map { notification ->
            notification.copy(
                isRead = previousNotificationsById[notification.id]?.isRead == true,
            )
        } +
            this
        )
        .distinctBy { it.id }
        .sortedByDescending { it.createdAtEpochMillis }
        .take(MAX_CACHED_SPECIAL_CARE_NOTIFICATIONS)
}

internal sealed interface NotificationAvatarMode {
    data object Transparent : NotificationAvatarMode

    data object AppIcon : NotificationAvatarMode

    data class UserAvatar(
        val avatarUrl: String?,
        val fallbackInitial: String,
    ) : NotificationAvatarMode
}

internal fun cc.hhhl.client.model.NotificationItem.isAppNotification(): Boolean {
    return actor.id == "system" || type in appNotificationTypes
}

internal fun NotificationItem.notificationEventId(): String = "notification-alert:$id"

private fun NotificationItem.automationEventId(): String = "notification:$id"

private fun String.automationSeenId(): String = "automation:$this"

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

private data class ChatAttentionMessage(
    val message: ChatMessage,
    val kind: ChatAttentionKind,
)

private fun List<ChatMessage>.recentUnreadAutomationMessages(
    unreadCount: Int,
    seenIds: Set<String>,
    limit: Int,
): List<ChatMessage> {
    if (unreadCount <= 0 || isEmpty() || limit <= 0) return emptyList()
    return takeLast(unreadCount.coerceAtMost(limit))
        .filter { message -> "chat-message:${message.unreadMarker()}".automationSeenId() !in seenIds }
}

private fun List<Note>.backgroundTimelineAutomationEvents(
    sourceId: String,
    kind: TimelineKind,
    timelineSource: String,
    seenIds: Set<String>,
    currentUser: User?,
    allowLatestOnFirstScan: Boolean,
    fallbackChannelId: String = "",
): BackgroundTimelineAutomationScan {
    val latestNoteId = firstOrNull()?.id.orEmpty()
    if (latestNoteId.isBlank()) return BackgroundTimelineAutomationScan()
    val latestSeenId = sourceId.timelineBaselineSeenId(latestNoteId)
    val previousBaselineId = seenIds
        .asSequence()
        .mapNotNull { it.timelineBaselineNoteId(sourceId) }
        .firstOrNull()
    val notesToEmit = when {
        previousBaselineId == null && allowLatestOnFirstScan -> take(1)
        previousBaselineId == null -> emptyList()
        latestNoteId == previousBaselineId -> emptyList()
        else -> takeWhile { note -> note.id != previousBaselineId }
            .take(MAX_BACKGROUND_AUTOMATION_TIMELINE_NOTES_PER_SOURCE)
            .asReversed()
    }
    val events = notesToEmit
        .filter { note -> note.id.isNotBlank() }
        .filter { note -> note.timelineAutomationSeenId(sourceId) !in seenIds }
        .map { note ->
            note.copy(channelId = note.channelId.ifBlank { fallbackChannelId })
                .toAutomationTimelineEvent(
                    kind = kind,
                    timelineSource = timelineSource,
                    currentUser = currentUser,
                )
                .copy(id = "timeline-note:$sourceId:${note.id}")
        }
    return BackgroundTimelineAutomationScan(
        events = events,
        seenIds = buildList {
            add(latestSeenId)
            events.forEach { event -> add(event.id.automationSeenId()) }
        },
    )
}

private data class BackgroundTimelineAutomationScan(
    val events: List<AutomationEvent> = emptyList(),
    val seenIds: List<String> = emptyList(),
)

private data class BackgroundTimelineAutomationPlan(
    val timelineKinds: List<TimelineKind> = emptyList(),
    val channelIds: List<String> = emptyList(),
    val userIds: List<String> = emptyList(),
)

private fun String.timelineBaselineSeenId(noteId: String): String = "automation-baseline:$this:$noteId"

private fun String.timelineBaselineNoteId(sourceId: String): String? {
    val prefix = "automation-baseline:$sourceId:"
    return takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isNotBlank() }
}

private fun String.splitAutomationValues(): List<String> {
    return split(',', '，', '\n', ';', '；', '|', '/', '、')
        .map { it.trim().trim('@') }
        .filter { it.isNotEmpty() }
}

private fun String.timelineBaselineSourceId(): String? {
    val prefix = "automation-baseline:"
    if (!startsWith(prefix)) return null
    return removePrefix(prefix).substringBeforeLast(':').takeIf { it.isNotBlank() }
}

private fun List<String>.mergeBackgroundSeenIds(previous: Set<String>, limit: Int): Set<String> {
    val updatedBaselineSources = mapNotNull { it.timelineBaselineSourceId() }.toSet()
    val retainedPrevious = previous.filterNot { seenId ->
        seenId.timelineBaselineSourceId()?.let { it in updatedBaselineSources } == true
    }
    return (this + retainedPrevious).take(limit).toSet()
}

private fun Note.timelineAutomationSeenId(sourceId: String): String = "timeline-note:$sourceId:$id".automationSeenId()

private fun String.toTimelineKindOrNull(): TimelineKind? {
    return when (trim().lowercase()) {
        "home", "首页", "主页" -> TimelineKind.Home
        "social", "hybrid", "社交" -> TimelineKind.Social
        "local", "本地" -> TimelineKind.Local
        "global", "全局" -> TimelineKind.Global
        "bubble", "气泡" -> TimelineKind.Bubble
        "featured", "精选" -> TimelineKind.Featured
        "mentions", "mention", "提及", "@" -> TimelineKind.Mentions
        "channel", "频道" -> null
        else -> TimelineKind.entries.firstOrNull { it.name.equals(trim(), ignoreCase = true) }
    }
}

private fun List<ChatMessage>.latestAttentionMessage(
    specialCareUserIds: Set<String>,
    currentUser: User?,
): ChatAttentionMessage? {
    return asReversed().firstNotNullOfOrNull { message ->
        message.chatAttentionKind(
            specialCareUserIds = specialCareUserIds,
            currentUser = currentUser,
        )?.let { kind -> ChatAttentionMessage(message, kind) }
    }
}

private fun AutomationEvent.asBackgroundChatAttentionEvent(kind: ChatAttentionKind): AutomationEvent {
    return copy(id = "chat-attention:${kind.name}:${chatMessageId.ifBlank { id }}")
}

private suspend fun ChatRepository.latestAttentionRoomMessage(
    roomId: String,
    specialCareUserIds: Set<String>,
    currentUser: User?,
): ChatAttentionMessage? {
    if (roomId.isBlank()) return null
    return when (val result = refreshMessages(roomId)) {
        is ChatMessageRepositoryResult.Success -> result.messages
            .asReversed()
            .firstNotNullOfOrNull { message ->
                message.chatAttentionKind(
                    specialCareUserIds = specialCareUserIds,
                    currentUser = currentUser,
                )?.let { kind -> ChatAttentionMessage(message, kind) }
            }
        is ChatMessageRepositoryResult.Created,
        is ChatMessageRepositoryResult.Deleted,
        is ChatMessageRepositoryResult.Error,
        ChatMessageRepositoryResult.ReactionUpdated,
        ChatMessageRepositoryResult.Unauthorized,
        -> null
    }
}

private fun ChatMessage?.chatAttentionKind(
    specialCareUserIds: Set<String>,
    currentUser: User?,
): ChatAttentionKind? {
    val message = this ?: return null
    val currentUserId = currentUser?.id?.trim().orEmpty()
    val isFromSelf = currentUserId.isNotEmpty() && message.fromUser.id == currentUserId
    return when {
        !isFromSelf && currentUserId.isNotEmpty() && message.text.mentionsChatUser(currentUser) -> ChatAttentionKind.Mention
        !isFromSelf && currentUserId.isNotEmpty() && message.reply?.fromUser?.id == currentUserId -> ChatAttentionKind.Reply
        !isFromSelf && currentUserId.isNotEmpty() && message.quote?.fromUser?.id == currentUserId -> ChatAttentionKind.Quote
        message.fromUser.id in specialCareUserIds -> ChatAttentionKind.SpecialCare
        else -> null
    }
}

private fun String.mentionsChatUser(user: User?): Boolean {
    val username = user?.username?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val host = user.host?.trim()?.takeIf { it.isNotEmpty() }
    var index = indexOf('@')
    while (index >= 0 && index < length - 1) {
        val parsed = parseMentionAt(index)
        if (parsed != null && parsed.username.equals(username, ignoreCase = true)) {
            if (host == null || parsed.host == null || parsed.host.equals(host, ignoreCase = true)) return true
        }
        index = indexOf('@', startIndex = index + 1)
    }
    return false
}

private data class ParsedMention(
    val username: String,
    val host: String?,
)

private fun String.parseMentionAt(atIndex: Int): ParsedMention? {
    if (atIndex !in indices || this[atIndex] != '@') return null
    val usernameStart = atIndex + 1
    if (usernameStart >= length || !this[usernameStart].isMentionPart()) return null
    var usernameEnd = usernameStart
    while (usernameEnd < length && this[usernameEnd].isMentionPart()) usernameEnd++
    val username = substring(usernameStart, usernameEnd).takeIf { it.isNotBlank() } ?: return null
    var host: String? = null
    if (usernameEnd < length && this[usernameEnd] == '@') {
        val hostStart = usernameEnd + 1
        var hostEnd = hostStart
        while (hostEnd < length && this[hostEnd].isMentionHostPart()) hostEnd++
        host = substring(hostStart, hostEnd).trim('.').takeIf { it.isNotBlank() }
    }
    return ParsedMention(username, host)
}

private fun Char.isMentionPart(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-'
}

private fun Char.isMentionHostPart(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-' || this == '.'
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
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true) &&
            !cleanUrl.startsWith("http://", ignoreCase = true)
        ) {
            return null
        }
        return runCatching {
            val connection = URL(cleanUrl).openConnection() as? HttpURLConnection
                ?: return@runCatching null
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = REMOTE_AVATAR_CONNECT_TIMEOUT_MS
                connection.readTimeout = REMOTE_AVATAR_READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "HHHL-Android/${BuildConfig.VERSION_NAME}")
                if (connection.responseCode !in 200..299) return@runCatching null
                if (connection.contentLengthLong > REMOTE_AVATAR_MAX_BYTES) return@runCatching null
                val contentType = connection.contentType.orEmpty().substringBefore(';').lowercase()
                if (contentType.isNotBlank() && !contentType.startsWith("image/")) return@runCatching null

                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(REMOTE_AVATAR_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > REMOTE_AVATAR_MAX_BYTES) return@runCatching null
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                if (bytes.isEmpty()) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = avatarSampleSize(bounds.outWidth, bounds.outHeight)
                    },
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun avatarSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > REMOTE_AVATAR_MAX_DIMENSION_PX ||
            height / sampleSize > REMOTE_AVATAR_MAX_DIMENSION_PX
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun initialNotificationAvatar(initial: String): Bitmap {
        val cleanInitial = initial.trim().take(1).ifBlank { "?" }
        val avatarColors = notificationAvatarColors()
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = avatarColors.background
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, background)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = avatarColors.content
            textAlign = Paint.Align.CENTER
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bounds = Rect()
        textPaint.getTextBounds(cleanInitial, 0, cleanInitial.length, bounds)
        canvas.drawText(cleanInitial, size / 2f, size / 2f - bounds.exactCenterY(), textPaint)
        return bitmap
    }

    private fun notificationAvatarColors(): NotificationAvatarColors {
        val customTheme = AndroidThemeStore(context).loadCustomTheme()
        return NotificationAvatarColors(
            background = customTheme.avatarBackgroundColorHex.toAndroidColorOrNull()
                ?: customTheme.mediaBackgroundColorHex.toAndroidColorOrNull()
                ?: customTheme.surfaceColorHex.toAndroidColorOrNull()
                ?: DEFAULT_NOTIFICATION_AVATAR_BACKGROUND,
            content = customTheme.primaryTextColorHex.toAndroidColorOrNull()
                ?: customTheme.outgoingBubbleTextColorHex.toAndroidColorOrNull()
                ?: DEFAULT_NOTIFICATION_AVATAR_CONTENT,
        )
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

    private companion object {
        const val REMOTE_AVATAR_CONNECT_TIMEOUT_MS = 2_000
        const val REMOTE_AVATAR_READ_TIMEOUT_MS = 2_500
        const val REMOTE_AVATAR_MAX_BYTES = 512L * 1024L
        const val REMOTE_AVATAR_BUFFER_BYTES = 8 * 1024
        const val REMOTE_AVATAR_MAX_DIMENSION_PX = 256
    }
}

private data class NotificationAvatarColors(
    val background: Int,
    val content: Int,
)

private const val DEFAULT_NOTIFICATION_AVATAR_BACKGROUND = 0xFF19191C.toInt()
private const val DEFAULT_NOTIFICATION_AVATAR_CONTENT = 0xFFFFFFFF.toInt()

private fun String.toAndroidColorOrNull(): Int? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) {
        (0xFF000000 or value).toInt()
    } else {
        value.toInt()
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
private const val MAX_CACHED_REMOTE_NOTIFICATIONS = 240
