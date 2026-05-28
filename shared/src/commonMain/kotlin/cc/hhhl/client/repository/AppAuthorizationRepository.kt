package cc.hhhl.client.repository

import cc.hhhl.client.api.AppAuthorizationActionResult
import cc.hhhl.client.api.AppAuthorizationApi
import cc.hhhl.client.api.AppAuthorizationResult
import cc.hhhl.client.api.SharkeyAppAuthorizationApi
import cc.hhhl.client.model.AppCreateInput
import cc.hhhl.client.model.AuthSession
import cc.hhhl.client.model.AuthSessionDetail
import cc.hhhl.client.model.AuthSessionUserKey
import cc.hhhl.client.model.AuthorizedApp
import cc.hhhl.client.model.MiAuthTokenInput

open class AppAuthorizationRepository(
    private val tokenProvider: () -> String?,
    private val api: AppAuthorizationApi = SharkeyAppAuthorizationApi(),
) {
    open suspend fun loadCurrentApp(): AppAuthorizationRepositoryResult<AuthorizedApp> {
        return api.loadCurrentApp().toRepositoryResult()
    }

    open suspend fun showApp(appId: String): AppAuthorizationRepositoryResult<AuthorizedApp> {
        return api.showApp(appId).toRepositoryResult()
    }

    open suspend fun createApp(input: AppCreateInput): AppAuthorizationRepositoryResult<AuthorizedApp> {
        return api.createApp(input).toRepositoryResult()
    }

    open suspend fun loadMyApps(
        limit: Int = 20,
        offset: Int = 0,
    ): AppAuthorizationRepositoryResult<List<AuthorizedApp>> {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AppAuthorizationRepositoryResult.Unauthorized
        return api.loadMyApps(token, limit, offset).toRepositoryResult()
    }

    open suspend fun generateAuthSession(appSecret: String): AppAuthorizationRepositoryResult<AuthSession> {
        return api.generateAuthSession(appSecret).toRepositoryResult()
    }

    open suspend fun showAuthSession(token: String): AppAuthorizationRepositoryResult<AuthSessionDetail> {
        return api.showAuthSession(token).toRepositoryResult()
    }

    open suspend fun fetchAuthSessionUserKey(
        appSecret: String,
        token: String,
    ): AppAuthorizationRepositoryResult<AuthSessionUserKey> {
        return api.fetchAuthSessionUserKey(appSecret, token).toRepositoryResult()
    }

    open suspend fun acceptAuthSession(sessionToken: String): AppAuthorizationActionRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AppAuthorizationActionRepositoryResult.Unauthorized
        return when (val result = api.acceptAuthSession(token, sessionToken)) {
            AppAuthorizationActionResult.Success -> AppAuthorizationActionRepositoryResult.Success
            AppAuthorizationActionResult.Unauthorized -> AppAuthorizationActionRepositoryResult.Unauthorized
            is AppAuthorizationActionResult.NetworkError -> {
                AppAuthorizationActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AppAuthorizationActionResult.ServerError -> AppAuthorizationActionRepositoryResult.Error(result.message)
        }
    }

    open suspend fun generateMiAuthToken(input: MiAuthTokenInput): AppAuthorizationRepositoryResult<String> {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AppAuthorizationRepositoryResult.Unauthorized
        return api.generateMiAuthToken(token, input).toRepositoryResult()
    }
}

sealed interface AppAuthorizationRepositoryResult<out T> {
    data class Success<T>(val value: T) : AppAuthorizationRepositoryResult<T>
    data object Unauthorized : AppAuthorizationRepositoryResult<Nothing>
    data class Error(val message: String) : AppAuthorizationRepositoryResult<Nothing>
}

sealed interface AppAuthorizationActionRepositoryResult {
    data object Success : AppAuthorizationActionRepositoryResult
    data object Unauthorized : AppAuthorizationActionRepositoryResult
    data class Error(val message: String) : AppAuthorizationActionRepositoryResult
}

private fun <T> AppAuthorizationResult<T>.toRepositoryResult(): AppAuthorizationRepositoryResult<T> {
    return when (this) {
        is AppAuthorizationResult.Success -> AppAuthorizationRepositoryResult.Success(value)
        AppAuthorizationResult.Unauthorized -> AppAuthorizationRepositoryResult.Unauthorized
        is AppAuthorizationResult.NetworkError -> AppAuthorizationRepositoryResult.Error("无法连接服务器：$message")
        is AppAuthorizationResult.ServerError -> AppAuthorizationRepositoryResult.Error(message)
    }
}
