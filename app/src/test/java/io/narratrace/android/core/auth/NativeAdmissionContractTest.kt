package io.narratrace.android.core.auth

import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `email verification sends only the opaque continuation proof and six digit code`() {
        val encoded = NarratraceJson.encodeToString(
            NativeEmailOtpRequest(
                emailOtpContinuation = "opaque-continuation",
                emailOtpToken = "opaque-otp-token",
                emailOtpCode = "123456",
            ),
        )
        val fields = NarratraceJson.parseToJsonElement(encoded).jsonObject

        assertEquals(setOf("emailOtpContinuation", "emailOtpToken", "emailOtpCode"), fields.keys)
        assertTrue(encoded.contains("123456"))
        assertFalse(encoded.contains("provider-token"))
        assertFalse(encoded.contains("inviteCode"))
    }

    @Test
    fun `email verification required response decodes as a challenge rather than tokens`() {
        val response = NarratraceJson.decodeFromString<NativeAdmissionResponse>(
            """{"status":"email_verification_required","continuationToken":"continuation","emailOtpToken":"otp-token","maskedEmail":"p***@gmail.com","expiresAt":"2026-08-25T20:10:00Z"}""",
        )

        val outcome = response.toAdmissionOutcomeOrNull()

        assertTrue(outcome is NativeAdmissionResult.EmailVerificationRequired)
        assertEquals(
            "p***@gmail.com",
            (outcome as NativeAdmissionResult.EmailVerificationRequired).challenge.maskedEmail,
        )
    }

    @Test
    fun `incomplete native success response fails closed`() {
        val response = NativeAdmissionResponse(accessToken = "access-only")

        assertEquals(null, response.toAdmissionOutcomeOrNull())
    }
}
