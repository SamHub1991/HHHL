package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.state.ComposeDraftCodec
import cc.hhhl.client.state.ComposeDraftStore

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

    private companion object {
        const val PREFERENCES_NAME = "hhhl_compose_draft"
        const val KEY_DRAFT = "draft"
    }
}
