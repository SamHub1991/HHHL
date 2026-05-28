package cc.hhhl.client.automation

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AutomationSnapshot(
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<AutomationExecutionLog> = emptyList(),
)

interface AutomationStore {
    fun read(accountId: String): AutomationSnapshot

    fun write(accountId: String, snapshot: AutomationSnapshot)

    fun clearAccount(accountId: String)
}

object NoopAutomationStore : AutomationStore {
    override fun read(accountId: String): AutomationSnapshot = AutomationSnapshot()

    override fun write(accountId: String, snapshot: AutomationSnapshot) = Unit

    override fun clearAccount(accountId: String) = Unit
}

object AutomationStoreCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(snapshot: AutomationSnapshot): String {
        return json.encodeToString(
            CachedAutomationEnvelope(
                rules = snapshot.rules.take(MAX_RULES),
                logs = snapshot.logs.take(MAX_LOGS),
            ),
        )
    }

    fun decode(payload: String?): AutomationSnapshot {
        if (payload.isNullOrBlank()) return AutomationSnapshot()
        return runCatching {
            val envelope = json.decodeFromString<CachedAutomationEnvelope>(payload)
            AutomationSnapshot(
                rules = envelope.rules.take(MAX_RULES),
                logs = envelope.logs.take(MAX_LOGS),
            )
        }.getOrDefault(AutomationSnapshot())
    }

    const val MAX_RULES = 80
    const val MAX_LOGS = 160
}

@Serializable
private data class CachedAutomationEnvelope(
    val version: Int = 1,
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<AutomationExecutionLog> = emptyList(),
)
