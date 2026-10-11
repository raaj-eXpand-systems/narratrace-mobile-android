package io.narratrace.android.core.media

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.*
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType

class InterviewExperienceContractTest {
    @Test fun `video selection fails closed without a fresh affirmative server check`() {
        val legacy = NarratraceJson.decodeFromString<RecordingCapacity>("""{"remainingBytes":1000,"audioMaxSeconds":600,"videoMaxSeconds":600}""")
        assertNotNull(legacy.videoSelectionError)
        assertNotNull(legacy.copy(videoAvailabilityChecked = true, videoUnavailableReason = "No credits remain.").videoSelectionError)
        assertNotNull(legacy.copy(videoAvailabilityChecked = true, videoMaxSeconds = 0).videoSelectionError)
        assertNull(legacy.copy(videoAvailabilityChecked = true).videoSelectionError)
    }
    @Test fun `video preflight sends scoped fresh requests and preserves server denial`() = kotlinx.coroutines.test.runTest {
        val requests = mutableListOf<okhttp3.Request>()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val body = """{"ok":true,"data":{"remainingBytes":1000,"audioMaxSeconds":600,"videoMaxSeconds":600,"videoAvailabilityChecked":true,"videoUnavailableReason":"No credits remain."},"meta":{"apiVersion":"1","requestId":"test"}}"""
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("fixture").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = MediaAndInterviewApi(io.narratrace.android.core.network.NarratraceApiClient(baseUrl = "https://example.test", httpClient = http))
        repeat(2) {
            val result = api.capacity("fixture-token", "story") as io.narratrace.android.core.network.ApiResult.Success
            assertEquals("No credits remain.", result.value.videoSelectionError)
        }
        assertEquals(2, requests.size)
        requests.forEach { assertEquals("story", it.url.queryParameter("interviewId")); assertEquals("GET", it.method) }
    }

    @Test fun `schema outage manual retry keeps request identity and sends once per attempt`() = kotlinx.coroutines.test.runTest {
        val requests = mutableListOf<okhttp3.Request>()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val status = if (requests.size == 1) 503 else 200
            val body = if (status == 503) """{"ok":false,"error":{"code":"SERVICE_UNAVAILABLE","message":"Interviews are being updated. Please try again shortly."},"meta":{"apiVersion":"1","requestId":"test"}}"""
                else """{"ok":true,"data":{"message":{"id":"2","role":"assistant","content":"Saved","createdAt":"now","message_kind":"control","control_intent":"pause"},"conversationState":"paused","replayed":true},"meta":{"apiVersion":"1","requestId":"test"}}"""
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(status).message("fixture").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = MediaAndInterviewApi(io.narratrace.android.core.network.NarratraceApiClient(baseUrl = "https://example.test", httpClient = http))
        assertTrue(api.control("story", "pause", "retained-key", "fixture-token") is io.narratrace.android.core.network.ApiResult.ServerError)
        assertEquals(1, requests.size)
        assertTrue(api.control("story", "pause", "retained-key", "fixture-token") is io.narratrace.android.core.network.ApiResult.Success)
        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("retained-key", request.header("Idempotency-Key"))
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            assertEquals("{\"control\":\"pause\"}", buffer.readUtf8())
        }
    }

    @Test fun `legacy answer defaults and aliases never classify story text locally`() {
        val legacy = NarratraceJson.decodeFromString<InterviewMessage>("""{"id":"1","role":"user","content":"stop in my story","createdAt":"now"}""")
        assertTrue(legacy.isAnswer)
        assertFalse(legacy.copy(messageKind = "help").isAnswer)
        assertEquals("control", legacy.copy(messageKind = "answer", message_kind = "control", control_intent = "pause").kind)
    }
    @Test fun `state is server supplied even when control wording changes`() {
        val value = NarratraceJson.decodeFromString<InterviewResponse>("""{"message":{"id":"2","role":"assistant","content":"Changed wording","createdAt":"now","message_kind":"control","control_intent":"pause"},"conversationState":"paused","replayed":true}""")
        assertEquals("paused", value.conversationState)
        assertTrue(value.replayed)
        assertEquals("pause", value.message.control_intent)
        assertEquals(listOf("pause", "change_topic", "help"), value.experience.menuActions.map { it.id })
        assertNull(value.experience.menuActions.last().control)
        assertEquals("companion", value.experience.menuActions.last().destination)
    }
    @Test fun `explicit control has no draft content in payload`() {
        assertEquals("{\"control\":\"resume\"}", NarratraceJson.encodeToString(InterviewControlRequest.serializer(), InterviewControlRequest("resume")))
    }
    @Test fun `blocked owner retains revocation but cannot grant`() {
        val value = NarratraceJson.decodeFromString<KeepsakeMembers>("""{"roleBlocked":true,"familyOwnerName":"Owner","members":[{"accountId":"member","name":"Family","canGrant":false,"photoConsent":true}]}""")
        assertTrue(value.roleBlocked)
        assertTrue(value.members.single().canChange("photos"))
        assertFalse(value.members.single().canChange("chapter"))
    }
}
