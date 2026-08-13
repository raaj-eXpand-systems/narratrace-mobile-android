package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiResult

fun interface SessionAdopter {
    fun adopt(tokens: TokenPair, accountId: String): Boolean
}

fun interface IdentityTokenRequester {
    suspend fun request(nonce: String): GoogleIdentityResult
}

sealed interface SignInResult {
    data object Authenticated : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String, val supportReference: String = "") : SignInResult
}

/** Runs one native admission attempt. Its server nonce is never cached or reused. */
class AuthenticationCoordinator(
    private val gateway: AuthenticationGateway,
    private val identityTokenRequester: IdentityTokenRequester,
    private val installationIdProvider: InstallationIdProvider,
    private val sessionAdopter: SessionAdopter,
    private val appVersion: String,
    private val osVersion: String,
) {
    suspend fun signIn(mfaCode: String?): SignInResult {
        val installationId = installationIdProvider.installationId()
            ?: return SignInResult.Failed("Narratrace could not prepare secure sign-in on this device.")
        val challenge = when (val result = gateway.challenge()) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result.toSignInFailure()
        }
        val idToken = when (val identity = identityTokenRequester.request(challenge.nonce)) {
            is GoogleIdentityResult.Success -> identity.idToken
            GoogleIdentityResult.Cancelled -> return SignInResult.Cancelled
            GoogleIdentityResult.NoAccountAvailable -> return SignInResult.Failed("No Google account is available for sign-in on this device.")
            GoogleIdentityResult.NotConfigured -> return SignInResult.Failed("Google sign-in is not configured for this build.")
            is GoogleIdentityResult.Failed -> return SignInResult.Failed(identity.reason)
        }
        val tokens = when (val result = gateway.admit(
            idToken = idToken,
            nonce = challenge.nonce,
            installationId = installationId,
            appVersion = appVersion,
            mfaCode = mfaCode?.trim()?.takeIf(String::isNotEmpty),
            osVersion = osVersion,
        )) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result.toSignInFailure()
        }
        val account = when (val result = gateway.me(tokens.accessToken)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result.toSignInFailure()
        }
        return if (sessionAdopter.adopt(tokens, account.accountId)) {
            SignInResult.Authenticated
        } else {
            SignInResult.Failed("Narratrace could not protect the new session on this device.")
        }
    }

    private fun ApiResult.Failure.toSignInFailure(): SignInResult.Failed =
        SignInResult.Failed(message = message, supportReference = supportReference)
}
