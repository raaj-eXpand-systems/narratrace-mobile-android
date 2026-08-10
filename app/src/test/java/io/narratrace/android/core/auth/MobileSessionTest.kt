package io.narratrace.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Session expiry and the inactivity gate.
 *
 * These two rules are what stand between an unattended, unlocked phone and someone
 * else's family memories, so they are tested as pure logic rather than inferred from
 * a device.
 */
class MobileSessionTest {

    private val now = 1_754_000_000_000L

    private fun session(
        expiresInMinutes: Long = 15,
        lastActiveMinutesAgo: Long = 0,
    ) = MobileSession(
        accessToken = "ntm_at_" + "a".repeat(43),
        refreshToken = "ntm_rt_" + "b".repeat(43),
        accessExpiresAtMillis = now + expiresInMinutes.minutes.inWholeMilliseconds,
        accountId = "account-1",
        lastActiveAtMillis = now - lastActiveMinutesAgo.minutes.inWholeMilliseconds,
    )

    @Test
    fun `a fresh access token is not expired`() {
        assertFalse(session(expiresInMinutes = 15).isAccessExpired(now))
    }

    @Test
    fun `an access token is treated as expired shortly before the server would reject it`() {
        // 30 seconds of life left, inside the one-minute grace window.
        val nearlyDone = session(expiresInMinutes = 0).copy(accessExpiresAtMillis = now + 30_000)
        assertTrue(nearlyDone.isAccessExpired(now))
    }

    @Test
    fun `a lapsed access token is expired`() {
        assertTrue(session().copy(accessExpiresAtMillis = now - 1).isAccessExpired(now))
    }

    @Test
    fun `rotation replaces both tokens together`() {
        val rotated = session().withRotatedTokens("ntm_at_new", "ntm_rt_new", now + 900_000)
        assertEquals("ntm_at_new", rotated.accessToken)
        assertEquals("ntm_rt_new", rotated.refreshToken)
        // Identity and activity survive rotation; only credentials change.
        assertEquals("account-1", rotated.accountId)
        assertEquals(session().lastActiveAtMillis, rotated.lastActiveAtMillis)
    }
}

class InactivityGateTest {

    private val now = 1_754_000_000_000L
    private val gate = InactivityGate()

    private fun sessionActive(minutesAgo: Long) = MobileSession(
        accessToken = "a", refreshToken = "r",
        accessExpiresAtMillis = now + 900_000,
        accountId = "account-1",
        lastActiveAtMillis = now - minutesAgo.minutes.inWholeMilliseconds,
    )

    @Test
    fun `recent activity keeps the session open`() {
        assertFalse(gate.isLapsed(sessionActive(29), now))
    }

    @Test
    fun `thirty minutes of inactivity locks the session`() {
        assertTrue(gate.isLapsed(sessionActive(30), now))
        assertTrue(gate.isLapsed(sessionActive(31), now))
    }

    @Test
    fun `a clock moving backwards fails closed`() {
        // Timezone change, manual adjustment, or an NTP correction must never read
        // as "recently active" — that would extend an unattended session silently.
        val future = sessionActive(0).copy(lastActiveAtMillis = now + 60_000)
        assertTrue(gate.isLapsed(future, now))
    }

    @Test
    fun `remaining time counts down and floors at zero`() {
        assertEquals(10.minutes.inWholeMilliseconds, gate.millisUntilLapse(sessionActive(20), now))
        assertEquals(0L, gate.millisUntilLapse(sessionActive(45), now))
    }

    @Test
    fun `touching the session restarts the window`() {
        val stale = sessionActive(29)
        assertFalse(gate.isLapsed(stale.touched(now), now + 29.minutes.inWholeMilliseconds))
        assertTrue(gate.isLapsed(stale.touched(now), now + 30.minutes.inWholeMilliseconds))
    }

    @Test
    fun `a shorter timeout is honoured for security-sensitive surfaces`() {
        val strict = InactivityGate(timeout = 5.minutes)
        assertTrue(strict.isLapsed(sessionActive(6), now))
        assertFalse(strict.isLapsed(sessionActive(4), now))
    }
}

class AuthStateTest {

    @Test
    fun `locked retains the account so the prompt can be a welcome back`() {
        val locked = AuthState.Locked(accountId = "account-1")
        assertEquals("account-1", locked.accountId)
    }

    @Test
    fun `signed out and locked are not interchangeable`() {
        val signedOut: AuthState = AuthState.SignedOut
        val locked: AuthState = AuthState.Locked("account-1")
        assertTrue(signedOut !is AuthState.Locked)
        assertTrue(locked !is AuthState.SignedOut)
    }
}
