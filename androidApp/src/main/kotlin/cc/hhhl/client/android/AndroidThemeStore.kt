package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.theme.ThemeStore

class AndroidThemeStore(context: Context) : ThemeStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadThemePresetName(): String? {
        return preferences.getString(KEY_THEME_PRESET, null)
    }

    override fun saveThemePresetName(presetName: String) {
        preferences.edit()
            .putString(KEY_THEME_PRESET, presetName)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_theme"
        const val KEY_THEME_PRESET = "theme_preset"
    }
}
