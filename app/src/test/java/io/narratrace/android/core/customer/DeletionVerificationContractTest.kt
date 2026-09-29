package io.narratrace.android.core.customer

import io.narratrace.android.core.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DeletionVerificationContractTest {
    @Serializable private data class Deleted(val deleted: Boolean)
    @Test fun `only selected resource gets proof and incorrect code can retry without signing out`() = runBlocking {
        var deletes = 0; var prompts = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val challenge = request.url.encodedPath.endsWith("deletion-challenge")
            val status: Int
            val response: String
            if (challenge) {
                assertEquals("POST", request.method)
                val body = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                assertEquals("{\"resource\":\"letter:fixture\"}", body)
                status = 200
                response = """{"data":{"token":"fixture-proof","method":"email","maskedEmail":"a***@example.com","expiresInSeconds":600}}"""
            } else {
                assertEquals("DELETE", request.method)
                assertEquals("Bearer fixture-access", request.header("Authorization"))
                assertEquals("fixture-proof", request.header("X-Narratrace-Deletion-Token"))
                deletes++
                status = if (deletes == 1) 428 else 200
                response = if (deletes == 1) """{"error":{"code":"PRECONDITION_REQUIRED","message":"Enter a new verification code to confirm this deletion."}}""" else """{"data":{"deleted":true}}"""
                assertEquals(if (deletes == 1) "000000" else "123456", request.header("X-Narratrace-Deletion-Code"))
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture").body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = NarratraceApiClient("https://example.invalid", httpClient = http, deletionVerification = { challenge, bearer, error ->
            assertEquals("fixture-access", bearer); assertEquals("email", challenge.method)
            prompts++; if (prompts == 1) { assertNull(error); "000000" } else { assertNotNull(error); "123456" }
        })
        val result = client.delete("/api/v1/letters/fixture", serializer<Deleted>(), "fixture-access", deletionResource = "letter:fixture")
        assertTrue(result is ApiResult.Success); assertEquals(2, deletes); assertEquals(2, prompts)
    }
    @Test fun `cancelled code prompt sends no destructive request`() = runBlocking {
        var mutations = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().method == "DELETE") mutations++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body("""{"data":{"token":"proof","method":"authenticator","maskedEmail":"a***@example.com","expiresInSeconds":600}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = NarratraceApiClient("https://example.invalid", httpClient = http, deletionVerification = { _, _, _ -> null })
        assertTrue(client.delete("/api/v1/media/fixture", serializer<Deleted>(), "fixture-access", deletionResource = "media:fixture") is ApiResult.Failure)
        assertEquals(0, mutations)
    }
}
