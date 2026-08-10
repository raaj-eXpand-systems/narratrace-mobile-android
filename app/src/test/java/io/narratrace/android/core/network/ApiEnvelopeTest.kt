package io.narratrace.android.core.network

import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEnvelopeTest {
    @Test
    fun `supported API metadata is accepted`() {
        ApiMeta(
            apiVersion = "1",
            requestId = "request-1",
            supportId = "support-1",
        ).requireSupportedVersion()
    }

    @Test
    fun `unknown API version fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiMeta(
                apiVersion = "2",
                requestId = "request-1",
                supportId = "support-1",
            ).requireSupportedVersion()
        }
    }
}

