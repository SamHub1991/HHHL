package cc.hhhl.client.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var authRequestId = 0
    private val accountSaveMutex = Mutex()

    fun startBrowserLogin(openUrl: (String) -> Unit) {
        if (state.value.isLoading) return

        val current = state.value
        val hadPendingSession = current.pendingSession != null
        val requestedSession = current.pendingSession ?: sessionIdProvider()
        val requestId = nextAuthRequestId()

        mutableState.update {
            it.copy(
                isLoading = true,
                statusMessage = "正在打开登录页...",
                errorMessage = null,
                passwordLoginNeedsTotp = false,
            )
        }

        scope.launch {
            when (val result = authenticator.prepareBrowserLogin(requestedSession)) {
                is BrowserLoginRequestResult.Success -> {
                    if (!isCurrentAuthRequest(requestId)) return@launch
                    val prepared = result.request
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            pendingSession = prepared.session,
                            statusMessage = "请在浏览器完成登录",
                            errorMessage = null,
                            passwordLoginNeedsTotp = false,
                        )
                    }
                    try {
                        openUrl(prepared.url)
                    } catch (error: Throwable) {
                        mutableState.update {
                            it.copy(
                                pendingSession = if (hadPendingSession) prepared.session else null,
                                statusMessage = null,
                                errorMessage = "无法打开登录页：${error.message ?: "未知错误"}",
                            )
                        }
                    }
                }
                is BrowserLoginRequestResult.NetworkError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = loginNetworkErrorMessage(result.message),
                    )
                }
                is BrowserLoginRequestResult.ServerError -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = result.message,
                    )
                }
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

        val requestId = nextAuthRequestId()
        mutableState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = "正在登录...",
            )
        }

        scope.launch {
            val result = authenticator.checkSession(session)
            if (!isCurrentAuthRequest(requestId)) return@launch
            when (result) {
                is AuthResult.Success -> {
                    val updatedAccounts = upsertCurrentAccount(
                        accounts = state.value.accounts,
                        token = result.token,
                        user = result.user,
                    )
                    val saveError = saveAccounts(updatedAccounts, requestId)
                    if (!isCurrentAuthRequest(requestId)) return@launch
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
                    removeCurrentAccount(requestId)
                    if (!isCurrentAuthRequest(requestId)) return@launch
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

        val requestId = nextAuthRequestId()
        mutableState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = "正在登录...",
                pendingSession = null,
            )
        }

        scope.launch {
            val result = authenticator.signInWithPassword(cleanUsername, password, cleanToken)
            if (!isCurrentAuthRequest(requestId)) return@launch
            when (result) {
                is PasswordLoginResult.Success -> {
                    val updatedAccounts = upsertCurrentAccount(
                        accounts = state.value.accounts,
                        token = result.token,
                        user = result.user,
                    )
                    val saveError = saveAccounts(updatedAccounts, requestId)
                    if (!isCurrentAuthRequest(requestId)) return@launch
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

        val requestId = nextAuthRequestId()
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
                    if (!isCurrentAuthRequest(requestId)) return@launch
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = "无法读取登录状态：${error.message ?: "未知错误"}",
                        )
                    }
                    return@launch
                }
            if (!isCurrentAuthRequest(requestId)) return@launch

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

            val currentAccount = accounts.currentAccount() ?: accounts.maxByOrNull { it.lastUsed }
            if (currentAccount == null) {
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
            val result = authenticator.verifyToken(currentAccount.token)
            if (!isCurrentAuthRequest(requestId)) return@launch
            when (result) {
                is AuthResult.Success -> {
                    val updatedAccounts = upsertCurrentAccount(
                        accounts = accounts,
                        token = result.token,
                        user = result.user,
                    )
                    saveAccounts(updatedAccounts, requestId)
                    if (!isCurrentAuthRequest(requestId)) return@launch
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
                    saveAccounts(normalizeAccounts(remainingAccounts), requestId)
                    if (!isCurrentAuthRequest(requestId)) return@launch
                    onAccountRemoved(currentAccount.id)
                    if (!isCurrentAuthRequest(requestId)) return@launch
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
        invalidateAuthRequests()
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
        val requestId = nextAuthRequestId()

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
            if (!isCurrentAuthRequest(requestId)) return@launch
            saveAccounts(updatedAccounts, requestId)
            if (!isCurrentAuthRequest(requestId)) return@launch
            if (target.user != null) return@launch

            val result = authenticator.verifyToken(target.token)
            if (!isCurrentAuthRequest(requestId)) return@launch
            when (result) {
                is AuthResult.Success -> {
                    val verifiedAccounts = upsertCurrentAccount(
                        accounts = state.value.accounts,
                        token = result.token,
                        user = result.user,
                    )
                    saveAccounts(verifiedAccounts, requestId)
                    if (!isCurrentAuthRequest(requestId)) return@launch
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
                AuthResult.InvalidToken -> removeAccount(target.id, "登录已失效，请重新登录", requestId)
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

    fun importSessionToken(token: String, userIdHint: String? = null) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return
        val hintedId = userIdHint?.trim().orEmpty()
        val accountId = if (hintedId.isNotEmpty()) {
            "${DefaultAccountHost}:$hintedId"
        } else {
            legacyAccountSessionId(cleanToken)
        }
        val importedAccount = AccountSession(
            id = accountId,
            user = null,
            token = cleanToken,
            host = DefaultAccountHost,
            lastUsed = nowProvider(),
            current = false,
        )
        val requestId = nextAuthRequestId()

        scope.launch {
            val updatedAccounts = normalizeAccounts(
                state.value.accounts.filterNot { it.id == accountId || it.token == cleanToken } + importedAccount,
                preferredCurrentId = state.value.currentAccountId,
            )
            if (!isCurrentAuthRequest(requestId)) return@launch
            val saveError = saveAccounts(updatedAccounts, requestId)
            if (!isCurrentAuthRequest(requestId)) return@launch
            mutableState.update {
                it.copy(
                    accounts = updatedAccounts,
                    errorMessage = saveError,
                )
            }
            switchAccount(accountId)
        }
    }

    fun removeAccount(accountId: String) {
        invalidateAuthRequests()
        scope.launch {
            removeAccount(accountId, null)
        }
    }

    private suspend fun removeAccount(
        accountId: String,
        errorMessage: String?,
        requestId: Int? = null,
    ) {
        val wasCurrent = state.value.currentAccountId == accountId
        val remainingAccounts = normalizeAccounts(state.value.accounts.filterNot { it.id == accountId })
        saveAccounts(remainingAccounts)
        if (requestId != null && !isCurrentAuthRequest(requestId)) return
        onAccountRemoved(accountId)
        if (requestId != null && !isCurrentAuthRequest(requestId)) return
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

    private suspend fun removeCurrentAccount(requestId: Int? = null) {
        val currentId = state.value.currentAccountId
        if (currentId == null) {
            clearStoredToken(requestId)
            if (requestId != null && !isCurrentAuthRequest(requestId)) return
        } else {
            removeAccount(currentId, null, requestId)
        }
    }

    private suspend fun saveAccounts(
        accounts: List<AccountSession>,
        requestId: Int? = null,
    ): String? {
        return runCatching {
            accountSaveMutex.withLock {
                if (requestId != null && !isCurrentAuthRequest(requestId)) return null
                tokenStore.saveAccountSessions(accounts)
            }
        }
            .fold(
                onSuccess = { null },
                onFailure = { "登录成功，但无法保存登录状态：${it.message ?: "未知错误"}" },
            )
    }

    private suspend fun clearStoredToken(requestId: Int? = null) {
        runCatching {
            accountSaveMutex.withLock {
                if (requestId != null && !isCurrentAuthRequest(requestId)) return
                tokenStore.clearAccountSessions()
            }
        }
    }

    private fun nextAuthRequestId(): Int {
        authRequestId += 1
        return authRequestId
    }

    private fun invalidateAuthRequests() {
        authRequestId += 1
    }

    private fun isCurrentAuthRequest(requestId: Int): Boolean {
        return requestId == authRequestId
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
            ?: cleanAccounts.maxByOrNull { it.lastUsed }?.id
            ?: return emptyList()
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
