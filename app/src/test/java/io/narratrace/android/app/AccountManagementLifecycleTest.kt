package io.narratrace.android.app

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountManagementLifecycleTest {
    @Test
    fun `returning from secure account management refreshes server owned billing state`() {
        assertTrue(shouldRefreshAccountAfterExternalBilling(true, Lifecycle.Event.ON_RESUME))
    }

    @Test
    fun `ordinary lifecycle changes do not cause a billing refresh`() {
        assertFalse(shouldRefreshAccountAfterExternalBilling(false, Lifecycle.Event.ON_RESUME))
        assertFalse(shouldRefreshAccountAfterExternalBilling(true, Lifecycle.Event.ON_PAUSE))
        assertFalse(shouldRefreshAccountAfterExternalBilling(true, Lifecycle.Event.ON_STOP))
    }
}
