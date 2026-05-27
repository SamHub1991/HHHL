package cc.hhhl.client.auth

import kotlinx.serialization.Serializable

@Serializable
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

sealed interface PasswordLoginResult {
    data class Success(
        val token: String,
        val user: AuthenticatedUser,
    ) : PasswordLoginResult

    data object NeedsTotp : PasswordLoginResult

    data object CaptchaRequired : PasswordLoginResult

    data object PasskeyRequired : PasswordLoginResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : PasswordLoginResult

    data class NetworkError(
        val message: String,
    ) : PasswordLoginResult
}
