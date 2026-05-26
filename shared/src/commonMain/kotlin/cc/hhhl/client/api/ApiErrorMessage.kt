package cc.hhhl.client.api

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal suspend fun HttpResponse.sharkeyApiErrorMessage(): String? {
    return bodyAsText().toSharkeyApiErrorMessage(status.value)
}

internal fun String.toSharkeyApiErrorMessage(statusCode: Int? = null): String? {
    val root = runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull() ?: return null
    val nestedMessage = root["error"]
        ?.let { runCatching { it.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
    val topLevelMessage = root["message"]
        ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() }

    if (statusCode == 404 && topLevelMessage?.contains("Route ") == true) {
        return "功能端点不可用，当前实例可能不支持此接口"
    }
    if (
        statusCode == 403 &&
        listOf(nestedMessage, topLevelMessage).any {
            it?.contains("necessary permissions", ignoreCase = true) == true
        }
    ) {
        return "当前登录缺少此功能权限，请重新登录一次后再试"
    }
    return nestedMessage ?: topLevelMessage
}
