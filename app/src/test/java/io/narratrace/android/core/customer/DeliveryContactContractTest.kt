package io.narratrace.android.core.customer

import io.narratrace.android.core.auth.*
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.network.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeliveryContactContractTest {
    private fun sessions(): SessionManager {
        var saved: ByteArray? = null
        return SessionManager(SessionStore(object : CredentialCipher {
            override fun encrypt(plaintext: ByteArray) = plaintext
            override fun decrypt(ciphertext: ByteArray) = ciphertext
        }, object : EncryptedBlobStore {
            override fun read() = saved
            override fun write(bytes: ByteArray): Boolean { saved = bytes; return true }
            override fun clear(): Boolean { saved = null; return true }
        }), SessionRefresher { ApiResult.Offline() }).also {
            assertTrue(it.adopt(TokenPair("fixture-a", "refresh", "2099-01-01T00:00:00Z"), "a"))
        }
    }
    private fun client(block: (Request) -> String) = NarratraceApiClient("https://example.invalid", httpClient = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(block(chain.request()).toResponseBody("application/json".toMediaType())).build()
    }.build())

    @Test fun `challenge request and verification use native endpoint and no sign-in change`() = runBlocking {
        val requests = mutableListOf<String>()
        val repository = DeliveryContactRepository(client { request ->
            assertEquals("/api/v1/account/delivery-contact", request.url.encodedPath)
            assertEquals("Bearer fixture-a", request.header("Authorization"))
            val body = okio.Buffer().also { request.body?.writeTo(it) }.readUtf8()
            requests += body
            if (body.contains("request")) """{"data":{"kind":"sent","challengeId":"challenge","expiresAt":"2026-09-29T00:00:00Z"}}"""
            else """{"data":{"contact":{"email":"owner@example.com","status":"verified"}}}"""
        }, sessions())
        assertTrue(repository.request(" owner@example.com ") is FeatureResult.Success)
        assertTrue(requests.first().contains("\"email\":\"owner@example.com\""))
        assertTrue(repository.verify("challenge", "123456") is FeatureResult.Success)
        assertTrue(requests.last().contains("\"challengeId\":\"challenge\""))
        assertEquals(2, requests.size)
        assertTrue(repository.verify("challenge", "１２３４５６") is FeatureResult.Unavailable)
        assertEquals(2, requests.size)
    }

    @Test fun `delivery email from old account cannot be shown after account switch`() = runBlocking {
        val manager = sessions()
        val started = CountDownLatch(1); val released = CountDownLatch(1)
        val repository = DeliveryContactRepository(client {
            started.countDown(); check(released.await(10, TimeUnit.SECONDS))
            """{"data":{"contact":{"email":"old@example.com","status":"verified"}}}"""
        }, manager)
        val pending = async(Dispatchers.Default) { repository.load() }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        manager.signOut()
        assertTrue(manager.adopt(TokenPair("fixture-b", "refresh", "2099-01-01T00:00:00Z"), "b"))
        released.countDown()
        assertEquals(FeatureResult.AuthenticationRequired, pending.await())
    }
}
