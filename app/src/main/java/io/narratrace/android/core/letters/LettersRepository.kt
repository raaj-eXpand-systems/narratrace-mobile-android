package io.narratrace.android.core.letters

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.delivery.ArtifactDeliveryRequest
import io.narratrace.android.core.delivery.ArtifactDeliveryValidator
import io.narratrace.android.core.delivery.DeliveryMode
import io.narratrace.android.core.delivery.DeliveryValidationResult
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.media.destructiveFeatureResult
import io.narratrace.android.core.network.ApiResult
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

class LettersRepository(
    private val api: LettersApi,
    private val sessions: SessionManager,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun audio(id: String) = call { api.audio(id, it) }
    suspend fun audioBytes(url: String) = api.audioBytes(url)
    suspend fun attachAudio(id: String, bytes: ByteArray): FeatureResult<AudioPreservation> {
        if (bytes.isEmpty() || bytes.size > 20 * 1024 * 1024) return FeatureResult.Unavailable("Recording must be under 20 MB.")
        return call { api.attachAudio(id, bytes, it) }
    }
    suspend fun letters() = call { api.letters(it) }
    suspend fun letter(id: String) = call { api.letter(id, it) }
    suspend fun deliveries() = call { api.deliveries(it) }
    suspend fun revokeDelivery(id: String) = call { api.revokeDelivery(id, it) }
    suspend fun createArtifactDelivery(
        uploadId: String, recipientName: String, recipientEmail: String?, selfDelivery: Boolean,
        mode: DeliveryMode, localDateTime: LocalDateTime?, deliveryTimezone: String = ZoneId.systemDefault().id,
    ): FeatureResult<ArtifactDeliveryCreation> {
        val name = recipientName.trim()
        if (name.isEmpty() || name.length > 100) return FeatureResult.Unavailable("Enter a recipient name.")
        val zone = runCatching { ZoneId.of(deliveryTimezone) }.getOrNull() ?: return FeatureResult.Unavailable("Choose a valid delivery time zone.")
        val instant = localDateTime?.atZone(zone)?.toInstant()
        val validation = ArtifactDeliveryValidator(clock).validate(ArtifactDeliveryRequest(
            "member@narratrace.invalid", selfDelivery, recipientEmail, mode, instant,
            if (mode == DeliveryMode.LATER) zone.id else null,
            if (mode == DeliveryMode.LATER) localDateTime else null,
        ))
        if (validation is DeliveryValidationResult.Invalid) return FeatureResult.Unavailable(
            if (validation.reason == DeliveryValidationResult.Reason.DELIVERY_TIME_NOT_FUTURE) "Choose a future delivery date and time." else "Check the recipient and delivery choices.",
        )
        return call { api.createDelivery(
            uploadId, name, recipientEmail?.trim()?.lowercase(), selfDelivery,
            if (mode == DeliveryMode.NOW) "now" else "later", instant?.toString(),
            if (mode == DeliveryMode.LATER) zone.id else null,
            if (mode == DeliveryMode.LATER) localDateTime.toString() else null, it,
        ) }
    }
    suspend fun manage(id: String, action: String, email: String? = null): FeatureResult<LetterManagement> {
        if (action !in setOf("resend_verification", "update_recipient_email")) return FeatureResult.Unavailable("Choose a supported Letter action.")
        return call { api.manage(id, action, email, it) }
    }
    suspend fun delete(id: String) = call(destructive = true) { api.delete(id, it) }

    suspend fun create(
        recipientName: String, recipientEmail: String?, selfDelivery: Boolean, subject: String, body: String,
        mode: DeliveryMode, localDateTime: LocalDateTime?, idempotencyKey: String, circleId: String? = null, circleMemberEmail: String? = null, deliveryTimezone: String = ZoneId.systemDefault().id,
    ): FeatureResult<LetterCreation> {
        val name = recipientName.trim(); val title = subject.trim(); val content = body.trim()
        if (name.isEmpty() || name.length > 100 || title.isEmpty() || title.length > 200 || content.isEmpty() || content.length > 10_000) {
            return FeatureResult.Unavailable("Complete the Letter within the supported limits.")
        }
        val zone = runCatching { ZoneId.of(deliveryTimezone) }.getOrNull() ?: return FeatureResult.Unavailable("Choose a valid delivery timezone.")
        val instant = localDateTime?.atZone(zone)?.toInstant()
        val validation = ArtifactDeliveryValidator(clock).validate(ArtifactDeliveryRequest(
            creatorEmail = "member@narratrace.invalid", selfDelivery = selfDelivery || circleId != null,
            recipientEmail = recipientEmail, mode = mode, deliverAt = instant,
            deliverTimezone = if (mode == DeliveryMode.LATER) zone.id else null,
            deliverLocalDateTime = if (mode == DeliveryMode.LATER) localDateTime else null,
        ))
        if (validation is DeliveryValidationResult.Invalid) return FeatureResult.Unavailable(
            if (validation.reason == DeliveryValidationResult.Reason.DELIVERY_TIME_NOT_FUTURE) "Choose a future delivery date and time." else "Check the recipient and delivery choices.",
        )
        return call { token -> api.create(
            name, recipientEmail?.trim()?.lowercase(), selfDelivery, title, content,
            if (mode == DeliveryMode.NOW) "now" else "later", instant?.toString(),
            if (mode == DeliveryMode.LATER) zone.id else null,
            if (mode == DeliveryMode.LATER) localDateTime.toString() else null,
            idempotencyKey, token, circleId, circleMemberEmail,
        ) }
    }

    private suspend fun <T> call(
        destructive: Boolean = false,
        block: suspend (String) -> ApiResult<T>,
    ): FeatureResult<T> {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = block(lease.accessToken)
        if (result is ApiResult.Unauthorized) {
            val recovered = sessions.recoverFromUnauthorized(lease.accessToken)
            if (recovered !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
            result = block(recovered.accessToken)
        }
        if (destructive) return destructiveFeatureResult(result, sessions::signOut)
        return when (result) {
            is ApiResult.Success -> FeatureResult.Success(result.value)
            is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired
            is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference, result is ApiResult.Offline, result is ApiResult.Forbidden, code = (result as? ApiResult.ServerError)?.rawCode ?: (result as? ApiResult.Forbidden)?.rawCode)
        }
    }
}
