package io.narratrace.android.core.offline

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

@Serializable data class OfflineLease(val leaseId: String, val expiresAt: String, val scopes: List<String>, val authoritative: Boolean)
@Serializable data class OfflineLeaseResponse(val lease: OfflineLease)
@Serializable data class DraftState(val status: String, val draftId: String, val revision: Int, val draftState: String)
@Serializable data class DraftResponse(val draft: DraftState)
@Serializable private data class DraftInput(val clientDraftId: String, val baseRevision: Int, val toName: String?, val subject: String, val body: String, val unlockAt: String?)

class OfflineApi(private val client: NarratraceApiClient) {
    suspend fun lease(token: String): ApiResult<OfflineLeaseResponse> = client.post("/api/v1/mobile/offline-lease", null, serializer<OfflineLeaseResponse>(), token)
    suspend fun sync(draft: OfflineLetterDraft, token: String): ApiResult<DraftResponse> = client.post(
        "/api/v1/mobile/offline-letter-drafts", NarratraceJson.encodeToString(DraftInput(draft.clientDraftId, draft.revision, draft.recipientName, draft.subject, draft.body, draft.unlockAt)), serializer<DraftResponse>(), token, draft.idempotencyKey,
    )
}
