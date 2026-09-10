package io.narratrace.android.app

import java.io.File
import io.narratrace.android.core.customer.AccountCapabilities
import io.narratrace.android.core.customer.AccountSummary
import io.narratrace.android.core.customer.ProductionAllowance
import io.narratrace.android.core.customer.ProductionArchive
import io.narratrace.android.core.customer.ProductionPools
import io.narratrace.android.core.customer.StorageSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionLanguageContractTest {
    @Test fun `library illustration requires verified photo absence`() {
        val empty = io.narratrace.android.core.media.MediaList(emptyList())
        assertFalse(shouldShowLibraryIllustration(null))
        assertFalse(shouldShowLibraryIllustration(io.narratrace.android.core.media.FeatureResult.AuthenticationRequired))
        assertFalse(shouldShowLibraryIllustration(io.narratrace.android.core.media.FeatureResult.Unavailable("Unavailable", "")))
        assertTrue(shouldShowLibraryIllustration(io.narratrace.android.core.media.FeatureResult.Success(empty)))
        val photo = io.narratrace.android.core.media.MediaSummary("photo", "photo", "My photo", "preserved", createdAt = "2026-09-09")
        assertFalse(shouldShowLibraryIllustration(io.narratrace.android.core.media.FeatureResult.Success(empty.copy(media = listOf(photo)))))
        assertTrue(shouldShowLibraryIllustration(io.narratrace.android.core.media.FeatureResult.Success(empty.copy(media = listOf(photo.copy(kind = "audio"))))))
        assertFalse(shouldShowLibraryIllustration(io.narratrace.android.core.media.FeatureResult.Success(empty.copy(media = List(1000) { photo.copy(kind = "audio") }))))
    }

    @Test
    fun `account allowance summary preserves per storyteller limits and counts shared capacity once`() {
        val account = productionAccount(listOf(
            productionArchive("maya", "Maya", photos = 0, audio = 60, video = 0),
            productionArchive("alex", "Alex", photos = 20, audio = 0, video = 0),
        ), ProductionPools(audioSeconds = allowance(3600)))
        val labels = accountAllowanceLabels(account)
        assertTrue(labels.contains("Maya" to "Photos: 0 of 0 remaining"))
        assertTrue(labels.contains("Alex" to "Photos: 20 of 20 remaining"))
        assertEquals(1, labels.count { it.first == "Shared allowance" })
        assertTrue(labels.contains("Letters" to "Available with your plan"))
        assertFalse(labels.any { it.second.contains(" B") || it.second.contains("GB") })
        val restricted = accountAllowanceLabels(account.copy(capabilities = account.capabilities.copy(captureVideo = false, createLetters = false)))
        assertFalse(restricted.any { it.second.startsWith("Video:") })
        assertTrue(restricted.contains("Video" to "Not included in your current access"))
        assertTrue(restricted.contains("Letters" to "Not included in your current access"))
    }

    @Test
    fun `customer visible android surfaces do not use beta positioning`() {
        val customerSurfaceFiles = sequenceOf(
            File("src/main/java"),
            File("src/main/res"),
        ).flatMap { root ->
            root.walkTopDown().filter { file ->
                file.isFile && file.extension in setOf("kt", "xml")
            }
        }

        val betaReference = Regex("\\bbeta\\b", RegexOption.IGNORE_CASE)
        val offendingFiles = customerSurfaceFiles
            .filter { file -> betaReference.containsMatchIn(file.readText()) }
            .map { file -> file.relativeTo(File(".")).path }
            .toList()

        assertFalse(
            "Customer-visible Android Kotlin/XML must use production-service language; found beta positioning in: $offendingFiles",
            offendingFiles.isNotEmpty(),
        )
    }

    @Test
    fun `storyteller quota states are visible and accessible on capture surfaces`() {
        val source = File("src/main/java/io/narratrace/android/app/NarratraceApp.kt").readText()

        assertTrue(source.contains("Storyteller allowance"))
        assertTrue(source.contains("Choose a storyteller"))
        assertTrue(source.contains("Video is not included in this plan."))
        assertTrue(source.contains("remaining"))
        assertTrue(source.contains("heightIn(min = 48.dp)"))
        assertTrue(source.contains("creationMessage = made.message"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Assertive"))
    }

    @Test
    fun `capture availability requires a storyteller and honors server remaining values`() {
        val maya = productionArchive("11111111-2222-4333-8444-555555555555", "Maya", photos = 0, audio = 60, video = 0)
        val alex = productionArchive("21111111-2222-4333-8444-555555555555", "Alex", photos = 20, audio = 0, video = 0)
        val account = productionAccount(listOf(maya, alex), ProductionPools(videoSeconds = allowance(300)))

        val unselected = productionCaptureAvailability(account, null)
        assertTrue(unselected.targetRequired)
        assertFalse(unselected.photoEnabled)
        assertFalse(unselected.audioEnabled)
        assertFalse(unselected.videoEnabled)

        val mayaSelected = productionCaptureAvailability(account, maya.id)
        assertTrue(mayaSelected.audioEnabled)
        assertFalse(mayaSelected.photoEnabled)
        assertTrue(mayaSelected.videoEnabled)
    }
}

private fun allowance(remaining: Long) = ProductionAllowance(remaining, 0, remaining)

private fun productionArchive(id: String, name: String, photos: Long, audio: Long, video: Long) = ProductionArchive(
    id = id,
    entitlementId = "31111111-2222-4333-8444-555555555555",
    subjectName = name,
    productFamily = "family",
    productTier = "complete",
    audioSeconds = allowance(audio),
    videoSeconds = allowance(video),
    photographs = allowance(photos),
)

private fun productionAccount(archives: List<ProductionArchive>, pools: ProductionPools) = AccountSummary(
    status = "subscription_active",
    plan = "family",
    hasAccess = true,
    canReadArchive = true,
    storage = StorageSummary(0, 1, 1, "0 B", "1 B", "1 B", 0),
    productionArchives = archives,
    productionPools = pools,
    capabilities = AccountCapabilities(
        captureMemories = true,
        captureVideo = true,
        createLetters = true,
        managePeople = true,
        familyCircles = true,
    ),
)
