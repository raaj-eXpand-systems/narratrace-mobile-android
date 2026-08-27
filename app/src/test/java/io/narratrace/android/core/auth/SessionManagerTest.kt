package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

/**
 * Session lifecycle, with emphasis on concurrent refresh.
 *
 * Independent concurrent refreshes are the classic way a mobile client signs people
 * out for no reason: the server rotates the refresh token on every call and
 * invalidates the previous one, so a second rotation kills the first. It reproduces
 * only under load, which is why it is pinned here rather than left to manual testing.
 */
class SessionManagerTest {

    private val now = 1_754_000_000_000L

    private fun session(
        expiresInMinutes: Long = 15,
        lastActiveMinutesAgo: Long = 0,
    ) = MobileSession(
        accessToken = "ntm_at_current",
        refreshToken = "ntm_rt_current",
        accessExpiresAtMillis = now + expiresInMinutes.minutes.inWholeMilliseconds,
        accountId = "account-1",
        lastActiveAtMillis = now - lastActiveMinutesAgo.minutes.inWholeMilliseconds,
    )

    @Test
    fun `parses the instant format the server emits`() {
        assertEquals(1_754_827_200_000L, parseIso8601Millis("2025-08-10T12:00:00.000Z"))
    }

    @Test
    fun `refuses an unparseable instant rather than guessing a token lifetime`() {
        assertEquals(null, parseIso8601Millis("not-a-date"))
        assertEquals(null, parseIso8601Millis(""))
        assertEquals(null, parseIso8601Millis("2026-08-10"))
    }

    @Test
    fun `a valid token is returned without contacting the server`() = runTest {
        val calls = AtomicInteger(0)
        val manager = manager(session(expiresInMinutes = 10)) {
            calls.incrementAndGet()
            ApiResult.Success(TokenPair("a", "r", instant(now + 900_000)), "s")
        }
        assertTrue(manager.accessToken() is TokenLease.Valid)
        assertEquals(0, calls.get())
    }

    @Test
    fun `concurrent callers trigger exactly one refresh`() = runTest {
        val calls = AtomicInteger(0)
        val manager = manager(session(expiresInMinutes = 0)) {
            calls.incrementAndGet()
            ApiResult.Success(TokenPair("ntm_at_new", "ntm_rt_new", instant(now + 900_000)), "s")
        }

        val leases = (1..8).map { async { manager.accessToken() } }.awaitAll()

        // The heart of it: eight simultaneous callers, one rotation.
        assertEquals(1, calls.get())
        assertTrue(leases.all { it is TokenLease.Valid })
        assertTrue(leases.all { (it as TokenLease.Valid).accessToken == "ntm_at_new" })
    }

    @Test
    fun `a rejected refresh token signs out and is never retried`() = runTest {
        val calls = AtomicInteger(0)
        val manager = manager(session(expiresInMinutes = 0)) {
            calls.incrementAndGet()
            ApiResult.Unauthorized("Authentication is required.", "s")
        }
        assertTrue(manager.accessToken() is TokenLease.SignedOut)
        assertTrue(manager.state.value is AuthState.SignedOut)
        // Terminal: a consumed refresh token cannot succeed on a second attempt.
        assertTrue(manager.accessToken() is TokenLease.SignedOut)
        assertEquals(1, calls.get())
    }

    @Test
    fun `being offline keeps the session rather than destroying it`() = runTest {
        val manager = manager(session(expiresInMinutes = 0)) { ApiResult.Offline() }
        assertTrue(manager.accessToken() is TokenLease.Unavailable)
        // Nothing was invalidated. A member on a train must not be signed out by a tunnel.
        assertTrue(manager.state.value is AuthState.Authenticated)
    }

    @Test
    fun `server rejection forces exactly one token rotation`() = runTest {
        val calls = AtomicInteger(0)
        val manager = manager(session(expiresInMinutes = 10)) {
            calls.incrementAndGet()
            ApiResult.Success(TokenPair("ntm_at_recovered", "ntm_rt_recovered", instant(now + 900_000)), "s")
        }

        val lease = manager.recoverFromUnauthorized("ntm_at_current")
        assertEquals("ntm_at_recovered", (lease as TokenLease.Valid).accessToken)
        assertEquals(1, calls.get())
    }

    @Test
    fun `queued rejection reuses a token another request already rotated`() = runTest {
        val calls = AtomicInteger(0)
        val manager = manager(session(expiresInMinutes = 10)) {
            calls.incrementAndGet()
            ApiResult.Success(TokenPair("ntm_at_recovered", "ntm_rt_recovered", instant(now + 900_000)), "s")
        }

        manager.recoverFromUnauthorized("ntm_at_current")
        val lease = manager.recoverFromUnauthorized("ntm_at_current")
        assertEquals("ntm_at_recovered", (lease as TokenLease.Valid).accessToken)
        assertEquals(1, calls.get())
    }

    @Test
    fun `an inactive session locks without discarding the account`() = runTest {
        val manager = manager(session(lastActiveMinutesAgo = 31)) {
            ApiResult.Success(TokenPair("a", "r", instant(now)), "s")
        }
        assertTrue(manager.accessToken() is TokenLease.Locked)
        val state = manager.state.value
        assertTrue(state is AuthState.Locked)
        assertEquals("account-1", (state as AuthState.Locked).accountId)
        assertEquals("ntm_at_current", manager.lifecycleCredential())
    }

    @Test
    fun `an unparseable expiry from the server signs out rather than trusting it`() = runTest {
        val manager = manager(session(expiresInMinutes = 0)) {
            ApiResult.Success(TokenPair("a", "r", "garbage"), "s")
        }
        assertTrue(manager.accessToken() is TokenLease.SignedOut)
    }

    @Test
    fun `adopting an admission result authenticates and persists`() = runTest {
        val manager = manager(session(), restoreFirst = false) { ApiResult.Offline() }
        assertTrue(manager.adopt(TokenPair("ntm_at_x", "ntm_rt_x", instant(now + 900_000)), "account-9"))
        val state = manager.state.value
        assertTrue(state is AuthState.Authenticated)
        assertEquals("account-9", (state as AuthState.Authenticated).session.accountId)
    }

    @Test
    fun `successful closure transition retains only the durable lifecycle credential`() = runTest {
        val manager = manager(session()) { ApiResult.Offline() }

        assertTrue(manager.retainLifecycleCredentialAfterClosure("ntm_at_current"))
        assertEquals("ntm_at_current", manager.lifecycleCredential())
        assertTrue(manager.state.value is AuthState.Authenticated)
        assertTrue(!manager.retainLifecycleCredentialAfterClosure("different-token"))
    }

    @Test
    fun `adoption refuses an expiry it cannot parse`() = runTest {
        val manager = manager(session(), restoreFirst = false) { ApiResult.Offline() }
        assertTrue(!manager.adopt(TokenPair("a", "r", "nonsense"), "account-9"))
    }

    @Test
    fun `terminal account purge stays locked if credential removal fails`() = runTest {
        val manager = manager(session(), failClear = true) { ApiResult.Offline() }
        assertTrue(!manager.purgeAccountSession())
        assertTrue(manager.state.value is AuthState.Authenticated)
    }

    @Test
    fun `terminal account purge removes credentials before signing out`() = runTest {
        val manager = manager(session()) { ApiResult.Offline() }
        assertTrue(manager.purgeAccountSession())
        assertTrue(manager.state.value is AuthState.SignedOut)
        assertEquals(null, manager.lifecycleCredential())
    }

    // ── harness ──────────────────────────────────────────────────────────────

    private fun instant(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).toString()

    // `refresh` is last so trailing-lambda call sites bind to it rather than to a
    // trailing Boolean — which is exactly the mistake this signature previously made.
    private fun manager(
        initial: MobileSession,
        restoreFirst: Boolean = true,
        failClear: Boolean = false,
        refresh: suspend (String) -> ApiResult<TokenPair>,
    ): SessionManager {
        val cipher = object : CredentialCipher {
            override fun encrypt(plaintext: ByteArray) = plaintext
            override fun decrypt(ciphertext: ByteArray) = ciphertext
        }
        var stored: ByteArray? = null
        val blob = object : EncryptedBlobStore {
            override fun read() = stored
            override fun write(bytes: ByteArray): Boolean { stored = bytes; return true }
            override fun clear(): Boolean { if (failClear) return false; stored = null; return true }
        }
        val store = SessionStore(cipher, blob)
        store.save(initial)

        val manager = SessionManager(
            store = store,
            refresher = SessionRefresher { refresh(it) },
            inactivityGate = InactivityGate(),
            clock = { now },
        )
        if (restoreFirst) manager.restore()
        return manager
    }
}
