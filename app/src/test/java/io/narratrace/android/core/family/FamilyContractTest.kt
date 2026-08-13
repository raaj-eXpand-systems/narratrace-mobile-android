package io.narratrace.android.core.family

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyContractTest {
    @Test fun `family roles and pending membership decode`() {
        val summary = NarratraceJson.decodeFromString<FamilySummary>("""{
          "family":{"id":"f-1","name":"Sharma family","myRole":"owner"},
          "members":[{"id":"m-1","email":"maya@example.com","role":"viewer","status":"pending","isCurrentUser":false}],
          "future":"safe"
        }""")
        assertEquals("owner", summary.family?.myRole)
        assertEquals("pending", summary.members.single().status)
    }

    @Test fun `circle member response may redact email for non-owner`() {
        val detail = NarratraceJson.decodeFromString<CircleDetail>("""{
          "circle":{"id":"c-1","name":"Cousins","role":"member","createdAt":"now"},
          "members":[{"id":"m-1","memberEmail":"","displayName":"Circle member","status":"active","invitedAt":"now"}],
          "sharedInterviewIds":[],"sharedMemories":[],"deliveredLetters":[]
        }""")
        assertTrue(detail.members.single().memberEmail.isEmpty())
        assertEquals("Circle member", detail.members.single().displayName)
    }
}
