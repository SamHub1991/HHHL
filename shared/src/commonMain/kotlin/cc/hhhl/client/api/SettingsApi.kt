package cc.hhhl.client.api

import cc.hhhl.client.model.FilterSettings
import cc.hhhl.client.model.SettingsManagementItem
import cc.hhhl.client.model.SettingsManagementItemAction
import cc.hhhl.client.model.SettingsManagementAction
import cc.hhhl.client.model.SettingsManagementSection
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.SettingsWebhookDetail
import cc.hhhl.client.model.SettingsWebhookCreateInput
import cc.hhhl.client.model.SettingsWebhookUpdateInput
import cc.hhhl.client.model.IntegrationSettings
import cc.hhhl.client.model.NotificationSettings
import cc.hhhl.client.model.PrivacySettings
import cc.hhhl.client.model.SecuritySettings
import cc.hhhl.client.model.SettingsPreferenceUpdate
import cc.hhhl.client.model.SettingsPreferences
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
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface SettingsApi {
    suspend fun loadPreferences(token: String): SettingsPreferencesResult

    suspend fun updatePreferences(
        token: String,
        update: SettingsPreferenceUpdate,
    ): SettingsPreferencesResult

    suspend fun loadTwoFactorStatus(token: String): SettingsCapabilityResult

    suspend fun loadPasskeys(token: String): SettingsCapabilityResult

    suspend fun loadSigninHistory(token: String): SettingsCapabilityResult

    suspend fun loadApiTokens(token: String): SettingsCapabilityResult

    suspend fun loadInvites(token: String): SettingsCapabilityResult

    suspend fun loadSharedAccess(token: String): SettingsCapabilityResult

    suspend fun loginSharedAccess(
        token: String,
        grantId: String,
    ): SettingsSharedAccessLoginResult

    suspend fun loadWebhooks(token: String): SettingsCapabilityResult

    suspend fun loadWebhook(
        token: String,
        webhookId: String,
    ): SettingsWebhookDetailResult

    suspend fun loadAuthorizedApps(token: String): SettingsCapabilityResult

    suspend fun loadManagementSection(
        token: String,
        key: SettingsManagementSectionKey,
    ): SettingsManagementSectionResult

    suspend fun revokeApiToken(
        token: String,
        tokenId: String,
    ): SettingsManagementMutationResult

    suspend fun createInvite(token: String): SettingsManagementMutationResult

    suspend fun deleteInvite(
        token: String,
        inviteId: String,
    ): SettingsManagementMutationResult

    suspend fun deleteWebhook(
        token: String,
        webhookId: String,
    ): SettingsManagementMutationResult

    suspend fun updateWebhookActive(
        token: String,
        webhookId: String,
        active: Boolean,
    ): SettingsManagementMutationResult

    suspend fun testWebhook(
        token: String,
        webhookId: String,
        type: String = "note",
    ): SettingsManagementMutationResult

    suspend fun createWebhook(
        token: String,
        input: SettingsWebhookCreateInput,
    ): SettingsManagementMutationResult

    suspend fun updateWebhook(
        token: String,
        webhookId: String,
        input: SettingsWebhookUpdateInput,
    ): SettingsManagementMutationResult
}

sealed interface SettingsPreferencesResult {
    data class Success(val preferences: SettingsPreferences) : SettingsPreferencesResult

    data object Unauthorized : SettingsPreferencesResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : SettingsPreferencesResult

    data class NetworkError(val message: String) : SettingsPreferencesResult
}

sealed interface SettingsCapabilityResult {
    data object Available : SettingsCapabilityResult

    data class Count(
        val total: Int,
        val active: Int? = null,
        val latestLabel: String? = null,
    ) : SettingsCapabilityResult

    data class Unsupported(val message: String) : SettingsCapabilityResult
}

sealed interface SettingsManagementSectionResult {
    data class Success(val section: SettingsManagementSection) : SettingsManagementSectionResult

    data object Unauthorized : SettingsManagementSectionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : SettingsManagementSectionResult

    data class NetworkError(val message: String) : SettingsManagementSectionResult
}

sealed interface SettingsManagementMutationResult {
    data object Success : SettingsManagementMutationResult

    data object Unauthorized : SettingsManagementMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : SettingsManagementMutationResult

    data class NetworkError(val message: String) : SettingsManagementMutationResult
}

sealed interface SettingsWebhookDetailResult {
    data class Success(val webhook: SettingsWebhookDetail) : SettingsWebhookDetailResult

    data object Unauthorized : SettingsWebhookDetailResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : SettingsWebhookDetailResult

    data class NetworkError(val message: String) : SettingsWebhookDetailResult
}

sealed interface SettingsSharedAccessLoginResult {
    data class Success(
        val userId: String,
        val token: String,
    ) : SettingsSharedAccessLoginResult

    data object Unauthorized : SettingsSharedAccessLoginResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : SettingsSharedAccessLoginResult

    data class NetworkError(val message: String) : SettingsSharedAccessLoginResult
}

class SharkeySettingsApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultSettingsClient(),
    private val displayTimeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SettingsApi {
    override suspend fun loadTwoFactorStatus(token: String): SettingsCapabilityResult {
        return when (val result = loadPreferences(token)) {
            is SettingsPreferencesResult.Success -> result.preferences.security.twoFactorEnabled?.let {
                if (it) SettingsCapabilityResult.Available else SettingsCapabilityResult.Count(total = 0)
            } ?: SettingsCapabilityResult.Unsupported("当前实例未返回双重验证状态")
            SettingsPreferencesResult.Unauthorized -> SettingsCapabilityResult.Unsupported("登录已失效，请重新登录")
            is SettingsPreferencesResult.ServerError -> SettingsCapabilityResult.Unsupported(result.message)
            is SettingsPreferencesResult.NetworkError -> SettingsCapabilityResult.Unsupported(result.message)
        }
    }

    override suspend fun loadPasskeys(token: String): SettingsCapabilityResult {
        return when (val result = loadPreferences(token)) {
            is SettingsPreferencesResult.Success -> result.preferences.security.passkeysEnabled?.let {
                if (it) SettingsCapabilityResult.Available else SettingsCapabilityResult.Count(total = 0)
            } ?: SettingsCapabilityResult.Unsupported("当前实例未返回 Passkey 状态")
            SettingsPreferencesResult.Unauthorized -> SettingsCapabilityResult.Unsupported("登录已失效，请重新登录")
            is SettingsPreferencesResult.ServerError -> SettingsCapabilityResult.Unsupported(result.message)
            is SettingsPreferencesResult.NetworkError -> SettingsCapabilityResult.Unsupported(result.message)
        }
    }

    override suspend fun loadApiTokens(token: String): SettingsCapabilityResult {
        return loadCapabilityCount(
            token = token,
            endpoint = arrayOf("i", "apps"),
            body = buildJsonObject {
                put("limit", JsonPrimitive(100))
                put("sort", JsonPrimitive("-lastUsedAt"))
            },
        )
    }

    override suspend fun loadInvites(token: String): SettingsCapabilityResult {
        return when (val list = loadCapabilityCount(token = token, endpoint = arrayOf("invite", "list"))) {
            is SettingsCapabilityResult.Count -> when (val limit = loadInviteLimitValue(token)) {
                null -> list
                else -> list.copy(active = limit)
            }
            else -> list
        }
    }

    override suspend fun loadSharedAccess(token: String): SettingsCapabilityResult {
        return loadCapabilityCount(
            token = token,
            endpoint = arrayOf("i", "shared-access", "list"),
            body = buildJsonObject {
                put("limit", JsonPrimitive(100))
            },
        )
    }

    override suspend fun loginSharedAccess(
        token: String,
        grantId: String,
    ): SettingsSharedAccessLoginResult {
        val cleanToken = token.trim()
        val cleanGrantId = grantId.trim()
        if (cleanToken.isEmpty()) return SettingsSharedAccessLoginResult.Unauthorized
        if (cleanGrantId.isEmpty()) {
            return SettingsSharedAccessLoginResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "共享访问 ID 不能为空",
            )
        }

        return try {
            val response = client.post(apiUrl("i", "shared-access", "login")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("grantId", JsonPrimitive(cleanGrantId))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return SettingsSharedAccessLoginResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> {
                    val payload = response.body<JsonObject>()
                    val userId = payload["userId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val sharedToken = payload["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (userId.isBlank() || sharedToken.isBlank()) {
                        SettingsSharedAccessLoginResult.ServerError(
                            statusCode = HttpStatusCode.InternalServerError.value,
                            message = "服务器未返回完整的共享访问登录信息",
                        )
                    } else {
                        SettingsSharedAccessLoginResult.Success(
                            userId = userId,
                            token = sharedToken,
                        )
                    }
                }
                HttpStatusCode.Unauthorized -> SettingsSharedAccessLoginResult.Unauthorized
                else -> SettingsSharedAccessLoginResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsSharedAccessLoginResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadWebhooks(token: String): SettingsCapabilityResult {
        return loadCapabilityCount(
            token = token,
            endpoint = arrayOf("i", "webhooks", "list"),
            activeSelector = { element ->
                runCatching { element.jsonObject["active"]?.jsonPrimitive?.content == "true" }.getOrDefault(false)
            },
        )
    }

    override suspend fun loadWebhook(
        token: String,
        webhookId: String,
    ): SettingsWebhookDetailResult {
        val cleanToken = token.trim()
        val cleanWebhookId = webhookId.trim()
        if (cleanToken.isEmpty()) return SettingsWebhookDetailResult.Unauthorized
        if (cleanWebhookId.isEmpty()) {
            return SettingsWebhookDetailResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook ID 不能为空",
            )
        }

        return try {
            val response = client.post(apiUrl("i", "webhooks", "show")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("webhookId", JsonPrimitive(cleanWebhookId))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return SettingsWebhookDetailResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> SettingsWebhookDetailResult.Success(response.body<JsonObject>().toWebhookDetail())
                HttpStatusCode.Unauthorized -> SettingsWebhookDetailResult.Unauthorized
                else -> SettingsWebhookDetailResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsWebhookDetailResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadAuthorizedApps(token: String): SettingsCapabilityResult {
        return loadCapabilityCount(
            token = token,
            endpoint = arrayOf("i", "authorized-apps"),
            body = buildJsonObject {
                put("limit", JsonPrimitive(100))
                put("offset", JsonPrimitive(0))
                put("sort", JsonPrimitive("desc"))
            },
        )
    }

    override suspend fun loadSigninHistory(token: String): SettingsCapabilityResult {
        return loadCapabilityCount(
            token = token,
            endpoint = arrayOf("i", "signin-history"),
            body = buildJsonObject {
                put("limit", JsonPrimitive(100))
            },
            latestLabelSelector = { element ->
                runCatching {
                    element.jsonObject["createdAt"]?.jsonPrimitive?.content?.toLocalCompactDateLabel(displayTimeZone)
                }.getOrNull()
            },
        )
    }

    override suspend fun loadPreferences(token: String): SettingsPreferencesResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return SettingsPreferencesResult.Unauthorized

        return try {
            val response = client.post(apiUrl("i")) {
                contentType(ContentType.Application.Json)
                setBody(SettingsReadRequest(i = cleanToken))
            }

            if (response.isSharkeyUnauthorized()) return SettingsPreferencesResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> SettingsPreferencesResult.Success(response.body<SettingsAccountDto>().toDomain())
                HttpStatusCode.Unauthorized -> SettingsPreferencesResult.Unauthorized
                else -> SettingsPreferencesResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsPreferencesResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadManagementSection(
        token: String,
        key: SettingsManagementSectionKey,
    ): SettingsManagementSectionResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return SettingsManagementSectionResult.Unauthorized

        return try {
            when (key) {
                SettingsManagementSectionKey.ApiTokens -> loadApiTokensSection(cleanToken)
                SettingsManagementSectionKey.Invites -> loadInvitesSection(cleanToken)
                SettingsManagementSectionKey.SharedAccess -> loadSharedAccessSection(cleanToken)
                SettingsManagementSectionKey.AuthorizedApps -> loadAuthorizedAppsSection(cleanToken)
                SettingsManagementSectionKey.Webhooks -> loadWebhooksSection(cleanToken)
                SettingsManagementSectionKey.SigninHistory -> loadSigninHistorySection(cleanToken)
                SettingsManagementSectionKey.AvatarDecorations -> SettingsManagementSectionResult.ServerError(
                    statusCode = HttpStatusCode.BadRequest.value,
                    message = "头像挂件由头像挂件接口加载",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsManagementSectionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun revokeApiToken(
        token: String,
        tokenId: String,
    ): SettingsManagementMutationResult {
        val cleanToken = token.trim()
        val cleanTokenId = tokenId.trim()
        if (cleanToken.isEmpty()) return SettingsManagementMutationResult.Unauthorized
        if (cleanTokenId.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "令牌 ID 不能为空",
            )
        }

        return try {
            val response = client.post(apiUrl("i", "revoke-token")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("tokenId", JsonPrimitive(cleanTokenId))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return SettingsManagementMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.NoContent -> SettingsManagementMutationResult.Success
                else -> SettingsManagementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsManagementMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun createInvite(token: String): SettingsManagementMutationResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return SettingsManagementMutationResult.Unauthorized

        return postSimpleMutation(
            endpoint = arrayOf("invite", "create"),
            body = buildJsonObject { put("i", JsonPrimitive(cleanToken)) },
        )
    }

    override suspend fun deleteInvite(
        token: String,
        inviteId: String,
    ): SettingsManagementMutationResult {
        val cleanToken = token.trim()
        val cleanInviteId = inviteId.trim()
        if (cleanToken.isEmpty()) return SettingsManagementMutationResult.Unauthorized
        if (cleanInviteId.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "邀请码 ID 不能为空",
            )
        }

        return postSimpleMutation(
            endpoint = arrayOf("invite", "delete"),
            body = buildJsonObject {
                put("i", JsonPrimitive(cleanToken))
                put("inviteId", JsonPrimitive(cleanInviteId))
            },
        )
    }

    override suspend fun deleteWebhook(
        token: String,
        webhookId: String,
    ): SettingsManagementMutationResult {
        val cleanToken = token.trim()
        val cleanWebhookId = webhookId.trim()
        if (cleanToken.isEmpty()) return SettingsManagementMutationResult.Unauthorized
        if (cleanWebhookId.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook ID 不能为空",
            )
        }

        return try {
            val response = client.post(apiUrl("i", "webhooks", "delete")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("webhookId", JsonPrimitive(cleanWebhookId))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return SettingsManagementMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.NoContent -> SettingsManagementMutationResult.Success
                else -> SettingsManagementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsManagementMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postSimpleMutation(
        endpoint: Array<String>,
        body: JsonElement,
    ): SettingsManagementMutationResult {
        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            if (response.isSharkeyUnauthorized()) return SettingsManagementMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.NoContent -> SettingsManagementMutationResult.Success
                else -> SettingsManagementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsManagementMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updateWebhookActive(
        token: String,
        webhookId: String,
        active: Boolean,
    ): SettingsManagementMutationResult {
        val cleanToken = token.trim()
        val cleanWebhookId = webhookId.trim()
        if (cleanToken.isEmpty()) return SettingsManagementMutationResult.Unauthorized
        if (cleanWebhookId.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook ID 不能为空",
            )
        }

        return try {
            val response = client.post(apiUrl("i", "webhooks", "update")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("webhookId", JsonPrimitive(cleanWebhookId))
                        put("active", JsonPrimitive(active))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return SettingsManagementMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.NoContent -> SettingsManagementMutationResult.Success
                else -> SettingsManagementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsManagementMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun testWebhook(
        token: String,
        webhookId: String,
        type: String,
    ): SettingsManagementMutationResult {
        val cleanToken = token.trim()
        val cleanWebhookId = webhookId.trim()
        val cleanType = type.trim().ifBlank { "note" }
        if (cleanToken.isEmpty()) return SettingsManagementMutationResult.Unauthorized
        if (cleanWebhookId.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook ID 不能为空",
            )
        }

        return try {
            val response = client.post(apiUrl("i", "webhooks", "test")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("webhookId", JsonPrimitive(cleanWebhookId))
                        put("type", JsonPrimitive(cleanType))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return SettingsManagementMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.NoContent -> SettingsManagementMutationResult.Success
                else -> SettingsManagementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsManagementMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun createWebhook(
        token: String,
        input: SettingsWebhookCreateInput,
    ): SettingsManagementMutationResult {
        val cleanToken = token.trim()
        val cleanName = input.name.trim()
        val cleanUrl = input.url.trim()
        val events = input.events.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanToken.isEmpty()) return SettingsManagementMutationResult.Unauthorized
        if (cleanName.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook 名称不能为空",
            )
        }
        if (cleanUrl.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook URL 不能为空",
            )
        }
        if (events.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "至少选择一个触发事件",
            )
        }

        return try {
            val response = client.post(apiUrl("i", "webhooks", "create")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("name", JsonPrimitive(cleanName))
                        put("url", JsonPrimitive(cleanUrl))
                        put("secret", JsonPrimitive(input.secret.trim()))
                        put("on", JsonArray(events.map { JsonPrimitive(it) }))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return SettingsManagementMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.NoContent -> SettingsManagementMutationResult.Success
                else -> SettingsManagementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsManagementMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updateWebhook(
        token: String,
        webhookId: String,
        input: SettingsWebhookUpdateInput,
    ): SettingsManagementMutationResult {
        val cleanToken = token.trim()
        val cleanWebhookId = webhookId.trim()
        val cleanName = input.name.trim()
        val cleanUrl = input.url.trim()
        val events = input.events.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanToken.isEmpty()) return SettingsManagementMutationResult.Unauthorized
        if (cleanWebhookId.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook ID 不能为空",
            )
        }
        if (cleanName.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook 名称不能为空",
            )
        }
        if (cleanUrl.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "Webhook URL 不能为空",
            )
        }
        if (events.isEmpty()) {
            return SettingsManagementMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "至少选择一个触发事件",
            )
        }

        return try {
            val response = client.post(apiUrl("i", "webhooks", "update")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("webhookId", JsonPrimitive(cleanWebhookId))
                        put("name", JsonPrimitive(cleanName))
                        put("url", JsonPrimitive(cleanUrl))
                        input.secret?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            put("secret", JsonPrimitive(it))
                        }
                        put("on", JsonArray(events.map { JsonPrimitive(it) }))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return SettingsManagementMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.NoContent -> SettingsManagementMutationResult.Success
                else -> SettingsManagementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsManagementMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updatePreferences(
        token: String,
        update: SettingsPreferenceUpdate,
    ): SettingsPreferencesResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return SettingsPreferencesResult.Unauthorized

        return try {
            val response = client.post(apiUrl("i", "update")) {
                contentType(ContentType.Application.Json)
                setBody(SettingsUpdateRequest.fromUpdate(cleanToken, update))
            }

            if (response.isSharkeyUnauthorized()) return SettingsPreferencesResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> SettingsPreferencesResult.Success(response.body<SettingsAccountDto>().toDomain())
                HttpStatusCode.Unauthorized -> SettingsPreferencesResult.Unauthorized
                else -> SettingsPreferencesResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsPreferencesResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun loadCapabilityCount(
        token: String,
        endpoint: Array<String>,
        body: JsonElement = buildJsonObject {},
        activeSelector: ((JsonElement) -> Boolean)? = null,
        latestLabelSelector: ((JsonElement) -> String?)? = null,
    ): SettingsCapabilityResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return SettingsCapabilityResult.Unsupported("未登录")

        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body.withToken(cleanToken))
            }

            if (response.isSharkeyUnauthorized()) {
                return SettingsCapabilityResult.Unsupported("登录已失效，请重新登录")
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val items = response.body<JsonArray>()
                    SettingsCapabilityResult.Count(
                        total = items.size,
                        active = activeSelector?.let { selector -> items.count(selector) },
                        latestLabel = latestLabelSelector?.let { selector ->
                            items.firstNotNullOfOrNull(selector)
                        },
                    )
                }
                HttpStatusCode.NotFound -> SettingsCapabilityResult.Unsupported("当前实例不支持此接口")
                else -> SettingsCapabilityResult.Unsupported(
                    response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SettingsCapabilityResult.Unsupported(error.message ?: "网络请求失败")
        }
    }

    private suspend fun loadApiTokensSection(token: String): SettingsManagementSectionResult {
        val response = postManagementRequest(
            endpoint = arrayOf("i", "apps"),
            body = buildJsonObject {
                put("limit", JsonPrimitive(100))
                put("sort", JsonPrimitive("-lastUsedAt"))
            }.withToken(token),
        ) ?: return SettingsManagementSectionResult.Unauthorized

        val items = response.body<JsonArray>().map { element ->
            val obj = element.jsonObject
            val granteeNames = obj["grantees"]?.jsonArray
                ?.mapNotNull { user -> user.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: user.jsonObject["username"]?.jsonPrimitive?.contentOrNull }
                ?.filter { it.isNotBlank() }
            SettingsManagementItem(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                title = obj["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { "未命名令牌" } ?: "未命名令牌",
                subtitle = granteeNames?.joinToString("、").orEmpty(),
                meta = buildList {
                    obj["lastUsedAt"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                        add("最近 ${it.toLocalCompactDateLabel(displayTimeZone)}")
                    }
                    obj["createdAt"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                        add("创建于 ${it.toLocalCompactDateLabel(displayTimeZone)}")
                    }
                }.joinToString(" · "),
                permissions = obj["permission"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                badges = listOfNotNull(obj["rank"]?.jsonPrimitive?.contentOrNull?.toRankLabel()),
                actions = listOf(
                    SettingsManagementItemAction(
                        type = SettingsManagementAction.RevokeToken,
                        label = "撤销",
                        enabled = true,
                        destructive = true,
                    ),
                ),
            )
        }

        return SettingsManagementSectionResult.Success(
            SettingsManagementSection(
                key = SettingsManagementSectionKey.ApiTokens,
                title = "访问令牌",
                description = "已创建的 API 令牌和最近使用情况",
                items = items,
                supportsPrimaryAction = true,
            ),
        )
    }

    private suspend fun loadInvitesSection(token: String): SettingsManagementSectionResult {
        val response = postManagementRequest(
            endpoint = arrayOf("invite", "list"),
            body = buildJsonObject { put("i", JsonPrimitive(token)) },
        ) ?: return SettingsManagementSectionResult.Unauthorized

        val items = response.body<JsonArray>().map { element ->
            val obj = element.jsonObject
            val usedBy = obj["usedBy"]?.jsonObject
            val usedAt = obj["usedAt"]?.jsonPrimitive?.contentOrNull
            val expiresAt = obj["expiresAt"]?.jsonPrimitive?.contentOrNull
            val code = obj["code"]?.jsonPrimitive?.contentOrNull
                ?: obj["inviteCode"]?.jsonPrimitive?.contentOrNull
                ?: obj["token"]?.jsonPrimitive?.contentOrNull
                ?: obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            SettingsManagementItem(
                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: code,
                title = code.ifBlank { "邀请码" },
                subtitle = usedBy?.settingsDisplayUserName()?.takeIf { it.isNotBlank() }?.let { "已由 $it 使用" }
                    ?: "未使用",
                meta = buildList {
                    obj["createdAt"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                        add("创建于 ${it.toLocalCompactDateLabel(displayTimeZone)}")
                    }
                    usedAt?.takeIf { it.isNotBlank() }?.let {
                        add("使用于 ${it.toLocalCompactDateLabel(displayTimeZone)}")
                    }
                    expiresAt?.takeIf { it.isNotBlank() }?.let {
                        add("过期 ${it.toLocalCompactDateLabel(displayTimeZone)}")
                    }
                }.joinToString(" · "),
                badges = listOf(if (usedAt.isNullOrBlank() && usedBy == null) "可用" else "已使用"),
                actions = if (usedAt.isNullOrBlank()) {
                    listOf(
                        SettingsManagementItemAction(
                            type = SettingsManagementAction.DeleteInvite,
                            label = "删除",
                            enabled = true,
                            destructive = true,
                        ),
                    )
                } else {
                    emptyList()
                },
            )
        }

        val remaining = loadInviteLimitValue(token)
        return SettingsManagementSectionResult.Success(
            SettingsManagementSection(
                key = SettingsManagementSectionKey.Invites,
                title = "邀请码",
                description = buildList {
                    add("创建和管理当前账号的邀请码")
                    remaining?.let { add("剩余额度 $it") }
                }.joinToString(" · "),
                items = items,
                supportsPrimaryAction = remaining == null || remaining > 0,
            ),
        )
    }

    private suspend fun loadSharedAccessSection(token: String): SettingsManagementSectionResult {
        val response = postManagementRequest(
            endpoint = arrayOf("i", "shared-access", "list"),
            body = buildJsonObject { put("limit", JsonPrimitive(100)) }.withToken(token),
        ) ?: return SettingsManagementSectionResult.Unauthorized

        val items = response.body<JsonArray>().map { element ->
            val obj = element.jsonObject
            val user = obj["user"]?.jsonObject
            SettingsManagementItem(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                title = user?.settingsDisplayUserName().orEmpty().ifBlank { "共享访问" },
                subtitle = listOfNotNull(
                    user?.get("username")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { "@$it" },
                    user?.get("host")?.jsonPrimitive?.contentOrNull,
                ).joinToString("@"),
                meta = obj["permissions"]?.jsonArray?.size?.let { "$it 项权限" }.orEmpty(),
                permissions = obj["permissions"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                badges = listOfNotNull(obj["rank"]?.jsonPrimitive?.contentOrNull?.toRankLabel()),
                actions = listOf(
                    SettingsManagementItemAction(
                        type = SettingsManagementAction.LoginSharedAccess,
                        label = "进入",
                        enabled = true,
                    ),
                ),
            )
        }

        return SettingsManagementSectionResult.Success(
            SettingsManagementSection(
                key = SettingsManagementSectionKey.SharedAccess,
                title = "共享访问",
                description = "通过共享访问获得权限的用户",
                items = items,
            ),
        )
    }

    private suspend fun loadAuthorizedAppsSection(token: String): SettingsManagementSectionResult {
        val response = postManagementRequest(
            endpoint = arrayOf("i", "authorized-apps"),
            body = buildJsonObject {
                put("limit", JsonPrimitive(100))
                put("offset", JsonPrimitive(0))
                put("sort", JsonPrimitive("desc"))
            }.withToken(token),
        ) ?: return SettingsManagementSectionResult.Unauthorized

        val items = response.body<JsonArray>().map { element ->
            val obj = element.jsonObject
            SettingsManagementItem(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                title = obj["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { "未命名应用" } ?: "未命名应用",
                subtitle = obj["callbackUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                meta = if (obj["isAuthorized"]?.jsonPrimitive?.content == "true") "已授权" else "未确认授权",
                permissions = obj["permission"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                badges = listOfNotNull(
                    obj["isAuthorized"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()?.let { if (it) "已授权" else "未确认" },
                ),
            )
        }

        return SettingsManagementSectionResult.Success(
            SettingsManagementSection(
                key = SettingsManagementSectionKey.AuthorizedApps,
                title = "已授权应用",
                description = "外部应用持有的权限范围",
                items = items,
            ),
        )
    }

    private suspend fun loadWebhooksSection(token: String): SettingsManagementSectionResult {
        val response = postManagementRequest(
            endpoint = arrayOf("i", "webhooks", "list"),
            body = buildJsonObject { put("i", JsonPrimitive(token)) },
        ) ?: return SettingsManagementSectionResult.Unauthorized

        val items = response.body<JsonArray>().map { element ->
            val obj = element.jsonObject
            val latestStatus = obj["latestStatus"]?.jsonPrimitive?.intOrNull
            val latestSentAt = obj["latestSentAt"]?.jsonPrimitive?.contentOrNull
            SettingsManagementItem(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                title = obj["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { "Webhook" } ?: "Webhook",
                subtitle = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                meta = buildList<String> {
                    latestStatus?.let { add("最近状态 $it") }
                    latestSentAt?.takeIf { it.isNotBlank() }?.let {
                        add("最近推送 ${it.toLocalCompactDateLabel(displayTimeZone)}")
                    }
                }.joinToString(" · "),
                permissions = obj["on"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                badges = buildList<String> {
                    add(if (obj["active"]?.jsonPrimitive?.content == "true") "启用" else "停用")
                },
                actions = buildList {
                    val active = obj["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true
                    add(
                        SettingsManagementItemAction(
                            type = SettingsManagementAction.EditWebhook,
                            label = "编辑",
                            enabled = true,
                        ),
                    )
                    add(
                        SettingsManagementItemAction(
                            type = if (active) {
                                SettingsManagementAction.DisableWebhook
                            } else {
                                SettingsManagementAction.EnableWebhook
                            },
                            label = if (active) "停用" else "启用",
                            enabled = true,
                        ),
                    )
                    add(
                        SettingsManagementItemAction(
                            type = SettingsManagementAction.TestWebhook,
                            label = "发送测试",
                            enabled = true,
                        ),
                    )
                    add(
                        SettingsManagementItemAction(
                            type = SettingsManagementAction.DeleteWebhook,
                            label = "删除",
                            enabled = true,
                            destructive = true,
                        ),
                    )
                },
            )
        }

        return SettingsManagementSectionResult.Success(
            SettingsManagementSection(
                key = SettingsManagementSectionKey.Webhooks,
                title = "Webhook",
                description = "出站事件回调和最近推送状态",
                items = items,
                supportsPrimaryAction = true,
            ),
        )
    }

    private suspend fun loadSigninHistorySection(token: String): SettingsManagementSectionResult {
        val response = postManagementRequest(
            endpoint = arrayOf("i", "signin-history"),
            body = buildJsonObject { put("limit", JsonPrimitive(100)) }.withToken(token),
        ) ?: return SettingsManagementSectionResult.Unauthorized

        val items = response.body<JsonArray>().map { element ->
            val obj = element.jsonObject
            val ip = obj["ip"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val headers = obj["headers"]?.jsonObject
            val userAgent = headers?.get("user-agent")?.jsonPrimitive?.contentOrNull
                ?: headers?.get("User-Agent")?.jsonPrimitive?.contentOrNull
            SettingsManagementItem(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                title = obj["createdAt"]?.jsonPrimitive?.contentOrNull?.toLocalCompactDateLabel(displayTimeZone)
                    ?: "登录记录",
                subtitle = ip,
                meta = listOfNotNull(
                    obj["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()?.let { if (it) "成功" else "失败" },
                    userAgent?.takeIf { it.isNotBlank() }?.take(72),
                ).joinToString(" · "),
            )
        }

        return SettingsManagementSectionResult.Success(
            SettingsManagementSection(
                key = SettingsManagementSectionKey.SigninHistory,
                title = "登录记录",
                description = "最近登录时间、IP 和结果",
                items = items,
            ),
        )
    }

    private suspend fun postManagementRequest(
        endpoint: Array<String>,
        body: JsonElement,
    ): HttpResponse? {
        val response = client.post(apiUrl(*endpoint)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.isSharkeyUnauthorized()) return null
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException(response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
        }
        return response
    }

    private suspend fun loadInviteLimitValue(token: String): Int? {
        return runCatching {
            val response = postManagementRequest(
                endpoint = arrayOf("invite", "limit"),
                body = buildJsonObject { put("i", JsonPrimitive(token)) },
            ) ?: return null
            val element = response.body<JsonElement>()
            when (element) {
                is JsonPrimitive -> element.intOrNull
                is JsonObject -> element["remaining"]?.jsonPrimitive?.intOrNull
                    ?: element["limit"]?.jsonPrimitive?.intOrNull
                    ?: element["count"]?.jsonPrimitive?.intOrNull
                    ?: element["available"]?.jsonPrimitive?.intOrNull
                else -> null
            }
        }.getOrNull()
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

private fun JsonElement.withToken(token: String): JsonElement {
    val existing = runCatching { jsonObject }.getOrNull()
    return buildJsonObject {
        existing?.entries?.forEach { entry -> put(entry.key, entry.value) }
        put("i", JsonPrimitive(token))
    }
}

private fun JsonObject.settingsDisplayUserName(): String {
    val username = this["username"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return this["name"]?.jsonPrimitive?.contentOrNull
        ?: this["displayName"]?.jsonPrimitive?.contentOrNull
        ?: if (username.isNotBlank()) "@$username" else this["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.toWebhookDetail(): SettingsWebhookDetail {
    return SettingsWebhookDetail(
        id = this["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        name = this["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { "Webhook" } ?: "Webhook",
        url = this["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        secret = this["secret"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        events = this["on"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty().ifEmpty { listOf("note") },
        active = this["active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
    )
}

private fun String.toRankLabel(): String {
    return when (this) {
        "admin" -> "管理员"
        "mod" -> "审核员"
        "user" -> "用户"
        else -> this
    }
}

@Serializable
private data class SettingsReadRequest(
    val i: String,
)

@Serializable
private data class SettingsUpdateRequest(
    val i: String,
    val isLocked: Boolean? = null,
    val autoAcceptFollowed: Boolean? = null,
    val noCrawle: Boolean? = null,
    val preventAiLearning: Boolean? = null,
    val publicReactions: Boolean? = null,
    val mutingNotificationTypes: List<String>? = null,
    val mutedWords: List<List<String>>? = null,
    val hardMutedWords: List<List<String>>? = null,
    val mutedInstances: List<String>? = null,
) {
    companion object {
        fun fromUpdate(
            token: String,
            update: SettingsPreferenceUpdate,
        ): SettingsUpdateRequest {
            return SettingsUpdateRequest(
                i = token,
                isLocked = update.privacy?.isLocked,
                autoAcceptFollowed = update.privacy?.autoAcceptFollowed,
                noCrawle = update.privacy?.noCrawle,
                preventAiLearning = update.privacy?.preventAiLearning,
                publicReactions = update.privacy?.publicReactions,
                mutingNotificationTypes = update.notifications?.mutedTypes,
                mutedWords = update.filters?.mutedWords?.toMutedWordPayload(),
                hardMutedWords = update.filters?.hardMutedWords?.toMutedWordPayload(),
                mutedInstances = update.filters?.mutedInstances,
            )
        }
    }
}

@Serializable
private data class SettingsAccountDto(
    val isLocked: Boolean = false,
    val autoAcceptFollowed: Boolean = true,
    val noCrawle: Boolean = false,
    val preventAiLearning: Boolean = false,
    val publicReactions: Boolean = true,
    val mutingNotificationTypes: List<String> = emptyList(),
    val mutedWords: JsonElement? = null,
    val hardMutedWords: JsonElement? = null,
    val mutedInstances: List<String> = emptyList(),
    @SerialName("twoFactorEnabled")
    val twoFactorEnabled: Boolean? = null,
    val usePasswordLessLogin: Boolean? = null,
) {
    fun toDomain(): SettingsPreferences {
        return SettingsPreferences(
            privacy = PrivacySettings(
                isLocked = isLocked,
                autoAcceptFollowed = autoAcceptFollowed,
                noCrawle = noCrawle,
                preventAiLearning = preventAiLearning,
                publicReactions = publicReactions,
            ),
            notifications = NotificationSettings(
                mutedTypes = mutingNotificationTypes.distinct(),
            ),
            filters = FilterSettings(
                mutedWords = mutedWords.toMutedWordLines(),
                hardMutedWords = hardMutedWords.toMutedWordLines(),
                mutedInstances = mutedInstances.mapNotNull { it.trim().takeIf(String::isNotBlank) },
            ),
            security = SecuritySettings(
                twoFactorEnabled = twoFactorEnabled,
                passkeysEnabled = usePasswordLessLogin,
            ),
            integrations = IntegrationSettings(),
        )
    }
}

private fun List<String>.toMutedWordPayload(): List<List<String>> {
    return mapNotNull { phrase ->
        phrase.trim()
            .split(settingsWhitespaceRegex)
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .takeIf { it.isNotEmpty() }
    }
}

private val settingsWhitespaceRegex = Regex("\\s+")

private fun JsonElement?.toMutedWordLines(): List<String> {
    val array = this as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.trim()
            is JsonArray -> element.jsonArray
                .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                .joinToString(" ")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }.distinct()
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultSettingsClient(): HttpClient {
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
