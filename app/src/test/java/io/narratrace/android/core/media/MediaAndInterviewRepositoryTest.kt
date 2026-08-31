package io.narratrace.android.core.media

import io.narratrace.android.core.network.NarratraceJson
import io.narratrace.android.core.network.ApiErrorCode
import io.narratrace.android.core.network.ApiResult
import org.junit.Assert.assertEquals
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

    @Test fun `quota and archive target failures pause automatic retries`() {
        listOf(
            ApiErrorCode.ARCHIVE_TARGET_REQUIRED,
            ApiErrorCode.PRODUCTION_ALLOWANCE_EXHAUSTED,
            ApiErrorCode.STORAGE_LIMIT_REACHED,
            ApiErrorCode.DUPLICATE_RESOURCE,
        ).forEach { code ->
            val issue = reconciliationIssue(ApiResult.ServerError(
                code, code.name, "Choose a supported next step.", "archiveEntitlementId", 409, "support-1",
            ))
            assertFalse(issue.retryAutomatically)
            assertEquals("support-1", issue.supportReference)
        }
        assertTrue(reconciliationIssue(ApiResult.Offline()).retryAutomatically)
    }

    @Test fun `deletion precondition clears the stale session and returns to authentication`() {
        var cleared = false
        val result = destructiveFeatureResult<Unit>(
            ApiResult.PreconditionRequired(
                "Sign in again before deleting this resource.", "support-delete",
            ),
        ) { cleared = true }

        assertTrue(cleared)
        assertTrue(result is FeatureResult.AuthenticationRequired)
    }

    @Test fun `ordinary deletion failure does not clear a usable session`() {
        var cleared = false
        val result = destructiveFeatureResult<Unit>(ApiResult.Offline()) { cleared = true }

        assertFalse(cleared)
        assertTrue(result is FeatureResult.Unavailable)
    }

    @Test fun `standalone media requests carry archive target and old requests stay additive`() {
        val archiveId = "11111111-2222-4333-8444-555555555555"
        val targeted = PendingMedia(
            "id", PendingMediaKind.Photo, "encrypted.bin", "photo.jpg", "image/jpeg", 42,
            "a".repeat(64), idempotencyKey = "retry", archiveEntitlementId = archiveId,
        )
        val legacy = targeted.copy(archiveEntitlementId = null)

        assertTrue(mobileUploadRequestBody(targeted, "authorize").contains("\"archiveEntitlementId\":\"$archiveId\""))
        assertFalse(mobileUploadRequestBody(legacy, "authorize").contains("archiveEntitlementId"))
        val audio = targeted.copy(
            kind = PendingMediaKind.StandaloneAudio,
            originalFilename = "voice.m4a",
            mimeType = "audio/mp4",
        )
        assertTrue(mobileUploadRequestBody(audio, "confirm", "mobile/account/voice.m4a").contains("\"kind\":\"audio\""))
        assertTrue(mobileUploadRequestBody(audio, "confirm", "mobile/account/voice.m4a").contains(archiveId))
        assertTrue(mobileVideoRequestBody(targeted.copy(kind = PendingMediaKind.StandaloneVideo)).contains(archiveId))
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
