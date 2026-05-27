@file:OptIn(ExperimentalLayoutApi::class)

package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.AdminAbuseReport
import cc.hhhl.client.model.AdminAnnouncementSummary
import cc.hhhl.client.model.AdminInstanceSettings
import cc.hhhl.client.model.AdminRoleSummary
import cc.hhhl.client.model.AdminUserSummary
import cc.hhhl.client.state.AdminDashboardTab
import cc.hhhl.client.state.AdminDashboardUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar

@Composable
fun AdminDashboardScreen(
    state: AdminDashboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTabSelected: (AdminDashboardTab) -> Unit,
    onUserQueryChanged: (String) -> Unit,
    onSearchUsers: () -> Unit,
    onLoadUserRoles: (String) -> Unit,
    onResolveReport: (String, Boolean) -> Unit,
    onAnnouncementDraftChanged: (String, String) -> Unit,
    onCreateAnnouncement: () -> Unit,
    onEditAnnouncement: (AdminAnnouncementSummary) -> Unit,
    onCancelAnnouncementEdit: () -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "管理",
            supportingText = when {
                state.isLoading -> "同步中"
                state.reports.isNotEmpty() -> "${state.reports.size} 条待处理"
                else -> "后台概览"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlIconActionButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = if (state.isLoading) "同步中" else "刷新",
                    enabled = !state.isLoading,
                    emphasized = true,
                    onClick = onRefresh,
                )
            },
        )
        HhhlDivider()
        AdminTabRow(
            selectedTab = state.selectedTab,
            onTabSelected = onTabSelected,
        )
        HhhlDivider()
        if (state.isPermissionDenied) {
            AdminStatusRow(
                text = state.errorMessage ?: "当前账号没有管理权限",
                actionText = "重试",
                onAction = onRefresh,
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading) {
                item(contentType = "admin-status") {
                    AdminStatusRow(text = "正在加载管理数据...", loading = true)
                }
            }
            state.errorMessage?.let { message ->
                item(contentType = "admin-status") {
                    AdminStatusRow(text = message, actionText = "重试", onAction = onRefresh)
                }
            }
            state.actionMessage?.let { message ->
                item(contentType = "admin-status") { AdminStatusRow(text = message) }
            }
            when (state.selectedTab) {
                AdminDashboardTab.Reports -> reportsContent(
                    reports = state.reports,
                    pendingIds = state.pendingIds,
                    onResolveReport = onResolveReport,
                )
                AdminDashboardTab.Users -> usersContent(
                    users = state.users,
                    query = state.userQuery,
                    selectedUserId = state.selectedUserId,
                    selectedUserRoles = state.selectedUserRoles,
                    isSearching = state.isSearchingUsers,
                    onUserQueryChanged = onUserQueryChanged,
                    onSearchUsers = onSearchUsers,
                    onLoadUserRoles = onLoadUserRoles,
                )
                AdminDashboardTab.Roles -> rolesContent(state.roles)
                AdminDashboardTab.Announcements -> announcementsContent(
                    announcements = state.announcements,
                    titleDraft = state.announcementTitleDraft,
                    textDraft = state.announcementTextDraft,
                    editingAnnouncementId = state.editingAnnouncementId,
                    pendingIds = state.pendingIds,
                    onAnnouncementDraftChanged = onAnnouncementDraftChanged,
                    onCreateAnnouncement = onCreateAnnouncement,
                    onEditAnnouncement = onEditAnnouncement,
                    onCancelAnnouncementEdit = onCancelAnnouncementEdit,
                    onDeleteAnnouncement = onDeleteAnnouncement,
                )
                AdminDashboardTab.Instance -> instanceContent(state.instance)
            }
        }
    }
}

@Composable
private fun AdminTabRow(
    selectedTab: AdminDashboardTab,
    onTabSelected: (AdminDashboardTab) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AdminDashboardTab.entries.forEach { tab ->
            HhhlActionChip(
                label = tab.label,
                emphasized = tab == selectedTab,
                onClick = { onTabSelected(tab) },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportsContent(
    reports: List<AdminAbuseReport>,
    pendingIds: Set<String>,
    onResolveReport: (String, Boolean) -> Unit,
) {
    if (reports.isEmpty()) {
        item(contentType = "admin-status") { AdminStatusRow(text = "暂无待处理举报") }
    }
    items(
        items = reports,
        key = { it.id },
        contentType = { "admin-report" },
    ) { report ->
        ReportRow(
            report = report,
            isPending = pendingIds.contains(report.id),
            onResolveReport = onResolveReport,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.usersContent(
    users: List<AdminUserSummary>,
    query: String,
    selectedUserId: String?,
    selectedUserRoles: List<AdminRoleSummary>,
    isSearching: Boolean,
    onUserQueryChanged: (String) -> Unit,
    onSearchUsers: () -> Unit,
    onLoadUserRoles: (String) -> Unit,
) {
    item(contentType = "admin-user-search") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HhhlTextInput(
                value = query,
                onValueChange = onUserQueryChanged,
                placeholder = "用户名、ID 或邮箱",
                singleLine = true,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Search,
                contentDescription = if (isSearching) "搜索中" else "搜索用户",
                enabled = !isSearching,
                emphasized = true,
                onClick = onSearchUsers,
            )
        }
        HhhlDivider()
    }
    if (users.isEmpty()) {
        item(contentType = "admin-status") { AdminStatusRow(text = "没有用户结果") }
    }
    items(
        items = users,
        key = { it.id },
        contentType = { "admin-user" },
    ) { user ->
        UserRow(
            user = user,
            selectedRoles = if (selectedUserId == user.id) selectedUserRoles else emptyList(),
            onLoadUserRoles = onLoadUserRoles,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rolesContent(
    roles: List<AdminRoleSummary>,
) {
    if (roles.isEmpty()) {
        item(contentType = "admin-status") { AdminStatusRow(text = "没有可显示的角色") }
    }
    items(
        items = roles,
        key = { it.id },
        contentType = { "admin-role" },
    ) { role ->
        RoleRow(role = role)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.announcementsContent(
    announcements: List<AdminAnnouncementSummary>,
    titleDraft: String,
    textDraft: String,
    editingAnnouncementId: String?,
    pendingIds: Set<String>,
    onAnnouncementDraftChanged: (String, String) -> Unit,
    onCreateAnnouncement: () -> Unit,
    onEditAnnouncement: (AdminAnnouncementSummary) -> Unit,
    onCancelAnnouncementEdit: () -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
) {
    item(contentType = "admin-announcement-editor") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HhhlTextInput(
                value = titleDraft,
                onValueChange = { onAnnouncementDraftChanged(it, textDraft) },
                placeholder = "公告标题",
                singleLine = true,
            )
            HhhlTextInput(
                value = textDraft,
                onValueChange = { onAnnouncementDraftChanged(titleDraft, it) },
                placeholder = "公告内容",
                minLines = 3,
                maxLines = 6,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HhhlActionChip(
                    label = if (editingAnnouncementId == null) "创建公告" else "保存公告",
                    emphasized = true,
                    onClick = onCreateAnnouncement,
                )
                if (editingAnnouncementId != null) {
                    HhhlActionChip(
                        label = "取消",
                        onClick = onCancelAnnouncementEdit,
                    )
                }
            }
        }
        HhhlDivider()
    }
    if (announcements.isEmpty()) {
        item(contentType = "admin-status") { AdminStatusRow(text = "没有可显示的公告") }
    }
    items(
        items = announcements,
        key = { it.id },
        contentType = { "admin-announcement" },
    ) { announcement ->
        AnnouncementAdminRow(
            announcement = announcement,
            isPending = pendingIds.contains(announcement.id),
            isEditing = editingAnnouncementId == announcement.id,
            onEditAnnouncement = onEditAnnouncement,
            onDeleteAnnouncement = onDeleteAnnouncement,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.instanceContent(
    instance: AdminInstanceSettings?,
) {
    if (instance == null) {
        item(contentType = "admin-status") { AdminStatusRow(text = "实例设置不可用") }
        return
    }
    item(contentType = "admin-instance") {
        AdminKeyValueBlock(
            rows = listOf(
                "名称" to instance.name,
                "版本" to instance.version,
                "描述" to instance.description,
                "维护者" to instance.maintainerName,
                "邮箱" to instance.maintainerEmail,
                "注册" to when (instance.enableRegistration) {
                    true -> "允许"
                    false -> "关闭"
                    null -> "未知"
                },
                "注册邮箱" to when (instance.emailRequiredForSignup) {
                    true -> "需要"
                    false -> "不需要"
                    null -> "未知"
                },
                "条款" to instance.tosUrl,
            ),
        )
    }
}

@Composable
private fun ReportRow(
    report: AdminAbuseReport,
    isPending: Boolean,
    onResolveReport: (String, Boolean) -> Unit,
) {
    AdminListRow(
        title = report.targetUserName.ifBlank { report.targetUserId },
        subtitle = report.comment.ifBlank { "没有附加说明" },
        meta = "举报人 ${report.reporterName} · ${report.createdAtLabel}",
        action = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HhhlActionChip(
                    label = if (isPending) "处理中" else "处理",
                    enabled = !isPending,
                    emphasized = true,
                    onClick = { onResolveReport(report.id, false) },
                )
                HhhlActionChip(
                    label = "转发",
                    enabled = !isPending,
                    onClick = { onResolveReport(report.id, true) },
                )
            }
        },
    )
}

@Composable
private fun UserRow(
    user: AdminUserSummary,
    selectedRoles: List<AdminRoleSummary>,
    onLoadUserRoles: (String) -> Unit,
) {
    val badges = buildList {
        if (user.isAdmin) add("管理员")
        if (user.isModerator) add("审核员")
        if (user.isSuspended) add("已停用")
        if (user.isSilenced) add("已静音")
    }
    AdminListRow(
        title = user.displayName.ifBlank { "@${user.username}" },
        subtitle = listOf("@${user.username}", user.host.orEmpty()).filter { it.isNotBlank() }.joinToString("@"),
        meta = listOf(
            "${user.notesCount} 动态",
            "${user.followersCount} 粉丝",
            user.createdAtLabel,
        ).filter { it.isNotBlank() }.joinToString(" · "),
        badges = badges,
        onClick = { onLoadUserRoles(user.id) },
    )
    if (selectedRoles.isNotEmpty()) {
        AdminStatusRow(text = "角色：" + selectedRoles.joinToString("、") { it.name })
    }
}

@Composable
private fun RoleRow(role: AdminRoleSummary) {
    val badges = buildList {
        if (role.isAdministratorRole) add("管理")
        if (role.isModeratorRole) add("审核")
        if (role.usersCount > 0) add("${role.usersCount} 人")
    }
    AdminListRow(
        title = role.name,
        subtitle = role.description.ifBlank { "没有描述" },
        meta = role.id,
        badges = badges,
    )
}

@Composable
private fun AnnouncementAdminRow(
    announcement: AdminAnnouncementSummary,
    isPending: Boolean,
    isEditing: Boolean,
    onEditAnnouncement: (AdminAnnouncementSummary) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
) {
    AdminListRow(
        title = announcement.title,
        subtitle = announcement.text,
        meta = listOf(announcement.display, announcement.createdAtLabel).filter { it.isNotBlank() }.joinToString(" · "),
        badges = if (announcement.isActive) listOf("生效中") else listOf("已停用"),
        action = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HhhlActionChip(
                    label = if (isEditing) "编辑中" else "编辑",
                    enabled = !isPending,
                    emphasized = isEditing,
                    onClick = { onEditAnnouncement(announcement) },
                )
                HhhlActionChip(
                    label = if (isPending) "删除中" else "删除",
                    enabled = !isPending,
                    onClick = { onDeleteAnnouncement(announcement.id) },
                )
            }
        },
    )
}

@Composable
private fun AdminListRow(
    title: String,
    subtitle: String,
    meta: String,
    badges: List<String> = emptyList(),
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    HhhlInlinePanel(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title.ifBlank { "未命名" },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badges.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        badges.forEach { badge ->
                            Text(
                                text = badge,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            action?.invoke()
        }
    }
}

@Composable
private fun AdminKeyValueBlock(rows: List<Pair<String, String>>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = key,
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.32f),
                )
                Text(
                    text = value.ifBlank { "未设置" },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.68f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HhhlDivider()
        }
    }
}

@Composable
private fun AdminStatusRow(
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
