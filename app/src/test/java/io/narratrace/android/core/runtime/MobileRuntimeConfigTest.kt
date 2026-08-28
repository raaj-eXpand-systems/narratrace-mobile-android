package io.narratrace.android.core.runtime

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileRuntimeConfigTest {
    @Test fun `runtime response decodes the authoritative fail-closed behavior`() {
        val value = NarratraceJson.decodeFromString<MobileRuntimeConfig>("""{
          "minimumSupportedVersion":"1.2.3","maintenance":false,"cacheForSeconds":86400,
          "behavior":{"capture":"last_known_good_then_allow_offline","upload":"fail_closed","billing":"fail_closed","quota":"fail_closed"},
          "authentication":{"mode":"hosted","protocolVersion":1,"startPath":"/api/v1/auth/hosted/start","legacyNativeAdmission":"compatibility_only"}
        }""")

        assertEquals("1.2.3", value.minimumSupportedVersion)
        assertEquals("fail_closed", value.behavior.upload)
        assertEquals(1, value.authentication?.protocolVersion)
        assertEquals(86_400, value.cacheForSeconds)
    }

    @Test fun `semantic versions compare numeric components rather than text`() {
        assertTrue(SemanticVersion.parse("1.10.0")!! > SemanticVersion.parse("1.2.9")!!)
        assertTrue(SemanticVersion.parse("2.0.0")!! > SemanticVersion.parse("1.99.99")!!)
        assertNull(SemanticVersion.parse("1.02.0"))
        assertNull(SemanticVersion.parse("1.0"))
    }

    @Test fun `minimum version and maintenance block online use`() = runTest {
        val update = repository(config(minimum = "1.0.1"), current = "1.0.0").resolve()
        assertEquals(RuntimeBlockReason.UpdateRequired, (update as RuntimeResolution.Blocked).reason)
        assertEquals("1.0.1", update.minimumSupportedVersion)

        val maintenance = repository(config(maintenance = true), current = "1.0.0").resolve()
        assertEquals(RuntimeBlockReason.Maintenance, (maintenance as RuntimeResolution.Blocked).reason)
    }

    @Test fun `valid compatible config allows online use and is cached`() = runTest {
        val cache = MemoryCache()
        val repository = repository(config(), cache = cache)
        val result = repository.resolve()

        assertEquals(RuntimeResolution.Available, result)
        assertEquals("1.0.0", cache.value?.value?.minimumSupportedVersion)
        assertTrue(repository.hostedAuthenticationAvailable())
    }

    @Test fun `older runtime contract uses only the bounded legacy admission fallback`() = runTest {
        val repository = repository(config().copy(authentication = null))

        assertEquals(RuntimeResolution.Available, repository.resolve())
        assertTrue(!repository.hostedAuthenticationAvailable())
    }

    @Test fun `offline lookup preserves a fresh cached blocker but never authorizes uploads`() = runTest {
        val blockedCache = MemoryCache(CachedRuntimeConfig(config(maintenance = true), 900))
        val blocked = repository(ApiResult.Offline(), blockedCache, now = 1_000).resolve()
        assertEquals(RuntimeBlockReason.Maintenance, (blocked as RuntimeResolution.Blocked).reason)

        val availableCache = MemoryCache(CachedRuntimeConfig(config(), 900))
        val offline = repository(ApiResult.Offline(), availableCache, now = 1_000).resolve()
        assertEquals(RuntimeBlockReason.OfflineCaptureOnly, (offline as RuntimeResolution.Blocked).reason)
    }

    @Test fun `expired cache and malformed safety behavior fall back to offline capture only`() = runTest {
        val expired = MemoryCache(CachedRuntimeConfig(config(maintenance = true), 0))
        val expiredResult = repository(ApiResult.Offline(), expired, now = 100_000).resolve()
        assertEquals(RuntimeBlockReason.OfflineCaptureOnly, (expiredResult as RuntimeResolution.Blocked).reason)

        val malformed = config().copy(behavior = config().behavior.copy(upload = "allow"))
        val malformedResult = repository(malformed).resolve()
        assertEquals(RuntimeBlockReason.OfflineCaptureOnly, (malformedResult as RuntimeResolution.Blocked).reason)
    }

    @Test fun `background upload worker checks runtime safety before restoring or reconciling`() {
        val source = File("src/main/java/io/narratrace/android/core/media/ProtectedUploadWorker.kt").readText()
        val runtimeCheck = source.indexOf("runtimeConfigRepository.resolve()")
        val sessionRestore = source.indexOf("sessionManager.restore()")
        val lifecycleCheck = source.indexOf("accountLifecycleApi.signal")
        val reconcile = source.indexOf("mediaRepository.reconcile()")

        assertTrue(runtimeCheck >= 0)
        assertTrue(runtimeCheck < sessionRestore)
        assertTrue(sessionRestore < lifecycleCheck)
        assertTrue(lifecycleCheck < reconcile)
        assertTrue(runtimeCheck < reconcile)
    }

    @Test fun `runtime blocking surfaces are accessible and preserve encrypted local capture`() {
        val source = File("src/main/java/io/narratrace/android/app/NarratraceApp.kt").readText()

        assertTrue(source.contains("Narratrace is undergoing maintenance"))
        assertTrue(source.contains("Update Narratrace to continue"))
        assertTrue(source.contains("Modifier.semantics { heading() }"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Assertive"))
        assertTrue(source.contains("Capture privately on this device"))
        assertTrue(source.contains("allowUpload = false"))
    }

    private fun config(minimum: String = "1.0.0", maintenance: Boolean = false) = MobileRuntimeConfig(
        minimumSupportedVersion = minimum,
        maintenance = maintenance,
        cacheForSeconds = 86_400,
        behavior = MobileRuntimeBehavior(
            capture = "last_known_good_then_allow_offline",
            upload = "fail_closed",
            billing = "fail_closed",
            quota = "fail_closed",
        ),
        authentication = MobileAuthenticationContract(
            mode = "hosted",
            protocolVersion = 1,
            startPath = "/api/v1/auth/hosted/start",
            legacyNativeAdmission = "compatibility_only",
        ),
    )

    private fun repository(
        value: MobileRuntimeConfig,
        current: String = "1.0.0",
        cache: MemoryCache = MemoryCache(),
    ) = repository(ApiResult.Success(value, "support"), cache, current = current)

    private fun repository(
        result: ApiResult<MobileRuntimeConfig>,
        cache: MemoryCache,
        current: String = "1.0.0",
        now: Long = 1_000,
    ) = RuntimeConfigRepository(
        gateway = object : RuntimeConfigGateway { override suspend fun load() = result },
        cache = cache,
        currentVersion = current,
        nowEpochSeconds = { now },
    )

    private class MemoryCache(var value: CachedRuntimeConfig? = null) : RuntimeConfigCache {
        override fun load() = value
        override fun save(value: CachedRuntimeConfig): Boolean { this.value = value; return true }
    }
}
