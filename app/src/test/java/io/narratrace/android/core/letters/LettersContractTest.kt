package io.narratrace.android.core.letters

import io.narratrace.android.core.network.NarratraceJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LettersContractTest {
    @Test fun `letter detail preserves locked body semantics`() {
        val response = NarratraceJson.decodeFromString<LetterDetailResponse>("""{"letter":{
          "id":"00000000-0000-4000-8000-000000000001","recipientName":"Maya","recipientEmail":"maya@example.com",
          "subject":"For later","unlockAt":"2027-01-01T00:00:00Z","delivered":false,"recipientVerified":false,
          "createdAt":"2026-08-11T00:00:00Z","hasAudio":false,"isOwner":true,"sharedDeliveryManaged":false,
          "canCancel":true,"unlocked":false,"body":null,"future":"safe"
        }}""")
        assertFalse(response.letter.unlocked)
        assertNull(response.letter.body)
    }

    @Test fun `delivery contract decodes revocation state`() {
        val list = NarratraceJson.decodeFromString<ArtifactDeliveryList>("""{"deliveries":[{
          "id":"d-1","artifactKind":"video","recipientName":"Maya","recipientEmail":"maya@example.com",
          "selfDelivery":false,"deliverAt":"2027-01-01T00:00:00Z","state":"revoked","revokedAt":"2026-08-11T00:00:00Z"
        }]}""")
        assertEquals("revoked", list.deliveries.single().state)
        assertEquals("2026-08-11T00:00:00Z", list.deliveries.single().revokedAt)
    }
}
