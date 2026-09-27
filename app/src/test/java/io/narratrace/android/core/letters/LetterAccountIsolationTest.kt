package io.narratrace.android.core.letters

import io.narratrace.android.core.auth.*
import io.narratrace.android.core.delivery.DeliveryMode
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.network.*
import io.narratrace.android.core.offline.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LetterAccountIsolationTest {
    private val cipher = object : CredentialCipher {
        override fun encrypt(plaintext: ByteArray) = byteArrayOf(7) + plaintext.reversedArray()
        override fun decrypt(ciphertext: ByteArray) = ciphertext.drop(1).toByteArray().reversedArray()
    }
    private fun sessions(): SessionManager {
        var saved: ByteArray? = null
        val blob = object : EncryptedBlobStore {
            override fun read() = saved
            override fun write(bytes: ByteArray): Boolean { saved = bytes; return true }
            override fun clear(): Boolean { saved = null; return true }
        }
        return SessionManager(SessionStore(cipher, blob), SessionRefresher { ApiResult.Offline() }).also { adopt(it, "a") }
    }
    private fun adopt(manager: SessionManager, owner: String) {
        assertTrue(manager.adopt(TokenPair("fixture-$owner", "refresh", "2099-01-01T00:00:00Z"), owner))
    }
    private fun delayedLetter(switch: String) = runBlocking {
        val sessions = sessions()
        val owner = sessions.captureOperationLease()!!
        val directory = Files.createTempDirectory("letter-generation").toFile()
        try {
            val store = OfflineDraftStore(directory.resolve("draft.bin"), cipher) { sessions.captureOperationLease()?.accountId }
            val pendingDraft = OfflineLetterDraft(recipientName = "A", subject = "Fixture", body = "Private A", ownerAccountId = "a")
            assertTrue(store.save(pendingDraft))
            val started = CountDownLatch(1); val release = CountDownLatch(1)
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                assertEquals("Bearer fixture-a", chain.request().header("Authorization"))
                started.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"data":{"id":"fixture","replayed":false,"verificationPending":false}}""".toResponseBody("application/json".toMediaType())).build()
            }.build()
            val repository = LettersRepository(LettersApi(NarratraceApiClient(baseUrl = "https://example.invalid", httpClient = http)), sessions)
            val request = async(Dispatchers.Default) { repository.create("A", null, true, "Fixture", "Private A", DeliveryMode.NOW, null, "key", operationLease = owner) }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            when (switch) {
                "b" -> { sessions.signOut(); adopt(sessions, "b") }
                "aba" -> { sessions.signOut(); adopt(sessions, "b"); adopt(sessions, "a") }
                "out" -> sessions.signOut()
            }
            release.countDown()
            val result = request.await()
            if (switch == "same") {
                assertTrue(result is FeatureResult.Success)
                assertTrue(sessions.withCurrent(owner) { store.remove(pendingDraft.clientDraftId) } == true)
                assertTrue(store.load().isEmpty())
            } else {
                assertEquals(FeatureResult.AuthenticationRequired, result)
                assertNull(sessions.withCurrent(owner) { store.save(pendingDraft.copy(revision = 2)) })
                if (switch != "aba") assertTrue(store.load().isEmpty())
                adopt(sessions, "a")
                assertEquals(0, store.load().single().revision)
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun `delayed success cannot enter next account`() = delayedLetter("b")
    @Test fun `delayed success cannot survive ABA generation`() = delayedLetter("aba")
    @Test fun `delayed success cannot survive signout`() = delayedLetter("out")
    @Test fun `current generation still creates and removes its draft`() = delayedLetter("same")

    @Test fun `current offline draft preserves intent and rejects mismatched owner`() {
        val sessions = sessions(); val owner = sessions.captureOperationLease()!!
        val directory = Files.createTempDirectory("letter-offline").toFile()
        try {
            val store = OfflineDraftStore(directory.resolve("draft.bin"), cipher) { sessions.captureOperationLease()?.accountId }
            val draft = OfflineLetterDraft(recipientName = "A", subject = "Fixture", body = "Offline writing", ownerAccountId = "a", deliveryMode = "later", deliveryTimezone = "America/New_York")
            assertEquals(true, sessions.withCurrent(owner) { store.save(draft) })
            assertEquals(draft, store.load().single())
            assertTrue(store.load().single().requiresDeliveryReview)
            assertFalse(store.save(draft.copy(ownerAccountId = "b")))
        } finally { directory.deleteRecursively() }
    }
}
