package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.state.FavoriteMessage
import cc.hhhl.client.state.FavoriteMessageStore
import cc.hhhl.client.state.FavoriteMessageStoreCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidFavoriteMessageStore(context: Context) : FavoriteMessageStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutex = Mutex()

    override suspend fun read(accountId: String): List<FavoriteMessage> {
        return mutex.withLock {
            FavoriteMessageStoreCodec.decode(preferences.getString(accountKey(accountId), null))
        }
    }

    override suspend fun save(accountId: String, messages: List<FavoriteMessage>) {
        mutex.withLock {
            preferences.edit()
                .putString(accountKey(accountId), FavoriteMessageStoreCodec.encode(messages))
                .apply()
        }
    }

    override suspend fun clearAccount(accountId: String) {
        mutex.withLock {
            preferences.edit()
                .remove(accountKey(accountId))
                .apply()
        }
    }

    private fun accountKey(accountId: String): String {
        return "messages_${accountId.encodeStorageKeyPart()}"
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_favorite_messages"
    }
}

private fun String.encodeStorageKeyPart(): String {
    return replace("%", "%25").replace("|", "%7C")
}
