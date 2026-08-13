package io.narratrace.android.core.offline

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.network.ApiResult

class OfflineRepository(private val api: OfflineApi, private val sessions: SessionManager, val store: OfflineDraftStore) {
    suspend fun reconcile(): Int {
        val token = (sessions.accessToken() as? TokenLease.Valid)?.accessToken ?: return store.load().size
        val lease = api.lease(token) as? ApiResult.Success ?: return store.load().size
        if (!lease.value.lease.authoritative || "letter.draft.sync" !in lease.value.lease.scopes) return store.load().size
        store.load().forEach { draft ->
            val result = api.sync(draft, token)
            if (result is ApiResult.Success && result.value.draft.status == "ok") store.remove(draft.clientDraftId)
        }
        return store.load().size
    }
}
