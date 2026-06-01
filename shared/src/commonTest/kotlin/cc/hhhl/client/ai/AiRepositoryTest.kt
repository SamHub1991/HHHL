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
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

private fun aiTestClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient {
    return HttpClient(MockEngine { request -> handler(request) }) {
        install(ContentNegotiation) { json() }
    }
}
