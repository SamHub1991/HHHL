package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.CustomEmojiPicker
import cc.hhhl.client.ui.component.DriveFilePreview
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlCheckbox
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlDropdownMenu
import cc.hhhl.client.ui.component.HhhlDropdownMenuItem
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.aiResultMutedWordCandidate
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class AiAssistantMessage(
    val id: String,
    val role: AiAssistantRole,
    val text: String,
    val attachments: List<DriveFile> = emptyList(),
    val status: AiAssistantMessageStatus = AiAssistantMessageStatus.Completed,
    val retryPrompt: String = "",
    val retryAttachments: List<DriveFile> = emptyList(),
    val actions: List<AiAssistantActionProposal> = emptyList(),
)

data class AiAssistantActionProposal(
    val id: String,
    val kind: AiAssistantActionKind,
    val title: String,
    val description: String,
    val payload: String = "",
    val risk: AiAssistantActionRisk = kind.defaultRisk,
    val status: AiAssistantActionStatus = AiAssistantActionStatus.Pending,
    val statusDetail: String = "",
)

enum class AiAssistantActionKind(
    val label: String,
    val defaultRisk: AiAssistantActionRisk,
) {
    CreateAutomationDraft("创建自动化草稿", AiAssistantActionRisk.Draft),
    ReviewAutomationRisk("审核自动化风险", AiAssistantActionRisk.ReadOnly),
    OpenAutomation("打开自动化", AiAssistantActionRisk.ReadOnly),
    OpenAutomationLogs("打开执行日志", AiAssistantActionRisk.ReadOnly),
    OpenTimeline("打开时间线", AiAssistantActionRisk.ReadOnly),
    OpenDiscover("打开发现", AiAssistantActionRisk.ReadOnly),
    OpenDiscoverSearch("站内搜索", AiAssistantActionRisk.ReadOnly),
    OpenNotifications("打开通知", AiAssistantActionRisk.ReadOnly),
    OpenProfile("打开我的", AiAssistantActionRisk.ReadOnly),
    OpenProfileNotes("打开我的帖子", AiAssistantActionRisk.ReadOnly),
    OpenSettings("打开设置", AiAssistantActionRisk.ReadOnly),
    OpenAiSettings("打开 AI 设置", AiAssistantActionRisk.ReadOnly),
    OpenReleaseNotes("打开更新日志", AiAssistantActionRisk.ReadOnly),
    OpenThemeCustomization("打开主题自定义", AiAssistantActionRisk.ReadOnly),
    OpenCompose("打开发帖页", AiAssistantActionRisk.Draft),
    OpenChat("打开聊天", AiAssistantActionRisk.ReadOnly),
    OpenDrive("打开网盘", AiAssistantActionRisk.ReadOnly),
    OpenAdminDashboard("打开管理后台", AiAssistantActionRisk.ReadOnly),
    OpenAchievements("打开成就", AiAssistantActionRisk.ReadOnly),
    OpenFavoriteNotes("打开收藏", AiAssistantActionRisk.ReadOnly),
    OpenUserLists("打开列表", AiAssistantActionRisk.ReadOnly),
    OpenFollowRequests("打开关注请求", AiAssistantActionRisk.ReadOnly),
    OpenRelationshipManagement("打开关系管理", AiAssistantActionRisk.ReadOnly),
    OpenAntennas("打开天线", AiAssistantActionRisk.ReadOnly),
    OpenClips("打开剪辑", AiAssistantActionRisk.ReadOnly),
    OpenChannels("打开频道", AiAssistantActionRisk.ReadOnly),
    OpenPages("打开页面", AiAssistantActionRisk.ReadOnly),
    OpenGallery("打开相册", AiAssistantActionRisk.ReadOnly),
    OpenFlash("打开 Flash", AiAssistantActionRisk.ReadOnly),
    OpenAnnouncements("打开公告", AiAssistantActionRisk.ReadOnly),
    RefreshCurrentView("刷新当前页面", AiAssistantActionRisk.ReadOnly),
    CheckForUpdates("检查更新", AiAssistantActionRisk.ReadOnly),
    RunChatSummary("生成聊天室摘要", AiAssistantActionRisk.ReadOnly),
    CreateForwardTemplateDraft("创建转发模板草稿", AiAssistantActionRisk.RequiresConfirmation),
    FillChatDraft("填入聊天草稿", AiAssistantActionRisk.Draft),
    SendChatDraft("发送聊天草稿", AiAssistantActionRisk.RequiresConfirmation),
    FillComposeDraft("填入发帖草稿", AiAssistantActionRisk.Draft),
    PublishComposeDraft("发布发帖草稿", AiAssistantActionRisk.RequiresConfirmation),
    MarkNotificationsRead("通知全部已读", AiAssistantActionRisk.RequiresConfirmation),
    OpenWebhookManagement("打开 Webhook 管理", AiAssistantActionRisk.ReadOnly),
    ReviewBulkOperation("审核批量操作", AiAssistantActionRisk.ReadOnly),
    ClearCurrentChatRoomMessages("清空当前聊天室", AiAssistantActionRisk.HighRisk),
    DeleteCurrentChatRoom("删除当前聊天室", AiAssistantActionRisk.HighRisk),
    LeaveCurrentChatRoom("退出当前聊天室", AiAssistantActionRisk.HighRisk),
    MuteCurrentChatRoom("静音当前/指定聊天室", AiAssistantActionRisk.RequiresConfirmation),
    UnmuteCurrentChatRoom("取消静音当前/指定聊天室", AiAssistantActionRisk.RequiresConfirmation),
    FavoriteCurrentNote("收藏当前帖子", AiAssistantActionRisk.RequiresConfirmation),
    UnfavoriteCurrentNote("取消收藏当前帖子", AiAssistantActionRisk.RequiresConfirmation),
    ReactCurrentNote("点赞当前帖子", AiAssistantActionRisk.RequiresConfirmation),
    DeleteReactionCurrentNote("取消点赞当前帖子", AiAssistantActionRisk.RequiresConfirmation),
    RenoteCurrentNote("转发当前帖子", AiAssistantActionRisk.RequiresConfirmation),
    DeleteCurrentNote("删除当前帖子", AiAssistantActionRisk.HighRisk),
    ReviewCurrentPageAction("审核当前页面操作", AiAssistantActionRisk.RequiresConfirmation),
    AddMutedWord("添加静音词", AiAssistantActionRisk.RequiresConfirmation),
    CopyChecklist("复制为清单", AiAssistantActionRisk.ReadOnly),
    OpenWebSearch("打开网络搜索", AiAssistantActionRisk.RequiresConfirmation),
    SaveMemory("保存记忆", AiAssistantActionRisk.Draft),
}

enum class AiAssistantActionRisk(val label: String) {
    ReadOnly("只读"),
    Draft("草稿"),
    RequiresConfirmation("需确认"),
    HighRisk("高风险"),
}

enum class AiAssistantActionStatus(val label: String) {
    Pending("待确认"),
    Running("执行中"),
    Approved("已执行"),
    Failed("未执行"),
    Rejected("已拒绝"),
}

data class AiAssistantAutoApprovalSettings(
    val lowRiskEnabled: Boolean = true,
    val highRiskEnabled: Boolean = false,
)

fun AiAssistantActionProposal.canAutoApprove(settings: AiAssistantAutoApprovalSettings): Boolean {
    if (status != AiAssistantActionStatus.Pending) return false
    return when (risk) {
        AiAssistantActionRisk.ReadOnly,
        AiAssistantActionRisk.Draft,
            -> settings.lowRiskEnabled
        AiAssistantActionRisk.RequiresConfirmation,
        AiAssistantActionRisk.HighRisk,
            -> settings.highRiskEnabled
    }
}

enum class AiAssistantRole(val label: String) {
    User("我"),
    Assistant("AI"),
    System("系统"),
}

enum class AiAssistantMessageStatus {
    Sending,
    Completed,
    Failed,
}

@Composable
fun AiAssistantScreen(
    messages: List<AiAssistantMessage>,
    draft: String,
    contextSummary: String,
    aiEnabled: Boolean,
    isProcessing: Boolean,
    errorMessage: String?,
    attachments: List<DriveFile> = emptyList(),
    isUploadingAttachment: Boolean = false,
    isMediaPickerAvailable: Boolean = false,
    customEmojis: List<CustomEmoji> = emptyList(),
    recentEmojiCodes: List<String> = emptyList(),
    memoryNotes: List<String> = emptyList(),
    autoApprovalSettings: AiAssistantAutoApprovalSettings = AiAssistantAutoApprovalSettings(),
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onRetry: (String, List<DriveFile>) -> Unit,
    onNewConversation: () -> Unit,
    onAddImage: () -> Unit = {},
    onAddFile: () -> Unit = {},
    onOpenDrivePicker: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onOpenAttachmentUrl: (String) -> Unit = {},
    onOpenAutomation: () -> Unit,
    onAutoApprovalSettingsChanged: (AiAssistantAutoApprovalSettings) -> Unit = {},
    onApproveAction: (String) -> Unit = {},
    onRejectAction: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex + 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "AI 助手",
            supportingText = if (aiEnabled) aiAssistantAutoApprovalSupportingText(autoApprovalSettings) else "AI 未配置",
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HhhlIconActionButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "新建对话",
                        enabled = messages.isNotEmpty() || draft.isNotBlank(),
                        onClick = onNewConversation,
                    )
                    AiAssistantOverflowMenu(
                        settings = autoApprovalSettings,
                        onSettingsChanged = onAutoApprovalSettingsChanged,
                    )
                }
            },
        )
        errorMessage?.let { message ->
            HhhlStatusRow(text = message)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "assistant-context") {
                AiAssistantContextPanel(
                    contextSummary = contextSummary,
                    memoryNotes = memoryNotes,
                    enabled = aiEnabled && !isProcessing,
                    onSendPrompt = onSendPrompt,
                    onOpenAutomation = onOpenAutomation,
                )
            }
            if (messages.isEmpty()) {
                item(key = "assistant-empty") {
                    AiAssistantMessageBubble(
                        message = AiAssistantMessage(
                            id = "assistant-empty-message",
                            role = AiAssistantRole.Assistant,
                            text = "可以直接问我当前聊天、自动化规则、更新问题或让它先生成草稿。低风险动作会按右上角设置自动批准；开启高风险自动批准后，已支持的发送、发布、删除和管理动作会直接执行。",
                        ),
                        onRetry = onRetry,
                        onApproveAction = onApproveAction,
                        onRejectAction = onRejectAction,
                        onOpenAttachmentUrl = onOpenAttachmentUrl,
                    )
                }
            }
            items(
                items = messages,
                key = { message -> message.id },
            ) { message ->
                AiAssistantMessageBubble(
                    message = message,
                    onRetry = onRetry,
                    onApproveAction = onApproveAction,
                    onRejectAction = onRejectAction,
                    onOpenAttachmentUrl = onOpenAttachmentUrl,
                )
            }
        }
        HhhlDivider()
        AiAssistantComposer(
            draft = draft,
            enabled = aiEnabled && !isProcessing,
            isProcessing = isProcessing,
            onDraftChanged = onDraftChanged,
            onSend = onSend,
            attachments = attachments,
            isUploadingAttachment = isUploadingAttachment,
            isMediaPickerAvailable = isMediaPickerAvailable,
            customEmojis = customEmojis,
            recentEmojiCodes = recentEmojiCodes,
            onAddImage = onAddImage,
            onAddFile = onAddFile,
            onOpenDrivePicker = onOpenDrivePicker,
            onRemoveAttachment = onRemoveAttachment,
            onOpenAttachmentUrl = onOpenAttachmentUrl,
        )
    }
}

@Composable
private fun AiAssistantOverflowMenu(
    settings: AiAssistantAutoApprovalSettings,
    onSettingsChanged: (AiAssistantAutoApprovalSettings) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
    Box {
        HhhlIconActionButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "助手设置",
            onClick = { expanded = true },
        )
        HhhlDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 236.dp, max = 292.dp),
        ) {
            AiAssistantApprovalMenuItem(
                checked = settings.lowRiskEnabled,
                title = "低风险默认批准",
                description = "只读和草稿动作自动执行",
                onCheckedChange = { checked ->
                    onSettingsChanged(
                        settings.copy(
                            lowRiskEnabled = checked,
                        ),
                    )
                },
            )
            AiAssistantApprovalMenuItem(
                checked = settings.highRiskEnabled,
                title = "高风险也自动批准",
                description = "发送、发布、删除和管理动作会直接执行",
                danger = true,
                onCheckedChange = { checked ->
                    onSettingsChanged(
                        settings.copy(
                            highRiskEnabled = checked,
                        ),
                    )
                },
            )
            Text(
                text = "设置只影响 AI 助手建议出的动作，不改变系统权限。",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AiAssistantApprovalMenuItem(
    checked: Boolean,
    title: String,
    description: String,
    danger: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlDropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = if (danger) colors.danger else colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingIcon = {
            HhhlCheckbox(
                checked = checked,
                onCheckedChange = null,
            )
        },
        destructive = danger,
        onClick = { onCheckedChange(!checked) },
    )
}

private fun aiAssistantAutoApprovalSupportingText(settings: AiAssistantAutoApprovalSettings): String {
    return when {
        settings.highRiskEnabled -> "远端 AI · 高风险也自动批准"
        settings.lowRiskEnabled -> "远端 AI · 低风险自动批准"
        else -> "远端 AI · 全部需确认"
    }
}

@Composable
private fun AiAssistantContextPanel(
    contextSummary: String,
    memoryNotes: List<String>,
    enabled: Boolean,
    onSendPrompt: (String) -> Unit,
    onOpenAutomation: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = contextSummary,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (memoryNotes.isNotEmpty()) {
            Text(
                text = "记忆：" + memoryNotes.takeLast(2).joinToString("；") { it.take(42) },
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HhhlActionChip(
                label = "调试规则",
                enabled = enabled,
                onClick = { onSendPrompt("结合最近自动化日志和调试记录，帮我判断哪些规则可能没触发，以及下一步应该检查什么。") },
            )
            HhhlActionChip(
                label = "创建草稿",
                enabled = enabled,
                onClick = { onSendPrompt("我想创建一条自动化规则。请先问清触发来源、条件、动作和风险，不要直接执行。") },
            )
            HhhlActionChip(
                label = "聊天室摘要",
                enabled = enabled,
                onClick = { onSendPrompt("如果当前有选中的聊天室，请总结最近消息、待办和需要我回复的人；如果没有，请告诉我先进入哪个聊天室。") },
            )
            HhhlActionChip(
                label = "转发模板",
                enabled = enabled,
                onClick = { onSendPrompt("帮我设计聊天室转发模板，包含来源聊天室、发送者、摘要、原文链接和防循环注意事项。") },
            )
            HhhlActionChip(
                label = "消息改写",
                enabled = enabled,
                onClick = { onSendPrompt("帮我把当前要发送或转发的内容改写得更简短清楚，只生成草稿，不要发送。") },
            )
            HhhlActionChip(
                label = "写帖子",
                enabled = enabled,
                onClick = { onSendPrompt("帮我写一条帖子草稿。先根据当前上下文给出正文，确认后只填入发帖框，不要直接发布。") },
            )
            HhhlActionChip(
                label = "通知",
                enabled = enabled,
                onClick = { onSendPrompt("打开通知页，并帮我判断未读通知里哪些需要处理。") },
            )
            HhhlActionChip(
                label = "刷新",
                enabled = enabled,
                onClick = { onSendPrompt("帮我刷新当前页面，如果涉及发送或标记已读等写操作，需要先让我批准。") },
            )
            HhhlActionChip(
                label = "设置",
                enabled = enabled,
                onClick = { onSendPrompt("打开设置页，帮我检查 AI 权限、后台通知和更新相关配置。") },
            )
            HhhlActionChip(
                label = "网络搜索",
                enabled = enabled,
                onClick = { onSendPrompt("我想做一次网络搜索，请根据我接下来给出的关键词准备搜索查询，打开外部搜索前需要我确认。") },
            )
            HhhlActionChip(
                label = "自动化",
                onClick = onOpenAutomation,
            )
        }
    }
}

@Composable
private fun AiAssistantMessageBubble(
    message: AiAssistantMessage,
    onRetry: (String, List<DriveFile>) -> Unit,
    onApproveAction: (String) -> Unit,
    onRejectAction: (String) -> Unit,
    onOpenAttachmentUrl: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    val isUser = message.role == AiAssistantRole.User
    val isFailed = message.status == AiAssistantMessageStatus.Failed
    var streamingFrame by remember(message.id) { mutableStateOf(0) }
    LaunchedEffect(message.id, message.status) {
        if (message.status != AiAssistantMessageStatus.Sending) return@LaunchedEffect
        while (true) {
            delay(360)
            streamingFrame = (streamingFrame + 1) % AI_ASSISTANT_STREAMING_FRAMES
        }
    }
    val displayText = if (message.status == AiAssistantMessageStatus.Sending) {
        aiAssistantStreamingText(streamingFrame)
    } else {
        message.text
    }
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(aiAssistantBubbleColor(message, colors.buttonSelectedBackground, colors.inputBackground, colors.danger))
                .border(
                    width = 1.dp,
                    color = if (isFailed) colors.danger.copy(alpha = 0.26f) else colors.border.copy(alpha = 0.26f),
                    shape = RoundedCornerShape(13.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isUser) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = if (isFailed) colors.danger else colors.accent,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = message.role.label,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (message.status == AiAssistantMessageStatus.Sending) {
                    Text(
                        text = "生成中",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            InlineRichText(
                text = displayText,
                color = if (isFailed) colors.danger else colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (message.attachments.isNotEmpty()) {
                AiAssistantAttachmentGrid(
                    attachments = message.attachments,
                    onRemoveAttachment = null,
                    onOpenAttachmentUrl = onOpenAttachmentUrl,
                )
            }
            if (isFailed && message.retryPrompt.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HhhlTextButton(onClick = { onRetry(message.retryPrompt, message.retryAttachments) }) {
                        Text("重试")
                    }
                }
            }
            message.actions.forEach { action ->
                AiAssistantActionCard(
                    action = action,
                    enabled = !isFailed && message.status != AiAssistantMessageStatus.Sending,
                    onApprove = { onApproveAction(action.id) },
                    onReject = { onRejectAction(action.id) },
                )
            }
        }
    }
}

@Composable
private fun AiAssistantActionCard(
    action: AiAssistantActionProposal,
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.58f))
            .border(1.dp, colors.border.copy(alpha = 0.26f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = action.title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${action.risk.label} · ${action.status.label}",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = action.description,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (action.status == AiAssistantActionStatus.Failed && action.statusDetail.isNotBlank()) {
            Text(
                text = action.statusDetail,
                color = colors.danger,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action.status == AiAssistantActionStatus.Pending || action.status == AiAssistantActionStatus.Failed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HhhlActionChip(
                    label = if (action.status == AiAssistantActionStatus.Failed) "重试" else "批准",
                    emphasized = true,
                    enabled = enabled,
                    onClick = onApprove,
                )
                HhhlActionChip(
                    label = "拒绝",
                    enabled = enabled,
                    onClick = onReject,
                )
            }
        }
    }
}

@Composable
private fun AiAssistantComposer(
    draft: String,
    enabled: Boolean,
    isProcessing: Boolean,
    attachments: List<DriveFile>,
    isUploadingAttachment: Boolean,
    isMediaPickerAvailable: Boolean,
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onAddImage: () -> Unit,
    onAddFile: () -> Unit,
    onOpenDrivePicker: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenAttachmentUrl: (String) -> Unit,
) {
    var emojiPickerOpen by remember { mutableStateOf(false) }
    val canEdit = enabled && !isUploadingAttachment
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (attachments.isNotEmpty()) {
            AiAssistantAttachmentGrid(
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
                onOpenAttachmentUrl = onOpenAttachmentUrl,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HhhlIconActionButton(
                icon = Icons.Filled.Image,
                contentDescription = if (isUploadingAttachment) "上传中" else "添加图片",
                enabled = canEdit && isMediaPickerAvailable,
                onClick = onAddImage,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.AttachFile,
                contentDescription = if (isUploadingAttachment) "上传中" else "添加文件",
                enabled = canEdit && isMediaPickerAvailable,
                onClick = onAddFile,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Folder,
                contentDescription = "从 Drive 选择",
                enabled = canEdit,
                onClick = onOpenDrivePicker,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.EmojiEmotions,
                contentDescription = if (emojiPickerOpen) "收起表情" else "选择表情",
                emphasized = emojiPickerOpen,
                enabled = enabled,
                onClick = { emojiPickerOpen = !emojiPickerOpen },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HhhlTextInput(
                value = draft,
                onValueChange = onDraftChanged,
                placeholder = when {
                    isUploadingAttachment -> "附件上传中"
                    enabled -> "问 AI 助手"
                    else -> "AI 不可用或正在处理"
                },
                minLines = 1,
                maxLines = 4,
                enabled = canEdit,
                modifier = Modifier.weight(1f),
            )
            HhhlIconActionButton(
                icon = Icons.AutoMirrored.Filled.Send,
                contentDescription = when {
                    isProcessing -> "生成中"
                    isUploadingAttachment -> "附件上传中"
                    else -> "发送给 AI 助手"
                },
                emphasized = true,
                enabled = canEdit && (draft.isNotBlank() || attachments.isNotEmpty()),
                onClick = onSend,
            )
        }
        if (emojiPickerOpen) {
            CustomEmojiPicker(
                customEmojis = customEmojis,
                recentEmojiCodes = recentEmojiCodes,
                onEmojiSelected = { emoji ->
                    onDraftChanged(draft + emoji)
                    emojiPickerOpen = false
                },
                compact = true,
            )
        }
    }
}

@Composable
private fun AiAssistantAttachmentGrid(
    attachments: List<DriveFile>,
    onRemoveAttachment: ((String) -> Unit)?,
    onOpenAttachmentUrl: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        attachments.forEach { file ->
            AiAssistantAttachmentItem(
                file = file,
                onRemoveAttachment = onRemoveAttachment,
                onOpenAttachmentUrl = onOpenAttachmentUrl,
            )
        }
    }
}

@Composable
private fun AiAssistantAttachmentItem(
    file: DriveFile,
    onRemoveAttachment: ((String) -> Unit)?,
    onOpenAttachmentUrl: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.56f))
            .border(1.dp, colors.border.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
            .padding(5.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DriveFilePreview(
                file = file,
                onOpenUrl = onOpenAttachmentUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
            Text(
                text = file.name.ifBlank { file.type.ifBlank { "附件" } },
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemoveAttachment != null) {
            HhhlIconActionButton(
                icon = Icons.Filled.Close,
                contentDescription = "移除附件",
                onClick = { onRemoveAttachment(file.id) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp),
            )
        }
    }
}

private fun aiAssistantBubbleColor(
    message: AiAssistantMessage,
    userColor: Color,
    assistantColor: Color,
    dangerColor: Color,
): Color {
    return when {
        message.status == AiAssistantMessageStatus.Failed -> dangerColor.copy(alpha = 0.08f)
        message.role == AiAssistantRole.User -> userColor.copy(alpha = 0.72f)
        message.role == AiAssistantRole.System -> assistantColor.copy(alpha = 0.42f)
        else -> assistantColor.copy(alpha = 0.62f)
    }
}

private const val AI_ASSISTANT_STREAMING_FRAMES = 4

fun aiAssistantStreamingText(frame: Int): String {
    val phase = when (frame.floorMod(AI_ASSISTANT_STREAMING_FRAMES)) {
        0 -> "正在读取当前上下文"
        1 -> "正在匹配可用工具"
        2 -> "正在生成回复草稿"
        else -> "正在准备需确认动作"
    }
    return "$phase${".".repeat(frame.floorMod(AI_ASSISTANT_STREAMING_FRAMES))}\n▌"
}

private fun Int.floorMod(mod: Int): Int {
    val value = this % mod
    return if (value < 0) value + mod else value
}

private val AiAssistantAutomationIntentRegex = Regex("创建.*(自动化|规则)|新建.*(自动化|规则)|生成.*(自动化|规则)|设计.*(自动化|规则)|建立.*(自动化|规则)|添加.*(自动化|规则)|自动回复|自动转发|转发规则|触发后|匹配后|调用.*webhook", RegexOption.IGNORE_CASE)
private val AiAssistantOpenAutomationIntentRegex = Regex("打开.*(自动化|规则)|进入.*(自动化|规则)|查看.*(自动化|规则)|自动化中心|规则中心|自动化页|规则页", RegexOption.IGNORE_CASE)
private val AiAssistantAutomationRiskIntentRegex = Regex("启用.*规则|关闭.*规则|禁用.*规则|删除.*规则|创建并启用|直接启用|自动转发|跨聊天室|调用.*webhook", RegexOption.IGNORE_CASE)
private val AiAssistantWebhookManagementIntentRegex = Regex("打开.*webhook|管理.*webhook|配置.*webhook|创建.*webhook|启用.*webhook|禁用.*webhook|删除.*webhook|测试.*webhook|webhook.*管理|webhook.*配置|webhook.*测试", RegexOption.IGNORE_CASE)
private val AiAssistantBulkIntentRegex = Regex("批量|全部删除|全部清空|全部取消|一键.*删除|一键.*清空|全部.*发送|群发|bulk", RegexOption.IGNORE_CASE)
private val AiAssistantChatRoomTargetWords = "(?:聊天室|房间|群聊|群)"
private val AiAssistantCurrentTargetWords = "(?:当前|这个|此|本|这间|已打开的|选中的)?"
private val AiAssistantCurrentChatConversationWords = "(?:(?:当前|这个|此|本|已打开的|选中的)(?:聊天|会话|对话)|这里|这儿)"
private val AiAssistantChatManagementTargetWords = "(?:$AiAssistantCurrentTargetWords$AiAssistantChatRoomTargetWords|$AiAssistantCurrentChatConversationWords)"
private val AiAssistantShortCommandLinePrefix = "(?:^|\\n)\\s*"
private val AiAssistantShortCommandLineSuffix = "\\s*(?:$|\\n)"
private val AiAssistantShortCurrentActionSuffix = "(?:(?:了|掉)吧?|吧|下|一下)"
private val AiAssistantClearCurrentChatRoomIntentRegex = Regex(
    "(清空|清掉|清除|清理|删除全部|全部删除|全部清空).*$AiAssistantChatManagementTargetWords.*(消息|聊天记录)?|$AiAssistantChatManagementTargetWords.*(消息|聊天记录|消息记录).*(清空|清掉|清除|清理|删除全部|全部删除|全部清空)|(清空|清掉|清除|清理|删除全部|全部删除|全部清空).*(当前|这个|此|本|已打开的|选中的)?(聊天记录|聊天消息|消息记录)|$AiAssistantShortCommandLinePrefix(清空|清掉|清除|清理|清)$AiAssistantShortCurrentActionSuffix$AiAssistantShortCommandLineSuffix",
    RegexOption.IGNORE_CASE,
)
private val AiAssistantDeleteCurrentChatRoomIntentRegex = Regex(
    "(删除|删掉|删了|删|移除|解散).*$AiAssistantCurrentTargetWords$AiAssistantChatRoomTargetWords|把.*$AiAssistantChatRoomTargetWords.*(删除|删掉|删了|删|移除|解散)|$AiAssistantShortCommandLinePrefix(删除|删|移除|解散)$AiAssistantShortCurrentActionSuffix$AiAssistantShortCommandLineSuffix",
    RegexOption.IGNORE_CASE,
)
private val AiAssistantLeaveCurrentChatRoomIntentRegex = Regex(
    "(退出|离开|退群|退房).*$AiAssistantChatManagementTargetWords|从.*$AiAssistantChatRoomTargetWords.*(退出|离开|退群|退房)|退群|退房|$AiAssistantShortCommandLinePrefix(退出|离开|退)$AiAssistantShortCurrentActionSuffix$AiAssistantShortCommandLineSuffix",
    RegexOption.IGNORE_CASE,
)
private val AiAssistantMuteCurrentChatRoomIntentRegex = Regex(
    "静音.*$AiAssistantChatManagementTargetWords|把.*$AiAssistantChatRoomTargetWords.*静音",
    RegexOption.IGNORE_CASE,
)
private val AiAssistantUnmuteCurrentChatRoomIntentRegex = Regex(
    "取消静音.*$AiAssistantChatManagementTargetWords|解除.*$AiAssistantChatRoomTargetWords.*静音",
    RegexOption.IGNORE_CASE,
)
private val AiAssistantFavoriteCurrentNoteIntentRegex = Regex("收藏.*(当前|这条|这个).*(帖子|动态|note)|把.*(当前|这条|这个).*(帖子|动态|note).*收藏|加入收藏.*(当前|这条|这个).*(帖子|动态|note)", RegexOption.IGNORE_CASE)
private val AiAssistantUnfavoriteCurrentNoteIntentRegex = Regex("取消收藏.*(当前|这条|这个).*(帖子|动态|note)|把.*(当前|这条|这个).*(帖子|动态|note).*取消收藏", RegexOption.IGNORE_CASE)
private val AiAssistantReactCurrentNoteIntentRegex = Regex("(点赞|喜欢|反应).*(当前|这条|这个).*(帖子|动态|note)|给.*(当前|这条|这个).*(帖子|动态|note).*(点赞|反应)", RegexOption.IGNORE_CASE)
private val AiAssistantDeleteReactionCurrentNoteIntentRegex = Regex("取消.*(点赞|反应).*(当前|这条|这个).*(帖子|动态|note)|把.*(当前|这条|这个).*(帖子|动态|note).*(取消点赞|取消反应)", RegexOption.IGNORE_CASE)
private val AiAssistantRenoteCurrentNoteIntentRegex = Regex("(转发|renote).*(当前|这条|这个).*(帖子|动态|note)|把.*(当前|这条|这个).*(帖子|动态|note).*(转发|renote)", RegexOption.IGNORE_CASE)
private val AiAssistantDeleteCurrentNoteIntentRegex = Regex("(删除|删掉|删了|移除).*(当前|这条|这个).*(帖子|动态|note)|把.*(当前|这条|这个).*(帖子|动态|note).*(删除|删掉|删了|移除)", RegexOption.IGNORE_CASE)
private val AiAssistantCurrentPageActionIntentRegex = Regex("点赞|反应|取消反应|收藏|取消收藏|转发帖子|取消转发|删除帖子|举报|投票|关注|取消关注|屏蔽|拉黑|解除屏蔽|静音用户|取消静音|上传|删除文件|删除文件夹|邀请|加入|退出|删除聊天室|删除消息|删除页面|删除相册|删除 Flash|删除公告|操作当前|帮我点|帮我处理", RegexOption.IGNORE_CASE)
private val AiAssistantDebugIntentRegex = Regex("调试|debug|日志|没触发|不触发|失败|为什么", RegexOption.IGNORE_CASE)
private val AiAssistantComposeIntentRegex = Regex("发帖|帖子草稿|写一条|写个|新建帖子|post", RegexOption.IGNORE_CASE)
private val AiAssistantPublishComposeIntentRegex = Regex("发布.*(帖子|草稿|频道)|发布到|直接发布|发送.*帖子|发出去.*帖子|把.*帖子.*发出去|发到.*频道|.{1,60}(?:频道|channel)(?:里面|里|中|内)?\\s*(?:发布|发帖|发|发送)|publish|send.*post", RegexOption.IGNORE_CASE)
private val AiAssistantChatSummaryIntentRegex = Regex("聊天室摘要|聊天摘要|总结.*聊天|总结.*聊天室|最近消息|未读摘要", RegexOption.IGNORE_CASE)
private val AiAssistantForwardTemplateIntentRegex = Regex("转发模板|聊天室转发|转发到|转发规则|forward", RegexOption.IGNORE_CASE)
private val AiAssistantChatRewriteIntentRegex = Regex("消息改写|改写消息|改写回复|润色回复|聊天回复|回复草稿", RegexOption.IGNORE_CASE)
private val AiAssistantSendChatIntentRegex = Regex("发送.*(消息|回复|聊天|草稿)|把.*(消息|回复|聊天草稿|当前聊天草稿|草稿).*(发送|发出去)|直接发出去|发出去.*(消息|回复|聊天|草稿)|草稿发送|send.*message|私聊.*(说|告诉|通知|发|回复)|发给.*(说|消息|回复|通知)|给.{1,40}(发消息|发个消息|发送消息|私信|私聊|说|回复|通知)|告诉(?!我).{1,80}|通知(?!页|列表|全部|已读|设置).{1,80}|在.*(聊天室|房间|群聊|群).*(说|发|发送|告诉|通知|回复)", RegexOption.IGNORE_CASE)
private val AiAssistantMutedIntentRegex = Regex("静音词|过滤词|屏蔽词|关键词过滤|mute", RegexOption.IGNORE_CASE)
private val AiAssistantWebSearchIntentRegex = Regex("网络搜索|外部搜索|网页搜索|浏览器搜索|google|web search", RegexOption.IGNORE_CASE)
private val AiAssistantDiscoverSearchIntentRegex = Regex("站内搜索|搜索帖子|搜帖子|找帖子|搜索用户|搜用户|找用户|搜索话题|搜话题|查找|search", RegexOption.IGNORE_CASE)
private val AiAssistantTimelineIntentRegex = Regex("时间线|首页|主页时间线|timeline", RegexOption.IGNORE_CASE)
private val AiAssistantDiscoverIntentRegex = Regex("发现页|发现|探索|discover", RegexOption.IGNORE_CASE)
private val AiAssistantNotificationsIntentRegex = Regex("通知|提醒|未读|notification", RegexOption.IGNORE_CASE)
private val AiAssistantProfileIntentRegex = Regex("我的|个人页|资料页|个人资料|profile", RegexOption.IGNORE_CASE)
private val AiAssistantProfileNotesIntentRegex = Regex("我的帖子|我发的帖子|个人帖子|发过的帖子|profile notes", RegexOption.IGNORE_CASE)
private val AiAssistantSettingsIntentRegex = Regex("设置|配置|权限|setting", RegexOption.IGNORE_CASE)
private val AiAssistantAiSettingsIntentRegex = Regex("AI 设置|AI配置|模型设置|模型配置|api key|apikey|Provider", RegexOption.IGNORE_CASE)
private val AiAssistantReleaseNotesIntentRegex = Regex("更新日志|版本记录|版本时间线|changelog|release", RegexOption.IGNORE_CASE)
private val AiAssistantThemeIntentRegex = Regex("主题自定义|自定义主题|主题配置|换主题|背景图|配色", RegexOption.IGNORE_CASE)
private val AiAssistantOpenComposeIntentRegex = Regex("打开发帖|发帖页|新建帖子页|写帖子页面|compose", RegexOption.IGNORE_CASE)
private val AiAssistantChatPageIntentRegex = Regex("聊天列表|聊天室列表|聊天页|打开聊天|chat", RegexOption.IGNORE_CASE)
private val AiAssistantDriveIntentRegex = Regex("网盘|文件|附件|drive", RegexOption.IGNORE_CASE)
private val AiAssistantAdminIntentRegex = Regex("管理后台|管理员面板|站点管理|后台管理|admin", RegexOption.IGNORE_CASE)
private val AiAssistantAchievementsIntentRegex = Regex("成就|徽章|里程碑|achievement", RegexOption.IGNORE_CASE)
private val AiAssistantFavoriteIntentRegex = Regex("收藏|稍后看|favorite", RegexOption.IGNORE_CASE)
private val AiAssistantUserListsIntentRegex = Regex("用户列表|我的列表|列表页|list", RegexOption.IGNORE_CASE)
private val AiAssistantFollowRequestsIntentRegex = Regex("关注请求|待批准关注|follow request", RegexOption.IGNORE_CASE)
private val AiAssistantRelationshipIntentRegex = Regex("关系管理|屏蔽|拉黑|静音用户|relationship", RegexOption.IGNORE_CASE)
private val AiAssistantAntennasIntentRegex = Regex("天线|antenna", RegexOption.IGNORE_CASE)
private val AiAssistantClipsIntentRegex = Regex("剪辑|clip", RegexOption.IGNORE_CASE)
private val AiAssistantChannelsIntentRegex = Regex("频道|channel", RegexOption.IGNORE_CASE)
private val AiAssistantPagesIntentRegex = Regex("页面|page", RegexOption.IGNORE_CASE)
private val AiAssistantGalleryIntentRegex = Regex("相册|图库|gallery", RegexOption.IGNORE_CASE)
private val AiAssistantFlashIntentRegex = Regex("Flash|flash", RegexOption.IGNORE_CASE)
private val AiAssistantAnnouncementsIntentRegex = Regex("公告|announcement", RegexOption.IGNORE_CASE)
private val AiAssistantRefreshIntentRegex = Regex("刷新|同步|重新加载|更新当前|refresh", RegexOption.IGNORE_CASE)
private val AiAssistantCheckUpdatesIntentRegex = Regex("检查更新|获取更新|查更新|新版本|应用更新", RegexOption.IGNORE_CASE)
private val AiAssistantMarkReadIntentRegex = Regex("全部已读|全标已读|标记已读|清空未读|通知已读", RegexOption.IGNORE_CASE)
private val AiAssistantMemoryIntentRegex = Regex("记住|记忆|以后|偏好|习惯", RegexOption.IGNORE_CASE)
private val AiAssistantChecklistIntentRegex = Regex("清单|待办|todo|checklist", RegexOption.IGNORE_CASE)
private const val AiAssistantStructuredMaxChars = 1_200

enum class AiAssistantPayloadField(val keys: List<String>) {
    Body(listOf("body", "text", "content", "message", "draft", "reply", "noteBody", "正文", "内容", "消息", "草稿", "回复", "帖子正文", "发帖正文", "聊天正文", "模板")),
    TargetRoom(listOf("targetRoom", "room", "roomId", "roomName", "chatRoom", "chatRoomId", "chatRoomName", "targetChat", "目标聊天室", "聊天室", "房间", "群聊", "目标群聊")),
    TargetUser(listOf("targetUser", "user", "userId", "userName", "username", "directUser", "recipient", "toUser", "目标用户", "私聊用户", "收件人", "用户", "名字")),
    Mentions(listOf("mentions", "mention", "mentionUsers", "at", "atUsers", "atUser", "提及", "艾特", "@", "@用户", "要@的人", "被@的人")),
    Channel(listOf("channel", "channelId", "channelName", "targetChannel", "频道", "目标频道")),
    Visibility(listOf("visibility", "noteVisibility", "可见性", "范围")),
    ContentWarning(listOf("cw", "contentWarning", "summary", "内容警告", "折叠标题")),
    LocalOnly(listOf("localOnly", "local", "本地", "仅本站", "本地限定")),
    SearchQuery(listOf("query", "searchQuery", "keyword", "keywords", "搜索词", "查询", "关键词")),
    AutomationGoal(listOf("automationGoal", "automation", "goal", "rule", "target", "自动化目标", "规则目标", "规则", "目标", "转发模板")),
    MutedWord(listOf("mutedWord", "muteWord", "filterWord", "keyword", "静音词", "过滤词", "屏蔽词", "关键词")),
    Memory(listOf("memory", "preference", "note", "记忆", "偏好", "备注")),
    Checklist(listOf("checklist", "items", "todo", "清单", "待办")),
}

data class AiAssistantStructuredPayload(
    val body: String = "",
    val targetRoom: String = "",
    val targetUser: String = "",
    val mentions: List<String> = emptyList(),
    val channel: String = "",
    val visibility: String = "",
    val contentWarning: String = "",
    val localOnly: String = "",
    val searchQuery: String = "",
    val automationGoal: String = "",
    val mutedWord: String = "",
    val memory: String = "",
    val checklist: String = "",
) {
    val hasAny: Boolean
        get() = body.isNotBlank() ||
            targetRoom.isNotBlank() ||
            targetUser.isNotBlank() ||
            mentions.isNotEmpty() ||
            channel.isNotBlank() ||
            visibility.isNotBlank() ||
            contentWarning.isNotBlank() ||
            localOnly.isNotBlank() ||
            searchQuery.isNotBlank() ||
            automationGoal.isNotBlank() ||
            mutedWord.isNotBlank() ||
            memory.isNotBlank() ||
            checklist.isNotBlank()

    val hasRouting: Boolean
        get() = targetRoom.isNotBlank() ||
            targetUser.isNotBlank() ||
            mentions.isNotEmpty() ||
            channel.isNotBlank() ||
            visibility.isNotBlank() ||
            contentWarning.isNotBlank() ||
            localOnly.isNotBlank()

    fun value(field: AiAssistantPayloadField): String {
        return when (field) {
            AiAssistantPayloadField.Body -> body
            AiAssistantPayloadField.TargetRoom -> targetRoom
            AiAssistantPayloadField.TargetUser -> targetUser
            AiAssistantPayloadField.Mentions -> mentions.joinToString("\n")
            AiAssistantPayloadField.Channel -> channel
            AiAssistantPayloadField.Visibility -> visibility
            AiAssistantPayloadField.ContentWarning -> contentWarning
            AiAssistantPayloadField.LocalOnly -> localOnly
            AiAssistantPayloadField.SearchQuery -> searchQuery
            AiAssistantPayloadField.AutomationGoal -> automationGoal
            AiAssistantPayloadField.MutedWord -> mutedWord
            AiAssistantPayloadField.Memory -> memory
            AiAssistantPayloadField.Checklist -> checklist
        }
    }

    fun intentText(): String {
        return listOf(
            body,
            targetRoom,
            targetUser,
            mentions.joinToString("\n"),
            channel,
            visibility,
            contentWarning,
            localOnly,
            searchQuery,
            automationGoal,
            mutedWord,
            memory,
            checklist,
        )
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}

data class AiAssistantActionPayload(
    val body: String = "",
    val targetRoom: String = "",
    val targetUser: String = "",
    val mentions: List<String> = emptyList(),
    val channel: String = "",
    val visibility: String = "",
    val contentWarning: String = "",
    val localOnly: String = "",
) {
    val hasRouting: Boolean
        get() = targetRoom.isNotBlank() ||
            targetUser.isNotBlank() ||
            mentions.isNotEmpty() ||
            channel.isNotBlank() ||
            visibility.isNotBlank() ||
            contentWarning.isNotBlank() ||
            localOnly.isNotBlank()
}

data class AiAssistantStructuredReply(
    val visibleText: String,
    val payload: AiAssistantStructuredPayload = AiAssistantStructuredPayload(),
)

private val AiAssistantJson = Json { ignoreUnknownKeys = true }
private val AiAssistantLabelStopKeys = (
    AiAssistantPayloadField.values().flatMap { it.keys } +
        listOf("说明", "建议", "风险", "需确认", "注意", "原因", "理由", "操作", "动作", "可见性", "标签", "确认", "备注", "标题", "来源")
    )
    .map { Regex.escape(it) }
    .joinToString("|")
private val AiAssistantLabelLineRegex = Regex("^\\s*(?:[-*+]\\s*)?(?:$AiAssistantLabelStopKeys)\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
private val AiAssistantDraftWrapperRegex = Regex(
    "^(?:好的[，,。!！\\s]*)?(?:以下|下面|这里|我建议|可以这样|建议这样|已为你|给你)(?:是|这个|一版|整理)?(?:.*?)(?:[:：])?$|^(?:只输出|需确认|请确认|不要直接|不会直接).*$|^(?:风险|说明|建议|备注|操作|动作|可见性|标签|标题|原因|理由)\\s*[:：].*$",
    RegexOption.IGNORE_CASE,
)
private val AiAssistantFenceRegex = Regex("""```(?:([A-Za-z0-9_-]+)\s*)?([\s\S]*?)```""")
private val AiAssistantQuotedWholeRegex = Regex("^[\\s`\"'“”‘’「」『』]+([\\s\\S]*?)[\\s`\"'“”‘’「」『』]+$")
private val AiAssistantPayloadFenceRegex = Regex("""```hhhl-assistant-payload\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
private val AiAssistantPayloadLineRegex = Regex("""(?m)^hhhlAssistant\.payload\s*[:：]\s*(\{[\s\S]*\})\s*$""", RegexOption.IGNORE_CASE)

fun aiAssistantSuggestedActions(
    prompt: String,
    reply: String,
    idPrefix: String,
): List<AiAssistantActionProposal> {
    val cleanPrompt = prompt.trim()
    val structuredReply = aiAssistantStructuredReply(reply)
    val cleanReply = structuredReply.visibleText.trim()
    val payload = structuredReply.payload.copy(
        targetRoom = structuredReply.payload.targetRoom.ifBlank {
            aiAssistantTargetRoomCandidate(cleanPrompt)
        },
        targetUser = structuredReply.payload.targetUser.ifBlank {
            aiAssistantTargetUserCandidate(cleanPrompt)
        },
        mentions = structuredReply.payload.mentions.ifEmpty {
            aiAssistantMentionCandidates(cleanPrompt)
        },
        channel = structuredReply.payload.channel.ifBlank {
            aiAssistantTargetChannelCandidate(cleanPrompt)
        },
    )
    val source = listOf(cleanPrompt, cleanReply, payload.intentText())
        .filter { it.isNotBlank() }
        .joinToString("\n")
    val actions = mutableListOf<AiAssistantActionProposal>()

    fun add(
        kind: AiAssistantActionKind,
        title: String = kind.label,
        description: String,
        payload: String = cleanPrompt,
    ) {
        if (actions.none { it.kind == kind }) {
            actions += AiAssistantActionProposal(
                id = "$idPrefix-${kind.name}",
                kind = kind,
                title = title,
                description = description,
                payload = payload,
            )
        }
    }

    fun payloadForIntent(intent: Regex): String {
        return if (intent.containsMatchIn(cleanPrompt)) {
            cleanPrompt
        } else {
            source.take(AiAssistantStructuredMaxChars)
        }
    }

    if (AiAssistantDebugIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenAutomationLogs,
            description = "打开自动化执行日志和最近调试记录，用于确认触发链路。",
        )
    }
    if (AiAssistantAutomationIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.CreateAutomationDraft,
            description = "根据这轮需求生成结构化规则草稿，进入自动化页后仍需确认创建。",
            payload = aiAssistantAutomationGoalCandidate(cleanPrompt, reply),
        )
    } else if (AiAssistantOpenAutomationIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenAutomation,
            description = "打开自动化中心查看规则、日志和草稿。",
        )
    }
    if (AiAssistantAutomationRiskIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.ReviewAutomationRisk,
            description = "打开自动化中心审核启用、删除、跨聊天室转发或 Webhook 风险。",
            payload = aiAssistantAutomationGoalCandidate(cleanPrompt, reply),
        )
    }
    if (AiAssistantBulkIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.ReviewBulkOperation,
            description = "检测到批量操作意图，打开相关管理入口继续处理。",
            payload = aiAssistantAutomationGoalCandidate(cleanPrompt, reply),
        )
    }
    if (AiAssistantClearCurrentChatRoomIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.ClearCurrentChatRoomMessages,
            description = "清空当前或明确指定聊天室的全部消息；高风险自动批准开启时会直接调用聊天室清空接口。",
            payload = payloadForIntent(AiAssistantClearCurrentChatRoomIntentRegex),
        )
    }
    if (AiAssistantDeleteCurrentChatRoomIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.DeleteCurrentChatRoom,
            description = "删除当前或明确指定的聊天室；高风险自动批准开启时会直接调用聊天室删除接口。",
            payload = payloadForIntent(AiAssistantDeleteCurrentChatRoomIntentRegex),
        )
    }
    if (AiAssistantLeaveCurrentChatRoomIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.LeaveCurrentChatRoom,
            description = "退出当前或明确指定的聊天室；高风险自动批准开启时会直接调用退出接口。",
            payload = payloadForIntent(AiAssistantLeaveCurrentChatRoomIntentRegex),
        )
    }
    if (AiAssistantUnmuteCurrentChatRoomIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.UnmuteCurrentChatRoom,
            description = "取消当前或明确指定聊天室的静音状态。",
            payload = payloadForIntent(AiAssistantUnmuteCurrentChatRoomIntentRegex),
        )
    } else if (AiAssistantMuteCurrentChatRoomIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.MuteCurrentChatRoom,
            description = "静音当前或明确指定的聊天室。",
            payload = payloadForIntent(AiAssistantMuteCurrentChatRoomIntentRegex),
        )
    }
    val currentRoomActionKinds = setOf(
        AiAssistantActionKind.ClearCurrentChatRoomMessages,
        AiAssistantActionKind.DeleteCurrentChatRoom,
        AiAssistantActionKind.LeaveCurrentChatRoom,
        AiAssistantActionKind.MuteCurrentChatRoom,
        AiAssistantActionKind.UnmuteCurrentChatRoom,
    )
    val currentNoteActionKinds = setOf(
        AiAssistantActionKind.FavoriteCurrentNote,
        AiAssistantActionKind.UnfavoriteCurrentNote,
        AiAssistantActionKind.ReactCurrentNote,
        AiAssistantActionKind.DeleteReactionCurrentNote,
        AiAssistantActionKind.RenoteCurrentNote,
        AiAssistantActionKind.DeleteCurrentNote,
    )
    if (AiAssistantUnfavoriteCurrentNoteIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.UnfavoriteCurrentNote,
            description = "取消收藏当前或明确指定 ID 的帖子；高风险自动批准开启时会直接调用取消收藏接口。",
            payload = payloadForIntent(AiAssistantUnfavoriteCurrentNoteIntentRegex),
        )
    } else if (AiAssistantFavoriteCurrentNoteIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.FavoriteCurrentNote,
            description = "收藏当前或明确指定 ID 的帖子；高风险自动批准开启时会直接调用收藏接口。",
            payload = payloadForIntent(AiAssistantFavoriteCurrentNoteIntentRegex),
        )
    }
    if (AiAssistantDeleteReactionCurrentNoteIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.DeleteReactionCurrentNote,
            description = "取消当前或明确指定 ID 帖子的反应；高风险自动批准开启时会直接调用取消反应接口。",
            payload = payloadForIntent(AiAssistantDeleteReactionCurrentNoteIntentRegex),
        )
    } else if (AiAssistantReactCurrentNoteIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.ReactCurrentNote,
            description = "给当前或明确指定 ID 的帖子发送默认反应；高风险自动批准开启时会直接调用反应接口。",
            payload = payloadForIntent(AiAssistantReactCurrentNoteIntentRegex),
        )
    }
    if (AiAssistantRenoteCurrentNoteIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.RenoteCurrentNote,
            description = "转发当前或明确指定 ID 的帖子；高风险自动批准开启时会直接调用转发接口。",
            payload = payloadForIntent(AiAssistantRenoteCurrentNoteIntentRegex),
        )
    }
    if (AiAssistantDeleteCurrentNoteIntentRegex.containsMatchIn(source)) {
        add(
            kind = AiAssistantActionKind.DeleteCurrentNote,
            description = "删除当前或明确指定 ID 的自己发布的帖子；高风险自动批准开启时会直接调用删除接口。",
            payload = payloadForIntent(AiAssistantDeleteCurrentNoteIntentRegex),
        )
    }
    if (AiAssistantCurrentPageActionIntentRegex.containsMatchIn(cleanPrompt)) {
        if (actions.none { it.kind in currentRoomActionKinds || it.kind in currentNoteActionKinds }) {
            add(
                kind = AiAssistantActionKind.ReviewCurrentPageAction,
                description = "检测到会修改远程状态的界面操作，高风险自动批准开启时会直接切到当前相关页面；不会静默点击未知控件。",
                payload = aiAssistantAutomationGoalCandidate(cleanPrompt, reply),
            )
        }
    }
    if (AiAssistantTimelineIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenTimeline,
            description = "切到时间线页查看最新帖子。",
        )
    }
    if (AiAssistantDiscoverIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenDiscover,
            description = "打开发现页查看搜索、趋势和推荐内容。",
        )
    }
    if (!AiAssistantWebSearchIntentRegex.containsMatchIn(cleanPrompt) && AiAssistantDiscoverSearchIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenDiscoverSearch,
            description = "用这轮消息作为关键词打开站内搜索。",
            payload = aiAssistantSearchQueryCandidate(cleanPrompt, reply),
        )
    }
    if (AiAssistantNotificationsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenNotifications,
            description = "打开通知页查看未读、提及、关注请求和聊天提醒。",
        )
    }
    if (AiAssistantProfileIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenProfile,
            description = "打开我的页面查看账号、个人资料和管理入口。",
        )
    }
    if (AiAssistantProfileNotesIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenProfileNotes,
            description = "打开我的帖子列表，查看自己发布过的内容。",
        )
    }
    if (AiAssistantSettingsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenSettings,
            description = "打开设置页查看 AI、通知、显示和更新配置。",
        )
    }
    if (AiAssistantAiSettingsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenAiSettings,
            description = "打开 AI 设置页调整 Provider、模型、权限、队列和用量限制。",
        )
    }
    if (AiAssistantReleaseNotesIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenReleaseNotes,
            description = "打开完整更新日志时间线，最新版本排在最上面。",
        )
    }
    if (AiAssistantThemeIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenThemeCustomization,
            description = "打开主题自定义页调整配色和背景图。",
        )
    }
    if (AiAssistantOpenComposeIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenCompose,
            description = "打开发帖页准备草稿，不会自动发布。",
        )
    }
    if (AiAssistantChatPageIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenChat,
            description = "打开聊天列表或当前聊天入口。",
        )
    }
    if (AiAssistantDriveIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenDrive,
            description = "打开网盘页面管理文件和附件。",
        )
    }
    if (AiAssistantAdminIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenAdminDashboard,
            description = "打开管理后台查看站点管理入口。",
        )
    }
    if (AiAssistantAchievementsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenAchievements,
            description = "打开成就页面查看已解锁和可领取的里程碑。",
        )
    }
    if (AiAssistantFavoriteIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenFavoriteNotes,
            description = "打开收藏/稍后看帖子列表。",
        )
    }
    if (AiAssistantUserListsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenUserLists,
            description = "打开用户列表页面管理列表。",
        )
    }
    if (AiAssistantFollowRequestsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenFollowRequests,
            description = "打开关注请求页面处理待批准关注。",
        )
    }
    if (AiAssistantRelationshipIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenRelationshipManagement,
            description = "打开关系管理页面查看关注、屏蔽和静音对象。",
        )
    }
    if (AiAssistantAntennasIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenAntennas,
            description = "打开天线页面管理订阅条件。",
        )
    }
    if (AiAssistantClipsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenClips,
            description = "打开剪辑页面查看和管理收藏集合。",
        )
    }
    if (AiAssistantChannelsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenChannels,
            description = "打开频道页面查看已加入和可发现频道。",
        )
    }
    if (AiAssistantPagesIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenPages,
            description = "打开页面功能查看站内页面。",
        )
    }
    if (AiAssistantGalleryIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenGallery,
            description = "打开相册/图库页面查看媒体作品。",
        )
    }
    if (AiAssistantFlashIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenFlash,
            description = "打开 Flash 页面查看互动内容。",
        )
    }
    if (AiAssistantAnnouncementsIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenAnnouncements,
            description = "打开公告页面查看站点公告。",
        )
    }
    if (AiAssistantRefreshIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.RefreshCurrentView,
            description = "刷新当前所在页面的数据，不会提交写操作。",
        )
    }
    if (AiAssistantCheckUpdatesIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.CheckForUpdates,
            description = "检查 GitHub 最新发布并触发已有应用内更新流程。",
        )
    }
    if (AiAssistantMarkReadIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.MarkNotificationsRead,
            description = "把通知标记为全部已读，会修改通知读取状态。",
        )
    }
    if (AiAssistantWebhookManagementIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenWebhookManagement,
            description = "打开 Webhook 管理页审核创建、启用、测试或删除操作。",
            payload = aiAssistantAutomationGoalCandidate(cleanPrompt, reply),
        )
    }
    if (AiAssistantChatSummaryIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.RunChatSummary,
            description = "对当前选中的聊天室生成最近消息摘要，不会发送任何消息。",
            payload = payload.automationGoal.ifBlank { cleanPrompt },
        )
    }
    if (AiAssistantForwardTemplateIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.CreateForwardTemplateDraft,
            description = "把聊天室转发模板整理成自动化规则草稿，创建前仍需确认。",
            payload = aiAssistantAutomationGoalCandidate(cleanPrompt, reply),
        )
    }
    if (AiAssistantChatRewriteIntentRegex.containsMatchIn(cleanPrompt)) {
        val draft = aiAssistantBodyCandidate(cleanPrompt, reply)
        add(
            kind = AiAssistantActionKind.FillChatDraft,
            description = "把改写后的内容填入当前聊天输入框，只作为草稿，不会发送。",
            payload = aiAssistantActionPayloadText(payload, draft),
        )
    }
    if (AiAssistantSendChatIntentRegex.containsMatchIn(cleanPrompt)) {
        val outgoing = aiAssistantPublishPayloadCandidate(cleanPrompt, reply)
        add(
            kind = AiAssistantActionKind.SendChatDraft,
            description = if (payload.hasRouting) {
                "按 AI 解析出的聊天室、私聊用户或 @ 对象发送消息；高风险自动批准开启时会直接发送。"
            } else {
                "发送当前聊天输入框里的草稿；高风险自动批准开启时会直接发送。"
            },
            payload = aiAssistantActionPayloadText(payload, outgoing),
        )
    }
    if (AiAssistantComposeIntentRegex.containsMatchIn(cleanPrompt) && !AiAssistantPublishComposeIntentRegex.containsMatchIn(cleanPrompt)) {
        val draft = aiAssistantBodyCandidate(cleanPrompt, reply)
        add(
            kind = AiAssistantActionKind.FillComposeDraft,
            description = "把 AI 生成的正文填入发帖框，只保存为草稿，不会直接发布。",
            payload = aiAssistantActionPayloadText(payload, draft),
        )
    }
    if (AiAssistantPublishComposeIntentRegex.containsMatchIn(cleanPrompt)) {
        val outgoing = aiAssistantPublishPayloadCandidate(cleanPrompt, reply)
        add(
            kind = AiAssistantActionKind.PublishComposeDraft,
            description = if (payload.hasRouting) {
                "按 AI 解析出的 @ 对象、频道、可见性等发布帖子；高风险自动批准开启时会直接发布。"
            } else {
                "发布当前发帖框里的草稿；高风险自动批准开启时会直接发布。"
            },
            payload = aiAssistantActionPayloadText(payload, outgoing),
        )
    }
    if (AiAssistantMutedIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.AddMutedWord,
            description = "从 AI 建议里提取一个静音词，批准后写入账号过滤设置。",
            payload = aiAssistantMutedWordPayload(cleanPrompt, reply),
        )
    }
    if (AiAssistantWebSearchIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.OpenWebSearch,
            description = "用这轮消息作为查询词打开外部搜索页面。",
            payload = aiAssistantSearchQueryCandidate(cleanPrompt, reply),
        )
    }
    if (AiAssistantMemoryIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.SaveMemory,
            description = "把这条偏好保存到本地对话记忆，后续本地助手上下文会带上。",
            payload = aiAssistantMemoryCandidate(cleanPrompt, reply),
        )
    }
    if (AiAssistantChecklistIntentRegex.containsMatchIn(cleanPrompt)) {
        add(
            kind = AiAssistantActionKind.CopyChecklist,
            description = "把这段 AI 回复整理成项目清单并复制到剪贴板。",
            payload = aiAssistantChecklistCandidate(cleanPrompt, reply),
        )
    }

    return actions.take(6)
}

fun aiAssistantStructuredReply(text: String): AiAssistantStructuredReply {
    val raw = text.trim()
    if (raw.isBlank()) return AiAssistantStructuredReply(visibleText = "")
    val fenceMatch = AiAssistantPayloadFenceRegex.find(raw)
    val lineMatch = if (fenceMatch == null) AiAssistantPayloadLineRegex.find(raw) else null
    val jsonText = fenceMatch?.groupValues?.getOrNull(1)
        ?: lineMatch?.groupValues?.getOrNull(1)
    val visibleText = when {
        fenceMatch != null -> raw.removeRange(fenceMatch.range).trim()
        lineMatch != null -> raw.removeRange(lineMatch.range).trim()
        else -> raw
    }
    return AiAssistantStructuredReply(
        visibleText = visibleText,
        payload = jsonText?.let(::aiAssistantPayloadFromJson).takeIf { it?.hasAny == true }
            ?: AiAssistantStructuredPayload(),
    )
}

private fun aiAssistantPayloadFromJson(jsonText: String): AiAssistantStructuredPayload? {
    val root = runCatching { AiAssistantJson.parseToJsonElement(jsonText.trim()) }.getOrNull()
        ?: return null
    return AiAssistantStructuredPayload(
        body = root.structuredValue(AiAssistantPayloadField.Body),
        targetRoom = root.structuredValue(AiAssistantPayloadField.TargetRoom),
        targetUser = root.structuredValue(AiAssistantPayloadField.TargetUser),
        mentions = root.structuredList(AiAssistantPayloadField.Mentions),
        channel = root.structuredValue(AiAssistantPayloadField.Channel),
        visibility = root.structuredValue(AiAssistantPayloadField.Visibility),
        contentWarning = root.structuredValue(AiAssistantPayloadField.ContentWarning),
        localOnly = root.structuredValue(AiAssistantPayloadField.LocalOnly),
        searchQuery = root.structuredValue(AiAssistantPayloadField.SearchQuery),
        automationGoal = root.structuredValue(AiAssistantPayloadField.AutomationGoal),
        mutedWord = root.structuredValue(AiAssistantPayloadField.MutedWord),
        memory = root.structuredValue(AiAssistantPayloadField.Memory),
        checklist = root.structuredValue(AiAssistantPayloadField.Checklist),
    )
}

private fun JsonElement.structuredValue(field: AiAssistantPayloadField): String {
    val values = mutableListOf<String>()
    fun collect(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                field.keys.forEach { key ->
                    element[key]?.let { values += it.toStructuredText() }
                }
                element["payload"]?.let(::collect)
                element["data"]?.let(::collect)
            }
            else -> Unit
        }
    }
    collect(this)
    return values
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.take(AiAssistantStructuredMaxChars)
        ?.trim()
        .orEmpty()
}

private fun JsonElement.structuredList(field: AiAssistantPayloadField): List<String> {
    val values = mutableListOf<String>()
    fun collect(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                field.keys.forEach { key ->
                    element[key]?.let { values += it.toStructuredListValues() }
                }
                element["payload"]?.let(::collect)
                element["data"]?.let(::collect)
            }
            else -> Unit
        }
    }
    collect(this)
    return values
        .flatMap { it.split(Regex("[\\n,，、;；]+")) }
        .map { it.trim().trim('"', '\'', '“', '”', '‘', '’') }
        .filter { it.isNotBlank() }
        .distinct()
        .take(12)
}

private fun JsonElement.toStructuredListValues(): List<String> {
    return when (this) {
        is JsonPrimitive -> listOf(jsonPrimitive.contentOrNull.orEmpty())
        is JsonArray -> flatMap { it.toStructuredListValues() }
        is JsonObject -> values.flatMap { it.toStructuredListValues() }
    }
}

private fun JsonElement.toStructuredText(): String {
    return when (this) {
        is JsonPrimitive -> jsonPrimitive.contentOrNull.orEmpty()
        is JsonArray -> map { it.toStructuredText().trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        is JsonObject -> values.joinToString("\n") { it.toStructuredText() }
    }
}

fun aiAssistantActionPayload(raw: String): AiAssistantActionPayload {
    val clean = raw.trim()
    if (clean.isBlank()) return AiAssistantActionPayload()
    val root = runCatching { AiAssistantJson.parseToJsonElement(clean) as? JsonObject }.getOrNull()
        ?: return AiAssistantActionPayload(body = clean)
    return AiAssistantActionPayload(
        body = root.structuredValue(AiAssistantPayloadField.Body),
        targetRoom = root.structuredValue(AiAssistantPayloadField.TargetRoom),
        targetUser = root.structuredValue(AiAssistantPayloadField.TargetUser),
        mentions = root.structuredList(AiAssistantPayloadField.Mentions),
        channel = root.structuredValue(AiAssistantPayloadField.Channel),
        visibility = root.structuredValue(AiAssistantPayloadField.Visibility),
        contentWarning = root.structuredValue(AiAssistantPayloadField.ContentWarning),
        localOnly = root.structuredValue(AiAssistantPayloadField.LocalOnly),
    )
}

fun aiAssistantActionPayloadText(
    payload: AiAssistantStructuredPayload,
    body: String,
): String {
    val actionPayload = AiAssistantActionPayload(
        body = body.trim(),
        targetRoom = payload.targetRoom.trim(),
        targetUser = payload.targetUser.trim(),
        mentions = payload.mentions.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        channel = payload.channel.trim(),
        visibility = payload.visibility.trim(),
        contentWarning = payload.contentWarning.trim(),
        localOnly = payload.localOnly.trim(),
    )
    if (!actionPayload.hasRouting) return actionPayload.body
    return buildJsonObject {
        put("body", JsonPrimitive(actionPayload.body))
        put("targetRoom", JsonPrimitive(actionPayload.targetRoom))
        put("targetUser", JsonPrimitive(actionPayload.targetUser))
        put(
            "mentions",
            buildJsonArray {
                actionPayload.mentions.forEach { mention -> add(JsonPrimitive(mention)) }
            },
        )
        put("channel", JsonPrimitive(actionPayload.channel))
        put("visibility", JsonPrimitive(actionPayload.visibility))
        put("cw", JsonPrimitive(actionPayload.contentWarning))
        put("localOnly", JsonPrimitive(actionPayload.localOnly))
    }.toString()
}

private fun aiAssistantStructuredCandidate(text: String, field: AiAssistantPayloadField): String {
    return aiAssistantStructuredReply(text).payload.value(field).trim()
}

fun aiAssistantBodyCandidate(prompt: String, reply: String): String {
    return aiAssistantStructuredCandidate(reply, AiAssistantPayloadField.Body)
        .ifBlank { aiAssistantDraftCandidate(aiAssistantStructuredReply(reply).visibleText) }
        .ifBlank { aiAssistantStructuredCandidate(prompt, AiAssistantPayloadField.Body) }
        .ifBlank { aiAssistantDraftCandidate(prompt) }
        .ifBlank { prompt.trim().take(AiAssistantStructuredMaxChars) }
        .take(AiAssistantStructuredMaxChars)
        .trim()
}

fun aiAssistantPublishPayloadCandidate(prompt: String, reply: String): String {
    val visibleReply = aiAssistantStructuredReply(reply).visibleText
    val fromReply = aiAssistantStructuredCandidate(reply, AiAssistantPayloadField.Body)
        .ifBlank { aiAssistantLabeledSection(visibleReply, AiAssistantPayloadField.Body) }
        .ifBlank {
            AiAssistantFenceRegex.find(visibleReply)?.takeIf { match ->
                match.groupValues.getOrNull(1)?.equals("hhhl-assistant-payload", ignoreCase = true) != true
            }?.groupValues?.getOrNull(2)?.let(::aiAssistantDraftCandidate).orEmpty()
        }
    if (fromReply.isNotBlank()) return fromReply.take(AiAssistantStructuredMaxChars).trim()
    return aiAssistantStructuredCandidate(prompt, AiAssistantPayloadField.Body)
        .ifBlank { aiAssistantLabeledSection(prompt, AiAssistantPayloadField.Body) }
        .ifBlank { aiAssistantInlineBodyCandidate(prompt) }
        .take(AiAssistantStructuredMaxChars)
        .trim()
}

fun aiAssistantAutomationGoalCandidate(prompt: String, reply: String): String {
    return aiAssistantStructuredCandidate(reply, AiAssistantPayloadField.AutomationGoal)
        .ifBlank { aiAssistantStructuredCandidate(prompt, AiAssistantPayloadField.AutomationGoal) }
        .ifBlank { aiAssistantBodyCandidate(prompt, reply).takeIf { it != prompt.trim() }.orEmpty() }
        .ifBlank { prompt.trim() }
        .take(AiAssistantStructuredMaxChars)
        .trim()
}

fun aiAssistantMutedWordPayload(prompt: String, reply: String): String {
    val structured = aiAssistantStructuredCandidate(reply, AiAssistantPayloadField.MutedWord)
        .ifBlank { aiAssistantStructuredCandidate(prompt, AiAssistantPayloadField.MutedWord) }
    return structured.ifBlank {
        aiResultMutedWordCandidate(aiAssistantStructuredReply(reply).visibleText)
            ?: aiResultMutedWordCandidate(prompt)
            ?: prompt.trim()
    }.take(80).trim()
}

fun aiAssistantMemoryCandidate(prompt: String, reply: String): String {
    return aiAssistantStructuredCandidate(reply, AiAssistantPayloadField.Memory)
        .ifBlank { aiAssistantStructuredCandidate(prompt, AiAssistantPayloadField.Memory) }
        .ifBlank { prompt.trim() }
        .take(240)
        .trim()
}

fun aiAssistantChecklistCandidate(prompt: String, reply: String): String {
    val visibleReply = aiAssistantStructuredReply(reply).visibleText
    return aiAssistantStructuredCandidate(reply, AiAssistantPayloadField.Checklist)
        .ifBlank { visibleReply.trim() }
        .ifBlank { prompt.trim() }
        .take(AiAssistantStructuredMaxChars)
        .trim()
}

fun aiAssistantSearchQueryCandidate(text: String, reply: String = ""): String {
    aiAssistantStructuredCandidate(reply, AiAssistantPayloadField.SearchQuery)
        .ifBlank { aiAssistantStructuredCandidate(text, AiAssistantPayloadField.SearchQuery) }
        .takeIf { it.isNotBlank() }
        ?.let { return it.take(160).trim() }
    var clean = text
        .trim()
        .replace(Regex("^(帮我|请|麻烦你|我想|我要|打开|去|查一下|查找|搜索|网络搜索|站内搜索|外部搜索|网页搜索)+"), "")
        .trim()
        .replace(Regex("^(一下|看看|下|搜一下|查一下)\\s*"), "")
        .trim()
    val commandSplit = Regex("[，,。；;！!？?]\\s*(把|并|然后|再|同时|以及)").find(clean)?.range?.first
    if (commandSplit != null) clean = clean.take(commandSplit)
    return clean
        .replace(Regex("(一下|看看|页面|页)$"), "")
        .trim(' ', '：', ':', '，', ',', '。', '.')
        .take(160)
        .ifBlank { text.trim().take(160) }
}

private fun aiAssistantMentionCandidates(text: String): List<String> {
    val candidates = mutableListOf<String>()
    AiAssistantAtMentionRegex.findAll(text).forEach { match ->
        match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { candidates += it }
    }
    AiAssistantNamedMentionRegex.findAll(text).forEach { match ->
        match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { candidates += it }
    }
    return candidates
        .map { it.trim().trim('@', '＠', '：', ':', '，', ',', '。', '.', '；', ';') }
        .filter { it.length in 1..80 }
        .distinct()
        .take(8)
}

private fun aiAssistantTargetUserCandidate(text: String): String {
    return AiAssistantTargetUserRegexes.firstNotNullOfOrNull { regex ->
        regex.find(text)?.groupValues?.getOrNull(1)
            ?.trimAiAssistantInferredTarget()
            ?.takeIf { it.isUsableAiAssistantTarget() }
    }.orEmpty()
}

private fun aiAssistantTargetRoomCandidate(text: String): String {
    return AiAssistantTargetRoomRegexes.firstNotNullOfOrNull { regex ->
        regex.find(text)?.groupValues?.getOrNull(1)
            ?.trimAiAssistantInferredTarget()
            ?.takeIf { it.isUsableAiAssistantTarget() }
    }.orEmpty()
}

private fun aiAssistantTargetChannelCandidate(text: String): String {
    return AiAssistantTargetChannelRegexes.firstNotNullOfOrNull { regex ->
        regex.find(text)?.groupValues?.getOrNull(1)
            ?.trimAiAssistantInferredTarget()
            ?.takeIf { it.isUsableAiAssistantTarget() }
    }.orEmpty()
}

private fun aiAssistantInlineBodyCandidate(text: String): String {
    return AiAssistantInlineBodyRegexes.firstNotNullOfOrNull { regex ->
        regex.find(text)?.groupValues?.getOrNull(1)
            ?.trimAiAssistantInlineBody()
            ?.takeIf { it.isUsableAiAssistantInlineBody() }
    }.orEmpty()
}

private val AiAssistantTargetUserRegexes = listOf(
    Regex("""私聊\s*([^\s，,。；;、：:]{1,40}?)(?:说|告诉|通知|发|回复|[:：]|，|,|。|$)""", RegexOption.IGNORE_CASE),
    Regex("""(?:给|向)\s*([^\s，,。；;、：:]{1,40}?)(?:发?私信|发?私聊|私聊消息|私信消息)(?:说|告诉|通知|回复|[:：]|，|,|。|$)""", RegexOption.IGNORE_CASE),
    Regex("""发给\s*([^\s，,。；;、：:]{1,40}?)(?:一?条?(?:消息|回复)|说|通知|[:：]|，|,|。|$)""", RegexOption.IGNORE_CASE),
    Regex("""给\s*([^\s，,。；;、：:]{1,40}?)(?:发消息|发个消息|发送消息|说|回复|通知|[:：])""", RegexOption.IGNORE_CASE),
    Regex("""(?:告诉|通知)\s*([^\s，,。；;、：:]{1,40}?)(?:说|[:：]|，|,|。|$)""", RegexOption.IGNORE_CASE),
)

private val AiAssistantTargetRoomRegexes = listOf(
    Regex("""(?:在|到)\s*(.{1,60}?)(?:聊天室|房间|群聊|群)(?:里面|里|中|内)?\s*(?:发|发送|说|告诉|通知|回复|[:：]|，|,|。|$)""", RegexOption.IGNORE_CASE),
    Regex("""(?:聊天室|房间|群聊)\s*[:：]\s*(.{1,60}?)(?:\s|，|,|。|；|;|$)""", RegexOption.IGNORE_CASE),
)

private val AiAssistantTargetChannelRegexes = listOf(
    Regex("""(?:发布到|发到|发送到|投到)\s*(.{1,60}?)(?:频道|channel)(?:里面|里|中|内)?\s*(?:[:：]|，|,|。|；|;|$)""", RegexOption.IGNORE_CASE),
    Regex("""(?:在|到)\s*(.{1,60}?)(?:频道|channel)(?:里面|里|中|内)?\s*(?:发布|发帖|发|发送)\s*(?:[:：]|，|,|。|；|;|$)""", RegexOption.IGNORE_CASE),
    Regex("""(.{1,60}?)(?:频道|channel)(?:里面|里|中|内)?\s*(?:发布|发帖|发|发送)\s*(?:[:：]|，|,|。|；|;|$|(?=[\p{L}\p{N}]))""", RegexOption.IGNORE_CASE),
    Regex("""(?:频道|channel)\s*[:：]\s*(.{1,60}?)(?:\s|，|,|。|；|;|$)""", RegexOption.IGNORE_CASE),
)

private val AiAssistantInlineBodyRegexes = listOf(
    Regex("""私聊\s*[^\s，,。；;、：:]{1,40}?(?:说|告诉|通知|回复)\s*[:：]?\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""(?:给|向)\s*[^\s，,。；;、：:]{1,40}?(?:发?私信|发?私聊|私聊消息|私信消息)(?:说|告诉|通知|回复)?\s*[:：]?\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""发给\s*[^\s，,。；;、：:]{1,40}?(?:一?条?(?:消息|回复)|说|通知|回复|[:：])\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""给\s*[^\s，,。；;、：:]{1,40}?(?:发消息|发个消息|发送消息|说|回复|通知)\s*[:：]?\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""(?:告诉|通知)\s*[^\s，,。；;、：:]{1,40}?(?:说|[:：])\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""(?:在|到)\s*.{1,60}?(?:聊天室|房间|群聊|群)(?:里面|里|中|内)?\s*(?:说|告诉|通知|回复)\s*[:：]?\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""(?:在|到)\s*.{1,60}?(?:聊天室|房间|群聊|群)(?:里面|里|中|内)?\s*(?:发|发送)(?:消息|回复)?\s*[:：]\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""(?:在|到)\s*.{1,60}?(?:聊天室|房间|群聊|群)(?:里面|里|中|内)?\s*(?:发|发送)(?:一?条?)?(?:消息|回复)\s*[:：]?\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""(?:发布到|发到|发送到|投到)\s*.{1,60}?(?:频道|channel)(?:里面|里|中|内)?\s*[:：]?\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""(?:在|到)?\s*.{1,60}?(?:频道|channel)(?:里面|里|中|内)?\s*(?:发布|发帖|发|发送)\s*[:：]?\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""(?:消息|回复|正文|内容)\s*[:：]\s*(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^[^:：]{1,80}[:：]\s*(.+)$""", RegexOption.IGNORE_CASE),
)

private fun String.trimAiAssistantInferredTarget(): String {
    return trim()
        .trim('@', '＠', '：', ':', '，', ',', '。', '.', '；', ';', '“', '”', '"', '\'', '「', '」')
        .replace(Regex("""^(给|向|跟|和)\s*"""), "")
        .replace(Regex("""\s*(用户|同学|这位)$"""), "")
        .trim()
}

private fun String.isUsableAiAssistantTarget(): Boolean {
    if (length !in 1..80) return false
    val lowered = lowercase()
    return lowered !in setOf(
        "我",
        "自己",
        "本人",
        "大家",
        "所有人",
        "全部",
        "全体",
        "当前",
        "这个",
        "这位",
        "他",
        "她",
        "ta",
    )
}

private fun String.trimAiAssistantInlineBody(): String {
    return trim()
        .trim(' ', '：', ':', '，', ',', '。', '.', '；', ';', '“', '”', '"', '\'', '「', '」')
        .replace(Regex("""^(说|回复|通知|告诉|消息|内容|正文)\s*[:：]?\s*"""), "")
        .trim()
}

private fun String.isUsableAiAssistantInlineBody(): Boolean {
    if (isBlank() || length > AiAssistantStructuredMaxChars) return false
    val clean = trim()
    if (clean.startsWith("并 @") || clean.startsWith("并@") || clean.startsWith("并艾特") || clean.startsWith("并 提及")) {
        return false
    }
    return clean.any { it.isLetterOrDigit() }
}

private val AiAssistantAtMentionRegex = Regex("""[@＠]([A-Za-z0-9_.-]+(?:@[A-Za-z0-9_.-]+)?|[\p{L}\p{N}_\-.]{1,40})""")
private val AiAssistantNamedMentionRegex = Regex("""(?:艾特|提及|@)\s*([^\s，,。；;、]{1,40})""")

fun aiAssistantDraftCandidate(text: String): String {
    val fenced = AiAssistantFenceRegex.find(text)?.takeIf { match ->
        match.groupValues.getOrNull(1)?.equals("hhhl-assistant-payload", ignoreCase = true) != true
    }?.groupValues?.getOrNull(2)
    val source = fenced ?: aiAssistantStructuredReply(text).visibleText
    val labeled = aiAssistantLabeledSection(source, AiAssistantPayloadField.Body)
    return (labeled.ifBlank { source })
        .lineSequence()
        .map { line ->
            line.trim()
                .removePrefix("草稿：")
                .removePrefix("正文：")
                .removePrefix("帖子：")
                .removePrefix("消息：")
                .removePrefix("回复：")
                .replace(Regex("^[-*+]\\s+"), "")
                .trim()
        }
        .filter { it.isNotBlank() }
        .filterNot { line -> AiAssistantDraftWrapperRegex.matches(line) }
        .filterNot { line ->
            line.startsWith("建议") ||
                line.startsWith("说明") ||
                line.startsWith("风险") ||
                line.startsWith("需确认")
        }
        .joinToString("\n")
        .take(1_200)
        .trimStructuredQuotes()
        .trim()
}

private fun aiAssistantLabeledSection(text: String, field: AiAssistantPayloadField): String {
    val lines = text.lines()
    val output = mutableListOf<String>()
    var collecting = false
    for (line in lines) {
        val trimmed = line.trim()
        val labelMatch = AiAssistantLabelLineRegex.find(trimmed)
        if (labelMatch != null) {
            val label = trimmed.substringBefore('：').substringBefore(':').trim().trim('-', '*', '+', ' ')
            val isTarget = field.keys.any { key -> label.equals(key, ignoreCase = true) }
            if (collecting && !isTarget) break
            if (isTarget) {
                collecting = true
                val value = labelMatch.groupValues.getOrNull(1).orEmpty().trim()
                if (value.isNotBlank()) output += value
            }
            continue
        }
        if (collecting) output += line
    }
    return output.joinToString("\n").trim()
}

private fun String.trimStructuredQuotes(): String {
    val clean = trim()
    return AiAssistantQuotedWholeRegex.matchEntire(clean)?.groupValues?.getOrNull(1)?.trim() ?: clean
}
