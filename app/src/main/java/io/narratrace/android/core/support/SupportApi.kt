package io.narratrace.android.core.support

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import java.util.UUID

@Serializable data class FeedbackScreenshot(val name: String, val type: String, val data: String)
@Serializable private data class FeedbackInput(val senderName: String, val kind: String, val message: String, val pageReference: String, val screenshot: FeedbackScreenshot? = null)
@Serializable data class FeedbackResponse(val submitted: Boolean)
@Serializable data class ProcessingJob(val id: String, val jobType: String, val resourceType: String, val resourceId: String, val state: String, val progress: Int? = null, val failureCategory: String? = null, val canRetry: Boolean, val createdAt: String, val updatedAt: String)
@Serializable data class ProcessingDetailResponse(val kind: String, val job: ProcessingJob)
@Serializable data class ProcessingRetryResponse(val kind: String)

class SupportApi(private val client: NarratraceApiClient) {
    suspend fun feedback(token: String, senderName: String, kind: String, message: String, pageReference: String, screenshot: FeedbackScreenshot?) = client.post(
        "/api/v1/feedback", NarratraceJson.encodeToString(FeedbackInput(senderName, kind, message, pageReference, screenshot)), serializer<FeedbackResponse>(), token,
    )
    suspend fun processing(token: String, id: String) = client.get("/api/v1/processing/$id", serializer<ProcessingDetailResponse>(), token)
    suspend fun retryProcessing(token: String, id: String) = client.post("/api/v1/processing/$id", null, serializer<ProcessingRetryResponse>(), token)
}

class SupportRepository(private val api: SupportApi, private val sessions: SessionManager) {
    suspend fun submitFeedback(senderName: String, kind: String, message: String, pageReference: String, screenshot: FeedbackScreenshot?): FeatureResult<Boolean> {
        val clean = message.trim()
        if (kind !in setOf("feedback", "issue") || clean.isEmpty() || clean.length > 5_000 || pageReference.length > 200 || senderName.isBlank()) return FeatureResult.Unavailable("Complete the submission within the supported limits.")
        if (kind == "feedback" && screenshot != null) return FeatureResult.Unavailable("Attachments are available only for issue reports.")
        return call { api.feedback(it, senderName.take(120), kind, clean, pageReference, screenshot) }.map { it.submitted }
    }
    suspend fun processing(id: String): FeatureResult<ProcessingJob> {
        if (runCatching { UUID.fromString(id) }.isFailure) return FeatureResult.Unavailable("Choose a valid processing item.")
        return call { api.processing(it, id) }.map { it.job }
    }
    suspend fun retryProcessing(id: String): FeatureResult<Boolean> {
        if (runCatching { UUID.fromString(id) }.isFailure) return FeatureResult.Unavailable("Choose a valid processing item.")
        return call { api.retryProcessing(it, id) }.map { it.kind == "retried" }
    }
    private suspend fun <T> call(block: suspend (String) -> ApiResult<T>): FeatureResult<T> {
        val lease = sessions.accessToken(); if (lease !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = block(lease.accessToken)
        if (result is ApiResult.Unauthorized) { val recovered = sessions.recoverFromUnauthorized(lease.accessToken); if (recovered !is TokenLease.Valid) return FeatureResult.AuthenticationRequired; result = block(recovered.accessToken) }
        return when (result) { is ApiResult.Success -> FeatureResult.Success(result.value); is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired; is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference) }
    }
    private fun <T, R> FeatureResult<T>.map(transform: (T) -> R): FeatureResult<R> = when (this) { is FeatureResult.Success -> FeatureResult.Success(transform(value)); is FeatureResult.Unavailable -> FeatureResult.Unavailable(message, supportReference); FeatureResult.AuthenticationRequired -> FeatureResult.AuthenticationRequired }
}
