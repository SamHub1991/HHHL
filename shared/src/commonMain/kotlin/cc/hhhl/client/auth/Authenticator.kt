package cc.hhhl.client.auth

interface Authenticator {
    fun buildAuthorizationUrl(session: String): String

    suspend fun checkSession(session: String): AuthResult

    suspend fun verifyToken(token: String): AuthResult
}
