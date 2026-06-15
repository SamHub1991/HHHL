package cc.hhhl.client.auth

data class BrowserLoginRequest(
    val session: String,
    val url: String,
)

sealed interface BrowserLoginRequestResult {
    data class Success(val request: BrowserLoginRequest) : BrowserLoginRequestResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : BrowserLoginRequestResult

    data class NetworkError(
        val message: String,
    ) : BrowserLoginRequestResult
}

interface Authenticator {
    fun buildAuthorizationUrl(session: String): String

    suspend fun prepareBrowserLogin(session: String): BrowserLoginRequestResult {
        return BrowserLoginRequestResult.Success(
            BrowserLoginRequest(
                session = session,
                url = buildAuthorizationUrl(session),
            ),
        )
    }

    suspend fun checkSession(session: String): AuthResult

    suspend fun verifyToken(token: String): AuthResult

    suspend fun signInWithPassword(
        username: String,
        password: String,
        token: String? = null,
    ): PasswordLoginResult
}
