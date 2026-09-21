package io.narratrace.android.core.auth

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A server-issued mobile session.
 *
 * Tokens are opaque strings, never JWTs — the server stores only SHA-256 hashes of
 * them, so nothing here can be introspected locally and nothing should try.
 * Verified against narratrace-app/lib/mobileTokens.ts.
 */
@Serializable
data class MobileSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch milliseconds. The server sends ISO-8601; parsing happens at the edge. */
    val accessExpiresAtMillis: Long,
    /** Stable server account ID. Namespaces every encrypted artefact on this device. */
    val accountId: String,
    /** Last deliberate member interaction, epoch millis. Retained for persisted-session compatibility. */
    val lastActiveAtMillis: Long,
) {
    /**
     * Access tokens live 15 minutes (lib/mobileSession.ts ACCESS_TTL_MS). Treated as
     * expired slightly early so a request that is already in flight when the token
     * lapses does not fail for a reason the member cannot act on.
     */
    fun isAccessExpired(nowMillis: Long): Boolean =
        nowMillis >= accessExpiresAtMillis - EXPIRY_GRACE.inWholeMilliseconds

    fun withRotatedTokens(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
    ): MobileSession = copy(
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessExpiresAtMillis = accessExpiresAtMillis,
    )

    fun touched(nowMillis: Long): MobileSession = copy(lastActiveAtMillis = nowMillis)

    companion object {
        /** Refresh a little before the server would reject the token. */
        val EXPIRY_GRACE: Duration = 1.minutes
    }
}

/**
 * What the app is allowed to do right now.
 *
 * Capture, playback, and every protected read are gated on [Authenticated]. There is
 * deliberately no "probably signed in" state: an unverifiable session is
 * [SignedOut], because showing someone else's memories is worse than an extra
 * sign-in.
 */
sealed interface AuthState {

    /** No credentials, or credentials that could not be decrypted or trusted. */
    data object SignedOut : AuthState

    /** Credentials are being restored from encrypted storage. Show nothing yet. */
    data object Restoring : AuthState

    /** A live session. */
    data class Authenticated(val session: MobileSession) : AuthState

    /**
     * Credentials exist but protected access requires reauthentication.
     *
     * Distinct from [SignedOut] on purpose: the member's account is known, so the
     * prompt can be "welcome back" rather than a cold start, and their in-progress
     * drafts stay on the device. Protected content stays locked either way.
     */
    data class Locked(val accountId: String) : AuthState
}
