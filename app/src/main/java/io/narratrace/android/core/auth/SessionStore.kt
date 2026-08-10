package io.narratrace.android.core.auth

import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Where the session bytes live. Separated so tests need no filesystem.
 */
interface EncryptedBlobStore {
    fun read(): ByteArray?
    fun write(bytes: ByteArray): Boolean
    fun clear(): Boolean
}

/**
 * App-private file storage.
 *
 * Must be constructed with `context.filesDir` — never external storage, never the
 * cache directory. `android:allowBackup="false"` and the data-extraction rules keep
 * this out of device transfer and cloud backup, so the ciphertext never leaves the
 * device it was created on.
 */
class FileBlobStore(private val file: File) : EncryptedBlobStore {

    override fun read(): ByteArray? =
        runCatching { if (file.exists()) file.readBytes() else null }.getOrNull()

    override fun write(bytes: ByteArray): Boolean = runCatching {
        // Write to a sibling then rename, so an interrupted write cannot leave a
        // half-written session that decrypts to nothing and locks the member out.
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeBytes(bytes)
        val renamed = temporary.renameTo(file)
        if (!renamed) temporary.delete()
        renamed
    }.getOrDefault(false)

    override fun clear(): Boolean =
        runCatching { !file.exists() || file.delete() }.getOrDefault(false)
}

/**
 * Persists the mobile session, encrypted at rest.
 *
 * Fail-closed rules, all of them deliberate:
 *
 *   - Any decryption failure discards the credentials rather than retrying. GCM is
 *     authenticated, so a failure means absent, corrupt, or tampered-with — and for
 *     all three the correct answer is to sign the member in again.
 *   - A partial write is never persisted; tokens rotate as a pair, and half a
 *     rotation loses the account.
 *   - Clearing destroys the Keystore key as well as the file. Deleting a file can
 *     fail; without the key the bytes are noise regardless.
 */
class SessionStore(
    private val cipher: CredentialCipher,
    private val blobStore: EncryptedBlobStore,
) {

    fun load(): MobileSession? {
        val ciphertext = blobStore.read() ?: return null
        val plaintext = cipher.decrypt(ciphertext) ?: run {
            // Unreadable credentials are not recoverable and must not linger.
            blobStore.clear()
            return null
        }
        return runCatching {
            NarratraceJson.decodeFromString<MobileSession>(plaintext.decodeToString())
        }.getOrElse {
            blobStore.clear()
            null
        }
    }

    fun save(session: MobileSession): Boolean {
        val plaintext = runCatching {
            NarratraceJson.encodeToString(session).encodeToByteArray()
        }.getOrNull() ?: return false
        val ciphertext = cipher.encrypt(plaintext) ?: return false
        return blobStore.write(ciphertext)
    }

    /**
     * Sign-out, session revocation, account change, and authorisation loss all land
     * here. [destroyKey] additionally makes every artefact encrypted under the
     * current key unreadable, which is what makes revocation immediate.
     */
    fun clear(destroyKey: Boolean = true): Boolean {
        val fileCleared = blobStore.clear()
        val keyDestroyed = if (destroyKey && cipher is KeystoreCredentialCipher) {
            cipher.destroyKey()
        } else {
            true
        }
        return fileCleared && keyDestroyed
    }
}
