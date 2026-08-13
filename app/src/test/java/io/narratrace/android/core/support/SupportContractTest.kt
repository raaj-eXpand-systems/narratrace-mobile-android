package io.narratrace.android.core.support

import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportContractTest {
    @Test fun `processing contract preserves retry and progress state`() {
        val response = NarratraceJson.decodeFromString(
            serializer<ProcessingDetailResponse>(),
            """{"kind":"processing","job":{"id":"11111111-2222-4333-8444-555555555555","jobType":"audio_transcription","resourceType":"media","resourceId":"m1","state":"needs_attention","progress":42,"failureCategory":"transcription unavailable","canRetry":true,"createdAt":"2026-08-11T10:00:00Z","updatedAt":"2026-08-11T10:01:00Z"}}""",
        )
        assertEquals(42, response.job.progress)
        assertTrue(response.job.canRetry)
        assertEquals("needs_attention", response.job.state)
    }

    @Test fun `feedback screenshot encodes without line-wrapped protected data`() {
        val encoded = NarratraceJson.encodeToString(FeedbackScreenshot("android-screen-capture", "image/png", "YWJj"))
        assertTrue(encoded.contains("image/png"))
        assertFalse(encoded.contains("\n"))
    }

    @Test fun `retry response must explicitly report retried`() {
        val response = NarratraceJson.decodeFromString(serializer<ProcessingRetryResponse>(), """{"kind":"retried"}""")
        assertEquals("retried", response.kind)
    }
}
