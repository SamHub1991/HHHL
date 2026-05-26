package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.state.SpecialCareStore

class AndroidSpecialCareStore(context: Context) : SpecialCareStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadSpecialCareUserIds(): Set<String> {
        return preferences.getString(KEY_USER_IDS, null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    }

    override fun saveSpecialCareUserIds(userIds: Set<String>) {
        preferences.edit()
            .putString(KEY_USER_IDS, userIds.joinToString(SEPARATOR))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_special_care"
        const val KEY_USER_IDS = "user_ids"
        const val SEPARATOR = "\n"
    }
}
