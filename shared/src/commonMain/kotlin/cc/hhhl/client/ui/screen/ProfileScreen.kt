package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cc.hhhl.client.api.USER_PROFILE_DESCRIPTION_MAX_LENGTH
import cc.hhhl.client.api.USER_PROFILE_NAME_MAX_LENGTH
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserSocialKind
import cc.hhhl.client.state.UserProfileUiState
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity
import cc.hhhl.client.ui.component.ThemePicker
import cc.hhhl.client.ui.component.TimelineDensityPicker

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
    selectedTheme: HhhlThemePreset = HhhlThemePreset.System,
    selectedTimelineDensity: TimelineDensity = TimelineDensity.Comfortable,
    title: String = "我的",
    isOwnProfile: Boolean = true,
    capabilities: InstanceCapabilities = InstanceCapabilities(),
    onFollowToggle: () -> Unit = {},
    onMuteToggle: () -> Unit = {},
    onBlockToggle: () -> Unit = {},
    onReportUser: () -> Unit = {},
    onUpdateProfile: (String, String) -> Unit = { _, _ -> },
    onOpenSocial: (UserSocialKind) -> Unit = {},
    onOpenDrive: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenRelationshipManagement: () -> Unit = {},
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
    onThemeSelected: (HhhlThemePreset) -> Unit = {},
    onTimelineDensitySelected: (TimelineDensity) -> Unit = {},
    onClearMessage: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val user = state?.user ?: if (state == null) FakeData.me else null
    var profileEditorOpen by remember(user?.id) { mutableStateOf(false) }
    var profileEditSubmitted by remember(user?.id) { mutableStateOf(false) }

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
                item { ProfileStatusRow(text = "正在加载资料...", loading = true) }
            }
            state?.errorMessage?.let { message ->
                item {
                    ProfileStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            state?.message?.let { message ->
                item { ProfileStatusRow(text = message) }
            }
            item {
                if (user != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProfileHeaderIdentity(
                            user = user,
                            isOwnProfile = isOwnProfile,
                            isMuted = state?.relationship?.isMuted == true,
                            isBlocking = state?.relationship?.isBlocking == true,
                            isChanging = state?.isRelationshipChanging == true,
                            isProfileSaving = state?.isProfileSaving == true,
                            onFollowToggle = onFollowToggle,
                            onMuteToggle = onMuteToggle,
                            onBlockToggle = onBlockToggle,
                            onReportUser = onReportUser,
                            onEditProfile = {
                                onClearMessage()
                                profileEditorOpen = true
                            },
                        )
                        if (user.bio.isNotBlank()) {
                            Text(
                                user.bio,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        ProfileSocialStatsRow(user = user, onOpenSocial = onOpenSocial)
                        if (isOwnProfile) {
                            ProfileQuickActions(
                                capabilities = capabilities,
                                selectedTheme = selectedTheme,
                                selectedTimelineDensity = selectedTimelineDensity,
                                onRefresh = onRefresh,
                                onOpenDrive = onOpenDrive,
                                onOpenSettings = onOpenSettings,
                                onOpenFlash = onOpenFlash,
                                onOpenAnnouncements = onOpenAnnouncements,
                                onOpenRelationshipManagement = onOpenRelationshipManagement,
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
                                onLogout = onLogout,
                            )
                        }
                    }
                    HhhlDivider()
                }
            }
            if (state == null) {
                items(FakeData.timeline, key = { "profile-${it.id}" }) { note ->
                    NoteRow(
                        note = note,
                        onClick = onOpenNote,
                        onOpenUser = onOpenUser,
                        onOpenMedia = onOpenMedia,
                        onOpenMediaPreview = onOpenMediaPreview,
                        onOpenMention = onOpenMention,
                        onOpenHashtag = onOpenHashtag,
                        onVotePoll = onVotePoll,
                        density = noteRowDensity,
                    )
                }
            } else if (user != null) {
                if (user.pinnedNotes.isNotEmpty()) {
                    item { ProfileStatusRow(text = "置顶") }
                    items(user.pinnedNotes, key = { "profile-pinned-${it.id}" }) { note ->
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
                }
                if (state.isLoadingNotes && state.notes.isEmpty()) {
                    item { ProfileStatusRow(text = "正在加载帖子...", loading = true) }
                }
                state.notesErrorMessage?.let { message ->
                    item {
                        ProfileStatusRow(
                            text = message,
                            actionText = "重试",
                            onAction = onRefresh,
                        )
                    }
                }
                if (!state.isLoadingNotes && state.notes.isEmpty() && state.notesErrorMessage == null) {
                    item { ProfileStatusRow(text = "还没有帖子") }
                }
                val pinnedNoteIds = user.pinnedNotes.map { it.id }.toSet()
                val timelineNotes = state.notes.filterNot { it.id in pinnedNoteIds }
                items(timelineNotes, key = { "profile-note-${it.id}" }) { note ->
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
                if (state.notes.isNotEmpty()) {
                    item {
                        ProfileStatusRow(
                            text = if (state.isLoadingMoreNotes) "正在加载更多..." else "加载更多",
                            loading = state.isLoadingMoreNotes,
                            onAction = if (state.isLoadingMoreNotes) null else onLoadMoreNotes,
                        )
                    }
                }
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
                if (state == null) {
                    profileEditorOpen = false
                    profileEditSubmitted = false
                }
            },
        )
    }
}

@Composable
private fun ProfileHeaderIdentity(
    user: User,
    isOwnProfile: Boolean,
    isMuted: Boolean,
    isBlocking: Boolean,
    isChanging: Boolean,
    isProfileSaving: Boolean,
    onFollowToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onBlockToggle: () -> Unit,
    onReportUser: () -> Unit,
    onEditProfile: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileBanner(bannerUrl = user.bannerUrl)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                initial = user.avatarInitial,
                avatarUrl = user.avatarUrl,
                size = 54.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    user.displayName,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@${user.username}",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isOwnProfile) {
                ProfileRelationshipActions(
                    isFollowing = user.isFollowing,
                    isMuted = isMuted,
                    isBlocking = isBlocking,
                    isChanging = isChanging,
                    onFollowToggle = onFollowToggle,
                    onMuteToggle = onMuteToggle,
                    onBlockToggle = onBlockToggle,
                    onReportUser = onReportUser,
                )
            } else {
                HhhlActionChip(
                    label = if (isProfileSaving) "保存中" else "编辑",
                    enabled = !isProfileSaving,
                    onClick = onEditProfile,
                )
            }
        }
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

    AlertDialog(
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
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(cleanName, cleanDescription) },
                enabled = canSubmit,
            ) {
                Text(if (isSaving) "保存中" else "保存")
            }
        },
        dismissButton = {
            TextButton(
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
    Text(
        text = "$count / $max",
        color = if (isError) MaterialTheme.colorScheme.error else LocalHhhlColors.current.subtleText,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileSocialStatsRow(
    user: User,
    onOpenSocial: (UserSocialKind) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${user.followingCount} 关注",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable { onOpenSocial(UserSocialKind.Following) },
        )
        Text(
            "${user.followersCount} 关注者",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable { onOpenSocial(UserSocialKind.Followers) },
        )
        Text(
            "${user.notesCount} 帖子",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ProfileQuickActions(
    capabilities: InstanceCapabilities,
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    onRefresh: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenRelationshipManagement: () -> Unit,
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
    onLogout: () -> Unit,
) {
    val primaryActions = profilePrimaryActions(capabilities)
    val accountActions = profileAccountMenuActions(
        onOpenFavoriteNotes = onOpenFavoriteNotes,
        onOpenFollowRequests = onOpenFollowRequests,
    )
    val workspaceActions = profileWorkspaceMenuActions(
        capabilities = capabilities,
        onOpenLists = onOpenLists,
        onOpenClips = onOpenClips,
        onOpenAntennas = onOpenAntennas,
        onOpenChannels = onOpenChannels,
        onOpenPages = onOpenPages,
        onOpenGallery = onOpenGallery,
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        primaryActions.forEach { action ->
            HhhlActionChip(
                label = action.label,
                emphasized = action.emphasized,
                onClick = when (action.key) {
                    ProfileActionKey.Refresh -> onRefresh
                    ProfileActionKey.Drive -> onOpenDrive
                    ProfileActionKey.Settings -> onOpenSettings
                    else -> onRefresh
                },
            )
        }
        HhhlOverflowMenu(
            actions = accountActions,
            label = "打开账号内容",
        )
        HhhlOverflowMenu(
            actions = workspaceActions,
            label = "打开工作区",
        )
        ProfileToolMenu(
            selectedTheme = selectedTheme,
            selectedTimelineDensity = selectedTimelineDensity,
            onThemeSelected = onThemeSelected,
            onTimelineDensitySelected = onTimelineDensitySelected,
            onOpenFlash = onOpenFlash,
            onOpenAnnouncements = onOpenAnnouncements,
            onOpenRelationshipManagement = onOpenRelationshipManagement,
            onLogout = onLogout,
        )
    }
}

private fun profileAccountMenuActions(
    onOpenFavoriteNotes: () -> Unit,
    onOpenFollowRequests: () -> Unit,
): List<HhhlOverflowMenuAction> {
    return profileAccountActions().mapNotNull { action ->
        val onClick = when (action.key) {
            ProfileActionKey.FavoriteNotes -> onOpenFavoriteNotes
            ProfileActionKey.FollowRequests -> onOpenFollowRequests
            else -> null
        } ?: return@mapNotNull null
        HhhlOverflowMenuAction(action.label, onClick = onClick)
    }
}

private fun profileWorkspaceMenuActions(
    capabilities: InstanceCapabilities,
    onOpenLists: () -> Unit,
    onOpenClips: () -> Unit,
    onOpenAntennas: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenGallery: () -> Unit,
): List<HhhlOverflowMenuAction> {
    return profileWorkspaceActions(capabilities).mapNotNull { action ->
        val onClick = when (action.key) {
            ProfileActionKey.UserLists -> onOpenLists
            ProfileActionKey.Clips -> onOpenClips
            ProfileActionKey.Antennas -> onOpenAntennas
            ProfileActionKey.Channels -> onOpenChannels
            ProfileActionKey.Pages -> onOpenPages
            ProfileActionKey.Gallery -> onOpenGallery
            else -> null
        } ?: return@mapNotNull null
        HhhlOverflowMenuAction(action.label, onClick = onClick)
    }
}

private enum class ProfileActionKey {
    Refresh,
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
}

private data class ProfileAction(
    val key: ProfileActionKey,
    val label: String,
    val emphasized: Boolean = false,
)

private fun profilePrimaryActions(capabilities: InstanceCapabilities): List<ProfileAction> {
    return buildList {
        add(ProfileAction(ProfileActionKey.Refresh, "刷新资料", emphasized = true))
        add(ProfileAction(ProfileActionKey.Drive, "Drive"))
        add(ProfileAction(ProfileActionKey.Settings, "设置"))
    }
}

private fun profileAccountActions(): List<ProfileAction> {
    return buildList {
        add(ProfileAction(ProfileActionKey.FavoriteNotes, "收藏"))
        add(ProfileAction(ProfileActionKey.FollowRequests, "请求"))
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
    isChanging: Boolean,
    onFollowToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onBlockToggle: () -> Unit,
    onReportUser: () -> Unit,
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
        HhhlOverflowMenu(
            enabled = !isChanging,
            actions = listOf(
                HhhlOverflowMenuAction(
                    label = if (isMuted) "取消静音" else "静音",
                    onClick = onMuteToggle,
                ),
                HhhlOverflowMenuAction(
                    label = if (isBlocking) "取消拉黑" else "拉黑",
                    destructive = !isBlocking,
                    onClick = { blockDialogOpen = true },
                ),
                HhhlOverflowMenuAction(
                    label = "举报用户",
                    destructive = true,
                    onClick = { reportDialogOpen = true },
                ),
            ),
        )
    }

    if (blockDialogOpen) {
        AlertDialog(
            onDismissRequest = { blockDialogOpen = false },
            title = { Text(if (isBlocking) "取消拉黑" else "拉黑用户") },
            text = {
                Text(
                    text = if (isBlocking) {
                        "取消拉黑后，对方将不再处于你的屏蔽列表。"
                    } else {
                        "拉黑后，你将减少看到对方内容，对方与你的互动也会被限制。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBlockToggle()
                        blockDialogOpen = false
                    },
                    enabled = !isChanging,
                ) {
                    Text(if (isChanging) "处理中" else if (isBlocking) "取消拉黑" else "拉黑")
                }
            },
            dismissButton = {
                TextButton(onClick = { blockDialogOpen = false }, enabled = !isChanging) {
                    Text("取消")
                }
            },
        )
    }

    if (reportDialogOpen) {
        AlertDialog(
            onDismissRequest = { reportDialogOpen = false },
            title = { Text("举报用户") },
            text = {
                Text(
                    text = "举报会提交给实例管理员处理。请只在账号存在骚扰、垃圾信息或违反规则时使用。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReportUser()
                        reportDialogOpen = false
                    },
                    enabled = !isChanging,
                ) {
                    Text(if (isChanging) "处理中" else "举报")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportDialogOpen = false }, enabled = !isChanging) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ProfileToolMenu(
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenRelationshipManagement: () -> Unit,
    onLogout: () -> Unit,
) {
    var appearanceDialogOpen by remember { mutableStateOf(false) }
    var logoutDialogOpen by remember { mutableStateOf(false) }
    HhhlOverflowMenu(
        actions = profileToolActions(
            onOpenAppearance = { appearanceDialogOpen = true },
            onOpenFlash = onOpenFlash,
            onOpenAnnouncements = onOpenAnnouncements,
            onOpenRelationshipManagement = onOpenRelationshipManagement,
            onLogout = { logoutDialogOpen = true },
        ),
        label = "打开更多个人工具",
    )

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
        AlertDialog(
            onDismissRequest = { logoutDialogOpen = false },
            title = { Text("退出登录") },
            text = {
                Text(
                    text = "退出后需要重新授权才能继续使用当前账号。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutDialogOpen = false
                        onLogout()
                    },
                ) {
                    Text("退出登录")
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutDialogOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
}

private fun profileToolActions(
    onOpenAppearance: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenRelationshipManagement: () -> Unit,
    onLogout: () -> Unit,
): List<HhhlOverflowMenuAction> {
    return listOf(
        HhhlOverflowMenuAction("外观", onClick = onOpenAppearance),
        HhhlOverflowMenuAction("Play", onClick = onOpenFlash),
        HhhlOverflowMenuAction("公告", onClick = onOpenAnnouncements),
        HhhlOverflowMenuAction("关系管理", onClick = onOpenRelationshipManagement),
        HhhlOverflowMenuAction("退出登录", destructive = true, onClick = onLogout),
    )
}

@Composable
private fun ProfileAppearanceDialog(
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
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
            TextButton(onClick = onDismiss) {
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = profileAppearanceSummaryLabel(selectedTheme, selectedTimelineDensity),
            color = LocalHhhlColors.current.subtleText,
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
    return listOf("外观", "Play", "公告", "关系管理", "退出登录")
}

@Composable
private fun ProfileBanner(bannerUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(LocalHhhlColors.current.mediaBackground),
    ) {
        bannerUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        Text(
            text = actionText ?: text,
            color = if (onAction != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = if (onAction != null) Modifier.clickable { onAction() } else Modifier,
        )
        if (actionText != null) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HhhlDivider()
}
