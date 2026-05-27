package cc.hhhl.client.api

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal suspend fun HttpResponse.sharkeyApiErrorMessage(): String? {
    return bodyAsText().toSharkeyApiErrorMessage(status.value)
}

internal suspend fun HttpResponse.isSharkeyUnauthorized(): Boolean {
    if (status == HttpStatusCode.Unauthorized) return true
    if (status != HttpStatusCode.Forbidden) return false
    return runCatching { bodyAsText().isSharkeyAuthenticationFailure() }.getOrDefault(false)
}

internal fun String.toSharkeyApiErrorMessage(statusCode: Int? = null): String? {
    val plainTextMessage = toPlainSharkeyApiErrorMessage(statusCode)
    val root = runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull() ?: return plainTextMessage
    val nestedMessage = root["error"]
        ?.let { runCatching { it.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
    val topLevelMessage = root["message"]
        ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() }

    val messages = listOfNotNull(nestedMessage, topLevelMessage)

    if (statusCode == 404 && messages.any { it.contains("Route ") && it.contains("not found", ignoreCase = true) }) {
        return "功能端点不可用，当前实例可能不支持此接口"
    }
    if (
        statusCode == 403 &&
        messages.any { it.contains("necessary permissions", ignoreCase = true) }
    ) {
        return "当前登录缺少此功能权限，请检查应用授权或账号权限"
    }
    if (statusCode == 403 && messages.any { it.isGenericForbiddenMessage() }) {
        return "当前账号没有执行此操作的权限"
    }
    return nestedMessage ?: topLevelMessage ?: plainTextMessage
}

private fun String.toPlainSharkeyApiErrorMessage(statusCode: Int?): String? {
    val clean = trim().takeIf { it.isNotEmpty() } ?: return null
    if (
        statusCode == 404 &&
        clean.contains("Route ", ignoreCase = true) &&
        clean.contains("not found", ignoreCase = true)
    ) {
        return "功能端点不可用，当前实例可能不支持此接口"
    }
    if (clean.contains("Endpoint unavailable", ignoreCase = true)) {
        return "功能端点不可用，当前实例可能不支持此接口"
    }
    if (statusCode == 403 && clean.contains("necessary permissions", ignoreCase = true)) {
        return "当前登录缺少此功能权限，请检查应用授权或账号权限"
    }
    if (statusCode == 403 && clean.isGenericForbiddenMessage()) {
        return "当前账号没有执行此操作的权限"
    }
    return null
}

internal fun String.isSharkeyAuthenticationFailure(): Boolean {
    val cleanText = trim()
    if (cleanText.isEmpty()) return false

    val root = runCatching { Json.parseToJsonElement(cleanText).jsonObject }.getOrNull()
    val nestedError = root?.get("error")
        ?.let { runCatching { it.jsonObject }.getOrNull() }
    val code = nestedError?.get("code")
        ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.trim()
    val nestedMessage = nestedError?.get("message")
        ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.trim()
    val topLevelMessage = root?.get("message")
        ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.trim()
    val messages = listOfNotNull(nestedMessage, topLevelMessage, cleanText)

    return code.equals("AUTHENTICATION_FAILED", ignoreCase = true) ||
        code.equals("CREDENTIAL_REQUIRED", ignoreCase = true) ||
        messages.any { it.isAuthenticationFailureMessage() }
}

private fun String.isAuthenticationFailureMessage(): Boolean {
    return contains("Authentication failed", ignoreCase = true) ||
        contains("Credential required", ignoreCase = true) ||
        contains("token is correct", ignoreCase = true)
}

private fun String.isGenericForbiddenMessage(): Boolean {
    val normalized = trim().trimEnd('.')
    return normalized.equals("Forbidden", ignoreCase = true) ||
        normalized.equals("Access denied", ignoreCase = true) ||
        normalized.equals("Permission denied", ignoreCase = true)
}
