package cc.hhhl.client.auth

import io.ktor.http.Url

object MiAuthCallback {
    fun parseSession(callbackUrl: String?): String? {
        if (callbackUrl.isNullOrBlank()) return null

        val url = runCatching { Url(callbackUrl) }.getOrNull() ?: return null
        if (url.protocol.name != "hhhl" || url.host != "miauth") {
            return null
        }

        return url.parameters["token"]?.takeIf { it.isNotBlank() }
            ?: url.parameters["session"]?.takeIf { it.isNotBlank() }
    }
}
