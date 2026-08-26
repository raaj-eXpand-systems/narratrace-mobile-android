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
@Serializable data class MediaAiPreferencesResponse(val preferences: MediaAiPreferences)
@Serializable private data class MediaAiPreferencePatch(
    @SerialName("photo_ai_insights_enabled") val photoAiInsightsEnabled: Boolean? = null,
    @SerialName("video_ai_insights_enabled") val videoAiInsightsEnabled: Boolean? = null,
)
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
    suspend fun updateMediaAiPreference(key: String, value: Boolean, token: String): ApiResult<MediaAiPreferencesResponse> {
        val body = if (key == "photo_ai_insights_enabled") MediaAiPreferencePatch(photoAiInsightsEnabled = value) else MediaAiPreferencePatch(videoAiInsightsEnabled = value)
        return client.patch("/api/v1/mobile/media-ai-preferences", NarratraceJson.encodeToString(body), serializer<MediaAiPreferencesResponse>(), token)
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
