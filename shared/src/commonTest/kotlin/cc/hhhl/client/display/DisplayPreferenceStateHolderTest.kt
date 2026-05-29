package cc.hhhl.client.display

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayPreferenceStateHolderTest {
    @Test
    fun startsWithComfortableTimelineDensity() {
        val holder = DisplayPreferenceStateHolder()

        assertEquals(TimelineDensity.Comfortable, holder.state.value.timelineDensity)
    }

    @Test
    fun restoresStoredCompactTimelineDensity() {
        val store = InMemoryDisplayPreferenceStore(TimelineDensity.Compact.storageKey)
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.restoreStoredPreferences()

        assertEquals(TimelineDensity.Compact, holder.state.value.timelineDensity)
    }

    @Test
    fun invalidStoredTimelineDensityFallsBackToComfortable() {
        val store = InMemoryDisplayPreferenceStore("dense")
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.restoreStoredPreferences()

        assertEquals(TimelineDensity.Comfortable, holder.state.value.timelineDensity)
    }

    @Test
    fun selectingTimelineDensityPersistsStorageKey() {
        val store = InMemoryDisplayPreferenceStore()
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.selectTimelineDensity(TimelineDensity.Compact)

        assertEquals(TimelineDensity.Compact.storageKey, store.savedTimelineDensity)
        assertEquals(TimelineDensity.Compact, holder.state.value.timelineDensity)
    }

    @Test
    fun restoresStoredUltraCompactTimelineDensity() {
        val store = InMemoryDisplayPreferenceStore(TimelineDensity.UltraCompact.storageKey)
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.restoreStoredPreferences()

        assertEquals(TimelineDensity.UltraCompact, holder.state.value.timelineDensity)
    }

    @Test
    fun restoresStoredDefaultNoteVisibility() {
        val store = InMemoryDisplayPreferenceStore(
            storedDefaultNoteVisibility = DefaultNoteVisibility.Followers.storageKey,
        )
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.restoreStoredPreferences()

        assertEquals(DefaultNoteVisibility.Followers, holder.state.value.defaultNoteVisibility)
    }

    @Test
    fun selectingDefaultNoteVisibilityPersistsStorageKey() {
        val store = InMemoryDisplayPreferenceStore()
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.selectDefaultNoteVisibility(DefaultNoteVisibility.Home)

        assertEquals(DefaultNoteVisibility.Home.storageKey, store.savedDefaultNoteVisibility)
        assertEquals(DefaultNoteVisibility.Home, holder.state.value.defaultNoteVisibility)
    }

    @Test
    fun restoresStoredNotificationBadgeMode() {
        val store = InMemoryDisplayPreferenceStore(
            storedNotificationBadgeMode = NotificationBadgeMode.Hide.storageKey,
        )
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.restoreStoredPreferences()

        assertEquals(NotificationBadgeMode.Hide, holder.state.value.notificationBadgeMode)
    }

    @Test
    fun selectingNotificationBadgeModePersistsStorageKey() {
        val store = InMemoryDisplayPreferenceStore()
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.selectNotificationBadgeMode(NotificationBadgeMode.Hide)

        assertEquals(NotificationBadgeMode.Hide.storageKey, store.savedNotificationBadgeMode)
        assertEquals(NotificationBadgeMode.Hide, holder.state.value.notificationBadgeMode)
    }

    @Test
    fun restoresStoredListGesturesPreference() {
        val store = InMemoryDisplayPreferenceStore(storedListGesturesEnabled = false)
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.restoreStoredPreferences()

        assertEquals(false, holder.state.value.listGesturesEnabled)
    }

    @Test
    fun changingListGesturesPreferencePersistsValue() {
        val store = InMemoryDisplayPreferenceStore()
        val holder = DisplayPreferenceStateHolder(store = store)

        holder.setListGesturesEnabled(false)

        assertEquals(false, store.savedListGesturesEnabled)
        assertEquals(false, holder.state.value.listGesturesEnabled)
    }

    private class InMemoryDisplayPreferenceStore(
        private var storedTimelineDensity: String? = null,
        private var storedDefaultNoteVisibility: String? = null,
        private var storedNotificationBadgeMode: String? = null,
        private var storedListGesturesEnabled: Boolean? = null,
    ) : DisplayPreferenceStore {
        var savedTimelineDensity: String? = null
        var savedDefaultNoteVisibility: String? = null
        var savedNotificationBadgeMode: String? = null
        var savedListGesturesEnabled: Boolean? = null

        override fun loadTimelineDensity(): String? {
            return storedTimelineDensity
        }

        override fun saveTimelineDensity(density: String) {
            savedTimelineDensity = density
            storedTimelineDensity = density
        }

        override fun loadDefaultNoteVisibility(): String? {
            return storedDefaultNoteVisibility
        }

        override fun saveDefaultNoteVisibility(visibility: String) {
            savedDefaultNoteVisibility = visibility
            storedDefaultNoteVisibility = visibility
        }

        override fun loadNotificationBadgeMode(): String? {
            return storedNotificationBadgeMode
        }

        override fun saveNotificationBadgeMode(mode: String) {
            savedNotificationBadgeMode = mode
            storedNotificationBadgeMode = mode
        }

        override fun loadListGesturesEnabled(): Boolean? {
            return storedListGesturesEnabled
        }

        override fun saveListGesturesEnabled(enabled: Boolean) {
            savedListGesturesEnabled = enabled
            storedListGesturesEnabled = enabled
        }
    }
}
