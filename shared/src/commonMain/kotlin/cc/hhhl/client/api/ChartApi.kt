package cc.hhhl.client.api

import cc.hhhl.client.model.ChartPayload
import cc.hhhl.client.model.ChartSpan
import cc.hhhl.client.model.InstanceChartKind
import cc.hhhl.client.model.UserChartKind
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
import kotlinx.serialization.json.JsonObject

interface ChartApi {
    suspend fun loadInstanceChart(
        kind: InstanceChartKind,
        span: ChartSpan,
        limit: Int = 30,
        offset: Int? = null,
        host: String? = null,
    ): ChartLoadResult

    suspend fun loadUserChart(
        kind: UserChartKind,
        userId: String,
        span: ChartSpan,
        limit: Int = 30,
        offset: Int? = null,
    ): ChartLoadResult
}

sealed interface ChartLoadResult {
    data class Success(val chart: ChartPayload) : ChartLoadResult
    data class ServerError(val statusCode: Int, val message: String) : ChartLoadResult
    data class NetworkError(val message: String) : ChartLoadResult
}

class SharkeyChartApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultChartClient(),
) : ChartApi {
    override suspend fun loadInstanceChart(
        kind: InstanceChartKind,
        span: ChartSpan,
        limit: Int,
        offset: Int?,
        host: String?,
    ): ChartLoadResult {
        val cleanHost = host?.trim()?.takeIf { it.isNotEmpty() }
        if (kind == InstanceChartKind.Instance && cleanHost == null) {
            return ChartLoadResult.ServerError(400, "实例图表需要 host")
        }

        return postChart(
            endpoint = kind.endpoint,
            body = ChartRequest(
                span = span.apiValue,
                limit = limit.coerceIn(1, 500),
                offset = offset?.coerceAtLeast(0),
                host = cleanHost,
            ),
            kind = kind.name,
            span = span,
        )
    }

    override suspend fun loadUserChart(
        kind: UserChartKind,
        userId: String,
        span: ChartSpan,
        limit: Int,
        offset: Int?,
    ): ChartLoadResult {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty()) return ChartLoadResult.ServerError(400, "用户 ID 不能为空")

        return postChart(
            endpoint = kind.endpoint,
            body = ChartRequest(
                span = span.apiValue,
                limit = limit.coerceIn(1, 500),
                offset = offset?.coerceAtLeast(0),
                userId = cleanUserId,
            ),
            kind = kind.name,
            span = span,
        )
    }

    private suspend fun postChart(
        endpoint: List<String>,
        body: ChartRequest,
        kind: String,
        span: ChartSpan,
    ): ChartLoadResult {
        return try {
            val response = client.post(apiUrl(endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            when (response.status) {
                HttpStatusCode.OK -> ChartLoadResult.Success(
                    ChartPayload(
                        kind = kind,
                        span = span,
                        data = response.body<JsonObject>(),
                    ),
                )
                else -> ChartLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChartLoadResult.NetworkError(error.message ?: "网络请求失败")
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

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultChartClient(): HttpClient {
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
private data class ChartRequest(
    val span: String,
    val limit: Int = 30,
    val offset: Int? = null,
    val host: String? = null,
    val userId: String? = null,
)
