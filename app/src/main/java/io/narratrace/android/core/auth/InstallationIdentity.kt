package io.narratrace.android.core.auth

import java.util.UUID

fun interface InstallationIdProvider {
    fun newSignInInstallationId(): String?
}

/**
 * A fresh account-binding identity for an explicit sign-in attempt. The encrypted
 * pending PKCE transaction retains it across browser return and process restart;
 * the server binds the resulting session to it for that session's lifetime.
 * Reusing a previous account's binding would prevent secure account switching.
 */
class AppInstallationIdentity : InstallationIdProvider {
    override fun newSignInInstallationId(): String = UUID.randomUUID().toString().lowercase()
}
