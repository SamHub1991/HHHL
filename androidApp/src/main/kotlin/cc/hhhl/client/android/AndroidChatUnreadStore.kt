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
            roomReadMarkers = loadMarkers(cleanAccountId, TYPE_ROOM_READ_MARKER),
            userReadMarkers = loadMarkers(cleanAccountId, TYPE_USER_READ_MARKER),
            pinnedRoomIds = loadStringSet(cleanAccountId, TYPE_PINNED_ROOMS),
            pinnedUserIds = loadStringSet(cleanAccountId, TYPE_PINNED_USERS),
            roomGroups = loadStrings(cleanAccountId, TYPE_ROOM_GROUP),
        )
    }

    override fun save(accountId: String, snapshot: ChatUnreadSnapshot) {
        val cleanAccountId = accountId.trim()
        if (cleanAccountId.isEmpty()) return
        preferences.edit()
            .replaceCounts(cleanAccountId, TYPE_ROOM, snapshot.roomCounts)
            .replaceCounts(cleanAccountId, TYPE_USER, snapshot.userCounts)
            .replaceMarkers(cleanAccountId, TYPE_ROOM_READ_MARKER, snapshot.roomReadMarkers)
            .replaceMarkers(cleanAccountId, TYPE_USER_READ_MARKER, snapshot.userReadMarkers)
            .replaceStringSet(cleanAccountId, TYPE_PINNED_ROOMS, snapshot.pinnedRoomIds)
            .replaceStringSet(cleanAccountId, TYPE_PINNED_USERS, snapshot.pinnedUserIds)
            .replaceStrings(cleanAccountId, TYPE_ROOM_GROUP, snapshot.roomGroups)
            .apply()
    }

    override fun clearRoom(accountId: String, roomId: String) {
        removeCount(accountId, TYPE_ROOM, roomId, TYPE_ROOM_MARKER, TYPE_ROOM_READ_MARKER)
    }

    override fun clearUser(accountId: String, userId: String) {
        removeCount(accountId, TYPE_USER, userId, TYPE_USER_MARKER, TYPE_USER_READ_MARKER)
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
            readMarkers = current.roomReadMarkers,
        )
        val userMerge = current.userCounts.mergeUnreadCounts(
            incoming = snapshot.userCounts,
            currentMarkers = currentUserMarkers,
            incomingMarkers = userLatestMarkers,
            readMarkers = current.userReadMarkers,
        )
        val merged = current.copy(
            roomCounts = roomMerge.counts,
            userCounts = userMerge.counts,
        )
        save(cleanAccountId, merged)
        saveMarkers(cleanAccountId, TYPE_ROOM_MARKER, roomMerge.markers)
        saveMarkers(cleanAccountId, TYPE_USER_MARKER, userMerge.markers)
        return merged
    }

    fun loadRoomMarkers(accountId: String): Map<String, String> {
        val cleanAccountId = accountId.trim()
        if (cleanAccountId.isEmpty()) return emptyMap()
        return loadMarkers(cleanAccountId, TYPE_ROOM_MARKER)
    }

    fun loadUserMarkers(accountId: String): Map<String, String> {
        val cleanAccountId = accountId.trim()
        if (cleanAccountId.isEmpty()) return emptyMap()
        return loadMarkers(cleanAccountId, TYPE_USER_MARKER)
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

    private fun loadStrings(accountId: String, type: String): Map<String, String> {
        val prefix = "$accountId|$type|"
        return preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix)) return@mapNotNull null
            val text = (value as? String)?.trim().orEmpty()
            val id = key.removePrefix(prefix).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (text.isEmpty()) null else id to text
        }.toMap()
    }

    private fun loadStringSet(accountId: String, type: String): Set<String> {
        return loadStrings(accountId, type).keys
    }

    private fun saveMarkers(accountId: String, type: String, markers: Map<String, String>) {
        preferences.edit()
            .replaceMarkers(accountId, type, markers)
            .apply()
    }

    private fun removeCount(accountId: String, type: String, id: String, vararg markerTypes: String) {
        val cleanAccountId = accountId.trim()
        val cleanId = id.trim()
        if (cleanAccountId.isEmpty() || cleanId.isEmpty()) return
        preferences.edit()
            .remove(key(cleanAccountId, type, cleanId))
            .apply {
                markerTypes.forEach { markerType ->
                    remove(key(cleanAccountId, markerType, cleanId))
                }
            }
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

    private fun android.content.SharedPreferences.Editor.replaceStrings(
        accountId: String,
        type: String,
        values: Map<String, String>,
    ): android.content.SharedPreferences.Editor {
        val prefix = "$accountId|$type|"
        preferences.all.keys
            .filter { it.startsWith(prefix) }
            .forEach(::remove)
        values.forEach { (id, value) ->
            val cleanId = id.trim()
            val cleanValue = value.trim()
            if (cleanId.isNotEmpty() && cleanValue.isNotEmpty()) {
                putString(key(accountId, type, cleanId), cleanValue)
            }
        }
        return this
    }

    private fun android.content.SharedPreferences.Editor.replaceStringSet(
        accountId: String,
        type: String,
        values: Set<String>,
    ): android.content.SharedPreferences.Editor {
        return replaceStrings(accountId, type, values.associateWith { "1" })
    }

    private fun key(accountId: String, type: String, id: String): String = "$accountId|$type|$id"

    private companion object {
        const val PREFERENCES_NAME = "hhhl_chat_unread"
        const val TYPE_ROOM = "room"
        const val TYPE_USER = "user"
        const val TYPE_ROOM_MARKER = "room_marker"
        const val TYPE_USER_MARKER = "user_marker"
        const val TYPE_ROOM_READ_MARKER = "room_read_marker"
        const val TYPE_USER_READ_MARKER = "user_read_marker"
        const val TYPE_PINNED_ROOMS = "pinned_room"
        const val TYPE_PINNED_USERS = "pinned_user"
        const val TYPE_ROOM_GROUP = "room_group"
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
    readMarkers: Map<String, String>,
): UnreadMergeResult {
    val mergedCounts = LinkedHashMap<String, Int>(size + incoming.size)
    val mergedMarkers = LinkedHashMap<String, String>(currentMarkers.size + incomingMarkers.size)
    fun markerAlreadyRead(key: String, marker: String): Boolean {
        val cleanMarker = marker.trim()
        return cleanMarker.isNotEmpty() && cleanMarker in readMarkers[key].readMarkerAliases()
    }
    forEach { (key, value) ->
        val marker = currentMarkers[key].orEmpty()
        if (value > 0 && !markerAlreadyRead(key, marker)) mergedCounts[key] = value
    }
    currentMarkers.forEach { (key, value) ->
        if (value.isNotBlank() && !markerAlreadyRead(key, value)) mergedMarkers[key] = value
    }
    incomingMarkers.forEach { (rawKey, rawMarker) ->
        val key = rawKey.trim()
        val marker = rawMarker.trim()
        if (key.isNotEmpty() && marker.isNotEmpty()) {
            if (markerAlreadyRead(key, marker)) {
                mergedCounts.remove(key)
                mergedMarkers.remove(key)
            } else {
                mergedMarkers[key] = marker
            }
        }
    }
    incoming.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        val incomingCount = rawValue.coerceAtLeast(0)
        if (key.isEmpty() || incomingCount <= 0) return@forEach
        val currentCount = mergedCounts[key] ?: 0
        val incomingMarker = incomingMarkers[key]?.trim().orEmpty()
        if (markerAlreadyRead(key, incomingMarker)) {
            mergedCounts.remove(key)
            mergedMarkers.remove(key)
            return@forEach
        }
        val currentMarker = currentMarkers[key].orEmpty()
        val markerChanged = incomingMarker.isStableUnreadMarker() &&
            currentMarker.isStableUnreadMarker() &&
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
        markers = mergedMarkers.filterValues { it.isNotBlank() },
    )
}

private fun String?.readMarkerAliases(): List<String> {
    return orEmpty()
        .split(READ_MARKER_ALIAS_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun String.isStableUnreadMarker(): Boolean {
    val clean = trim()
    return clean.isNotEmpty() && !unstableUnreadMarkerPattern.containsMatchIn(clean)
}

private const val READ_MARKER_ALIAS_SEPARATOR = "\n"
private val unstableUnreadMarkerPattern = Regex(
    pattern = "(刚刚|秒前|分钟前|小时前|天前|周前|月前|年前|just now|\\bago\\b)",
    options = setOf(RegexOption.IGNORE_CASE),
)
