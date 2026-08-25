package io.narratrace.android.core.network

/**
 * Every outcome of a Narratrace API call.
 *
 * Deliberately exhaustive and deliberately not an exception hierarchy: a failed
 * request is an ordinary, expected state in a product people use on a train, and
 * modelling it as a value forces every caller to decide what the member sees.
 *
 * Fail-closed principle: there is no "success with missing data" case. If the
 * envelope did not parse, or the API version is unknown, the call failed.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(
        val value: T,
        val supportReference: String,
    ) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing> {
        /** Shown to the member. Server copy is member-facing; local copy is fallback. */
        val message: String

        /** Quote this to support. May be blank when the failure never reached the server. */
        val supportReference: String
    }

    /**
     * The server answered with an error envelope.
     *
     * [httpStatus] is retained because the same [code] can arrive with different
     * statuses — `202` on media playback means "still processing", not an error.
     */
    data class ServerError(
        val code: ApiErrorCode,
        val rawCode: String,
        override val message: String,
        val fieldName: String? = null,
        val httpStatus: Int,
        override val supportReference: String,
    ) : Failure

    /**
     * Authentication is required or has lapsed.
     *
     * Separated from [ServerError] because it is the only failure with an automatic
     * remedy — refresh once, then fail closed. Never retried more than once, and
     * never retried for a mutation without an idempotency key.
     */
    data class Unauthorized(
        override val message: String,
        override val supportReference: String,
        val code: ApiErrorCode = ApiErrorCode.AUTHENTICATION_REQUIRED,
        val rawCode: String = "AUTHENTICATION_REQUIRED",
        val fieldName: String? = null,
    ) : Failure

    /**
     * The member's family role or plan forbids this action.
     *
     * Distinct from [Unauthorized] on purpose: re-authenticating will never fix a
     * 403, so showing a sign-in prompt would send the member in a circle. This
     * needs an explanation, not a login screen.
     */
    data class Forbidden(
        override val message: String,
        val fieldName: String? = null,
        override val supportReference: String,
    ) : Failure

    /**
     * The request cannot proceed until the member accepts current legal terms.
     *
     * The server answers 428 on AI capture routes. This is a flow, not an error —
     * route to legal acceptance and resume the capture the member had started.
     */
    data class LegalAcceptanceRequired(
        override val message: String,
        override val supportReference: String,
    ) : Failure

    /** Rate limited. [retryAfterSeconds] is null when the server did not say. */
    data class RateLimited(
        override val message: String,
        val retryAfterSeconds: Long?,
        override val supportReference: String,
    ) : Failure

    /** No usable connection, DNS failure, TLS failure, or timeout. Nothing was sent. */
    data class Offline(
        override val message: String = "Narratrace could not reach the internet.",
        override val supportReference: String = "",
    ) : Failure

    /**
     * The response could not be trusted: malformed envelope, or an API version this
     * build does not understand.
     *
     * Fails closed. A partially understood response about someone's private memories
     * is worse than an honest failure.
     */
    data class Unreadable(
        override val message: String = "Narratrace could not read the response safely.",
        val reason: String,
        override val supportReference: String = "",
    ) : Failure
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value), supportReference)
    is ApiResult.Failure -> this
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(value)
    return this
}

inline fun <T> ApiResult<T>.onFailure(action: (ApiResult.Failure) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(this)
    return this
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value
