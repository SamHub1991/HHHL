package cc.hhhl.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cc.hhhl.client.automation.AppAutomationActionExecutor
import cc.hhhl.client.automation.AutomationAction
import cc.hhhl.client.automation.AutomationActionMode
import cc.hhhl.client.automation.AutomationActionType
import cc.hhhl.client.automation.AutomationCondition
import cc.hhhl.client.automation.AutomationConditionMode
import cc.hhhl.client.automation.AutomationConditionType
import cc.hhhl.client.automation.AutomationRule
import cc.hhhl.client.automation.AutomationRuleDraftResolveInput
import cc.hhhl.client.automation.AutomationStore
import cc.hhhl.client.automation.AutomationStateHolder
import cc.hhhl.client.automation.AutomationTrigger
import cc.hhhl.client.automation.AutomationUiState
import cc.hhhl.client.automation.NoopAutomationStore
import cc.hhhl.client.automation.automationChatAttentionKind
import cc.hhhl.client.automation.parseAutomationRuleDraft
import cc.hhhl.client.automation.resolveAutomationRuleDraft
import cc.hhhl.client.automation.toAutomationChatEvent
import cc.hhhl.client.automation.toAutomationNotificationEvent
import cc.hhhl.client.automation.toAutomationTimelineEvent
import cc.hhhl.client.ai.AiProviderPreset
import cc.hhhl.client.ai.AiRepositoryResult
import cc.hhhl.client.ai.AiSettings
import cc.hhhl.client.ai.AiStore
import cc.hhhl.client.ai.AiStateHolder
import cc.hhhl.client.ai.AiTask
import cc.hhhl.client.ai.AiTaskInput
import cc.hhhl.client.ai.AiTaskKind
import cc.hhhl.client.ai.AiUiState
import cc.hhhl.client.ai.NoopAiStore
import cc.hhhl.client.ai.toAiChatMessageContexts
import cc.hhhl.client.ai.toAiNotificationContexts
import cc.hhhl.client.ai.toAiPostContexts
import cc.hhhl.client.ai.toAiProfileContext
import cc.hhhl.client.ai.toAiReadableText
import cc.hhhl.client.ai.toAiTaskInput
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.ComposeReactionAcceptance
import cc.hhhl.client.api.ComposeScheduledNote
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.api.MainStreamingEvent
import cc.hhhl.client.api.toApiInstantOrNull
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.auth.AuthenticatedUser
import cc.hhhl.client.auth.AuthTokenStore
import cc.hhhl.client.auth.LoginStateHolder
import cc.hhhl.client.auth.NoopAuthTokenStore
import cc.hhhl.client.auth.SharkeyAuthApi
import cc.hhhl.client.cache.ChatMessageCache
import cc.hhhl.client.cache.ChatUnreadStore
import cc.hhhl.client.cache.InMemoryTimelineCache
import cc.hhhl.client.cache.NoopChatMessageCache
import cc.hhhl.client.cache.NoopChatUnreadStore
import cc.hhhl.client.cache.NoopNotificationCache
import cc.hhhl.client.cache.NotificationCache
import cc.hhhl.client.cache.TimelineCache
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.DisplayPreferenceStore
import cc.hhhl.client.display.DisplayPreferenceStateHolder
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.NoopDisplayPreferenceStore
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.media.MediaPicker
import cc.hhhl.client.model.Clip
import cc.hhhl.client.model.ClipListKind
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserList
import cc.hhhl.client.model.UserSocialKind
import cc.hhhl.client.model.UserRelationshipListEntry
import cc.hhhl.client.repository.AntennaRepository
import cc.hhhl.client.repository.AnnouncementRepository
import cc.hhhl.client.repository.AdminRepository
import cc.hhhl.client.repository.AchievementRepository
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatStreamingRepository
import cc.hhhl.client.repository.ChannelRepository
import cc.hhhl.client.repository.ClipRepository
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.EmojiRepository
import cc.hhhl.client.repository.FavoriteNoteRepository
import cc.hhhl.client.repository.FlashRepository
import cc.hhhl.client.repository.FollowRequestRepository
import cc.hhhl.client.repository.GalleryRepository
import cc.hhhl.client.repository.InstanceMetaRepository
import cc.hhhl.client.repository.MainStreamingRepository
import cc.hhhl.client.repository.NoteDetailRepository
import cc.hhhl.client.repository.NoteRepliesRepository
import cc.hhhl.client.repository.NoteActionRepository
import cc.hhhl.client.repository.NoteActionRequest
import cc.hhhl.client.repository.NotificationRepository
import cc.hhhl.client.repository.PageRepository
import cc.hhhl.client.repository.SettingsRepository
import cc.hhhl.client.repository.TimelineRepository
import cc.hhhl.client.repository.UserNotesRepository
import cc.hhhl.client.repository.UserListRepository
import cc.hhhl.client.repository.UserProfileRepository
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserSocialRepository
import cc.hhhl.client.repository.ChannelsRepositoryResult
import cc.hhhl.client.state.AntennaStateHolder
import cc.hhhl.client.state.AntennaUiState
import cc.hhhl.client.state.AnnouncementStateHolder
import cc.hhhl.client.state.AdminStateHolder
import cc.hhhl.client.state.AdminDashboardUiState
import cc.hhhl.client.state.AchievementStateHolder
import cc.hhhl.client.state.AchievementUiState
import cc.hhhl.client.state.ChatStateHolder
import cc.hhhl.client.state.ChannelStateHolder
import cc.hhhl.client.state.ChannelUiState
import cc.hhhl.client.state.ClipStateHolder
import cc.hhhl.client.state.ClipUiState
import cc.hhhl.client.state.ComposeCompletionStateHolder
import cc.hhhl.client.state.ComposeCompletionKind
import cc.hhhl.client.state.ComposeCompletionUiState
import cc.hhhl.client.state.ComposePollDeadlinePreset
import cc.hhhl.client.state.ComposeStateHolder
import cc.hhhl.client.state.ComposeUiState
import cc.hhhl.client.state.ComposeDraftStore
import cc.hhhl.client.state.DiscoverStateHolder
import cc.hhhl.client.state.DiscoverUiState
import cc.hhhl.client.state.ChatAttentionKind
import cc.hhhl.client.state.ChatUiState
import cc.hhhl.client.state.DriveFilesUiState
import cc.hhhl.client.state.DriveFilesStateHolder
import cc.hhhl.client.state.FavoriteNoteStateHolder
import cc.hhhl.client.state.FavoriteNoteUiState
import cc.hhhl.client.state.FlashStateHolder
import cc.hhhl.client.state.FlashUiState
import cc.hhhl.client.state.FollowRequestStateHolder
import cc.hhhl.client.state.FollowRequestUiState
import cc.hhhl.client.state.GalleryStateHolder
import cc.hhhl.client.state.GalleryUiState
import cc.hhhl.client.state.InstanceMetaStateHolder
import cc.hhhl.client.state.InstanceMetaUiState
import cc.hhhl.client.state.NoteLocalMutation
import cc.hhhl.client.state.NoteActionStateHolder
import cc.hhhl.client.state.NoteActionUiState
import cc.hhhl.client.state.NoteDetailUiState
import cc.hhhl.client.state.NoopComposeDraftStore
import cc.hhhl.client.state.NoteDetailStateHolder
import cc.hhhl.client.state.NoopNotificationReadStore
import cc.hhhl.client.state.NoopRecentReactionStore
import cc.hhhl.client.state.NotificationReadStore
import cc.hhhl.client.state.NotificationStateHolder
import cc.hhhl.client.state.NotificationUiState
import cc.hhhl.client.state.PageStateHolder
import cc.hhhl.client.state.PageUiState
import cc.hhhl.client.state.RecentReactionStore
import cc.hhhl.client.state.RelationshipManagementTab
import cc.hhhl.client.state.RelationshipManagementStateHolder
import cc.hhhl.client.state.RelationshipManagementUiState
import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.state.SettingsStateHolder
import cc.hhhl.client.state.SettingsUiState
import cc.hhhl.client.state.AnnouncementUiState
import cc.hhhl.client.state.NoopSpecialCareStore
import cc.hhhl.client.state.SpecialCareStateHolder
import cc.hhhl.client.state.SpecialCareStore
import cc.hhhl.client.state.TimelineStateHolder
import cc.hhhl.client.state.TimelineUiState
import cc.hhhl.client.state.UserListStateHolder
import cc.hhhl.client.state.UserListUiState
import cc.hhhl.client.state.UserProfileStateHolder
import cc.hhhl.client.state.UserProfileUiState
import cc.hhhl.client.state.UserSocialStateHolder
import cc.hhhl.client.state.UserSocialUiState
import cc.hhhl.client.navigation.AppRoute
import cc.hhhl.client.navigation.MentionNavigationTarget
import cc.hhhl.client.navigation.RootRoute
import cc.hhhl.client.navigation.SiteLinkNavigationTarget
import cc.hhhl.client.navigation.mentionNavigationTarget
import cc.hhhl.client.navigation.rootRouteFor
import cc.hhhl.client.navigation.siteLinkNavigationTarget
import cc.hhhl.client.navigation.supportedRouteOrFallback
import cc.hhhl.client.navigation.visibleRootRoutes
import cc.hhhl.client.theme.HhhlTheme
import cc.hhhl.client.theme.HhhlCustomTheme
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.theme.NoopThemeStore
import cc.hhhl.client.theme.ThemeStore
import cc.hhhl.client.theme.ThemeStateHolder
import cc.hhhl.client.update.AppReleaseNotes
import cc.hhhl.client.update.NoopReleaseNotesStore
import cc.hhhl.client.update.ReleaseNotesStore
import cc.hhhl.client.update.releaseNotesFor
import cc.hhhl.client.ui.component.HhhlBottomNav
import cc.hhhl.client.ui.component.HhhlAlertDialog
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.LocalCustomEmojiUrls
import cc.hhhl.client.ui.component.LocalBlockedNoteAuthorIds
import cc.hhhl.client.ui.component.LocalNoteRowActions
import cc.hhhl.client.ui.component.LocalNoteRowGesturesEnabled
import cc.hhhl.client.ui.component.MediaPreviewOverlay
import cc.hhhl.client.presentation.chatMessageBodyText
import cc.hhhl.client.presentation.notePreviewText
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.LocalMutedNoteFilters
import cc.hhhl.client.ui.component.LocalUserQuickActions
import cc.hhhl.client.ui.component.MutedNoteFilters
import cc.hhhl.client.ui.component.NoteRowDensity
import cc.hhhl.client.ui.component.NoteRowActions
import cc.hhhl.client.ui.component.UserQuickActions
import cc.hhhl.client.ui.screen.AnnouncementScreen
import cc.hhhl.client.ui.screen.AutomationScreen
import cc.hhhl.client.ui.screen.AdminDashboardScreen
import cc.hhhl.client.ui.screen.AchievementScreen
import cc.hhhl.client.ui.screen.AntennaScreen
import cc.hhhl.client.ui.screen.ChatScreen
import cc.hhhl.client.ui.screen.ChannelScreen
import cc.hhhl.client.ui.screen.ClipScreen
import cc.hhhl.client.ui.screen.ComposeScreen
import cc.hhhl.client.ui.screen.DiscoverScreen
import cc.hhhl.client.ui.screen.DriveScreen
import cc.hhhl.client.ui.screen.FavoriteNoteScreen
import cc.hhhl.client.ui.screen.FlashScreen
import cc.hhhl.client.ui.screen.flashWebPath
import cc.hhhl.client.ui.screen.FollowRequestScreen
import cc.hhhl.client.ui.screen.GalleryScreen
import cc.hhhl.client.ui.screen.LoginScreen
import cc.hhhl.client.ui.screen.NoteDetailScreen
import cc.hhhl.client.ui.screen.NotificationsScreen
import cc.hhhl.client.ui.screen.PageScreen
import cc.hhhl.client.ui.screen.ProfileNotesScreen
import cc.hhhl.client.ui.screen.ProfileScreen
import cc.hhhl.client.ui.screen.RelationshipManagementScreen
import cc.hhhl.client.ui.screen.SettingsScreen
import cc.hhhl.client.ui.screen.SettingsManagementScreen
import cc.hhhl.client.ui.screen.settingsWebManagementPath
import cc.hhhl.client.ui.screen.settingsManagementSectionKey
import cc.hhhl.client.ui.screen.ThemeCustomizationScreen
import cc.hhhl.client.ui.screen.TimelineScreen
import cc.hhhl.client.ui.screen.UserListScreen
import cc.hhhl.client.ui.screen.UserSocialScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun HhhlApp(
    openUrl: (String) -> Unit = {},
    shareUrl: (String) -> Unit = {},
    downloadUrl: (String, String, String) -> Unit = { url, _, _ -> openUrl(url) },
    mediaPicker: MediaPicker? = null,
    authCallbackSession: String? = null,
    authTokenStore: AuthTokenStore = NoopAuthTokenStore,
    themeStore: ThemeStore = NoopThemeStore,
    displayPreferenceStore: DisplayPreferenceStore = NoopDisplayPreferenceStore,
    recentReactionStore: RecentReactionStore = NoopRecentReactionStore,
    specialCareStore: SpecialCareStore = NoopSpecialCareStore,
    automationStore: AutomationStore = NoopAutomationStore,
    aiStore: AiStore = NoopAiStore,
    composeDraftStore: ComposeDraftStore = NoopComposeDraftStore,
    chatMessageCache: ChatMessageCache = NoopChatMessageCache,
    chatUnreadStore: ChatUnreadStore = NoopChatUnreadStore,
    notificationCache: NotificationCache = NoopNotificationCache,
    notificationReadStore: NotificationReadStore = NoopNotificationReadStore,
    timelineCache: TimelineCache? = null,
    backgroundNotificationsEnabled: Boolean = false,
    specialCareBackgroundNotificationsEnabled: Boolean = true,
    onBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onSpecialCareUsersChanged: (Set<String>) -> Unit = {},
    onSpecialCareSystemNotification: (NotificationItem) -> Unit = {},
    onAutomationSystemNotification: ((String, String) -> Boolean?)? = null,
    onAiQueueChanged: () -> Unit = {},
    initialSharedText: String? = null,
    onInitialSharedTextConsumed: () -> Unit = {},
    appVersionName: String = "",
    releaseNotesStore: ReleaseNotesStore = NoopReleaseNotesStore,
    onCheckForUpdates: (((String) -> Unit) -> Unit) = { report -> report("当前平台暂不支持应用内更新") },
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit) = {},
    onAuthCallbackConsumed: () -> Unit = {},
) {
    val appScope = rememberCoroutineScope()
    val loginStateHolder = remember {
        LoginStateHolder(
            authenticator = SharkeyAuthApi(),
            tokenStore = authTokenStore,
            onAccountRemoved = { accountId ->
                chatMessageCache.clearAccount(accountId)
                chatUnreadStore.clearAccount(accountId)
                specialCareStore.clearAccount(accountId)
                automationStore.clearAccount(accountId)
                aiStore.clearAccount(accountId)
                notificationCache.clearAccount(accountId)
                notificationReadStore.clearAccount(accountId)
            },
            scope = appScope,
        )
    }
    val themeStateHolder = remember { ThemeStateHolder(themeStore = themeStore) }
    val displayPreferenceStateHolder = remember {
        DisplayPreferenceStateHolder(store = displayPreferenceStore)
    }
    val loginState by loginStateHolder.state.collectAsState()
    val themeState by themeStateHolder.state.collectAsState()
    val displayPreferenceState by displayPreferenceStateHolder.state.collectAsState()

    LaunchedEffect(Unit) {
        themeStateHolder.restoreStoredTheme()
        displayPreferenceStateHolder.restoreStoredPreferences()
        if (authCallbackSession.isNullOrBlank()) {
            loginStateHolder.restoreStoredToken()
        }
    }

    LaunchedEffect(authCallbackSession) {
        if (!authCallbackSession.isNullOrBlank()) {
            loginStateHolder.completeBrowserLogin(authCallbackSession)
            onAuthCallbackConsumed()
        }
    }

    HhhlTheme(
        preset = themeState.selectedPreset,
        customTheme = themeState.customTheme,
    ) {
        if (loginState.user == null) {
            LoginScreen(
                state = loginState,
                onLogin = {
                    val pendingSession = loginState.pendingSession
                    if (pendingSession == null) {
                        loginStateHolder.startBrowserLogin(openUrl)
                    } else {
                        loginStateHolder.completeBrowserLogin(pendingSession)
                    }
                },
                onSwitchAccount = loginStateHolder::switchAccount,
                onRemoveAccount = loginStateHolder::removeAccount,
                modifier = Modifier.safeContentPadding(),
            )
        } else {
            key(loginState.currentAccountId) {
                MainShell(
                    sessionToken = loginState.sessionToken,
                    accountUser = loginState.user,
                    accounts = loginState.accounts,
                    currentAccountId = loginState.currentAccountId,
                    openUrl = openUrl,
                    shareUrl = shareUrl,
                    downloadUrl = downloadUrl,
                    mediaPicker = mediaPicker,
                    timelineCache = timelineCache,
                    recentReactionStore = recentReactionStore,
                    specialCareStore = specialCareStore,
                    automationStore = automationStore,
                    aiStore = aiStore,
                    composeDraftStore = composeDraftStore,
                    chatMessageCache = chatMessageCache,
                    chatUnreadStore = chatUnreadStore,
                    notificationCache = notificationCache,
                    notificationReadStore = notificationReadStore,
                    selectedTheme = themeState.selectedPreset,
                    customTheme = themeState.customTheme,
                    onCustomThemeChanged = themeStateHolder::updateCustomTheme,
                    onResetCustomTheme = themeStateHolder::resetCustomTheme,
                    onSetGlobalBackgroundImage = themeStateHolder::setGlobalBackgroundImage,
                    onClearGlobalBackgroundImage = themeStateHolder::clearGlobalBackgroundImage,
                    onSetChatBackgroundImage = themeStateHolder::setChatBackgroundImage,
                    onClearChatBackgroundImage = themeStateHolder::clearChatBackgroundImage,
                    onThemeSelected = themeStateHolder::select,
                    selectedTimelineDensity = displayPreferenceState.timelineDensity,
                    onTimelineDensitySelected = displayPreferenceStateHolder::selectTimelineDensity,
                    listGesturesEnabled = displayPreferenceState.listGesturesEnabled,
                    onListGesturesEnabledChanged = displayPreferenceStateHolder::setListGesturesEnabled,
                    selectedDefaultNoteVisibility = displayPreferenceState.defaultNoteVisibility,
                    onDefaultNoteVisibilitySelected = displayPreferenceStateHolder::selectDefaultNoteVisibility,
                    selectedNotificationBadgeMode = displayPreferenceState.notificationBadgeMode,
                    onNotificationBadgeModeSelected = displayPreferenceStateHolder::selectNotificationBadgeMode,
                    backgroundNotificationsEnabled = backgroundNotificationsEnabled,
                    specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
                    onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
                    onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
                    onSpecialCareUsersChanged = onSpecialCareUsersChanged,
                    onSpecialCareSystemNotification = onSpecialCareSystemNotification,
                    onAutomationSystemNotification = onAutomationSystemNotification,
                    onAiQueueChanged = onAiQueueChanged,
                    initialSharedText = initialSharedText,
                    onInitialSharedTextConsumed = onInitialSharedTextConsumed,
                    appVersionName = appVersionName,
                    releaseNotesStore = releaseNotesStore,
                    onCheckForUpdates = onCheckForUpdates,
                    onBackHandlerChanged = onBackHandlerChanged,
                    onSwitchAccount = loginStateHolder::switchAccount,
                    onRemoveAccount = loginStateHolder::removeAccount,
                    onAddAccount = { loginStateHolder.startBrowserLogin(openUrl) },
                    onAuthInvalid = loginStateHolder::logout,
                    onSharedAccessLogin = loginStateHolder::importSessionToken,
                )
            }
        }
    }
}

internal fun loadedNotesForActions(
    timelineState: cc.hhhl.client.state.TimelineUiState,
    noteDetailState: cc.hhhl.client.state.NoteDetailUiState,
    userProfileState: cc.hhhl.client.state.UserProfileUiState,
    viewedProfileState: cc.hhhl.client.state.UserProfileUiState,
    discoverState: cc.hhhl.client.state.DiscoverUiState,
    favoriteNoteState: cc.hhhl.client.state.FavoriteNoteUiState,
    userListState: cc.hhhl.client.state.UserListUiState,
    antennaState: cc.hhhl.client.state.AntennaUiState,
    clipState: cc.hhhl.client.state.ClipUiState,
    channelState: cc.hhhl.client.state.ChannelUiState,
): List<Note> {
    return buildList {
        noteDetailState.note?.let { add(it) }
        addAll(noteDetailState.replies)
        userProfileState.user?.pinnedNotes?.let { addAll(it) }
        viewedProfileState.user?.pinnedNotes?.let { addAll(it) }
        channelState.selectedChannel?.pinnedNotes?.let { addAll(it) }
        channelState.channels.forEach { addAll(it.pinnedNotes) }
        timelineState.tabs.values.forEach { addAll(it.notes) }
        addAll(userProfileState.notes)
        addAll(viewedProfileState.notes)
        addAll(discoverState.notes)
        addAll(favoriteNoteState.favorites.map { it.note })
        addAll(userListState.notes)
        addAll(antennaState.notes)
        addAll(clipState.notes)
        addAll(channelState.notes)
    }
}

private fun relationshipSpecialCareEntries(
    userIds: Set<String>,
    accountUser: AuthenticatedUser?,
    userProfileState: cc.hhhl.client.state.UserProfileUiState,
    viewedProfileState: cc.hhhl.client.state.UserProfileUiState,
    loadedNotes: List<Note>,
    chatState: ChatUiState,
): List<UserRelationshipListEntry> {
    if (userIds.isEmpty()) return emptyList()
    val usersById = linkedMapOf<String, User>()

    fun addUser(user: User?) {
        if (user == null || user.id !in userIds) return
        usersById.putIfAbsent(user.id, user)
    }

    accountUser?.let { user ->
        addUser(
            User(
                id = user.id,
                displayName = user.displayName.ifBlank { user.username },
                username = user.username,
                avatarInitial = user.displayName.ifBlank { user.username }.take(1).uppercase().ifBlank { "?" },
                avatarUrl = user.avatarUrl,
            ),
        )
    }
    addUser(userProfileState.user)
    addUser(viewedProfileState.user)
    loadedNotes.forEach { note ->
        addUser(note.author)
        note.quotedNote?.let { addUser(it.author) }
    }
    chatState.rooms.forEach { addUser(it.owner) }
    chatState.userConversations.forEach { conversation ->
        addUser(conversation.user)
        conversation.latestMessage?.let { message ->
            addUser(message.fromUser)
            addUser(message.toUser)
        }
    }
    chatState.messages.forEach { message ->
        addUser(message.fromUser)
        addUser(message.toUser)
        addUser(message.reply?.fromUser)
        addUser(message.quote?.fromUser)
    }

    return userIds.map { userId ->
        val user = usersById[userId] ?: relationshipPlaceholderUser(userId)
        UserRelationshipListEntry(
            id = "special-care-$userId",
            user = user,
        )
    }
}

private fun relationshipPlaceholderUser(userId: String): User {
    val label = userId.takeIf { it.isNotBlank() } ?: "unknown"
    return User(
        id = userId,
        displayName = label,
        username = label,
        avatarInitial = label.take(1).uppercase().ifBlank { "?" },
    )
}

private fun routeLabel(route: AppRoute): String {
    return when (route) {
        AppRoute.Timeline -> "时间线"
        AppRoute.Discover -> "发现"
        AppRoute.Chat -> "聊天"
        AppRoute.Notifications -> "通知"
        AppRoute.Profile -> "我的"
        AppRoute.ProfileNotes -> "我的帖子"
        AppRoute.Settings -> "设置"
        AppRoute.ThemeCustomization -> "主题自定义"
        AppRoute.Automation -> "自动化"
        is AppRoute.SettingsManagement -> "设置管理"
        AppRoute.AdminDashboard -> "管理后台"
        AppRoute.Drive -> "网盘"
        AppRoute.Achievements -> "成就"
        AppRoute.FavoriteNotes -> "收藏"
        AppRoute.UserLists -> "列表"
        AppRoute.FollowRequests -> "关注请求"
        AppRoute.RelationshipManagement -> "关系管理"
        AppRoute.Antennas -> "天线"
        AppRoute.Clips -> "剪辑"
        AppRoute.Channels -> "频道"
        AppRoute.Pages -> "页面"
        AppRoute.Gallery -> "相册"
        AppRoute.Flash -> "Flash"
        AppRoute.Announcements -> "公告"
        is AppRoute.UserProfile -> "用户资料"
        is AppRoute.UserSocial -> route.kind.label
        is AppRoute.NoteDetail -> "帖子详情"
        is AppRoute.Compose -> "发帖"
    }
}

private fun selectedChatTitle(state: ChatUiState): String {
    return state.selectedRoom?.name?.ifBlank { "聊天室" }
        ?: state.selectedUserConversation?.user?.let { user -> user.displayName.ifBlank { user.username } }
        ?: "聊天"
}

private fun relationshipEntryForUser(
    user: User,
    prefix: String,
): UserRelationshipListEntry {
    return UserRelationshipListEntry(
        id = "$prefix-${user.id}",
        user = user,
    )
}

@Composable
private fun ReleaseNotesDialog(
    notes: AppReleaseNotes,
    onDismiss: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(notes.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = notes.summary,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    notes.highlights.forEach { item ->
                        Text(
                            text = "· $item",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    text = "此版本只展示一次，关闭后不会在下次打开时再次弹出。",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            HhhlTextButton(onClick = onDismiss, emphasized = true) {
                Text("知道了")
            }
        },
    )
}

@Composable
private fun QuickUserListDialog(
    user: User,
    lists: List<UserList>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectList: (UserList) -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入列表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "选择要加入 @${user.username} 的列表。",
                    color = LocalHhhlColors.current.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = LocalHhhlColors.current.danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (lists.isEmpty()) {
                    Text(
                        text = if (isLoading) "正在读取列表" else "暂无可用列表",
                        color = LocalHhhlColors.current.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        lists.forEach { list ->
                            HhhlTextButton(
                                onClick = { onSelectList(list) },
                                enabled = !isLoading,
                            ) {
                                Text(list.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = onRefresh,
                enabled = !isLoading,
            ) {
                Text(if (isLoading) "读取中" else "刷新")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun AuthenticatedUser.toDomainUser(host: String? = null): User {
    val display = displayName.ifBlank { username }
    return User(
        id = id,
        displayName = display,
        username = username,
        avatarInitial = display.take(1).uppercase().ifBlank { "?" },
        avatarUrl = avatarUrl,
        host = host,
    )
}

private fun Note.toSpecialCareNotification(fallbackEpochMillis: Long): NotificationItem {
    return NotificationItem(
        id = "special-care-note-$id",
        type = NotificationType.Note,
        actor = author,
        text = "发布了新帖子",
        createdAtLabel = createdAtLabel.ifBlank { "刚刚" },
        createdAtEpochMillis = createdAt.toApiInstantOrNull()?.toEpochMilliseconds()
            ?: fallbackEpochMillis,
        noteId = id,
        notePreviewText = notePreviewText(text = text, cw = cw),
        isSpecialCare = true,
    )
}

private fun cc.hhhl.client.state.SpecialCareChatToast.toChatAttentionNotification(
    fallbackEpochMillis: Long,
): NotificationItem {
    val notificationType = when (kind) {
        ChatAttentionKind.SpecialCare -> NotificationType.App
        ChatAttentionKind.Mention -> NotificationType.Mention
        ChatAttentionKind.Reply -> NotificationType.Reply
        ChatAttentionKind.Quote -> NotificationType.Quote
    }
    val actorDisplayName = displayName.ifBlank { userId }
    val prefix = when (kind) {
        ChatAttentionKind.SpecialCare -> "特别关心"
        ChatAttentionKind.Mention -> "有人 @ 你"
        ChatAttentionKind.Reply -> "有人回复你"
        ChatAttentionKind.Quote -> "有人引用你"
    }
    return NotificationItem(
        id = "chat-attention-${kind.name.lowercase()}-${chatUserId ?: roomId}-$messageId",
        type = notificationType,
        actor = cc.hhhl.client.model.User(
            id = userId,
            displayName = actorDisplayName,
            username = actorDisplayName,
            avatarInitial = actorDisplayName.trim().firstOrNull()?.toString()?.uppercase() ?: "聊",
            avatarUrl = avatarUrl,
        ),
        text = "$prefix · 在聊天中发来了新消息",
        createdAtLabel = createdAtLabel.ifBlank { "刚刚" },
        createdAtEpochMillis = fallbackEpochMillis,
        notePreviewText = previewText,
        isSpecialCare = kind == ChatAttentionKind.SpecialCare,
        chatRoomId = roomId.takeIf { it.isNotBlank() },
        chatUserId = chatUserId,
        chatMessageId = messageId,
    )
}

private fun TimelineDensity.toNoteRowDensity(): NoteRowDensity {
    return when (this) {
        TimelineDensity.UltraCompact -> NoteRowDensity.UltraCompact
        TimelineDensity.Compact -> NoteRowDensity.Compact
        TimelineDensity.Comfortable -> NoteRowDensity.Comfortable
    }
}

private fun appRouteForRootRoute(route: RootRoute): AppRoute {
    return when (route) {
        RootRoute.Timeline -> AppRoute.Timeline
        RootRoute.Discover -> AppRoute.Discover
        RootRoute.Chat -> AppRoute.Chat
        RootRoute.Notifications -> AppRoute.Notifications
        RootRoute.Profile -> AppRoute.Profile
    }
}

private enum class DrivePickerTarget {
    Compose,
    Chat,
}

private data class UserListQuickTarget(
    val user: User,
)

private const val CHAT_ROOM_REFRESH_INTERVAL_MS = 15_000L
private const val CHAT_MESSAGE_REFRESH_INTERVAL_MS = 5_000L
private const val STREAMING_CHAT_REFRESH_INTERVAL_MS = 60_000L
private const val CHAT_STREAM_RECONNECT_DELAY_MS = 3_000L
private const val CHAT_EVENT_RECHECK_DELAY_MS = 1_500L
private const val TIMELINE_REFRESH_INTERVAL_MS = 12_000L
private const val STREAMING_TIMELINE_FALLBACK_REFRESH_INTERVAL_MS = 60_000L
private const val STREAMING_TIMELINE_REFRESH_DEBOUNCE_MS = 2_000L
private const val AUTOMATION_CHANNEL_SCAN_INTERVAL_MS = 60_000L
private const val AUTOMATION_CHANNEL_SCAN_LIMIT = 4
private const val TREND_REFRESH_INTERVAL_MS = 5_000L
private const val NOTIFICATION_REFRESH_INTERVAL_MS = 20_000L
private const val STREAMING_NOTIFICATION_FALLBACK_REFRESH_INTERVAL_MS = 30_000L
private const val MAX_AUTOMATION_SEEN_CHAT_EVENTS = 240
private const val MAX_AUTOMATION_CHAT_SOURCES = 120
private const val AUTOMATION_ROOM_SCAN_LIMIT = 8

private fun Set<String>.takeLastSet(limit: Int): Set<String> {
    if (size <= limit) return this
    return toList().takeLast(limit).toSet()
}

private fun Map<String, String>.takeLastEntries(limit: Int): Map<String, String> {
    if (size <= limit) return this
    return entries.toList().takeLast(limit).associate { it.key to it.value }
}

private fun ChatMessage.automationMessageKey(): String {
    return id.ifBlank { createdAt.ifBlank { createdAtLabel } }
}

private data class AutomationRoomScanTarget(
    val sourceId: String,
    val roomId: String,
    val roomName: String,
    val marker: String,
)

private fun List<ChatMessage>.automationMessagesAfterBaseline(baselineId: String): List<ChatMessage> {
    if (baselineId.isBlank()) return emptyList()
    val baselineIndex = indexOfLast { message -> message.automationMessageKey() == baselineId }
    return if (baselineIndex >= 0) drop(baselineIndex + 1) else listOfNotNull(lastOrNull())
}

@Composable
private fun MainShellBottomNav(
    selected: RootRoute,
    chatAvailable: Boolean,
    notificationBadgeMode: NotificationBadgeMode,
    unreadChatCount: Int,
    unreadNotificationCount: Int,
    onSelected: (RootRoute) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground),
    ) {
        HhhlBottomNav(
            selected = selected,
            onSelected = onSelected,
            routes = visibleRootRoutes(chatAvailable = chatAvailable),
            badgeCounts = if (notificationBadgeMode.showsBadges) {
                buildMap {
                    put(RootRoute.Chat, unreadChatCount)
                    put(RootRoute.Notifications, unreadNotificationCount)
                }
            } else {
                emptyMap()
            },
        )
    }
}

@Composable
private fun AuthInvalidConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录状态需要确认") },
        text = {
            Text(
                text = "服务器返回未授权。当前页面不会立刻退出登录；确认后会回到登录授权流程。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onConfirm) {
                Text("重新登录")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("稍后")
            }
        },
    )
}

private data class NoteInteractionCallbacks(
    val onOpenNote: (String) -> Unit,
    val onOpenUser: (String) -> Unit,
    val onReply: (String) -> Unit,
    val onRenote: (String) -> Unit,
    val onQuote: (String) -> Unit,
    val onReact: (String, String) -> Unit,
    val onDeleteReaction: (String, String) -> Unit,
    val onFavorite: (String) -> Unit,
    val onAddToClip: ((Note) -> Unit)?,
    val onDelete: (String) -> Unit,
    val onOpenMedia: (String) -> Unit,
    val onOpenMediaPreview: (MediaPreviewSession) -> Unit,
    val onOpenMention: (String) -> Unit,
    val onOpenHashtag: (String) -> Unit,
    val onVotePoll: (String, Int) -> Unit,
    val isActionPending: (String) -> Boolean,
    val canDeleteAuthor: (String) -> Boolean,
    val noteRowDensity: NoteRowDensity,
)

@Composable
private fun ChatRouteContent(
    state: ChatUiState,
    currentUserId: String?,
    blockedUserIds: Set<String>,
    stateHolder: ChatStateHolder,
    mediaPicker: MediaPicker?,
    onOpenUser: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMediaPreview: (MediaPreviewSession) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    customEmojis: List<cc.hhhl.client.model.CustomEmoji>,
    recentEmojiCodes: List<String>,
    customTheme: HhhlCustomTheme,
    onOpenDrivePicker: () -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    aiResultText: String?,
    aiResultLabel: String?,
    onAiAction: (AiTaskKind, ChatUiState, String) -> Unit,
    onDismissAiResult: () -> Unit,
) {
    ChatScreen(
        state = state,
        currentUserId = currentUserId,
        blockedUserIds = blockedUserIds,
        onRefresh = stateHolder::refresh,
        onLoadMore = stateHolder::loadMore,
        onOpenRoom = stateHolder::selectRoom,
        onOpenUserConversation = stateHolder::selectUserConversation,
        onToggleRoomPinned = stateHolder::toggleRoomPinned,
        onToggleUserConversationPinned = stateHolder::toggleUserConversationPinned,
        onDeleteUserConversation = stateHolder::deleteUserConversation,
        onCreateRoom = stateHolder::createRoom,
        onRefreshRoomExtras = stateHolder::refreshRoomExtras,
        onJoinRoomInvitation = stateHolder::joinInvitedRoom,
        onIgnoreRoomInvitation = stateHolder::ignoreRoomInvitation,
        onBackToRooms = stateHolder::closeRoom,
        onRefreshMessages = stateHolder::refreshMessages,
        onLoadOlderMessages = stateHolder::loadOlderMessages,
        onSearchMessages = stateHolder::searchMessages,
        onLoadMoreMessageSearch = stateHolder::loadMoreMessageSearch,
        onShowMessages = stateHolder::showMessages,
        onShowMembers = stateHolder::showMembers,
        onLoadMoreMembers = stateHolder::loadMoreMembers,
        onUpdateRoom = stateHolder::updateSelectedRoom,
        onInviteRoomMember = stateHolder::inviteSelectedRoomMember,
        onLeaveRoom = stateHolder::leaveSelectedRoom,
        onDeleteRoom = stateHolder::deleteSelectedRoom,
        onMuteRoom = stateHolder::muteSelectedRoom,
        onMessageDraftChanged = stateHolder::updateMessageDraft,
        onSendMessage = stateHolder::sendMessage,
        onQuoteMessage = stateHolder::quoteMessage,
        onReplyMessage = stateHolder::replyMessage,
        onCancelQuoteMessage = stateHolder::cancelQuotedMessage,
        onReactMessage = stateHolder::reactToMessage,
        onUnreactMessage = stateHolder::unreactToMessage,
        onDeleteMessage = stateHolder::deleteMessage,
        onCopyMessage = {},
        onReportMessage = stateHolder::reportMessage,
        onAddMedia = {
            mediaPicker?.pickImages(
                onPicked = stateHolder::uploadMedia,
                onError = stateHolder::reportMediaUploadError,
            )
        },
        onAddFile = {
            mediaPicker?.pickFiles(
                onPicked = stateHolder::uploadMedia,
                onError = stateHolder::reportMediaUploadError,
            )
        },
        onOpenDrivePicker = onOpenDrivePicker,
        onRemoveAttachedFile = stateHolder::removeAttachedFile,
        onOpenUser = onOpenUser,
        onOpenUrl = onOpenUrl,
        onOpenMediaPreview = onOpenMediaPreview,
        onOpenMention = onOpenMention,
        onOpenHashtag = onOpenHashtag,
        onOpenSpecialCareToast = stateHolder::openSpecialCareToast,
        onDismissSpecialCareToast = stateHolder::dismissSpecialCareToast,
        onSpecialCareJumpHandled = stateHolder::consumeSpecialCareJump,
        onUnreadJumpHandled = stateHolder::consumeUnreadJump,
        customEmojis = customEmojis,
        recentEmojiCodes = recentEmojiCodes,
        isMediaPickerAvailable = mediaPicker != null,
        customTheme = customTheme,
        aiEnabled = aiEnabled,
        isAiProcessing = isAiProcessing,
        aiResultText = aiResultText,
        aiResultLabel = aiResultLabel,
        onAiAction = onAiAction,
        onDismissAiResult = onDismissAiResult,
    )
}

@Composable
private fun TimelineRouteContent(
    state: TimelineUiState,
    instanceCapabilities: InstanceCapabilities,
    discoverState: DiscoverUiState,
    timelineListStates: Map<TimelineKind, LazyListState>,
    isTrendSelected: Boolean,
    noteActionState: NoteActionUiState,
    aiState: AiUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onTimelineSelected: (TimelineKind) -> Unit,
    onRefresh: (TimelineKind) -> Unit,
    onLoadMore: (TimelineKind) -> Unit,
    isSpecialCareAuthor: (String) -> Boolean,
    onTrendSelected: () -> Unit,
    onRefreshTrends: () -> Unit,
    onNewNotesMarkerConsumed: (TimelineKind) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    latestAiResultFor: (Array<out AiTaskKind>) -> AiTask?,
    onAiAction: (AiTaskKind, TimelineKind, List<Note>) -> Unit,
    onDismissAiResult: () -> Unit,
) {
    val aiResult = latestAiResultFor(
        arrayOf(
            AiTaskKind.TimelineDigest,
            AiTaskKind.TimelineReplyOpportunities,
            AiTaskKind.TimelineFilterSuggestions,
        ),
    )
    TimelineScreen(
        state = state,
        onTimelineSelected = onTimelineSelected,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        isSpecialCareAuthor = isSpecialCareAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
        capabilities = instanceCapabilities,
        listStates = timelineListStates,
        isTrendSelected = isTrendSelected,
        trends = discoverState.trends,
        isRefreshingTrends = discoverState.isRefreshingTrends,
        trendErrorMessage = discoverState.trendErrorMessage,
        onTrendSelected = onTrendSelected,
        onRefreshTrends = onRefreshTrends,
        onNewNotesMarkerConsumed = onNewNotesMarkerConsumed,
        onCompose = onCompose,
        onSearch = onSearch,
        aiEnabled = aiState.hasUsableModel,
        isAiProcessing = aiState.isProcessing,
        aiResultText = aiResult?.resultText,
        aiResultLabel = aiResult?.kind?.label,
        onAiAction = onAiAction,
        onDismissAiResult = onDismissAiResult,
    )
}

@Composable
private fun ProfileRouteContent(
    state: UserProfileUiState,
    capabilities: InstanceCapabilities,
    noteActionState: NoteActionUiState,
    aiState: AiUiState,
    noteCallbacks: NoteInteractionCallbacks,
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    onRefresh: () -> Unit,
    onLoadMoreNotes: () -> Unit,
    latestAiResultFor: (Array<out AiTaskKind>) -> AiTask?,
    onAiProfileSummary: () -> Unit,
    onAiProfileSuggestions: () -> Unit,
    onDismissAiResult: () -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onChangeBanner: () -> Unit,
    onLogout: () -> Unit,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onClearMessage: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenFavoriteNotes: () -> Unit,
    onOpenLists: () -> Unit,
    onOpenFollowRequests: () -> Unit,
    onOpenRelationshipManagement: () -> Unit,
    onOpenAntennas: () -> Unit,
    onOpenClips: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenProfileNotes: () -> Unit,
    onOpenSocial: (UserSocialKind) -> Unit,
) {
    val aiResult = latestAiResultFor(
        arrayOf(AiTaskKind.ProfileSummary, AiTaskKind.ProfileInteractionSuggestions),
    )
    ProfileScreen(
        state = state,
        capabilities = capabilities,
        onRefresh = onRefresh,
        onLoadMoreNotes = onLoadMoreNotes,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
        aiEnabled = aiState.hasUsableModel,
        isAiProcessing = aiState.isProcessing,
        aiResultText = aiResult?.resultText,
        aiResultLabel = aiResult?.kind?.label,
        onAiProfileSummary = onAiProfileSummary,
        onAiProfileSuggestions = onAiProfileSuggestions,
        onDismissAiResult = onDismissAiResult,
        selectedTheme = selectedTheme,
        selectedTimelineDensity = selectedTimelineDensity,
        onUpdateProfile = onUpdateProfile,
        onChangeBanner = onChangeBanner,
        onLogout = onLogout,
        onThemeSelected = onThemeSelected,
        onTimelineDensitySelected = onTimelineDensitySelected,
        onClearMessage = onClearMessage,
        onOpenDrive = onOpenDrive,
        onOpenAchievements = onOpenAchievements,
        onOpenSettings = onOpenSettings,
        onOpenAutomation = onOpenAutomation,
        onOpenFavoriteNotes = onOpenFavoriteNotes,
        onOpenLists = onOpenLists,
        onOpenFollowRequests = onOpenFollowRequests,
        onOpenRelationshipManagement = onOpenRelationshipManagement,
        onOpenAntennas = onOpenAntennas,
        onOpenClips = onOpenClips,
        onOpenChannels = onOpenChannels,
        onOpenPages = onOpenPages,
        onOpenGallery = onOpenGallery,
        onOpenFlash = onOpenFlash,
        onOpenAnnouncements = onOpenAnnouncements,
        onOpenProfileNotes = onOpenProfileNotes,
        onOpenSocial = onOpenSocial,
    )
}

@Composable
private fun ViewedProfileRouteContent(
    state: UserProfileUiState,
    noteActionState: NoteActionUiState,
    aiState: AiUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onRefresh: () -> Unit,
    onLoadMoreNotes: () -> Unit,
    onBack: () -> Unit,
    latestAiResultFor: (Array<out AiTaskKind>) -> AiTask?,
    onAiProfileSummary: () -> Unit,
    onAiProfileSuggestions: () -> Unit,
    onDismissAiResult: () -> Unit,
    onFollowToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onBlockToggle: () -> Unit,
    onOpenChatWithUser: (User) -> Unit,
    isSpecialCareUser: (String) -> Boolean,
    onToggleSpecialCareUser: (String) -> Boolean,
    onOpenSocial: (UserSocialKind) -> Unit,
) {
    val aiResult = latestAiResultFor(
        arrayOf(AiTaskKind.ProfileSummary, AiTaskKind.ProfileInteractionSuggestions),
    )
    ProfileScreen(
        state = state,
        onRefresh = onRefresh,
        onLoadMoreNotes = onLoadMoreNotes,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onBack = onBack,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
        title = "资料",
        isOwnProfile = false,
        aiEnabled = aiState.hasUsableModel,
        isAiProcessing = aiState.isProcessing,
        aiResultText = aiResult?.resultText,
        aiResultLabel = aiResult?.kind?.label,
        onAiProfileSummary = onAiProfileSummary,
        onAiProfileSuggestions = onAiProfileSuggestions,
        onDismissAiResult = onDismissAiResult,
        onFollowToggle = onFollowToggle,
        onMuteToggle = onMuteToggle,
        onBlockToggle = onBlockToggle,
        onOpenChatWithUser = onOpenChatWithUser,
        isSpecialCareUser = isSpecialCareUser,
        onToggleSpecialCareUser = onToggleSpecialCareUser,
        onOpenSocial = onOpenSocial,
    )
}

@Composable
private fun AutomationRouteContent(
    state: AutomationUiState,
    aiState: AiUiState,
    onBack: () -> Unit,
    onCreateRule: (AutomationTrigger) -> Unit,
    onOpenRule: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onToggleRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onDuplicateRule: (String) -> Unit,
    onUpdateRuleName: (String, String) -> Unit,
    onUpdateRuleTrigger: (String, AutomationTrigger) -> Unit,
    onUpdateConditionMode: (String, AutomationConditionMode) -> Unit,
    onUpdateActionMode: (String, AutomationActionMode) -> Unit,
    onUpdateIgnoreOwnMessages: (String, Boolean) -> Unit,
    onUpdateCooldown: (String, Int) -> Unit,
    onAddCondition: (String, AutomationConditionType) -> Unit,
    onUpdateCondition: (String, AutomationCondition) -> Unit,
    onRemoveCondition: (String, String) -> Unit,
    onAddAction: (String, AutomationActionType) -> Unit,
    onUpdateAction: (String, AutomationAction) -> Unit,
    onRemoveAction: (String, String) -> Unit,
    onClearLogs: () -> Unit,
    latestAiResultFor: (Array<out AiTaskKind>) -> AiTask?,
    onAiExplainRule: (AutomationRule?) -> Unit,
    onAiSuggestRules: (AutomationUiState) -> Unit,
    onAiCreateRule: (String, AutomationUiState) -> Unit,
    onDismissAiResult: () -> Unit,
) {
    val aiResult = latestAiResultFor(
        arrayOf(
            AiTaskKind.AutomationExplain,
            AiTaskKind.AutomationRuleSuggestions,
            AiTaskKind.AutomationRuleDraft,
        ),
    )
    AutomationScreen(
        state = state,
        onBack = onBack,
        onCreateRule = onCreateRule,
        onOpenRule = onOpenRule,
        onCloseEditor = onCloseEditor,
        onToggleRule = onToggleRule,
        onDeleteRule = onDeleteRule,
        onDuplicateRule = onDuplicateRule,
        onUpdateRuleName = onUpdateRuleName,
        onUpdateRuleTrigger = onUpdateRuleTrigger,
        onUpdateConditionMode = onUpdateConditionMode,
        onUpdateActionMode = onUpdateActionMode,
        onUpdateIgnoreOwnMessages = onUpdateIgnoreOwnMessages,
        onUpdateCooldown = onUpdateCooldown,
        onAddCondition = onAddCondition,
        onUpdateCondition = onUpdateCondition,
        onRemoveCondition = onRemoveCondition,
        onAddAction = onAddAction,
        onUpdateAction = onUpdateAction,
        onRemoveAction = onRemoveAction,
        onClearLogs = onClearLogs,
        aiEnabled = aiState.hasUsableModel,
        isAiProcessing = aiState.isProcessing,
        aiResultText = aiResult?.resultText,
        aiResultLabel = aiResult?.kind?.label,
        onAiExplainRule = onAiExplainRule,
        onAiSuggestRules = onAiSuggestRules,
        onAiCreateRule = onAiCreateRule,
        onDismissAiResult = onDismissAiResult,
    )
}

@Composable
private fun NoteDetailRouteContent(
    noteId: String,
    state: NoteDetailUiState,
    noteActionState: NoteActionUiState,
    aiState: AiUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onRefresh: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    onLoadConversation: () -> Unit,
    onLoadRenotes: () -> Unit,
    onLoadReactionUsers: () -> Unit,
    onLoadVersions: () -> Unit,
    onRefreshPollRecommendation: () -> Unit,
    onTranslate: () -> Unit,
    latestAiResultFor: (Array<out AiTaskKind>) -> AiTask?,
    onAiThreadSummary: (NoteDetailUiState) -> Unit,
    onAiThreadReplyDraft: (NoteDetailUiState) -> Unit,
    onDismissAiResult: () -> Unit,
    onToggleChildReplies: (String) -> Unit,
    onBack: () -> Unit,
) {
    val aiResult = latestAiResultFor(arrayOf(AiTaskKind.ThreadSummary, AiTaskKind.ThreadReplyDraft))
    NoteDetailScreen(
        noteId = noteId,
        state = state,
        onRefresh = onRefresh,
        onLoadMoreReplies = onLoadMoreReplies,
        onLoadConversation = onLoadConversation,
        onLoadRenotes = onLoadRenotes,
        onLoadReactionUsers = onLoadReactionUsers,
        onLoadVersions = onLoadVersions,
        onRefreshPollRecommendation = onRefreshPollRecommendation,
        onTranslate = onTranslate,
        aiEnabled = aiState.hasUsableModel,
        isAiProcessing = aiState.isProcessing,
        aiResultText = aiResult?.resultText,
        aiResultLabel = aiResult?.kind?.label,
        onAiThreadSummary = onAiThreadSummary,
        onAiThreadReplyDraft = onAiThreadReplyDraft,
        onDismissAiResult = onDismissAiResult,
        onToggleChildReplies = onToggleChildReplies,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
        onBack = onBack,
    )
}

@Composable
private fun ComposeRouteContent(
    state: ComposeUiState,
    noteActionState: NoteActionUiState,
    completionState: ComposeCompletionUiState,
    aiState: AiUiState,
    targetNote: Note?,
    isMediaPickerAvailable: Boolean,
    onTextChanged: (String) -> Unit,
    onCwChanged: (String?) -> Unit,
    onVisibilitySelected: (NoteVisibility) -> Unit,
    onLocalOnlyChanged: (Boolean) -> Unit,
    onReactionAcceptanceSelected: (ComposeReactionAcceptance) -> Unit,
    onScheduleAtChanged: (Long?) -> Unit,
    onInsertText: (String) -> Unit,
    onResetDraft: () -> Unit,
    onLoadScheduledNotes: () -> Unit,
    onEditScheduledNote: (ComposeScheduledNote) -> Unit,
    onDeleteScheduledNote: (String) -> Unit,
    onVisibleUserIdsChanged: (String) -> Unit,
    onResolveVisibleUserMentions: () -> Unit,
    onPollEnabledChanged: (Boolean) -> Unit,
    onPollChoiceChanged: (Int, String) -> Unit,
    onPollMultipleChanged: (Boolean) -> Unit,
    onPollExpiresAtChanged: (String) -> Unit,
    onPollDeadlinePresetSelected: (ComposePollDeadlinePreset, Long) -> Unit,
    onPollChoiceAdded: () -> Unit,
    onPollChoiceRemoved: (Int) -> Unit,
    onAddMedia: () -> Unit,
    onOpenDrivePicker: () -> Unit,
    onRemoveFileId: (String) -> Unit,
    onAttachedFileMetadataChanged: (String, String?, Boolean) -> Unit,
    onCompletionTokenChanged: (ComposeCompletionKind?, String) -> Unit,
    onSend: () -> Unit,
    onRetryFailedSend: (String) -> Unit,
    onRestoreFailedSend: (String) -> Unit,
    onRemoveFailedSend: (String) -> Unit,
    latestAiResultFor: (Array<out AiTaskKind>) -> AiTask?,
    onAiAction: (AiTaskKind, ComposeDraft, Note?) -> Unit,
    onDismissAiResult: () -> Unit,
    onBack: () -> Unit,
) {
    val aiResult = latestAiResultFor(
        arrayOf(
            AiTaskKind.ComposePolish,
            AiTaskKind.ComposeShorten,
            AiTaskKind.ComposeExpand,
            AiTaskKind.ComposeTranslateZh,
            AiTaskKind.ComposeContentWarning,
            AiTaskKind.ComposeHashtags,
            AiTaskKind.ComposeMentionSuggestions,
            AiTaskKind.ComposeFromRecentPosts,
            AiTaskKind.PostReplyDraft,
            AiTaskKind.ThreadReplyDraft,
        ),
    )
    ComposeScreen(
        state = state,
        targetNote = targetNote,
        onTextChanged = onTextChanged,
        onCwChanged = onCwChanged,
        onVisibilitySelected = onVisibilitySelected,
        onLocalOnlyChanged = onLocalOnlyChanged,
        onReactionAcceptanceSelected = onReactionAcceptanceSelected,
        onScheduleAtChanged = onScheduleAtChanged,
        onInsertText = onInsertText,
        onResetDraft = onResetDraft,
        onLoadScheduledNotes = onLoadScheduledNotes,
        onEditScheduledNote = onEditScheduledNote,
        onDeleteScheduledNote = onDeleteScheduledNote,
        onVisibleUserIdsChanged = onVisibleUserIdsChanged,
        onResolveVisibleUserMentions = onResolveVisibleUserMentions,
        onPollEnabledChanged = onPollEnabledChanged,
        onPollChoiceChanged = onPollChoiceChanged,
        onPollMultipleChanged = onPollMultipleChanged,
        onPollExpiresAtChanged = onPollExpiresAtChanged,
        onPollDeadlinePresetSelected = onPollDeadlinePresetSelected,
        onPollChoiceAdded = onPollChoiceAdded,
        onPollChoiceRemoved = onPollChoiceRemoved,
        onAddMedia = onAddMedia,
        onOpenDrivePicker = onOpenDrivePicker,
        onRemoveFileId = onRemoveFileId,
        onAttachedFileMetadataChanged = onAttachedFileMetadataChanged,
        isMediaPickerAvailable = isMediaPickerAvailable,
        customEmojis = noteActionState.customEmojis,
        recentEmojiCodes = noteActionState.recentReactions,
        completionState = completionState,
        onCompletionTokenChanged = onCompletionTokenChanged,
        onSend = onSend,
        onRetryFailedSend = onRetryFailedSend,
        onRestoreFailedSend = onRestoreFailedSend,
        onRemoveFailedSend = onRemoveFailedSend,
        aiEnabled = aiState.hasUsableModel,
        aiResultText = aiResult?.resultText,
        aiResultLabel = aiResult?.kind?.label,
        isAiProcessing = aiState.isProcessing,
        onAiAction = onAiAction,
        onDismissAiResult = onDismissAiResult,
        onBack = onBack,
    )
}

@Composable
private fun DriveRouteContent(
    state: DriveFilesUiState,
    stateHolder: DriveFilesStateHolder,
    mediaPicker: MediaPicker?,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMediaPreview: (MediaPreviewSession) -> Unit,
    isPickerMode: Boolean = false,
    onPickFile: (cc.hhhl.client.model.DriveFile) -> Unit = {},
) {
    DriveScreen(
        state = state,
        onBack = onBack,
        onRefresh = stateHolder::refresh,
        onLoadMore = stateHolder::loadMore,
        onLoadMoreFolders = stateHolder::loadMoreFolders,
        onQueryChanged = stateHolder::updateSearchQuery,
        onSearch = stateHolder::search,
        onStreamModeChanged = stateHolder::setStreamMode,
        onSortSelected = stateHolder::selectSort,
        onTypeFilterSelected = stateHolder::selectTypeFilter,
        onUpload = {
            mediaPicker?.pickMedia(
                mimeType = "*/*",
                onPicked = stateHolder::upload,
                onError = stateHolder::reportUploadError,
            )
        },
        onCreateFolder = stateHolder::createFolder,
        onOpenFile = { file ->
            file.url?.let(onOpenUrl)
        },
        onSelectFile = stateHolder::selectFile,
        onPickFile = onPickFile,
        onCloseFileDetails = stateHolder::clearSelectedFile,
        onOpenNote = onOpenNote,
        onOpenMediaPreview = onOpenMediaPreview,
        onOpenFolder = stateHolder::openFolder,
        onNavigateUp = stateHolder::navigateUp,
        onNavigateToPathIndex = stateHolder::navigateToPathIndex,
        onRenameFile = { file, name ->
            stateHolder.updateFile(
                file = file,
                name = name,
                comment = file.comment,
                isSensitive = file.isSensitive,
            )
        },
        onToggleFileSensitive = { file ->
            stateHolder.updateFile(
                file = file,
                name = file.name,
                comment = file.comment,
                isSensitive = !file.isSensitive,
            )
        },
        onMoveFileToRoot = { file ->
            stateHolder.moveFile(
                file = file,
                folderId = null,
            )
        },
        onDeleteFile = { file ->
            stateHolder.deleteFile(file.id)
        },
        onRenameFolder = { folder, name ->
            stateHolder.updateFolder(folder = folder, name = name)
        },
        onDeleteFolder = { folder ->
            stateHolder.deleteFolder(folder.id)
        },
        isMediaPickerAvailable = mediaPicker != null,
        isPickerMode = isPickerMode,
    )
}

@Composable
private fun SettingsRouteContent(
    state: SettingsUiState,
    stateHolder: SettingsStateHolder,
    instanceMetaState: cc.hhhl.client.state.InstanceMetaUiState,
    accounts: List<AccountSession>,
    currentAccountId: String?,
    onBack: () -> Unit,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    customTheme: HhhlCustomTheme,
    onCustomThemeChanged: (HhhlCustomTheme) -> Unit,
    onResetCustomTheme: () -> Unit,
    onPickGlobalBackgroundImage: () -> Unit,
    onClearGlobalBackgroundImage: () -> Unit,
    onPickChatBackgroundImage: () -> Unit,
    onClearChatBackgroundImage: () -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onListGesturesEnabledChanged: (Boolean) -> Unit,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
    onBackgroundNotificationsChanged: (Boolean) -> Unit,
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit,
    onCheckForUpdates: (((String) -> Unit) -> Unit),
    onClearChatMessageCache: () -> Unit,
    onOpenThemeCustomization: () -> Unit,
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit),
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenWebSettings: (SettingsItemKey) -> Unit,
    onOpenManagement: (cc.hhhl.client.model.SettingsManagementSectionKey) -> Unit,
    onAiSettingsChanged: (AiSettings) -> Unit,
    onAiProviderSelected: (AiProviderPreset) -> Unit,
    onTestAiConnection: () -> Unit,
    onAiWorkspacePlan: () -> Unit,
    aiConnectionMessage: String?,
    isTestingAiConnection: Boolean,
) {
    SettingsScreen(
        state = state,
        instanceMeta = instanceMetaState.meta,
        isInstanceMetaLoading = instanceMetaState.isLoading,
        onBack = onBack,
        onThemeSelected = onThemeSelected,
        customTheme = customTheme,
        onCustomThemeChanged = onCustomThemeChanged,
        onResetCustomTheme = onResetCustomTheme,
        onPickGlobalBackgroundImage = onPickGlobalBackgroundImage,
        onClearGlobalBackgroundImage = onClearGlobalBackgroundImage,
        onPickChatBackgroundImage = onPickChatBackgroundImage,
        onClearChatBackgroundImage = onClearChatBackgroundImage,
        onTimelineDensitySelected = onTimelineDensitySelected,
        onListGesturesEnabledChanged = onListGesturesEnabledChanged,
        onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
        onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
        onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
        onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
        onCheckForUpdates = onCheckForUpdates,
        onClearChatMessageCache = onClearChatMessageCache,
        onOpenThemeCustomization = onOpenThemeCustomization,
        accounts = accounts,
        currentAccountId = currentAccountId,
        onSwitchAccount = onSwitchAccount,
        onRemoveAccount = onRemoveAccount,
        onAddAccount = onAddAccount,
        onRefreshRemote = stateHolder::refreshRemote,
        onPrivacyToggle = stateHolder::togglePrivacy,
        onNotificationTypeToggle = stateHolder::toggleNotificationType,
        onMutedWordDraftChanged = stateHolder::updateMutedWordDraft,
        onAddMutedWord = stateHolder::addMutedWord,
        onRemoveMutedWord = stateHolder::removeMutedWord,
        onHardMutedWordDraftChanged = stateHolder::updateHardMutedWordDraft,
        onAddHardMutedWord = stateHolder::addHardMutedWord,
        onRemoveHardMutedWord = stateHolder::removeHardMutedWord,
        onMutedInstanceDraftChanged = stateHolder::updateMutedInstanceDraft,
        onAddMutedInstance = stateHolder::addMutedInstance,
        onRemoveMutedInstance = stateHolder::removeMutedInstance,
        onOpenAdminDashboard = onOpenAdminDashboard,
        onOpenWebSettings = onOpenWebSettings,
        onOpenManagement = onOpenManagement,
        onAiSettingsChanged = onAiSettingsChanged,
        onAiProviderSelected = onAiProviderSelected,
        onTestAiConnection = onTestAiConnection,
        onAiWorkspacePlan = onAiWorkspacePlan,
        aiConnectionMessage = aiConnectionMessage,
        isTestingAiConnection = isTestingAiConnection,
    )
}

@Composable
private fun NotificationsRouteContent(
    state: NotificationUiState,
    announcementState: AnnouncementUiState,
    followRequestState: FollowRequestUiState,
    aiState: AiUiState,
    notificationListState: LazyListState,
    announcementListState: LazyListState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onMarkAllAsRead: () -> Unit,
    onFlush: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onFilterSelected: (NotificationFilter) -> Unit,
    onRefreshAnnouncements: () -> Unit,
    onLoadMoreAnnouncements: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onCloseAnnouncement: () -> Unit,
    onMarkAnnouncementRead: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReplyToNote: (String) -> Unit,
    onReactToNote: (String, String) -> Unit,
    onFollowUser: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onAcceptFollowRequest: (String) -> Unit,
    onRejectFollowRequest: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenChatUser: (String, String?) -> Unit,
    onSendTestNotification: () -> Unit,
    onSendReminderNotification: () -> Unit,
    latestAiResultFor: (Array<out AiTaskKind>) -> AiTask?,
    onAiAction: (AiTaskKind, List<NotificationItem>, NotificationFilter) -> Unit,
    onDismissAiResult: () -> Unit,
) {
    val aiResult = latestAiResultFor(
        arrayOf(
            AiTaskKind.NotificationSummary,
            AiTaskKind.NotificationFollowUp,
            AiTaskKind.NotificationPriority,
        ),
    )
    NotificationsScreen(
        state = state,
        announcementState = announcementState,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
        onMarkAllAsRead = onMarkAllAsRead,
        onFlush = onFlush,
        onMarkNotificationRead = onMarkNotificationRead,
        onFilterSelected = onFilterSelected,
        onRefreshAnnouncements = onRefreshAnnouncements,
        onLoadMoreAnnouncements = onLoadMoreAnnouncements,
        onOpenAnnouncement = onOpenAnnouncement,
        onCloseAnnouncement = onCloseAnnouncement,
        onMarkAnnouncementRead = onMarkAnnouncementRead,
        onOpenNote = onOpenNote,
        onOpenUser = onOpenUser,
        onReplyToNote = onReplyToNote,
        onReactToNote = onReactToNote,
        onFollowUser = onFollowUser,
        onOpenUrl = onOpenUrl,
        onOpenMention = onOpenMention,
        onOpenHashtag = onOpenHashtag,
        pendingFollowRequestUserIds = followRequestState.pendingUserIds,
        onAcceptFollowRequest = onAcceptFollowRequest,
        onRejectFollowRequest = onRejectFollowRequest,
        onOpenChat = onOpenChat,
        onOpenChatUser = onOpenChatUser,
        onSendTestNotification = onSendTestNotification,
        onSendReminderNotification = onSendReminderNotification,
        aiEnabled = aiState.hasUsableModel,
        isAiProcessing = aiState.isProcessing,
        aiResultText = aiResult?.resultText,
        aiResultLabel = aiResult?.kind?.label,
        onAiAction = onAiAction,
        onDismissAiResult = onDismissAiResult,
        notificationListState = notificationListState,
        announcementListState = announcementListState,
    )
}

@Composable
private fun RelationshipManagementRouteContent(
    state: RelationshipManagementUiState,
    stateHolder: RelationshipManagementStateHolder,
    onRemoveSpecialCareUser: (String) -> Unit,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    RelationshipManagementScreen(
        state = state,
        onBack = onBack,
        onRefresh = stateHolder::refresh,
        onLoadMore = stateHolder::loadMore,
        onTabSelected = stateHolder::selectTab,
        onOpenUser = onOpenUser,
        onRemoveRelationship = { userId ->
            if (state.selectedTab == RelationshipManagementTab.SpecialCare) {
                onRemoveSpecialCareUser(userId)
            }
            stateHolder.removeRelationship(userId)
        },
        onUpdateAllFollowing = stateHolder::updateAllFollowing,
    )
}

@Composable
private fun DiscoverRouteContent(
    state: DiscoverUiState,
    stateHolder: DiscoverStateHolder,
    noteActionState: NoteActionUiState,
    noteCallbacks: NoteInteractionCallbacks,
    listState: LazyListState,
    onOpenChannels: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
) {
    DiscoverScreen(
        state = state,
        onQueryChanged = stateHolder::updateQuery,
        onSearch = stateHolder::search,
        onModeSelected = stateHolder::selectMode,
        onFiltersChanged = stateHolder::updateFilters,
        onClearFilters = stateHolder::clearFilters,
        onLoadMore = stateHolder::loadMore,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        onOpenChannels = onOpenChannels,
        onOpenPages = onOpenPages,
        onOpenGallery = onOpenGallery,
        onOpenFlash = onOpenFlash,
        onOpenAnnouncements = onOpenAnnouncements,
        onOpenFederationInstance = stateHolder::openFederationInstance,
        onCloseFederationInstanceDetails = stateHolder::closeFederationInstance,
        onToggleFederationSilence = stateHolder::toggleFederationSilence,
        onToggleFederationBlock = stateHolder::toggleFederationBlock,
        onOpenRole = stateHolder::openRole,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
        listState = listState,
    )
}

@Composable
private fun FavoriteNotesRouteContent(
    state: FavoriteNoteUiState,
    stateHolder: FavoriteNoteStateHolder,
    noteActionState: NoteActionUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onBack: () -> Unit,
) {
    FavoriteNoteScreen(
        state = state,
        onBack = onBack,
        onRefresh = stateHolder::refresh,
        onLoadMore = stateHolder::loadMore,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
    )
}

@Composable
private fun UserListsRouteContent(
    state: UserListUiState,
    stateHolder: UserListStateHolder,
    noteActionState: NoteActionUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onBack: () -> Unit,
) {
    UserListScreen(
        state = state,
        onBack = onBack,
        onRefreshLists = stateHolder::refreshLists,
        onRefreshTimeline = stateHolder::refreshTimeline,
        onCreateList = stateHolder::createList,
        onUpdateSelectedList = stateHolder::updateSelectedList,
        onDeleteSelectedList = stateHolder::deleteSelectedList,
        onAddUserToSelectedList = stateHolder::addUserToSelectedList,
        onRemoveUserFromSelectedList = stateHolder::removeUserFromSelectedList,
        onSelectList = stateHolder::selectList,
        onLoadMore = stateHolder::loadMore,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
    )
}

@Composable
private fun AntennasRouteContent(
    state: AntennaUiState,
    stateHolder: AntennaStateHolder,
    noteActionState: NoteActionUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onBack: () -> Unit,
) {
    AntennaScreen(
        state = state,
        onBack = onBack,
        onRefreshAntennas = stateHolder::refreshAntennas,
        onRefreshNotes = stateHolder::refreshNotes,
        onCreateAntenna = stateHolder::createAntenna,
        onUpdateSelectedAntenna = stateHolder::updateSelectedAntenna,
        onDeleteSelectedAntenna = stateHolder::deleteSelectedAntenna,
        onSelectAntenna = stateHolder::selectAntenna,
        onLoadMore = stateHolder::loadMore,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
    )
}

@Composable
private fun ClipsRouteContent(
    state: ClipUiState,
    stateHolder: ClipStateHolder,
    noteActionState: NoteActionUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onBack: () -> Unit,
) {
    ClipScreen(
        state = state,
        onBack = onBack,
        onRefreshClips = { stateHolder.refreshClips() },
        onRefreshNotes = stateHolder::refreshNotes,
        onCreateClip = stateHolder::createClip,
        onUpdateSelectedClip = stateHolder::updateSelectedClip,
        onDeleteSelectedClip = stateHolder::deleteSelectedClip,
        onKindSelected = stateHolder::selectKind,
        onSelectClip = stateHolder::selectClip,
        onToggleFavoriteClip = stateHolder::toggleFavoriteSelectedClip,
        onRemoveNoteFromClip = stateHolder::removeNoteFromSelectedClip,
        onLoadMore = stateHolder::loadMore,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
    )
}

@Composable
private fun ChannelsRouteContent(
    state: ChannelUiState,
    stateHolder: ChannelStateHolder,
    noteActionState: NoteActionUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onBack: () -> Unit,
    onComposeInChannel: (Channel) -> Unit,
) {
    ChannelScreen(
        state = state,
        onBack = onBack,
        onRefreshChannels = { stateHolder.refreshChannels() },
        onRefreshTimeline = stateHolder::refreshTimeline,
        onKindSelected = stateHolder::selectKind,
        onSelectChannel = stateHolder::selectChannel,
        onToggleFollowChannel = stateHolder::toggleFollowSelectedChannel,
        onToggleFavoriteChannel = stateHolder::toggleFavoriteSelectedChannel,
        onCreateChannel = stateHolder::createChannel,
        onUpdateSelectedChannel = stateHolder::updateSelectedChannel,
        onArchiveSelectedChannel = stateHolder::archiveSelectedChannel,
        onComposeInChannel = onComposeInChannel,
        onLoadMore = stateHolder::loadMore,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
    )
}

@Composable
private fun ProfileNotesRouteContent(
    state: UserProfileUiState,
    noteActionState: NoteActionUiState,
    noteCallbacks: NoteInteractionCallbacks,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreNotes: () -> Unit,
) {
    ProfileNotesScreen(
        state = state,
        onBack = onBack,
        onRefresh = onRefresh,
        onLoadMoreNotes = onLoadMoreNotes,
        onOpenNote = noteCallbacks.onOpenNote,
        onOpenUser = noteCallbacks.onOpenUser,
        onReply = noteCallbacks.onReply,
        onRenote = noteCallbacks.onRenote,
        onQuote = noteCallbacks.onQuote,
        onReact = noteCallbacks.onReact,
        onDeleteReaction = noteCallbacks.onDeleteReaction,
        onFavorite = noteCallbacks.onFavorite,
        onAddToClip = noteCallbacks.onAddToClip,
        onDelete = noteCallbacks.onDelete,
        onOpenMedia = noteCallbacks.onOpenMedia,
        onOpenMediaPreview = noteCallbacks.onOpenMediaPreview,
        onOpenMention = noteCallbacks.onOpenMention,
        onOpenHashtag = noteCallbacks.onOpenHashtag,
        onVotePoll = noteCallbacks.onVotePoll,
        reactionOptions = noteActionState.reactionOptions,
        recentReactions = noteActionState.recentReactions,
        isActionPending = noteCallbacks.isActionPending,
        canDeleteAuthor = noteCallbacks.canDeleteAuthor,
        noteRowDensity = noteCallbacks.noteRowDensity,
    )
}

@Composable
private fun PagesRouteContent(
    state: PageUiState,
    stateHolder: PageStateHolder,
    currentUserId: String?,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    PageScreen(
        state = state,
        onBack = onBack,
        onRefreshPages = { stateHolder.refreshPages() },
        onKindSelected = stateHolder::selectKind,
        onOpenPage = stateHolder::openPage,
        onCloseDetail = stateHolder::closeDetail,
        onToggleLikePage = stateHolder::toggleLikeSelectedPage,
        onStartCreatingPage = stateHolder::startCreatingPage,
        onStartEditingPage = stateHolder::startEditingSelectedPage,
        onCancelEditingPage = stateHolder::cancelEditingPage,
        onDraftChanged = stateHolder::updateDraft,
        onSavePage = stateHolder::saveEditingPage,
        onDeletePage = stateHolder::deleteSelectedPage,
        onLoadMore = stateHolder::loadMore,
        onOpenUser = onOpenUser,
        currentUserId = currentUserId,
    )
}

@Composable
private fun GalleryRouteContent(
    state: GalleryUiState,
    stateHolder: GalleryStateHolder,
    currentUserId: String?,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: (MediaPreviewSession) -> Unit,
) {
    GalleryScreen(
        state = state,
        onBack = onBack,
        onRefreshPosts = { stateHolder.refreshPosts() },
        onKindSelected = stateHolder::selectKind,
        onOpenPost = stateHolder::openPost,
        onCloseDetail = stateHolder::closeDetail,
        onToggleLikePost = stateHolder::toggleLikeSelectedPost,
        onCreatePost = stateHolder::createPost,
        onUpdatePost = stateHolder::updateSelectedPost,
        onDeletePost = stateHolder::deleteSelectedPost,
        onLoadMore = stateHolder::loadMore,
        onOpenUser = onOpenUser,
        onOpenMedia = onOpenMedia,
        onOpenMediaPreview = onOpenMediaPreview,
        currentUserId = currentUserId,
    )
}

@Composable
private fun FlashRouteContent(
    state: FlashUiState,
    stateHolder: FlashStateHolder,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenFlashInWeb: (String) -> Unit,
) {
    FlashScreen(
        state = state,
        onBack = onBack,
        onRefreshFlashes = { stateHolder.refreshFlashes() },
        onKindSelected = stateHolder::selectKind,
        onOpenFlash = stateHolder::openFlash,
        onCloseDetail = stateHolder::closeDetail,
        onToggleLikeFlash = stateHolder::toggleLikeSelectedFlash,
        onStartCreateFlash = stateHolder::startCreatingFlash,
        onStartEditFlash = stateHolder::startEditingSelectedFlash,
        onDraftChanged = stateHolder::updateDraft,
        onSaveDraft = stateHolder::saveDraft,
        onCancelDraft = stateHolder::cancelDraft,
        onDeleteFlash = stateHolder::deleteSelectedFlash,
        onLoadMore = stateHolder::loadMore,
        onOpenUser = onOpenUser,
        onOpenFlashInWeb = onOpenFlashInWeb,
    )
}

@Composable
private fun AnnouncementsRouteContent(
    state: AnnouncementUiState,
    stateHolder: AnnouncementStateHolder,
    onBack: () -> Unit,
) {
    AnnouncementScreen(
        state = state,
        onBack = onBack,
        onRefresh = { stateHolder.refresh() },
        onOpenAnnouncement = stateHolder::openAnnouncement,
        onCloseDetail = stateHolder::closeDetail,
        onLoadMore = stateHolder::loadMore,
        onMarkRead = stateHolder::markRead,
        onEnterManagement = stateHolder::enterManagement,
        onExitManagement = stateHolder::exitManagement,
        onRefreshAdmin = stateHolder::refreshAdmin,
        onCreateAnnouncement = stateHolder::createAnnouncement,
        onUpdateAnnouncement = stateHolder::updateAnnouncement,
        onDeleteAnnouncement = stateHolder::deleteAnnouncement,
    )
}

@Composable
private fun ClipTargetDialog(
    note: Note,
    clips: List<Clip>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenClips: () -> Unit,
    onSelectClip: (Clip) -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到剪辑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InlineRichText(
                    text = notePreviewText(note, fallback = "这条动态"),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxChars = 96,
                )
                when {
                    isLoading -> Text("正在加载我的剪辑...")
                    errorMessage != null -> Text(errorMessage)
                    clips.isEmpty() -> Text("还没有可添加的剪辑")
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(clips, key = { it.id }) { clip ->
                            HhhlTextButton(
                                onClick = { onSelectClip(clip) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = clip.name,
                                        color = colors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "${clip.visibilityLabel} · ${clip.notesCount} 条动态",
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = if (errorMessage != null || clips.isEmpty()) onRefresh else onOpenClips,
            ) {
                Text(if (errorMessage != null) "重试" else "管理剪辑")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MainShell(
    sessionToken: String?,
    accountUser: AuthenticatedUser?,
    accounts: List<AccountSession>,
    currentAccountId: String?,
    openUrl: (String) -> Unit,
    shareUrl: (String) -> Unit,
    downloadUrl: (String, String, String) -> Unit,
    mediaPicker: MediaPicker?,
    timelineCache: TimelineCache?,
    recentReactionStore: RecentReactionStore,
    specialCareStore: SpecialCareStore,
    automationStore: AutomationStore,
    aiStore: AiStore,
    composeDraftStore: ComposeDraftStore,
    chatMessageCache: ChatMessageCache,
    chatUnreadStore: ChatUnreadStore,
    notificationCache: NotificationCache,
    notificationReadStore: NotificationReadStore,
    selectedTheme: HhhlThemePreset,
    customTheme: HhhlCustomTheme,
    onCustomThemeChanged: (HhhlCustomTheme) -> Unit,
    onResetCustomTheme: () -> Unit,
    onSetGlobalBackgroundImage: (cc.hhhl.client.api.DriveFileUpload) -> Unit,
    onClearGlobalBackgroundImage: () -> Unit,
    onSetChatBackgroundImage: (cc.hhhl.client.api.DriveFileUpload) -> Unit,
    onClearChatBackgroundImage: () -> Unit,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    selectedTimelineDensity: TimelineDensity,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    listGesturesEnabled: Boolean,
    onListGesturesEnabledChanged: (Boolean) -> Unit,
    selectedDefaultNoteVisibility: DefaultNoteVisibility,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    selectedNotificationBadgeMode: NotificationBadgeMode,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
    backgroundNotificationsEnabled: Boolean,
    specialCareBackgroundNotificationsEnabled: Boolean,
    onBackgroundNotificationsChanged: (Boolean) -> Unit,
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit,
    onSpecialCareUsersChanged: (Set<String>) -> Unit,
    onSpecialCareSystemNotification: (NotificationItem) -> Unit,
    onAutomationSystemNotification: ((String, String) -> Boolean?)?,
    onAiQueueChanged: () -> Unit,
    initialSharedText: String?,
    onInitialSharedTextConsumed: () -> Unit,
    appVersionName: String,
    releaseNotesStore: ReleaseNotesStore,
    onCheckForUpdates: (((String) -> Unit) -> Unit),
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit),
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onAuthInvalid: () -> Unit,
    onSharedAccessLogin: (String, String?) -> Unit,
) {
    val appScope = rememberCoroutineScope()
    var rootRoute by remember { mutableStateOf(RootRoute.Timeline) }
    var route: AppRoute by remember { mutableStateOf(AppRoute.Timeline) }
    var timelineTrendSelected by remember { mutableStateOf(false) }
    var drivePickerTarget by remember { mutableStateOf<DrivePickerTarget?>(null) }
    var userListQuickTarget by remember { mutableStateOf<UserListQuickTarget?>(null) }
    var mediaPreviewSession by remember { mutableStateOf<MediaPreviewSession?>(null) }
    var viewedUserId by remember { mutableStateOf<String?>(null) }
    var authInvalidDialogOpen by remember { mutableStateOf(false) }
    var pendingReleaseNotes by remember { mutableStateOf<AppReleaseNotes?>(null) }
    var lastStreamingTimelineRefreshAt by remember { mutableStateOf<Map<TimelineKind, Long>>(emptyMap()) }
    var notifiedSpecialCareNoteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var automationTimelineSourceBaselines by remember(currentAccountId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var automationSeenTimelineNoteIds by remember(currentAccountId) { mutableStateOf<Set<String>>(emptySet()) }
    var automationSeenChatMessageIds by remember(currentAccountId) { mutableStateOf<Set<String>>(emptySet()) }
    var automationChatSourceBaselines by remember(currentAccountId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var automationRoomSourceMarkers by remember(currentAccountId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val fallbackTimelineCache = remember { InMemoryTimelineCache() }
    val activeTimelineCache = timelineCache ?: fallbackTimelineCache
    val specialCareStateHolder = remember(currentAccountId) {
        SpecialCareStateHolder(
            store = specialCareStore,
            accountId = currentAccountId,
        )
    }
    val specialCareState by specialCareStateHolder.state.collectAsState()
    val timelineStateHolder = remember {
        TimelineStateHolder(
            repository = TimelineRepository(
                tokenProvider = { sessionToken },
                cache = activeTimelineCache,
            ),
            repliesRepository = NoteRepliesRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val timelineState by timelineStateHolder.state.collectAsState()
    val latestRoute by rememberUpdatedState(route)
    val latestTimelineTrendSelected by rememberUpdatedState(timelineTrendSelected)
    val latestTimelineState by rememberUpdatedState(timelineState)
    val latestSpecialCareUserIds by rememberUpdatedState(specialCareState.userIds)
    val instanceMetaStateHolder = remember {
        InstanceMetaStateHolder(
            repository = InstanceMetaRepository(),
            scope = appScope,
        )
    }
    val instanceMetaState by instanceMetaStateHolder.state.collectAsState()
    val composeStateHolder = remember {
        ComposeStateHolder(
            repository = ComposeRepository(tokenProvider = { sessionToken }),
            driveFileRepository = DriveFileRepository(tokenProvider = { sessionToken }),
            userProfileRepository = UserProfileRepository(
                tokenProvider = { sessionToken },
                userIdProvider = { accountUser?.id },
            ),
            draftStore = composeDraftStore,
            draftKeyProvider = { currentAccountId ?: accountUser?.id ?: "default" },
            scope = appScope,
        )
    }
    val composeState by composeStateHolder.state.collectAsState()
    val discoverStateHolder = remember {
        DiscoverStateHolder(
            repository = DiscoverRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val discoverState by discoverStateHolder.state.collectAsState()
    val currentAccountHost = remember(accounts, currentAccountId, accountUser?.id) {
        accounts.firstOrNull { account -> account.id == currentAccountId }?.host
            ?: currentAccountId?.substringBefore(':')?.takeIf { host -> host.isNotBlank() && !host.startsWith("legacy-") }
    }
    val clipboardManager = LocalClipboardManager.current
    val chatRepository = remember(sessionToken, currentAccountId, accountUser?.id, chatMessageCache) {
        ChatRepository(
            tokenProvider = { sessionToken },
            currentUserIdProvider = { accountUser?.id },
            cacheAccountIdProvider = { currentAccountId },
            messageCache = chatMessageCache,
        )
    }
    val chatStateHolder = remember {
        ChatStateHolder(
            repository = chatRepository,
            driveFileRepository = DriveFileRepository(tokenProvider = { sessionToken }),
            streamingRepository = ChatStreamingRepository(tokenProvider = { sessionToken }),
            relationshipRepository = UserRelationshipRepository(tokenProvider = { sessionToken }),
            accountIdProvider = { currentAccountId },
            unreadStore = chatUnreadStore,
            currentUserProvider = { accountUser?.toDomainUser(host = currentAccountHost) },
            scope = appScope,
        )
    }
    val chatState by chatStateHolder.state.collectAsState()
    var mainStreamingConnected by remember(sessionToken) { mutableStateOf(false) }
    val driveFilesStateHolder = remember {
        DriveFilesStateHolder(
            repository = DriveFileRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val driveFilesState by driveFilesStateHolder.state.collectAsState()
    val favoriteNoteStateHolder = remember {
        FavoriteNoteStateHolder(
            repository = FavoriteNoteRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val favoriteNoteState by favoriteNoteStateHolder.state.collectAsState()
    val achievementStateHolder = remember {
        AchievementStateHolder(
            repository = AchievementRepository(
                tokenProvider = { sessionToken },
                userIdProvider = { accountUser?.id },
            ),
            scope = appScope,
        )
    }
    val achievementState by achievementStateHolder.state.collectAsState()
    val noteActionStateHolder = remember {
        NoteActionStateHolder(
            repository = NoteActionRepository(tokenProvider = { sessionToken }),
            emojiRepository = EmojiRepository(),
            recentReactionStore = recentReactionStore,
            scope = appScope,
        )
    }
    val noteActionState by noteActionStateHolder.state.collectAsState()
    val notificationRepository = remember(sessionToken) {
        NotificationRepository(tokenProvider = { sessionToken })
    }
    val notificationStateHolder = remember {
        NotificationStateHolder(
            repository = notificationRepository,
            readStore = notificationReadStore,
            notificationCache = notificationCache,
            accountId = currentAccountId,
            scope = appScope,
        )
    }
    val notificationState by notificationStateHolder.state.collectAsState()
    val aiStateHolder = remember(currentAccountId, aiStore) {
        AiStateHolder(
            store = aiStore,
            accountId = currentAccountId,
            onQueueChanged = onAiQueueChanged,
            scope = appScope,
        )
    }
    val aiState by aiStateHolder.state.collectAsState()
    val latestAutomationSystemNotification by rememberUpdatedState(onAutomationSystemNotification)
    val automationSystemNotificationPublisher: ((String, String) -> Boolean?)? = remember(
        onAutomationSystemNotification != null,
    ) {
        if (onAutomationSystemNotification == null) {
            null
        } else {
            { title: String, body: String ->
                latestAutomationSystemNotification?.invoke(title, body)
            }
        }
    }
    val automationStateHolder = remember(
        currentAccountId,
        automationStore,
        chatRepository,
        notificationRepository,
        automationSystemNotificationPublisher != null,
    ) {
        AutomationStateHolder(
            store = automationStore,
            accountId = currentAccountId,
            executor = AppAutomationActionExecutor(
                chatRepository = chatRepository,
                notificationRepository = notificationRepository,
                composeRepository = ComposeRepository(tokenProvider = { sessionToken }),
                noteActionRepository = NoteActionRepository(tokenProvider = { sessionToken }),
                clipboardWriter = { text ->
                    clipboardManager.setText(AnnotatedString(text))
                    true
                },
                systemNotificationPublisher = automationSystemNotificationPublisher,
                aiBridge = aiStateHolder,
            ),
            aiBridge = aiStateHolder,
            aiToolPermissionProvider = { aiStateHolder.state.value.settings.toolsAllowed },
            scope = appScope,
        )
    }
    val automationState by automationStateHolder.state.collectAsState()
    val mainStreamingRepository = remember {
        MainStreamingRepository(tokenProvider = { sessionToken })
    }
    val noteDetailStateHolder = remember {
        NoteDetailStateHolder(
            repository = NoteDetailRepository(tokenProvider = { sessionToken }),
            repliesRepository = NoteRepliesRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val noteDetailState by noteDetailStateHolder.state.collectAsState()
    val userProfileStateHolder = remember {
        UserProfileStateHolder(
            repository = UserProfileRepository(
                tokenProvider = { sessionToken },
                userIdProvider = { accountUser?.id },
            ),
            notesRepository = UserNotesRepository(
                tokenProvider = { sessionToken },
                userIdProvider = { accountUser?.id },
            ),
            driveFileRepository = DriveFileRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val userProfileState by userProfileStateHolder.state.collectAsState()
    val viewedProfileStateHolder = remember {
        UserProfileStateHolder(
            repository = UserProfileRepository(
                tokenProvider = { sessionToken },
                userIdProvider = { viewedUserId },
            ),
            notesRepository = UserNotesRepository(
                tokenProvider = { sessionToken },
                userIdProvider = { viewedUserId },
            ),
            relationshipRepository = UserRelationshipRepository(
                tokenProvider = { sessionToken },
            ),
            scope = appScope,
        )
    }
    val viewedProfileState by viewedProfileStateHolder.state.collectAsState()
    val mentionResolverRepository = remember {
        UserProfileRepository(
            tokenProvider = { sessionToken },
            userIdProvider = { accountUser?.id },
        )
    }
    val composeCompletionStateHolder = remember {
        ComposeCompletionStateHolder(
            repository = DiscoverRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val composeCompletionState by composeCompletionStateHolder.state.collectAsState()
    val userSocialStateHolder = remember {
        UserSocialStateHolder(
            repository = UserSocialRepository(tokenProvider = { sessionToken }),
            relationshipRepository = UserRelationshipRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val userSocialState by userSocialStateHolder.state.collectAsState()
    val quickRelationshipRepository = remember(sessionToken) {
        UserRelationshipRepository(tokenProvider = { sessionToken })
    }
    val userListStateHolder = remember {
        UserListStateHolder(
            repository = UserListRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val userListState by userListStateHolder.state.collectAsState()
    val followRequestStateHolder = remember {
        FollowRequestStateHolder(
            repository = FollowRequestRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val followRequestState by followRequestStateHolder.state.collectAsState()
    val relationshipManagementStateHolder = remember {
        RelationshipManagementStateHolder(
            repository = UserRelationshipRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val relationshipManagementState by relationshipManagementStateHolder.state.collectAsState()
    val antennaStateHolder = remember {
        AntennaStateHolder(
            repository = AntennaRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val antennaState by antennaStateHolder.state.collectAsState()
    val clipStateHolder = remember {
        ClipStateHolder(
            repository = ClipRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val clipState by clipStateHolder.state.collectAsState()
    val channelStateHolder = remember {
        ChannelStateHolder(
            repository = ChannelRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val channelState by channelStateHolder.state.collectAsState()
    val automationChannelRepository = remember(sessionToken) {
        ChannelRepository(tokenProvider = { sessionToken })
    }
    val automationDiscoverRepository = remember(sessionToken) {
        DiscoverRepository(tokenProvider = { sessionToken })
    }
    val pageStateHolder = remember {
        PageStateHolder(
            repository = PageRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val pageState by pageStateHolder.state.collectAsState()
    val galleryStateHolder = remember {
        GalleryStateHolder(
            repository = GalleryRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val galleryState by galleryStateHolder.state.collectAsState()
    val flashStateHolder = remember {
        FlashStateHolder(
            repository = FlashRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val flashState by flashStateHolder.state.collectAsState()
    val announcementStateHolder = remember {
        AnnouncementStateHolder(
            repository = AnnouncementRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val announcementState by announcementStateHolder.state.collectAsState()
    val adminStateHolder = remember {
        AdminStateHolder(
            repository = AdminRepository(tokenProvider = { sessionToken }),
            scope = appScope,
        )
    }
    val adminState by adminStateHolder.state.collectAsState()
    val settingsStateHolder = remember {
        SettingsStateHolder(
            repository = SettingsRepository(tokenProvider = { sessionToken }),
            scope = appScope,
            onSharedAccessLogin = onSharedAccessLogin,
        )
    }
    val settingsState by settingsStateHolder.state.collectAsState()
    val noteRowDensity = selectedTimelineDensity.toNoteRowDensity()
    val instanceCapabilities = instanceMetaState.meta?.capabilities ?: InstanceCapabilities()
    val loadedNotes = loadedNotesForActions(
        timelineState = timelineState,
        noteDetailState = noteDetailState,
        userProfileState = userProfileState,
        viewedProfileState = viewedProfileState,
        discoverState = discoverState,
        favoriteNoteState = favoriteNoteState,
        userListState = userListState,
        antennaState = antennaState,
        clipState = clipState,
        channelState = channelState,
    )
    var noteActionToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentAccountId) {
        instanceMetaStateHolder.load()
        specialCareStateHolder.restoreStoredSpecialCare()
        composeStateHolder.restoreStoredDraft()
        composeStateHolder.restoreFailedSendQueue()
        aiStateHolder.restore()
        noteActionStateHolder.restoreRecentReactions()
        noteActionStateHolder.loadReactionOptions()
        automationStateHolder.restore()
        timelineStateHolder.refresh()
        notificationStateHolder.refresh()
        settingsStateHolder.refreshRemote()
        relationshipManagementStateHolder.refreshBlockedUsers()
    }

    LaunchedEffect(appVersionName) {
        val cleanVersion = appVersionName.trim().removePrefix("v")
        if (cleanVersion.isBlank()) return@LaunchedEffect
        val lastShownVersion = runCatching { releaseNotesStore.loadLastShownVersion() }.getOrNull()
            ?.trim()
            ?.removePrefix("v")
        if (lastShownVersion != cleanVersion) {
            pendingReleaseNotes = releaseNotesFor(cleanVersion)
        }
    }

    LaunchedEffect(initialSharedText, currentAccountId) {
        val sharedText = initialSharedText?.trim().orEmpty()
        if (sharedText.isNotEmpty()) {
            composeStateHolder.startNewNote()
            composeStateHolder.appendText(sharedText)
            rootRoute = RootRoute.Timeline
            route = AppRoute.Compose()
            noteActionToast = "已填入分享内容"
            onInitialSharedTextConsumed()
        }
    }

    LaunchedEffect(specialCareState.userIds) {
        chatStateHolder.updateSpecialCareUsers(specialCareState.userIds)
        notificationStateHolder.updateSpecialCareUsers(specialCareState.userIds)
        onSpecialCareUsersChanged(specialCareState.userIds)
        notifiedSpecialCareNoteIds += loadedNotes
            .filter { it.author.id in specialCareState.userIds }
            .map { it.id }
    }

    LaunchedEffect(
        specialCareState.userIds,
        accountUser,
        userProfileState.user,
        viewedProfileState.user,
        loadedNotes,
        chatState.rooms,
        chatState.userConversations,
        chatState.messages,
    ) {
        relationshipManagementStateHolder.updateSpecialCareUsers(
            relationshipSpecialCareEntries(
                userIds = specialCareState.userIds,
                accountUser = accountUser,
                userProfileState = userProfileState,
                viewedProfileState = viewedProfileState,
                loadedNotes = loadedNotes,
                chatState = chatState,
            ),
        )
    }

    LaunchedEffect(userSocialState.message) {
        if (userSocialState.message == "已屏蔽") {
            relationshipManagementStateHolder.refreshBlockedUsers()
        }
    }

    LaunchedEffect(viewedProfileState.user?.id, viewedProfileState.relationship?.isBlocking) {
        val user = viewedProfileState.user ?: return@LaunchedEffect
        val blocked = viewedProfileState.relationship?.isBlocking ?: return@LaunchedEffect
        relationshipManagementStateHolder.updateBlockedUser(
            entry = relationshipEntryForUser(user, prefix = "blocked"),
            blocked = blocked,
        )
    }

    LaunchedEffect(
        selectedTheme,
        customTheme,
        selectedTimelineDensity,
        listGesturesEnabled,
        selectedDefaultNoteVisibility,
        selectedNotificationBadgeMode,
        aiState.settings,
        aiState.usage,
        aiState.tasks,
        backgroundNotificationsEnabled,
        specialCareBackgroundNotificationsEnabled,
        accountUser,
    ) {
        settingsStateHolder.sync(
            selectedTheme = selectedTheme,
            customTheme = customTheme,
            selectedTimelineDensity = selectedTimelineDensity,
            listGesturesEnabled = listGesturesEnabled,
            selectedDefaultNoteVisibility = selectedDefaultNoteVisibility,
            selectedNotificationBadgeMode = selectedNotificationBadgeMode,
            aiSettings = aiState.settings,
            aiTasks = aiState.tasks,
            aiUsage = aiState.usage,
            backgroundNotificationsEnabled = backgroundNotificationsEnabled,
            specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
            accountUser = accountUser,
        )
    }

    LaunchedEffect(
        instanceMetaState.meta?.capabilities?.canSearchNotes,
        instanceMetaState.meta?.capabilities?.canTrend,
        instanceMetaState.meta?.capabilities?.canViewFederation,
    ) {
        instanceMetaState.meta?.capabilities?.let { capabilities ->
            discoverStateHolder.updateCapabilities(
                canSearchNotes = capabilities.canSearchNotes,
                canTrend = capabilities.canTrend,
                canViewFederation = capabilities.canViewFederation,
            )
        }
    }

    LaunchedEffect(instanceMetaState.meta?.capabilities?.chatAvailable) {
        val chatAvailable = instanceMetaState.meta?.capabilities?.chatAvailable == true
        chatStateHolder.updateAvailability(chatAvailable)
        if (!chatAvailable && rootRoute == RootRoute.Chat) {
            rootRoute = RootRoute.Timeline
            route = AppRoute.Timeline
        }
    }

    LaunchedEffect(chatState.chatAvailable, chatState.selectedRoom?.id, route) {
        if (!chatState.chatAvailable) return@LaunchedEffect
        val markSelectedRoomRead = route == AppRoute.Chat && chatState.selectedRoom != null
        chatStateHolder.refreshRoomsQuietly(markSelectedRoomRead = markSelectedRoomRead)
        chatStateHolder.refreshUserConversationsQuietly(
            markSelectedUserRead = route == AppRoute.Chat && chatState.selectedUserConversation != null,
        )
        while (true) {
            delay(CHAT_ROOM_REFRESH_INTERVAL_MS)
            chatStateHolder.refreshRoomsQuietly(markSelectedRoomRead = markSelectedRoomRead)
            chatStateHolder.refreshUserConversationsQuietly(
                markSelectedUserRead = route == AppRoute.Chat && chatState.selectedUserConversation != null,
            )
        }
    }

    LaunchedEffect(
        chatState.chatAvailable,
        chatState.selectedRoom?.id,
        chatState.selectedUserConversation?.user?.id,
        chatState.isStreamingMessages,
        route,
    ) {
        if (
            !chatState.chatAvailable ||
            (chatState.selectedRoom == null && chatState.selectedUserConversation == null) ||
            route != AppRoute.Chat
        ) {
            return@LaunchedEffect
        }
        chatStateHolder.refreshRoomsQuietly(markSelectedRoomRead = chatState.selectedRoom != null)
        chatStateHolder.refreshUserConversationsQuietly(
            markSelectedUserRead = chatState.selectedUserConversation != null,
        )
        chatStateHolder.refreshSelectedMessagesQuietly()
        while (true) {
            delay(if (chatState.isStreamingMessages) STREAMING_CHAT_REFRESH_INTERVAL_MS else CHAT_MESSAGE_REFRESH_INTERVAL_MS)
            chatStateHolder.refreshRoomsQuietly(markSelectedRoomRead = chatState.selectedRoom != null)
            chatStateHolder.refreshUserConversationsQuietly(
                markSelectedUserRead = chatState.selectedUserConversation != null,
            )
            chatStateHolder.refreshSelectedMessagesQuietly()
        }
    }

    LaunchedEffect(route, chatState.selectedRoom?.id, chatState.selectedUserConversation?.user?.id) {
        if (
            route != AppRoute.Chat &&
            (chatState.selectedRoom != null || chatState.selectedUserConversation != null)
        ) {
            chatStateHolder.stopMessageStreaming()
        }
    }

    LaunchedEffect(
        chatState.chatAvailable,
        chatState.selectedRoom?.id,
        chatState.selectedUserConversation?.user?.id,
        chatState.isStreamingMessages,
        chatState.requiresRelogin,
        route,
    ) {
        if (
            !chatState.chatAvailable ||
            (chatState.selectedRoom == null && chatState.selectedUserConversation == null) ||
            chatState.isStreamingMessages ||
            chatState.requiresRelogin ||
            route != AppRoute.Chat
        ) {
            return@LaunchedEffect
        }
        delay(CHAT_STREAM_RECONNECT_DELAY_MS)
        if (chatState.selectedRoom != null) {
            chatStateHolder.startMessageStreaming()
        } else {
            chatStateHolder.startUserMessageStreaming()
        }
    }

    LaunchedEffect(route, timelineState.selectedKind, timelineState.requiresRelogin, mainStreamingConnected) {
        if (route != AppRoute.Timeline || timelineTrendSelected || timelineState.requiresRelogin) return@LaunchedEffect
        timelineStateHolder.refreshQuietly(timelineState.selectedKind)
        while (true) {
            delay(
                if (mainStreamingConnected) {
                    STREAMING_TIMELINE_FALLBACK_REFRESH_INTERVAL_MS
                } else {
                    TIMELINE_REFRESH_INTERVAL_MS
                },
            )
            timelineStateHolder.refreshQuietly(timelineState.selectedKind)
        }
    }

    LaunchedEffect(route, timelineTrendSelected, discoverState.canTrend, discoverState.requiresRelogin) {
        if (
            route != AppRoute.Timeline ||
            !timelineTrendSelected ||
            !discoverState.canTrend ||
            discoverState.requiresRelogin
        ) {
            return@LaunchedEffect
        }
        discoverStateHolder.refreshTrendsQuietly()
        while (true) {
            delay(TREND_REFRESH_INTERVAL_MS)
            discoverStateHolder.refreshTrendsQuietly()
        }
    }

    LaunchedEffect(route) {
        if (route == AppRoute.Discover) {
            discoverStateHolder.refreshPinnedUsersQuietly()
        }
    }

    LaunchedEffect(notificationState.requiresRelogin, route, mainStreamingConnected) {
        if (notificationState.requiresRelogin) return@LaunchedEffect
        notificationStateHolder.refreshQuietly()
        while (true) {
            delay(
                if (mainStreamingConnected) {
                    STREAMING_NOTIFICATION_FALLBACK_REFRESH_INTERVAL_MS
                } else {
                    NOTIFICATION_REFRESH_INTERVAL_MS
                },
            )
            notificationStateHolder.refreshQuietly()
        }
    }

    suspend fun refreshNotificationsAfterStreamingEvent() {
        notificationStateHolder.refreshQuietly()
        delay(1_500L)
        notificationStateHolder.refreshQuietly()
        delay(4_000L)
        notificationStateHolder.refreshQuietly()
    }

    LaunchedEffect(sessionToken, chatState.chatAvailable) {
        mainStreamingConnected = false
        while (true) {
            var unauthorized = false
            mainStreamingRepository.streamMain().collectLatest { event ->
                when (event) {
                    MainStreamingEvent.UnreadNotification -> refreshNotificationsAfterStreamingEvent()
                    is MainStreamingEvent.NotificationReceived -> {
                        notificationStateHolder.addStreamingNotification(event.notification)
                        refreshNotificationsAfterStreamingEvent()
                    }
                    MainStreamingEvent.ReadAllNotifications -> notificationStateHolder.syncAllReadFromStreaming()
                    is MainStreamingEvent.TimelineNote -> {
                        val currentTimelineState = latestTimelineState
                        val streamingNote = event.note
                        val now = Clock.System.now().toEpochMilliseconds()
                        if (streamingNote != null) {
                            val eventKey = "${event.kind.name}:${streamingNote.id}"
                            if (eventKey !in automationSeenTimelineNoteIds) {
                                automationSeenTimelineNoteIds = (automationSeenTimelineNoteIds + eventKey)
                                    .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
                                automationStateHolder.emit(
                                    streamingNote.toAutomationTimelineEvent(
                                        kind = event.kind,
                                        currentUser = accountUser?.toDomainUser(host = currentAccountHost),
                                    ),
                                )
                            }
                        }
                        if (
                            streamingNote != null &&
                            streamingNote.author.id in latestSpecialCareUserIds &&
                            streamingNote.id !in notifiedSpecialCareNoteIds
                        ) {
                            val notification = streamingNote.toSpecialCareNotification(
                                fallbackEpochMillis = now,
                            )
                            notificationStateHolder.addSpecialCareNotification(
                                notification,
                            )
                            automationStateHolder.emit(notification.toAutomationNotificationEvent())
                            onSpecialCareSystemNotification(notification)
                            notifiedSpecialCareNoteIds += streamingNote.id
                        }
                        if (
                            latestRoute == AppRoute.Timeline &&
                            !latestTimelineTrendSelected &&
                            !currentTimelineState.requiresRelogin &&
                            event.kind == currentTimelineState.selectedKind
                        ) {
                            val previousRefreshAt = lastStreamingTimelineRefreshAt[event.kind] ?: 0L
                            if (now - previousRefreshAt >= STREAMING_TIMELINE_REFRESH_DEBOUNCE_MS) {
                                lastStreamingTimelineRefreshAt = lastStreamingTimelineRefreshAt + (event.kind to now)
                                timelineStateHolder.refreshQuietly(event.kind)
                            }
                        } else if (
                            event.kind == TimelineKind.Home &&
                            latestSpecialCareUserIds.isNotEmpty() &&
                            !currentTimelineState.requiresRelogin
                        ) {
                            val previousRefreshAt = lastStreamingTimelineRefreshAt[event.kind] ?: 0L
                            if (now - previousRefreshAt >= STREAMING_TIMELINE_REFRESH_DEBOUNCE_MS) {
                                lastStreamingTimelineRefreshAt = lastStreamingTimelineRefreshAt + (event.kind to now)
                                timelineStateHolder.refreshQuietly(event.kind)
                            }
                        }
                    }
                    MainStreamingEvent.NewChatMessage -> {
                        if (chatState.chatAvailable) {
                            chatStateHolder.refreshRoomsQuietly(
                                markSelectedRoomRead = route == AppRoute.Chat && chatState.selectedRoom != null,
                            )
                            chatStateHolder.refreshUserConversationsQuietly(
                                markSelectedUserRead = route == AppRoute.Chat &&
                                    chatState.selectedUserConversation != null,
                            )
                            chatStateHolder.refreshSelectedMessagesQuietly()
                            chatStateHolder.refreshSpecialCareMessagesQuietly()
                            appScope.launch {
                                delay(CHAT_EVENT_RECHECK_DELAY_MS)
                                chatStateHolder.refreshRoomsQuietly(
                                    markSelectedRoomRead = route == AppRoute.Chat && chatState.selectedRoom != null,
                                )
                                chatStateHolder.refreshUserConversationsQuietly(
                                    markSelectedUserRead = route == AppRoute.Chat &&
                                        chatState.selectedUserConversation != null,
                                )
                                chatStateHolder.refreshSpecialCareMessagesQuietly()
                            }
                        }
                    }
                    MainStreamingEvent.Unauthorized -> {
                        unauthorized = true
                        mainStreamingConnected = false
                    }
                    MainStreamingEvent.Connected -> mainStreamingConnected = true
                    MainStreamingEvent.Connecting -> mainStreamingConnected = false
                    MainStreamingEvent.Closed -> mainStreamingConnected = false
                    is MainStreamingEvent.Error -> mainStreamingConnected = false
                }
            }
            if (unauthorized) return@LaunchedEffect
            delay(CHAT_STREAM_RECONNECT_DELAY_MS)
        }
    }

    LaunchedEffect(instanceCapabilities, route) {
        val supportedRoute = supportedRouteOrFallback(
            route = route,
            capabilities = instanceCapabilities,
        )
        if (supportedRoute != route) {
            route = supportedRoute
            rootRoute = rootRouteFor(supportedRoute)
        }
        if (!instanceCapabilities.canTrend && timelineTrendSelected) {
            timelineTrendSelected = false
        }
    }

    LaunchedEffect(instanceMetaState.meta?.maxNoteTextLength, instanceMetaState.meta?.maxCwLength) {
        instanceMetaState.meta?.let { meta ->
            composeStateHolder.updateLimits(
                maxTextLength = meta.maxNoteTextLength,
                maxCwLength = meta.maxCwLength,
            )
        }
    }

    LaunchedEffect(
        instanceMetaState.meta?.capabilities?.canPublicNote,
        instanceMetaState.meta?.capabilities?.canScheduleNotes,
    ) {
        composeStateHolder.updateCapabilities(
            canPublicNote = instanceCapabilities.canPublicNote,
            canScheduleNotes = instanceCapabilities.canScheduleNotes,
        )
    }

    LaunchedEffect(instanceMetaState.meta?.defaultLike) {
        instanceMetaState.meta?.defaultLike?.let { defaultLike ->
            noteActionStateHolder.updateDefaultReaction(defaultLike)
            chatStateHolder.updateDefaultReaction(defaultLike)
        }
    }

    LaunchedEffect(noteActionState.reactionOptions) {
        chatStateHolder.updateReactionOptions(noteActionState.reactionOptions)
    }

    LaunchedEffect(
        chatState.selectedRoom?.id,
        chatState.selectedUserConversation?.user?.id,
        chatState.messages.map { message -> message.id },
        chatState.userConversations.map { conversation ->
            conversation.user.id to conversation.latestMessage?.automationMessageKey()
        },
        chatState.rooms.take(AUTOMATION_ROOM_SCAN_LIMIT).map { room ->
            room.id to room.latestMessageMarker.ifBlank { room.latestMessageAtLabel }
        },
    ) {
        val currentUser = accountUser?.toDomainUser(host = currentAccountHost)
        val selectedSourceId = chatState.selectedUserConversation?.user?.id
            ?.let { userId -> "user:$userId" }
            ?: chatState.selectedRoom?.id?.let { roomId -> "room:$roomId" }
        val selectedLatestMessage = chatState.messages.lastOrNull()
        val selectedLatestMessageId = selectedLatestMessage?.automationMessageKey().orEmpty()
        val selectedBaselineId = selectedSourceId?.let { sourceId -> automationChatSourceBaselines[sourceId] }
        val selectedMessagesAfterBaseline = if (
            selectedSourceId != null &&
            selectedBaselineId != null &&
            selectedLatestMessageId.isNotBlank() &&
            selectedBaselineId != selectedLatestMessageId
        ) {
            chatState.messages.automationMessagesAfterBaseline(selectedBaselineId)
        } else {
            emptyList()
        }
        val selectedMessageEvents = selectedMessagesAfterBaseline.mapNotNull { message ->
            val messageId = message.automationMessageKey()
            if (messageId.isBlank() || selectedSourceId == null) return@mapNotNull null
            val eventKey = "$selectedSourceId:$messageId"
            if (eventKey in automationSeenChatMessageIds) null else eventKey to message
        }
        val userEvents = chatState.userConversations.mapNotNull { conversation ->
            val message = conversation.latestMessage ?: return@mapNotNull null
            val messageId = message.automationMessageKey()
            if (messageId.isBlank()) return@mapNotNull null
            val sourceId = "user:${conversation.user.id}"
            if (automationChatSourceBaselines[sourceId] == null) return@mapNotNull null
            if (automationChatSourceBaselines[sourceId] == messageId) return@mapNotNull null
            if (sourceId == selectedSourceId && messageId == selectedLatestMessageId) return@mapNotNull null
            val eventKey = "$sourceId:$messageId"
            if (eventKey in automationSeenChatMessageIds) return@mapNotNull null
            eventKey to (conversation to message)
        }
        val nextBaselines = buildMap {
            putAll(automationChatSourceBaselines)
            if (selectedSourceId != null && selectedLatestMessageId.isNotBlank()) {
                put(selectedSourceId, selectedLatestMessageId)
            }
            chatState.userConversations.forEach { conversation ->
                val latestId = conversation.latestMessage?.automationMessageKey().orEmpty()
                if (latestId.isNotBlank()) {
                    put("user:${conversation.user.id}", latestId)
                }
            }
        }.takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
        automationChatSourceBaselines = nextBaselines

        val roomSourceIdsToScan = chatState.rooms
            .take(AUTOMATION_ROOM_SCAN_LIMIT)
            .mapNotNull { room ->
                val sourceId = "room:${room.id}"
                val marker = room.latestMessageMarker.ifBlank { room.latestMessageAtLabel }
                val baseline = automationRoomSourceMarkers[sourceId]
                if (
                    sourceId != selectedSourceId &&
                    baseline != null &&
                    marker.isNotBlank() &&
                    marker != baseline
                ) {
                    AutomationRoomScanTarget(
                        sourceId = sourceId,
                        roomId = room.id,
                        roomName = room.name,
                        marker = marker,
                    )
                } else {
                    null
                }
            }
        automationRoomSourceMarkers = buildMap {
            putAll(automationRoomSourceMarkers)
            chatState.rooms.take(AUTOMATION_ROOM_SCAN_LIMIT).forEach { room ->
                val sourceId = "room:${room.id}"
                val marker = room.latestMessageMarker.ifBlank { room.latestMessageAtLabel }
                if (marker.isNotBlank() && sourceId !in automationRoomSourceMarkers) {
                    put(sourceId, marker)
                }
            }
        }.takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)

        if (selectedMessageEvents.isEmpty() && userEvents.isEmpty() && roomSourceIdsToScan.isEmpty()) return@LaunchedEffect

        automationSeenChatMessageIds = (automationSeenChatMessageIds + selectedMessageEvents.map { it.first } + userEvents.map { it.first })
            .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)

        selectedMessageEvents.forEach { (_, message) ->
            val attentionKind = message.automationChatAttentionKind(
                currentUser = currentUser,
                specialCareUserIds = specialCareState.userIds,
            )
            automationStateHolder.emit(
                message.toAutomationChatEvent(
                    roomId = chatState.selectedRoom?.id ?: message.roomId,
                    roomName = chatState.selectedRoom?.name.orEmpty(),
                    directUserId = chatState.selectedUserConversation?.user?.id,
                    attentionKind = attentionKind?.name.orEmpty(),
                    currentUser = currentUser,
                ),
            )
        }
        userEvents.forEach { (_, pair) ->
            val (conversation, message) = pair
            val attentionKind = message.automationChatAttentionKind(
                currentUser = currentUser,
                specialCareUserIds = specialCareState.userIds,
            )
            automationStateHolder.emit(
                message.toAutomationChatEvent(
                    roomId = message.roomId,
                    directUserId = conversation.user.id,
                    attentionKind = attentionKind?.name.orEmpty(),
                    currentUser = currentUser,
                ),
            )
        }
        roomSourceIdsToScan.forEach { target ->
            val baselineId = automationChatSourceBaselines[target.sourceId].orEmpty()
            val result = chatRepository.refreshMessages(target.roomId)
            if (result !is ChatMessageRepositoryResult.Success) return@forEach
            val roomMessageEvents = result.messages
                .let { messages ->
                    messages.automationMessagesAfterBaseline(baselineId).ifEmpty {
                        if (baselineId.isBlank()) listOfNotNull(messages.lastOrNull()) else emptyList()
                    }
                }
                .mapNotNull { message ->
                    val messageId = message.automationMessageKey()
                    if (messageId.isBlank()) return@mapNotNull null
                    val eventKey = "${target.sourceId}:$messageId"
                    if (eventKey in automationSeenChatMessageIds) null else eventKey to message
                }
            if (roomMessageEvents.isEmpty()) {
                result.messages.lastOrNull()?.automationMessageKey()?.takeIf { it.isNotBlank() }?.let { latestId ->
                    automationChatSourceBaselines = (automationChatSourceBaselines + (target.sourceId to latestId))
                        .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
                }
                return@forEach
            }
            automationSeenChatMessageIds = (automationSeenChatMessageIds + roomMessageEvents.map { it.first })
                .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
            automationChatSourceBaselines = (automationChatSourceBaselines + (target.sourceId to roomMessageEvents.last().second.automationMessageKey()))
                .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
            automationRoomSourceMarkers = (automationRoomSourceMarkers + (target.sourceId to target.marker))
                .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
            roomMessageEvents.forEach { (_, message) ->
                val attentionKind = message.automationChatAttentionKind(
                    currentUser = currentUser,
                    specialCareUserIds = specialCareState.userIds,
                )
                automationStateHolder.emit(
                    message.toAutomationChatEvent(
                        roomId = target.roomId,
                        roomName = target.roomName,
                        attentionKind = attentionKind?.name.orEmpty(),
                        currentUser = currentUser,
                    ),
                )
            }
        }
    }

    LaunchedEffect(
        channelState.selectedChannel?.id,
        channelState.notes.map { it.id },
    ) {
        val channel = channelState.selectedChannel ?: return@LaunchedEffect
        val latestNoteId = channelState.notes.firstOrNull()?.id.orEmpty()
        val sourceId = "channel:${channel.id}"
        val baselineId = automationTimelineSourceBaselines[sourceId]
        if (baselineId == null) {
            if (latestNoteId.isNotBlank()) {
                automationTimelineSourceBaselines = (automationTimelineSourceBaselines + (sourceId to latestNoteId))
                    .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
            }
            return@LaunchedEffect
        }
        if (latestNoteId.isBlank() || latestNoteId == baselineId) return@LaunchedEffect
        val notesAfterBaseline = channelState.notes.takeWhile { it.id != baselineId }.asReversed()
        val currentUser = accountUser?.toDomainUser(host = currentAccountHost)
        notesAfterBaseline.forEach { note ->
            val eventKey = "$sourceId:${note.id}"
            if (eventKey !in automationSeenTimelineNoteIds) {
                automationSeenTimelineNoteIds = (automationSeenTimelineNoteIds + eventKey)
                    .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
                automationStateHolder.emit(
                    note.toAutomationTimelineEvent(
                        kind = TimelineKind.Home,
                        channelName = channel.name,
                        timelineSource = "Channel",
                        currentUser = currentUser,
                    ),
                )
            }
        }
        automationTimelineSourceBaselines = (automationTimelineSourceBaselines + (sourceId to latestNoteId))
            .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
    }

    LaunchedEffect(
        automationState.rules.map { rule -> rule.id to rule.enabled to rule.trigger to rule.conditions.map { it.type to it.value } },
        currentAccountId,
    ) {
        val channelRules = automationState.rules.filter { rule ->
            rule.enabled && rule.trigger == AutomationTrigger.TimelineNote && rule.conditions.any { condition ->
                condition.enabled && condition.type == AutomationConditionType.ChannelId && condition.value.isNotBlank()
            }
        }
        if (channelRules.isEmpty()) return@LaunchedEffect
        while (true) {
            val targets = channelRules
                .flatMap { rule ->
                    rule.conditions.filter { it.enabled && it.type == AutomationConditionType.ChannelId }
                        .map { it.value.trim() }
                }
                .filter { it.isNotBlank() }
                .distinct()
                .take(AUTOMATION_CHANNEL_SCAN_LIMIT)
            val knownChannels = buildList {
                addAll(channelState.channels)
                channelState.selectedChannel?.let { add(it) }
            }.distinctBy { it.id }.associateBy { it.id }
            val currentUser = accountUser?.toDomainUser(host = currentAccountHost)
            targets.forEach { channelId ->
                val result = automationChannelRepository.refreshTimeline(channelId)
                if (result !is cc.hhhl.client.repository.ChannelTimelineRepositoryResult.Success) return@forEach
                val latestNoteId = result.notes.firstOrNull()?.id.orEmpty()
                val sourceId = "channel:$channelId"
                val baselineId = automationTimelineSourceBaselines[sourceId]
                if (baselineId == null) {
                    if (latestNoteId.isNotBlank()) {
                        automationTimelineSourceBaselines = (automationTimelineSourceBaselines + (sourceId to latestNoteId))
                            .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
                    }
                    return@forEach
                }
                if (latestNoteId.isBlank() || latestNoteId == baselineId) return@forEach
                val channelName = knownChannels[channelId]?.name.orEmpty()
                result.notes.takeWhile { it.id != baselineId }.asReversed().forEach { note ->
                    val eventKey = "$sourceId:${note.id}"
                    if (eventKey !in automationSeenTimelineNoteIds) {
                        automationSeenTimelineNoteIds = (automationSeenTimelineNoteIds + eventKey)
                            .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
                        automationStateHolder.emit(
                            note.copy(channelId = note.channelId.ifBlank { channelId }).toAutomationTimelineEvent(
                                kind = TimelineKind.Home,
                                channelName = channelName,
                                timelineSource = "Channel",
                                currentUser = currentUser,
                            ),
                        )
                    }
                }
                automationTimelineSourceBaselines = (automationTimelineSourceBaselines + (sourceId to latestNoteId))
                    .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
            }
            delay(AUTOMATION_CHANNEL_SCAN_INTERVAL_MS)
        }
    }

    LaunchedEffect(chatState.specialCareToast?.messageId) {
        chatState.specialCareToast?.let { toast ->
            val notification = toast.toChatAttentionNotification(
                fallbackEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
            if (toast.kind == ChatAttentionKind.SpecialCare) {
                notificationStateHolder.addSpecialCareNotification(notification)
            } else {
                notificationStateHolder.addChatAttentionNotification(notification)
            }
            automationStateHolder.emit(notification.toAutomationNotificationEvent())
            onSpecialCareSystemNotification(notification)
        }
    }

    LaunchedEffect(
        specialCareState.userIds,
        loadedNotes.map { it.id to it.author.id },
    ) {
        if (specialCareState.userIds.isEmpty()) return@LaunchedEffect
        val newSpecialCareNotes = loadedNotes
            .filter { it.author.id in specialCareState.userIds }
            .filter { it.id !in notifiedSpecialCareNoteIds }
        newSpecialCareNotes.forEach { note ->
            val notification = note.toSpecialCareNotification(
                fallbackEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
            notificationStateHolder.addSpecialCareNotification(notification)
            automationStateHolder.emit(notification.toAutomationNotificationEvent())
        }
        if (newSpecialCareNotes.isNotEmpty()) {
            notifiedSpecialCareNoteIds += newSpecialCareNotes.map { it.id }
        }
    }

    val requiresRelogin = listOf(
        timelineState.requiresRelogin,
        composeState.requiresRelogin,
        discoverState.requiresRelogin,
        chatState.requiresRelogin,
        driveFilesState.requiresRelogin,
        favoriteNoteState.requiresRelogin,
        achievementState.requiresRelogin,
        notificationState.requiresRelogin,
        noteActionState.requiresRelogin,
        noteDetailState.requiresRelogin,
        userProfileState.requiresRelogin,
        viewedProfileState.requiresRelogin,
        userSocialState.requiresRelogin,
        userListState.requiresRelogin,
        followRequestState.requiresRelogin,
        relationshipManagementState.requiresRelogin,
        antennaState.requiresRelogin,
        clipState.requiresRelogin,
        channelState.requiresRelogin,
        pageState.requiresRelogin,
        galleryState.requiresRelogin,
        flashState.requiresRelogin,
        announcementState.requiresRelogin,
        adminState.requiresRelogin,
        settingsState.requiresRelogin,
    ).any { it }

    LaunchedEffect(requiresRelogin) {
        if (requiresRelogin) {
            authInvalidDialogOpen = true
        } else {
            authInvalidDialogOpen = false
        }
    }

    LaunchedEffect(route, drivePickerTarget) {
        if (route == AppRoute.Notifications && notificationState.notifications.isEmpty()) {
            notificationStateHolder.refresh()
        }
        if (
            route == AppRoute.Notifications &&
            announcementState.announcements.isEmpty() &&
            !announcementState.isLoading &&
            announcementState.errorMessage == null
        ) {
            announcementStateHolder.refresh()
        }
        if ((route == AppRoute.Profile || route == AppRoute.ProfileNotes) && userProfileState.user == null) {
            userProfileStateHolder.load()
        }
        if (route == AppRoute.Chat && chatState.chatAvailable) {
            if (chatState.rooms.isEmpty()) {
                chatStateHolder.refresh()
            } else {
                chatStateHolder.refreshRoomsQuietly(markSelectedRoomRead = chatState.selectedRoom != null)
                chatStateHolder.refreshUserConversationsQuietly(
                    markSelectedUserRead = chatState.selectedUserConversation != null,
                )
                chatStateHolder.refreshSelectedMessagesQuietly()
            }
        }
        if (
            (route == AppRoute.Drive || drivePickerTarget != null) &&
            driveFilesState.files.isEmpty() &&
            driveFilesState.folders.isEmpty() &&
            !driveFilesState.isLoading &&
            driveFilesState.errorMessage == null
        ) {
            driveFilesStateHolder.refresh()
        }
        if (route == AppRoute.FavoriteNotes && favoriteNoteState.favorites.isEmpty()) {
            favoriteNoteStateHolder.refresh()
        }
        if (route == AppRoute.Achievements && achievementState.achievements.isEmpty()) {
            achievementStateHolder.refresh()
        }
        if (route == AppRoute.UserLists && instanceCapabilities.canUseUserLists && userListState.lists.isEmpty()) {
            userListStateHolder.refreshLists()
        }
        if (route == AppRoute.FollowRequests && followRequestState.requests.isEmpty()) {
            followRequestStateHolder.refresh()
        }
        if (
            route == AppRoute.RelationshipManagement &&
            relationshipManagementState.visibleEntries.isEmpty() &&
            !relationshipManagementState.isLoading &&
            relationshipManagementState.errorMessage == null
        ) {
            relationshipManagementStateHolder.refresh()
        }
        if (route == AppRoute.Antennas && instanceCapabilities.canUseAntennas && antennaState.antennas.isEmpty()) {
            antennaStateHolder.refreshAntennas()
        }
        if (route == AppRoute.Clips && instanceCapabilities.canUseClips && clipState.clips.isEmpty()) {
            clipStateHolder.refreshClips()
        }
        if (route == AppRoute.Channels && channelState.channels.isEmpty()) {
            channelStateHolder.refreshChannels()
        }
        if (route == AppRoute.Pages && pageState.pages.isEmpty()) {
            pageStateHolder.refreshPages()
        }
        if (route == AppRoute.Gallery && galleryState.posts.isEmpty()) {
            galleryStateHolder.refreshPosts()
        }
        if (route == AppRoute.Flash && flashState.flashes.isEmpty()) {
            flashStateHolder.refreshFlashes()
        }
        if (route == AppRoute.Announcements && announcementState.announcements.isEmpty()) {
            announcementStateHolder.refresh()
        }
        if (
            route == AppRoute.AdminDashboard &&
            !adminState.isLoading &&
            adminState.reports.isEmpty() &&
            adminState.users.isEmpty() &&
            adminState.roles.isEmpty() &&
            adminState.announcements.isEmpty() &&
            adminState.errorMessage == null
        ) {
            adminStateHolder.refresh()
        }
        val currentRoute = route
        if (currentRoute is AppRoute.NoteDetail) {
            noteDetailStateHolder.load(currentRoute.noteId)
        }
        if (currentRoute is AppRoute.UserProfile) {
            viewedUserId = currentRoute.userId
            viewedProfileStateHolder.load(clearContent = true)
        }
        if (currentRoute is AppRoute.UserSocial) {
            userSocialStateHolder.load(
                userId = currentRoute.userId,
                kind = currentRoute.kind,
                displayName = currentRoute.displayName,
            )
        }
        if (currentRoute is AppRoute.Compose) {
            if (currentRoute.replyToId != null) {
                composeStateHolder.startReply(currentRoute.replyToId)
            } else if (currentRoute.renoteId != null) {
                composeStateHolder.startQuote(currentRoute.renoteId)
            } else if (currentRoute.channelId != null) {
                composeStateHolder.startChannelNote(currentRoute.channelId)
            } else {
                composeStateHolder.startNewNote()
            }
        }
    }

    LaunchedEffect(composeState.createdNoteId) {
        if (composeState.createdNoteId != null) {
            val channelId = composeState.draft.channelId?.takeIf { it.isNotBlank() }
            composeStateHolder.consumeCreatedNote()
            if (channelId != null) {
                rootRoute = RootRoute.Discover
                route = AppRoute.Channels
                channelStateHolder.refreshTimeline()
            } else {
                rootRoute = RootRoute.Timeline
                route = AppRoute.Timeline
                timelineStateHolder.refresh(TimelineKind.Home)
            }
        }
    }

    val onReplyNote: (String) -> Unit = { noteId ->
        route = AppRoute.Compose(replyToId = noteId)
    }
    fun applyNoteMutation(mutation: NoteLocalMutation) {
        timelineStateHolder.applyNoteMutation(mutation)
        noteDetailStateHolder.applyNoteMutation(mutation)
        userProfileStateHolder.applyNoteMutation(mutation)
        viewedProfileStateHolder.applyNoteMutation(mutation)
        discoverStateHolder.applyNoteMutation(mutation)
        favoriteNoteStateHolder.applyNoteMutation(mutation)
        userListStateHolder.applyNoteMutation(mutation)
        antennaStateHolder.applyNoteMutation(mutation)
        clipStateHolder.applyNoteMutation(mutation)
        channelStateHolder.applyNoteMutation(mutation)
    }
    fun findLoadedNote(noteId: String): Note? {
        return loadedNotes.firstOrNull { it.id == noteId }
    }
    fun latestAiResultFor(vararg kinds: AiTaskKind): AiTask? {
        val result = aiState.latestResult ?: return null
        return result.takeIf { task -> task.resultText.isNotBlank() && task.kind in kinds }
    }
    fun requestAiTask(kind: AiTaskKind, input: AiTaskInput, toast: String) {
        val task = aiStateHolder.request(kind, input)
        if (task != null) {
            noteActionToast = toast
        }
    }

    fun automationKnownUsers(): List<User> {
        return buildList {
            accountUser?.toDomainUser(host = currentAccountHost)?.let { add(it) }
            addAll(chatState.userConversations.map { it.user })
            addAll(chatState.messages.map { it.fromUser })
            addAll(chatState.messages.mapNotNull { it.toUser })
            addAll(chatState.members.map { it.user })
            addAll(loadedNotes.map { it.author })
            addAll(notificationState.notifications.map { it.actor })
            userProfileState.user?.let { add(it) }
            viewedProfileState.user?.let { add(it) }
        }.distinctBy { it.id }.take(120)
    }

    fun automationKnownChannels(): List<Channel> {
        return buildList {
            addAll(channelState.channels)
            channelState.selectedChannel?.let { add(it) }
        }.distinctBy { it.id }.take(80)
    }

    suspend fun loadAutomationRoomsForResolve(): List<cc.hhhl.client.model.ChatRoom> {
        val joined = when (val result = chatRepository.refresh()) {
            is ChatRepositoryResult.Success -> result.rooms
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error -> emptyList()
        }
        val owned = when (val result = chatRepository.refreshOwnedRooms()) {
            is ChatRepositoryResult.Success -> result.rooms
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error -> emptyList()
        }
        return (chatState.rooms + chatState.ownedRooms + joined + owned).distinctBy { it.id }.take(160)
    }

    suspend fun loadAutomationChannelsForResolve(): List<Channel> {
        val loaded = ChannelListKind.entries.flatMap { kind ->
            when (val result = automationChannelRepository.refreshChannels(kind)) {
                is ChannelsRepositoryResult.Success -> result.channels
                ChannelsRepositoryResult.Unauthorized,
                is ChannelsRepositoryResult.Error -> emptyList()
            }
        }
        return (automationKnownChannels() + loaded).distinctBy { it.id }.take(160)
    }

    suspend fun searchAutomationUsers(query: String): List<User> {
        return when (val result = automationDiscoverRepository.searchUsers(query)) {
            is DiscoverRepositoryResult.UserSuccess -> result.users
            DiscoverRepositoryResult.Unauthorized,
            is DiscoverRepositoryResult.Error,
            is DiscoverRepositoryResult.Success,
            is DiscoverRepositoryResult.PinnedUsersSuccess,
            is DiscoverRepositoryResult.RoleSuccess,
            is DiscoverRepositoryResult.RoleDetailSuccess,
            is DiscoverRepositoryResult.RoleUsersSuccess,
            is DiscoverRepositoryResult.RoleNotesSuccess,
            is DiscoverRepositoryResult.TrendSuccess,
            is DiscoverRepositoryResult.HashtagSuccess,
            is DiscoverRepositoryResult.FederationSuccess,
            is DiscoverRepositoryResult.FederationInstanceSuccess,
            is DiscoverRepositoryResult.FederationFollowSuccess,
            is DiscoverRepositoryResult.FederationStatsSuccess,
            DiscoverRepositoryResult.FederationActionSuccess -> emptyList()
        }
    }

    fun automationContextText(state: AutomationUiState): String {
        return buildString {
            appendLine("现有规则：${state.rules.size} 条，启用 ${state.enabledRuleCount} 条")
            state.rules.take(12).forEachIndexed { index, rule ->
                appendLine("规则 ${index + 1}：${rule.name} · ${rule.trigger.label} · ${rule.conditionMode.label} · ${rule.actionMode.label}")
                if (rule.conditions.isEmpty()) {
                    appendLine("  条件：无")
                } else {
                    appendLine("  条件：${rule.conditions.joinToString("；") { "${it.type.label}=${it.value.ifBlank { "未填写" }}" }}")
                }
                if (rule.actions.isEmpty()) {
                    appendLine("  动作：无")
                } else {
                    appendLine("  动作：${rule.actions.joinToString("；") { "${it.type.label}->${it.targetId.ifBlank { "本地" }}" }}")
                }
            }
            if (state.logs.isNotEmpty()) {
                appendLine()
                appendLine("最近执行日志：")
                state.logs.take(10).forEach { log ->
                    appendLine("- ${log.eventLabel} · ${log.actionLabel} · ${if (log.success) "成功" else "失败"}：${log.message}")
                }
            }
            appendLine()
            appendLine("可用触发器：${AutomationTrigger.entries.joinToString("、") { it.label }}")
            appendLine("可用条件：${AutomationConditionType.entries.joinToString("、") { it.label }}")
            appendLine("可用动作：${AutomationActionType.entries.joinToString("、") { it.label }}")
            appendLine("工具权限：${if (aiState.settings.toolsAllowed) "已开启" else "未开启"}")
            appendLine()
            appendLine("已加载聊天室（名称 -> ID）：")
            (chatState.rooms + chatState.ownedRooms).distinctBy { it.id }.take(40).forEach { room ->
                appendLine("- ${room.name} -> ${room.id}")
            }
            appendLine("已加载用户（名称 / 用户名 -> ID）：")
            automationKnownUsers().take(60).forEach { user ->
                appendLine("- ${user.displayName.ifBlank { user.username }} / @${user.username}${user.host?.let { "@$it" }.orEmpty()} -> ${user.id}")
            }
            appendLine("已加载频道（名称 -> ID）：")
            automationKnownChannels().take(40).forEach { channel ->
                appendLine("- ${channel.name} -> ${channel.id}")
            }
        }
    }

    fun requestComposeAi(kind: AiTaskKind, draft: ComposeDraft, targetNote: Note?) {
        val recentNotes = if (kind == AiTaskKind.ComposeFromRecentPosts) {
            loadedNotes.take(24)
        } else {
            emptyList()
        }
        if (draft.text.isBlank() && targetNote == null && recentNotes.isEmpty()) {
            noteActionToast = "先输入草稿再使用 AI"
            return
        }
        val allowSensitiveUpload = aiState.settings.uploadSensitiveContentAllowed
        requestAiTask(
            kind = kind,
            input = AiTaskInput(
                text = draft.text,
                title = draft.cw.orEmpty(),
                noteId = targetNote?.id.orEmpty(),
                noteAuthor = targetNote?.author?.displayName?.ifBlank { targetNote.author.username }.orEmpty(),
                noteText = targetNote?.let { notePreviewText(it, fallback = "") }.orEmpty(),
                quotedNoteText = targetNote?.quotedNote?.let { quoted ->
                    "${quoted.author.displayName.ifBlank { quoted.author.username }}: ${notePreviewText(quoted, fallback = "")}"
                }.orEmpty(),
                timelineTitle = "最近帖子",
                timelineNotes = recentNotes.toAiPostContexts(allowSensitiveUpload),
            ),
            toast = if (kind == AiTaskKind.ComposeFromRecentPosts) "AI 正在结合最近帖子生成" else "AI 已开始处理草稿",
        )
    }
    fun requestPostAi(kind: AiTaskKind, note: Note, openCompose: Boolean = false) {
        if (openCompose) {
            route = AppRoute.Compose(replyToId = note.id)
        }
        val allowSensitiveUpload = aiState.settings.uploadSensitiveContentAllowed
        requestAiTask(
            kind = kind,
            input = note.toAiTaskInput(allowSensitiveUpload),
            toast = if (openCompose) "AI 正在生成回复草稿" else "AI 正在总结帖子",
        )
    }
    fun requestThreadAi(kind: AiTaskKind, detail: NoteDetailUiState, openCompose: Boolean = false) {
        val rootNote = detail.note
        if (rootNote == null) {
            noteActionToast = "当前帖子还没加载完成"
            return
        }
        if (openCompose) {
            route = AppRoute.Compose(replyToId = rootNote.id)
        }
        val threadNotes = buildList {
            add(rootNote)
            addAll(detail.conversationNotes)
            addAll(detail.replies)
            detail.childRepliesByParentId.values.forEach { addAll(it) }
        }
            .distinctBy { it.id }
            .take(80)
        requestAiTask(
            kind = kind,
            input = AiTaskInput(
                title = "${rootNote.author.displayName.ifBlank { rootNote.author.username }} 的帖子线程",
                timelineTitle = "帖子线程 · ${detail.replies.size} 条回复",
                noteId = rootNote.id,
                noteAuthor = rootNote.author.displayName.ifBlank { rootNote.author.username },
                noteText = rootNote.toAiReadableText(aiState.settings.uploadSensitiveContentAllowed),
                timelineNotes = threadNotes.toAiPostContexts(aiState.settings.uploadSensitiveContentAllowed),
            ),
            toast = if (openCompose) "AI 正在生成线程回复" else "AI 正在总结线程",
        )
    }
    fun requestTimelineAi(kind: AiTaskKind, timelineKind: TimelineKind, notes: List<Note>) {
        val readableNotes = notes.take(40)
        if (readableNotes.isEmpty()) {
            noteActionToast = "当前时间线还没有可分析的内容"
            return
        }
        requestAiTask(
            kind = kind,
            input = AiTaskInput(
                title = timelineKind.label,
                timelineTitle = timelineKind.label,
                timelineNotes = readableNotes.toAiPostContexts(aiState.settings.uploadSensitiveContentAllowed),
            ),
            toast = "AI 已开始整理时间线",
        )
    }
    fun requestChatAi(kind: AiTaskKind, chat: ChatUiState, title: String) {
        if (chat.selectedUserConversation != null && !aiState.settings.readPrivateChatAllowed) {
            noteActionToast = "AI 未获得私聊读取权限"
            return
        }
        if (chat.messages.isEmpty()) {
            noteActionToast = "当前聊天还没有可分析的消息"
            return
        }
        requestAiTask(
            kind = kind,
            input = AiTaskInput(
                text = chat.messageDraft,
                chatTitle = title,
                chatMessages = chat.messages.takeLast(80).map { message ->
                    message.copy(text = chatMessageBodyText(message))
                }.toAiChatMessageContexts(),
            ),
            toast = "AI 已开始处理聊天",
        )
    }
    fun requestNotificationAi(kind: AiTaskKind, notifications: List<NotificationItem>, filter: NotificationFilter) {
        if (notifications.isEmpty()) {
            noteActionToast = "当前通知列表为空"
            return
        }
        requestAiTask(
            kind = kind,
            input = AiTaskInput(
                title = filter.label,
                notifications = notifications.take(80).toAiNotificationContexts(),
            ),
            toast = "AI 已开始整理通知",
        )
    }
    fun requestProfileAi(kind: AiTaskKind, profileState: UserProfileUiState, ownProfile: Boolean) {
        val user = profileState.user
        if (user == null) {
            noteActionToast = "资料还没加载完成"
            return
        }
        val relationshipText = buildList {
            if (ownProfile) add("这是我自己的资料")
            if (user.isFollowing) add("已关注")
            profileState.relationship?.let { relationship ->
                if (relationship.isMuted) add("已静音")
                if (relationship.isBlocking) add("已屏蔽")
                if (relationship.isFollowed) add("对方关注我")
            }
            if (specialCareStateHolder.isSpecialCare(user.id)) add("特别关心")
        }.joinToString(" · ")
        requestAiTask(
            kind = kind,
            input = AiTaskInput(
                title = user.displayName.ifBlank { user.username },
                profile = user.toAiProfileContext(relationshipText),
                timelineTitle = "${user.displayName.ifBlank { user.username }} 的最近帖子",
                timelineNotes = (user.pinnedNotes + profileState.notes)
                    .distinctBy { it.id }
                    .take(40)
                    .toAiPostContexts(aiState.settings.uploadSensitiveContentAllowed),
            ),
            toast = "AI 已开始分析资料",
        )
    }
    fun requestAutomationExplain(rule: AutomationRule?) {
        if (rule == null) {
            noteActionToast = "先选择一条自动化规则"
            return
        }
        val eventText = buildString {
            appendLine("规则：${rule.name}")
            appendLine("触发器：${rule.trigger.label}")
            appendLine("条件关系：${rule.conditionMode.label}")
            rule.conditions.forEachIndexed { index, condition ->
                appendLine("条件 ${index + 1}：${condition.type.label} = ${condition.value.ifBlank { "未填写" }}")
            }
            appendLine("动作执行：${rule.actionMode.label}")
            rule.actions.forEachIndexed { index, action ->
                appendLine("动作 ${index + 1}：${action.type.label} -> ${action.targetId.ifBlank { "本地" }}")
                if (action.bodyTemplate.isNotBlank()) appendLine("模板：${action.bodyTemplate}")
            }
        }
        requestAiTask(
            kind = AiTaskKind.AutomationExplain,
            input = AiTaskInput(
                title = rule.name,
                prompt = "解释这条规则的匹配逻辑、风险和可以加强的地方。",
                automationEventText = eventText,
            ),
            toast = "AI 已开始解释规则",
        )
    }
    fun requestAutomationRuleSuggestions(state: AutomationUiState) {
        val contextText = automationContextText(state)
        requestAiTask(
            kind = AiTaskKind.AutomationRuleSuggestions,
            input = AiTaskInput(
                title = "自动化规则建议",
                prompt = "结合当前 HHHL 使用场景，建议哪些规则能减少手动处理、提醒和回调整理。",
                automationEventText = contextText,
            ),
            toast = "AI 已开始生成规则建议",
        )
    }

    fun requestAutomationRuleDraft(description: String, state: AutomationUiState) {
        val cleanDescription = description.trim()
        if (cleanDescription.isBlank()) {
            noteActionToast = "先描述想自动完成的事"
            return
        }
        appScope.launch {
            noteActionToast = "AI 正在生成规则草稿"
            when (val result = aiStateHolder.runBlockingTask(
                AiTaskKind.AutomationRuleDraft,
                AiTaskInput(
                    prompt = cleanDescription,
                    automationEventText = automationContextText(state),
                ),
            )) {
                is AiRepositoryResult.Success -> {
                    val parsed = parseAutomationRuleDraft(result.text)
                    val rule = parsed.rule
                    if (rule == null) {
                        noteActionToast = parsed.errorMessage ?: "AI 规则草稿解析失败"
                    } else {
                        val resolved = resolveAutomationRuleDraft(
                            rule = rule,
                            input = AutomationRuleDraftResolveInput(
                                rooms = (chatState.rooms + chatState.ownedRooms).distinctBy { it.id },
                                users = automationKnownUsers(),
                                channels = automationKnownChannels(),
                                searchUsers = ::searchAutomationUsers,
                                loadRooms = ::loadAutomationRoomsForResolve,
                                loadChannels = ::loadAutomationChannelsForResolve,
                            ),
                        )
                        automationStateHolder.addRuleDraft(resolved.rule.copy(enabled = resolved.rule.enabled))
                        noteActionToast = buildString {
                            append(if (resolved.rule.enabled) "AI 已生成并启用规则草稿" else "AI 已生成规则草稿")
                            resolved.messages.take(4).takeIf { it.isNotEmpty() }?.let { messages ->
                                append("：")
                                append(messages.joinToString("；"))
                            }
                        }
                    }
                }
                AiRepositoryResult.Unauthorized -> noteActionToast = "AI API Key 无效或权限不足"
                is AiRepositoryResult.Error -> noteActionToast = result.message
            }
        }
    }
    fun requestWorkspaceActionPlan() {
        val settings = aiState.settings
        val allowSensitiveUpload = settings.uploadSensitiveContentAllowed
        val timelineNotes = if (settings.readTimelineAllowed) {
            timelineState.tabs[timelineState.selectedKind]?.notes.orEmpty().take(25)
        } else {
            emptyList()
        }
        val notificationContexts = if (settings.readNotificationsAllowed) {
            notificationState.notifications.take(30).toAiNotificationContexts()
        } else {
            emptyList()
        }
        val canReadSelectedChat = settings.readChatAllowed &&
            (chatState.selectedUserConversation == null || settings.readPrivateChatAllowed)
        val chatContexts = if (canReadSelectedChat) {
            chatState.messages.takeLast(40).map { message ->
                message.copy(text = chatMessageBodyText(message))
            }.toAiChatMessageContexts()
        } else {
            emptyList()
        }
        val automationText = if (settings.automationAllowed) {
            buildString {
                appendLine("自动化：${automationState.rules.size} 条规则，启用 ${automationState.enabledRuleCount} 条")
                automationState.logs.take(8).forEach { log ->
                    appendLine("- ${log.eventLabel} · ${log.actionLabel} · ${if (log.success) "成功" else "失败"}：${log.message}")
                }
            }
        } else {
            "自动化上下文未授权"
        }
        val contextText = buildString {
            appendLine("当前账号：${accountUser?.displayName?.ifBlank { accountUser.username } ?: "未登录"}")
            appendLine("当前页面：${routeLabel(route)}")
            appendLine("权限：帖子=${settings.readTimelineAllowed}，通知=${settings.readNotificationsAllowed}，聊天=${settings.readChatAllowed}，私聊=${settings.readPrivateChatAllowed}，草稿=${settings.readDraftsAllowed}，自动化=${settings.automationAllowed}，工具=${settings.toolsAllowed}")
            appendLine("后台：${if (settings.backgroundAllowed) "可继续处理" else "已关闭"}${if (settings.wifiOnlyBackground) " · 仅 Wi-Fi" else ""}")
            appendLine("今日用量：${aiState.usedDailyRequests}/${settings.dailyRequestLimit}，剩余 ${aiState.remainingDailyRequests}")
            if (composeState.draft.text.isNotBlank() && settings.readDraftsAllowed) {
                appendLine()
                appendLine("当前草稿：")
                appendLine(composeState.draft.text.take(1_200))
            }
            if (timelineNotes.isNotEmpty()) {
                appendLine()
                appendLine("当前时间线：${timelineState.selectedKind.label}")
                timelineNotes.toAiPostContexts(allowSensitiveUpload).forEachIndexed { index, note ->
                    appendLine("${index + 1}. ${note.author} @${note.username}：${note.text}")
                    if (note.stats.isNotBlank()) appendLine("   ${note.stats}")
                }
            }
            if (notificationContexts.isNotEmpty()) {
                appendLine()
                appendLine("当前通知：${notificationState.selectedFilter.label} · 未读 ${notificationState.unreadCount}")
                notificationContexts.forEachIndexed { index, item ->
                    appendLine("${index + 1}. ${item.type} · ${item.actor}：${item.text}")
                    if (item.notePreviewText.isNotBlank()) appendLine("   ${item.notePreviewText}")
                }
            }
            if (chatContexts.isNotEmpty()) {
                appendLine()
                appendLine("当前聊天：${selectedChatTitle(chatState)}")
                chatContexts.forEach { message ->
                    appendLine("${message.sender}: ${message.text}")
                }
            } else if (chatState.selectedUserConversation != null && settings.readChatAllowed && !settings.readPrivateChatAllowed) {
                appendLine()
                appendLine("当前私聊未上传：私聊读取权限未开启")
            }
            appendLine()
            appendLine(automationText)
        }
        requestAiTask(
            kind = AiTaskKind.WorkspaceActionPlan,
            input = AiTaskInput(
                title = "全局行动计划",
                prompt = "从当前 HHHL 状态里找出最值得我下一步处理、回复、@ 人、整理和自动化的事情。",
                automationEventText = contextText,
            ),
            toast = "AI 已开始生成全局行动计划",
        )
    }
    val onRenoteNote: (String) -> Unit = { noteId ->
        applyNoteMutation(NoteLocalMutation.Renote(noteId))
        noteActionStateHolder.perform(NoteActionRequest.Renote(noteId))
    }
    val onQuoteNote: (String) -> Unit = { noteId ->
        route = AppRoute.Compose(renoteId = noteId)
    }
    val onReactNote: (String, String) -> Unit = { noteId, reaction ->
        applyNoteMutation(NoteLocalMutation.React(noteId, reaction))
        noteActionStateHolder.perform(NoteActionRequest.React(noteId, reaction))
    }
    val onDeleteReactionNote: (String, String) -> Unit = { noteId, reaction ->
        applyNoteMutation(NoteLocalMutation.DeleteReaction(noteId, reaction))
        noteActionStateHolder.perform(NoteActionRequest.DeleteReaction(noteId))
    }
    val onFavoriteNote: (String) -> Unit = { noteId ->
        val loadedNote = findLoadedNote(noteId)
        val isFavorited = loadedNote?.isFavorited == true
        applyNoteMutation(
            if (isFavorited) {
                NoteLocalMutation.Unfavorite(noteId)
            } else {
                NoteLocalMutation.Favorite(noteId)
            },
        )
        if (!isFavorited && loadedNote != null) {
            favoriteNoteStateHolder.addLocalFavorite(loadedNote)
        }
        noteActionStateHolder.perform(
            if (isFavorited) {
                NoteActionRequest.Unfavorite(noteId)
            } else {
                NoteActionRequest.Favorite(noteId)
            },
        )
    }
    val onDeleteNote: (String) -> Unit = { noteId ->
        applyNoteMutation(NoteLocalMutation.Delete(noteId))
        noteActionStateHolder.perform(NoteActionRequest.Delete(noteId))
    }
    val onHideNoteFromList: (String) -> Unit = { noteId ->
        applyNoteMutation(NoteLocalMutation.Delete(noteId))
    }
    val onMuteNote: (String) -> Unit = { noteId ->
        noteActionStateHolder.perform(NoteActionRequest.Mute(noteId))
    }
    val onUnmuteNote: (String) -> Unit = { noteId ->
        noteActionStateHolder.perform(NoteActionRequest.Unmute(noteId))
    }
    val onMuteRenotes: (String, String) -> Unit = { noteId, userId ->
        noteActionStateHolder.perform(NoteActionRequest.MuteRenotes(noteId, userId))
    }
    val onUnmuteRenotes: (String, String) -> Unit = { noteId, userId ->
        noteActionStateHolder.perform(NoteActionRequest.UnmuteRenotes(noteId, userId))
    }
    val onReportNote: (String, String) -> Unit = { noteId, userId ->
        noteActionStateHolder.perform(
            NoteActionRequest.Report(
                noteId = noteId,
                userId = userId,
            ),
        )
    }
    val onVotePoll: (String, Int) -> Unit = { noteId, choice ->
        applyNoteMutation(NoteLocalMutation.VotePoll(noteId, choice))
        noteActionStateHolder.perform(NoteActionRequest.VotePoll(noteId, choice))
    }
    val isNoteActionPending: (String) -> Boolean = { noteId ->
        noteActionState.pendingNoteIds.contains(noteId)
    }
    val canDeleteAuthor: (String) -> Boolean = { authorId ->
        authorId == accountUser?.id
    }
    val onOpenUser: (String) -> Unit = { userId ->
        if (userId == accountUser?.id) {
            rootRoute = RootRoute.Profile
            route = AppRoute.Profile
        } else {
            route = AppRoute.UserProfile(userId)
        }
    }
    val openDiscoverSearch: () -> Unit = {
        rootRoute = RootRoute.Discover
        route = AppRoute.Discover
    }
    val onOpenMention: (String) -> Unit = { username ->
        appScope.launch {
            when (
                val target = mentionNavigationTarget(
                    username = username,
                    result = mentionResolverRepository.resolveMention(username),
                )
            ) {
                is MentionNavigationTarget.UserProfile -> onOpenUser(target.userId)
                is MentionNavigationTarget.DiscoverSearch -> {
                    openDiscoverSearch()
                    discoverStateHolder.openMention(target.username)
                }
            }
        }
    }
    val onOpenHashtag: (String) -> Unit = { tag ->
        openDiscoverSearch()
        discoverStateHolder.openHashtag(tag)
    }
    fun navigateTo(routeTarget: AppRoute) {
        if (routeTarget is AppRoute.UserProfile && routeTarget.userId != accountUser?.id) {
            route = routeTarget
        } else {
            rootRoute = rootRouteFor(routeTarget)
            route = routeTarget
        }
    }
    fun navigateToChannels(channelId: String? = null) {
        navigateTo(AppRoute.Channels)
        channelId
            ?.let { id -> channelState.channels.firstOrNull { it.id == id } }
            ?.let(channelStateHolder::selectChannel)
    }
    fun navigateToPage(pageId: String? = null) {
        navigateTo(AppRoute.Pages)
        pageId?.let(pageStateHolder::openPage)
    }
    fun navigateToPagePath(
        username: String,
        name: String,
    ) {
        navigateTo(AppRoute.Pages)
        pageStateHolder.openPageByPath(username, name)
    }
    fun navigateToGallery(postId: String? = null) {
        navigateTo(AppRoute.Gallery)
        postId?.let(galleryStateHolder::openPost)
    }
    fun navigateToFlash(flashId: String? = null) {
        navigateTo(AppRoute.Flash)
        flashId?.let(flashStateHolder::openFlash)
    }
    fun navigateToAnnouncements(announcementId: String? = null) {
        navigateTo(AppRoute.Announcements)
        announcementId?.let(announcementStateHolder::openAnnouncement)
    }
    val onOpenUrl: (String) -> Unit = { url ->
        when (val target = siteLinkNavigationTarget(url)) {
            is SiteLinkNavigationTarget.NoteDetail -> navigateTo(AppRoute.NoteDetail(target.noteId))
            is SiteLinkNavigationTarget.Mention -> onOpenMention(target.username)
            is SiteLinkNavigationTarget.UserProfile -> onOpenUser(target.userId)
            SiteLinkNavigationTarget.Channels -> navigateToChannels()
            is SiteLinkNavigationTarget.ChannelDetail -> navigateToChannels(target.channelId)
            SiteLinkNavigationTarget.Pages -> navigateToPage()
            is SiteLinkNavigationTarget.PageDetail -> navigateToPage(target.pageId)
            is SiteLinkNavigationTarget.PagePath -> navigateToPagePath(target.username, target.name)
            SiteLinkNavigationTarget.Gallery -> navigateToGallery()
            is SiteLinkNavigationTarget.GalleryPostDetail -> navigateToGallery(target.postId)
            SiteLinkNavigationTarget.Flash -> navigateToFlash()
            is SiteLinkNavigationTarget.FlashDetail -> navigateToFlash(target.flashId)
            SiteLinkNavigationTarget.Announcements -> navigateToAnnouncements()
            is SiteLinkNavigationTarget.AnnouncementDetail -> navigateToAnnouncements(target.announcementId)
            SiteLinkNavigationTarget.Chat -> navigateTo(AppRoute.Chat)
            is SiteLinkNavigationTarget.ChatRoom -> {
                navigateTo(AppRoute.Chat)
                chatStateHolder.openRoomById(target.roomId)
            }
            is SiteLinkNavigationTarget.ChatUser -> {
                navigateTo(AppRoute.Chat)
                val user =
                    chatState.userConversations.firstOrNull { it.user.id == target.userId }?.user
                        ?: viewedProfileState.user?.takeIf { it.id == target.userId }
                        ?: userProfileState.user?.takeIf { it.id == target.userId }
                        ?: cc.hhhl.client.model.User(
                            id = target.userId,
                            displayName = target.userId,
                            username = target.userId,
                            avatarInitial = target.userId.trim().firstOrNull()?.toString()?.uppercase() ?: "?",
                        )
                chatStateHolder.openUserConversation(user)
            }
            SiteLinkNavigationTarget.Drive -> navigateTo(AppRoute.Drive)
            SiteLinkNavigationTarget.Achievements -> navigateTo(AppRoute.Achievements)
            SiteLinkNavigationTarget.FavoriteNotes -> navigateTo(AppRoute.FavoriteNotes)
            SiteLinkNavigationTarget.UserLists -> navigateTo(AppRoute.UserLists)
            is SiteLinkNavigationTarget.UserListDetail -> {
                navigateTo(AppRoute.UserLists)
                userListState.lists.firstOrNull { it.id == target.listId }?.let(userListStateHolder::selectList)
                    ?: userListStateHolder.refreshLists()
            }
            SiteLinkNavigationTarget.FollowRequests -> navigateTo(AppRoute.FollowRequests)
            SiteLinkNavigationTarget.RelationshipManagement -> navigateTo(AppRoute.RelationshipManagement)
            SiteLinkNavigationTarget.Antennas -> navigateTo(AppRoute.Antennas)
            is SiteLinkNavigationTarget.AntennaDetail -> {
                navigateTo(AppRoute.Antennas)
                antennaState.antennas.firstOrNull { it.id == target.antennaId }?.let(antennaStateHolder::selectAntenna)
                    ?: antennaStateHolder.refreshAntennas()
            }
            SiteLinkNavigationTarget.Clips -> navigateTo(AppRoute.Clips)
            is SiteLinkNavigationTarget.ClipDetail -> {
                navigateTo(AppRoute.Clips)
                clipState.clips.firstOrNull { it.id == target.clipId }?.let(clipStateHolder::selectClip)
                    ?: clipStateHolder.refreshClips()
            }
            SiteLinkNavigationTarget.Settings -> navigateTo(AppRoute.Settings)
            is SiteLinkNavigationTarget.SettingsManagement -> {
                navigateTo(AppRoute.SettingsManagement(target.key))
                settingsStateHolder.openManagement(target.key)
            }
            SiteLinkNavigationTarget.Profile -> navigateTo(AppRoute.Profile)
            is SiteLinkNavigationTarget.External -> openUrl(target.url)
        }
    }
    val onAcceptFollowRequestFromNotification: (String) -> Unit = { userId ->
        followRequestStateHolder.accept(userId)
        notificationStateHolder.dismissFollowRequestNotification(userId)
    }
    val onRejectFollowRequestFromNotification: (String) -> Unit = { userId ->
        followRequestStateHolder.reject(userId)
        notificationStateHolder.dismissFollowRequestNotification(userId)
    }
    val onOpenChatFromNotification: () -> Unit = {
        rootRoute = RootRoute.Chat
        route = AppRoute.Chat
        chatStateHolder.refresh()
    }
    val onOpenChatUser: (cc.hhhl.client.model.User) -> Unit = { user ->
        rootRoute = RootRoute.Chat
        route = AppRoute.Chat
        chatStateHolder.openUserConversation(user)
    }
    val onOpenChatUserById: (String, String?) -> Unit = { userId, messageId ->
        val cleanUserId = userId.trim()
        if (cleanUserId.isNotEmpty()) {
            val user =
                chatState.userConversations.firstOrNull { it.user.id == cleanUserId }?.user
                ?: viewedProfileState.user?.takeIf { it.id == cleanUserId }
                ?: userProfileState.user?.takeIf { it.id == cleanUserId }
                ?: cc.hhhl.client.model.User(
                    id = cleanUserId,
                    displayName = cleanUserId,
                    username = cleanUserId,
                    avatarInitial = cleanUserId.firstOrNull()?.toString()?.uppercase() ?: "聊",
                )
            rootRoute = RootRoute.Chat
            route = AppRoute.Chat
            chatStateHolder.openUserConversation(user, jumpMessageId = messageId)
        }
    }
    var clipTargetNote by remember { mutableStateOf<Note?>(null) }
    LaunchedEffect(instanceMetaState.meta?.capabilities?.clipLimit) {
        if (!instanceCapabilities.canUseClips) {
            clipTargetNote = null
            if (route == AppRoute.Clips) {
                rootRoute = RootRoute.Profile
                route = AppRoute.Profile
            }
        }
    }
    val onRequestAddToClip: ((Note) -> Unit)? = if (instanceCapabilities.canUseClips && !clipState.isChangingClipNote) {
        { note ->
            clipTargetNote = note
            if (clipState.selectedKind != ClipListKind.Owned || clipState.clips.isEmpty()) {
                clipStateHolder.refreshClips(ClipListKind.Owned)
            }
        }
    } else {
        null
    }
    val noteCallbacks = NoteInteractionCallbacks(
        onOpenNote = { route = AppRoute.NoteDetail(it) },
        onOpenUser = onOpenUser,
        onReply = onReplyNote,
        onRenote = onRenoteNote,
        onQuote = onQuoteNote,
        onReact = onReactNote,
        onDeleteReaction = onDeleteReactionNote,
        onFavorite = onFavoriteNote,
        onAddToClip = onRequestAddToClip,
        onDelete = onDeleteNote,
        onOpenMedia = onOpenUrl,
        onOpenMediaPreview = { mediaPreviewSession = it },
        onOpenMention = onOpenMention,
        onOpenHashtag = onOpenHashtag,
        onVotePoll = onVotePoll,
        isActionPending = isNoteActionPending,
        canDeleteAuthor = canDeleteAuthor,
        noteRowDensity = noteRowDensity,
    )
    val timelineListStates = remember {
        TimelineKind.values().associateWith { LazyListState() }
    }
    val notificationListState = remember { LazyListState() }
    val announcementListState = remember { LazyListState() }
    val discoverListStates = remember {
        cc.hhhl.client.state.DiscoverSearchMode.entries.associateWith { LazyListState() }
    }
    fun handleSystemBack(): Boolean {
        if (mediaPreviewSession != null) {
            mediaPreviewSession = null
            return true
        }
        if (drivePickerTarget != null) {
            drivePickerTarget = null
            return true
        }
        return when (val current = route) {
            AppRoute.Timeline -> {
                if (timelineTrendSelected) {
                    timelineTrendSelected = false
                    true
                } else {
                    false
                }
            }
            AppRoute.Discover,
            AppRoute.Notifications,
            AppRoute.Profile -> false
            AppRoute.Chat -> {
                if (chatState.selectedRoom != null || chatState.selectedUserConversation != null) {
                    chatStateHolder.closeRoom()
                    true
                } else {
                    false
                }
            }
            AppRoute.ProfileNotes,
            AppRoute.Drive,
            AppRoute.Achievements,
            AppRoute.FavoriteNotes,
            AppRoute.UserLists,
            AppRoute.FollowRequests,
            AppRoute.RelationshipManagement,
            AppRoute.Antennas,
            AppRoute.Clips,
            AppRoute.Settings -> {
                route = AppRoute.Profile
                rootRoute = RootRoute.Profile
                true
            }
            AppRoute.ThemeCustomization -> {
                route = AppRoute.Settings
                rootRoute = RootRoute.Profile
                true
            }
            AppRoute.Automation -> {
                route = AppRoute.Profile
                rootRoute = RootRoute.Profile
                true
            }
            AppRoute.AdminDashboard -> {
                route = AppRoute.Settings
                rootRoute = RootRoute.Profile
                true
            }
            is AppRoute.SettingsManagement -> {
                settingsStateHolder.closeManagement()
                route = AppRoute.Settings
                rootRoute = RootRoute.Profile
                true
            }
            AppRoute.Channels,
            AppRoute.Pages,
            AppRoute.Gallery,
            AppRoute.Flash,
            AppRoute.Announcements -> {
                route = if (rootRoute == RootRoute.Profile) AppRoute.Profile else AppRoute.Discover
                rootRoute = rootRouteFor(route)
                true
            }
            is AppRoute.UserProfile -> {
                route = if (rootRoute == RootRoute.Profile) {
                    AppRoute.Profile
                } else {
                    appRouteForRootRoute(rootRoute)
                }
                true
            }
            is AppRoute.UserSocial -> {
                route = if (current.userId == accountUser?.id) {
                    AppRoute.Profile
                } else {
                    AppRoute.UserProfile(current.userId)
                }
                rootRoute = RootRoute.Profile
                true
            }
            is AppRoute.NoteDetail,
            is AppRoute.Compose -> {
                route = AppRoute.Timeline
                rootRoute = RootRoute.Timeline
                true
            }
        }
    }
    val latestSystemBackHandler by rememberUpdatedState(newValue = ::handleSystemBack)

    DisposableEffect(onBackHandlerChanged) {
        onBackHandlerChanged { latestSystemBackHandler() }
        onDispose { onBackHandlerChanged(null) }
    }

    val onFollowUserQuick: (String) -> Unit = { userId ->
        appScope.launch {
            when (val result = quickRelationshipRepository.follow(userId)) {
                cc.hhhl.client.repository.UserRelationshipRepositoryResult.Success -> noteActionToast = "已关注"
                cc.hhhl.client.repository.UserRelationshipRepositoryResult.Unauthorized -> noteActionToast = "登录已失效，请重新登录"
                is cc.hhhl.client.repository.UserRelationshipRepositoryResult.Error -> noteActionToast = result.message
                is cc.hhhl.client.repository.UserRelationshipRepositoryResult.RelationLoaded -> noteActionToast = "已更新关注状态"
            }
        }
    }
    fun handleUserRelationshipResult(
        result: cc.hhhl.client.repository.UserRelationshipRepositoryResult,
        successMessage: String,
    ) {
        when (result) {
            cc.hhhl.client.repository.UserRelationshipRepositoryResult.Success -> noteActionToast = successMessage
            cc.hhhl.client.repository.UserRelationshipRepositoryResult.Unauthorized -> noteActionToast = "登录已失效，请重新登录"
            is cc.hhhl.client.repository.UserRelationshipRepositoryResult.Error -> noteActionToast = result.message
            is cc.hhhl.client.repository.UserRelationshipRepositoryResult.RelationLoaded -> noteActionToast = successMessage
        }
    }
    val userQuickActions = UserQuickActions(
        onFollowUser = { user ->
            appScope.launch {
                handleUserRelationshipResult(
                    result = quickRelationshipRepository.follow(user.id),
                    successMessage = "已关注 @${user.username}",
                )
            }
        },
        onMuteUser = { user ->
            appScope.launch {
                handleUserRelationshipResult(
                    result = quickRelationshipRepository.mute(user.id),
                    successMessage = "已静音 @${user.username}",
                )
            }
        },
        onBlockUser = { user ->
            appScope.launch {
                val result = quickRelationshipRepository.block(user.id)
                handleUserRelationshipResult(
                    result = result,
                    successMessage = "已屏蔽 @${user.username}",
                )
                if (result == cc.hhhl.client.repository.UserRelationshipRepositoryResult.Success) {
                    relationshipManagementStateHolder.updateBlockedUser(
                        entry = relationshipEntryForUser(user, prefix = "blocked"),
                        blocked = true,
                    )
                }
            }
        },
        onAddUserToList = { user ->
            userListQuickTarget = UserListQuickTarget(user)
            if (userListState.lists.isEmpty() && !userListState.isLoadingLists) {
                userListStateHolder.refreshLists()
            }
        },
        onToggleSpecialCareUser = { user ->
            val enabled = specialCareStateHolder.toggleSpecialCare(user.id)
            noteActionToast = if (enabled) {
                "已加入特别关心"
            } else {
                "已取消特别关心"
            }
        },
    )
    LaunchedEffect(noteActionState.message, noteActionState.errorMessage) {
        val message = noteActionState.message ?: noteActionState.errorMessage ?: return@LaunchedEffect
        noteActionToast = message
        delay(2_200)
        if (noteActionToast == message) {
            noteActionToast = null
        }
    }
    LaunchedEffect(noteActionToast) {
        val message = noteActionToast ?: return@LaunchedEffect
        delay(2_200)
        if (noteActionToast == message) {
            noteActionToast = null
        }
    }
    val blockedUserIds = relationshipManagementState.blockedUserIds
    val mutedNoteFilters = MutedNoteFilters(
        mutedWords = settingsState.remotePreferences?.filters?.mutedWords.orEmpty(),
        hardMutedWords = settingsState.remotePreferences?.filters?.hardMutedWords.orEmpty(),
    )

    CompositionLocalProvider(
        LocalCustomEmojiUrls provides noteActionState.customEmojiUrls,
        LocalBlockedNoteAuthorIds provides blockedUserIds,
        LocalMutedNoteFilters provides mutedNoteFilters,
        LocalNoteRowGesturesEnabled provides listGesturesEnabled,
        LocalUserQuickActions provides userQuickActions,
        LocalNoteRowActions provides NoteRowActions(
            onShareNote = { url -> shareUrl(url) },
            onAiSummarizeNote = { note -> requestPostAi(AiTaskKind.PostSummary, note) },
            onAiReplyDraft = { note -> requestPostAi(AiTaskKind.PostReplyDraft, note, openCompose = true) },
            onHideFromList = onHideNoteFromList,
            onMuteNote = onMuteNote,
            onUnmuteNote = onUnmuteNote,
            onMuteRenotes = onMuteRenotes,
            onUnmuteRenotes = onUnmuteRenotes,
            onReportNote = onReportNote,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    when (val current = route) {
                AppRoute.Timeline -> TimelineRouteContent(
                    state = timelineState,
                    instanceCapabilities = instanceMetaState.meta?.capabilities ?: InstanceCapabilities(),
                    discoverState = discoverState,
                    timelineListStates = timelineListStates,
                    isTrendSelected = timelineTrendSelected,
                    noteActionState = noteActionState,
                    aiState = aiState,
                    noteCallbacks = noteCallbacks,
                    onTimelineSelected = {
                        timelineTrendSelected = false
                        timelineStateHolder.select(it)
                    },
                    onRefresh = timelineStateHolder::refresh,
                    onLoadMore = timelineStateHolder::loadMore,
                    isSpecialCareAuthor = specialCareStateHolder::isSpecialCare,
                    onTrendSelected = {
                        timelineTrendSelected = true
                        discoverStateHolder.refreshTrendsQuietly()
                    },
                    onRefreshTrends = discoverStateHolder::refreshTrendsQuietly,
                    onNewNotesMarkerConsumed = timelineStateHolder::consumeNewNotesMarker,
                    onCompose = { route = AppRoute.Compose() },
                    onSearch = {
                        rootRoute = RootRoute.Discover
                        route = AppRoute.Discover
                        discoverStateHolder.selectMode(cc.hhhl.client.state.DiscoverSearchMode.Notes)
                    },
                    latestAiResultFor = { kinds -> latestAiResultFor(*kinds) },
                    onAiAction = ::requestTimelineAi,
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                )
                AppRoute.Discover -> DiscoverRouteContent(
                    state = discoverState,
                    stateHolder = discoverStateHolder,
                    noteActionState = noteActionState,
                    noteCallbacks = noteCallbacks,
                    listState = discoverListStates[discoverState.selectedMode] ?: LazyListState(),
                    onOpenChannels = { route = AppRoute.Channels },
                    onOpenPages = { route = AppRoute.Pages },
                    onOpenGallery = { route = AppRoute.Gallery },
                    onOpenFlash = { route = AppRoute.Flash },
                    onOpenAnnouncements = { route = AppRoute.Announcements },
                )
                AppRoute.Chat -> ChatRouteContent(
                    state = chatState,
                    currentUserId = accountUser?.id,
                    blockedUserIds = blockedUserIds,
                    stateHolder = chatStateHolder,
                    mediaPicker = mediaPicker,
                    onOpenUser = onOpenUser,
                    onOpenUrl = onOpenUrl,
                    onOpenMediaPreview = { mediaPreviewSession = it },
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                    customEmojis = noteActionState.customEmojis,
                    recentEmojiCodes = noteActionState.recentReactions,
                    customTheme = customTheme,
                    onOpenDrivePicker = { drivePickerTarget = DrivePickerTarget.Chat },
                    aiEnabled = aiState.hasUsableModel,
                    isAiProcessing = aiState.isProcessing,
                    aiResultText = latestAiResultFor(
                        AiTaskKind.ChatSummary,
                        AiTaskKind.ChatReplyDraft,
                        AiTaskKind.ChatActionItems,
                        AiTaskKind.ChatDecisionSummary,
                    )?.resultText,
                    aiResultLabel = latestAiResultFor(
                        AiTaskKind.ChatSummary,
                        AiTaskKind.ChatReplyDraft,
                        AiTaskKind.ChatActionItems,
                        AiTaskKind.ChatDecisionSummary,
                    )?.kind?.label,
                    onAiAction = ::requestChatAi,
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                )
                AppRoute.Drive -> DriveRouteContent(
                    state = driveFilesState,
                    stateHolder = driveFilesStateHolder,
                    mediaPicker = mediaPicker,
                    onBack = { route = AppRoute.Profile },
                    onOpenUrl = openUrl,
                    onOpenNote = { route = AppRoute.NoteDetail(it) },
                    onOpenMediaPreview = { mediaPreviewSession = it },
                )
                AppRoute.Achievements -> AchievementScreen(
                    state = achievementState,
                    onBack = { route = AppRoute.Profile },
                    onRefresh = achievementStateHolder::refresh,
                    onClaimViewMilestone = achievementStateHolder::claimViewAchievementsMilestone,
                )
                AppRoute.FavoriteNotes -> FavoriteNotesRouteContent(
                    state = favoriteNoteState,
                    stateHolder = favoriteNoteStateHolder,
                    noteActionState = noteActionState,
                    noteCallbacks = noteCallbacks,
                    onBack = { route = AppRoute.Profile },
                )
                AppRoute.Notifications -> NotificationsRouteContent(
                    state = notificationState,
                    announcementState = announcementState,
                    followRequestState = followRequestState,
                    aiState = aiState,
                    notificationListState = notificationListState,
                    announcementListState = announcementListState,
                    onRefresh = notificationStateHolder::refresh,
                    onLoadMore = notificationStateHolder::loadMore,
                    onMarkAllAsRead = notificationStateHolder::markAllAsRead,
                    onFlush = notificationStateHolder::flush,
                    onMarkNotificationRead = notificationStateHolder::markNotificationRead,
                    onFilterSelected = notificationStateHolder::selectFilter,
                    onRefreshAnnouncements = announcementStateHolder::refresh,
                    onLoadMoreAnnouncements = announcementStateHolder::loadMore,
                    onOpenAnnouncement = announcementStateHolder::openAnnouncement,
                    onCloseAnnouncement = announcementStateHolder::closeDetail,
                    onMarkAnnouncementRead = announcementStateHolder::markRead,
                    onOpenNote = { route = AppRoute.NoteDetail(it) },
                    onOpenUser = onOpenUser,
                    onReplyToNote = onReplyNote,
                    onReactToNote = onReactNote,
                    onFollowUser = onFollowUserQuick,
                    onOpenUrl = onOpenUrl,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                    onAcceptFollowRequest = onAcceptFollowRequestFromNotification,
                    onRejectFollowRequest = onRejectFollowRequestFromNotification,
                    onOpenChat = onOpenChatFromNotification,
                    onOpenChatUser = onOpenChatUserById,
                    onSendTestNotification = notificationStateHolder::sendTestNotification,
                    onSendReminderNotification = notificationStateHolder::createLocalReminderNotification,
                    latestAiResultFor = { kinds -> latestAiResultFor(*kinds) },
                    onAiAction = ::requestNotificationAi,
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                )
                AppRoute.UserLists -> UserListsRouteContent(
                    state = userListState,
                    stateHolder = userListStateHolder,
                    noteActionState = noteActionState,
                    noteCallbacks = noteCallbacks,
                    onBack = { route = AppRoute.Profile },
                )
                AppRoute.FollowRequests -> FollowRequestScreen(
                    state = followRequestState,
                    onBack = { route = AppRoute.Profile },
                    onRefresh = followRequestStateHolder::refresh,
                    onLoadMore = followRequestStateHolder::loadMore,
                    onAccept = followRequestStateHolder::accept,
                    onReject = followRequestStateHolder::reject,
                    onCancel = followRequestStateHolder::cancel,
                    onOpenUser = onOpenUser,
                )
                AppRoute.Antennas -> AntennasRouteContent(
                    state = antennaState,
                    stateHolder = antennaStateHolder,
                    noteActionState = noteActionState,
                    noteCallbacks = noteCallbacks,
                    onBack = { route = AppRoute.Profile },
                )
                AppRoute.Clips -> ClipsRouteContent(
                    state = clipState,
                    stateHolder = clipStateHolder,
                    noteActionState = noteActionState,
                    noteCallbacks = noteCallbacks,
                    onBack = { route = AppRoute.Profile },
                )
                AppRoute.Channels -> ChannelsRouteContent(
                    state = channelState,
                    stateHolder = channelStateHolder,
                    noteActionState = noteActionState,
                    noteCallbacks = noteCallbacks,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onComposeInChannel = { channel ->
                        route = AppRoute.Compose(channelId = channel.id)
                    },
                )
                AppRoute.Pages -> PagesRouteContent(
                    state = pageState,
                    stateHolder = pageStateHolder,
                    currentUserId = accountUser?.id,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onOpenUser = onOpenUser,
                )
                AppRoute.Gallery -> GalleryRouteContent(
                    state = galleryState,
                    stateHolder = galleryStateHolder,
                    currentUserId = accountUser?.id,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onOpenUser = onOpenUser,
                    onOpenMedia = onOpenUrl,
                    onOpenMediaPreview = { mediaPreviewSession = it },
                )
                AppRoute.Flash -> FlashRouteContent(
                    state = flashState,
                    stateHolder = flashStateHolder,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onOpenUser = onOpenUser,
                    onOpenFlashInWeb = { flashId ->
                        openUrl("${SharkeyAuthApi.DEFAULT_BASE_URL}${flashWebPath(flashId)}")
                    },
                )
                AppRoute.Announcements -> AnnouncementsRouteContent(
                    state = announcementState,
                    stateHolder = announcementStateHolder,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                )
                AppRoute.Profile -> ProfileRouteContent(
                    state = userProfileState,
                    capabilities = instanceCapabilities,
                    noteActionState = noteActionState,
                    aiState = aiState,
                    noteCallbacks = noteCallbacks,
                    selectedTheme = selectedTheme,
                    selectedTimelineDensity = selectedTimelineDensity,
                    onRefresh = userProfileStateHolder::load,
                    onLoadMoreNotes = userProfileStateHolder::loadMoreNotes,
                    latestAiResultFor = { kinds -> latestAiResultFor(*kinds) },
                    onAiProfileSummary = { requestProfileAi(AiTaskKind.ProfileSummary, userProfileState, ownProfile = true) },
                    onAiProfileSuggestions = {
                        requestProfileAi(AiTaskKind.ProfileInteractionSuggestions, userProfileState, ownProfile = true)
                    },
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                    onUpdateProfile = userProfileStateHolder::updateProfile,
                    onChangeBanner = {
                        val picker = mediaPicker
                        if (picker == null) {
                            userProfileStateHolder.showProfileEditError("当前设备不支持选择图片")
                        } else {
                            picker.pickImages(
                                onPicked = userProfileStateHolder::updateBanner,
                                onError = userProfileStateHolder::showProfileEditError,
                            )
                        }
                    },
                    onLogout = onAuthInvalid,
                    onThemeSelected = onThemeSelected,
                    onTimelineDensitySelected = onTimelineDensitySelected,
                    onClearMessage = userProfileStateHolder::clearMessage,
                    onOpenDrive = { route = AppRoute.Drive },
                    onOpenAchievements = { route = AppRoute.Achievements },
                    onOpenSettings = { route = AppRoute.Settings },
                    onOpenAutomation = { route = AppRoute.Automation },
                    onOpenFavoriteNotes = { route = AppRoute.FavoriteNotes },
                    onOpenLists = { route = AppRoute.UserLists },
                    onOpenFollowRequests = { route = AppRoute.FollowRequests },
                    onOpenRelationshipManagement = { route = AppRoute.RelationshipManagement },
                    onOpenAntennas = { route = AppRoute.Antennas },
                    onOpenClips = { route = AppRoute.Clips },
                    onOpenChannels = { route = AppRoute.Channels },
                    onOpenPages = { route = AppRoute.Pages },
                    onOpenGallery = { route = AppRoute.Gallery },
                    onOpenFlash = { route = AppRoute.Flash },
                    onOpenAnnouncements = { route = AppRoute.Announcements },
                    onOpenProfileNotes = { route = AppRoute.ProfileNotes },
                    onOpenSocial = { kind ->
                        userProfileState.user?.let {
                            route = AppRoute.UserSocial(it.id, kind, it.displayName)
                        }
                    },
                )
                AppRoute.ProfileNotes -> ProfileNotesRouteContent(
                    state = userProfileState,
                    noteActionState = noteActionState,
                    noteCallbacks = noteCallbacks,
                    onBack = { route = AppRoute.Profile },
                    onRefresh = userProfileStateHolder::load,
                    onLoadMoreNotes = userProfileStateHolder::loadMoreNotes,
                )
                AppRoute.Settings -> SettingsRouteContent(
                    state = settingsState,
                    stateHolder = settingsStateHolder,
                    instanceMetaState = instanceMetaState,
                    accounts = accounts,
                    currentAccountId = currentAccountId,
                    onBack = { route = AppRoute.Profile },
                    onThemeSelected = onThemeSelected,
                    customTheme = customTheme,
                    onCustomThemeChanged = onCustomThemeChanged,
                    onResetCustomTheme = onResetCustomTheme,
                    onPickGlobalBackgroundImage = {
                        mediaPicker?.pickImages(
                            onPicked = onSetGlobalBackgroundImage,
                            onError = {},
                        )
                    },
                    onClearGlobalBackgroundImage = onClearGlobalBackgroundImage,
                    onPickChatBackgroundImage = {
                        mediaPicker?.pickImages(
                            onPicked = onSetChatBackgroundImage,
                            onError = {},
                        )
                    },
                    onClearChatBackgroundImage = onClearChatBackgroundImage,
                    onTimelineDensitySelected = onTimelineDensitySelected,
                    onListGesturesEnabledChanged = onListGesturesEnabledChanged,
                    onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
                    onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
                    onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
                    onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
                    onCheckForUpdates = onCheckForUpdates,
                    onClearChatMessageCache = {
                        currentAccountId?.let { accountId ->
                            appScope.launch {
                                chatMessageCache.clearAccount(accountId)
                            }
                            chatUnreadStore.clearAccount(accountId)
                        }
                    },
                    onOpenThemeCustomization = { route = AppRoute.ThemeCustomization },
                    onBackHandlerChanged = onBackHandlerChanged,
                    onSwitchAccount = onSwitchAccount,
                    onRemoveAccount = onRemoveAccount,
                    onAddAccount = onAddAccount,
                    onOpenAdminDashboard = { route = AppRoute.AdminDashboard },
                    onOpenWebSettings = { key ->
                        settingsWebManagementPath(key)?.let { path ->
                            openUrl("${SharkeyAuthApi.DEFAULT_BASE_URL}$path")
                        }
                    },
                    onOpenManagement = { key ->
                        settingsStateHolder.openManagement(key)
                        route = AppRoute.SettingsManagement(key)
                    },
                    onAiSettingsChanged = aiStateHolder::updateSettings,
                    onAiProviderSelected = aiStateHolder::applyProviderPreset,
                    onTestAiConnection = aiStateHolder::testConnection,
                    onAiWorkspacePlan = ::requestWorkspaceActionPlan,
                    aiConnectionMessage = aiState.message ?: aiState.errorMessage,
                    isTestingAiConnection = aiState.isTestingConnection,
                )
                AppRoute.ThemeCustomization -> ThemeCustomizationScreen(
                    customTheme = customTheme,
                    onCustomThemeChanged = onCustomThemeChanged,
                    onReset = onResetCustomTheme,
                    onPickGlobalBackgroundImage = {
                        mediaPicker?.pickImages(
                            onPicked = onSetGlobalBackgroundImage,
                            onError = {},
                        )
                    },
                    onClearGlobalBackgroundImage = onClearGlobalBackgroundImage,
                    onPickChatBackgroundImage = {
                        mediaPicker?.pickImages(
                            onPicked = onSetChatBackgroundImage,
                            onError = {},
                        )
                    },
                    onClearChatBackgroundImage = onClearChatBackgroundImage,
                    onBack = { route = AppRoute.Settings },
                )
                AppRoute.Automation -> AutomationRouteContent(
                    state = automationState,
                    aiState = aiState,
                    onBack = { route = AppRoute.Profile },
                    onCreateRule = automationStateHolder::createRule,
                    onOpenRule = automationStateHolder::openRule,
                    onCloseEditor = automationStateHolder::closeEditor,
                    onToggleRule = automationStateHolder::toggleRule,
                    onDeleteRule = automationStateHolder::deleteRule,
                    onDuplicateRule = automationStateHolder::duplicateRule,
                    onUpdateRuleName = automationStateHolder::updateRuleName,
                    onUpdateRuleTrigger = automationStateHolder::updateRuleTrigger,
                    onUpdateConditionMode = automationStateHolder::updateRuleConditionMode,
                    onUpdateActionMode = automationStateHolder::updateRuleActionMode,
                    onUpdateIgnoreOwnMessages = automationStateHolder::updateRuleIgnoreOwnMessages,
                    onUpdateCooldown = automationStateHolder::updateRuleCooldown,
                    onAddCondition = automationStateHolder::addCondition,
                    onUpdateCondition = automationStateHolder::updateCondition,
                    onRemoveCondition = automationStateHolder::removeCondition,
                    onAddAction = automationStateHolder::addAction,
                    onUpdateAction = automationStateHolder::updateAction,
                    onRemoveAction = automationStateHolder::removeAction,
                    onClearLogs = automationStateHolder::clearLogs,
                    latestAiResultFor = { kinds -> latestAiResultFor(*kinds) },
                    onAiExplainRule = ::requestAutomationExplain,
                    onAiSuggestRules = ::requestAutomationRuleSuggestions,
                    onAiCreateRule = ::requestAutomationRuleDraft,
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                )
                is AppRoute.SettingsManagement -> SettingsManagementScreen(
                    section = settingsState.managementSection,
                    isLoading = settingsState.isManagementLoading,
                    isMutating = settingsState.isManagementMutating,
                    editingWebhook = settingsState.editingWebhook,
                    isWebhookEditorLoading = settingsState.isWebhookEditorLoading,
                    message = settingsState.managementMessage,
                    onBack = {
                        settingsStateHolder.closeManagement()
                        route = AppRoute.Settings
                    },
                    onRefresh = settingsStateHolder::refreshManagement,
                    onPerformAction = settingsStateHolder::performManagementAction,
                    onOpenWebhookEditor = settingsStateHolder::openWebhookEditor,
                    onCloseWebhookEditor = settingsStateHolder::closeWebhookEditor,
                    onCreateWebhook = settingsStateHolder::createWebhook,
                    onUpdateWebhook = settingsStateHolder::updateWebhook,
                )
                AppRoute.AdminDashboard -> AdminDashboardScreen(
                    state = adminState,
                    onBack = { route = AppRoute.Settings },
                    onRefresh = adminStateHolder::refresh,
                    onTabSelected = adminStateHolder::selectTab,
                    onUserQueryChanged = adminStateHolder::updateUserQuery,
                    onSearchUsers = adminStateHolder::searchUsers,
                    onLoadUserRoles = adminStateHolder::loadUserRoles,
                    onResolveReport = adminStateHolder::resolveReport,
                    onAnnouncementDraftChanged = adminStateHolder::updateAnnouncementDraft,
                    onCreateAnnouncement = adminStateHolder::createAnnouncement,
                    onEditAnnouncement = adminStateHolder::editAnnouncement,
                    onCancelAnnouncementEdit = adminStateHolder::cancelAnnouncementEdit,
                    onDeleteAnnouncement = adminStateHolder::deleteAnnouncement,
                )
                AppRoute.RelationshipManagement -> RelationshipManagementRouteContent(
                    state = relationshipManagementState,
                    stateHolder = relationshipManagementStateHolder,
                    onRemoveSpecialCareUser = { userId ->
                        if (specialCareStateHolder.isSpecialCare(userId)) {
                            specialCareStateHolder.toggleSpecialCare(userId)
                        }
                    },
                    onBack = { route = AppRoute.Profile },
                    onOpenUser = onOpenUser,
                )
                is AppRoute.UserProfile -> ViewedProfileRouteContent(
                    state = viewedProfileState,
                    noteActionState = noteActionState,
                    aiState = aiState,
                    noteCallbacks = noteCallbacks,
                    onRefresh = { viewedProfileStateHolder.load(clearContent = true) },
                    onLoadMoreNotes = viewedProfileStateHolder::loadMoreNotes,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            appRouteForRootRoute(rootRoute)
                        }
                    },
                    latestAiResultFor = { kinds -> latestAiResultFor(*kinds) },
                    onAiProfileSummary = { requestProfileAi(AiTaskKind.ProfileSummary, viewedProfileState, ownProfile = false) },
                    onAiProfileSuggestions = {
                        requestProfileAi(AiTaskKind.ProfileInteractionSuggestions, viewedProfileState, ownProfile = false)
                    },
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                    onFollowToggle = viewedProfileStateHolder::toggleFollow,
                    onMuteToggle = viewedProfileStateHolder::toggleMute,
                    onBlockToggle = viewedProfileStateHolder::toggleBlock,
                    onOpenChatWithUser = onOpenChatUser,
                    isSpecialCareUser = specialCareStateHolder::isSpecialCare,
                    onToggleSpecialCareUser = specialCareStateHolder::toggleSpecialCare,
                    onOpenSocial = { kind ->
                        viewedProfileState.user?.let {
                            route = AppRoute.UserSocial(it.id, kind, it.displayName)
                        }
                    },
                )
                is AppRoute.UserSocial -> UserSocialScreen(
                    state = userSocialState,
                    kind = if (userSocialState.userId == current.userId) {
                        userSocialState.kind
                    } else {
                        current.kind
                    },
                    displayName = if (userSocialState.userId == current.userId) {
                        userSocialState.displayName ?: current.displayName
                    } else {
                        current.displayName
                    },
                    onRefresh = {
                        userSocialStateHolder.load(
                            userId = current.userId,
                            kind = if (userSocialState.userId == current.userId) {
                                userSocialState.kind
                            } else {
                                current.kind
                            },
                            displayName = current.displayName,
                        )
                    },
                    onKindSelected = { kind ->
                        route = current.copy(kind = kind)
                        userSocialStateHolder.load(
                            userId = current.userId,
                            kind = kind,
                            displayName = current.displayName,
                        )
                    },
                    onLoadMore = userSocialStateHolder::loadMore,
                    onOpenUser = onOpenUser,
                    onUnfollowUser = userSocialStateHolder::unfollow,
                    onMuteUser = userSocialStateHolder::mute,
                    onBlockUser = userSocialStateHolder::block,
                    onReportUser = userSocialStateHolder::reportUser,
                    isSpecialCareUser = specialCareStateHolder::isSpecialCare,
                    onToggleSpecialCareUser = specialCareStateHolder::toggleSpecialCare,
                    onBack = {
                        route = if (current.userId == accountUser?.id) {
                            AppRoute.Profile
                        } else {
                            AppRoute.UserProfile(current.userId)
                        }
                    },
                )
                is AppRoute.NoteDetail -> NoteDetailRouteContent(
                    noteId = current.noteId,
                    state = noteDetailState,
                    noteActionState = noteActionState,
                    aiState = aiState,
                    noteCallbacks = noteCallbacks,
                    onRefresh = { noteDetailStateHolder.load(current.noteId) },
                    onLoadMoreReplies = noteDetailStateHolder::loadMoreReplies,
                    onLoadConversation = noteDetailStateHolder::loadConversation,
                    onLoadRenotes = noteDetailStateHolder::loadRenotes,
                    onLoadReactionUsers = noteDetailStateHolder::loadReactionUsers,
                    onLoadVersions = noteDetailStateHolder::loadVersions,
                    onRefreshPollRecommendation = noteDetailStateHolder::refreshPollRecommendation,
                    onTranslate = noteDetailStateHolder::translate,
                    latestAiResultFor = { kinds -> latestAiResultFor(*kinds) },
                    onAiThreadSummary = { detail -> requestThreadAi(AiTaskKind.ThreadSummary, detail) },
                    onAiThreadReplyDraft = { detail -> requestThreadAi(AiTaskKind.ThreadReplyDraft, detail, openCompose = true) },
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                    onToggleChildReplies = noteDetailStateHolder::toggleChildReplies,
                    onBack = { route = AppRoute.Timeline },
                )
                is AppRoute.Compose -> ComposeRouteContent(
                    state = composeState,
                    noteActionState = noteActionState,
                    completionState = composeCompletionState,
                    aiState = aiState,
                    targetNote = when {
                        current.replyToId != null -> findLoadedNote(current.replyToId)
                        current.renoteId != null -> findLoadedNote(current.renoteId)
                        else -> null
                    },
                    onTextChanged = composeStateHolder::updateText,
                    onCwChanged = composeStateHolder::updateCw,
                    onVisibilitySelected = composeStateHolder::updateVisibility,
                    onLocalOnlyChanged = composeStateHolder::updateLocalOnly,
                    onReactionAcceptanceSelected = composeStateHolder::updateReactionAcceptance,
                    onScheduleAtChanged = composeStateHolder::updateScheduleNote,
                    onInsertText = composeStateHolder::appendText,
                    onResetDraft = composeStateHolder::resetDraft,
                    onLoadScheduledNotes = composeStateHolder::loadScheduledNotes,
                    onEditScheduledNote = composeStateHolder::editScheduledNote,
                    onDeleteScheduledNote = composeStateHolder::deleteScheduledNote,
                    onVisibleUserIdsChanged = composeStateHolder::updateVisibleUserIds,
                    onResolveVisibleUserMentions = composeStateHolder::resolveVisibleUserMentions,
                    onPollEnabledChanged = composeStateHolder::setPollEnabled,
                    onPollChoiceChanged = composeStateHolder::updatePollChoice,
                    onPollMultipleChanged = composeStateHolder::updatePollMultiple,
                    onPollExpiresAtChanged = composeStateHolder::updatePollExpiresAt,
                    onPollDeadlinePresetSelected = composeStateHolder::selectPollDeadlinePreset,
                    onPollChoiceAdded = composeStateHolder::addPollChoice,
                    onPollChoiceRemoved = composeStateHolder::removePollChoice,
                    onAddMedia = {
                        mediaPicker?.pickMedia(
                            mimeType = "image/*",
                            onPicked = composeStateHolder::uploadMedia,
                            onError = composeStateHolder::reportMediaUploadError,
                        )
                    },
                    onOpenDrivePicker = { drivePickerTarget = DrivePickerTarget.Compose },
                    onRemoveFileId = composeStateHolder::removeFileId,
                    onAttachedFileMetadataChanged = composeStateHolder::updateAttachedFileMetadata,
                    isMediaPickerAvailable = mediaPicker != null,
                    onCompletionTokenChanged = composeCompletionStateHolder::request,
                    onSend = composeStateHolder::send,
                    onRetryFailedSend = composeStateHolder::retryFailedSend,
                    onRestoreFailedSend = composeStateHolder::restoreFailedSend,
                    onRemoveFailedSend = composeStateHolder::removeFailedSend,
                    latestAiResultFor = { kinds -> latestAiResultFor(*kinds) },
                    onAiAction = ::requestComposeAi,
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                    onBack = { route = AppRoute.Timeline },
                )
                }
            }
            MainShellBottomNav(
                selected = rootRoute,
                chatAvailable = chatState.chatAvailable,
                notificationBadgeMode = selectedNotificationBadgeMode,
                unreadChatCount = chatState.rooms.sumOf { it.unreadCount.coerceAtLeast(0) } +
                    chatState.userConversations.sumOf { it.unreadCount.coerceAtLeast(0) },
                unreadNotificationCount = notificationState.unreadCount,
                onSelected = {
                    rootRoute = it
                    route = appRouteForRootRoute(it)
                },
            )
            drivePickerTarget?.let { target ->
                DriveRouteContent(
                    state = driveFilesState,
                    stateHolder = driveFilesStateHolder,
                    mediaPicker = mediaPicker,
                    onBack = { drivePickerTarget = null },
                    onOpenUrl = openUrl,
                    onOpenNote = { route = AppRoute.NoteDetail(it) },
                    onOpenMediaPreview = { mediaPreviewSession = it },
                    isPickerMode = true,
                    onPickFile = { file ->
                        when (target) {
                            DrivePickerTarget.Compose -> composeStateHolder.attachDriveFile(file)
                            DrivePickerTarget.Chat -> chatStateHolder.attachDriveFile(file)
                        }
                        drivePickerTarget = null
                    },
                )
            }
            }
            mediaPreviewSession?.let { session ->
                MediaPreviewOverlay(
                    session = session,
                    onDismiss = { mediaPreviewSession = null },
                    onSessionChanged = { mediaPreviewSession = it },
                    onOpenExternal = openUrl,
                    onShare = shareUrl,
                    onDownload = { item ->
                        downloadUrl(item.openUrl, item.label, item.type)
                    },
                )
            }
            clipTargetNote?.let { note ->
                ClipTargetDialog(
                    note = note,
                    clips = if (clipState.selectedKind == ClipListKind.Owned) clipState.clips else emptyList(),
                    isLoading = clipState.isLoadingClips,
                    errorMessage = clipState.errorMessage,
                    onDismiss = { clipTargetNote = null },
                    onRefresh = { clipStateHolder.refreshClips(ClipListKind.Owned) },
                    onOpenClips = {
                        clipTargetNote = null
                        rootRoute = RootRoute.Profile
                        route = AppRoute.Clips
                        clipStateHolder.refreshClips(ClipListKind.Owned)
                    },
                    onSelectClip = { clip ->
                        clipStateHolder.addNoteToClip(clip, note)
                        clipTargetNote = null
                    },
                )
            }
            userListQuickTarget?.let { target ->
                QuickUserListDialog(
                    user = target.user,
                    lists = userListState.lists,
                    isLoading = userListState.isLoadingLists || userListState.isMutatingMembers,
                    errorMessage = userListState.errorMessage,
                    onDismiss = { userListQuickTarget = null },
                    onRefresh = userListStateHolder::refreshLists,
                    onSelectList = { list ->
                        userListStateHolder.selectList(list)
                        userListStateHolder.addUserToSelectedList(target.user.id)
                        noteActionToast = "已加入 ${list.name}"
                        userListQuickTarget = null
                    },
                )
            }
            if (authInvalidDialogOpen) {
                AuthInvalidConfirmationDialog(
                    onDismiss = { authInvalidDialogOpen = false },
                    onConfirm = {
                        authInvalidDialogOpen = false
                        onAuthInvalid()
                    },
                )
            }
            pendingReleaseNotes?.let { notes ->
                ReleaseNotesDialog(
                    notes = notes,
                    onDismiss = {
                        pendingReleaseNotes = null
                        runCatching { releaseNotesStore.saveLastShownVersion(notes.versionName) }
                    },
                )
            }
            noteActionToast?.let { message ->
                val colors = LocalHhhlColors.current
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 78.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.toastBackground)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = message,
                        color = colors.toastText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
