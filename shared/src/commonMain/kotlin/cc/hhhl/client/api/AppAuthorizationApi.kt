package cc.hhhl.client.api

import cc.hhhl.client.model.AppCreateInput
import cc.hhhl.client.model.AuthSession
import cc.hhhl.client.model.AuthSessionDetail
import cc.hhhl.client.model.AuthSessionUserKey
import cc.hhhl.client.model.AuthorizedApp
import cc.hhhl.client.model.MiAuthTokenInput
import cc.hhhl.client.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

interface AppAuthorizationApi {
    suspend fun loadCurrentApp(): AppAuthorizationResult<AuthorizedApp>

    suspend fun showApp(appId: String): AppAuthorizationResult<AuthorizedApp>

    suspend fun createApp(input: AppCreateInput): AppAuthorizationResult<AuthorizedApp>

    suspend fun loadMyApps(
        token: String,
        limit: Int = 20,
        offset: Int = 0,
    ): AppAuthorizationResult<List<AuthorizedApp>>

    suspend fun generateAuthSession(appSecret: String): AppAuthorizationResult<AuthSession>

    suspend fun showAuthSession(token: String): AppAuthorizationResult<AuthSessionDetail>

    suspend fun fetchAuthSessionUserKey(
        appSecret: String,
        token: String,
    ): AppAuthorizationResult<AuthSessionUserKey>

    suspend fun acceptAuthSession(
        token: String,
        sessionToken: String,
    ): AppAuthorizationActionResult

    suspend fun generateMiAuthToken(
        token: String,
        input: MiAuthTokenInput,
    ): AppAuthorizationResult<String>
}

sealed interface AppAuthorizationResult<out T> {
    data class Success<T>(val value: T) : AppAuthorizationResult<T>
    data object Unauthorized : AppAuthorizationResult<Nothing>
    data class ServerError(val statusCode: Int, val message: String) : AppAuthorizationResult<Nothing>
    data class NetworkError(val message: String) : AppAuthorizationResult<Nothing>
}

sealed interface AppAuthorizationActionResult {
    data object Success : AppAuthorizationActionResult
    data object Unauthorized : AppAuthorizationActionResult
    data class ServerError(val statusCode: Int, val message: String) : AppAuthorizationActionResult
    data class NetworkError(val message: String) : AppAuthorizationActionResult
}

class SharkeyAppAuthorizationApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultAppAuthorizationClient(),
) : AppAuthorizationApi {
    override suspend fun loadCurrentApp(): AppAuthorizationResult<AuthorizedApp> {
        return postJsonResult(
            endpoint = arrayOf("app", "current"),
            body = null,
        ) { response -> response.body<AppDto>().toDomain() }
    }

    override suspend fun showApp(appId: String): AppAuthorizationResult<AuthorizedApp> {
        val cleanAppId = appId.trim()
        if (cleanAppId.isEmpty()) return AppAuthorizationResult.ServerError(400, "应用 ID 不能为空")
        return postJsonResult(
            endpoint = arrayOf("app", "show"),
            body = AppShowRequest(appId = cleanAppId),
        ) { response -> response.body<AppDto>().toDomain() }
    }

    override suspend fun createApp(input: AppCreateInput): AppAuthorizationResult<AuthorizedApp> {
        val cleanInput = input.clean()
        cleanInput.validate()?.let { return AppAuthorizationResult.ServerError(400, it) }
        return postJsonResult(
            endpoint = arrayOf("app", "create"),
            body = cleanInput.toRequest(),
        ) { response -> response.body<AppDto>().toDomain() }
    }

    override suspend fun loadMyApps(
        token: String,
        limit: Int,
        offset: Int,
    ): AppAuthorizationResult<List<AuthorizedApp>> {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return AppAuthorizationResult.Unauthorized
        return postJsonResult(
            endpoint = arrayOf("my", "apps"),
            body = MyAppsRequest(
                limit = limit.coerceIn(1, 100),
                offset = offset.coerceAtLeast(0),
            ),
            bearerToken = cleanToken,
        ) { response -> response.body<List<AppDto>>().map { it.toDomain() } }
    }

    override suspend fun generateAuthSession(appSecret: String): AppAuthorizationResult<AuthSession> {
        val cleanSecret = appSecret.trim()
        if (cleanSecret.isEmpty()) return AppAuthorizationResult.ServerError(400, "应用密钥不能为空")
        return postJsonResult(
            endpoint = arrayOf("auth", "session", "generate"),
            body = AuthSessionGenerateRequest(appSecret = cleanSecret),
        ) { response -> response.body<AuthSessionDto>().toDomain() }
    }

    override suspend fun showAuthSession(token: String): AppAuthorizationResult<AuthSessionDetail> {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return AppAuthorizationResult.ServerError(400, "授权会话 token 不能为空")
        return postJsonResult(
            endpoint = arrayOf("auth", "session", "show"),
            body = AuthSessionShowRequest(token = cleanToken),
        ) { response -> response.body<AuthSessionDetailDto>().toDomain() }
    }

    override suspend fun fetchAuthSessionUserKey(
        appSecret: String,
        token: String,
    ): AppAuthorizationResult<AuthSessionUserKey> {
        val cleanSecret = appSecret.trim()
        val cleanToken = token.trim()
        if (cleanSecret.isEmpty()) return AppAuthorizationResult.ServerError(400, "应用密钥不能为空")
        if (cleanToken.isEmpty()) return AppAuthorizationResult.ServerError(400, "授权会话 token 不能为空")
        return postJsonResult(
            endpoint = arrayOf("auth", "session", "userkey"),
            body = AuthSessionUserKeyRequest(appSecret = cleanSecret, token = cleanToken),
        ) { response -> response.body<AuthSessionUserKeyDto>().toDomain() }
    }

    override suspend fun acceptAuthSession(
        token: String,
        sessionToken: String,
    ): AppAuthorizationActionResult {
        val cleanToken = token.trim()
        val cleanSessionToken = sessionToken.trim()
        if (cleanToken.isEmpty()) return AppAuthorizationActionResult.Unauthorized
        if (cleanSessionToken.isEmpty()) return AppAuthorizationActionResult.ServerError(400, "授权会话 token 不能为空")

        return try {
            val response = client.post(apiUrl("auth", "accept")) {
                header(HttpHeaders.Authorization, "Bearer $cleanToken")
                contentType(ContentType.Application.Json)
                setBody(AuthAcceptRequest(token = cleanSessionToken))
            }

            when {
                response.status.value in 200..299 -> AppAuthorizationActionResult.Success
                response.isSharkeyUnauthorized() -> AppAuthorizationActionResult.Unauthorized
                else -> AppAuthorizationActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppAuthorizationActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun generateMiAuthToken(
        token: String,
        input: MiAuthTokenInput,
    ): AppAuthorizationResult<String> {
        val cleanToken = token.trim()
        val cleanInput = input.clean()
        if (cleanToken.isEmpty()) return AppAuthorizationResult.Unauthorized
        cleanInput.validate()?.let { return AppAuthorizationResult.ServerError(400, it) }
        return postJsonResult(
            endpoint = arrayOf("miauth", "gen-token"),
            body = cleanInput.toRequestJson(),
            bearerToken = cleanToken,
        ) { response -> response.body<MiAuthTokenResponse>().token }
    }

    private suspend fun <T> postJsonResult(
        endpoint: Array<String>,
        body: Any?,
        bearerToken: String? = null,
        decode: suspend (HttpResponse) -> T,
    ): AppAuthorizationResult<T> {
        return try {
            val response = client.post(apiUrl(*endpoint)) {
                bearerToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            if (response.isSharkeyUnauthorized()) return AppAuthorizationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> AppAuthorizationResult.Success(decode(response))
                HttpStatusCode.Unauthorized -> AppAuthorizationResult.Unauthorized
                else -> AppAuthorizationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppAuthorizationResult.NetworkError(error.message ?: "网络请求失败")
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
private data class AppDto(
    val id: String,
    val name: String,
    val callbackUrl: String? = null,
    val permission: List<String> = emptyList(),
    val secret: String? = null,
    val isAuthorized: Boolean? = null,
) {
    fun toDomain(): AuthorizedApp {
        return AuthorizedApp(
            id = id,
            name = name,
            callbackUrl = callbackUrl?.takeIf { it.isNotBlank() },
            permissions = permission,
            secret = secret?.takeIf { it.isNotBlank() },
            isAuthorized = isAuthorized == true,
        )
    }
}

@Serializable
private data class AppShowRequest(val appId: String)

@Serializable
private data class AppCreateRequest(
    val name: String,
    val description: String,
    val permission: List<String>,
    val callbackUrl: String? = null,
)

@Serializable
private data class MyAppsRequest(
    val limit: Int,
    val offset: Int,
)

@Serializable
private data class AuthSessionGenerateRequest(val appSecret: String)

@Serializable
private data class AuthSessionDto(
    val token: String,
    val url: String,
) {
    fun toDomain(): AuthSession = AuthSession(token = token, url = url)
}

@Serializable
private data class AuthSessionShowRequest(val token: String)

@Serializable
private data class AuthSessionDetailDto(
    val id: String,
    val app: AppDto,
    val token: String,
) {
    fun toDomain(): AuthSessionDetail {
        return AuthSessionDetail(
            id = id,
            app = app.toDomain(),
            token = token,
        )
    }
}

@Serializable
private data class AuthSessionUserKeyRequest(
    val appSecret: String,
    val token: String,
)

@Serializable
private data class AuthSessionUserKeyDto(
    val accessToken: String,
    val user: AppAuthUserDto,
) {
    fun toDomain(): AuthSessionUserKey {
        return AuthSessionUserKey(
            accessToken = accessToken,
            user = user.toDomainUser(),
        )
    }
}

@Serializable
private data class AuthAcceptRequest(val token: String)

@Serializable
private data class MiAuthTokenResponse(val token: String)

@Serializable
private data class AppAuthUserDto(
    val id: String,
    val username: String,
    val host: String? = null,
    val name: String? = null,
    val description: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val notesCount: Int = 0,
    val isFollowing: Boolean = false,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
) {
    fun toDomainUser(): User {
        val displayName = name?.takeIf { it.isNotBlank() } ?: username
        return User(
            id = id,
            displayName = displayName,
            username = username,
            avatarInitial = displayName.avatarInitial(),
            bio = description.orEmpty(),
            followersCount = followersCount,
            followingCount = followingCount,
            notesCount = notesCount,
            isFollowing = isFollowing,
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
            bannerUrl = bannerUrl?.takeIf { it.isNotBlank() },
            host = host?.takeIf { it.isNotBlank() },
        )
    }
}

private fun AppCreateInput.clean(): AppCreateInput {
    return copy(
        name = name.trim(),
        description = description.trim(),
        permissions = permissions.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        callbackUrl = callbackUrl?.trim()?.takeIf { it.isNotBlank() },
    )
}

private fun AppCreateInput.validate(): String? {
    return when {
        name.isBlank() -> "应用名称不能为空"
        description.isBlank() -> "应用描述不能为空"
        permissions.isEmpty() -> "应用权限不能为空"
        else -> null
    }
}

private fun AppCreateInput.toRequest(): AppCreateRequest {
    return AppCreateRequest(
        name = name,
        description = description,
        permission = permissions,
        callbackUrl = callbackUrl,
    )
}

private fun MiAuthTokenInput.clean(): MiAuthTokenInput {
    return copy(
        session = session?.trim()?.takeIf { it.isNotBlank() },
        permissions = permissions.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        name = name?.trim()?.takeIf { it.isNotBlank() },
        description = description?.trim()?.takeIf { it.isNotBlank() },
        iconUrl = iconUrl?.trim()?.takeIf { it.isNotBlank() },
        grantees = grantees.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        rank = rank?.trim()?.takeIf { it.isNotBlank() },
    )
}

private fun MiAuthTokenInput.validate(): String? {
    return when {
        permissions.isEmpty() -> "令牌权限不能为空"
        else -> null
    }
}

private fun MiAuthTokenInput.toRequestJson() = buildJsonObject {
    put("session", session?.let(::JsonPrimitive) ?: JsonNull)
    put(
        "permission",
        buildJsonArray {
            permissions.forEach { add(JsonPrimitive(it)) }
        },
    )
    name?.let { put("name", JsonPrimitive(it)) }
    description?.let { put("description", JsonPrimitive(it)) }
    iconUrl?.let { put("iconUrl", JsonPrimitive(it)) }
    if (grantees.isNotEmpty()) {
        put(
            "grantees",
            buildJsonArray {
                grantees.forEach { add(JsonPrimitive(it)) }
            },
        )
    }
    rank?.let { put("rank", JsonPrimitive(it)) }
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private fun defaultAppAuthorizationClient(): HttpClient {
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
