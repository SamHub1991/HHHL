package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.state.NotificationReadStore

class AndroidNotificationReadStore(context: Context) : NotificationReadStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadReadNotificationIds(accountId: String): Set<String> {
        return preferences.getString(keyFor(accountId), null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    }

    override fun saveReadNotificationIds(accountId: String, notificationIds: Set<String>) {
        preferences.edit()
            .putString(keyFor(accountId), notificationIds.joinToString(SEPARATOR))
            .apply()
    }

    override fun clearAccount(accountId: String) {
        preferences.edit()
            .remove(keyFor(accountId))
            .apply()
    }

    private fun keyFor(accountId: String): String {
        return "$KEY_PREFIX${accountId.trim()}"
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_notification_reads"
        const val KEY_PREFIX = "read_ids_"
        const val SEPARATOR = "\n"
    }
}
