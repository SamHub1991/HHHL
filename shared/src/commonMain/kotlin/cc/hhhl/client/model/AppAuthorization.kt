package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class AuthorizedApp(
    val id: String,
    val name: String,
    val callbackUrl: String? = null,
    val permissions: List<String> = emptyList(),
    val secret: String? = null,
    val isAuthorized: Boolean = false,
)

@Immutable
data class AppCreateInput(
    val name: String,
    val description: String,
    val permissions: List<String>,
    val callbackUrl: String? = null,
)

@Immutable
data class AuthSession(
    val token: String,
    val url: String,
)

@Immutable
data class AuthSessionDetail(
    val id: String,
    val app: AuthorizedApp,
    val token: String,
)

@Immutable
data class AuthSessionUserKey(
    val accessToken: String,
    val user: User,
)

@Immutable
data class MiAuthTokenInput(
    val session: String?,
    val permissions: List<String>,
    val name: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val grantees: List<String> = emptyList(),
    val rank: String? = null,
)
