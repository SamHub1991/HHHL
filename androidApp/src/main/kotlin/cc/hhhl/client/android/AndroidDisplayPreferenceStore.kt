package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.display.DisplayPreferenceStore

class AndroidDisplayPreferenceStore(context: Context) : DisplayPreferenceStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadTimelineDensity(): String? {
        return preferences.getString(KEY_TIMELINE_DENSITY, null)
    }

    override fun saveTimelineDensity(density: String) {
        preferences.edit()
            .putString(KEY_TIMELINE_DENSITY, density)
            .apply()
    }

    override fun loadDefaultNoteVisibility(): String? {
        return preferences.getString(KEY_DEFAULT_NOTE_VISIBILITY, null)
    }

    override fun saveDefaultNoteVisibility(visibility: String) {
        preferences.edit()
            .putString(KEY_DEFAULT_NOTE_VISIBILITY, visibility)
            .apply()
    }

    override fun loadNotificationBadgeMode(): String? {
        return preferences.getString(KEY_NOTIFICATION_BADGE_MODE, null)
    }

    override fun saveNotificationBadgeMode(mode: String) {
        preferences.edit()
            .putString(KEY_NOTIFICATION_BADGE_MODE, mode)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_display_preferences"
        const val KEY_TIMELINE_DENSITY = "timeline_density"
        const val KEY_DEFAULT_NOTE_VISIBILITY = "default_note_visibility"
        const val KEY_NOTIFICATION_BADGE_MODE = "notification_badge_mode"
    }
}
