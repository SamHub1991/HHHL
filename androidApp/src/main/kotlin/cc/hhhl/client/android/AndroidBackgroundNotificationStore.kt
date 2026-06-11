package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.notification.ChatNoiseReductionSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidBackgroundNotificationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun isBackgroundSyncEnabled(): Boolean {
        return preferences.getBoolean(KEY_BACKGROUND_SYNC_ENABLED, true)
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_BACKGROUND_SYNC_ENABLED, enabled)
            .apply()
    }

    fun isSpecialCareEnabled(): Boolean {
        return preferences.getBoolean(KEY_SPECIAL_CARE_ENABLED, true)
    }

    fun setSpecialCareEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SPECIAL_CARE_ENABLED, enabled)
            .apply()
    }

    fun loadChatNoiseReductionSettings(): ChatNoiseReductionSettings {
        val payload = preferences.getString(KEY_CHAT_NOISE_REDUCTION, null)
        return runCatching {
            json.decodeFromString<ChatNoiseReductionSettings>(payload.orEmpty())
        }.getOrDefault(ChatNoiseReductionSettings()).normalized
    }

    fun saveChatNoiseReductionSettings(settings: ChatNoiseReductionSettings) {
        preferences.edit()
            .putString(KEY_CHAT_NOISE_REDUCTION, json.encodeToString(settings.normalized))
            .apply()
    }

    fun loadSeenIds(): Set<String> {
        return synchronized(SEEN_IDS_LOCK) {
            loadSeenIdsLocked()
        }
    }

    fun saveSeenIds(ids: Set<String>) {
        mergeSeenIds(ids, MAX_SEEN_IDS)
    }

    fun mergeSeenIds(
        ids: Collection<String>,
        limit: Int = MAX_SEEN_IDS,
    ) {
        val cleanIds = ids.cleanSeenIds()
        if (cleanIds.isEmpty()) return
        synchronized(SEEN_IDS_LOCK) {
            val merged = cleanIds.mergeWithSeenIds(
                previous = loadSeenIdsLocked(),
                limit = limit.coerceAtLeast(1),
            )
            saveSeenIdsLocked(merged)
        }
    }

    fun claimSeenIds(
        ids: Collection<String>,
        limit: Int = MAX_SEEN_IDS,
    ): Set<String> {
        val cleanIds = ids.cleanSeenIds()
        if (cleanIds.isEmpty()) return emptySet()
        return synchronized(SEEN_IDS_LOCK) {
            val current = loadSeenIdsLocked()
            val claimed = cleanIds.filterNot { it in current }
            if (claimed.isNotEmpty()) {
                saveSeenIdsLocked(
                    claimed.mergeWithSeenIds(
                        previous = current,
                        limit = limit.coerceAtLeast(1),
                    ),
                )
            }
            claimed.toSet()
        }
    }

    fun claimSeenIdGroup(
        ids: Collection<String>,
        limit: Int = MAX_SEEN_IDS,
    ): Boolean {
        val cleanIds = ids.cleanSeenIds()
        if (cleanIds.isEmpty()) return false
        return synchronized(SEEN_IDS_LOCK) {
            val current = loadSeenIdsLocked()
            if (cleanIds.any { it in current }) {
                saveSeenIdsLocked(
                    cleanIds.mergeWithSeenIds(
                        previous = current,
                        limit = limit.coerceAtLeast(1),
                    ),
                )
                return@synchronized false
            }
            saveSeenIdsLocked(
                cleanIds.mergeWithSeenIds(
                    previous = current,
                    limit = limit.coerceAtLeast(1),
                ),
            )
            true
        }
    }

    private fun loadSeenIdsLocked(): Set<String> {
        return preferences.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty().toSet()
    }

    private fun saveSeenIdsLocked(ids: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_SEEN_IDS, ids.take(MAX_SEEN_IDS).toSet())
            .commit()
    }

    private fun Collection<String>.cleanSeenIds(): List<String> {
        return map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun List<String>.mergeWithSeenIds(
        previous: Set<String>,
        limit: Int,
    ): Set<String> {
        val updatedBaselineSources = mapNotNull { it.timelineBaselineSourceId() }.toSet()
        val retainedPrevious = previous.filterNot { seenId ->
            seenId.timelineBaselineSourceId()?.let { it in updatedBaselineSources } == true
        }
        return (this + retainedPrevious).take(limit).toSet()
    }

    private fun String.timelineBaselineSourceId(): String? {
        val prefix = "automation-baseline:"
        if (!startsWith(prefix)) return null
        return removePrefix(prefix).substringBeforeLast(':').takeIf { it.isNotBlank() }
    }

    private companion object {
        val SEEN_IDS_LOCK = Any()
        const val PREFERENCES_NAME = "hhhl_background_notifications"
        const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
        const val KEY_SPECIAL_CARE_ENABLED = "special_care_enabled"
        const val KEY_CHAT_NOISE_REDUCTION = "chat_noise_reduction"
        const val KEY_SEEN_IDS = "seen_ids"
        const val MAX_SEEN_IDS = 1_000

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
