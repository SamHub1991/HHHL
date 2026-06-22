package cc.hhhl.client.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.HhhlAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import coil3.compose.AsyncImage
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.USER_PROFILE_DESCRIPTION_MAX_LENGTH
import cc.hhhl.client.api.USER_PROFILE_NAME_MAX_LENGTH
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserSocialKind
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import cc.hhhl.client.state.AVATAR_DAILY_UPLOAD_LIMIT
import cc.hhhl.client.state.UserProfileUiState
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AiResultCommonActionChips
import cc.hhhl.client.ui.component.AiResultPanel
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlProgressIndicator
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity
import cc.hhhl.client.ui.component.ThemePicker
import cc.hhhl.client.ui.component.TimelineDensityPicker

internal val ProfileQuickActionsHorizontalPadding = 18.dp
internal val ProfileQuickActionsCardInnerPadding = 10.dp
internal val ProfilePrimaryShortcutTileHeight = 58.dp
internal val ProfileWorkspaceShortcutTileHeight = 52.dp
internal val ProfileShortcutTileCornerRadius = 14.dp
internal val ProfileShortcutIconContainerSize = 30.dp
internal val ProfileShortcutIconSize = 17.dp
/** 客户端静态资源基础 URL */
private const val ClientAssetsBaseUrl = "https://dc.hhhl.cc/client-assets"

private const val ProfileDefaultBannerImageUrl = "$ClientAssetsBaseUrl/icon.png"

/**
 * 预设头像库
 * 提供系统默认的头像选项
 */
private val PresetAvatarUrls = listOf(
    "$ClientAssetsBaseUrl/avatars/avatar1.png",
    "$ClientAssetsBaseUrl/avatars/avatar2.png",
    "$ClientAssetsBaseUrl/avatars/avatar3.png",
    "$ClientAssetsBaseUrl/avatars/avatar4.png",
    "$ClientAssetsBaseUrl/avatars/avatar5.png",
    "$ClientAssetsBaseUrl/avatars/avatar6.png",
    "$ClientAssetsBaseUrl/avatars/avatar7.png",
    "$ClientAssetsBaseUrl/avatars/avatar8.png",
    "$ClientAssetsBaseUrl/avatars/avatar9.png",
    "$ClientAssetsBaseUrl/avatars/avatar10.png",
    "$ClientAssetsBaseUrl/avatars/avatar11.png",
    "$ClientAssetsBaseUrl/avatars/avatar12.png",
)

@Composable
fun ProfileScreen(
    state: UserProfileUiState? = null,
    onRefresh: () -> Unit = {},
    onLoadMoreNotes: () -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onReply: (String) -> Unit = {},
    onRenote: (String) -> Unit = {},
    onQuote: (String) -> Unit = {},
    onReact: (String, String) -> Unit = { _, _ -> },
    onDeleteReaction: (String, String) -> Unit = { _, _ -> },
    onFavorite: (String) -> Unit = {},
    onAddToClip: ((Note) -> Unit)? = null,
    onDelete: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    onVotePoll: (String, Int) -> Unit = { _, _ -> },
    reactionOptions: List<String> = emptyList(),
    recentReactions: List<String> = emptyList(),
    isActionPending: (String) -> Boolean = { false },
    canDeleteAuthor: (String) -> Boolean = { false },
    noteRowDensity: NoteRowDensity = NoteRowDensity.Comfortable,
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    aiResultText: String? = null,
    aiResultLabel: String? = null,
    onAiProfileSummary: () -> Unit = {},
    onAiProfileSuggestions: () -> Unit = {},
    onCopyAiResult: ((String) -> Unit)? = null,
    onAddAiMutedWord: ((String) -> Unit)? = null,
    onAddAiRelatedNoteToWatchLater: ((String, List<Note>) -> Unit)? = null,
    onOpenAiRelatedNote: ((String, List<Note>) -> Unit)? = null,
    onDismissAiResult: () -> Unit = {},
    selectedTheme: HhhlThemePreset = HhhlThemePreset.System,
    selectedTimelineDensity: TimelineDensity = TimelineDensity.Comfortable,
    title: String = "我的",
    isOwnProfile: Boolean = true,
    capabilities: InstanceCapabilities = InstanceCapabilities(),
    onFollowToggle: () -> Unit = {},
    onMuteToggle: () -> Unit = {},
    onBlockToggle: () -> Unit = {},
    onReportUser: () -> Unit = {},
    onOpenChatWithUser: (User) -> Unit = {},
    isSpecialCareUser: (String) -> Boolean = { false },
    onToggleSpecialCareUser: ((String) -> Boolean)? = null,
    onUpdateProfile: (String, String) -> Unit = { _, _ -> },
    onChangeBanner: (() -> Unit)? = null,
    onChangeAvatar: (() -> Unit)? = null,
    onSelectPresetAvatar: ((String) -> Unit)? = null,
    onTakePhoto: (() -> Unit)? = null,
    pendingAvatarUpload: DriveFileUpload? = null,
    onConfirmAvatar: () -> Unit = {},
    onCancelAvatar: () -> Unit = {},
    onOpenSocial: (UserSocialKind) -> Unit = {},
    onOpenDrive: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAutomation: () -> Unit = {},
    onOpenRelationshipManagement: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenFavoriteNotes: () -> Unit = {},
    onOpenLists: () -> Unit = {},
    onOpenFollowRequests: () -> Unit = {},
    onOpenAntennas: () -> Unit = {},
    onOpenClips: () -> Unit = {},
    onOpenChannels: () -> Unit = {},
    onOpenPages: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onOpenFlash: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
    onOpenProfileNotes: () -> Unit = {},
    onThemeSelected: (HhhlThemePreset) -> Unit = {},
    onTimelineDensitySelected: (TimelineDensity) -> Unit = {},
    onClearMessage: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val user = state?.user
    var profileEditorOpen by remember(user?.id) { mutableStateOf(false) }
    var profileEditSubmitted by remember(user?.id) { mutableStateOf(false) }
    val profileTimelineNotes = remember(user?.pinnedNotes, state?.notes) {
        filteredProfileTimelineNotes(user, state?.notes.orEmpty())
    }

    LaunchedEffect(state?.isProfileSaving, state?.profileEditErrorMessage) {
        if (
            profileEditSubmitted &&
            state?.isProfileSaving == false &&
            state.profileEditErrorMessage == null
        ) {
            profileEditorOpen = false
            profileEditSubmitted = false
        }
    }

    // 头像预览确认对话框：选图后先预览，用户确认后才上传
    if (pendingAvatarUpload != null) {
        AvatarPreviewConfirmDialog(
            upload = pendingAvatarUpload,
            onConfirm = onConfirmAvatar,
            onCancel = onCancelAvatar,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (onBack != null) {
            HhhlTopBar(
                title = title,
                supportingText = user?.let { "@${it.username}" },
                navigation = {
                    HhhlBackButton(onClick = onBack)
                },
            )
            HhhlDivider()
        }
        LazyColumn {
            if (state?.isLoading == true && user == null) {
                item(key = "profile-loading", contentType = "profile-status") {
                    ProfileStatusRow(text = "正在加载资料...", loading = true)
                }
            }
            state?.errorMessage?.let { message ->
                item(key = "profile-error", contentType = "profile-status") {
                    ProfileStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            state?.message?.let { message ->
                item(key = "profile-message", contentType = "profile-status") { ProfileStatusRow(text = message) }
            }
            item(key = "profile-header-${user?.id.orEmpty()}", contentType = "profile-header") {
                if (user != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileHeaderIdentity(
                            user = user,
                            isOwnProfile = isOwnProfile,
                            isMuted = state?.relationship?.isMuted == true,
                            isBlocking = state?.relationship?.isBlocking == true,
                            isSpecialCare = isSpecialCareUser(user.id),
                            isChanging = state?.isRelationshipChanging == true,
                            isProfileSaving = state?.isProfileSaving == true,
                            canChangeBanner = isOwnProfile && onChangeBanner != null,
                            canChangeAvatar = isOwnProfile && onChangeAvatar != null,
                            onFollowToggle = onFollowToggle,
                            onMuteToggle = onMuteToggle,
                            onBlockToggle = onBlockToggle,
                            onReportUser = onReportUser,
                            onOpenChatWithUser = { onOpenChatWithUser(user) },
                            onToggleSpecialCare = onToggleSpecialCareUser?.let { toggle ->
                                { toggle(user.id) }
                            },
                            onEditProfile = {
                                onClearMessage()
                                profileEditorOpen = true
                            },
                            onChangeBanner = onChangeBanner,
                            onChangeAvatar = onChangeAvatar,
                            onSelectPresetAvatar = onSelectPresetAvatar,
                            onTakePhoto = onTakePhoto,
                            avatarUploadCooldownSeconds = state?.avatarUploadCooldownSeconds ?: 0,
                            avatarDailyUploadRemaining = state?.avatarDailyUploadRemaining ?: 5,
                        )
                        if (user.bio.isNotBlank()) {
                            InlineRichText(
                                text = user.bio,
                                color = LocalHhhlColors.current.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 18.dp),
                                onOpenMention = onOpenMention,
                                onOpenHashtag = onOpenHashtag,
                            )
                        }
                        ProfileSocialStatsRow(user = user, onOpenSocial = onOpenSocial)
                        if (!isOwnProfile) {
                            ProfileAiActions(
                                enabled = aiEnabled,
                                isProcessing = isAiProcessing,
                                onSummary = onAiProfileSummary,
                                onSuggestions = onAiProfileSuggestions,
                            )
                        }
                        if (!aiResultText.isNullOrBlank()) {
                            ProfileAiResultPanel(
                                label = aiResultLabel ?: "AI 资料",
                                text = aiResultText,
                                notes = (user.pinnedNotes + state.notes).distinctBy { it.id },
                                onCopyAiResult = onCopyAiResult,
                                onAddAiMutedWord = onAddAiMutedWord,
                                onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
                                onOpenAiRelatedNote = onOpenAiRelatedNote,
                                onDismiss = onDismissAiResult,
                            )
                        }
                        if (isOwnProfile) {
                            ProfileQuickActions(
                                capabilities = capabilities,
                                selectedTheme = selectedTheme,
                                selectedTimelineDensity = selectedTimelineDensity,
                                profilePostsText = profilePostsShortcutText(user, state),
                                onRefresh = onRefresh,
                                onOpenProfileNotes = onOpenProfileNotes,
                                onOpenDrive = onOpenDrive,
                                onOpenSettings = onOpenSettings,
                                onOpenAutomation = onOpenAutomation,
                                onOpenFlash = onOpenFlash,
                                onOpenAnnouncements = onOpenAnnouncements,
                                onOpenRelationshipManagement = onOpenRelationshipManagement,
                                onOpenAchievements = onOpenAchievements,
                                onOpenFavoriteNotes = onOpenFavoriteNotes,
                                onOpenFollowRequests = onOpenFollowRequests,
                                onOpenLists = onOpenLists,
                                onOpenClips = onOpenClips,
                                onOpenAntennas = onOpenAntennas,
                                onOpenChannels = onOpenChannels,
                                onOpenPages = onOpenPages,
                                onOpenGallery = onOpenGallery,
                                onThemeSelected = onThemeSelected,
                                onTimelineDensitySelected = onTimelineDensitySelected,
                                aiEnabled = aiEnabled,
                                isAiProcessing = isAiProcessing,
                                onAiProfileSummary = onAiProfileSummary,
                                onAiProfileSuggestions = onAiProfileSuggestions,
                                onLogout = onLogout,
                            )
                        }
                    }
                    HhhlDivider()
                }
            }
            if (!isOwnProfile) {
                profileNoteItems(
                    state = state,
                    user = user,
                    timelineNotes = profileTimelineNotes,
                    onRefresh = onRefresh,
                    onLoadMoreNotes = onLoadMoreNotes,
                    onOpenNote = onOpenNote,
                    onOpenUser = onOpenUser,
                    onReply = onReply,
                    onRenote = onRenote,
                    onQuote = onQuote,
                    onReact = onReact,
                    onDeleteReaction = onDeleteReaction,
                    onFavorite = onFavorite,
                    onAddToClip = onAddToClip,
                    onDelete = onDelete,
                    onOpenMedia = onOpenMedia,
                    onOpenMediaPreview = onOpenMediaPreview,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                    onVotePoll = onVotePoll,
                    reactionOptions = reactionOptions,
                    recentReactions = recentReactions,
                    isActionPending = isActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                    showProfileLoadingWhenUserMissing = false,
                )
            }
        }
    }

    if (profileEditorOpen && user != null) {
        ProfileEditDialog(
            user = user,
            isSaving = state?.isProfileSaving == true,
            errorMessage = state?.profileEditErrorMessage,
            onDismiss = {
                profileEditorOpen = false
                profileEditSubmitted = false
            },
            onSubmit = { name, description ->
                profileEditSubmitted = true
                onUpdateProfile(name, description)
            },
        )
    }
}

@Composable
fun ProfileNotesScreen(
    state: UserProfileUiState? = null,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onLoadMoreNotes: () -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onReply: (String) -> Unit = {},
    onRenote: (String) -> Unit = {},
    onQuote: (String) -> Unit = {},
    onReact: (String, String) -> Unit = { _, _ -> },
    onDeleteReaction: (String, String) -> Unit = { _, _ -> },
    onFavorite: (String) -> Unit = {},
    onAddToClip: ((Note) -> Unit)? = null,
    onDelete: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    onVotePoll: (String, Int) -> Unit = { _, _ -> },
    reactionOptions: List<String> = emptyList(),
    recentReactions: List<String> = emptyList(),
    isActionPending: (String) -> Boolean = { false },
    canDeleteAuthor: (String) -> Boolean = { false },
    noteRowDensity: NoteRowDensity = NoteRowDensity.Comfortable,
) {
    val user = state?.user
    val profileTimelineNotes = remember(user?.pinnedNotes, state?.notes) {
        filteredProfileTimelineNotes(user, state?.notes.orEmpty())
    }
    val listState = rememberLazyListState()
    var lastAutoLoadNoteCount by remember(user?.id) { mutableStateOf(0) }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = profileTimelineNotes.size,
        isLoadingMore = state?.isLoadingMoreNotes == true || profileTimelineNotes.isEmpty(),
        onLoadMore = {
            if (profileTimelineNotes.size != lastAutoLoadNoteCount) {
                lastAutoLoadNoteCount = profileTimelineNotes.size
                onLoadMoreNotes()
            }
        },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "我的帖子",
            supportingText = profilePostsScreenText(user, state),
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlIconActionButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = if (state?.isLoadingNotes == true) "同步中" else "刷新帖子",
                    emphasized = true,
                    enabled = state?.isLoadingNotes != true,
                    onClick = onRefresh,
                )
            },
        )
        HhhlDivider()
        LazyColumn(state = listState) {
            profileNoteItems(
                state = state,
                user = user,
                timelineNotes = profileTimelineNotes,
                onRefresh = onRefresh,
                onLoadMoreNotes = onLoadMoreNotes,
                onOpenNote = onOpenNote,
                onOpenUser = onOpenUser,
                onReply = onReply,
                onRenote = onRenote,
                onQuote = onQuote,
                onReact = onReact,
                onDeleteReaction = onDeleteReaction,
                onFavorite = onFavorite,
                onAddToClip = onAddToClip,
                onDelete = onDelete,
                onOpenMedia = onOpenMedia,
                onOpenMediaPreview = onOpenMediaPreview,
                onOpenMention = onOpenMention,
                onOpenHashtag = onOpenHashtag,
                onVotePoll = onVotePoll,
                reactionOptions = reactionOptions,
                recentReactions = recentReactions,
                isActionPending = isActionPending,
                canDeleteAuthor = canDeleteAuthor,
                noteRowDensity = noteRowDensity,
                showProfileLoadingWhenUserMissing = true,
            )
        }
    }
}

private fun LazyListScope.profileNoteItems(
    state: UserProfileUiState?,
    user: User?,
    timelineNotes: List<Note>,
    onRefresh: () -> Unit,
    onLoadMoreNotes: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReply: (String) -> Unit,
    onRenote: (String) -> Unit,
    onQuote: (String) -> Unit,
    onReact: (String, String) -> Unit,
    onDeleteReaction: (String, String) -> Unit,
    onFavorite: (String) -> Unit,
    onAddToClip: ((Note) -> Unit)?,
    onDelete: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onVotePoll: (String, Int) -> Unit,
    reactionOptions: List<String>,
    recentReactions: List<String>,
    isActionPending: (String) -> Boolean,
    canDeleteAuthor: (String) -> Boolean,
    noteRowDensity: NoteRowDensity,
    showProfileLoadingWhenUserMissing: Boolean,
) {
    if (state == null) {
        if (showProfileLoadingWhenUserMissing) {
            item(key = "profile-notes-state-loading", contentType = "profile-status") {
                ProfileStatusRow(text = "正在加载资料...", loading = true)
            }
        }
        return
    }

    if (user == null) {
        if (showProfileLoadingWhenUserMissing) {
            item(key = "profile-notes-user-loading", contentType = "profile-status") {
                ProfileStatusRow(text = "正在加载资料...", loading = true)
            }
        }
        return
    }

    val hasPinnedNotes = user.pinnedNotes.isNotEmpty()
    if (hasPinnedNotes) {
        item(key = "profile-pinned-title-${user.id}", contentType = "profile-status") { ProfileStatusRow(text = "置顶") }
        items(
            items = user.pinnedNotes,
            key = { "profile-pinned-${it.id}" },
            contentType = { "profile-pinned-note" },
        ) { note ->
            ProfileNoteRow(
                note = note,
                onOpenNote = onOpenNote,
                onOpenUser = onOpenUser,
                onReply = onReply,
                onRenote = onRenote,
                onQuote = onQuote,
                onReact = onReact,
                onDeleteReaction = onDeleteReaction,
                onFavorite = onFavorite,
                onAddToClip = onAddToClip,
                onDelete = onDelete,
                onOpenMedia = onOpenMedia,
                onOpenMediaPreview = onOpenMediaPreview,
                onOpenMention = onOpenMention,
                onOpenHashtag = onOpenHashtag,
                onVotePoll = onVotePoll,
                reactionOptions = reactionOptions,
                recentReactions = recentReactions,
                isActionPending = isActionPending,
                canDeleteAuthor = canDeleteAuthor,
                noteRowDensity = noteRowDensity,
            )
        }
    }
    if (state.isLoadingNotes && state.notes.isEmpty()) {
        item(key = "profile-notes-loading-${user.id}", contentType = "profile-status") {
            ProfileStatusRow(text = "正在加载帖子...", loading = true)
        }
    }
    state.notesErrorMessage?.let { message ->
        item(key = "profile-notes-error-${user.id}", contentType = "profile-status") {
            ProfileStatusRow(
                text = message,
                actionText = "重试",
                onAction = onRefresh,
            )
        }
    }
    if (!state.isLoadingNotes && state.notes.isEmpty() && !hasPinnedNotes && state.notesErrorMessage == null) {
        item(key = "profile-notes-empty-${user.id}", contentType = "profile-status") { ProfileStatusRow(text = "还没有帖子") }
    }
    items(
        items = timelineNotes,
        key = { "profile-note-${it.id}" },
        contentType = { "profile-note" },
    ) { note ->
        ProfileNoteRow(
            note = note,
            onOpenNote = onOpenNote,
            onOpenUser = onOpenUser,
            onReply = onReply,
            onRenote = onRenote,
            onQuote = onQuote,
            onReact = onReact,
            onDeleteReaction = onDeleteReaction,
            onFavorite = onFavorite,
            onAddToClip = onAddToClip,
            onDelete = onDelete,
            onOpenMedia = onOpenMedia,
            onOpenMediaPreview = onOpenMediaPreview,
            onOpenMention = onOpenMention,
            onOpenHashtag = onOpenHashtag,
            onVotePoll = onVotePoll,
            reactionOptions = reactionOptions,
            recentReactions = recentReactions,
            isActionPending = isActionPending,
            canDeleteAuthor = canDeleteAuthor,
            noteRowDensity = noteRowDensity,
        )
    }
    if (state.notes.isNotEmpty() && state.isLoadingMoreNotes) {
        item(key = "profile-notes-loading-more-${user.id}", contentType = "profile-status") {
            ProfileStatusRow(
                text = "正在加载更多...",
                loading = true,
            )
        }
    }
}

private fun filteredProfileTimelineNotes(
    user: User?,
    notes: List<Note>,
): List<Note> {
    if (user == null || notes.isEmpty() || user.pinnedNotes.isEmpty()) return notes
    val pinnedNoteIds = user.pinnedNotes.mapTo(HashSet(user.pinnedNotes.size)) { it.id }
    val filtered = notes.filterNot { it.id in pinnedNoteIds }
    return if (filtered.size == notes.size) notes else filtered
}

@Composable
private fun ProfileNoteRow(
    note: Note,
    onOpenNote: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReply: (String) -> Unit,
    onRenote: (String) -> Unit,
    onQuote: (String) -> Unit,
    onReact: (String, String) -> Unit,
    onDeleteReaction: (String, String) -> Unit,
    onFavorite: (String) -> Unit,
    onAddToClip: ((Note) -> Unit)?,
    onDelete: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onVotePoll: (String, Int) -> Unit,
    reactionOptions: List<String>,
    recentReactions: List<String>,
    isActionPending: (String) -> Boolean,
    canDeleteAuthor: (String) -> Boolean,
    noteRowDensity: NoteRowDensity,
) {
    NoteRow(
        note = note,
        onClick = onOpenNote,
        onOpenUser = onOpenUser,
        onReply = onReply,
        onRenote = onRenote,
        onQuote = onQuote,
        onReact = onReact,
        onDeleteReaction = onDeleteReaction,
        onFavorite = onFavorite,
        onAddToClip = onAddToClip,
        onDelete = onDelete,
        onOpenMedia = onOpenMedia,
        onOpenMediaPreview = onOpenMediaPreview,
        onOpenMention = onOpenMention,
        onOpenHashtag = onOpenHashtag,
        onVotePoll = onVotePoll,
        reactionOptions = reactionOptions,
        recentReactions = recentReactions,
        isActionPending = isActionPending(note.id),
        canDelete = canDeleteAuthor(note.author.id),
        density = noteRowDensity,
    )
}

private fun profilePostsShortcutText(
    user: User?,
    state: UserProfileUiState?,
): String {
    return when {
        state?.isLoadingNotes == true && state.notes.isEmpty() -> "正在同步帖子"
        state?.notesErrorMessage != null -> "同步失败，点按进入重试"
        user == null -> "查看发布内容"
        user.notesCount > 0 -> "${user.notesCount} 条帖子"
        else -> "查看发布内容"
    }
}

private fun profilePostsScreenText(
    user: User?,
    state: UserProfileUiState?,
): String {
    return when {
        state?.isLoadingNotes == true && state.notes.isEmpty() -> "正在同步帖子"
        state?.notesErrorMessage != null -> "同步失败"
        user == null -> "发布内容"
        user.notesCount > 0 -> "${user.notesCount} 条帖子"
        else -> "发布内容"
    }
}

@Composable
private fun ProfileHeaderIdentity(
    user: User,
    isOwnProfile: Boolean,
    isMuted: Boolean,
    isBlocking: Boolean,
    isSpecialCare: Boolean,
    isChanging: Boolean,
    isProfileSaving: Boolean,
    canChangeBanner: Boolean,
    canChangeAvatar: Boolean,
    onFollowToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onBlockToggle: () -> Unit,
    onReportUser: () -> Unit,
    onOpenChatWithUser: () -> Unit,
    onToggleSpecialCare: (() -> Unit)?,
    onEditProfile: () -> Unit,
    onChangeBanner: (() -> Unit)?,
    onChangeAvatar: (() -> Unit)?,
    onSelectPresetAvatar: ((String) -> Unit)?,
    onTakePhoto: (() -> Unit)? = null,
    avatarUploadCooldownSeconds: Int = 0,
    avatarDailyUploadRemaining: Int = AVATAR_DAILY_UPLOAD_LIMIT,
) {
    val colors = LocalHhhlColors.current
    var showAvatarMenu by remember { mutableStateOf(false) }
    var showAvatarFullImage by remember { mutableStateOf(false) }
    var showAvatarLibrary by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileBanner(
            bannerUrl = user.bannerUrl,
            canChangeBanner = canChangeBanner,
            isChangingBanner = isProfileSaving,
            onChangeBanner = onChangeBanner,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Avatar(
                    initial = user.avatarInitial,
                    avatarUrl = user.avatarUrl,
                    size = 54.dp,
                    modifier = Modifier.clickable(
                        enabled = canChangeAvatar && !isProfileSaving,
                        onClick = { showAvatarMenu = true }
                    ),
                )
                if (canChangeAvatar) {
                    // 添加相机图标提示可更换头像
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "更换头像",
                        tint = colors.textPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .background(colors.surfaceElevated, CircleShape)
                            .padding(2.dp)
                            .clickable(
                                enabled = !isProfileSaving,
                                onClick = { showAvatarMenu = true }
                            ),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    user.displayName,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@${user.username}",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isOwnProfile && isSpecialCare) {
                    Text(
                        text = "特别关心提醒已开启",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!isOwnProfile) {
                ProfileRelationshipActions(
                    isFollowing = user.isFollowing,
                    isMuted = isMuted,
                    isBlocking = isBlocking,
                    isSpecialCare = isSpecialCare,
                    isChanging = isChanging,
                    onFollowToggle = onFollowToggle,
                    onMuteToggle = onMuteToggle,
                    onBlockToggle = onBlockToggle,
                    onReportUser = onReportUser,
                    onOpenChatWithUser = onOpenChatWithUser,
                    onToggleSpecialCare = onToggleSpecialCare,
                )
            } else {
                HhhlActionChip(
                    label = if (isProfileSaving) "保存中" else "编辑资料",
                    enabled = !isProfileSaving,
                    onClick = onEditProfile,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

    // 头像操作底部弹出菜单
    if (showAvatarMenu && canChangeAvatar) {
        AvatarActionBottomSheet(
            onDismiss = { showAvatarMenu = false },
            onPickFromLibrary = {
                showAvatarMenu = false
                showAvatarLibrary = true
            },
            onViewFullImage = {
                showAvatarMenu = false
                showAvatarFullImage = true
            },
            onPickFromGallery = {
                showAvatarMenu = false
                onChangeAvatar?.invoke()
            },
            onTakePhoto = {
                showAvatarMenu = false
                onTakePhoto?.invoke()
            },
            cooldownSeconds = avatarUploadCooldownSeconds,
            dailyRemaining = avatarDailyUploadRemaining,
        )
    }
    
    // 头像库选择对话框
    if (showAvatarLibrary && canChangeAvatar) {
        AvatarLibraryDialog(
            onDismiss = { showAvatarLibrary = false },
            onSelectAvatar = { avatarUrl ->
                showAvatarLibrary = false
                onSelectPresetAvatar?.invoke(avatarUrl)
            }
        )
    }
    
    // 头像大图查看对话框
    if (showAvatarFullImage) {
        AvatarFullImageDialog(
            avatarUrl = user.avatarUrl,
            onDismiss = { showAvatarFullImage = false }
        )
    }
}

/**
 * 头像操作底部弹出菜单
 * 提供头像更换的多种选项：从头像库选择、查看大图、从相册选择、拍照
 * 同时显示上传冷却时间和剩余次数
 */
@Composable
private fun AvatarActionBottomSheet(
    onDismiss: () -> Unit,
    onPickFromLibrary: () -> Unit,
    onViewFullImage: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: (() -> Unit)? = null,
    cooldownSeconds: Int = 0,
    dailyRemaining: Int = 5,
) {
    val colors = LocalHhhlColors.current
    val isUploadDisabled = cooldownSeconds > 0 || dailyRemaining <= 0
    
    // 使用 AlertDialog 模拟底部弹出菜单
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更换头像") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 显示上传限制信息
                if (cooldownSeconds > 0) {
                    Text(
                        text = "⏱ 冷却中：请等待 ${cooldownSeconds} 秒后再上传",
                        color = colors.warning,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (dailyRemaining <= 0) {
                    Text(
                        text = "📊 今日上传次数已用完，请明天再试",
                        color = colors.danger,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                } else {
                    Text(
                        text = "📊 今日剩余上传次数：$dailyRemaining 次",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                
                HhhlDivider()
                
                AvatarActionItem(
                    icon = Icons.Filled.Collections,
                    label = "从头像库选择",
                    onClick = onPickFromLibrary,
                    colors = colors,
                    enabled = !isUploadDisabled
                )
                AvatarActionItem(
                    icon = Icons.Filled.ZoomIn,
                    label = "查看大图",
                    onClick = onViewFullImage,
                    colors = colors,
                    enabled = true
                )
                AvatarActionItem(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "从手机相册中选择",
                    onClick = onPickFromGallery,
                    colors = colors,
                    enabled = !isUploadDisabled
                )
                if (onTakePhoto != null) {
                    AvatarActionItem(
                        icon = Icons.Filled.CameraAlt,
                        label = "拍照",
                        onClick = onTakePhoto,
                        colors = colors,
                        enabled = !isUploadDisabled
                    )
                }
                HhhlDivider()
                AvatarActionItem(
                    icon = Icons.Filled.Close,
                    label = "取消",
                    onClick = onDismiss,
                    colors = colors,
                    isDestructive = true
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

/**
 * 头像操作菜单项
 */
@Composable
private fun AvatarActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    colors: cc.hhhl.client.theme.HhhlColors,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (!enabled) colors.textMuted else if (isDestructive) colors.danger else colors.textPrimary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = if (!enabled) colors.textMuted else if (isDestructive) colors.danger else colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * 头像库选择对话框
 * 展示预设头像列表供用户选择
 */
@Composable
private fun AvatarLibraryDialog(
    onDismiss: () -> Unit,
    onSelectAvatar: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择头像") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "从预设头像中选择一个作为您的头像",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // 使用 FlowRow 展示头像网格
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PresetAvatarUrls.forEach { avatarUrl ->
                        AvatarLibraryItem(
                            avatarUrl = avatarUrl,
                            onClick = { onSelectAvatar(avatarUrl) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 头像库单项
 */
@Composable
private fun AvatarLibraryItem(
    avatarUrl: String,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .border(
                width = if (isPressed) 2.dp else 1.dp,
                color = if (isPressed) colors.accent else colors.border,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "预设头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
private fun ProfileEditDialog(
    user: User,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember(user.id) { mutableStateOf(user.displayName) }
    var description by remember(user.id) { mutableStateOf(user.bio) }
    val cleanName = name.trim()
    val cleanDescription = description.trim()
    val nameError = when {
        cleanName.isBlank() -> "请输入名称"
        cleanName.length > USER_PROFILE_NAME_MAX_LENGTH -> "名称不能超过 ${USER_PROFILE_NAME_MAX_LENGTH} 字"
        else -> null
    }
    val descriptionError = when {
        cleanDescription.length > USER_PROFILE_DESCRIPTION_MAX_LENGTH -> {
            "简介不能超过 ${USER_PROFILE_DESCRIPTION_MAX_LENGTH} 字"
        }
        else -> null
    }
    val hasChanges = cleanName != user.displayName.trim() || cleanDescription != user.bio.trim()
    val localError = nameError ?: descriptionError
    val canSubmit = localError == null && hasChanges && !isSaving

    HhhlAlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = { Text("编辑资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = name,
                    onValueChange = {
                        if (it.length <= USER_PROFILE_NAME_MAX_LENGTH + 20) {
                            name = it
                        }
                    },
                    label = "名称",
                    placeholder = "显示名称",
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = description,
                    onValueChange = {
                        if (it.length <= USER_PROFILE_DESCRIPTION_MAX_LENGTH + 100) {
                            description = it
                        }
                    },
                    label = "简介",
                    placeholder = "介绍一下自己",
                    enabled = !isSaving,
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                ProfileEditFieldCounter(
                    count = cleanDescription.length,
                    max = USER_PROFILE_DESCRIPTION_MAX_LENGTH,
                    isError = descriptionError != null,
                )
                (localError ?: errorMessage)?.let {
                    val colors = LocalHhhlColors.current
                    Text(
                        text = it,
                        color = colors.danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = { onSubmit(cleanName, cleanDescription) },
                enabled = canSubmit,
            ) {
                Text(if (isSaving) "保存中" else "保存")
            }
        },
        dismissButton = {
            HhhlTextButton(
                onClick = onDismiss,
                enabled = !isSaving,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ProfileEditFieldCounter(
    count: Int,
    max: Int,
    isError: Boolean,
) {
    val colors = LocalHhhlColors.current
    Text(
        text = "$count / $max",
        color = if (isError) colors.danger else colors.textMuted,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileSocialStatsRow(
    user: User,
    onOpenSocial: (UserSocialKind) -> Unit,
) {
    val colors = LocalHhhlColors.current
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${user.followingCount} 关注",
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable { onOpenSocial(UserSocialKind.Following) },
        )
        Text(
            "${user.followersCount} 关注者",
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable { onOpenSocial(UserSocialKind.Followers) },
        )
        Text(
            "${user.notesCount} 帖子",
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ProfileAiActions(
    enabled: Boolean,
    isProcessing: Boolean,
    onSummary: () -> Unit,
    onSuggestions: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HhhlActionChip(
            label = if (isProcessing) "AI 处理中" else "AI 资料速览",
            enabled = enabled && !isProcessing,
            onClick = onSummary,
        )
        HhhlActionChip(
            label = "AI 互动建议",
            enabled = enabled && !isProcessing,
            onClick = onSuggestions,
        )
    }
}

@Composable
private fun ProfileAiResultPanel(
    label: String,
    text: String,
    notes: List<Note>,
    onCopyAiResult: ((String) -> Unit)?,
    onAddAiMutedWord: ((String) -> Unit)?,
    onAddAiRelatedNoteToWatchLater: ((String, List<Note>) -> Unit)?,
    onOpenAiRelatedNote: ((String, List<Note>) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AiResultPanel(
        label = label,
        text = text,
        onDismiss = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        actions = {
            AiResultCommonActionChips(
                text = text,
                onCopyChecklist = onCopyAiResult,
                onAddMutedWord = onAddAiMutedWord,
                onAddToWatchLater = onAddAiRelatedNoteToWatchLater?.let { add -> { add(text, notes) } },
                onOpenRelatedNote = onOpenAiRelatedNote?.let { open -> { open(text, notes) } },
            )
        },
    )
}

@Composable
private fun ProfileQuickActions(
    capabilities: InstanceCapabilities,
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    profilePostsText: String,
    onRefresh: () -> Unit,
    onOpenProfileNotes: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenRelationshipManagement: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenFavoriteNotes: () -> Unit,
    onOpenFollowRequests: () -> Unit,
    onOpenLists: () -> Unit,
    onOpenClips: () -> Unit,
    onOpenAntennas: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenGallery: () -> Unit,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    onAiProfileSummary: () -> Unit,
    onAiProfileSuggestions: () -> Unit,
    onLogout: () -> Unit,
) {
    var appearanceDialogOpen by remember { mutableStateOf(false) }
    var logoutDialogOpen by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
    val primaryShortcuts = profilePrimaryShortcuts(
        profilePostsText = profilePostsText,
        onOpenProfileNotes = onOpenProfileNotes,
        onOpenDrive = onOpenDrive,
        onOpenSettings = onOpenSettings,
        onOpenAutomation = onOpenAutomation,
    )
    val workspaceShortcuts = profileWorkspaceShortcuts(
        capabilities = capabilities,
        onOpenFavoriteNotes = onOpenFavoriteNotes,
        onOpenAchievements = onOpenAchievements,
        onOpenRelationshipManagement = onOpenRelationshipManagement,
        onOpenLists = onOpenLists,
        onOpenClips = onOpenClips,
        onOpenAntennas = onOpenAntennas,
        onOpenChannels = onOpenChannels,
        onOpenPages = onOpenPages,
        onOpenGallery = onOpenGallery,
    )
    val moreActions = profileMoreMenuActions(
        selectedTheme = selectedTheme,
        selectedTimelineDensity = selectedTimelineDensity,
        onRefresh = onRefresh,
        onOpenFollowRequests = onOpenFollowRequests,
        onOpenRelationshipManagement = onOpenRelationshipManagement,
        onOpenFlash = onOpenFlash,
        onOpenAnnouncements = onOpenAnnouncements,
        onOpenAppearance = { appearanceDialogOpen = true },
        aiEnabled = aiEnabled,
        isAiProcessing = isAiProcessing,
        onAiProfileSummary = onAiProfileSummary,
        onAiProfileSuggestions = onAiProfileSuggestions,
        onLogout = { logoutDialogOpen = true },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ProfileQuickActionsHorizontalPadding)
            .padding(vertical = ProfileQuickActionsCardInnerPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "快捷入口",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HhhlActionChip(
                label = "刷新",
                emphasized = true,
                onClick = onRefresh,
                modifier = Modifier.widthIn(max = 80.dp),
            )
            HhhlOverflowMenu(
                actions = moreActions,
                label = "更多入口",
            )
        }

        ProfileShortcutGrid(primaryShortcuts, columns = 2, compact = true)
        ProfileShortcutGroupLabel("工作区")
        ProfileShortcutGrid(workspaceShortcuts, columns = 2, compact = true)
    }

    if (appearanceDialogOpen) {
        ProfileAppearanceDialog(
            selectedTheme = selectedTheme,
            selectedTimelineDensity = selectedTimelineDensity,
            onThemeSelected = onThemeSelected,
            onTimelineDensitySelected = onTimelineDensitySelected,
            onDismiss = { appearanceDialogOpen = false },
        )
    }

    if (logoutDialogOpen) {
        HhhlAlertDialog(
            onDismissRequest = { logoutDialogOpen = false },
            title = { Text("退出登录") },
            text = {
                Text(
                    text = "退出后需要重新授权才能继续使用当前账号。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        logoutDialogOpen = false
                        onLogout()
                    },
                    destructive = true,
                ) {
                    Text("退出登录")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { logoutDialogOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
}

private data class ProfileShortcut(
    val title: String,
    val supportingText: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private fun profilePrimaryShortcuts(
    profilePostsText: String,
    onOpenProfileNotes: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAutomation: () -> Unit,
): List<ProfileShortcut> = buildList {
    add(
        ProfileShortcut(
            title = "帖子",
            supportingText = profilePostsText,
            icon = Icons.AutoMirrored.Filled.Article,
            onClick = onOpenProfileNotes,
        ),
    )
    add(
        ProfileShortcut(
            title = "Drive",
            supportingText = "文件",
            icon = Icons.Filled.Folder,
            onClick = onOpenDrive,
        ),
    )
    add(
        ProfileShortcut(
            title = "设置",
            supportingText = "偏好",
            icon = Icons.Filled.Settings,
            onClick = onOpenSettings,
        ),
    )
    add(
        ProfileShortcut(
            title = "自动化",
            supportingText = "规则与回调",
            icon = Icons.Filled.Tune,
            onClick = onOpenAutomation,
        ),
    )
}

private fun profileWorkspaceShortcuts(
    capabilities: InstanceCapabilities,
    onOpenFavoriteNotes: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenRelationshipManagement: () -> Unit,
    onOpenLists: () -> Unit,
    onOpenClips: () -> Unit,
    onOpenAntennas: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenGallery: () -> Unit,
): List<ProfileShortcut> = buildList {
    add(
        ProfileShortcut(
            title = "收藏",
            supportingText = "帖子与信息",
            icon = Icons.Filled.Bookmark,
            onClick = onOpenFavoriteNotes,
        ),
    )
    add(
        ProfileShortcut(
            title = "成就",
            supportingText = "奖杯",
            icon = Icons.Filled.EmojiEvents,
            onClick = onOpenAchievements,
        ),
    )
    add(
        ProfileShortcut(
            title = "关系",
            supportingText = "关心与静音",
            icon = Icons.Filled.People,
            onClick = onOpenRelationshipManagement,
        ),
    )
    if (capabilities.canUseUserLists) {
        add(
            ProfileShortcut(
                title = "列表",
                supportingText = "上限 ${capabilities.userListLimit}",
                icon = Icons.AutoMirrored.Filled.List,
                onClick = onOpenLists,
            ),
        )
    }
    if (capabilities.canUseClips) {
        add(
            ProfileShortcut(
                title = "剪辑",
                supportingText = "上限 ${capabilities.clipLimit}",
                icon = Icons.Filled.Crop,
                onClick = onOpenClips,
            ),
        )
    }
    if (capabilities.canUseAntennas) {
        add(
            ProfileShortcut(
                title = "天线",
                supportingText = "上限 ${capabilities.antennaLimit}",
                icon = Icons.Filled.RssFeed,
                onClick = onOpenAntennas,
            ),
        )
    }
    add(
        ProfileShortcut(
            title = "频道",
            supportingText = "内容管理",
            icon = Icons.Filled.Forum,
            onClick = onOpenChannels,
        ),
    )
    add(
        ProfileShortcut(
            title = "页面",
            supportingText = "个人页面",
            icon = Icons.AutoMirrored.Filled.Article,
            onClick = onOpenPages,
        ),
    )
    add(
        ProfileShortcut(
            title = "图库",
            supportingText = "图片内容",
            icon = Icons.Filled.Image,
            onClick = onOpenGallery,
        ),
    )
}

@Composable
private fun ProfileShortcutGrid(
    shortcuts: List<ProfileShortcut>,
    columns: Int,
    compact: Boolean,
) {
    val spacing = if (compact) 8.dp else 10.dp

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        shortcuts.chunked(columns).forEach { rowShortcuts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowShortcuts.forEach { shortcut ->
                    ProfileShortcutTile(
                        shortcut = shortcut,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowShortcuts.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileShortcutGroupLabel(label: String) {
    Text(
        text = label,
        color = LocalHhhlColors.current.textMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
    )
}

@Composable
private fun ProfileShortcutTile(
    shortcut: ProfileShortcut,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    val baseTileHeight = if (compact) {
        ProfileWorkspaceShortcutTileHeight
    } else {
        ProfilePrimaryShortcutTileHeight
    }
    val tileHeight = baseTileHeight + ((fontScale - 1f) * if (compact) 24f else 28f).dp
    val supportingMaxLines = if (fontScale > 1.25f) 2 else 1
    val tileShape = RoundedCornerShape(ProfileShortcutTileCornerRadius)
    val iconContainerShape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tileBorderColor by animateColorAsState(
        targetValue = if (pressed) {
            colors.focusRing.copy(alpha = 0.22f)
        } else {
            colors.border.copy(alpha = 0.24f)
        },
        label = "profile-shortcut-border",
    )
    val pressedOverlayColor by animateColorAsState(
        targetValue = if (pressed) {
            colors.accent.copy(alpha = 0.05f)
        } else {
            colors.inputBackground.copy(alpha = 0f)
        },
        label = "profile-shortcut-overlay",
    )
    val tileModifier = modifier
        .height(tileHeight)
        .clip(tileShape)
        .background(colors.surfaceElevated.copy(alpha = 0.54f))
        .background(pressedOverlayColor)
        .border(
            width = 1.dp,
            color = tileBorderColor,
            shape = tileShape,
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = shortcut.onClick,
        )

    if (compact) {
        Row(
            modifier = tileModifier
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(ProfileShortcutIconContainerSize)
                    .clip(iconContainerShape)
                    .background(colors.accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = shortcut.icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(ProfileShortcutIconSize),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = shortcut.title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                Text(
                    text = shortcut.supportingText,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 17.sp),
                    maxLines = supportingMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = supportingMaxLines > 1,
                )
            }
        }
        return
    }

    Column(
        modifier = tileModifier
            .padding(
                horizontal = 10.dp,
                vertical = 9.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Top),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(ProfileShortcutIconContainerSize)
                .clip(iconContainerShape)
                .background(colors.accent.copy(alpha = 0.08f))
                .border(1.dp, colors.border.copy(alpha = 0.16f), iconContainerShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = shortcut.icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(ProfileShortcutIconSize),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = shortcut.title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Text(
                text = shortcut.supportingText,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 17.sp),
                maxLines = supportingMaxLines,
                overflow = TextOverflow.Ellipsis,
                softWrap = supportingMaxLines > 1,
            )
        }
    }
}

private fun profileMoreMenuActions(
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    onRefresh: () -> Unit,
    onOpenFollowRequests: () -> Unit,
    onOpenRelationshipManagement: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenAppearance: () -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    onAiProfileSummary: () -> Unit,
    onAiProfileSuggestions: () -> Unit,
    onLogout: () -> Unit,
): List<HhhlOverflowMenuAction> {
    return listOf(
        HhhlOverflowMenuAction("刷新资料", onClick = onRefresh),
        HhhlOverflowMenuAction(
            label = "AI",
            enabled = aiEnabled,
            icon = Icons.Filled.AutoAwesome,
            onClick = {},
            children = listOf(
                HhhlOverflowMenuAction(
                    label = if (isAiProcessing) "AI 处理中" else "资料速览",
                    enabled = aiEnabled && !isAiProcessing,
                    icon = Icons.Filled.AutoAwesome,
                    onClick = onAiProfileSummary,
                ),
                HhhlOverflowMenuAction(
                    label = "互动建议",
                    enabled = aiEnabled && !isAiProcessing,
                    icon = Icons.Filled.AutoAwesome,
                    onClick = onAiProfileSuggestions,
                ),
            ),
        ),
        HhhlOverflowMenuAction("关注请求", onClick = onOpenFollowRequests),
        HhhlOverflowMenuAction("关系管理", onClick = onOpenRelationshipManagement),
        HhhlOverflowMenuAction(
            "外观 · ${profileAppearanceSummaryLabel(selectedTheme, selectedTimelineDensity)}",
            onClick = onOpenAppearance,
        ),
        HhhlOverflowMenuAction("Flash", onClick = onOpenFlash),
        HhhlOverflowMenuAction("公告", onClick = onOpenAnnouncements),
        HhhlOverflowMenuAction("退出登录", destructive = true, onClick = onLogout),
    )
}

private enum class ProfileActionKey {
    Refresh,
    ProfileNotes,
    Drive,
    Settings,
    FavoriteNotes,
    UserLists,
    FollowRequests,
    Clips,
    Antennas,
    Channels,
    Pages,
    Gallery,
    RelationshipManagement,
    Appearance,
    Flash,
    Announcements,
    Logout,
}

private data class ProfileAction(
    val key: ProfileActionKey,
    val label: String,
    val emphasized: Boolean = false,
)

private fun profilePrimaryActions(capabilities: InstanceCapabilities): List<ProfileAction> {
    return buildList {
        add(ProfileAction(ProfileActionKey.ProfileNotes, "帖子", emphasized = true))
        add(ProfileAction(ProfileActionKey.Drive, "Drive"))
        add(ProfileAction(ProfileActionKey.Settings, "设置"))
    }
}

private fun profileAccountActions(): List<ProfileAction> {
    return buildList {
        add(ProfileAction(ProfileActionKey.FavoriteNotes, "收藏"))
        add(ProfileAction(ProfileActionKey.FollowRequests, "关注请求"))
        add(ProfileAction(ProfileActionKey.RelationshipManagement, "关系管理"))
    }
}

private fun profileWorkspaceActions(capabilities: InstanceCapabilities): List<ProfileAction> {
    return buildList {
        if (capabilities.canUseUserLists) {
            add(ProfileAction(ProfileActionKey.UserLists, "列表 ${capabilities.userListLimit}"))
        }
        if (capabilities.canUseClips) {
            add(ProfileAction(ProfileActionKey.Clips, "剪辑 ${capabilities.clipLimit}"))
        }
        if (capabilities.canUseAntennas) {
            add(ProfileAction(ProfileActionKey.Antennas, "天线 ${capabilities.antennaLimit}"))
        }
        add(ProfileAction(ProfileActionKey.Channels, "频道"))
        add(ProfileAction(ProfileActionKey.Pages, "页面"))
        add(ProfileAction(ProfileActionKey.Gallery, "图库"))
    }
}

fun profilePrimaryActionLabels(capabilities: InstanceCapabilities): List<String> {
    return profilePrimaryActions(capabilities).map { it.label }
}

fun profileAccountActionLabels(): List<String> {
    return profileAccountActions().map { it.label }
}

fun profileWorkspaceActionLabels(capabilities: InstanceCapabilities): List<String> {
    return profileWorkspaceActions(capabilities).map { it.label }
}

fun profileEditableFieldLabels(): List<String> {
    return listOf("名称", "简介")
}

@Composable
private fun ProfileRelationshipActions(
    isFollowing: Boolean,
    isMuted: Boolean,
    isBlocking: Boolean,
    isSpecialCare: Boolean,
    isChanging: Boolean,
    onFollowToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onBlockToggle: () -> Unit,
    onReportUser: () -> Unit,
    onOpenChatWithUser: () -> Unit,
    onToggleSpecialCare: (() -> Unit)?,
) {
    var blockDialogOpen by remember { mutableStateOf(false) }
    var reportDialogOpen by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlActionChip(
            label = when {
                isChanging -> "处理中"
                isFollowing -> "已关注"
                else -> "关注"
            },
            emphasized = !isChanging && !isFollowing,
            enabled = !isChanging,
            onClick = onFollowToggle,
        )
        HhhlIconActionButton(
            icon = Icons.AutoMirrored.Filled.Send,
            contentDescription = "发送消息",
            onClick = onOpenChatWithUser,
        )
        HhhlOverflowMenu(
            enabled = !isChanging,
            actions = buildList {
                onToggleSpecialCare?.let {
                    add(
                        HhhlOverflowMenuAction(
                            label = userSocialSpecialCareActionLabel(isSpecialCare),
                            onClick = it,
                        ),
                    )
                }
                add(
                    HhhlOverflowMenuAction(
                        label = if (isMuted) "取消静音" else "静音",
                        onClick = onMuteToggle,
                    ),
                )
                add(
                    HhhlOverflowMenuAction(
                        label = if (isBlocking) "取消屏蔽" else "屏蔽",
                        destructive = !isBlocking,
                        onClick = { blockDialogOpen = true },
                    ),
                )
                add(
                    HhhlOverflowMenuAction(
                        label = "举报用户",
                        destructive = true,
                        onClick = { reportDialogOpen = true },
                    ),
                )
            },
        )
    }

    if (blockDialogOpen) {
        HhhlAlertDialog(
            onDismissRequest = { blockDialogOpen = false },
            title = { Text(if (isBlocking) "取消屏蔽" else "屏蔽用户") },
            text = {
                Text(
                    text = if (isBlocking) {
                        "取消屏蔽后，对方将不再处于你的屏蔽列表。"
                    } else {
                        "屏蔽后，你将不再看到对方的帖子和群聊消息，对方与你的互动也会被限制。"
                    },
                    color = LocalHhhlColors.current.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        onBlockToggle()
                        blockDialogOpen = false
                    },
                    enabled = !isChanging,
                    destructive = !isBlocking,
                ) {
                    Text(if (isChanging) "处理中" else if (isBlocking) "取消屏蔽" else "屏蔽")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { blockDialogOpen = false }, enabled = !isChanging) {
                    Text("取消")
                }
            },
        )
    }

    if (reportDialogOpen) {
        HhhlAlertDialog(
            onDismissRequest = { reportDialogOpen = false },
            title = { Text("举报用户") },
            text = {
                Text(
                    text = "举报会提交给实例管理员处理。请只在账号存在骚扰、垃圾信息或违反规则时使用。",
                    color = LocalHhhlColors.current.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        onReportUser()
                        reportDialogOpen = false
                    },
                    enabled = !isChanging,
                    destructive = true,
                ) {
                    Text(if (isChanging) "处理中" else "举报")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { reportDialogOpen = false }, enabled = !isChanging) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ProfileAppearanceDialog(
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onDismiss: () -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外观") },
        text = {
            ProfileAppearancePanel(
                selectedTheme = selectedTheme,
                selectedTimelineDensity = selectedTimelineDensity,
                onThemeSelected = onThemeSelected,
                onTimelineDensitySelected = onTimelineDensitySelected,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

@Composable
private fun ProfileAppearancePanel(
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = profileAppearanceSummaryLabel(selectedTheme, selectedTimelineDensity),
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        ThemePicker(
            selectedTheme = selectedTheme,
            onThemeSelected = onThemeSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        TimelineDensityPicker(
            selectedDensity = selectedTimelineDensity,
            onDensitySelected = onTimelineDensitySelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun profileAppearanceSummaryLabel(
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
): String {
    return "${selectedTheme.label} · ${selectedTimelineDensity.label}"
}

fun profileAppearanceActionLabels(): List<String> {
    return listOf("更换主题", "信息流密度")
}

fun profileToolActionLabels(): List<String> {
    return listOf("刷新资料", "外观", "Flash", "公告", "退出登录")
}

@Composable
private fun ProfileBanner(
    bannerUrl: String?,
    canChangeBanner: Boolean,
    isChangingBanner: Boolean,
    onChangeBanner: (() -> Unit)?,
) {
    val colors = LocalHhhlColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(colors.mediaBackground)
            .clickable(
                enabled = canChangeBanner && onChangeBanner != null && !isChangingBanner,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onChangeBanner?.invoke() },
            ),
    ) {
        AsyncImage(
            model = bannerUrl?.takeIf { it.isNotBlank() } ?: ProfileDefaultBannerImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (bannerUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.noteBackground.copy(alpha = 0.18f),
                                colors.mediaBackground.copy(alpha = 0.54f),
                            ),
                        ),
                    ),
            )
        }
        if (isChangingBanner) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.pageBackground.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                HhhlProgressIndicator()
            }
        }
    }
}

@Composable
private fun ProfileStatusRow(
    text: String,
    loading: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    HhhlStatusRow(
        text = text,
        loading = loading,
        actionText = actionText,
        onAction = onAction,
    )
}

/**
 * 头像大图查看对话框
 * 展示用户头像的高清大图
 */
@Composable
private fun AvatarFullImageDialog(
    avatarUrl: String?,
    onDismiss: () -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查看头像") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        color = LocalHhhlColors.current.mediaBackground,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "默认头像",
                        tint = LocalHhhlColors.current.textMuted,
                        modifier = Modifier.size(80.dp)
                    )
                } else {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "用户头像",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        confirmButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

/**
 * 头像预览确认对话框
 * 选图后显示预览，图片以裁剪模式显示（居中裁剪为正方形），
 * 用户确认后执行上传
 */
@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun AvatarPreviewConfirmDialog(
    upload: DriveFileUpload,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    // 将文件字节编码为 data URL 供 AsyncImage 加载
    val dataUrl = remember(upload) {
        val safeContentType = upload.contentType.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        "data:$safeContentType;base64,${Base64.encode(upload.bytes)}"
    }

    HhhlAlertDialog(
        onDismissRequest = onCancel,
        title = { Text("确认更换头像") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 预览区域：使用 Crop 模式模拟正方形裁剪效果
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(
                            color = LocalHhhlColors.current.mediaBackground,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = dataUrl,
                        contentDescription = "头像预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 裁剪提示
                Text(
                    text = "图片将自动裁剪为正方形头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalHhhlColors.current.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        },
        confirmButton = {
            HhhlTextButton(onClick = onConfirm) {
                Text("确认上传")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onCancel) {
                Text("取消")
            }
        },
    )
}
