package io.narratrace.android.core.settings

import io.narratrace.android.core.network.NarratraceJson
import io.narratrace.android.core.media.FeatureResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsContractTest {
    @Test fun `profile supports the English and Hindi gift interview preferences`() {
        for (language in listOf("en", "hi")) {
            val response = NarratraceJson.decodeFromString<ProfileResponse>("""{"profile":{
              "email":"maya@example.com","displayName":"Maya","birthYear":1980,"preferredLanguage":"$language"
            }}""")
            assertEquals(language, response.profile.preferredLanguage)
        }
    }

    @Test fun `notification preference snake case fields decode`() {
        val response = NarratraceJson.decodeFromString<PreferencesResponse>("""{"preferences":{
          "processing_ready":true,"invitations":true,"letters":false,"trial_and_billing":true,
          "product_guidance":false,"weekly_memory_nudge":false,"re_engagement":false,
          "yearbook_reminder":false,"interview_anniversary":true
        }}""")
        assertFalse(response.preferences.letters)
        assertEquals(true, response.preferences.interviewAnniversary)
    }

    @Test fun `missing media AI preferences fail closed to off`() {
        val response = NarratraceJson.decodeFromString<MediaAiPreferencesResponse>("""{"preferences":{}}""")
        assertFalse(response.preferences.photoAiInsightsEnabled)
        assertFalse(response.preferences.videoAiInsightsEnabled)
        assertNull(response.disclosure("photo_ai_insights_enabled"))
    }

    private fun consentState() = NarratraceJson.decodeFromString<MediaAiPreferencesResponse>("""{
        "preferences":{"photo_ai_insights_enabled":false,"video_ai_insights_enabled":false},
        "consentVersions":{"photo":"2026-09-04-family-context","video":"2026-09-04"},
        "consentCopy":{"photo":"Allow selected photos and relevant family context to OpenAI in the United States.","video":"Allow selected videos, frames and audio to AI processors."},
        "decisions":{"photo":"not_asked","video":"not_asked"}
    }""")

    @Test fun `purpose-specific server disclosure is preserved and only its reviewed version is submitted`() {
        val state = consentState()
        for (kind in listOf("photo", "video")) {
            val key = "${kind}_ai_insights_enabled"
            val disclosure = state.disclosure(key)!!
            assertEquals(state.consentCopy[kind], disclosure.copy)
            val body = NarratraceJson.parseToJsonElement(mediaAiPreferenceBody(key, true, disclosure)!!).jsonObject
            assertEquals("true", body[key]?.jsonPrimitive?.content)
            assertEquals(setOf(kind), body["consentVersions"]!!.jsonObject.keys)
            assertEquals(state.consentVersions[kind], body["consentVersions"]!!.jsonObject[kind]!!.jsonPrimitive.content)
        }
    }

    @Test fun `missing blank mismatched and unknown disclosures cannot authorize a purpose`() {
        val state = consentState()
        val key = "photo_ai_insights_enabled"
        assertNull(mediaAiPreferenceBody(key, true, null))
        assertNull(mediaAiPreferenceBody(key, true, state.disclosure("video_ai_insights_enabled")))
        assertNull(state.copy(consentCopy = mapOf("photo" to " ")).disclosure(key))
        assertNull(state.copy(consentVersions = emptyMap()).disclosure(key))
        assertNull(mediaAiPreferenceBody("unknown", false, null))
    }

    @Test fun `opt-in rechecks reviewed disclosure and sends exactly one mutation on match`() = runTest {
        val state = consentState()
        var loads = 0; var saves = 0
        val result = updateReviewedMediaAiPreference("photo_ai_insights_enabled", true, state.disclosure("photo_ai_insights_enabled"),
            load = { loads++; FeatureResult.Success(state) }, save = { saves++; FeatureResult.Success(state) })
        assertTrue(result is FeatureResult.Success)
        assertEquals(1, loads); assertEquals(1, saves)
    }

    @Test fun `changed copy or version and unavailable reload never send opt-in`() = runTest {
        val state = consentState()
        val candidates = listOf(
            FeatureResult.Success(state.copy(consentVersions = mapOf("photo" to "new-version"))),
            FeatureResult.Success(state.copy(consentCopy = mapOf("photo" to "New disclosure"))),
            FeatureResult.Success(state.copy(consentCopy = emptyMap())),
            FeatureResult.Unavailable("Service unavailable."),
            FeatureResult.AuthenticationRequired,
        )
        for (current in candidates) {
            var saves = 0
            val result = updateReviewedMediaAiPreference("photo_ai_insights_enabled", true, state.disclosure("photo_ai_insights_enabled"),
                load = { current }, save = { saves++; FeatureResult.Success(state) })
            assertFalse(result is FeatureResult.Success)
            assertEquals(0, saves)
        }
    }

    @Test fun `withdrawal works without disclosures or reload and server rejection is surfaced`() = runTest {
        val state = consentState()
        val body = NarratraceJson.parseToJsonElement(mediaAiPreferenceBody("photo_ai_insights_enabled", false, null)!!).jsonObject
        assertEquals("false", body["photo_ai_insights_enabled"]!!.jsonPrimitive.content)
        assertNull(body["consentVersions"])
        var loads = 0; var saves = 0
        val rejection = FeatureResult.Unavailable("Review the current media AI disclosure before enabling insights.")
        val result = updateReviewedMediaAiPreference("photo_ai_insights_enabled", false, null,
            load = { loads++; FeatureResult.Success(state) }, save = { saves++; rejection })
        assertEquals(rejection, result)
        assertEquals(0, loads); assertEquals(1, saves)
    }
}
