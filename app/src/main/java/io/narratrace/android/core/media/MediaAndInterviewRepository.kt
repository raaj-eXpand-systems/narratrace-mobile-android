package io.narratrace.android.core.media

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.ApiErrorCode

sealed interface FeatureResult<out T> {
    data class Success<T>(val value: T) : FeatureResult<T>
    data object AuthenticationRequired : FeatureResult<Nothing>
    data class Unavailable(val message: String, val supportReference: String = "") : FeatureResult<Nothing>
}

internal fun <T> destructiveFeatureResult(
    result: ApiResult<T>,
    clearSession: () -> Unit,
): FeatureResult<T> {
    if (result is ApiResult.PreconditionRequired) {
        clearSession()
        return FeatureResult.AuthenticationRequired
    }
    return when (result) {
        is ApiResult.Success -> FeatureResult.Success(result.value)
        is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired
        is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference)
    }
}

/** Local originals remain queued unless the server explicitly confirms both guarantees. */
internal fun PreservationAcknowledgement?.permitsLocalRemoval(): Boolean =
    this?.originalDurablyStored == true && integrityVerified

data class MediaReconciliationIssue(
    val message: String,
    val supportReference: String,
    val retryAutomatically: Boolean,
)

internal fun reconciliationIssue(failure: ApiResult.Failure): MediaReconciliationIssue {
    val code = (failure as? ApiResult.ServerError)?.code
    val needsMemberAction = code in setOf(
        ApiErrorCode.ARCHIVE_TARGET_REQUIRED,
        ApiErrorCode.PRODUCTION_ALLOWANCE_EXHAUSTED,
        ApiErrorCode.STORAGE_LIMIT_REACHED,
        ApiErrorCode.DUPLICATE_RESOURCE,
    ) || failure is ApiResult.Forbidden || failure is ApiResult.LegalAcceptanceRequired
    return MediaReconciliationIssue(failure.message, failure.supportReference, !needsMemberAction)
}

class MediaAndInterviewRepository(
    private val api: MediaAndInterviewApi,
    private val sessions: SessionManager,
    val queue: ProtectedMediaQueue,
) {
    @Volatile private var latestIssue: MediaReconciliationIssue? = null

    fun latestReconciliationIssue(): MediaReconciliationIssue? = latestIssue

    private fun rememberFailure(result: ApiResult<*>): Boolean {
        val failure = result as? ApiResult.Failure ?: return false
        val issue = reconciliationIssue(failure)
        if (latestIssue == null || latestIssue?.retryAutomatically == true && !issue.retryAutomatically) {
            latestIssue = issue
        }
        return true
    }

    suspend fun reconcile(): Int {
        latestIssue = null
        val token = (sessions.accessToken() as? TokenLease.Valid)?.accessToken ?: return queue.items().size
        queue.items().forEach { item ->
            queue.markAttempt(item.id)
            when (item.kind) {
                PendingMediaKind.StandaloneAudio, PendingMediaKind.Photo -> {
                    val bytes = queue.read(item) ?: return@forEach
                    val authResult = api.authorizeUpload(item, token)
                    val auth = authResult as? ApiResult.Success ?: run { rememberFailure(authResult); return@forEach }
                    if (!api.transfer(auth.value, bytes, item.mimeType)) return@forEach
                    val confirmationResult = api.confirmUpload(item, auth.value, token)
                    val confirmation = confirmationResult as? ApiResult.Success ?: run { rememberFailure(confirmationResult); return@forEach }
                    val ack = confirmation.value.preservationAcknowledgement
                    if (ack.permitsLocalRemoval()) queue.acknowledgeAndRemove(item.id)
                }
                PendingMediaKind.InterviewAudio -> {
                    val bytes = queue.read(item) ?: return@forEach
                    val id = item.interviewId ?: return@forEach
                    val responseResult = api.respondAudio(id, bytes, item.mimeType, item.sha256, item.idempotencyKey, token)
                    val response = responseResult as? ApiResult.Success ?: run { rememberFailure(responseResult); return@forEach }
                    if (response.value.preservationAcknowledgement.permitsLocalRemoval()) queue.acknowledgeAndRemove(item.id)
                }
                PendingMediaKind.StandaloneVideo, PendingMediaKind.InterviewVideo -> {
                    var current = item
                    if (current.uploadUrl == null || current.serverId == null) {
                        val authorizationResult = api.authorizeVideo(current, token)
                        val authorization = authorizationResult as? ApiResult.Success
                            ?: run { rememberFailure(authorizationResult); return@forEach }
                        if (!queue.setAuthorization(current.id, authorization.value.uploadUrl, authorization.value.videoId)) return@forEach
                        current = queue.items().firstOrNull { it.id == current.id } ?: return@forEach
                    }
                    if (!api.transferVideo(current.uploadUrl!!, current, queue)) return@forEach
                    if (current.kind == PendingMediaKind.InterviewVideo) {
                        val responseResult = api.confirmInterviewVideo(current, token)
                        val response = responseResult as? ApiResult.Success
                            ?: run { rememberFailure(responseResult); return@forEach }
                        if (response.value.preservationAcknowledgement.permitsLocalRemoval()) queue.acknowledgeAndRemove(current.id)
                    } else {
                        val preservedResult = api.videoPreservation(current.serverId!!, token)
                        val preserved = preservedResult as? ApiResult.Success
                            ?: run { rememberFailure(preservedResult); return@forEach }
                        val ack = preserved.value.video.preservationAcknowledgement
                        if (ack.permitsLocalRemoval()) queue.acknowledgeAndRemove(current.id)
                    }
                }
            }
        }
        return queue.items().size
    }

    suspend fun interviews() = call { api.interviews(it) }
    suspend fun interview(id: String) = call { api.interview(id, it) }
    suspend fun capacity() = call { api.capacity(it) }
    suspend fun legal() = call { api.legal(it) }
    suspend fun acceptTerms() = call { api.acceptTerms(it) }
    suspend fun acknowledgePrivacy() = call { api.acknowledgePrivacy(it) }
    suspend fun acknowledgeAiNotice() = call { api.acknowledgeAiNotice(it) }
    suspend fun attestContentRights() = call { api.attestContentRights(it) }
    suspend fun grantSpecialCategoryConsent() = call { api.grantSpecialCategoryConsent(it) }
    suspend fun withdrawSpecialCategoryConsent() = call { api.withdrawSpecialCategoryConsent(it) }
    suspend fun createInterview(name: String, relation: String?, decade: Int?, key: String): FeatureResult<InterviewCreation> {
        if (name.trim().isEmpty() || name.trim().length > 120 || decade !in setOf(null, 0,10,20,30,40,50,60,70,80,90,100)) {
            return FeatureResult.Unavailable("Enter a valid person and life chapter.")
        }
        return call { api.createInterview(name.trim(), relation?.trim()?.takeIf(String::isNotEmpty), decade, key, it) }
    }
    suspend fun respond(id: String, content: String, key: String): FeatureResult<InterviewResponse> {
        val clean = content.trim()
        if (clean.isEmpty() || clean.length > 4_000) return FeatureResult.Unavailable("Enter a response of 4,000 characters or fewer.")
        return call { api.respond(id, clean, key, it) }
    }
    suspend fun status(id: String, status: String): FeatureResult<InterviewMutation> {
        if (status !in setOf("active", "complete")) return FeatureResult.Unavailable("Choose a supported interview status.")
        return call { api.status(id, status, it) }
    }
    suspend fun deleteInterview(id: String) = call(destructive = true) { api.deleteInterview(id, it) }
    suspend fun insights(id: String) = call { api.insights(id, it) }
    suspend fun narrative(id: String, generate: Boolean) = call { api.narrative(id, generate, it) }
    suspend fun share(id: String, method: String) = call { api.share(id, method, it) }
    suspend fun media() = call { api.media(it) }
    suspend fun mediaDetail(id: String) = call { api.mediaDetail(id, it) }
    suspend fun deleteMedia(id: String) = call(destructive = true) { api.deleteMedia(id, it) }
    suspend fun updateCaption(id: String, caption: String): FeatureResult<MediaMutation> {
        if (caption.length > 300) return FeatureResult.Unavailable("Caption must be 300 characters or fewer.")
        return call { api.updateCaption(id, caption.trim(), it) }
    }
    suspend fun updateTags(id: String, tags: List<String>): FeatureResult<MediaTagsMutation> {
        val clean = tags.map(String::trim).filter(String::isNotEmpty).distinct()
        if (clean.size > 10 || clean.any { it.length > 30 }) return FeatureResult.Unavailable("Use up to 10 tags, each 30 characters or fewer.")
        return call { api.updateTags(id, clean, it) }
    }
    suspend fun playback(url: String) = api.playback(url)

    private suspend fun <T> call(
        destructive: Boolean = false,
        block: suspend (String) -> ApiResult<T>,
    ): FeatureResult<T> {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = block(lease.accessToken)
        if (result is ApiResult.Unauthorized) {
            val recovered = sessions.recoverFromUnauthorized(lease.accessToken)
            if (recovered !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
            result = block(recovered.accessToken)
        }
        if (destructive) return destructiveFeatureResult(result, sessions::signOut)
        return when (result) {
            is ApiResult.Success -> FeatureResult.Success(result.value)
            is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired
            is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference)
        }
    }
}
