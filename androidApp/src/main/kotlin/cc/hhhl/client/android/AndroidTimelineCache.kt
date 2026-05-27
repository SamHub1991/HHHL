package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.cache.TimelineCache
import cc.hhhl.client.cache.TimelineCacheCodec
import cc.hhhl.client.model.Note

class AndroidTimelineCache(context: Context) : TimelineCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private var snapshots: Map<TimelineKind, List<Note>>? = null

    override suspend fun read(kind: TimelineKind): List<Note> {
        return loadedSnapshots()[kind].orEmpty()
    }

    override suspend fun write(kind: TimelineKind, notes: List<Note>) {
        val nextSnapshots = loadedSnapshots() + (kind to notes.take(MAX_NOTES_PER_TIMELINE))
        snapshots = nextSnapshots
        preferences.edit()
            .putString(KEY_SNAPSHOTS, TimelineCacheCodec.encode(nextSnapshots))
            .apply()
    }

    private fun loadedSnapshots(): Map<TimelineKind, List<Note>> {
        return snapshots ?: readSnapshots().also { snapshots = it }
    }

    private fun readSnapshots(): Map<TimelineKind, List<Note>> {
        return TimelineCacheCodec.decode(preferences.getString(KEY_SNAPSHOTS, null))
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_timeline_cache"
        const val KEY_SNAPSHOTS = "snapshots"
        const val MAX_NOTES_PER_TIMELINE = 80
    }
}
