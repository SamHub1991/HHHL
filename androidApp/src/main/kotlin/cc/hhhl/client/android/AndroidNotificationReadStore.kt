package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.state.NotificationReadStore

class AndroidNotificationReadStore(context: Context) : NotificationReadStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun loadReadNotificationIds(accountId: String): Set<String> = synchronized(STORE_LOCK) {
        loadLocked(accountId)
    }

    override fun saveReadNotificationIds(accountId: String, notificationIds: Set<String>) {
        synchronized(STORE_LOCK) {
            val nextIds = (notificationIds + loadLocked(accountId))
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_READ_NOTIFICATION_IDS)
                .toSet()
            preferences.edit()
                .putString(keyFor(accountId), nextIds.joinToString(SEPARATOR))
                .commit()
        }
    }

    override fun clearAccount(accountId: String) {
        synchronized(STORE_LOCK) {
            preferences.edit()
                .remove(keyFor(accountId))
                .commit()
        }
    }

    private fun loadLocked(accountId: String): Set<String> {
        return preferences.getString(keyFor(accountId), null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    }

    private fun keyFor(accountId: String): String {
        return "$KEY_PREFIX${accountId.trim()}"
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_notification_reads"
        const val KEY_PREFIX = "read_ids_"
        const val SEPARATOR = "\n"
        const val MAX_READ_NOTIFICATION_IDS = 1_000
        val STORE_LOCK = Any()
    }
}
