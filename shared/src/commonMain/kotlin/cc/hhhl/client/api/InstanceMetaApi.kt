package cc.hhhl.client.api

import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.InstanceMeta
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface InstanceMetaApi {
    suspend fun loadMeta(): InstanceMetaLoadResult
}

sealed interface InstanceMetaLoadResult {
    data class Success(val meta: InstanceMeta) : InstanceMetaLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : InstanceMetaLoadResult

    data class NetworkError(val message: String) : InstanceMetaLoadResult
}

class SharkeyInstanceMetaApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultInstanceMetaClient(),
) : InstanceMetaApi {
    override suspend fun loadMeta(): InstanceMetaLoadResult {
        return try {
            val response = client.post(apiUrl("meta")) {
                contentType(ContentType.Application.Json)
                setBody(InstanceMetaRequest())
            }

            when (response.status) {
                HttpStatusCode.OK -> InstanceMetaLoadResult.Success(
                    response.body<InstanceMetaDto>().toDomainMeta(),
                )
                else -> InstanceMetaLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            InstanceMetaLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

@Serializable
private class InstanceMetaRequest

@Serializable
private data class InstanceMetaDto(
    val name: String = "hhhl",
    val description: String = "",
    val version: String = "",
    val iconUrl: String? = null,
    val themeColor: String? = null,
    val maxNoteTextLength: Int = 3000,
    val maxCwLength: Int = 500,
    val defaultLike: String = "❤️",
    val noteSearchableScope: String? = null,
    val policies: InstancePoliciesDto = InstancePoliciesDto(),
    val features: InstanceFeaturesDto = InstanceFeaturesDto(),
) {
    fun toDomainMeta(): InstanceMeta {
        return InstanceMeta(
            name = name,
            description = description,
            version = version,
            iconUrl = iconUrl,
            themeColor = themeColor,
            maxNoteTextLength = maxNoteTextLength,
            maxCwLength = maxCwLength,
            defaultLike = defaultLike,
            noteSearchableScope = noteSearchableScope,
            capabilities = InstanceCapabilities(
                miauthEnabled = features.miauth,
                localTimelineAvailable = policies.ltlAvailable,
                globalTimelineAvailable = policies.gtlAvailable,
                bubbleTimelineAvailable = policies.btlAvailable,
                canPublicNote = policies.canPublicNote,
                canSearchNotes = policies.canSearchNotes,
                chatAvailable = policies.chatAvailability == "available",
                canTrend = policies.canTrend,
                canViewFederation = policies.canViewFederation,
                clipLimit = policies.clipLimit,
                antennaLimit = policies.antennaLimit,
                userListLimit = policies.userListLimit,
                userEachUserListsLimit = policies.userEachUserListsLimit,
                scheduleNoteMax = policies.scheduleNoteMax,
                driveCapacityMb = policies.driveCapacityMb,
                maxFileSizeMb = policies.maxFileSizeMb,
            ),
        )
    }
}

@Serializable
private data class InstancePoliciesDto(
    val gtlAvailable: Boolean = true,
    val ltlAvailable: Boolean = true,
    val btlAvailable: Boolean = false,
    val canPublicNote: Boolean = true,
    val canSearchNotes: Boolean = true,
    val clipLimit: Int = 0,
    val antennaLimit: Int = 0,
    val userListLimit: Int = 0,
    val userEachUserListsLimit: Int = 0,
    val scheduleNoteMax: Int = 0,
    val driveCapacityMb: Int = 0,
    val maxFileSizeMb: Int = 0,
    val chatAvailability: String = "unavailable",
    val canTrend: Boolean = false,
    val canViewFederation: Boolean = false,
)

@Serializable
private data class InstanceFeaturesDto(
    val miauth: Boolean = true,
)

@Serializable
private data class InstanceMetaErrorEnvelope(
    val error: InstanceMetaErrorDto? = null,
)

@Serializable
private data class InstanceMetaErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultInstanceMetaClient(): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
}
