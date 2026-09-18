package io.narratrace.android.core.family

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.media.destructiveFeatureResult
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

@Serializable data class BlockSource(val kind: String, val id: String, val email: String? = null, val memberId: String? = null, val target: String? = null)
@Serializable data class UserBlock(val accountId: String, val createdAt: String)
@Serializable data class UserBlockList(val blocks: List<UserBlock>)
@Serializable data class BlockMutation(val blocked: Boolean, val changed: Boolean)
@Serializable internal data class BlockInput(val source: BlockSource)
@Serializable internal data class UnblockInput(val accountId: String)

class UserBlocksApi(private val client: NarratraceApiClient) {
    suspend fun list(token: String): ApiResult<UserBlockList> = client.get("/api/v1/account/blocks", serializer<UserBlockList>(), token)
    suspend fun block(source: BlockSource, token: String): ApiResult<BlockMutation> = client.post("/api/v1/account/blocks", NarratraceJson.encodeToString(BlockInput(source)), serializer<BlockMutation>(), token)
    suspend fun unblock(accountId: String, token: String): ApiResult<BlockMutation> = client.delete("/api/v1/account/blocks", serializer<BlockMutation>(), token, NarratraceJson.encodeToString(UnblockInput(accountId)))
}

class UserBlocksRepository(private val api: UserBlocksApi, private val sessions: SessionManager, private val invalidateSharedViews: () -> Unit) {
    suspend fun list() = call { api.list(it) }
    suspend fun block(source: BlockSource) = mutate(true) { api.block(source, it) }
    suspend fun unblock(accountId: String) = mutate(false) { api.unblock(accountId, it) }
    private suspend fun mutate(expected: Boolean, operation: suspend (String) -> ApiResult<BlockMutation>): FeatureResult<BlockMutation> {
        val result = call(operation)
        return finishBlockMutation(result, expected, invalidateSharedViews)
    }
    private suspend fun <T> call(operation: suspend (String) -> ApiResult<T>): FeatureResult<T> {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = operation(lease.accessToken)
        if (result is ApiResult.Unauthorized) {
            val refreshed = sessions.recoverFromUnauthorized(lease.accessToken)
            if (refreshed !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
            result = operation(refreshed.accessToken)
        }
        return destructiveFeatureResult(result, sessions::signOut)
    }
}

internal fun finishBlockMutation(result: FeatureResult<BlockMutation>, expected: Boolean, invalidate: () -> Unit): FeatureResult<BlockMutation> {
    if (result is FeatureResult.Success) {
        // Repeated mutations still refresh access. Never erase the owned offline draft store.
        invalidate()
        if (result.value.blocked != expected) return FeatureResult.Unavailable("The change could not be confirmed. Refresh blocked people before trying again.")
    }
    return result
}
