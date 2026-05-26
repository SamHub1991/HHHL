package cc.hhhl.client.repository

import cc.hhhl.client.api.SharkeyTimelineApi
import cc.hhhl.client.api.TimelineApi
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.api.TimelineLoadResult
import cc.hhhl.client.cache.NoopTimelineCache
import cc.hhhl.client.cache.TimelineCache
import cc.hhhl.client.model.Note

open class TimelineRepository(
    private val tokenProvider: () -> String?,
    private val api: TimelineApi = SharkeyTimelineApi(),
    private val cache: TimelineCache = NoopTimelineCache,
) {
    open suspend fun restore(kind: TimelineKind): TimelineRepositoryResult {
        return TimelineRepositoryResult.Success(cache.read(kind))
    }

    open suspend fun refresh(kind: TimelineKind): TimelineRepositoryResult {
        return load(kind = kind, currentNotes = emptyList(), untilId = null)
    }

    open suspend fun loadMore(
        kind: TimelineKind,
        currentNotes: List<Note>,
    ): TimelineRepositoryResult {
        return load(
            kind = kind,
            currentNotes = currentNotes,
            untilId = currentNotes.lastOrNull()?.id,
        )
    }

    private suspend fun load(
        kind: TimelineKind,
        currentNotes: List<Note>,
        untilId: String?,
    ): TimelineRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return TimelineRepositoryResult.Unauthorized

        return when (val result = api.loadTimeline(kind, token, DEFAULT_PAGE_SIZE, untilId)) {
            is TimelineLoadResult.Success -> {
                val notes = (currentNotes + result.notes).distinctBy { it.id }
                runCatching { cache.write(kind, notes) }
                TimelineRepositoryResult.Success(
                    notes = notes,
                    endReached = result.notes.isEmpty(),
                )
            }
            TimelineLoadResult.Unauthorized -> TimelineRepositoryResult.Unauthorized
            is TimelineLoadResult.NetworkError -> TimelineRepositoryResult.Error("无法连接服务器：${result.message}")
            is TimelineLoadResult.ServerError -> TimelineRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface TimelineRepositoryResult {
    data class Success(
        val notes: List<Note>,
        val endReached: Boolean = false,
    ) : TimelineRepositoryResult

    data object Unauthorized : TimelineRepositoryResult

    data class Error(val message: String) : TimelineRepositoryResult
}
