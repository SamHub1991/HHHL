package cc.hhhl.client.cache

data class ChatUnreadSnapshot(
    val roomCounts: Map<String, Int> = emptyMap(),
    val userCounts: Map<String, Int> = emptyMap(),
    val roomReadMarkers: Map<String, String> = emptyMap(),
    val userReadMarkers: Map<String, String> = emptyMap(),
    val pinnedRoomIds: Set<String> = emptySet(),
    val pinnedUserIds: Set<String> = emptySet(),
    val roomGroups: Map<String, String> = emptyMap(),
)

interface ChatUnreadStore {
    fun load(accountId: String): ChatUnreadSnapshot

    fun save(accountId: String, snapshot: ChatUnreadSnapshot)

    fun markRoomRead(accountId: String, roomId: String, marker: String) {
        val cleanAccountId = accountId.trim()
        val cleanRoomId = roomId.trim()
        if (cleanAccountId.isEmpty() || cleanRoomId.isEmpty()) return
        save(cleanAccountId, load(cleanAccountId).withRoomReadMarker(cleanRoomId, marker))
    }

    fun markUserRead(accountId: String, userId: String, marker: String) {
        val cleanAccountId = accountId.trim()
        val cleanUserId = userId.trim()
        if (cleanAccountId.isEmpty() || cleanUserId.isEmpty()) return
        save(cleanAccountId, load(cleanAccountId).withUserReadMarker(cleanUserId, marker))
    }

    fun clearRoom(accountId: String, roomId: String)

    fun clearUser(accountId: String, userId: String)

    fun clearAccount(accountId: String)
}

object NoopChatUnreadStore : ChatUnreadStore {
    override fun load(accountId: String): ChatUnreadSnapshot = ChatUnreadSnapshot()

    override fun save(accountId: String, snapshot: ChatUnreadSnapshot) = Unit

    override fun clearRoom(accountId: String, roomId: String) = Unit

    override fun clearUser(accountId: String, userId: String) = Unit

    override fun clearAccount(accountId: String) = Unit
}

fun ChatUnreadSnapshot.withRoomReadMarker(roomId: String, marker: String): ChatUnreadSnapshot {
    val cleanRoomId = roomId.trim()
    if (cleanRoomId.isEmpty()) return this
    val cleanMarker = marker.trim()
    return copy(
        roomCounts = roomCounts - cleanRoomId,
        roomReadMarkers = if (cleanMarker.isNotEmpty()) {
            roomReadMarkers + (cleanRoomId to roomReadMarkers[cleanRoomId].withChatReadMarkerAlias(cleanMarker))
        } else {
            roomReadMarkers
        },
    )
}

fun ChatUnreadSnapshot.withUserReadMarker(userId: String, marker: String): ChatUnreadSnapshot {
    val cleanUserId = userId.trim()
    if (cleanUserId.isEmpty()) return this
    val cleanMarker = marker.trim()
    return copy(
        userCounts = userCounts - cleanUserId,
        userReadMarkers = if (cleanMarker.isNotEmpty()) {
            userReadMarkers + (cleanUserId to userReadMarkers[cleanUserId].withChatReadMarkerAlias(cleanMarker))
        } else {
            userReadMarkers
        },
    )
}

fun String?.withChatReadMarkerAlias(marker: String): String {
    val cleanMarker = marker.trim()
    if (cleanMarker.isEmpty()) return this?.trim().orEmpty()
    val markers = (this.chatReadMarkerAliases() + cleanMarker)
        .distinct()
        .takeLast(MAX_CHAT_READ_MARKER_ALIASES)
    return markers.joinToString(CHAT_READ_MARKER_ALIAS_SEPARATOR)
}

fun String?.chatReadMarkerAliases(): List<String> {
    return orEmpty()
        .split(CHAT_READ_MARKER_ALIAS_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

internal const val CHAT_READ_MARKER_ALIAS_SEPARATOR = "\n"
private const val MAX_CHAT_READ_MARKER_ALIASES = 4
