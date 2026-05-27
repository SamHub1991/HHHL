package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.cache.NotificationCache
import cc.hhhl.client.cache.NotificationCacheCodec
import cc.hhhl.client.cache.NotificationCacheSnapshot

class AndroidNotificationCache(context: Context) : NotificationCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(accountId: String): NotificationCacheSnapshot {
        return NotificationCacheCodec.decode(preferences.getString(keyFor(accountId), null))
            .trimmed()
    }

    override fun write(accountId: String, snapshot: NotificationCacheSnapshot) {
        preferences.edit()
            .putString(keyFor(accountId), NotificationCacheCodec.encode(snapshot.trimmed()))
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

    private fun NotificationCacheSnapshot.trimmed(): NotificationCacheSnapshot {
        return copy(
            notifications = notifications.take(MAX_NOTIFICATIONS),
            specialCareNotifications = specialCareNotifications.take(MAX_SPECIAL_CARE_NOTIFICATIONS),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_notification_cache"
        const val KEY_PREFIX = "snapshot_"
        const val MAX_NOTIFICATIONS = 240
        const val MAX_SPECIAL_CARE_NOTIFICATIONS = 240
    }
}
