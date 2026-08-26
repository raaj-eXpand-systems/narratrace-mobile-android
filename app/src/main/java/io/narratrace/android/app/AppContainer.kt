package io.narratrace.android.app

import android.content.Context
import android.os.Build
import io.narratrace.android.BuildConfig
import io.narratrace.android.core.auth.AppInstallationIdentity
import io.narratrace.android.core.auth.AuthApi
import io.narratrace.android.core.auth.AuthenticationCoordinator
import io.narratrace.android.core.auth.CredentialManagerGoogleProvider
import io.narratrace.android.core.auth.FileBlobStore
import io.narratrace.android.core.auth.KeystoreCredentialCipher
import io.narratrace.android.core.auth.IdentityTokenRequester
import io.narratrace.android.core.auth.SessionAdopter
import io.narratrace.android.core.auth.SessionManager
import io.narratrace.android.core.auth.SessionStore
import io.narratrace.android.core.auth.SecurityRepository
import io.narratrace.android.core.network.NarratraceApiClient
import io.narratrace.android.core.customer.CustomerApi
import io.narratrace.android.core.customer.CustomerRepository
import io.narratrace.android.core.media.MediaAndInterviewApi
import io.narratrace.android.core.media.MediaAndInterviewRepository
import io.narratrace.android.core.media.ProtectedMediaQueue
import io.narratrace.android.core.letters.LettersApi
import io.narratrace.android.core.letters.LettersRepository
import io.narratrace.android.core.family.FamilyApi
import io.narratrace.android.core.family.FamilyRepository
import io.narratrace.android.core.account.AccountLifecycleApi
import io.narratrace.android.core.settings.AppearanceStore
import io.narratrace.android.core.settings.SettingsApi
import io.narratrace.android.core.settings.SettingsRepository
import io.narratrace.android.core.offline.OfflineApi
import io.narratrace.android.core.offline.OfflineDraftStore
import io.narratrace.android.core.offline.OfflineRepository
import io.narratrace.android.core.offline.OnboardingStore
import io.narratrace.android.core.support.SupportApi
import io.narratrace.android.core.support.SupportRepository
import java.io.File
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Application-scoped dependencies, wired by hand.
 *
 * ANDROID_ARCHITECTURE_PLAN.md §2 specifies Hilt, and that remains the destination.
 * It is deferred deliberately: Hilt requires KSP, KSP versions are pinned to exact
 * Kotlin releases, and adopting it before there is anything non-trivial to inject
 * buys an annotation processor and a version-matching constraint in exchange for
 * nothing. This container is the entire cost of the alternative.
 *
 * Swap to Hilt when either becomes true:
 *   - a dependency needs a scope narrower than the application (an authenticated
 *     account scope is the likely first one, in Phase 1b), or
 *   - this file exceeds roughly fifty lines.
 *
 * Nothing outside this file constructs a collaborator, so the swap stays mechanical.
 */
class AppContainer(context: Context) {
    data class PendingInvite(val kind: String, val token: String)
    @Volatile var pendingInvite: PendingInvite? = null
    private val appContext = context.applicationContext
    val appearanceStore = AppearanceStore(appContext)
    val onboardingStore = OnboardingStore(appContext)
    private val supportPreferences = appContext.getSharedPreferences("support.v1", Context.MODE_PRIVATE)
    fun latestSupportReference(): String = supportPreferences.getString("latest", "").orEmpty()

    val apiClient: NarratraceApiClient by lazy {
        NarratraceApiClient(
            baseUrl = BuildConfig.API_BASE_URL,
            appVersion = BuildConfig.VERSION_NAME,
            supportReferenceSink = { supportPreferences.edit().putString("latest", it).apply() },
        )
    }

    /**
     * Whether this build can talk to Narratrace at all.
     *
     * `API_BASE_URL` is intentionally empty in a checked-out tree — the origin is
     * supplied per-machine through local.properties and never committed, mirroring
     * the iOS arrangement. A build without it fails closed at the first request
     * rather than pretending to work offline, so surfacing it early lets the UI say
     * something honest instead of showing an endless spinner.
     */
    val isApiConfigured: Boolean get() = BuildConfig.API_BASE_URL.isNotBlank()

    val authApi: AuthApi by lazy { AuthApi(apiClient) }
    val customerApi: CustomerApi by lazy { CustomerApi(apiClient) }
    val accountLifecycleApi: AccountLifecycleApi by lazy { AccountLifecycleApi(apiClient) }
    val securityRepository: SecurityRepository by lazy { SecurityRepository(authApi, sessionManager) }

    val sessionManager: SessionManager by lazy {
        val store = SessionStore(
            cipher = KeystoreCredentialCipher(),
            blobStore = FileBlobStore(File(appContext.filesDir, "protected-session.bin")),
        )
        SessionManager(store = store, refresher = authApi::refresh)
    }

    val customerRepository: CustomerRepository by lazy {
        CustomerRepository(customerApi, sessionManager)
    }

    val mediaRepository: MediaAndInterviewRepository by lazy {
        MediaAndInterviewRepository(
            MediaAndInterviewApi(apiClient), sessionManager,
            ProtectedMediaQueue(
                File(appContext.filesDir, "protected-media"),
                KeystoreCredentialCipher("io.narratrace.android.media.v1"),
            ),
        )
    }
    val lettersRepository: LettersRepository by lazy { LettersRepository(LettersApi(apiClient), sessionManager) }
    val familyRepository: FamilyRepository by lazy { FamilyRepository(FamilyApi(apiClient), sessionManager) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(SettingsApi(apiClient), sessionManager) }
    val supportRepository: SupportRepository by lazy { SupportRepository(SupportApi(apiClient), sessionManager) }
    fun firebaseMessaging(): FirebaseMessaging? {
        if (listOf(BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APPLICATION_ID, BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID).any(String::isBlank)) return null
        val app = FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(appContext, FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY).setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build())
        return app?.let { FirebaseMessaging.getInstance() }
    }
    val offlineRepository: OfflineRepository by lazy { OfflineRepository(
        OfflineApi(apiClient), sessionManager,
        OfflineDraftStore(File(appContext.filesDir, "protected-drafts.bin"), KeystoreCredentialCipher("io.narratrace.android.drafts.v1")),
    ) }

    /** Applies the server's terminal local-data disposition without retaining a duplicate path. */
    fun purgeAccountLocalData(): Boolean {
        val mediaPurged = mediaRepository.queue.purgeAccountData()
        val draftsPurged = offlineRepository.store.purgeAccountData()
        val supportPurged = supportPreferences.edit().clear().commit()
        val modesPurged = appContext.getSharedPreferences("interview-modes.v1", Context.MODE_PRIVATE).edit().clear().commit()
        val capturesPurged = appContext.cacheDir.listFiles()
            ?.filter { it.name.startsWith("capture-") }
            ?.all { !it.exists() || it.delete() } ?: true
        val sessionPurged = sessionManager.purgeAccountSession()
        return mediaPurged && draftsPurged && supportPurged && modesPurged && capturesPurged && sessionPurged
    }

    fun authenticationCoordinator(context: Context): AuthenticationCoordinator =
        AuthenticationCoordinator(
            gateway = authApi,
            identityTokenRequester = IdentityTokenRequester { nonce ->
                CredentialManagerGoogleProvider().requestIdToken(context, nonce)
            },
            installationIdProvider = AppInstallationIdentity(appContext),
            sessionAdopter = SessionAdopter(sessionManager::adopt),
            appVersion = BuildConfig.VERSION_NAME.removeSuffix("-debug"),
            osVersion = Build.VERSION.RELEASE.take(40),
        )
}
