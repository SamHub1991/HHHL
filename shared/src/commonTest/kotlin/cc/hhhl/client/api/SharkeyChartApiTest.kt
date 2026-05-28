package cc.hhhl.client.api

import cc.hhhl.client.model.ChartSpan
import cc.hhhl.client.model.InstanceChartKind
import cc.hhhl.client.model.UserChartKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharkeyChartApiTest {
    @Test
    fun loadsNotesChartFromChartsEndpoint() = runTest {
        val api = SharkeyChartApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/charts/notes", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("""{"span":"day","limit":30}""", (request.body as TextContent).text)
                respond("""{"local":{"total":[10,12]},"remote":{"total":[4,5]}}""", HttpStatusCode.OK, jsonHeaders)
            },
        )

        val result = api.loadInstanceChart(InstanceChartKind.Notes, ChartSpan.Day)

        val success = assertIs<ChartLoadResult.Success>(result)
        assertEquals("Notes", success.chart.kind)
        assertEquals("10", success.chart.data["local"]!!.jsonObject["total"]!!.jsonArray.first().jsonPrimitive.content)
    }

    @Test
    fun loadsRemoteInstanceChartWithHost() = runTest {
        val api = SharkeyChartApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/charts/instance", request.url.toString())
                assertEquals("""{"span":"hour","limit":24,"offset":2,"host":"remote.example"}""", (request.body as TextContent).text)
                respond("""{"requests":{"failed":[0],"succeeded":[2],"received":[3]}}""", HttpStatusCode.OK, jsonHeaders)
            },
        )

        val result = api.loadInstanceChart(
            kind = InstanceChartKind.Instance,
            span = ChartSpan.Hour,
            limit = 24,
            offset = 2,
            host = "remote.example",
        )

        assertIs<ChartLoadResult.Success>(result)
    }

    @Test
    fun loadsUserChartWithUserId() = runTest {
        val api = SharkeyChartApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/charts/user/notes", request.url.toString())
                assertEquals("""{"span":"day","limit":10,"userId":"user-1"}""", (request.body as TextContent).text)
                respond("""{"total":[8],"inc":[1],"dec":[0],"diffs":{"normal":[1]}}""", HttpStatusCode.OK, jsonHeaders)
            },
        )

        val result = api.loadUserChart(UserChartKind.Notes, userId = "user-1", span = ChartSpan.Day, limit = 10)

        assertIs<ChartLoadResult.Success>(result)
    }

    private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
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

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
