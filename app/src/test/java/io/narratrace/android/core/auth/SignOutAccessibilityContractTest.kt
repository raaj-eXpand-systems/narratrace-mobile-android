package io.narratrace.android.core.auth

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SignOutAccessibilityContractTest {
    @Test
    fun `secure revocation exposes immediate progress and failure announcements`() {
        val source = File("src/main/java/io/narratrace/android/app/NarratraceApp.kt").readText()

        assertTrue(source.contains("Signing out securely. Please wait."))
        assertTrue(source.contains("if (revoking) \"Signing out…\""))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Assertive"))
        assertTrue(source.contains("enabled = !revoking"))
    }
}
