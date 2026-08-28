package io.narratrace.android.core.runtime

import android.content.Context
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable
data class MobileRuntimeBehavior(
    val capture: String,
    val upload: String,
    val billing: String,
    val quota: String,
)

@Serializable
data class MobileAuthenticationContract(
    val mode: String,
    val protocolVersion: Int,
    val startPath: String,
    val legacyNativeAdmission: String,
)

@Serializable
data class MobileRuntimeConfig(
    val minimumSupportedVersion: String,
    val maintenance: Boolean,
    val cacheForSeconds: Long,
    val behavior: MobileRuntimeBehavior,
    /** Absent only on an older server during the bounded rollback window. */
    val authentication: MobileAuthenticationContract? = null,
)

internal interface RuntimeConfigGateway {
    suspend fun load(): ApiResult<MobileRuntimeConfig>
}

internal class RuntimeConfigApi(private val client: NarratraceApiClient) : RuntimeConfigGateway {
    override suspend fun load(): ApiResult<MobileRuntimeConfig> = client.get(
        "/api/v1/mobile/runtime-config?platform=android",
        serializer<MobileRuntimeConfig>(),
    )
}

@Serializable
internal data class CachedRuntimeConfig(val value: MobileRuntimeConfig, val recordedAtEpochSeconds: Long)

internal interface RuntimeConfigCache {
    fun load(): CachedRuntimeConfig?
    fun save(value: CachedRuntimeConfig): Boolean
}

internal class SharedPreferencesRuntimeConfigCache(context: Context) : RuntimeConfigCache {
    private val preferences = context.applicationContext.getSharedPreferences("mobile-runtime-config.v1", Context.MODE_PRIVATE)

    override fun load(): CachedRuntimeConfig? = preferences.getString("config", null)?.let { encoded ->
        runCatching { NarratraceJson.decodeFromString<CachedRuntimeConfig>(encoded) }.getOrNull()
    }

    override fun save(value: CachedRuntimeConfig): Boolean = preferences.edit()
        .putString("config", NarratraceJson.encodeToString(CachedRuntimeConfig.serializer(), value))
        .commit()
}

internal enum class RuntimeBlockReason { Maintenance, UpdateRequired, OfflineCaptureOnly }

internal sealed interface RuntimeResolution {
    data object Available : RuntimeResolution
    data class Blocked(
        val reason: RuntimeBlockReason,
        val minimumSupportedVersion: String? = null,
    ) : RuntimeResolution
}

internal class RuntimeConfigRepository(
    private val gateway: RuntimeConfigGateway,
    private val cache: RuntimeConfigCache,
    currentVersion: String,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {
    private val installedVersion = SemanticVersion.parse(currentVersion.substringBefore('-'))
    @Volatile private var resolvedAuthentication: MobileAuthenticationContract? = null

    fun hostedAuthenticationAvailable(): Boolean = resolvedAuthentication?.let {
        it.mode == "hosted" && it.protocolVersion == 1 &&
            it.startPath == "/api/v1/auth/hosted/start" && it.legacyNativeAdmission == "compatibility_only"
    } == true

    suspend fun resolve(): RuntimeResolution {
        val now = nowEpochSeconds()
        val remote = gateway.load()
        if (remote is ApiResult.Success && remote.value.isValidSafetyContract()) {
            resolvedAuthentication = remote.value.authentication
            cache.save(CachedRuntimeConfig(remote.value, now))
            return evaluate(remote.value)
        }

        val cached = cache.load()?.takeIf { candidate ->
            candidate.value.isValidSafetyContract() &&
                now >= candidate.recordedAtEpochSeconds &&
                now - candidate.recordedAtEpochSeconds <= candidate.value.cacheForSeconds
        }
        val cachedDecision = cached?.let { evaluate(it.value) }
        resolvedAuthentication = cached?.value?.authentication
        return if (cachedDecision is RuntimeResolution.Blocked) cachedDecision
        else RuntimeResolution.Blocked(RuntimeBlockReason.OfflineCaptureOnly)
    }

    private fun evaluate(config: MobileRuntimeConfig): RuntimeResolution {
        if (config.maintenance) return RuntimeResolution.Blocked(RuntimeBlockReason.Maintenance)
        val minimum = SemanticVersion.parse(config.minimumSupportedVersion)
            ?: return RuntimeResolution.Blocked(RuntimeBlockReason.OfflineCaptureOnly)
        val installed = installedVersion
            ?: return RuntimeResolution.Blocked(RuntimeBlockReason.UpdateRequired, config.minimumSupportedVersion)
        return if (installed < minimum) {
            RuntimeResolution.Blocked(RuntimeBlockReason.UpdateRequired, config.minimumSupportedVersion)
        } else RuntimeResolution.Available
    }
}

private fun MobileRuntimeConfig.isValidSafetyContract(): Boolean =
    SemanticVersion.parse(minimumSupportedVersion) != null &&
        cacheForSeconds in 1..604_800 &&
        behavior.capture == "last_known_good_then_allow_offline" &&
        behavior.upload == "fail_closed" &&
        behavior.billing == "fail_closed" &&
        behavior.quota == "fail_closed" &&
        (authentication == null || (
            authentication.mode == "hosted" && authentication.protocolVersion == 1 &&
                authentication.startPath == "/api/v1/auth/hosted/start" &&
                authentication.legacyNativeAdmission == "compatibility_only"
        ))

internal data class SemanticVersion(
    val major: Long,
    val minor: Long,
    val patch: Long,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    companion object {
        private val pattern = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")

        fun parse(value: String): SemanticVersion? {
            val match = pattern.matchEntire(value) ?: return null
            val parts = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
            return SemanticVersion(parts[0], parts[1], parts[2])
        }
    }
}
