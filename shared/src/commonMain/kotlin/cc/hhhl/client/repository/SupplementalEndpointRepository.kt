package cc.hhhl.client.repository

import cc.hhhl.client.api.ActivityPubGetQuery
import cc.hhhl.client.api.PublicNotesOptions
import cc.hhhl.client.api.SharkeySupplementalEndpointApi
import cc.hhhl.client.api.SupplementalActionResult
import cc.hhhl.client.api.SupplementalEndpointApi
import cc.hhhl.client.api.SupplementalResult
import kotlinx.serialization.json.JsonElement

open class SupplementalEndpointRepository(
    private val tokenProvider: () -> String?,
    private val api: SupplementalEndpointApi = SharkeySupplementalEndpointApi(),
) {
    open suspend fun loadActivityPubObject(query: ActivityPubGetQuery): SupplementalRepositoryResult {
        return mapValue(api.loadActivityPubObject(tokenProvider().orEmpty(), query))
    }

    open suspend fun showActivityPubObject(uri: String): SupplementalRepositoryResult {
        return mapValue(api.showActivityPubObject(tokenProvider().orEmpty(), uri))
    }

    open suspend fun fetchExternalResource(url: String, hash: String): SupplementalRepositoryResult {
        return mapValue(api.fetchExternalResource(tokenProvider().orEmpty(), url, hash))
    }

    open suspend fun fetchRss(url: String): SupplementalRepositoryResult {
        return mapValue(api.fetchRss(url))
    }

    open suspend fun pushPageEvent(pageId: String, event: String, value: JsonElement? = null): SupplementalRepositoryResult {
        return mapAction(api.pushPageEvent(tokenProvider().orEmpty(), pageId, event, value))
    }

    open suspend fun readPromo(noteId: String): SupplementalRepositoryResult {
        return mapAction(api.readPromo(tokenProvider().orEmpty(), noteId))
    }

    open suspend fun loadRetention(): SupplementalRepositoryResult {
        return mapValue(api.loadRetention())
    }

    open suspend fun loadSponsors(forceUpdate: Boolean = false, instance: Boolean = false): SupplementalRepositoryResult {
        return mapValue(api.loadSponsors(forceUpdate, instance))
    }

    open suspend fun loadPublicNotes(options: PublicNotesOptions = PublicNotesOptions()): SupplementalRepositoryResult {
        return mapValue(api.loadPublicNotes(options))
    }

    open suspend fun loadDriveUsage(): SupplementalRepositoryResult {
        return mapValue(api.loadDriveUsage(tokenProvider().orEmpty()))
    }

    private fun <T> mapValue(result: SupplementalResult<T>): SupplementalRepositoryResult {
        return when (result) {
            is SupplementalResult.Success -> SupplementalRepositoryResult.Success(result.value)
            SupplementalResult.Unauthorized -> SupplementalRepositoryResult.Unauthorized
            is SupplementalResult.NetworkError -> SupplementalRepositoryResult.Error("无法连接服务器：${result.message}")
            is SupplementalResult.ServerError -> SupplementalRepositoryResult.Error(result.message)
        }
    }

    private fun mapAction(result: SupplementalActionResult): SupplementalRepositoryResult {
        return when (result) {
            SupplementalActionResult.Success -> SupplementalRepositoryResult.ActionSuccess
            SupplementalActionResult.Unauthorized -> SupplementalRepositoryResult.Unauthorized
            is SupplementalActionResult.NetworkError -> SupplementalRepositoryResult.Error("无法连接服务器：${result.message}")
            is SupplementalActionResult.ServerError -> SupplementalRepositoryResult.Error(result.message)
        }
    }
}

sealed interface SupplementalRepositoryResult {
    data class Success<T>(val value: T) : SupplementalRepositoryResult
    data object ActionSuccess : SupplementalRepositoryResult
    data object Unauthorized : SupplementalRepositoryResult
    data class Error(val message: String) : SupplementalRepositoryResult
}
