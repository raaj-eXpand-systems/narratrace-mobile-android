package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

internal const val HOSTED_AUTH_PROTOCOL_VERSION = 1
internal const val HOSTED_AUTH_CALLBACK = "https://www.narratrace.io/mobile/auth/callback/android"
private const val HOSTED_AUTH_ORIGIN = "www.narratrace.io"
private val OPAQUE_TOKEN = Regex("^[A-Za-z0-9_-]{43}$")
private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

@Serializable
internal data class HostedAuthStartRequest(
    val platform: String = "android",
    val installationId: String,
    val appVersion: String,
    val osVersion: String? = null,
    val codeChallenge: String,
)

@Serializable
internal data class HostedAuthStartResponse(
    val protocolVersion: Int,
    val transactionId: String,
    val authorizeUrl: String,
    val expiresAt: String,
)

@Serializable
internal data class HostedAuthExchangeRequest(
    val transactionId: String,
    val code: String,
    val codeVerifier: String,
    val installationId: String,
    val platform: String = "android",
    val appVersion: String,
    val osVersion: String? = null,
)

@Serializable
data class AccountBootstrap(
    val generatedAt: String,
    val access: BootstrapAccess,
    val subscription: BootstrapSubscription? = null,
    val legal: BootstrapLegal? = null,
    val profile: BootstrapProfile,
    val clientContract: BootstrapClientContract,
)

@Serializable data class BootstrapAccess(val verified: Boolean, val accountClosed: Boolean)
@Serializable data class BootstrapProfile(val birthYear: Int? = null)
@Serializable data class BootstrapExperiment(
    val cardGateArm: String,
    val experienceFirst: Boolean,
    val resourceState: String? = null,
)
@Serializable data class BootstrapSubscription(
    val status: String,
    val hasAccess: Boolean,
    val canReadArchive: Boolean,
    val currentPeriodEndsAt: String? = null,
    val plan: String? = null,
    val billingCycle: String? = null,
    val preferredLanguage: String,
    val productFamily: String? = null,
    val productTier: String? = null,
    val isFamily: Boolean,
    val experiment: BootstrapExperiment,
)
@Serializable data class BootstrapLegal(
    val termsAccepted: Boolean,
    val privacyAcknowledged: Boolean,
    val aiNoticeAcknowledged: Boolean,
    val specialCategoryConsent: Boolean,
    val contentRightsAttested: Boolean,
    val termsVersion: String,
    val privacyVersion: String,
    val aiNoticeVersion: String,
    val contentRightsVersion: String,
    val acceptedAt: String? = null,
)
@Serializable data class BootstrapClientContract(val version: Int, val hostedActions: HostedActions)
@Serializable data class HostedActions(val upgrades: HostedUpgradeAction)
@Serializable data class HostedUpgradeAction(
    val mode: String,
    val destination: String,
    val purposes: List<String>,
    val returnsToApp: Boolean,
) {
    /** All purchase and billing authority stays on the server-authored web page. */
    fun destinationFor(purpose: String): String? {
        if (mode != "hosted_web" || returnsToApp || purpose !in purposes) return null
        val uri = runCatching { URI(destination) }.getOrNull() ?: return null
        return destination.takeIf {
            uri.scheme == "https" && uri.host == HOSTED_AUTH_ORIGIN && uri.userInfo == null &&
                uri.port == -1 && uri.fragment?.length.orZero() <= 80
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0

internal interface HostedAuthenticationGateway {
    suspend fun start(request: HostedAuthStartRequest): ApiResult<HostedAuthStartResponse>
    suspend fun exchange(request: HostedAuthExchangeRequest): ApiResult<TokenPair>
    suspend fun me(accessToken: String): ApiResult<AuthenticatedAccount>
    suspend fun bootstrap(accessToken: String): ApiResult<AccountBootstrap>
}

internal class HostedAuthApi(private val client: NarratraceApiClient) : HostedAuthenticationGateway {
    override suspend fun start(request: HostedAuthStartRequest): ApiResult<HostedAuthStartResponse> =
        client.post(
            "/api/v1/auth/hosted/start",
            NarratraceJson.encodeToString(request),
            serializer<HostedAuthStartResponse>(),
        )

    override suspend fun exchange(request: HostedAuthExchangeRequest): ApiResult<TokenPair> =
        client.post(
            "/api/v1/auth/hosted/exchange",
            NarratraceJson.encodeToString(request),
            serializer<TokenPair>(),
        )

    override suspend fun me(accessToken: String): ApiResult<AuthenticatedAccount> =
        client.get("/api/v1/me", serializer<AuthenticatedAccount>(), accessToken)

    override suspend fun bootstrap(accessToken: String): ApiResult<AccountBootstrap> =
        client.get("/api/v1/bootstrap", serializer<AccountBootstrap>(), accessToken)
}

@Serializable
internal data class PendingHostedAuthentication(
    val transactionId: String,
    val codeVerifier: String,
    val installationId: String,
    val expiresAt: String,
)

internal class PendingHostedAuthStore(
    private val cipher: CredentialCipher,
    private val blobStore: EncryptedBlobStore,
) {
    fun save(value: PendingHostedAuthentication): Boolean {
        val plaintext = runCatching { NarratraceJson.encodeToString(value).encodeToByteArray() }.getOrNull() ?: return false
        return cipher.encrypt(plaintext)?.let(blobStore::write) ?: false
    }

    fun load(): PendingHostedAuthentication? {
        val encoded = blobStore.read() ?: return null
        val plaintext = cipher.decrypt(encoded) ?: run { clear(); return null }
        return runCatching { NarratraceJson.decodeFromString<PendingHostedAuthentication>(plaintext.decodeToString()) }
            .getOrElse { clear(); null }
    }

    fun clear(): Boolean = blobStore.clear()
}

internal sealed interface HostedAuthEvent {
    data object Idle : HostedAuthEvent
    data object AwaitingBrowser : HostedAuthEvent
    data object Exchanging : HostedAuthEvent
    data class Authenticated(val bootstrap: AccountBootstrap) : HostedAuthEvent
    data class Failed(val message: String, val supportReference: String = "") : HostedAuthEvent
}

internal sealed interface HostedAuthStartResult {
    data class OpenBrowser(val authorizeUrl: String) : HostedAuthStartResult
    data class Failed(val message: String, val supportReference: String = "") : HostedAuthStartResult
}

internal data class HostedAuthCallback(val code: String, val state: String)

internal fun parseHostedAuthCallback(rawUri: String): HostedAuthCallback? {
    val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host != HOSTED_AUTH_ORIGIN || uri.port != -1 || uri.userInfo != null ||
        uri.path != "/mobile/auth/callback/android" || uri.fragment != null
    ) return null
    val values = mutableMapOf<String, String>()
    val pairs = uri.rawQuery?.split('&') ?: return null
    for (pair in pairs) {
        val parts = pair.split('=', limit = 2)
        if (parts.size != 2 || parts[0] !in setOf("code", "state") || values.put(parts[0], parts[1]) != null) return null
    }
    val code = values["code"] ?: return null
    val state = values["state"] ?: return null
    return HostedAuthCallback(code, state).takeIf { OPAQUE_TOKEN.matches(code) && UUID.matches(state) }
}

internal fun isTrustedHostedAuthorizeUrl(rawUrl: String, transactionId: String): Boolean {
    if (!UUID.matches(transactionId)) return false
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
    val token = uri.path?.removePrefix("/auth/hosted/") ?: return false
    return uri.scheme == "https" && uri.host == HOSTED_AUTH_ORIGIN && uri.port == -1 && uri.userInfo == null &&
        uri.query == null && uri.fragment == null && uri.path == "/auth/hosted/$token" && OPAQUE_TOKEN.matches(token)
}

internal class HostedAuthenticationCoordinator(
    private val gateway: HostedAuthenticationGateway,
    private val installationIdProvider: InstallationIdProvider,
    private val pendingStore: PendingHostedAuthStore,
    private val sessionAdopter: SessionAdopter,
    private val appVersion: String,
    private val osVersion: String,
    private val random: SecureRandom = SecureRandom(),
) {
    private val callbackMutex = Mutex()
    private val _event = MutableStateFlow<HostedAuthEvent>(HostedAuthEvent.Idle)
    val event: StateFlow<HostedAuthEvent> = _event.asStateFlow()

    suspend fun start(): HostedAuthStartResult {
        val installationId = installationIdProvider.installationId()
            ?: return failStart("Narratrace could not prepare secure sign-in on this device.")
        val verifier = ByteArray(32).also(random::nextBytes).base64Url()
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()).base64Url()
        return when (val result = gateway.start(
            HostedAuthStartRequest(installationId = installationId, appVersion = appVersion, osVersion = osVersion, codeChallenge = challenge),
        )) {
            is ApiResult.Success -> {
                val response = result.value
                if (response.protocolVersion != HOSTED_AUTH_PROTOCOL_VERSION ||
                    !isTrustedHostedAuthorizeUrl(response.authorizeUrl, response.transactionId) ||
                    parseIso8601Millis(response.expiresAt) == null ||
                    !pendingStore.save(PendingHostedAuthentication(response.transactionId, verifier, installationId, response.expiresAt))
                ) return failStart("Narratrace could not protect this sign-in attempt on your device.")
                _event.value = HostedAuthEvent.AwaitingBrowser
                HostedAuthStartResult.OpenBrowser(response.authorizeUrl)
            }
            is ApiResult.Failure -> failStart(result.message, result.supportReference)
        }
    }

    suspend fun handleCallback(rawUri: String) = callbackMutex.withLock {
        val callback = parseHostedAuthCallback(rawUri)
            ?: return@withLock fail("Narratrace blocked an unverified sign-in return.")
        val pending = pendingStore.load()
            ?: return@withLock fail("This sign-in attempt is no longer available. Start again.")
        if (callback.state != pending.transactionId) {
            return@withLock fail("Narratrace blocked a sign-in return that did not match this device.")
        }
        if ((parseIso8601Millis(pending.expiresAt) ?: 0) <= System.currentTimeMillis()) {
            pendingStore.clear()
            return@withLock fail("This sign-in attempt expired. Start again.")
        }
        _event.value = HostedAuthEvent.Exchanging
        val exchange = gateway.exchange(
            HostedAuthExchangeRequest(
                transactionId = pending.transactionId,
                code = callback.code,
                codeVerifier = pending.codeVerifier,
                installationId = pending.installationId,
                appVersion = appVersion,
                osVersion = osVersion,
            ),
        )
        if (exchange is ApiResult.Failure) {
            pendingStore.clear()
            return@withLock fail(exchange.message, exchange.supportReference)
        }
        val tokens = (exchange as ApiResult.Success).value
        val account = gateway.me(tokens.accessToken)
        if (account is ApiResult.Failure) {
            pendingStore.clear()
            return@withLock fail(account.message, account.supportReference)
        }
        when (val bootstrap = gateway.bootstrap(tokens.accessToken)) {
            is ApiResult.Success -> {
                if (!bootstrap.value.hasSafeClientContract()) {
                    pendingStore.clear()
                    return@withLock fail("This version of Narratrace cannot safely read the account setup.", bootstrap.supportReference)
                }
                if (!sessionAdopter.adopt(tokens, (account as ApiResult.Success).value.accountId)) {
                    pendingStore.clear()
                    return@withLock fail("Narratrace could not protect the new session on this device.")
                }
                pendingStore.clear()
                _event.value = HostedAuthEvent.Authenticated(bootstrap.value)
            }
            is ApiResult.Failure -> {
                pendingStore.clear()
                fail(bootstrap.message, bootstrap.supportReference)
            }
        }
    }

    fun resetEvent() { _event.value = HostedAuthEvent.Idle }

    private fun AccountBootstrap.hasSafeClientContract(): Boolean =
        clientContract.version == 1 && clientContract.hostedActions.upgrades.run {
            mode == "hosted_web" && !returnsToApp && purposes.containsAll(
                listOf("plan_upgrade", "plan_change", "add_on", "billing_recovery"),
            ) && purposes.all { destinationFor(it) != null }
        }

    private fun failStart(message: String, supportReference: String = ""):
        HostedAuthStartResult.Failed = HostedAuthStartResult.Failed(message, supportReference).also {
            _event.value = HostedAuthEvent.Failed(message, supportReference)
        }

    private fun fail(message: String, supportReference: String = "") {
        _event.value = HostedAuthEvent.Failed(message, supportReference)
    }
}

private fun ByteArray.base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
