package io.narratrace.android.app

import io.narratrace.android.BuildConfig
import io.narratrace.android.core.network.NarratraceApiClient

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
class AppContainer {

    val apiClient: NarratraceApiClient by lazy {
        NarratraceApiClient(
            baseUrl = BuildConfig.API_BASE_URL,
            appVersion = BuildConfig.VERSION_NAME,
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
}
