package io.narratrace.android.core.customer

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

@Serializable data class DeliveryContactEnvelope(val contact: DeliveryContact)
@Serializable data class DeliveryContactChallenge(val kind: String, val challengeId: String, val expiresAt: String)
@Serializable private data class ContactAction(val action: String, val email: String? = null, val challengeId: String? = null, val code: String? = null)

class DeliveryContactRepository(private val client: NarratraceApiClient, private val sessions: SessionManager) {
    suspend fun load() = call { client.get(PATH, serializer<DeliveryContactEnvelope>(), it) }
    suspend fun request(email: String): FeatureResult<DeliveryContactChallenge> {
        val address = email.trim()
        if (address.isEmpty() || address.length > 254) return FeatureResult.Unavailable("Enter a valid email address.")
        return call { client.post(PATH, NarratraceJson.encodeToString(ContactAction("request", email = address)), serializer<DeliveryContactChallenge>(), it) }
    }
    suspend fun verify(challengeId: String, code: String): FeatureResult<DeliveryContactEnvelope> {
        if (challengeId.isBlank() || !code.matches(Regex("[0-9]{6}"))) return FeatureResult.Unavailable("Enter the 6-digit code from your email.")
        return call { client.post(PATH, NarratraceJson.encodeToString(ContactAction("verify", challengeId = challengeId, code = code)), serializer<DeliveryContactEnvelope>(), it) }
    }
    private suspend fun <T> call(block: suspend (String) -> ApiResult<T>): FeatureResult<T> {
        val owner = sessions.captureOperationLease() ?: return FeatureResult.AuthenticationRequired
        val token = sessions.accessToken()
        if (!sessions.isCurrent(owner) || token !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = block(token.accessToken)
        if (!sessions.isCurrent(owner)) return FeatureResult.AuthenticationRequired
        if (result is ApiResult.Unauthorized) {
            val refreshed = sessions.recoverFromUnauthorized(token.accessToken)
            if (!sessions.isCurrent(owner) || refreshed !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
            result = block(refreshed.accessToken)
        }
        if (!sessions.isCurrent(owner)) return FeatureResult.AuthenticationRequired
        return when (result) {
            is ApiResult.Success -> FeatureResult.Success(result.value)
            is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired
            is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference)
        }
    }
    private companion object { const val PATH = "/api/v1/account/delivery-contact" }
}
