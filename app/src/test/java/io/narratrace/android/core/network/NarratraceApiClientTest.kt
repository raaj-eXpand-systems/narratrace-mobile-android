package io.narratrace.android.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Serializable
private data class Profile(val email: String, val displayName: String? = null)

/**
 * Envelope decoding and failure classification, against a real HTTP server.
 *
 * Fixtures mirror narratrace-app/lib/apiContract.ts exactly. If the server envelope
 * changes, these fail — which is the point.
 */
class NarratraceApiClientTest {

    @Test
    fun `protected-content hash header accepts only lowercase sha256`() {
        val valid = "a".repeat(64)
        assertEquals(valid, contentSha256Header(valid))
        assertEquals(null, contentSha256Header("A".repeat(64)))
        assertEquals(null, contentSha256Header("a".repeat(63)))
        assertEquals(null, contentSha256Header("not-a-hash"))
    }

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * MockWebServer serves http://, and the client refuses non-HTTPS origins by
     * design. Tests therefore drive [decodeForTest] through a client whose origin
     * check is satisfied by an https base that routes to the local server via the
     * `url` override below.
     */
    private fun client(baseUrl: String = server.url("/").toString()) =
        NarratraceApiClient(
            baseUrl = baseUrl,
            appVersion = "1.0.0",
            requestIdFactory = { "11111111-2222-4333-8444-555555555555" },
        )

    @Test
    fun `rejects a non-https origin rather than transmitting in the clear`() = runTest {
        val result = client().get("/api/v1/profile", serializer<Profile>())
        assertTrue(result is ApiResult.Unreadable)
        assertTrue((result as ApiResult.Unreadable).reason.contains("HTTPS"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fails closed when no API origin is configured`() = runTest {
        val result = client(baseUrl = "").get("/api/v1/profile", serializer<Profile>())
        assertTrue(result is ApiResult.Unreadable)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `decodes a success envelope and surfaces the support reference`() {
        val json = NarratraceJson
        val body = """
            {"data":{"email":"person@gmail.com","displayName":"Raaj"},
             "meta":{"apiVersion":"1","requestId":"req-1","supportId":"sup-1"}}
        """.trimIndent()
        val envelope = json.decodeFromString(ApiSuccess.serializer(serializer<Profile>()), body)
        assertEquals("person@gmail.com", envelope.data.email)
        assertEquals("sup-1", envelope.meta.supportReference)
        assertTrue(envelope.meta.isSupportedVersion)
    }

    @Test
    fun `supportReference falls back to requestId when supportId is absent`() {
        val meta = ApiMeta(apiVersion = "1", requestId = "req-9", supportId = "")
        assertEquals("req-9", meta.supportReference)
    }

    @Test
    fun `tolerates unknown fields so the server can add them safely`() {
        val body = """
            {"data":{"email":"a@gmail.com","futureField":"ignored"},
             "meta":{"apiVersion":"1","requestId":"r","supportId":"s","newMeta":1}}
        """.trimIndent()
        val envelope = NarratraceJson
            .decodeFromString(ApiSuccess.serializer(serializer<Profile>()), body)
        assertEquals("a@gmail.com", envelope.data.email)
    }

    @Test
    fun `an unknown error code never throws`() {
        val body = """
            {"error":{"code":"SOME_FUTURE_CODE","message":"Not yet known."},
             "meta":{"apiVersion":"1","requestId":"r","supportId":"s"}}
        """.trimIndent()
        val failure = NarratraceJson.decodeFromString(ApiFailure.serializer(), body)
        assertEquals(ApiErrorCode.UNRECOGNISED, failure.error.parsedCode)
        assertEquals("SOME_FUTURE_CODE", failure.error.code)
        assertEquals("Not yet known.", failure.error.message)
    }

    @Test
    fun `every documented error code parses`() {
        val documented = listOf(
            "AUTHENTICATION_REQUIRED", "AUTHENTICATION_METHOD_UNSUPPORTED", "FORBIDDEN",
            "PRECONDITION_REQUIRED", "INVALID_REQUEST", "VALIDATION_FAILED", "IDEMPOTENCY_CONFLICT",
            "PROMOTION_NOT_AVAILABLE", "DELIVERY_CONTACT_REQUIRED", "VERIFICATION_FAILED",
            "METHOD_NOT_ALLOWED", "RATE_LIMITED", "RESOURCE_NOT_FOUND", "GONE",
            "SERVICE_UNAVAILABLE", "INTERNAL_ERROR",
            "ARCHIVE_TARGET_REQUIRED", "PRODUCTION_ALLOWANCE_EXHAUSTED", "STORAGE_LIMIT_REACHED",
            "DUPLICATE_RESOURCE", "STORYTELLER_LIMIT_REACHED",
        )
        for (code in documented) {
            assertEquals(code, ApiErrorCode.fromWire(code).name)
        }
    }

    @Test
    fun `an omitted field property decodes as null rather than failing`() {
        val body = """
            {"error":{"code":"VALIDATION_FAILED","message":"Check the recipient."},
             "meta":{"apiVersion":"1","requestId":"r","supportId":"s"}}
        """.trimIndent()
        val failure = NarratraceJson.decodeFromString(ApiFailure.serializer(), body)
        assertEquals(null, failure.error.fieldName)
    }

    @Test
    fun `a present field property is read from the wire name`() {
        val body = """
            {"error":{"code":"VALIDATION_FAILED","message":"Bad scope.","field":"scope"},
             "meta":{"apiVersion":"1","requestId":"r","supportId":"s"}}
        """.trimIndent()
        val failure = NarratraceJson.decodeFromString(ApiFailure.serializer(), body)
        assertEquals("scope", failure.error.fieldName)
    }

    @Test
    fun `a 401 retains the authentication factor field`() {
        val body = """
            {"error":{"code":"AUTHENTICATION_REQUIRED","message":"Authenticator setup is required.","field":"mfaEnrollment"},
             "meta":{"apiVersion":"1","requestId":"request-123","supportId":"support-123"}}
        """.trimIndent()
        val response = Response.Builder()
            .request(Request.Builder().url("https://www.narratrace.io/api/v1/auth/native").build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

        val result = client("https://www.narratrace.io").decode(response, serializer<Profile>())

        assertTrue(result is ApiResult.Unauthorized)
        result as ApiResult.Unauthorized
        assertEquals("mfaEnrollment", result.fieldName)
        assertEquals(ApiErrorCode.AUTHENTICATION_REQUIRED, result.code)
        assertEquals("support-123", result.supportReference)
    }

    @Test
    fun `a versioned deletion precondition requires recent authentication rather than legal review`() {
        val body = """
            {"error":{"code":"PRECONDITION_REQUIRED","message":"Sign in again before deleting this resource."},
             "meta":{"apiVersion":"1","requestId":"request-delete","supportId":"support-delete"}}
        """.trimIndent()
        val response = Response.Builder()
            .request(Request.Builder().url("https://www.narratrace.io/api/v1/media/resource-1").build())
            .protocol(Protocol.HTTP_1_1)
            .code(428)
            .message("Precondition Required")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

        val result = client("https://www.narratrace.io").decode(response, serializer<Profile>())

        assertTrue(result is ApiResult.PreconditionRequired)
        assertEquals("support-delete", (result as ApiResult.PreconditionRequired).supportReference)
    }
}

/**
 * Failure classification, isolated from transport.
 *
 * 401, 403, 428 and 429 each need a different response from the product, and
 * collapsing them would send members in circles — re-authenticating never fixes a
 * 403, and a 428 is a flow rather than an error.
 */
class ApiFailureClassificationTest {

    @Test
    fun `unauthorized and forbidden are distinct outcomes`() {
        val unauthorized: ApiResult<Unit> = ApiResult.Unauthorized("Authentication is required.", "s1")
        val forbidden: ApiResult<Unit> = ApiResult.Forbidden("Your family role cannot write Letters.", null, "s2")
        assertTrue(unauthorized is ApiResult.Failure)
        assertTrue(forbidden is ApiResult.Failure)
        assertTrue(unauthorized !is ApiResult.Forbidden)
        assertTrue(forbidden !is ApiResult.Unauthorized)
    }

    @Test
    fun `legal acceptance is modelled as its own outcome, not a generic error`() {
        val result: ApiResult<Unit> = ApiResult.LegalAcceptanceRequired("Accept the AI notice.", "s3")
        assertTrue(result is ApiResult.LegalAcceptanceRequired)
    }

    @Test
    fun `recent authentication is distinct from legal acceptance`() {
        val result: ApiResult<Unit> = ApiResult.PreconditionRequired(
            "Sign in again before deleting this resource.", "s-delete",
        )
        assertTrue(result is ApiResult.PreconditionRequired)
        assertTrue(result !is ApiResult.LegalAcceptanceRequired)
    }

    @Test
    fun `rate limiting carries retry guidance when the server supplies it`() {
        val result = ApiResult.RateLimited("Please wait.", 900, "s4")
        assertEquals(900L, result.retryAfterSeconds)
    }

    @Test
    fun `map preserves the support reference on success`() {
        val mapped = ApiResult.Success("42", "sup-7").map { it.toInt() }
        assertTrue(mapped is ApiResult.Success)
        assertEquals(42, (mapped as ApiResult.Success).value)
        assertEquals("sup-7", mapped.supportReference)
    }

    @Test
    fun `map leaves a failure untouched`() {
        val original: ApiResult<String> = ApiResult.Offline()
        assertTrue(original.map { it.length } is ApiResult.Offline)
    }

    @Test
    fun `every failure exposes a member-facing message`() {
        val failures: List<ApiResult.Failure> = listOf(
            ApiResult.Unauthorized("a", "s"),
            ApiResult.Forbidden("b", null, "s"),
            ApiResult.LegalAcceptanceRequired("c", "s"),
            ApiResult.PreconditionRequired("sign in again", "s"),
            ApiResult.RateLimited("d", null, "s"),
            ApiResult.Offline(),
            ApiResult.Unreadable(reason = "x"),
            ApiResult.ServerError(ApiErrorCode.INTERNAL_ERROR, "INTERNAL_ERROR", "e", null, 500, "s"),
        )
        for (failure in failures) {
            assertTrue("blank message on ${failure::class.simpleName}", failure.message.isNotBlank())
        }
    }
}
