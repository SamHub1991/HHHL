package cc.hhhl.client.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

open class AiRepository(
    private val httpClient: HttpClient = defaultAiHttpClient(),
) {
    open suspend fun complete(
        settings: AiSettings,
        prompt: AiPrompt,
        model: String = settings.activeChatModel,
    ): AiRepositoryResult {
        if (!settings.enabled && prompt.user != "请只回复 OK。") {
            return AiRepositoryResult.Error("AI 未启用")
        }
        val baseUrl = settings.cleanBaseUrl
        if (baseUrl.isBlank()) return AiRepositoryResult.Error("Base URL 不能为空")
        val cleanModel = model.trim()
        if (cleanModel.isBlank()) return AiRepositoryResult.Error("模型不能为空")
        if (settings.supportsCloudAuth && settings.apiKey.isBlank()) {
            return AiRepositoryResult.Unauthorized
        }

        return if (settings.provider == AiProviderPreset.Claude) {
            completeAnthropic(settings, prompt, cleanModel, baseUrl)
        } else {
            completeOpenAiCompatible(settings, prompt, cleanModel, baseUrl)
        }
    }

    private suspend fun completeOpenAiCompatible(
        settings: AiSettings,
        prompt: AiPrompt,
        cleanModel: String,
        baseUrl: String,
    ): AiRepositoryResult {
        return runCatching {
            val response = httpClient.post(baseUrl.aiEndpointUrl("chat/completions")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                if (settings.apiKey.isNotBlank()) header(HttpHeaders.Authorization, "Bearer ${settings.apiKey.trim()}")
                setBody(
                    OpenAiChatCompletionRequest(
                        model = cleanModel,
                        messages = listOf(
                            OpenAiChatMessage(role = "system", content = prompt.system),
                            OpenAiChatMessage(role = "user", content = prompt.user),
                        ),
                        temperature = 0.3,
                        maxTokens = prompt.maxOutputTokens,
                    ),
                )
            }

            val status = response.status.value
            if (status == 401 || status == 403) return@runCatching AiRepositoryResult.Unauthorized
            if (status !in 200..299) {
                val body = runCatching { response.body<String>() }.getOrDefault("").take(240)
                return@runCatching AiRepositoryResult.Error(
                    "AI 接口返回 $status${body.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}",
                )
            }

            val payload = response.body<OpenAiChatCompletionResponse>()
            val text = payload.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (text.isBlank()) {
                AiRepositoryResult.Error("AI 没有返回内容")
            } else {
                AiRepositoryResult.Success(text)
            }
        }.getOrElse { error ->
            AiRepositoryResult.Error(error.message ?: "AI 请求失败")
        }
    }

    private suspend fun completeAnthropic(
        settings: AiSettings,
        prompt: AiPrompt,
        cleanModel: String,
        baseUrl: String,
    ): AiRepositoryResult {
        return runCatching {
            val response = httpClient.post(baseUrl.aiEndpointUrl("messages")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                header("anthropic-version", ANTHROPIC_API_VERSION)
                if (settings.apiKey.isNotBlank()) header("x-api-key", settings.apiKey.trim())
                setBody(
                    AnthropicMessagesRequest(
                        model = cleanModel,
                        system = prompt.system,
                        messages = listOf(
                            AnthropicMessage(role = "user", content = prompt.user),
                        ),
                        maxTokens = prompt.maxOutputTokens.coerceAtLeast(1),
                    ),
                )
            }

            val status = response.status.value
            if (status == 401 || status == 403) return@runCatching AiRepositoryResult.Unauthorized
            if (status !in 200..299) {
                val body = runCatching { response.body<String>() }.getOrDefault("").take(240)
                return@runCatching AiRepositoryResult.Error(
                    "AI 接口返回 $status${body.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}",
                )
            }

            val payload = response.body<AnthropicMessagesResponse>()
            val text = payload.content
                .mapNotNull { block -> block.text.trim().takeIf { it.isNotBlank() } }
                .joinToString("\n\n")
                .trim()
            if (text.isBlank()) {
                AiRepositoryResult.Error("AI 没有返回内容")
            } else {
                AiRepositoryResult.Success(text)
            }
        }.getOrElse { error ->
            AiRepositoryResult.Error(error.message ?: "AI 请求失败")
        }
    }
}

sealed interface AiRepositoryResult {
    data class Success(val text: String) : AiRepositoryResult
    data object Unauthorized : AiRepositoryResult
    data class Error(val message: String) : AiRepositoryResult
}

@Serializable
private data class OpenAiChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
private data class OpenAiChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class OpenAiChatCompletionResponse(
    val choices: List<OpenAiChatChoice> = emptyList(),
)

@Serializable
private data class OpenAiChatChoice(
    val message: OpenAiChatMessage = OpenAiChatMessage(role = "assistant", content = ""),
)

@Serializable
private data class AnthropicMessagesRequest(
    val model: String,
    val system: String,
    val messages: List<AnthropicMessage>,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class AnthropicMessagesResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
)

@Serializable
private data class AnthropicContentBlock(
    val type: String = "",
    val text: String = "",
)

private const val ANTHROPIC_API_VERSION = "2023-06-01"

private fun String.aiEndpointUrl(endpointPath: String): String {
    val cleanBase = trim().trimEnd('/')
    val cleanEndpointPath = endpointPath.trim('/')
    if (cleanBase.isBlank() || cleanEndpointPath.isBlank()) return cleanBase
    return if (cleanBase.lowercase().endsWith("/${cleanEndpointPath.lowercase()}")) {
        cleanBase
    } else {
        "$cleanBase/$cleanEndpointPath"
    }
}

private fun defaultAiHttpClient(): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 12_000
            socketTimeoutMillis = 45_000
        }
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
