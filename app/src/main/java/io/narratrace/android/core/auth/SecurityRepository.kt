package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiResult

sealed interface SecuritySessionsResult {
    data class Success(val sessions: List<RemoteSession>) : SecuritySessionsResult
    data object AuthenticationRequired : SecuritySessionsResult
    data class Unavailable(val message: String, val supportReference: String = "") : SecuritySessionsResult
}

sealed interface RevocationResult {
    data object Revoked : RevocationResult
    data object AuthenticationRequired : RevocationResult
    data class Unavailable(val message: String, val supportReference: String = "") : RevocationResult
}

class SecurityRepository(
    private val api: SecurityGateway,
    private val sessions: SessionManager,
) {
    suspend fun loadSessions(): SecuritySessionsResult {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toSessionsResult()
        return loadWithToken(lease.accessToken, allowRecovery = true)
    }

    suspend fun revoke(scope: RevokeScope): RevocationResult {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toRevocationResult()
        return revokeWithToken(lease.accessToken, scope, allowRecovery = true)
    }

    private suspend fun loadWithToken(token: String, allowRecovery: Boolean): SecuritySessionsResult =
        when (val result = api.sessions(token)) {
            is ApiResult.Success -> SecuritySessionsResult.Success(result.value)
            is ApiResult.Unauthorized -> if (allowRecovery) {
                when (val recovered = sessions.recoverFromUnauthorized(token)) {
                    is TokenLease.Valid -> loadWithToken(recovered.accessToken, allowRecovery = false)
                    else -> recovered.toSessionsResult()
                }
            } else SecuritySessionsResult.AuthenticationRequired
            is ApiResult.Failure -> SecuritySessionsResult.Unavailable(result.message, result.supportReference)
        }

    private suspend fun revokeWithToken(
        token: String,
        scope: RevokeScope,
        allowRecovery: Boolean,
    ): RevocationResult = when (val result = api.revoke(token, scope)) {
        is ApiResult.Success -> {
            sessions.signOut()
            RevocationResult.Revoked
        }
        is ApiResult.Unauthorized -> if (allowRecovery) {
            when (val recovered = sessions.recoverFromUnauthorized(token)) {
                is TokenLease.Valid -> revokeWithToken(recovered.accessToken, scope, allowRecovery = false)
                else -> recovered.toRevocationResult()
            }
        } else {
            sessions.signOut()
            RevocationResult.AuthenticationRequired
        }
        is ApiResult.Failure -> RevocationResult.Unavailable(result.message, result.supportReference)
    }

    private fun TokenLease.toSessionsResult(): SecuritySessionsResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> SecuritySessionsResult.AuthenticationRequired
        TokenLease.Unavailable -> SecuritySessionsResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to load sessions.")
    }

    private fun TokenLease.toRevocationResult(): RevocationResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> RevocationResult.AuthenticationRequired
        TokenLease.Unavailable -> RevocationResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to revoke sessions.")
    }
}
