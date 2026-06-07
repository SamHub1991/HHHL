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
import cc.hhhl.client.ai.AiRepository
import cc.hhhl.client.ai.AiRepositoryResult
import cc.hhhl.client.ai.AiTaskInput
import cc.hhhl.client.ai.AiTaskKind
import cc.hhhl.client.ai.toAiChatMessageContexts
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
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.cache.ChatMessageCacheConversationType
import cc.hhhl.client.cache.ChatUnreadSnapshot
import cc.hhhl.client.cache.NotificationCacheSnapshot
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.User
import cc.hhhl.client.presentation.chatMessageBodyText
import cc.hhhl.client.notification.ChatNoiseReductionCandidate
import cc.hhhl.client.notification.ChatNoiseReductionDecision
import cc.hhhl.client.notification.ChatNoiseReductionSettings
import cc.hhhl.client.notification.aiChatImportanceDecision
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatRoomMutationRepositoryResult
import cc.hhhl.client.repository.ChatUserConversationRepositoryResult
import cc.hhhl.client.repository.ChannelRepository
import cc.hhhl.client.repository.ChannelTimelineRepositoryResult
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.NoteActionRepository
import cc.hhhl.client.repository.NotificationRepository
import cc.hhhl.client.repository.requiresRealtimeAttentionResolution
import cc.hhhl.client.state.ChatAttentionKind
import cc.hhhl.client.repository.TimelineRepository
import cc.hhhl.client.repository.TimelineRepositoryResult
import cc.hhhl.client.repository.UserNotesRepository
import cc.hhhl.client.repository.UserNotesRepositoryResult
import cc.hhhl.client.repository.resolveRealtimeMessage
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
    suspend fun loadRealtimeChatStreamTargets(session: AccountSession): RealtimeChatStreamTargets {
        val token = session.token.trim()
        if (token.isEmpty()) return RealtimeChatStreamTargets()
        val specialCareUserIds = AndroidSpecialCareStore(context).loadSpecialCareUserIds(session.id)
        val chatRepository = ChatRepository(
            tokenProvider = { token },
            currentUserIdProvider = { session.user?.id },
            cacheAccountIdProvider = { session.id },
            messageCache = AndroidChatMessageCache(context),
        )
        val chatUnreadStore = AndroidChatUnreadStore(context)
        val localChatSnapshot = chatUnreadStore.load(session.id)
        val cachedChatTargets = AndroidChatMessageCache(context)
            .readAccount(session.id)
            .keys
        val rules = AndroidAutomationStore(context).read(session.id).rules
        val rooms = chatRepository.loadAllRoomsForRealtimeTargets()
        val conversations = when (val result = chatRepository.refreshUserConversations()) {
            is ChatUserConversationRepositoryResult.Success -> result.conversations
            ChatUserConversationRepositoryResult.Unauthorized,
            is ChatUserConversationRepositoryResult.Error,
                -> emptyList()
        }
        return realtimeChatStreamTargets(
            rooms = rooms,
            conversations = conversations,
            rememberedRoomIds = localChatSnapshot.pinnedRoomIds +
                localChatSnapshot.roomCounts.keys +
                chatUnreadStore.loadRoomMarkers(session.id).keys +
                cachedChatTargets
                    .filter { key -> key.type == ChatMessageCacheConversationType.Room }
                    .map { key -> key.conversationId },
            rememberedUserIds = localChatSnapshot.pinnedUserIds +
                localChatSnapshot.userCounts.keys +
                chatUnreadStore.loadUserMarkers(session.id).keys +
                cachedChatTargets
                    .filter { key -> key.type == ChatMessageCacheConversationType.User }
                    .map { key -> key.conversationId },
            specialCareUserIds = specialCareUserIds,
            automationRules = rules,
        )
    }

    suspend fun cacheRealtimeChatMessage(
        session: AccountSession,
        message: ChatMessage,
        directUserId: String?,
        roomId: String? = null,
    ) {
        val token = session.token.trim()
        if (token.isEmpty()) return
        val repository = ChatRepository(
            tokenProvider = { token },
            currentUserIdProvider = { session.user?.id },
            cacheAccountIdProvider = { session.id },
            messageCache = AndroidChatMessageCache(context),
        )
        val cleanDirectUserId = directUserId?.trim()?.takeIf { it.isNotEmpty() }
        val cleanRoomId = roomId?.trim()?.takeIf { it.isNotEmpty() } ?: message.roomId.trim().takeIf { it.isNotEmpty() }
        if (cleanDirectUserId != null) {
            repository.cacheUserMessage(cleanDirectUserId, message)
        } else if (cleanRoomId != null) {
            repository.cacheRoomMessage(cleanRoomId, message.copy(roomId = cleanRoomId))
        }
    }

    suspend fun handleRealtimeChatMessage(
        session: AccountSession,
        message: ChatMessage,
        directUserId: String?,
        roomId: String? = null,
        roomName: String = "",
    ): Boolean {
        val token = session.token.trim()
        if (token.isEmpty()) return false
        val settings = AndroidBackgroundNotificationStore(context)
        if (!settings.isBackgroundSyncEnabled()) return false
        val cleanDirectUserId = directUserId?.trim()?.takeIf { it.isNotEmpty() }
        val cleanRoomId = roomId?.trim()?.takeIf { it.isNotEmpty() } ?: message.roomId.trim().takeIf { it.isNotEmpty() }
        if (cleanDirectUserId == null && cleanRoomId == null) return false
        val seenIds = settings.loadSeenIds()
        val specialCareUserIds = AndroidSpecialCareStore(context).loadSpecialCareUserIds(session.id)
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
        val chatRepository = ChatRepository(
            tokenProvider = { token },
            currentUserIdProvider = { session.user?.id },
            cacheAccountIdProvider = { session.id },
            messageCache = AndroidChatMessageCache(context),
        )
        val sourceMessage = if (cleanDirectUserId == null && cleanRoomId != null && message.roomId != cleanRoomId) {
            message.copy(roomId = cleanRoomId)
        } else {
            message
        }
        val resolvedMessage = chatRepository.resolveRealtimeMessage(sourceMessage, cleanDirectUserId)
        val resolvedRoomId = if (cleanDirectUserId == null) {
            resolvedMessage.roomId.ifBlank { cleanRoomId.orEmpty() }
        } else {
            resolvedMessage.roomId
        }
        val normalizedMessage = if (resolvedRoomId.isNotBlank() && resolvedMessage.roomId != resolvedRoomId) {
            resolvedMessage.copy(roomId = resolvedRoomId)
        } else {
            resolvedMessage
        }
        val resolvedRoomName = if (cleanDirectUserId == null) {
            roomName.ifBlank {
                chatRepository.resolveRealtimeRoomName(resolvedRoomId)
            }
        } else {
            ""
        }
        val attentionKind = normalizedMessage.chatAttentionKind(
            specialCareUserIds = if (settings.isSpecialCareEnabled()) specialCareUserIds else emptySet(),
            currentUser = currentUser,
        )
        val chatEvent = normalizedMessage.toAutomationChatEvent(
            roomId = resolvedRoomId,
            roomName = resolvedRoomName,
            directUserId = cleanDirectUserId.orEmpty(),
            currentUser = currentUser,
        )
        val attentionEvent = attentionKind?.let { kind ->
            normalizedMessage.toAutomationChatEvent(
                roomId = resolvedRoomId,
                roomName = resolvedRoomName,
                directUserId = cleanDirectUserId.orEmpty(),
                attentionKind = kind.name,
                currentUser = currentUser,
            ).asBackgroundChatAttentionEvent(kind)
        }
        val chatSeenId = chatEvent.id.automationSeenId()
        val attentionSeenId = attentionEvent?.id?.automationSeenId()
        val isNewChatEvent = chatSeenId !in seenIds

        cacheRealtimeChatMessage(session, normalizedMessage, cleanDirectUserId, resolvedRoomId)
        if (isNewChatEvent) {
            recordRealtimeUnread(
                session = session,
                message = normalizedMessage,
                directUserId = cleanDirectUserId,
                currentUser = currentUser,
            )
        }

        val automationHolder = backgroundAutomationHolder(
            accountId = session.id,
            token = token,
            chatRepository = chatRepository,
        )
        automationHolder.restore()
        val automationEvents = buildList {
            add(chatEvent)
            if (attentionEvent != null) add(attentionEvent)
        }
        automationEvents.distinctBy { it.backgroundAutomationCandidateKey() }.forEach { event -> automationHolder.emitNow(event) }

        val notification = attentionKind?.let { kind ->
            normalizedMessage.toRealtimeChatAttentionEvent(
                kind = kind,
                directUserId = cleanDirectUserId,
                roomName = resolvedRoomName,
            )
        }
        val fallbackNotificationSeenId = notification?.let {
            normalizedMessage.realtimeFallbackNotificationEventId(cleanDirectUserId)
        }
        val claimedNotificationIds = notification
            ?.let { event -> settings.claimSeenIds(listOf(event.id), MAX_STORED_SEEN_IDS) }
            .orEmpty()
        notification?.let { event ->
            if (event.id in claimedNotificationIds && (!event.gatedBySpecialCareSetting || settings.isSpecialCareEnabled())) {
                BackgroundNotificationPublisher(context).publish(listOf(event))
                context.cacheBackgroundNotificationEvents(session.id, listOf(event))
            }
        }
        val handledIds = buildList {
            if (chatSeenId !in seenIds) add(chatSeenId)
            attentionSeenId?.takeIf { it !in seenIds }?.let { add(it) }
            notification?.id?.takeIf { it !in seenIds }?.let { add(it) }
            fallbackNotificationSeenId?.takeIf { it !in seenIds }?.let { add(it) }
        }
        if (handledIds.isNotEmpty()) {
            settings.mergeSeenIds(handledIds, MAX_STORED_SEEN_IDS)
        }
        if (automationEvents.isNotEmpty()) AiBackgroundScheduler.syncNow(context)
        return true
    }

    private fun recordRealtimeUnread(
        session: AccountSession,
        message: ChatMessage,
        directUserId: String?,
        currentUser: User?,
    ) {
        val currentUserId = currentUser?.id?.trim().orEmpty()
        if (currentUserId.isNotBlank() && message.fromUser.id == currentUserId) return
        val marker = message.unreadMarker().takeIf { it.isNotBlank() } ?: return
        val store = AndroidChatUnreadStore(context)
        val cleanDirectUserId = directUserId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanDirectUserId != null) {
            store.merge(
                session.id,
                ChatUnreadSnapshot(userCounts = mapOf(cleanDirectUserId to 1)),
                userLatestMarkers = mapOf(cleanDirectUserId to marker),
            )
        } else {
            val roomId = message.roomId.trim().takeIf { it.isNotEmpty() } ?: return
            store.merge(
                session.id,
                ChatUnreadSnapshot(roomCounts = mapOf(roomId to 1)),
                roomLatestMarkers = mapOf(roomId to marker),
            )
        }
    }

    suspend fun emitNotificationAutomation(notification: NotificationItem): Boolean {
        if (notification.id.isBlank() || notification.isRead) return false
        val settings = AndroidBackgroundNotificationStore(context)
        if (!settings.isBackgroundSyncEnabled()) return false
        val session = AndroidAuthTokenStore(context)
            .readAccountSessions()
            .firstOrNull { it.current }
            ?: return false
        val token = session.token.trim()
        if (token.isEmpty()) return false
        val specialCareUserIds = AndroidSpecialCareStore(context).loadSpecialCareUserIds(session.id)
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
        automationHolder.emitNow(
            notification
                .copy(isSpecialCare = notification.isSpecialCare || notification.actor.id in specialCareUserIds)
                .toAutomationNotificationEvent(),
        )
        AiBackgroundScheduler.syncNow(context)
        return true
    }

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
        val chatNoiseSettings = settings.loadChatNoiseReductionSettings()
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
                    cacheAccountIdProvider = { session.id },
                    messageCache = AndroidChatMessageCache(context),
                )
                val automationHolder = backgroundAutomationHolder(
                    accountId = session.id,
                    token = token,
                    chatRepository = chatRepository,
                )
                automationHolder.restore()
                val aiStateHolder = backgroundAiStateHolder(session.id, session.token, session.host)
                val automationRules = automationHolder.state.value.rules
                val timelinePlan = automationRules.backgroundTimelineAutomationPlan()
                val timelineRepository = TimelineRepository(tokenProvider = { token })
                val channelRepository = ChannelRepository(tokenProvider = { token })
                val automationEvents = mutableListOf<AutomationEvent>()
                val timelineSeenIds = mutableListOf<String>()
                var shouldRetry = false
                val notificationsDeferred = async {
                    SharkeyNotificationApi().loadNotifications(token, limit = 20)
                }
                val chatDeferred = async {
                    chatRepository.loadAllRoomsForRealtimeTargets()
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
                    is NotificationLoadResult.NetworkError -> {
                        shouldRetry = true
                    }
                    is NotificationLoadResult.ServerError -> Unit
                }

                run {
                    val rooms = chatDeferred.await()
                    val previousRoomLatestMarkers = chatUnreadStore.loadRoomMarkers(session.id)
                    val roomLatestMarkers = rooms.associate { room ->
                            room.id to room.unreadMarker()
                        }
                    val markerChangedRoomIds = roomLatestMarkers.mapNotNull { (roomId, marker) ->
                        val previousMarker = previousRoomLatestMarkers[roomId].orEmpty()
                        roomId.takeIf {
                            marker.isNotBlank() &&
                                previousMarker.isNotBlank() &&
                                marker != previousMarker
                        }
                    }.toSet()
                        val mergedUnread = chatUnreadStore.merge(
                            session.id,
                            ChatUnreadSnapshot(
                                roomCounts = rooms
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
                        val unreadRooms = rooms.filter { room -> room.unreadCount > 0 }
                        val roomsToCheck = if (shouldScanRealtimeAttentionRooms) {
                            (rooms.take(SPECIAL_CARE_ROOM_SCAN_LIMIT) + unreadRooms + rooms.filter { it.id in markerChangedRoomIds }).distinctBy { it.id }
                        } else {
                            (unreadRooms + rooms.filter { it.id in markerChangedRoomIds }).distinctBy { it.id }
                        }
                        for (room in roomsToCheck) {
                            val markerChanged = room.id in markerChangedRoomIds
                            val effectiveUnreadCount = maxOf(room.unreadCount, if (markerChanged) 1 else 0)
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
                                unreadCount = effectiveUnreadCount,
                                limit = MAX_AUTOMATION_ROOM_MESSAGES_PER_SYNC,
                            )
                            unreadAutomationMessages.forEach { message ->
                                automationEvents += message.toAutomationChatEvent(
                                    roomId = room.id,
                                    roomName = room.name,
                                    currentUser = currentUser,
                                )
                            }
                            val latestAttention = if (
                                (shouldScanAttentionRooms || shouldScanSpecialCareRooms || markerChanged) &&
                                effectiveUnreadCount > 0
                            ) {
                                roomMessages.latestAttentionMessage(
                                    unreadCount = effectiveUnreadCount,
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
                                automationEvents += attentionEvent
                            }
                            if (effectiveUnreadCount <= 0 && latestAttention == null) continue
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
                            val unreadCount = maxOf(mergedUnread.roomCounts[room.id] ?: room.unreadCount, effectiveUnreadCount)
                            val eventId = roomEventId(
                                roomId = room.id,
                                marker = latestMarker,
                                unreadCount = effectiveUnreadCount,
                            )
                            if (eventId in seenIds) continue
                            val roomNoise = chatNoiseSettings.chatNoiseDecision(
                                message = attentionMessage,
                                fallbackUser = actor,
                                attentionKind = attentionKind,
                                roomName = room.name,
                                chatTitle = room.name,
                                unreadMessages = roomMessages.recentUnreadMessagesForNoise(effectiveUnreadCount),
                                aiStateHolder = aiStateHolder,
                            )
                            if (!roomNoise.shouldNotify) continue
                            val createdAtEpochMillis = attentionMessage?.createdAt.toEpochMillisOrNow()
                            val titlePrefix = attentionKind?.label
                            val burstSummary = chatNoiseSettings.burstSummary(
                                unreadCount = unreadCount,
                                messages = roomMessages.recentUnreadMessagesForNoise(effectiveUnreadCount),
                            )
                            events += BackgroundNotificationEvent(
                                id = eventId,
                                title = when {
                                    titlePrefix != null -> "$titlePrefix · ${actor.displayName}"
                                    isSpecialCare -> "特别关心 · ${actor.displayName}"
                                    burstSummary != null -> "${room.name} · 合并提醒"
                                    else -> room.name
                                },
                                text = burstSummary ?: latestPreview ?: "${unreadCount.coerceAtLeast(0)} 条未读消息",
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

                when (val result = userChatDeferred.await()) {
                    is ChatUserConversationRepositoryResult.Success -> {
                        val previousUserLatestMarkers = chatUnreadStore.loadUserMarkers(session.id)
                        val userLatestMarkers = result.conversations.associate { conversation ->
                            conversation.user.id to conversation.latestMessage.unreadMarker()
                        }
                        val markerChangedUserIds = userLatestMarkers.mapNotNull { (userId, marker) ->
                            val previousMarker = previousUserLatestMarkers[userId].orEmpty()
                            userId.takeIf {
                                marker.isNotBlank() &&
                                    previousMarker.isNotBlank() &&
                                    marker != previousMarker
                            }
                        }.toSet()
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
                            .filter { conversation ->
                                conversation.unreadCount > 0 ||
                                    conversation.user.id in markerChangedUserIds
                            }
                            .mapNotNull { conversation ->
                                val markerChanged = conversation.user.id in markerChangedUserIds
                                val effectiveUnreadCount = maxOf(conversation.unreadCount, if (markerChanged) 1 else 0)
                                val notificationEventId = userEventId(
                                    userId = conversation.user.id,
                                    marker = userLatestMarkers[conversation.user.id].orEmpty(),
                                    unreadCount = effectiveUnreadCount,
                                )
                                val rawLatestMessage = conversation.latestMessage
                                val latestMessage = if (rawLatestMessage?.requiresRealtimeAttentionResolution() == true) {
                                    chatRepository.resolveRealtimeMessage(rawLatestMessage, conversation.user.id)
                                } else {
                                    rawLatestMessage
                                }
                                val refreshedUserMessages = if (effectiveUnreadCount > 1) {
                                    when (val messagesResult = chatRepository.refreshUserMessages(conversation.user.id)) {
                                        is ChatMessageRepositoryResult.Success -> messagesResult.messages
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
                                val unreadUserMessages = refreshedUserMessages.recentUnreadAutomationMessages(
                                    unreadCount = effectiveUnreadCount,
                                    limit = MAX_AUTOMATION_USER_MESSAGES_PER_SYNC,
                                )
                                unreadUserMessages.forEach { message ->
                                    automationEvents += message.toAutomationChatEvent(
                                        roomId = message.roomId,
                                        directUserId = conversation.user.id,
                                        currentUser = currentUser,
                                    )
                                }
                                val latestAttention = (refreshedUserMessages.ifEmpty { listOfNotNull(latestMessage) })
                                    .latestAttentionMessage(
                                        unreadCount = effectiveUnreadCount,
                                        specialCareUserIds = if (specialCareEnabled) specialCareUserIds else emptySet(),
                                        currentUser = currentUser,
                                    )
                                val attentionMessage = latestAttention?.message
                                val attentionKind = latestAttention?.kind
                                latestMessage?.let { message ->
                                    val latestEvent = message.toAutomationChatEvent(
                                        roomId = message.roomId,
                                        directUserId = conversation.user.id,
                                        currentUser = currentUser,
                                    )
                                    automationEvents += latestEvent
                                }
                                latestAttention?.let { attention ->
                                    val attentionEvent = attention.message.toAutomationChatEvent(
                                        roomId = attention.message.roomId,
                                        directUserId = conversation.user.id,
                                        attentionKind = attention.kind.name,
                                        currentUser = currentUser,
                                    ).asBackgroundChatAttentionEvent(attention.kind)
                                    automationEvents += attentionEvent
                                }
                                val isSpecialCare = conversation.user.id in specialCareUserIds
                                val gatedBySpecialCareSetting = attentionKind == ChatAttentionKind.SpecialCare ||
                                    (attentionKind == null && isSpecialCare)
                                val unreadCount = maxOf(
                                    mergedUnread.userCounts[conversation.user.id] ?: conversation.unreadCount,
                                    effectiveUnreadCount,
                                )
                                val displayMessage = attentionMessage ?: latestMessage
                                if (notificationEventId in seenIds) return@mapNotNull null
                                val createdAtEpochMillis = displayMessage?.createdAt.toEpochMillisOrNow()
                                val userNoise = chatNoiseSettings.chatNoiseDecision(
                                    message = displayMessage,
                                    fallbackUser = conversation.user,
                                    attentionKind = attentionKind ?: ChatAttentionKind.SpecialCare.takeIf { isSpecialCare },
                                    roomName = "",
                                    chatTitle = conversation.user.displayName,
                                    unreadMessages = unreadUserMessages.ifEmpty { listOfNotNull(displayMessage) },
                                    aiStateHolder = aiStateHolder,
                                )
                                if (!userNoise.shouldNotify) return@mapNotNull null
                                val burstSummary = chatNoiseSettings.burstSummary(
                                    unreadCount = unreadCount,
                                    messages = unreadUserMessages.ifEmpty { listOfNotNull(displayMessage) },
                                )
                                BackgroundNotificationEvent(
                                    id = notificationEventId,
                                    title = attentionKind?.let { "${it.label} · ${conversation.user.displayName}" }
                                        ?: if (isSpecialCare) {
                                            "特别关心 · ${conversation.user.displayName}"
                                        } else if (burstSummary != null) {
                                            "${conversation.user.displayName} · 合并提醒"
                                    } else {
                                        conversation.user.displayName
                                    },
                                    text = burstSummary
                                        ?: displayMessage?.text?.takeIf { it.isNotBlank() }
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
                                            id = "chat-attention-user-${conversation.user.id}-${displayMessage.unreadMarker().ifBlank { unreadCount.toString() }}",
                                            type = NotificationType.App,
                                            actor = conversation.user,
                                            text = "${attentionKind?.label ?: "特别关心"} · 在聊天中发来了新消息",
                                            createdAtLabel = displayMessage?.createdAtLabel?.ifBlank { "刚刚" } ?: "刚刚",
                                            createdAtEpochMillis = createdAtEpochMillis,
                                            notePreviewText = displayMessage?.text?.takeIf { it.isNotBlank() }
                                                ?: "${unreadCount.coerceAtLeast(0)} 条未读私聊",
                                            isSpecialCare = true,
                                            chatUserId = conversation.user.id,
                                            chatMessageId = displayMessage?.id?.takeIf { it.isNotBlank() },
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

                val visibleEventCandidates = events
                    .filter { !it.gatedBySpecialCareSetting || specialCareEnabled }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<BackgroundNotificationEvent> { if (it.specialCare) 1 else 0 }
                            .thenByDescending { it.createdAtEpochMillis },
                    )
                    .take(maxNotificationsPerSync(trigger))
                val claimedVisibleEventIds = settings.claimSeenIds(
                    ids = visibleEventCandidates.map { it.id },
                    limit = MAX_STORED_SEEN_IDS,
                )
                val visibleEvents = visibleEventCandidates.filter { it.id in claimedVisibleEventIds }
                val automationEventCandidates = automationEvents.distinctBy { it.backgroundAutomationCandidateKey() }
                if (visibleEvents.isNotEmpty()) {
                    BackgroundNotificationPublisher(context).publish(visibleEvents)
                    context.cacheBackgroundNotificationEvents(
                        accountId = session.id,
                        events = visibleEvents,
                    )
                }
                if (timelineSeenIds.isNotEmpty()) {
                    settings.mergeSeenIds(timelineSeenIds.distinct().take(MAX_SEEN_IDS_PER_SYNC), MAX_STORED_SEEN_IDS)
                }
                automationEventCandidates.forEach { event -> automationHolder.emitNow(event) }
                if (automationEventCandidates.isNotEmpty()) {
                    AiBackgroundScheduler.syncNow(context)
                }
                if (shouldRetry) BackgroundNotificationSyncResult.Retry else BackgroundNotificationSyncResult.Success
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
        const val MAX_STORED_SEEN_IDS = 1_000
        const val SPECIAL_CARE_ROOM_SCAN_LIMIT = 256
        const val MAX_AUTOMATION_ROOM_MESSAGES_PER_SYNC = 40
        const val MAX_AUTOMATION_USER_MESSAGES_PER_SYNC = 40
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
        val aiStateHolder = backgroundAiStateHolder(accountId, token)
        return AutomationStateHolder(
            store = AndroidAutomationStore(context),
            accountId = accountId,
            executor = AppAutomationActionExecutor(
                chatRepository = chatRepository,
                notificationRepository = NotificationRepository(tokenProvider = { token }),
                composeRepository = ComposeRepository(tokenProvider = { token }),
                noteActionRepository = NoteActionRepository(tokenProvider = { token }),
                driveFileRepository = DriveFileRepository(tokenProvider = { token }),
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
                attachmentAuthHeaderProvider = {
                    token.takeIf { it.isNotBlank() }
                        ?.let { value -> mapOf("Authorization" to "Bearer $value") }
                        .orEmpty()
                },
            ),
            aiBridge = aiStateHolder,
            aiToolPermissionProvider = { aiStateHolder.state.value.settings.toolsAllowed },
            scope = CoroutineScope(Dispatchers.Default),
        )
    }

    private fun backgroundAiStateHolder(
        accountId: String,
        token: String,
        host: String = "dc.hhhl.cc",
    ): AiStateHolder {
        return AiStateHolder(
            store = AndroidAiStore(context),
            accountId = accountId,
            repository = AiRepository(
                remoteTokenProvider = { token },
                remoteBaseUrlProvider = { "https://${host.ifBlank { "dc.hhhl.cc" }}" },
            ),
            onQueueChanged = { AiBackgroundScheduler.syncNow(context) },
            scope = CoroutineScope(Dispatchers.Default),
        ).also { it.restore() }
    }

    private suspend fun ChatRepository.resolveRealtimeRoomName(roomId: String): String {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isBlank()) return ""
        return when (val result = showRoom(cleanRoomId)) {
            is ChatRoomMutationRepositoryResult.RoomSaved -> result.room.name
            is ChatRoomMutationRepositoryResult.ActionCompleted,
            is ChatRoomMutationRepositoryResult.Error,
            is ChatRoomMutationRepositoryResult.RoomMessagesCleared,
            is ChatRoomMutationRepositoryResult.RoomMuted,
            is ChatRoomMutationRepositoryResult.RoomRemoved,
            ChatRoomMutationRepositoryResult.Unauthorized,
            is ChatRoomMutationRepositoryResult.ValidationError,
                -> ""
        }
    }
}

private const val MAX_BACKGROUND_AUTOMATION_TIMELINE_NOTES_PER_SOURCE = 8
private const val MAX_CHAT_NOISE_MESSAGES_FOR_AI = 12
private const val MAX_CHAT_NOISE_SUMMARY_MESSAGES = 5
private const val MAX_REALTIME_CHAT_STREAM_ROOMS = 256
private const val MAX_REALTIME_CHAT_STREAM_USERS = 256

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
    cache.update(cleanAccountId) { snapshot ->
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
        NotificationCacheSnapshot(
            notifications = snapshot.notifications,
            chatAttentionNotifications = snapshot.chatAttentionNotifications,
            specialCareNotifications = nextSpecialCareNotifications,
        )
    }
}

internal fun Context.cacheRemoteNotifications(
    accountId: String,
    notifications: List<NotificationItem>,
) {
    val cleanAccountId = accountId.trim()
    if (cleanAccountId.isBlank() || notifications.isEmpty()) return
    val cache = AndroidNotificationCache(this)
    cache.update(cleanAccountId) { snapshot ->
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
        NotificationCacheSnapshot(
            notifications = nextNotifications,
            chatAttentionNotifications = snapshot.chatAttentionNotifications,
            specialCareNotifications = snapshot.specialCareNotifications,
        )
    }
}

internal fun Context.cacheBackgroundNotificationEvents(
    accountId: String,
    events: List<BackgroundNotificationEvent>,
) {
    val cleanAccountId = accountId.trim()
    if (cleanAccountId.isBlank() || events.isEmpty()) return
    val chatAttentionNotifications = events
        .filter { !it.gatedBySpecialCareSetting }
        .mapNotNull { it.cacheNotification }
    val specialCareNotifications = events
        .filter { it.gatedBySpecialCareSetting }
        .mapNotNull { it.cacheNotification }
    if (chatAttentionNotifications.isEmpty() && specialCareNotifications.isEmpty()) return

    val cache = AndroidNotificationCache(this)
    cache.update(cleanAccountId) { snapshot ->
        NotificationCacheSnapshot(
            notifications = snapshot.notifications,
            chatAttentionNotifications = snapshot.chatAttentionNotifications.mergeCachedBackgroundNotifications(
                chatAttentionNotifications,
            ),
            specialCareNotifications = snapshot.specialCareNotifications.mergeCachedBackgroundNotifications(
                specialCareNotifications,
            ),
        )
    }
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

private suspend fun ChatNoiseReductionSettings.chatNoiseDecision(
    message: ChatMessage?,
    fallbackUser: User,
    attentionKind: ChatAttentionKind?,
    roomName: String,
    chatTitle: String,
    unreadMessages: List<ChatMessage>,
    aiStateHolder: AiStateHolder,
): ChatNoiseReductionDecision {
    val candidate = message.toChatNoiseCandidate(
        fallbackUser = fallbackUser,
        attentionKind = attentionKind,
        roomName = roomName,
    )
    val initial = evaluate(candidate)
    if (!initial.requiresAi) return initial
    val aiImportant = runCatching {
        when (val result = aiStateHolder.runBlockingTask(
            AiTaskKind.ChatImportanceCheck,
            AiTaskInput(
                chatTitle = chatTitle.ifBlank { roomName.ifBlank { fallbackUser.displayName } },
                prompt = "后台通知降噪：只判断是否需要立刻弹系统通知。",
                chatMessages = unreadMessages.ifEmpty { listOfNotNull(message) }
                    .map { it.copy(text = it.text.take(800)) }
                    .toAiChatMessageContexts(),
            ),
        )) {
            is AiRepositoryResult.Success -> result.text.aiChatImportanceDecision()
            AiRepositoryResult.Unauthorized,
            is AiRepositoryResult.Error,
                -> null
        }
    }.getOrNull()
    return evaluate(candidate, aiImportant = aiImportant)
}

private fun ChatMessage?.toChatNoiseCandidate(
    fallbackUser: User,
    attentionKind: ChatAttentionKind?,
    roomName: String,
): ChatNoiseReductionCandidate {
    val user = this?.fromUser ?: fallbackUser
    val message = this
    val body = buildString {
        append(message?.text.orEmpty())
        message?.file?.name?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
        if (roomName.isNotBlank()) append(' ').append(roomName)
    }
    return ChatNoiseReductionCandidate(
        senderUserId = user.id,
        senderUsername = user.username,
        senderDisplayName = user.displayName,
        senderHost = user.host.orEmpty(),
        text = body,
        attentionKindName = attentionKind?.label.orEmpty(),
    )
}

private fun List<ChatMessage>.recentUnreadMessagesForNoise(unreadCount: Int): List<ChatMessage> {
    if (unreadCount <= 0 || isEmpty()) return emptyList()
    return takeLast(unreadCount.coerceAtMost(MAX_CHAT_NOISE_MESSAGES_FOR_AI))
}

private fun ChatNoiseReductionSettings.burstSummary(
    unreadCount: Int,
    messages: List<ChatMessage>,
): String? {
    val settings = normalized
    if (!settings.aggregateBurstNotifications || unreadCount < settings.burstMessageThreshold) return null
    val recentMessages = messages.takeLast(settings.burstMessageThreshold.coerceAtMost(MAX_CHAT_NOISE_SUMMARY_MESSAGES))
    val actorSummary = recentMessages
        .map { it.fromUser.displayName.ifBlank { it.fromUser.username } }
        .filter { it.isNotBlank() }
        .distinct()
        .take(3)
        .joinToString("、")
    val latestText = recentMessages.lastOrNull()?.text?.trim().orEmpty().take(80)
    return buildString {
        append("${unreadCount.coerceAtLeast(0)} 条新消息")
        if (actorSummary.isNotBlank()) append(" · ").append(actorSummary)
        if (latestText.isNotBlank()) append("：").append(latestText)
    }
}

private fun List<ChatMessage>.recentUnreadAutomationMessages(
    unreadCount: Int,
    limit: Int,
): List<ChatMessage> {
    if (unreadCount <= 0 || isEmpty() || limit <= 0) return emptyList()
    return takeLast(unreadCount.coerceAtMost(limit))
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

data class RealtimeChatStreamTargets(
    val roomIds: List<String> = emptyList(),
    val userIds: List<String> = emptyList(),
    val roomNamesById: Map<String, String> = emptyMap(),
) {
    val roomIdSet: Set<String> = roomIds.toSet()
    val userIdSet: Set<String> = userIds.toSet()
    val isEmpty: Boolean = roomIds.isEmpty() && userIds.isEmpty()
}

private fun realtimeChatStreamTargets(
    rooms: List<ChatRoom>,
    conversations: List<cc.hhhl.client.model.ChatUserConversation>,
    rememberedRoomIds: Collection<String> = emptyList(),
    rememberedUserIds: Collection<String> = emptyList(),
    specialCareUserIds: Set<String>,
    automationRules: List<AutomationRule>,
): RealtimeChatStreamTargets {
    val chatRules = automationRules.filter { rule ->
        rule.enabled && (rule.trigger == AutomationTrigger.ChatMessage || rule.trigger == AutomationTrigger.ChatAttention || rule.trigger == AutomationTrigger.SpecialCare)
    }
    val explicitRoomIds = chatRules.flatMap { rule ->
        rule.conditions
            .filter { condition -> condition.enabled && condition.type == AutomationConditionType.RoomId }
            .flatMap { condition -> condition.value.splitAutomationValues() }
    }
    val explicitUserIds = chatRules.flatMap { rule ->
        rule.conditions
            .filter { condition ->
                condition.enabled &&
                    (condition.type == AutomationConditionType.DirectUserId || condition.type == AutomationConditionType.SenderUserId || condition.type == AutomationConditionType.SenderUserIds)
            }
            .flatMap { condition -> condition.value.splitAutomationValues() }
    }
    val roomIds = buildList {
        addAll(explicitRoomIds)
        rooms.filter { room -> room.unreadCount > 0 }.forEach { add(it.id) }
        addAll(rememberedRoomIds)
        rooms.forEach { add(it.id) }
    }.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .distinct()
        .take(MAX_REALTIME_CHAT_STREAM_ROOMS)

    val userIds = buildList {
        addAll(explicitUserIds)
        conversations.filter { conversation -> conversation.unreadCount > 0 }.forEach { add(it.user.id) }
        addAll(specialCareUserIds)
        addAll(rememberedUserIds)
        conversations.filter { conversation -> conversation.user.id in specialCareUserIds }.forEach { add(it.user.id) }
        conversations.forEach { add(it.user.id) }
    }.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .distinct()
        .take(MAX_REALTIME_CHAT_STREAM_USERS)

    return RealtimeChatStreamTargets(
        roomIds = roomIds,
        userIds = userIds,
        roomNamesById = rooms
            .filter { room -> room.id.isNotBlank() && room.name.isNotBlank() }
            .associate { room -> room.id to room.name },
    )
}

    private suspend fun ChatRepository.loadAllRoomsForRealtimeTargets(): List<ChatRoom> {
        val initial = when (val result = refresh()) {
            is ChatRepositoryResult.Success -> result
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error,
                -> return emptyList()
        }
        var rooms = initial.rooms
        var endReached = initial.endReached
        while (!endReached && rooms.size < MAX_REALTIME_CHAT_STREAM_ROOMS) {
            val next = when (val result = loadMore(rooms)) {
                is ChatRepositoryResult.Success -> result
                ChatRepositoryResult.Unauthorized,
                is ChatRepositoryResult.Error,
                    -> break
            }
            if (next.rooms.size <= rooms.size) {
                endReached = true
                continue
            }
            rooms = next.rooms
            endReached = next.endReached
        }
        val ownedRooms = when (val result = refreshOwnedRooms()) {
            is ChatRepositoryResult.Success -> result.rooms
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error,
                -> emptyList()
        }
        return rooms.mergeRealtimeOwnedRooms(ownedRooms)
    }

fun ChatMessage.belongsToDirectChat(userId: String): Boolean {
    val cleanUserId = userId.trim()
    return cleanUserId.isNotEmpty() &&
        (fromUser.id == cleanUserId || toUserId == cleanUserId || toUser?.id == cleanUserId)
}

private fun List<ChatRoom>.mergeRealtimeOwnedRooms(ownedRooms: List<ChatRoom>): List<ChatRoom> {
    if (ownedRooms.isEmpty()) return this
    val ownedById = ownedRooms.associateBy { room -> room.id }
    val existingIds = map { room -> room.id }.toSet()
    return map { room -> ownedById[room.id] ?: room } +
        ownedRooms.filterNot { room -> room.id in existingIds }
}

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
    unreadCount: Int,
    specialCareUserIds: Set<String>,
    currentUser: User?,
): ChatAttentionMessage? {
    if (unreadCount <= 0 || isEmpty()) return null
    return takeLast(unreadCount.coerceAtMost(size)).asReversed().firstNotNullOfOrNull { message ->
        message.chatAttentionKind(
            specialCareUserIds = specialCareUserIds,
            currentUser = currentUser,
        )?.let { kind -> ChatAttentionMessage(message, kind) }
    }
}

private fun AutomationEvent.asBackgroundChatAttentionEvent(kind: ChatAttentionKind): AutomationEvent {
    return copy(id = "chat-attention:${kind.name}:${chatMessageId.ifBlank { id }}")
}

private fun ChatMessage.toRealtimeChatAttentionEvent(
    kind: ChatAttentionKind,
    directUserId: String?,
    roomName: String = "",
): BackgroundNotificationEvent {
    val sourceId = directUserId?.takeIf { it.isNotBlank() } ?: roomId
    val messageKey = unreadMarker()
    val preview = chatMessageBodyText(this).takeIf { it.isNotBlank() }
        ?: file?.name?.takeIf { it.isNotBlank() }
        ?: "发来了新消息"
    val location = if (directUserId.isNullOrBlank()) {
        roomName.takeIf { it.isNotBlank() }?.let { "在聊天室 $it" } ?: "在聊天室"
    } else {
        "在聊天中"
    }
    val createdAtEpochMillis = createdAt.toEpochMillisOrNow()
    return BackgroundNotificationEvent(
        id = "chat-attention-alert:${kind.name}:$sourceId:$messageKey",
        title = "${kind.label} · ${fromUser.displayName.ifBlank { fromUser.username }}",
        text = preview,
        specialCare = true,
        gatedBySpecialCareSetting = kind == ChatAttentionKind.SpecialCare,
        avatarMode = NotificationAvatarMode.UserAvatar(fromUser.avatarUrl, fromUser.avatarInitial),
        createdAtEpochMillis = createdAtEpochMillis,
        cacheNotification = NotificationItem(
            id = "chat-attention-${kind.name.lowercase()}-$sourceId-$messageKey",
            type = kind.toNotificationType(),
            actor = fromUser,
            text = "${kind.label} · $location 发来了新消息",
            createdAtLabel = createdAtLabel.ifBlank { "刚刚" },
            createdAtEpochMillis = createdAtEpochMillis,
            notePreviewText = preview,
            isSpecialCare = kind == ChatAttentionKind.SpecialCare,
            chatRoomId = roomId.takeIf { it.isNotBlank() },
            chatUserId = directUserId?.takeIf { it.isNotBlank() },
            chatMessageId = id.takeIf { it.isNotBlank() },
        ),
    )
}

private fun ChatMessage.realtimeFallbackNotificationEventId(directUserId: String?): String? {
    val marker = unreadMarker().takeIf { it.isNotBlank() } ?: return null
    val cleanDirectUserId = directUserId?.trim()?.takeIf { it.isNotEmpty() }
    return if (cleanDirectUserId != null) {
        userEventId(userId = cleanDirectUserId, marker = marker, unreadCount = 1)
    } else {
        roomId.takeIf { it.isNotBlank() }?.let { roomId ->
            roomEventId(roomId = roomId, marker = marker, unreadCount = 1)
        }
    }
}

private fun ChatAttentionKind.toNotificationType(): NotificationType {
    return when (this) {
        ChatAttentionKind.SpecialCare -> NotificationType.App
        ChatAttentionKind.Mention -> NotificationType.Mention
        ChatAttentionKind.Reply -> NotificationType.Reply
        ChatAttentionKind.Quote -> NotificationType.Quote
    }
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

private fun AutomationEvent.backgroundAutomationCandidateKey(): String {
    val cleanId = id.trim()
    if (cleanId.isNotEmpty()) return cleanId
    return buildList {
        add(trigger.name)
        sourceKind.trim().takeIf { it.isNotEmpty() }?.let(::add)
        senderUserId.trim().takeIf { it.isNotEmpty() }?.let(::add)
        roomId.trim().takeIf { it.isNotEmpty() }?.let(::add)
        directUserId.trim().takeIf { it.isNotEmpty() }?.let(::add)
        notificationType.trim().takeIf { it.isNotEmpty() }?.let(::add)
        noteId.trim().takeIf { it.isNotEmpty() }?.let(::add)
        channelId.trim().takeIf { it.isNotEmpty() }?.let(::add)
        if (createdAtEpochMillis > 0L) add(createdAtEpochMillis.toString())
        defaultBody.trim().takeIf { it.isNotEmpty() }?.let { body -> add(body.take(120)) }
    }.joinToString(":").ifBlank { trigger.name }
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
