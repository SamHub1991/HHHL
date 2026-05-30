package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.update.ReleaseNotesStore

class AndroidReleaseNotesStore(context: Context) : ReleaseNotesStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadLastShownVersion(): String? {
        return preferences.getString(KEY_LAST_SHOWN_VERSION, null)?.takeIf { it.isNotBlank() }
    }

    override fun saveLastShownVersion(versionName: String) {
        preferences.edit()
            .putString(KEY_LAST_SHOWN_VERSION, versionName.trim())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_release_notes"
        const val KEY_LAST_SHOWN_VERSION = "last_shown_version"
    }
}
