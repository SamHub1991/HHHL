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
    fun translatesNestedMissingFastifyRouteToEndpointUnavailableMessage() {
        assertEquals(
            "功能端点不可用，当前实例可能不支持此接口",
            """{"error":{"message":"Route POST:/api/chat/rooms/create not found"}}"""
                .toSharkeyApiErrorMessage(statusCode = 404),
        )
    }

    @Test
    fun translatesPlainMissingRouteToEndpointUnavailableMessage() {
        assertEquals(
            "功能端点不可用，当前实例可能不支持此接口",
            "Route POST:/api/chat/rooms/create not found"
                .toSharkeyApiErrorMessage(statusCode = 404),
        )
    }

    @Test
    fun translatesPlainEndpointUnavailableMessage() {
        assertEquals(
            "功能端点不可用，当前实例可能不支持此接口",
            "Endpoint unavailable"
                .toSharkeyApiErrorMessage(statusCode = 404),
        )
    }

    @Test
    fun translatesMissingPermissionMessage() {
        assertEquals(
            "当前登录缺少此功能权限，请检查应用授权或账号权限",
            """{"error":{"message":"Your app does not have the necessary permissions to use this endpoint."}}"""
                .toSharkeyApiErrorMessage(statusCode = 403),
        )
    }

    @Test
    fun detectsForbiddenAuthenticationFailurePayload() {
        assertEquals(
            true,
            """{"error":{"message":"Authentication failed. Please ensure your token is correct.","code":"AUTHENTICATION_FAILED"}}"""
                .isSharkeyAuthenticationFailure(),
        )
    }

    @Test
    fun doesNotTreatMissingPermissionAsAuthenticationFailure() {
        assertEquals(
            false,
            """{"error":{"message":"Your app does not have the necessary permissions to use this endpoint.","code":"PERMISSION_DENIED"}}"""
                .isSharkeyAuthenticationFailure(),
        )
    }

    @Test
    fun translatesGenericForbiddenMessage() {
        assertEquals(
            "当前账号没有执行此操作的权限",
            """{"message":"Forbidden"}""".toSharkeyApiErrorMessage(statusCode = 403),
        )
    }

    @Test
    fun translatesPlainForbiddenMessage() {
        assertEquals(
            "当前账号没有执行此操作的权限",
            "Forbidden".toSharkeyApiErrorMessage(statusCode = 403),
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
