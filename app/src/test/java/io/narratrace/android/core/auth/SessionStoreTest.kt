package io.narratrace.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Credential persistence, with the cipher and the filesystem both faked.
 *
 * Android Keystore cannot be reached from a JVM test, so [KeystoreCredentialCipher]
 * itself needs instrumentation. What is tested here is every decision *around* the
 * cipher — and those decisions are where credentials actually leak: writing
 * plaintext, keeping bytes that failed to authenticate, or persisting half a token
 * rotation.
 */

/** Reversible stand-in. Never resembles real encryption; it only has to round-trip. */
private class FakeCipher(
    var failEncryption: Boolean = false,
    var failDecryption: Boolean = false,
) : CredentialCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray? =
        if (failEncryption) null else plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

    override fun decrypt(ciphertext: ByteArray): ByteArray? =
        if (failDecryption) null else ciphertext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

private class InMemoryBlobStore(
    var bytes: ByteArray? = null,
    var failWrite: Boolean = false,
) : EncryptedBlobStore {
    var clearCount = 0
    override fun read(): ByteArray? = bytes
    override fun write(newBytes: ByteArray): Boolean {
        if (failWrite) return false
        bytes = newBytes
        return true
    }
    override fun clear(): Boolean {
        clearCount++
        bytes = null
        return true
    }
}

class SessionStoreTest {

    private val session = MobileSession(
        accessToken = "ntm_at_" + "a".repeat(43),
        refreshToken = "ntm_rt_" + "b".repeat(43),
        accessExpiresAtMillis = 1_754_000_900_000L,
        accountId = "account-1",
        lastActiveAtMillis = 1_754_000_000_000L,
    )

    @Test
    fun `a saved session round-trips`() {
        val store = SessionStore(FakeCipher(), InMemoryBlobStore())
        assertTrue(store.save(session))
        assertEquals(session, store.load())
    }

    @Test
    fun `tokens are never written in plaintext`() {
        val blobStore = InMemoryBlobStore()
        SessionStore(FakeCipher(), blobStore).save(session)
        val persisted = blobStore.bytes!!.decodeToString()
        assertFalse(persisted.contains(session.accessToken))
        assertFalse(persisted.contains(session.refreshToken))
        assertFalse(persisted.contains("account-1"))
        assertFalse(persisted.contains("accessToken"))
    }

    @Test
    fun `no session yields null rather than an empty session`() {
        assertNull(SessionStore(FakeCipher(), InMemoryBlobStore()).load())
    }

    @Test
    fun `credentials that fail authentication are discarded, not retried`() {
        val blobStore = InMemoryBlobStore()
        val cipher = FakeCipher()
        val store = SessionStore(cipher, blobStore)
        store.save(session)

        cipher.failDecryption = true
        assertNull(store.load())
        // GCM failure means absent, corrupt, or tampered with. All three are
        // unrecoverable, so the bytes must not linger for a later attempt.
        assertNull(blobStore.bytes)
        assertEquals(1, blobStore.clearCount)
    }

    @Test
    fun `corrupt payload that decrypts to nonsense is discarded`() {
        val blobStore = InMemoryBlobStore(bytes = FakeCipher().encrypt("not json".encodeToByteArray()))
        val store = SessionStore(FakeCipher(), blobStore)
        assertNull(store.load())
        assertNull(blobStore.bytes)
    }

    @Test
    fun `a failed encryption persists nothing`() {
        val blobStore = InMemoryBlobStore()
        val store = SessionStore(FakeCipher(failEncryption = true), blobStore)
        assertFalse(store.save(session))
        assertNull(blobStore.bytes)
    }

    @Test
    fun `a failed write reports failure rather than claiming success`() {
        val store = SessionStore(FakeCipher(), InMemoryBlobStore(failWrite = true))
        assertFalse(store.save(session))
    }

    @Test
    fun `clearing removes the stored session`() {
        val blobStore = InMemoryBlobStore()
        val store = SessionStore(FakeCipher(), blobStore)
        store.save(session)
        assertTrue(store.clear(destroyKey = false))
        assertNull(blobStore.bytes)
        assertNull(store.load())
    }

    @Test
    fun `rotation overwrites both tokens together`() {
        val blobStore = InMemoryBlobStore()
        val store = SessionStore(FakeCipher(), blobStore)
        store.save(session)
        val rotated = session.withRotatedTokens("ntm_at_new", "ntm_rt_new", 1_754_001_800_000L)
        assertTrue(store.save(rotated))

        val loaded = store.load()!!
        assertEquals("ntm_at_new", loaded.accessToken)
        assertEquals("ntm_rt_new", loaded.refreshToken)
        // A consumed refresh token can never be replayed server-side, so a stale
        // one surviving here would lock the member out of their own account.
        assertFalse(loaded.refreshToken == session.refreshToken)
    }
}
