package cc.hhhl.client.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiErrorMessageTest {
    @Test
    fun extractsNestedSharkeyErrorMessage() {
        assertEquals(
            "Invalid param.",
            """{"error":{"message":"Invalid param.","code":"INVALID_PARAM"}}"""
                .toSharkeyApiErrorMessage(statusCode = 400),
        )
    }

    @Test
    fun translatesMissingFastifyRouteToEndpointUnavailableMessage() {
        assertEquals(
            "功能端点不可用，当前实例可能不支持此接口",
            """{"message":"Route POST:/api/pages/my not found","error":"Not Found","statusCode":404}"""
                .toSharkeyApiErrorMessage(statusCode = 404),
        )
    }

    @Test
    fun translatesMissingPermissionMessage() {
        assertEquals(
            "当前登录缺少此功能权限，请重新登录一次后再试",
            """{"error":{"message":"Your app does not have the necessary permissions to use this endpoint."}}"""
                .toSharkeyApiErrorMessage(statusCode = 403),
        )
    }

    @Test
    fun fallsBackToTopLevelMessageForPlainServerErrors() {
        assertEquals(
            "temporary failure",
            """{"message":"temporary failure"}""".toSharkeyApiErrorMessage(statusCode = 503),
        )
    }
}
