package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
enum class SettingsManagementAction {
    RevokeToken,
    CreateInvite,
    DeleteInvite,
    LoginSharedAccess,
    EditWebhook,
    EnableWebhook,
    DisableWebhook,
    TestWebhook,
    DeleteWebhook,
}

@Immutable
data class SettingsManagementItemAction(
    val type: SettingsManagementAction,
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

@Immutable
data class SettingsManagementItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val meta: String = "",
    val permissions: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    val actions: List<SettingsManagementItemAction> = emptyList(),
)

@Immutable
data class SettingsManagementSection(
    val key: SettingsManagementSectionKey,
    val title: String,
    val description: String = "",
    val items: List<SettingsManagementItem> = emptyList(),
    val errorMessage: String? = null,
    val supportsPrimaryAction: Boolean = false,
)

enum class SettingsManagementSectionKey {
    ApiTokens,
    SharedAccess,
    AuthorizedApps,
    Webhooks,
    SigninHistory,
    AvatarDecorations,
    Invites,
}

@Immutable
data class SettingsWebhookCreateInput(
    val name: String,
    val url: String,
    val secret: String = "",
    val events: List<String> = listOf("note"),
)

@Immutable
data class SettingsWebhookUpdateInput(
    val name: String,
    val url: String,
    val secret: String? = null,
    val events: List<String> = listOf("note"),
)

@Immutable
data class SettingsWebhookDetail(
    val id: String,
    val name: String,
    val url: String,
    val secret: String = "",
    val events: List<String> = listOf("note"),
    val active: Boolean = true,
)
