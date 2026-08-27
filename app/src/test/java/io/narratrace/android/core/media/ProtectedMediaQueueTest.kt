package io.narratrace.android.core.media

import io.narratrace.android.core.auth.CredentialCipher
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedMediaQueueTest {
    private val cipher = object : CredentialCipher {
        override fun encrypt(plaintext: ByteArray) = byteArrayOf(42) + plaintext.reversedArray()
        override fun decrypt(ciphertext: ByteArray) = ciphertext.takeIf { it.firstOrNull() == 42.toByte() }?.drop(1)?.toByteArray()?.reversedArray()
    }

    @Test fun `stages only ciphertext and restores original bytes`() {
        val directory = Files.createTempDirectory("media-queue").toFile()
        val queue = ProtectedMediaQueue(directory, cipher)
        val original = "private family story".encodeToByteArray()
        val item = queue.enqueue(original, PendingMediaKind.StandaloneAudio, "story.m4a", "audio/mp4")!!

        assertArrayEquals(original, queue.read(item))
        assertFalse(directory.resolve(item.encryptedFilename).readBytes().contentEquals(original))
        assertFalse(directory.resolve("queue.bin").readText().contains("story.m4a"))
    }

    @Test fun `keeps stable retry identity and removes only after acknowledgement`() {
        val directory = Files.createTempDirectory("media-queue").toFile()
        val queue = ProtectedMediaQueue(directory, cipher)
        val item = queue.enqueue(byteArrayOf(1, 2, 3), PendingMediaKind.InterviewAudio, "answer.m4a", "audio/mp4", "interview-1", "retry-1")!!

        queue.markAttempt(item.id)
        assertEquals("retry-1", queue.items().single().idempotencyKey)
        assertEquals(1, queue.items().single().attempts)
        assertTrue(queue.acknowledgeAndRemove(item.id))
        assertTrue(queue.items().isEmpty())
        assertNull(queue.read(item))
    }

    @Test fun `repeated reconciliation warning exposes no protected-content metadata`() {
        val privateName = "family-secret-photo.jpg"
        val item = PendingMedia(
            id = "opaque-id",
            kind = PendingMediaKind.Photo,
            encryptedFilename = "opaque.bin",
            originalFilename = privateName,
            mimeType = "image/jpeg",
            byteCount = 42,
            sha256 = "a".repeat(64),
            idempotencyKey = "retry-key",
            attempts = ProtectedMediaQueue.RECONCILIATION_ATTENTION_ATTEMPTS,
        )

        val warning = protectedUploadAttention(listOf(item))!!

        assertTrue(warning.contains("1 protected upload"))
        assertTrue(warning.contains("encrypted on this device"))
        assertFalse(warning.contains(privateName))
        assertFalse(warning.contains(item.id))
        assertFalse(warning.contains(item.mimeType))
        assertNull(protectedUploadAttention(listOf(item.copy(attempts = 2))))
    }

    @Test fun `rejects empty and path-like input`() {
        val queue = ProtectedMediaQueue(Files.createTempDirectory("media-queue").toFile(), cipher)
        assertNull(queue.enqueue(byteArrayOf(), PendingMediaKind.StandaloneAudio, "a.m4a", "audio/mp4"))
        assertNull(queue.enqueue(byteArrayOf(1), PendingMediaKind.StandaloneAudio, "../a.m4a", "audio/mp4"))
    }

    @Test fun `chunked video is encrypted and supports resumable offset reads`() {
        val directory = Files.createTempDirectory("video-queue").toFile()
        val queue = ProtectedMediaQueue(directory, cipher)
        val original = ByteArray(ProtectedMediaQueue.CHUNK_BYTES + 137) { (it % 251).toByte() }
        val item = queue.enqueueVideoStream(
            original.inputStream(), PendingMediaKind.InterviewVideo, "answer.mp4", "video/mp4", "interview-1",
        )!!

        assertTrue(item.chunked)
        assertArrayEquals(original.copyOfRange(ProtectedMediaQueue.CHUNK_BYTES - 20, ProtectedMediaQueue.CHUNK_BYTES + 80),
            queue.readRange(item, ProtectedMediaQueue.CHUNK_BYTES - 20, 100))
        assertFalse(directory.resolve(item.encryptedFilename).resolve("000000.bin").readBytes().contentEquals(original.copyOfRange(0, ProtectedMediaQueue.CHUNK_BYTES)))
        assertTrue(queue.acknowledgeAndRemove(item.id))
        assertFalse(directory.resolve(item.encryptedFilename).exists())
    }

    @Test fun `account purge removes every staged media artifact`() {
        val directory = Files.createTempDirectory("media-purge").toFile()
        val queue = ProtectedMediaQueue(directory, cipher)
        assertTrue(queue.enqueue("private photo".encodeToByteArray(), PendingMediaKind.Photo, "photo.jpg", "image/jpeg") != null)

        assertTrue(queue.purgeAccountData())
        assertFalse(directory.exists())
        assertTrue(queue.items().isEmpty())
    }
}
