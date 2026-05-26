package cc.hhhl.client.repository

import cc.hhhl.client.api.FlashApi
import cc.hhhl.client.api.FlashActionResult
import cc.hhhl.client.api.FlashLoadResult
import cc.hhhl.client.api.FlashShowResult
import cc.hhhl.client.api.SharkeyFlashApi
import cc.hhhl.client.model.Flash
import cc.hhhl.client.model.FlashListKind

open class FlashRepository(
    private val tokenProvider: () -> String?,
    private val api: FlashApi = SharkeyFlashApi(),
) {
    open suspend fun refreshFlashes(kind: FlashListKind): FlashesRepositoryResult {
        return loadFlashes(
            kind = kind,
            currentFlashes = emptyList(),
            untilId = null,
            offset = 0,
        )
    }

    open suspend fun loadMoreFlashes(
        kind: FlashListKind,
        currentFlashes: List<Flash>,
    ): FlashesRepositoryResult {
        return loadFlashes(
            kind = kind,
            currentFlashes = currentFlashes,
            untilId = if (kind == FlashListKind.Featured) null else currentFlashes.lastOrNull()?.id,
            offset = if (kind == FlashListKind.Featured) currentFlashes.size else 0,
        )
    }

    open suspend fun showFlash(flashId: String): FlashRepositoryResult {
        val cleanFlashId = flashId.trim()
        if (cleanFlashId.isEmpty()) {
            return FlashRepositoryResult.Error("无法读取 Play")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return FlashRepositoryResult.Unauthorized

        return when (val result = api.showFlash(token, cleanFlashId)) {
            is FlashShowResult.Success -> FlashRepositoryResult.Success(result.flash)
            FlashShowResult.Unauthorized -> FlashRepositoryResult.Unauthorized
            is FlashShowResult.NetworkError -> {
                FlashRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is FlashShowResult.ServerError -> FlashRepositoryResult.Error(result.message)
        }
    }

    open suspend fun likeFlash(flashId: String): FlashActionRepositoryResult {
        return performFlashAction(flashId) { token, cleanFlashId ->
            api.likeFlash(token, cleanFlashId)
        }
    }

    open suspend fun unlikeFlash(flashId: String): FlashActionRepositoryResult {
        return performFlashAction(flashId) { token, cleanFlashId ->
            api.unlikeFlash(token, cleanFlashId)
        }
    }

    private suspend fun loadFlashes(
        kind: FlashListKind,
        currentFlashes: List<Flash>,
        untilId: String?,
        offset: Int,
    ): FlashesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return FlashesRepositoryResult.Unauthorized

        return when (
            val result = api.loadFlashes(
                token = token,
                kind = kind,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
                offset = offset,
            )
        ) {
            is FlashLoadResult.Success -> FlashesRepositoryResult.Success(
                flashes = (currentFlashes + result.flashes).distinctBy { it.id },
                endReached = result.flashes.isEmpty(),
            )
            FlashLoadResult.Unauthorized -> FlashesRepositoryResult.Unauthorized
            is FlashLoadResult.NetworkError -> {
                FlashesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is FlashLoadResult.ServerError -> FlashesRepositoryResult.Error(result.message)
        }
    }

    private suspend fun performFlashAction(
        flashId: String,
        action: suspend (String, String) -> FlashActionResult,
    ): FlashActionRepositoryResult {
        val cleanFlashId = flashId.trim()
        if (cleanFlashId.isEmpty()) {
            return FlashActionRepositoryResult.Error("无法读取 Play")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return FlashActionRepositoryResult.Unauthorized

        return when (val result = action(token, cleanFlashId)) {
            FlashActionResult.Success -> FlashActionRepositoryResult.Success
            FlashActionResult.Unauthorized -> FlashActionRepositoryResult.Unauthorized
            is FlashActionResult.NetworkError -> {
                FlashActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is FlashActionResult.ServerError -> FlashActionRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface FlashesRepositoryResult {
    data class Success(
        val flashes: List<Flash>,
        val endReached: Boolean = false,
    ) : FlashesRepositoryResult

    data object Unauthorized : FlashesRepositoryResult

    data class Error(val message: String) : FlashesRepositoryResult
}

sealed interface FlashRepositoryResult {
    data class Success(val flash: Flash) : FlashRepositoryResult

    data object Unauthorized : FlashRepositoryResult

    data class Error(val message: String) : FlashRepositoryResult
}

sealed interface FlashActionRepositoryResult {
    data object Success : FlashActionRepositoryResult

    data object Unauthorized : FlashActionRepositoryResult

    data class Error(val message: String) : FlashActionRepositoryResult
}
