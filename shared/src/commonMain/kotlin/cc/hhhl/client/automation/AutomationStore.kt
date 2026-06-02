package cc.hhhl.client.automation

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AutomationSnapshot(
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<AutomationExecutionLog> = emptyList(),
    val debugRecords: List<AutomationRuleDebugRecord> = emptyList(),
    val executedEvents: List<AutomationExecutedEvent> = emptyList(),
)

@Serializable
data class AutomationExecutedEvent(
    val key: String,
    val ruleId: String,
    val eventKey: String,
    val eventId: String,
    val createdAtEpochMillis: Long,
)

interface AutomationStore {
    fun read(accountId: String): AutomationSnapshot

    fun write(accountId: String, snapshot: AutomationSnapshot)

    fun clearAccount(accountId: String)

    fun update(accountId: String, transform: (AutomationSnapshot) -> AutomationSnapshot): AutomationSnapshot {
        val cleanAccountId = accountId.trim()
        val updated = transform(read(cleanAccountId))
        write(cleanAccountId, updated)
        return updated
    }

    fun claimExecutedEvent(accountId: String, record: AutomationExecutedEvent): Boolean {
        val cleanRecord = record.cleaned()
        if (cleanRecord.key.isBlank()) return false
        val cleanAccountId = accountId.trim()
        var claimed = false
        update(cleanAccountId) { snapshot ->
            if (snapshot.executedEvents.any { event -> event.cleaned().key == cleanRecord.key }) {
                snapshot
            } else {
                claimed = true
                snapshot.copy(
                    executedEvents = mergeStoredAutomationExecutedEvents(
                        current = snapshot.executedEvents,
                        updates = listOf(cleanRecord),
                    ),
                )
            }
        }
        return claimed
    }

    fun releaseExecutedEvent(accountId: String, key: String) {
        val cleanKey = key.trim()
        if (cleanKey.isBlank()) return
        val cleanAccountId = accountId.trim()
        update(cleanAccountId) { snapshot ->
            val remaining = snapshot.executedEvents.filter { event -> event.cleaned().key != cleanKey }
            if (remaining.size == snapshot.executedEvents.size) {
                snapshot
            } else {
                snapshot.copy(executedEvents = remaining)
            }
        }
    }
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
                executedEvents = snapshot.executedEvents.take(MAX_EXECUTED_EVENTS),
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
                executedEvents = envelope.executedEvents.take(MAX_EXECUTED_EVENTS),
            )
        }.getOrDefault(AutomationSnapshot())
    }

    const val MAX_RULES = 80
    const val MAX_LOGS = 160
    const val MAX_DEBUG_RECORDS = 240
    const val MAX_EXECUTED_EVENTS = 2_000
}

@Serializable
private data class CachedAutomationEnvelope(
    val version: Int = 2,
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<AutomationExecutionLog> = emptyList(),
    val debugRecords: List<AutomationRuleDebugRecord> = emptyList(),
    val executedEvents: List<AutomationExecutedEvent> = emptyList(),
)

internal fun mergeStoredAutomationExecutedEvents(
    current: List<AutomationExecutedEvent>,
    updates: List<AutomationExecutedEvent>,
): List<AutomationExecutedEvent> {
    val seenKeys = HashSet<String>()
    return (updates + current)
        .asSequence()
        .map(AutomationExecutedEvent::cleaned)
        .filter { event -> event.key.isNotBlank() }
        .sortedByDescending { event -> event.createdAtEpochMillis }
        .filter { event -> seenKeys.add(event.key) }
        .take(AutomationStoreCodec.MAX_EXECUTED_EVENTS)
        .toList()
}

internal fun AutomationExecutedEvent.cleaned(): AutomationExecutedEvent {
    return copy(
        key = key.trim(),
        ruleId = ruleId.trim(),
        eventKey = eventKey.trim(),
        eventId = eventId.trim(),
    )
}
