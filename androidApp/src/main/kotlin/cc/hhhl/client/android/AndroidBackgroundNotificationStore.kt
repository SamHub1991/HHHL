package cc.hhhl.client.android

import android.content.Context

class AndroidBackgroundNotificationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun isBackgroundSyncEnabled(): Boolean {
        return preferences.getBoolean(KEY_BACKGROUND_SYNC_ENABLED, false)
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_BACKGROUND_SYNC_ENABLED, enabled)
            .apply()
    }

    fun isSpecialCareEnabled(): Boolean {
        return preferences.getBoolean(KEY_SPECIAL_CARE_ENABLED, true)
    }

    fun setSpecialCareEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SPECIAL_CARE_ENABLED, enabled)
            .apply()
    }

    fun loadSeenIds(): Set<String> {
        return preferences.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty()
    }

    fun saveSeenIds(ids: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_SEEN_IDS, ids.take(MAX_SEEN_IDS).toSet())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_background_notifications"
        const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
        const val KEY_SPECIAL_CARE_ENABLED = "special_care_enabled"
        const val KEY_SEEN_IDS = "seen_ids"
        const val MAX_SEEN_IDS = 200
    }
}
