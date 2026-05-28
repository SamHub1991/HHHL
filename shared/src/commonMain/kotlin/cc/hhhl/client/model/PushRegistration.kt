package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class PushRegistrationInput(
    val endpoint: String,
    val auth: String,
    val publicKey: String,
    val sendReadMessage: Boolean = false,
)

@Immutable
data class PushRegistration(
    val userId: String,
    val endpoint: String,
    val sendReadMessage: Boolean,
    val key: String? = null,
    val state: PushRegistrationState? = null,
)

enum class PushRegistrationState {
    Subscribed,
    AlreadySubscribed,
    Unknown,
}
