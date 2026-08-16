package io.narratrace.android.core.settings

import android.content.Context
import io.narratrace.android.BuildConfig
import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.ui.NarratraceAppearance

class AppearanceStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance.v1", Context.MODE_PRIVATE)
    fun load(): NarratraceAppearance = runCatching { NarratraceAppearance.valueOf(preferences.getString("mode", null) ?: "System") }.getOrDefault(NarratraceAppearance.System)
    fun save(value: NarratraceAppearance): Boolean = preferences.edit().putString("mode", value.name).commit()
}

class SettingsRepository(private val api: SettingsApi, private val sessions: SessionManager) {
    suspend fun profile() = call { api.profile(it) }
    suspend fun preferences() = call { api.preferences(it) }
    suspend fun mediaAiPreferences() = call { api.mediaAiPreferences(it) }
    suspend fun updateProfile(name: String, birthYear: Int?, language: String): FeatureResult<ProfileResponse> {
        val clean = name.trim(); val year = java.time.Year.now().value
        if (clean.isEmpty() || clean.length > 80 || language !in setOf("en", "hi") || (birthYear != null && birthYear !in 1900..year - 5)) return FeatureResult.Unavailable("Check the profile name, birth year, and language.")
        return call { api.updateProfile(clean, birthYear, language, it) }
    }
    suspend fun updatePreference(key: String, value: Boolean) = call { api.updatePreference(key, value, it) }
    suspend fun updateMediaAiPreference(key: String, value: Boolean) = call { api.updateMediaAiPreference(key, value, it) }
    suspend fun disablePush(osVersion: String) = call { api.installation(BuildConfig.VERSION_NAME, osVersion, null, false, it) }
    suspend fun registerPush(pushToken: String, osVersion: String): FeatureResult<Updated> {
        if (pushToken.isBlank() || pushToken.length > 4096) return FeatureResult.Unavailable("A valid push registration token is required.")
        return call { api.installation(BuildConfig.VERSION_NAME, osVersion, pushToken, true, it) }
    }
    private suspend fun <T> call(block: suspend (String) -> ApiResult<T>): FeatureResult<T> {
        val lease = sessions.accessToken(); if (lease !is TokenLease.Valid) return FeatureResult.AuthenticationRequired
        var result = block(lease.accessToken); if (result is ApiResult.Unauthorized) { val recovered = sessions.recoverFromUnauthorized(lease.accessToken); if (recovered !is TokenLease.Valid) return FeatureResult.AuthenticationRequired; result = block(recovered.accessToken) }
        return when (result) { is ApiResult.Success -> FeatureResult.Success(result.value); is ApiResult.Unauthorized -> FeatureResult.AuthenticationRequired; is ApiResult.Failure -> FeatureResult.Unavailable(result.message, result.supportReference) }
    }
}
