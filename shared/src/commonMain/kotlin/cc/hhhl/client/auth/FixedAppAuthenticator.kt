package cc.hhhl.client.auth

import cc.hhhl.client.api.AppAuthorizationApi
import cc.hhhl.client.api.AppAuthorizationResult
import cc.hhhl.client.api.SharkeyAppAuthorizationApi
import cc.hhhl.client.model.User

data class FixedAppAuthConfig(
    val clientId: String,
    val appSecret: String,
) {
    val isEnabled: Boolean
        get() = appSecret.isNotBlank()
}

class FixedAppAuthenticator(
    private val config: FixedAppAuthConfig,
    private val appAuthorizationApi: AppAuthorizationApi = SharkeyAppAuthorizationApi(),
    private val fallback: Authenticator = SharkeyAuthApi(),
) : Authenticator {
    override fun buildAuthorizationUrl(session: String): String {
        return fallback.buildAuthorizationUrl(session)
    }

    override suspend fun prepareBrowserLogin(session: String): BrowserLoginRequestResult {
        val secret = config.appSecret.trim()
        if (secret.isEmpty()) return fallback.prepareBrowserLogin(session)

        return when (val result = appAuthorizationApi.generateAuthSession(secret)) {
            is AppAuthorizationResult.Success -> BrowserLoginRequestResult.Success(
                BrowserLoginRequest(
                    session = result.value.token,
                    url = result.value.url,
                ),
            )
            AppAuthorizationResult.Unauthorized -> BrowserLoginRequestResult.ServerError(
                statusCode = 401,
                message = "应用授权配置无效，请检查 Client Secret",
            )
            is AppAuthorizationResult.NetworkError -> BrowserLoginRequestResult.NetworkError(result.message)
            is AppAuthorizationResult.ServerError -> BrowserLoginRequestResult.ServerError(
                statusCode = result.statusCode,
                message = result.message,
            )
        }
    }

    override suspend fun checkSession(session: String): AuthResult {
        val secret = config.appSecret.trim()
        if (secret.isEmpty()) return fallback.checkSession(session)

        return when (val result = appAuthorizationApi.fetchAuthSessionUserKey(secret, session)) {
            is AppAuthorizationResult.Success -> AuthResult.Success(
                token = result.value.accessToken,
                user = result.value.user.toAuthenticatedUser(),
            )
            AppAuthorizationResult.Unauthorized -> AuthResult.InvalidToken
            is AppAuthorizationResult.NetworkError -> AuthResult.NetworkError(result.message)
            is AppAuthorizationResult.ServerError -> AuthResult.ServerError(
                statusCode = result.statusCode,
                message = result.message,
            )
        }
    }

    override suspend fun verifyToken(token: String): AuthResult {
        return fallback.verifyToken(token)
    }

    override suspend fun signInWithPassword(
        username: String,
        password: String,
        token: String?,
    ): PasswordLoginResult {
        return fallback.signInWithPassword(username, password, token)
    }
}

private fun User.toAuthenticatedUser(): AuthenticatedUser {
    return AuthenticatedUser(
        id = id,
        username = username,
        displayName = displayName.ifBlank { username },
        avatarUrl = avatarUrl,
    )
}
