package io.narratrace.android.core.auth

import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeAdmissionContractTest {
    @Test
    fun `fresh admission carries the manually entered invitation and no handoff`() {
        val encoded = NarratraceJson.encodeToString(
            NativeAdmissionRequest(
                idToken = "provider-token",
                nonce = "nonce",
                inviteCode = "NRTX-TEST-CODE",
                installationId = "123e4567-e89b-42d3-a456-426614174000",
                appVersion = "1.0.0",
            ),
        )
        val fields = NarratraceJson.parseToJsonElement(encoded).jsonObject

        assertEquals("NRTX-TEST-CODE", fields.getValue("inviteCode").toString().trim('"'))
        assertFalse(fields.containsKey("inviteHandoff"))
    }

    @Test
    fun `fresh admission contains no legacy email otp continuation fields`() {
        val encoded = NarratraceJson.encodeToString(
            NativeAdmissionRequest(
                idToken = "provider-token",
                nonce = "nonce",
                inviteCode = "NRTX-TEST-CODE",
                installationId = "123e4567-e89b-42d3-a456-426614174000",
                appVersion = "1.0.0",
            ),
        )
        val fields = NarratraceJson.parseToJsonElement(encoded).jsonObject

        assertFalse(fields.containsKey("emailOtpContinuation"))
        assertFalse(fields.containsKey("emailOtpToken"))
        assertFalse(fields.containsKey("emailOtpCode"))
    }
}
