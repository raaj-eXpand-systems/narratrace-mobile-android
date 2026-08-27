package io.narratrace.android.core.letters

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LettersContractTest {
    @Test fun `letter detail preserves locked body semantics`() {
        val response = NarratraceJson.decodeFromString<LetterDetailResponse>("""{"letter":{
          "id":"00000000-0000-4000-8000-000000000001","recipientName":"Maya","recipientEmail":"maya@example.com",
          "subject":"For later","unlockAt":"2027-01-01T00:00:00Z","delivered":false,"recipientVerified":false,
          "deliveryState":"pending_verification","createdAt":"2026-08-11T00:00:00Z","hasAudio":false,"isOwner":true,"sharedDeliveryManaged":false,
          "canCancel":true,"unlocked":false,"body":null,"future":"safe"
        }}""")
        assertFalse(response.letter.unlocked)
        assertNull(response.letter.body)
    }

    @Test fun `shared revoked Letter never appears verified or ready`() {
        val list = NarratraceJson.decodeFromString<LetterList>("""{"letters":[{
          "id":"00000000-0000-4000-8000-000000000001","recipientName":"Maya","subject":"For later",
          "unlockAt":"2027-01-01T00:00:00Z","delivered":false,"recipientVerified":false,
          "deliveryState":"revoked","sharedDeliveryManaged":true,"isOwner":true,"createdAt":"2026-08-11T00:00:00Z"
        }]}""")
        val letter = list.letters.single()
        assertFalse(letter.recipientVerified)
        assertFalse(letter.delivered)
        assertEquals("Delivery revoked · recipient cannot access", letterDeliveryStatus(letter.deliveryState, letter.recipientVerified, letter.delivered, letter.unlockAt))
    }

    @Test fun `revoked recipient cannot display content even if an inconsistent payload includes it`() {
        val response = NarratraceJson.decodeFromString<LetterDetailResponse>("""{"letter":{
          "id":"00000000-0000-4000-8000-000000000001","recipientName":"Maya","subject":"For later",
          "unlockAt":"2026-01-01T00:00:00Z","delivered":false,"recipientVerified":false,
          "deliveryState":"revoked","createdAt":"2025-08-11T00:00:00Z","hasAudio":false,"isOwner":false,
          "sharedDeliveryManaged":true,"canCancel":false,"unlocked":true,"body":"must stay hidden"
        }}""")
        assertFalse(response.letter.canDisplayContent())
    }

    @Test fun `Letter delivery presentation fails closed unless the server reports a consistent ready state`() {
        for (state in listOf("failed", "declined", "revoked", "future_state")) {
            assertTrue(letterDeliveryStatus(state, false, false, "later").contains("cannot access"))
        }
        assertEquals("Recipient confirmation pending · no content shared", letterDeliveryStatus("pending_verification", false, false, "later"))
        assertTrue(letterDeliveryStatus("scheduled", false, false, "later").contains("cannot access"))
        assertTrue(letterDeliveryStatus("delivered", false, true, "later").contains("cannot access"))
        assertEquals("Private until later", letterDeliveryStatus("scheduled", true, false, "later"))
        assertEquals("Secure delivery in progress", letterDeliveryStatus("delivering", true, false, "later"))
        assertEquals("Delivered", letterDeliveryStatus("delivered", true, true, "later"))
    }

    @Test fun `delivery contract decodes revocation state`() {
        val list = NarratraceJson.decodeFromString<ArtifactDeliveryList>("""{"deliveries":[{
          "id":"d-1","letterId":"00000000-0000-4000-8000-000000000001","artifactKind":"letter","recipientName":"Maya","recipientEmail":"maya@example.com",
          "selfDelivery":false,"deliverAt":"2027-01-01T00:00:00Z","state":"revoked","revokedAt":"2026-08-11T00:00:00Z"
        }]}""")
        assertEquals("revoked", list.deliveries.single().state)
        assertEquals("00000000-0000-4000-8000-000000000001", list.deliveries.single().letterId)
        assertEquals("2026-08-11T00:00:00Z", list.deliveries.single().revokedAt)
        assertEquals("Revoked · recipient cannot access", list.deliveries.single().statusLabel())
    }
}
