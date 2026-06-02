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

    override fun read(accountId: String): NotificationCacheSnapshot = synchronized(STORE_LOCK) {
        readLocked(accountId)
    }

    override fun write(accountId: String, snapshot: NotificationCacheSnapshot) {
        synchronized(STORE_LOCK) {
            writeLocked(accountId, snapshot)
        }
    }

    override fun update(
        accountId: String,
        transform: (NotificationCacheSnapshot) -> NotificationCacheSnapshot,
    ): NotificationCacheSnapshot = synchronized(STORE_LOCK) {
        val updated = transform(readLocked(accountId)).trimmed()
        writeLocked(accountId, updated)
        updated
    }

    override fun clearAccount(accountId: String) {
        synchronized(STORE_LOCK) {
            preferences.edit()
                .remove(preferenceEntryFor(accountId))
                .commit()
        }
    }

    private fun readLocked(accountId: String): NotificationCacheSnapshot {
        return NotificationCacheCodec.decode(preferences.getString(preferenceEntryFor(accountId), null))
            .trimmed()
    }

    private fun writeLocked(accountId: String, snapshot: NotificationCacheSnapshot) {
        preferences.edit()
            .putString(preferenceEntryFor(accountId), NotificationCacheCodec.encode(snapshot.trimmed()))
            .commit()
    }

    private fun preferenceEntryFor(accountId: String): String {
        return "$KEY_PREFIX${accountId.trim()}"
    }

    private fun NotificationCacheSnapshot.trimmed(): NotificationCacheSnapshot {
        return copy(
            notifications = notifications.take(MAX_NOTIFICATIONS),
            chatAttentionNotifications = chatAttentionNotifications.take(MAX_NOTIFICATIONS),
            specialCareNotifications = specialCareNotifications.take(MAX_SPECIAL_CARE_NOTIFICATIONS),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_notification_cache"
        const val KEY_PREFIX = "snapshot_"
        const val MAX_NOTIFICATIONS = 240
        const val MAX_SPECIAL_CARE_NOTIFICATIONS = 240
        val STORE_LOCK = Any()
    }
}
