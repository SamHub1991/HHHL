package cc.hhhl.client.repository

import cc.hhhl.client.api.FavoriteNoteApi
import cc.hhhl.client.api.FavoriteNoteLoadResult
import cc.hhhl.client.api.SharkeyFavoriteNoteApi
import cc.hhhl.client.model.FavoriteNote

open class FavoriteNoteRepository(
    private val tokenProvider: () -> String?,
    private val api: FavoriteNoteApi = SharkeyFavoriteNoteApi(),
) {
    open suspend fun refresh(): FavoriteNotesRepositoryResult {
        return loadFavorites(currentFavorites = emptyList(), untilId = null)
    }

    open suspend fun loadMore(currentFavorites: List<FavoriteNote>): FavoriteNotesRepositoryResult {
        return loadFavorites(
            currentFavorites = currentFavorites,
            untilId = currentFavorites.lastOrNull()?.id,
        )
    }

    private suspend fun loadFavorites(
        currentFavorites: List<FavoriteNote>,
        untilId: String?,
    ): FavoriteNotesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return FavoriteNotesRepositoryResult.Unauthorized

        return when (val result = api.loadFavorites(token = token, limit = PAGE_SIZE, untilId = untilId)) {
            is FavoriteNoteLoadResult.Success -> FavoriteNotesRepositoryResult.Success(
                favorites = currentFavorites.appendDistinctBy(result.favorites) { it.id },
            )
            FavoriteNoteLoadResult.Unauthorized -> FavoriteNotesRepositoryResult.Unauthorized
            is FavoriteNoteLoadResult.NetworkError -> {
                FavoriteNotesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is FavoriteNoteLoadResult.ServerError -> FavoriteNotesRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

sealed interface FavoriteNotesRepositoryResult {
    data class Success(val favorites: List<FavoriteNote>) : FavoriteNotesRepositoryResult

    data object Unauthorized : FavoriteNotesRepositoryResult

    data class Error(val message: String) : FavoriteNotesRepositoryResult
}
