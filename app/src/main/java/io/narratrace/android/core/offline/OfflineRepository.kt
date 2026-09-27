package io.narratrace.android.core.offline

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.network.ApiResult

class OfflineRepository(private val api: OfflineApi, private val sessions: SessionManager, val store: OfflineDraftStore) {
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
