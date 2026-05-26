package cc.hhhl.client.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val pendingSession: String? = null,
    val user: AuthenticatedUser? = null,
    val sessionToken: String? = null,
)

class LoginStateHolder(
    private val authenticator: Authenticator,
    private val sessionIdProvider: () -> String = ::newMiAuthSessionId,
    private val tokenStore: AuthTokenStore = NoopAuthTokenStore,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState

    fun startBrowserLogin(openUrl: (String) -> Unit) {
        if (state.value.isLoading) return

        val current = state.value
        val hadPendingSession = current.pendingSession != null
        val session = current.pendingSession ?: sessionIdProvider()
        val url = authenticator.buildAuthorizationUrl(session)

        mutableState.update {
            it.copy(
                pendingSession = session,
                statusMessage = "请在浏览器完成登录",
                errorMessage = null,
            )
        }

        try {
            openUrl(url)
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(
                    pendingSession = if (hadPendingSession) session else null,
                    statusMessage = null,
                    errorMessage = "无法打开登录页：${error.message ?: "未知错误"}",
                )
            }
        }
    }

    fun completeBrowserLogin(session: String) {
        val current = state.value
        if (state.value.isLoading) return

        if (current.pendingSession == null) {
            mutableState.update {
                it.copy(
                    errorMessage = "登录会话不存在，请重新登录",
                    statusMessage = null,
                )
            }
            return
        }

        if (session != current.pendingSession) {
            mutableState.update {
                it.copy(
                    errorMessage = "登录会话不匹配，请重新登录",
                    statusMessage = null,
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = "正在登录...",
            )
        }

        scope.launch {
            when (val result = authenticator.checkSession(session)) {
                is AuthResult.Success -> {
                    val saveError = saveToken(result.token)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = saveError,
                            statusMessage = null,
                            pendingSession = null,
                            user = result.user,
                            sessionToken = result.token,
                        )
                    }
                }
                AuthResult.InvalidToken -> {
                    clearStoredToken()
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            user = null,
                            sessionToken = null,
                            pendingSession = null,
                            statusMessage = null,
                            errorMessage = "授权未完成或已失效，请重新登录",
                        )
                    }
                }
                is AuthResult.NetworkError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = "无法连接服务器：${result.message}",
                    )
                }
                is AuthResult.ServerError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun restoreStoredToken() {
        val current = state.value
        if (current.isLoading || current.user != null) return

        mutableState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = "正在登录...",
            )
        }

        scope.launch {
            val token = runCatching { tokenStore.readToken()?.trim() }
                .getOrElse { error ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = "无法读取登录状态：${error.message ?: "未知错误"}",
                        )
                    }
                    return@launch
                }

            if (token.isNullOrEmpty()) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                    )
                }
                return@launch
            }

            when (val result = authenticator.verifyToken(token)) {
                is AuthResult.Success -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        statusMessage = null,
                        user = result.user,
                        sessionToken = result.token,
                    )
                }
                AuthResult.InvalidToken -> {
                    clearStoredToken()
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            user = null,
                            sessionToken = null,
                            statusMessage = null,
                            errorMessage = "登录已失效，请重新登录",
                        )
                    }
                }
                is AuthResult.NetworkError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = "无法验证登录状态：${result.message}",
                    )
                }
                is AuthResult.ServerError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun logout() {
        scope.launch {
            clearStoredToken()
            mutableState.value = LoginUiState()
        }
    }

    private suspend fun saveToken(token: String): String? {
        return runCatching { tokenStore.saveToken(token) }
            .fold(
                onSuccess = { null },
                onFailure = { "登录成功，但无法保存登录状态：${it.message ?: "未知错误"}" },
            )
    }

    private suspend fun clearStoredToken() {
        runCatching { tokenStore.clearToken() }
    }
}

private fun newMiAuthSessionId(): String {
    val chars = "0123456789abcdef"
    val raw = CharArray(32) { chars[kotlin.random.Random.nextInt(chars.length)] }.concatToString()
    return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-" +
        "${raw.substring(16, 20)}-${raw.substring(20, 32)}"
}
