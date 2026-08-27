package io.narratrace.android.core.customer

import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.media.FeatureResult

data class CustomerHome(val account: AccountSummary, val home: HomeSummary)

sealed interface CustomerHomeResult {
    data class Success(val value: CustomerHome) : CustomerHomeResult
    data object AuthenticationRequired : CustomerHomeResult
    data class Unavailable(val message: String, val supportReference: String = "") : CustomerHomeResult
}

sealed interface CustomerMemoriesResult {
    data class Success(val value: MemoryList) : CustomerMemoriesResult
    data object AuthenticationRequired : CustomerMemoriesResult
    data class Unavailable(val message: String, val supportReference: String = "") : CustomerMemoriesResult
}

sealed interface CustomerPeopleResult {
    data class Success(val value: PeopleList) : CustomerPeopleResult
    data object AuthenticationRequired : CustomerPeopleResult
    data class Unavailable(val message: String, val supportReference: String = "") : CustomerPeopleResult
}

sealed interface CustomerPersonResult {
    data class Success(val value: RemotePersonDetail) : CustomerPersonResult
    data object AuthenticationRequired : CustomerPersonResult
    data class Unavailable(val message: String, val supportReference: String = "") : CustomerPersonResult
}

sealed interface AccountResult {
    data class Success(val value: AccountSummary) : AccountResult
    data object AuthenticationRequired : AccountResult
    data class Unavailable(val message: String, val supportReference: String = "") : AccountResult
}

sealed interface WrittenMemoryResult {
    data class Success(val id: String, val replayed: Boolean) : WrittenMemoryResult
    data object AuthenticationRequired : WrittenMemoryResult
    data class Unavailable(val message: String, val supportReference: String = "") : WrittenMemoryResult
}

sealed interface CustomerMemoryResult {
    data class Success(val value: RemoteMemory) : CustomerMemoryResult
    data object AuthenticationRequired : CustomerMemoryResult
    data class Unavailable(val message: String, val supportReference: String = "") : CustomerMemoryResult
}

class CustomerRepository(
    private val gateway: CustomerGateway,
    private val sessions: SessionManager,
) {
    suspend fun search(query: String): FeatureResult<SearchResponse> {
        val clean = query.trim()
        if (clean.length < 2 || clean.length > 100) return FeatureResult.Unavailable("Enter at least two characters to search your archive.")
        return genericCall { gateway.search(it, clean) }
    }

    suspend fun activity(): FeatureResult<ActivityPage> = genericCall { gateway.activity(it) }

    suspend fun createPerson(name: String, relation: String): FeatureResult<RemotePerson> {
        val cleanName = name.trim(); val cleanRelation = relation.trim()
        if (cleanName.isEmpty() || cleanName.length > 200 || cleanRelation.length > 100) return FeatureResult.Unavailable("Enter a name and optional relationship within the supported limits.")
        return when (val result = genericCall { gateway.createPerson(it, cleanName, cleanRelation) }) {
            is FeatureResult.Success -> FeatureResult.Success(result.value.person)
            is FeatureResult.Unavailable -> FeatureResult.Unavailable(result.message, result.supportReference)
            FeatureResult.AuthenticationRequired -> FeatureResult.AuthenticationRequired
        }
    }

    suspend fun updatePerson(id: String, name: String, relation: String): FeatureResult<Boolean> {
        val cleanName = name.trim(); val cleanRelation = relation.trim()
        if (cleanName.isEmpty() || cleanName.length > 200 || cleanRelation.length > 100) return FeatureResult.Unavailable("Enter a name and optional relationship within the supported limits.")
        return when (val result = genericCall { gateway.updatePerson(it, id, cleanName, cleanRelation) }) {
            is FeatureResult.Success -> FeatureResult.Success(result.value.updated)
            is FeatureResult.Unavailable -> FeatureResult.Unavailable(result.message, result.supportReference)
            FeatureResult.AuthenticationRequired -> FeatureResult.AuthenticationRequired
        }
    }
    suspend fun loadHome(): CustomerHomeResult {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toHomeResult()
        return loadWithToken(lease.accessToken, allowRecovery = true)
    }

    suspend fun loadMemories(): CustomerMemoriesResult {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toMemoriesResult()
        return loadMemoriesWithToken(lease.accessToken, allowRecovery = true)
    }

    suspend fun loadPeople(): CustomerPeopleResult {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toPeopleResult()
        return loadPeopleWithToken(lease.accessToken, allowRecovery = true)
    }

    suspend fun loadPerson(id: String): CustomerPersonResult {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toPersonResult()
        return loadPersonWithToken(lease.accessToken, id, allowRecovery = true)
    }

    suspend fun loadAccount(): AccountResult {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toAccountResult()
        return loadAccountWithToken(lease.accessToken, allowRecovery = true)
    }

    suspend fun createWrittenMemory(
        title: String,
        content: String,
        idempotencyKey: String,
    ): WrittenMemoryResult {
        val normalizedTitle = title.trim()
        val normalizedContent = content.trim()
        if (normalizedTitle.isEmpty() || normalizedTitle.length > 120 ||
            normalizedContent.isEmpty() || normalizedContent.length > 50_000
        ) return WrittenMemoryResult.Unavailable("Enter a title and Memory within the supported limits.")
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toWrittenMemoryResult()
        return createWrittenMemoryWithToken(
            lease.accessToken, normalizedTitle, normalizedContent, idempotencyKey, allowRecovery = true,
        )
    }

    suspend fun loadMemory(id: String): CustomerMemoryResult {
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toMemoryResult()
        return loadMemoryWithToken(lease.accessToken, id, allowRecovery = true)
    }

    suspend fun updateMemory(
        id: String,
        visibility: String? = null,
        pinned: Boolean? = null,
        status: String? = null,
    ): CustomerMemoryResult {
        if (visibility !in setOf(null, "private", "family") ||
            status !in setOf(null, "active", "dismissed") ||
            (visibility == null && pinned == null && status == null)
        ) return CustomerMemoryResult.Unavailable("Choose a supported Memory action.")
        val lease = sessions.accessToken()
        if (lease !is TokenLease.Valid) return lease.toMemoryResult()
        return updateMemoryWithToken(
            lease.accessToken, id, visibility, pinned, status, allowRecovery = true,
        )
    }

    private suspend fun loadWithToken(token: String, allowRecovery: Boolean): CustomerHomeResult {
        val account = gateway.account(token)
        if (account is ApiResult.Unauthorized && allowRecovery) return recover(token)
        if (account is ApiResult.Failure) return account.toHomeResult()

        val home = gateway.home(token)
        if (home is ApiResult.Unauthorized && allowRecovery) return recover(token)
        if (home is ApiResult.Failure) return home.toHomeResult()

        return CustomerHomeResult.Success(
            CustomerHome(
                account = (account as ApiResult.Success).value,
                home = (home as ApiResult.Success).value,
            ),
        )
    }

    private suspend fun recover(rejectedToken: String): CustomerHomeResult =
        when (val lease = sessions.recoverFromUnauthorized(rejectedToken)) {
            is TokenLease.Valid -> loadWithToken(lease.accessToken, allowRecovery = false)
            else -> lease.toHomeResult()
        }

    private suspend fun loadMemoriesWithToken(
        token: String,
        allowRecovery: Boolean,
    ): CustomerMemoriesResult = when (val result = gateway.memories(token)) {
        is ApiResult.Success -> CustomerMemoriesResult.Success(result.value)
        is ApiResult.Unauthorized -> if (allowRecovery) {
            when (val lease = sessions.recoverFromUnauthorized(token)) {
                is TokenLease.Valid -> loadMemoriesWithToken(lease.accessToken, allowRecovery = false)
                else -> lease.toMemoriesResult()
            }
        } else {
            CustomerMemoriesResult.AuthenticationRequired
        }
        is ApiResult.Failure -> CustomerMemoriesResult.Unavailable(result.message, result.supportReference)
    }

    private fun TokenLease.toHomeResult(): CustomerHomeResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> CustomerHomeResult.AuthenticationRequired
        TokenLease.Unavailable -> CustomerHomeResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to load data.")
    }

    private fun ApiResult.Failure.toHomeResult(): CustomerHomeResult.Unavailable =
        CustomerHomeResult.Unavailable(message, supportReference)

    private fun TokenLease.toMemoriesResult(): CustomerMemoriesResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> CustomerMemoriesResult.AuthenticationRequired
        TokenLease.Unavailable -> CustomerMemoriesResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to load data.")
    }

    private suspend fun loadPeopleWithToken(token: String, allowRecovery: Boolean): CustomerPeopleResult =
        when (val result = gateway.people(token)) {
            is ApiResult.Success -> CustomerPeopleResult.Success(result.value)
            is ApiResult.Unauthorized -> if (allowRecovery) {
                when (val lease = sessions.recoverFromUnauthorized(token)) {
                    is TokenLease.Valid -> loadPeopleWithToken(lease.accessToken, allowRecovery = false)
                    else -> lease.toPeopleResult()
                }
            } else CustomerPeopleResult.AuthenticationRequired
            is ApiResult.Failure -> CustomerPeopleResult.Unavailable(result.message, result.supportReference)
        }

    private suspend fun loadPersonWithToken(token: String, id: String, allowRecovery: Boolean): CustomerPersonResult =
        when (val result = gateway.person(token, id)) {
            is ApiResult.Success -> CustomerPersonResult.Success(result.value.person)
            is ApiResult.Unauthorized -> if (allowRecovery) {
                when (val lease = sessions.recoverFromUnauthorized(token)) {
                    is TokenLease.Valid -> loadPersonWithToken(lease.accessToken, id, allowRecovery = false)
                    else -> lease.toPersonResult()
                }
            } else CustomerPersonResult.AuthenticationRequired
            is ApiResult.Failure -> CustomerPersonResult.Unavailable(result.message, result.supportReference)
        }

    private fun TokenLease.toPeopleResult(): CustomerPeopleResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> CustomerPeopleResult.AuthenticationRequired
        TokenLease.Unavailable -> CustomerPeopleResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to load data.")
    }

    private fun TokenLease.toPersonResult(): CustomerPersonResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> CustomerPersonResult.AuthenticationRequired
        TokenLease.Unavailable -> CustomerPersonResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to load data.")
    }

    private suspend fun loadAccountWithToken(token: String, allowRecovery: Boolean): AccountResult =
        when (val result = gateway.account(token)) {
            is ApiResult.Success -> AccountResult.Success(result.value)
            is ApiResult.Unauthorized -> if (allowRecovery) {
                when (val lease = sessions.recoverFromUnauthorized(token)) {
                    is TokenLease.Valid -> loadAccountWithToken(lease.accessToken, allowRecovery = false)
                    else -> lease.toAccountResult()
                }
            } else AccountResult.AuthenticationRequired
            is ApiResult.Failure -> AccountResult.Unavailable(result.message, result.supportReference)
        }

    private suspend fun createWrittenMemoryWithToken(
        token: String,
        title: String,
        content: String,
        idempotencyKey: String,
        allowRecovery: Boolean,
    ): WrittenMemoryResult = when (
        val result = gateway.createWrittenMemory(token, title, content, idempotencyKey)
    ) {
        is ApiResult.Success -> WrittenMemoryResult.Success(result.value.id, result.value.replayed)
        is ApiResult.Unauthorized -> if (allowRecovery) {
            when (val lease = sessions.recoverFromUnauthorized(token)) {
                is TokenLease.Valid -> createWrittenMemoryWithToken(
                    lease.accessToken, title, content, idempotencyKey, allowRecovery = false,
                )
                else -> lease.toWrittenMemoryResult()
            }
        } else WrittenMemoryResult.AuthenticationRequired
        is ApiResult.Failure -> WrittenMemoryResult.Unavailable(result.message, result.supportReference)
    }

    private fun TokenLease.toAccountResult(): AccountResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> AccountResult.AuthenticationRequired
        TokenLease.Unavailable -> AccountResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to load data.")
    }

    private fun TokenLease.toWrittenMemoryResult(): WrittenMemoryResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> WrittenMemoryResult.AuthenticationRequired
        TokenLease.Unavailable -> WrittenMemoryResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to preserve data.")
    }

    private suspend fun loadMemoryWithToken(
        token: String,
        id: String,
        allowRecovery: Boolean,
    ): CustomerMemoryResult = when (val result = gateway.memory(token, id)) {
        is ApiResult.Success -> CustomerMemoryResult.Success(result.value.memory)
        is ApiResult.Unauthorized -> if (allowRecovery) {
            when (val lease = sessions.recoverFromUnauthorized(token)) {
                is TokenLease.Valid -> loadMemoryWithToken(lease.accessToken, id, allowRecovery = false)
                else -> lease.toMemoryResult()
            }
        } else CustomerMemoryResult.AuthenticationRequired
        is ApiResult.Failure -> CustomerMemoryResult.Unavailable(result.message, result.supportReference)
    }

    private suspend fun updateMemoryWithToken(
        token: String,
        id: String,
        visibility: String?,
        pinned: Boolean?,
        status: String?,
        allowRecovery: Boolean,
    ): CustomerMemoryResult = when (
        val result = gateway.updateMemory(token, id, visibility, pinned, status)
    ) {
        is ApiResult.Success -> CustomerMemoryResult.Success(result.value.memory)
        is ApiResult.Unauthorized -> if (allowRecovery) {
            when (val lease = sessions.recoverFromUnauthorized(token)) {
                is TokenLease.Valid -> updateMemoryWithToken(
                    lease.accessToken, id, visibility, pinned, status, allowRecovery = false,
                )
                else -> lease.toMemoryResult()
            }
        } else CustomerMemoryResult.AuthenticationRequired
        is ApiResult.Failure -> CustomerMemoryResult.Unavailable(result.message, result.supportReference)
    }

    private fun TokenLease.toMemoryResult(): CustomerMemoryResult = when (this) {
        TokenLease.Locked, TokenLease.SignedOut -> CustomerMemoryResult.AuthenticationRequired
        TokenLease.Unavailable -> CustomerMemoryResult.Unavailable("Narratrace could not verify your session.")
        is TokenLease.Valid -> error("A valid lease must be used to load Memory data.")
    }

    private suspend fun <T> genericCall(block: suspend (String) -> ApiResult<T>): FeatureResult<T> {
        val lease = sessions.accessToken(); if (lease !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = block(lease.accessToken)
        if (result is ApiResult.Unauthorized) { val recovered = sessions.recoverFromUnauthorized(lease.accessToken); if (recovered !is TokenLease.Valid) return FeatureResult.AuthenticationRequired; result = block(recovered.accessToken) }
        return when (result) { is ApiResult.Success -> FeatureResult.Success(result.value); is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired; is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference) }
    }
}
