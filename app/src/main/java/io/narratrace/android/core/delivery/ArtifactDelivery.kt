package io.narratrace.android.core.delivery

import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class DeliveryMode { NOW, LATER }

data class ArtifactDeliveryRequest(
    val creatorEmail: String,
    val selfDelivery: Boolean,
    val recipientEmail: String?,
    val mode: DeliveryMode,
    val deliverAt: Instant?,
    val deliverTimezone: String? = null,
    val deliverLocalDateTime: LocalDateTime? = null,
)

data class ValidatedArtifactDelivery(
    val selfDelivery: Boolean,
    val recipientEmail: String,
    val mode: DeliveryMode,
    val deliverAt: Instant,
    val deliverTimezone: String?,
    val deliverLocalDateTime: LocalDateTime?,
)

sealed interface DeliveryValidationResult {
    data class Valid(val delivery: ValidatedArtifactDelivery) : DeliveryValidationResult
    data class Invalid(val reason: Reason) : DeliveryValidationResult

    enum class Reason {
        INVALID_CREATOR_EMAIL,
        INVALID_RECIPIENT_EMAIL,
        MISSING_DELIVERY_TIME,
        MISSING_DELIVERY_TIMEZONE,
        INVALID_DELIVERY_TIMEZONE,
        DELIVERY_TIME_MISMATCH,
        DELIVERY_TIME_NOT_FUTURE,
    }
}

class ArtifactDeliveryValidator(private val clock: Clock) {
    fun validate(request: ArtifactDeliveryRequest): DeliveryValidationResult {
        val creator = normalizeEmail(request.creatorEmail)
            ?: return DeliveryValidationResult.Invalid(
                DeliveryValidationResult.Reason.INVALID_CREATOR_EMAIL,
            )
        val recipient = if (request.selfDelivery) creator else normalizeEmail(request.recipientEmail)
            ?: return DeliveryValidationResult.Invalid(
                DeliveryValidationResult.Reason.INVALID_RECIPIENT_EMAIL,
            )
        val now = clock.instant()
        val schedule = when (request.mode) {
            DeliveryMode.NOW -> DeliverySchedule(now, null, null)
            DeliveryMode.LATER -> {
                val deliverAt = request.deliverAt
                    ?: return DeliveryValidationResult.Invalid(
                        DeliveryValidationResult.Reason.MISSING_DELIVERY_TIME,
                    )
                val timezone = request.deliverTimezone
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return DeliveryValidationResult.Invalid(
                        DeliveryValidationResult.Reason.MISSING_DELIVERY_TIMEZONE,
                    )
                val localDateTime = request.deliverLocalDateTime
                    ?: return DeliveryValidationResult.Invalid(
                        DeliveryValidationResult.Reason.MISSING_DELIVERY_TIME,
                    )
                val zone = try {
                    ZoneId.of(timezone)
                } catch (_: DateTimeException) {
                    return DeliveryValidationResult.Invalid(
                        DeliveryValidationResult.Reason.INVALID_DELIVERY_TIMEZONE,
                    )
                }
                val resolvedInstant = localDateTime.atZone(zone).toInstant()
                if (resolvedInstant != deliverAt) {
                    return DeliveryValidationResult.Invalid(
                        DeliveryValidationResult.Reason.DELIVERY_TIME_MISMATCH,
                    )
                }
                DeliverySchedule(deliverAt, zone.id, localDateTime)
            }
        }
        if (request.mode == DeliveryMode.LATER && !schedule.instant.isAfter(now)) {
            return DeliveryValidationResult.Invalid(
                DeliveryValidationResult.Reason.DELIVERY_TIME_NOT_FUTURE,
            )
        }
        return DeliveryValidationResult.Valid(
            ValidatedArtifactDelivery(
                selfDelivery = request.selfDelivery,
                recipientEmail = recipient,
                mode = request.mode,
                deliverAt = schedule.instant,
                deliverTimezone = schedule.timezone,
                deliverLocalDateTime = schedule.localDateTime,
            ),
        )
    }

    private data class DeliverySchedule(
        val instant: Instant,
        val timezone: String?,
        val localDateTime: LocalDateTime?,
    )

    private fun normalizeEmail(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.takeIf { it.length <= 254 } ?: return null
        val at = normalized.indexOf('@')
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.lastIndex) return null
        val domain = normalized.substring(at + 1)
        if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) return null
        return normalized
    }
}
