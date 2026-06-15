package cc.hhhl.client.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LoginStateHolderTest {
    @Test
    fun startBrowserLoginGeneratesSessionAndOpensAuthorizationUrl() = runTest {
        val openedUrls = mutableListOf<String>()
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(),
            sessionIdProvider = { "session-123" },
            scope = TestScope(testScheduler),
        )

        holder.startBrowserLogin { openedUrls.add(it) }
        runCurrent()

        assertEquals(listOf("https://dc.hhhl.cc/miauth/session-123"), openedUrls)
        assertEquals("session-123", holder.state.value.pendingSession)
        assertEquals("请在浏览器完成登录", holder.state.value.statusMessage)
        assertFalse(holder.state.value.isLoading)
    }

    @Test
    fun startBrowserLoginReusesPendingSessionInsteadOfRotatingIt() = runTest {
        val openedUrls = mutableListOf<String>()
        val sessions = ArrayDeque(listOf("session-123", "session-456"))
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(),
            sessionIdProvider = { sessions.removeFirst() },
            scope = TestScope(testScheduler),
        )

        holder.startBrowserLogin { openedUrls.add(it) }
        runCurrent()
        holder.startBrowserLogin { openedUrls.add(it) }
        runCurrent()

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/miauth/session-123",
                "https://dc.hhhl.cc/miauth/session-123",
            ),
            openedUrls,
        )
        assertEquals("session-123", holder.state.value.pendingSession)
        assertEquals(1, sessions.size)
    }

    @Test
    fun matchingCallbackSessionChecksMiAuthAndStoresUser() = runTest {
        val user = AuthenticatedUser(
            id = "u1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
        )
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                checkResult = AuthResult.Success("token-123", user),
            ),
            sessionIdProvider = { "session-123" },
            scope = TestScope(testScheduler),
        )

        holder.startBrowserLogin {}
        advanceUntilIdle()
        holder.completeBrowserLogin("session-123")
        assertTrue(holder.state.value.isLoading)

        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertNull(holder.state.value.errorMessage)
        assertEquals(user, holder.state.value.user)
        assertEquals("token-123", holder.state.value.sessionToken)
    }

    @Test
    fun matchingCallbackSessionSavesToken() = runTest {
        val tokenStore = FakeTokenStore()
        val user = AuthenticatedUser(
            id = "u1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
        )
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                checkResult = AuthResult.Success("token-123", user),
            ),
            sessionIdProvider = { "session-123" },
            tokenStore = tokenStore,
            scope = TestScope(testScheduler),
        )

        holder.startBrowserLogin {}
        advanceUntilIdle()
        holder.completeBrowserLogin("session-123")
        advanceUntilIdle()

        assertEquals("token-123", tokenStore.token)
        assertEquals(listOf("token-123"), tokenStore.savedTokens)
    }

    @Test
    fun passwordLoginStoresTokenAndUser() = runTest {
        val tokenStore = FakeTokenStore()
        val user = AuthenticatedUser(
            id = "u1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
        )
        val submitted = mutableListOf<Triple<String, String, String?>>()
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                passwordLoginResult = PasswordLoginResult.Success("token-123", user),
                onPasswordLogin = { username, password, token -> submitted.add(Triple(username, password, token)) },
            ),
            tokenStore = tokenStore,
            scope = TestScope(testScheduler),
        )

        holder.signInWithPassword("@alice", "secret")
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertEquals(listOf(Triple<String, String, String?>("alice", "secret", null)), submitted)
        assertFalse(holder.state.value.isLoading)
        assertEquals(user, holder.state.value.user)
        assertEquals("token-123", holder.state.value.sessionToken)
        assertEquals(listOf("token-123"), tokenStore.savedTokens)
    }

    @Test
    fun passwordLoginCanRequestTotpThenContinue() = runTest {
        val user = AuthenticatedUser("u1", "alice", "Alice", null)
        var attempts = 0
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                onPasswordLogin = { _, _, _ -> attempts += 1 },
                passwordLoginResultProvider = {
                    if (attempts == 1) {
                        PasswordLoginResult.NeedsTotp
                    } else {
                        PasswordLoginResult.Success("token-123", user)
                    }
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.signInWithPassword("alice", "secret")
        advanceUntilIdle()

        assertTrue(holder.state.value.passwordLoginNeedsTotp)
        assertEquals("请输入二步验证码", holder.state.value.statusMessage)

        holder.signInWithPassword("alice", "secret", "123456")
        advanceUntilIdle()

        assertFalse(holder.state.value.passwordLoginNeedsTotp)
        assertEquals(user, holder.state.value.user)
        assertEquals("token-123", holder.state.value.sessionToken)
    }

    @Test
    fun passwordLoginRequiresTotpCodeAfterServerRequestsIt() = runTest {
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(passwordLoginResult = PasswordLoginResult.NeedsTotp),
            scope = TestScope(testScheduler),
        )

        holder.signInWithPassword("alice", "secret")
        advanceUntilIdle()
        holder.signInWithPassword("alice", "secret")
        advanceUntilIdle()

        assertEquals("请输入二步验证码或备用码", holder.state.value.errorMessage)
    }

    @Test
    fun passwordLoginCaptchaRequirementShowsBrowserAuthorizationMessage() = runTest {
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(passwordLoginResult = PasswordLoginResult.CaptchaRequired),
            scope = TestScope(testScheduler),
        )

        holder.signInWithPassword("alice", "secret")
        advanceUntilIdle()

        assertEquals(
            "当前实例要求验证码验证，App 暂不支持此密码登录流程，请使用浏览器授权登录",
            holder.state.value.errorMessage,
        )
        assertFalse(holder.state.value.passwordLoginNeedsTotp)
    }

    @Test
    fun browserLoginDnsFailureShowsReadableNetworkHint() = runTest {
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                checkResult = AuthResult.NetworkError("Unable to resolve host \"dc.hhhl.cc\": No address associated with hostname"),
            ),
            sessionIdProvider = { "session-123" },
            scope = TestScope(testScheduler),
        )

        holder.startBrowserLogin {}
        advanceUntilIdle()
        holder.completeBrowserLogin("session-123")
        advanceUntilIdle()

        assertEquals(
            "无法连接服务器：设备网络或 DNS 解析异常，请检查模拟器联网状态后重试",
            holder.state.value.errorMessage,
        )
    }

    @Test
    fun restoreStoredTokenVerifiesTokenAndStoresUser() = runTest {
        val user = AuthenticatedUser(
            id = "u1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
        )
        val verifiedTokens = mutableListOf<String>()
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                verifyResult = AuthResult.Success("token-123", user),
                onVerify = { verifiedTokens.add(it) },
            ),
            tokenStore = FakeTokenStore(initialToken = "token-123"),
            scope = TestScope(testScheduler),
        )

        holder.restoreStoredToken()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertEquals(listOf("token-123"), verifiedTokens)
        assertFalse(holder.state.value.isLoading)
        assertNull(holder.state.value.errorMessage)
        assertEquals(user, holder.state.value.user)
        assertEquals("token-123", holder.state.value.sessionToken)
    }

    @Test
    fun restoreStoredTokenMigratesLegacyTokenToAccountSession() = runTest {
        val user = AuthenticatedUser(
            id = "u1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
        )
        val tokenStore = FakeTokenStore(initialToken = "legacy-token")
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                verifyResult = AuthResult.Success("legacy-token", user),
            ),
            tokenStore = tokenStore,
            nowProvider = { 42 },
            scope = TestScope(testScheduler),
        )

        holder.restoreStoredToken()
        advanceUntilIdle()

        assertEquals(user, holder.state.value.user)
        assertEquals("legacy-token", holder.state.value.sessionToken)
        assertEquals(listOf(accountSessionId(user)), holder.state.value.accounts.map { it.id })
        assertTrue(holder.state.value.accounts.single().current)
        assertEquals(42, holder.state.value.accounts.single().lastUsed)
        assertEquals(holder.state.value.accounts, tokenStore.accountSessions)
    }

    @Test
    fun restoreStoredTokenClearsInvalidStoredToken() = runTest {
        val tokenStore = FakeTokenStore(initialToken = "expired-token")
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(verifyResult = AuthResult.InvalidToken),
            tokenStore = tokenStore,
            scope = TestScope(testScheduler),
        )

        holder.restoreStoredToken()
        advanceUntilIdle()

        assertNull(tokenStore.token)
        assertEquals(1, tokenStore.clearCount)
        assertFalse(holder.state.value.isLoading)
        assertNull(holder.state.value.user)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun logoutClearsTokenAndUser() = runTest {
        val user = AuthenticatedUser(
            id = "u1",
            username = "alice",
            displayName = "Alice",
            avatarUrl = null,
        )
        val tokenStore = FakeTokenStore()
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                checkResult = AuthResult.Success("token-123", user),
            ),
            sessionIdProvider = { "session-123" },
            tokenStore = tokenStore,
            scope = TestScope(testScheduler),
        )
        holder.startBrowserLogin {}
        advanceUntilIdle()
        holder.completeBrowserLogin("session-123")
        advanceUntilIdle()

        holder.logout()
        advanceUntilIdle()

        assertNull(tokenStore.token)
        assertEquals(1, tokenStore.clearCount)
        assertNull(holder.state.value.user)
        assertNull(holder.state.value.sessionToken)
    }

    @Test
    fun switchAccountUpdatesCurrentAccountAndSessionToken() = runTest {
        val alice = AuthenticatedUser("u1", "alice", "Alice", null)
        val bob = AuthenticatedUser("u2", "bob", "Bob", null)
        val tokenStore = FakeTokenStore(
            initialAccounts = listOf(
                AccountSession(accountSessionId(alice), alice, "token-a", lastUsed = 1, current = true),
                AccountSession(accountSessionId(bob), bob, "token-b", lastUsed = 2, current = false),
            ),
        )
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                verifyResult = AuthResult.Success("token-a", alice),
            ),
            tokenStore = tokenStore,
            nowProvider = { 99 },
            scope = TestScope(testScheduler),
        )
        holder.restoreStoredToken()
        advanceUntilIdle()

        holder.switchAccount(accountSessionId(bob))
        advanceUntilIdle()

        assertEquals(bob, holder.state.value.user)
        assertEquals("token-b", holder.state.value.sessionToken)
        assertEquals(accountSessionId(bob), holder.state.value.currentAccountId)
        assertTrue(holder.state.value.accounts.first { it.id == accountSessionId(bob) }.current)
        assertEquals(99, holder.state.value.accounts.first { it.id == accountSessionId(bob) }.lastUsed)
        assertEquals(holder.state.value.accounts, tokenStore.accountSessions)
    }

    @Test
    fun staleSwitchAccountVerificationDoesNotOverwriteNewerAccount() = runTest {
        val alice = AuthenticatedUser("u1", "alice", "Alice", null)
        val bob = AuthenticatedUser("u2", "bob", "Bob", null)
        val charlie = AuthenticatedUser("u3", "charlie", "Charlie", null)
        val pendingBobVerification = CompletableDeferred<AuthResult>()
        val tokenStore = FakeTokenStore(
            initialAccounts = listOf(
                AccountSession(accountSessionId(alice), alice, "token-a", lastUsed = 3, current = true),
                AccountSession(accountSessionId(bob), null, "token-b", lastUsed = 2, current = false),
                AccountSession(accountSessionId(charlie), charlie, "token-c", lastUsed = 1, current = false),
            ),
        )
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                verifyResultProvider = { token ->
                    when (token) {
                        "token-a" -> AuthResult.Success("token-a", alice)
                        "token-b" -> pendingBobVerification.await()
                        "token-c" -> AuthResult.Success("token-c", charlie)
                        else -> AuthResult.InvalidToken
                    }
                },
            ),
            tokenStore = tokenStore,
            nowProvider = { 99 },
            scope = TestScope(testScheduler),
        )
        holder.restoreStoredToken()
        advanceUntilIdle()

        holder.switchAccount(accountSessionId(bob))
        runCurrent()
        assertEquals(accountSessionId(bob), holder.state.value.currentAccountId)
        assertTrue(holder.state.value.isLoading)

        holder.switchAccount(accountSessionId(charlie))
        advanceUntilIdle()
        assertEquals(charlie, holder.state.value.user)
        assertEquals("token-c", holder.state.value.sessionToken)

        pendingBobVerification.complete(AuthResult.Success("token-b", bob))
        advanceUntilIdle()

        assertEquals(charlie, holder.state.value.user)
        assertEquals("token-c", holder.state.value.sessionToken)
        assertEquals(accountSessionId(charlie), holder.state.value.currentAccountId)
        assertEquals("token-c", tokenStore.token)
        assertEquals(accountSessionId(charlie), tokenStore.accountSessions?.firstOrNull { it.current }?.id)
    }

    @Test
    fun staleRestoreResultDoesNotLogUserBackInAfterLogout() = runTest {
        val alice = AuthenticatedUser("u1", "alice", "Alice", null)
        val pendingVerification = CompletableDeferred<AuthResult>()
        val tokenStore = FakeTokenStore(initialToken = "token-a")
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                verifyResultProvider = { pendingVerification.await() },
            ),
            tokenStore = tokenStore,
            scope = TestScope(testScheduler),
        )

        holder.restoreStoredToken()
        runCurrent()
        assertTrue(holder.state.value.isLoading)

        holder.logout()
        advanceUntilIdle()
        pendingVerification.complete(AuthResult.Success("token-a", alice))
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertNull(holder.state.value.user)
        assertNull(holder.state.value.sessionToken)
        assertNull(tokenStore.token)
        assertEquals(emptyList(), tokenStore.accountSessions)
    }

    @Test
    fun removingCurrentAccountPromotesRemainingAccount() = runTest {
        val alice = AuthenticatedUser("u1", "alice", "Alice", null)
        val bob = AuthenticatedUser("u2", "bob", "Bob", null)
        val tokenStore = FakeTokenStore(
            initialAccounts = listOf(
                AccountSession(accountSessionId(alice), alice, "token-a", lastUsed = 2, current = true),
                AccountSession(accountSessionId(bob), bob, "token-b", lastUsed = 1, current = false),
            ),
        )
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                verifyResult = AuthResult.Success("token-a", alice),
            ),
            tokenStore = tokenStore,
            scope = TestScope(testScheduler),
        )
        holder.restoreStoredToken()
        advanceUntilIdle()

        holder.removeAccount(accountSessionId(alice))
        advanceUntilIdle()

        assertEquals(bob, holder.state.value.user)
        assertEquals("token-b", holder.state.value.sessionToken)
        assertEquals(listOf(accountSessionId(bob)), holder.state.value.accounts.map { it.id })
        assertTrue(holder.state.value.accounts.single().current)
        assertEquals(holder.state.value.accounts, tokenStore.accountSessions)
    }

    @Test
    fun mismatchedCallbackSessionDoesNotCallApi() = runTest {
        var checks = 0
        val holder = LoginStateHolder(
            authenticator = fakeAuthenticator(
                onCheck = { checks += 1 },
            ),
            sessionIdProvider = { "session-123" },
            scope = TestScope(testScheduler),
        )

        holder.startBrowserLogin {}
        advanceUntilIdle()
        holder.completeBrowserLogin("other-session")
        advanceUntilIdle()

        assertEquals(0, checks)
        assertNull(holder.state.value.user)
        assertEquals("session-123", holder.state.value.pendingSession)
        assertEquals("登录会话不匹配，请重新登录", holder.state.value.errorMessage)
    }

    private fun fakeAuthenticator(
        checkResult: AuthResult = AuthResult.InvalidToken,
        verifyResult: AuthResult = AuthResult.InvalidToken,
        verifyResultProvider: (suspend (String) -> AuthResult)? = null,
        passwordLoginResult: PasswordLoginResult = PasswordLoginResult.ServerError(
            statusCode = 400,
            message = "登录失败",
        ),
        passwordLoginResultProvider: (() -> PasswordLoginResult)? = null,
        onCheck: () -> Unit = {},
        onVerify: (String) -> Unit = {},
        onPasswordLogin: (String, String, String?) -> Unit = { _, _, _ -> },
    ): Authenticator {
        return object : Authenticator {
            override fun buildAuthorizationUrl(session: String): String {
                return "https://dc.hhhl.cc/miauth/$session"
            }

            override suspend fun checkSession(session: String): AuthResult {
                onCheck()
                return checkResult
            }

            override suspend fun verifyToken(token: String): AuthResult {
                onVerify(token)
                return verifyResultProvider?.invoke(token) ?: verifyResult
            }

            override suspend fun signInWithPassword(
                username: String,
                password: String,
                token: String?,
            ): PasswordLoginResult {
                onPasswordLogin(username, password, token)
                return passwordLoginResultProvider?.invoke() ?: passwordLoginResult
            }
        }
    }

    private class FakeTokenStore(
        initialToken: String? = null,
        initialAccounts: List<AccountSession>? = null,
    ) : AuthTokenStore {
        var token: String? = initialToken
        var accountSessions: List<AccountSession>? = initialAccounts
        val savedTokens = mutableListOf<String>()
        var clearCount = 0

        override suspend fun readToken(): String? = token

        override suspend fun saveToken(token: String) {
            this.token = token
            savedTokens.add(token)
        }

        override suspend fun clearToken() {
            token = null
            clearCount += 1
        }

        override suspend fun readAccountSessions(): List<AccountSession> {
            return accountSessions ?: super.readAccountSessions()
        }

        override suspend fun saveAccountSessions(sessions: List<AccountSession>) {
            if (sessions.isEmpty()) {
                clearAccountSessions()
                return
            }
            accountSessions = sessions
            token = sessions.firstOrNull { it.current }?.token
            savedTokens.addAll(sessions.filter { it.current }.map { it.token })
        }

        override suspend fun clearAccountSessions() {
            accountSessions = emptyList()
            clearToken()
        }
    }
}
