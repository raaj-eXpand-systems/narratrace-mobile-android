package io.narratrace.android.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The versioned Narratrace API envelope.
 *
 * Shape verified against narratrace-app/lib/apiContract.ts:
 *
 *   success  { "data": T,      "meta": { apiVersion, requestId, supportId } }
 *   failure  { "error": {...}, "meta": { apiVersion, requestId, supportId } }
 *
 * See docs/API_CONTRACT_INVENTORY.md for the full route inventory.
 */
const val NARRATRACE_API_VERSION = "1"

/** Response-only. The server does not read a version header from requests. */
const val HEADER_API_VERSION = "X-Narratrace-Api-Version"
const val HEADER_REQUEST_ID = "X-Request-Id"
const val HEADER_SUPPORT_ID = "X-Support-Id"
const val HEADER_PLATFORM = "X-Narratrace-Platform"
const val HEADER_APP_VERSION = "X-Narratrace-App-Version"
const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
const val HEADER_CONTENT_SHA256 = "X-Narratrace-Content-SHA256"

@Serializable
data class ApiMeta(
    val apiVersion: String = "",
    val requestId: String = "",
    val supportId: String = "",
) {
    /**
     * `supportId` is currently always equal to `requestId` server-side. Prefer this
     * accessor over reading either field directly so a future divergence is a
     * one-line change here rather than a search across every error surface.
     */
    val supportReference: String get() = supportId.ifBlank { requestId }

    val isSupportedVersion: Boolean get() = apiVersion == NARRATRACE_API_VERSION
}

@Serializable
data class ApiSuccess<T>(
    val data: T,
    val meta: ApiMeta = ApiMeta(),
)

@Serializable
data class ApiFailure(
    val error: ApiErrorBody,
    val meta: ApiMeta = ApiMeta(),
)

/**
 * The wire form of an error.
 *
 * `code` is deliberately a String, not an enum.
 *
 * kotlinx.serialization throws on an unrecognised enum value, so modelling this as
 * an enum would mean that the day the server adds a thirteenth error code, every
 * Android client in the field starts crashing on the error path — the one path that
 * is already going badly for the member. The server's `ApiErrorCode` union is
 * explicitly expected to grow.
 *
 * `field` is absent rather than null when the server has nothing to report, hence
 * the default.
 */
@Serializable
data class ApiErrorBody(
    val code: String = "",
    val message: String = "",
    @SerialName("field") val fieldName: String? = null,
) {
    val parsedCode: ApiErrorCode get() = ApiErrorCode.fromWire(code)
}

/**
 * Known error codes, plus an explicit fallback.
 *
 * Verified against lib/apiContract.ts. Treat [UNRECOGNISED] as retryable-never and
 * show the server's message — it is written for members, not for developers.
 */
enum class ApiErrorCode {
    AUTHENTICATION_REQUIRED,
    AUTHENTICATION_METHOD_UNSUPPORTED,
    FORBIDDEN,
    INVALID_REQUEST,
    VALIDATION_FAILED,
    IDEMPOTENCY_CONFLICT,
    PROMOTION_NOT_AVAILABLE,
    METHOD_NOT_ALLOWED,
    RATE_LIMITED,
    RESOURCE_NOT_FOUND,
    SERVICE_UNAVAILABLE,
    INTERNAL_ERROR,

    /** Any code this build does not know about. Never thrown, always handled. */
    UNRECOGNISED,
    ;

    companion object {
        fun fromWire(value: String): ApiErrorCode =
            entries.firstOrNull { it != UNRECOGNISED && it.name == value } ?: UNRECOGNISED
    }
}
