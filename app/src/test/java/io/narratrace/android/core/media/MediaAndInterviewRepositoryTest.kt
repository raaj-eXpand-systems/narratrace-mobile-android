package io.narratrace.android.core.media

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAndInterviewRepositoryTest {
    @Test fun `guided media stays queued when preservation acknowledgement is missing`() {
        val response = decodeResponse(acknowledgement = null)

        assertNull(response.preservationAcknowledgement)
        assertFalse(response.preservationAcknowledgement.permitsLocalRemoval())
    }

    @Test fun `guided media stays queued for every partial preservation acknowledgement`() {
        val acknowledgements = listOf(
            PreservationAcknowledgement(originalDurablyStored = false, integrityVerified = false),
            PreservationAcknowledgement(originalDurablyStored = true, integrityVerified = false),
            PreservationAcknowledgement(originalDurablyStored = false, integrityVerified = true),
        )

        assertTrue(acknowledgements.none { it.permitsLocalRemoval() })
    }

    @Test fun `guided media may be removed only after both preservation guarantees`() {
        val response = decodeResponse(PreservationAcknowledgement(originalDurablyStored = true, integrityVerified = true))

        assertTrue(response.preservationAcknowledgement.permitsLocalRemoval())
    }

    @Test fun `interview audio and video use the same two-part local purge gate`() {
        val partial = PreservationAcknowledgement(originalDurablyStored = true, integrityVerified = false)
        val complete = PreservationAcknowledgement(originalDurablyStored = true, integrityVerified = true)
        val audioPartial = decodeResponse(partial)
        val audioComplete = decodeResponse(complete)
        val videoPartial = VideoPreservationResponse(
            kind = "preserved",
            video = VideoPreservation("media-1", "video-1", "ready", partial),
        )
        val videoComplete = VideoPreservationResponse(
            kind = "preserved",
            video = VideoPreservation("media-1", "video-1", "ready", complete),
        )

        assertFalse(audioPartial.preservationAcknowledgement.permitsLocalRemoval())
        assertFalse(videoPartial.video.preservationAcknowledgement.permitsLocalRemoval())
        assertTrue(audioComplete.preservationAcknowledgement.permitsLocalRemoval())
        assertTrue(videoComplete.video.preservationAcknowledgement.permitsLocalRemoval())
    }

    private fun decodeResponse(acknowledgement: PreservationAcknowledgement?): InterviewResponse {
        val acknowledgementJson = acknowledgement?.let {
            ""","preservationAcknowledgement":{"originalDurablyStored":${it.originalDurablyStored},"integrityVerified":${it.integrityVerified}}"""
        }.orEmpty()
        return NarratraceJson.decodeFromString(
            """{"message":{"id":"message-1","role":"user","content":"","hasMedia":true,"mediaType":"audio","createdAt":"2026-08-27T12:00:00.000Z"}$acknowledgementJson}""",
        )
    }
}
