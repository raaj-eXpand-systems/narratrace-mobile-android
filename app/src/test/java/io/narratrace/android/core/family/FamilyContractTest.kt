package io.narratrace.android.core.family

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyContractTest {
    @Test fun `blocked members sort last by status and alphabetically by name`() {
        val active = FamilyMember("a", "a@example.invalid", "owner", "active", true, "Maya")
        val pending = FamilyMember("p", "p@example.invalid", "viewer", "pending", false, "Zoe")
        val blocked = FamilyMember("b", "b@example.invalid", "viewer", "blocked", false, "Alex")
        val summary = FamilySummary(members = listOf(pending, active), blockedMembers = listOf(blocked))
        assertEquals(listOf("a", "p", "b"), summary.sortedMembers(false).map { it.id })
        assertEquals(listOf("b", "a", "p"), summary.sortedMembers(true).map { it.id })
        assertEquals(2, summary.members.size)
    }

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
