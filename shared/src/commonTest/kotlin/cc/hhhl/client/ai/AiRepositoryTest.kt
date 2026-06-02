package cc.hhhl.client.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiRepositoryTest {
    @Test
    fun completeMapsSuccessfulChatCompletionResponse() = runTest {
        val repository = AiRepository(
            httpClient = aiTestClient {
                respond(
                    content = """{"choices":[{"message":{"role":"assistant","content":"整理完成"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = repository.complete(
            settings = AiSettings(enabled = true, apiKey = "key"),
            prompt = AiPrompt("system", "user", 120),
        )

        assertEquals(AiRepositoryResult.Success("整理完成"), result)
    }

    @Test
    fun completeDoesNotAppendChatEndpointWhenBaseUrlAlreadyContainsIt() = runTest {
        val repository = AiRepository(
            httpClient = aiTestClient { request ->
                assertEquals("/v1/chat/completions", request.url.encodedPath)
                respond(
                    content = """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = repository.complete(
            settings = AiSettings(
                enabled = true,
                apiKey = "key",
                baseUrl = "http" + "://localhost/v1/chat/completions",
            ),
            prompt = AiPrompt("system", "user", 120),
        )

        assertEquals(AiRepositoryResult.Success("OK"), result)
    }

    @Test
    fun completeUsesAnthropicMessagesEndpointForClaude() = runTest {
        val repository = AiRepository(
            httpClient = aiTestClient { request ->
                assertEquals("/v1/messages", request.url.encodedPath)
                assertEquals("claude-key", request.headers["x-api-key"])
                assertEquals("2023-06-01", request.headers["anthropic-version"])
                assertNull(request.headers[HttpHeaders.Authorization])
                respond(
                    content = """{"content":[{"type":"text","text":"Claude 完成"}]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = repository.complete(
            settings = AiSettings(
                enabled = true,
                provider = AiProviderPreset.Claude,
                baseUrl = "http" + "://localhost/v1",
                apiKey = "claude-key",
                chatModel = "claude4.7",
            ),
            prompt = AiPrompt("system", "user", 120),
        )

        assertEquals(AiRepositoryResult.Success("Claude 完成"), result)
    }

    @Test
    fun completeDoesNotAppendClaudeMessagesEndpointWhenBaseUrlAlreadyContainsIt() = runTest {
        val repository = AiRepository(
            httpClient = aiTestClient { request ->
                assertEquals("/v1/messages", request.url.encodedPath)
                respond(
                    content = """{"content":[{"type":"text","text":"Claude OK"}]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = repository.complete(
            settings = AiSettings(
                enabled = true,
                provider = AiProviderPreset.Claude,
                baseUrl = "http" + "://localhost/v1/messages",
                apiKey = "claude-key",
                chatModel = "claude4.7",
            ),
            prompt = AiPrompt("system", "user", 120),
        )

        assertEquals(AiRepositoryResult.Success("Claude OK"), result)
    }

    @Test
    fun completeMapsUnauthorizedStatuses() = runTest {
        val repository = AiRepository(
            httpClient = aiTestClient {
                respond("{}", status = HttpStatusCode.Unauthorized, headers = jsonHeaders)
            },
        )

        val result = repository.complete(
            settings = AiSettings(enabled = true, apiKey = "bad-key"),
            prompt = AiPrompt("system", "user", 120),
        )

        assertEquals(AiRepositoryResult.Unauthorized, result)
    }

    @Test
    fun completeMapsServerErrorBody() = runTest {
        val repository = AiRepository(
            httpClient = aiTestClient {
                respond("quota exceeded", status = HttpStatusCode.TooManyRequests, headers = jsonHeaders)
            },
        )

        val result = repository.complete(
            settings = AiSettings(enabled = true, apiKey = "key"),
            prompt = AiPrompt("system", "user", 120),
        )

        assertTrue(result is AiRepositoryResult.Error)
        assertTrue(result.message.contains("429"))
        assertTrue(result.message.contains("quota exceeded"))
    }

    @Test
    fun completeMapsEmptyAssistantContent() = runTest {
        val repository = AiRepository(
            httpClient = aiTestClient {
                respond(
                    content = """{"choices":[{"message":{"role":"assistant","content":"   "}}]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = repository.complete(
            settings = AiSettings(enabled = true, apiKey = "key"),
            prompt = AiPrompt("system", "user", 120),
        )

        assertEquals(AiRepositoryResult.Error("AI 没有返回内容"), result)
    }

    @Test
    fun completeUsesServerAiStreamFirstAndPrefersGpt55() = runTest {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val repository = AiRepository(
            httpClient = aiTestClient { request ->
                paths += request.url.encodedPath
                bodies += (request.body as TextContent).text
                assertEquals("Bearer session-token", request.headers[HttpHeaders.Authorization])
                when (request.url.encodedPath) {
                    "/api/ai/status" -> respond(
                        content = """
                            {
                              "enabled": true,
                              "defaultProviderId": "fallback",
                              "providers": [
                                {"id":"fallback","name":"Fallback","isEnabled":true,"defaultModel":"other-model","allowedModels":["other-model"]},
                                {"id":"remote-gpt","name":"Remote GPT","isEnabled":true,"defaultModel":"gpt-5.5","allowedModels":["gpt-5.5","gpt-5-mini"]}
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                    "/ai/chat-stream" -> respond(
                        content = """
                            event: delta
                            data: {"delta":"远端"}

                            event: delta
                            data: {"content":"完成"}

                            event: done
                            data: {"message":""}
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = eventStreamHeaders,
                    )
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            },
            remoteTokenProvider = { "session-token" },
            remoteBaseUrlProvider = { "https://dc.hhhl.cc" },
        )

        val result = repository.complete(
            settings = AiSettings(apiKey = "local-key"),
            prompt = AiPrompt("system prompt", "user prompt", 120),
        )

        assertEquals(AiRepositoryResult.Success("远端完成"), result)
        assertEquals(listOf("/api/ai/status", "/ai/chat-stream"), paths)
        assertTrue(bodies[0].contains(""""i":"session-token""""))
        assertTrue(bodies[1].contains(""""providerId":"remote-gpt""""))
        assertTrue(bodies[1].contains(""""model":"gpt-5.5""""))
        assertTrue(bodies[1].contains(""""content":"user prompt""""))
        assertTrue(bodies[1].contains(""""systemPrompt":"system prompt""""))
    }

    @Test
    fun completeSendsFileIdsToServerAiStream() = runTest {
        var streamBody = ""
        val repository = AiRepository(
            httpClient = aiTestClient { request ->
                when (request.url.encodedPath) {
                    "/api/ai/status" -> respond(
                        content = """
                            {
                              "enabled": true,
                              "defaultProviderId": "remote-gpt",
                              "providers": [
                                {"id":"remote-gpt","name":"Remote GPT","isEnabled":true,"defaultModel":"gpt-5.5","allowedModels":["gpt-5.5"]}
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                    "/ai/chat-stream" -> {
                        streamBody = (request.body as TextContent).text
                        respond(
                            content = """event: done${"\n"}data: {"message":"OK"}""",
                            status = HttpStatusCode.OK,
                            headers = eventStreamHeaders,
                        )
                    }
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            },
            remoteTokenProvider = { "session-token" },
            remoteBaseUrlProvider = { "https://dc.hhhl.cc" },
        )

        val result = repository.complete(
            settings = AiSettings(apiKey = "local-key"),
            prompt = AiPrompt("system prompt", "user prompt", 120),
            fileIds = listOf("file-a", " file-b ", "file-a", ""),
        )

        assertEquals(AiRepositoryResult.Success("OK"), result)
        assertTrue(streamBody.contains(""""fileIds":["file-a","file-b"]"""))
    }

    @Test
    fun completeFallsBackToLocalWhenRemoteFirstStatusFails() = runTest {
        val paths = mutableListOf<String>()
        val repository = AiRepository(
            httpClient = aiTestClient { request ->
                paths += request.url.encodedPath
                when (request.url.encodedPath) {
                    "/api/ai/status" -> respond("server down", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                    "/v1/chat/completions" -> respond(
                        content = """{"choices":[{"message":{"role":"assistant","content":"本地兜底"}}]}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            },
            remoteTokenProvider = { "session-token" },
            remoteBaseUrlProvider = { "https://dc.hhhl.cc" },
        )

        val result = repository.complete(
            settings = AiSettings(apiKey = "local-key"),
            prompt = AiPrompt("system", "user", 120),
        )

        assertEquals(AiRepositoryResult.Success("本地兜底"), result)
        assertEquals(listOf("/api/ai/status", "/v1/chat/completions"), paths)
    }

    @Test
    fun serverAiJsonMethodsPostToAllServerEndpointsWithSessionToken() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val repository = AiRepository(
            httpClient = aiTestClient { request ->
                val body = (request.body as TextContent).text
                calls += request.url.encodedPath to body
                assertEquals("Bearer session-token", request.headers[HttpHeaders.Authorization])
                assertTrue(body.contains(""""i":"session-token""""))
                respond("""{"ok":true}""", status = HttpStatusCode.OK, headers = jsonHeaders)
            },
            remoteTokenProvider = { "session-token" },
            remoteBaseUrlProvider = { "https://dc.hhhl.cc" },
        )

        assertTrue(repository.listServerConversations() is ServerAiJsonResult.Success)
        assertTrue(repository.createServerConversation(providerId = "p1", model = "gpt-5.5", title = "新对话") is ServerAiJsonResult.Success)
        assertTrue(repository.showServerConversation("c1") is ServerAiJsonResult.Success)
        assertTrue(repository.updateServerConversation("c1", title = "标题") is ServerAiJsonResult.Success)
        assertTrue(repository.deleteServerConversation("c1") is ServerAiJsonResult.Success)
        assertTrue(repository.listServerMessages("c1") is ServerAiJsonResult.Success)
        assertTrue(repository.deleteServerMessage("m1") is ServerAiJsonResult.Success)
        assertTrue(repository.chatServer(content = "你好", conversationId = "c1", model = "gpt-5.5") is ServerAiJsonResult.Success)
        assertTrue(repository.showAdminAiSettings() is ServerAiJsonResult.Success)
        assertTrue(
            repository.updateAdminAiSettings(
                enableAi = true,
                showAiInNavbar = true,
                aiDefaultProviderId = "p1",
                aiMaxContextMessages = 30,
            ) is ServerAiJsonResult.Success,
        )
        assertTrue(repository.listAdminAiProviders() is ServerAiJsonResult.Success)
        assertTrue(
            repository.createAdminAiProvider(
                name = "Provider",
                baseUrl = "https://ai.example/v1",
                apiKey = "sk-test",
                timeoutMs = 30_000,
                maxTokens = 1_024,
                temperature = 0.7,
            ) is ServerAiJsonResult.Success,
        )
        assertTrue(
            repository.updateAdminAiProvider(
                id = "p1",
                name = "Provider 2",
                baseUrl = "https://ai2.example/v1",
                apiKey = null,
                isEnabled = false,
                defaultModel = "gpt-5.5",
                allowedModels = listOf("gpt-5.5", "gpt-5.5"),
                timeoutMs = 20_000,
                maxTokens = 2_048,
                temperature = 0.3,
            ) is ServerAiJsonResult.Success,
        )
        assertTrue(repository.testAdminAiProvider("p1") is ServerAiJsonResult.Success)
        assertTrue(repository.fetchAdminAiProviderModels("p1") is ServerAiJsonResult.Success)
        assertTrue(repository.deleteAdminAiProvider("p1") is ServerAiJsonResult.Success)

        assertEquals(
            listOf(
                "/api/ai/conversations/list",
                "/api/ai/conversations/create",
                "/api/ai/conversations/show",
                "/api/ai/conversations/update",
                "/api/ai/conversations/delete",
                "/api/ai/messages/list",
                "/api/ai/messages/delete",
                "/api/ai/chat",
                "/api/admin/ai/settings/show",
                "/api/admin/ai/settings/update",
                "/api/admin/ai/providers/list",
                "/api/admin/ai/providers/create",
                "/api/admin/ai/providers/update",
                "/api/admin/ai/providers/test",
                "/api/admin/ai/providers/fetch-models",
                "/api/admin/ai/providers/delete",
            ),
            calls.map { it.first },
        )
        assertTrue(calls[1].second.contains(""""providerId":"p1""""))
        assertTrue(calls[1].second.contains(""""model":"gpt-5.5""""))
        assertTrue(calls[2].second.contains(""""conversationId":"c1""""))
        assertTrue(calls[6].second.contains(""""messageId":"m1""""))
        assertTrue(calls[7].second.contains(""""content":"你好""""))
        assertTrue(calls[9].second.contains(""""enableAi":true"""))
        assertTrue(calls[9].second.contains(""""aiDefaultProviderId":"p1""""))
        assertTrue(calls[11].second.contains(""""baseUrl":"https://ai.example/v1""""))
        assertTrue(calls[11].second.contains(""""temperature":0.7"""))
        assertTrue(calls[12].second.contains(""""id":"p1""""))
        assertTrue(calls[12].second.contains(""""allowedModels":["gpt-5.5"]"""))
        assertTrue(calls[13].second.contains(""""id":"p1""""))
    }
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private val eventStreamHeaders = headersOf(HttpHeaders.ContentType, "text/event-stream")

private fun aiTestClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient {
    return HttpClient(MockEngine { request -> handler(request) }) {
        install(ContentNegotiation) { json() }
    }
}
