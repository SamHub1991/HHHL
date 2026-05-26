package cc.hhhl.client.auth

interface AuthTokenStore {
    suspend fun readToken(): String?

    suspend fun saveToken(token: String)

    suspend fun clearToken()
}

object NoopAuthTokenStore : AuthTokenStore {
    override suspend fun readToken(): String? = null

    override suspend fun saveToken(token: String) = Unit

    override suspend fun clearToken() = Unit
}
