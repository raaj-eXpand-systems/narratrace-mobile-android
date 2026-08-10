package io.narratrace.android.core.network

import kotlinx.serialization.Serializable

const val NARRATRACE_API_VERSION = "1"

@Serializable
data class ApiMeta(
    val apiVersion: String,
    val requestId: String,
    val supportId: String,
) {
    fun requireSupportedVersion() {
        require(apiVersion == NARRATRACE_API_VERSION) { "Unsupported Narratrace API version" }
        require(requestId.isNotBlank()) { "Missing request ID" }
        require(supportId.isNotBlank()) { "Missing support ID" }
    }
}

@Serializable
data class ApiSuccess<T>(
    val data: T,
    val meta: ApiMeta,
)

@Serializable
data class ApiFailure(
    val error: ApiError,
    val meta: ApiMeta,
)

@Serializable
data class ApiError(
    val code: ApiErrorCode,
    val message: String,
    val field: String? = null,
)

@Serializable
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
}

