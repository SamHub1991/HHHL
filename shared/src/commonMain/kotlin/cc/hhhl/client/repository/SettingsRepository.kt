package cc.hhhl.client.repository

import cc.hhhl.client.ai.AiSettings
import cc.hhhl.client.ai.AiTask
import cc.hhhl.client.ai.AiTaskStatus
import cc.hhhl.client.ai.AiUsageWindow
import cc.hhhl.client.ai.normalizedAiUsage
import cc.hhhl.client.api.SettingsApi
import cc.hhhl.client.api.SettingsCapabilityResult
import cc.hhhl.client.api.SettingsManagementMutationResult
import cc.hhhl.client.api.SettingsManagementSectionResult
import cc.hhhl.client.api.SettingsPreferencesResult
import cc.hhhl.client.api.SettingsSharedAccessLoginResult
import cc.hhhl.client.api.SettingsWebhookDetailResult
import cc.hhhl.client.api.SharkeySettingsApi
import cc.hhhl.client.repository.AvatarDecorationRepositoryResult
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.model.FilterSettings
import cc.hhhl.client.model.NotificationSettings
import cc.hhhl.client.model.PrivacySettings
import cc.hhhl.client.model.SecuritySettings
import cc.hhhl.client.model.SettingsPreferenceUpdate
import cc.hhhl.client.model.SettingsPreferences
import cc.hhhl.client.model.IntegrationSettings
import cc.hhhl.client.model.SettingsManagementSection
import cc.hhhl.client.model.SettingsWebhookDetail
import cc.hhhl.client.model.SettingsWebhookCreateInput
import cc.hhhl.client.model.SettingsWebhookUpdateInput
import cc.hhhl.client.state.SettingsGroup
import cc.hhhl.client.state.SettingsGroupKey
import cc.hhhl.client.state.SettingsItem
import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.HhhlCustomTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

open class SettingsRepository(
    private val tokenProvider: () -> String? = { null },
    private val api: SettingsApi = SharkeySettingsApi(),
    private val avatarDecorationRepository: AvatarDecorationRepository = AvatarDecorationRepository(tokenProvider),
) {
    open suspend fun loadRemotePreferences(): SettingsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsRepositoryResult.Unauthorized

        return mapResult(token, api.loadPreferences(token))
    }

    open suspend fun updatePrivacy(privacy: PrivacySettings): SettingsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsRepositoryResult.Unauthorized

        return mapResult(token, api.updatePreferences(token, SettingsPreferenceUpdate(privacy = privacy)))
    }

    open suspend fun updateNotifications(notifications: NotificationSettings): SettingsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsRepositoryResult.Unauthorized

        return mapResult(token, api.updatePreferences(token, SettingsPreferenceUpdate(notifications = notifications)))
    }

    open suspend fun updateFilters(filters: FilterSettings): SettingsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsRepositoryResult.Unauthorized

        return mapResult(token, api.updatePreferences(token, SettingsPreferenceUpdate(filters = filters)))
    }

    open suspend fun loadManagementSection(key: cc.hhhl.client.model.SettingsManagementSectionKey): SettingsManagementRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementRepositoryResult.Unauthorized

        if (key == cc.hhhl.client.model.SettingsManagementSectionKey.AvatarDecorations) {
            return when (val result = avatarDecorationRepository.load()) {
                is AvatarDecorationRepositoryResult.Success -> SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "头像挂件",
                        description = "服务器返回的可用头像挂件，头像组件会按用户资料里的挂件数据渲染。",
                        items = result.decorations.map { decoration ->
                            cc.hhhl.client.model.SettingsManagementItem(
                                id = decoration.id,
                                title = decoration.id.ifBlank { "未命名挂件" },
                                subtitle = decoration.url,
                                meta = "偏移 ${decoration.offsetX}, ${decoration.offsetY} · 角度 ${decoration.angle}",
                                badges = listOfNotNull(
                                    "预览".takeIf { decoration.url.isNotBlank() },
                                    "翻转".takeIf { decoration.flipH },
                                ),
                            )
                        },
                    ),
                )
                AvatarDecorationRepositoryResult.Unauthorized -> SettingsManagementRepositoryResult.Unauthorized
                is AvatarDecorationRepositoryResult.Error -> SettingsManagementRepositoryResult.Error(result.message)
            }
        }

        return when (val result = api.loadManagementSection(token, key)) {
            is SettingsManagementSectionResult.Success -> SettingsManagementRepositoryResult.Success(result.section)
            SettingsManagementSectionResult.Unauthorized -> SettingsManagementRepositoryResult.Unauthorized
            is SettingsManagementSectionResult.NetworkError -> {
                SettingsManagementRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementSectionResult.ServerError -> SettingsManagementRepositoryResult.Error(result.message)
        }
    }

    open suspend fun revokeApiToken(tokenId: String): SettingsManagementMutationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementMutationRepositoryResult.Unauthorized

        return when (val result = api.revokeApiToken(token, tokenId)) {
            SettingsManagementMutationResult.Success -> SettingsManagementMutationRepositoryResult.Success
            SettingsManagementMutationResult.Unauthorized -> SettingsManagementMutationRepositoryResult.Unauthorized
            is SettingsManagementMutationResult.NetworkError -> {
                SettingsManagementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementMutationResult.ServerError -> SettingsManagementMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun createInvite(): SettingsManagementMutationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementMutationRepositoryResult.Unauthorized

        return when (val result = api.createInvite(token)) {
            SettingsManagementMutationResult.Success -> SettingsManagementMutationRepositoryResult.Success
            SettingsManagementMutationResult.Unauthorized -> SettingsManagementMutationRepositoryResult.Unauthorized
            is SettingsManagementMutationResult.NetworkError -> {
                SettingsManagementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementMutationResult.ServerError -> SettingsManagementMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun deleteInvite(inviteId: String): SettingsManagementMutationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementMutationRepositoryResult.Unauthorized

        return when (val result = api.deleteInvite(token, inviteId)) {
            SettingsManagementMutationResult.Success -> SettingsManagementMutationRepositoryResult.Success
            SettingsManagementMutationResult.Unauthorized -> SettingsManagementMutationRepositoryResult.Unauthorized
            is SettingsManagementMutationResult.NetworkError -> {
                SettingsManagementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementMutationResult.ServerError -> SettingsManagementMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun loginSharedAccess(grantId: String): SettingsSharedAccessLoginRepositoryResult {
        val cleanGrantId = grantId.trim()
        if (cleanGrantId.isEmpty()) {
            return SettingsSharedAccessLoginRepositoryResult.Error("无法读取共享访问")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsSharedAccessLoginRepositoryResult.Unauthorized

        return when (val result = api.loginSharedAccess(token, cleanGrantId)) {
            is SettingsSharedAccessLoginResult.Success -> SettingsSharedAccessLoginRepositoryResult.Success(
                userId = result.userId,
                token = result.token,
            )
            SettingsSharedAccessLoginResult.Unauthorized -> SettingsSharedAccessLoginRepositoryResult.Unauthorized
            is SettingsSharedAccessLoginResult.NetworkError -> {
                SettingsSharedAccessLoginRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsSharedAccessLoginResult.ServerError -> SettingsSharedAccessLoginRepositoryResult.Error(result.message)
        }
    }

    open suspend fun deleteWebhook(webhookId: String): SettingsManagementMutationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementMutationRepositoryResult.Unauthorized

        return when (val result = api.deleteWebhook(token, webhookId)) {
            SettingsManagementMutationResult.Success -> SettingsManagementMutationRepositoryResult.Success
            SettingsManagementMutationResult.Unauthorized -> SettingsManagementMutationRepositoryResult.Unauthorized
            is SettingsManagementMutationResult.NetworkError -> {
                SettingsManagementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementMutationResult.ServerError -> SettingsManagementMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun loadWebhook(webhookId: String): SettingsWebhookDetailRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsWebhookDetailRepositoryResult.Unauthorized

        return when (val result = api.loadWebhook(token, webhookId)) {
            is SettingsWebhookDetailResult.Success -> SettingsWebhookDetailRepositoryResult.Success(result.webhook)
            SettingsWebhookDetailResult.Unauthorized -> SettingsWebhookDetailRepositoryResult.Unauthorized
            is SettingsWebhookDetailResult.NetworkError -> {
                SettingsWebhookDetailRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsWebhookDetailResult.ServerError -> SettingsWebhookDetailRepositoryResult.Error(result.message)
        }
    }

    open suspend fun updateWebhookActive(
        webhookId: String,
        active: Boolean,
    ): SettingsManagementMutationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementMutationRepositoryResult.Unauthorized

        return when (val result = api.updateWebhookActive(token, webhookId, active)) {
            SettingsManagementMutationResult.Success -> SettingsManagementMutationRepositoryResult.Success
            SettingsManagementMutationResult.Unauthorized -> SettingsManagementMutationRepositoryResult.Unauthorized
            is SettingsManagementMutationResult.NetworkError -> {
                SettingsManagementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementMutationResult.ServerError -> SettingsManagementMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun testWebhook(webhookId: String): SettingsManagementMutationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementMutationRepositoryResult.Unauthorized

        return when (val result = api.testWebhook(token, webhookId)) {
            SettingsManagementMutationResult.Success -> SettingsManagementMutationRepositoryResult.Success
            SettingsManagementMutationResult.Unauthorized -> SettingsManagementMutationRepositoryResult.Unauthorized
            is SettingsManagementMutationResult.NetworkError -> {
                SettingsManagementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementMutationResult.ServerError -> SettingsManagementMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun createWebhook(input: SettingsWebhookCreateInput): SettingsManagementMutationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementMutationRepositoryResult.Unauthorized

        return when (val result = api.createWebhook(token, input)) {
            SettingsManagementMutationResult.Success -> SettingsManagementMutationRepositoryResult.Success
            SettingsManagementMutationResult.Unauthorized -> SettingsManagementMutationRepositoryResult.Unauthorized
            is SettingsManagementMutationResult.NetworkError -> {
                SettingsManagementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementMutationResult.ServerError -> SettingsManagementMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun updateWebhook(
        webhookId: String,
        input: SettingsWebhookUpdateInput,
    ): SettingsManagementMutationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return SettingsManagementMutationRepositoryResult.Unauthorized

        return when (val result = api.updateWebhook(token, webhookId, input)) {
            SettingsManagementMutationResult.Success -> SettingsManagementMutationRepositoryResult.Success
            SettingsManagementMutationResult.Unauthorized -> SettingsManagementMutationRepositoryResult.Unauthorized
            is SettingsManagementMutationResult.NetworkError -> {
                SettingsManagementMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsManagementMutationResult.ServerError -> SettingsManagementMutationRepositoryResult.Error(result.message)
        }
    }

    fun groups(
        selectedTheme: HhhlThemePreset = HhhlThemePreset.System,
        customTheme: HhhlCustomTheme = HhhlCustomTheme(),
        selectedTimelineDensity: TimelineDensity = TimelineDensity.Comfortable,
        selectedDefaultNoteVisibility: DefaultNoteVisibility = DefaultNoteVisibility.Public,
        selectedNotificationBadgeMode: NotificationBadgeMode = NotificationBadgeMode.Show,
        aiSettings: AiSettings = AiSettings(),
        aiTasks: List<AiTask> = emptyList(),
        aiUsage: AiUsageWindow = AiUsageWindow(),
        listGesturesEnabled: Boolean = true,
        backgroundNotificationsEnabled: Boolean = false,
        specialCareBackgroundNotificationsEnabled: Boolean = true,
        accountDisplayName: String = "未登录",
        remotePreferences: SettingsPreferences? = null,
        isRemoteLoading: Boolean = false,
    ): List<SettingsGroup> {
        val remoteValue = if (isRemoteLoading) "同步中" else "网页版"
        return listOf(
            SettingsGroup(
                key = SettingsGroupKey.Appearance,
                label = "外观",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.Theme,
                        label = "主题",
                        value = selectedTheme.label,
                        icon = "色",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AdvancedTheme,
                        label = "高级自定义主题",
                        value = if (customTheme.enabled) "已自定义" else "未启用",
                        icon = "画",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.TimelineDensity,
                        label = "信息流密度",
                        value = selectedTimelineDensity.label,
                        icon = "密",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.ListGestures,
                        label = "列表手势",
                        value = listGesturesEnabled.onOffLabel(),
                        icon = "滑",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.AccountSecurity,
                label = "账号与安全",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.AccountProfile,
                        label = "账号资料",
                        value = accountDisplayName,
                        icon = "我",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.TwoFactor,
                        label = "双重验证",
                        value = remotePreferences?.security?.twoFactorEnabled?.let { if (it) "已开启" else "未开启" }
                            ?: "状态未返回",
                        icon = "2F",
                        enabled = remotePreferences?.security?.twoFactorEnabled != null,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.Passkeys,
                        label = "Passkey",
                        value = remotePreferences?.security?.passkeysEnabled?.let { if (it) "已开启" else "未开启" }
                            ?: "状态未返回",
                        icon = "钥",
                        enabled = remotePreferences?.security?.passkeysEnabled != null,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.SigninHistory,
                        label = "登录记录",
                        value = remotePreferences?.security?.let { security ->
                            when {
                                security.signinHistoryCount == null -> null
                                security.latestSigninLabel != null ->
                                    "${security.signinHistoryCount} 条，最近 ${security.latestSigninLabel}"
                                else -> "${security.signinHistoryCount} 条"
                            }
                        } ?: remoteValue,
                        icon = "录",
                        enabled = remotePreferences?.security?.signinHistoryAvailable == true,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AvatarDecorations,
                        label = "头像挂件",
                        value = "同步服务器挂件",
                        icon = "挂",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Management,
                label = "管理",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.AdminDashboard,
                        label = "管理后台",
                        value = "举报、用户、角色和公告",
                        icon = "管",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Privacy,
                label = "隐私",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.DefaultNoteVisibility,
                        label = "默认可见范围",
                        value = selectedDefaultNoteVisibility.label,
                        icon = "权",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.LockAccount,
                        label = "关注需批准",
                        value = remotePreferences?.privacy?.isLocked?.onOffLabel() ?: remoteValue,
                        icon = "锁",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AutoAcceptFollowed,
                        label = "自动批准已关注者",
                        value = remotePreferences?.privacy?.autoAcceptFollowed?.onOffLabel() ?: remoteValue,
                        icon = "批",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.NoCrawle,
                        label = "拒绝搜索引擎索引",
                        value = remotePreferences?.privacy?.noCrawle?.onOffLabel() ?: remoteValue,
                        icon = "搜",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.PreventAiLearning,
                        label = "拒绝 AI 学习",
                        value = remotePreferences?.privacy?.preventAiLearning?.onOffLabel() ?: remoteValue,
                        icon = "AI",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.PublicReactions,
                        label = "公开回应记录",
                        value = remotePreferences?.privacy?.publicReactions?.onOffLabel() ?: remoteValue,
                        icon = "应",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Notifications,
                label = "通知",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.NotificationBadges,
                        label = "未读角标",
                        value = selectedNotificationBadgeMode.label,
                        icon = "铃",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.BackgroundNotifications,
                        label = "后台收消息",
                        value = backgroundNotificationsEnabled.onOffLabel(),
                        icon = "收",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.SpecialCareBackgroundNotifications,
                        label = "特别关心后台提醒",
                        value = specialCareBackgroundNotificationsEnabled.onOffLabel(),
                        icon = "特",
                        enabled = backgroundNotificationsEnabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.ChatMessageCache,
                        label = "聊天缓存",
                        value = "用于离线保留与按日期搜索历史消息",
                        icon = "存",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.MuteReactions,
                        label = "静音回应通知",
                        value = remotePreferences?.notifications?.mutedTypes?.contains("reaction")?.onOffLabel()
                            ?: remoteValue,
                        icon = "应",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.MuteFollows,
                        label = "静音关注通知",
                        value = remotePreferences?.notifications?.mutedTypes?.contains("follow")?.onOffLabel()
                            ?: remoteValue,
                        icon = "关",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Filters,
                label = "过滤",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.MutedWords,
                        label = "词语静音",
                        value = remotePreferences?.filters?.mutedWords?.size?.let { "$it 条" } ?: remoteValue,
                        icon = "词",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.HardMutedWords,
                        label = "强过滤词",
                        value = remotePreferences?.filters?.hardMutedWords?.size?.let { "$it 条" } ?: remoteValue,
                        icon = "滤",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.MutedInstances,
                        label = "静音实例",
                        value = remotePreferences?.filters?.mutedInstances?.size?.let { "$it 个" } ?: remoteValue,
                        icon = "域",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Ai,
                label = "AI",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.AiEnabled,
                        label = "AI 助手",
                        value = aiSettings.enabled.onOffLabel(),
                        icon = "AI",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiProvider,
                        label = "Provider",
                        value = aiSettings.provider.label,
                        icon = "源",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiBaseUrl,
                        label = "Base URL",
                        value = aiSettings.cleanBaseUrl.ifBlank { "未配置" },
                        icon = "址",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiApiKey,
                        label = "API Key",
                        value = if (aiSettings.apiKey.isBlank()) "未配置" else "已配置",
                        icon = "钥",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiChatModel,
                        label = "对话模型",
                        value = aiSettings.chatModel.ifBlank { "未配置" },
                        icon = "模",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiFastModel,
                        label = "快速模型",
                        value = aiSettings.fastModel.ifBlank { "未配置" },
                        icon = "快",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiLongContextModel,
                        label = "长上下文模型",
                        value = aiSettings.longContextModel.ifBlank { "未配置" },
                        icon = "长",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiVisionModel,
                        label = "视觉模型",
                        value = aiSettings.visionModel.ifBlank { "未配置" },
                        icon = "视",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiEmbeddingModel,
                        label = "向量模型",
                        value = aiSettings.embeddingModel.ifBlank { "未配置" },
                        icon = "向",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiReadPermissions,
                        label = "读取权限",
                        value = aiReadPermissionSummary(aiSettings),
                        icon = "权",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiAutomation,
                        label = "AI 自动化",
                        value = aiSettings.automationAllowed.onOffLabel(),
                        icon = "自",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiBackground,
                        label = "后台 AI 队列",
                        value = buildString {
                            append(aiSettings.backgroundAllowed.onOffLabel())
                            if (aiSettings.wifiOnlyBackground) append(" · 仅 Wi-Fi")
                        },
                        icon = "队",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiQueue,
                        label = "AI 队列",
                        value = aiQueueSummary(aiTasks, aiSettings, aiUsage),
                        icon = "列",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiLimits,
                        label = "用量限制",
                        value = "${aiSettings.maxInputChars} 字输入 · ${aiSettings.maxOutputTokens} token · ${aiSettings.dailyRequestLimit} 次/日",
                        icon = "限",
                        enabled = aiSettings.enabled,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AiTone,
                        label = "语气偏好",
                        value = aiSettings.tonePreference.ifBlank { "默认" },
                        icon = "调",
                        enabled = aiSettings.enabled,
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Integrations,
                label = "授权",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.ApiTokens,
                        label = "访问令牌",
                        value = remotePreferences?.integrations?.apiTokensCount?.let { "$it 个" } ?: remoteValue,
                        icon = "令",
                        enabled = remotePreferences?.integrations?.apiTokensAvailable == true,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.Invites,
                        label = "邀请码",
                        value = remotePreferences?.integrations?.let { integrations ->
                            when {
                                integrations.invitesCount == null -> null
                                integrations.inviteRemaining != null ->
                                    "${integrations.invitesCount} 个，剩余 ${integrations.inviteRemaining} 个"
                                else -> "${integrations.invitesCount} 个"
                            }
                        } ?: remoteValue,
                        icon = "邀",
                        enabled = remotePreferences?.integrations?.invitesAvailable == true,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.SharedAccess,
                        label = "共享访问",
                        value = remotePreferences?.integrations?.sharedAccessCount?.let { "$it 个" } ?: remoteValue,
                        icon = "共",
                        enabled = remotePreferences?.integrations?.sharedAccessAvailable == true,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.Webhooks,
                        label = "Webhook",
                        value = remotePreferences?.integrations?.let { integrations ->
                            when {
                                integrations.webhooksCount == null -> null
                                integrations.activeWebhooksCount != null ->
                                    "${integrations.webhooksCount} 个，启用 ${integrations.activeWebhooksCount} 个"
                                else -> "${integrations.webhooksCount} 个"
                            }
                        } ?: remoteValue,
                        icon = "钩",
                        enabled = remotePreferences?.integrations?.webhooksAvailable == true,
                    ),
                    SettingsItem(
                        key = SettingsItemKey.AuthorizedApps,
                        label = "已授权应用",
                        value = remotePreferences?.security?.authorizedAppsCount?.let { "$it 个" } ?: remoteValue,
                        icon = "应",
                        enabled = remotePreferences?.security?.authorizedAppsAvailable == true,
                    ),
                ),
            ),
        )
    }

    private suspend fun mapResult(
        token: String,
        result: SettingsPreferencesResult,
    ): SettingsRepositoryResult {
        return when (result) {
            is SettingsPreferencesResult.Success -> SettingsRepositoryResult.Success(
                result.preferences.withCapabilityCounts(token),
            )
            SettingsPreferencesResult.Unauthorized -> SettingsRepositoryResult.Unauthorized
            is SettingsPreferencesResult.NetworkError -> {
                SettingsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is SettingsPreferencesResult.ServerError -> SettingsRepositoryResult.Error(result.message)
        }
    }

    private suspend fun SettingsPreferences.withCapabilityCounts(token: String): SettingsPreferences {
        return coroutineScope {
            val apiTokens = async { api.loadApiTokens(token) }
            val invites = async { api.loadInvites(token) }
            val sharedAccess = async { api.loadSharedAccess(token) }
            val webhooks = async { api.loadWebhooks(token) }
            val authorizedApps = async { api.loadAuthorizedApps(token) }
            val signinHistory = async { api.loadSigninHistory(token) }

            copy(
                security = security
                    .withAuthorizedApps(authorizedApps.await())
                    .withSigninHistory(signinHistory.await()),
                integrations = integrations
                    .withApiTokens(apiTokens.await())
                    .withInvites(invites.await())
                    .withSharedAccess(sharedAccess.await())
                    .withWebhooks(webhooks.await()),
            )
        }
    }

    private fun SecuritySettings.withAuthorizedApps(result: SettingsCapabilityResult): SecuritySettings {
        return when (result) {
            is SettingsCapabilityResult.Count -> copy(
                authorizedAppsAvailable = true,
                authorizedAppsCount = result.total,
            )
            SettingsCapabilityResult.Available -> copy(authorizedAppsAvailable = true)
            is SettingsCapabilityResult.Unsupported -> copy(authorizedAppsAvailable = false)
        }
    }

    private fun SecuritySettings.withSigninHistory(result: SettingsCapabilityResult): SecuritySettings {
        return when (result) {
            is SettingsCapabilityResult.Count -> copy(
                signinHistoryAvailable = true,
                signinHistoryCount = result.total,
                latestSigninLabel = result.latestLabel,
            )
            SettingsCapabilityResult.Available -> copy(signinHistoryAvailable = true)
            is SettingsCapabilityResult.Unsupported -> copy(signinHistoryAvailable = false)
        }
    }

    private fun IntegrationSettings.withApiTokens(result: SettingsCapabilityResult): IntegrationSettings {
        return when (result) {
            is SettingsCapabilityResult.Count -> copy(
                apiTokensAvailable = true,
                apiTokensCount = result.total,
            )
            SettingsCapabilityResult.Available -> copy(apiTokensAvailable = true)
            is SettingsCapabilityResult.Unsupported -> copy(apiTokensAvailable = false)
        }
    }

    private fun IntegrationSettings.withInvites(result: SettingsCapabilityResult): IntegrationSettings {
        return when (result) {
            is SettingsCapabilityResult.Count -> copy(
                invitesAvailable = true,
                invitesCount = result.total,
                inviteRemaining = result.active,
            )
            SettingsCapabilityResult.Available -> copy(invitesAvailable = true)
            is SettingsCapabilityResult.Unsupported -> copy(invitesAvailable = false)
        }
    }

    private fun IntegrationSettings.withSharedAccess(result: SettingsCapabilityResult): IntegrationSettings {
        return when (result) {
            is SettingsCapabilityResult.Count -> copy(
                sharedAccessAvailable = true,
                sharedAccessCount = result.total,
            )
            SettingsCapabilityResult.Available -> copy(sharedAccessAvailable = true)
            is SettingsCapabilityResult.Unsupported -> copy(sharedAccessAvailable = false)
        }
    }

    private fun IntegrationSettings.withWebhooks(result: SettingsCapabilityResult): IntegrationSettings {
        return when (result) {
            is SettingsCapabilityResult.Count -> copy(
                webhooksAvailable = true,
                webhooksCount = result.total,
                activeWebhooksCount = result.active,
            )
            SettingsCapabilityResult.Available -> copy(webhooksAvailable = true)
            is SettingsCapabilityResult.Unsupported -> copy(webhooksAvailable = false)
        }
    }

    companion object {
        fun defaultGroups(): List<SettingsGroup> = SettingsRepository().groups()
    }
}

sealed interface SettingsRepositoryResult {
    data class Success(val preferences: SettingsPreferences) : SettingsRepositoryResult

    data object Unauthorized : SettingsRepositoryResult

    data class Error(val message: String) : SettingsRepositoryResult
}

sealed interface SettingsManagementRepositoryResult {
    data class Success(val section: SettingsManagementSection) : SettingsManagementRepositoryResult

    data object Unauthorized : SettingsManagementRepositoryResult

    data class Error(val message: String) : SettingsManagementRepositoryResult
}

sealed interface SettingsManagementMutationRepositoryResult {
    data object Success : SettingsManagementMutationRepositoryResult

    data object Unauthorized : SettingsManagementMutationRepositoryResult

    data class Error(val message: String) : SettingsManagementMutationRepositoryResult
}

sealed interface SettingsWebhookDetailRepositoryResult {
    data class Success(val webhook: SettingsWebhookDetail) : SettingsWebhookDetailRepositoryResult

    data object Unauthorized : SettingsWebhookDetailRepositoryResult

    data class Error(val message: String) : SettingsWebhookDetailRepositoryResult
}

sealed interface SettingsSharedAccessLoginRepositoryResult {
    data class Success(
        val userId: String,
        val token: String,
    ) : SettingsSharedAccessLoginRepositoryResult

    data object Unauthorized : SettingsSharedAccessLoginRepositoryResult

    data class Error(val message: String) : SettingsSharedAccessLoginRepositoryResult
}

private fun Boolean.onOffLabel(): String = if (this) "开启" else "关闭"

private fun aiReadPermissionSummary(settings: AiSettings): String {
    val enabled = buildList {
        if (settings.readTimelineAllowed) add("帖子")
        if (settings.readNotificationsAllowed) add("通知")
        if (settings.readChatAllowed) add(if (settings.readPrivateChatAllowed) "聊天" else "公开聊天")
        if (settings.readDraftsAllowed) add("草稿")
        if (settings.readProfileAllowed) add("资料")
    }
    return enabled.joinToString("、").ifBlank { "未授权读取" }
}

private fun aiQueueSummary(tasks: List<AiTask>, settings: AiSettings, usage: AiUsageWindow): String {
    val normalizedUsage = usage.normalizedAiUsage()
    val remaining = (settings.dailyRequestLimit - normalizedUsage.requestCount).coerceAtLeast(0)
    if (tasks.isEmpty()) return "今日 ${normalizedUsage.requestCount}/${settings.dailyRequestLimit} · 剩余 $remaining · 暂无任务"
    val pending = tasks.count { it.status == AiTaskStatus.Pending || it.status == AiTaskStatus.Running }
    val failed = tasks.count { it.status == AiTaskStatus.Failed }
    val latest = tasks.maxByOrNull { it.updatedAtEpochMillis }
    return buildString {
        append("今日 ${normalizedUsage.requestCount}/${settings.dailyRequestLimit} · 剩余 $remaining · ")
        if (pending > 0) append("待处理 $pending · ")
        append("已完成 ${tasks.count { it.status == AiTaskStatus.Completed }}")
        if (failed > 0) append(" · 失败 $failed")
        latest?.let { append(" · 最近 ${it.kind.label}") }
    }
}
