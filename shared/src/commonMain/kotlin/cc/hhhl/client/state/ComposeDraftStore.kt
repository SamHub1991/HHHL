package cc.hhhl.client.state

import cc.hhhl.client.api.ComposeDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ComposeFailedSend(
    val id: String,
    val draft: ComposeDraft,
    val message: String,
    val createdAtEpochMillis: Long,
    val isRetrying: Boolean = false,
)

interface ComposeDraftStore {
    fun loadDraft(): ComposeDraft?

    fun saveDraft(draft: ComposeDraft)

    fun clearDraft()

    fun loadDraft(key: String): ComposeDraft? = if (key.isNewDraftKey()) loadDraft() else null

    fun saveDraft(key: String, draft: ComposeDraft) = saveDraft(draft)

    fun clearDraft(key: String) = clearDraft()

    fun loadFailedSendQueue(key: String): List<ComposeFailedSend> = emptyList()

    fun saveFailedSendQueue(key: String, queue: List<ComposeFailedSend>) = Unit
}

object NoopComposeDraftStore : ComposeDraftStore {
    override fun loadDraft(): ComposeDraft? = null

    override fun saveDraft(draft: ComposeDraft) = Unit

    override fun clearDraft() = Unit
}

object ComposeDraftCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(draft: ComposeDraft): String {
        return json.encodeToString(draft)
    }

    fun decode(payload: String?): ComposeDraft? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString<ComposeDraft>(payload)
        }.getOrNull()
    }

    fun encodeFailedSendQueue(queue: List<ComposeFailedSend>): String {
        return json.encodeToString(queue.map { it.copy(isRetrying = false) })
    }

    fun decodeFailedSendQueue(payload: String?): List<ComposeFailedSend> {
        if (payload.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ComposeFailedSend>>(payload)
                .map { it.copy(isRetrying = false) }
                .filter { it.id.isNotBlank() && it.draft.hasQueuedDraftContent() }
        }.getOrDefault(emptyList())
    }
}

fun String.isNewDraftKey(): Boolean {
    return substringAfterLast('|', missingDelimiterValue = this) == "new"
}

private fun ComposeDraft.hasQueuedDraftContent(): Boolean {
    return text.isNotBlank() || fileIds.isNotEmpty() || poll != null || !cw.isNullOrBlank()
}
