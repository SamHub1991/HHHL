package cc.hhhl.client.repository

import cc.hhhl.client.api.AppAuthorizationActionResult
import cc.hhhl.client.api.AppAuthorizationApi
import cc.hhhl.client.api.AppAuthorizationResult
import cc.hhhl.client.model.AppCreateInput
import cc.hhhl.client.model.AuthSession
import cc.hhhl.client.model.AuthSessionDetail
import cc.hhhl.client.model.AuthSessionUserKey
import cc.hhhl.client.model.AuthorizedApp
import cc.hhhl.client.model.MiAuthTokenInput
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AppAuthorizationRepositoryTest {
    @Test
    fun loadPublicAppEndpointsDoNotRequireToken() = runTest {
        val app = sampleApp()
        val repository = AppAuthorizationRepository(
            tokenProvider = { null },
            api = fakeApi(appResult = AppAuthorizationResult.Success(app)),
        )

        assertEquals(
            AppAuthorizationRepositoryResult.Success(app),
            repository.loadCurrentApp(),
        )
        assertEquals(
            AppAuthorizationRepositoryResult.Success(app),
            repository.showApp("app-1"),
        )
        assertEquals(
            AppAuthorizationRepositoryResult.Success(app),
            repository.createApp(sampleCreateInput()),
        )
    }

    @Test
    fun loadMyAppsRequiresToken() = runTest {
        var calls = 0
        val repository = AppAuthorizationRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertEquals(AppAuthorizationRepositoryResult.Unauthorized, repository.loadMyApps())
        assertEquals(0, calls)
    }

    @Test
    fun loadMyAppsMapsSuccess() = runTest {
        val apps = listOf(sampleApp())
        val repository = AppAuthorizationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(appListResult = AppAuthorizationResult.Success(apps)),
        )

        assertEquals(
            AppAuthorizationRepositoryResult.Success(apps),
            repository.loadMyApps(limit = 10, offset = 20),
        )
    }

    @Test
    fun authSessionMethodsMapSuccess() = runTest {
        val session = AuthSession(token = "session-token", url = "https://dc.hhhl.cc/auth")
        val detail = AuthSessionDetail(id = "session-1", app = sampleApp(), token = "session-token")
        val userKey = AuthSessionUserKey(accessToken = "access-1", user = sampleUser())
        val repository = AppAuthorizationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                sessionResult = AppAuthorizationResult.Success(session),
                sessionDetailResult = AppAuthorizationResult.Success(detail),
                userKeyResult = AppAuthorizationResult.Success(userKey),
            ),
        )

        assertEquals(
            AppAuthorizationRepositoryResult.Success(session),
            repository.generateAuthSession("secret-1"),
        )
        assertEquals(
            AppAuthorizationRepositoryResult.Success(detail),
            repository.showAuthSession("session-token"),
        )
        assertEquals(
            AppAuthorizationRepositoryResult.Success(userKey),
            repository.fetchAuthSessionUserKey("secret-1", "session-token"),
        )
    }

    @Test
    fun acceptAuthSessionRequiresToken() = runTest {
        var calls = 0
        val repository = AppAuthorizationRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertEquals(
            AppAuthorizationActionRepositoryResult.Unauthorized,
            repository.acceptAuthSession("session-token"),
        )
        assertEquals(0, calls)
    }

    @Test
    fun generateMiAuthTokenRequiresToken() = runTest {
        val repository = AppAuthorizationRepository(
            tokenProvider = { null },
            api = fakeApi(),
        )

        assertEquals(
            AppAuthorizationRepositoryResult.Unauthorized,
            repository.generateMiAuthToken(sampleMiAuthInput()),
        )
    }

    @Test
    fun mapsNetworkAndServerErrors() = runTest {
        val repository = AppAuthorizationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                appResult = AppAuthorizationResult.NetworkError("timeout"),
                tokenResult = AppAuthorizationResult.ServerError(400, "invalid payload"),
                actionResult = AppAuthorizationActionResult.ServerError(403, "permission denied"),
            ),
        )

        assertEquals(
            AppAuthorizationRepositoryResult.Error("无法连接服务器：timeout"),
            repository.loadCurrentApp(),
        )
        assertEquals(
            AppAuthorizationRepositoryResult.Error("invalid payload"),
            repository.generateMiAuthToken(sampleMiAuthInput()),
        )
        assertEquals(
            AppAuthorizationActionRepositoryResult.Error("permission denied"),
            repository.acceptAuthSession("session-token"),
        )
    }

    @Test
    fun mapsUnauthorizedFromApi() = runTest {
        val repository = AppAuthorizationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(appResult = AppAuthorizationResult.Unauthorized),
        )

        assertIs<AppAuthorizationRepositoryResult.Unauthorized>(repository.loadCurrentApp())
    }

    private fun fakeApi(
        appResult: AppAuthorizationResult<AuthorizedApp> = AppAuthorizationResult.Success(sampleApp()),
        appListResult: AppAuthorizationResult<List<AuthorizedApp>> = AppAuthorizationResult.Success(listOf(sampleApp())),
        sessionResult: AppAuthorizationResult<AuthSession> = AppAuthorizationResult.Success(
            AuthSession(token = "session-token", url = "https://dc.hhhl.cc/auth"),
        ),
        sessionDetailResult: AppAuthorizationResult<AuthSessionDetail> = AppAuthorizationResult.Success(
            AuthSessionDetail(id = "session-1", app = sampleApp(), token = "session-token"),
        ),
        userKeyResult: AppAuthorizationResult<AuthSessionUserKey> = AppAuthorizationResult.Success(
            AuthSessionUserKey(accessToken = "access-1", user = sampleUser()),
        ),
        actionResult: AppAuthorizationActionResult = AppAuthorizationActionResult.Success,
        tokenResult: AppAuthorizationResult<String> = AppAuthorizationResult.Success("generated-token"),
        onCall: () -> Unit = {},
    ): AppAuthorizationApi {
        return object : AppAuthorizationApi {
            override suspend fun loadCurrentApp(): AppAuthorizationResult<AuthorizedApp> {
                onCall()
                return appResult
            }

            override suspend fun showApp(appId: String): AppAuthorizationResult<AuthorizedApp> {
                onCall()
                return appResult
            }

            override suspend fun createApp(input: AppCreateInput): AppAuthorizationResult<AuthorizedApp> {
                onCall()
                return appResult
            }

            override suspend fun loadMyApps(
                token: String,
                limit: Int,
                offset: Int,
            ): AppAuthorizationResult<List<AuthorizedApp>> {
                onCall()
                return appListResult
            }

            override suspend fun generateAuthSession(appSecret: String): AppAuthorizationResult<AuthSession> {
                onCall()
                return sessionResult
            }

            override suspend fun showAuthSession(token: String): AppAuthorizationResult<AuthSessionDetail> {
                onCall()
                return sessionDetailResult
            }

            override suspend fun fetchAuthSessionUserKey(
                appSecret: String,
                token: String,
            ): AppAuthorizationResult<AuthSessionUserKey> {
                onCall()
                return userKeyResult
            }

            override suspend fun acceptAuthSession(
                token: String,
                sessionToken: String,
            ): AppAuthorizationActionResult {
                onCall()
                return actionResult
            }

            override suspend fun generateMiAuthToken(
                token: String,
                input: MiAuthTokenInput,
            ): AppAuthorizationResult<String> {
                onCall()
                return tokenResult
            }
        }
    }

    private fun sampleApp(): AuthorizedApp {
        return AuthorizedApp(
            id = "app-1",
            name = "HHHL Mobile",
            callbackUrl = "hhhl://miauth",
            permissions = listOf("read:account", "write:notes"),
            secret = "secret-1",
            isAuthorized = true,
        )
    }

    private fun sampleCreateInput(): AppCreateInput {
        return AppCreateInput(
            name = "HHHL Mobile",
            description = "Mobile client",
            permissions = listOf("read:account"),
            callbackUrl = "hhhl://miauth",
        )
    }

    private fun sampleMiAuthInput(): MiAuthTokenInput {
        return MiAuthTokenInput(
            session = null,
            permissions = listOf("read:account"),
            name = "HHHL Mobile",
        )
    }

    private fun sampleUser(): User {
        return User(
            id = "user-1",
            displayName = "Alice",
            username = "alice",
            avatarInitial = "A",
        )
    }
}
