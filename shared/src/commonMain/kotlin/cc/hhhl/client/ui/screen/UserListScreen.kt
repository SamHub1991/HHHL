package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.UserList
import cc.hhhl.client.model.UserListDraft
import cc.hhhl.client.state.UserListUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity

@Composable
fun UserListScreen(
    state: UserListUiState? = null,
    onBack: () -> Unit,
    onRefreshLists: () -> Unit = {},
    onRefreshTimeline: () -> Unit = {},
    onCreateList: (UserListDraft) -> Unit = {},
    onUpdateSelectedList: (UserListDraft) -> Unit = {},
    onDeleteSelectedList: () -> Unit = {},
    onAddUserToSelectedList: (String) -> Unit = {},
    onRemoveUserFromSelectedList: (String) -> Unit = {},
    onSelectList: (UserList) -> Unit = {},
    onLoadMore: () -> Unit = {},
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
    val lists = state?.lists.orEmpty()
    val selectedList = state?.selectedList ?: lists.firstOrNull()
    val notes = state?.notes.orEmpty()
    val listState = rememberLazyListState()
    var editorMode by remember { mutableStateOf<UserListEditorMode?>(null) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var removeMemberId by remember(selectedList?.id) { mutableStateOf<String?>(null) }
    var memberUserId by remember(selectedList?.id) { mutableStateOf("") }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = notes.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "列表",
            supportingText = selectedList?.name?.ifBlank { null } ?: "自定义时间线",
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserListCreateButton(
                        isMutating = state?.isMutatingList == true,
                        onClick = { editorMode = UserListEditorMode.Create },
                    )
                    HhhlOverflowMenu(
                        actions = userListSummaryActions(
                            hasSelectedList = selectedList != null,
                            isLoadingLists = state?.isLoadingLists == true,
                            isLoadingTimeline = state?.isLoadingTimeline == true,
                            onRefreshLists = onRefreshLists,
                            onRefreshTimeline = onRefreshTimeline,
                        ),
                    )
                }
            },
        )
        HhhlDivider()
        UserListSummaryRow(
            listCount = lists.size,
            selectedList = selectedList,
            isLoadingLists = state?.isLoadingLists == true,
            isLoadingTimeline = state?.isLoadingTimeline == true,
        )
        HhhlDivider()
        if (lists.isNotEmpty()) {
            UserListPickerRow(
                lists = lists,
                selectedList = selectedList,
                isLoading = state?.isLoadingLists == true,
                onSelectList = onSelectList,
            )
            HhhlDivider()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item(contentType = "user-list-status") {
                    UserListStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshLists,
                    )
                }
            }
            if (state?.isLoadingLists == true && lists.isEmpty()) {
                item(contentType = "user-list-status") {
                    UserListStatusRow(text = "正在加载列表...", loading = true)
                }
            }
            if (state != null && !state.isLoadingLists && lists.isEmpty() && state.errorMessage == null) {
                item(contentType = "user-list-status") { UserListStatusRow(text = "还没有创建或收藏列表") }
            }
            if (state?.isLoadingTimeline == true && notes.isEmpty()) {
                item(contentType = "user-list-status") {
                    UserListStatusRow(text = "正在加载列表时间线...", loading = true)
                }
            }
            selectedList?.let { list ->
                item(contentType = "user-list-header") {
                    UserListHeaderRow(
                        list = list,
                        isMutating = state?.isMutatingList == true,
                        isMutatingMembers = state?.isMutatingMembers == true,
                        memberUserId = memberUserId,
                        onMemberUserIdChange = { memberUserId = it },
                        onEdit = { editorMode = UserListEditorMode.Edit },
                        onDelete = { deleteDialogOpen = true },
                        onAddMember = {
                            onAddUserToSelectedList(memberUserId)
                            memberUserId = ""
                        },
                        onRemoveMember = { removeMemberId = it },
                    )
                }
            }
            state?.timelineErrorMessage?.let { message ->
                item(contentType = "user-list-status") {
                    UserListStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshTimeline,
                    )
                }
            }
            if (
                state != null &&
                selectedList != null &&
                !state.isLoadingTimeline &&
                notes.isEmpty() &&
                state.timelineErrorMessage == null
            ) {
                item(contentType = "user-list-status") { UserListStatusRow(text = "这个列表还没有动态") }
            }
            items(
                items = notes,
                key = { "user-list-note-${it.id}" },
                contentType = { "user-list-note" },
            ) { note ->
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
            if (state != null && notes.isNotEmpty() && state.isLoadingMore) {
                item(contentType = "user-list-status") {
                    UserListStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }

    when (editorMode) {
        UserListEditorMode.Create -> UserListEditorDialog(
            title = "新建列表",
            isMutating = state?.isMutatingList == true,
            onDismiss = { editorMode = null },
            onSubmit = {
                onCreateList(it)
                editorMode = null
            },
        )
        UserListEditorMode.Edit -> selectedList?.let { list ->
            UserListEditorDialog(
                title = "编辑列表",
                initialList = list,
                isMutating = state?.isMutatingList == true,
                onDismiss = { editorMode = null },
                onSubmit = {
                    onUpdateSelectedList(it)
                    editorMode = null
                },
            )
        }
        null -> Unit
    }
    if (deleteDialogOpen && selectedList != null) {
        DeleteUserListDialog(
            list = selectedList,
            isMutating = state?.isMutatingList == true,
            onDismiss = { deleteDialogOpen = false },
            onDelete = {
                onDeleteSelectedList()
                deleteDialogOpen = false
            },
        )
    }
    removeMemberId?.let { userId ->
        RemoveUserListMemberDialog(
            userId = userId,
            isMutating = state?.isMutatingMembers == true,
            onDismiss = { removeMemberId = null },
            onRemove = {
                onRemoveUserFromSelectedList(userId)
                removeMemberId = null
            },
        )
    }
}

private enum class UserListEditorMode {
    Create,
    Edit,
}

@Composable
private fun UserListSummaryRow(
    listCount: Int,
    selectedList: UserList?,
    isLoadingLists: Boolean,
    isLoadingTimeline: Boolean,
) {
    val titleText = listOfNotNull(
        selectedList?.name?.ifBlank { "未命名列表" } ?: "未选择",
        selectedList?.let { "${it.memberCount} 人 · ${if (it.isPublic) "公开" else "私密"}" },
    ).joinToString(" · ")
    val stateText = when {
        isLoadingLists -> "加载列表中"
        isLoadingTimeline -> "加载动态中"
        else -> "${listCount} 个列表"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$titleText · $stateText",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UserListCreateButton(
    isMutating: Boolean,
    onClick: () -> Unit,
) {
    HhhlIconActionButton(
        icon = Icons.Filled.Add,
        contentDescription = if (isMutating) "正在新建列表" else "新建列表",
        onClick = onClick,
        enabled = !isMutating,
        emphasized = true,
    )
}

fun userListSummaryActions(
    hasSelectedList: Boolean,
    isLoadingLists: Boolean,
    isLoadingTimeline: Boolean,
    onRefreshLists: () -> Unit,
    onRefreshTimeline: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isLoadingLists) "同步列表中" else "刷新列表",
        enabled = !isLoadingLists,
        onClick = onRefreshLists,
    ),
    HhhlOverflowMenuAction(
        label = if (isLoadingTimeline) "同步动态中" else "刷新动态",
        enabled = hasSelectedList && !isLoadingTimeline,
        onClick = onRefreshTimeline,
    ),
)

@Composable
private fun UserListHeaderRow(
    list: UserList,
    isMutating: Boolean,
    isMutatingMembers: Boolean,
    memberUserId: String,
    onMemberUserIdChange: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddMember: () -> Unit,
    onRemoveMember: (String) -> Unit,
) {
    HhhlInlinePanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name.ifBlank { "未命名列表" },
                    color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${list.memberCount} 人 · ${if (list.isPublic) "公开" else "私密"}",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HhhlActionChip(
                label = if (isMutating) "处理中" else "编辑",
                enabled = !isMutating,
                onClick = onEdit,
            )
            HhhlOverflowMenu(
                enabled = !isMutating,
                actions = userListHeaderActions(
                    isMutating = isMutating,
                    onDelete = onDelete,
                ),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhhlTextInput(
                value = memberUserId,
                onValueChange = onMemberUserIdChange,
                label = "用户 ID",
                placeholder = "输入 userId",
                singleLine = true,
                enabled = !isMutatingMembers,
                modifier = Modifier.weight(1f),
            )
            HhhlActionChip(
                label = if (isMutatingMembers) "处理中" else "添加成员",
                enabled = memberUserId.isNotBlank() && !isMutatingMembers,
                emphasized = true,
                onClick = onAddMember,
            )
        }
        if (list.userIds.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(
                    items = list.userIds,
                    key = { it },
                    contentType = { "user-list-member-chip" },
                ) { userId ->
                    Row(
                        modifier = Modifier
                            .background(
                                color = LocalHhhlColors.current.inputBackground.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(999.dp),
                            )
                            .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = userId,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HhhlOverflowMenu(
                            enabled = !isMutatingMembers,
                            actions = userListMemberActions(
                                onRemoveMember = { onRemoveMember(userId) },
                            ),
                            label = "成员操作",
                        )
                    }
                }
            }
        }
    }
    HhhlDivider()
}

fun userListHeaderActions(
    isMutating: Boolean,
    onDelete: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "删除",
        enabled = !isMutating,
        destructive = true,
        onClick = onDelete,
    ),
)

fun userListMemberActions(
    onRemoveMember: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "移除成员",
        destructive = true,
        onClick = onRemoveMember,
    ),
)

@Composable
private fun UserListEditorDialog(
    title: String,
    initialList: UserList? = null,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (UserListDraft) -> Unit,
) {
    var name by remember(initialList?.id) { mutableStateOf(initialList?.name.orEmpty()) }
    var isPublic by remember(initialList?.id) { mutableStateOf(initialList?.isPublic == true) }
    val canSubmit = name.isNotBlank() && !isMutating

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    placeholder = "列表名称",
                    singleLine = true,
                    enabled = !isMutating,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        enabled = !isMutating,
                    )
                    Text(
                        text = "公开列表",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = { onSubmit(UserListDraft(name = name, isPublic = isPublic)) },
                enabled = canSubmit,
            ) {
                Text(if (isMutating) "处理中" else "保存")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DeleteUserListDialog(
    list: UserList,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除列表") },
        text = {
            Text(
                text = "删除「${list.name.ifBlank { "未命名列表" }}」后，列表时间线会从这里移除。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onDelete, enabled = !isMutating, destructive = true) {
                Text(if (isMutating) "删除中" else "删除")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun RemoveUserListMemberDialog(
    userId: String,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移除成员") },
        text = {
            Text(
                text = "将「$userId」从当前列表移除，列表时间线会随之更新。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onRemove, enabled = !isMutating, destructive = true) {
                Text(if (isMutating) "移除中" else "移除")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun UserListPickerRow(
    lists: List<UserList>,
    selectedList: UserList?,
    isLoading: Boolean,
    onSelectList: (UserList) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading && lists.isNotEmpty()) {
            item(contentType = "user-list-picker-status") {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(
                        text = "加载中",
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(
            items = lists,
            key = { it.id },
            contentType = { "user-list-picker" },
        ) { list ->
            val active = selectedList?.id == list.id
            UserListPickerChip(
                list = list,
                active = active,
                onClick = { onSelectList(list) },
            )
        }
    }
}

@Composable
private fun UserListPickerChip(
    list: UserList,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 188.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else LocalHhhlColors.current.inputBackground,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = list.name.ifBlank { "未命名列表" },
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${list.memberCount} 人${if (list.isPublic) " · 公开" else ""}",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UserListStatusRow(
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
