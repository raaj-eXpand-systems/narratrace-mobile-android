package io.narratrace.android.core.customer

import io.narratrace.android.core.auth.*
import io.narratrace.android.core.network.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class AccountCaptureContinuityTest {
    private val account = NarratraceJson.decodeFromString<AccountSummary>("""{"status":"subscription_active","productFamily":"a_life","productTier":"essential","hasAccess":true,"canReadArchive":true,"storage":{"usedBytes":0,"availableBytes":100,"totalBytes":100,"usedLabel":"0","availableLabel":"100","totalLabel":"100","usedPercent":0},"capabilities":{"captureMemories":true,"createLetters":true,"managePeople":true,"familyCircles":false}}""")

    @Test fun `offline capture reuses only verified same-session paid eligibility and rejects downgrade`() = runTest {
        var bytes: ByteArray? = null
        val store = SessionStore(object : CredentialCipher {
            override fun encrypt(plaintext: ByteArray) = plaintext
            override fun decrypt(ciphertext: ByteArray) = ciphertext
        }, object : EncryptedBlobStore {
            override fun read() = bytes
            override fun write(value: ByteArray): Boolean { bytes = value; return true }
            override fun clear(): Boolean { bytes = null; return true }
        })
        store.save(MobileSession("access", "refresh", Long.MAX_VALUE, "account", 0))
        val sessions = SessionManager(store, SessionRefresher { ApiResult.Offline() })
        sessions.restore()
        var response: ApiResult<AccountSummary> = ApiResult.Offline()
        val gateway = Proxy.newProxyInstance(CustomerGateway::class.java.classLoader, arrayOf(CustomerGateway::class.java)) { _, method, _ ->
            check(method.name == "account"); response
        } as CustomerGateway
        val repository = CustomerRepository(gateway, sessions)
        assertTrue(repository.accountForLocalCapture() is AccountResult.Unavailable)
        response = ApiResult.Success(account, "")
        assertTrue(repository.loadAccount() is AccountResult.Success)
        response = ApiResult.Offline()
        assertTrue(repository.accountForLocalCapture() is AccountResult.Success)
        response = ApiResult.Success(account.copy(hasAccess = false, status = "trial_active", productFamily = null, productTier = null), "")
        repository.loadAccount()
        response = ApiResult.Offline()
        assertTrue(repository.accountForLocalCapture() is AccountResult.Unavailable)
        sessions.signOut()
        assertNull(repository.lastVerifiedAccount())
        assertEquals(AccountResult.AuthenticationRequired, repository.accountForLocalCapture())
    }
}
