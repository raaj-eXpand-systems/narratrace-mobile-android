package io.narratrace.android.app

import androidx.lifecycle.Lifecycle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountManagementLifecycleTest {
    @Test
    fun `returning from secure account management refreshes server owned account state`() {
        assertTrue(shouldRefreshAccountAfterExternalManagement(true, Lifecycle.Event.ON_RESUME))
    }

    @Test
    fun `ordinary lifecycle changes do not cause an account refresh`() {
        assertFalse(shouldRefreshAccountAfterExternalManagement(false, Lifecycle.Event.ON_RESUME))
        assertFalse(shouldRefreshAccountAfterExternalManagement(true, Lifecycle.Event.ON_PAUSE))
        assertFalse(shouldRefreshAccountAfterExternalManagement(true, Lifecycle.Event.ON_STOP))
    }

    @Test
    fun `account closure link is direct and purchase calls to action are absent`() {
        val source = File("src/main/java/io/narratrace/android/app/NarratraceApp.kt").readText()

        assertEquals("https://www.narratrace.io/account#closure", ACCOUNT_CLOSURE_URL)
        assertFalse(source.contains("/subscribe"))
        assertFalse(source.contains("Review Narratrace plans"))
        assertFalse(source.contains("purchase a plan"))
    }

    @Test
    fun `default release version is one point zero`() {
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("ifBlank { \"1.0.0\" }"))
    }

    @Test
    fun `refund amount is rendered from minor currency units`() {
        assertEquals("USD 12.50", formatMinorCurrency(1250, "usd"))
        assertEquals("USD 0.00", formatMinorCurrency(-1, "usd"))
    }

    @Test
    fun `native closure uses status close and reopen while closed archive access stays blocked`() {
        val source = File("src/main/java/io/narratrace/android/app/NarratraceApp.kt").readText()

        assertTrue(source.contains("accountLifecycleApi.closureStatus"))
        assertTrue(source.contains("accountLifecycleApi.close(token)"))
        assertTrue(source.contains("accountLifecycleApi.reopen(accessCredential)"))
        assertTrue(source.contains("if (signal.state != \"closure_pending\")"))
        assertTrue(source.contains("reopened.value.requiresSignIn"))
        assertTrue(source.contains("container.sessionManager.signOut()"))
    }
}
