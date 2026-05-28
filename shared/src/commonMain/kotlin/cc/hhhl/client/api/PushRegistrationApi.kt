package cc.hhhl.client.api

import cc.hhhl.client.model.PushRegistration
import cc.hhhl.client.model.PushRegistrationInput
import cc.hhhl.client.model.PushRegistrationState
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface PushRegistrationApi {
    suspend fun register(
        token: String,
        input: PushRegistrationInput,
    ): PushRegistrationResult

    suspend fun showRegistration(
        token: String,
        endpoint: String,
    ): PushRegistrationLookupResult

    suspend fun updateRegistration(
        token: String,
        endpoint: String,
        sendReadMessage: Boolean,
    ): PushRegistrationResult

    suspend fun unregister(endpoint: String): PushRegistrationActionResult
}

sealed interface PushRegistrationResult {
    data class Success(val registration: PushRegistration) : PushRegistrationResult
    data object Unauthorized : PushRegistrationResult
    data class ServerError(val statusCode: Int, val message: String) : PushRegistrationResult
    data class NetworkError(val message: String) : PushRegistrationResult
}

sealed interface PushRegistrationLookupResult {
    data class Success(val registration: PushRegistration?) : PushRegistrationLookupResult
    data object Unauthorized : PushRegistrationLookupResult
    data class ServerError(val statusCode: Int, val message: String) : PushRegistrationLookupResult
    data class NetworkError(val message: String) : PushRegistrationLookupResult
}

sealed interface PushRegistrationActionResult {
    data object Success : PushRegistrationActionResult
    data class ServerError(val statusCode: Int, val message: String) : PushRegistrationActionResult
    data class NetworkError(val message: String) : PushRegistrationActionResult
}

class SharkeyPushRegistrationApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultPushRegistrationClient(),
) : PushRegistrationApi {
    override suspend fun register(
        token: String,
        input: PushRegistrationInput,
    ): PushRegistrationResult {
        val cleanToken = token.trim()
        val cleanInput = input.clean()
        if (cleanToken.isEmpty()) return PushRegistrationResult.Unauthorized
        cleanInput.validate()?.let { return PushRegistrationResult.ServerError(400, it) }

        return try {
            val response = client.post(apiUrl("sw", "register")) {
                header(HttpHeaders.Authorization, "Bearer $cleanToken")
                contentType(ContentType.Application.Json)
                setBody(cleanInput.toRegisterRequest())
            }
            if (response.isSharkeyUnauthorized()) return PushRegistrationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> PushRegistrationResult.Success(response.body<PushRegistrationDto>().toDomain())
                HttpStatusCode.Unauthorized -> PushRegistrationResult.Unauthorized
                else -> PushRegistrationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PushRegistrationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun showRegistration(
        token: String,
        endpoint: String,
    ): PushRegistrationLookupResult {
        val cleanToken = token.trim()
        val cleanEndpoint = endpoint.trim()
        if (cleanToken.isEmpty()) return PushRegistrationLookupResult.Unauthorized
        if (cleanEndpoint.isEmpty()) return PushRegistrationLookupResult.ServerError(400, "推送 endpoint 不能为空")

        return try {
            val response = client.post(apiUrl("sw", "show-registration")) {
                header(HttpHeaders.Authorization, "Bearer $cleanToken")
                contentType(ContentType.Application.Json)
                setBody(PushEndpointRequest(endpoint = cleanEndpoint))
            }
            if (response.isSharkeyUnauthorized()) return PushRegistrationLookupResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> PushRegistrationLookupResult.Success(
                    response.body<PushRegistrationDto?>()?.toDomain(),
                )
                HttpStatusCode.NoContent -> PushRegistrationLookupResult.Success(null)
                HttpStatusCode.Unauthorized -> PushRegistrationLookupResult.Unauthorized
                else -> PushRegistrationLookupResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PushRegistrationLookupResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updateRegistration(
        token: String,
        endpoint: String,
        sendReadMessage: Boolean,
    ): PushRegistrationResult {
        val cleanToken = token.trim()
        val cleanEndpoint = endpoint.trim()
        if (cleanToken.isEmpty()) return PushRegistrationResult.Unauthorized
        if (cleanEndpoint.isEmpty()) return PushRegistrationResult.ServerError(400, "推送 endpoint 不能为空")

        return try {
            val response = client.post(apiUrl("sw", "update-registration")) {
                header(HttpHeaders.Authorization, "Bearer $cleanToken")
                contentType(ContentType.Application.Json)
                setBody(PushUpdateRequest(endpoint = cleanEndpoint, sendReadMessage = sendReadMessage))
            }
            if (response.isSharkeyUnauthorized()) return PushRegistrationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> PushRegistrationResult.Success(response.body<PushRegistrationDto>().toDomain())
                HttpStatusCode.Unauthorized -> PushRegistrationResult.Unauthorized
                else -> PushRegistrationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PushRegistrationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun unregister(endpoint: String): PushRegistrationActionResult {
        val cleanEndpoint = endpoint.trim()
        if (cleanEndpoint.isEmpty()) return PushRegistrationActionResult.ServerError(400, "推送 endpoint 不能为空")

        return try {
            val response = client.post(apiUrl("sw", "unregister")) {
                contentType(ContentType.Application.Json)
                setBody(PushEndpointRequest(endpoint = cleanEndpoint))
            }
            when {
                response.status.value in 200..299 -> PushRegistrationActionResult.Success
                else -> PushRegistrationActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PushRegistrationActionResult.NetworkError(error.message ?: "网络请求失败")
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
private data class PushRegisterRequest(
    val endpoint: String,
    val auth: String,
    @SerialName("publickey") val publicKey: String,
    val sendReadMessage: Boolean = false,
)

@Serializable
private data class PushEndpointRequest(
    val endpoint: String,
)

@Serializable
private data class PushUpdateRequest(
    val endpoint: String,
    val sendReadMessage: Boolean,
)

@Serializable
private data class PushRegistrationDto(
    val userId: String = "",
    val endpoint: String = "",
    val sendReadMessage: Boolean = false,
    val key: String? = null,
    val state: String? = null,
) {
    fun toDomain(): PushRegistration {
        return PushRegistration(
            userId = userId,
            endpoint = endpoint,
            sendReadMessage = sendReadMessage,
            key = key?.takeIf { it.isNotBlank() },
            state = state.toPushRegistrationState(),
        )
    }
}

private fun PushRegistrationInput.clean(): PushRegistrationInput {
    return copy(
        endpoint = endpoint.trim(),
        auth = auth.trim(),
        publicKey = publicKey.trim(),
    )
}

private fun PushRegistrationInput.validate(): String? {
    return when {
        endpoint.isBlank() -> "推送 endpoint 不能为空"
        auth.isBlank() -> "推送 auth 不能为空"
        publicKey.isBlank() -> "推送 public key 不能为空"
        else -> null
    }
}

private fun PushRegistrationInput.toRegisterRequest(): PushRegisterRequest {
    return PushRegisterRequest(
        endpoint = endpoint,
        auth = auth,
        publicKey = publicKey,
        sendReadMessage = sendReadMessage,
    )
}

private fun String?.toPushRegistrationState(): PushRegistrationState? {
    return when (this) {
        null -> null
        "subscribed" -> PushRegistrationState.Subscribed
        "already-subscribed" -> PushRegistrationState.AlreadySubscribed
        else -> PushRegistrationState.Unknown
    }
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultPushRegistrationClient(): HttpClient {
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
