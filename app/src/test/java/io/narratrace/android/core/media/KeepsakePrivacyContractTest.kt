package io.narratrace.android.core.media

import io.narratrace.android.core.network.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class KeepsakePrivacyContractTest {
    @Test fun `native privacy controls are reachable without adding native book downloads`() {
        val app = java.io.File("src/main/java/io/narratrace/android/app/NarratraceApp.kt").readText()
        val controls = java.io.File("src/main/java/io/narratrace/android/app/KeepsakePermissions.kt").readText()
        assertTrue(app.contains("item { PublicStoryLinksPanel(container) }"))
        assertTrue(app.contains("item { KeepsakeConsentPanel(container, summary.id) }"))
        assertTrue(app.contains("KeepsakeOmissionsPanel(container)"))
        assertTrue(app.contains("Open Keepsake books on the web"))
        assertTrue(controls.contains("member.canChange(permission)"))
        assertTrue(controls.contains("value = if (saved.value.ok) load() else FeatureResult.Unavailable"))
        assertTrue(controls.contains("value = saved"))
        assertTrue(controls.contains("value = FeatureResult.AuthenticationRequired"))
        assertTrue(controls.contains("inventory.omitted == null"))
        assertTrue(controls.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(controls.contains("copies already downloaded"))
    }

    @Test fun `missing permission flags never grant use and blocked members can still revoke`() {
        val member = NarratraceJson.decodeFromString<KeepsakeMember>("""{"accountId":"member","name":"Maya"}""")
        assertFalse(member.canChange("chapter"))
        assertFalse(member.canChange("photos"))
        assertTrue(member.copy(chapterConsent = true).canChange("chapter"))
        assertFalse(member.copy(chapterConsent = true).canChange("photos"))
        assertTrue(member.copy(canGrant = true).canChange("photos"))
    }

    @Test fun `old keepsake response permits absent omissions but missing inventories fail closed`() {
        assertNull(NarratraceJson.decodeFromString<KeepsakeOmissions>("""{"books":[],"subjects":[]}""").omitted)
        assertTrue(NarratraceJson.decodeFromString<KeepsakeOmissions>("""{"omitted":[]}""").omitted!!.isEmpty())
        assertTrue(runCatching { NarratraceJson.decodeFromString<PublicStoryLinks>("{}") }.isFailure)
        assertTrue(runCatching { NarratraceJson.decodeFromString<KeepsakeMembers>("{}") }.isFailure)
        val links = NarratraceJson.decodeFromString<PublicStoryLinks>("""{"links":[{"id":"story","subject_name":"Maya","share_expires_at":null,"future":1}]}""")
        assertNull(links.links.single().created_at)
        assertEquals("Maya", links.links.single().subject_name)
    }

    @Test fun `privacy calls use versioned routes exact mutation bodies and bearer`() = runTest {
        val requests = mutableListOf<Triple<String, String, String>>()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("Bearer fixture", request.header("Authorization"))
            assertEquals("no-store", request.header("Cache-Control"))
            val buffer = Buffer(); request.body?.writeTo(buffer)
            requests.add(Triple(request.method, request.url.encodedPath, buffer.readUtf8()))
            val data = if (request.method != "GET") "{\"ok\":true}" else when {
                request.url.encodedPath.endsWith("public-story-links") -> "{\"links\":[]}"
                request.url.encodedPath.endsWith("keepsake-consent") -> "{\"members\":[]}"
                else -> "{\"omitted\":[{\"id\":\"story\",\"subjectName\":\"Maya\"}]}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":$data,"meta":{"apiVersion":"1","requestId":"r"}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = MediaAndInterviewApi(NarratraceApiClient(baseUrl = "https://www.narratrace.io", httpClient = transport))
        assertTrue(api.publicLinks("fixture") is ApiResult.Success)
        assertTrue(api.revokePublicLink("story", "fixture") is ApiResult.Success)
        assertTrue(api.keepsakeMembers("story", "fixture") is ApiResult.Success)
        assertTrue(api.keepsakePermission("story", "member", "chapter", true, "fixture") is ApiResult.Success)
        assertTrue(api.keepsakePermission("story", "member", "photos", false, "fixture") is ApiResult.Success)
        assertEquals("Maya", (api.keepsakeOmissions("fixture") as ApiResult.Success).value.omitted!!.single().subjectName)
        assertTrue(api.requestKeepsakePermission("story", "fixture") is ApiResult.Success)
        assertEquals(Triple("DELETE", "/api/v1/account/public-story-links", "{\"interviewId\":\"story\"}"), requests[1])
        assertEquals(Triple("POST", "/api/v1/interviews/story/keepsake-consent", "{\"requesterAccountId\":\"member\",\"scope\":\"chapter\"}"), requests[3])
        assertEquals("DELETE", requests[4].first)
        assertTrue(requests[4].third.contains("\"scope\":\"photos\""))
        assertEquals("{\"action\":\"request\"}", requests[6].third)
    }

    @Test fun `non owner and unreadable responses do not become empty permissions`() = runTest {
        for (status in listOf(404, 503, 200)) {
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Unavailable")
                    .body("{}".toResponseBody("application/json".toMediaType())).build()
            }.build()
            val api = MediaAndInterviewApi(NarratraceApiClient(baseUrl = "https://www.narratrace.io", httpClient = transport))
            assertFalse(api.keepsakeMembers("story", "fixture") is ApiResult.Success)
        }
    }
}
