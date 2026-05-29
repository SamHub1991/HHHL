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

@Serializable
private data class CachedAiEnvelope(
    val version: Int = 1,
    val settings: AiSettings = AiSettings(),
    val tasks: List<AiTask> = emptyList(),
    val usage: AiUsageWindow = AiUsageWindow(),
)
