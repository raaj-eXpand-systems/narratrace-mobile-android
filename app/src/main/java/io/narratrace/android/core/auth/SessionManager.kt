package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one capability [SessionManager] needs from the network.
 *
 * Narrower than [AuthApi] on purpose: session rotation is the most correctness-
 * sensitive logic in the client, and depending on a single-method interface means
 * it can be tested exhaustively without a class hierarchy opened up purely to
 * accommodate a test. `AuthApi::refresh` satisfies this directly.
 */
fun interface SessionRefresher {
    suspend fun refresh(refreshToken: String): ApiResult<TokenPair>
}

/** Result of asking for a usable access token. */
sealed interface TokenLease {
    data class Valid(val accessToken: String) : TokenLease
    /** Session lapsed through inactivity. Credentials survive; reauthentication required. */
    data object Locked : TokenLease
    /** No usable session. The member must sign in again. */
    data object SignedOut : TokenLease
    /** The refresh attempt could not reach the server. Nothing was invalidated. */
    data object Unavailable : TokenLease
}

/**
 * Owns the session lifecycle: restore, rotate, lock, and clear.
 *
 * The single most failure-prone part of a mobile client is concurrent refresh.
 * Access tokens live fifteen minutes, so on a home screen that fires several
 * requests at once, all of them will see an expired token in the same instant. If
 * each refreshes independently, the server rotates the refresh token several times
 * and every rotation but one is immediately invalid — the member is signed out
 * while doing nothing wrong, and it reproduces only under load.
 *
 * [refreshMutex] plus the re-check after acquiring it makes refresh single-flight:
 * the first caller rotates, the rest wake to a valid token and use it.
 *
 * Plan §4: refresh at most once per authentication failure, then fail closed.
 */
class SessionManager(
    private val store: SessionStore,
    private val refresher: SessionRefresher,
    private val inactivityGate: InactivityGate = InactivityGate(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val parseInstant: (String) -> Long? = ::parseIso8601Millis,
) {

    private val refreshMutex = Mutex()
    private val _state = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * Restore a session from encrypted storage.
     *
     * A session past its inactivity window becomes [AuthState.Locked] rather than
     * being discarded: the account is known, so the prompt can be a welcome back and
     * local drafts survive. Protected content stays inaccessible either way.
     */
    fun restore(): AuthState {
        val session = store.load()
        val next = when {
            session == null -> AuthState.SignedOut
            inactivityGate.isLapsed(session, clock()) -> AuthState.Locked(session.accountId)
            else -> AuthState.Authenticated(session)
        }
        _state.value = next
        return next
    }

    /**
     * A usable access token, refreshing once if the current one has expired.
     *
     * Callers must not retry on [TokenLease.Unavailable] — a refresh that could not
     * reach the server has invalidated nothing, and hammering it turns a brief
     * network blip into a rate limit.
     */
    suspend fun accessToken(): TokenLease {
        val current = _state.value
        val session = when (current) {
            is AuthState.Authenticated -> current.session
            is AuthState.Locked -> return TokenLease.Locked
            AuthState.SignedOut, AuthState.Restoring -> return TokenLease.SignedOut
        }

        if (inactivityGate.isLapsed(session, clock())) {
            _state.value = AuthState.Locked(session.accountId)
            return TokenLease.Locked
        }

        if (!session.isAccessExpired(clock())) return TokenLease.Valid(session.accessToken)

        return refreshMutex.withLock {
            // Re-read inside the lock. A caller that queued behind the rotation will
            // find a fresh token here and must not rotate again — the refresh token
            // it captured before waiting has already been consumed server-side.
            val latest = (_state.value as? AuthState.Authenticated)?.session
                ?: return@withLock TokenLease.SignedOut
            if (!latest.isAccessExpired(clock())) return@withLock TokenLease.Valid(latest.accessToken)
            rotate(latest)
        }
    }

    /** Refresh once after the server rejects a token that looked valid locally. */
    suspend fun recoverFromUnauthorized(rejectedAccessToken: String): TokenLease =
        refreshMutex.withLock {
            val latest = (_state.value as? AuthState.Authenticated)?.session
                ?: return@withLock TokenLease.SignedOut
            if (latest.accessToken != rejectedAccessToken) {
                return@withLock TokenLease.Valid(latest.accessToken)
            }
            rotate(latest)
        }

    /**
     * Exchange a rotation. Called only while holding [refreshMutex].
     *
     * The server mints access and refresh atomically and will never accept the old
     * refresh token again, so a failure to persist the new pair loses the account.
     * A write failure therefore signs out rather than continuing in memory with
     * credentials that no longer exist on disk.
     */
    private suspend fun rotate(session: MobileSession): TokenLease =
        when (val result = refresher.refresh(session.refreshToken)) {
            is ApiResult.Success -> {
                val expiresAt = parseInstant(result.value.accessExpiresAt)
                if (expiresAt == null) {
                    signOut()
                    TokenLease.SignedOut
                } else {
                    val rotated = session.withRotatedTokens(
                        accessToken = result.value.accessToken,
                        refreshToken = result.value.refreshToken,
                        accessExpiresAtMillis = expiresAt,
                    )
                    if (store.save(rotated)) {
                        _state.value = AuthState.Authenticated(rotated)
                        TokenLease.Valid(rotated.accessToken)
                    } else {
                        signOut()
                        TokenLease.SignedOut
                    }
                }
            }
            // A rejected refresh token is terminal — it cannot be retried, and the
            // server has already invalidated the session.
            is ApiResult.Unauthorized -> {
                signOut()
                TokenLease.SignedOut
            }
            // Offline or a server fault invalidates nothing. Keep the credentials.
            is ApiResult.Failure -> TokenLease.Unavailable
        }

    /** Adopt a session freshly issued by admission. */
    fun adopt(tokens: TokenPair, accountId: String): Boolean {
        val expiresAt = parseInstant(tokens.accessExpiresAt) ?: return false
        val session = MobileSession(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessExpiresAtMillis = expiresAt,
            accountId = accountId,
            lastActiveAtMillis = clock(),
        )
        if (!store.save(session)) return false
        _state.value = AuthState.Authenticated(session)
        return true
    }

    /** Record deliberate member interaction, restarting the inactivity window. */
    fun touch() {
        val current = _state.value as? AuthState.Authenticated ?: return
        val touched = current.session.touched(clock())
        // Persisted so the window survives process death; an app killed in the
        // background must not come back looking freshly active.
        if (store.save(touched)) _state.value = AuthState.Authenticated(touched)
    }

    /**
     * Discard everything. Destroys the Keystore key, so every artefact encrypted
     * under it becomes unreadable — that is what makes revocation immediate rather
     * than dependent on file deletion succeeding.
     */
    fun signOut() {
        store.clear(destroyKey = true)
        _state.value = AuthState.SignedOut
    }

    /** Terminal account deletion must not claim completion while credentials remain. */
    fun purgeAccountSession(): Boolean {
        val purged = store.clear(destroyKey = true)
        if (purged) _state.value = AuthState.SignedOut
        return purged
    }
}

/**
 * Minimal ISO-8601 parsing for the instants this API returns.
 *
 * Deliberately narrow: the server emits `Date.toISOString()`, always UTC with
 * milliseconds and a trailing Z. Anything else returns null and the caller fails
 * closed rather than guessing at a token's lifetime.
 */
fun parseIso8601Millis(value: String): Long? = runCatching {
    java.time.Instant.parse(value).toEpochMilli()
}.getOrNull()
