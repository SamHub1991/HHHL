package cc.hhhl.client.state

import cc.hhhl.client.api.ComposeDraft
import kotlinx.serialization.json.Json

interface ComposeDraftStore {
    fun loadDraft(): ComposeDraft?

    fun saveDraft(draft: ComposeDraft)

    fun clearDraft()
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
}
