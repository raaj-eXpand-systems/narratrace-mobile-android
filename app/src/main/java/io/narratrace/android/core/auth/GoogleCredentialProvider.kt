package io.narratrace.android.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.narratrace.android.BuildConfig

/**
 * The outcome of asking Google for an identity.
 *
 * Cancellation is modelled separately from failure because they warrant different
 * responses: someone who dismissed the sheet does not want an error message, they
 * want the screen they were on.
 */
sealed interface GoogleIdentityResult {
    data class Success(val idToken: String) : GoogleIdentityResult
    data object Cancelled : GoogleIdentityResult
    data object NoAccountAvailable : GoogleIdentityResult
    data class Failed(val reason: String) : GoogleIdentityResult
    data object NotConfigured : GoogleIdentityResult
}

/** Seam for testing. The real implementation needs an Activity context. */
interface GoogleCredentialProvider {
    suspend fun requestIdToken(context: Context, nonce: String): GoogleIdentityResult
}

/**
 * Obtains a Google ID token via Credential Manager.
 *
 * The [nonce] is the one-time value from `/api/v1/auth/challenge`. Google echoes it
 * verbatim in the token's `nonce` claim, and the server rejects any token whose
 * nonce does not match the challenge it issued. That binding is what makes an
 * intercepted token useless — so a nonce is never reused, never cached, and never
 * generated on the device.
 *
 * `serverClientId` must be the **Web** client ID, not the Android one. It becomes
 * the token's `aud` claim, which the backend matches against
 * GOOGLE_MOBILE_CLIENT_IDS. The Android client exists only so Google will issue a
 * credential to this package and signing certificate; it never appears in a token.
 */
class CredentialManagerGoogleProvider(
    private val serverClientId: String = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
) : GoogleCredentialProvider {

    override suspend fun requestIdToken(context: Context, nonce: String): GoogleIdentityResult {
        if (serverClientId.isBlank()) return GoogleIdentityResult.NotConfigured
        if (nonce.length < MIN_NONCE_LENGTH) {
            // The server rejects a nonce outside 16..180 characters. Catching it
            // here avoids showing an account picker that was always going to fail.
            return GoogleIdentityResult.Failed("The sign-in challenge was not valid.")
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setNonce(nonce)
            // false ⇒ offer every Google account on the device, not only ones that
            // have used Narratrace before. Narratrace is invitation-only, and a
            // member's invitation may not be on the account they last signed in
            // with; hiding the others would strand them with no way forward.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return GoogleIdentityResult.Failed("Narratrace received an unexpected credential type.")
            }
            val googleId = GoogleIdTokenCredential.createFrom(credential.data)
            val token = googleId.idToken
            if (token.isBlank()) {
                GoogleIdentityResult.Failed("Google did not return an identity token.")
            } else {
                GoogleIdentityResult.Success(token)
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleIdentityResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleIdentityResult.NoAccountAvailable
        } catch (_: GetCredentialException) {
            // Exception messages from Credential Manager can name the account.
            // Nothing provider-supplied is surfaced or logged.
            GoogleIdentityResult.Failed("Google sign-in could not be completed.")
        }
    }

    private companion object {
        const val MIN_NONCE_LENGTH = 16
    }
}
