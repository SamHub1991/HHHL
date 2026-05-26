package cc.hhhl.client.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpecialCareStateHolderTest {
    @Test
    fun startsWithNoSpecialCareUsers() {
        val holder = SpecialCareStateHolder()

        assertEquals(emptySet(), holder.state.value.userIds)
    }

    @Test
    fun restoresStoredSpecialCareUsers() {
        val store = InMemorySpecialCareStore(setOf(" user-1 ", "", "user-2"))
        val holder = SpecialCareStateHolder(store)

        holder.restoreStoredSpecialCare()

        assertEquals(setOf("user-1", "user-2"), holder.state.value.userIds)
        assertTrue(holder.isSpecialCare("user-1"))
    }

    @Test
    fun toggleAddsAndPersistsSpecialCareUser() {
        val store = InMemorySpecialCareStore()
        val holder = SpecialCareStateHolder(store)

        val enabled = holder.toggleSpecialCare(" user-1 ")

        assertTrue(enabled)
        assertEquals(setOf("user-1"), holder.state.value.userIds)
        assertEquals(setOf("user-1"), store.savedUserIds)
    }

    @Test
    fun toggleRemovesAndPersistsSpecialCareUser() {
        val store = InMemorySpecialCareStore(setOf("user-1", "user-2"))
        val holder = SpecialCareStateHolder(store)
        holder.restoreStoredSpecialCare()

        val enabled = holder.toggleSpecialCare("user-1")

        assertFalse(enabled)
        assertEquals(setOf("user-2"), holder.state.value.userIds)
        assertEquals(setOf("user-2"), store.savedUserIds)
    }

    @Test
    fun blankToggleDoesNotChangeStateOrStore() {
        val store = InMemorySpecialCareStore(setOf("user-1"))
        val holder = SpecialCareStateHolder(store)
        holder.restoreStoredSpecialCare()

        val enabled = holder.toggleSpecialCare("  ")

        assertFalse(enabled)
        assertEquals(setOf("user-1"), holder.state.value.userIds)
        assertEquals(null, store.savedUserIds)
    }

    @Test
    fun restoreFallsBackToEmptyWhenStoreFails() {
        val holder = SpecialCareStateHolder(ThrowingSpecialCareStore(loadFails = true))

        holder.restoreStoredSpecialCare()

        assertEquals(emptySet(), holder.state.value.userIds)
    }

    @Test
    fun toggleStillUpdatesStateWhenPersistingFails() {
        val holder = SpecialCareStateHolder(ThrowingSpecialCareStore(saveFails = true))

        val enabled = holder.toggleSpecialCare("user-1")

        assertTrue(enabled)
        assertEquals(setOf("user-1"), holder.state.value.userIds)
    }

    private class InMemorySpecialCareStore(
        private var storedUserIds: Set<String> = emptySet(),
    ) : SpecialCareStore {
        var savedUserIds: Set<String>? = null

        override fun loadSpecialCareUserIds(): Set<String> {
            return storedUserIds
        }

        override fun saveSpecialCareUserIds(userIds: Set<String>) {
            savedUserIds = userIds
            storedUserIds = userIds
        }
    }

    private class ThrowingSpecialCareStore(
        private val loadFails: Boolean = false,
        private val saveFails: Boolean = false,
    ) : SpecialCareStore {
        override fun loadSpecialCareUserIds(): Set<String> {
            if (loadFails) error("special care store load failed")
            return emptySet()
        }

        override fun saveSpecialCareUserIds(userIds: Set<String>) {
            if (saveFails) error("special care store save failed")
        }
    }
}
