package io.narratrace.android.core.settings

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsContractTest {
    @Test fun `profile supports English and Hindi preference contract`() {
        val response = NarratraceJson.decodeFromString<ProfileResponse>("""{"profile":{
          "email":"maya@example.com","displayName":"Maya","birthYear":1980,"preferredLanguage":"hi"
        }}""")
        assertEquals("hi", response.profile.preferredLanguage)
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
}
