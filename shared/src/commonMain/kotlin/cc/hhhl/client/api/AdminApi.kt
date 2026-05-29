package cc.hhhl.client.api

import cc.hhhl.client.model.AdminAbuseReport
import cc.hhhl.client.model.AdminAnnouncementSummary
import cc.hhhl.client.model.AdminInstanceSettings
import cc.hhhl.client.model.AdminRoleSummary
import cc.hhhl.client.model.AdminUserSummary
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface AdminApi {
    suspend fun searchUsers(token: String, query: String, limit: Int): AdminApiResult<List<AdminUserSummary>>
    suspend fun loadReports(token: String, limit: Int): AdminApiResult<List<AdminAbuseReport>>
    suspend fun resolveReport(token: String, reportId: String, forward: Boolean): AdminApiResult<Unit>
    suspend fun loadRoles(token: String): AdminApiResult<List<AdminRoleSummary>>
    suspend fun loadUserRoles(token: String, userId: String): AdminApiResult<List<AdminRoleSummary>>
    suspend fun loadAnnouncements(token: String): AdminApiResult<List<AdminAnnouncementSummary>>
    suspend fun createAnnouncement(token: String, title: String, text: String): AdminApiResult<AdminAnnouncementSummary?>
    suspend fun updateAnnouncement(token: String, announcementId: String, title: String, text: String): AdminApiResult<Unit>
    suspend fun deleteAnnouncement(token: String, announcementId: String): AdminApiResult<Unit>
    suspend fun loadInstanceSettings(token: String): AdminApiResult<AdminInstanceSettings>
}

sealed interface AdminApiResult<out T> {
    data class Success<T>(val value: T) : AdminApiResult<T>
    data object Unauthorized : AdminApiResult<Nothing>
    data class ServerError(val statusCode: Int, val message: String) : AdminApiResult<Nothing>
    data class NetworkError(val message: String) : AdminApiResult<Nothing>
}

class SharkeyAdminApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultAdminClient(),
) : AdminApi {
    override suspend fun searchUsers(token: String, query: String, limit: Int): AdminApiResult<List<AdminUserSummary>> {
        return postJson(
            endpoint = listOf("admin", "show-users"),
            body = AdminUsersRequest(
                i = token,
                query = query.takeIf { it.isNotBlank() },
                limit = limit.coerceIn(1, 100),
                sort = "+createdAt",
                state = "all",
            ),
            parse = { response -> response.body<JsonElement>().asArrayElements().map { it.toAdminUserSummary() } },
        )
    }

    override suspend fun loadReports(token: String, limit: Int): AdminApiResult<List<AdminAbuseReport>> {
        return postJson(
            endpoint = listOf("admin", "abuse-user-reports"),
            body = AdminReportsRequest(i = token, limit = limit.coerceIn(1, 100), state = "unresolved"),
            parse = { response -> response.body<JsonElement>().asArrayElements().map { it.toAdminAbuseReport() } },
        )
    }

    override suspend fun resolveReport(token: String, reportId: String, forward: Boolean): AdminApiResult<Unit> {
        return postJson(
            endpoint = listOf("admin", "resolve-abuse-user-report"),
            body = AdminResolveReportRequest(i = token, reportId = reportId, forward = forward),
            parse = { Unit },
        )
    }

    override suspend fun loadRoles(token: String): AdminApiResult<List<AdminRoleSummary>> {
        return postJson(
            endpoint = listOf("admin", "roles", "list"),
            body = AdminTokenRequest(i = token),
            parse = { response -> response.body<JsonElement>().asArrayElements().map { it.toAdminRoleSummary() } },
        )
    }

    override suspend fun loadUserRoles(token: String, userId: String): AdminApiResult<List<AdminRoleSummary>> {
        return postJson(
            endpoint = listOf("admin", "roles", "users"),
            body = AdminUserRolesRequest(i = token, userId = userId),
            parse = { response -> response.body<JsonElement>().asArrayElements().map { it.toAdminRoleSummary() } },
        )
    }

    override suspend fun loadAnnouncements(token: String): AdminApiResult<List<AdminAnnouncementSummary>> {
        return postJson(
            endpoint = listOf("admin", "announcements", "list"),
            body = AdminAnnouncementListRequest(i = token, limit = 50),
            parse = { response -> response.body<JsonElement>().asArrayElements().map { it.toAdminAnnouncement() } },
        )
    }

    override suspend fun createAnnouncement(
        token: String,
        title: String,
        text: String,
    ): AdminApiResult<AdminAnnouncementSummary?> {
        return postJson(
            endpoint = listOf("admin", "announcements", "create"),
            body = AdminAnnouncementSaveRequest(i = token, title = title, text = text),
            parse = { response -> response.body<JsonElement>().takeIf { it is JsonObject }?.toAdminAnnouncement() },
        )
    }

    override suspend fun updateAnnouncement(
        token: String,
        announcementId: String,
        title: String,
        text: String,
    ): AdminApiResult<Unit> {
        return postJson(
            endpoint = listOf("admin", "announcements", "update"),
            body = AdminAnnouncementUpdateRequest(i = token, id = announcementId, title = title, text = text),
            parse = { Unit },
        )
    }

    override suspend fun deleteAnnouncement(token: String, announcementId: String): AdminApiResult<Unit> {
        return postJson(
            endpoint = listOf("admin", "announcements", "delete"),
            body = AdminIdRequest(i = token, id = announcementId),
            parse = { Unit },
        )
    }

    override suspend fun loadInstanceSettings(token: String): AdminApiResult<AdminInstanceSettings> {
        return postJson(
            endpoint = listOf("admin", "meta"),
            body = AdminTokenRequest(i = token),
            parse = { response -> response.body<JsonElement>().toAdminInstanceSettings() },
        )
    }

    private suspend inline fun <reified B : Any, T> postJson(
        endpoint: List<String>,
        body: B,
        parse: suspend (HttpResponse) -> T,
    ): AdminApiResult<T> {
        return try {
            val response = client.post(apiUrl(endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            if (response.isSharkeyUnauthorized()) return AdminApiResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> AdminApiResult.Success(parse(response))
                HttpStatusCode.Unauthorized -> AdminApiResult.Unauthorized
                HttpStatusCode.Forbidden -> AdminApiResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
                else -> AdminApiResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AdminApiResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(endpoint: List<String>): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint.toTypedArray())
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

@Serializable
private data class AdminTokenRequest(val i: String)

@Serializable
private data class AdminUsersRequest(
    val i: String,
    val query: String? = null,
    val limit: Int,
    val sort: String,
    val state: String,
)

@Serializable
private data class AdminReportsRequest(
    val i: String,
    val limit: Int,
    val state: String,
)

@Serializable
private data class AdminResolveReportRequest(
    val i: String,
    val reportId: String,
    val forward: Boolean = false,
)

@Serializable
private data class AdminUserRolesRequest(
    val i: String,
    val userId: String,
)

@Serializable
private data class AdminAnnouncementListRequest(
    val i: String,
    val limit: Int,
)

@Serializable
private data class AdminAnnouncementSaveRequest(
    val i: String,
    val title: String,
    val text: String,
    val icon: String = "info",
    val display: String = "normal",
)

@Serializable
private data class AdminAnnouncementUpdateRequest(
    val i: String,
    val id: String,
    val title: String,
    val text: String,
    val icon: String = "info",
    val display: String = "normal",
)

@Serializable
private data class AdminIdRequest(
    val i: String,
    val id: String,
)

private fun JsonElement.asArrayElements(): List<JsonElement> {
    return when (this) {
        is JsonArray -> this.toList()
        is JsonObject -> this["items"]?.jsonArray?.toList()
            ?: this["users"]?.jsonArray?.toList()
            ?: this["reports"]?.jsonArray?.toList()
            ?: emptyList()
        else -> emptyList()
    }
}

private fun JsonElement.toAdminUserSummary(): AdminUserSummary {
    val obj = jsonObject
    val host = obj.string("host")
    val username = obj.string("username") ?: ""
    return AdminUserSummary(
        id = obj.string("id") ?: "",
        username = username,
        displayName = obj.string("name") ?: obj.string("displayName") ?: "@$username",
        host = host,
        avatarUrl = obj.string("avatarUrl"),
        isAdmin = obj.bool("isAdmin"),
        isModerator = obj.bool("isModerator"),
        isSuspended = obj.bool("isSuspended") || obj.bool("suspended"),
        isSilenced = obj.bool("isSilenced") || obj.bool("silenced"),
        createdAtLabel = obj.string("createdAt")?.toLocalCompactDateLabel().orEmpty(),
        notesCount = obj.int("notesCount"),
        followersCount = obj.int("followersCount"),
        followingCount = obj.int("followingCount"),
    )
}

private fun JsonElement.toAdminAbuseReport(): AdminAbuseReport {
    val obj = jsonObject
    val target = obj["targetUser"]?.jsonObject
    val reporter = obj["reporter"]?.jsonObject
    val assignee = obj["assignee"]?.jsonObject
    return AdminAbuseReport(
        id = obj.string("id") ?: "",
        comment = obj.string("comment") ?: "",
        reporterName = reporter?.displayUserName() ?: obj.string("reporterId").orEmpty(),
        targetUserId = target?.string("id") ?: obj.string("targetUserId").orEmpty(),
        targetUserName = target?.displayUserName() ?: obj.string("targetUserId").orEmpty(),
        assigneeName = assignee?.displayUserName(),
        resolved = obj.bool("resolved"),
        createdAtLabel = obj.string("createdAt")?.toLocalCompactDateLabel().orEmpty(),
    )
}

private fun JsonElement.toAdminRoleSummary(): AdminRoleSummary {
    val obj = jsonObject
    val permissionNames = obj["policies"]?.jsonObject?.keys.orEmpty() + obj["condFormula"]?.toString().orEmpty()
    return AdminRoleSummary(
        id = obj.string("id") ?: "",
        name = obj.string("name") ?: "Role",
        description = obj.string("description").orEmpty(),
        isModeratorRole = obj.bool("isModerator") || permissionNames.any { it.contains("moderator", ignoreCase = true) },
        isAdministratorRole = obj.bool("isAdministrator") || permissionNames.any { it.contains("admin", ignoreCase = true) },
        usersCount = obj.int("usersCount"),
    )
}

private fun JsonElement.toAdminAnnouncement(): AdminAnnouncementSummary {
    val obj = jsonObject
    return AdminAnnouncementSummary(
        id = obj.string("id") ?: "",
        title = obj.string("title") ?: "公告",
        text = obj.string("text") ?: "",
        icon = obj.string("icon") ?: "info",
        display = obj.string("display") ?: "normal",
        isActive = obj.boolOrNull("isActive") ?: obj.boolOrNull("active") ?: true,
        createdAtLabel = obj.string("createdAt")?.toLocalCompactDateLabel().orEmpty(),
        updatedAtLabel = obj.string("updatedAt")?.toLocalCompactDateLabel().orEmpty(),
    )
}

private fun JsonElement.toAdminInstanceSettings(): AdminInstanceSettings {
    val obj = jsonObject
    return AdminInstanceSettings(
        name = obj.string("name").orEmpty(),
        description = obj.string("description").orEmpty(),
        maintainerName = obj.string("maintainerName").orEmpty(),
        maintainerEmail = obj.string("maintainerEmail").orEmpty(),
        tosUrl = obj.string("tosUrl").orEmpty(),
        repositoryUrl = obj.string("repositoryUrl").orEmpty(),
        version = obj.string("version").orEmpty(),
        enableRegistration = obj.boolOrNull("enableRegistration") ?: obj.boolOrNull("disableRegistration")?.not(),
        emailRequiredForSignup = obj.boolOrNull("emailRequiredForSignup"),
    )
}

private fun JsonObject.displayUserName(): String {
    val username = string("username").orEmpty()
    return string("name") ?: string("displayName") ?: if (username.isNotBlank()) "@$username" else string("id").orEmpty()
}

private fun JsonObject.string(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.int(key: String): Int {
    return this[key]?.jsonPrimitive?.intOrNull ?: 0
}

private fun JsonObject.bool(key: String): Boolean {
    return boolOrNull(key) ?: false
}

private fun JsonObject.boolOrNull(key: String): Boolean? {
    return this[key]?.jsonPrimitive?.booleanOrNull
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultAdminClient(): HttpClient {
    return HttpClient {
        installDefaultHttpTimeouts()
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
