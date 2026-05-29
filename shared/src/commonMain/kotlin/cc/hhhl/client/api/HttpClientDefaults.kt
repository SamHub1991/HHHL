package cc.hhhl.client.api

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

internal fun HttpClientConfig<*>.installDefaultHttpTimeouts() {
    install(HttpTimeout) {
        requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MS
        connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MS
        socketTimeoutMillis = DEFAULT_SOCKET_TIMEOUT_MS
    }
}

private const val DEFAULT_REQUEST_TIMEOUT_MS = 15_000L
private const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000L
private const val DEFAULT_SOCKET_TIMEOUT_MS = 15_000L
