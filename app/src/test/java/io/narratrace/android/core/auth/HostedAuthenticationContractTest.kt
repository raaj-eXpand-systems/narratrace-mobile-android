package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostedAuthenticationContractTest {
    @Test fun `start creates S256 PKCE and stores only encrypted pending protocol state`() = runTest {
        val gateway = FakeHostedGateway()
        val blob = MemoryBlobStore()
        val coordinator = coordinator(gateway, blob)
        val result = coordinator.start()
        assertTrue(result is HostedAuthStartResult.OpenBrowser)
        assertEquals("android", gateway.startRequest?.platform)
        assertTrue(gateway.startRequest?.codeChallenge?.matches(Regex("^[A-Za-z0-9_-]{43}$")) == true)
        val stored = blob.bytes?.decodeToString().orEmpty()
        assertFalse(stored.contains(gateway.startRequest!!.codeChallenge))
        assertFalse(stored.contains("member@example.com"))
    }

    @Test fun `verified callback exchanges exact state installation and verifier then bootstraps`() = runTest {
        val gateway = FakeHostedGateway()
        var adoptedAccount = ""
        val coordinator = coordinator(gateway, adopter = SessionAdopter { _, account -> adoptedAccount = account; true })
        coordinator.start()
        coordinator.handleCallback("$HOSTED_AUTH_CALLBACK?code=${"c".repeat(43)}&state=${gateway.transactionId}")
        assertEquals(gateway.transactionId, gateway.exchangeRequest?.transactionId)
        assertEquals("123e4567-e89b-42d3-a456-426614174000", gateway.exchangeRequest?.installationId)
        assertEquals("android", gateway.exchangeRequest?.platform)
        assertTrue(gateway.exchangeRequest?.codeVerifier?.length == 43)
        assertEquals("account-123", adoptedAccount)
        assertEquals(1, gateway.bootstrapCount)
        assertTrue(coordinator.event.value is HostedAuthEvent.Authenticated)
    }

    @Test fun `mismatched state and unverified deep links fail closed before exchange`() = runTest {
        val gateway = FakeHostedGateway()
        val coordinator = coordinator(gateway)
        coordinator.start()
        coordinator.handleCallback("$HOSTED_AUTH_CALLBACK?code=${"c".repeat(43)}&state=223e4567-e89b-42d3-a456-426614174000")
        coordinator.handleCallback("https://attacker.invalid/mobile/auth/callback/android?code=${"c".repeat(43)}&state=${gateway.transactionId}")
        assertEquals(0, gateway.exchangeCount)
        assertTrue(coordinator.event.value is HostedAuthEvent.Failed)
    }

    @Test fun `callback rejects fragments duplicates extras tokens and the wrong platform path`() {
        val good = "$HOSTED_AUTH_CALLBACK?code=${"c".repeat(43)}&state=123e4567-e89b-42d3-a456-426614174000"
        assertNotNull(parseHostedAuthCallback(good))
        assertNull(parseHostedAuthCallback("$good#access_token=secret"))
        assertNull(parseHostedAuthCallback("$good&email=member%40example.com"))
        assertNull(parseHostedAuthCallback("$good&code=${"d".repeat(43)}"))
        assertNull(parseHostedAuthCallback(good.replace("/android", "/ios")))
        assertNull(parseHostedAuthCallback(good.replace("https://", "http://")))
    }

    @Test fun `all upgrades stay on the server-authored Narratrace web destination`() {
        val action = bootstrap().clientContract.hostedActions.upgrades
        for (purpose in listOf("plan_upgrade", "plan_change", "add_on", "billing_recovery")) {
            assertEquals("https://www.narratrace.io/account#subscription", action.destinationFor(purpose))
        }
        assertNull(action.destinationFor("purchase_in_app"))
        assertNull(action.copy(destination = "https://attacker.invalid/pay").destinationFor("plan_upgrade"))
        assertNull(action.copy(returnsToApp = true).destinationFor("plan_upgrade"))
    }

    @Test fun `manifest exposes only the verified HTTPS Android auth callback`() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:autoVerify=\"true\""))
        assertTrue(manifest.contains("android:path=\"/mobile/auth/callback/android\""))
        assertFalse(manifest.contains("android:scheme=\"io.narratrace"))
    }

    private fun coordinator(
        gateway: FakeHostedGateway,
        blob: MemoryBlobStore = MemoryBlobStore(),
        adopter: SessionAdopter = SessionAdopter { _, _ -> true },
    ) = HostedAuthenticationCoordinator(
        gateway = gateway,
        installationIdProvider = InstallationIdProvider { "123e4567-e89b-42d3-a456-426614174000" },
        pendingStore = PendingHostedAuthStore(ReverseCipher, blob),
        sessionAdopter = adopter,
        appVersion = "1.0.0",
        osVersion = "16",
    )
}

private class FakeHostedGateway : HostedAuthenticationGateway {
    val transactionId = "123e4567-e89b-42d3-a456-426614174000"
    var startRequest: HostedAuthStartRequest? = null
    var exchangeRequest: HostedAuthExchangeRequest? = null
    var exchangeCount = 0
    var bootstrapCount = 0
    override suspend fun start(request: HostedAuthStartRequest): ApiResult<HostedAuthStartResponse> {
        startRequest = request
        return ApiResult.Success(HostedAuthStartResponse(1, transactionId, "https://www.narratrace.io/auth/hosted/${"a".repeat(43)}", "2099-08-28T20:00:00.000Z"), "support-start")
    }
    override suspend fun exchange(request: HostedAuthExchangeRequest): ApiResult<TokenPair> {
        exchangeCount++; exchangeRequest = request
        return ApiResult.Success(TokenPair("access", "refresh", "2099-08-28T20:00:00.000Z"), "support-exchange")
    }
    override suspend fun me(accessToken: String) = ApiResult.Success(AuthenticatedAccount("account-123", "subject", "member@example.com", "mobile_access_token"), "support-me")
    override suspend fun bootstrap(accessToken: String): ApiResult<AccountBootstrap> {
        bootstrapCount++; return ApiResult.Success(bootstrap(), "support-bootstrap")
    }
}

private fun bootstrap() = AccountBootstrap(
    "2026-08-28T20:00:00.000Z", BootstrapAccess(true, false), null, null, BootstrapProfile(null),
    BootstrapClientContract(1, HostedActions(HostedUpgradeAction(
        "hosted_web", "https://www.narratrace.io/account#subscription",
        listOf("plan_upgrade", "plan_change", "add_on", "billing_recovery"), false,
    ))),
)

private object ReverseCipher : CredentialCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext.reversedArray()
    override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext.reversedArray()
}
private class MemoryBlobStore : EncryptedBlobStore {
    var bytes: ByteArray? = null
    override fun read(): ByteArray? = bytes
    override fun write(bytes: ByteArray): Boolean { this.bytes = bytes; return true }
    override fun clear(): Boolean { bytes = null; return true }
}
