package cc.hhhl.client.auth

data class AuthenticatedUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
)

sealed interface AuthResult {
    data class Success(
        val token: String,
        val user: AuthenticatedUser,
    ) : AuthResult

    data object InvalidToken : AuthResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AuthResult

    data class NetworkError(
        val message: String,
    ) : AuthResult
}
