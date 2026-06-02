package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.automation.AutomationExecutedEvent
import cc.hhhl.client.automation.AutomationSnapshot
import cc.hhhl.client.automation.AutomationStore
import cc.hhhl.client.automation.AutomationStoreCodec

class AndroidAutomationStore(context: Context) : AutomationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(accountId: String): AutomationSnapshot = synchronized(CLAIM_LOCK) {
        readLocked(accountId)
    }

    override fun write(accountId: String, snapshot: AutomationSnapshot) = synchronized(CLAIM_LOCK) {
        writeLocked(accountId, snapshot)
    }

    override fun clearAccount(accountId: String) {
        synchronized(CLAIM_LOCK) {
            preferences.edit()
                .remove(keyFor(accountId))
                .commit()
        }
    }

    override fun update(
        accountId: String,
        transform: (AutomationSnapshot) -> AutomationSnapshot,
    ): AutomationSnapshot = synchronized(CLAIM_LOCK) {
        val updated = transform(readLocked(accountId))
        writeLocked(accountId, updated)
        updated
    }

    override fun claimExecutedEvent(accountId: String, record: AutomationExecutedEvent): Boolean {
        val cleanAccountId = accountId.trim()
        val cleanRecord = record.sanitized()
        if (cleanRecord.key.isBlank()) return false
        return synchronized(CLAIM_LOCK) {
            val key = keyFor(cleanAccountId)
            val snapshot = AutomationStoreCodec.decode(preferences.getString(key, null))
            if (snapshot.executedEvents.any { event -> event.sanitized().key == cleanRecord.key }) {
                return@synchronized false
            }
            val updated = snapshot.copy(
                executedEvents = mergeExecutedEvents(
                    current = snapshot.executedEvents,
                    updates = listOf(cleanRecord),
                ),
            )
            preferences.edit()
                .putString(key, AutomationStoreCodec.encode(updated))
                .commit()
        }
    }

    private fun readLocked(accountId: String): AutomationSnapshot {
        return AutomationStoreCodec.decode(preferences.getString(keyFor(accountId), null))
    }

    private fun writeLocked(accountId: String, snapshot: AutomationSnapshot) {
        preferences.edit()
            .putString(keyFor(accountId), AutomationStoreCodec.encode(snapshot))
            .commit()
    }

    private fun keyFor(accountId: String): String {
        return "$KEY_PREFIX${accountId.trim().encodeKeyPart()}"
    }

    private fun String.encodeKeyPart(): String {
        return replace("%", "%25").replace(":", "%3A").replace("\n", "%0A")
    }

    private fun mergeExecutedEvents(
        current: List<AutomationExecutedEvent>,
        updates: List<AutomationExecutedEvent>,
    ): List<AutomationExecutedEvent> {
        val seenKeys = HashSet<String>()
        return (updates + current)
            .asSequence()
            .map { event -> event.sanitized() }
            .filter { event -> event.key.isNotBlank() }
            .sortedByDescending { event -> event.createdAtEpochMillis }
            .filter { event -> seenKeys.add(event.key) }
            .take(AutomationStoreCodec.MAX_EXECUTED_EVENTS)
            .toList()
    }

    private fun AutomationExecutedEvent.sanitized(): AutomationExecutedEvent {
        return copy(
            key = key.trim(),
            ruleId = ruleId.trim(),
            eventKey = eventKey.trim(),
            eventId = eventId.trim(),
        )
    }

    private companion object {
        val CLAIM_LOCK = Any()
        const val PREFERENCES_NAME = "hhhl_automation"
        const val KEY_PREFIX = "snapshot_"
    }
}
