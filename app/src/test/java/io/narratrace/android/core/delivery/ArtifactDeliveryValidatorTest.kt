package io.narratrace.android.core.delivery

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactDeliveryValidatorTest {
    private val now = Instant.parse("2026-08-07T16:00:00Z")
    private val validator = ArtifactDeliveryValidator(Clock.fixed(now, ZoneOffset.UTC))
    private val futureLocal = LocalDateTime.parse("2026-08-07T13:00:00")
    private val futureZone = "America/New_York"
    private val futureInstant = futureLocal.atZone(ZoneId.of(futureZone)).toInstant()

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
                    deliverTimezone = "UTC",
                    deliverLocalDateTime = LocalDateTime.ofInstant(invalidTime, ZoneOffset.UTC),
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
                deliverAt = futureInstant,
                deliverTimezone = futureZone,
                deliverLocalDateTime = futureLocal,
            ),
        )

        assertTrue(result is DeliveryValidationResult.Valid)
        val delivery = (result as DeliveryValidationResult.Valid).delivery
        assertEquals(futureZone, delivery.deliverTimezone)
        assertEquals(futureLocal, delivery.deliverLocalDateTime)
    }

    @Test
    fun `scheduled delivery requires an IANA timezone and wall clock value`() {
        val missingZone = validator.validate(
            ArtifactDeliveryRequest(
                creatorEmail = "creator@example.com",
                selfDelivery = true,
                recipientEmail = null,
                mode = DeliveryMode.LATER,
                deliverAt = futureInstant,
                deliverLocalDateTime = futureLocal,
            ),
        )
        assertEquals(
            DeliveryValidationResult.Invalid(
                DeliveryValidationResult.Reason.MISSING_DELIVERY_TIMEZONE,
            ),
            missingZone,
        )

        val invalidZone = validator.validate(
            ArtifactDeliveryRequest(
                creatorEmail = "creator@example.com",
                selfDelivery = true,
                recipientEmail = null,
                mode = DeliveryMode.LATER,
                deliverAt = futureInstant,
                deliverTimezone = "Eastern Time",
                deliverLocalDateTime = futureLocal,
            ),
        )
        assertEquals(
            DeliveryValidationResult.Invalid(
                DeliveryValidationResult.Reason.INVALID_DELIVERY_TIMEZONE,
            ),
            invalidZone,
        )
    }

    @Test
    fun `scheduled delivery rejects an instant that disagrees with the wall clock`() {
        val result = validator.validate(
            ArtifactDeliveryRequest(
                creatorEmail = "creator@example.com",
                selfDelivery = true,
                recipientEmail = null,
                mode = DeliveryMode.LATER,
                deliverAt = futureInstant.plusSeconds(3_600),
                deliverTimezone = futureZone,
                deliverLocalDateTime = futureLocal,
            ),
        )
        assertEquals(
            DeliveryValidationResult.Invalid(
                DeliveryValidationResult.Reason.DELIVERY_TIME_MISMATCH,
            ),
            result,
        )
    }
}
