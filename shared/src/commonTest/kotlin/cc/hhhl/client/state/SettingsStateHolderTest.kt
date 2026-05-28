@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.hhhl.client.state

import cc.hhhl.client.auth.AuthenticatedUser
import cc.hhhl.client.model.FilterSettings
import cc.hhhl.client.model.IntegrationSettings
import cc.hhhl.client.model.NotificationSettings
import cc.hhhl.client.model.PrivacySettings
import cc.hhhl.client.model.SecuritySettings
import cc.hhhl.client.model.SettingsManagementAction
import cc.hhhl.client.model.SettingsManagementItemAction
import cc.hhhl.client.model.SettingsManagementItem
import cc.hhhl.client.model.SettingsManagementSection
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.SettingsWebhookCreateInput
import cc.hhhl.client.model.SettingsWebhookDetail
import cc.hhhl.client.model.SettingsWebhookUpdateInput
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.model.SettingsPreferences
import cc.hhhl.client.repository.SettingsManagementMutationRepositoryResult
import cc.hhhl.client.repository.SettingsManagementRepositoryResult
import cc.hhhl.client.repository.SettingsRepository
import cc.hhhl.client.repository.SettingsRepositoryResult
import cc.hhhl.client.repository.SettingsSharedAccessLoginRepositoryResult
import cc.hhhl.client.repository.SettingsWebhookDetailRepositoryResult
import cc.hhhl.client.theme.HhhlThemePreset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStateHolderTest {
    @Test
    fun groupsExposeCurrentLocalSettingValues() {
        val holder = SettingsStateHolder(repository = SettingsRepository())

        holder.sync(
            selectedTheme = HhhlThemePreset.Dim,
            selectedTimelineDensity = TimelineDensity.Compact,
            selectedDefaultNoteVisibility = DefaultNoteVisibility.Followers,
            selectedNotificationBadgeMode = NotificationBadgeMode.Hide,
            accountUser = AuthenticatedUser(
                id = "user-1",
                username = "alice",
                displayName = "Alice",
                avatarUrl = null,
            ),
        )

        val items = holder.state.value.groups.flatMap { it.items }

        assertEquals("暗灰", items.first { it.key == SettingsItemKey.Theme }.value)
        assertEquals("紧凑", items.first { it.key == SettingsItemKey.TimelineDensity }.value)
        assertEquals("Alice", items.first { it.key == SettingsItemKey.AccountProfile }.value)
        assertEquals("关注者", items.first { it.key == SettingsItemKey.DefaultNoteVisibility }.value)
        assertEquals("隐藏", items.first { it.key == SettingsItemKey.NotificationBadges }.value)
        assertTrue(items.first { it.key == SettingsItemKey.LockAccount }.enabled)
        assertFalse(items.any { it.value == null })
    }

    @Test
    fun accountFallsBackToUsernameWhenDisplayNameIsBlank() {
        val holder = SettingsStateHolder(repository = SettingsRepository())

        holder.sync(
            selectedTheme = HhhlThemePreset.System,
            selectedTimelineDensity = TimelineDensity.Comfortable,
            selectedDefaultNoteVisibility = DefaultNoteVisibility.Public,
            selectedNotificationBadgeMode = NotificationBadgeMode.Show,
            accountUser = AuthenticatedUser(
                id = "user-1",
                username = "alice",
                displayName = "",
                avatarUrl = null,
            ),
        )

        val profile = holder.state.value.groups
            .flatMap { it.items }
            .first { it.key == SettingsItemKey.AccountProfile }

        assertEquals("@alice", profile.value)
    }

    @Test
    fun remotePreferencesExposeSecurityPrivacyNotificationsAndFilters() = runTest {
        val holder = SettingsStateHolder(
            repository = fakeRepository(
                loadResult = SettingsRepositoryResult.Success(
                    SettingsPreferences(
                        privacy = cc.hhhl.client.model.PrivacySettings(
                            isLocked = true,
                            autoAcceptFollowed = false,
                            noCrawle = true,
                            preventAiLearning = true,
                            publicReactions = false,
                        ),
                        notifications = cc.hhhl.client.model.NotificationSettings(
                            mutedTypes = listOf("reaction"),
                        ),
                        filters = cc.hhhl.client.model.FilterSettings(
                            mutedWords = listOf("alpha"),
                            hardMutedWords = listOf("beta"),
                            mutedInstances = listOf("example.com"),
                        ),
                        security = cc.hhhl.client.model.SecuritySettings(twoFactorEnabled = true),
                    ),
                ),
            ),
            scope = this,
        )

        holder.refreshRemote()
        advanceUntilIdle()

        val items = holder.state.value.groups.flatMap { it.items }
        assertEquals("已开启", items.first { it.key == SettingsItemKey.TwoFactor }.value)
        assertEquals("状态未返回", items.first { it.key == SettingsItemKey.Passkeys }.value)
        assertEquals("开启", items.first { it.key == SettingsItemKey.LockAccount }.value)
        assertEquals("开启", items.first { it.key == SettingsItemKey.MuteReactions }.value)
        assertEquals("1 条", items.first { it.key == SettingsItemKey.MutedWords }.value)
        assertEquals("1 个", items.first { it.key == SettingsItemKey.MutedInstances }.value)
    }

    @Test
    fun remotePreferencesExposeCapabilityCounts() = runTest {
        val holder = SettingsStateHolder(
            repository = fakeRepository(
                loadResult = SettingsRepositoryResult.Success(
                    SettingsPreferences(
                        security = SecuritySettings(
                            twoFactorEnabled = true,
                            passkeysEnabled = true,
                            signinHistoryAvailable = true,
                            signinHistoryCount = 3,
                            latestSigninLabel = "今天 23:49",
                            authorizedAppsAvailable = true,
                            authorizedAppsCount = 2,
                        ),
                        integrations = IntegrationSettings(
                            apiTokensAvailable = true,
                            apiTokensCount = 4,
                            sharedAccessAvailable = true,
                            sharedAccessCount = 1,
                            webhooksAvailable = true,
                            webhooksCount = 5,
                            activeWebhooksCount = 3,
                        ),
                    ),
                ),
            ),
            scope = this,
        )

        holder.refreshRemote()
        advanceUntilIdle()

        val items = holder.state.value.groups.flatMap { it.items }
        assertEquals("已开启", items.first { it.key == SettingsItemKey.Passkeys }.value)
        assertEquals("3 条，最近 今天 23:49", items.first { it.key == SettingsItemKey.SigninHistory }.value)
        assertEquals("4 个", items.first { it.key == SettingsItemKey.ApiTokens }.value)
        assertEquals("1 个", items.first { it.key == SettingsItemKey.SharedAccess }.value)
        assertEquals("5 个，启用 3 个", items.first { it.key == SettingsItemKey.Webhooks }.value)
        assertEquals("2 个", items.first { it.key == SettingsItemKey.AuthorizedApps }.value)
    }

    @Test
    fun updatedRemotePreferencesCanKeepCapabilityCounts() = runTest {
        val updatedPreferences = SettingsPreferences(
            privacy = PrivacySettings(isLocked = true),
            notifications = NotificationSettings(mutedTypes = listOf("reaction")),
            filters = FilterSettings(mutedWords = listOf("alpha")),
            security = SecuritySettings(
                signinHistoryAvailable = true,
                signinHistoryCount = 2,
                latestSigninLabel = "今天 23:49",
                authorizedAppsAvailable = true,
                authorizedAppsCount = 1,
            ),
            integrations = IntegrationSettings(
                apiTokensAvailable = true,
                apiTokensCount = 3,
                sharedAccessAvailable = true,
                sharedAccessCount = 2,
                webhooksAvailable = true,
                webhooksCount = 2,
                activeWebhooksCount = 1,
            ),
        )
        val repository = object : SettingsRepository() {
            override suspend fun loadRemotePreferences(): SettingsRepositoryResult {
                return SettingsRepositoryResult.Success(updatedPreferences)
            }

            override suspend fun updatePrivacy(privacy: PrivacySettings): SettingsRepositoryResult {
                return SettingsRepositoryResult.Success(updatedPreferences.copy(privacy = privacy))
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)

        holder.refreshRemote()
        advanceUntilIdle()
        holder.togglePrivacy(SettingsItemKey.LockAccount, false)
        advanceUntilIdle()

        val items = holder.state.value.groups.flatMap { it.items }
        assertEquals("2 条，最近 今天 23:49", items.first { it.key == SettingsItemKey.SigninHistory }.value)
        assertEquals("3 个", items.first { it.key == SettingsItemKey.ApiTokens }.value)
        assertEquals("2 个", items.first { it.key == SettingsItemKey.SharedAccess }.value)
        assertEquals("2 个，启用 1 个", items.first { it.key == SettingsItemKey.Webhooks }.value)
        assertEquals("1 个", items.first { it.key == SettingsItemKey.AuthorizedApps }.value)
    }

    @Test
    fun openManagementLoadsSectionAndMutationRefreshesItems() = runTest {
        var revokedTokenId: String? = null
        var loadCount = 0
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                loadCount += 1
                return SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "访问令牌",
                        items = if (loadCount == 1) {
                            listOf(
                                SettingsManagementItem(
                                    id = "token-1",
                                    title = "Desktop app",
                                    actions = listOf(
                                        SettingsManagementItemAction(
                                            type = SettingsManagementAction.RevokeToken,
                                            label = "撤销",
                                            enabled = true,
                                            destructive = true,
                                        ),
                                    ),
                                ),
                            )
                        } else {
                            emptyList()
                        },
                    ),
                )
            }

            override suspend fun revokeApiToken(tokenId: String): SettingsManagementMutationRepositoryResult {
                revokedTokenId = tokenId
                return SettingsManagementMutationRepositoryResult.Success
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)

        holder.openManagement(SettingsManagementSectionKey.ApiTokens)
        advanceUntilIdle()

        assertEquals(SettingsManagementSectionKey.ApiTokens, holder.state.value.openedManagementKey)
        assertEquals(1, holder.state.value.managementSection?.items?.size)

        holder.performManagementAction(SettingsManagementAction.RevokeToken, "token-1")
        advanceUntilIdle()

        assertEquals("token-1", revokedTokenId)
        assertEquals(0, holder.state.value.managementSection?.items?.size)
        assertEquals("操作已完成", holder.state.value.managementMessage)
    }

    @Test
    fun openingAnotherManagementSectionInvalidatesPendingSectionLoad() = runTest {
        val tokenLoad = CompletableDeferred<SettingsManagementRepositoryResult>()
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                return if (key == SettingsManagementSectionKey.ApiTokens) {
                    tokenLoad.await()
                } else {
                    SettingsManagementRepositoryResult.Success(
                        SettingsManagementSection(
                            key = key,
                            title = "Webhook",
                            items = listOf(SettingsManagementItem(id = "webhook-1", title = "Deploy")),
                        ),
                    )
                }
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)

        holder.openManagement(SettingsManagementSectionKey.ApiTokens)
        runCurrent()
        holder.openManagement(SettingsManagementSectionKey.Webhooks)
        advanceUntilIdle()
        tokenLoad.complete(
            SettingsManagementRepositoryResult.Success(
                SettingsManagementSection(
                    key = SettingsManagementSectionKey.ApiTokens,
                    title = "访问令牌",
                    items = listOf(SettingsManagementItem(id = "token-1", title = "Desktop app")),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(SettingsManagementSectionKey.Webhooks, holder.state.value.openedManagementKey)
        assertEquals("Webhook", holder.state.value.managementSection?.title)
        assertEquals(listOf("webhook-1"), holder.state.value.managementSection?.items?.map { it.id })
    }

    @Test
    fun deleteWebhookRefreshesSectionAndKeepsSuccessMessage() = runTest {
        var deletedWebhookId: String? = null
        var loadCount = 0
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                loadCount += 1
                return SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "Webhook",
                        items = if (loadCount == 1) {
                            listOf(
                                SettingsManagementItem(
                                    id = "webhook-1",
                                    title = "Deploy",
                                    actions = listOf(
                                        SettingsManagementItemAction(
                                            type = SettingsManagementAction.DeleteWebhook,
                                            label = "删除",
                                            enabled = true,
                                            destructive = true,
                                        ),
                                    ),
                                ),
                            )
                        } else {
                            emptyList()
                        },
                    ),
                )
            }

            override suspend fun deleteWebhook(webhookId: String): SettingsManagementMutationRepositoryResult {
                deletedWebhookId = webhookId
                return SettingsManagementMutationRepositoryResult.Success
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)

        holder.openManagement(SettingsManagementSectionKey.Webhooks)
        advanceUntilIdle()
        holder.performManagementAction(SettingsManagementAction.DeleteWebhook, "webhook-1")
        advanceUntilIdle()

        assertEquals("webhook-1", deletedWebhookId)
        assertEquals(0, holder.state.value.managementSection?.items?.size)
        assertEquals("Webhook 已删除", holder.state.value.managementMessage)
    }

    @Test
    fun toggleWebhookAndTestKeepRealActionMessages() = runTest {
        var activeWebhookId: String? = null
        var activeValue: Boolean? = null
        var testedWebhookId: String? = null
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                return SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "Webhook",
                        items = listOf(
                            SettingsManagementItem(
                                id = "webhook-1",
                                title = "Deploy",
                                actions = listOf(
                                    SettingsManagementItemAction(
                                        type = SettingsManagementAction.DisableWebhook,
                                        label = "停用",
                                        enabled = true,
                                    ),
                                    SettingsManagementItemAction(
                                        type = SettingsManagementAction.TestWebhook,
                                        label = "发送测试",
                                        enabled = true,
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }

            override suspend fun updateWebhookActive(
                webhookId: String,
                active: Boolean,
            ): SettingsManagementMutationRepositoryResult {
                activeWebhookId = webhookId
                activeValue = active
                return SettingsManagementMutationRepositoryResult.Success
            }

            override suspend fun testWebhook(webhookId: String): SettingsManagementMutationRepositoryResult {
                testedWebhookId = webhookId
                return SettingsManagementMutationRepositoryResult.Success
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)

        holder.openManagement(SettingsManagementSectionKey.Webhooks)
        advanceUntilIdle()
        holder.performManagementAction(SettingsManagementAction.DisableWebhook, "webhook-1")
        advanceUntilIdle()

        assertEquals("webhook-1", activeWebhookId)
        assertEquals(false, activeValue)
        assertEquals("Webhook 已停用", holder.state.value.managementMessage)

        holder.performManagementAction(SettingsManagementAction.TestWebhook, "webhook-1")
        advanceUntilIdle()

        assertEquals("webhook-1", testedWebhookId)
        assertEquals("Webhook 测试已发送", holder.state.value.managementMessage)
    }

    @Test
    fun createWebhookRefreshesSectionAndKeepsSuccessMessage() = runTest {
        var createdInput: SettingsWebhookCreateInput? = null
        var loadCount = 0
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                loadCount += 1
                return SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "Webhook",
                        items = if (loadCount == 1) {
                            emptyList()
                        } else {
                            listOf(SettingsManagementItem(id = "webhook-1", title = "Deploy"))
                        },
                    ),
                )
            }

            override suspend fun createWebhook(input: SettingsWebhookCreateInput): SettingsManagementMutationRepositoryResult {
                createdInput = input
                return SettingsManagementMutationRepositoryResult.Success
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)
        val input = SettingsWebhookCreateInput(
            name = "Deploy",
            url = "https://example.com/hook",
            events = listOf("note", "reply"),
        )

        holder.openManagement(SettingsManagementSectionKey.Webhooks)
        advanceUntilIdle()
        holder.createWebhook(input)
        advanceUntilIdle()

        assertEquals(input, createdInput)
        assertEquals(1, holder.state.value.managementSection?.items?.size)
        assertEquals("Webhook 已创建", holder.state.value.managementMessage)
    }

    @Test
    fun updateWebhookRefreshesSectionAndKeepsSuccessMessage() = runTest {
        var updatedWebhookId: String? = null
        var updatedInput: SettingsWebhookUpdateInput? = null
        var loadCount = 0
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                loadCount += 1
                return SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "Webhook",
                        items = listOf(
                            SettingsManagementItem(
                                id = "webhook-1",
                                title = if (loadCount == 1) "Deploy" else "Deploy updated",
                            ),
                        ),
                    ),
                )
            }

            override suspend fun updateWebhook(
                webhookId: String,
                input: SettingsWebhookUpdateInput,
            ): SettingsManagementMutationRepositoryResult {
                updatedWebhookId = webhookId
                updatedInput = input
                return SettingsManagementMutationRepositoryResult.Success
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)
        val input = SettingsWebhookUpdateInput(
            name = "Deploy updated",
            url = "https://example.com/hook",
            events = listOf("note", "reply"),
        )

        holder.openManagement(SettingsManagementSectionKey.Webhooks)
        advanceUntilIdle()
        holder.updateWebhook("webhook-1", input)
        advanceUntilIdle()

        assertEquals("webhook-1", updatedWebhookId)
        assertEquals(input, updatedInput)
        assertEquals("Deploy updated", holder.state.value.managementSection?.items?.first()?.title)
        assertEquals("Webhook 已更新", holder.state.value.managementMessage)
    }

    @Test
    fun openWebhookEditorLoadsDetailFromRepository() = runTest {
        var loadedWebhookId: String? = null
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                return SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "Webhook",
                        items = listOf(SettingsManagementItem(id = "webhook-1", title = "Deploy")),
                    ),
                )
            }

            override suspend fun loadWebhook(webhookId: String): SettingsWebhookDetailRepositoryResult {
                loadedWebhookId = webhookId
                return SettingsWebhookDetailRepositoryResult.Success(
                    SettingsWebhookDetail(
                        id = webhookId,
                        name = "Deploy detail",
                        url = "https://example.com/detail",
                        secret = "secret-1",
                        events = listOf("note", "reply"),
                        active = true,
                    ),
                )
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)

        holder.openManagement(SettingsManagementSectionKey.Webhooks)
        advanceUntilIdle()
        holder.openWebhookEditor("webhook-1")
        advanceUntilIdle()

        assertEquals("webhook-1", loadedWebhookId)
        assertEquals("Deploy detail", holder.state.value.editingWebhook?.name)
        assertEquals("https://example.com/detail", holder.state.value.editingWebhook?.url)
        assertEquals("secret-1", holder.state.value.editingWebhook?.secret)
        assertEquals(listOf("note", "reply"), holder.state.value.editingWebhook?.events)
        assertFalse(holder.state.value.isWebhookEditorLoading)
        assertEquals(null, holder.state.value.managementMessage)
    }

    @Test
    fun sharedAccessLoginImportsReturnedSessionToken() = runTest {
        var loginGrantId: String? = null
        var importedToken: String? = null
        var importedUserId: String? = null
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                return SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "共享访问",
                        items = listOf(
                            SettingsManagementItem(
                                id = "grant-1",
                                title = "Mobile",
                                actions = listOf(
                                    SettingsManagementItemAction(
                                        type = SettingsManagementAction.LoginSharedAccess,
                                        label = "进入",
                                        enabled = true,
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }

            override suspend fun loginSharedAccess(grantId: String): SettingsSharedAccessLoginRepositoryResult {
                loginGrantId = grantId
                return SettingsSharedAccessLoginRepositoryResult.Success(
                    userId = "user-2",
                    token = "session-token-2",
                )
            }
        }
        val holder = SettingsStateHolder(
            repository = repository,
            scope = this,
            onSharedAccessLogin = { token, userId ->
                importedToken = token
                importedUserId = userId
            },
        )

        holder.openManagement(SettingsManagementSectionKey.SharedAccess)
        advanceUntilIdle()
        holder.performManagementAction(SettingsManagementAction.LoginSharedAccess, "grant-1")
        advanceUntilIdle()

        assertEquals("grant-1", loginGrantId)
        assertEquals("session-token-2", importedToken)
        assertEquals("user-2", importedUserId)
        assertEquals("共享访问已导入，正在切换账号", holder.state.value.managementMessage)
        assertFalse(holder.state.value.isManagementMutating)
    }

    @Test
    fun closingWebhookEditorInvalidatesPendingDetailLoad() = runTest {
        val pending = CompletableDeferred<SettingsWebhookDetailRepositoryResult>()
        val repository = object : SettingsRepository() {
            override suspend fun loadManagementSection(key: SettingsManagementSectionKey): SettingsManagementRepositoryResult {
                return SettingsManagementRepositoryResult.Success(
                    SettingsManagementSection(
                        key = key,
                        title = "Webhook",
                        items = listOf(SettingsManagementItem(id = "webhook-1", title = "Deploy")),
                    ),
                )
            }

            override suspend fun loadWebhook(webhookId: String): SettingsWebhookDetailRepositoryResult {
                return pending.await()
            }
        }
        val holder = SettingsStateHolder(repository = repository, scope = this)

        holder.openManagement(SettingsManagementSectionKey.Webhooks)
        advanceUntilIdle()
        holder.openWebhookEditor("webhook-1")
        runCurrent()
        assertTrue(holder.state.value.isWebhookEditorLoading)

        holder.closeWebhookEditor()
        pending.complete(
            SettingsWebhookDetailRepositoryResult.Success(
                SettingsWebhookDetail(
                    id = "webhook-1",
                    name = "Deploy detail",
                    url = "https://example.com/detail",
                ),
            ),
        )
        advanceUntilIdle()

        assertFalse(holder.state.value.isWebhookEditorLoading)
        assertEquals(null, holder.state.value.editingWebhook)
    }

    private fun fakeRepository(
        loadResult: SettingsRepositoryResult,
    ): SettingsRepository {
        return object : SettingsRepository() {
            override suspend fun loadRemotePreferences(): SettingsRepositoryResult = loadResult
        }
    }
}
