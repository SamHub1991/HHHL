package cc.hhhl.client.api

import cc.hhhl.client.model.Achievement
import cc.hhhl.client.model.achievementCatalog
import cc.hhhl.client.model.achievementMetadata
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.datetime.Instant

interface AchievementApi {
    suspend fun loadAchievements(
        token: String,
        userId: String,
    ): AchievementLoadResult

    suspend fun claimAchievement(
        token: String,
        name: String,
    ): AchievementClaimResult
}

sealed interface AchievementLoadResult {
    data class Success(val achievements: List<Achievement>) : AchievementLoadResult
    data object Unauthorized : AchievementLoadResult
    data class ServerError(val statusCode: Int, val message: String) : AchievementLoadResult
    data class NetworkError(val message: String) : AchievementLoadResult
}

sealed interface AchievementClaimResult {
    data object Success : AchievementClaimResult
    data object Unauthorized : AchievementClaimResult
    data class ServerError(val statusCode: Int, val message: String) : AchievementClaimResult
    data class NetworkError(val message: String) : AchievementClaimResult
}

class SharkeyAchievementApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultAchievementClient(),
) : AchievementApi {
    override suspend fun loadAchievements(
        token: String,
        userId: String,
    ): AchievementLoadResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty() || cleanUserId.isEmpty()) return AchievementLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("users", "achievements")) {
                contentType(ContentType.Application.Json)
                setBody(AchievementListRequest(i = cleanToken, userId = cleanUserId))
            }

            if (response.isSharkeyUnauthorized()) return AchievementLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> AchievementLoadResult.Success(
                    response.body<List<AchievementDto>>().toDomainAchievements(),
                )
                HttpStatusCode.Unauthorized -> AchievementLoadResult.Unauthorized
                else -> AchievementLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AchievementLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun claimAchievement(
        token: String,
        name: String,
    ): AchievementClaimResult {
        val cleanToken = token.trim()
        val cleanName = name.trim()
        if (cleanToken.isEmpty() || cleanName.isEmpty()) return AchievementClaimResult.Unauthorized

        return try {
            val response = client.post(apiUrl("i", "claim-achievement")) {
                contentType(ContentType.Application.Json)
                setBody(AchievementClaimRequest(i = cleanToken, name = cleanName))
            }

            if (response.isSharkeyUnauthorized()) return AchievementClaimResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> AchievementClaimResult.Success
                HttpStatusCode.Unauthorized -> AchievementClaimResult.Unauthorized
                else -> AchievementClaimResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AchievementClaimResult.NetworkError(error.message ?: "网络请求失败")
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
private data class AchievementListRequest(
    val i: String,
    val userId: String,
)

@Serializable
private data class AchievementClaimRequest(
    val i: String,
    val name: String,
)

@Serializable
private data class AchievementDto(
    val name: String,
    val unlockedAt: JsonElement? = null,
)

private fun List<AchievementDto>.toDomainAchievements(): List<Achievement> {
    val unlockedByName = associateBy { it.name }
    val unlocked = map { dto ->
        val unlockedAt = dto.unlockedAt.toAchievementUnlockedAt()
        achievementMetadata(dto.name).copy(
            unlockedAt = unlockedAt,
            unlockedAtLabel = unlockedAt?.toLocalCompactDateLabel().orEmpty(),
        )
    }
    val locked = achievementCatalog
        .filterNot { unlockedByName.containsKey(it.name) }
        .map { it.copy(unlockedAt = null, unlockedAtLabel = "") }
    return unlocked.sortedByDescending { it.unlockedAt.orEmpty() } + locked
}

private fun JsonElement?.toAchievementUnlockedAt(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    primitive.longOrNull?.let { millis ->
        return runCatching { Instant.fromEpochMilliseconds(millis).toString() }.getOrNull()
    }
    return primitive.contentOrNull?.takeIf { it.isNotBlank() }
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultAchievementClient(): HttpClient {
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
