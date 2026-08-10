package io.narratrace.android.core.delivery

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactDeliveryValidatorTest {
    private val now = Instant.parse("2026-08-07T16:00:00Z")
    private val validator = ArtifactDeliveryValidator(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `self delivery accepts creator email and normalizes it`() {
        val result = validator.validate(
            ArtifactDeliveryRequest(
                creatorEmail = " Person@Example.com ",
                selfDelivery = true,
                recipientEmail = null,
                mode = DeliveryMode.NOW,
                deliverAt = null,
            ),
        )

        assertEquals(
            "person@example.com",
            (result as DeliveryValidationResult.Valid).delivery.recipientEmail,
        )
    }

    @Test
    fun `scheduled delivery rejects the present and the past`() {
        listOf(now, now.minusSeconds(1)).forEach { invalidTime ->
            val result = validator.validate(
                ArtifactDeliveryRequest(
                    creatorEmail = "creator@example.com",
                    selfDelivery = false,
                    recipientEmail = "recipient@example.com",
                    mode = DeliveryMode.LATER,
                    deliverAt = invalidTime,
                ),
            )
            assertEquals(
                DeliveryValidationResult.Invalid(
                    DeliveryValidationResult.Reason.DELIVERY_TIME_NOT_FUTURE,
                ),
                result,
            )
        }
    }

    @Test
    fun `scheduled delivery accepts a future instant`() {
        val result = validator.validate(
            ArtifactDeliveryRequest(
                creatorEmail = "creator@example.com",
                selfDelivery = false,
                recipientEmail = "recipient@example.com",
                mode = DeliveryMode.LATER,
                deliverAt = now.plusSeconds(1),
            ),
        )

        assertTrue(result is DeliveryValidationResult.Valid)
    }
}

