package io.narratrace.android.core.family

import io.narratrace.android.core.network.*
import io.narratrace.android.core.media.FeatureResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class UserBlocksTest {
    @Test fun `block and unblock send contextual JSON and bearer with no cache`() = runTest {
        val requests = mutableListOf<Request>()
        val client = NarratraceApiClient(baseUrl = "https://narratrace.example", httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":{"blocked":true,"changed":false},"meta":{"apiVersion":"1","requestId":"test","supportId":"test"}}""".toResponseBody("application/json".toMediaType())).build()
        }.build())
        val api = UserBlocksApi(client)
        assertTrue(api.block(BlockSource("family", "group-id", "visible@example.invalid"), "fixture-token") is ApiResult.Success)
        api.unblock("account-id", "fixture-token")
        assertEquals(listOf("POST", "DELETE"), requests.map { it.method })
        requests.forEach { assertEquals("/api/v1/account/blocks", it.url.encodedPath); assertEquals("Bearer fixture-token", it.header("Authorization")); assertTrue(it.header("Cache-Control")!!.contains("no-store")) }
        fun body(index: Int) = Buffer().also { requests[index].body!!.writeTo(it) }.readUtf8()
        assertEquals("""{"source":{"kind":"family","id":"group-id","email":"visible@example.invalid"}}""", body(0))
        assertEquals("""{"accountId":"account-id"}""", body(1))
    }

    @Test fun `Circle contextual selectors never include hidden emails`() {
        assertEquals("""{"source":{"kind":"circle","id":"circle-id","memberId":"member-id"}}""",
            NarratraceJson.encodeToString(BlockInput(BlockSource("circle", "circle-id", memberId = "member-id"))))
        assertEquals("""{"source":{"kind":"circle","id":"circle-id","target":"owner"}}""",
            NarratraceJson.encodeToString(BlockInput(BlockSource("circle", "circle-id", target = "owner"))))
    }

    @Test fun `Circle self marker remains backward compatible and keeps email redacted`() {
        val old = """{"id":"member-id","memberEmail":"","status":"active","invitedAt":"now"}"""
        assertFalse(NarratraceJson.decodeFromString<CircleMember>(old).isCurrentUser)
        val current = old.dropLast(1) + """, "isCurrentUser":true}"""
        val member = NarratraceJson.decodeFromString<CircleMember>(current)
        assertTrue(member.isCurrentUser)
        assertEquals("", member.memberEmail)
    }

    @Test fun `outgoing list accepts additive fields without needing private addresses`() {
        val decoded = NarratraceJson.decodeFromString<UserBlockList>("""{"blocks":[{"accountId":"account-id","createdAt":"2026-09-18T00:00:00Z","future":true}]}""")
        assertEquals("account-id", decoded.blocks.single().accountId)
    }

    @Test fun `mutation clears shared state even when repeated and never on failure`() {
        var clears = 0
        val success = FeatureResult.Success(BlockMutation(true, false))
        assertEquals(success, finishBlockMutation(success, true) { clears++ })
        assertEquals(1, clears)
        assertTrue(finishBlockMutation(success, false) { clears++ } is FeatureResult.Unavailable)
        assertEquals(2, clears)
        val failure = FeatureResult.Unavailable("Unavailable")
        assertEquals(failure, finishBlockMutation(failure, true) { clears++ })
        assertEquals(2, clears)
    }
}
