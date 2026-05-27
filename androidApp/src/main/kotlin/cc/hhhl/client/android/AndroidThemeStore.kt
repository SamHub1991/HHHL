package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.theme.HhhlCustomTheme
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

    override fun loadCustomTheme(): HhhlCustomTheme {
        return HhhlCustomTheme(
            accentColorHex = preferences.getString(KEY_CUSTOM_ACCENT_COLOR, null).orEmpty(),
            backgroundColorHex = preferences.getString(KEY_CUSTOM_BACKGROUND_COLOR, null).orEmpty(),
            chatBackgroundColorHex = preferences.getString(KEY_CUSTOM_CHAT_BACKGROUND_COLOR, null).orEmpty(),
            inputBackgroundColorHex = preferences.getString(KEY_CUSTOM_INPUT_BACKGROUND_COLOR, null).orEmpty(),
            cardBackgroundColorHex = preferences.getString(KEY_CUSTOM_CARD_BACKGROUND_COLOR, null).orEmpty(),
            globalBackgroundImageDataUri = preferences.getString(KEY_CUSTOM_GLOBAL_BACKGROUND_IMAGE, null).orEmpty(),
            chatBackgroundImageDataUri = preferences.getString(KEY_CUSTOM_CHAT_BACKGROUND_IMAGE, null).orEmpty(),
        )
    }

    override fun saveCustomTheme(customTheme: HhhlCustomTheme) {
        preferences.edit()
            .putString(KEY_CUSTOM_ACCENT_COLOR, customTheme.accentColorHex)
            .putString(KEY_CUSTOM_BACKGROUND_COLOR, customTheme.backgroundColorHex)
            .putString(KEY_CUSTOM_CHAT_BACKGROUND_COLOR, customTheme.chatBackgroundColorHex)
            .putString(KEY_CUSTOM_INPUT_BACKGROUND_COLOR, customTheme.inputBackgroundColorHex)
            .putString(KEY_CUSTOM_CARD_BACKGROUND_COLOR, customTheme.cardBackgroundColorHex)
            .putString(KEY_CUSTOM_GLOBAL_BACKGROUND_IMAGE, customTheme.globalBackgroundImageDataUri)
            .putString(KEY_CUSTOM_CHAT_BACKGROUND_IMAGE, customTheme.chatBackgroundImageDataUri)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_theme"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color"
        const val KEY_CUSTOM_BACKGROUND_COLOR = "custom_background_color"
        const val KEY_CUSTOM_CHAT_BACKGROUND_COLOR = "custom_chat_background_color"
        const val KEY_CUSTOM_INPUT_BACKGROUND_COLOR = "custom_input_background_color"
        const val KEY_CUSTOM_CARD_BACKGROUND_COLOR = "custom_card_background_color"
        const val KEY_CUSTOM_GLOBAL_BACKGROUND_IMAGE = "custom_global_background_image"
        const val KEY_CUSTOM_CHAT_BACKGROUND_IMAGE = "custom_chat_background_image"
    }
}
