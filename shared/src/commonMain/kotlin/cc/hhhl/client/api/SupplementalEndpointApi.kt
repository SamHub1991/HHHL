package cc.hhhl.client.api

import cc.hhhl.client.model.ActivityPubShowResult
import cc.hhhl.client.model.DriveUsage
import cc.hhhl.client.model.ExternalResource
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.RetentionRecord
import cc.hhhl.client.model.RssFeed
import cc.hhhl.client.model.RssFeedItem
import cc.hhhl.client.model.RssFeedMedia
import cc.hhhl.client.model.Sponsor
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

interface SupplementalEndpointApi {
    suspend fun loadActivityPubObject(
        token: String,
        query: ActivityPubGetQuery,
    ): SupplementalResult<JsonElement>

    suspend fun showActivityPubObject(
        token: String,
        uri: String,
    ): SupplementalResult<ActivityPubShowResult>

    suspend fun fetchExternalResource(
        token: String,
        url: String,
        hash: String,
    ): SupplementalResult<ExternalResource>

    suspend fun fetchRss(url: String): SupplementalResult<RssFeed>

    suspend fun pushPageEvent(
        token: String,
        pageId: String,
        event: String,
        value: JsonElement? = null,
    ): SupplementalActionResult

    suspend fun readPromo(
        token: String,
        noteId: String,
    ): SupplementalActionResult

    suspend fun loadRetention(): SupplementalResult<List<RetentionRecord>>

    suspend fun loadSponsors(
        forceUpdate: Boolean = false,
        instance: Boolean = false,
    ): SupplementalResult<List<Sponsor>>

    suspend fun loadPublicNotes(
        options: PublicNotesOptions = PublicNotesOptions(),
    ): SupplementalResult<List<Note>>

    suspend fun loadDriveUsage(token: String): SupplementalResult<DriveUsage>
}

data class ActivityPubGetQuery(
    val uri: String? = null,
    val userId: String? = null,
    val noteId: String? = null,
    val expandCollectionItems: Boolean = false,
    val expandCollectionLimit: Int? = null,
    val allowAnonymous: Boolean = false,
)

data class PublicNotesOptions(
    val local: Boolean = false,
    val reply: Boolean? = null,
    val renote: Boolean? = null,
    val withFiles: Boolean? = null,
    val poll: Boolean? = null,
    val limit: Int = 20,
    val sinceId: String? = null,
    val untilId: String? = null,
)

sealed interface SupplementalResult<out T> {
    data class Success<T>(val value: T) : SupplementalResult<T>
    data object Unauthorized : SupplementalResult<Nothing>
    data class ServerError(val statusCode: Int, val message: String) : SupplementalResult<Nothing>
    data class NetworkError(val message: String) : SupplementalResult<Nothing>
}

sealed interface SupplementalActionResult {
    data object Success : SupplementalActionResult
    data object Unauthorized : SupplementalActionResult
    data class ServerError(val statusCode: Int, val message: String) : SupplementalActionResult
    data class NetworkError(val message: String) : SupplementalActionResult
}

class SharkeySupplementalEndpointApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultSupplementalEndpointClient(),
) : SupplementalEndpointApi {
    override suspend fun loadActivityPubObject(
        token: String,
        query: ActivityPubGetQuery,
    ): SupplementalResult<JsonElement> {
        val cleanToken = token.trim()
        val inputs = listOf(query.uri, query.userId, query.noteId).count { !it.isNullOrBlank() }
        if (cleanToken.isEmpty()) return SupplementalResult.Unauthorized
        if (inputs != 1) return SupplementalResult.ServerError(400, "uri、userId、noteId 只能填写一个")

        return postValue<JsonElement, ActivityPubGetRequest>("ap", "get") {
            ActivityPubGetRequest(
                i = cleanToken,
                uri = query.uri.cleanNullable(),
                userId = query.userId.cleanNullable(),
                noteId = query.noteId.cleanNullable(),
                expandCollectionItems = query.expandCollectionItems,
                expandCollectionLimit = query.expandCollectionLimit,
                allowAnonymous = query.allowAnonymous,
            )
        }
    }

    override suspend fun showActivityPubObject(
        token: String,
        uri: String,
    ): SupplementalResult<ActivityPubShowResult> {
        val cleanToken = token.trim()
        val cleanUri = uri.trim()
        if (cleanToken.isEmpty()) return SupplementalResult.Unauthorized
        if (cleanUri.isEmpty()) return SupplementalResult.ServerError(400, "URI 不能为空")

        return postValue("ap", "show", body = { ActivityPubShowRequest(i = cleanToken, uri = cleanUri) }) {
            it.body<ActivityPubShowDto>().toDomain()
        }
    }

    override suspend fun fetchExternalResource(
        token: String,
        url: String,
        hash: String,
    ): SupplementalResult<ExternalResource> {
        val cleanToken = token.trim()
        val cleanUrl = url.trim()
        val cleanHash = hash.trim()
        if (cleanToken.isEmpty()) return SupplementalResult.Unauthorized
        if (cleanUrl.isEmpty() || cleanHash.isEmpty()) return SupplementalResult.ServerError(400, "资源地址和 hash 不能为空")

        return postValue("fetch-external-resources", body = {
            ExternalResourceRequest(i = cleanToken, url = cleanUrl, hash = cleanHash)
        }) {
            it.body<ExternalResourceDto>().toDomain()
        }
    }

    override suspend fun fetchRss(url: String): SupplementalResult<RssFeed> {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return SupplementalResult.ServerError(400, "RSS 地址不能为空")

        return postValue("fetch-rss", body = { FetchRssRequest(url = cleanUrl) }) {
            it.body<RssFeedDto>().toDomain()
        }
    }

    override suspend fun pushPageEvent(
        token: String,
        pageId: String,
        event: String,
        value: JsonElement?,
    ): SupplementalActionResult {
        val cleanToken = token.trim()
        val cleanPageId = pageId.trim()
        val cleanEvent = event.trim()
        if (cleanToken.isEmpty()) return SupplementalActionResult.Unauthorized
        if (cleanPageId.isEmpty() || cleanEvent.isEmpty()) {
            return SupplementalActionResult.ServerError(400, "页面 ID 和事件名不能为空")
        }

        return postAction("page-push") {
            PagePushRequest(i = cleanToken, pageId = cleanPageId, event = cleanEvent, value = value)
        }
    }

    override suspend fun readPromo(
        token: String,
        noteId: String,
    ): SupplementalActionResult {
        val cleanToken = token.trim()
        val cleanNoteId = noteId.trim()
        if (cleanToken.isEmpty()) return SupplementalActionResult.Unauthorized
        if (cleanNoteId.isEmpty()) return SupplementalActionResult.ServerError(400, "帖子 ID 不能为空")

        return postAction("promo", "read") { PromoReadRequest(i = cleanToken, noteId = cleanNoteId) }
    }

    override suspend fun loadRetention(): SupplementalResult<List<RetentionRecord>> {
        return postValueNoBody("retention") { response ->
            response.body<List<RetentionRecordDto>>().map { it.toDomain() }
        }
    }

    override suspend fun loadSponsors(
        forceUpdate: Boolean,
        instance: Boolean,
    ): SupplementalResult<List<Sponsor>> {
        return postValue("sponsors", body = { SponsorsRequest(forceUpdate = forceUpdate, instance = instance) }) {
            it.body<SponsorsResponseDto>().sponsorData.map { sponsor -> sponsor.toDomain() }
        }
    }

    override suspend fun loadPublicNotes(options: PublicNotesOptions): SupplementalResult<List<Note>> {
        return postValue("notes", body = {
            PublicNotesRequest(
                local = options.local,
                reply = options.reply,
                renote = options.renote,
                withFiles = options.withFiles,
                poll = options.poll,
                limit = options.limit.coerceIn(1, 100),
                sinceId = options.sinceId.cleanNullable(),
                untilId = options.untilId.cleanNullable(),
            )
        }) {
            it.body<List<SharkeyNoteDto>>().map { note -> note.toDomainNote() }
        }
    }

    override suspend fun loadDriveUsage(token: String): SupplementalResult<DriveUsage> {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return SupplementalResult.Unauthorized
        return postValue("drive", body = { AuthOnlyRequest(i = cleanToken) }) {
            it.body<DriveUsageDto>().toDomain()
        }
    }

    private suspend inline fun <reified T, reified B : Any> postValue(
        vararg endpoint: String,
        noinline body: () -> B,
    ): SupplementalResult<T> {
        return postValue(*endpoint, body = body) { response -> response.body<T>() }
    }

    private suspend inline fun <reified B : Any, T> postValue(
        vararg endpoint: String,
        noinline body: () -> B,
        noinline decode: suspend (HttpResponse) -> T,
    ): SupplementalResult<T> {
        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body())
            }
            response.toSupplementalResult(decode)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SupplementalResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun <T> postValueNoBody(
        vararg endpoint: String,
        decode: suspend (HttpResponse) -> T,
    ): SupplementalResult<T> {
        return try {
            val response = client.post(apiUrl(*endpoint))
            response.toSupplementalResult(decode)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SupplementalResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend inline fun <reified B : Any> postAction(
        vararg endpoint: String,
        noinline body: () -> B,
    ): SupplementalActionResult {
        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body())
            }
            if (response.isSharkeyUnauthorized()) return SupplementalActionResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> SupplementalActionResult.Success
                HttpStatusCode.Unauthorized -> SupplementalActionResult.Unauthorized
                else -> SupplementalActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SupplementalActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun <T> HttpResponse.toSupplementalResult(
        decode: suspend (HttpResponse) -> T,
    ): SupplementalResult<T> {
        if (isSharkeyUnauthorized()) return SupplementalResult.Unauthorized
        return when (status) {
            HttpStatusCode.OK -> SupplementalResult.Success(decode(this))
            HttpStatusCode.Unauthorized -> SupplementalResult.Unauthorized
            else -> SupplementalResult.ServerError(
                statusCode = status.value,
                message = apiErrorMessage() ?: "服务器返回 ${status.value}",
            )
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

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun String?.cleanNullable(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun defaultSupplementalEndpointClient(): HttpClient {
    return HttpClient {
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

@Serializable
private data class AuthOnlyRequest(val i: String)

@Serializable
private data class ActivityPubGetRequest(
    val i: String,
    val uri: String? = null,
    val userId: String? = null,
    val noteId: String? = null,
    val expandCollectionItems: Boolean = false,
    val expandCollectionLimit: Int? = null,
    val allowAnonymous: Boolean = false,
)

@Serializable
private data class ActivityPubShowRequest(
    val i: String,
    val uri: String,
)

@Serializable
private data class ActivityPubShowDto(
    val type: String,
    val `object`: JsonElement,
) {
    fun toDomain(): ActivityPubShowResult {
        return ActivityPubShowResult(type = type, objectJson = `object`)
    }
}

@Serializable
private data class ExternalResourceRequest(
    val i: String,
    val url: String,
    val hash: String,
)

@Serializable
private data class ExternalResourceDto(
    val type: String = "",
    val data: String = "",
) {
    fun toDomain(): ExternalResource = ExternalResource(type = type, data = data)
}

@Serializable
private data class FetchRssRequest(val url: String)

@Serializable
private data class RssFeedDto(
    val type: String = "",
    val id: String = "",
    val updated: String = "",
    val author: String = "",
    val link: String = "",
    val title: String = "",
    val description: String = "",
    val items: List<RssFeedItemDto> = emptyList(),
) {
    fun toDomain(): RssFeed {
        return RssFeed(
            type = type,
            id = id,
            updated = updated,
            author = author,
            link = link,
            title = title,
            description = description,
            items = items.map { it.toDomain() },
        )
    }
}

@Serializable
private data class RssFeedItemDto(
    val link: String = "",
    val guid: String = "",
    val title: String = "",
    val pubDate: String = "",
    val description: String = "",
    val media: List<RssFeedMediaDto> = emptyList(),
) {
    fun toDomain(): RssFeedItem {
        return RssFeedItem(
            link = link,
            guid = guid,
            title = title,
            pubDate = pubDate,
            description = description,
            media = media.map { it.toDomain() },
        )
    }
}

@Serializable
private data class RssFeedMediaDto(
    val medium: String = "",
    val url: String = "",
    val type: String = "",
    val lang: String = "",
) {
    fun toDomain(): RssFeedMedia = RssFeedMedia(medium = medium, url = url, type = type, lang = lang)
}

@Serializable
private data class PagePushRequest(
    val i: String,
    val pageId: String,
    val event: String,
    @SerialName("var")
    val value: JsonElement? = null,
)

@Serializable
private data class PromoReadRequest(
    val i: String,
    val noteId: String,
)

@Serializable
private data class RetentionRecordDto(
    val createdAt: String = "",
    val users: Double = 0.0,
    val data: Map<String, Double> = emptyMap(),
) {
    fun toDomain(): RetentionRecord = RetentionRecord(createdAt = createdAt, users = users, data = data)
}

@Serializable
private data class SponsorsRequest(
    val forceUpdate: Boolean = false,
    val instance: Boolean = false,
)

@Serializable
private data class SponsorsResponseDto(
    @SerialName("sponsor_data")
    val sponsorData: List<SponsorDto> = emptyList(),
)

@Serializable
private data class SponsorDto(
    val name: String = "",
    val image: String? = null,
    val website: String? = null,
    val profile: String = "",
) {
    fun toDomain(): Sponsor {
        return Sponsor(name = name, imageUrl = image, websiteUrl = website, profile = profile)
    }
}

@Serializable
private data class PublicNotesRequest(
    val local: Boolean = false,
    val reply: Boolean? = null,
    val renote: Boolean? = null,
    val withFiles: Boolean? = null,
    val poll: Boolean? = null,
    val limit: Int = 20,
    val sinceId: String? = null,
    val untilId: String? = null,
)

@Serializable
private data class DriveUsageDto(
    val capacity: Double = 0.0,
    val usage: Double = 0.0,
) {
    fun toDomain(): DriveUsage = DriveUsage(capacity = capacity, usage = usage)
}
