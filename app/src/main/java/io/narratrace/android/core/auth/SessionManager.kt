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
    /** Protected session is locked; reauthentication required. */
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
    private val clock: () -> Long = System::currentTimeMillis,
    private val parseInstant: (String) -> Long? = ::parseIso8601Millis,
) {

    private val refreshMutex = Mutex()
    // Guarded by this monitor alongside durable writes and state transitions.
    private var sessionGeneration = 0L
    private var previousAccessToken: String? = null
    private val _state = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * Restore a session from encrypted storage.
     *
     * Ordinary inactivity does not discard a customer session. Server expiry,
     * revocation, lifecycle restrictions and sensitive-action verification remain authoritative.
     */
    @Synchronized
    fun restore(): AuthState {
        sessionGeneration++
        previousAccessToken = null
        val session = store.load()
        val next = when {
            session == null -> AuthState.SignedOut
            else -> AuthState.Authenticated(session)
        }
        _state.value = next
        return next
    }

    /**
     * Returns the stored access credential only for the restricted account
     * lifecycle and closure endpoints. It deliberately bypasses access expiry because the server can retain the credential hash solely for the
     * 30-day closure recovery window after ordinary sessions are revoked.
     */
    fun lifecycleCredential(): String? = store.load()?.accessToken

    /** Confirms the just-used credential remains durably available for recovery. */
    fun retainLifecycleCredentialAfterClosure(accessCredential: String): Boolean {
        val current = (_state.value as? AuthState.Authenticated)?.session ?: return false
        if (current.accessToken != accessCredential) return false
        return store.load()?.accessToken == accessCredential
    }

    /**
     * A usable access token, refreshing once if the current one has expired.
     *
     * Callers must not retry on [TokenLease.Unavailable] — a refresh that could not
     * reach the server has invalidated nothing, and hammering it turns a brief
     * network blip into a rate limit.
     */
    suspend fun accessToken(): TokenLease {
        val (current, generation) = synchronized(this) { _state.value to sessionGeneration }
        val session = when (current) {
            is AuthState.Authenticated -> current.session
            is AuthState.Locked -> return TokenLease.Locked
            AuthState.SignedOut, AuthState.Restoring -> return TokenLease.SignedOut
        }

        if (!session.isAccessExpired(clock())) return TokenLease.Valid(session.accessToken)

        return refreshMutex.withLock {
            // Re-check ownership and expiry atomically after waiting for rotation.
            val latest = synchronized(this) {
                if (generation != sessionGeneration) return@withLock TokenLease.Unavailable
                val currentSession = (_state.value as? AuthState.Authenticated)?.session
                    ?: return@withLock TokenLease.SignedOut
                if (!currentSession.isAccessExpired(clock())) return@withLock TokenLease.Valid(currentSession.accessToken)
                currentSession
            }
            rotate(latest)
        }
    }

    /** Refresh once after the server rejects a token that looked valid locally. */
    suspend fun recoverFromUnauthorized(rejectedAccessToken: String): TokenLease {
        val generation = synchronized(this) { sessionGeneration }
        return refreshMutex.withLock {
            val latest = synchronized(this) {
                if (generation != sessionGeneration) return@withLock TokenLease.Unavailable
                val current = (_state.value as? AuthState.Authenticated)?.session
                    ?: return@withLock TokenLease.SignedOut
                if (current.accessToken != rejectedAccessToken) {
                    return@withLock if (previousAccessToken == rejectedAccessToken && !current.isAccessExpired(clock())) {
                        TokenLease.Valid(current.accessToken)
                    } else TokenLease.Unavailable
                }
                current
            }
            rotate(latest)
        }
    }

    /**
     * Exchange a rotation. Called only while holding [refreshMutex].
     *
     * The server mints access and refresh atomically and will never accept the old
     * refresh token again, so a failure to persist the new pair loses the account.
     * A write failure therefore signs out rather than continuing in memory with
     * credentials that no longer exist on disk.
     */
    private suspend fun rotate(session: MobileSession): TokenLease {
        val generation = synchronized(this) { sessionGeneration }
        val result = refresher.refresh(session.refreshToken)
        return synchronized(this) {
            val current = (_state.value as? AuthState.Authenticated)?.session
            if (generation != sessionGeneration || current?.accountId != session.accountId ||
                current.accessToken != session.accessToken || current.refreshToken != session.refreshToken) {
                return@synchronized when (_state.value) {
                    is AuthState.Authenticated -> TokenLease.Unavailable
                    is AuthState.Locked -> TokenLease.Locked
                    else -> TokenLease.SignedOut
                }
            }
            when (result) {
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
                            previousAccessToken = session.accessToken
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
        }
    }

    /** Adopt a session freshly issued by admission. */
    @Synchronized
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
        sessionGeneration++
        previousAccessToken = null
        _state.value = AuthState.Authenticated(session)
        return true
    }

    /** Record deliberate member interaction, retaining the compatible activity timestamp. */
    @Synchronized
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
    @Synchronized
    fun signOut() {
        sessionGeneration++
        previousAccessToken = null
        store.clear(destroyKey = true)
        _state.value = AuthState.SignedOut
    }

    /** Terminal account deletion must not claim completion while credentials remain. */
    @Synchronized
    fun purgeAccountSession(): Boolean {
        sessionGeneration++
        previousAccessToken = null
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
