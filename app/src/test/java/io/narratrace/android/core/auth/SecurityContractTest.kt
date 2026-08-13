package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiSuccess
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityContractTest {
    @Test
    fun `session list decodes current device without credential material`() {
        val payload = """
            {"data":{"sessions":[{"id":"session-1","platform":"android","appVersion":"0.1.0","osVersion":"16",
            "lastActiveAt":"2026-08-11T20:00:00.000Z","authenticatedAt":"2026-08-11T19:00:00.000Z",
            "expiresAt":"2026-09-10T19:00:00.000Z","isCurrent":true}]},
            "meta":{"apiVersion":"1","requestId":"request-1","supportId":"support-1"}}
        """.trimIndent()
        val sessions = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<SessionList>()), payload,
        ).data.sessions
        assertEquals(1, sessions.size)
        assertTrue(sessions.single().isCurrent)
        assertEquals("android", sessions.single().platform)
    }

    @Test
    fun `session revocation decodes explicit scope`() {
        val payload = """
            {"data":{"revoked":true,"scope":"all"},
            "meta":{"apiVersion":"1","requestId":"request-2","supportId":"support-2"}}
        """.trimIndent()
        val result = NarratraceJson.decodeFromString(
            ApiSuccess.serializer(serializer<RevokeResult>()), payload,
        ).data
        assertTrue(result.revoked)
        assertEquals("all", result.scope)
    }
}
