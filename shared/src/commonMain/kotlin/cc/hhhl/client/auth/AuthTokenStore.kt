package cc.hhhl.client.auth

interface AuthTokenStore {
    suspend fun readToken(): String?

    suspend fun saveToken(token: String)

    suspend fun clearToken()

    suspend fun readAccountSessions(): List<AccountSession> {
        val token = readToken()?.trim().orEmpty()
        if (token.isEmpty()) return emptyList()
        return listOf(
            AccountSession(
                id = legacyAccountSessionId(token),
                user = null,
                token = token,
                current = true,
            ),
        )
    }

    suspend fun saveAccountSessions(sessions: List<AccountSession>) {
        val currentToken = sessions.firstOrNull { it.current }?.token
            ?: sessions.maxByOrNull { it.lastUsed }?.token
        if (currentToken == null) {
            clearToken()
        } else {
            saveToken(currentToken)
        }
    }

    suspend fun clearAccountSessions() {
        clearToken()
    }
}

object NoopAuthTokenStore : AuthTokenStore {
    override suspend fun readToken(): String? = null

    override suspend fun saveToken(token: String) = Unit

    override suspend fun clearToken() = Unit

    override suspend fun readAccountSessions(): List<AccountSession> = emptyList()

    override suspend fun saveAccountSessions(sessions: List<AccountSession>) = Unit

    override suspend fun clearAccountSessions() = Unit
}
