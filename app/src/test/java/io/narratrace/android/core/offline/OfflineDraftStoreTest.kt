package io.narratrace.android.core.offline

import io.narratrace.android.core.auth.CredentialCipher
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDraftStoreTest {
    private val cipher = object : CredentialCipher {
        override fun encrypt(plaintext: ByteArray) = byteArrayOf(7) + plaintext.reversedArray()
        override fun decrypt(ciphertext: ByteArray) = ciphertext.takeIf { it.firstOrNull() == 7.toByte() }?.drop(1)?.toByteArray()?.reversedArray()
    }
    @Test fun `draft content is encrypted and removed only after reconciliation`() {
        val file = Files.createTempDirectory("drafts").resolve("drafts.bin").toFile()
        val store = OfflineDraftStore(file, cipher)
        val draft = OfflineLetterDraft(recipientName = "Maya", subject = "Later", body = "Private story")
        assertTrue(store.save(draft)); assertFalse(file.readText().contains("Private story"))
        assertEquals("Private story", store.load().single().body)
        assertTrue(store.remove(draft.clientDraftId)); assertTrue(store.load().isEmpty())
    }
}
