package cc.hhhl.client.cache

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatUnreadStoreTest {
    @Test
    fun markRoomReadClearsCountAndAppendsMarkerAlias() {
        val store = MemoryChatUnreadStore(
            ChatUnreadSnapshot(
                roomCounts = mapOf("room-1" to 99),
                roomReadMarkers = mapOf("room-1" to "message-1"),
            ),
        )

        store.markRoomRead("account-1", "room-1", "message-2")

        val snapshot = store.load("account-1")
        assertEquals(emptyMap(), snapshot.roomCounts)
        assertEquals("message-1\nmessage-2", snapshot.roomReadMarkers["room-1"])
    }

    @Test
    fun markUserReadClearsCountAndAppendsMarkerAlias() {
        val store = MemoryChatUnreadStore(
            ChatUnreadSnapshot(
                userCounts = mapOf("user-1" to 3),
                userReadMarkers = mapOf("user-1" to "message-1"),
            ),
        )

        store.markUserRead("account-1", "user-1", "message-2")

        val snapshot = store.load("account-1")
        assertEquals(emptyMap(), snapshot.userCounts)
        assertEquals("message-1\nmessage-2", snapshot.userReadMarkers["user-1"])
    }

    private class MemoryChatUnreadStore(initial: ChatUnreadSnapshot) : ChatUnreadStore {
        private var snapshot = initial

        override fun load(accountId: String): ChatUnreadSnapshot = snapshot

        override fun save(accountId: String, snapshot: ChatUnreadSnapshot) {
            this.snapshot = snapshot
        }

        override fun clearRoom(accountId: String, roomId: String) = Unit

        override fun clearUser(accountId: String, userId: String) = Unit

        override fun clearAccount(accountId: String) = Unit
    }
}