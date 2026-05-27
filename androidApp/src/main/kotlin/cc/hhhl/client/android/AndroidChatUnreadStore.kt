package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.cache.ChatUnreadSnapshot
import cc.hhhl.client.cache.ChatUnreadStore

class AndroidChatUnreadStore(context: Context) : ChatUnreadStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(accountId: String): ChatUnreadSnapshot {
        val cleanAccountId = accountId.trim()
        if (cleanAccountId.isEmpty()) return ChatUnreadSnapshot()
        return ChatUnreadSnapshot(
            roomCounts = loadCounts(cleanAccountId, TYPE_ROOM),
            userCounts = loadCounts(cleanAccountId, TYPE_USER),
        )
    }

    override fun save(accountId: String, snapshot: ChatUnreadSnapshot) {
        val cleanAccountId = accountId.trim()
        if (cleanAccountId.isEmpty()) return
        preferences.edit()
            .replaceCounts(cleanAccountId, TYPE_ROOM, snapshot.roomCounts)
            .replaceCounts(cleanAccountId, TYPE_USER, snapshot.userCounts)
            .apply()
    }

    override fun clearRoom(accountId: String, roomId: String) {
        removeCount(accountId, TYPE_ROOM, roomId, TYPE_ROOM_MARKER)
    }

    override fun clearUser(accountId: String, userId: String) {
        removeCount(accountId, TYPE_USER, userId, TYPE_USER_MARKER)
    }

    override fun clearAccount(accountId: String) {
        val cleanAccountId = accountId.trim()
        if (cleanAccountId.isEmpty()) return
        val prefix = "$cleanAccountId|"
        val keys = preferences.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        preferences.edit().apply {
            keys.forEach(::remove)
        }.apply()
    }

    fun merge(
        accountId: String,
        snapshot: ChatUnreadSnapshot,
        roomLatestMarkers: Map<String, String> = emptyMap(),
        userLatestMarkers: Map<String, String> = emptyMap(),
    ): ChatUnreadSnapshot {
        val cleanAccountId = accountId.trim()
        if (cleanAccountId.isEmpty()) return ChatUnreadSnapshot()
        val current = load(cleanAccountId)
        val currentRoomMarkers = loadMarkers(cleanAccountId, TYPE_ROOM_MARKER)
        val currentUserMarkers = loadMarkers(cleanAccountId, TYPE_USER_MARKER)
        val roomMerge = current.roomCounts.mergeUnreadCounts(
            incoming = snapshot.roomCounts,
            currentMarkers = currentRoomMarkers,
            incomingMarkers = roomLatestMarkers,
        )
        val userMerge = current.userCounts.mergeUnreadCounts(
            incoming = snapshot.userCounts,
            currentMarkers = currentUserMarkers,
            incomingMarkers = userLatestMarkers,
        )
        val merged = ChatUnreadSnapshot(
            roomCounts = roomMerge.counts,
            userCounts = userMerge.counts,
        )
        save(cleanAccountId, merged)
        saveMarkers(cleanAccountId, TYPE_ROOM_MARKER, roomMerge.markers)
        saveMarkers(cleanAccountId, TYPE_USER_MARKER, userMerge.markers)
        return merged
    }

    private fun loadCounts(accountId: String, type: String): Map<String, Int> {
        val prefix = "$accountId|$type|"
        return preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix)) return@mapNotNull null
            val count = (value as? Int)?.coerceAtLeast(0) ?: return@mapNotNull null
            val id = key.removePrefix(prefix).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (count == 0) null else id to count
        }.toMap()
    }

    private fun loadMarkers(accountId: String, type: String): Map<String, String> {
        val prefix = "$accountId|$type|"
        return preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix)) return@mapNotNull null
            val marker = (value as? String)?.trim().orEmpty()
            val id = key.removePrefix(prefix).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (marker.isEmpty()) null else id to marker
        }.toMap()
    }

    private fun saveMarkers(accountId: String, type: String, markers: Map<String, String>) {
        preferences.edit()
            .replaceMarkers(accountId, type, markers)
            .apply()
    }

    private fun removeCount(accountId: String, type: String, id: String, markerType: String) {
        val cleanAccountId = accountId.trim()
        val cleanId = id.trim()
        if (cleanAccountId.isEmpty() || cleanId.isEmpty()) return
        preferences.edit()
            .remove(key(cleanAccountId, type, cleanId))
            .remove(key(cleanAccountId, markerType, cleanId))
            .apply()
    }

    private fun android.content.SharedPreferences.Editor.replaceCounts(
        accountId: String,
        type: String,
        counts: Map<String, Int>,
    ): android.content.SharedPreferences.Editor {
        val prefix = "$accountId|$type|"
        preferences.all.keys
            .filter { it.startsWith(prefix) }
            .forEach(::remove)
        counts.forEach { (id, count) ->
            val cleanId = id.trim()
            val cleanCount = count.coerceAtLeast(0)
            if (cleanId.isNotEmpty() && cleanCount > 0) {
                putInt(key(accountId, type, cleanId), cleanCount)
            }
        }
        return this
    }

    private fun android.content.SharedPreferences.Editor.replaceMarkers(
        accountId: String,
        type: String,
        markers: Map<String, String>,
    ): android.content.SharedPreferences.Editor {
        val prefix = "$accountId|$type|"
        preferences.all.keys
            .filter { it.startsWith(prefix) }
            .forEach(::remove)
        markers.forEach { (id, marker) ->
            val cleanId = id.trim()
            val cleanMarker = marker.trim()
            if (cleanId.isNotEmpty() && cleanMarker.isNotEmpty()) {
                putString(key(accountId, type, cleanId), cleanMarker)
            }
        }
        return this
    }

    private fun key(accountId: String, type: String, id: String): String = "$accountId|$type|$id"

    private companion object {
        const val PREFERENCES_NAME = "hhhl_chat_unread"
        const val TYPE_ROOM = "room"
        const val TYPE_USER = "user"
        const val TYPE_ROOM_MARKER = "room_marker"
        const val TYPE_USER_MARKER = "user_marker"
    }
}

private data class UnreadMergeResult(
    val counts: Map<String, Int>,
    val markers: Map<String, String>,
)

private fun Map<String, Int>.mergeUnreadCounts(
    incoming: Map<String, Int>,
    currentMarkers: Map<String, String>,
    incomingMarkers: Map<String, String>,
): UnreadMergeResult {
    val mergedCounts = LinkedHashMap<String, Int>(size + incoming.size)
    val mergedMarkers = LinkedHashMap<String, String>(currentMarkers.size + incomingMarkers.size)
    forEach { (key, value) -> if (value > 0) mergedCounts[key] = value }
    currentMarkers.forEach { (key, value) -> if (key in mergedCounts && value.isNotBlank()) mergedMarkers[key] = value }
    incoming.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        val incomingCount = rawValue.coerceAtLeast(0)
        if (key.isEmpty() || incomingCount <= 0) return@forEach
        val currentCount = mergedCounts[key] ?: 0
        val incomingMarker = incomingMarkers[key]?.trim().orEmpty()
        val currentMarker = mergedMarkers[key].orEmpty()
        val markerChanged = incomingMarker.isNotEmpty() &&
            currentMarker.isNotEmpty() &&
            incomingMarker != currentMarker
        mergedCounts[key] = if (markerChanged && incomingCount <= currentCount) {
            currentCount + 1
        } else {
            maxOf(currentCount, incomingCount)
        }
        if (incomingMarker.isNotEmpty()) {
            mergedMarkers[key] = incomingMarker
        }
    }
    return UnreadMergeResult(
        counts = mergedCounts.filterValues { it > 0 },
        markers = mergedMarkers.filterKeys { it in mergedCounts },
    )
}
