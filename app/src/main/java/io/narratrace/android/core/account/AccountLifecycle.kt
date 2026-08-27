package io.narratrace.android.core.account

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

@Serializable
data class AccountLifecycleSignal(
    val state: String,
    val effectiveAt: String,
    val recoveryEndsAt: String? = null,
    val purgeEligibleAt: String? = null,
    val reasonCode: String? = null,
    val appealStatus: String,
    val appealUrl: String? = null,
    val localDataDisposition: String,
    val installationBound: Boolean,
)

@Serializable
data class AccountClosureStatus(
    val accountClosed: Boolean,
    val closedAt: String? = null,
    val graceEndsAt: String? = null,
    val daysLeft: Int? = null,
    val expired: Boolean = false,
    val supportRef: String? = null,
    val purgeScheduledAt: String? = null,
    val closureFinalizedAt: String? = null,
    val refundAmount: Int = 0,
    val currency: String = "usd",
    val requiresRecentAuthentication: Boolean = true,
)

@Serializable
data class AccountClosureMutation(
    val ok: Boolean,
    val accountClosed: Boolean,
    val supportRef: String? = null,
    val restoredStatus: String? = null,
    val requiresSignIn: Boolean = false,
)

@Serializable
internal data class AccountClosureAction(val action: String)

class AccountLifecycleApi(private val client: NarratraceApiClient) {
    /**
     * Uses the last access credential directly. The server retains only its hash as
     * a restricted lifecycle signal after revocation; this call can never restore
     * product access or read account content.
     */
    suspend fun signal(accessCredential: String): ApiResult<AccountLifecycleSignal> =
        client.get("/api/v1/account/lifecycle", serializer<AccountLifecycleSignal>(), accessCredential)

    suspend fun closureStatus(accessCredential: String): ApiResult<AccountClosureStatus> =
        client.get("/api/v1/account/closure", serializer<AccountClosureStatus>(), accessCredential)

    suspend fun close(accessCredential: String): ApiResult<AccountClosureMutation> =
        closureAction("close", accessCredential)

    suspend fun reopen(lifecycleCredential: String): ApiResult<AccountClosureMutation> =
        closureAction("reopen", lifecycleCredential)

    private suspend fun closureAction(action: String, credential: String): ApiResult<AccountClosureMutation> =
        client.post(
            "/api/v1/account/closure",
            io.narratrace.android.core.network.NarratraceJson.encodeToString(AccountClosureAction(action)),
            serializer<AccountClosureMutation>(),
            credential,
        )
}

internal fun AccountLifecycleSignal.requiresLocalPurge(): Boolean =
    localDataDisposition == "purge_account_data" && state in setOf("deletion_in_progress", "deleted")

internal fun AccountLifecycleSignal.allowsOrdinaryAccess(): Boolean =
    state in setOf("active", "lapsed", "dormant")

/** Only open the rights-preserving Narratrace appeal route supplied by the API. */
internal fun AccountLifecycleSignal.safeAppealUrl(): String? {
    if (state !in setOf("suspended", "company_terminated")) return null
    if (appealStatus !in setOf("available", "submitted")) return null
    val candidate = appealUrl ?: return null
    val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return null
    return candidate.takeIf {
        uri.scheme == "https" && uri.host == "www.narratrace.io" && uri.port == -1 &&
            uri.userInfo == null && uri.path == "/account/appeal" && uri.fragment == null
    }
}
