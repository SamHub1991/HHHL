package cc.hhhl.client.api

import cc.hhhl.client.model.FilterSettings
import cc.hhhl.client.model.NotificationSettings
import cc.hhhl.client.model.PrivacySettings
import cc.hhhl.client.model.SettingsPreferenceUpdate
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.SettingsWebhookCreateInput
import cc.hhhl.client.model.SettingsWebhookUpdateInput
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
import kotlin.test.assertTrue
import cc.hhhl.client.model.SettingsManagementAction
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SharkeySettingsApiTest {
    @Test
    fun loadsPreferencesFromIEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertTrue((request.body as TextContent).text.contains(""""i":"token-123""""))

                respondPreferences()
            },
        )

        val result = api.loadPreferences(" token-123 ")

        assertIs<SettingsPreferencesResult.Success>(result)
        assertEquals(true, result.preferences.privacy.isLocked)
        assertEquals(listOf("reaction"), result.preferences.notifications.mutedTypes)
        assertEquals(listOf("alpha beta", "single"), result.preferences.filters.mutedWords)
        assertEquals(listOf("bad.example"), result.preferences.filters.mutedInstances)
        assertEquals(true, result.preferences.security.twoFactorEnabled)
        assertEquals(false, result.preferences.security.passkeysEnabled)
    }

    @Test
    fun updatesPreferencesThroughIUpdateEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/update", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""isLocked":true"""))
                assertTrue(body.contains(""""mutingNotificationTypes":["reaction"]"""))
                assertTrue(body.contains(""""mutedWords":[["alpha","beta"]]"""))

                respondPreferences()
            },
        )

        val result = api.updatePreferences(
            token = "token-123",
            update = SettingsPreferenceUpdate(
                privacy = PrivacySettings(isLocked = true),
                notifications = NotificationSettings(mutedTypes = listOf("reaction")),
                filters = FilterSettings(mutedWords = listOf("alpha beta")),
            ),
        )

        assertIs<SettingsPreferencesResult.Success>(result)
    }

    @Test
    fun loadsApiTokenCountFromAppsEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/apps", request.url.toString())
                assertTrue((request.body as TextContent).text.contains(""""i":"token-123""""))
                respondJson("""[{"id":"a","createdAt":"2026-01-01T00:00:00.000Z","permission":[],"grantees":[],"rank":null}]""")
            },
        )

        val result = api.loadApiTokens("token-123")

        assertEquals(SettingsCapabilityResult.Count(total = 1), result)
    }

    @Test
    fun loadsSharedAccessCountFromRealEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/shared-access/list", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""limit":100"""))
                assertTrue(body.contains(""""i":"token-123""""))
                respondJson("""[{"id":"sa-1","user":{"id":"u-1","username":"alice"},"permissions":["read:account"],"rank":"admin"}]""")
            },
        )

        val result = api.loadSharedAccess("token-123")

        assertEquals(SettingsCapabilityResult.Count(total = 1), result)
    }

    @Test
    fun loadsWebhookCountAndActiveCount() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/webhooks/list", request.url.toString())
                respondJson(
                    """
                        [
                          {"id":"1","userId":"u","name":"A","on":[],"url":"https://example.com","secret":"","active":true,"latestSentAt":null,"latestStatus":null},
                          {"id":"2","userId":"u","name":"B","on":[],"url":"https://example.net","secret":"","active":false,"latestSentAt":null,"latestStatus":null}
                        ]
                    """.trimIndent(),
                )
            },
        )

        val result = api.loadWebhooks("token-123")

        assertEquals(SettingsCapabilityResult.Count(total = 2, active = 1), result)
    }

    @Test
    fun loadsAuthorizedAppCountFromRealEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/authorized-apps", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""limit":100"""))
                assertTrue(body.contains(""""i":"token-123""""))
                respondJson("""[{"id":"a","name":"Client","callbackUrl":null,"permission":[],"isAuthorized":true}]""")
            },
        )

        val result = api.loadAuthorizedApps("token-123")

        assertEquals(SettingsCapabilityResult.Count(total = 1), result)
    }

    @Test
    fun loadsSigninHistoryCountAndLatestLabel() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            displayTimeZone = TimeZone.of("Asia/Shanghai"),
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/signin-history", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""limit":100"""))
                respondJson(
                    """
                        [
                          {"id":"s1","createdAt":"2026-05-27T15:49:00.000Z","ip":"127.0.0.1","headers":{},"success":true},
                          {"id":"s2","createdAt":"2026-05-26T10:00:00.000Z","ip":"127.0.0.2","headers":{},"success":false}
                        ]
                    """.trimIndent(),
                )
            },
        )

        val result = api.loadSigninHistory("token-123")

        assertIs<SettingsCapabilityResult.Count>(result)
        assertEquals(2, result.total)
        assertEquals("2026-05-27 23:49", result.latestLabel)
    }

    @Test
    fun loadsTwoFactorCapabilityFromAccountPayload() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient {
                respondJson("""{"twoFactorEnabled":true}""")
            },
        )

        assertEquals(SettingsCapabilityResult.Available, api.loadTwoFactorStatus("token-123"))
    }

    @Test
    fun loadsPasskeyCapabilityFromAccountPayload() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient {
                respondJson("""{"usePasswordLessLogin":false}""")
            },
        )

        assertEquals(SettingsCapabilityResult.Count(total = 0), api.loadPasskeys("token-123"))
    }

    @Test
    fun loadsApiTokenManagementSection() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            displayTimeZone = TimeZone.of("Asia/Shanghai"),
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/apps", request.url.toString())
                respondJson(
                    """
                        [
                          {
                            "id":"token-1",
                            "name":"Desktop app",
                            "createdAt":"2026-05-27T10:00:00.000Z",
                            "lastUsedAt":"2026-05-27T15:49:00.000Z",
                            "permission":["read:account","write:notes"],
                            "grantees":[{"name":"Alice"}],
                            "rank":"admin"
                          }
                        ]
                    """.trimIndent(),
                )
            },
        )

        val result = api.loadManagementSection("token-123", SettingsManagementSectionKey.ApiTokens)

        assertIs<SettingsManagementSectionResult.Success>(result)
        assertEquals("访问令牌", result.section.title)
        assertEquals(1, result.section.items.size)
        assertEquals("Desktop app", result.section.items.first().title)
        assertEquals("Alice", result.section.items.first().subtitle)
        assertEquals(listOf("撤销"), result.section.items.first().actions.map { it.label })
        assertEquals(SettingsManagementAction.RevokeToken, result.section.items.first().actions.first().type)
    }

    @Test
    fun revokesApiTokenThroughRevokeTokenEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/revoke-token", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""tokenId":"token-1""""))
                respondJson("""{}""")
            },
        )

        val result = api.revokeApiToken("token-123", "token-1")

        assertEquals(SettingsManagementMutationResult.Success, result)
    }

    @Test
    fun loadsWebhookManagementSectionWithDeleteAction() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/webhooks/list", request.url.toString())
                respondJson(
                    """
                        [
                          {
                            "id":"webhook-1",
                            "userId":"u",
                            "name":"Deploy",
                            "on":["note","reply"],
                            "url":"https://example.com/hook",
                            "secret":"abc",
                            "active":true,
                            "latestSentAt":"2026-05-27T15:49:00.000Z",
                            "latestStatus":200
                          }
                        ]
                    """.trimIndent(),
                )
            },
        )

        val result = api.loadManagementSection("token-123", SettingsManagementSectionKey.Webhooks)

        assertIs<SettingsManagementSectionResult.Success>(result)
        assertEquals(1, result.section.items.size)
        assertEquals(listOf("编辑", "停用", "发送测试", "删除"), result.section.items.first().actions.map { it.label })
        assertEquals(
            listOf(
                SettingsManagementAction.EditWebhook,
                SettingsManagementAction.DisableWebhook,
                SettingsManagementAction.TestWebhook,
                SettingsManagementAction.DeleteWebhook,
            ),
            result.section.items.first().actions.map { it.type },
        )
        assertTrue(result.section.items.first().actions.all { it.enabled })
    }

    @Test
    fun loadsWebhookDetailThroughShowEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/webhooks/show", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""webhookId":"webhook-1""""))
                respondJson(
                    """
                        {
                          "id":"webhook-1",
                          "userId":"u",
                          "name":"Deploy detail",
                          "on":["note","reply"],
                          "url":"https://example.com/detail",
                          "secret":"secret-1",
                          "active":true,
                          "latestSentAt":null,
                          "latestStatus":null
                        }
                    """.trimIndent(),
                )
            },
        )

        val result = api.loadWebhook("token-123", "webhook-1")

        assertIs<SettingsWebhookDetailResult.Success>(result)
        assertEquals("webhook-1", result.webhook.id)
        assertEquals("Deploy detail", result.webhook.name)
        assertEquals("https://example.com/detail", result.webhook.url)
        assertEquals("secret-1", result.webhook.secret)
        assertEquals(listOf("note", "reply"), result.webhook.events)
    }

    @Test
    fun deletesWebhookThroughDeleteEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/webhooks/delete", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""webhookId":"webhook-1""""))
                respondJson("""{}""")
            },
        )

        val result = api.deleteWebhook("token-123", "webhook-1")

        assertEquals(SettingsManagementMutationResult.Success, result)
    }

    @Test
    fun updatesWebhookDetailsThroughRealEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/webhooks/update", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""webhookId":"webhook-1""""))
                assertTrue(body.contains(""""name":"Deploy""""))
                assertTrue(body.contains(""""url":"https://example.com/hook""""))
                assertTrue(body.contains(""""secret":"secret-1""""))
                assertTrue(body.contains(""""on":["note","reply"]"""))
                respondJson("""{}""")
            },
        )

        val result = api.updateWebhook(
            token = "token-123",
            webhookId = "webhook-1",
            input = SettingsWebhookUpdateInput(
                name = " Deploy ",
                url = " https://example.com/hook ",
                secret = " secret-1 ",
                events = listOf("note", "reply", "note"),
            ),
        )

        assertEquals(SettingsManagementMutationResult.Success, result)
    }

    @Test
    fun updatesWebhookActiveThroughRealEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/webhooks/update", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""webhookId":"webhook-1""""))
                assertTrue(body.contains(""""active":false"""))
                respondJson("""{}""")
            },
        )

        val result = api.updateWebhookActive("token-123", "webhook-1", active = false)

        assertEquals(SettingsManagementMutationResult.Success, result)
    }

    @Test
    fun testsWebhookThroughRealEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/webhooks/test", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""webhookId":"webhook-1""""))
                assertTrue(body.contains(""""type":"note""""))
                respondJson("""{}""")
            },
        )

        val result = api.testWebhook("token-123", "webhook-1")

        assertEquals(SettingsManagementMutationResult.Success, result)
    }

    @Test
    fun createsWebhookThroughRealEndpoint() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/i/webhooks/create", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""name":"Deploy""""))
                assertTrue(body.contains(""""url":"https://example.com/hook""""))
                assertTrue(body.contains(""""secret":"secret-1""""))
                assertTrue(body.contains(""""on":["note","reply"]"""))
                respondJson("""{"id":"webhook-1"}""")
            },
        )

        val result = api.createWebhook(
            token = "token-123",
            input = SettingsWebhookCreateInput(
                name = " Deploy ",
                url = " https://example.com/hook ",
                secret = " secret-1 ",
                events = listOf("note", "reply", "note"),
            ),
        )

        assertEquals(SettingsManagementMutationResult.Success, result)
    }

    @Test
    fun doesNotTreatPasswordlessFlagAsTwoFactorState() = runTest {
        val api = SharkeySettingsApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient {
                respondJson(
                    """
                        {
                          "usePasswordLessLogin": true
                        }
                    """.trimIndent(),
                )
            },
        )

        val result = api.loadPreferences("token-123")

        assertIs<SettingsPreferencesResult.Success>(result)
        assertEquals(null, result.preferences.security.twoFactorEnabled)
        assertEquals(true, result.preferences.security.passkeysEnabled)
    }

    private fun MockRequestHandleScope.respondPreferences(): HttpResponseData {
        return respondJson(
            """
                {
                  "isLocked": true,
                  "autoAcceptFollowed": false,
                  "noCrawle": true,
                  "preventAiLearning": true,
                  "publicReactions": false,
                  "mutingNotificationTypes": ["reaction"],
                  "mutedWords": [["alpha", "beta"], "single"],
                  "hardMutedWords": [["hard"]],
                  "mutedInstances": ["bad.example"],
                  "twoFactorEnabled": true,
                  "usePasswordLessLogin": false
                }
            """.trimIndent(),
        )
    }

    private fun MockRequestHandleScope.respondJson(content: String): HttpResponseData {
        return respond(
            content = content,
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
