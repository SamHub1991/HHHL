package cc.hhhl.client.cache

data class ChatUnreadSnapshot(
    val roomCounts: Map<String, Int> = emptyMap(),
    val userCounts: Map<String, Int> = emptyMap(),
    val pinnedRoomIds: Set<String> = emptySet(),
    val pinnedUserIds: Set<String> = emptySet(),
    val roomGroups: Map<String, String> = emptyMap(),
)

interface ChatUnreadStore {
    fun load(accountId: String): ChatUnreadSnapshot

    fun save(accountId: String, snapshot: ChatUnreadSnapshot)

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
