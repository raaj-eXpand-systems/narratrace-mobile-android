package io.narratrace.android.core.network

import io.narratrace.android.core.media.MediaAndInterviewApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class QuestionSpeechContractTest {
    @Test fun `question speech posts only message identity with credentials and private caching`() = runTest {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/interviews/interview/speech", request.url.encodedPath)
            assertEquals("Bearer fixture-token", request.header("Authorization"))
            assertEquals("no-store", request.header("Cache-Control"))
            val body = Buffer(); request.body!!.writeTo(body)
            assertEquals("{\"messageId\":\"question\"}", body.readUtf8())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("fixture-audio".toResponseBody("audio/mpeg".toMediaType())).build()
        }.build()
        val api = MediaAndInterviewApi(NarratraceApiClient(baseUrl = "https://www.narratrace.io", httpClient = transport))
        val result = api.questionSpeech("interview", "question", "fixture-token")
        assertTrue(result is ApiResult.Success)
        assertArrayEquals("fixture-audio".toByteArray(), (result as ApiResult.Success).value)
    }

    @Test fun `question speech rejects empty wrong format oversized and denied audio`() = runTest {
        for ((status, mime, bytes) in listOf(
            Triple(200, "audio/mpeg", byteArrayOf()),
            Triple(200, "audio/wav", byteArrayOf(1)),
            Triple(200, "audio/mpeg", ByteArray(8 * 1024 * 1024 + 1)),
            Triple(403, "audio/mpeg", byteArrayOf(1)),
            Triple(302, "audio/mpeg", byteArrayOf(1)),
        )) {
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                    .body(bytes.toResponseBody(mime.toMediaType())).build()
            }.build()
            val client = NarratraceApiClient(baseUrl = "https://www.narratrace.io", httpClient = transport)
            assertFalse(client.postAudio("/api/v1/interviews/interview/speech", "{}", "fixture") is ApiResult.Success)
        }
    }

    @Test fun `question speech rejects JSON masquerading as successful audio`() = runTest {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = NarratraceApiClient(baseUrl = "https://www.narratrace.io", httpClient = transport)
        assertTrue(client.postAudio("/api/v1/interviews/interview/speech", "{}", "fixture") is ApiResult.Unreadable)
    }
}
