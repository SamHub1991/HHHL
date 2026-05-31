package cc.hhhl.client.automation

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AutomationSnapshot(
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<AutomationExecutionLog> = emptyList(),
    val debugRecords: List<AutomationRuleDebugRecord> = emptyList(),
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
                debugRecords = snapshot.debugRecords.take(MAX_DEBUG_RECORDS),
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
                debugRecords = envelope.debugRecords.take(MAX_DEBUG_RECORDS),
            )
        }.getOrDefault(AutomationSnapshot())
    }

    const val MAX_RULES = 80
    const val MAX_LOGS = 160
    const val MAX_DEBUG_RECORDS = 240
}

@Serializable
private data class CachedAutomationEnvelope(
    val version: Int = 2,
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<AutomationExecutionLog> = emptyList(),
    val debugRecords: List<AutomationRuleDebugRecord> = emptyList(),
)
