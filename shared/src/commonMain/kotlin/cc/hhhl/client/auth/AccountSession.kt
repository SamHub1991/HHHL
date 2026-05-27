package cc.hhhl.client.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val DefaultAccountHost = "dc.hhhl.cc"

@Serializable
data class AccountSession(
    val id: String,
    val user: AuthenticatedUser?,
    val token: String,
    val host: String = DefaultAccountHost,
    val lastUsed: Long = 0,
    val current: Boolean = false,
)

fun legacyAccountSessionId(
    token: String,
    host: String = DefaultAccountHost,
): String {
    return "legacy-$host-${token.hashCode().toUInt().toString(16)}"
}

fun accountSessionId(
    user: AuthenticatedUser,
    host: String = DefaultAccountHost,
): String {
    return "$host:${user.id}"
}

fun decodeAccountSessions(payload: String): List<AccountSession> {
    return accountSessionJson.decodeFromString<AccountSessionEnvelope>(payload).accounts
}

fun encodeAccountSessions(sessions: List<AccountSession>): String {
    return accountSessionJson.encodeToString(AccountSessionEnvelope(accounts = sessions))
}

@Serializable
private data class AccountSessionEnvelope(
    val version: Int = 1,
    val accounts: List<AccountSession>,
)

private val accountSessionJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
