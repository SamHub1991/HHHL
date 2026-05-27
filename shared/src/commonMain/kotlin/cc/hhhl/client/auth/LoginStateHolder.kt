package cc.hhhl.client.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val pendingSession: String? = null,
    val user: AuthenticatedUser? = null,
    val sessionToken: String? = null,
    val accounts: List<AccountSession> = emptyList(),
    val currentAccountId: String? = null,
    val passwordLoginNeedsTotp: Boolean = false,
)

class LoginStateHolder(
    private val authenticator: Authenticator,
    private val sessionIdProvider: () -> String = ::newMiAuthSessionId,
    private val tokenStore: AuthTokenStore = NoopAuthTokenStore,
    private val nowProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onAccountRemoved: suspend (String) -> Unit = {},
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
                passwordLoginNeedsTotp = false,
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
                    val updatedAccounts = upsertCurrentAccount(
                        accounts = state.value.accounts,
                        token = result.token,
                        user = result.user,
                    )
                    val saveError = saveAccounts(updatedAccounts)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = saveError,
                            statusMessage = null,
                            pendingSession = null,
                            passwordLoginNeedsTotp = false,
                            user = result.user,
                            sessionToken = result.token,
                            accounts = updatedAccounts,
                            currentAccountId = updatedAccounts.currentAccount()?.id,
                        )
                    }
                }
                AuthResult.InvalidToken -> {
                    removeCurrentAccount()
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            user = null,
                            sessionToken = null,
                            pendingSession = null,
                            passwordLoginNeedsTotp = false,
                            statusMessage = null,
                            errorMessage = "授权未完成或已失效，请重新登录",
                        )
                    }
                }
                is AuthResult.NetworkError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = loginNetworkErrorMessage(result.message),
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

    fun signInWithPassword(username: String, password: String, token: String? = null) {
        if (state.value.isLoading) return

        val cleanUsername = username.trim().trimStart('@')
        val cleanToken = token?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanUsername.isEmpty() || password.isEmpty()) {
            mutableState.update {
                it.copy(
                    errorMessage = "请输入用户名和密码",
                    statusMessage = null,
                )
            }
            return
        }
        if (state.value.passwordLoginNeedsTotp && cleanToken == null) {
            mutableState.update {
                it.copy(
                    errorMessage = "请输入二步验证码或备用码",
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
                pendingSession = null,
            )
        }

        scope.launch {
            when (val result = authenticator.signInWithPassword(cleanUsername, password, cleanToken)) {
                is PasswordLoginResult.Success -> {
                    val updatedAccounts = upsertCurrentAccount(
                        accounts = state.value.accounts,
                        token = result.token,
                        user = result.user,
                    )
                    val saveError = saveAccounts(updatedAccounts)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = saveError,
                            statusMessage = null,
                            pendingSession = null,
                            passwordLoginNeedsTotp = false,
                            user = result.user,
                            sessionToken = result.token,
                            accounts = updatedAccounts,
                            currentAccountId = updatedAccounts.currentAccount()?.id,
                        )
                    }
                }
                PasswordLoginResult.NeedsTotp -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "请输入二步验证码",
                        errorMessage = null,
                        passwordLoginNeedsTotp = true,
                    )
                }
                PasswordLoginResult.CaptchaRequired -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = "当前实例要求验证码验证，App 暂不支持此密码登录流程，请使用浏览器授权登录",
                        passwordLoginNeedsTotp = false,
                    )
                }
                PasswordLoginResult.PasskeyRequired -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = "该账号需要 Passkey，请使用浏览器授权登录",
                        passwordLoginNeedsTotp = false,
                    )
                }
                is PasswordLoginResult.NetworkError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = loginNetworkErrorMessage(result.message),
                    )
                }
                is PasswordLoginResult.ServerError -> mutableState.update {
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
            val accounts = runCatching { normalizeAccounts(tokenStore.readAccountSessions()) }
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

            if (accounts.isEmpty()) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        accounts = emptyList(),
                        currentAccountId = null,
                    )
                }
                return@launch
            }

            val currentAccount = accounts.currentAccount() ?: accounts.maxByOrNull { it.lastUsed }!!
            when (val result = authenticator.verifyToken(currentAccount.token)) {
                is AuthResult.Success -> {
                    val updatedAccounts = upsertCurrentAccount(
                        accounts = accounts,
                        token = result.token,
                        user = result.user,
                    )
                    saveAccounts(updatedAccounts)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            statusMessage = null,
                            user = result.user,
                            sessionToken = result.token,
                            accounts = updatedAccounts,
                            currentAccountId = updatedAccounts.currentAccount()?.id,
                        )
                    }
                }
                AuthResult.InvalidToken -> {
                    val remainingAccounts = accounts.filterNot { it.id == currentAccount.id }
                    saveAccounts(normalizeAccounts(remainingAccounts))
                    onAccountRemoved(currentAccount.id)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            user = null,
                            sessionToken = null,
                            statusMessage = null,
                            errorMessage = "登录已失效，请重新登录",
                            accounts = normalizeAccounts(remainingAccounts),
                            currentAccountId = normalizeAccounts(remainingAccounts).currentAccount()?.id,
                        )
                    }
                }
                is AuthResult.NetworkError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = loginVerificationNetworkErrorMessage(result.message),
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
            val removedAccountId = state.value.currentAccountId
            val remainingAccounts = normalizeAccounts(
                state.value.accounts.filterNot { it.id == removedAccountId },
            )
            saveAccounts(remainingAccounts)
            if (removedAccountId != null) {
                onAccountRemoved(removedAccountId)
            }
            val current = remainingAccounts.currentAccount()
            mutableState.value = LoginUiState(
                accounts = remainingAccounts,
                currentAccountId = current?.id,
            )
            if (current != null) {
                switchAccount(current.id)
            }
        }
    }

    fun switchAccount(accountId: String) {
        val target = state.value.accounts.firstOrNull { it.id == accountId } ?: return
        if (target.id == state.value.currentAccountId && state.value.user != null) return

        val updatedAccounts = state.value.accounts.map {
            it.copy(
                current = it.id == target.id,
                lastUsed = if (it.id == target.id) nowProvider() else it.lastUsed,
            )
        }

        mutableState.update {
            it.copy(
                accounts = updatedAccounts,
                currentAccountId = target.id,
                user = target.user,
                sessionToken = target.token,
                isLoading = target.user == null,
                statusMessage = if (target.user == null) "正在登录..." else null,
                errorMessage = null,
                pendingSession = null,
                passwordLoginNeedsTotp = false,
            )
        }

        scope.launch {
            saveAccounts(updatedAccounts)
            if (target.user != null) return@launch

            when (val result = authenticator.verifyToken(target.token)) {
                is AuthResult.Success -> {
                    val verifiedAccounts = upsertCurrentAccount(
                        accounts = state.value.accounts,
                        token = result.token,
                        user = result.user,
                    )
                    saveAccounts(verifiedAccounts)
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = null,
                            user = result.user,
                            sessionToken = result.token,
                            accounts = verifiedAccounts,
                            currentAccountId = verifiedAccounts.currentAccount()?.id,
                        )
                    }
                }
                AuthResult.InvalidToken -> removeAccount(target.id, "登录已失效，请重新登录")
                is AuthResult.NetworkError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = loginVerificationNetworkErrorMessage(result.message),
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

    fun removeAccount(accountId: String) {
        scope.launch {
            removeAccount(accountId, null)
        }
    }

    private suspend fun removeAccount(accountId: String, errorMessage: String?) {
        val wasCurrent = state.value.currentAccountId == accountId
        val remainingAccounts = normalizeAccounts(state.value.accounts.filterNot { it.id == accountId })
        saveAccounts(remainingAccounts)
        onAccountRemoved(accountId)
        val current = remainingAccounts.currentAccount()
        mutableState.update {
            if (wasCurrent) {
                LoginUiState(
                    errorMessage = errorMessage,
                    accounts = remainingAccounts,
                    currentAccountId = current?.id,
                    user = current?.user,
                    sessionToken = current?.token,
                )
            } else {
                it.copy(
                    errorMessage = errorMessage ?: it.errorMessage,
                    accounts = remainingAccounts,
                )
            }
        }
        if (wasCurrent && current?.user == null && current != null) {
            switchAccount(current.id)
        }
    }

    private suspend fun removeCurrentAccount() {
        val currentId = state.value.currentAccountId
        if (currentId == null) {
            clearStoredToken()
        } else {
            removeAccount(currentId, null)
        }
    }

    private suspend fun saveAccounts(accounts: List<AccountSession>): String? {
        return runCatching { tokenStore.saveAccountSessions(accounts) }
            .fold(
                onSuccess = { null },
                onFailure = { "登录成功，但无法保存登录状态：${it.message ?: "未知错误"}" },
            )
    }

    private suspend fun clearStoredToken() {
        runCatching { tokenStore.clearAccountSessions() }
    }

    private fun upsertCurrentAccount(
        accounts: List<AccountSession>,
        token: String,
        user: AuthenticatedUser,
    ): List<AccountSession> {
        val id = accountSessionId(user)
        val now = nowProvider()
        val session = AccountSession(
            id = id,
            user = user,
            token = token,
            host = DefaultAccountHost,
            lastUsed = now,
            current = true,
        )
        val withoutSameAccount = accounts.filterNot { it.id == id || it.token == token }
        return normalizeAccounts(withoutSameAccount + session, preferredCurrentId = id)
    }

    private fun normalizeAccounts(
        accounts: List<AccountSession>,
        preferredCurrentId: String? = null,
    ): List<AccountSession> {
        val cleanAccounts = accounts
            .filter { it.token.isNotBlank() }
            .distinctBy { it.id }
        if (cleanAccounts.isEmpty()) return emptyList()

        val currentId = preferredCurrentId
            ?: cleanAccounts.firstOrNull { it.current }?.id
            ?: cleanAccounts.maxByOrNull { it.lastUsed }!!.id
        return cleanAccounts.map { it.copy(current = it.id == currentId) }
    }
}

private fun List<AccountSession>.currentAccount(): AccountSession? = firstOrNull { it.current }

internal fun loginNetworkErrorMessage(message: String): String {
    val clean = message.trim()
    return if (clean.contains("Unable to resolve host", ignoreCase = true)) {
        "无法连接服务器：设备网络或 DNS 解析异常，请检查模拟器联网状态后重试"
    } else {
        "无法连接服务器：${clean.ifBlank { "网络请求失败" }}"
    }
}

internal fun loginVerificationNetworkErrorMessage(message: String): String {
    val clean = message.trim()
    return if (clean.contains("Unable to resolve host", ignoreCase = true)) {
        "无法验证登录状态：设备网络或 DNS 解析异常，请检查模拟器联网状态后重试"
    } else {
        "无法验证登录状态：${clean.ifBlank { "网络请求失败" }}"
    }
}

private fun newMiAuthSessionId(): String {
    val chars = "0123456789abcdef"
    val raw = CharArray(32) { chars[kotlin.random.Random.nextInt(chars.length)] }.concatToString()
    return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-" +
        "${raw.substring(16, 20)}-${raw.substring(20, 32)}"
}
