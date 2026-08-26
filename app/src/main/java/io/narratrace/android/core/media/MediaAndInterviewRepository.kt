package io.narratrace.android.core.media

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.network.ApiResult

sealed interface FeatureResult<out T> {
    data class Success<T>(val value: T) : FeatureResult<T>
    data object AuthenticationRequired : FeatureResult<Nothing>
    data class Unavailable(val message: String, val supportReference: String = "") : FeatureResult<Nothing>
}

class MediaAndInterviewRepository(
    private val api: MediaAndInterviewApi,
    private val sessions: SessionManager,
    val queue: ProtectedMediaQueue,
) {
    suspend fun reconcile(): Int {
        val token = (sessions.accessToken() as? TokenLease.Valid)?.accessToken ?: return queue.items().size
        queue.items().forEach { item ->
            queue.markAttempt(item.id)
            when (item.kind) {
                PendingMediaKind.StandaloneAudio, PendingMediaKind.Photo -> {
                    val bytes = queue.read(item) ?: return@forEach
                    val auth = api.authorizeUpload(item, token) as? ApiResult.Success ?: return@forEach
                    if (!api.transfer(auth.value, bytes, item.mimeType)) return@forEach
                    val confirmation = api.confirmUpload(item, auth.value, token) as? ApiResult.Success ?: return@forEach
                    val ack = confirmation.value.preservationAcknowledgement
                    if (ack.originalDurablyStored && ack.integrityVerified) queue.acknowledgeAndRemove(item.id)
                }
                PendingMediaKind.InterviewAudio -> {
                    val bytes = queue.read(item) ?: return@forEach
                    val id = item.interviewId ?: return@forEach
                    if (api.respondAudio(id, bytes, item.mimeType, item.idempotencyKey, token) is ApiResult.Success) {
                        queue.acknowledgeAndRemove(item.id)
                    }
                }
                PendingMediaKind.StandaloneVideo, PendingMediaKind.InterviewVideo -> {
                    var current = item
                    if (current.uploadUrl == null || current.serverId == null) {
                        val authorization = api.authorizeVideo(current, token) as? ApiResult.Success ?: return@forEach
                        if (!queue.setAuthorization(current.id, authorization.value.uploadUrl, authorization.value.videoId)) return@forEach
                        current = queue.items().firstOrNull { it.id == current.id } ?: return@forEach
                    }
                    if (!api.transferVideo(current.uploadUrl!!, current, queue)) return@forEach
                    if (current.kind == PendingMediaKind.InterviewVideo) {
                        if (api.confirmInterviewVideo(current, token) is ApiResult.Success) queue.acknowledgeAndRemove(current.id)
                    } else {
                        val preserved = api.videoPreservation(current.serverId!!, token) as? ApiResult.Success ?: return@forEach
                        val ack = preserved.value.video.preservationAcknowledgement
                        if (ack?.originalDurablyStored == true && ack.integrityVerified) queue.acknowledgeAndRemove(current.id)
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
    suspend fun deleteInterview(id: String) = call { api.deleteInterview(id, it) }
    suspend fun insights(id: String) = call { api.insights(id, it) }
    suspend fun narrative(id: String, generate: Boolean) = call { api.narrative(id, generate, it) }
    suspend fun share(id: String, method: String) = call { api.share(id, method, it) }
    suspend fun media() = call { api.media(it) }
    suspend fun mediaDetail(id: String) = call { api.mediaDetail(id, it) }
    suspend fun deleteMedia(id: String) = call { api.deleteMedia(id, it) }
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

    private suspend fun <T> call(block: suspend (String) -> ApiResult<T>): FeatureResult<T> {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = block(lease.accessToken)
        if (result is ApiResult.Unauthorized) {
            val recovered = sessions.recoverFromUnauthorized(lease.accessToken)
            if (recovered !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
            result = block(recovered.accessToken)
        }
        return when (result) {
            is ApiResult.Success -> FeatureResult.Success(result.value)
            is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired
            is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference)
        }
    }
}
