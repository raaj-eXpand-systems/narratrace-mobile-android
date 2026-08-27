package io.narratrace.android.core.media

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.narratrace.android.app.requiredLegalAcceptanceComplete
import io.narratrace.android.app.COOKIE_POLICY_URL
import io.narratrace.android.app.ADULT_ACCOUNT_NOTICE
import io.narratrace.android.app.LEGAL_CHANGE_SUMMARY
import io.narratrace.android.app.LEGAL_REVIEW_HEADING
import io.narratrace.android.app.MEDIA_INSIGHTS_HEADING
import io.narratrace.android.app.NIA_DEFINITION
import io.narratrace.android.app.PRIVACY_POLICY_URL
import io.narratrace.android.app.TERMS_POLICY_URL

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
            termsVersion = "2026-08-27.1", privacyVersion = "2026-08-27.2",
            aiNoticeVersion = "2026-08-26.1", contentRightsVersion = "2026-08-26",
        )
        assertTrue(requiredLegalAcceptanceComplete(accepted))
        assertFalse(requiredLegalAcceptanceComplete(accepted.copy(contentRightsAttested = false)))
        assertFalse(requiredLegalAcceptanceComplete(accepted.copy(termsAccepted = false)))
        assertFalse(requiredLegalAcceptanceComplete(accepted.copy(privacyAcknowledged = false)))
    }

    @Test fun `customer policy links use the canonical public routes`() {
        assertEquals("https://getnarratrace.com/terms", TERMS_POLICY_URL)
        assertEquals("https://getnarratrace.com/privacy", PRIVACY_POLICY_URL)
        assertEquals("https://getnarratrace.com/cookies", COOKIE_POLICY_URL)
    }

    @Test fun `native legal and media headings use current customer language`() {
        assertEquals("Review Narratrace Terms", LEGAL_REVIEW_HEADING)
        assertEquals("Nia’s media insights", MEDIA_INSIGHTS_HEADING)
        assertEquals(
            "The Privacy Policy clarifies that optional photo and video insights require your affirmative choice and can be turned off in Account.",
            LEGAL_CHANGE_SUMMARY,
        )
        assertTrue(NIA_DEFINITION.contains("AI assistant"))
        assertTrue(NIA_DEFINITION.contains("not a person"))
        assertTrue(ADULT_ACCOUNT_NOTICE.contains("18 years of age or older"))
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
