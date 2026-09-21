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

    @Test fun `full delivery intent survives encryption while legacy drafts require review`() {
        val file = Files.createTempDirectory("draft-intent").resolve("drafts.bin").toFile()
        val store = OfflineDraftStore(file, cipher)
        val draft = OfflineLetterDraft(recipientName = "Maya", subject = "Later", body = "Story", recipientEmail = "maya@example.test", selfDelivery = false, deliveryMode = "later", deliveryTimezone = "America/New_York", deliveryLocalDatetime = "2027-11-07T01:30", unlockAt = "2027-11-07T05:30:00Z")
        assertTrue(store.save(draft))
        assertEquals(draft, store.load().single())
        assertTrue(store.load().single().requiresDeliveryReview)
        val legacy = io.narratrace.android.core.network.NarratraceJson.decodeFromString<OfflineLetterDraft>("""{"recipientName":"Maya","subject":"Old","body":"Story"}""")
        assertTrue(legacy.requiresDeliveryReview)
        assertEquals(null, legacy.selfDelivery)
        assertEquals(null, legacy.recipientEmail)
    }

    @Test fun `signed out and other accounts cannot see legacy or owned drafts`() {
        val file = Files.createTempDirectory("draft-owner").resolve("drafts.bin").toFile()
        val legacy = OfflineDraftStore(file, cipher)
        legacy.save(OfflineLetterDraft(recipientName = "Legacy", subject = "Private", body = "Legacy"))
        var owner: String? = "a"
        val store = OfflineDraftStore(file, cipher) { owner }
        assertTrue(store.load().isEmpty())
        assertTrue(store.save(OfflineLetterDraft(recipientName = "A", subject = "A", body = "A")))
        owner = "b"
        assertTrue(store.load().isEmpty())
        assertTrue(store.save(OfflineLetterDraft(recipientName = "B", subject = "B", body = "B")))
        owner = null
        assertTrue(store.load().isEmpty())
        assertFalse(store.save(OfflineLetterDraft(recipientName = "No", subject = "No", body = "No")))
        owner = "a"
        assertEquals("A", store.load().single().body)
        assertTrue(store.purgeAccountData())
        owner = "b"
        assertEquals("B", store.load().single().body)
        assertEquals(2, legacy.load().size)
    }

    @Test fun `account switch during draft purge never removes the new owners index`() {
        val file = Files.createTempDirectory("draft-purge-race").resolve("drafts.bin").toFile()
        var owner: String? = "a"
        var switchDuringRead = false
        var ownerReads = 0
        val racingCipher = object : CredentialCipher {
            override fun encrypt(plaintext: ByteArray) = plaintext.reversedArray()
            override fun decrypt(ciphertext: ByteArray): ByteArray {
                if (switchDuringRead) { switchDuringRead = false; owner = "b" }
                return ciphertext.reversedArray()
            }
        }
        val store = OfflineDraftStore(file, racingCipher) { ownerReads++; owner }
        store.save(OfflineLetterDraft(recipientName = "A", subject = "A", body = "A"))
        owner = "b"
        store.save(OfflineLetterDraft(recipientName = "B", subject = "B", body = "B"))
        owner = "a"; ownerReads = 0; switchDuringRead = true
        assertTrue(store.purgeAccountData())
        assertEquals(1, ownerReads)
        assertEquals("B", store.load().single().body)
        owner = "a"
        assertTrue(store.load().isEmpty())
    }

    @Test fun `account purge removes encrypted drafts`() {
        val file = Files.createTempDirectory("draft-purge").resolve("drafts.bin").toFile()
        val store = OfflineDraftStore(file, cipher)
        assertTrue(store.save(OfflineLetterDraft(recipientName = "Maya", subject = "Later", body = "Private story")))

        assertTrue(store.purgeAccountData())
        assertFalse(file.exists())
        assertTrue(store.load().isEmpty())
    }
}
