package cc.hhhl.client.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

open class AiRepository(
    private val httpClient: HttpClient = defaultAiHttpClient(),
    private val remoteTokenProvider: () -> String? = { null },
    private val remoteBaseUrlProvider: () -> String = { DEFAULT_SERVER_AI_BASE_URL },
) {
    open suspend fun complete(
        settings: AiSettings,
        prompt: AiPrompt,
        model: String = settings.activeChatModel,
        fileIds: List<String> = emptyList(),
    ): AiRepositoryResult {
        if (!settings.enabled && prompt.user != "请只回复 OK。") {
            return AiRepositoryResult.Error("AI 未启用")
        }
        if (settings.serviceMode != AiServiceMode.LocalOnly) {
            val remoteResult = completeServerBuiltIn(settings, prompt, fileIds)
            when (remoteResult) {
                is AiRepositoryResult.Success -> return remoteResult
                AiRepositoryResult.Unauthorized -> {
                    if (settings.serviceMode == AiServiceMode.RemoteOnly || !settings.hasLocalEndpoint) {
                        return AiRepositoryResult.Unauthorized
                    }
                }
                is AiRepositoryResult.Error -> {
                    if (!settings.allowLocalFallback || !settings.hasLocalEndpoint) return remoteResult
                }
            }
        }
        if (settings.serviceMode == AiServiceMode.RemoteOnly) {
            return AiRepositoryResult.Error("远端 AI 不可用，且当前设置为仅远端")
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

    open suspend fun loadServerStatus(settings: AiSettings = AiSettings()): ServerAiStatusResult {
        val token = remoteTokenProvider()?.trim().orEmpty()
        if (token.isBlank()) return ServerAiStatusResult.Unauthorized
        return fetchServerAiStatus(token, settings.effectiveRemoteBaseUrl(remoteBaseUrlProvider()))
    }

    open suspend fun listServerConversations(settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(settings, "ai", "conversations", "list")
    }

    open suspend fun showServerConversation(conversationId: String, settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "ai",
            "conversations",
            "show",
            body = buildJsonObject { put("conversationId", JsonPrimitive(conversationId)) },
        )
    }

    open suspend fun createServerConversation(
        providerId: String? = null,
        model: String? = null,
        title: String? = null,
        systemPrompt: String? = null,
        settings: AiSettings = AiSettings(),
    ): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "ai",
            "conversations",
            "create",
            body = buildJsonObject {
                putNullableString("providerId", providerId)
                putNullableString("model", model)
                putNullableString("title", title)
                putNullableString("systemPrompt", systemPrompt)
            },
        )
    }

    open suspend fun updateServerConversation(
        conversationId: String,
        title: String? = null,
        systemPrompt: String? = null,
        settings: AiSettings = AiSettings(),
    ): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "ai",
            "conversations",
            "update",
            body = buildJsonObject {
                put("conversationId", JsonPrimitive(conversationId))
                putNullableString("title", title)
                putNullableString("systemPrompt", systemPrompt)
            },
        )
    }

    open suspend fun deleteServerConversation(conversationId: String, settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "ai",
            "conversations",
            "delete",
            body = buildJsonObject { put("conversationId", JsonPrimitive(conversationId)) },
        )
    }

    open suspend fun listServerMessages(conversationId: String, settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "ai",
            "messages",
            "list",
            body = buildJsonObject { put("conversationId", JsonPrimitive(conversationId)) },
        )
    }

    open suspend fun deleteServerMessage(messageId: String, settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "ai",
            "messages",
            "delete",
            body = buildJsonObject { put("messageId", JsonPrimitive(messageId)) },
        )
    }

    open suspend fun chatServer(
        content: String,
        conversationId: String? = null,
        providerId: String? = null,
        model: String? = null,
        fileIds: List<String> = emptyList(),
        systemPrompt: String? = null,
        settings: AiSettings = AiSettings(),
    ): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "ai",
            "chat",
            body = serverAiChatBody(
                conversationId = conversationId,
                providerId = providerId,
                model = model,
                content = content,
                fileIds = fileIds,
                systemPrompt = systemPrompt,
            ),
        )
    }

    open suspend fun showAdminAiSettings(settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(settings, "admin", "ai", "settings", "show")
    }

    open suspend fun updateAdminAiSettings(
        enableAi: Boolean,
        showAiInNavbar: Boolean? = null,
        aiDefaultProviderId: String? = null,
        aiMaxContextMessages: Int? = null,
        settings: AiSettings = AiSettings(),
    ): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "admin",
            "ai",
            "settings",
            "update",
            body = buildJsonObject {
                put("enableAi", JsonPrimitive(enableAi))
                showAiInNavbar?.let { put("showAiInNavbar", JsonPrimitive(it)) }
                putNullableString("aiDefaultProviderId", aiDefaultProviderId)
                aiMaxContextMessages?.let { put("aiMaxContextMessages", JsonPrimitive(it)) }
            },
        )
    }

    open suspend fun listAdminAiProviders(settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(settings, "admin", "ai", "providers", "list")
    }

    open suspend fun createAdminAiProvider(
        name: String,
        baseUrl: String,
        apiKey: String,
        isEnabled: Boolean = true,
        timeoutMs: Int? = null,
        maxTokens: Int? = null,
        temperature: Double? = null,
        settings: AiSettings = AiSettings(),
    ): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "admin",
            "ai",
            "providers",
            "create",
            body = buildJsonObject {
                put("name", JsonPrimitive(name))
                put("baseUrl", JsonPrimitive(baseUrl))
                put("apiKey", JsonPrimitive(apiKey))
                put("isEnabled", JsonPrimitive(isEnabled))
                timeoutMs?.let { put("timeoutMs", JsonPrimitive(it)) }
                maxTokens?.let { put("maxTokens", JsonPrimitive(it)) }
                temperature?.let { put("temperature", JsonPrimitive(it)) }
            },
        )
    }

    open suspend fun updateAdminAiProvider(
        id: String,
        name: String? = null,
        baseUrl: String? = null,
        apiKey: String? = null,
        isEnabled: Boolean? = null,
        defaultModel: String? = null,
        allowedModels: List<String>? = null,
        timeoutMs: Int? = null,
        maxTokens: Int? = null,
        temperature: Double? = null,
        settings: AiSettings = AiSettings(),
    ): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "admin",
            "ai",
            "providers",
            "update",
            body = buildJsonObject {
                put("id", JsonPrimitive(id))
                putNullableString("name", name)
                putNullableString("baseUrl", baseUrl)
                putNullableString("apiKey", apiKey)
                isEnabled?.let { put("isEnabled", JsonPrimitive(it)) }
                putNullableString("defaultModel", defaultModel)
                allowedModels?.let { models ->
                    put(
                        "allowedModels",
                        buildJsonArray {
                            models.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { add(it) }
                        },
                    )
                }
                timeoutMs?.let { put("timeoutMs", JsonPrimitive(it)) }
                maxTokens?.let { put("maxTokens", JsonPrimitive(it)) }
                temperature?.let { put("temperature", JsonPrimitive(it)) }
            },
        )
    }

    open suspend fun deleteAdminAiProvider(id: String, settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "admin",
            "ai",
            "providers",
            "delete",
            body = buildJsonObject { put("id", JsonPrimitive(id)) },
        )
    }

    open suspend fun testAdminAiProvider(id: String, settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "admin",
            "ai",
            "providers",
            "test",
            body = buildJsonObject { put("id", JsonPrimitive(id)) },
        )
    }

    open suspend fun fetchAdminAiProviderModels(id: String, settings: AiSettings = AiSettings()): ServerAiJsonResult {
        return postServerAiJson(
            settings,
            "admin",
            "ai",
            "providers",
            "fetch-models",
            body = buildJsonObject { put("id", JsonPrimitive(id)) },
        )
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

    private suspend fun completeServerBuiltIn(
        settings: AiSettings,
        prompt: AiPrompt,
        fileIds: List<String> = emptyList(),
    ): AiRepositoryResult {
        val token = remoteTokenProvider()?.trim().orEmpty()
        if (token.isBlank()) return AiRepositoryResult.Unauthorized
        val remoteBaseUrl = settings.effectiveRemoteBaseUrl(remoteBaseUrlProvider())
        val status = when (val result = fetchServerAiStatus(token, remoteBaseUrl)) {
            is ServerAiStatusResult.Success -> result.status
            ServerAiStatusResult.Unauthorized -> return AiRepositoryResult.Unauthorized
            is ServerAiStatusResult.Error -> return AiRepositoryResult.Error(result.message)
        }
        if (!status.enabled) return AiRepositoryResult.Error("服务器 AI 未启用")
        if (status.providers.isEmpty()) return AiRepositoryResult.Error("服务器 AI 没有可用供应商")

        val selection = status.selectProviderAndModel(settings.remotePreferredModel)
            ?: return AiRepositoryResult.Error("服务器 AI 没有可用模型")

        return runCatching {
            val response = httpClient.post(remoteBaseUrl.serverAiStreamUrl()) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "text/event-stream")
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    ServerAiChatStreamRequest(
                        i = token,
                        conversationId = null,
                        providerId = selection.providerId,
                        model = selection.model,
                        content = prompt.user,
                        fileIds = fileIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                        systemPrompt = prompt.system.ifBlank { null },
                    ),
                )
            }

            val statusCode = response.status.value
            if (statusCode == 401 || statusCode == 403) return@runCatching AiRepositoryResult.Unauthorized
            val body = response.bodyAsText()
            if (statusCode !in 200..299) {
                return@runCatching AiRepositoryResult.Error(
                    "服务器 AI 返回 $statusCode${body.take(240).takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}",
                )
            }
            val text = parseServerAiSseText(body)
            if (text.isBlank()) {
                AiRepositoryResult.Error("服务器 AI 没有返回内容")
            } else {
                AiRepositoryResult.Success(text)
            }
        }.getOrElse { error ->
            AiRepositoryResult.Error(error.message ?: "服务器 AI 请求失败")
        }
    }

    private suspend fun fetchServerAiStatus(
        token: String,
        remoteBaseUrl: String,
    ): ServerAiStatusResult {
        return runCatching {
            val response = httpClient.post(remoteBaseUrl.serverApiUrl("ai", "status")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(buildJsonObject { put("i", JsonPrimitive(token)) })
            }
            val statusCode = response.status.value
            if (statusCode == 401 || statusCode == 403) return@runCatching ServerAiStatusResult.Unauthorized
            val body = response.bodyAsText()
            if (statusCode !in 200..299) {
                return@runCatching ServerAiStatusResult.Error(
                    "服务器 AI 状态返回 $statusCode${body.take(240).takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}",
                )
            }
            val payload = runCatching {
                AiRepositoryJson.decodeFromString<ServerAiStatus>(body)
            }.getOrElse {
                val element = AiRepositoryJson.parseToJsonElement(body)
                AiRepositoryJson.decodeFromJsonElement(element)
            }
            ServerAiStatusResult.Success(payload)
        }.getOrElse { error ->
            ServerAiStatusResult.Error(error.message ?: "服务器 AI 状态请求失败")
        }
    }

    private suspend fun postServerAiJson(
        settings: AiSettings,
        vararg endpoint: String,
        body: JsonObject = buildJsonObject {},
    ): ServerAiJsonResult {
        val token = remoteTokenProvider()?.trim().orEmpty()
        if (token.isBlank()) return ServerAiJsonResult.Unauthorized
        val remoteBaseUrl = settings.effectiveRemoteBaseUrl(remoteBaseUrlProvider())
        return runCatching {
            val response = httpClient.post(remoteBaseUrl.serverApiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(body.withServerAiToken(token))
            }
            val statusCode = response.status.value
            if (statusCode == 401 || statusCode == 403) return@runCatching ServerAiJsonResult.Unauthorized
            val text = response.bodyAsText()
            if (statusCode !in 200..299) {
                return@runCatching ServerAiJsonResult.Error(
                    statusCode = statusCode,
                    message = text.ifBlank { "服务器返回 $statusCode" }.take(500),
                )
            }
            val payload = text.takeIf { it.isNotBlank() }?.let { AiRepositoryJson.parseToJsonElement(it) }
                ?: buildJsonObject {}
            ServerAiJsonResult.Success(payload)
        }.getOrElse { error ->
            ServerAiJsonResult.Error(message = error.message ?: "服务器 AI 请求失败")
        }
    }
}

sealed interface AiRepositoryResult {
    data class Success(val text: String) : AiRepositoryResult
    data object Unauthorized : AiRepositoryResult
    data class Error(val message: String) : AiRepositoryResult
}

sealed interface ServerAiStatusResult {
    data class Success(val status: ServerAiStatus) : ServerAiStatusResult
    data object Unauthorized : ServerAiStatusResult
    data class Error(val message: String) : ServerAiStatusResult
}

sealed interface ServerAiJsonResult {
    data class Success(val payload: JsonElement) : ServerAiJsonResult
    data object Unauthorized : ServerAiJsonResult
    data class Error(
        val statusCode: Int? = null,
        val message: String,
    ) : ServerAiJsonResult
}

@Serializable
data class ServerAiStatus(
    val enabled: Boolean = false,
    val providers: List<ServerAiProvider> = emptyList(),
    val defaultProviderId: String? = null,
    val maxContextMessages: Int = 20,
)

@Serializable
data class ServerAiProvider(
    val id: String = "",
    val name: String = "",
    val isEnabled: Boolean = true,
    val defaultModel: String? = null,
    val allowedModels: List<String> = emptyList(),
    val models: List<String> = emptyList(),
)

data class ServerAiModelSelection(
    val providerId: String?,
    val model: String?,
)

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

@Serializable
private data class ServerAiChatStreamRequest(
    val i: String,
    val conversationId: String? = null,
    val providerId: String? = null,
    val model: String? = null,
    val content: String = "",
    val fileIds: List<String> = emptyList(),
    val systemPrompt: String? = null,
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

private fun AiSettings.effectiveRemoteBaseUrl(providerBaseUrl: String? = null): String {
    val provided = providerBaseUrl?.trim()?.trimEnd('/').orEmpty()
    val configured = cleanRemoteBaseUrl
    return when {
        configured.isBlank() -> provided.ifBlank { DEFAULT_SERVER_AI_BASE_URL }
        configured == DEFAULT_SERVER_AI_BASE_URL && provided.isNotBlank() -> provided
        else -> configured
    }
}

private fun String.serverApiUrl(vararg endpoint: String): String {
    return URLBuilder(trim().trimEnd('/'))
        .appendPathSegments("api", *endpoint)
        .buildString()
}

private fun String.serverAiStreamUrl(): String {
    return serverApiUrl("ai", "chat-stream")
}

private fun JsonObject.withServerAiToken(token: String): JsonObject {
    return buildJsonObject {
        this@withServerAiToken.entries.forEach { entry -> put(entry.key, entry.value) }
        put("i", JsonPrimitive(token))
    }
}

private fun JsonObjectBuilder.putNullableString(key: String, value: String?) {
    val clean = value?.trim()
    if (clean == null) {
        put(key, JsonNull)
    } else {
        put(key, JsonPrimitive(clean))
    }
}

private fun serverAiChatBody(
    conversationId: String?,
    providerId: String?,
    model: String?,
    content: String,
    fileIds: List<String>,
    systemPrompt: String?,
): JsonObject {
    return buildJsonObject {
        putNullableString("conversationId", conversationId)
        putNullableString("providerId", providerId)
        putNullableString("model", model)
        put("content", JsonPrimitive(content))
        put(
            "fileIds",
            buildJsonArray {
                fileIds.map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) }
            },
        )
        putNullableString("systemPrompt", systemPrompt)
    }
}

private fun ServerAiStatus.selectProviderAndModel(preferredModel: String): ServerAiModelSelection? {
    val preferred = preferredModel.trim().ifBlank { DEFAULT_SERVER_AI_MODEL }
    val enabledProviders = providers.filter { provider -> provider.isEnabled && provider.id.isNotBlank() }
        .ifEmpty { providers.filter { it.id.isNotBlank() } }
    if (enabledProviders.isEmpty()) return null

    fun ServerAiProvider.modelCandidates(): List<String> {
        return (listOfNotNull(defaultModel) + allowedModels + models)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val exact = enabledProviders.firstOrNull { provider ->
        provider.modelCandidates().any { it.equals(preferred, ignoreCase = true) }
    }
    if (exact != null) {
        return ServerAiModelSelection(
            providerId = exact.id,
            model = exact.modelCandidates().firstOrNull { it.equals(preferred, ignoreCase = true) } ?: preferred,
        )
    }

    val defaultProvider = defaultProviderId
        ?.let { id -> enabledProviders.firstOrNull { provider -> provider.id == id } }
    val provider = defaultProvider ?: enabledProviders.first()
    val model = provider.modelCandidates().firstOrNull()
        ?: enabledProviders.asSequence().flatMap { it.modelCandidates().asSequence() }.firstOrNull()
        ?: preferred
    return ServerAiModelSelection(providerId = provider.id, model = model)
}

private fun parseServerAiSseText(body: String): String {
    if (body.isBlank()) return ""
    val output = StringBuilder()
    val events = body
        .replace("\r\n", "\n")
        .split(Regex("\n{2,}"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    var finalPayload: JsonElement? = null
    for (eventText in events) {
        val event = parseServerAiSseEvent(eventText)
        when (event.type) {
            "delta" -> event.data?.serverAiTextValue()?.takeIf { it.isNotBlank() }?.let(output::append)
            "done" -> finalPayload = event.data
            "error" -> throw IllegalStateException(event.data?.serverAiErrorMessage() ?: "服务器 AI 流式请求失败")
        }
    }
    if (output.isNotBlank()) return output.toString().trim()
    return finalPayload?.serverAiTextValue().orEmpty().trim()
}

private data class ServerAiSseEvent(
    val type: String,
    val data: JsonElement?,
)

private fun parseServerAiSseEvent(eventText: String): ServerAiSseEvent {
    var type = "message"
    val dataLines = mutableListOf<String>()
    eventText.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.startsWith("event:") -> type = line.substringAfter("event:").trim()
            line.startsWith("data:") -> dataLines += line.substringAfter("data:").trimStart()
        }
    }
    val data = dataLines.joinToString("\n")
        .takeIf { it.isNotBlank() && it != "[DONE]" }
        ?.let { AiRepositoryJson.parseToJsonElement(it) }
    return ServerAiSseEvent(type = type, data = data)
}

private fun JsonElement.serverAiTextValue(): String {
    return when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> map { it.serverAiTextValue() }.filter { it.isNotBlank() }.joinToString("\n")
        is JsonObject -> {
            val direct = listOf("text", "content", "message", "delta")
                .firstNotNullOfOrNull { key -> get(key)?.serverAiTextValue()?.takeIf { it.isNotBlank() } }
            direct ?: listOf("assistantMessage", "assistant", "reply", "result", "data")
                .firstNotNullOfOrNull { key -> get(key)?.serverAiTextValue()?.takeIf { it.isNotBlank() } }
                ?: ""
        }
    }
}

private fun JsonElement.serverAiErrorMessage(): String {
    val obj = this as? JsonObject ?: return serverAiTextValue().ifBlank { toString() }
    return obj["message"]?.jsonPrimitive?.contentOrNull
        ?: obj["error"]?.serverAiTextValue()?.takeIf { it.isNotBlank() }
        ?: obj.toString()
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
                AiRepositoryJson,
            )
        }
    }
}

private val AiRepositoryJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
