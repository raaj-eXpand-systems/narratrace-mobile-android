package io.narratrace.android.core.media

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

@Serializable data class UploadAuthorization(val uploadUrl: String, val storagePath: String)
@Serializable data class PreservationAcknowledgement(val originalDurablyStored: Boolean, val integrityVerified: Boolean)
@Serializable data class UploadConfirmation(val preservationAcknowledgement: PreservationAcknowledgement)
@Serializable private data class UploadRequest(
    val action: String, val kind: String, val filename: String, val fileSize: Int,
    val mimeType: String, val fileHash: String, val storagePath: String? = null,
)
@Serializable data class InterviewSummary(
    val id: String, val subjectName: String, val subjectRelation: String? = null,
    val lifeDecade: Int? = null, val status: String, val messageCount: Int,
    val createdAt: String, val updatedAt: String,
)
@Serializable data class InterviewList(val interviews: List<InterviewSummary>, val nextCursor: String? = null)
@Serializable data class InterviewCreation(val interview: InterviewSummary, val replayed: Boolean = false)
@Serializable private data class CreateInterview(val subjectName: String, val subjectRelation: String?, val lifeDecade: Int?)
@Serializable data class InterviewMessage(
    val id: String, val role: String, val content: String, val hasMedia: Boolean = false,
    val mediaType: String? = null, val createdAt: String,
)
@Serializable data class InterviewDetail(
    val interview: InterviewSummary, val messages: List<InterviewMessage>, val narrative: String? = null,
)
@Serializable data class InterviewResponse(
    val message: InterviewMessage,
    val replayed: Boolean = false,
    val requiresCheckout: Boolean = false,
    val preservationAcknowledgement: PreservationAcknowledgement? = null,
)
@Serializable private data class InterviewTextResponse(val content: String)
@Serializable data class RecordingCapacity(
    val remainingBytes: Long, val remainingLabel: String, val audioMaxSeconds: Int, val videoMaxSeconds: Int,
)
@Serializable data class LegalAcceptance(
    val termsAccepted: Boolean, val privacyAcknowledged: Boolean, val aiNoticeAcknowledged: Boolean,
    val specialCategoryConsent: Boolean, val contentRightsAttested: Boolean,
    val termsVersion: String, val privacyVersion: String, val aiNoticeVersion: String,
    val contentRightsVersion: String, val acceptedAt: String? = null,
)
@Serializable internal data class LegalAcknowledgement(
    val acceptTerms: Boolean? = null,
    val acknowledgePrivacy: Boolean? = null,
    val acknowledgeAiNotice: Boolean? = null,
    val attestContentRights: Boolean? = null,
    val specialCategoryConsent: Boolean? = null,
)
@Serializable private data class InterviewStatus(val status: String)
@Serializable data class InterviewMutation(val updated: Boolean? = null, val deleted: Boolean? = null)
@Serializable data class InterviewHighlight(val id: String, val title: String, val excerpt: String, val type: String)
@Serializable data class InterviewInsights(val covered: List<String>, val highlights: List<InterviewHighlight>)
@Serializable data class InterviewNarrative(val narrative: String? = null)
@Serializable data class NarrativeGroundingConsent(val groundingAgreementAccepted: Boolean = true)
@Serializable data class InterviewShare(val shareToken: String? = null)
@Serializable data class MediaSummary(val id: String, val kind: String, val title: String, val state: String, val duration: Int? = null, val createdAt: String)
@Serializable data class MediaList(val media: List<MediaSummary>)
@Serializable data class MediaInsight(val label: String, val value: String)
@Serializable data class MediaDetail(
    val id: String, val kind: String, val title: String, val state: String, val duration: Int? = null,
    val createdAt: String, val text: String? = null, val transcript: String? = null,
    val summary: String? = null, val caption: String? = null, val narrative: String? = null,
    val tags: List<String> = emptyList(), val customTags: List<String> = emptyList(),
    val insights: List<MediaInsight> = emptyList(), val playbackUrl: String? = null,
)
@Serializable data class MediaDetailResponse(val kind: String, val media: MediaDetail)
@Serializable data class Deleted(val deleted: Boolean)
@Serializable private data class MediaCaptionInput(val caption: String)
@Serializable private data class MediaTagsInput(val customTags: List<String>)
@Serializable data class MediaMutation(val kind: String, val media: MediaSummary)
@Serializable data class MediaTagsMutation(val kind: String, val customTags: List<String>)
@Serializable private data class VideoUploadRequest(val filename: String, val fileSize: Int, val mimeType: String, val fileHash: String)
@Serializable data class VideoAuthorization(val kind: String? = null, val uploadUrl: String, val videoId: String, val maxSeconds: Int? = null)
@Serializable data class VideoPreservation(val id: String, val videoId: String, val state: String, val preservationAcknowledgement: PreservationAcknowledgement? = null)
@Serializable data class VideoPreservationResponse(val kind: String, val video: VideoPreservation)
@Serializable private data class InterviewVideoRequest(val action: String, val mimeType: String? = null, val fileSize: Int? = null, val videoId: String? = null, val content: String? = null)

class MediaAndInterviewApi(private val client: NarratraceApiClient) {
    suspend fun authorizeUpload(item: PendingMedia, token: String): ApiResult<UploadAuthorization> = client.post(
        "/api/v1/uploads", NarratraceJson.encodeToString(UploadRequest(
            "authorize", if (item.kind == PendingMediaKind.Photo) "photo" else "audio", item.originalFilename, item.byteCount, item.mimeType, item.sha256,
        )), serializer<UploadAuthorization>(), token,
    )
    suspend fun confirmUpload(item: PendingMedia, auth: UploadAuthorization, token: String): ApiResult<UploadConfirmation> = client.post(
        "/api/v1/uploads", NarratraceJson.encodeToString(UploadRequest(
            "confirm", if (item.kind == PendingMediaKind.Photo) "photo" else "audio", item.originalFilename, item.byteCount, item.mimeType, item.sha256, auth.storagePath,
        )), serializer<UploadConfirmation>(), token,
    )
    suspend fun transfer(auth: UploadAuthorization, bytes: ByteArray, mime: String) = client.putSignedStorage(auth.uploadUrl, bytes, mime)
    suspend fun interviews(token: String): ApiResult<InterviewList> = client.get("/api/v1/interviews?limit=100", serializer<InterviewList>(), token)
    suspend fun createInterview(name: String, relation: String?, decade: Int?, key: String, token: String): ApiResult<InterviewCreation> = client.post(
        "/api/v1/interviews", NarratraceJson.encodeToString(CreateInterview(name, relation, decade)), serializer<InterviewCreation>(), token, key,
    )
    suspend fun interview(id: String, token: String): ApiResult<InterviewDetail> = client.get(
        "/api/v1/interviews/${segment(id)}", serializer<InterviewDetail>(), token,
    )
    suspend fun respond(id: String, content: String, key: String, token: String): ApiResult<InterviewResponse> = client.post(
        "/api/v1/interviews/${segment(id)}/responses", NarratraceJson.encodeToString(InterviewTextResponse(content)),
        serializer<InterviewResponse>(), token, key,
    )
    suspend fun respondAudio(id: String, bytes: ByteArray, mime: String, sha256: String, key: String, token: String): ApiResult<InterviewResponse> = client.postBytes(
        "/api/v1/interviews/${segment(id)}/audio-responses", bytes, mime, sha256, serializer<InterviewResponse>(), token, key,
    )
    suspend fun capacity(token: String): ApiResult<RecordingCapacity> = client.get(
        "/api/v1/interviews/recording-capacity", serializer<RecordingCapacity>(), token,
    )
    suspend fun legal(token: String): ApiResult<LegalAcceptance> = client.get("/api/v1/legal/acceptance", serializer<LegalAcceptance>(), token)
    suspend fun acceptTerms(token: String) = legalChoice(LegalAcknowledgement(acceptTerms = true), token)
    suspend fun acknowledgePrivacy(token: String) = legalChoice(LegalAcknowledgement(acknowledgePrivacy = true), token)
    suspend fun acknowledgeAiNotice(token: String) = legalChoice(LegalAcknowledgement(acknowledgeAiNotice = true), token)
    suspend fun attestContentRights(token: String) = legalChoice(LegalAcknowledgement(attestContentRights = true), token)
    suspend fun grantSpecialCategoryConsent(token: String) = legalChoice(LegalAcknowledgement(specialCategoryConsent = true), token)
    suspend fun withdrawSpecialCategoryConsent(token: String): ApiResult<LegalAcceptance> = client.delete(
        "/api/v1/legal/acceptance", serializer<LegalAcceptance>(), token,
    )
    private suspend fun legalChoice(choice: LegalAcknowledgement, token: String): ApiResult<LegalAcceptance> = client.post(
        "/api/v1/legal/acceptance", NarratraceJson.encodeToString(choice), serializer<LegalAcceptance>(), token,
    )
    suspend fun status(id: String, status: String, token: String): ApiResult<InterviewMutation> = client.patch(
        "/api/v1/interviews/${segment(id)}", NarratraceJson.encodeToString(InterviewStatus(status)), serializer<InterviewMutation>(), token,
    )
    suspend fun deleteInterview(id: String, token: String): ApiResult<InterviewMutation> = client.delete("/api/v1/interviews/${segment(id)}", serializer<InterviewMutation>(), token)
    suspend fun insights(id: String, token: String): ApiResult<InterviewInsights> = client.get("/api/v1/interviews/${segment(id)}/insights", serializer<InterviewInsights>(), token)
    suspend fun narrative(id: String, generate: Boolean, token: String): ApiResult<InterviewNarrative> = if (generate) client.post(
        "/api/v1/interviews/${segment(id)}/narrative", NarratraceJson.encodeToString(NarrativeGroundingConsent()), serializer<InterviewNarrative>(), token,
    ) else client.get("/api/v1/interviews/${segment(id)}/narrative", serializer<InterviewNarrative>(), token)
    suspend fun share(id: String, method: String, token: String): ApiResult<InterviewShare> = when (method) {
        "POST" -> client.post("/api/v1/interviews/${segment(id)}/share", null, serializer<InterviewShare>(), token)
        "DELETE" -> client.delete("/api/v1/interviews/${segment(id)}/share", serializer<InterviewShare>(), token)
        else -> client.get("/api/v1/interviews/${segment(id)}/share", serializer<InterviewShare>(), token)
    }
    suspend fun media(token: String): ApiResult<MediaList> = client.get("/api/v1/media", serializer<MediaList>(), token)
    suspend fun mediaDetail(id: String, token: String): ApiResult<MediaDetailResponse> = client.get("/api/v1/media/${segment(id)}", serializer<MediaDetailResponse>(), token)
    suspend fun deleteMedia(id: String, token: String): ApiResult<Deleted> = client.delete("/api/v1/media/${segment(id)}", serializer<Deleted>(), token)
    suspend fun updateCaption(id: String, caption: String, token: String): ApiResult<MediaMutation> = client.patch("/api/v1/media/${segment(id)}", NarratraceJson.encodeToString(MediaCaptionInput(caption)), serializer<MediaMutation>(), token)
    suspend fun updateTags(id: String, tags: List<String>, token: String): ApiResult<MediaTagsMutation> = client.patch("/api/v1/media/${segment(id)}", NarratraceJson.encodeToString(MediaTagsInput(tags)), serializer<MediaTagsMutation>(), token)
    suspend fun playback(url: String): ByteArray? = client.getSignedStorage(url)
    suspend fun authorizeVideo(item: PendingMedia, token: String): ApiResult<VideoAuthorization> = if (item.kind == PendingMediaKind.InterviewVideo) client.post(
        "/api/v1/interviews/${segment(item.interviewId.orEmpty())}/video-responses",
        NarratraceJson.encodeToString(InterviewVideoRequest("authorize", item.mimeType, item.byteCount)), serializer<VideoAuthorization>(), token,
    ) else client.post(
        "/api/v1/videos", NarratraceJson.encodeToString(VideoUploadRequest(item.originalFilename, item.byteCount, item.mimeType, item.sha256)), serializer<VideoAuthorization>(), token,
    )
    suspend fun transferVideo(url: String, bytes: ByteArray) = client.uploadTus(url, bytes)
    suspend fun transferVideo(url: String, item: PendingMedia, queue: ProtectedMediaQueue) =
        client.uploadTus(url, item.byteCount) { offset, size -> queue.readRange(item, offset, size) }
    suspend fun videoPreservation(id: String, token: String): ApiResult<VideoPreservationResponse> = client.get("/api/v1/videos?id=${segment(id)}", serializer<VideoPreservationResponse>(), token)
    suspend fun confirmInterviewVideo(item: PendingMedia, token: String): ApiResult<InterviewResponse> = client.post(
        "/api/v1/interviews/${segment(item.interviewId.orEmpty())}/video-responses",
        NarratraceJson.encodeToString(InterviewVideoRequest("confirm", videoId = item.serverId, content = "")), serializer<InterviewResponse>(), token, item.idempotencyKey,
    )
}

@Suppress("DEPRECATION") private fun segment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
