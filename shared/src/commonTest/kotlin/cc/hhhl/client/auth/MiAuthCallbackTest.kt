package cc.hhhl.client.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MiAuthCallbackTest {
    @Test
    fun parsesCallbackSession() {
        assertEquals(
            "session-123",
            MiAuthCallback.parseSession("hhhl://miauth?session=session-123"),
        )
    }

    @Test
    fun parsesFixedAppCallbackToken() {
        assertEquals(
            "auth-session-token",
            MiAuthCallback.parseSession("hhhl://miauth?token=auth-session-token"),
        )
    }

    @Test
    fun ignoresUnexpectedCallbackHost() {
        assertNull(MiAuthCallback.parseSession("hhhl://other?session=session-123"))
    }
}
