package io.narratrace.android.core.offline

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.network.ApiResult

class OfflineRepository(private val api: OfflineApi, private val sessions: SessionManager, val store: OfflineDraftStore) {
    suspend fun syncPrivateDraft(draft: OfflineLetterDraft, owner: SessionManager.AccountOperationLease): io.narratrace.android.core.media.FeatureResult<DraftState> {
        if (!sessions.isCurrent(owner) || draft.ownerAccountId != owner.accountId) return io.narratrace.android.core.media.FeatureResult.AuthenticationRequired
        val token = sessions.accessToken()
        if (!sessions.isCurrent(owner) || token !is TokenLease.Valid) return io.narratrace.android.core.media.FeatureResult.AuthenticationRequired
        val result = api.sync(draft.copy(idempotencyKey = java.util.UUID.randomUUID().toString()), token.accessToken)
        if (!sessions.isCurrent(owner)) return io.narratrace.android.core.media.FeatureResult.AuthenticationRequired
        return when (result) {
            is ApiResult.Success -> {
                if (result.value.draft.status != "ok") return io.narratrace.android.core.media.FeatureResult.Unavailable("Draft sync was not accepted. Your private draft remains on this device.")
                val saved = sessions.withCurrent(owner) { store.save(draft.copy(revision = result.value.draft.revision)) } == true
                if (saved) io.narratrace.android.core.media.FeatureResult.Success(result.value.draft)
                else io.narratrace.android.core.media.FeatureResult.Unavailable("Draft text synced, but the device copy could not be updated. Review it before trying again.")
            }
            is ApiResult.Unauthorized -> io.narratrace.android.core.media.FeatureResult.AuthenticationRequired
            is ApiResult.Failure -> io.narratrace.android.core.media.FeatureResult.Unavailable(result.message, result.supportReference, offline = result is ApiResult.Offline)
        }
    }
    suspend fun reconcile(): Int {
        val owner = sessions.captureOperationLease() ?: return 0
        val token = (sessions.accessToken() as? TokenLease.Valid)?.accessToken ?: return store.load().size
        if (!sessions.isCurrent(owner)) return 0
        val lease = api.lease(token) as? ApiResult.Success ?: return store.load().size
        if (!sessions.isCurrent(owner)) return 0
        if (!lease.value.lease.authoritative || "letter.draft.sync" !in lease.value.lease.scopes) return store.load().size
        store.load().filterNot { it.requiresDeliveryReview }.forEach { draft ->
            if (!sessions.isCurrent(owner) || draft.ownerAccountId != owner.accountId) return 0
            val result = api.sync(draft, token)
            if (!sessions.isCurrent(owner)) return 0
            if (result is ApiResult.Success && result.value.draft.status == "ok") {
                sessions.withCurrent(owner) { store.remove(draft.clientDraftId) }
            }
        }
        return store.load().size
    }
}
