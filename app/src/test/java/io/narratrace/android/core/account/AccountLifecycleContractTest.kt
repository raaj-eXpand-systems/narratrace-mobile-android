package io.narratrace.android.core.account

import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLifecycleContractTest {
    @Test fun `terminal lifecycle disposition purges only terminal account states`() {
        val deleting = signal("deletion_in_progress", "purge_account_data")
        val deleted = signal("deleted", "purge_account_data")
        val closure = signal("closure_pending", "retain_encrypted")
        assertTrue(deleting.requiresLocalPurge())
        assertTrue(deleted.requiresLocalPurge())
        assertFalse(closure.requiresLocalPurge())
    }

    @Test fun `valid 200 ordinary lifecycle states allow product access`() {
        assertTrue(signal("active", "retain_encrypted").allowsOrdinaryAccess())
        assertTrue(signal("lapsed", "retain_encrypted").allowsOrdinaryAccess())
        assertTrue(signal("dormant", "retain_encrypted").allowsOrdinaryAccess())
    }

    @Test fun `rights restricted states never become ordinary access`() {
        for (state in listOf("closure_pending", "suspended", "company_terminated", "legal_hold", "deletion_in_progress", "deleted")) {
            assertFalse(signal(state, if (state.startsWith("deletion") || state == "deleted") "purge_account_data" else "retain_encrypted").allowsOrdinaryAccess())
        }
    }

    @Test fun `current lifecycle response decodes additive server fields`() {
        val value = NarratraceJson.decodeFromString<AccountLifecycleSignal>("""{
          "state":"closure_pending","effectiveAt":"2026-08-26T00:00:00.000Z",
          "recoveryEndsAt":"2026-09-25T00:00:00.000Z","purgeEligibleAt":"2026-09-25T00:00:00.000Z",
          "reasonCode":"voluntary_closure","appealStatus":"not_applicable",
          "localDataDisposition":"retain_encrypted","installationBound":true,"future":"safe"
        }""")
        assertFalse(value.requiresLocalPurge())
        assertFalse(value.allowsOrdinaryAccess())
        assertEquals("voluntary_closure", value.reasonCode)
        assertTrue(value.installationBound)
    }

    @Test fun `mobile account closure status decodes recovery refund and privacy-safe reference`() {
        val value = NarratraceJson.decodeFromString<AccountClosureStatus>("""{
          "accountClosed":true,"closedAt":"2026-08-27T12:00:00.000Z",
          "graceEndsAt":"2026-09-26T12:00:00.000Z","daysLeft":30,"expired":false,
          "supportRef":"opaque-support-reference","purgeScheduledAt":null,"closureFinalizedAt":null,
          "refundAmount":1250,"currency":"usd","requiresRecentAuthentication":true,"future":"safe"
        }""")

        assertTrue(value.accountClosed)
        assertEquals(30, value.daysLeft)
        assertEquals(1250, value.refundAmount)
        assertTrue(value.requiresRecentAuthentication)
        assertEquals("opaque-support-reference", value.supportRef)
    }

    @Test fun `closure mutations serialize only the allowlisted action`() {
        assertEquals("{\"action\":\"close\"}", NarratraceJson.encodeToString(AccountClosureAction("close")))
        assertEquals("{\"action\":\"reopen\"}", NarratraceJson.encodeToString(AccountClosureAction("reopen")))

        val reopened = NarratraceJson.decodeFromString<AccountClosureMutation>("""{
          "ok":true,"accountClosed":false,"restoredStatus":"vault","requiresSignIn":true
        }""")
        assertTrue(reopened.ok)
        assertFalse(reopened.accountClosed)
        assertTrue(reopened.requiresSignIn)
    }

    @Test fun `suspension appeal remains reachable without ordinary access`() {
        val value = NarratraceJson.decodeFromString<AccountLifecycleSignal>("""{
          "state":"suspended","effectiveAt":"2026-08-26T00:00:00.000Z",
          "appealStatus":"available","appealUrl":"https://www.narratrace.io/account/appeal",
          "localDataDisposition":"retain_encrypted","installationBound":true
        }""")
        assertFalse(value.allowsOrdinaryAccess())
        assertEquals("https://www.narratrace.io/account/appeal", value.safeAppealUrl())
    }

    @Test fun `appeal URL is absent or rejected outside its narrow rights preserving contract`() {
        assertNull(signal("suspended", "retain_encrypted").safeAppealUrl())
        assertNull(signal("active", "retain_encrypted").copy(
            appealStatus = "available", appealUrl = "https://www.narratrace.io/account/appeal",
        ).safeAppealUrl())
        assertNull(signal("company_terminated", "retain_encrypted").copy(
            appealStatus = "submitted", appealUrl = "https://attacker.example/account/appeal",
        ).safeAppealUrl())
    }

    private fun signal(state: String, disposition: String) = AccountLifecycleSignal(
        state = state, effectiveAt = "now", appealStatus = "not_applicable",
        localDataDisposition = disposition, installationBound = true,
    )
}
