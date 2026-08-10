package io.narratrace.android.core.delivery

import java.time.Clock
import java.time.Instant

enum class DeliveryMode { NOW, LATER }

data class ArtifactDeliveryRequest(
    val creatorEmail: String,
    val selfDelivery: Boolean,
    val recipientEmail: String?,
    val mode: DeliveryMode,
    val deliverAt: Instant?,
)

data class ValidatedArtifactDelivery(
    val selfDelivery: Boolean,
    val recipientEmail: String,
    val mode: DeliveryMode,
    val deliverAt: Instant,
)

sealed interface DeliveryValidationResult {
    data class Valid(val delivery: ValidatedArtifactDelivery) : DeliveryValidationResult
    data class Invalid(val reason: Reason) : DeliveryValidationResult

    enum class Reason {
        INVALID_CREATOR_EMAIL,
        INVALID_RECIPIENT_EMAIL,
        MISSING_DELIVERY_TIME,
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
        val deliverAt = when (request.mode) {
            DeliveryMode.NOW -> now
            DeliveryMode.LATER -> request.deliverAt
                ?: return DeliveryValidationResult.Invalid(
                    DeliveryValidationResult.Reason.MISSING_DELIVERY_TIME,
                )
        }
        if (request.mode == DeliveryMode.LATER && !deliverAt.isAfter(now)) {
            return DeliveryValidationResult.Invalid(
                DeliveryValidationResult.Reason.DELIVERY_TIME_NOT_FUTURE,
            )
        }
        return DeliveryValidationResult.Valid(
            ValidatedArtifactDelivery(
                selfDelivery = request.selfDelivery,
                recipientEmail = recipient,
                mode = request.mode,
                deliverAt = deliverAt,
            ),
        )
    }

    private fun normalizeEmail(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.takeIf { it.length <= 254 } ?: return null
        val at = normalized.indexOf('@')
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.lastIndex) return null
        val domain = normalized.substring(at + 1)
        if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) return null
        return normalized
    }
}

