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
    data class EmailVerificationRequired(val challenge: EmailVerificationChallenge) : SignInResult
    data class MfaEnrollmentRequired(
        val message: String,
        val supportReference: String = "",
    ) : SignInResult
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
    suspend fun signIn(inviteCode: String, mfaCode: String?): SignInResult {
        val normalizedInviteCode = inviteCode.trim().uppercase()
        if (normalizedInviteCode.isBlank()) {
            return SignInResult.Failed("Enter your invitation code to continue.")
        }
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
            inviteCode = normalizedInviteCode,
        )) {
            is ApiResult.Success -> when (val admission = result.value) {
                is NativeAdmissionResult.Authenticated -> admission.tokens
                is NativeAdmissionResult.EmailVerificationRequired -> {
                    return SignInResult.EmailVerificationRequired(admission.challenge)
                }
            }
            is ApiResult.Failure -> return result.toSignInResult()
        }
        return adopt(tokens)
    }

    suspend fun verifyEmailOtp(
        challenge: EmailVerificationChallenge,
        code: String,
    ): SignInResult {
        val normalizedCode = code.filter(Char::isDigit)
        if (normalizedCode.length != 6) {
            return SignInResult.Failed("Enter the 6-digit code sent to your email.")
        }
        val tokens = when (val result = gateway.verifyEmailOtp(challenge, normalizedCode)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return result.toSignInResult()
        }
        return adopt(tokens)
    }

    private suspend fun adopt(tokens: TokenPair): SignInResult {
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

    private fun ApiResult.Failure.toSignInResult(): SignInResult =
        if (this is ApiResult.Unauthorized && fieldName == "mfaEnrollment") {
            SignInResult.MfaEnrollmentRequired(message, supportReference)
        } else {
            toSignInFailure()
        }

    private fun ApiResult.Failure.toSignInFailure(): SignInResult.Failed =
        SignInResult.Failed(message = message, supportReference = supportReference)
}
