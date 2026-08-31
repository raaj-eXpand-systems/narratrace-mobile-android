package io.narratrace.android.core.family

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.media.destructiveFeatureResult
import io.narratrace.android.core.network.ApiResult

class FamilyRepository(private val api: FamilyApi, private val sessions: SessionManager) {
    suspend fun family() = call { api.family(it) }
    suspend fun circles() = call { api.circles(it) }
    suspend fun circle(id: String) = call { api.circle(id, it) }
    suspend fun createFamily(name: String): FeatureResult<FamilySummary> {
        val clean = name.trim(); if (clean.isEmpty() || clean.length > 100) return FeatureResult.Unavailable("Enter a family name up to 100 characters.")
        return call { api.createFamily(clean, it) }
    }
    suspend fun invite(email: String, role: String): FeatureResult<InvitationResponse> {
        if (role !in setOf("editor", "viewer") || !validEmail(email)) return FeatureResult.Unavailable("Enter a supported sign-in email and choose editor or viewer access.")
        return call { api.invite(email.trim().lowercase(), role, it) }
    }
    suspend fun update(email: String, role: String) = if (role in setOf("editor", "viewer")) call { api.updateMember(email, role, it) } else FeatureResult.Unavailable("Choose editor or viewer access.")
    suspend fun remove(email: String) = call { api.removeMember(email, it) }
    suspend fun decideFamily(tokenValue: String, accept: Boolean) = call { api.decideFamily(tokenValue.trim(), accept, it) }
    suspend fun createCircle(name: String, description: String?): FeatureResult<CircleResponse> {
        val clean = name.trim(); val detail = description?.trim()?.takeIf(String::isNotEmpty)
        if (clean.isEmpty() || clean.length > 80 || (detail?.length ?: 0) > 500) return FeatureResult.Unavailable("Check the Circle name and description.")
        return call { api.createCircle(clean, detail, it) }
    }
    suspend fun circleAction(id: String, action: String, email: String? = null, displayName: String? = null, ids: List<String>? = null) = call { api.circleAction(id, action, email, displayName, ids, it) }
    suspend fun deleteCircle(id: String) = call(destructive = true) { api.deleteCircle(id, it) }
    suspend fun decideCircle(tokenValue: String, accept: Boolean) = call { api.decideCircle(tokenValue.trim(), accept, it) }

    private fun validEmail(value: String): Boolean { val e = value.trim(); return e.length <= 254 && e.count { it == '@' } == 1 && e.substringAfter('@').contains('.') }
    private suspend fun <T> call(destructive: Boolean = false, block: suspend (String) -> ApiResult<T>): FeatureResult<T> {
        val lease = sessions.accessToken(); if (lease !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = block(lease.accessToken)
        if (result is ApiResult.Unauthorized) { val recovered = sessions.recoverFromUnauthorized(lease.accessToken); if (recovered !is TokenLease.Valid) return FeatureResult.AuthenticationRequired; result = block(recovered.accessToken) }
        if (destructive) return destructiveFeatureResult(result, sessions::signOut)
        return when (result) { is ApiResult.Success -> FeatureResult.Success(result.value); is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired; is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference) }
    }
}
