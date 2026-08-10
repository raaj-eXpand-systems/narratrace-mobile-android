package io.narratrace.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Envelope metadata.
 *
 * This previously asserted that `requireSupportedVersion()` threw on an unknown
 * version. Throwing was replaced with a value: version handling is now a decision
 * the caller makes, surfacing as [ApiResult.Unreadable] with copy telling the member
 * to update the app. An exception thrown from a parsed response body would have
 * crashed the app on the day the server moved to v2 — the failure mode the version
 * check exists to prevent.
 */
class ApiEnvelopeTest {

    @Test
    fun `the supported API version is recognised`() {
        val meta = ApiMeta(apiVersion = "1", requestId = "request-1", supportId = "support-1")
        assertTrue(meta.isSupportedVersion)
    }

    @Test
    fun `an unknown API version is reported, never thrown`() {
        val meta = ApiMeta(apiVersion = "2", requestId = "request-1", supportId = "support-1")
        assertFalse(meta.isSupportedVersion)
    }

    @Test
    fun `the support reference prefers supportId`() {
        val meta = ApiMeta(apiVersion = "1", requestId = "request-1", supportId = "support-1")
        assertEquals("support-1", meta.supportReference)
    }

    @Test
    fun `the support reference falls back to requestId`() {
        val meta = ApiMeta(apiVersion = "1", requestId = "request-1", supportId = "")
        assertEquals("request-1", meta.supportReference)
    }

    @Test
    fun `absent metadata does not crash a response that otherwise parsed`() {
        val meta = ApiMeta()
        assertFalse(meta.isSupportedVersion)
        assertEquals("", meta.supportReference)
    }
}
