package cc.hhhl.client.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
        holder.startBrowserLogin { openedUrls.add(it) }

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
        holder.completeBrowserLogin("session-123")
        advanceUntilIdle()

        assertEquals("token-123", tokenStore.token)
        assertEquals(listOf("token-123"), tokenStore.savedTokens)
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
        onCheck: () -> Unit = {},
        onVerify: (String) -> Unit = {},
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
                return verifyResult
            }
        }
    }

    private class FakeTokenStore(initialToken: String? = null) : AuthTokenStore {
        var token: String? = initialToken
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
    }
}
