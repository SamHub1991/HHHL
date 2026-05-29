package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.state.ComposeDraftCodec
import cc.hhhl.client.state.ComposeFailedSend
import cc.hhhl.client.state.ComposeDraftStore
import cc.hhhl.client.state.isNewDraftKey

class AndroidComposeDraftStore(context: Context) : ComposeDraftStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadDraft(): ComposeDraft? {
        return ComposeDraftCodec.decode(preferences.getString(KEY_DRAFT, null))
    }

    override fun saveDraft(draft: ComposeDraft) {
        preferences.edit()
            .putString(KEY_DRAFT, ComposeDraftCodec.encode(draft))
            .apply()
    }

    override fun clearDraft() {
        preferences.edit()
            .remove(KEY_DRAFT)
            .apply()
    }

    override fun loadDraft(key: String): ComposeDraft? {
        val scopedDraft = ComposeDraftCodec.decode(preferences.getString(draftKey(key), null))
        return scopedDraft ?: loadDraft().takeIf { key.isNewDraftKey() }
    }

    override fun saveDraft(key: String, draft: ComposeDraft) {
        preferences.edit()
            .putString(draftKey(key), ComposeDraftCodec.encode(draft))
            .apply()
    }

    override fun clearDraft(key: String) {
        preferences.edit()
            .remove(draftKey(key))
            .apply()
    }

    override fun loadFailedSendQueue(key: String): List<ComposeFailedSend> {
        return ComposeDraftCodec.decodeFailedSendQueue(preferences.getString(failedQueueKey(key), null))
    }

    override fun saveFailedSendQueue(key: String, queue: List<ComposeFailedSend>) {
        preferences.edit()
            .putString(failedQueueKey(key), ComposeDraftCodec.encodeFailedSendQueue(queue))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_compose_draft"
        const val KEY_DRAFT = "draft"

        fun draftKey(key: String): String {
            val cleanKey = key.trim()
                .map { char -> if (char.isLetterOrDigit() || char == '_' || char == '-') char else '_' }
                .joinToString("")
                .ifBlank { "default" }
            return "draft_$cleanKey"
        }

        fun failedQueueKey(key: String): String {
            val cleanKey = key.trim()
                .map { char -> if (char.isLetterOrDigit() || char == '_' || char == '-') char else '_' }
                .joinToString("")
                .ifBlank { "default" }
            return "failed_queue_$cleanKey"
        }
    }
}
