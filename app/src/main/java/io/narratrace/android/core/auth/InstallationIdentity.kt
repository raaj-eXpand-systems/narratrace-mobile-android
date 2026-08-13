package io.narratrace.android.core.auth

import android.content.Context
import java.util.UUID

fun interface InstallationIdProvider {
    fun installationId(): String?
}

/** Stable, non-secret identity used to bind sessions to this app installation. */
class AppInstallationIdentity(context: Context) : InstallationIdProvider {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun installationId(): String? {
        val existing = preferences.getString(KEY_INSTALLATION_ID, null)
        if (existing != null && UUID_PATTERN.matches(existing)) return existing

        val generated = UUID.randomUUID().toString().lowercase()
        if (!preferences.edit().putString(KEY_INSTALLATION_ID, generated).commit()) return null
        return generated
    }

    private companion object {
        const val PREFERENCES = "narratrace_installation"
        const val KEY_INSTALLATION_ID = "installation_id"
        val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
