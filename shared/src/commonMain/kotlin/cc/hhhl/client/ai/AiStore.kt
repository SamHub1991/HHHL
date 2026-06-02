package cc.hhhl.client.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AiSnapshot(
    val settings: AiSettings = AiSettings(),
    val tasks: List<AiTask> = emptyList(),
    val usage: AiUsageWindow = AiUsageWindow(),
)

interface AiStore {
    fun read(accountId: String): AiSnapshot

    fun write(accountId: String, snapshot: AiSnapshot)

    fun clearAccount(accountId: String)

    fun update(accountId: String, transform: (AiSnapshot) -> AiSnapshot): AiSnapshot {
        val cleanAccountId = accountId.trim()
        val updated = transform(read(cleanAccountId))
        write(cleanAccountId, updated)
        return updated
    }
}

object NoopAiStore : AiStore {
    override fun read(accountId: String): AiSnapshot = AiSnapshot()

    override fun write(accountId: String, snapshot: AiSnapshot) = Unit

    override fun clearAccount(accountId: String) = Unit
}

object AiStoreCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(snapshot: AiSnapshot): String {
        return json.encodeToString(
            CachedAiEnvelope(
                settings = snapshot.settings,
                tasks = snapshot.tasks.take(AI_MAX_TASKS),
                usage = snapshot.usage,
            ),
        )
    }

    fun decode(payload: String?): AiSnapshot {
        if (payload.isNullOrBlank()) return AiSnapshot()
        return runCatching {
            val envelope = json.decodeFromString<CachedAiEnvelope>(payload)
            AiSnapshot(
                settings = envelope.settings,
                tasks = envelope.tasks.take(AI_MAX_TASKS),
                usage = envelope.usage,
            )
        }.getOrDefault(AiSnapshot())
    }
}

fun mergeStoredAiTasks(
    current: List<AiTask>,
    updates: List<AiTask>,
): List<AiTask> {
    val merged = LinkedHashMap<String, AiTask>()
    (updates + current)
        .asSequence()
        .filter { task -> task.id.isNotBlank() }
        .forEach { task ->
            val existing = merged[task.id]
            if (existing == null || task.updatedAtEpochMillis > existing.updatedAtEpochMillis) {
                merged[task.id] = task
            }
        }
    return merged.values
        .sortedByDescending { task -> task.updatedAtEpochMillis }
        .take(AI_MAX_TASKS)
}

fun mergeStoredAiUsage(
    current: AiUsageWindow,
    update: AiUsageWindow,
): AiUsageWindow {
    val currentNormalized = current.normalizedAiUsage()
    val updateNormalized = update.normalizedAiUsage()
    return if (currentNormalized.dayKey == updateNormalized.dayKey) {
        updateNormalized.copy(
            requestCount = maxOf(currentNormalized.requestCount, updateNormalized.requestCount),
        )
    } else {
        updateNormalized
    }
}

@Serializable
private data class CachedAiEnvelope(
    val version: Int = 1,
    val settings: AiSettings = AiSettings(),
    val tasks: List<AiTask> = emptyList(),
    val usage: AiUsageWindow = AiUsageWindow(),
)
