package io.narratrace.android.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Authenticated encryption for anything that must not survive in plaintext.
 *
 * An interface rather than a concrete class because Android Keystore only exists on
 * a device — it cannot be reached from a JVM unit test. Everything that *decides*
 * what to encrypt is therefore testable, and only [KeystoreCredentialCipher] itself
 * needs a device to verify.
 */
interface CredentialCipher {
    /** @return ciphertext with the IV prefixed, or null if encryption is unavailable. */
    fun encrypt(plaintext: ByteArray): ByteArray?

    /** @return plaintext, or null if the data was absent, tampered with, or unreadable. */
    fun decrypt(ciphertext: ByteArray): ByteArray?
}

/**
 * AES-256-GCM with a non-exportable Android Keystore key.
 *
 * GCM is authenticated encryption: a modified ciphertext fails to decrypt rather
 * than yielding wrong plaintext. That property is why decryption failure is treated
 * as tampering and answered by discarding the credentials entirely.
 *
 * `setUserAuthenticationRequired` is deliberately NOT set. Requiring a device
 * credential per operation would break background upload of a memory the member
 * already chose to preserve. The inactivity gate provides the session-level
 * protection instead, at a layer where it does not fight the product.
 *
 * `setInvalidatedByBiometricEnrolment` is not applicable without user
 * authentication, but the key IS bound to the device and cannot be extracted, so a
 * copied app-data directory yields nothing.
 */
class KeystoreCredentialCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : CredentialCipher {

    private fun secretKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()
    }.getOrNull()

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // Randomised IV per operation; never reuse a GCM nonce under one key.
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plaintext: ByteArray): ByteArray? = runCatching {
        val key = secretKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected GCM IV length" }
        iv + cipher.doFinal(plaintext)
    }.getOrNull()

    override fun decrypt(ciphertext: ByteArray): ByteArray? = runCatching {
        if (ciphertext.size <= IV_LENGTH) return null
        val key = secretKey() ?: return null
        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val payload = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        cipher.doFinal(payload)
    }.getOrNull()

    /**
     * Destroys the key, rendering every artefact encrypted under it permanently
     * unreadable.
     *
     * This is the sign-out and revocation primitive: deleting files can fail or be
     * interrupted, but without the key the remaining bytes are noise.
     */
    fun destroyKey(): Boolean = runCatching {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(keyAlias)
        true
    }.getOrDefault(false)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_LENGTH_BITS = 128
        private const val IV_LENGTH = 12
        const val DEFAULT_KEY_ALIAS = "io.narratrace.android.credentials.v1"
    }
}
