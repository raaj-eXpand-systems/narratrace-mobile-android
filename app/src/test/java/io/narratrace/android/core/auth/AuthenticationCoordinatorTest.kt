package io.narratrace.android.core.auth

import io.narratrace.android.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationCoordinatorTest {
    @Test
    fun `successful sign in binds one nonce and adopts verified account`() = runTest {
        val gateway = FakeAuthenticationGateway()
        var requestedNonce: String? = null
        var adoptedAccount: String? = null
        val coordinator = coordinator(
            gateway = gateway,
            requester = IdentityTokenRequester { nonce ->
                requestedNonce = nonce
                GoogleIdentityResult.Success("google-token")
            },
            adopter = SessionAdopter { _, accountId -> adoptedAccount = accountId; true },
        )

        assertEquals(SignInResult.Authenticated, coordinator.signIn(" nrtx-test-code ", " 123456 "))
        assertEquals("server-nonce", requestedNonce)
        assertEquals("server-nonce", gateway.admittedNonce)
        assertEquals("123456", gateway.admittedMfaCode)
        assertEquals("NRTX-TEST-CODE", gateway.admittedInviteCode)
        assertEquals("account-123", adoptedAccount)
    }

    @Test
    fun `cancelled identity sheet never attempts admission`() = runTest {
        val gateway = FakeAuthenticationGateway()
        val coordinator = coordinator(
            gateway = gateway,
            requester = IdentityTokenRequester { GoogleIdentityResult.Cancelled },
        )

        assertEquals(SignInResult.Cancelled, coordinator.signIn("NRTX-TEST-CODE", null))
        assertEquals(0, gateway.admissionCount)
    }

    @Test
    fun `missing durable installation identity fails before network access`() = runTest {
        val gateway = FakeAuthenticationGateway()
        val coordinator = AuthenticationCoordinator(
            gateway = gateway,
            identityTokenRequester = IdentityTokenRequester { GoogleIdentityResult.Success("unused") },
            installationIdProvider = InstallationIdProvider { null },
            sessionAdopter = SessionAdopter { _, _ -> true },
            appVersion = "0.1.0",
            osVersion = "18",
        )

        val result = coordinator.signIn("NRTX-TEST-CODE", null)
        assertTrue(result is SignInResult.Failed)
        assertEquals(0, gateway.challengeCount)
    }

    @Test
    fun `missing invitation fails before provider or network access`() = runTest {
        val gateway = FakeAuthenticationGateway()
        val coordinator = coordinator(
            gateway = gateway,
            requester = IdentityTokenRequester { GoogleIdentityResult.Success("unused") },
        )

        val result = coordinator.signIn("   ", null)

        assertTrue(result is SignInResult.Failed)
        assertEquals(0, gateway.challengeCount)
        assertEquals(0, gateway.admissionCount)
    }

    @Test
    fun `internal role enrollment denial remains a distinct blocked state`() = runTest {
        val gateway = FakeAuthenticationGateway().apply {
            admissionFailure = ApiResult.Unauthorized(
                message = "Authenticator setup is required.",
                supportReference = "support-123",
                fieldName = "mfaEnrollment",
            )
        }
        val coordinator = coordinator(
            gateway = gateway,
            requester = IdentityTokenRequester { GoogleIdentityResult.Success("google-token") },
        )

        val result = coordinator.signIn("NRTX-TEST-CODE", null)

        assertTrue(result is SignInResult.MfaEnrollmentRequired)
        assertEquals(0, gateway.meCount)
    }

    private fun coordinator(
        gateway: FakeAuthenticationGateway,
        requester: IdentityTokenRequester,
        adopter: SessionAdopter = SessionAdopter { _, _ -> true },
    ) = AuthenticationCoordinator(
        gateway = gateway,
        identityTokenRequester = requester,
        installationIdProvider = InstallationIdProvider { "123e4567-e89b-42d3-a456-426614174000" },
        sessionAdopter = adopter,
        appVersion = "0.1.0",
        osVersion = "18",
    )
}

private class FakeAuthenticationGateway : AuthenticationGateway {
    var challengeCount = 0
    var admissionCount = 0
    var admittedNonce: String? = null
    var admittedMfaCode: String? = null
    var admittedInviteCode: String? = null
    var meCount = 0
    var admissionResult = TokenPair("access", "refresh", "2026-08-11T20:00:00Z")
    var admissionFailure: ApiResult.Failure? = null

    override suspend fun challenge(): ApiResult<AuthChallenge> {
        challengeCount++
        return ApiResult.Success(AuthChallenge("server-nonce", "2026-08-11T20:00:00Z"), "support")
    }

    override suspend fun admit(
        idToken: String,
        nonce: String,
        installationId: String,
        appVersion: String,
        inviteCode: String,
        mfaCode: String?,
        osVersion: String?,
    ): ApiResult<TokenPair> {
        admissionCount++
        admittedNonce = nonce
        admittedMfaCode = mfaCode
        admittedInviteCode = inviteCode
        return admissionFailure ?: ApiResult.Success(admissionResult, "support")
    }

    override suspend fun me(accessToken: String): ApiResult<AuthenticatedAccount> {
        meCount++
        return ApiResult.Success(AuthenticatedAccount("account-123", "subject", "member@example.com", "mobile_access_token"), "support")
    }
}
