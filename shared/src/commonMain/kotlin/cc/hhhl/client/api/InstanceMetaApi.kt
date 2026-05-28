package cc.hhhl.client.api

import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.InstanceEndpointInfo
import cc.hhhl.client.model.InstanceEndpointParam
import cc.hhhl.client.model.InstanceMeta
import cc.hhhl.client.model.InstanceOnlineUsers
import cc.hhhl.client.model.InstanceServerInfo
import cc.hhhl.client.model.InstanceStats
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

    suspend fun ping(): InstanceAuxiliaryLoadResult<Long>

    suspend fun loadEndpoints(): InstanceAuxiliaryLoadResult<List<String>>

    suspend fun loadEndpointInfo(endpoint: String): InstanceAuxiliaryLoadResult<InstanceEndpointInfo?>

    suspend fun loadStats(): InstanceAuxiliaryLoadResult<InstanceStats>

    suspend fun loadOnlineUsers(): InstanceAuxiliaryLoadResult<InstanceOnlineUsers>

    suspend fun loadServerInfo(): InstanceAuxiliaryLoadResult<InstanceServerInfo>
}

sealed interface InstanceMetaLoadResult {
    data class Success(val meta: InstanceMeta) : InstanceMetaLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : InstanceMetaLoadResult

    data class NetworkError(val message: String) : InstanceMetaLoadResult
}

sealed interface InstanceAuxiliaryLoadResult<out T> {
    data class Success<T>(val value: T) : InstanceAuxiliaryLoadResult<T>

    data object Unavailable : InstanceAuxiliaryLoadResult<Nothing>
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

    override suspend fun ping(): InstanceAuxiliaryLoadResult<Long> {
        return loadAuxiliary("ping") { response -> response.body<InstancePingDto>().pong }
    }

    override suspend fun loadEndpoints(): InstanceAuxiliaryLoadResult<List<String>> {
        return loadAuxiliary("endpoints") { response -> response.body<List<String>>() }
    }

    override suspend fun loadEndpointInfo(endpoint: String): InstanceAuxiliaryLoadResult<InstanceEndpointInfo?> {
        return try {
            val response = client.post(apiUrl("endpoint")) {
                contentType(ContentType.Application.Json)
                setBody(EndpointInfoRequest(endpoint = endpoint))
            }
            if (response.status == HttpStatusCode.OK) {
                InstanceAuxiliaryLoadResult.Success(response.body<EndpointInfoDto?>()?.toDomain())
            } else {
                InstanceAuxiliaryLoadResult.Unavailable
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            InstanceAuxiliaryLoadResult.Unavailable
        }
    }

    override suspend fun loadStats(): InstanceAuxiliaryLoadResult<InstanceStats> {
        return loadAuxiliary("stats") { response -> response.body<InstanceStatsDto>().toDomainStats() }
    }

    override suspend fun loadOnlineUsers(): InstanceAuxiliaryLoadResult<InstanceOnlineUsers> {
        return loadAuxiliary("get-online-users-count") { response ->
            response.body<InstanceOnlineUsersDto>().toDomainOnlineUsers()
        }
    }

    override suspend fun loadServerInfo(): InstanceAuxiliaryLoadResult<InstanceServerInfo> {
        return loadAuxiliary("server-info") { response -> response.body<InstanceServerInfoDto>().toDomainServerInfo() }
    }

    private suspend fun <T> loadAuxiliary(
        endpoint: String,
        decode: suspend (HttpResponse) -> T,
    ): InstanceAuxiliaryLoadResult<T> {
        return try {
            val response = client.post(apiUrl(endpoint))
            if (response.status == HttpStatusCode.OK) {
                InstanceAuxiliaryLoadResult.Success(decode(response))
            } else {
                InstanceAuxiliaryLoadResult.Unavailable
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            InstanceAuxiliaryLoadResult.Unavailable
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
private data class EndpointInfoRequest(
    val endpoint: String,
)

@Serializable
private data class InstancePingDto(
    val pong: Long = 0,
)

@Serializable
private data class EndpointInfoDto(
    val params: List<EndpointParamDto> = emptyList(),
) {
    fun toDomain(): InstanceEndpointInfo {
        return InstanceEndpointInfo(
            params = params.map { param ->
                InstanceEndpointParam(
                    name = param.name,
                    type = param.type,
                )
            },
        )
    }
}

@Serializable
private data class EndpointParamDto(
    val name: String = "",
    val type: String = "",
)

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
private data class InstanceStatsDto(
    val notesCount: Long = 0,
    val originalNotesCount: Long = 0,
    val usersCount: Long = 0,
    val originalUsersCount: Long = 0,
    val reactionsCount: Long = 0,
    val instances: Long = 0,
    val driveUsageLocal: Long = 0,
    val driveUsageRemote: Long = 0,
) {
    fun toDomainStats(): InstanceStats {
        return InstanceStats(
            notesCount = notesCount,
            originalNotesCount = originalNotesCount,
            usersCount = usersCount,
            originalUsersCount = originalUsersCount,
            reactionsCount = reactionsCount,
            instances = instances,
            driveUsageLocal = driveUsageLocal,
            driveUsageRemote = driveUsageRemote,
        )
    }
}

@Serializable
private data class InstanceOnlineUsersDto(
    val count: Int = 0,
    val countAcrossNetwork: Int = 0,
) {
    fun toDomainOnlineUsers(): InstanceOnlineUsers {
        return InstanceOnlineUsers(
            count = count,
            countAcrossNetwork = countAcrossNetwork,
        )
    }
}

@Serializable
private data class InstanceServerInfoDto(
    val machine: String = "",
    val cpu: InstanceCpuInfoDto = InstanceCpuInfoDto(),
    val mem: InstanceMemoryInfoDto = InstanceMemoryInfoDto(),
    val fs: InstanceFileSystemInfoDto = InstanceFileSystemInfoDto(),
) {
    fun toDomainServerInfo(): InstanceServerInfo {
        return InstanceServerInfo(
            machine = machine.takeIf { it.isNotBlank() && it != "?" }.orEmpty(),
            cpuModel = cpu.model.takeIf { it.isNotBlank() && it != "?" }.orEmpty(),
            cpuCores = cpu.cores,
            memoryTotal = mem.total,
            storageTotal = fs.total,
            storageUsed = fs.used,
        )
    }
}

@Serializable
private data class InstanceCpuInfoDto(
    val model: String = "",
    val cores: Int = 0,
)

@Serializable
private data class InstanceMemoryInfoDto(
    val total: Long = 0,
)

@Serializable
private data class InstanceFileSystemInfoDto(
    val total: Long = 0,
    val used: Long = 0,
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
