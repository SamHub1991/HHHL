package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.state.SpecialCareStore

class AndroidSpecialCareStore(context: Context) : SpecialCareStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadSpecialCareUserIds(): Set<String> {
        return preferences.loadUserIds(KEY_USER_IDS)
    }

    override fun saveSpecialCareUserIds(userIds: Set<String>) {
        preferences.edit()
            .putString(KEY_USER_IDS, userIds.cleanUserIds().joinToString(SEPARATOR))
            .apply()
    }

    override fun loadSpecialCareUserIds(accountId: String): Set<String> {
        val accountKey = keyFor(accountId)
        return if (preferences.contains(accountKey)) {
            preferences.loadUserIds(accountKey)
        } else {
            val legacyUserIds = preferences.loadUserIds(KEY_USER_IDS)
            if (legacyUserIds.isNotEmpty()) {
                preferences.edit()
                    .putString(accountKey, legacyUserIds.joinToString(SEPARATOR))
                    .remove(KEY_USER_IDS)
                    .apply()
            }
            legacyUserIds
        }
    }

    override fun saveSpecialCareUserIds(accountId: String, userIds: Set<String>) {
        preferences.edit()
            .putString(keyFor(accountId), userIds.cleanUserIds().joinToString(SEPARATOR))
            .apply()
    }

    override fun clearAccount(accountId: String) {
        preferences.edit()
            .remove(keyFor(accountId))
            .apply()
    }

    private fun keyFor(accountId: String): String {
        return "$KEY_ACCOUNT_USER_IDS${accountId.trim().encodeKeyPart()}"
    }

    private fun android.content.SharedPreferences.loadUserIds(key: String): Set<String> {
        return getString(key, null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    }

    private fun Set<String>.cleanUserIds(): Set<String> {
        return mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
    }

    private fun String.encodeKeyPart(): String {
        return replace("%", "%25").replace(":", "%3A").replace("\n", "%0A")
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_special_care"
        const val KEY_USER_IDS = "user_ids"
        const val KEY_ACCOUNT_USER_IDS = "user_ids_"
        const val SEPARATOR = "\n"
    }
}
