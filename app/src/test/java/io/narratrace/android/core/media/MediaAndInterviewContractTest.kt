package io.narratrace.android.core.media

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.narratrace.android.app.requiredLegalAcceptanceComplete

class MediaAndInterviewContractTest {
    @Test fun `interview contract tolerates additive fields`() {
        val list = NarratraceJson.decodeFromString<InterviewList>("""{
          "interviews":[{"id":"i-1","subjectName":"Maya","status":"active","messageCount":2,"createdAt":"now","updatedAt":"now","future":"safe"}],
          "nextCursor":null,"anotherFutureField":true
        }""")
        assertEquals("Maya", list.interviews.single().subjectName)
        assertEquals(2, list.interviews.single().messageCount)
    }

    @Test fun `preservation acknowledgement requires both guarantees`() {
        val verified = PreservationAcknowledgement(true, true)
        val incomplete = PreservationAcknowledgement(true, false)
        assertTrue(verified.originalDurablyStored && verified.integrityVerified)
        assertFalse(incomplete.originalDurablyStored && incomplete.integrityVerified)
    }

    @Test fun `current required acceptance includes separate content rights attestation`() {
        val accepted = LegalAcceptance(
            termsAccepted = true, privacyAcknowledged = true, aiNoticeAcknowledged = false,
            specialCategoryConsent = false, contentRightsAttested = true,
            termsVersion = "2026-08-26", privacyVersion = "2026-06",
            aiNoticeVersion = "2026-08-26", contentRightsVersion = "2026-08-26",
        )
        assertTrue(requiredLegalAcceptanceComplete(accepted))
        assertFalse(requiredLegalAcceptanceComplete(accepted.copy(contentRightsAttested = false)))
    }

    @Test fun `legal choices serialize separately and never bundle optional consent`() {
        val terms = NarratraceJson.encodeToString(LegalAcknowledgement.serializer(), LegalAcknowledgement(acceptTerms = true))
        val rights = NarratraceJson.encodeToString(LegalAcknowledgement.serializer(), LegalAcknowledgement(attestContentRights = true))
        val sensitive = NarratraceJson.encodeToString(LegalAcknowledgement.serializer(), LegalAcknowledgement(specialCategoryConsent = true))
        assertEquals("{\"acceptTerms\":true}", terms)
        assertEquals("{\"attestContentRights\":true}", rights)
        assertEquals("{\"specialCategoryConsent\":true}", sensitive)
    }
}
