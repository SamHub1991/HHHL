package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsPreferences(
    val privacy: PrivacySettings = PrivacySettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val filters: FilterSettings = FilterSettings(),
    val security: SecuritySettings = SecuritySettings(),
    val integrations: IntegrationSettings = IntegrationSettings(),
)

@Immutable
data class PrivacySettings(
    val isLocked: Boolean = false,
    val autoAcceptFollowed: Boolean = true,
    val noCrawle: Boolean = false,
    val preventAiLearning: Boolean = false,
    val publicReactions: Boolean = true,
)

@Immutable
data class NotificationSettings(
    val mutedTypes: List<String> = emptyList(),
)

@Immutable
data class FilterSettings(
    val mutedWords: List<String> = emptyList(),
    val hardMutedWords: List<String> = emptyList(),
    val mutedInstances: List<String> = emptyList(),
)

@Immutable
data class SecuritySettings(
    val twoFactorEnabled: Boolean? = null,
    val passkeysEnabled: Boolean? = null,
    val signinHistoryAvailable: Boolean = false,
    val signinHistoryCount: Int? = null,
    val latestSigninLabel: String? = null,
    val authorizedAppsAvailable: Boolean = false,
    val authorizedAppsCount: Int? = null,
)

@Immutable
data class IntegrationSettings(
    val apiTokensAvailable: Boolean = false,
    val apiTokensCount: Int? = null,
    val invitesAvailable: Boolean = false,
    val invitesCount: Int? = null,
    val inviteRemaining: Int? = null,
    val sharedAccessAvailable: Boolean = false,
    val sharedAccessCount: Int? = null,
    val webhooksAvailable: Boolean = false,
    val webhooksCount: Int? = null,
    val activeWebhooksCount: Int? = null,
)

@Immutable
data class SettingsPreferenceUpdate(
    val privacy: PrivacySettings? = null,
    val notifications: NotificationSettings? = null,
    val filters: FilterSettings? = null,
)
