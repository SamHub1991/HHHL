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
import cc.hhhl.client.automation.toMainStreamingOptions
import cc.hhhl.client.ai.AiProviderPreset
import cc.hhhl.client.ai.AiRepository
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
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.api.ChatStreamingEvent
import cc.hhhl.client.api.MainStreamingEvent
import cc.hhhl.client.api.toApiInstantOrNull
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.auth.AuthenticatedUser
import cc.hhhl.client.auth.AuthTokenStore
import cc.hhhl.client.auth.FixedAppAuthConfig
import cc.hhhl.client.auth.FixedAppAuthenticator
import cc.hhhl.client.auth.LoginStateHolder
import cc.hhhl.client.auth.NoopAuthTokenStore
import cc.hhhl.client.auth.SharkeyAuthApi
import cc.hhhl.client.cache.ChatMessageCache
import cc.hhhl.client.cache.ChatMessageCacheConversationType
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
import cc.hhhl.client.media.SpeechTextInput
import cc.hhhl.client.media.createImageProcessor
import cc.hhhl.client.model.Clip
import cc.hhhl.client.model.ClipListKind
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatUserConversation
import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.User
import cc.hhhl.client.notification.ChatNoiseReductionSettings
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
import cc.hhhl.client.repository.requiresRealtimeAttentionResolution
import cc.hhhl.client.repository.resolveRealtimeMessage
import cc.hhhl.client.repository.ChatStreamingRepository
import cc.hhhl.client.repository.ChatUserConversationRepositoryResult
import cc.hhhl.client.repository.ChannelTimelineRepositoryResult
import cc.hhhl.client.repository.ChannelRepository
import cc.hhhl.client.repository.ClipRepository
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
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
import cc.hhhl.client.repository.ComposeRepositoryResult
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
import cc.hhhl.client.state.FavoriteMessageStore
import cc.hhhl.client.state.NoopFavoriteMessageStore
import cc.hhhl.client.state.FavoriteMessageConversationType
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
import cc.hhhl.client.state.AVATAR_MAX_FILE_SIZE_BYTES
import cc.hhhl.client.state.UserProfileUiState
import cc.hhhl.client.state.UserSocialStateHolder
import cc.hhhl.client.state.UserSocialUiState
import cc.hhhl.client.navigation.AppRoute
import cc.hhhl.client.navigation.MentionNavigationTarget
import cc.hhhl.client.navigation.RootRoute
import cc.hhhl.client.navigation.SiteLinkNavigationTarget
import cc.hhhl.client.navigation.aiAssistantReviewPageTarget
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
import cc.hhhl.client.update.releaseNotesTimeline
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import cc.hhhl.client.ui.component.HhhlBottomNav
import cc.hhhl.client.ui.component.HhhlAlertDialog
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.aiResultChecklistText
import cc.hhhl.client.ui.component.aiResultMutedWordCandidate
import cc.hhhl.client.ui.component.aiResultReferencedNoteId
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
import cc.hhhl.client.ui.screen.AutomationExecutionLogScreen
import cc.hhhl.client.ui.screen.AutomationScreen
import cc.hhhl.client.ui.screen.AdminDashboardScreen
import cc.hhhl.client.ui.screen.AchievementScreen
import cc.hhhl.client.ui.screen.AntennaScreen
import cc.hhhl.client.ui.screen.AiAssistantFloatingOrb
import cc.hhhl.client.ui.screen.AiAssistantMessage
import cc.hhhl.client.ui.screen.AiAssistantMessageStatus
import cc.hhhl.client.ui.screen.AiAssistantRole
import cc.hhhl.client.ui.screen.AiAssistantScreen
import cc.hhhl.client.ui.screen.AiAssistantActionKind
import cc.hhhl.client.ui.screen.AiAssistantActionProposal
import cc.hhhl.client.ui.screen.AiAssistantActionStatus
import cc.hhhl.client.ui.screen.AiAssistantActionPayload
import cc.hhhl.client.ui.screen.AiAssistantAutoApprovalSettings
import cc.hhhl.client.ui.screen.AiSettingsScreen
import cc.hhhl.client.ui.screen.aiAssistantActionPayload
import cc.hhhl.client.ui.screen.aiAssistantOutgoingDraftText
import cc.hhhl.client.ui.screen.aiAssistantStructuredReply
import cc.hhhl.client.ui.screen.aiAssistantSuggestedActions
import cc.hhhl.client.ui.screen.canAutoApprove
import cc.hhhl.client.ui.screen.canManageChatRoom
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
import cc.hhhl.client.ui.screen.ReleaseNotesTimelineScreen
import cc.hhhl.client.ui.screen.SettingsScreen
import cc.hhhl.client.ui.screen.SettingsManagementScreen
import cc.hhhl.client.ui.screen.settingsWebManagementPath
import cc.hhhl.client.ui.screen.settingsManagementSectionKey
import cc.hhhl.client.ui.screen.ThemeCustomizationScreen
import cc.hhhl.client.ui.screen.TimelineScreen
import cc.hhhl.client.ui.screen.UserListScreen
import cc.hhhl.client.ui.screen.UserSocialScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HhhlApp(
    openUrl: (String) -> Unit = {},
    shareUrl: (String) -> Unit = {},
    downloadUrl: (String, String, String) -> Unit = { url, _, _ -> openUrl(url) },
    mediaPicker: MediaPicker? = null,
    speechTextInput: SpeechTextInput? = null,
    authCallbackSession: String? = null,
    fixedAppAuthClientId: String = "",
    fixedAppAuthSecret: String = "",
    authTokenStore: AuthTokenStore = NoopAuthTokenStore,
    themeStore: ThemeStore = NoopThemeStore,
    displayPreferenceStore: DisplayPreferenceStore = NoopDisplayPreferenceStore,
    recentReactionStore: RecentReactionStore = NoopRecentReactionStore,
    specialCareStore: SpecialCareStore = NoopSpecialCareStore,
    automationStore: AutomationStore = NoopAutomationStore,
    aiStore: AiStore = NoopAiStore,
    composeDraftStore: ComposeDraftStore = NoopComposeDraftStore,
    favoriteMessageStore: FavoriteMessageStore = NoopFavoriteMessageStore,
    chatMessageCache: ChatMessageCache = NoopChatMessageCache,
    chatUnreadStore: ChatUnreadStore = NoopChatUnreadStore,
    notificationCache: NotificationCache = NoopNotificationCache,
    notificationReadStore: NotificationReadStore = NoopNotificationReadStore,
    timelineCache: TimelineCache? = null,
    backgroundNotificationsEnabled: Boolean = false,
    specialCareBackgroundNotificationsEnabled: Boolean = true,
    chatNoiseReductionSettings: ChatNoiseReductionSettings = ChatNoiseReductionSettings(),
    onBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onChatNoiseReductionSettingsChanged: (ChatNoiseReductionSettings) -> Unit = {},
    onSpecialCareUsersChanged: (Set<String>) -> Unit = {},
    onSpecialCareSystemNotification: (NotificationItem) -> Unit = {},
    onAutomationSystemNotification: ((String, String) -> Boolean?)? = null,
    onAiQueueChanged: () -> Unit = {},
    initialSharedText: String? = null,
    onInitialSharedTextConsumed: () -> Unit = {},
    appVersionName: String = "",
    releaseNotesStore: ReleaseNotesStore = NoopReleaseNotesStore,
    onCheckForUpdates: (((String) -> Unit) -> Unit) = { report -> report("当前平台暂不支持应用内更新") },
    onOpenBatteryOptimizationSettings: () -> Unit = {},
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit) = {},
    onAuthCallbackConsumed: () -> Unit = {},
) {
    val appScope = rememberCoroutineScope()
    val loginStateHolder = remember(fixedAppAuthClientId, fixedAppAuthSecret) {
        val fixedAppAuthConfig = FixedAppAuthConfig(
            clientId = fixedAppAuthClientId,
            appSecret = fixedAppAuthSecret,
        )
        LoginStateHolder(
            authenticator = if (fixedAppAuthConfig.isEnabled) {
                FixedAppAuthenticator(fixedAppAuthConfig)
            } else {
                SharkeyAuthApi()
            },
            tokenStore = authTokenStore,
            onAccountRemoved = { accountId ->
                chatMessageCache.clearAccount(accountId)
                chatUnreadStore.clearAccount(accountId)
                specialCareStore.clearAccount(accountId)
                automationStore.clearAccount(accountId)
                aiStore.clearAccount(accountId)
                favoriteMessageStore.clearAccount(accountId)
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
                    speechTextInput = speechTextInput,
                    timelineCache = timelineCache,
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
                    chatNoiseReductionSettings = chatNoiseReductionSettings,
                    onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
                    onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
                    onChatNoiseReductionSettingsChanged = onChatNoiseReductionSettingsChanged,
                    onSpecialCareUsersChanged = onSpecialCareUsersChanged,
                    onSpecialCareSystemNotification = onSpecialCareSystemNotification,
                    onAutomationSystemNotification = onAutomationSystemNotification,
                    onAiQueueChanged = onAiQueueChanged,
                    initialSharedText = initialSharedText,
                    onInitialSharedTextConsumed = onInitialSharedTextConsumed,
                    appVersionName = appVersionName,
                    releaseNotesStore = releaseNotesStore,
                    onCheckForUpdates = onCheckForUpdates,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
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
        addAll(noteDetailState.conversationNotes)
        addAll(noteDetailState.renoteNotes)
        noteDetailState.childRepliesByParentId.values.forEach { addAll(it) }
        userProfileState.user?.pinnedNotes?.let { addAll(it) }
        viewedProfileState.user?.pinnedNotes?.let { addAll(it) }
        channelState.selectedChannel?.pinnedNotes?.let { addAll(it) }
        channelState.channels.forEach { addAll(it.pinnedNotes) }
        timelineState.tabs.values.forEach { addAll(it.notes) }
        addAll(userProfileState.notes)
        addAll(viewedProfileState.notes)
        addAll(discoverState.notes)
        addAll(discoverState.recommendedNotes)
        addAll(discoverState.discoverySections.coverNotes)
        addAll(discoverState.discoverySections.hotNotes)
        addAll(discoverState.discoverySections.tutorialNotes)
        discoverState.discoverySections.channels.forEach { addAll(it.pinnedNotes) }
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
        AppRoute.AiAssistant -> "AI 助手"
        AppRoute.Notifications -> "通知"
        AppRoute.Profile -> "我的"
        AppRoute.ProfileNotes -> "我的帖子"
        AppRoute.Settings -> "设置"
        AppRoute.AiSettings -> "AI 设置"
        AppRoute.ReleaseNotes -> "更新日志"
        AppRoute.ThemeCustomization -> "主题自定义"
        AppRoute.Automation -> "自动化"
        AppRoute.AutomationLogs -> "自动化日志"
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

private fun chatAiMessageSelection(
    kind: AiTaskKind,
    chat: ChatUiState,
): Pair<List<ChatMessage>, String> {
    val messages = chat.messages
    return when (kind) {
        AiTaskKind.ChatRecentSummary -> messages.takeLast(50) to "范围：最近 50 条消息。"
        AiTaskKind.ChatTodaySummary -> {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val todayMessages = messages.filter { message ->
                message.createdAt.toApiInstantOrNull()
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.date == today
            }
            if (todayMessages.isNotEmpty()) {
                todayMessages.takeLast(80) to "范围：今天的聊天消息。"
            } else {
                messages.takeLast(50) to "范围：未找到可解析为今天的消息时间，以下为最近 50 条兜底上下文。"
            }
        }
        AiTaskKind.ChatUnreadSummary -> {
            val unreadCount = when {
                chat.selectedRoom != null -> chat.selectedRoomUnreadCount
                chat.selectedUserConversation != null -> chat.selectedUserUnreadCount
                else -> 0
            }.coerceAtLeast(0)
            if (unreadCount > 0) {
                messages.takeLast(unreadCount.coerceAtMost(80)) to "范围：未读期间的 $unreadCount 条消息。"
            } else {
                emptyList<ChatMessage>() to "当前会话没有未读消息。"
            }
        }
        else -> messages.takeLast(80) to "范围：最近聊天消息。"
    }
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
    onOpenTimeline: () -> Unit,
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
        dismissButton = {
            HhhlTextButton(onClick = onOpenTimeline) {
                Text("更新日志")
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

private fun ChatAttentionKind.toNotificationType(): NotificationType {
    return when (this) {
        ChatAttentionKind.SpecialCare -> NotificationType.App
        ChatAttentionKind.Mention -> NotificationType.Mention
        ChatAttentionKind.Reply -> NotificationType.Reply
        ChatAttentionKind.Quote -> NotificationType.Quote
    }
}

private fun ChatAttentionKind.notificationPrefix(): String {
    return when (this) {
        ChatAttentionKind.SpecialCare -> "特别关心"
        ChatAttentionKind.Mention -> "有人 @ 你"
        ChatAttentionKind.Reply -> "有人回复你"
        ChatAttentionKind.Quote -> "有人引用你"
    }
}

private fun ChatMessage.toChatAttentionNotification(
    kind: ChatAttentionKind,
    roomName: String = "",
    directUserId: String? = null,
    fallbackEpochMillis: Long,
): NotificationItem {
    val sourceId = directUserId?.takeIf { it.isNotBlank() } ?: roomId
    val messageId = automationMessageKey()
    val location = if (directUserId.isNullOrBlank()) {
        roomName.takeIf { it.isNotBlank() }?.let { "在聊天室 $it" } ?: "在聊天室"
    } else {
        "在聊天中"
    }
    return NotificationItem(
        id = "chat-attention-${kind.name.lowercase()}-$sourceId-$messageId",
        type = kind.toNotificationType(),
        actor = fromUser,
        text = "${kind.notificationPrefix()} · $location 发来了新消息",
        createdAtLabel = createdAtLabel.ifBlank { "刚刚" },
        createdAtEpochMillis = createdAt.toApiInstantOrNull()?.toEpochMilliseconds()
            ?: fallbackEpochMillis,
        notePreviewText = chatMessageBodyText(this),
        isSpecialCare = kind == ChatAttentionKind.SpecialCare,
        chatRoomId = roomId.takeIf { it.isNotBlank() },
        chatUserId = directUserId?.takeIf { it.isNotBlank() },
        chatMessageId = id.takeIf { it.isNotBlank() },
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
    AiAssistant,
}

private data class UserListQuickTarget(
    val user: User,
)

private data class PendingAiAssistantRoomManagementAction(
    val actionId: String,
    val successMessages: Set<String>,
    val started: Boolean = false,
)

private data class PendingAiAssistantObservedAction(
    val actionId: String,
    val started: Boolean = false,
)

private data class AiAssistantRouteState(
    val messages: List<AiAssistantMessage>,
    val draft: String,
    val contextSummary: String,
    val aiState: AiUiState,
    val isProcessing: Boolean,
    val attachments: List<DriveFile>,
    val isUploadingAttachment: Boolean,
    val mediaPicker: MediaPicker?,
    val customEmojis: List<CustomEmoji>,
    val recentEmojiCodes: List<String>,
    val autoApprovalSettings: AiAssistantAutoApprovalSettings,
)

private data class AiAssistantRouteActions(
    val onDraftChanged: (String) -> Unit,
    val onSend: () -> Unit,
    val onSendPrompt: (String) -> Unit,
    val onRetry: (String, List<DriveFile>) -> Unit,
    val onNewConversation: () -> Unit,
    val onUploadAttachment: (DriveFileUpload) -> Unit,
    val onUploadAttachmentError: (String) -> Unit,
    val onOpenDrivePicker: () -> Unit,
    val onRemoveAttachment: (String) -> Unit,
    val onOpenAttachmentUrl: (String) -> Unit,
    val onOpenAutomation: () -> Unit,
    val onAutoApprovalSettingsChanged: (AiAssistantAutoApprovalSettings) -> Unit,
    val onApproveAction: (String) -> Unit,
    val onRejectAction: (String) -> Unit,
    val onBack: () -> Unit,
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
private const val AUTOMATION_ROOM_SCAN_LIMIT = 64
private const val REALTIME_CHAT_STREAM_ROOM_LIMIT = 256
private const val REALTIME_CHAT_STREAM_USER_LIMIT = 256
private const val AI_ASSISTANT_MAX_ATTACHMENTS = 8

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

private fun aiAssistantAttachmentContext(files: List<DriveFile>): String {
    if (files.isEmpty()) return ""
    return buildString {
        appendLine("附件列表：")
        files.take(AI_ASSISTANT_MAX_ATTACHMENTS).forEachIndexed { index, file ->
            val name = file.name.ifBlank { "附件 ${index + 1}" }
            val type = file.type.ifBlank { "unknown" }
            appendLine("${index + 1}. $name")
            appendLine("   id: ${file.id}")
            appendLine("   type: $type")
            appendLine("   size: ${file.size} bytes")
            file.comment?.takeIf { it.isNotBlank() }?.let { appendLine("   comment: ${it.take(240)}") }
            file.url?.takeIf { it.isNotBlank() }?.let { appendLine("   url: $it") }
        }
    }.trim()
}

private data class AiAssistantSendStart(
    val promptForAi: String,
    val attachmentContext: String,
    val pendingMessageId: String,
    val messages: List<AiAssistantMessage>,
    val draft: String,
    val attachments: List<DriveFile>,
    val memoryText: String,
)

internal fun aiAssistantReplyRequestIsCurrent(
    requestGeneration: Long,
    currentGeneration: Long,
): Boolean {
    return requestGeneration == currentGeneration
}

private fun prepareAiAssistantSend(
    prompt: String,
    attachments: List<DriveFile>,
    currentAttachments: List<DriveFile>,
    currentDraft: String,
    messages: List<AiAssistantMessage>,
): AiAssistantSendStart {
    val cleanPrompt = prompt.trim()
    val cleanAttachments = attachments.distinctBy { it.id }.take(AI_ASSISTANT_MAX_ATTACHMENTS)
    val promptForAi = cleanPrompt.ifBlank { "请分析这些附件，并结合当前应用上下文给出结论或下一步建议。" }
    val attachmentContext = aiAssistantAttachmentContext(cleanAttachments)
    val now = Clock.System.now().toEpochMilliseconds()
    val userMessage = AiAssistantMessage(
        id = "assistant-user-$now",
        role = AiAssistantRole.User,
        text = cleanPrompt.ifBlank { "请分析附件" },
        attachments = cleanAttachments,
    )
    val pendingMessage = AiAssistantMessage(
        id = "assistant-pending-$now",
        role = AiAssistantRole.Assistant,
        text = "正在结合当前上下文生成回复...",
        status = AiAssistantMessageStatus.Sending,
        retryPrompt = promptForAi,
        retryAttachments = cleanAttachments,
    )
    val memoryText = (messages + userMessage)
        .takeLast(12)
        .joinToString("\n") { message ->
            val files = aiAssistantAttachmentContext(message.attachments).takeIf { it.isNotBlank() }
                ?.let { "\n$it" }
                .orEmpty()
            "${message.role.label}: ${message.text}$files"
        }
    return AiAssistantSendStart(
        promptForAi = promptForAi,
        attachmentContext = attachmentContext,
        pendingMessageId = pendingMessage.id,
        messages = messages + userMessage + pendingMessage,
        draft = if (cleanAttachments == currentAttachments) "" else currentDraft,
        attachments = if (cleanAttachments == currentAttachments) emptyList() else currentAttachments,
        memoryText = memoryText,
    )
}

private fun completeAiAssistantMessage(
    messages: List<AiAssistantMessage>,
    pendingMessageId: String,
    result: AiRepositoryResult,
    promptForAi: String,
): Pair<List<AiAssistantMessage>, List<AiAssistantActionProposal>> {
    return when (result) {
        is AiRepositoryResult.Success -> {
            val structuredReply = aiAssistantStructuredReply(result.text)
            val visibleReply = structuredReply.visibleText.ifBlank { result.text.trim() }
            val actions = aiAssistantSuggestedActions(
                prompt = promptForAi,
                reply = result.text,
                idPrefix = pendingMessageId,
            )
            messages.map { message ->
                if (message.id == pendingMessageId) {
                    message.copy(
                        text = visibleReply,
                        status = AiAssistantMessageStatus.Completed,
                        actions = actions,
                    )
                } else {
                    message
                }
            } to actions
        }
        AiRepositoryResult.Unauthorized -> {
            messages.map { message ->
                if (message.id == pendingMessageId) {
                    message.copy(
                        text = "AI API Key 无效或权限不足",
                        status = AiAssistantMessageStatus.Failed,
                    )
                } else {
                    message
                }
            } to emptyList()
        }
        is AiRepositoryResult.Error -> {
            messages.map { message ->
                if (message.id == pendingMessageId) {
                    message.copy(
                        text = result.message,
                        status = AiAssistantMessageStatus.Failed,
                    )
                } else {
                    message
                }
            } to emptyList()
        }
    }
}

private fun aiAssistantUploadToast(result: DriveFileRepositoryResult): String {
    return when (result) {
        is DriveFileRepositoryResult.Success -> "已添加附件"
        DriveFileRepositoryResult.Unauthorized -> "登录已失效，请重新登录"
        is DriveFileRepositoryResult.ValidationError -> result.message
        is DriveFileRepositoryResult.Error -> result.message
    }
}

private fun appendAiAssistantAttachment(current: List<DriveFile>, file: DriveFile): List<DriveFile> {
    return (current + file)
        .distinctBy { it.id }
        .take(AI_ASSISTANT_MAX_ATTACHMENTS)
}

private fun startAiAssistantAttachmentUpload(
    upload: DriveFileUpload,
    isUploading: Boolean,
    currentAttachments: List<DriveFile>,
    tokenProvider: () -> String?,
    scope: CoroutineScope,
    onToast: (String) -> Unit,
    onUploadingChanged: (Boolean) -> Unit,
    onAttachFile: (DriveFile) -> Unit,
) {
    if (isUploading) {
        onToast("附件正在上传，稍后再选")
        return
    }
    if (currentAttachments.size >= AI_ASSISTANT_MAX_ATTACHMENTS) {
        onToast("AI 助手最多添加 $AI_ASSISTANT_MAX_ATTACHMENTS 个附件")
        return
    }
    onUploadingChanged(true)
    onToast("正在上传附件")
    val repository = DriveFileRepository(tokenProvider = tokenProvider)
    scope.launch {
        val result = repository.upload(upload)
        if (result is DriveFileRepositoryResult.Success) {
            onAttachFile(result.file)
        }
        onToast(aiAssistantUploadToast(result))
        onUploadingChanged(false)
    }
}

internal fun startAiAssistantReply(
    prompt: String,
    attachments: List<DriveFile>,
    currentAttachments: List<DriveFile>,
    currentDraft: String,
    messages: List<AiAssistantMessage>,
    pendingPrompt: String?,
    aiProcessing: Boolean,
    attachmentUploading: Boolean,
    aiStateHolder: AiStateHolder,
    scope: CoroutineScope,
    contextTextProvider: () -> String,
    requestGeneration: Long,
    isRequestCurrent: (Long) -> Boolean,
    onToast: (String) -> Unit,
    onMessagesChanged: (List<AiAssistantMessage>) -> Unit,
    onPendingPromptChanged: (String?) -> Unit,
    onDraftChanged: (String) -> Unit,
    onAttachmentsChanged: (List<DriveFile>) -> Unit,
    onAutoApproveActions: (List<AiAssistantActionProposal>) -> Unit,
) {
    val cleanPrompt = prompt.trim()
    val cleanAttachments = attachments.distinctBy { it.id }.take(AI_ASSISTANT_MAX_ATTACHMENTS)
    if (cleanPrompt.isBlank() && cleanAttachments.isEmpty()) return
    if (pendingPrompt != null || aiProcessing) {
        onToast("AI 正在处理上一条消息")
        return
    }
    if (attachmentUploading) {
        onToast("附件上传中，稍后再发送")
        return
    }
    val prepared = prepareAiAssistantSend(
        prompt = prompt,
        attachments = cleanAttachments,
        currentAttachments = currentAttachments,
        currentDraft = currentDraft,
        messages = messages,
    )
    onMessagesChanged(prepared.messages)
    onPendingPromptChanged(prepared.promptForAi)
    onDraftChanged(prepared.draft)
    onAttachmentsChanged(prepared.attachments)
    scope.launch {
        val result = aiStateHolder.runBlockingTask(
            AiTaskKind.AssistantChat,
            AiTaskInput(
                prompt = prepared.promptForAi,
                text = prepared.memoryText,
                automationEventText = contextTextProvider(),
                fileIds = cleanAttachments.map { it.id },
                fileContext = prepared.attachmentContext,
            ),
        )
        if (!isRequestCurrent(requestGeneration)) return@launch
        val (nextMessages, actions) = completeAiAssistantMessage(
            messages = prepared.messages,
            pendingMessageId = prepared.pendingMessageId,
            result = result,
            promptForAi = prepared.promptForAi,
        )
        onMessagesChanged(nextMessages)
        if (actions.isNotEmpty()) onAutoApproveActions(actions)
        onPendingPromptChanged(null)
    }
}

private fun ChatMessage.unreadMarker(): String {
    return automationMessageKey()
}

private fun ChatRoom.unreadMarker(): String {
    return latestMessageMarker.ifBlank { latestMessageAtLabel }
}

private fun ChatUserConversation.unreadMarker(): String {
    return latestMessage?.unreadMarker().orEmpty()
}

private fun ChatMessage.directPeerId(currentUserId: String): String? {
    val cleanCurrentUserId = currentUserId.trim()
    val recipientId = toUserId?.trim()?.takeIf { it.isNotEmpty() }
        ?: toUser?.id?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    return when {
        cleanCurrentUserId.isNotBlank() && fromUser.id == cleanCurrentUserId -> recipientId
        cleanCurrentUserId.isNotBlank() && recipientId == cleanCurrentUserId -> fromUser.id
        cleanCurrentUserId.isBlank() && fromUser.id.isNotBlank() -> fromUser.id
        else -> null
    }?.takeIf { it.isNotBlank() }
}

private fun ChatMessage.belongsToDirectChat(userId: String): Boolean {
    val cleanUserId = userId.trim()
    return cleanUserId.isNotEmpty() &&
        (fromUser.id == cleanUserId || toUserId == cleanUserId || toUser?.id == cleanUserId)
}

internal fun String.aiAssistantTargetsCurrentChatRoom(): Boolean {
    val clean = trim()
    if (clean.isBlank()) return true
    if (listOf(
        "当前聊天室",
        "当前房间",
        "当前群聊",
        "当前群",
        "当前聊天",
        "当前会话",
        "当前对话",
        "当前已打开",
        "选中的聊天室",
        "选中的房间",
        "选中的群聊",
        "选中的聊天",
        "这个聊天室",
        "这个房间",
        "这个群聊",
        "这个群",
        "这个聊天",
        "这个会话",
        "这个对话",
        "此聊天室",
        "此房间",
        "本聊天室",
        "本房间",
        "本群",
        "这里",
        "这儿",
        "这边",
    ).any { marker -> clean.contains(marker, ignoreCase = true) }) {
        return true
    }
    val compact = clean.replace(Regex("\\s+"), "")
    return listOf(
        Regex("^(删|删除|移除)(了|掉)?(聊天室|房间|群聊|群)$"),
        Regex("^删群$"),
        Regex("^(删|删除|移除|解散)((了|掉)吧?|吧|下|一下)$"),
        Regex("^(清空|清理|清除|清掉|清)(聊天室|房间|群聊|群|聊天|会话|对话)?(消息|聊天)?(记录)?$"),
        Regex("^(清空|清理|清除|清掉|清)((了|掉)吧?|吧|下|一下)$"),
        Regex("^(退出|离开|退)(聊天室|房间|群聊|群|会话|对话)$"),
        Regex("^(退出|离开|退)((了|掉)吧?|吧|下|一下)$"),
        Regex("^(静音|免打扰)(聊天室|房间|群聊|群|聊天|会话|对话)$"),
        Regex("^(静音|免打扰)(吧|下|一下)?$"),
        Regex("^(取消静音|解除静音)(聊天室|房间|群聊|群|聊天|会话|对话)$"),
        Regex("^(取消静音|解除静音)(吧|下|一下)?$"),
    ).any { regex -> regex.matches(compact) }
}

internal fun String.aiAssistantCleanTarget(): String {
    return trim()
        .trim('@', '＠', ' ', '：', ':', '，', ',', '。', '.', '；', ';', '"', '\'', '“', '”', '‘', '’')
        .lowercase()
}

internal fun aiAssistantFuzzyTargetMatches(
    name: String,
    target: String,
): Boolean {
    val cleanName = name.aiAssistantCleanTarget()
    val cleanTarget = target.aiAssistantCleanTarget()
    if (cleanName.isBlank() || cleanTarget.isBlank() || cleanName == cleanTarget) return false
    if (!cleanName.isAiAssistantUsableFuzzyTarget() || !cleanTarget.isAiAssistantUsableFuzzyTarget()) {
        return false
    }
    return cleanName.contains(cleanTarget) || cleanTarget.contains(cleanName)
}

internal fun User.aiAssistantAcct(): String {
    val cleanUsername = username.trim().trim('@')
    val cleanHost = host?.trim().orEmpty()
    return if (cleanHost.isNotBlank() && !cleanUsername.contains("@")) {
        "$cleanUsername@$cleanHost"
    } else {
        cleanUsername
    }
}

internal fun User.aiAssistantMention(): String {
    val acct = aiAssistantAcct()
    return if (acct.isNotBlank()) "@$acct" else displayName.trim()
}

internal fun User.aiAssistantMatchesTarget(target: String, fuzzy: Boolean = false): Boolean {
    val clean = target.aiAssistantCleanTarget()
    if (clean.isBlank()) return false
    val names = listOf(
        id,
        username,
        displayName,
        aiAssistantAcct(),
        "@${aiAssistantAcct()}",
        "@${username.trim().trim('@')}",
    )
        .map { it.aiAssistantCleanTarget() }
        .filter { it.isNotBlank() }
    return if (fuzzy) {
        names.any { name -> aiAssistantFuzzyTargetMatches(name, clean) }
    } else {
        clean in names
    }
}

internal fun Iterable<User>.aiAssistantMatchingUsers(
    target: String,
    fuzzy: Boolean = false,
): List<User> {
    return filter { user -> user.aiAssistantMatchesTarget(target, fuzzy = fuzzy) }
        .distinctBy { it.id }
}

private fun String.isAiAssistantUsableFuzzyTarget(): Boolean {
    val cjkCount = count { it.isAiAssistantCjk() }
    val otherCount = count { it.isLetterOrDigit() && !it.isAiAssistantCjk() }
    return cjkCount >= 2 || otherCount >= 3
}

private fun Char.isAiAssistantCjk(): Boolean {
    return this in '\u3400'..'\u4DBF' ||
        this in '\u4E00'..'\u9FFF' ||
        this in '\uF900'..'\uFAFF'
}

private fun String.containsRoomIdentifier(room: ChatRoom): Boolean {
    val clean = trim()
    if (clean.isBlank()) return false
    val id = room.id.trim()
    val name = room.name.trim()
    return (id.isNotBlank() && clean.contains(id, ignoreCase = true)) ||
        (name.length >= 2 && clean.contains(name, ignoreCase = true))
}

private fun canAttemptAiAssistantRoomManagement(
    room: ChatRoom,
    ownedRooms: List<ChatRoom>,
    currentUserId: String?,
): Boolean {
    if (canManageChatRoom(room, ownedRooms, currentUserId)) return true
    // The owned-room list can be stale or paged. Let the server make the final
    // permission decision so high-risk auto approval can still call the action.
    return room.id.isNotBlank()
}

private fun String.targetsCurrentNote(): Boolean {
    val clean = trim()
    if (clean.isBlank()) return true
    return listOf("当前帖子", "当前动态", "当前 note", "这条帖子", "这个帖子", "这条动态", "这个动态", "当前页面帖子")
        .any { marker -> clean.contains(marker, ignoreCase = true) }
}

private fun String.containsNoteIdentifier(note: Note): Boolean {
    val clean = trim()
    val id = note.id.trim()
    return id.isNotBlank() && clean.contains(id, ignoreCase = true)
}

private data class RealtimeChatStreamTargets(
    val roomIds: List<String>,
    val userIds: List<String>,
    val roomNamesById: Map<String, String> = emptyMap(),
) {
    val roomIdSet: Set<String> = roomIds.toSet()
    val userIdSet: Set<String> = userIds.toSet()
    val isEmpty: Boolean = roomIds.isEmpty() && userIds.isEmpty()
}

private data class LocalRealtimeChatTargets(
    val roomIds: Collection<String> = emptyList(),
    val userIds: Collection<String> = emptyList(),
)

private fun realtimeChatStreamTargets(
    rooms: List<ChatRoom>,
    conversations: List<ChatUserConversation>,
    rememberedRoomIds: Collection<String> = emptyList(),
    rememberedUserIds: Collection<String> = emptyList(),
    specialCareUserIds: Set<String>,
    automationRules: List<AutomationRule>,
    selectedRoomId: String?,
    selectedUserId: String?,
): RealtimeChatStreamTargets {
    val chatRules = automationRules.filter { rule ->
        rule.enabled && (rule.trigger == AutomationTrigger.ChatMessage || rule.trigger == AutomationTrigger.ChatAttention || rule.trigger == AutomationTrigger.SpecialCare)
    }
    val explicitRoomIds = chatRules.flatMap { rule ->
        rule.conditions
            .filter { condition -> condition.enabled && condition.type == AutomationConditionType.RoomId }
            .flatMap { condition -> condition.value.splitAutomationTargetValues() }
    }
    val explicitUserIds = chatRules.flatMap { rule ->
        rule.conditions
            .filter { condition ->
                condition.enabled &&
                    (condition.type == AutomationConditionType.DirectUserId || condition.type == AutomationConditionType.SenderUserId || condition.type == AutomationConditionType.SenderUserIds)
            }
            .flatMap { condition -> condition.value.splitAutomationTargetValues() }
    }
    val roomIds = buildList {
        addAll(explicitRoomIds)
        selectedRoomId?.let { add(it) }
        rooms.filter { room -> room.unreadCount > 0 }.forEach { add(it.id) }
        addAll(rememberedRoomIds)
        rooms.forEach { add(it.id) }
    }.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .distinct()
        .take(REALTIME_CHAT_STREAM_ROOM_LIMIT)

    val userIds = buildList {
        addAll(explicitUserIds)
        selectedUserId?.let { add(it) }
        conversations.filter { conversation -> conversation.unreadCount > 0 }.forEach { add(it.user.id) }
        addAll(specialCareUserIds)
        addAll(rememberedUserIds)
        conversations.filter { conversation -> conversation.user.id in specialCareUserIds }.forEach { add(it.user.id) }
        conversations.forEach { add(it.user.id) }
    }.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .distinct()
        .take(REALTIME_CHAT_STREAM_USER_LIMIT)

    return RealtimeChatStreamTargets(
        roomIds = roomIds,
        userIds = userIds,
        roomNamesById = rooms
            .filter { room -> room.id.isNotBlank() && room.name.isNotBlank() }
            .associate { room -> room.id to room.name },
    )
}

private fun String.splitAutomationTargetValues(): List<String> {
    return split(',', '，', '\n', ';', '；', '|', '/', '、')
        .map { it.trim().trim('@') }
        .filter { it.isNotEmpty() }
}

private fun List<ChatRoom>.mergeRealtimeOwnedRooms(ownedRooms: List<ChatRoom>): List<ChatRoom> {
    if (ownedRooms.isEmpty()) return this
    val ownedById = ownedRooms.associateBy { room -> room.id }
    val existingIds = map { room -> room.id }.toSet()
    return map { room -> ownedById[room.id] ?: room } +
        ownedRooms.filterNot { room -> room.id in existingIds }
}

private data class AutomationRoomScanTarget(
    val sourceId: String,
    val roomId: String,
    val roomName: String,
    val marker: String,
)

private data class AiAssistantActionExecutionResult(
    val success: Boolean,
    val message: String,
)

private fun List<ChatMessage>.automationMessagesAfterBaseline(baselineId: String): List<ChatMessage> {
    if (baselineId.isBlank()) return emptyList()
    val baselineIndex = indexOfLast { message -> message.automationMessageKey() == baselineId }
    return if (baselineIndex >= 0) drop(baselineIndex + 1) else listOfNotNull(lastOrNull())
}

private fun List<ChatMessage>.recentAutomationMessages(
    baselineId: String?,
    unreadCount: Int,
    allowLatestOnFirstScan: Boolean,
): List<ChatMessage> {
    val cleanBaseline = baselineId.orEmpty()
    if (cleanBaseline.isNotBlank()) return automationMessagesAfterBaseline(cleanBaseline)
    if (!allowLatestOnFirstScan) return emptyList()
    val count = unreadCount.coerceAtLeast(0)
    return if (count > 0) takeLast(count.coerceAtMost(size)) else listOfNotNull(lastOrNull())
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
    val onEdit: (Note) -> Unit,
    val onDelete: (String) -> Unit,
    val onOpenMedia: (String) -> Unit,
    val onOpenMediaPreview: (MediaPreviewSession) -> Unit,
    val onOpenMention: (String) -> Unit,
    val onOpenHashtag: (String) -> Unit,
    val onVotePoll: (String, Int) -> Unit,
    val isActionPending: (String) -> Boolean,
    val canEditNote: (Note) -> Boolean,
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
    onOpenAiAssistant: () -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    aiResultText: String?,
    aiResultLabel: String?,
    onAiAction: (AiTaskKind, ChatUiState, String) -> Unit,
    onCopyAiResult: (String) -> Unit,
    onAddAiMutedWord: (String) -> Unit,
    onCreateAutomationFromAiResult: (String) -> Unit,
    onDismissAiResult: () -> Unit,
    onFavoriteMessage: (FavoriteMessageConversationType, String, String, ChatMessage) -> Unit,
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit),
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
        onSetRoomGroup = stateHolder::setRoomGroup,
        onDeleteRoomFromList = stateHolder::deleteRoom,
        onToggleUserConversationPinned = stateHolder::toggleUserConversationPinned,
        onDeleteUserConversation = stateHolder::deleteUserConversation,
        onOpenAiAssistant = onOpenAiAssistant,
        onCreateRoom = stateHolder::createRoom,
        onRefreshRoomExtras = stateHolder::refreshRoomExtras,
        onJoinRoomInvitation = stateHolder::joinInvitedRoom,
        onIgnoreRoomInvitation = stateHolder::ignoreRoomInvitation,
        onBackToRooms = stateHolder::closeRoom,
        onRefreshMessages = stateHolder::refreshMessages,
        onLoadOlderMessages = stateHolder::loadOlderMessages,
        onSearchMessages = stateHolder::searchMessages,
        onLoadMoreMessageSearch = stateHolder::loadMoreMessageSearch,
        onSearchChatUsers = stateHolder::searchChatUsers,
        onShowMessages = stateHolder::showMessages,
        onShowMembers = stateHolder::showMembers,
        onLoadMoreMembers = stateHolder::loadMoreMembers,
        onUpdateRoom = { name, description, joinMode ->
            stateHolder.updateSelectedRoom(name, description, joinMode)
        },
        onUpdateRoomManagement = stateHolder::updateSelectedRoomManagement,
        onClearRoomMessages = stateHolder::clearSelectedRoomMessages,
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
        onFavoriteMessage = onFavoriteMessage,
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
        onDismissErrorMessage = stateHolder::dismissErrorMessage,
        onDismissMessageErrorMessage = stateHolder::dismissMessageErrorMessage,
        onDismissMessageSearchErrorMessage = stateHolder::dismissMessageSearchErrorMessage,
        onDismissChatUserSearchErrorMessage = stateHolder::dismissChatUserSearchErrorMessage,
        onDismissMemberErrorMessage = stateHolder::dismissMemberErrorMessage,
        onDismissRoomManagementMessage = stateHolder::dismissRoomManagementMessage,
        onDismissStreamingErrorMessage = stateHolder::dismissStreamingErrorMessage,
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
        onCopyAiResult = onCopyAiResult,
        onAddAiMutedWord = onAddAiMutedWord,
        onCreateAutomationFromAiResult = onCreateAutomationFromAiResult,
        onDismissAiResult = onDismissAiResult,
        onBackHandlerChanged = onBackHandlerChanged,
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
    onCopyAiResult: (String) -> Unit,
    onAddAiMutedWord: (String) -> Unit,
    onAddAiRelatedNoteToWatchLater: (String, List<Note>) -> Unit,
    onOpenAiRelatedNote: (String, List<Note>) -> Unit,
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
        onCopyAiResult = onCopyAiResult,
        onAddAiMutedWord = onAddAiMutedWord,
        onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
        onOpenAiRelatedNote = onOpenAiRelatedNote,
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
    onCopyAiResult: (String) -> Unit,
    onAddAiMutedWord: (String) -> Unit,
    onAddAiRelatedNoteToWatchLater: (String, List<Note>) -> Unit,
    onOpenAiRelatedNote: (String, List<Note>) -> Unit,
    onDismissAiResult: () -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onChangeBanner: () -> Unit,
    onChangeAvatar: () -> Unit,
    onSelectPresetAvatar: (String) -> Unit,
    onTakePhoto: (() -> Unit)? = null,
    pendingAvatarUpload: DriveFileUpload? = null,
    onConfirmAvatar: () -> Unit = {},
    onCancelAvatar: () -> Unit = {},
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
        onCopyAiResult = onCopyAiResult,
        onAddAiMutedWord = onAddAiMutedWord,
        onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
        onOpenAiRelatedNote = onOpenAiRelatedNote,
        onDismissAiResult = onDismissAiResult,
        selectedTheme = selectedTheme,
        selectedTimelineDensity = selectedTimelineDensity,
        onUpdateProfile = onUpdateProfile,
        onChangeBanner = onChangeBanner,
        onChangeAvatar = onChangeAvatar,
        onSelectPresetAvatar = onSelectPresetAvatar,
        onTakePhoto = onTakePhoto,
        pendingAvatarUpload = pendingAvatarUpload,
        onConfirmAvatar = onConfirmAvatar,
        onCancelAvatar = onCancelAvatar,
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
    onCopyAiResult: (String) -> Unit,
    onAddAiMutedWord: (String) -> Unit,
    onAddAiRelatedNoteToWatchLater: (String, List<Note>) -> Unit,
    onOpenAiRelatedNote: (String, List<Note>) -> Unit,
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
        onCopyAiResult = onCopyAiResult,
        onAddAiMutedWord = onAddAiMutedWord,
        onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
        onOpenAiRelatedNote = onOpenAiRelatedNote,
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
    recentChatMessages: List<ChatMessage>,
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
    onUpdateBurstLimit: (String, Int) -> Unit,
    onAddCondition: (String, AutomationConditionType) -> Unit,
    onUpdateCondition: (String, AutomationCondition) -> Unit,
    onRemoveCondition: (String, String) -> Unit,
    onAddAction: (String, AutomationActionType) -> Unit,
    onUpdateAction: (String, AutomationAction) -> Unit,
    onRemoveAction: (String, String) -> Unit,
    onClearLogs: () -> Unit,
    onClearDebugRecords: () -> Unit,
    onSimulateChatMessage: (ChatMessage) -> Unit,
    onOpenLogs: () -> Unit,
    onApproveRuleDraft: () -> Unit,
    onRejectRuleDraft: () -> Unit,
    latestAiResultFor: (Array<out AiTaskKind>) -> AiTask?,
    onAiExplainRule: (AutomationRule?) -> Unit,
    onAiSuggestRules: (AutomationUiState) -> Unit,
    onAiCreateRule: (String, AutomationUiState) -> Unit,
    onCopyAiResult: (String) -> Unit,
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
        onUpdateBurstLimit = onUpdateBurstLimit,
        onAddCondition = onAddCondition,
        onUpdateCondition = onUpdateCondition,
        onRemoveCondition = onRemoveCondition,
        onAddAction = onAddAction,
        onUpdateAction = onUpdateAction,
        onRemoveAction = onRemoveAction,
        onClearLogs = onClearLogs,
        onClearDebugRecords = onClearDebugRecords,
        recentChatMessages = recentChatMessages,
        onSimulateChatMessage = onSimulateChatMessage,
        onOpenLogs = onOpenLogs,
        onApproveRuleDraft = onApproveRuleDraft,
        onRejectRuleDraft = onRejectRuleDraft,
        aiEnabled = aiState.hasUsableModel,
        isAiProcessing = aiState.isProcessing,
        aiResultText = aiResult?.resultText,
        aiResultLabel = aiResult?.kind?.label,
        onAiExplainRule = onAiExplainRule,
        onAiSuggestRules = onAiSuggestRules,
        onAiCreateRule = onAiCreateRule,
        onCopyAiResult = onCopyAiResult,
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
    onCopyAiResult: (String) -> Unit,
    onAddAiRelatedNoteToWatchLater: (Note) -> Unit,
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
        onCopyAiResult = onCopyAiResult,
        onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
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
private fun AiAssistantRouteContent(
    state: AiAssistantRouteState,
    actions: AiAssistantRouteActions,
) {
    AiAssistantScreen(
        messages = state.messages,
        draft = state.draft,
        contextSummary = state.contextSummary,
        aiEnabled = state.aiState.hasUsableModel,
        isProcessing = state.isProcessing,
        errorMessage = state.aiState.errorMessage,
        attachments = state.attachments,
        isUploadingAttachment = state.isUploadingAttachment,
        isMediaPickerAvailable = state.mediaPicker != null,
        customEmojis = state.customEmojis,
        recentEmojiCodes = state.recentEmojiCodes,
        memoryNotes = state.aiState.settings.assistantMemoryNotes,
        autoApprovalSettings = state.autoApprovalSettings,
        onDraftChanged = actions.onDraftChanged,
        onSend = actions.onSend,
        onSendPrompt = actions.onSendPrompt,
        onRetry = actions.onRetry,
        onNewConversation = actions.onNewConversation,
        onAddImage = {
            state.mediaPicker?.pickImages(
                onPicked = actions.onUploadAttachment,
                onError = actions.onUploadAttachmentError,
            )
        },
        onAddFile = {
            state.mediaPicker?.pickFiles(
                onPicked = actions.onUploadAttachment,
                onError = actions.onUploadAttachmentError,
            )
        },
        onOpenDrivePicker = actions.onOpenDrivePicker,
        onRemoveAttachment = actions.onRemoveAttachment,
        onOpenAttachmentUrl = actions.onOpenAttachmentUrl,
        onOpenAutomation = actions.onOpenAutomation,
        onAutoApprovalSettingsChanged = actions.onAutoApprovalSettingsChanged,
        onApproveAction = actions.onApproveAction,
        onRejectAction = actions.onRejectAction,
        onBack = actions.onBack,
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
    onCopyAiResult: (String) -> Unit,
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
        onCopyAiResult = onCopyAiResult,
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
    onChatNoiseReductionSettingsChanged: (ChatNoiseReductionSettings) -> Unit,
    onCheckForUpdates: (((String) -> Unit) -> Unit),
    appVersionName: String,
    onOpenReleaseNotes: () -> Unit,
    onClearChatMessageCache: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenThemeCustomization: () -> Unit,
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit),
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenWebSettings: (SettingsItemKey) -> Unit,
    onOpenManagement: (cc.hhhl.client.model.SettingsManagementSectionKey) -> Unit,
    onOpenAiSettings: () -> Unit,
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
        onChatNoiseSettingsChanged = onChatNoiseReductionSettingsChanged,
        onChatNoiseKeywordDraftChanged = stateHolder::updateChatNoiseKeywordDraft,
        onAddChatNoiseKeyword = stateHolder::addChatNoiseKeyword,
        onRemoveChatNoiseKeyword = stateHolder::removeChatNoiseKeyword,
        onChatNoiseUserDraftChanged = stateHolder::updateChatNoiseUserDraft,
        onAddChatNoiseUser = stateHolder::addChatNoiseUser,
        onRemoveChatNoiseUser = stateHolder::removeChatNoiseUser,
        onCheckForUpdates = onCheckForUpdates,
        appVersionName = appVersionName,
        onOpenReleaseNotes = onOpenReleaseNotes,
        onClearChatMessageCache = onClearChatMessageCache,
        onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
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
        onOpenAiSettings = onOpenAiSettings,
        onAiSettingsChanged = onAiSettingsChanged,
        onAiProviderSelected = onAiProviderSelected,
        onTestAiConnection = onTestAiConnection,
        onAiWorkspacePlan = onAiWorkspacePlan,
        aiConnectionMessage = aiConnectionMessage,
        isTestingAiConnection = isTestingAiConnection,
    )
}

@Composable
private fun MainShellSettingsRoute(
    settingsState: SettingsUiState,
    settingsStateHolder: SettingsStateHolder,
    instanceMetaState: InstanceMetaUiState,
    accounts: List<AccountSession>,
    currentAccountId: String?,
    appScope: CoroutineScope,
    mediaPicker: MediaPicker?,
    chatMessageCache: ChatMessageCache,
    chatUnreadStore: ChatUnreadStore,
    openUrl: (String) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    customTheme: HhhlCustomTheme,
    onCustomThemeChanged: (HhhlCustomTheme) -> Unit,
    onResetCustomTheme: () -> Unit,
    onSetGlobalBackgroundImage: (cc.hhhl.client.api.DriveFileUpload) -> Unit,
    onClearGlobalBackgroundImage: () -> Unit,
    onSetChatBackgroundImage: (cc.hhhl.client.api.DriveFileUpload) -> Unit,
    onClearChatBackgroundImage: () -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onListGesturesEnabledChanged: (Boolean) -> Unit,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
    onBackgroundNotificationsChanged: (Boolean) -> Unit,
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit,
    onChatNoiseReductionSettingsChanged: (ChatNoiseReductionSettings) -> Unit,
    onCheckForUpdates: (((String) -> Unit) -> Unit),
    appVersionName: String,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit),
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    aiState: AiUiState,
    aiStateHolder: AiStateHolder,
    onAiWorkspacePlan: () -> Unit,
) {
    SettingsRouteContent(
        state = settingsState,
        stateHolder = settingsStateHolder,
        instanceMetaState = instanceMetaState,
        accounts = accounts,
        currentAccountId = currentAccountId,
        onBack = { onNavigate(AppRoute.Profile) },
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
        onChatNoiseReductionSettingsChanged = onChatNoiseReductionSettingsChanged,
        onCheckForUpdates = onCheckForUpdates,
        appVersionName = appVersionName,
        onOpenReleaseNotes = { onNavigate(AppRoute.ReleaseNotes) },
        onClearChatMessageCache = {
            currentAccountId?.let { accountId ->
                appScope.launch {
                    chatMessageCache.clearAccount(accountId)
                }
                chatUnreadStore.clearAccount(accountId)
            }
        },
        onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
        onOpenThemeCustomization = { onNavigate(AppRoute.ThemeCustomization) },
        onBackHandlerChanged = onBackHandlerChanged,
        onSwitchAccount = onSwitchAccount,
        onRemoveAccount = onRemoveAccount,
        onAddAccount = onAddAccount,
        onOpenAdminDashboard = { onNavigate(AppRoute.AdminDashboard) },
        onOpenWebSettings = { key ->
            settingsWebManagementPath(key)?.let { path ->
                openUrl("${SharkeyAuthApi.DEFAULT_BASE_URL}$path")
            }
        },
        onOpenManagement = { key ->
            settingsStateHolder.openManagement(key)
            onNavigate(AppRoute.SettingsManagement(key))
        },
        onOpenAiSettings = { onNavigate(AppRoute.AiSettings) },
        onAiSettingsChanged = aiStateHolder::updateSettings,
        onAiProviderSelected = aiStateHolder::applyProviderPreset,
        onTestAiConnection = aiStateHolder::testConnection,
        onAiWorkspacePlan = onAiWorkspacePlan,
        aiConnectionMessage = aiState.message ?: aiState.errorMessage,
        isTestingAiConnection = aiState.isTestingConnection,
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
    onCopyAiResult: (String) -> Unit,
    onAddAiMutedWord: (String) -> Unit,
    onAddAiRelatedNoteToWatchLater: (String, List<NotificationItem>) -> Unit,
    onOpenAiRelatedNote: (String, List<NotificationItem>) -> Unit,
    onDismissAiResult: () -> Unit,
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit),
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
        onCopyAiResult = onCopyAiResult,
        onAddAiMutedWord = onAddAiMutedWord,
        onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
        onOpenAiRelatedNote = onOpenAiRelatedNote,
        onDismissAiResult = onDismissAiResult,
        notificationListState = notificationListState,
        announcementListState = announcementListState,
        onBackHandlerChanged = onBackHandlerChanged,
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
    onOpenChannel: (Channel) -> Unit,
) {
    DiscoverScreen(
        state = state,
        onQueryChanged = stateHolder::updateQuery,
        onSearch = stateHolder::search,
        onModeSelected = stateHolder::selectMode,
        onFiltersChanged = stateHolder::updateFilters,
        onClearFilters = stateHolder::clearFilters,
        onLoadMore = stateHolder::loadMore,
        onLoadMoreRecommended = stateHolder::loadMoreRecommendedTimeline,
        onRecommendedScopeSelected = stateHolder::updateRecommendedScope,
        onRecommendedCategorySelected = stateHolder::updateRecommendedCategory,
        onToggleRecommendedWithFiles = stateHolder::toggleRecommendedWithFiles,
        onRecommendationFeedback = { noteId, event ->
            stateHolder.sendRecommendationFeedback(noteId, event)
        },
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
        onOpenChannel = onOpenChannel,
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
        onRemoveFavoriteMessage = stateHolder::removeFavoriteMessage,
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
        onRefreshChannels = stateHolder::refreshCurrentChannels,
        onRefreshTimeline = stateHolder::refreshTimeline,
        onKindSelected = stateHolder::selectKind,
        onCategorySelected = stateHolder::selectCategory,
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
    speechTextInput: SpeechTextInput?,
    timelineCache: TimelineCache?,
    recentReactionStore: RecentReactionStore,
    specialCareStore: SpecialCareStore,
    automationStore: AutomationStore,
    aiStore: AiStore,
    composeDraftStore: ComposeDraftStore,
    favoriteMessageStore: FavoriteMessageStore,
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
    chatNoiseReductionSettings: ChatNoiseReductionSettings,
    onBackgroundNotificationsChanged: (Boolean) -> Unit,
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit,
    onChatNoiseReductionSettingsChanged: (ChatNoiseReductionSettings) -> Unit,
    onSpecialCareUsersChanged: (Set<String>) -> Unit,
    onSpecialCareSystemNotification: (NotificationItem) -> Unit,
    onAutomationSystemNotification: ((String, String) -> Boolean?)?,
    onAiQueueChanged: () -> Unit,
    initialSharedText: String?,
    onInitialSharedTextConsumed: () -> Unit,
    appVersionName: String,
    releaseNotesStore: ReleaseNotesStore,
    onCheckForUpdates: (((String) -> Unit) -> Unit),
    onOpenBatteryOptimizationSettings: () -> Unit,
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
    var aiAssistantSourceRootRoute by remember(currentAccountId) { mutableStateOf<RootRoute?>(null) }
    var aiAssistantSourceRoute by remember(currentAccountId) { mutableStateOf<AppRoute?>(null) }
    var aiAssistantSourceRoom by remember(currentAccountId) { mutableStateOf<ChatRoom?>(null) }
    var aiAssistantSourceConversation by remember(currentAccountId) { mutableStateOf<ChatUserConversation?>(null) }
    var aiAssistantSourceNote by remember(currentAccountId) { mutableStateOf<Note?>(null) }
    var timelineTrendSelected by remember { mutableStateOf(false) }
    var drivePickerTarget by remember { mutableStateOf<DrivePickerTarget?>(null) }
    var userListQuickTarget by remember { mutableStateOf<UserListQuickTarget?>(null) }
    var mediaPreviewSession by remember { mutableStateOf<MediaPreviewSession?>(null) }
    var nestedBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var aiAssistantMessages by remember(currentAccountId) { mutableStateOf<List<AiAssistantMessage>>(emptyList()) }
    var aiAssistantDraft by remember(currentAccountId) { mutableStateOf("") }
    var aiAssistantAttachments by remember(currentAccountId) { mutableStateOf<List<DriveFile>>(emptyList()) }
    var aiAssistantAttachmentUploading by remember(currentAccountId) { mutableStateOf(false) }
    var aiAssistantPendingPrompt by remember(currentAccountId) { mutableStateOf<String?>(null) }
    var aiAssistantConversationGeneration by remember(currentAccountId) { mutableStateOf(0L) }
    var pendingAiAssistantComposeDraft by remember(currentAccountId) { mutableStateOf<String?>(null) }
    var viewedUserId by remember { mutableStateOf<String?>(null) }
    var authInvalidDialogOpen by remember { mutableStateOf(false) }
    var pendingReleaseNotes by remember { mutableStateOf<AppReleaseNotes?>(null) }
    var lastStreamingTimelineRefreshAt by remember { mutableStateOf<Map<TimelineKind, Long>>(emptyMap()) }
    var notifiedSpecialCareNoteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var automationTimelineSourceBaselines by remember(currentAccountId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var automationSeenTimelineNoteIds by remember(currentAccountId) { mutableStateOf<Set<String>>(emptySet()) }
    var automationSeenChatMessageIds by remember(currentAccountId) { mutableStateOf<Set<String>>(emptySet()) }
    var automationSeenNotificationIds by remember(currentAccountId) { mutableStateOf<Set<String>>(emptySet()) }
    var automationChatSourceBaselines by remember(currentAccountId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var automationRoomSourceMarkers by remember(currentAccountId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var automationAiGeneratedMessageKeys by remember(currentAccountId) { mutableStateOf<Set<String>>(emptySet()) }
    var automationAiGeneratedNoteIds by remember(currentAccountId) { mutableStateOf<Set<String>>(emptySet()) }
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
    val composeRepository = remember {
        ComposeRepository(tokenProvider = { sessionToken })
    }
    val composeStateHolder = remember {
        ComposeStateHolder(
            repository = composeRepository,
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
    val chatStreamingRepository = remember(sessionToken) {
        ChatStreamingRepository(tokenProvider = { sessionToken })
    }
    val chatStateHolder = remember {
        ChatStateHolder(
            repository = chatRepository,
            driveFileRepository = DriveFileRepository(tokenProvider = { sessionToken }),
            streamingRepository = chatStreamingRepository,
            relationshipRepository = UserRelationshipRepository(tokenProvider = { sessionToken }),
            discoverRepository = DiscoverRepository(tokenProvider = { sessionToken }),
            accountIdProvider = { currentAccountId },
            unreadStore = chatUnreadStore,
            currentUserProvider = { accountUser?.toDomainUser(host = currentAccountHost) },
            scope = appScope,
            workerDispatcher = Dispatchers.Default,
        )
    }
    val chatState by chatStateHolder.state.collectAsState()
    var mainStreamingConnected by remember(sessionToken) { mutableStateOf(false) }
    LaunchedEffect(route, rootRoute) {
        if (route != AppRoute.AiAssistant) {
            aiAssistantSourceRootRoute = rootRoute
            aiAssistantSourceRoute = route
        }
    }
    LaunchedEffect(route) {
        if (route != AppRoute.Chat && route != AppRoute.Notifications) {
            nestedBackHandler = null
        }
    }
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
            favoriteMessageStore = favoriteMessageStore,
            accountIdProvider = { currentAccountId },
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
    val aiStateHolder = remember(currentAccountId, aiStore, sessionToken, currentAccountHost) {
        AiStateHolder(
            store = aiStore,
            accountId = currentAccountId,
            repository = AiRepository(
                remoteTokenProvider = { sessionToken },
                remoteBaseUrlProvider = { "https://${currentAccountHost.orEmpty().ifBlank { "dc.hhhl.cc" }}" },
            ),
            onQueueChanged = onAiQueueChanged,
            scope = appScope,
        )
    }
    val aiState by aiStateHolder.state.collectAsState()
    var aiAssistantAutoApprovalSettings by remember(currentAccountId) {
        mutableStateOf(AiAssistantAutoApprovalSettings())
    }
    LaunchedEffect(
        currentAccountId,
        aiState.settings.assistantLowRiskAutoApproval,
        aiState.settings.assistantHighRiskAutoApproval,
    ) {
        aiAssistantAutoApprovalSettings = AiAssistantAutoApprovalSettings(
            lowRiskEnabled = aiState.settings.assistantLowRiskAutoApproval,
            highRiskEnabled = aiState.settings.assistantHighRiskAutoApproval,
        )
    }
    fun updateAiAssistantAutoApprovalSettings(settings: AiAssistantAutoApprovalSettings) {
        aiAssistantAutoApprovalSettings = settings
        aiStateHolder.updateSettings(
            aiState.settings.copy(
                assistantLowRiskAutoApproval = settings.lowRiskEnabled,
                assistantHighRiskAutoApproval = settings.highRiskEnabled,
            ),
        )
    }
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
                driveFileRepository = DriveFileRepository(tokenProvider = { sessionToken }),
                clipboardWriter = { text ->
                    clipboardManager.setText(AnnotatedString(text))
                    true
                },
                systemNotificationPublisher = automationSystemNotificationPublisher,
                aiBridge = aiStateHolder,
                aiGeneratedChatMessageReporter = { message ->
                    val messageKey = message.automationMessageKey()
                    if (messageKey.isNotBlank()) {
                        automationAiGeneratedMessageKeys = (automationAiGeneratedMessageKeys + messageKey)
                            .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
                    }
                },
                aiGeneratedNoteReporter = { noteId ->
                    if (noteId.isNotBlank()) {
                        automationAiGeneratedNoteIds = (automationAiGeneratedNoteIds + noteId)
                            .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
                    }
                },
                attachmentAuthHeaderProvider = {
                    sessionToken.orEmpty().takeIf { it.isNotBlank() }
                        ?.let { token -> mapOf("Authorization" to "Bearer $token") }
                        .orEmpty()
                },
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
    val mainStreamingOptions = remember(
        automationState.rules.map { rule -> rule.id to rule.enabled to rule.trigger to rule.conditions.map { it.type to it.value to it.enabled } },
    ) {
        automationState.rules.toMainStreamingOptions(AUTOMATION_CHANNEL_SCAN_LIMIT)
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
            imageProcessor = createImageProcessor(),
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
    fun findLoadedNote(noteId: String): Note? {
        return loadedNotes.firstOrNull { it.id == noteId }
    }
    var noteActionToast by remember { mutableStateOf<String?>(null) }
    var pendingAiAssistantRoomManagementAction by remember { mutableStateOf<PendingAiAssistantRoomManagementAction?>(null) }
    var pendingAiAssistantChatSendAction by remember { mutableStateOf<PendingAiAssistantObservedAction?>(null) }
    var pendingAiAssistantComposeSendAction by remember { mutableStateOf<PendingAiAssistantObservedAction?>(null) }
    var pendingAiAssistantAsyncActionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var accountBootstrapRestored by remember(currentAccountId) { mutableStateOf(false) }

    LaunchedEffect(currentAccountId) {
        accountBootstrapRestored = false
        instanceMetaStateHolder.load()
        specialCareStateHolder.restoreStoredSpecialCare()
        composeStateHolder.restoreStoredDraft()
        composeStateHolder.restoreFailedSendQueue()
        aiStateHolder.restore()
        noteActionStateHolder.restoreRecentReactions()
        noteActionStateHolder.loadReactionOptions()
        automationStateHolder.restore()
        accountBootstrapRestored = true
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

    LaunchedEffect(specialCareState.userIds, specialCareState.isRestored) {
        if (!specialCareState.isRestored) return@LaunchedEffect
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
        chatNoiseReductionSettings,
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
            chatNoiseReductionSettings = chatNoiseReductionSettings,
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
        chatStateHolder.refreshSpecialCareMessagesQuietly()
        while (true) {
            delay(CHAT_ROOM_REFRESH_INTERVAL_MS)
            chatStateHolder.refreshRoomsQuietly(markSelectedRoomRead = markSelectedRoomRead)
            chatStateHolder.refreshUserConversationsQuietly(
                markSelectedUserRead = route == AppRoute.Chat && chatState.selectedUserConversation != null,
            )
            chatStateHolder.refreshSpecialCareMessagesQuietly()
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
            discoverStateHolder.refreshHomeQuietly()
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

    fun handleRealtimeNotification(notification: NotificationItem) {
        if (notification.id.isBlank() || notification.isRead) return
        val item = notification.copy(
            isSpecialCare = notification.isSpecialCare || notification.actor.id in specialCareState.userIds,
        )
        notificationStateHolder.addStreamingNotification(item)
        val eventKey = item.id
        if (eventKey !in automationSeenNotificationIds) {
            automationSeenNotificationIds = (automationSeenNotificationIds + eventKey)
                .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
            automationStateHolder.emit(item.toAutomationNotificationEvent())
        }
        if (item.isSpecialCare) {
            onSpecialCareSystemNotification(item)
        }
    }

    suspend fun refreshUnreadNotificationsForAutomation() {
        when (val result = notificationRepository.refresh(NotificationFilter.All)) {
            is cc.hhhl.client.repository.NotificationRepositoryResult.Success -> {
                result.notifications
                    .filterNot { it.isRead }
                    .forEach(::handleRealtimeNotification)
            }
            cc.hhhl.client.repository.NotificationRepositoryResult.ActionSuccess,
            cc.hhhl.client.repository.NotificationRepositoryResult.AllRead,
            cc.hhhl.client.repository.NotificationRepositoryResult.Unauthorized,
            is cc.hhhl.client.repository.NotificationRepositoryResult.Error,
            -> Unit
        }
    }

    suspend fun refreshNotificationsAfterStreamingEvent() {
        notificationStateHolder.refreshQuietly()
        refreshUnreadNotificationsForAutomation()
        delay(1_500L)
        notificationStateHolder.refreshQuietly()
        refreshUnreadNotificationsForAutomation()
        delay(4_000L)
        notificationStateHolder.refreshQuietly()
        refreshUnreadNotificationsForAutomation()
    }

    fun emitChatAttentionNotificationIfNew(
        message: ChatMessage,
        kind: ChatAttentionKind,
        roomName: String = "",
        directUserId: String? = null,
    ) {
        val notification = message.toChatAttentionNotification(
            kind = kind,
            roomName = roomName,
            directUserId = directUserId,
            fallbackEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        if (notification.id in automationSeenNotificationIds) return
        automationSeenNotificationIds = (automationSeenNotificationIds + notification.id)
            .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
        if (kind == ChatAttentionKind.SpecialCare) {
            notificationStateHolder.addSpecialCareNotification(notification)
        } else {
            notificationStateHolder.addChatAttentionNotification(notification)
        }
        automationStateHolder.emit(notification.toAutomationNotificationEvent())
        onSpecialCareSystemNotification(notification)
    }

    fun emitChatMessageAutomationIfNew(
        sourceId: String,
        message: ChatMessage,
        roomId: String,
        roomName: String = "",
        directUserId: String? = null,
        attentionKind: ChatAttentionKind?,
        currentUser: User?,
    ) {
        val messageId = message.automationMessageKey()
        if (sourceId.isBlank() || messageId.isBlank()) return
        val eventKey = "$sourceId:$messageId"
        if (eventKey !in automationSeenChatMessageIds) {
            automationSeenChatMessageIds = (automationSeenChatMessageIds + eventKey)
                .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
            automationStateHolder.emit(
                message.toAutomationChatEvent(
                    roomId = roomId,
                    roomName = roomName,
                    directUserId = directUserId,
                    attentionKind = "",
                    currentUser = currentUser,
                    isAiGenerated = messageId in automationAiGeneratedMessageKeys,
                ),
            )
        }
        attentionKind?.let { kind ->
            val attentionEventKey = "$eventKey:attention:${kind.name}"
            if (attentionEventKey !in automationSeenChatMessageIds) {
                automationSeenChatMessageIds = (automationSeenChatMessageIds + attentionEventKey)
                    .takeLastSet(MAX_AUTOMATION_SEEN_CHAT_EVENTS)
                automationStateHolder.emit(
                    message.toAutomationChatEvent(
                        roomId = roomId,
                        roomName = roomName,
                        directUserId = directUserId,
                        attentionKind = kind.name,
                        currentUser = currentUser,
                        isAiGenerated = messageId in automationAiGeneratedMessageKeys,
                    ),
                )
                emitChatAttentionNotificationIfNew(
                    message = message,
                    kind = kind,
                    roomName = roomName,
                    directUserId = directUserId,
                )
            }
        }
    }

    fun realtimeChatRoomName(roomId: String): String {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isBlank()) return ""
        return listOfNotNull(chatState.selectedRoom)
            .plus(chatState.rooms)
            .plus(chatState.ownedRooms)
            .firstOrNull { room -> room.id == cleanRoomId }
            ?.name
            .orEmpty()
    }

    fun emitResolvedRealtimeStreamChatMessage(
        message: ChatMessage,
        directUserId: String?,
        currentUser: User?,
        roomNameOverride: String = "",
    ) {
        val sourceId = directUserId?.let { userId -> "user:$userId" }
            ?: message.roomId.takeIf { it.isNotBlank() }?.let { roomId -> "room:$roomId" }
            ?: return
        appScope.launch {
            if (directUserId == null) {
                val roomId = message.roomId.takeIf { it.isNotBlank() } ?: return@launch
                chatRepository.cacheRoomMessage(roomId, message)
            } else {
                chatRepository.cacheUserMessage(directUserId, message)
            }
        }
        val roomName = if (directUserId == null) {
            roomNameOverride.ifBlank { realtimeChatRoomName(message.roomId) }
        } else {
            ""
        }
        chatStateHolder.recordRealtimeMessage(
            message = message,
            directUserId = directUserId,
        )
        val attentionKind = message.automationChatAttentionKind(
            currentUser = currentUser,
            specialCareUserIds = specialCareState.userIds,
        )
        emitChatMessageAutomationIfNew(
            sourceId = sourceId,
            message = message,
            roomId = message.roomId,
            roomName = roomName,
            directUserId = directUserId,
            attentionKind = attentionKind,
            currentUser = currentUser,
        )
        automationChatSourceBaselines = (automationChatSourceBaselines + (sourceId to message.automationMessageKey()))
            .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
        if (directUserId == null) {
            automationRoomSourceMarkers = (automationRoomSourceMarkers + (sourceId to message.unreadMarker()))
                .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
        }
    }

    fun emitRealtimeStreamChatMessage(
        message: ChatMessage,
        sourceUserId: String? = null,
        sourceRoomName: String = "",
    ) {
        val currentUser = accountUser?.toDomainUser(host = currentAccountHost)
        val directUserId = sourceUserId?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.directPeerId(currentUser?.id.orEmpty()).takeIf { message.roomId.isBlank() }
        if (message.requiresRealtimeAttentionResolution()) {
            appScope.launch {
                emitResolvedRealtimeStreamChatMessage(
                    message = chatRepository.resolveRealtimeMessage(message, directUserId),
                    directUserId = directUserId,
                    currentUser = currentUser,
                    roomNameOverride = sourceRoomName,
                )
            }
            return
        }
        emitResolvedRealtimeStreamChatMessage(
            message = message,
            directUserId = directUserId,
            currentUser = currentUser,
            roomNameOverride = sourceRoomName,
        )
    }

    suspend fun loadAllRoomsForRealtimeMonitoring(): ChatRepositoryResult {
        val initial = when (val result = chatRepository.refresh()) {
            is ChatRepositoryResult.Success -> result
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error,
                -> return result
        }
        var rooms = initial.rooms
        var endReached = initial.endReached
        while (!endReached && rooms.size < REALTIME_CHAT_STREAM_ROOM_LIMIT) {
            val next = when (val result = chatRepository.loadMore(rooms)) {
                is ChatRepositoryResult.Success -> result
                ChatRepositoryResult.Unauthorized,
                is ChatRepositoryResult.Error,
                    -> return result
            }
            if (next.rooms.size <= rooms.size) {
                endReached = true
                continue
            }
            rooms = next.rooms
            endReached = next.endReached
        }
        val ownedRooms = when (val result = chatRepository.refreshOwnedRooms()) {
            is ChatRepositoryResult.Success -> result.rooms
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error,
                -> emptyList()
        }
        rooms = rooms.mergeRealtimeOwnedRooms(ownedRooms)
        return ChatRepositoryResult.Success(rooms = rooms, endReached = endReached)
    }

    suspend fun scanRealtimeChatForAutomation(allowLatestOnFirstScan: Boolean) = coroutineScope {
        val currentUser = accountUser?.toDomainUser(host = currentAccountHost)
        val previousRooms = chatState.rooms.associateBy { it.id }
        val previousConversations = chatState.userConversations.associateBy { it.user.id }
        val roomsDeferred = async { loadAllRoomsForRealtimeMonitoring() }
        val userConversationsDeferred = async { chatRepository.refreshUserConversations() }

        when (val result = roomsDeferred.await()) {
            is ChatRepositoryResult.Success -> {
                val roomsToCheck = result.rooms
                    .filter { room ->
                        val sourceId = "room:${room.id}"
                        val marker = room.unreadMarker()
                        val baselineMarker = automationRoomSourceMarkers[sourceId]
                        room.unreadCount > 0 ||
                            (baselineMarker != null && marker.isNotBlank() && marker != baselineMarker) ||
                            (allowLatestOnFirstScan && room.id in previousRooms.keys)
                    }
                    .let { changedRooms ->
                        (changedRooms + result.rooms.filter { it.unreadCount > 0 })
                            .distinctBy { it.id }
                            .take(AUTOMATION_ROOM_SCAN_LIMIT)
                    }
                for (room in roomsToCheck) {
                    val messages = when (val messagesResult = chatRepository.refreshMessages(room.id)) {
                        is ChatMessageRepositoryResult.Success -> messagesResult.messages
                        is ChatMessageRepositoryResult.Created,
                        is ChatMessageRepositoryResult.Deleted,
                        is ChatMessageRepositoryResult.Error,
                        ChatMessageRepositoryResult.ReactionUpdated,
                        ChatMessageRepositoryResult.Unauthorized,
                        -> emptyList()
                    }
                    val sourceId = "room:${room.id}"
                    val baselineId = automationChatSourceBaselines[sourceId]
                    val messageEvents = messages.recentAutomationMessages(
                        baselineId = baselineId,
                        unreadCount = room.unreadCount,
                        allowLatestOnFirstScan = allowLatestOnFirstScan && room.unreadCount > 0,
                    )
                    messageEvents.forEach { message ->
                        val attentionKind = message.automationChatAttentionKind(
                            currentUser = currentUser,
                            specialCareUserIds = specialCareState.userIds,
                        )
                        emitChatMessageAutomationIfNew(
                            sourceId = sourceId,
                            message = message,
                            roomId = room.id,
                            roomName = room.name,
                            attentionKind = attentionKind,
                            currentUser = currentUser,
                        )
                    }
                    messages.lastOrNull()?.automationMessageKey()?.takeIf { it.isNotBlank() }?.let { latestId ->
                        automationChatSourceBaselines = (automationChatSourceBaselines + (sourceId to latestId))
                            .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
                    }
                    room.unreadMarker().takeIf { it.isNotBlank() }?.let { marker ->
                        automationRoomSourceMarkers = (automationRoomSourceMarkers + (sourceId to marker))
                            .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
                    }
                }
            }
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error,
            -> Unit
        }

        when (val result = userConversationsDeferred.await()) {
            is ChatUserConversationRepositoryResult.Success -> {
                result.conversations
                    .filter { conversation ->
                        val sourceId = "user:${conversation.user.id}"
                        val latestId = conversation.latestMessage?.automationMessageKey().orEmpty()
                        val baselineId = automationChatSourceBaselines[sourceId]
                        conversation.unreadCount > 0 ||
                            (baselineId != null && latestId.isNotBlank() && latestId != baselineId) ||
                            (allowLatestOnFirstScan && conversation.user.id in previousConversations.keys)
                    }
                    .take(AUTOMATION_ROOM_SCAN_LIMIT)
                    .forEach { conversation ->
                        val sourceId = "user:${conversation.user.id}"
                        val baselineId = automationChatSourceBaselines[sourceId]
                        val latestMessage = conversation.latestMessage
                        val shouldRefreshUserMessages =
                            conversation.unreadCount > 1 ||
                                baselineId != null ||
                                latestMessage?.requiresRealtimeAttentionResolution() == true
                        val messages = if (shouldRefreshUserMessages) {
                            when (val messagesResult = chatRepository.refreshUserMessages(conversation.user.id)) {
                                is ChatMessageRepositoryResult.Success -> messagesResult.messages
                                is ChatMessageRepositoryResult.Created,
                                is ChatMessageRepositoryResult.Deleted,
                                is ChatMessageRepositoryResult.Error,
                                ChatMessageRepositoryResult.ReactionUpdated,
                                ChatMessageRepositoryResult.Unauthorized,
                                -> listOfNotNull(latestMessage)
                            }
                        } else {
                            listOfNotNull(latestMessage)
                        }
                        val messageEvents = messages.recentAutomationMessages(
                            baselineId = baselineId,
                            unreadCount = conversation.unreadCount,
                            allowLatestOnFirstScan = allowLatestOnFirstScan && conversation.unreadCount > 0,
                        )
                        messageEvents.forEach { message ->
                            val attentionKind = message.automationChatAttentionKind(
                                currentUser = currentUser,
                                specialCareUserIds = specialCareState.userIds,
                            )
                            emitChatMessageAutomationIfNew(
                                sourceId = sourceId,
                                message = message,
                                roomId = message.roomId,
                                directUserId = conversation.user.id,
                                attentionKind = attentionKind,
                                currentUser = currentUser,
                            )
                        }
                        messages.lastOrNull()?.automationMessageKey()?.takeIf { it.isNotBlank() }?.let { latestId ->
                            automationChatSourceBaselines = (automationChatSourceBaselines + (sourceId to latestId))
                                .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
                        }
                    }
            }
            ChatUserConversationRepositoryResult.Unauthorized,
            is ChatUserConversationRepositoryResult.Error,
            -> Unit
        }
    }

    suspend fun refreshChatAfterRealtimeSignal() {
        if (!chatState.chatAvailable) return
        scanRealtimeChatForAutomation(allowLatestOnFirstScan = true)
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
            scanRealtimeChatForAutomation(allowLatestOnFirstScan = true)
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

    LaunchedEffect(currentAccountId, sessionToken, chatState.chatAvailable, accountBootstrapRestored) {
        if (!accountBootstrapRestored || !chatState.chatAvailable || sessionToken.isNullOrBlank()) return@LaunchedEffect
        chatStateHolder.refresh()
        chatStateHolder.refreshSpecialCareMessagesQuietly()
        scanRealtimeChatForAutomation(allowLatestOnFirstScan = true)
    }

    LaunchedEffect(
        sessionToken,
        chatState.chatAvailable,
        chatState.selectedRoom?.id,
        chatState.selectedUserConversation?.user?.id,
        specialCareState.userIds,
        automationState.rules.map { rule -> rule.id to rule.enabled to rule.trigger to rule.conditions.map { condition -> condition.type to condition.value to condition.enabled } },
    ) {
        if (!chatState.chatAvailable || sessionToken.isNullOrBlank()) return@LaunchedEffect

        while (true) {
            val rooms = when (val result = loadAllRoomsForRealtimeMonitoring()) {
                is ChatRepositoryResult.Success -> result.rooms
                ChatRepositoryResult.Unauthorized,
                is ChatRepositoryResult.Error,
                    -> chatState.rooms
            }
            val conversations = when (val result = chatRepository.refreshUserConversations()) {
                is ChatUserConversationRepositoryResult.Success -> result.conversations
                ChatUserConversationRepositoryResult.Unauthorized,
                is ChatUserConversationRepositoryResult.Error,
                    -> chatState.userConversations
            }
            val localChatSnapshot = currentAccountId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { accountId ->
                    val snapshot = chatUnreadStore.load(accountId)
                    val cachedTargets = chatMessageCache.readAccount(accountId).keys
                    LocalRealtimeChatTargets(
                        roomIds = snapshot.pinnedRoomIds +
                            snapshot.roomCounts.keys +
                            cachedTargets
                                .filter { key -> key.type == ChatMessageCacheConversationType.Room }
                                .map { key -> key.conversationId },
                        userIds = snapshot.pinnedUserIds +
                            snapshot.userCounts.keys +
                            cachedTargets
                                .filter { key -> key.type == ChatMessageCacheConversationType.User }
                                .map { key -> key.conversationId },
                    )
                }
                ?: LocalRealtimeChatTargets()
            val streamTargets = realtimeChatStreamTargets(
                rooms = rooms,
                conversations = conversations,
                rememberedRoomIds = localChatSnapshot.roomIds,
                rememberedUserIds = localChatSnapshot.userIds,
                specialCareUserIds = specialCareState.userIds,
                automationRules = automationState.rules,
                selectedRoomId = chatState.selectedRoom?.id,
                selectedUserId = chatState.selectedUserConversation?.user?.id,
            )
            if (streamTargets.isEmpty) {
                delay(CHAT_STREAM_RECONNECT_DELAY_MS)
                continue
            }
            var unauthorized = false
            withTimeoutOrNull(STREAMING_CHAT_REFRESH_INTERVAL_MS) {
                chatStreamingRepository.streamMessages(
                    roomIds = streamTargets.roomIds,
                    userIds = streamTargets.userIds,
                ).collect { event ->
                    when (event) {
                        is ChatStreamingEvent.MessageReceived -> {
                            val sourceRoomId = event.source.roomId?.takeIf { roomId -> roomId in streamTargets.roomIdSet }
                            val sourceUserId = event.source.userId?.takeIf { userId -> userId in streamTargets.userIdSet }
                            val fallbackUserId = if (sourceRoomId == null) {
                                streamTargets.userIds.firstOrNull { userId ->
                                    event.message.belongsToDirectChat(userId) && event.message.roomId !in streamTargets.roomIdSet
                                }
                            } else {
                                null
                            }
                            val userId = sourceUserId ?: fallbackUserId
                            val messageRoomId = event.message.roomId.takeIf { it.isNotBlank() }
                            val messagePeerId = event.message
                                .directPeerId(accountUser?.id.orEmpty())
                                .takeIf { event.message.roomId.isBlank() }
                            val roomId = sourceRoomId ?: messageRoomId
                            if (userId != null || roomId != null || messagePeerId != null) {
                                emitRealtimeStreamChatMessage(
                                    message = event.message,
                                    sourceUserId = userId ?: messagePeerId,
                                    sourceRoomName = roomId?.let { streamTargets.roomNamesById[it] }.orEmpty(),
                                )
                                chatStateHolder.refreshRoomsQuietly(
                                    markSelectedRoomRead = route == AppRoute.Chat && chatState.selectedRoom != null,
                                )
                                chatStateHolder.refreshUserConversationsQuietly(
                                    markSelectedUserRead = route == AppRoute.Chat && chatState.selectedUserConversation != null,
                                )
                            }
                        }
                        is ChatStreamingEvent.MessageDeleted -> {
                            val sourceRoomId = event.source.roomId?.takeIf { roomId -> roomId in streamTargets.roomIdSet }
                            val sourceUserId = event.source.userId?.takeIf { userId -> userId in streamTargets.userIdSet }
                            if (sourceRoomId != null || sourceUserId != null) {
                                chatStateHolder.recordRealtimeMessageDeletion(
                                    messageId = event.messageId,
                                    roomId = sourceRoomId,
                                    directUserId = sourceUserId,
                                )
                                refreshChatAfterRealtimeSignal()
                            }
                        }
                        ChatStreamingEvent.Unauthorized -> unauthorized = true
                        ChatStreamingEvent.Connecting,
                        ChatStreamingEvent.Connected,
                        ChatStreamingEvent.Closed,
                        is ChatStreamingEvent.Error,
                        -> Unit
                    }
                }
            }
            if (unauthorized) return@LaunchedEffect
            delay(CHAT_STREAM_RECONNECT_DELAY_MS)
        }
    }

    LaunchedEffect(sessionToken, chatState.chatAvailable, mainStreamingOptions) {
        mainStreamingConnected = false
        while (true) {
            var unauthorized = false
            mainStreamingRepository.streamMain(mainStreamingOptions).collect { event ->
                when (event) {
                    MainStreamingEvent.UnreadNotification -> refreshNotificationsAfterStreamingEvent()
                    is MainStreamingEvent.NotificationReceived -> {
                        handleRealtimeNotification(event.notification)
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
                                        timelineSource = event.timelineSource,
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
                    is MainStreamingEvent.ChatMessageReceived -> {
                        emitRealtimeStreamChatMessage(event.message)
                        refreshChatAfterRealtimeSignal()
                    }
                    is MainStreamingEvent.ChatMessageDeleted -> {
                        chatStateHolder.recordRealtimeMessageDeletion(
                            messageId = event.messageId,
                            roomId = event.source.roomId,
                            directUserId = event.source.userId,
                        )
                        refreshChatAfterRealtimeSignal()
                    }
                    MainStreamingEvent.NewChatMessage -> refreshChatAfterRealtimeSignal()
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
            message
        }
        val userEvents = chatState.userConversations.mapNotNull { conversation ->
            val message = conversation.latestMessage ?: return@mapNotNull null
            val messageId = message.automationMessageKey()
            if (messageId.isBlank()) return@mapNotNull null
            val sourceId = "user:${conversation.user.id}"
            if (automationChatSourceBaselines[sourceId] == null) return@mapNotNull null
            if (automationChatSourceBaselines[sourceId] == messageId) return@mapNotNull null
            if (sourceId == selectedSourceId && messageId == selectedLatestMessageId) return@mapNotNull null
            sourceId to (conversation to message)
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

        selectedMessageEvents.forEach { message ->
            val attentionKind = message.automationChatAttentionKind(
                currentUser = currentUser,
                specialCareUserIds = specialCareState.userIds,
            )
            emitChatMessageAutomationIfNew(
                sourceId = selectedSourceId ?: return@forEach,
                message = message,
                roomId = chatState.selectedRoom?.id ?: message.roomId,
                roomName = chatState.selectedRoom?.name.orEmpty(),
                directUserId = chatState.selectedUserConversation?.user?.id,
                attentionKind = attentionKind,
                currentUser = currentUser,
            )
        }
        userEvents.forEach { (sourceId, pair) ->
            val (conversation, message) = pair
            val attentionKind = message.automationChatAttentionKind(
                currentUser = currentUser,
                specialCareUserIds = specialCareState.userIds,
            )
            emitChatMessageAutomationIfNew(
                sourceId = sourceId,
                message = message,
                roomId = message.roomId,
                directUserId = conversation.user.id,
                attentionKind = attentionKind,
                currentUser = currentUser,
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
                .filter { message ->
                    val messageId = message.automationMessageKey()
                    messageId.isNotBlank()
                }
            if (roomMessageEvents.isEmpty()) {
                result.messages.lastOrNull()?.automationMessageKey()?.takeIf { it.isNotBlank() }?.let { latestId ->
                    automationChatSourceBaselines = (automationChatSourceBaselines + (target.sourceId to latestId))
                        .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
                }
                return@forEach
            }
            automationChatSourceBaselines = (automationChatSourceBaselines + (target.sourceId to roomMessageEvents.last().automationMessageKey()))
                .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
            automationRoomSourceMarkers = (automationRoomSourceMarkers + (target.sourceId to target.marker))
                .takeLastEntries(MAX_AUTOMATION_CHAT_SOURCES)
            roomMessageEvents.forEach { message ->
                val attentionKind = message.automationChatAttentionKind(
                    currentUser = currentUser,
                    specialCareUserIds = specialCareState.userIds,
                )
                emitChatMessageAutomationIfNew(
                    sourceId = target.sourceId,
                    message = message,
                    roomId = target.roomId,
                    roomName = target.roomName,
                    attentionKind = attentionKind,
                    currentUser = currentUser,
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
                        isAiGenerated = note.id in automationAiGeneratedNoteIds,
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
                                isAiGenerated = note.id in automationAiGeneratedNoteIds,
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
        if (route == AppRoute.FavoriteNotes) {
            favoriteNoteStateHolder.restoreFavoriteMessages()
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
            val editId = currentRoute.editId?.takeIf { it.isNotBlank() }
            if (editId != null) {
                if (composeState.draft.editId != editId) {
                    val editNote = findLoadedNote(editId)
                    if (editNote != null) {
                        composeStateHolder.startEditNote(editNote)
                    } else if (noteDetailState.noteId != editId || noteDetailState.note == null) {
                        noteDetailStateHolder.load(editId)
                    }
                }
            } else if (currentRoute.replyToId != null) {
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

    LaunchedEffect(route, noteDetailState.note, composeState.draft.editId) {
        val currentRoute = route as? AppRoute.Compose ?: return@LaunchedEffect
        val editId = currentRoute.editId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (composeState.draft.editId == editId) return@LaunchedEffect
        findLoadedNote(editId)?.let(composeStateHolder::startEditNote)
    }

    LaunchedEffect(route, pendingAiAssistantComposeDraft) {
        if (route is AppRoute.Compose) {
            pendingAiAssistantComposeDraft?.let { draft ->
                composeStateHolder.updateText(draft)
                pendingAiAssistantComposeDraft = null
            }
        }
    }

    LaunchedEffect(composeState.completedDraft, composeState.createdNoteId) {
        val completedDraft = composeState.completedDraft
            ?: composeState.draft.takeIf { composeState.createdNoteId != null }
        if (completedDraft != null) {
            val editId = completedDraft.editId?.takeIf { it.isNotBlank() }
            val channelId = completedDraft.channelId?.takeIf { it.isNotBlank() }
            composeStateHolder.consumeCreatedNote()
            if (editId != null) {
                noteDetailStateHolder.load(editId)
                route = AppRoute.NoteDetail(editId)
                noteActionToast = "已更新帖子"
            } else if (channelId != null) {
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
            is DiscoverRepositoryResult.DiscoverySectionsSuccess,
            is DiscoverRepositoryResult.SearchTrendsSuccess,
            is DiscoverRepositoryResult.RecommendedTimelineSuccess,
            DiscoverRepositoryResult.RecommendationFeedbackSuccess,
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
        val (selectedMessages, rangeNote) = chatAiMessageSelection(kind, chat)
        if (selectedMessages.isEmpty()) {
            if (kind == AiTaskKind.ChatUnreadSummary) {
                noteActionToast = "当前会话没有未读消息"
                return
            }
            noteActionToast = "当前聊天还没有可分析的消息"
            return
        }
        requestAiTask(
            kind = kind,
            input = AiTaskInput(
                text = chat.messageDraft,
                chatTitle = title,
                prompt = rangeNote,
                chatMessages = selectedMessages.map { message ->
                    message.copy(text = chatMessageBodyText(message))
                }.toAiChatMessageContexts(),
            ),
            toast = "AI 已开始处理聊天",
        )
    }
    fun aiAssistantEffectiveRoute(): AppRoute {
        return if (route == AppRoute.AiAssistant) {
            aiAssistantSourceRoute ?: AppRoute.Chat
        } else {
            route
        }
    }
    fun aiAssistantEffectiveRootRoute(targetRoute: AppRoute = aiAssistantEffectiveRoute()): RootRoute {
        return if (route == AppRoute.AiAssistant && aiAssistantSourceRoute == targetRoute) {
            aiAssistantSourceRootRoute ?: rootRouteFor(targetRoute)
        } else {
            rootRouteFor(targetRoute)
        }
    }
    fun aiAssistantCurrentRoom(): ChatRoom? {
        val effectiveRoute = aiAssistantEffectiveRoute()
        val sourceRoomId = aiAssistantSourceRoom?.id?.trim().orEmpty()
        val selectedRoom = chatState.selectedRoom?.takeIf { effectiveRoute == AppRoute.Chat }
        if (selectedRoom != null) return selectedRoom
        if (sourceRoomId.isBlank()) return null
        return (listOfNotNull(aiAssistantSourceRoom) + chatState.rooms + chatState.ownedRooms)
            .firstOrNull { room -> room.id == sourceRoomId }
    }
    fun aiAssistantCurrentUserConversation(): ChatUserConversation? {
        val effectiveRoute = aiAssistantEffectiveRoute()
        val sourceUserId = aiAssistantSourceConversation?.user?.id?.trim().orEmpty()
        val selectedConversation = chatState.selectedUserConversation?.takeIf { effectiveRoute == AppRoute.Chat }
        if (selectedConversation != null) return selectedConversation
        if (sourceUserId.isBlank()) return null
        return (listOfNotNull(aiAssistantSourceConversation) + chatState.userConversations)
            .firstOrNull { conversation -> conversation.user.id == sourceUserId }
    }
    fun ensureAiAssistantCurrentChatSelected(actionName: String): Boolean {
        if (!chatState.chatAvailable) {
            rootRoute = RootRoute.Chat
            route = AppRoute.Chat
            noteActionToast = "实例未启用聊天"
            return false
        }
        aiAssistantCurrentRoom()?.let { room ->
            if (chatState.selectedRoom?.id != room.id) {
                chatStateHolder.selectRoom(room)
            }
            return true
        }
        aiAssistantCurrentUserConversation()?.let { conversation ->
            if (chatState.selectedUserConversation?.user?.id != conversation.user.id) {
                chatStateHolder.selectUserConversation(conversation)
            }
            return true
        }
        rootRoute = RootRoute.Chat
        route = AppRoute.Chat
        noteActionToast = "请先打开要${actionName}的聊天会话"
        return false
    }
    fun aiAssistantCurrentNote(): Note? {
        val effectiveRoute = aiAssistantEffectiveRoute()
        val routeNoteId = (effectiveRoute as? AppRoute.NoteDetail)?.noteId?.trim().orEmpty()
        val sourceNoteId = aiAssistantSourceNote?.id?.trim().orEmpty()
        val targetNoteId = routeNoteId.ifBlank { sourceNoteId }
        if (targetNoteId.isBlank()) return null
        return findLoadedNote(targetNoteId)
            ?: noteDetailState.note?.takeIf { note -> note.id == targetNoteId }
            ?: aiAssistantSourceNote?.takeIf { note -> note.id == targetNoteId }
    }
    fun openAiAssistantFromCurrentContext() {
        val sourceRoute = route
        val sourceIsChat = sourceRoute == AppRoute.Chat
        aiAssistantSourceRootRoute = rootRoute
        aiAssistantSourceRoute = sourceRoute
        aiAssistantSourceRoom = chatState.selectedRoom.takeIf { sourceIsChat }
        aiAssistantSourceConversation = chatState.selectedUserConversation.takeIf { sourceIsChat }
        aiAssistantSourceNote = (sourceRoute as? AppRoute.NoteDetail)
            ?.noteId
            ?.let { noteId ->
                findLoadedNote(noteId)
                    ?: noteDetailState.note?.takeIf { note -> note.id == noteId }
            }
        rootRoute = RootRoute.Chat
        route = AppRoute.AiAssistant
    }
    fun returnFromAiAssistant() {
        val targetRoute = aiAssistantSourceRoute ?: AppRoute.Chat
        rootRoute = aiAssistantSourceRootRoute ?: rootRouteFor(targetRoute)
        route = targetRoute
    }
    fun aiAssistantSelectedChatTitle(): String {
        val effectiveRoute = aiAssistantEffectiveRoute()
        return aiAssistantCurrentRoom()?.name?.ifBlank { "聊天室" }
            ?: aiAssistantCurrentUserConversation()?.user?.let { user -> user.displayName.ifBlank { user.username } }
            ?: chatState.selectedUserConversation
                ?.takeIf { effectiveRoute == AppRoute.Chat }
                ?.user
                ?.let { user -> user.displayName.ifBlank { user.username } }
            ?: aiAssistantSourceConversation?.user?.let { user -> user.displayName.ifBlank { user.username } }
            ?: "聊天"
    }
    fun aiAssistantContextSummary(): String {
        val settings = aiState.settings
        val effectiveRoute = aiAssistantEffectiveRoute()
        return buildString {
            append("账号：${accountUser?.displayName?.ifBlank { accountUser.username } ?: "未登录"}")
            append(" · 当前：")
            append(
                if (route == AppRoute.AiAssistant && effectiveRoute != route) {
                    "${routeLabel(effectiveRoute)}（来源）"
                } else {
                    routeLabel(route)
                },
            )
            append(" · AI：${if (aiState.hasUsableModel) "可用" else "未配置"}")
            append(
                when {
                    aiAssistantAutoApprovalSettings.highRiskEnabled -> " · 助手高风险自动批准"
                    aiAssistantAutoApprovalSettings.lowRiskEnabled -> " · 助手低风险自动批准"
                    else -> " · 助手全部需确认"
                },
            )
            append(" · 规则 ${automationState.rules.size}/${automationState.enabledRuleCount}")
            append(" · 日志 ${automationState.logs.size}")
            if (!settings.toolsAllowed) append(" · 工具需确认")
        }
    }
    fun aiAssistantContextText(): String {
        val settings = aiState.settings
        val chatContexts = if (settings.readChatAllowed && (chatState.selectedUserConversation == null || settings.readPrivateChatAllowed)) {
            chatState.messages.takeLast(20).map { message ->
                message.copy(text = chatMessageBodyText(message))
            }.toAiChatMessageContexts()
        } else {
            emptyList()
        }
        return buildString {
            appendLine(aiAssistantContextSummary())
            appendLine("权限：帖子=${settings.readTimelineAllowed}，通知=${settings.readNotificationsAllowed}，聊天=${settings.readChatAllowed}，私聊=${settings.readPrivateChatAllowed}，草稿=${settings.readDraftsAllowed}，自动化=${settings.automationAllowed}，工具=${settings.toolsAllowed}")
            appendLine("助手动作批准：低风险=${aiAssistantAutoApprovalSettings.lowRiskEnabled}，高风险=${aiAssistantAutoApprovalSettings.highRiskEnabled}。高风险开启时，客户端会自动执行助手建议出的发送、发布、删除、清空、退出等已支持动作。")
            appendLine("当前聊天：${aiAssistantSelectedChatTitle()}，聊天室 ${chatState.rooms.size} 个，私聊 ${chatState.userConversations.size} 个")
            appendLine("可用聊天室（名称 -> ID）：")
            (chatState.rooms + chatState.ownedRooms).distinctBy { it.id }.take(24).forEach { room ->
                appendLine("- ${room.name.ifBlank { "未命名聊天室" }} -> ${room.id}")
            }
            appendLine("可用用户（显示名 / @acct -> ID）：")
            automationKnownUsers().take(36).forEach { user ->
                val acct = user.username.trim().trim('@').let { username ->
                    user.host?.takeIf { host -> host.isNotBlank() && !username.contains("@") }?.let { host -> "$username@$host" }
                        ?: username
                }
                appendLine("- ${user.displayName.ifBlank { user.username }} / @$acct -> ${user.id}")
            }
            appendLine("可用频道（名称 -> ID）：")
            automationKnownChannels().take(18).forEach { channel ->
                appendLine("- ${channel.name} -> ${channel.id}")
            }
            if (composeState.draft.text.isNotBlank() && settings.readDraftsAllowed) {
                appendLine("当前草稿：${composeState.draft.text.take(800)}")
            }
            val currentNote = aiAssistantCurrentNote()
            if (currentNote != null && settings.readTimelineAllowed) {
                appendLine("当前帖子：ID=${currentNote.id}，作者=${currentNote.author.displayName.ifBlank { currentNote.author.username }}")
                appendLine(currentNote.toAiReadableText(settings.uploadSensitiveContentAllowed).take(1_200))
            }
            if (aiState.settings.assistantMemoryNotes.isNotEmpty()) {
                appendLine("本地助手记忆：")
                aiState.settings.assistantMemoryNotes.takeLast(8).forEach { memory -> appendLine("- $memory") }
            }
            if (chatContexts.isNotEmpty()) {
                appendLine("最近聊天：")
                chatContexts.forEach { message -> appendLine("- ${message.sender}: ${message.text}") }
            }
            appendLine("自动化：${automationState.rules.size} 条规则，启用 ${automationState.enabledRuleCount} 条")
            automationState.rules.take(8).forEach { rule ->
                appendLine("- ${rule.name} · ${rule.trigger.label} · 条件 ${rule.conditions.size} · 动作 ${rule.actions.size}")
            }
            if (automationState.logs.isNotEmpty()) {
                appendLine("最近执行日志：")
                automationState.logs.take(8).forEach { log ->
                    appendLine("- ${log.eventLabel} · ${log.actionLabel} · ${if (log.success) "成功" else "失败"}：${log.message}")
                }
            }
            if (automationState.debugRecords.isNotEmpty()) {
                appendLine("最近调试记录：")
                automationState.debugRecords.take(8).forEach { record ->
                    appendLine("- ${record.ruleName} · ${if (record.matched) "命中" else "未命中"}：${record.reason}")
                }
            }
        }
    }

    var autoApproveAiAssistantActions: (List<AiAssistantActionProposal>) -> Unit = {}

    fun attachAiAssistantFile(file: DriveFile) {
        aiAssistantAttachments = appendAiAssistantAttachment(aiAssistantAttachments, file)
    }

    fun removeAiAssistantAttachment(fileId: String) {
        aiAssistantAttachments = aiAssistantAttachments.filterNot { it.id == fileId }
    }

    fun uploadAiAssistantAttachment(upload: DriveFileUpload) {
        startAiAssistantAttachmentUpload(
            upload = upload,
            isUploading = aiAssistantAttachmentUploading,
            currentAttachments = aiAssistantAttachments,
            tokenProvider = { sessionToken },
            scope = appScope,
            onToast = { noteActionToast = it },
            onUploadingChanged = { aiAssistantAttachmentUploading = it },
            onAttachFile = ::attachAiAssistantFile,
        )
    }

    fun reportAiAssistantAttachmentError(message: String) {
        noteActionToast = message.trim().takeIf { it.isNotBlank() } ?: "无法读取所选文件"
    }

    fun requestAiAssistantReply(prompt: String, attachments: List<DriveFile> = aiAssistantAttachments) {
        startAiAssistantReply(
            prompt = prompt,
            attachments = attachments,
            currentAttachments = aiAssistantAttachments,
            currentDraft = aiAssistantDraft,
            messages = aiAssistantMessages,
            pendingPrompt = aiAssistantPendingPrompt,
            aiProcessing = aiState.isProcessing,
            attachmentUploading = aiAssistantAttachmentUploading,
            aiStateHolder = aiStateHolder,
            scope = appScope,
            contextTextProvider = ::aiAssistantContextText,
            requestGeneration = aiAssistantConversationGeneration,
            isRequestCurrent = { requestGeneration ->
                aiAssistantReplyRequestIsCurrent(
                    requestGeneration = requestGeneration,
                    currentGeneration = aiAssistantConversationGeneration,
                )
            },
            onToast = { noteActionToast = it },
            onMessagesChanged = { aiAssistantMessages = it },
            onPendingPromptChanged = { aiAssistantPendingPrompt = it },
            onDraftChanged = { aiAssistantDraft = it },
            onAttachmentsChanged = { aiAssistantAttachments = it },
            onAutoApproveActions = autoApproveAiAssistantActions,
        )
    }

    fun updateAiAssistantDraft(value: String) {
        aiAssistantDraft = value
    }

    fun sendAiAssistantDraft() {
        requestAiAssistantReply(aiAssistantDraft)
    }

    fun sendAiAssistantPrompt(prompt: String) {
        requestAiAssistantReply(prompt)
    }

    fun retryAiAssistantPrompt(prompt: String, attachments: List<DriveFile>) {
        requestAiAssistantReply(prompt, attachments)
    }

    fun clearAiAssistantConversation() {
        aiAssistantConversationGeneration += 1
        aiAssistantMessages = emptyList()
        aiAssistantDraft = ""
        aiAssistantAttachments = emptyList()
        aiAssistantPendingPrompt = null
    }

    fun openAiAssistantDrivePicker() {
        drivePickerTarget = DrivePickerTarget.AiAssistant
    }

    fun openAiAssistantAutomation() {
        rootRoute = RootRoute.Profile
        route = AppRoute.Automation
    }

    fun requestAiAssistantVoiceInput() {
        if (!aiState.hasUsableModel) {
            rootRoute = RootRoute.Profile
            route = AppRoute.AiSettings
            noteActionToast = "先配置 AI 后再使用小助手"
            return
        }
        if (aiAssistantPendingPrompt != null || aiState.isProcessing) {
            noteActionToast = "AI 正在处理上一条消息"
            return
        }
        val speechInput = speechTextInput
        if (speechInput == null) {
            openAiAssistantFromCurrentContext()
            noteActionToast = "当前设备不支持系统语音输入，已打开完整助手"
            return
        }
        noteActionToast = "正在等待语音输入"
        speechInput.requestText(
            prompt = "对 AI 小助手说点什么",
            onResult = { text ->
                val cleanText = text.trim()
                if (cleanText.isBlank()) {
                    noteActionToast = "没有识别到语音内容"
                } else {
                    noteActionToast = "已提交语音指令"
                    requestAiAssistantReply(cleanText)
                }
            },
            onError = { message ->
                noteActionToast = message.ifBlank { "语音输入不可用" }
            },
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
                        automationStateHolder.previewRuleDraft(
                            sourceText = cleanDescription,
                            rule = resolved.rule.copy(enabled = resolved.rule.enabled),
                            messages = resolved.messages,
                        )
                        noteActionToast = buildString {
                            append("AI 已生成规则草稿，请确认后创建")
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

    fun copyAiResultChecklist(text: String) {
        clipboardManager.setText(AnnotatedString(aiResultChecklistText(text)))
        noteActionToast = "已复制 AI 清单"
    }

    fun addAiResultMutedWord(text: String) {
        val phrase = aiResultMutedWordCandidate(text)
        if (phrase.isNullOrBlank()) {
            noteActionToast = "AI 结果里没有可添加的静音词"
            return
        }
        if (settingsState.remotePreferences == null) {
            settingsStateHolder.refreshRemote()
            noteActionToast = "正在同步设置，稍后再添加静音词"
            return
        }
        settingsStateHolder.updateMutedWordDraft(phrase)
        settingsStateHolder.addMutedWord()
        noteActionToast = "已添加静音词：$phrase"
    }

    fun requestAutomationDraftFromAiResult(text: String) {
        requestAutomationRuleDraft(text, automationState)
        rootRoute = RootRoute.Profile
        route = AppRoute.Automation
    }

    fun noteFromAiResult(text: String, notes: List<Note>): Note? {
        val candidates = notes.distinctBy { it.id }
        val referencedId = aiResultReferencedNoteId(text, candidates.map { it.id })
        return referencedId?.let { id -> candidates.firstOrNull { it.id == id } }
            ?: candidates.firstOrNull()
    }

    fun notificationNoteFromAiResult(text: String, notifications: List<NotificationItem>): Note? {
        val noteIds = notifications.mapNotNull { it.noteId?.takeIf { id -> id.isNotBlank() } }
        val referencedId = aiResultReferencedNoteId(text, noteIds) ?: noteIds.firstOrNull()
        return referencedId?.let(::findLoadedNote)
    }

    fun openAiRelatedNote(text: String, notes: List<Note>) {
        val note = noteFromAiResult(text, notes)
        if (note == null) {
            noteActionToast = "没有可打开的相关帖子"
            return
        }
        rootRoute = RootRoute.Timeline
        route = AppRoute.NoteDetail(note.id)
    }

    fun openAiRelatedNotificationNote(text: String, notifications: List<NotificationItem>) {
        val note = notificationNoteFromAiResult(text, notifications)
        if (note == null) {
            val noteId = aiResultReferencedNoteId(text, notifications.mapNotNull { it.noteId })
                ?: notifications.firstOrNull { !it.noteId.isNullOrBlank() }?.noteId
            if (noteId.isNullOrBlank()) {
                noteActionToast = "没有可打开的相关帖子"
            } else {
                rootRoute = RootRoute.Timeline
                route = AppRoute.NoteDetail(noteId)
            }
            return
        }
        rootRoute = RootRoute.Timeline
        route = AppRoute.NoteDetail(note.id)
    }

    fun updateAiAssistantActionStatus(
        actionId: String,
        status: AiAssistantActionStatus,
        detail: String = "",
    ) {
        aiAssistantMessages = aiAssistantMessages.map { message ->
            if (message.actions.none { it.id == actionId }) {
                message
            } else {
                message.copy(
                    actions = message.actions.map { action ->
                        if (action.id == actionId) {
                            action.copy(
                                status = status,
                                statusDetail = if (status == AiAssistantActionStatus.Failed) {
                                    detail.ifBlank { action.statusDetail }.ifBlank { "执行失败，请检查当前页面和权限" }
                                } else {
                                    ""
                                },
                            )
                        } else {
                            action
                        }
                    },
                )
            }
        }
    }

    fun findAiAssistantAction(actionId: String): AiAssistantActionProposal? {
        return aiAssistantMessages.asSequence()
            .flatMap { it.actions.asSequence() }
            .firstOrNull { it.id == actionId }
    }

    fun aiAssistantStatusAfterExecution(
        actionId: String,
        executed: Boolean,
        previousStatus: AiAssistantActionStatus? = null,
    ): AiAssistantActionStatus {
        val currentStatus = findAiAssistantAction(actionId)?.status
        if (
            previousStatus != null &&
            currentStatus != null &&
            currentStatus != previousStatus &&
            actionId !in pendingAiAssistantAsyncActionIds &&
            pendingAiAssistantRoomManagementAction?.actionId != actionId
        ) {
            return currentStatus
        }
        return when {
            !executed -> AiAssistantActionStatus.Failed
            actionId in pendingAiAssistantAsyncActionIds -> AiAssistantActionStatus.Running
            pendingAiAssistantRoomManagementAction?.actionId == actionId -> AiAssistantActionStatus.Running
            else -> AiAssistantActionStatus.Approved
        }
    }

    fun startAiAssistantAsyncAction(actionId: String) {
        pendingAiAssistantAsyncActionIds = pendingAiAssistantAsyncActionIds + actionId
        updateAiAssistantActionStatus(actionId, AiAssistantActionStatus.Running)
    }

    fun finishAiAssistantAsyncAction(
        actionId: String,
        result: AiAssistantActionExecutionResult,
    ) {
        pendingAiAssistantAsyncActionIds = pendingAiAssistantAsyncActionIds - actionId
        updateAiAssistantActionStatus(
            actionId,
            if (result.success) AiAssistantActionStatus.Approved else AiAssistantActionStatus.Failed,
            result.message,
        )
    }

    LaunchedEffect(
        pendingAiAssistantChatSendAction,
        chatState.isSendingMessage,
        chatState.messageErrorMessage,
    ) {
        val pending = pendingAiAssistantChatSendAction ?: return@LaunchedEffect
        if (chatState.isSendingMessage) {
            if (!pending.started) {
                pendingAiAssistantChatSendAction = pending.copy(started = true)
            }
            return@LaunchedEffect
        }
        if (!pending.started) {
            delay(250L)
            val latest = chatStateHolder.state.value
            when {
                latest.isSendingMessage -> pendingAiAssistantChatSendAction = pending.copy(started = true)
                !latest.messageErrorMessage.isNullOrBlank() -> {
                    val result = AiAssistantActionExecutionResult(false, latest.messageErrorMessage.orEmpty())
                    finishAiAssistantAsyncAction(pending.actionId, result)
                    noteActionToast = result.message
                    pendingAiAssistantChatSendAction = null
                }
            }
            return@LaunchedEffect
        }
        val error = chatState.messageErrorMessage?.trim().orEmpty()
        val result = if (error.isBlank()) {
            AiAssistantActionExecutionResult(true, "已发送当前聊天草稿")
        } else {
            AiAssistantActionExecutionResult(false, error)
        }
        finishAiAssistantAsyncAction(pending.actionId, result)
        noteActionToast = result.message
        pendingAiAssistantChatSendAction = null
    }

    LaunchedEffect(
        pendingAiAssistantComposeSendAction,
        composeState.isSending,
        composeState.errorMessage,
        composeState.createdNoteId,
    ) {
        val pending = pendingAiAssistantComposeSendAction ?: return@LaunchedEffect
        if (composeState.isSending) {
            if (!pending.started) {
                pendingAiAssistantComposeSendAction = pending.copy(started = true)
            }
            return@LaunchedEffect
        }
        if (!pending.started) {
            delay(250L)
            val latest = composeStateHolder.state.value
            when {
                latest.isSending -> pendingAiAssistantComposeSendAction = pending.copy(started = true)
                !latest.errorMessage.isNullOrBlank() -> {
                    val result = AiAssistantActionExecutionResult(false, latest.errorMessage.orEmpty())
                    finishAiAssistantAsyncAction(pending.actionId, result)
                    noteActionToast = result.message
                    pendingAiAssistantComposeSendAction = null
                }
                !latest.createdNoteId.isNullOrBlank() -> {
                    val result = AiAssistantActionExecutionResult(true, "已发布帖子")
                    finishAiAssistantAsyncAction(pending.actionId, result)
                    noteActionToast = result.message
                    pendingAiAssistantComposeSendAction = null
                }
            }
            return@LaunchedEffect
        }
        val error = composeState.errorMessage?.trim().orEmpty()
        val result = if (error.isBlank()) {
            AiAssistantActionExecutionResult(true, "已发布帖子")
        } else {
            AiAssistantActionExecutionResult(false, error)
        }
        finishAiAssistantAsyncAction(pending.actionId, result)
        noteActionToast = result.message
        pendingAiAssistantComposeSendAction = null
    }

    LaunchedEffect(
        pendingAiAssistantRoomManagementAction,
        chatState.isManagingRoom,
        chatState.roomManagementMessage,
    ) {
        val pending = pendingAiAssistantRoomManagementAction ?: return@LaunchedEffect
        if (chatState.isManagingRoom) {
            if (!pending.started) {
                pendingAiAssistantRoomManagementAction = pending.copy(started = true)
            }
            return@LaunchedEffect
        }
        val message = chatState.roomManagementMessage?.trim().orEmpty()
        if (message.isBlank()) return@LaunchedEffect
        val success = pending.successMessages.any { expected -> message.contains(expected) }
        updateAiAssistantActionStatus(
            actionId = pending.actionId,
            status = if (success) AiAssistantActionStatus.Approved else AiAssistantActionStatus.Failed,
            detail = message,
        )
        noteActionToast = message
        pendingAiAssistantRoomManagementAction = null
    }

    fun aiAssistantSearchUrl(query: String): String {
        val encoded = query.trim().take(200).encodeToByteArray().joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            when {
                char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' -> char.toString()
                char == ' ' -> "+"
                char == '-' || char == '_' || char == '.' || char == '~' -> char.toString()
                else -> "%" + value.toString(16).uppercase().padStart(2, '0')
            }
        }
        return "https://www.google.com/search?q=$encoded"
    }

    fun openAiAssistantAppRoute(routeTarget: AppRoute, toast: String) {
        rootRoute = rootRouteFor(routeTarget)
        route = routeTarget
        noteActionToast = toast
    }

    fun refreshAiAssistantCurrentView() {
        when (val current = aiAssistantEffectiveRoute()) {
            AppRoute.Timeline -> timelineStateHolder.refresh()
            AppRoute.Discover -> {
                when {
                    discoverState.query.isBlank() && !discoverState.hasSearched -> discoverStateHolder.refreshHomeQuietly(force = true)
                    discoverState.selectedMode == cc.hhhl.client.state.DiscoverSearchMode.Trends -> discoverStateHolder.refreshTrendsQuietly()
                    discoverState.query.isNotBlank() || discoverState.hasSearched -> discoverStateHolder.search()
                    else -> discoverStateHolder.refreshPinnedUsersQuietly(force = true)
                }
            }
            AppRoute.Chat,
            AppRoute.AiAssistant,
                -> {
                chatStateHolder.refresh()
                chatStateHolder.refreshRoomExtras()
            }
            AppRoute.Notifications -> notificationStateHolder.refresh()
            AppRoute.Profile,
            AppRoute.ProfileNotes,
                -> userProfileStateHolder.load()
            AppRoute.Settings,
            AppRoute.AiSettings,
                -> settingsStateHolder.refreshRemote()
            AppRoute.ReleaseNotes -> noteActionToast = "更新日志已是本地时间线"
            AppRoute.ThemeCustomization -> noteActionToast = "主题自定义页无需刷新"
            AppRoute.Automation,
            AppRoute.AutomationLogs,
                -> automationStateHolder.restore()
            AppRoute.AdminDashboard -> adminStateHolder.refresh()
            AppRoute.Drive -> driveFilesStateHolder.refresh()
            AppRoute.Achievements -> achievementStateHolder.refresh(force = true)
            AppRoute.FavoriteNotes -> favoriteNoteStateHolder.refresh()
            AppRoute.UserLists -> {
                if (userListState.selectedList == null) userListStateHolder.refreshLists() else userListStateHolder.refreshTimeline()
            }
            AppRoute.FollowRequests -> followRequestStateHolder.refresh()
            AppRoute.RelationshipManagement -> relationshipManagementStateHolder.refresh()
            AppRoute.Antennas -> antennaStateHolder.refreshAntennas()
            AppRoute.Clips -> clipStateHolder.refreshClips()
            AppRoute.Channels -> {
                if (channelState.selectedChannel == null) channelStateHolder.refreshChannels() else channelStateHolder.refreshTimeline()
            }
            AppRoute.Pages -> pageStateHolder.refreshPages()
            AppRoute.Gallery -> galleryStateHolder.refreshPosts()
            AppRoute.Flash -> flashStateHolder.refreshFlashes()
            AppRoute.Announcements -> announcementStateHolder.refresh()
            is AppRoute.SettingsManagement -> settingsStateHolder.refreshManagement()
            is AppRoute.UserProfile -> viewedProfileStateHolder.load()
            is AppRoute.UserSocial -> userSocialStateHolder.load(current.userId, current.kind, current.displayName)
            is AppRoute.NoteDetail -> noteDetailStateHolder.load(current.noteId)
            is AppRoute.Compose -> noteActionToast = "发帖页保留当前草稿"
        }
        if (noteActionToast == null) {
            noteActionToast = "已刷新当前页面"
        }
    }

    fun aiAssistantRoomActionTarget(payload: String, actionName: String): ChatRoom? {
        val cleanPayload = payload.trim()
        val currentRoom = aiAssistantCurrentRoom()
        val availableRooms = (listOfNotNull(currentRoom, chatState.selectedRoom, aiAssistantSourceRoom) + chatState.rooms + chatState.ownedRooms)
            .filter { room -> room.id.isNotBlank() }
            .distinctBy { room -> room.id }
        val matchedRooms = availableRooms.filter { room ->
            cleanPayload.containsRoomIdentifier(room)
        }
        return when {
            matchedRooms.size == 1 -> matchedRooms.first()
            matchedRooms.size > 1 -> {
                rootRoute = RootRoute.Chat
                route = AppRoute.Chat
                noteActionToast = "找到多个可能要${actionName}的聊天室，请写完整房间名或 ID"
                null
            }
            cleanPayload.aiAssistantTargetsCurrentChatRoom() -> {
                currentRoom ?: run {
                    rootRoute = RootRoute.Chat
                    route = AppRoute.Chat
                    noteActionToast = "请先打开要${actionName}的聊天室"
                    null
                }
            }
            else -> {
                rootRoute = RootRoute.Chat
                route = AppRoute.Chat
                noteActionToast = "请明确要${actionName}的聊天室名称或 ID，或先打开目标聊天室"
                null
            }
        }
    }

    fun executeAiAssistantCurrentRoomManagementAction(
        actionId: String,
        actionName: String,
        requireManager: Boolean = false,
        payload: String = "",
        successMessages: Set<String>,
        execute: (ChatRoom) -> Unit,
    ): Boolean {
        val room = aiAssistantRoomActionTarget(payload = payload, actionName = actionName)
        if (room == null) {
            rootRoute = RootRoute.Chat
            route = AppRoute.Chat
            return false
        }
        if (requireManager && !canAttemptAiAssistantRoomManagement(room, chatState.ownedRooms, accountUser?.id)) {
            rootRoute = RootRoute.Chat
            route = AppRoute.Chat
            noteActionToast = "当前账号没有${actionName}这个聊天室的权限"
            return false
        }
        if (chatState.isManagingRoom) {
            rootRoute = RootRoute.Chat
            route = AppRoute.Chat
            noteActionToast = "聊天室管理操作正在执行，稍后再试"
            return false
        }
        rootRoute = RootRoute.Chat
        route = AppRoute.Chat
        pendingAiAssistantRoomManagementAction = PendingAiAssistantRoomManagementAction(
            actionId = actionId,
            successMessages = successMessages,
        )
        execute(room)
        noteActionToast = "已提交高风险动作：${actionName}${room.name.ifBlank { "目标聊天室" }}"
        return true
    }

    fun aiAssistantNoteActionTarget(payload: String, actionName: String): Note? {
        val cleanPayload = payload.trim()
        val currentNote = aiAssistantCurrentNote()
        val availableNotes = (listOfNotNull(currentNote, aiAssistantSourceNote, noteDetailState.note) + loadedNotes)
            .filter { note -> note.id.isNotBlank() }
            .distinctBy { note -> note.id }
        val matchedNotes = availableNotes
            .filter { note -> cleanPayload.containsNoteIdentifier(note) }
        return when {
            matchedNotes.size == 1 -> matchedNotes.first()
            matchedNotes.size > 1 -> {
                rootRoute = RootRoute.Timeline
                noteActionToast = "找到多个可能要${actionName}的帖子，请写完整帖子 ID"
                null
            }
            cleanPayload.targetsCurrentNote() -> {
                currentNote ?: run {
                    rootRoute = RootRoute.Timeline
                    noteActionToast = "请先打开要${actionName}的帖子详情，或写明帖子 ID"
                    null
                }
            }
            else -> {
                rootRoute = RootRoute.Timeline
                noteActionToast = "请明确要${actionName}的帖子 ID，或先打开目标帖子详情"
                null
            }
        }
    }

    fun executeAiAssistantCurrentNoteAction(
        actionName: String,
        payload: String,
        openAfterAction: Boolean = true,
        execute: (Note) -> AiAssistantActionExecutionResult,
    ): Boolean {
        val note = aiAssistantNoteActionTarget(payload = payload, actionName = actionName) ?: return false
        rootRoute = RootRoute.Timeline
        if (openAfterAction) {
            route = AppRoute.NoteDetail(note.id)
        }
        val result = execute(note)
        noteActionToast = result.message
        return result.success
    }

    fun favoriteAiAssistantNote(note: Note): AiAssistantActionExecutionResult {
        if (note.isFavorited) return AiAssistantActionExecutionResult(true, "当前帖子已收藏")
        applyNoteMutation(NoteLocalMutation.Favorite(note.id))
        favoriteNoteStateHolder.addLocalFavorite(note)
        noteActionStateHolder.perform(NoteActionRequest.Favorite(note.id))
        return AiAssistantActionExecutionResult(true, "已收藏当前帖子")
    }

    fun unfavoriteAiAssistantNote(note: Note): AiAssistantActionExecutionResult {
        if (!note.isFavorited) return AiAssistantActionExecutionResult(true, "当前帖子未收藏")
        applyNoteMutation(NoteLocalMutation.Unfavorite(note.id))
        noteActionStateHolder.perform(NoteActionRequest.Unfavorite(note.id))
        return AiAssistantActionExecutionResult(true, "已取消收藏当前帖子")
    }

    fun reactAiAssistantNote(note: Note): AiAssistantActionExecutionResult {
        if (!note.myReaction.isNullOrBlank()) return AiAssistantActionExecutionResult(true, "当前帖子已有反应")
        val reaction = NoteActionRequest.DEFAULT_REACTION
        applyNoteMutation(NoteLocalMutation.React(note.id, reaction))
        noteActionStateHolder.perform(NoteActionRequest.React(note.id, reaction))
        return AiAssistantActionExecutionResult(true, "已给当前帖子发送反应")
    }

    fun deleteReactionAiAssistantNote(note: Note): AiAssistantActionExecutionResult {
        val reaction = note.myReaction?.takeIf { it.isNotBlank() }
            ?: return AiAssistantActionExecutionResult(true, "当前帖子没有可取消的反应")
        applyNoteMutation(NoteLocalMutation.DeleteReaction(note.id, reaction))
        noteActionStateHolder.perform(NoteActionRequest.DeleteReaction(note.id))
        return AiAssistantActionExecutionResult(true, "已取消当前帖子反应")
    }

    fun renoteAiAssistantNote(note: Note): AiAssistantActionExecutionResult {
        applyNoteMutation(NoteLocalMutation.Renote(note.id))
        noteActionStateHolder.perform(NoteActionRequest.Renote(note.id))
        return AiAssistantActionExecutionResult(true, "已转发当前帖子")
    }

    fun deleteAiAssistantNote(note: Note): AiAssistantActionExecutionResult {
        if (accountUser?.id != note.author.id) return AiAssistantActionExecutionResult(false, "只能删除自己发布的帖子")
        route = AppRoute.Timeline
        applyNoteMutation(NoteLocalMutation.Delete(note.id))
        noteActionStateHolder.perform(NoteActionRequest.Delete(note.id))
        return AiAssistantActionExecutionResult(true, "已删除当前帖子")
    }

    fun resolveKnownAiAssistantUser(target: String): User? {
        val clean = target.aiAssistantCleanTarget()
        if (clean.isBlank()) return null
        val users = automationKnownUsers()
        val exact = users.aiAssistantMatchingUsers(clean, fuzzy = false)
        if (exact.size == 1) return exact.single()
        if (exact.size > 1) {
            noteActionToast = "找到多个叫“$target”的用户，请说完整用户名或 @acct"
            return null
        }
        val fuzzy = users.aiAssistantMatchingUsers(clean, fuzzy = true)
        return when (fuzzy.size) {
            0 -> null
            1 -> fuzzy.single()
            else -> {
                noteActionToast = "找到多个可能是“$target”的用户，请说完整用户名或 @acct"
                null
            }
        }
    }

    suspend fun resolveAiAssistantUser(target: String): User? {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) return null
        resolveKnownAiAssistantUser(cleanTarget)?.let { return it }
        val searched = searchAutomationUsers(cleanTarget)
        val exact = searched.aiAssistantMatchingUsers(cleanTarget, fuzzy = false)
        val candidates = if (exact.isNotEmpty()) {
            exact
        } else {
            searched.aiAssistantMatchingUsers(cleanTarget, fuzzy = true)
        }
        return when (candidates.size) {
            0 -> {
                noteActionToast = "找不到明确用户：$cleanTarget，请说完整用户名或 @acct"
                null
            }
            1 -> candidates.single()
            else -> {
                noteActionToast = "找到多个用户匹配“$cleanTarget”，请说完整用户名或 @acct"
                null
            }
        }
    }

    suspend fun resolveAiAssistantMentionUsers(names: List<String>): List<User>? {
        val cleanNames = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanNames.isEmpty()) return emptyList()
        val users = mutableListOf<User>()
        for (name in cleanNames) {
            val user = resolveAiAssistantUser(name) ?: return null
            users += user
        }
        return users.distinctBy { it.id }
    }

    fun ChatRoom.aiAssistantMatchesTarget(target: String): Boolean {
        val clean = target.aiAssistantCleanTarget()
        if (clean.isBlank()) return false
        val idValue = id.aiAssistantCleanTarget()
        val nameValue = name.aiAssistantCleanTarget()
        return clean == idValue ||
            clean == nameValue ||
            aiAssistantFuzzyTargetMatches(nameValue, clean)
    }

    fun resolveKnownAiAssistantRoom(target: String): ChatRoom? {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) return aiAssistantCurrentRoom()
        val rooms = (listOfNotNull(aiAssistantCurrentRoom(), chatState.selectedRoom, aiAssistantSourceRoom) + chatState.rooms + chatState.ownedRooms)
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        val matched = rooms.filter { room -> room.aiAssistantMatchesTarget(cleanTarget) }
        return when (matched.size) {
            0 -> {
                noteActionToast = "找不到聊天室：$cleanTarget"
                null
            }
            1 -> matched.single()
            else -> {
                noteActionToast = "找到多个聊天室匹配“$cleanTarget”，请说完整聊天室名或 ID"
                null
            }
        }
    }

    suspend fun resolveAiAssistantRoom(target: String): ChatRoom? {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) return aiAssistantCurrentRoom()
        resolveKnownAiAssistantRoom(cleanTarget)?.let { return it }
        val remoteRooms = loadAutomationRoomsForResolve()
        val matched = remoteRooms.filter { room -> room.aiAssistantMatchesTarget(cleanTarget) }
        return when (matched.size) {
            0 -> {
                noteActionToast = "找不到聊天室：$cleanTarget"
                null
            }
            1 -> matched.single()
            else -> {
                noteActionToast = "找到多个聊天室匹配“$cleanTarget”，请说完整聊天室名或 ID"
                null
            }
        }
    }

    fun Channel.aiAssistantMatchesTarget(target: String): Boolean {
        val clean = target.aiAssistantCleanTarget()
        if (clean.isBlank()) return false
        val idValue = id.aiAssistantCleanTarget()
        val nameValue = name.aiAssistantCleanTarget()
        return clean == idValue ||
            clean == nameValue ||
            aiAssistantFuzzyTargetMatches(nameValue, clean)
    }

    suspend fun resolveAiAssistantChannel(target: String): Channel? {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) return null
        val local = (automationKnownChannels() + channelState.channels + listOfNotNull(channelState.selectedChannel))
            .distinctBy { it.id }
        val localMatches = local.filter { channel -> channel.aiAssistantMatchesTarget(cleanTarget) }
        if (localMatches.size == 1) return localMatches.single()
        if (localMatches.size > 1) {
            noteActionToast = "找到多个频道匹配“$cleanTarget”，请说完整频道名或 ID"
            return null
        }
        val remote = loadAutomationChannelsForResolve()
        val remoteMatches = remote.filter { channel -> channel.aiAssistantMatchesTarget(cleanTarget) }
            .distinctBy { it.id }
        return when (remoteMatches.size) {
            0 -> {
                noteActionToast = "找不到频道：$cleanTarget"
                null
            }
            1 -> remoteMatches.single()
            else -> {
                noteActionToast = "找到多个频道匹配“$cleanTarget”，请说完整频道名或 ID"
                null
            }
        }
    }

    fun String.toAiAssistantVisibility(default: NoteVisibility): NoteVisibility {
        return when (trim().lowercase()) {
            "public", "公开", "公用", "所有人" -> NoteVisibility.Public
            "home", "首页", "主页", "首页可见" -> NoteVisibility.Home
            "followers", "follower", "关注者", "粉丝", "仅关注者" -> NoteVisibility.Followers
            "specified", "direct", "指定", "指定用户", "私密", "仅指定" -> NoteVisibility.Specified
            else -> default
        }
    }

    fun String.toAiAssistantBoolean(): Boolean? {
        return when (trim().lowercase()) {
            "true", "yes", "1", "on", "本地", "仅本站", "本地限定", "是", "开启" -> true
            "false", "no", "0", "off", "否", "关闭" -> false
            else -> null
        }
    }

    fun aiAssistantBodyWithMentions(body: String, mentionUsers: List<User>): String {
        val cleanBody = body.trim()
        val prefix = mentionUsers
            .map { it.aiAssistantMention() }
            .filter { mention -> mention.isNotBlank() && !cleanBody.contains(mention, ignoreCase = true) }
            .distinct()
            .joinToString(" ")
        return listOf(prefix, cleanBody)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    fun mapAiAssistantChatSendResult(result: ChatMessageRepositoryResult, targetLabel: String): AiAssistantActionExecutionResult {
        return when (result) {
            is ChatMessageRepositoryResult.Created -> AiAssistantActionExecutionResult(true, "已发送消息到$targetLabel")
            ChatMessageRepositoryResult.Unauthorized -> AiAssistantActionExecutionResult(false, "登录已失效，无法发送消息")
            is ChatMessageRepositoryResult.Error -> AiAssistantActionExecutionResult(false, result.message)
            is ChatMessageRepositoryResult.Success,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> AiAssistantActionExecutionResult(true, "已发送消息到$targetLabel")
        }
    }

    fun mapAiAssistantComposeResult(result: ComposeRepositoryResult): AiAssistantActionExecutionResult {
        return when (result) {
            is ComposeRepositoryResult.Success -> AiAssistantActionExecutionResult(true, "已发布帖子")
            ComposeRepositoryResult.Unauthorized -> AiAssistantActionExecutionResult(false, "登录已失效，无法发布帖子")
            is ComposeRepositoryResult.ValidationError -> AiAssistantActionExecutionResult(false, result.message)
            is ComposeRepositoryResult.Error -> AiAssistantActionExecutionResult(false, result.message)
        }
    }

    fun executeAiAssistantTargetedChatAction(action: AiAssistantActionProposal, payload: AiAssistantActionPayload): Boolean {
        startAiAssistantAsyncAction(action.id)
        appScope.launch {
            val mentionUsers = resolveAiAssistantMentionUsers(payload.mentions) ?: run {
                finishAiAssistantAsyncAction(action.id, AiAssistantActionExecutionResult(false, noteActionToast.orEmpty()))
                return@launch
            }
            val body = payload.body.ifBlank { chatStateHolder.state.value.messageDraft }.trim()
            val message = aiAssistantBodyWithMentions(body, mentionUsers)
            if (message.isBlank()) {
                noteActionToast = "没有可发送的聊天内容"
                finishAiAssistantAsyncAction(action.id, AiAssistantActionExecutionResult(false, noteActionToast.orEmpty()))
                return@launch
            }
            val targetUser = payload.targetUser.takeIf { it.isNotBlank() }?.let { resolveAiAssistantUser(it) }
            if (payload.targetUser.isNotBlank() && targetUser == null) {
                finishAiAssistantAsyncAction(action.id, AiAssistantActionExecutionResult(false, noteActionToast.orEmpty()))
                return@launch
            }
            val targetRoom = if (targetUser == null) resolveAiAssistantRoom(payload.targetRoom) else null
            if (targetUser == null && targetRoom == null) {
                finishAiAssistantAsyncAction(action.id, AiAssistantActionExecutionResult(false, noteActionToast.orEmpty()))
                return@launch
            }
            val result = if (targetUser != null) {
                mapAiAssistantChatSendResult(
                    chatRepository.sendUserMessage(userId = targetUser.id, text = message),
                    targetLabel = targetUser.displayName.ifBlank { targetUser.username },
                )
            } else {
                mapAiAssistantChatSendResult(
                    chatRepository.sendMessage(roomId = targetRoom!!.id, text = message),
                    targetLabel = targetRoom.name.ifBlank { "聊天室" },
                )
            }
            noteActionToast = result.message
            finishAiAssistantAsyncAction(action.id, result)
            rootRoute = RootRoute.Chat
            route = AppRoute.Chat
        }
        noteActionToast = "正在解析目标并发送聊天消息"
        return true
    }

    fun executeAiAssistantTargetedComposeAction(action: AiAssistantActionProposal, payload: AiAssistantActionPayload): Boolean {
        startAiAssistantAsyncAction(action.id)
        appScope.launch {
            val mentionUsers = resolveAiAssistantMentionUsers(payload.mentions) ?: run {
                finishAiAssistantAsyncAction(action.id, AiAssistantActionExecutionResult(false, noteActionToast.orEmpty()))
                return@launch
            }
            val targetUsers = buildList {
                payload.targetUser.takeIf { it.isNotBlank() }?.let { target ->
                    resolveAiAssistantUser(target)?.let(::add)
                }
            }
            if (payload.targetUser.isNotBlank() && targetUsers.isEmpty()) {
                finishAiAssistantAsyncAction(action.id, AiAssistantActionExecutionResult(false, noteActionToast.orEmpty()))
                return@launch
            }
            val body = payload.body.ifBlank { composeStateHolder.state.value.draft.text }.trim()
            val text = aiAssistantBodyWithMentions(body, mentionUsers)
            if (text.isBlank()) {
                noteActionToast = "没有可发布的帖子正文"
                finishAiAssistantAsyncAction(action.id, AiAssistantActionExecutionResult(false, noteActionToast.orEmpty()))
                return@launch
            }
            val channel = payload.channel.takeIf { it.isNotBlank() }?.let { resolveAiAssistantChannel(it) }
            if (payload.channel.isNotBlank() && channel == null) {
                finishAiAssistantAsyncAction(action.id, AiAssistantActionExecutionResult(false, noteActionToast.orEmpty()))
                return@launch
            }
            val baseDraft = composeStateHolder.state.value.draft
            val requestedVisibility = payload.visibility.toAiAssistantVisibility(baseDraft.visibility)
            val finalVisibility = if (targetUsers.isNotEmpty()) NoteVisibility.Specified else requestedVisibility
            val result = mapAiAssistantComposeResult(
                composeRepository.send(
                    ComposeDraft(
                        text = text,
                        visibility = finalVisibility,
                        visibleUserIds = if (finalVisibility == NoteVisibility.Specified) targetUsers.map { it.id } else emptyList(),
                        cw = payload.contentWarning.takeIf { it.isNotBlank() } ?: baseDraft.cw,
                        channelId = channel?.id ?: baseDraft.channelId,
                        localOnly = payload.localOnly.toAiAssistantBoolean() ?: baseDraft.localOnly,
                    ),
                ),
            )
            noteActionToast = result.message
            if (result.success) {
                if (channel != null) {
                    rootRoute = RootRoute.Discover
                    route = AppRoute.Channels
                    channelStateHolder.refreshTimeline()
                } else {
                    rootRoute = RootRoute.Timeline
                    route = AppRoute.Timeline
                    timelineStateHolder.refresh(TimelineKind.Home)
                }
            }
            finishAiAssistantAsyncAction(action.id, result)
        }
        noteActionToast = "正在解析目标并发布帖子"
        return true
    }

    fun executeAiAssistantAction(action: AiAssistantActionProposal): Boolean {
        var executed = true
        when (action.kind) {
            AiAssistantActionKind.CreateAutomationDraft -> requestAutomationDraftFromAiResult(action.payload)
            AiAssistantActionKind.ReviewAutomationRisk -> {
                rootRoute = RootRoute.Profile
                route = AppRoute.Automation
                noteActionToast = "已打开自动化中心"
            }
            AiAssistantActionKind.CreateForwardTemplateDraft -> requestAutomationDraftFromAiResult(
                action.payload.ifBlank {
                    "把当前聊天室的消息按模板转发到另一个聊天室，模板包含来源聊天室、发送者和摘要，并开启防循环。"
                },
            )
            AiAssistantActionKind.OpenAutomation -> {
                rootRoute = RootRoute.Profile
                route = AppRoute.Automation
                noteActionToast = "已打开自动化中心"
            }
            AiAssistantActionKind.OpenAutomationLogs -> {
                rootRoute = RootRoute.Profile
                route = AppRoute.AutomationLogs
                noteActionToast = "已打开自动化日志"
            }
            AiAssistantActionKind.OpenTimeline -> openAiAssistantAppRoute(AppRoute.Timeline, "已打开时间线")
            AiAssistantActionKind.OpenDiscover -> openAiAssistantAppRoute(AppRoute.Discover, "已打开发现")
            AiAssistantActionKind.OpenDiscoverSearch -> {
                rootRoute = RootRoute.Discover
                route = AppRoute.Discover
                discoverStateHolder.selectMode(cc.hhhl.client.state.DiscoverSearchMode.Notes)
                val query = action.payload.trim().take(160)
                if (query.isNotBlank()) {
                    discoverStateHolder.updateQuery(query)
                    discoverStateHolder.search()
                    noteActionToast = "已打开站内搜索"
                } else {
                    noteActionToast = "已打开发现搜索"
                }
            }
            AiAssistantActionKind.OpenNotifications -> openAiAssistantAppRoute(AppRoute.Notifications, "已打开通知")
            AiAssistantActionKind.OpenProfile -> openAiAssistantAppRoute(AppRoute.Profile, "已打开我的")
            AiAssistantActionKind.OpenProfileNotes -> openAiAssistantAppRoute(AppRoute.ProfileNotes, "已打开我的帖子")
            AiAssistantActionKind.OpenSettings -> openAiAssistantAppRoute(AppRoute.Settings, "已打开设置")
            AiAssistantActionKind.OpenAiSettings -> openAiAssistantAppRoute(AppRoute.AiSettings, "已打开 AI 设置")
            AiAssistantActionKind.OpenReleaseNotes -> openAiAssistantAppRoute(AppRoute.ReleaseNotes, "已打开更新日志")
            AiAssistantActionKind.OpenThemeCustomization -> openAiAssistantAppRoute(AppRoute.ThemeCustomization, "已打开主题自定义")
            AiAssistantActionKind.OpenCompose -> openAiAssistantAppRoute(AppRoute.Compose(), "已打开发帖页")
            AiAssistantActionKind.OpenChat -> {
                chatStateHolder.closeRoom()
                openAiAssistantAppRoute(AppRoute.Chat, "已打开聊天列表")
            }
            AiAssistantActionKind.OpenDrive -> openAiAssistantAppRoute(AppRoute.Drive, "已打开网盘")
            AiAssistantActionKind.OpenAdminDashboard -> openAiAssistantAppRoute(AppRoute.AdminDashboard, "已打开管理后台")
            AiAssistantActionKind.OpenAchievements -> openAiAssistantAppRoute(AppRoute.Achievements, "已打开成就")
            AiAssistantActionKind.OpenFavoriteNotes -> openAiAssistantAppRoute(AppRoute.FavoriteNotes, "已打开收藏")
            AiAssistantActionKind.OpenUserLists -> openAiAssistantAppRoute(AppRoute.UserLists, "已打开列表")
            AiAssistantActionKind.OpenFollowRequests -> openAiAssistantAppRoute(AppRoute.FollowRequests, "已打开关注请求")
            AiAssistantActionKind.OpenRelationshipManagement -> openAiAssistantAppRoute(AppRoute.RelationshipManagement, "已打开关系管理")
            AiAssistantActionKind.OpenAntennas -> openAiAssistantAppRoute(AppRoute.Antennas, "已打开天线")
            AiAssistantActionKind.OpenClips -> openAiAssistantAppRoute(AppRoute.Clips, "已打开剪辑")
            AiAssistantActionKind.OpenChannels -> openAiAssistantAppRoute(AppRoute.Channels, "已打开频道")
            AiAssistantActionKind.OpenPages -> openAiAssistantAppRoute(AppRoute.Pages, "已打开页面")
            AiAssistantActionKind.OpenGallery -> openAiAssistantAppRoute(AppRoute.Gallery, "已打开相册")
            AiAssistantActionKind.OpenFlash -> openAiAssistantAppRoute(AppRoute.Flash, "已打开 Flash")
            AiAssistantActionKind.OpenAnnouncements -> openAiAssistantAppRoute(AppRoute.Announcements, "已打开公告")
            AiAssistantActionKind.RefreshCurrentView -> refreshAiAssistantCurrentView()
            AiAssistantActionKind.CheckForUpdates -> {
                rootRoute = RootRoute.Profile
                route = AppRoute.Settings
                noteActionToast = "正在检查更新"
                onCheckForUpdates { message -> noteActionToast = message }
            }
            AiAssistantActionKind.RunChatSummary -> {
                if (!ensureAiAssistantCurrentChatSelected("总结")) {
                    executed = false
                } else {
                    rootRoute = RootRoute.Chat
                    route = AppRoute.Chat
                    appScope.launch {
                        delay(120L)
                        requestChatAi(
                            AiTaskKind.ChatRecentSummary,
                            chatStateHolder.state.value,
                            aiAssistantSelectedChatTitle(),
                        )
                    }
                }
            }
            AiAssistantActionKind.FillChatDraft -> {
                val payload = aiAssistantActionPayload(action.payload)
                if (payload.hasRouting) {
                    val mentionUsers = payload.mentions.mapNotNull(::resolveKnownAiAssistantUser)
                    val draft = aiAssistantBodyWithMentions(payload.body, mentionUsers)
                    if (draft.isBlank()) {
                        noteActionToast = "没有可填入的聊天草稿"
                        executed = false
                    } else {
                        payload.targetRoom.takeIf { it.isNotBlank() }?.let(::resolveKnownAiAssistantRoom)?.let(chatStateHolder::selectRoom)
                        payload.targetUser.takeIf { it.isNotBlank() }?.let(::resolveKnownAiAssistantUser)?.let { user ->
                            chatStateHolder.selectUserConversation(ChatUserConversation(user = user))
                        }
                        chatStateHolder.updateMessageDraft(draft)
                        rootRoute = RootRoute.Chat
                        route = AppRoute.Chat
                        noteActionToast = "已填入带目标信息的聊天草稿"
                    }
                } else if (!ensureAiAssistantCurrentChatSelected("填入草稿")) {
                    executed = false
                } else {
                    chatStateHolder.updateMessageDraft(payload.body)
                    rootRoute = RootRoute.Chat
                    route = AppRoute.Chat
                    noteActionToast = "已填入聊天输入框"
                }
            }
            AiAssistantActionKind.SendChatDraft -> {
                val payload = aiAssistantActionPayload(action.payload)
                if (payload.hasRouting) {
                    executed = executeAiAssistantTargetedChatAction(action, payload)
                } else if (!ensureAiAssistantCurrentChatSelected("发送")) {
                    executed = false
                } else {
                    val currentChatState = chatStateHolder.state.value
                    if (currentChatState.isSendingMessage) {
                        rootRoute = RootRoute.Chat
                        route = AppRoute.Chat
                        noteActionToast = "聊天消息正在发送，稍后再试"
                        executed = false
                    } else {
                        val outgoingDraft = aiAssistantOutgoingDraftText(currentChatState.messageDraft, payload.body)
                        val shouldUsePayloadBody = payload.body.isNotBlank() &&
                            currentChatState.messageDraft.trim() != outgoingDraft
                        if (outgoingDraft.isBlank()) {
                            rootRoute = RootRoute.Chat
                            route = AppRoute.Chat
                            noteActionToast = "聊天输入框没有可发送的草稿"
                            executed = false
                        } else {
                            startAiAssistantAsyncAction(action.id)
                            pendingAiAssistantChatSendAction = PendingAiAssistantObservedAction(action.id)
                            if (shouldUsePayloadBody) {
                                chatStateHolder.updateMessageDraft(outgoingDraft)
                                appScope.launch {
                                    delay(100L)
                                    chatStateHolder.sendMessage()
                                }
                            } else {
                                chatStateHolder.sendMessage()
                            }
                            rootRoute = RootRoute.Chat
                            route = AppRoute.Chat
                            noteActionToast = if (shouldUsePayloadBody) {
                                "已填入并发送 AI 聊天草稿"
                            } else {
                                "已发送当前聊天草稿"
                            }
                        }
                    }
                }
            }
            AiAssistantActionKind.FillComposeDraft -> {
                val payload = aiAssistantActionPayload(action.payload)
                val mentionUsers = payload.mentions.mapNotNull(::resolveKnownAiAssistantUser)
                pendingAiAssistantComposeDraft = aiAssistantBodyWithMentions(payload.body, mentionUsers)
                val localChannel = payload.channel.takeIf { it.isNotBlank() }?.let { target ->
                    (automationKnownChannels() + channelState.channels + listOfNotNull(channelState.selectedChannel))
                        .distinctBy { it.id }
                        .firstOrNull { channel -> channel.aiAssistantMatchesTarget(target) }
                }
                rootRoute = if (localChannel != null) RootRoute.Discover else RootRoute.Timeline
                route = AppRoute.Compose(channelId = localChannel?.id)
                noteActionToast = if (mentionUsers.size < payload.mentions.size) {
                    "已填入发帖草稿，部分 @ 对象需要你手动确认"
                } else {
                    "已填入发帖草稿"
                }
            }
            AiAssistantActionKind.PublishComposeDraft -> {
                val payload = aiAssistantActionPayload(action.payload)
                if (payload.hasRouting) {
                    executed = executeAiAssistantTargetedComposeAction(action, payload)
                } else {
                    rootRoute = RootRoute.Timeline
                    route = AppRoute.Compose()
                    if (composeState.isSending || composeState.isResolvingVisibleUsers) {
                        noteActionToast = "发帖正在提交，稍后再试"
                        executed = false
                    } else {
                        val outgoingDraft = aiAssistantOutgoingDraftText(composeState.draft.text, payload.body)
                        val shouldUsePayloadBody = payload.body.isNotBlank() &&
                            composeState.draft.text.trim() != outgoingDraft
                        if (outgoingDraft.isBlank()) {
                            noteActionToast = "发帖框没有可发布的草稿"
                            executed = false
                        } else {
                            startAiAssistantAsyncAction(action.id)
                            pendingAiAssistantComposeSendAction = PendingAiAssistantObservedAction(action.id)
                            if (shouldUsePayloadBody) {
                                composeStateHolder.updateText(outgoingDraft)
                                appScope.launch {
                                    delay(100L)
                                    composeStateHolder.send()
                                }
                            } else {
                                composeStateHolder.send()
                            }
                            noteActionToast = if (shouldUsePayloadBody) {
                                "已填入并请求发布 AI 草稿"
                            } else {
                                "已请求发布当前发帖草稿"
                            }
                        }
                    }
                }
            }
            AiAssistantActionKind.MarkNotificationsRead -> {
                notificationStateHolder.markAllAsRead()
                rootRoute = RootRoute.Notifications
                route = AppRoute.Notifications
                noteActionToast = "已标记通知全部已读"
            }
            AiAssistantActionKind.OpenWebhookManagement -> {
                settingsStateHolder.openManagement(SettingsManagementSectionKey.Webhooks)
                openAiAssistantAppRoute(AppRoute.SettingsManagement(SettingsManagementSectionKey.Webhooks), "已打开 Webhook 管理")
            }
            AiAssistantActionKind.ReviewBulkOperation -> {
                rootRoute = RootRoute.Profile
                route = AppRoute.Settings
                noteActionToast = "已打开批量操作相关设置"
            }
            AiAssistantActionKind.ClearCurrentChatRoomMessages -> {
                executed = executeAiAssistantCurrentRoomManagementAction(
                    actionId = action.id,
                    actionName = "清空",
                    requireManager = true,
                    payload = action.payload,
                    successMessages = setOf("聊天室消息已清空"),
                ) { room ->
                    chatStateHolder.clearRoomMessages(room.id)
                }
            }
            AiAssistantActionKind.DeleteCurrentChatRoom -> {
                executed = executeAiAssistantCurrentRoomManagementAction(
                    actionId = action.id,
                    actionName = "删除",
                    requireManager = true,
                    payload = action.payload,
                    successMessages = setOf("聊天室已删除"),
                ) { room ->
                    chatStateHolder.deleteRoom(room.id)
                }
            }
            AiAssistantActionKind.LeaveCurrentChatRoom -> {
                executed = executeAiAssistantCurrentRoomManagementAction(
                    actionId = action.id,
                    actionName = "退出",
                    payload = action.payload,
                    successMessages = setOf("已退出聊天室"),
                ) { room ->
                    chatStateHolder.leaveRoom(room.id)
                }
            }
            AiAssistantActionKind.MuteCurrentChatRoom -> {
                executed = executeAiAssistantCurrentRoomManagementAction(
                    actionId = action.id,
                    actionName = "静音",
                    payload = action.payload,
                    successMessages = setOf("聊天室已静音"),
                ) { room ->
                    chatStateHolder.muteRoom(room.id, true)
                }
            }
            AiAssistantActionKind.UnmuteCurrentChatRoom -> {
                executed = executeAiAssistantCurrentRoomManagementAction(
                    actionId = action.id,
                    actionName = "取消静音",
                    payload = action.payload,
                    successMessages = setOf("已取消静音"),
                ) { room ->
                    chatStateHolder.muteRoom(room.id, false)
                }
            }
            AiAssistantActionKind.FavoriteCurrentNote -> executed = executeAiAssistantCurrentNoteAction(
                actionName = "收藏",
                payload = action.payload,
                execute = ::favoriteAiAssistantNote,
            )
            AiAssistantActionKind.UnfavoriteCurrentNote -> executed = executeAiAssistantCurrentNoteAction(
                actionName = "取消收藏",
                payload = action.payload,
                execute = ::unfavoriteAiAssistantNote,
            )
            AiAssistantActionKind.ReactCurrentNote -> executed = executeAiAssistantCurrentNoteAction(
                actionName = "点赞",
                payload = action.payload,
                execute = ::reactAiAssistantNote,
            )
            AiAssistantActionKind.DeleteReactionCurrentNote -> executed = executeAiAssistantCurrentNoteAction(
                actionName = "取消点赞",
                payload = action.payload,
                execute = ::deleteReactionAiAssistantNote,
            )
            AiAssistantActionKind.RenoteCurrentNote -> executed = executeAiAssistantCurrentNoteAction(
                actionName = "转发",
                payload = action.payload,
                execute = ::renoteAiAssistantNote,
            )
            AiAssistantActionKind.DeleteCurrentNote -> executed = executeAiAssistantCurrentNoteAction(
                actionName = "删除",
                payload = action.payload,
                openAfterAction = false,
                execute = ::deleteAiAssistantNote,
            )
            AiAssistantActionKind.ReviewCurrentPageAction -> {
                val target = aiAssistantReviewPageTarget(
                    currentRoute = route,
                    sourceRoute = aiAssistantSourceRoute,
                    sourceRootRoute = aiAssistantSourceRootRoute,
                )
                rootRoute = target.rootRoute
                route = target.route
                noteActionToast = "请在当前页面手动确认具体控件，助手不会静默点击未知写操作"
            }
            AiAssistantActionKind.AddMutedWord -> addAiResultMutedWord(action.payload)
            AiAssistantActionKind.CopyChecklist -> copyAiResultChecklist(action.payload)
            AiAssistantActionKind.OpenWebSearch -> openUrl(aiAssistantSearchUrl(action.payload))
            AiAssistantActionKind.SaveMemory -> {
                val memory = action.payload.trim()
                if (memory.isNotBlank()) {
                    aiStateHolder.updateSettings(
                        aiState.settings.copy(
                            assistantMemoryNotes = (aiState.settings.assistantMemoryNotes + memory)
                                .distinct()
                                .takeLast(20),
                        ),
                    )
                    noteActionToast = "已保存本地助手记忆"
                } else {
                    noteActionToast = "没有可保存的助手记忆"
                    executed = false
                }
            }
        }
        return executed
    }

    fun approveAiAssistantAction(actionId: String) {
        val action = findAiAssistantAction(actionId) ?: return
        if (action.status != AiAssistantActionStatus.Pending && action.status != AiAssistantActionStatus.Failed) return
        noteActionToast = null
        val executed = executeAiAssistantAction(action)
        val failureDetail = noteActionToast.orEmpty()
        updateAiAssistantActionStatus(
            actionId,
            aiAssistantStatusAfterExecution(actionId, executed, previousStatus = action.status),
            detail = failureDetail,
        )
    }

    fun autoApprovePendingAiAssistantActions() {
        val pendingActions = aiAssistantMessages.flatMap { message -> message.actions }
        autoApproveAiAssistantActions(pendingActions)
    }

    autoApproveAiAssistantActions = { actions ->
        val settings = aiAssistantAutoApprovalSettings
        val approvedActions = actions.filter { action -> action.canAutoApprove(settings) }
        approvedActions
            .forEach { action ->
                val pendingAction = findAiAssistantAction(action.id) ?: return@forEach
                if (pendingAction.status != AiAssistantActionStatus.Pending) return@forEach
                noteActionToast = null
                val executed = executeAiAssistantAction(pendingAction)
                val failureDetail = noteActionToast.orEmpty()
                updateAiAssistantActionStatus(
                    action.id,
                    aiAssistantStatusAfterExecution(action.id, executed, previousStatus = pendingAction.status),
                    detail = failureDetail,
                )
            }
        if (approvedActions.any { action ->
                action.risk == cc.hhhl.client.ui.screen.AiAssistantActionRisk.HighRisk &&
                    findAiAssistantAction(action.id)?.status in setOf(
                        AiAssistantActionStatus.Approved,
                        AiAssistantActionStatus.Running,
                    )
            }
        ) {
            noteActionToast = noteActionToast ?: "已自动执行高风险助手动作"
        }
    }

    LaunchedEffect(aiAssistantAutoApprovalSettings, aiAssistantMessages.map { message -> message.id to message.actions.map { action -> action.id to action.status } }) {
        autoApprovePendingAiAssistantActions()
    }

    fun rejectAiAssistantAction(actionId: String) {
        updateAiAssistantActionStatus(actionId, AiAssistantActionStatus.Rejected)
        noteActionToast = "已拒绝助手动作"
    }

    fun requestWorkspaceActionPlan() {
        val prompt = "根据当前工作区上下文给我一个可执行计划；涉及发送、发布、删除、清空、外部搜索等写操作时只列出待确认动作，不要假装已经执行。"
        if (route == AppRoute.AiAssistant) {
            requestAiAssistantReply(prompt, emptyList())
        } else {
            requestAiTask(
                kind = AiTaskKind.WorkspaceActionPlan,
                input = AiTaskInput(
                    title = "全局行动计划",
                    prompt = prompt,
                    automationEventText = aiAssistantContextText(),
                ),
                toast = "AI 已开始生成全局行动计划",
            )
        }
    }

    LaunchedEffect(aiAssistantMessages.size) {
        if (route == AppRoute.AiAssistant && aiAssistantMessages.isEmpty() && aiAssistantPendingPrompt == null) {
            requestWorkspaceActionPlan()
        }
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
    fun addAiRelatedNoteToWatchLater(text: String, notes: List<Note>) {
        val note = noteFromAiResult(text, notes)
        if (note == null) {
            noteActionToast = "没有可加入收藏的帖子"
            return
        }
        onFavoriteNote(note.id)
        noteActionToast = "已加入收藏"
    }
    fun addAiRelatedNotificationNoteToWatchLater(text: String, notifications: List<NotificationItem>) {
        val note = notificationNoteFromAiResult(text, notifications)
        if (note == null) {
            noteActionToast = "这条通知的帖子还没加载，先打开帖子后再加入收藏"
            return
        }
        onFavoriteNote(note.id)
        noteActionToast = "已加入收藏"
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
    val canEditNote: (Note) -> Boolean = { note ->
        note.author.id == accountUser?.id && !note.isRenote
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
    val onEditNote: (Note) -> Unit = { note ->
        if (canEditNote(note)) {
            composeStateHolder.startEditNote(note)
            route = AppRoute.Compose(editId = note.id)
        }
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
        onEdit = onEditNote,
        onDelete = onDeleteNote,
        onOpenMedia = onOpenUrl,
        onOpenMediaPreview = { mediaPreviewSession = it },
        onOpenMention = onOpenMention,
        onOpenHashtag = onOpenHashtag,
        onVotePoll = onVotePoll,
        isActionPending = isNoteActionPending,
        canEditNote = canEditNote,
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
        if (nestedBackHandler?.invoke() == true) {
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
        AppRoute.Discover -> {
            when {
                discoverState.selectedFederationInstance != null -> {
                    discoverStateHolder.closeFederationInstance()
                    true
                }
                discoverState.selectedRole != null -> {
                    discoverStateHolder.closeRoleDetail()
                    true
                }
                else -> false
            }
        }
        AppRoute.Notifications -> {
            false
        }
        AppRoute.Profile -> false
        AppRoute.Chat -> {
            if (chatState.selectedRoom != null || chatState.selectedUserConversation != null) {
                chatStateHolder.closeRoom()
                true
            } else {
                false
            }
        }
        AppRoute.AiAssistant -> {
            returnFromAiAssistant()
            true
        }
            AppRoute.ProfileNotes,
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
            AppRoute.Drive -> {
                when {
                    driveFilesState.selectedFile != null -> {
                        driveFilesStateHolder.clearSelectedFile()
                        true
                    }
                    driveFilesState.folderPath.isNotEmpty() -> {
                        driveFilesStateHolder.navigateUp()
                        true
                    }
                    else -> {
                        route = AppRoute.Profile
                        rootRoute = RootRoute.Profile
                        true
                    }
                }
            }
            AppRoute.AiSettings -> {
                route = AppRoute.Settings
                rootRoute = RootRoute.Profile
                true
            }
            AppRoute.ReleaseNotes -> {
                route = AppRoute.Settings
                rootRoute = RootRoute.Profile
                true
            }
            AppRoute.ThemeCustomization -> {
                route = AppRoute.Settings
                rootRoute = RootRoute.Profile
                true
            }
            AppRoute.Automation -> {
                if (automationState.editorOpen) {
                    automationStateHolder.closeEditor()
                    true
                } else {
                    route = AppRoute.Profile
                    rootRoute = RootRoute.Profile
                    true
                }
            }
            AppRoute.AutomationLogs -> {
                route = AppRoute.Automation
                rootRoute = RootRoute.Profile
                true
            }
            AppRoute.AdminDashboard -> {
                route = AppRoute.Settings
                rootRoute = RootRoute.Profile
                true
            }
            is AppRoute.SettingsManagement -> {
                if (settingsState.editingWebhook != null || settingsState.isWebhookEditorLoading) {
                    settingsStateHolder.closeWebhookEditor()
                    true
                } else {
                    settingsStateHolder.closeManagement()
                    route = AppRoute.Settings
                    rootRoute = RootRoute.Profile
                    true
                }
            }
            AppRoute.Channels,
            AppRoute.Pages,
            AppRoute.Gallery,
            AppRoute.Flash,
            AppRoute.Announcements -> {
                when {
                    route == AppRoute.Pages && pageState.editingDraft != null -> {
                        pageStateHolder.cancelEditingPage()
                        return true
                    }
                    route == AppRoute.Pages && pageState.selectedPage != null -> {
                        pageStateHolder.closeDetail()
                        return true
                    }
                    route == AppRoute.Gallery && galleryState.selectedPost != null -> {
                        galleryStateHolder.closeDetail()
                        return true
                    }
                    route == AppRoute.Flash && flashState.draftMode != null -> {
                        flashStateHolder.cancelDraft()
                        return true
                    }
                    route == AppRoute.Flash && flashState.selectedFlash != null -> {
                        flashStateHolder.closeDetail()
                        return true
                    }
                    route == AppRoute.Announcements && announcementState.selectedAnnouncement != null -> {
                        announcementStateHolder.closeDetail()
                        return true
                    }
                    route == AppRoute.Announcements && announcementState.isManaging -> {
                        announcementStateHolder.exitManagement()
                        return true
                    }
                }
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
            is AppRoute.NoteDetail -> {
                route = AppRoute.Timeline
                rootRoute = RootRoute.Timeline
                true
            }
            is AppRoute.Compose -> {
                val editId = current.editId?.takeIf { it.isNotBlank() }
                if (editId != null) {
                    route = AppRoute.NoteDetail(editId)
                } else {
                    route = AppRoute.Timeline
                    rootRoute = RootRoute.Timeline
                }
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
    val aiAssistantRouteState = AiAssistantRouteState(
        messages = aiAssistantMessages,
        draft = aiAssistantDraft,
        contextSummary = aiAssistantContextSummary(),
        aiState = aiState,
        isProcessing = aiAssistantPendingPrompt != null,
        attachments = aiAssistantAttachments,
        isUploadingAttachment = aiAssistantAttachmentUploading,
        mediaPicker = mediaPicker,
        customEmojis = noteActionState.customEmojis,
        recentEmojiCodes = noteActionState.recentReactions,
        autoApprovalSettings = aiAssistantAutoApprovalSettings,
    )
    val aiAssistantRouteActions = AiAssistantRouteActions(
        onDraftChanged = ::updateAiAssistantDraft,
        onSend = ::sendAiAssistantDraft,
        onSendPrompt = ::sendAiAssistantPrompt,
        onRetry = ::retryAiAssistantPrompt,
        onNewConversation = ::clearAiAssistantConversation,
        onUploadAttachment = ::uploadAiAssistantAttachment,
        onUploadAttachmentError = ::reportAiAssistantAttachmentError,
        onOpenDrivePicker = ::openAiAssistantDrivePicker,
        onRemoveAttachment = ::removeAiAssistantAttachment,
        onOpenAttachmentUrl = openUrl,
        onOpenAutomation = ::openAiAssistantAutomation,
        onAutoApprovalSettingsChanged = ::updateAiAssistantAutoApprovalSettings,
        onApproveAction = ::approveAiAssistantAction,
        onRejectAction = ::rejectAiAssistantAction,
        onBack = ::returnFromAiAssistant,
    )

    CompositionLocalProvider(
        LocalCustomEmojiUrls provides noteActionState.customEmojiUrls,
        LocalBlockedNoteAuthorIds provides blockedUserIds,
        LocalMutedNoteFilters provides mutedNoteFilters,
        LocalNoteRowGesturesEnabled provides listGesturesEnabled,
        LocalUserQuickActions provides userQuickActions,
        LocalNoteRowActions provides NoteRowActions(
            onShareNote = { url -> shareUrl(url) },
            onEditNote = noteCallbacks.onEdit,
            canEditNote = noteCallbacks.canEditNote,
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
                    onCopyAiResult = ::copyAiResultChecklist,
                    onAddAiMutedWord = ::addAiResultMutedWord,
                    onAddAiRelatedNoteToWatchLater = ::addAiRelatedNoteToWatchLater,
                    onOpenAiRelatedNote = ::openAiRelatedNote,
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
                    onOpenChannel = { channel ->
                        channelStateHolder.openDiscoveredChannel(channel)
                        route = AppRoute.Channels
                    },
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
                    onOpenAiAssistant = ::openAiAssistantFromCurrentContext,
                    aiEnabled = aiState.hasUsableModel,
                    isAiProcessing = aiState.isProcessing,
                    aiResultText = latestAiResultFor(
                        AiTaskKind.ChatSummary,
                        AiTaskKind.ChatRecentSummary,
                        AiTaskKind.ChatTodaySummary,
                        AiTaskKind.ChatUnreadSummary,
                        AiTaskKind.ChatReplyDraft,
                        AiTaskKind.ChatActionItems,
                        AiTaskKind.ChatDecisionSummary,
                    )?.resultText,
                    aiResultLabel = latestAiResultFor(
                        AiTaskKind.ChatSummary,
                        AiTaskKind.ChatRecentSummary,
                        AiTaskKind.ChatTodaySummary,
                        AiTaskKind.ChatUnreadSummary,
                        AiTaskKind.ChatReplyDraft,
                        AiTaskKind.ChatActionItems,
                        AiTaskKind.ChatDecisionSummary,
                    )?.kind?.label,
                    onAiAction = ::requestChatAi,
                    onCopyAiResult = ::copyAiResultChecklist,
                    onAddAiMutedWord = ::addAiResultMutedWord,
                    onCreateAutomationFromAiResult = ::requestAutomationDraftFromAiResult,
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                    onFavoriteMessage = { type, conversationId, conversationTitle, message ->
                        favoriteNoteStateHolder.addFavoriteMessage(
                            conversationType = type,
                            conversationId = conversationId,
                            conversationTitle = conversationTitle,
                            message = message,
                        )
                        noteActionToast = "已收藏信息"
                    },
                    onBackHandlerChanged = { handler -> nestedBackHandler = handler },
                )
                AppRoute.AiAssistant -> AiAssistantRouteContent(
                    state = aiAssistantRouteState,
                    actions = aiAssistantRouteActions,
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
                    onCopyAiResult = ::copyAiResultChecklist,
                    onAddAiMutedWord = ::addAiResultMutedWord,
        onAddAiRelatedNoteToWatchLater = ::addAiRelatedNotificationNoteToWatchLater,
        onOpenAiRelatedNote = ::openAiRelatedNotificationNote,
        onDismissAiResult = aiStateHolder::consumeLatestResult,
        onBackHandlerChanged = { handler -> nestedBackHandler = handler },
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
                    onCopyAiResult = ::copyAiResultChecklist,
                    onAddAiMutedWord = ::addAiResultMutedWord,
                    onAddAiRelatedNoteToWatchLater = ::addAiRelatedNoteToWatchLater,
                    onOpenAiRelatedNote = ::openAiRelatedNote,
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
                    onChangeAvatar = {
                        val picker = mediaPicker
                        if (picker == null) {
                            userProfileStateHolder.showProfileEditError("当前设备不支持选择图片")
                        } else {
                            picker.pickSingleImage(
                                onPicked = userProfileStateHolder::setPendingAvatar,
                                onError = userProfileStateHolder::showProfileEditError,
                            )
                        }
                    },
                    onTakePhoto = {
                        val picker = mediaPicker
                        if (picker == null) {
                            userProfileStateHolder.showProfileEditError("当前设备不支持拍照")
                        } else {
                            picker.takePhoto(
                                onPicked = userProfileStateHolder::setPendingAvatar,
                                onError = userProfileStateHolder::showProfileEditError,
                            )
                        }
                    },
                    onSelectPresetAvatar = { avatarUrl ->
                        appScope.launch {
                            try {
                                // URL 白名单校验：仅允许预设头像域名，防止 SSRF
                                val allowedPrefix = "${SharkeyAuthApi.DEFAULT_BASE_URL}/"
                                if (!avatarUrl.startsWith(allowedPrefix)) {
                                    userProfileStateHolder.showProfileEditError("仅支持从预设头像库选择")
                                    return@launch
                                }

                                // 使用 use 块确保 HttpClient 在所有路径下正确关闭
                                HttpClient().use { httpClient ->
                                    val response = httpClient.get(avatarUrl)
                                    if (response.status.value !in 200..299) {
                                        userProfileStateHolder.showProfileEditError("下载头像失败：${response.status.value}")
                                        return@use
                                    }

                                    // Content-Type 校验：从响应头获取，防止内容投毒
                                    val responseContentType = response.headers["Content-Type"]?.lowercase() ?: ""
                                    if (!responseContentType.startsWith("image/")) {
                                        userProfileStateHolder.showProfileEditError("下载的内容不是有效图片")
                                        return@use
                                    }

                                    val bytes = response.bodyAsBytes()

                                    // 文件大小校验
                                    if (bytes.size > AVATAR_MAX_FILE_SIZE_BYTES) {
                                        val maxSizeMB = AVATAR_MAX_FILE_SIZE_BYTES / (1024 * 1024)
                                        userProfileStateHolder.showProfileEditError("头像文件过大（最大 ${maxSizeMB}MB）")
                                        return@use
                                    }

                                    val fileName = avatarUrl.substringAfterLast("/")
                                    val upload = DriveFileUpload(
                                        bytes = bytes,
                                        fileName = fileName,
                                        contentType = responseContentType,
                                    )
                                    userProfileStateHolder.setPendingAvatar(upload)
                                }
                            } catch (e: Exception) {
                                userProfileStateHolder.showProfileEditError("下载头像失败：${e.message ?: "未知错误"}")
                            }
                        }
                    },
                    pendingAvatarUpload = userProfileStateHolder.state.value.pendingAvatarUpload,
                    onConfirmAvatar = userProfileStateHolder::confirmPendingAvatar,
                    onCancelAvatar = userProfileStateHolder::cancelPendingAvatar,
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
                AppRoute.Settings -> MainShellSettingsRoute(
                    settingsState = settingsState,
                    settingsStateHolder = settingsStateHolder,
                    instanceMetaState = instanceMetaState,
                    accounts = accounts,
                    currentAccountId = currentAccountId,
                    appScope = appScope,
                    mediaPicker = mediaPicker,
                    chatMessageCache = chatMessageCache,
                    chatUnreadStore = chatUnreadStore,
                    openUrl = openUrl,
                    onNavigate = { route = it },
                    onThemeSelected = onThemeSelected,
                    customTheme = customTheme,
                    onCustomThemeChanged = onCustomThemeChanged,
                    onResetCustomTheme = onResetCustomTheme,
                    onSetGlobalBackgroundImage = onSetGlobalBackgroundImage,
                    onClearGlobalBackgroundImage = onClearGlobalBackgroundImage,
                    onSetChatBackgroundImage = onSetChatBackgroundImage,
                    onClearChatBackgroundImage = onClearChatBackgroundImage,
                    onTimelineDensitySelected = onTimelineDensitySelected,
                    onListGesturesEnabledChanged = onListGesturesEnabledChanged,
                    onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
                    onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
                    onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
                    onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
                    onChatNoiseReductionSettingsChanged = onChatNoiseReductionSettingsChanged,
                    onCheckForUpdates = onCheckForUpdates,
                    appVersionName = appVersionName,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                    onBackHandlerChanged = onBackHandlerChanged,
                    onSwitchAccount = onSwitchAccount,
                    onRemoveAccount = onRemoveAccount,
                    onAddAccount = onAddAccount,
                    aiState = aiState,
                    aiStateHolder = aiStateHolder,
                    onAiWorkspacePlan = ::requestWorkspaceActionPlan,
                )
                AppRoute.AiSettings -> AiSettingsScreen(
                    state = settingsState,
                    onBack = { route = AppRoute.Settings },
                    onAiSettingsChanged = aiStateHolder::updateSettings,
                    onAiProviderSelected = aiStateHolder::applyProviderPreset,
                    onTestAiConnection = aiStateHolder::testConnection,
                    onAiWorkspacePlan = ::requestWorkspaceActionPlan,
                    aiConnectionMessage = aiState.message ?: aiState.errorMessage,
                    isTestingAiConnection = aiState.isTestingConnection,
                )
                AppRoute.ReleaseNotes -> ReleaseNotesTimelineScreen(
                    notes = releaseNotesTimeline(),
                    currentVersionName = appVersionName,
                    onBack = { route = AppRoute.Settings },
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
                    recentChatMessages = chatState.messages.takeLast(8).reversed(),
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
                    onUpdateBurstLimit = automationStateHolder::updateRuleBurstLimit,
                    onAddCondition = automationStateHolder::addCondition,
                    onUpdateCondition = automationStateHolder::updateCondition,
                    onRemoveCondition = automationStateHolder::removeCondition,
                    onAddAction = automationStateHolder::addAction,
                    onUpdateAction = automationStateHolder::updateAction,
                    onRemoveAction = automationStateHolder::removeAction,
                    onClearLogs = automationStateHolder::clearLogs,
                    onClearDebugRecords = automationStateHolder::clearDebugRecords,
                    onSimulateChatMessage = { message ->
                        val currentUser = accountUser?.toDomainUser(host = currentAccountHost)
                        val attentionKind = message.automationChatAttentionKind(
                            currentUser = currentUser,
                            specialCareUserIds = specialCareState.userIds,
                        )
                        automationStateHolder.simulate(
                            message.toAutomationChatEvent(
                                roomId = chatState.selectedRoom?.id ?: message.roomId,
                                roomName = chatState.selectedRoom?.name.orEmpty(),
                                directUserId = chatState.selectedUserConversation?.user?.id,
                                attentionKind = attentionKind?.name.orEmpty(),
                                currentUser = currentUser,
                                isAiGenerated = message.automationMessageKey() in automationAiGeneratedMessageKeys,
                            ),
                        )
                    },
                    onOpenLogs = { route = AppRoute.AutomationLogs },
                    onApproveRuleDraft = automationStateHolder::approveRuleDraft,
                    onRejectRuleDraft = automationStateHolder::rejectRuleDraft,
                    latestAiResultFor = { kinds -> latestAiResultFor(*kinds) },
                    onAiExplainRule = ::requestAutomationExplain,
                    onAiSuggestRules = ::requestAutomationRuleSuggestions,
                    onAiCreateRule = ::requestAutomationRuleDraft,
                    onCopyAiResult = ::copyAiResultChecklist,
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                )
                AppRoute.AutomationLogs -> AutomationExecutionLogScreen(
                    logs = automationState.logs,
                    onBack = { route = AppRoute.Automation },
                    onOpenRule = { ruleId ->
                        route = AppRoute.Automation
                        automationStateHolder.openRule(ruleId)
                    },
                    onRetryLog = automationStateHolder::retryLog,
                    onCopyLog = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        noteActionToast = "已复制日志"
                    },
                    onClearLogs = automationStateHolder::clearLogs,
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
                    onCopyAiResult = ::copyAiResultChecklist,
                    onAddAiMutedWord = ::addAiResultMutedWord,
                    onAddAiRelatedNoteToWatchLater = ::addAiRelatedNoteToWatchLater,
                    onOpenAiRelatedNote = ::openAiRelatedNote,
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
                    onCopyAiResult = ::copyAiResultChecklist,
                    onAddAiRelatedNoteToWatchLater = { note ->
                        onFavoriteNote(note.id)
                        noteActionToast = "已加入收藏"
                    },
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
                        !current.editId.isNullOrBlank() -> findLoadedNote(current.editId)
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
                    onCopyAiResult = ::copyAiResultChecklist,
                    onDismissAiResult = aiStateHolder::consumeLatestResult,
                    onBack = {
                        route = current.editId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { AppRoute.NoteDetail(it) }
                            ?: AppRoute.Timeline
                    },
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
                            DrivePickerTarget.AiAssistant -> attachAiAssistantFile(file)
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
                    onOpenTimeline = {
                        pendingReleaseNotes = null
                        runCatching { releaseNotesStore.saveLastShownVersion(notes.versionName) }
                        rootRoute = RootRoute.Profile
                        route = AppRoute.ReleaseNotes
                    },
                    onDismiss = {
                        pendingReleaseNotes = null
                        runCatching { releaseNotesStore.saveLastShownVersion(notes.versionName) }
                    },
                )
            }
            if (aiState.settings.floatingAssistantEnabled && route != AppRoute.AiAssistant) {
                AiAssistantFloatingOrb(
                    visible = true,
                    aiEnabled = aiState.hasUsableModel,
                    isProcessing = aiAssistantPendingPrompt != null || aiState.isProcessing,
                    speechInputAvailable = speechTextInput != null,
                    autoApprovalSettings = aiAssistantAutoApprovalSettings,
                    onVoiceInput = ::requestAiAssistantVoiceInput,
                    onOpenAssistant = ::openAiAssistantFromCurrentContext,
                    onAutoApprovalSettingsChanged = ::updateAiAssistantAutoApprovalSettings,
                    onVisibilityChanged = { visible ->
                        aiStateHolder.updateSettings(
                            aiState.settings.copy(floatingAssistantEnabled = visible),
                        )
                        if (!visible) {
                            noteActionToast = "已隐藏 AI 小光球，可在设置里重新打开"
                        }
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
