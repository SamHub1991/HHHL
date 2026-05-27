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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.api.MainStreamingEvent
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
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.repository.AntennaRepository
import cc.hhhl.client.repository.AnnouncementRepository
import cc.hhhl.client.repository.AdminRepository
import cc.hhhl.client.repository.AchievementRepository
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatStreamingRepository
import cc.hhhl.client.repository.ChannelRepository
import cc.hhhl.client.repository.ClipRepository
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.DiscoverRepository
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
import cc.hhhl.client.state.AntennaStateHolder
import cc.hhhl.client.state.AnnouncementStateHolder
import cc.hhhl.client.state.AdminStateHolder
import cc.hhhl.client.state.AchievementStateHolder
import cc.hhhl.client.state.ChatStateHolder
import cc.hhhl.client.state.ChannelStateHolder
import cc.hhhl.client.state.ClipStateHolder
import cc.hhhl.client.state.ComposeCompletionStateHolder
import cc.hhhl.client.state.ComposeStateHolder
import cc.hhhl.client.state.ComposeDraftStore
import cc.hhhl.client.state.DiscoverStateHolder
import cc.hhhl.client.state.ChatUiState
import cc.hhhl.client.state.DriveFilesUiState
import cc.hhhl.client.state.DriveFilesStateHolder
import cc.hhhl.client.state.FavoriteNoteStateHolder
import cc.hhhl.client.state.FlashStateHolder
import cc.hhhl.client.state.FollowRequestStateHolder
import cc.hhhl.client.state.GalleryStateHolder
import cc.hhhl.client.state.InstanceMetaStateHolder
import cc.hhhl.client.state.NoteLocalMutation
import cc.hhhl.client.state.NoteActionStateHolder
import cc.hhhl.client.state.NoopComposeDraftStore
import cc.hhhl.client.state.NoteDetailStateHolder
import cc.hhhl.client.state.NoopNotificationReadStore
import cc.hhhl.client.state.NoopRecentReactionStore
import cc.hhhl.client.state.NotificationReadStore
import cc.hhhl.client.state.NotificationStateHolder
import cc.hhhl.client.state.PageStateHolder
import cc.hhhl.client.state.RecentReactionStore
import cc.hhhl.client.state.RelationshipManagementStateHolder
import cc.hhhl.client.state.RelationshipManagementUiState
import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.state.SettingsStateHolder
import cc.hhhl.client.state.SettingsUiState
import cc.hhhl.client.state.NoopSpecialCareStore
import cc.hhhl.client.state.SpecialCareStateHolder
import cc.hhhl.client.state.SpecialCareStore
import cc.hhhl.client.state.TimelineStateHolder
import cc.hhhl.client.state.UserListStateHolder
import cc.hhhl.client.state.UserProfileStateHolder
import cc.hhhl.client.state.UserSocialStateHolder
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
import cc.hhhl.client.theme.NoopThemeStore
import cc.hhhl.client.theme.ThemeStore
import cc.hhhl.client.theme.ThemeStateHolder
import cc.hhhl.client.ui.component.HhhlBottomNav
import cc.hhhl.client.ui.component.LocalCustomEmojiUrls
import cc.hhhl.client.ui.component.LocalNoteRowActions
import cc.hhhl.client.ui.component.MediaPreviewOverlay
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRowDensity
import cc.hhhl.client.ui.component.NoteRowActions
import cc.hhhl.client.ui.screen.AnnouncementScreen
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
    downloadUrl: (String, String, String) -> Unit = { url, _, _ -> openUrl(url) },
    mediaPicker: MediaPicker? = null,
    authCallbackSession: String? = null,
    authTokenStore: AuthTokenStore = NoopAuthTokenStore,
    themeStore: ThemeStore = NoopThemeStore,
    displayPreferenceStore: DisplayPreferenceStore = NoopDisplayPreferenceStore,
    recentReactionStore: RecentReactionStore = NoopRecentReactionStore,
    specialCareStore: SpecialCareStore = NoopSpecialCareStore,
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
                    downloadUrl = downloadUrl,
                    mediaPicker = mediaPicker,
                    timelineCache = timelineCache,
                    recentReactionStore = recentReactionStore,
                    specialCareStore = specialCareStore,
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
                    selectedDefaultNoteVisibility = displayPreferenceState.defaultNoteVisibility,
                    onDefaultNoteVisibilitySelected = displayPreferenceStateHolder::selectDefaultNoteVisibility,
                    selectedNotificationBadgeMode = displayPreferenceState.notificationBadgeMode,
                    onNotificationBadgeModeSelected = displayPreferenceStateHolder::selectNotificationBadgeMode,
                    backgroundNotificationsEnabled = backgroundNotificationsEnabled,
                    specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
                    onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
                    onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
                    onBackHandlerChanged = onBackHandlerChanged,
                    onSwitchAccount = loginStateHolder::switchAccount,
                    onRemoveAccount = loginStateHolder::removeAccount,
                    onAddAccount = { loginStateHolder.startBrowserLogin(openUrl) },
                    onAuthInvalid = loginStateHolder::logout,
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

private fun TimelineDensity.toNoteRowDensity(): NoteRowDensity {
    return when (this) {
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

private const val CHAT_ROOM_REFRESH_INTERVAL_MS = 15_000L
private const val CHAT_MESSAGE_REFRESH_INTERVAL_MS = 5_000L
private const val STREAMING_CHAT_REFRESH_INTERVAL_MS = 60_000L
private const val CHAT_STREAM_RECONNECT_DELAY_MS = 3_000L
private const val TIMELINE_REFRESH_INTERVAL_MS = 12_000L
private const val STREAMING_TIMELINE_FALLBACK_REFRESH_INTERVAL_MS = 60_000L
private const val STREAMING_TIMELINE_REFRESH_DEBOUNCE_MS = 2_000L
private const val TREND_REFRESH_INTERVAL_MS = 5_000L
private const val NOTIFICATION_REFRESH_INTERVAL_MS = 20_000L
private const val STREAMING_NOTIFICATION_FALLBACK_REFRESH_INTERVAL_MS = 60_000L

@Composable
private fun MainShellBottomNav(
    selected: RootRoute,
    chatAvailable: Boolean,
    notificationBadgeMode: NotificationBadgeMode,
    unreadChatCount: Int,
    unreadNotificationCount: Int,
    onSelected: (RootRoute) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录状态需要确认") },
        text = {
            Text(
                text = "服务器返回未授权。当前页面不会立刻退出登录；确认后会回到登录授权流程。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("重新登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        },
    )
}

@Composable
private fun ChatRouteContent(
    state: ChatUiState,
    currentUserId: String?,
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
) {
    ChatScreen(
        state = state,
        currentUserId = currentUserId,
        onRefresh = stateHolder::refresh,
        onLoadMore = stateHolder::loadMore,
        onOpenRoom = stateHolder::selectRoom,
        onOpenUserConversation = stateHolder::selectUserConversation,
        onToggleRoomPinned = stateHolder::toggleRoomPinned,
        onToggleUserConversationPinned = stateHolder::toggleUserConversationPinned,
        onDeleteUserConversation = stateHolder::deleteUserConversation,
        onCreateRoom = stateHolder::createRoom,
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
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
    onBackgroundNotificationsChanged: (Boolean) -> Unit,
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit,
    onClearChatMessageCache: () -> Unit,
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit),
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenWebSettings: (SettingsItemKey) -> Unit,
    onOpenManagement: (cc.hhhl.client.model.SettingsManagementSectionKey) -> Unit,
) {
    SettingsScreen(
        state = state,
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
        onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
        onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
        onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
        onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
        onClearChatMessageCache = onClearChatMessageCache,
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
    )
}

@Composable
private fun RelationshipManagementRouteContent(
    state: RelationshipManagementUiState,
    stateHolder: RelationshipManagementStateHolder,
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
        onRemoveRelationship = stateHolder::removeRelationship,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到剪辑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = note.text.ifBlank { "这条动态" }.take(48),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
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
                            TextButton(
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "${clip.visibilityLabel} · ${clip.notesCount} 条动态",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            TextButton(
                onClick = if (errorMessage != null || clips.isEmpty()) onRefresh else onOpenClips,
            ) {
                Text(if (errorMessage != null) "重试" else "管理剪辑")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
    downloadUrl: (String, String, String) -> Unit,
    mediaPicker: MediaPicker?,
    timelineCache: TimelineCache?,
    recentReactionStore: RecentReactionStore,
    specialCareStore: SpecialCareStore,
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
    selectedDefaultNoteVisibility: DefaultNoteVisibility,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    selectedNotificationBadgeMode: NotificationBadgeMode,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
    backgroundNotificationsEnabled: Boolean,
    specialCareBackgroundNotificationsEnabled: Boolean,
    onBackgroundNotificationsChanged: (Boolean) -> Unit,
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit,
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit),
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onAuthInvalid: () -> Unit,
) {
    val appScope = rememberCoroutineScope()
    var rootRoute by remember { mutableStateOf(RootRoute.Timeline) }
    var route: AppRoute by remember { mutableStateOf(AppRoute.Timeline) }
    var timelineTrendSelected by remember { mutableStateOf(false) }
    var drivePickerTarget by remember { mutableStateOf<DrivePickerTarget?>(null) }
    var mediaPreviewSession by remember { mutableStateOf<MediaPreviewSession?>(null) }
    var viewedUserId by remember { mutableStateOf<String?>(null) }
    var authInvalidDialogOpen by remember { mutableStateOf(false) }
    var lastStreamingTimelineRefreshAt by remember { mutableStateOf<Map<TimelineKind, Long>>(emptyMap()) }
    var notifiedSpecialCareNoteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val fallbackTimelineCache = remember { InMemoryTimelineCache() }
    val activeTimelineCache = timelineCache ?: fallbackTimelineCache
    val specialCareStateHolder = remember {
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
    val chatStateHolder = remember {
        ChatStateHolder(
            repository = ChatRepository(
                tokenProvider = { sessionToken },
                currentUserIdProvider = { accountUser?.id },
                cacheAccountIdProvider = { currentAccountId },
                messageCache = chatMessageCache,
            ),
            driveFileRepository = DriveFileRepository(tokenProvider = { sessionToken }),
            streamingRepository = ChatStreamingRepository(tokenProvider = { sessionToken }),
            relationshipRepository = UserRelationshipRepository(tokenProvider = { sessionToken }),
            accountIdProvider = { currentAccountId },
            unreadStore = chatUnreadStore,
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
    val notificationStateHolder = remember {
        NotificationStateHolder(
            repository = NotificationRepository(tokenProvider = { sessionToken }),
            readStore = notificationReadStore,
            notificationCache = notificationCache,
            accountId = currentAccountId,
            scope = appScope,
        )
    }
    val notificationState by notificationStateHolder.state.collectAsState()
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

    LaunchedEffect(Unit) {
        instanceMetaStateHolder.load()
        specialCareStateHolder.restoreStoredSpecialCare()
        composeStateHolder.restoreStoredDraft()
        noteActionStateHolder.restoreRecentReactions()
        noteActionStateHolder.loadReactionOptions()
        timelineStateHolder.refresh()
        notificationStateHolder.refresh()
        settingsStateHolder.refreshRemote()
    }

    LaunchedEffect(specialCareState.userIds) {
        chatStateHolder.updateSpecialCareUsers(specialCareState.userIds)
        notificationStateHolder.updateSpecialCareUsers(specialCareState.userIds)
        notifiedSpecialCareNoteIds += loadedNotes
            .filter { it.author.id in specialCareState.userIds }
            .map { it.id }
    }

    LaunchedEffect(
        selectedTheme,
        customTheme,
        selectedTimelineDensity,
        selectedDefaultNoteVisibility,
        selectedNotificationBadgeMode,
        backgroundNotificationsEnabled,
        specialCareBackgroundNotificationsEnabled,
        accountUser,
    ) {
        settingsStateHolder.sync(
            selectedTheme = selectedTheme,
            customTheme = customTheme,
            selectedTimelineDensity = selectedTimelineDensity,
            selectedDefaultNoteVisibility = selectedDefaultNoteVisibility,
            selectedNotificationBadgeMode = selectedNotificationBadgeMode,
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

    LaunchedEffect(sessionToken, chatState.chatAvailable) {
        mainStreamingConnected = false
        while (true) {
            var unauthorized = false
            mainStreamingRepository.streamMain().collectLatest { event ->
                when (event) {
                    MainStreamingEvent.UnreadNotification -> notificationStateHolder.refreshQuietly()
                    MainStreamingEvent.ReadAllNotifications -> notificationStateHolder.syncAllReadFromStreaming()
                    is MainStreamingEvent.TimelineNote -> {
                        val currentTimelineState = latestTimelineState
                        if (
                            latestRoute == AppRoute.Timeline &&
                            !latestTimelineTrendSelected &&
                            !currentTimelineState.requiresRelogin &&
                            event.kind == currentTimelineState.selectedKind
                        ) {
                            val now = Clock.System.now().toEpochMilliseconds()
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

    LaunchedEffect(chatState.specialCareToast?.messageId) {
        chatState.specialCareToast?.let { toast ->
            notificationStateHolder.addSpecialCareNotification(
                NotificationItem(
                    id = "special-care-chat-${toast.chatUserId ?: toast.roomId}-${toast.messageId}",
                    type = NotificationType.App,
                    actor = cc.hhhl.client.model.User(
                        id = toast.userId,
                        displayName = toast.displayName,
                        username = toast.displayName,
                        avatarInitial = toast.displayName.trim().firstOrNull()?.toString()?.uppercase() ?: "特",
                    ),
                    text = "在聊天中发来了新消息",
                    createdAtLabel = toast.createdAtLabel.ifBlank { "刚刚" },
                    notePreviewText = toast.previewText,
                    isSpecialCare = true,
                    chatRoomId = toast.roomId.takeIf { it.isNotBlank() },
                    chatUserId = toast.chatUserId,
                    chatMessageId = toast.messageId,
                ),
            )
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
            notificationStateHolder.addSpecialCareNotification(
                NotificationItem(
                    id = "special-care-note-${note.id}",
                    type = NotificationType.Note,
                    actor = note.author,
                    text = "发布了新帖子",
                    createdAtLabel = note.createdAtLabel.ifBlank { "刚刚" },
                    noteId = note.id,
                    notePreviewText = note.text.takeIf { it.isNotBlank() } ?: note.cw,
                    isSpecialCare = true,
                ),
            )
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
            } else {
                composeStateHolder.startNewNote()
            }
        }
    }

    LaunchedEffect(composeState.createdNoteId) {
        if (composeState.createdNoteId != null) {
            composeStateHolder.consumeCreatedNote()
            rootRoute = RootRoute.Timeline
            route = AppRoute.Timeline
            timelineStateHolder.refresh(TimelineKind.Home)
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
        val isFavorited = findLoadedNote(noteId)?.isFavorited == true
        applyNoteMutation(
            if (isFavorited) {
                NoteLocalMutation.Unfavorite(noteId)
            } else {
                NoteLocalMutation.Favorite(noteId)
            },
        )
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
                chatState.rooms.firstOrNull { it.id == target.roomId }?.let(chatStateHolder::selectRoom)
                    ?: chatStateHolder.refresh()
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
    val timelineListStates = remember {
        TimelineKind.values().associateWith { LazyListState() }
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

    CompositionLocalProvider(
        LocalCustomEmojiUrls provides noteActionState.customEmojiUrls,
        LocalNoteRowActions provides NoteRowActions(
            onShareNote = openUrl,
            onHideFromList = onHideNoteFromList,
            onMuteNote = onMuteNote,
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
                AppRoute.Timeline -> TimelineScreen(
                    state = timelineState,
                    onTimelineSelected = {
                        timelineTrendSelected = false
                        timelineStateHolder.select(it)
                    },
                    onRefresh = timelineStateHolder::refresh,
                    onLoadMore = timelineStateHolder::loadMore,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    isSpecialCareAuthor = specialCareStateHolder::isSpecialCare,
                    noteRowDensity = noteRowDensity,
                    capabilities = instanceMetaState.meta?.capabilities ?: InstanceCapabilities(),
                    listStates = timelineListStates,
                    isTrendSelected = timelineTrendSelected,
                    trends = discoverState.trends,
                    isRefreshingTrends = discoverState.isRefreshingTrends,
                    trendErrorMessage = discoverState.trendErrorMessage,
                    onTrendSelected = {
                        timelineTrendSelected = true
                        discoverStateHolder.refreshTrendsQuietly()
                    },
                    onRefreshTrends = discoverStateHolder::refreshTrendsQuietly,
                    onCompose = { route = AppRoute.Compose() },
                    onSearch = {
                        rootRoute = RootRoute.Discover
                        route = AppRoute.Discover
                        discoverStateHolder.selectMode(cc.hhhl.client.state.DiscoverSearchMode.Notes)
                    },
                )
                AppRoute.Discover -> DiscoverScreen(
                    state = discoverState,
                    onQueryChanged = discoverStateHolder::updateQuery,
                    onSearch = discoverStateHolder::search,
                    onModeSelected = discoverStateHolder::selectMode,
                    onFiltersChanged = discoverStateHolder::updateFilters,
                    onClearFilters = discoverStateHolder::clearFilters,
                    onLoadMore = discoverStateHolder::loadMore,
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
                    onOpenChannels = { route = AppRoute.Channels },
                    onOpenPages = { route = AppRoute.Pages },
                    onOpenGallery = { route = AppRoute.Gallery },
                    onOpenFlash = { route = AppRoute.Flash },
                    onOpenAnnouncements = { route = AppRoute.Announcements },
                    onOpenFederationInstance = discoverStateHolder::openFederationInstance,
                    onCloseFederationInstanceDetails = discoverStateHolder::closeFederationInstance,
                    onToggleFederationSilence = discoverStateHolder::toggleFederationSilence,
                    onToggleFederationBlock = discoverStateHolder::toggleFederationBlock,
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                )
                AppRoute.Chat -> ChatRouteContent(
                    state = chatState,
                    currentUserId = accountUser?.id,
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
                AppRoute.FavoriteNotes -> FavoriteNoteScreen(
                    state = favoriteNoteState,
                    onBack = { route = AppRoute.Profile },
                    onRefresh = favoriteNoteStateHolder::refresh,
                    onLoadMore = favoriteNoteStateHolder::loadMore,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                )
                AppRoute.Notifications -> NotificationsScreen(
                    state = notificationState,
                    announcementState = announcementState,
                    onRefresh = notificationStateHolder::refresh,
                    onLoadMore = notificationStateHolder::loadMore,
                    onMarkAllAsRead = notificationStateHolder::markAllAsRead,
                    onMarkNotificationRead = notificationStateHolder::markNotificationRead,
                    onFilterSelected = notificationStateHolder::selectFilter,
                    onRefreshAnnouncements = announcementStateHolder::refresh,
                    onLoadMoreAnnouncements = announcementStateHolder::loadMore,
                    onOpenAnnouncement = announcementStateHolder::openAnnouncement,
                    onCloseAnnouncement = announcementStateHolder::closeDetail,
                    onMarkAnnouncementRead = announcementStateHolder::markRead,
                    onOpenNote = { route = AppRoute.NoteDetail(it) },
                    onOpenUser = onOpenUser,
                    onOpenUrl = onOpenUrl,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                    pendingFollowRequestUserIds = followRequestState.pendingUserIds,
                    onAcceptFollowRequest = onAcceptFollowRequestFromNotification,
                    onRejectFollowRequest = onRejectFollowRequestFromNotification,
                    onOpenChat = onOpenChatFromNotification,
                    onOpenChatUser = onOpenChatUserById,
                )
                AppRoute.UserLists -> UserListScreen(
                    state = userListState,
                    onBack = { route = AppRoute.Profile },
                    onRefreshLists = userListStateHolder::refreshLists,
                    onRefreshTimeline = userListStateHolder::refreshTimeline,
                    onCreateList = userListStateHolder::createList,
                    onUpdateSelectedList = userListStateHolder::updateSelectedList,
                    onDeleteSelectedList = userListStateHolder::deleteSelectedList,
                    onAddUserToSelectedList = userListStateHolder::addUserToSelectedList,
                    onRemoveUserFromSelectedList = userListStateHolder::removeUserFromSelectedList,
                    onSelectList = userListStateHolder::selectList,
                    onLoadMore = userListStateHolder::loadMore,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                )
                AppRoute.FollowRequests -> FollowRequestScreen(
                    state = followRequestState,
                    onBack = { route = AppRoute.Profile },
                    onRefresh = followRequestStateHolder::refresh,
                    onLoadMore = followRequestStateHolder::loadMore,
                    onAccept = followRequestStateHolder::accept,
                    onReject = followRequestStateHolder::reject,
                    onOpenUser = onOpenUser,
                )
                AppRoute.Antennas -> AntennaScreen(
                    state = antennaState,
                    onBack = { route = AppRoute.Profile },
                    onRefreshAntennas = antennaStateHolder::refreshAntennas,
                    onRefreshNotes = antennaStateHolder::refreshNotes,
                    onCreateAntenna = antennaStateHolder::createAntenna,
                    onUpdateSelectedAntenna = antennaStateHolder::updateSelectedAntenna,
                    onDeleteSelectedAntenna = antennaStateHolder::deleteSelectedAntenna,
                    onSelectAntenna = antennaStateHolder::selectAntenna,
                    onLoadMore = antennaStateHolder::loadMore,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                )
                AppRoute.Clips -> ClipScreen(
                    state = clipState,
                    onBack = { route = AppRoute.Profile },
                    onRefreshClips = { clipStateHolder.refreshClips() },
                    onRefreshNotes = clipStateHolder::refreshNotes,
                    onCreateClip = clipStateHolder::createClip,
                    onUpdateSelectedClip = clipStateHolder::updateSelectedClip,
                    onDeleteSelectedClip = clipStateHolder::deleteSelectedClip,
                    onKindSelected = clipStateHolder::selectKind,
                    onSelectClip = clipStateHolder::selectClip,
                    onToggleFavoriteClip = clipStateHolder::toggleFavoriteSelectedClip,
                    onRemoveNoteFromClip = clipStateHolder::removeNoteFromSelectedClip,
                    onLoadMore = clipStateHolder::loadMore,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                )
                AppRoute.Channels -> ChannelScreen(
                    state = channelState,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onRefreshChannels = { channelStateHolder.refreshChannels() },
                    onRefreshTimeline = channelStateHolder::refreshTimeline,
                    onKindSelected = channelStateHolder::selectKind,
                    onSelectChannel = channelStateHolder::selectChannel,
                    onToggleFollowChannel = channelStateHolder::toggleFollowSelectedChannel,
                    onToggleFavoriteChannel = channelStateHolder::toggleFavoriteSelectedChannel,
                    onCreateChannel = channelStateHolder::createChannel,
                    onUpdateSelectedChannel = channelStateHolder::updateSelectedChannel,
                    onArchiveSelectedChannel = channelStateHolder::archiveSelectedChannel,
                    onComposeInChannel = { channel ->
                        composeStateHolder.startChannelNote(channel.id)
                        route = AppRoute.Compose()
                    },
                    onLoadMore = channelStateHolder::loadMore,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                )
                AppRoute.Pages -> PageScreen(
                    state = pageState,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onRefreshPages = { pageStateHolder.refreshPages() },
                    onKindSelected = pageStateHolder::selectKind,
                    onOpenPage = pageStateHolder::openPage,
                    onCloseDetail = pageStateHolder::closeDetail,
                    onToggleLikePage = pageStateHolder::toggleLikeSelectedPage,
                    onStartCreatingPage = pageStateHolder::startCreatingPage,
                    onStartEditingPage = pageStateHolder::startEditingSelectedPage,
                    onCancelEditingPage = pageStateHolder::cancelEditingPage,
                    onDraftChanged = pageStateHolder::updateDraft,
                    onSavePage = pageStateHolder::saveEditingPage,
                    onDeletePage = pageStateHolder::deleteSelectedPage,
                    onLoadMore = pageStateHolder::loadMore,
                    onOpenUser = onOpenUser,
                    currentUserId = accountUser?.id,
                )
                AppRoute.Gallery -> GalleryScreen(
                    state = galleryState,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onRefreshPosts = { galleryStateHolder.refreshPosts() },
                    onKindSelected = galleryStateHolder::selectKind,
                    onOpenPost = galleryStateHolder::openPost,
                    onCloseDetail = galleryStateHolder::closeDetail,
                    onToggleLikePost = galleryStateHolder::toggleLikeSelectedPost,
                    onCreatePost = galleryStateHolder::createPost,
                    onUpdatePost = galleryStateHolder::updateSelectedPost,
                    onDeletePost = galleryStateHolder::deleteSelectedPost,
                    onLoadMore = galleryStateHolder::loadMore,
                    onOpenUser = onOpenUser,
                    onOpenMedia = onOpenUrl,
                    onOpenMediaPreview = { mediaPreviewSession = it },
                    currentUserId = accountUser?.id,
                )
                AppRoute.Flash -> FlashScreen(
                    state = flashState,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onRefreshFlashes = { flashStateHolder.refreshFlashes() },
                    onKindSelected = flashStateHolder::selectKind,
                    onOpenFlash = flashStateHolder::openFlash,
                    onCloseDetail = flashStateHolder::closeDetail,
                    onToggleLikeFlash = flashStateHolder::toggleLikeSelectedFlash,
                    onStartCreateFlash = flashStateHolder::startCreatingFlash,
                    onStartEditFlash = flashStateHolder::startEditingSelectedFlash,
                    onDraftChanged = flashStateHolder::updateDraft,
                    onSaveDraft = flashStateHolder::saveDraft,
                    onCancelDraft = flashStateHolder::cancelDraft,
                    onDeleteFlash = flashStateHolder::deleteSelectedFlash,
                    onLoadMore = flashStateHolder::loadMore,
                    onOpenUser = onOpenUser,
                    onOpenFlashInWeb = { flashId ->
                        openUrl("${SharkeyAuthApi.DEFAULT_BASE_URL}${flashWebPath(flashId)}")
                    },
                )
                AppRoute.Announcements -> AnnouncementScreen(
                    state = announcementState,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            AppRoute.Discover
                        }
                    },
                    onRefresh = { announcementStateHolder.refresh() },
                    onOpenAnnouncement = announcementStateHolder::openAnnouncement,
                    onCloseDetail = announcementStateHolder::closeDetail,
                    onLoadMore = announcementStateHolder::loadMore,
                    onMarkRead = announcementStateHolder::markRead,
                    onEnterManagement = announcementStateHolder::enterManagement,
                    onExitManagement = announcementStateHolder::exitManagement,
                    onRefreshAdmin = announcementStateHolder::refreshAdmin,
                    onCreateAnnouncement = announcementStateHolder::createAnnouncement,
                    onUpdateAnnouncement = announcementStateHolder::updateAnnouncement,
                    onDeleteAnnouncement = announcementStateHolder::deleteAnnouncement,
                )
                AppRoute.Profile -> ProfileScreen(
                    state = userProfileState,
                    capabilities = instanceCapabilities,
                    onRefresh = userProfileStateHolder::load,
                    onLoadMoreNotes = userProfileStateHolder::loadMoreNotes,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                    selectedTheme = selectedTheme,
                    selectedTimelineDensity = selectedTimelineDensity,
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
                AppRoute.ProfileNotes -> ProfileNotesScreen(
                    state = userProfileState,
                    onBack = { route = AppRoute.Profile },
                    onRefresh = userProfileStateHolder::load,
                    onLoadMoreNotes = userProfileStateHolder::loadMoreNotes,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                )
                AppRoute.Settings -> SettingsRouteContent(
                    state = settingsState,
                    stateHolder = settingsStateHolder,
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
                    onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
                    onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
                    onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
                    onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
                    onClearChatMessageCache = {
                        currentAccountId?.let { accountId ->
                            appScope.launch {
                                chatMessageCache.clearAccount(accountId)
                            }
                            chatUnreadStore.clearAccount(accountId)
                        }
                    },
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
                    onBack = { route = AppRoute.Profile },
                    onOpenUser = onOpenUser,
                )
                is AppRoute.UserProfile -> ProfileScreen(
                    state = viewedProfileState,
                    onRefresh = { viewedProfileStateHolder.load(clearContent = true) },
                    onLoadMoreNotes = viewedProfileStateHolder::loadMoreNotes,
                    onOpenNote = { route = AppRoute.NoteDetail(it) },
                    onOpenUser = onOpenUser,
                    onBack = {
                        route = if (rootRoute == RootRoute.Profile) {
                            AppRoute.Profile
                        } else {
                            appRouteForRootRoute(rootRoute)
                        }
                    },
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                    title = "资料",
                    isOwnProfile = false,
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
                is AppRoute.NoteDetail -> NoteDetailScreen(
                    noteId = current.noteId,
                    state = noteDetailState,
                    onRefresh = { noteDetailStateHolder.load(current.noteId) },
                    onLoadMoreReplies = noteDetailStateHolder::loadMoreReplies,
                    onToggleChildReplies = noteDetailStateHolder::toggleChildReplies,
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
                    reactionOptions = noteActionState.reactionOptions,
                    recentReactions = noteActionState.recentReactions,
                    isActionPending = isNoteActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                    onBack = { route = AppRoute.Timeline },
                )
                is AppRoute.Compose -> ComposeScreen(
                    state = composeState,
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
                    customEmojis = noteActionState.customEmojis,
                    recentEmojiCodes = noteActionState.recentReactions,
                    completionState = composeCompletionState,
                    onCompletionTokenChanged = composeCompletionStateHolder::request,
                    onSend = composeStateHolder::send,
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
                    onDownload = { item ->
                        openUrl(item.openUrl)
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
            if (authInvalidDialogOpen) {
                AuthInvalidConfirmationDialog(
                    onDismiss = { authInvalidDialogOpen = false },
                    onConfirm = {
                        authInvalidDialogOpen = false
                        onAuthInvalid()
                    },
                )
            }
        }
    }
}
