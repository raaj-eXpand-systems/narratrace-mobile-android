package io.narratrace.android.core.media

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
