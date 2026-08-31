package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import io.narratrace.android.core.network.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

/**
 * The admission and session endpoints.
 *
 * Verified against docs/API_CONTRACT_INVENTORY.md §2 and the route sources in
 * narratrace-app/pages/api/v1/auth/. Field names are literal — the server applies no
 * key transformation, so neither may this.
 */

@Serializable
data class AuthChallenge(
    val nonce: String,
    val expiresAt: String,
)

@Serializable
internal data class NativeAdmissionRequest(
    val provider: String = "google",
    /** Android supplies a Credential Manager ID token; iOS supplies an auth code. */
    val idToken: String,
    val nonce: String,
    val inviteCode: String,
    val installationId: String,
    val platform: String = "android",
    val appVersion: String,
    val osVersion: String? = null,
    /** Required only when the customer enabled authenticator protection. */
    val mfaCode: String? = null,
)

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: String,
)

@Serializable
internal data class RefreshRequest(val refreshToken: String)

@Serializable
data class RemoteSession(
    val id: String,
    val platform: String,
    val appVersion: String,
    val osVersion: String? = null,
    val lastActiveAt: String,
    val authenticatedAt: String,
    val expiresAt: String,
    val isCurrent: Boolean,
)

@Serializable
data class SessionList(val sessions: List<RemoteSession>)

@Serializable
internal data class RevokeRequest(val scope: String)

@Serializable
data class RevokeResult(val revoked: Boolean, val scope: String)

@Serializable
data class AuthenticatedAccount(
    val accountId: String,
    val providerSubject: String,
    val email: String,
    val authenticationMethod: String,
)

interface AuthenticationGateway {
    suspend fun challenge(): ApiResult<AuthChallenge>
    suspend fun admit(
        idToken: String,
        nonce: String,
        installationId: String,
        appVersion: String,
        inviteCode: String,
        mfaCode: String? = null,
        osVersion: String? = null,
    ): ApiResult<TokenPair>
    suspend fun me(accessToken: String): ApiResult<AuthenticatedAccount>
}

interface SecurityGateway {
    suspend fun sessions(accessToken: String): ApiResult<List<RemoteSession>>
    suspend fun revoke(accessToken: String, scope: RevokeScope): ApiResult<RevokeResult>
}

class AuthApi(private val client: NarratraceApiClient) : AuthenticationGateway, SecurityGateway {

    /**
     * A one-time server nonce, valid five minutes.
     *
     * The server stores only its SHA-256 hash, and the nonce must be echoed
     * verbatim inside the Google ID token's `nonce` claim. This is what makes an
     * intercepted token useless elsewhere, so it is fetched fresh for every attempt
     * and never cached.
     */
    override suspend fun challenge(): ApiResult<AuthChallenge> =
        client.post("/api/v1/auth/challenge", null, serializer<AuthChallenge>())

    /**
     * Exchange a Google ID token for a Narratrace session.
     *
     * Every refusal returns 401 with copy chosen by the server; the client must not
     * infer a cause beyond what the message says. See the denial table in
     * narratrace-app/pages/api/v1/auth/native.ts.
     */
    override suspend fun admit(
        idToken: String,
        nonce: String,
        installationId: String,
        appVersion: String,
        inviteCode: String,
        mfaCode: String?,
        osVersion: String?,
    ): ApiResult<TokenPair> {
        val body = NarratraceJson.encodeToString(
            NativeAdmissionRequest(
                idToken = idToken,
                nonce = nonce,
                inviteCode = inviteCode,
                installationId = installationId,
                appVersion = appVersion,
                osVersion = osVersion,
                mfaCode = mfaCode,
            ),
        )
        return client.post("/api/v1/auth/native", body, serializer<TokenPair>())
    }

    override suspend fun me(accessToken: String): ApiResult<AuthenticatedAccount> =
        client.get("/api/v1/me", serializer<AuthenticatedAccount>(), accessToken)

    /**
     * Rotate the session.
     *
     * The server mints a new access *and* a new refresh token atomically, and a
     * consumed refresh token can never be replayed. The caller must therefore
     * persist both or neither — a partial write loses the account.
     */
    suspend fun refresh(refreshToken: String): ApiResult<TokenPair> {
        val body = NarratraceJson.encodeToString(RefreshRequest(refreshToken))
        return client.post("/api/v1/auth/refresh", body, serializer<TokenPair>())
    }

    override suspend fun sessions(accessToken: String): ApiResult<List<RemoteSession>> =
        client.get("/api/v1/auth/sessions", serializer<SessionList>(), accessToken)
            .map { it.sessions }

    /**
     * `scope` is `current` or `all`. `current` requires a mobile session.
     *
     * DELETE with a body — unusual, but that is the contract. Sending this as POST
     * would create a session rather than revoke one.
     */
    override suspend fun revoke(accessToken: String, scope: RevokeScope): ApiResult<RevokeResult> {
        val body = NarratraceJson.encodeToString(RevokeRequest(scope.wire))
        return client.delete("/api/v1/auth/sessions", serializer<RevokeResult>(), accessToken, body)
    }
}

enum class RevokeScope(val wire: String) {
    CurrentDevice("current"),
    AllDevices("all"),
}
