package io.narratrace.android.core.network

import io.narratrace.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Tolerant on read, strict on trust.
 *
 * `ignoreUnknownKeys` lets the server add fields without breaking shipped clients.
 * `explicitNulls = false` matches the server omitting absent optional fields rather
 * than sending null. Neither weakens verification — an envelope that fails to parse
 * still fails closed as [ApiResult.Unreadable].
 */
internal val NarratraceJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = false
}

/**
 * The single HTTP entry point for Narratrace.
 *
 * Responsibilities, and deliberately nothing else:
 *   - build absolute HTTPS URLs against a configured origin
 *   - attach request identity and platform headers
 *   - decode the versioned envelope
 *   - classify every failure into an exhaustive [ApiResult]
 *
 * Authentication, refresh, and session rotation arrive in Phase 1b as an OkHttp
 * Authenticator. Keeping them out of here means this class stays testable without
 * any credential material.
 */
class NarratraceApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val appVersion: String = BuildConfig.VERSION_NAME,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val json: Json = NarratraceJson,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString().lowercase(Locale.US) },
    private val supportReferenceSink: (String) -> Unit = {},
) {

    /**
     * Fails closed when no API origin is configured, and refuses a non-HTTPS origin.
     *
     * A debug build pointed at cleartext would silently transmit protected content
     * in the clear, so this is enforced here as well as in the network security
     * config. Two independent gates, because one of them is a resource file that a
     * future change could weaken without anyone noticing.
     */
    private fun resolve(path: String): HttpUrl? {
        if (baseUrl.isBlank()) return null
        val normalised = baseUrl.trimEnd('/') + path
        val url = normalised.toHttpUrlOrNull() ?: return null
        return if (url.scheme == "https") url else null
    }

    suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        bearer: String? = null,
    ): ApiResult<T> = execute(path, "GET", null, serializer, bearer, null)

    suspend fun <T> post(
        path: String,
        body: String?,
        serializer: KSerializer<T>,
        bearer: String? = null,
        idempotencyKey: String? = null,
    ): ApiResult<T> = execute(path, "POST", body, serializer, bearer, idempotencyKey)

    suspend fun <T> postBytes(
        path: String,
        bytes: ByteArray,
        mimeType: String,
        contentSha256: String,
        serializer: KSerializer<T>,
        bearer: String,
        idempotencyKey: String,
    ): ApiResult<T> {
        val verifiedHash = contentSha256Header(contentSha256)
            ?: return ApiResult.Unreadable(reason = "The protected-content hash is invalid.")
        return executeRequest(
            path = path,
            method = "POST",
            requestBody = bytes.toRequestBody(mimeType.toMediaType()),
            serializer = serializer,
            bearer = bearer,
            idempotencyKey = idempotencyKey,
            contentSha256 = verifiedHash,
        )
    }

    /** Uploads only to the signed Supabase storage endpoint returned by our API. */
    suspend fun putSignedStorage(url: String, bytes: ByteArray, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            val parsed = url.toHttpUrlOrNull() ?: return@withContext false
            if (parsed.scheme != "https" || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
                (parsed.port != 443) || !parsed.host.endsWith(".supabase.co") ||
                !parsed.encodedPath.startsWith("/storage/v1/upload/")
            ) return@withContext false
            val request = Request.Builder().url(parsed).put(bytes.toRequestBody(mimeType.toMediaType()))
                .header("Content-Type", mimeType).header("Cache-Control", "no-store").build()
            runCatching { httpClient.newCall(request).await().use { it.isSuccessful } }.getOrDefault(false)
        }

    suspend fun getSignedStorage(url: String, maximumBytes: Int = 50 * 1024 * 1024): ByteArray? =
        withContext(Dispatchers.IO) {
            val parsed = url.toHttpUrlOrNull() ?: return@withContext null
            if (parsed.scheme != "https" || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
                parsed.port != 443 || !parsed.host.endsWith(".supabase.co") ||
                !parsed.encodedPath.startsWith("/storage/v1/object/sign/")
            ) return@withContext null
            val request = Request.Builder().url(parsed).get().header("Cache-Control", "no-store").build()
            runCatching { httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@use null
                val length = response.body?.contentLength() ?: return@use null
                if (length !in 0..maximumBytes.toLong()) return@use null
                response.body?.bytes()?.takeIf { it.size <= maximumBytes }
            } }.getOrNull()
        }

    suspend fun uploadTus(url: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        uploadTusInternal(url, bytes.size) { offset, size -> bytes.copyOfRange(offset, minOf(bytes.size, offset + size)) }
    }

    suspend fun uploadTus(url: String, totalBytes: Int, reader: (Int, Int) -> ByteArray?): Boolean = withContext(Dispatchers.IO) {
        uploadTusInternal(url, totalBytes, reader)
    }

    private suspend fun uploadTusInternal(url: String, totalBytes: Int, reader: (Int, Int) -> ByteArray?): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val allowed = parsed.scheme == "https" && parsed.username.isEmpty() && parsed.password.isEmpty() &&
            parsed.port == 443 && (parsed.host == "upload.videodelivery.net" || parsed.host.endsWith(".upload.videodelivery.net") ||
            parsed.host == "upload.cloudflarestream.com" || parsed.host.endsWith(".upload.cloudflarestream.com"))
        if (!allowed) return false
        var offset = runCatching {
            httpClient.newCall(Request.Builder().url(parsed).head().header("Tus-Resumable", "1.0.0").build()).await().use {
                if (!it.isSuccessful) return@use null
                it.header("Upload-Offset")?.toIntOrNull()
            }
        }.getOrNull() ?: return false
        while (offset < totalBytes) {
            val bytes = reader(offset, minOf(5 * 1024 * 1024, totalBytes - offset)) ?: return false
            val chunk = bytes.toRequestBody("application/offset+octet-stream".toMediaType())
            val next = runCatching { httpClient.newCall(Request.Builder().url(parsed).patch(chunk)
                .header("Tus-Resumable", "1.0.0").header("Upload-Offset", offset.toString()).build()).await().use {
                if (!it.isSuccessful) return@use null
                it.header("Upload-Offset")?.toIntOrNull()
            } }.getOrNull() ?: return false
            if (next <= offset || next > totalBytes) return false
            offset = next
        }
        return true
    }

    suspend fun <T> patch(
        path: String,
        body: String?,
        serializer: KSerializer<T>,
        bearer: String? = null,
    ): ApiResult<T> = execute(path, "PATCH", body, serializer, bearer, null)

    /**
     * [body] is optional because the contract uses both forms: artifact deliveries
     * take `?id=`, while session revocation sends `{ "scope": "current" }`.
     */
    suspend fun <T> delete(
        path: String,
        serializer: KSerializer<T>,
        bearer: String? = null,
        body: String? = null,
    ): ApiResult<T> = execute(path, "DELETE", body, serializer, bearer, null)

    private suspend fun <T> execute(
        path: String,
        method: String,
        body: String?,
        serializer: KSerializer<T>,
        bearer: String?,
        idempotencyKey: String?,
    ): ApiResult<T> {
        val requestBody: RequestBody? = when {
            body != null -> body.toRequestBody(JSON_MEDIA_TYPE)
            method == "POST" || method == "PATCH" -> "".toRequestBody(JSON_MEDIA_TYPE)
            else -> null
        }
        return executeRequest(path, method, requestBody, serializer, bearer, idempotencyKey, null)
    }

    private suspend fun <T> executeRequest(
        path: String,
        method: String,
        requestBody: RequestBody?,
        serializer: KSerializer<T>,
        bearer: String?,
        idempotencyKey: String?,
        contentSha256: String?,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val url = resolve(path)
            ?: return@withContext ApiResult.Unreadable(
                reason = "No HTTPS API origin is configured for this build.",
            )

        val requestId = requestIdFactory()
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header(HEADER_REQUEST_ID, requestId)
            .header(HEADER_PLATFORM, "android")
            .header(HEADER_APP_VERSION, appVersion)
            // Protected content must never sit in an HTTP cache.
            .header("Cache-Control", "no-store")
            .header("Accept", "application/json")
            .apply {
                bearer?.let { header("Authorization", "Bearer $it") }
                idempotencyKey?.let { header(HEADER_IDEMPOTENCY_KEY, it) }
                contentSha256?.let { header(HEADER_CONTENT_SHA256, it) }
            }
            .build()

        val response = try {
            httpClient.newCall(request).await()
        } catch (_: IOException) {
            // No diagnostic detail is surfaced: OkHttp messages can carry the URL,
            // and URLs here can carry account identifiers.
            return@withContext ApiResult.Offline()
        }

        response.use { decode(it, serializer) }
    }

    internal fun <T> decode(response: Response, serializer: KSerializer<T>): ApiResult<T> {
        val payload = response.body?.string().orEmpty()
        val headerSupportId = response.header(HEADER_SUPPORT_ID)
            ?: response.header(HEADER_REQUEST_ID)
            ?: ""
        fun capture(value: String): String = value.also { if (it.matches(Regex("^[A-Za-z0-9_-]{8,80}$"))) supportReferenceSink(it) }

        if (response.isSuccessful) {
            val envelope = runCatching {
                json.decodeFromString(ApiSuccess.serializer(serializer), payload)
            }.getOrElse {
                return ApiResult.Unreadable(reason = "The success envelope could not be decoded.")
            }
            if (!envelope.meta.isSupportedVersion && envelope.meta.apiVersion.isNotBlank()) {
                return ApiResult.Unreadable(
                    message = "This version of Narratrace is out of date. Update the app to continue.",
                    reason = "Unsupported API version ${envelope.meta.apiVersion}.",
                    supportReference = capture(envelope.meta.supportReference.ifBlank { headerSupportId }),
                )
            }
            return ApiResult.Success(
                value = envelope.data,
                supportReference = capture(envelope.meta.supportReference.ifBlank { headerSupportId }),
            )
        }

        val failure = runCatching {
            json.decodeFromString(ApiFailure.serializer(), payload)
        }.getOrNull()

        val supportReference = failure?.meta?.supportReference?.ifBlank { headerSupportId }
            ?: headerSupportId
        capture(supportReference)

        if (failure == null) {
            return ApiResult.Unreadable(
                reason = "HTTP ${response.code} with an unreadable error envelope.",
                supportReference = supportReference,
            )
        }

        val message = failure.error.message.ifBlank { "Narratrace could not complete that request." }

        return when {
            response.code == 401 -> ApiResult.Unauthorized(
                message = message,
                supportReference = supportReference,
                code = failure.error.parsedCode,
                rawCode = failure.error.code,
                fieldName = failure.error.fieldName,
            )
            response.code == 403 -> ApiResult.Forbidden(message, failure.error.fieldName, supportReference)
            response.code == 428 && failure.error.parsedCode == ApiErrorCode.PRECONDITION_REQUIRED ->
                ApiResult.PreconditionRequired(message, supportReference)
            response.code == 428 -> ApiResult.LegalAcceptanceRequired(message, supportReference)
            response.code == 429 -> ApiResult.RateLimited(
                message = message,
                retryAfterSeconds = response.header("Retry-After")?.toLongOrNull(),
                supportReference = supportReference,
            )
            else -> ApiResult.ServerError(
                code = failure.error.parsedCode,
                rawCode = failure.error.code,
                message = message,
                fieldName = failure.error.fieldName,
                httpStatus = response.code,
                supportReference = supportReference,
            )
        }
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // Mutations must never be replayed silently; retries are the caller's
            // decision and require an idempotency key.
            .retryOnConnectionFailure(false)
            .build()
    }
}

internal fun contentSha256Header(value: String): String? =
    value.takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }

/** Bridges OkHttp's callback API to coroutines without pulling in another dependency. */
private suspend fun Call.await(): Response = suspendCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWith(Result.failure(e))
        }
    })
}
