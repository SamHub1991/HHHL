package cc.hhhl.client.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class TimelineDensity(
    val storageKey: String,
    val label: String,
) {
    Comfortable("comfortable", "舒适"),
    Compact("compact", "紧凑"),
    UltraCompact("ultra_compact", "超紧凑");

    companion object {
        fun fromStoredValue(value: String?): TimelineDensity {
            return value
                ?.let { stored -> entries.firstOrNull { it.storageKey == stored || it.name == stored } }
                ?: Comfortable
        }
    }
}

enum class DefaultNoteVisibility(
    val storageKey: String,
    val label: String,
) {
    Public("public", "公开"),
    Home("home", "首页"),
    Followers("followers", "关注者");

    companion object {
        fun fromStoredValue(value: String?): DefaultNoteVisibility {
            return value
                ?.let { stored -> entries.firstOrNull { it.storageKey == stored || it.name == stored } }
                ?: Public
        }
    }
}

enum class NotificationBadgeMode(
    val storageKey: String,
    val label: String,
    val showsBadges: Boolean,
) {
    Show("show", "显示", true),
    Hide("hide", "隐藏", false);

    companion object {
        fun fromStoredValue(value: String?): NotificationBadgeMode {
            return value
                ?.let { stored -> entries.firstOrNull { it.storageKey == stored || it.name == stored } }
                ?: Show
        }
    }
}

data class DisplayPreferenceUiState(
    val timelineDensity: TimelineDensity = TimelineDensity.Comfortable,
    val defaultNoteVisibility: DefaultNoteVisibility = DefaultNoteVisibility.Public,
    val notificationBadgeMode: NotificationBadgeMode = NotificationBadgeMode.Show,
    val listGesturesEnabled: Boolean = true,
)

interface DisplayPreferenceStore {
    fun loadTimelineDensity(): String?

    fun saveTimelineDensity(density: String)

    fun loadDefaultNoteVisibility(): String?

    fun saveDefaultNoteVisibility(visibility: String)

    fun loadNotificationBadgeMode(): String?

    fun saveNotificationBadgeMode(mode: String)

    fun loadListGesturesEnabled(): Boolean? = null

    fun saveListGesturesEnabled(enabled: Boolean) = Unit
}

object NoopDisplayPreferenceStore : DisplayPreferenceStore {
    override fun loadTimelineDensity(): String? = null

    override fun saveTimelineDensity(density: String) = Unit

    override fun loadDefaultNoteVisibility(): String? = null

    override fun saveDefaultNoteVisibility(visibility: String) = Unit

    override fun loadNotificationBadgeMode(): String? = null

    override fun saveNotificationBadgeMode(mode: String) = Unit

    override fun loadListGesturesEnabled(): Boolean? = null

    override fun saveListGesturesEnabled(enabled: Boolean) = Unit
}

class DisplayPreferenceStateHolder(
    private val store: DisplayPreferenceStore = NoopDisplayPreferenceStore,
) {
    private val mutableState = MutableStateFlow(DisplayPreferenceUiState())
    val state: StateFlow<DisplayPreferenceUiState> = mutableState

    fun restoreStoredPreferences() {
        val restoredDensity = TimelineDensity.fromStoredValue(store.loadTimelineDensity())
        val restoredDefaultVisibility = DefaultNoteVisibility.fromStoredValue(
            store.loadDefaultNoteVisibility(),
        )
        val restoredNotificationBadgeMode = NotificationBadgeMode.fromStoredValue(
            store.loadNotificationBadgeMode(),
        )
        val restoredListGesturesEnabled = store.loadListGesturesEnabled() ?: true

        mutableState.update {
            it.copy(
                timelineDensity = restoredDensity,
                defaultNoteVisibility = restoredDefaultVisibility,
                notificationBadgeMode = restoredNotificationBadgeMode,
                listGesturesEnabled = restoredListGesturesEnabled,
            )
        }
    }

    fun selectTimelineDensity(density: TimelineDensity) {
        store.saveTimelineDensity(density.storageKey)
        mutableState.update { it.copy(timelineDensity = density) }
    }

    fun selectDefaultNoteVisibility(visibility: DefaultNoteVisibility) {
        store.saveDefaultNoteVisibility(visibility.storageKey)
        mutableState.update { it.copy(defaultNoteVisibility = visibility) }
    }

    fun selectNotificationBadgeMode(mode: NotificationBadgeMode) {
        store.saveNotificationBadgeMode(mode.storageKey)
        mutableState.update { it.copy(notificationBadgeMode = mode) }
    }

    fun setListGesturesEnabled(enabled: Boolean) {
        store.saveListGesturesEnabled(enabled)
        mutableState.update { it.copy(listGesturesEnabled = enabled) }
    }
}
