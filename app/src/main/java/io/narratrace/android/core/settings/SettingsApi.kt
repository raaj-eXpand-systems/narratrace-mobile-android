package io.narratrace.android.core.settings

import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.network.NarratraceJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

@Serializable data class Profile(val email: String, val displayName: String, val birthYear: Int? = null, val preferredLanguage: String)
@Serializable data class ProfileResponse(val profile: Profile)
@Serializable private data class ProfileInput(val displayName: String, val birthYear: Int? = null, val preferredLanguage: String)
@Serializable data class NotificationPreferences(
    @SerialName("processing_ready") val processingReady: Boolean,
    val invitations: Boolean, val letters: Boolean,
    @SerialName("trial_and_billing") val trialAndBilling: Boolean,
    @SerialName("product_guidance") val productGuidance: Boolean,
    @SerialName("weekly_memory_nudge") val weeklyMemoryNudge: Boolean,
    @SerialName("re_engagement") val reEngagement: Boolean,
    @SerialName("yearbook_reminder") val yearbookReminder: Boolean,
    @SerialName("interview_anniversary") val interviewAnniversary: Boolean,
)
@Serializable data class PreferencesResponse(val preferences: NotificationPreferences)
@Serializable data class MediaAiPreferences(
    @SerialName("photo_ai_insights_enabled") val photoAiInsightsEnabled: Boolean = false,
    @SerialName("video_ai_insights_enabled") val videoAiInsightsEnabled: Boolean = false,
)
@Serializable data class MediaAiPreferencesResponse(
    val preferences: MediaAiPreferences,
    val consentVersions: Map<String, String> = emptyMap(),
    val consentCopy: Map<String, String> = emptyMap(),
) {
    fun disclosure(key: String): MediaAiDisclosure? {
        val kind = mediaAiKind(key) ?: return null
        val version = consentVersions[kind]?.takeIf { it.isNotBlank() } ?: return null
        val copy = consentCopy[kind]?.takeIf { it.isNotBlank() } ?: return null
        return MediaAiDisclosure(kind, version, copy)
    }
}
data class MediaAiDisclosure(val kind: String, val version: String, val copy: String)
internal fun mediaAiKind(key: String): String? = when (key) {
    "photo_ai_insights_enabled" -> "photo"
    "video_ai_insights_enabled" -> "video"
    else -> null
}
@Serializable private data class MediaAiPreferencePatch(
    @SerialName("photo_ai_insights_enabled") val photoAiInsightsEnabled: Boolean? = null,
    @SerialName("video_ai_insights_enabled") val videoAiInsightsEnabled: Boolean? = null,
    val consentVersions: Map<String, String>? = null,
)
internal fun mediaAiPreferenceBody(key: String, value: Boolean, disclosure: MediaAiDisclosure?): String? {
    val kind = mediaAiKind(key) ?: return null
    if (value && (disclosure?.kind != kind || disclosure.version.isBlank() || disclosure.copy.isBlank())) return null
    val versions = if (value) mapOf(kind to disclosure!!.version) else null
    return NarratraceJson.encodeToString(if (kind == "photo")
        MediaAiPreferencePatch(photoAiInsightsEnabled = value, consentVersions = versions)
    else MediaAiPreferencePatch(videoAiInsightsEnabled = value, consentVersions = versions))
}
@Serializable private data class PreferencePatch(
    @SerialName("processing_ready") val processingReady: Boolean? = null,
    val invitations: Boolean? = null, val letters: Boolean? = null,
    @SerialName("trial_and_billing") val trialAndBilling: Boolean? = null,
    @SerialName("product_guidance") val productGuidance: Boolean? = null,
)
@Serializable private data class InstallationInput(val appVersion: String, val osVersion: String, val pushToken: String? = null, val notificationsEnabled: Boolean)
@Serializable data class Updated(val updated: Boolean)

class SettingsApi(private val client: NarratraceApiClient) {
    suspend fun profile(token: String): ApiResult<ProfileResponse> = client.get("/api/v1/profile", serializer<ProfileResponse>(), token)
    suspend fun updateProfile(name: String, birthYear: Int?, language: String, token: String): ApiResult<ProfileResponse> = client.patch("/api/v1/profile", NarratraceJson.encodeToString(ProfileInput(name, birthYear, language)), serializer<ProfileResponse>(), token)
    suspend fun preferences(token: String): ApiResult<PreferencesResponse> = client.get("/api/v1/mobile/notification-preferences", serializer<PreferencesResponse>(), token)
    suspend fun mediaAiPreferences(token: String): ApiResult<MediaAiPreferencesResponse> = client.get("/api/v1/mobile/media-ai-preferences", serializer<MediaAiPreferencesResponse>(), token)
    suspend fun updateMediaAiPreference(key: String, value: Boolean, disclosure: MediaAiDisclosure?, token: String): ApiResult<MediaAiPreferencesResponse> {
        val body = mediaAiPreferenceBody(key, value, disclosure)
            ?: return ApiResult.Unreadable(reason = "Review the current media AI disclosure before enabling insights.")
        return client.patch("/api/v1/mobile/media-ai-preferences", body, serializer<MediaAiPreferencesResponse>(), token)
    }
    suspend fun updatePreference(key: String, value: Boolean, token: String): ApiResult<PreferencesResponse> {
        val body = when (key) {
            "processing_ready" -> PreferencePatch(processingReady = value); "invitations" -> PreferencePatch(invitations = value)
            "letters" -> PreferencePatch(letters = value); "trial_and_billing" -> PreferencePatch(trialAndBilling = value)
            else -> PreferencePatch(productGuidance = value)
        }
        return client.patch("/api/v1/mobile/notification-preferences", NarratraceJson.encodeToString(body), serializer<PreferencesResponse>(), token)
    }
    suspend fun installation(appVersion: String, osVersion: String, pushToken: String?, enabled: Boolean, token: String): ApiResult<Updated> = client.patch("/api/v1/mobile/installation", NarratraceJson.encodeToString(InstallationInput(appVersion, osVersion, pushToken, enabled)), serializer<Updated>(), token)
}
