package io.narratrace.android.app

import android.Manifest
import android.app.Activity
import android.os.Build
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.provider.Settings
import android.net.Uri
import android.util.Base64
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.BackHandler
import io.narratrace.android.core.auth.AuthState
import io.narratrace.android.core.auth.SignInResult
import io.narratrace.android.core.auth.HostedAuthEvent
import io.narratrace.android.core.auth.HostedAuthStartResult
import io.narratrace.android.core.auth.RevokeScope
import io.narratrace.android.core.auth.RevocationResult
import io.narratrace.android.core.auth.SecuritySessionsResult
import io.narratrace.android.core.auth.TokenLease
import io.narratrace.android.core.account.AccountLifecycleSignal
import io.narratrace.android.core.account.allowsOrdinaryAccess
import io.narratrace.android.core.account.requiresLocalPurge
import io.narratrace.android.core.account.safeAppealUrl
import io.narratrace.android.core.customer.CustomerHome
import io.narratrace.android.core.customer.CustomerHomeResult
import io.narratrace.android.core.customer.CustomerMemoriesResult
import io.narratrace.android.core.customer.RemoteMemory
import io.narratrace.android.core.customer.CustomerPeopleResult
import io.narratrace.android.core.customer.CustomerPersonResult
import io.narratrace.android.core.customer.RemotePerson
import io.narratrace.android.core.customer.RemotePersonDetail
import io.narratrace.android.core.customer.AccountResult
import io.narratrace.android.core.customer.AccountSummary
import io.narratrace.android.core.customer.ProductionArchive
import io.narratrace.android.core.customer.ProductionAllowance
import io.narratrace.android.core.customer.WrittenMemoryResult
import io.narratrace.android.core.customer.CustomerMemoryResult
import io.narratrace.android.core.customer.hasGuidedInterviewOnlyAccess
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.media.InterviewDetail
import io.narratrace.android.core.media.InterviewSummary
import io.narratrace.android.core.media.MediaSummary
import io.narratrace.android.core.media.PendingMediaKind
import io.narratrace.android.core.media.SecureAudioRecorder
import io.narratrace.android.core.media.ProtectedUploadWorker
import io.narratrace.android.core.media.protectedUploadAttention
import io.narratrace.android.core.letters.LetterSummary
import io.narratrace.android.core.letters.letterDeliveryStatus
import io.narratrace.android.core.letters.statusLabel
import io.narratrace.android.core.letters.canDisplayContent
import io.narratrace.android.core.delivery.DeliveryMode
import io.narratrace.android.core.support.FeedbackScreenshot
import io.narratrace.android.core.support.ProcessingJob
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.runtime.RuntimeBlockReason
import io.narratrace.android.core.runtime.RuntimeResolution
import java.time.LocalDateTime
import io.narratrace.android.core.ui.NarratraceAppearance
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

internal const val TERMS_POLICY_URL = "https://getnarratrace.com/terms"
internal const val PRIVACY_POLICY_URL = "https://getnarratrace.com/privacy"
internal const val COOKIE_POLICY_URL = "https://getnarratrace.com/cookies"
internal const val LEGAL_REVIEW_HEADING = "Review Narratrace Terms"
internal const val MEDIA_INSIGHTS_HEADING = "Nia’s media insights"
internal fun shouldRefreshPhotoInsights(mediaKind: String, enabled: Boolean) =
    mediaKind == "photo" && enabled

internal fun visibleMediaClarifyingQuestions(mediaKind: String, enabled: Boolean, questions: List<String>) =
    if (shouldRefreshPhotoInsights(mediaKind, enabled)) questions.take(3) else emptyList()
internal const val LEGAL_CHANGE_SUMMARY = "The Terms and Privacy Policy clarify that Stripe-hosted Checkout collects and stores payment credentials and checkout addresses, while Narratrace receives limited transaction and billing records."
internal const val NIA_DEFINITION = "Nia is the name Narratrace gives its AI assistant and AI-supported story companion. References to Nia mean Narratrace’s AI features, not a person."
internal const val ADULT_ACCOUNT_NOTICE = "Narratrace accounts are intended only for people 18 years of age or older."
internal const val ACCOUNT_CLOSURE_URL = "https://www.narratrace.io/account#closure"
internal const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=io.narratrace.android"

private fun InputStream.readBounded(maximum: Int): ByteArray? {
    val output = ByteArrayOutputStream(minOf(maximum, 64 * 1024))
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maximum) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun ProductionAllowance?.combinedRemaining(pool: ProductionAllowance?): Long? =
    if (this == null && pool == null) null else (this?.remaining ?: 0) + (pool?.remaining ?: 0)

internal data class ProductionCaptureAvailability(
    val selectedArchive: ProductionArchive?,
    val targetRequired: Boolean,
    val audioEnabled: Boolean,
    val photoEnabled: Boolean,
    val videoEnabled: Boolean,
)

internal fun productionCaptureAvailability(account: AccountSummary, selectedArchiveId: String?): ProductionCaptureAvailability {
    val selectedArchive = account.productionArchives.firstOrNull { it.id == selectedArchiveId }
    val targetRequired = account.productionArchives.size > 1 && selectedArchive == null
    val photoRemaining = selectedArchive?.photographs?.remaining
    val audioRemaining = selectedArchive?.audioSeconds.combinedRemaining(account.productionPools.audioSeconds)
    val videoRemaining = selectedArchive?.videoSeconds.combinedRemaining(account.productionPools.videoSeconds)
    return ProductionCaptureAvailability(
        selectedArchive = selectedArchive,
        targetRequired = targetRequired,
        photoEnabled = account.hasAccess && !targetRequired && photoRemaining != 0L,
        audioEnabled = account.hasAccess && !targetRequired && audioRemaining != 0L,
        videoEnabled = account.hasAccess && account.capabilities.captureVideo && !targetRequired && videoRemaining != 0L,
    )
}

private fun Long.durationAllowanceLabel(): String {
    val hours = this / 3_600
    val minutes = (this % 3_600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        this > 0 && minutes == 0L -> "${this}s"
        else -> "${minutes}m"
    }
}

private fun uploadResultMessage(container: AppContainer, mediaLabel: String, remaining: Int): String {
    val issue = container.mediaRepository.latestReconciliationIssue()
    if (issue != null) return buildString {
        append(issue.message)
        if (issue.supportReference.isNotBlank()) append(" Support reference: ${issue.supportReference}")
    }
    return if (remaining == 0) "$mediaLabel preserved securely."
    else "$mediaLabel protected on this device and waiting for secure transfer."
}

@Composable
private fun ProductionCaptureTargetCard(
    account: AccountSummary,
    selectedArchive: ProductionArchive?,
    choose: (() -> Unit)?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Storyteller allowance", style = MaterialTheme.typography.titleMedium)
            if (selectedArchive == null) {
                Text(
                    "Choose who these standalone audio recordings, photos, and videos are about before adding media.",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text("Adding media for ${selectedArchive.subjectName}")
                selectedArchive.audioSeconds.combinedRemaining(account.productionPools.audioSeconds)?.let { remaining ->
                    Text("Voice: ${remaining.durationAllowanceLabel()} remaining", color = if (remaining == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                selectedArchive.photographs?.let { allowance ->
                    Text(
                        "Photos: ${"%,d".format(allowance.remaining)} of ${"%,d".format(allowance.granted)} remaining",
                        color = if (allowance.remaining == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!account.capabilities.captureVideo) {
                    Text("Video is not included in this plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else selectedArchive.videoSeconds.combinedRemaining(account.productionPools.videoSeconds)?.let { remaining ->
                    Text("Video: ${remaining.durationAllowanceLabel()} remaining", color = if (remaining == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if ((account.productionPools.audioSeconds?.remaining ?: 0) > 0 || (account.productionPools.videoSeconds?.remaining ?: 0) > 0) {
                    Text("Remaining voice and video totals include shared add-on capacity.", style = MaterialTheme.typography.bodySmall)
                }
            }
            choose?.let { action ->
                Button(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (selectedArchive == null) "Choose storyteller" else "Change storyteller")
                }
            }
        }
    }
}

@Composable
fun NarratraceApp(container: AppContainer) {
    var onboarded by remember { mutableStateOf(container.onboardingStore.completed()) }
    if (!onboarded) { OnboardingScreen { if (container.onboardingStore.complete()) onboarded = true }; return }
    val authState by container.sessionManager.state.collectAsStateWithLifecycle()
    var runtimeResolution by remember { mutableStateOf<RuntimeResolution?>(null) }
    var runtimeRefresh by remember { mutableIntStateOf(0) }
    var offlineCapture by remember { mutableStateOf(false) }
    LaunchedEffect(container) { container.sessionManager.restore() }
    LaunchedEffect(container, runtimeRefresh) {
        runtimeResolution = container.runtimeConfigRepository.resolve()
    }

    val resolution = runtimeResolution
    if (resolution == null) { ProtectedLoadingScreen(); return }
    if (resolution is RuntimeResolution.Blocked) {
        val hasKnownLocalAccount = authState is AuthState.Authenticated || authState is AuthState.Locked
        if (offlineCapture && hasKnownLocalAccount) {
            OfflineCaptureScreen(container, Modifier) { offlineCapture = false }
        } else {
            RuntimeBlockedScreen(
                resolution = resolution,
                canCaptureOffline = hasKnownLocalAccount,
                captureOffline = { offlineCapture = true },
                signOut = if (hasKnownLocalAccount) container.sessionManager::signOut else null,
                retry = { runtimeResolution = null; runtimeRefresh++ },
            )
        }
        return
    }

    when (authState) {
        AuthState.Restoring -> ProtectedLoadingScreen()
        AuthState.SignedOut -> SignInScreen(container = container, returning = false)
        is AuthState.Locked -> LockedLifecycleGate(container) {
            SignInScreen(container = container, returning = true)
        }
        is AuthState.Authenticated -> AccountLifecycleGate(
            container = container,
            accessCredential = (authState as AuthState.Authenticated).session.accessToken,
        ) {
            RequiredLegalGate(container) {
                AuthenticatedShell(
                    container = container,
                    onInteraction = container.sessionManager::touch,
                    onSignOut = container.sessionManager::signOut,
                )
            }
        }
    }
}

@Composable
private fun LockedLifecycleGate(container: AppContainer, activeAccount: @Composable () -> Unit) {
    val credential = remember { container.sessionManager.lifecycleCredential() }
    var result by remember(credential) { mutableStateOf<ApiResult<AccountLifecycleSignal>?>(null) }
    LaunchedEffect(credential) {
        result = credential?.let { container.accountLifecycleApi.signal(it) }
            ?: ApiResult.Unauthorized("Sign in again.", "")
    }
    when (val current = result) {
        null -> ProtectedLoadingScreen()
        is ApiResult.Success -> if (current.value.allowsOrdinaryAccess()) activeAccount()
        else RestrictedLifecycleScreen(container, current.value, credential.orEmpty()) { result = null }
        is ApiResult.Failure -> activeAccount()
    }
}

@Composable
private fun RuntimeBlockedScreen(
    resolution: RuntimeResolution.Blocked,
    canCaptureOffline: Boolean,
    captureOffline: () -> Unit,
    signOut: (() -> Unit)?,
    retry: () -> Unit,
) {
    val context = LocalContext.current
    val heading = when (resolution.reason) {
        RuntimeBlockReason.Maintenance -> "Narratrace is undergoing maintenance"
        RuntimeBlockReason.UpdateRequired -> "Update Narratrace to continue"
        RuntimeBlockReason.OfflineCaptureOnly -> "Narratrace is temporarily offline"
    }
    val explanation = when (resolution.reason) {
        RuntimeBlockReason.Maintenance -> "Online features are paused while maintenance is completed. Nothing queued on this device will be uploaded yet."
        RuntimeBlockReason.UpdateRequired -> "This version can no longer connect safely. Install the current Android app before using online features. Nothing queued on this device will be uploaded yet."
        RuntimeBlockReason.OfflineCaptureOnly -> "The app could not verify its current connection requirements. Online features and uploads remain paused."
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(heading, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(
            explanation,
            Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        resolution.minimumSupportedVersion?.let {
            Text("Minimum supported version: $it", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (canCaptureOffline) {
            Button(captureOffline, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Capture privately on this device") }
            Text("Local captures stay encrypted and are not transferred until Narratrace verifies that online use is available.", style = MaterialTheme.typography.bodySmall)
        }
        if (resolution.reason == RuntimeBlockReason.UpdateRequired) {
            Button(
                { context.startActivity(Intent(Intent.ACTION_VIEW, PLAY_STORE_URL.toUri())) },
                Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Open Google Play") }
        }
        TextButton(retry, Modifier.fillMaxWidth()) { Text("Check again") }
        signOut?.let { TextButton(it, Modifier.fillMaxWidth()) { Text("Sign out on this device") } }
    }
}

@Composable private fun OnboardingScreen(complete: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val titles = listOf("Your stories, protected", "Capture in your own way", "Share only when you choose")
    val messages = listOf(
        "Narratrace preserves voices, photos, videos, Letters, and written Memories in your private account.",
        "Write, record, photograph, film, or use a guided interview. Interrupted work can remain encrypted on this device.",
        "Nothing is shared automatically. Delivery times, family sharing, and public story links always require an explicit choice.",
    )
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(titles[page], Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(messages[page], Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { if (page < 2) page++ else complete() }, Modifier.fillMaxWidth().padding(top = 24.dp)) { Text(if (page < 2) "Continue" else "Get started") }
    }
}

@Composable
private fun ProtectedLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingMessage("Checking your protected session…")
    }
}

@Composable
private fun AccountLifecycleGate(
    container: AppContainer,
    accessCredential: String,
    content: @Composable () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    var result by remember(accessCredential) { mutableStateOf<ApiResult<AccountLifecycleSignal>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var localPurgeFailed by remember { mutableStateOf(false) }
    LaunchedEffect(accessCredential, refresh) { result = container.accountLifecycleApi.signal(accessCredential) }
    when (val current = result) {
        null -> ProtectedLoadingScreen()
        is ApiResult.Unauthorized -> {
            // A valid active access credential returns a 200 lifecycle response.
            // A 401 is therefore an expired or unrecognised credential: rotate once,
            // never treat it as proof that ordinary product access is allowed.
            LaunchedEffect(accessCredential, refresh) {
                when (container.sessionManager.recoverFromUnauthorized(accessCredential)) {
                    TokenLease.Unavailable -> result = ApiResult.Offline()
                    else -> Unit // Rotation or sign-out updates authState and re-keys this gate.
                }
            }
            ProtectedLoadingScreen()
        }
        is ApiResult.Success -> when {
            current.value.requiresLocalPurge() -> {
                LaunchedEffect(current.value.state, refresh) { localPurgeFailed = !container.purgeAccountLocalData() }
                if (localPurgeFailed) LifecycleCheckFailure("Protected local account data could not be removed safely. Narratrace has kept the app locked.") { localPurgeFailed = false; refresh++ }
                else ProtectedLoadingScreen()
            }
            current.value.allowsOrdinaryAccess() -> {
                LaunchedEffect(accessCredential, current.value.state) {
                    container.offlineRepository.reconcile()
                    ProtectedUploadWorker.schedule(appContext)
                }
                content()
            }
            else -> RestrictedLifecycleScreen(container, current.value, accessCredential) { refresh++ }
        }
        is ApiResult.Failure -> LifecycleCheckFailure(current.message) { refresh++ }
    }
}

@Composable
private fun RestrictedLifecycleScreen(
    container: AppContainer,
    signal: AccountLifecycleSignal,
    accessCredential: String,
    retry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var closureStatus by remember(accessCredential) { mutableStateOf<ApiResult<io.narratrace.android.core.account.AccountClosureStatus>?>(null) }
    var reopening by remember { mutableStateOf(false) }
    var reopenMessage by remember { mutableStateOf<String?>(null) }
    if (signal.state == "closure_pending") LaunchedEffect(accessCredential) {
        closureStatus = container.accountLifecycleApi.closureStatus(accessCredential)
    }
    val label = when (signal.state) {
        "closure_pending" -> "Account closure in recovery"
        "suspended" -> "Account access suspended"
        "company_terminated" -> "Account access terminated"
        "legal_hold" -> "Account access restricted"
        else -> "Account access unavailable"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(
            if (signal.state == "closure_pending") "Your encrypted local drafts remain on this device during the 30-day recovery period. Ordinary account access and sharing are disabled. A routine account closure cannot skip this recovery period."
            else "Ordinary product access is disabled. Your account and privacy controls remain available.",
            Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        signal.recoveryEndsAt?.let { Text("Recovery ends: $it", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
        (closureStatus as? ApiResult.Success)?.value?.let { status ->
            status.daysLeft?.let { Text("$it day${if (it == 1) "" else "s"} remain to reopen this account.", Modifier.padding(top = 8.dp)) }
            status.supportRef?.let { Text("Support reference: $it", style = MaterialTheme.typography.bodySmall) }
            if (!status.expired && status.accountClosed) Button(
                onClick = {
                    reopening = true
                    reopenMessage = null
                    scope.launch {
                        when (val reopened = container.accountLifecycleApi.reopen(accessCredential)) {
                            is ApiResult.Success -> if (reopened.value.ok && reopened.value.requiresSignIn) {
                                container.sessionManager.signOut()
                            } else reopenMessage = "The account response could not be verified safely."
                            is ApiResult.Failure -> reopenMessage = reopened.message
                        }
                        reopening = false
                    }
                },
                enabled = !reopening,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(if (reopening) "Reopening…" else "Reopen account") }
        }
        if (signal.state == "closure_pending" && closureStatus == null) LoadingMessage("Checking the account reopening window…")
        if (signal.state == "closure_pending" && closureStatus is ApiResult.Failure) {
            Text((closureStatus as ApiResult.Failure).message, color = MaterialTheme.colorScheme.error)
        }
        reopenMessage?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error) }
        signal.appealStatus.takeIf { it in setOf("available", "submitted") }?.let { Text("Appeal status: ${it.replace('_', ' ')}", Modifier.padding(top = 8.dp)) }
        signal.safeAppealUrl()?.let { appealUrl ->
            Button(
                { context.startActivity(Intent(Intent.ACTION_VIEW, appealUrl.toUri())) },
                Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(if (signal.appealStatus == "submitted") "View appeal status" else "Submit an appeal") }
        }
        Button({ context.startActivity(Intent(Intent.ACTION_VIEW, ACCOUNT_CLOSURE_URL.toUri())) }, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Open account and recovery controls") }
        if (signal.state != "closure_pending") Button({ context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.narratrace.io/account#export".toUri())) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Export my data") }
        TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, TERMS_POLICY_URL.toUri())) }, Modifier.fillMaxWidth()) { Text("Terms of Service") }
        TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())) }, Modifier.fillMaxWidth()) { Text("Privacy and data-rights information") }
        TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, COOKIE_POLICY_URL.toUri())) }, Modifier.fillMaxWidth()) { Text("Cookie Policy") }
        TextButton(retry, Modifier.fillMaxWidth()) { Text("Check account status again") }
    }
}

@Composable
private fun LifecycleCheckFailure(message: String, retry: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Account status unavailable", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(message, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
        Button(retry, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Try again") }
        TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.narratrace.io/account".toUri())) }, Modifier.fillMaxWidth()) { Text("Account and privacy controls") }
    }
}

internal fun requiredLegalAcceptanceComplete(value: io.narratrace.android.core.media.LegalAcceptance): Boolean =
    value.termsAccepted && value.privacyAcknowledged && value.contentRightsAttested

@Composable
private fun RequiredLegalGate(container: AppContainer, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.media.LegalAcceptance>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { result = container.mediaRepository.legal() }
    val accepted = (result as? FeatureResult.Success)?.value
    if (accepted != null && requiredLegalAcceptanceComplete(accepted)) { content(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(LEGAL_REVIEW_HEADING, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        item { Text("$LEGAL_CHANGE_SUMMARY $NIA_DEFINITION Review the current documents before continuing. Privacy acknowledgement confirms receipt of the notice; it is not consent to optional AI processing.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when (val current = result) {
            null -> item { LoadingMessage("Checking current document versions…") }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to review the current documents.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(current.message, color = MaterialTheme.colorScheme.error); Button({ refresh++ }) { Text("Try again") } }
            is FeatureResult.Success -> {
                val legal = current.value
                if (!legal.termsAccepted) item { LegalChoiceCard("Terms of Service", "Read the complete Terms before accepting.", TERMS_POLICY_URL, "Accept current Terms", busy) {
                    busy = true; scope.launch { result = container.mediaRepository.acceptTerms(); busy = false }
                } }
                if (!legal.privacyAcknowledged) item { LegalChoiceCard("Privacy Policy", "Acknowledge that you received and reviewed the current Privacy Policy. This is not consent to optional processing.", PRIVACY_POLICY_URL, "Acknowledge Privacy Policy", busy) {
                    busy = true; scope.launch { result = container.mediaRepository.acknowledgePrivacy(); busy = false }
                } }
                if (!legal.contentRightsAttested) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Content rights", style = MaterialTheme.typography.titleMedium)
                    Text("I represent that I own, or have all necessary licenses, permissions, privacy and publicity rights, and recording consent for content I upload or record in Narratrace, including content involving other people or minors.")
                    Button({ busy = true; scope.launch { result = container.mediaRepository.attestContentRights(); busy = false } }, enabled = !busy) { Text("Attest content rights") }
                } } }
            }
        }
        item { Text("You can still use account, export, cancellation, deletion, and privacy controls without making these choices.", style = MaterialTheme.typography.bodySmall) }
        item { TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, COOKIE_POLICY_URL.toUri())) }, Modifier.fillMaxWidth()) { Text("Read Cookie Policy") } }
        item { TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.narratrace.io/account".toUri())) }, Modifier.fillMaxWidth()) { Text("Account and privacy controls") } }
    }
}

@Composable
private fun LegalChoiceCard(title: String, explanation: String, url: String, action: String, busy: Boolean, choose: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(explanation)
        TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) { Text("Read $title") }
        Button(choose, enabled = !busy) { Text(action) }
    } }
}

@Composable
private fun SignInScreen(container: AppContainer, returning: Boolean) {
    if (container.hostedAuthenticationAvailable()) {
        HostedSignInScreen(container, returning)
    } else {
        LegacySignInScreen(container, returning)
    }
}

/**
 * Protocol-v1 sign-in is intentionally only a launcher and return-status surface.
 * Invitation, provider authorization, verification, MFA, legal acceptance,
 * onboarding-journey selection, and routing stay in the hosted Narratrace flow.
 */
@Composable
private fun HostedSignInScreen(container: AppContainer, returning: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val event by container.hostedAuthenticationCoordinator.event.collectAsStateWithLifecycle()
    var starting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            if (returning) "Welcome back" else "Every family has stories worth keeping.",
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            if (returning) "Continue securely on the Narratrace website to unlock this device."
            else "Continue securely on the Narratrace website. Invitation, account verification, and setup are completed there.",
            Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Narratrace returns only a short-lived sign-in code to this app. Your invitation, email, and account details stay out of the return link.",
            Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                starting = true
                container.hostedAuthenticationCoordinator.resetEvent()
                scope.launch {
                    when (val result = container.hostedAuthenticationCoordinator.start()) {
                        is HostedAuthStartResult.OpenBrowser -> CustomTabsIntent.Builder()
                            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, result.authorizeUrl.toUri())
                        is HostedAuthStartResult.Failed -> Unit
                    }
                    starting = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
            enabled = !starting && event !is HostedAuthEvent.Exchanging && container.isApiConfigured,
        ) {
            if (starting || event is HostedAuthEvent.Exchanging) {
                LoadingMessage(if (starting) "Preparing secure sign-in…" else "Finishing secure sign-in…")
            } else {
                Text(if (event is HostedAuthEvent.AwaitingBrowser) "Start sign-in again" else "Continue securely on the web")
            }
        }
        if (event is HostedAuthEvent.AwaitingBrowser) {
            Text(
                "Finish in the secure browser. If it was closed, start sign-in again.",
                Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        (event as? HostedAuthEvent.Failed)?.let { failure ->
            Text(
                failure.message,
                Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                color = MaterialTheme.colorScheme.error,
            )
            if (failure.supportReference.isNotBlank()) Text(
                "Support reference: ${failure.supportReference}",
                Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!container.isApiConfigured) Text(
            "This build is not connected to the Narratrace service.",
            Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            ADULT_ACCOUNT_NOTICE,
            Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Compatibility-only admission for runtime responses predating hosted protocol v1. */
@Composable
private fun LegacySignInScreen(container: AppContainer, returning: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inviteCode by remember { mutableStateOf("") }
    var mfaCode by remember { mutableStateOf("") }
    var requiresMfaEnrollment by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var supportReference by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = if (returning) "Welcome back" else "Every family has stories worth keeping.",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = if (returning) {
                "Sign in again to unlock your private Narratrace account on this device."
            } else {
                "Preserve voices, photos, videos, and written memories—privately and on your terms."
            },
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = ADULT_ACCOUNT_NOTICE,
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!requiresMfaEnrollment) {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { value ->
                    inviteCode = value.uppercase().filter { it.isDigit() || it in 'A'..'Z' || it == '-' }.take(32)
                    message = null
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                label = { Text("Your invitation code") },
                supportingText = { Text("Required every time you sign in.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
                enabled = !isSigningIn,
            )
            OutlinedTextField(
                value = mfaCode,
                onValueChange = { value ->
                    mfaCode = value.uppercase().filter { it.isDigit() || it in 'A'..'F' || it == '-' }.take(19)
                    message = null
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                label = { Text("Authenticator or recovery code (optional)") },
                supportingText = { Text("Leave blank unless you enabled authenticator protection.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
                enabled = !isSigningIn,
            )
            Text(
                text = "Use the same account as your phone. It must be the Google account that received this invitation.",
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (requiresMfaEnrollment) {
            Text(
                text = "Your Narratrace role requires authenticator setup before you can sign in. Complete setup securely on the Narratrace website, then return here and try again.",
                modifier = Modifier.padding(top = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://www.narratrace.io/auth/signin?callbackUrl=%2Fauth%2Fmfa".toUri(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
            ) { Text("Set up authenticator on the web") }
            TextButton(
                onClick = {
                    requiresMfaEnrollment = false
                    message = null
                    supportReference = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Back to sign in") }
        }
        if (!requiresMfaEnrollment) Button(
            onClick = {
                isSigningIn = true
                message = null
                supportReference = ""
                scope.launch {
                    val coordinator = container.authenticationCoordinator(context)
                    val result = coordinator.signIn(inviteCode, mfaCode)
                    when (result) {
                        SignInResult.Authenticated -> Unit
                        SignInResult.Cancelled -> Unit
                        is SignInResult.MfaEnrollmentRequired -> {
                            requiresMfaEnrollment = true
                            message = null
                            supportReference = result.supportReference
                            inviteCode = ""
                            mfaCode = ""
                        }
                        is SignInResult.Failed -> {
                            message = result.message
                            supportReference = result.supportReference
                        }
                    }
                    isSigningIn = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
            enabled = !isSigningIn && container.isApiConfigured && !requiresMfaEnrollment && inviteCode.isNotBlank(),
        ) {
            if (isSigningIn) {
                LoadingMessage("Signing in securely…")
            } else {
                Text("Continue with Google")
            }
        }
        if (!container.isApiConfigured) {
            Text(
                text = "This build is not connected to the Narratrace service.",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        message?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (supportReference.isNotBlank()) {
            Text(
                text = "Support reference: $supportReference",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private enum class CustomerTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Capture("Capture", Icons.Default.AddCircle),
    Library("Library", Icons.Default.PhotoLibrary),
    People("People", Icons.Default.People),
    More("More", Icons.Default.MoreHoriz),
}

@Composable
private fun AuthenticatedShell(
    container: AppContainer,
    onInteraction: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selected by remember { mutableStateOf(CustomerTab.Home) }
    var invite by remember { mutableStateOf(container.pendingInvite) }
    val scope = rememberCoroutineScope()
    invite?.let { pending -> AlertDialog(
        onDismissRequest = {}, title = { Text(if (pending.kind == "family") "Join this family?" else "Join this Circle?") },
        text = { Text("Accepting grants access only according to the invitation. It does not automatically share your existing content.") },
        confirmButton = { Button(onClick = { scope.launch { if (pending.kind == "family") container.familyRepository.decideFamily(pending.token, true) else container.familyRepository.decideCircle(pending.token, true); container.pendingInvite = null; invite = null } }) { Text("Accept invitation") } },
        dismissButton = { TextButton(onClick = { scope.launch { if (pending.kind == "family") container.familyRepository.decideFamily(pending.token, false) else container.familyRepository.decideCircle(pending.token, false); container.pendingInvite = null; invite = null } }) { Text("Decline") } },
    ) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                CustomerTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab; onInteraction() },
                        icon = { androidx.compose.material3.Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selected) {
            CustomerTab.Home -> CustomerHomeScreen(container = container, modifier = Modifier.padding(innerPadding))
            CustomerTab.Capture -> CustomerCaptureScreen(container = container, modifier = Modifier.padding(innerPadding), onInteraction = onInteraction)
            CustomerTab.Library -> CustomerLibraryScreen(container = container, modifier = Modifier.padding(innerPadding))
            CustomerTab.People -> CustomerPeopleScreen(container = container, modifier = Modifier.padding(innerPadding))
            CustomerTab.More -> CustomerMoreScreen(container = container, modifier = Modifier.padding(innerPadding), fallbackSignOut = onSignOut)
        }
    }
}

@Composable
private fun CustomerMoreScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    fallbackSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<AccountResult?>(null) }
    var sessions by remember { mutableStateOf<SecuritySessionsResult?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var pendingRevocation by remember { mutableStateOf<RevokeScope?>(null) }
    var revoking by remember { mutableStateOf(false) }
    var revocationFailure by remember { mutableStateOf<RevocationResult.Unavailable?>(null) }
    var deliveries by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.letters.ArtifactDeliveryList>?>(null) }
    var revokeDeliveryId by remember { mutableStateOf<String?>(null) }
    var familyOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var feedbackOpen by remember { mutableStateOf(false) }
    var permissionsOpen by remember { mutableStateOf(false) }
    var activityOpen by remember { mutableStateOf(false) }
    var resourcesOpen by remember { mutableStateOf(false) }
    var closureOpen by remember { mutableStateOf(false) }
    if (familyOpen) { FamilySharingScreen(container, modifier) { familyOpen = false }; return }
    if (settingsOpen) { ProfileSettingsScreen(container, modifier) { settingsOpen = false }; return }
    if (feedbackOpen) { FeedbackSupportScreen(container, modifier) { feedbackOpen = false }; return }
    if (permissionsOpen) { PrivacyPermissionsScreen(modifier) { permissionsOpen = false }; return }
    if (activityOpen) { ActivityScreen(container, modifier) { activityOpen = false }; return }
    if (resourcesOpen) { WebResourcesScreen(modifier) { resourcesOpen = false }; return }
    if (closureOpen) { AccountClosureScreen(container, modifier) { closureOpen = false }; return }
    LaunchedEffect(refreshKey) {
        account = container.customerRepository.loadAccount()
        sessions = container.securityRepository.loadSessions()
        deliveries = container.lettersRepository.deliveries()
    }
    revokeDeliveryId?.let { id -> AlertDialog(
        onDismissRequest = { revokeDeliveryId = null }, title = { Text("Revoke this delivery?") },
        text = { Text("The recipient will no longer be able to receive this artifact through this delivery.") },
        confirmButton = { Button(onClick = { revokeDeliveryId = null; scope.launch { container.lettersRepository.revokeDelivery(id); refreshKey++ } }) { Text("Revoke delivery") } },
        dismissButton = { TextButton(onClick = { revokeDeliveryId = null }) { Text("Keep delivery") } },
    ) }

    pendingRevocation?.let { revokeScope ->
        AlertDialog(
            onDismissRequest = { if (!revoking) pendingRevocation = null },
            title = { Text(if (revokeScope == RevokeScope.AllDevices) "Sign out everywhere?" else "Sign out on this device?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (revokeScope == RevokeScope.AllDevices) {
                            "Every Narratrace mobile session will be revoked. Each device must sign in again."
                        } else {
                            "This device's protected session will be revoked and removed."
                        },
                    )
                    if (revoking) {
                        Text(
                            "Signing out securely. Please wait.",
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        revoking = true
                        revocationFailure = null
                        scope.launch {
                            when (val result = container.securityRepository.revoke(revokeScope)) {
                                RevocationResult.Revoked, RevocationResult.AuthenticationRequired -> fallbackSignOut()
                                is RevocationResult.Unavailable -> revocationFailure = result
                            }
                            revoking = false
                            pendingRevocation = null
                        }
                    },
                    enabled = !revoking,
                ) {
                    Text(
                        if (revoking) "Signing out…"
                        else if (revokeScope == RevokeScope.AllDevices) "Sign out everywhere"
                        else "Sign out",
                    )
                }
            },
            dismissButton = { TextButton(onClick = { pendingRevocation = null }, enabled = !revoking) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Account and security", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        when (val current = account) {
            null -> item { LoadingMessage("Loading account and security details…") }
            AccountResult.AuthenticationRequired -> item { Text("Sign in again to verify account access.", color = MaterialTheme.colorScheme.error) }
            is AccountResult.Unavailable -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }
            is AccountResult.Success -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Plan", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${current.value.plan.planLabel()} · ${current.value.status.statusLabel()}",
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        current.value.billingCycle?.let { Text("Billing: ${it.replace('_', ' ').replaceFirstChar(Char::uppercase)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (current.value.hasGuidedInterviewOnlyAccess()) {
                            Text("Your account includes one guided interview. Other capture choices are not available in this app.", style = MaterialTheme.typography.bodySmall)
                        } else if (current.value.experiment?.resourceState == "completed" && !current.value.hasAccess) {
                            Text("Your guided interview is complete. Additional capture choices are not available in this app.", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${current.value.storage.usedLabel} used · ${current.value.storage.availableLabel} available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (current.value.productionArchives.isNotEmpty()) {
                            Text("Allowance by storyteller", style = MaterialTheme.typography.titleSmall)
                            current.value.productionArchives.forEach { archive ->
                                Text(archive.subjectName, style = MaterialTheme.typography.labelLarge)
                                archive.audioSeconds?.let { Text("Voice: ${it.remaining.durationAllowanceLabel()} remaining", style = MaterialTheme.typography.bodySmall) }
                                archive.photographs?.let { Text("Photos: ${"%,d".format(it.remaining)} remaining", style = MaterialTheme.typography.bodySmall) }
                                archive.videoSeconds?.let { allowance ->
                                    Text(
                                        text = if (current.value.capabilities.captureVideo) {
                                            "Video: ${allowance.remaining.durationAllowanceLabel()} remaining"
                                        } else {
                                            "Video: not included"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            val sharedAudio = current.value.productionPools.audioSeconds?.remaining ?: 0
                            val sharedVideo = current.value.productionPools.videoSeconds?.remaining ?: 0
                            if (sharedAudio > 0 || sharedVideo > 0) {
                                Text("Shared add-on capacity", style = MaterialTheme.typography.labelLarge)
                                if (sharedAudio > 0) Text("Voice: ${sharedAudio.durationAllowanceLabel()}", style = MaterialTheme.typography.bodySmall)
                                if (sharedVideo > 0) Text("Video: ${sharedVideo.durationAllowanceLabel()}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (current.value.deliveryContact?.status == "verified") {
                            Text("Delivery contact verified: ${current.value.deliveryContact.email}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Verify a durable email address on the secure account website before scheduling a future delivery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { Text("Active mobile sessions", style = MaterialTheme.typography.titleLarge) }
        when (val current = sessions) {
            null -> item { LoadingMessage("Checking active mobile sessions…") }
            SecuritySessionsResult.AuthenticationRequired -> item { Text("Sign in again to verify active sessions.", color = MaterialTheme.colorScheme.error) }
            is SecuritySessionsResult.Unavailable -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    if (current.supportReference.isNotBlank()) Text("Support reference: ${current.supportReference}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { account = null; sessions = null; refreshKey++ }) { Text("Try again") }
                }
            }
            is SecuritySessionsResult.Success -> {
                if (current.sessions.isEmpty()) item { Text("No active mobile sessions were returned.") }
                else items(current.sessions, key = { it.id }) { session ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (session.isCurrent) "This device" else session.platform.platformLabel(), style = MaterialTheme.typography.titleMedium)
                            Text("Narratrace ${session.appVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            session.osVersion?.let { Text("System $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
        item { Text("Delivery center", style = MaterialTheme.typography.titleLarge) }
        item { Text("For an external delivery, Narratrace emails a private review request that identifies you as the creator but includes no artifact content. The recipient can confirm or decline; declining revokes the delivery. If their confirmation is more than 12 months old when delivery is due, Narratrace requires confirmation again before access.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when (val current = deliveries) {
            null -> item { LoadingMessage("Loading private deliveries…") }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify deliveries.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> if (current.value.deliveries.isEmpty()) item { Text("No scheduled artifact deliveries.") }
            else items(current.value.deliveries, key = { "delivery:${it.id}" }) { delivery -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${delivery.artifactKind.replaceFirstChar(Char::uppercase)} for ${delivery.recipientName}", style = MaterialTheme.typography.titleMedium)
                Text(if (delivery.selfDelivery) "Delivery to you" else delivery.recipientEmail)
                Text(delivery.statusLabel(), style = MaterialTheme.typography.bodySmall)
                if (delivery.revokedAt == null && delivery.state in setOf("pending_verification", "scheduled", "delivered", "failed")) TextButton(onClick = { revokeDeliveryId = delivery.id }) { Text("Revoke delivery access") }
            } } }
        }
        item { Text("Family", style = MaterialTheme.typography.titleLarge) }
        item { Card(Modifier.fillMaxWidth().clickable { familyOpen = true }) { Column(Modifier.padding(16.dp)) {
            Text("Family sharing and Circles", style = MaterialTheme.typography.titleMedium)
            Text("Manage roles, invitations, Mosaic access, and explicitly shared Circle stories.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        item { Card(Modifier.fillMaxWidth().clickable { settingsOpen = true }) { Column(Modifier.padding(16.dp)) {
            Text("Profile, language, and notifications", style = MaterialTheme.typography.titleMedium)
            Text("Manage your profile, optional notifications, and app appearance.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        item { Card(Modifier.fillMaxWidth().clickable { activityOpen = true }) { Column(Modifier.padding(16.dp)) { Text("Activity and processing", style = MaterialTheme.typography.titleMedium); Text("Review preservation progress and safely retry optional processing.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Card(Modifier.fillMaxWidth().clickable { permissionsOpen = true }) { Column(Modifier.padding(16.dp)) { Text("Privacy and permissions", style = MaterialTheme.typography.titleMedium); Text("Review device access without triggering a permission prompt.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Card(Modifier.fillMaxWidth().clickable { feedbackOpen = true }) { Column(Modifier.padding(16.dp)) { Text("Feedback and support", style = MaterialTheme.typography.titleMedium); Text("Send feedback or an issue report with an optional screen capture.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        item { Card(Modifier.fillMaxWidth().clickable { resourcesOpen = true }) { Column(Modifier.padding(16.dp)) { Text("Keepsake books and downloadable resources", style = MaterialTheme.typography.titleMedium); Text("Open authenticated resources on the Narratrace website.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        if (container.latestSupportReference().isNotBlank()) item { TextButton(onClick = { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Narratrace support reference", container.latestSupportReference())) }, Modifier.fillMaxWidth()) { Text("Copy latest support reference") } }
        item { Text("Account data and closure", style = MaterialTheme.typography.titleLarge) }
        item { Card(Modifier.fillMaxWidth().clickable(role = Role.Button) {
            closureOpen = true
        }) { Column(Modifier.padding(16.dp)) {
            Text("Manage account closure", style = MaterialTheme.typography.titleMedium)
            Text("Review closure details, close this account, or open the secure website. Signing out does not delete your account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Closing immediately restricts account access and revokes active sessions, but a routine closure cannot skip the 30-day recovery period. After it ends, deletion is attempted across active Narratrace-controlled systems and tracked providers; provider failures are retried.", style = MaterialTheme.typography.bodySmall)
            Text("Cold-storage tiering is not currently active. A Vault, lapsed, or dormant classification does not itself move or delete files.", style = MaterialTheme.typography.bodySmall)
        } } }
        item { Text("Legal and privacy", style = MaterialTheme.typography.titleLarge) }
        item { TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, TERMS_POLICY_URL.toUri())) }, Modifier.fillMaxWidth()) { Text("Terms of Service") } }
        item { TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())) }, Modifier.fillMaxWidth()) { Text("Privacy Policy") } }
        item { TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, COOKIE_POLICY_URL.toUri())) }, Modifier.fillMaxWidth()) { Text("Cookie Policy") } }
        revocationFailure?.let { failure -> item {
            Text(
                failure.message,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                color = MaterialTheme.colorScheme.error,
            )
            if (failure.supportReference.isNotBlank()) Text("Support reference: ${failure.supportReference}", style = MaterialTheme.typography.bodySmall)
        } }
        item {
            Button(
                onClick = { pendingRevocation = RevokeScope.CurrentDevice },
                modifier = Modifier.fillMaxWidth(),
                enabled = !revoking,
            ) { Text("Sign out on this device") }
        }
        item {
            TextButton(
                onClick = { pendingRevocation = RevokeScope.AllDevices },
                modifier = Modifier.fillMaxWidth(),
                enabled = !revoking,
            ) { Text("Sign out everywhere") }
        }
    }
}

@Composable
private fun AccountClosureScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var credential by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<ApiResult<io.narratrace.android.core.account.AccountClosureStatus>?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var supportReference by remember { mutableStateOf("") }
    var awaitingWebReturn by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(refreshKey) {
        when (val lease = container.sessionManager.accessToken()) {
            is TokenLease.Valid -> {
                credential = lease.accessToken
                status = container.accountLifecycleApi.closureStatus(lease.accessToken)
            }
            else -> message = "Sign in again to review account closure."
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (shouldRefreshAccountAfterExternalManagement(awaitingWebReturn, event)) {
                awaitingWebReturn = false
                status = null
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (confirmClose) AlertDialog(
        onDismissRequest = { if (!closing) confirmClose = false },
        title = { Text("Close this Narratrace account?") },
        text = { Text("Online access and active sessions will be restricted immediately. You can reopen during the 30-day recovery period; permanent deletion begins only after that period ends.") },
        confirmButton = { Button(
            onClick = {
                val token = credential ?: return@Button
                closing = true
                message = null
                scope.launch {
                    when (val result = container.accountLifecycleApi.close(token)) {
                        is ApiResult.Success -> if (
                            result.value.ok && result.value.accountClosed &&
                            container.sessionManager.retainLifecycleCredentialAfterClosure(token)
                        ) {
                            supportReference = result.value.supportRef.orEmpty()
                            confirmClose = false
                            (context as? Activity)?.recreate()
                        } else message = "The closure response could not be verified safely."
                        is ApiResult.LegalAcceptanceRequired -> message = result.message
                        is ApiResult.Unauthorized -> message = "Sign in again before closing your account."
                        is ApiResult.Failure -> { message = result.message; supportReference = result.supportReference }
                    }
                    closing = false
                }
            },
            enabled = !closing && credential != null,
        ) { Text(if (closing) "Closing…" else "Close account") } },
        dismissButton = { TextButton({ confirmClose = false }, enabled = !closing) { Text("Keep account open") } },
    )

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, enabled = !closing) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to account") }
            Text("Account closure", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        } }
        when (val current = status) {
            null -> item { if (message == null) LoadingMessage("Checking account closure details…") }
            is ApiResult.Success -> {
                item { Text("Closing immediately restricts account access and revokes active sessions. A 30-day recovery period begins before permanent deletion is attempted.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (current.value.refundAmount > 0) item {
                    Text("Estimated prorated refund: ${formatMinorCurrency(current.value.refundAmount, current.value.currency)}", style = MaterialTheme.typography.titleMedium)
                }
                item { Text("For security, Narratrace requires a sign-in from the last 10 minutes before closing an account.", style = MaterialTheme.typography.bodySmall) }
                if (!current.value.accountClosed) item {
                    Button({ confirmClose = true }, enabled = !closing, modifier = Modifier.fillMaxWidth()) { Text("Close my account") }
                }
            }
            is ApiResult.Failure -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }
        }
        message?.let { current -> item {
            Text(current, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
            if (current.contains("Sign in again", ignoreCase = true)) Button(
                { container.sessionManager.signOut() }, Modifier.fillMaxWidth(),
            ) { Text("Sign in again") }
        } }
        if (supportReference.isNotBlank()) item { Text("Support reference: $supportReference", style = MaterialTheme.typography.bodySmall) }
        item { TextButton(
            onClick = {
                awaitingWebReturn = true
                context.startActivity(Intent(Intent.ACTION_VIEW, ACCOUNT_CLOSURE_URL.toUri()))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open account closure on the secure website") } }
    }
}

internal fun formatMinorCurrency(amount: Int, currency: String): String =
    "${currency.uppercase(java.util.Locale.US)} ${"%.2f".format(java.util.Locale.US, amount.coerceAtLeast(0) / 100.0)}"

private fun String.platformLabel(): String = when (lowercase()) {
    "ios" -> "iPhone or iPad"
    "android" -> "Android device"
    else -> "Mobile device"
}

@Composable
private fun ProfileSettingsScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.settings.ProfileResponse>?>(null) }
    var preferences by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.settings.PreferencesResponse>?>(null) }
    var mediaAiPreferences by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.settings.MediaAiPreferencesResponse>?>(null) }
    var legal by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.media.LegalAcceptance>?>(null) }
    var name by remember { mutableStateOf("") }; var birthYear by remember { mutableStateOf("") }; var language by remember { mutableStateOf("en") }
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    fun registerPush() {
        val messaging = container.firebaseMessaging()
        if (messaging == null) { message = "Push is not configured for this build."; return }
        busy = true
        messaging.token.addOnSuccessListener { token -> scope.launch { val result = container.settingsRepository.registerPush(token, Build.VERSION.RELEASE.take(40)); message = if (result is FeatureResult.Success) "Push notifications enabled for this installation." else "Push registration could not be verified."; busy = false } }
            .addOnFailureListener { message = "Push registration is unavailable."; busy = false }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) registerPush() else message = "Notification permission was not granted." }
    LaunchedEffect(Unit) { profile = container.settingsRepository.profile(); preferences = container.settingsRepository.preferences(); mediaAiPreferences = container.settingsRepository.mediaAiPreferences(); legal = container.mediaRepository.legal(); (profile as? FeatureResult.Success)?.value?.profile?.let { name = it.displayName; birthYear = it.birthYear?.toString().orEmpty(); language = it.preferredLanguage } }
    val latestBirthYear = java.time.Year.now().value - 5
    val birthYearValid = birthYear.isEmpty() || (birthYear.length == 4 && birthYear.toIntOrNull()?.let { it in 1900..latestBirthYear } == true)
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to account") }; Text("Profile and preferences", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) } }
        item { Text("Support and feedback", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
        item { Text("Use Feedback & support to report an issue or tell us how Narratrace can serve you better.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true) }
        item { OutlinedTextField(birthYear, { birthYear = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Birth year (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
        item { Button(onClick = { language = if (language == "en") "hi" else "en" }, Modifier.fillMaxWidth()) { Text("Language: ${if (language == "hi") "हिन्दी" else "English"}") } }
        item { Button(onClick = { busy = true; scope.launch { val saved = container.settingsRepository.updateProfile(name, birthYear.toIntOrNull(), language); message = if (saved is FeatureResult.Success) "Profile saved." else (saved as? FeatureResult.Unavailable)?.message; busy = false } }, enabled = !busy && name.trim().isNotEmpty() && birthYearValid, modifier = Modifier.fillMaxWidth()) { Text("Save profile") } }
        item { Text("App appearance", style = MaterialTheme.typography.titleLarge) }
        items(NarratraceAppearance.entries, key = { it.name }) { appearance ->
            val chosen = container.appearanceStore.load() == appearance
            Button(
                onClick = { if (container.appearanceStore.save(appearance)) (context as? Activity)?.recreate() },
                modifier = Modifier.fillMaxWidth().semantics { selected = chosen },
            ) { Text(appearance.displayName + if (chosen) " ✓" else "") }
        }
        item { Text(MEDIA_INSIGHTS_HEADING, style = MaterialTheme.typography.titleLarge) }
        item { Text("Photo and video insights are off by default. Enable each purpose separately only if you want future media sent for that AI analysis. Turning either off keeps the media usable.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        (mediaAiPreferences as? FeatureResult.Success)?.value?.preferences?.let { prefs ->
            val choices = listOf("photo_ai_insights_enabled" to ("Photo insights" to prefs.photoAiInsightsEnabled), "video_ai_insights_enabled" to ("Video insights" to prefs.videoAiInsightsEnabled))
            items(choices, key = { it.first }) { choice -> Button(onClick = { busy = true; scope.launch { mediaAiPreferences = container.settingsRepository.updateMediaAiPreference(choice.first, !choice.second.second); busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(choice.second.first + if (choice.second.second) " ✓" else "") } }
        }
        item { Text("Sensitive story information", style = MaterialTheme.typography.titleMedium) }
        item { Text("This separate optional consent allows Nia to process story details that may reveal sensitive information. Withdrawing it stops future AI interview processing; preserved content remains available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(onClick = { busy = true; scope.launch {
            val current = (legal as? FeatureResult.Success)?.value
            val changed = if (current?.specialCategoryConsent == true) container.mediaRepository.withdrawSpecialCategoryConsent() else container.mediaRepository.grantSpecialCategoryConsent()
            legal = changed
            message = if (changed is FeatureResult.Success) if (changed.value.specialCategoryConsent) "Sensitive-story consent enabled." else "Sensitive-story consent withdrawn." else "Consent choice could not be updated."
            busy = false
        } }, enabled = !busy && legal is FeatureResult.Success, modifier = Modifier.fillMaxWidth()) {
            Text(if ((legal as? FeatureResult.Success)?.value?.specialCategoryConsent == true) "Withdraw sensitive-story consent" else "Enable sensitive-story consent")
        } }
        item { Text("Notification preferences", style = MaterialTheme.typography.titleLarge) }
        (preferences as? FeatureResult.Success)?.value?.preferences?.let { prefs ->
            val choices = listOf("processing_ready" to ("Processing ready" to prefs.processingReady), "invitations" to ("Invitations" to prefs.invitations), "letters" to ("Letters" to prefs.letters), "trial_and_billing" to ("Plan and billing" to prefs.trialAndBilling), "product_guidance" to ("Product guidance" to prefs.productGuidance))
            items(choices, key = { it.first }) { choice -> Button(onClick = { busy = true; scope.launch { preferences = container.settingsRepository.updatePreference(choice.first, !choice.second.second); busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(choice.second.first + if (choice.second.second) " ✓" else "") } }
        }
        item { Text("Notifications never include Memory, Letter, interview, or family content. In-app Activity remains authoritative.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(onClick = { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else registerPush() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Enable push notifications") } }
        item { Button(onClick = { busy = true; scope.launch { container.settingsRepository.disablePush(Build.VERSION.RELEASE.take(40)); message = "Push notifications disabled for this installation."; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Disable push notifications") } }
        message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
private fun FamilySharingScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    var family by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.family.FamilySummary>?>(null) }
    var circles by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.family.CircleList>?>(null) }
    var refresh by remember { mutableStateOf(0) }; var selectedCircle by remember { mutableStateOf<io.narratrace.android.core.family.Circle?>(null) }
    var familyName by remember { mutableStateOf("") }; var inviteEmail by remember { mutableStateOf("") }; var inviteRole by remember { mutableStateOf("viewer") }
    var circleName by remember { mutableStateOf("") }; var circleDescription by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope(); LaunchedEffect(refresh) { family = container.familyRepository.family(); circles = container.familyRepository.circles() }
    if (selectedCircle != null) { CircleDetailScreen(container, selectedCircle!!, modifier) { selectedCircle = null; refresh++ }; return }
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to account") }; Text("Family sharing", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) } }
        item { Text("Joining a family changes what can appear in Mosaic, but nothing is shared automatically. Each Memory and Circle sharing choice remains explicit.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when (val loaded = family) {
            null -> item { LoadingMessage("Loading family access…") }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify family access.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> if (loaded.value.family == null) {
                item { OutlinedTextField(familyName, { familyName = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Family name") }, singleLine = true) }
                item { Button(onClick = { busy = true; scope.launch { val made = container.familyRepository.createFamily(familyName); message = if (made is FeatureResult.Success) "Family created. No Memories were shared." else (made as? FeatureResult.Unavailable)?.message; busy = false; refresh++ } }, enabled = !busy && familyName.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Create family") } }
            } else {
                val own = loaded.value.family
                item { Text(own.name ?: "Your family", style = MaterialTheme.typography.titleLarge); Text("Your role: ${own.myRole}", style = MaterialTheme.typography.bodySmall) }
                items(loaded.value.members, key = { it.id }) { member -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (member.isCurrentUser) "You" else member.email, style = MaterialTheme.typography.titleMedium); Text("${member.role} · ${member.status}")
                    if (own.myRole == "owner" && !member.isCurrentUser) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { scope.launch { container.familyRepository.update(member.email, if (member.role == "viewer") "editor" else "viewer"); refresh++ } }) { Text(if (member.role == "viewer") "Make editor" else "Make viewer") }
                        TextButton(onClick = { scope.launch { container.familyRepository.remove(member.email); refresh++ } }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    } }
                } } }
                if (own.myRole == "owner") {
                    item { OutlinedTextField(inviteEmail, { inviteEmail = it.take(254) }, Modifier.fillMaxWidth(), label = { Text("Member sign-in email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)) }
                    item { Button(onClick = { inviteRole = if (inviteRole == "viewer") "editor" else "viewer" }, Modifier.fillMaxWidth()) { Text("Role: ${inviteRole.replaceFirstChar(Char::uppercase)}") } }
                    item { Button(onClick = { busy = true; scope.launch { val sent = container.familyRepository.invite(inviteEmail, inviteRole); message = if (sent is FeatureResult.Success && sent.value.invitation.delivered) "Invitation sent." else if (sent is FeatureResult.Success) "Invitation recorded, but email delivery is pending." else (sent as? FeatureResult.Unavailable)?.message; busy = false; refresh++ } }, enabled = !busy && inviteEmail.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Invite family member") } }
                }
            }
        }
        message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
        item { Text("Family Circles", style = MaterialTheme.typography.titleLarge) }
        item { Text("A Circle sees only completed interviews you explicitly select and Letters delivered to that Circle.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(circleName, { circleName = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("New Circle name") }, singleLine = true) }
        item { OutlinedTextField(circleDescription, { circleDescription = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Description (optional)") }) }
        item { Button(onClick = { busy = true; scope.launch { val made = container.familyRepository.createCircle(circleName, circleDescription); if (made is FeatureResult.Success) { circleName = ""; circleDescription = ""; message = "Circle created. Nothing was shared." } else message = (made as? FeatureResult.Unavailable)?.message; busy = false; refresh++ } }, enabled = !busy && circleName.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Create Circle") } }
        when (val loaded = circles) {
            is FeatureResult.Success -> items(loaded.value.circles, key = { it.id }) { circle -> Card(Modifier.fillMaxWidth().clickable { selectedCircle = circle }) { Column(Modifier.padding(16.dp)) { Text(circle.name, style = MaterialTheme.typography.titleMedium); Text(circle.role.replaceFirstChar(Char::uppercase)); circle.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error) }
            else -> Unit
        }
    }
}

@Composable
private fun CircleDetailScreen(container: AppContainer, circle: io.narratrace.android.core.family.Circle, modifier: Modifier, close: () -> Unit) {
    var detail by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.family.CircleDetail>?>(null) }
    var interviews by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.media.InterviewList>?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }; var email by remember { mutableStateOf("") }; var displayName by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }; var busy by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) { detail = container.familyRepository.circle(circle.id); interviews = container.mediaRepository.interviews(); (detail as? FeatureResult.Success)?.let { selected = it.value.sharedInterviewIds.toSet() } }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this Circle?") }, text = { Text("Circle access and invitations will be removed. Your original interviews and Letters stay in your private account.") }, confirmButton = { Button(onClick = { confirmDelete = false; scope.launch { if (container.familyRepository.deleteCircle(circle.id) is FeatureResult.Success) close() } }) { Text("Delete Circle") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep Circle") } })
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to family sharing") }; Text(circle.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) } }
        when (val loaded = detail) {
            null -> item { LoadingMessage("Opening this private Circle…") }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error) }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify this Circle.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> {
                item { Text("Members", style = MaterialTheme.typography.titleLarge) }
                items(loaded.value.members, key = { it.id }) { member -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(member.displayName ?: member.memberEmail.ifBlank { "Circle member" }); Text(member.status, style = MaterialTheme.typography.bodySmall); if (circle.role == "owner") TextButton(onClick = { scope.launch { container.familyRepository.circleAction(circle.id, "remove_member", member.memberEmail); refresh++ } }) { Text("Remove", color = MaterialTheme.colorScheme.error) } } } }
                if (circle.role == "owner") {
                    item { OutlinedTextField(email, { email = it.take(254) }, Modifier.fillMaxWidth(), label = { Text("Invite email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true) }
                    item { OutlinedTextField(displayName, { displayName = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Display name (optional)") }, singleLine = true) }
                    item { Button(onClick = { busy = true; scope.launch { container.familyRepository.circleAction(circle.id, "invite", email, displayName); busy = false; refresh++ } }, enabled = !busy && email.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Invite to Circle") } }
                    item { Text("Explicitly shared completed interviews", style = MaterialTheme.typography.titleLarge) }
                    (interviews as? FeatureResult.Success)?.value?.interviews?.filter { it.status == "complete" }?.let { values -> items(values, key = { "share:${it.id}" }) { interview ->
                        Card(Modifier.fillMaxWidth().clickable { selected = if (interview.id in selected) selected - interview.id else selected + interview.id }) { Column(Modifier.padding(12.dp)) { Text(interview.subjectName); Text(if (interview.id in selected) "Selected ✓" else "Private", style = MaterialTheme.typography.bodySmall) } }
                    } }
                    item { Button(onClick = { busy = true; scope.launch { container.familyRepository.circleAction(circle.id, "share", ids = selected.toList()); busy = false; refresh++ } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save Circle sharing") } }
                }
                item { Text("Shared Mosaic stories", style = MaterialTheme.typography.titleLarge) }
                if (loaded.value.sharedMemories.isEmpty()) item { Text("No interviews have been explicitly shared.") } else items(loaded.value.sharedMemories, key = { it.id }) { memory -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(memory.subjectName, style = MaterialTheme.typography.titleMedium); memory.narrative?.let { Text(it) } } } }
                item { Text("Delivered Letters", style = MaterialTheme.typography.titleLarge) }
                if (loaded.value.deliveredLetters.isEmpty()) item { Text("No Letters have been delivered to this Circle.") } else items(loaded.value.deliveredLetters, key = { it.id }) { letter -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(letter.subject, style = MaterialTheme.typography.titleMedium); Text(letter.body) } } }
                if (circle.role == "owner") item { TextButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth()) { Text("Delete Circle", color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
private fun OfflineCaptureScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    val context = LocalContext.current
    var recordingAudio by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    if (recordingAudio) {
        AudioCaptureScreen(container, modifier, null, null, allowUpload = false) { recordingAudio = false }
        return
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (mime !in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) {
                message = "Choose a supported photo format."
            } else {
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBounded(io.narratrace.android.core.media.ProtectedMediaQueue.MAX_BYTES) } }.getOrNull()
                val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/heic" -> "heic"; "image/heif" -> "heif"; else -> "jpg" }
                val queued = bytes?.let { container.mediaRepository.queue.enqueue(it, PendingMediaKind.Photo, "narratrace-${UUID.randomUUID()}.$extension", mime) }
                message = if (queued == null) "The photo could not be encrypted safely. The original was not uploaded."
                else "Photo encrypted on this device. It has not been uploaded."
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (mime !in setOf("video/mp4", "video/quicktime")) {
                message = "Choose an MP4 or QuickTime video."
            } else {
                val queued = runCatching { context.contentResolver.openInputStream(uri)?.use {
                    container.mediaRepository.queue.enqueueVideoStream(
                        it, PendingMediaKind.StandaloneVideo,
                        "narratrace-${UUID.randomUUID()}.${if (mime == "video/quicktime") "mov" else "mp4"}", mime,
                    )
                } }.getOrNull()
                message = if (queued == null) "This video could not be encrypted safely or exceeds the 2 GB limit."
                else "Video encrypted on this device. It has not been uploaded."
            }
        }
    }
    BackHandler(onBack = close)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to service status") }
            Text("Private offline capture", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        } }
        item { Text("Captures are encrypted on this device. Uploads, sharing, quota decisions, and other online actions remain paused.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button({ recordingAudio = true }, Modifier.fillMaxWidth()) { Text("Record audio") } }
        item { Button({ photoPicker.launch("image/*") }, Modifier.fillMaxWidth()) { Text("Choose a photo") } }
        item { Button({ videoPicker.launch("video/*") }, Modifier.fillMaxWidth()) { Text("Choose a video") } }
        message?.let { current -> item { Text(current, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        val waiting = container.mediaRepository.queue.items().size
        if (waiting > 0) item { Text("$waiting encrypted capture${if (waiting == 1) "" else "s"} waiting on this device.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CustomerCaptureScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit,
) {
    var accountResult by remember { mutableStateOf<AccountResult?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var writing by remember { mutableStateOf(false) }
    var recordingAudio by remember { mutableStateOf(false) }
    var interviews by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    var letters by remember { mutableStateOf(false) }
    var selectedArchiveId by remember { mutableStateOf<String?>(null) }
    var choosingArchive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val captureScope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) captureScope.launch {
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (mime !in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) {
                photoMessage = "Choose a supported photo format."
                return@launch
            }
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBounded(io.narratrace.android.core.media.ProtectedMediaQueue.MAX_BYTES) } }.getOrNull()
            val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/heic" -> "heic"; "image/heif" -> "heif"; else -> "jpg" }
            val queued = bytes?.let { container.mediaRepository.queue.enqueue(
                it, PendingMediaKind.Photo, "narratrace-${UUID.randomUUID()}.$extension", mime,
                archiveEntitlementId = selectedArchiveId,
            ) }
            if (queued == null) photoMessage = "The photo could not be encrypted safely. The original was not uploaded."
            else photoMessage = uploadResultMessage(container, "Photo", container.mediaRepository.reconcile())
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) captureScope.launch {
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (mime !in setOf("video/mp4", "video/quicktime")) { photoMessage = "Choose an MP4 or QuickTime video."; return@launch }
            val queued = runCatching { context.contentResolver.openInputStream(uri)?.use { container.mediaRepository.queue.enqueueVideoStream(
                it, PendingMediaKind.StandaloneVideo,
                "narratrace-${UUID.randomUUID()}.${if (mime == "video/quicktime") "mov" else "mp4"}", mime,
                archiveEntitlementId = selectedArchiveId,
            ) } }.getOrNull()
            if (queued == null) photoMessage = "This video could not be encrypted safely or exceeds the 2 GB limit."
            else photoMessage = uploadResultMessage(container, "Video", container.mediaRepository.reconcile())
        }
    }
    LaunchedEffect(refreshKey) { accountResult = container.customerRepository.loadAccount() }
    val productionArchives = (accountResult as? AccountResult.Success)?.value?.productionArchives.orEmpty()
    LaunchedEffect(productionArchives.map(ProductionArchive::id)) {
        selectedArchiveId = when {
            productionArchives.size == 1 -> productionArchives.single().id
            productionArchives.none { it.id == selectedArchiveId } -> null
            else -> selectedArchiveId
        }
        selectedArchiveId?.let(container.mediaRepository.queue::assignArchiveToUnscopedStandalone)
    }

    if (choosingArchive) AlertDialog(
        onDismissRequest = { choosingArchive = false },
        title = { Text("Choose a storyteller") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(productionArchives, key = ProductionArchive::id) { archive ->
                    TextButton(
                        onClick = {
                            selectedArchiveId = archive.id
                            container.mediaRepository.queue.assignArchiveToUnscopedStandalone(archive.id)
                            photoMessage = "Waiting standalone captures will be preserved for ${archive.subjectName}."
                            choosingArchive = false
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(archive.subjectName) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingArchive = false }) { Text("Cancel") } },
    )

    if (writing) {
        WrittenMemoryComposer(
            container = container,
            modifier = modifier,
            onInteraction = onInteraction,
            close = { writing = false },
        )
        return
    }
    if (recordingAudio) {
        AudioCaptureScreen(container, modifier, null, null, archiveEntitlementId = selectedArchiveId) { recordingAudio = false }
        return
    }
    if (interviews) {
        GuidedInterviewsScreen(container, modifier) { interviews = false }
        return
    }
    if (letters) { LettersScreen(container, modifier) { letters = false }; return }

    when (val current = accountResult) {
        null -> LoadingSurface(modifier, "Capture", "Checking your available capture features…")
        AccountResult.AuthenticationRequired -> FailureSurface(modifier, "Sign in again to verify Capture access.")
        is AccountResult.Unavailable -> RetrySurface(
            modifier, "Capture unavailable", current.message, current.supportReference,
            retry = { accountResult = null; refreshKey++ },
        )
        is AccountResult.Success -> {
            val fullCaptureAccess = current.value.hasAccess
            val experienceFirstAccess = current.value.hasGuidedInterviewOnlyAccess()
            val interviewAccess = fullCaptureAccess || experienceFirstAccess
            val captureAvailability = productionCaptureAvailability(current.value, selectedArchiveId)
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Capture a Memory", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
                protectedUploadAttention(container.mediaRepository.queue.items())?.let { attention -> item {
                    Text(attention, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
                } }
                item { Text("Nothing is shared automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (current.value.productionArchives.isNotEmpty()) item {
                    ProductionCaptureTargetCard(
                        account = current.value,
                        selectedArchive = captureAvailability.selectedArchive,
                        choose = if (current.value.productionArchives.size > 1) { { choosingArchive = true } } else null,
                    )
                }
                if (experienceFirstAccess) item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Begin with one guided interview", style = MaterialTheme.typography.titleMedium)
                            Text("Your account includes one guided interview. Other capture choices are not available in this app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (current.value.experiment?.resourceState == "completed" && !fullCaptureAccess) item {
                    Text("Your guided interview is complete. Additional capture choices are not available in this app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable(enabled = fullCaptureAccess) {
                            onInteraction(); writing = true
                        },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Write a Memory", style = MaterialTheme.typography.titleMedium)
                            Text("Preserve text privately with retry-safe confirmation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Card(Modifier.fillMaxWidth().clickable(enabled = current.value.capabilities.createLetters) { onInteraction(); letters = true }) { Column(Modifier.padding(16.dp)) {
                    Text("Write a Letter", style = MaterialTheme.typography.titleMedium)
                    Text("Send now or preserve it privately for a future delivery time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } } }
                item {
                    Card(Modifier.fillMaxWidth().clickable(enabled = captureAvailability.audioEnabled) { onInteraction(); recordingAudio = true }) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Record audio", style = MaterialTheme.typography.titleMedium)
                            Text("Encrypted on this device until preservation is verified.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Card(Modifier.fillMaxWidth().clickable(enabled = captureAvailability.photoEnabled) { photoPicker.launch("image/*") }) { Column(Modifier.padding(16.dp)) {
                    Text("Add a photo", style = MaterialTheme.typography.titleMedium)
                    Text("The selected photo is encrypted before retry staging.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    photoMessage?.let {
                        Text(
                            it,
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (container.mediaRepository.latestReconciliationIssue() != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } } }
                item { Card(Modifier.fillMaxWidth().clickable(enabled = captureAvailability.videoEnabled) { videoPicker.launch("video/*") }) { Column(Modifier.padding(16.dp)) {
                    Text("Record video", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (!current.value.capabilities.captureVideo) "Video is not included in this plan."
                        else "Choose or record a clip for encrypted, resumable preservation.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } } }
                item {
                    Card(Modifier.fillMaxWidth().clickable(enabled = interviewAccess) { onInteraction(); interviews = true }) { Column(Modifier.padding(16.dp)) {
                        Text("Start a guided interview", style = MaterialTheme.typography.titleMedium)
                        Text("Use text or protected audio responses with Nia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
                if (!fullCaptureAccess && !experienceFirstAccess) item {
                    Text("Your current account can read the archive but cannot capture new Memories.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun LettersScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    var result by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.letters.LetterList>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var composing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LetterSummary?>(null) }
    LaunchedEffect(refresh) { result = container.lettersRepository.letters() }
    if (composing) { LetterComposerScreen(container, modifier) { composing = false; refresh++ }; return }
    if (selected != null) { LetterDetailScreen(container, selected!!, modifier) { selected = null; refresh++ }; return }
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Capture") }; Text("Letters", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) } }
        item { Text("Letters use the same private, revocable delivery workflow as other artifacts. External recipients confirm or decline without seeing Letter content; Narratrace requires confirmation again at delivery when the prior confirmation is more than 12 months old.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(onClick = { composing = true }, Modifier.fillMaxWidth()) { Text("Write a Letter") } }
        when (val loaded = result) {
            null -> item { LoadingMessage("Loading private Letters…") }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify Letters.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> if (loaded.value.letters.isEmpty()) item { Text("No Letters yet.") } else items(loaded.value.letters, key = { it.id }) { letter ->
                Card(Modifier.fillMaxWidth().clickable { selected = letter }) { Column(Modifier.padding(16.dp)) {
                    Text(letter.subject, style = MaterialTheme.typography.titleMedium)
                    Text("To ${letter.recipientName}")
                    Text(letterDeliveryStatus(letter.deliveryState, letter.recipientVerified, letter.delivered, letter.unlockAt), style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
    }
}

@Composable
private fun LetterComposerScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    var recipient by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }
    var selfDelivery by remember { mutableStateOf(false) }; var later by remember { mutableStateOf(false) }
    var localTime by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }; var key by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = !saving, onBack = close)
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Letters") }; Text("Write a Letter", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        Text("Nothing is shared before delivery. The recipient email identifies you as the creator but contains no Letter content. External recipients can confirm or decline, and Narratrace requires confirmation again at delivery if the prior confirmation is more than 12 months old.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(recipient, { recipient = it.take(100); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Recipient name") }, singleLine = true)
        Button(onClick = { selfDelivery = !selfDelivery; key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth()) { Text(if (selfDelivery) "Deliver to me ✓" else "Deliver to me") }
        if (!selfDelivery) OutlinedTextField(email, { email = it.take(254); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Recipient email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(subject, { subject = it.take(200); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Subject") }, singleLine = true)
        OutlinedTextField(body, { body = it.take(10_000); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Letter") }, minLines = 8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { later = false; key = UUID.randomUUID().toString() }, Modifier.weight(1f)) { Text(if (!later) "Send now ✓" else "Send now") }
            Button(onClick = { later = true; key = UUID.randomUUID().toString() }, Modifier.weight(1f)) { Text(if (later) "Deliver later ✓" else "Deliver later") }
        }
        if (later) OutlinedTextField(localTime, { localTime = it.take(16); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Local date and time") }, placeholder = { Text("2026-12-31T18:30") }, supportingText = { Text("Uses ${java.time.ZoneId.systemDefault().id}") }, singleLine = true)
        Button(onClick = { saving = true; message = null; scope.launch {
            val parsed = if (later) runCatching { LocalDateTime.parse(localTime) }.getOrNull() else null
            if (later && parsed == null) message = "Enter a valid local date and time."
            else when (val created = container.lettersRepository.create(recipient, email.takeIf { !selfDelivery }, selfDelivery, subject, body, if (later) DeliveryMode.LATER else DeliveryMode.NOW, parsed, key)) {
                is FeatureResult.Success -> { message = if (created.value.verificationPending) "Letter saved. Recipient verification is pending; no content was shared." else "Letter saved securely."; recipient = ""; email = ""; subject = ""; body = ""; key = UUID.randomUUID().toString() }
                is FeatureResult.Unavailable -> {
                    val saved = container.offlineRepository.store.save(io.narratrace.android.core.offline.OfflineLetterDraft(recipientName = recipient.trim(), subject = subject.trim(), body = body.trim(), unlockAt = parsed?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toString(), idempotencyKey = key))
                    message = if (saved) "Encrypted draft saved on this device. Narratrace will reconcile it after authorization is restored." else created.message
                }
                FeatureResult.AuthenticationRequired -> {
                    val saved = container.offlineRepository.store.save(io.narratrace.android.core.offline.OfflineLetterDraft(recipientName = recipient.trim(), subject = subject.trim(), body = body.trim(), unlockAt = parsed?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toString(), idempotencyKey = key))
                    message = if (saved) "Encrypted draft saved on this device. Sign in again to reconcile it." else "Sign in again before saving this Letter."
                }
            }; saving = false
        } }, enabled = !saving && recipient.trim().isNotEmpty() && subject.trim().isNotEmpty() && body.trim().isNotEmpty() && (selfDelivery || email.trim().isNotEmpty()), modifier = Modifier.fillMaxWidth()) { Text("Save Letter") }
        message?.let { Text(it, color = if (it.contains("saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LetterDetailScreen(container: AppContainer, summary: LetterSummary, modifier: Modifier, close: () -> Unit) {
    var result by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.letters.LetterDetailResponse>?>(null) }
    var email by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    LaunchedEffect(summary.id) { result = container.lettersRepository.letter(summary.id) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Cancel this Letter?") }, text = { Text("The Letter and its pending delivery will be permanently removed.") }, confirmButton = { Button(onClick = { confirmDelete = false; busy = true; scope.launch { if (container.lettersRepository.delete(summary.id) is FeatureResult.Success) close(); busy = false } }) { Text("Cancel Letter") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep Letter") } })
    BackHandler(onBack = close)
    when (val loaded = result) {
        null -> LoadingSurface(modifier, "Letter", "Opening private Letter…")
        FeatureResult.AuthenticationRequired -> FailureSurface(modifier, "Sign in again to verify this Letter.")
        is FeatureResult.Unavailable -> RetrySurface(modifier, "Letter unavailable", loaded.message, loaded.supportReference) { result = null }
        is FeatureResult.Success -> { val letter = loaded.value.letter; Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Letters") }; Text(letter.subject, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
            Text("To ${letter.recipientName}"); Text(letterDeliveryStatus(letter.deliveryState, letter.recipientVerified, letter.delivered, letter.unlockAt))
            if (letter.canDisplayContent()) Text(letter.body!!, style = MaterialTheme.typography.bodyLarge)
            else Text("Letter content remains private until authorized delivery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (letter.isOwner && !letter.sharedDeliveryManaged && letter.deliveryState == "pending_verification" && !letter.recipientVerified && letter.canCancel) {
                Button(onClick = { busy = true; scope.launch { val value = container.lettersRepository.manage(letter.id, "resend_verification"); message = if (value is FeatureResult.Success) "Verification sent. No Letter content was included." else "Verification could not be sent."; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Resend verification") }
                OutlinedTextField(email, { email = it.take(254) }, Modifier.fillMaxWidth(), label = { Text("Correct recipient email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Button(onClick = { busy = true; scope.launch { val value = container.lettersRepository.manage(letter.id, "update_recipient_email", email); message = if (value is FeatureResult.Success) "Recipient updated. The previous verification link was revoked." else "Recipient could not be updated."; busy = false } }, enabled = !busy && email.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Update recipient and resend") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (letter.isOwner && letter.canCancel) TextButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth()) { Text("Cancel Letter", color = MaterialTheme.colorScheme.error) }
        } }
    }
}

@Composable
private fun AudioCaptureScreen(
    container: AppContainer,
    modifier: Modifier,
    interviewId: String?,
    maxSeconds: Int?,
    archiveEntitlementId: String? = null,
    assisted: Boolean = false,
    question: String? = null,
    allowUpload: Boolean = true,
    close: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { SecureAudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(maxSeconds?.coerceAtMost(600) ?: 600) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder.start(maxSeconds?.coerceAtMost(600))) { recording = true; remainingSeconds = maxSeconds?.coerceAtMost(600) ?: 600; message = "Recording privately on this device…" }
        else message = "Microphone access is required to record audio."
    }
    DisposableEffect(Unit) { onDispose { recorder.discard() } }
    LaunchedEffect(recording, remainingSeconds) {
        if (recording && remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
        }
    }
    BackHandler(enabled = !busy && !recording) { recorder.discard(); close() }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!recording) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Capture") }
                Text(if (interviewId == null) "Record audio" else "Audio response", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
            }
        }
        if (assisted && !question.isNullOrBlank()) {
            Text(question, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        }
        if (!assisted) Text("The recording stays private and encrypted until Narratrace verifies durable preservation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                if (!recording) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        if (recorder.start(maxSeconds?.coerceAtMost(600))) { recording = true; remainingSeconds = maxSeconds?.coerceAtMost(600) ?: 600; message = "Recording privately on this device…" }
                        else message = "The recording could not start."
                    } else permission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    val bytes = recorder.stop(); recording = false
                    if (bytes == null || bytes.isEmpty()) message = "No recording was saved."
                    else {
                        busy = true
                        val queued = container.mediaRepository.queue.enqueue(
                            bytes, if (interviewId == null) PendingMediaKind.StandaloneAudio else PendingMediaKind.InterviewAudio,
                            "narratrace-${UUID.randomUUID()}.m4a", "audio/mp4", interviewId,
                            archiveEntitlementId = if (interviewId == null) archiveEntitlementId else null,
                        )
                        if (queued == null) { message = "The recording could not be encrypted safely."; busy = false }
                        else scope.launch {
                            val remaining = if (allowUpload) container.mediaRepository.reconcile() else container.mediaRepository.queue.items().size
                            message = if (allowUpload) uploadResultMessage(container, "Audio", remaining)
                            else "Protected on this device. Narratrace will retry when secure transfer is available."
                            busy = false
                        }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(if (assisted) 80.dp else 48.dp),
        ) { Text(if (recording) "Stop and preserve" else "Start recording") }
        if (recording) Text("%02d:%02d remaining".format(remainingSeconds / 60, remainingSeconds % 60), style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(
            it,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (container.mediaRepository.latestReconciliationIssue() != null || it.startsWith("The recording could not") || it.startsWith("No recording")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        ) }
        val waiting = container.mediaRepository.queue.items().size
        if (waiting > 0) {
            Text("$waiting protected upload${if (waiting == 1) "" else "s"} waiting.", style = MaterialTheme.typography.bodySmall)
            protectedUploadAttention(container.mediaRepository.queue.items())?.let {
                Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
            }
            if (allowUpload) TextButton(onClick = { busy = true; scope.launch {
                val remaining = container.mediaRepository.reconcile()
                message = uploadResultMessage(container, "Upload", remaining)
                busy = false
            } }, enabled = !busy) { Text("Retry protected uploads") }
        }
    }
}

@Composable
private fun GuidedInterviewsScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    var selected by remember { mutableStateOf<InterviewSummary?>(null) }
    if (selected != null) {
        InterviewDetailScreen(container, selected!!, modifier) { selected = null }
        return
    }
    var result by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.media.InterviewList>?>(null) }
    var legal by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.media.LegalAcceptance>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var accepting by remember { mutableStateOf(false) }
    var creationMessage by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) { result = container.mediaRepository.interviews(); legal = container.mediaRepository.legal() }
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Capture") }
            Text("Guided interviews", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        } }
        item { Text("$NIA_DEFINITION Nia uses your responses to suggest thoughtful follow-up questions. AI may make mistakes; review generated material before relying on or sharing it.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (legal is FeatureResult.Success && !(legal as FeatureResult.Success<io.narratrace.android.core.media.LegalAcceptance>).value.aiNoticeAcknowledged) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI notice", style = MaterialTheme.typography.titleMedium)
                Text("Nia uses your responses to generate follow-up questions, transcripts, summaries, insights, and requested narratives. AI can make mistakes; review results before relying on or sharing them.")
                val context = LocalContext.current
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "$TERMS_POLICY_URL#ai-generated-content".toUri())) }) { Text("Read the AI notice") }
                Button(onClick = { accepting = true; scope.launch { legal = container.mediaRepository.acknowledgeAiNotice(); accepting = false } }, enabled = !accepting) { Text("Acknowledge AI notice") }
            } } }
        }
        if (legal is FeatureResult.Success && !(legal as FeatureResult.Success<io.narratrace.android.core.media.LegalAcceptance>).value.specialCategoryConsent) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Optional sensitive-story consent", style = MaterialTheme.typography.titleMedium)
                Text("Guided interviews may reveal sensitive information about health, beliefs, identity, or family history. Allow this processing only if you want to use Nia for these interviews. This consent is separate and can be withdrawn in Profile and preferences.")
                Button(onClick = { accepting = true; scope.launch { legal = container.mediaRepository.grantSpecialCategoryConsent(); accepting = false } }, enabled = !accepting) { Text("Allow sensitive-story processing") }
            } } }
        }
        item { OutlinedTextField(name, { name = it.take(120); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Who is this story about?") }, singleLine = true) }
        item { OutlinedTextField(relation, { relation = it.take(120); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Relationship (optional)") }, singleLine = true) }
        item { Button(onClick = { creating = true; creationMessage = null; scope.launch {
            when (val made = container.mediaRepository.createInterview(name, relation, null, key)) {
                is FeatureResult.Success -> { name = ""; relation = ""; key = UUID.randomUUID().toString(); selected = made.value.interview }
                is FeatureResult.Unavailable -> creationMessage = made.message
                FeatureResult.AuthenticationRequired -> creationMessage = "Sign in again before starting an interview."
            }; creating = false
        } }, modifier = Modifier.fillMaxWidth(), enabled = !creating && name.trim().isNotEmpty() && (legal as? FeatureResult.Success)?.value?.let { it.aiNoticeAcknowledged && it.specialCategoryConsent } == true) { Text("Start interview") } }
        creationMessage?.let { message -> item {
            Text(
                message,
                Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                color = MaterialTheme.colorScheme.error,
            )
        } }
        item { Text("Your interviews", style = MaterialTheme.typography.titleLarge) }
        when (val loaded = result) {
            null -> item { LoadingMessage("Refreshing your interviews…") }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error) }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify interviews.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> if (loaded.value.interviews.isEmpty()) item { Text("No interviews yet.") }
            else items(loaded.value.interviews, key = { it.id }) { interview -> Card(Modifier.fillMaxWidth().clickable { selected = interview }) { Column(Modifier.padding(16.dp)) {
                Text(interview.subjectName, style = MaterialTheme.typography.titleMedium)
                Text("${interview.messageCount} responses · ${if (interview.status == "complete") "Complete" else "In progress"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } } }
        }
    }
}

@Composable
private fun InterviewDetailScreen(container: AppContainer, summary: InterviewSummary, modifier: Modifier, close: () -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<FeatureResult<InterviewDetail>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var response by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var sending by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    var capacity by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.media.RecordingCapacity>?>(null) }
    var insights by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.media.InterviewInsights>?>(null) }
    var shareToken by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmNarrativeAgreement by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf<String?>(null) }
    var videoMessage by remember { mutableStateOf<String?>(null) }
    val modePreferences = remember(context) { context.getSharedPreferences("interview-modes.v1", android.content.Context.MODE_PRIVATE) }
    var recordingMode by remember(summary.id) { mutableStateOf(modePreferences.getString(summary.id, null)) }
    val scope = rememberCoroutineScope()
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (mime !in setOf("video/mp4", "video/quicktime")) { videoMessage = "Choose an MP4 or QuickTime video."; return@launch }
            val item = runCatching { context.contentResolver.openInputStream(uri)?.use { container.mediaRepository.queue.enqueueVideoStream(
                it, PendingMediaKind.InterviewVideo, "interview-${UUID.randomUUID()}.${if (mime == "video/quicktime") "mov" else "mp4"}", mime, summary.id,
            ) } }.getOrNull()
            if (item == null) videoMessage = "This video could not be encrypted safely or exceeds the 2 GB limit."
            else { videoMessage = uploadResultMessage(container, "Video response", container.mediaRepository.reconcile()); refresh++ }
        }
    }
    LaunchedEffect(refresh) {
        result = container.mediaRepository.interview(summary.id)
        capacity = container.mediaRepository.capacity()
        insights = container.mediaRepository.insights(summary.id)
        shareToken = (container.mediaRepository.share(summary.id, "GET") as? FeatureResult.Success)?.value?.shareToken
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Delete this interview?") },
        text = { Text("The transcript, protected response media, and narrative will be permanently removed.") },
        confirmButton = { Button(onClick = { confirmDelete = false; sending = true; scope.launch {
            if (container.mediaRepository.deleteInterview(summary.id) is FeatureResult.Success) close()
            sending = false
        } }) { Text("Delete interview") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    if (confirmNarrativeAgreement) AlertDialog(
        onDismissRequest = { confirmNarrativeAgreement = false },
        title = { Text("Ask Nia to shape this story?") },
        text = { Text("Nia may organize and lightly polish only what was shared. Nia must not add facts, events, names, places, dates, dialogue, emotions, or conclusions. If there is not enough detail, you will be asked to add another response.") },
        confirmButton = { Button(onClick = { confirmNarrativeAgreement = false; sending = true; scope.launch { when (val outcome = container.mediaRepository.narrative(summary.id, true)) {
            is FeatureResult.Success -> { processingMessage = null; refresh++ }
            is FeatureResult.Unavailable -> processingMessage = outcome.message
            FeatureResult.AuthenticationRequired -> processingMessage = "Sign in again before processing this interview."
        }; sending = false } }) { Text("I agree — create faithful story") } },
        dismissButton = { TextButton(onClick = { confirmNarrativeAgreement = false }) { Text("Cancel") } },
    )
    processingMessage?.let { failure -> AlertDialog(
        onDismissRequest = { processingMessage = null },
        title = { Text("Processing unavailable") },
        text = { Text(failure, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) },
        confirmButton = { TextButton(onClick = { processingMessage = null }) { Text("Close") } },
    ) }
    if (audio) {
        val currentQuestion = (result as? FeatureResult.Success)?.value?.messages?.lastOrNull { it.role == "assistant" }?.content
        AudioCaptureScreen(
            container,
            modifier,
            summary.id,
            (capacity as? FeatureResult.Success)?.value?.audioMaxSeconds,
            assisted = recordingMode == "together",
            question = currentQuestion,
        ) { audio = false; refresh++ }
        return
    }
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to interviews") }
            Text(summary.subjectName, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        } }
        item { Text("This interview is private. Nia’s suggestions are AI-generated and should be reviewed.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when (val loaded = result) {
            null -> item { LoadingMessage("Loading protected interview details…") }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify this interview.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> {
                if (loaded.value.interview.status != "complete") item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How are you recording today?", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        Text("Choose the view that feels comfortable. You can switch between answers without losing a recording.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { recordingMode = "together"; modePreferences.edit().putString(summary.id, "together").apply() }, modifier = Modifier.weight(1f)) { Text("Someone else is here") }
                            Button(onClick = { recordingMode = "self"; modePreferences.edit().putString(summary.id, "self").apply() }, modifier = Modifier.weight(1f)) { Text("Recording myself") }
                        }
                    }
                }
                val visibleMessages = if (recordingMode == "together") {
                    listOfNotNull(loaded.value.messages.lastOrNull { it.role == "assistant" })
                } else loaded.value.messages
                items(visibleMessages, key = { it.id }) { message -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(if (recordingMode == "together") 20.dp else 12.dp)) {
                    Text(if (message.role == "assistant") "Nia" else "You", style = MaterialTheme.typography.labelMedium)
                    Text(message.content, style = if (recordingMode == "together") MaterialTheme.typography.headlineLarge else MaterialTheme.typography.bodyLarge)
                    if (message.hasMedia) Text("Protected ${message.mediaType ?: "media"} response", style = MaterialTheme.typography.bodySmall)
                } } }
                if (loaded.value.interview.status != "complete") {
                    if (recordingMode == "self") {
                        item { OutlinedTextField(response, { response = it.take(4000); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Your response") }, minLines = 3) }
                        item { Button(onClick = { sending = true; scope.launch {
                            when (val outcome = container.mediaRepository.respond(summary.id, response, key)) {
                                is FeatureResult.Success -> { response = ""; key = UUID.randomUUID().toString(); processingMessage = null; refresh++ }
                                is FeatureResult.Unavailable -> processingMessage = outcome.message
                                FeatureResult.AuthenticationRequired -> processingMessage = "Sign in again before sending this response."
                            }
                            sending = false
                        } }, enabled = !sending && response.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Send response") } }
                    }
                    if (recordingMode != null) item { Button(onClick = { audio = true }, enabled = (capacity as? FeatureResult.Success)?.value?.audioMaxSeconds?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth().height(if (recordingMode == "together") 80.dp else 48.dp)) { Text("Record audio response") } }
                    if (recordingMode == "self") item { Button(onClick = { videoPicker.launch("video/*") }, enabled = (capacity as? FeatureResult.Success)?.value?.videoMaxSeconds?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth()) { Text("Add video response") } }
                    videoMessage?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    item { when (val available = capacity) {
                        is FeatureResult.Success -> Text("${available.value.remainingLabel} remains · audio up to ${available.value.audioMaxSeconds / 60}m ${available.value.audioMaxSeconds % 60}s. Capacity is checked again before transfer.", style = MaterialTheme.typography.bodySmall)
                        else -> Text("Recording capacity is unavailable. Refresh before recording audio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } }
                    if (recordingMode == "self") item { Button(onClick = { sending = true; scope.launch { container.mediaRepository.status(summary.id, "complete"); sending = false; refresh++ } },
                        enabled = !sending && loaded.value.messages.any { it.role != "assistant" }, modifier = Modifier.fillMaxWidth()) { Text("Mark interview complete") } }
                    if (recordingMode == "self" && loaded.value.messages.none { it.role != "assistant" }) item { Text("Add at least one response before marking this interview complete.", style = MaterialTheme.typography.bodySmall) }
                } else item { Button(onClick = { sending = true; scope.launch { container.mediaRepository.status(summary.id, "active"); sending = false; refresh++ } }, modifier = Modifier.fillMaxWidth()) { Text("Reopen interview") } }
                loaded.value.narrative?.let { narrative -> item { Text("Narrative", style = MaterialTheme.typography.titleLarge) }; item { Text(narrative) } }
                if (recordingMode != "together") item { Text("Interview coverage", style = MaterialTheme.typography.titleLarge) }
                if (recordingMode != "together") item { when (val value = insights) {
                    is FeatureResult.Success -> Text(if (value.value.covered.isEmpty()) "Coverage grows as the interview develops." else value.value.covered.joinToString(" · "))
                    else -> Text("Coverage is temporarily unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                (insights as? FeatureResult.Success)?.value?.highlights?.let { highlights -> items(highlights, key = { it.id }) { highlight ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(highlight.title, style = MaterialTheme.typography.titleMedium); Text(highlight.excerpt) } }
                } }
                if (loaded.value.interview.status == "complete") {
                    if (loaded.value.narrative == null) item { Button(onClick = { confirmNarrativeAgreement = true }, enabled = !sending, modifier = Modifier.fillMaxWidth()) { Text("Create narrative") } }
                    else {
                        item { if (shareToken == null) Button(onClick = { sending = true; scope.launch { shareToken = (container.mediaRepository.share(summary.id, "POST") as? FeatureResult.Success)?.value?.shareToken; sending = false } }, modifier = Modifier.fillMaxWidth()) { Text("Create public story link") }
                        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val url = "https://www.narratrace.io/story/$shareToken"; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, "Share public story link")) }, modifier = Modifier.fillMaxWidth()) { Text("Share public story link") }
                            TextButton(onClick = { sending = true; scope.launch { container.mediaRepository.share(summary.id, "DELETE"); shareToken = null; sending = false } }, modifier = Modifier.fillMaxWidth()) { Text("Revoke public story link") }
                        } }
                        item { Text("A public link exposes only this completed narrative. The transcript stays private, and the link can be revoked.", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (recordingMode != "together") item { TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete interview", color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
private fun WrittenMemoryComposer(
    container: AppContainer,
    modifier: Modifier,
    onInteraction: () -> Unit,
    close: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var idempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString().lowercase()) }
    var saving by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<WrittenMemoryResult?>(null) }
    val canSave = title.trim().isNotEmpty() && title.trim().length <= 120 &&
        content.trim().isNotEmpty() && content.trim().length <= 50_000 && !saving

    BackHandler(enabled = !saving, onBack = close)
    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, enabled = !saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Capture")
            }
            Text("Write a Memory", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120); outcome = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Memory title") },
            supportingText = { Text("${title.length} of 120") },
            enabled = !saving,
            singleLine = true,
        )
        OutlinedTextField(
            value = content,
            onValueChange = { content = it.take(50_000); outcome = null },
            modifier = Modifier.fillMaxWidth().height(240.dp),
            label = { Text("Your Memory") },
            supportingText = { Text("${content.length} of 50,000") },
            enabled = !saving,
        )
        Text(
            "Your writing remains private and is sent only when you choose Save securely. The same request key is reused if confirmation is uncertain.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        when (val current = outcome) {
            is WrittenMemoryResult.Unavailable -> {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                if (current.supportReference.isNotBlank()) Text("Support reference: ${current.supportReference}", style = MaterialTheme.typography.bodySmall)
            }
            WrittenMemoryResult.AuthenticationRequired -> Text("Sign in again before preserving this Memory.", color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        Button(
            onClick = {
                saving = true
                outcome = null
                onInteraction()
                scope.launch {
                    val result = container.customerRepository.createWrittenMemory(title, content, idempotencyKey)
                    outcome = result
                    saving = false
                    if (result is WrittenMemoryResult.Success) {
                        title = ""; content = ""; idempotencyKey = UUID.randomUUID().toString().lowercase()
                    }
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (saving) LoadingMessage("Saving securely…") else Text("Save securely")
        }
        if (outcome is WrittenMemoryResult.Success) {
            Text("Memory preserved privately in Narratrace.", color = MaterialTheme.colorScheme.primary)
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun CustomerPeopleScreen(container: AppContainer, modifier: Modifier = Modifier) {
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var relationshipMap by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CustomerPeopleResult?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    if (relationshipMap) {
        BackHandler { relationshipMap = false }
        when (val current = result) {
            is CustomerPeopleResult.Success -> RelationshipMapScreen(
                people = current.value.people,
                modifier = modifier,
                close = { relationshipMap = false },
                addPerson = { relationshipMap = false; creating = true },
            )
            else -> relationshipMap = false
        }
        return
    }
    if (creating) { PersonEditorScreen(container, null, "", "", modifier) { creating = false }; return }
    if (selectedPersonId != null) {
        BackHandler { selectedPersonId = null }
        CustomerPersonDetailScreen(
            container = container,
            personId = selectedPersonId!!,
            modifier = modifier,
            onBack = { selectedPersonId = null },
        )
        return
    }

    LaunchedEffect(refreshKey) { result = container.customerRepository.loadPeople() }

    when (val current = result) {
        null -> LoadingSurface(modifier, "People", "Loading authorized People…")
        CustomerPeopleResult.AuthenticationRequired -> FailureSurface(modifier, "Sign in again to verify People.")
        is CustomerPeopleResult.Unavailable -> RetrySurface(
            modifier = modifier,
            title = "People unavailable",
            message = current.message,
            supportReference = current.supportReference,
            retry = { result = null; refreshKey++ },
        )
        is CustomerPeopleResult.Success -> VerifiedPeople(
            mode = current.value.mode,
            people = current.value.people,
            open = { selectedPersonId = it.id },
            create = { creating = true },
            showMap = { relationshipMap = true },
        )
    }
}

@Composable
private fun VerifiedPeople(mode: String, people: List<RemotePerson>, open: (RemotePerson) -> Unit, create: () -> Unit, showMap: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("People", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        item {
            Text(
                if (mode == "family") "People connected across your authorized family records." else "People connected to your Memories, interviews, and Letters.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Button(onClick = create, Modifier.fillMaxWidth()) { Text("Add a person") } }
        item { TextButton(onClick = showMap, Modifier.fillMaxWidth()) { Text("View relationship map") } }
        if (people.isEmpty()) {
            item { Card(Modifier.fillMaxWidth()) { Text("No people yet. Add a private person record to connect interviews, Letters, and Memories. This does not invite anyone or share anything.", Modifier.padding(16.dp)) } }
        } else {
            items(people, key = { it.id }) { person ->
                Card(Modifier.fillMaxWidth().clickable { open(person) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(person.name, style = MaterialTheme.typography.titleMedium)
                        person.relation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("${person.interviewCount} interviews · ${person.letterCount} Letters", style = MaterialTheme.typography.bodySmall)
                        if (person.source == "derived") Text("Connected from existing content", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerPersonDetailScreen(
    container: AppContainer,
    personId: String,
    modifier: Modifier,
    onBack: () -> Unit,
) {
    var editing by remember(personId) { mutableStateOf(false) }
    var result by remember(personId) { mutableStateOf<CustomerPersonResult?>(null) }
    var refreshKey by remember(personId) { mutableStateOf(0) }
    LaunchedEffect(personId, refreshKey) { result = container.customerRepository.loadPerson(personId) }

    when (val current = result) {
        null -> LoadingSurface(modifier, "Person", "Loading authorized connections…")
        CustomerPersonResult.AuthenticationRequired -> FailureSurface(modifier, "Sign in again to verify this person.")
        is CustomerPersonResult.Unavailable -> RetrySurface(
            modifier, "Person unavailable", current.message, current.supportReference,
            retry = { result = null; refreshKey++ },
        )
        is CustomerPersonResult.Success -> if (editing) PersonEditorScreen(container, current.value.id, current.value.name, current.value.relation.orEmpty(), modifier) { editing = false; refreshKey++ }
        else VerifiedPersonDetail(current.value, modifier, onBack, edit = { editing = true })
    }
}

@Composable
private fun VerifiedPersonDetail(person: RemotePersonDetail, modifier: Modifier, onBack: () -> Unit, edit: () -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to People") }
                Column {
                    Text(person.name, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                    person.relation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item { Text("Opening a person never shares their Memories or family content.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (person.source == "manual") item { Button(onClick = edit, Modifier.fillMaxWidth()) { Text("Edit person") } }
        item { Text("Interviews (${person.interviews.size})", style = MaterialTheme.typography.titleLarge) }
        if (person.interviews.isEmpty()) item { Text("No connected interviews.") }
        else items(person.interviews, key = { "interview:${it.id}" }) { Text("${it.status.replace('_', ' ').replaceFirstChar(Char::uppercase)} interview") }
        item { Text("Letters (${person.letters.size})", style = MaterialTheme.typography.titleLarge) }
        if (person.letters.isEmpty()) item { Text("No connected Letters.") }
        else items(person.letters, key = { "letter:${it.id}" }) { letter ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(letter.subject, style = MaterialTheme.typography.titleMedium)
                Text("Open Letters to review the secure delivery status", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item { Text("Memories (${person.memories.size})", style = MaterialTheme.typography.titleLarge) }
        if (person.memories.isEmpty()) item { Text("No connected Memories.") }
        else items(person.memories, key = { "memory:${it.id}" }) { memory ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(memory.title, style = MaterialTheme.typography.titleMedium)
                Text(memory.excerpt, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (memory.visibility == "family") "Shared with family" else "Private", style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
}

@Composable
private fun PersonEditorScreen(container: AppContainer, id: String?, initialName: String, initialRelation: String, modifier: Modifier, close: () -> Unit) {
    var name by remember(id) { mutableStateOf(initialName) }; var relation by remember(id) { mutableStateOf(initialRelation) }
    var saving by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    BackHandler(enabled = !saving, onBack = close)
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to People") }; Text(if (id == null) "Add a person" else "Edit person", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        Text("Adding or editing a person organizes connections. It does not share any Memory, Letter, or interview.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        OutlinedTextField(relation, { relation = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Relationship (optional)") }, singleLine = true)
        Button(onClick = { saving = true; scope.launch {
            val result = if (id == null) container.customerRepository.createPerson(name, relation) else container.customerRepository.updatePerson(id, name, relation)
            if (result is FeatureResult.Success) close() else message = (result as? FeatureResult.Unavailable)?.message ?: "Sign in again before saving this person."
            saving = false
        } }, enabled = !saving && name.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save person") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RelationshipMapScreen(people: List<RemotePerson>, modifier: Modifier, close: () -> Unit, addPerson: () -> Unit) {
    fun generation(relation: String?): String {
        val value = relation.orEmpty().lowercase()
        return when {
            listOf("grandchild", "child", "son", "daughter", "niece", "nephew").any(value::contains) -> "Younger generations"
            listOf("grand", "parent", "aunt", "uncle", "elder").any(value::contains) -> "Older generations"
            listOf("sibling", "brother", "sister", "spouse", "partner", "cousin", "friend").any(value::contains) -> "Your generation"
            else -> "Other relationships"
        }
    }
    val groups = people.groupBy { generation(it.relation) }
    val order = listOf("Older generations", "Your generation", "Younger generations", "Other relationships")
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to People") }; Text("Relationship map", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) } }
        item { Text("Relationships determine placement. Edit a person’s relationship to move them between generations.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        order.forEach { title -> groups[title]?.let { members -> item { Text(title, style = MaterialTheme.typography.titleLarge) }; items(members, key = { "map:${it.id}" }) { person -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(person.name, style = MaterialTheme.typography.titleMedium); Text(person.relation ?: "Relationship not specified", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
        if (people.isEmpty()) {
            item { Text("Your relationship map is ready for its first person.", style = MaterialTheme.typography.titleLarge) }
            item { Text("Add a person to begin connecting the relationships in your story.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Button(onClick = addPerson, modifier = Modifier.fillMaxWidth()) { Text("Add a person") } }
        }
    }
}

@Composable
private fun FeedbackSupportScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("feedback") }; var message by remember { mutableStateOf("") }
    var screenshot by remember { mutableStateOf<FeedbackScreenshot?>(null) }; var status by remember { mutableStateOf<String?>(null) }; var sending by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        screenshot = null
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBounded(5 * 1024 * 1024) } }.getOrNull()?.let { bytes ->
            val type = when { bytes.size >= 8 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map(Int::toByte) -> "image/png"; bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"; bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> "image/webp"; else -> null }
            if (type == null) status = "Choose a PNG, JPEG, or WebP image." else screenshot = FeedbackScreenshot("android-screen-capture", type, Base64.encodeToString(bytes, Base64.NO_WRAP))
        } ?: run { status = "Choose an image no larger than 5 MB." }
    }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text(if (kind == "issue") "Report an issue" else "Feedback", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ kind = "feedback"; screenshot = null; status = null }, enabled = kind != "feedback") { Text("Feedback") }; Button({ kind = "issue"; status = null }, enabled = kind != "issue") { Text("Report an issue") } }
        if (kind == "issue") { Text("Screen reference: Android app · More"); Button({ picker.launch("image/*") }, Modifier.fillMaxWidth()) { Text(if (screenshot == null) "Attach one image" else "Replace attached image") }; if (screenshot != null) TextButton({ screenshot = null }) { Text("Remove attachment") }; Text("PNG, JPEG, or WebP up to 5 MB. Avoid private family content unless needed to explain the issue.", style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(message, { message = it.take(5_001); status = null }, Modifier.fillMaxWidth().height(220.dp), label = { Text(if (kind == "issue") "What happened?" else "Your feedback") }, supportingText = { Text("${message.length} of 5,000 characters") })
        status?.let { Text(it, color = if (it.contains("sent")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        Button(onClick = { sending = true; scope.launch {
            val profile = container.settingsRepository.profile(); val sender = (profile as? FeatureResult.Success)?.value?.profile?.displayName
            val result = if (sender == null) FeatureResult.AuthenticationRequired else container.supportRepository.submitFeedback(sender, kind, message, "Android app · More", screenshot)
            status = when (result) { is FeatureResult.Success -> if (kind == "issue") "Issue report sent. Thank you." else "Feedback sent. Thank you."; is FeatureResult.Unavailable -> result.message; FeatureResult.AuthenticationRequired -> "Sign in again before sending feedback." }
            if (result is FeatureResult.Success) { message = ""; screenshot = null }; sending = false
        } }, enabled = !sending && message.trim().isNotEmpty() && message.length <= 5_000, modifier = Modifier.fillMaxWidth()) { Text(if (sending) "Sending…" else if (kind == "issue") "Send issue report" else "Send feedback") }
        Text("Your account email is included so the team can follow up. Support references contain no private Memory content.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PrivacyPermissionsScreen(modifier: Modifier, close: () -> Unit) {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current; var refresh by remember { mutableStateOf(0) }
    DisposableEffect(owner) { val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }; owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) } }
    fun status(permission: String): String = if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) "Allowed" else if ((context as? Activity)?.shouldShowRequestPermissionRationale(permission) == true) "Not allowed" else "Not requested or not allowed"
    @Suppress("UNUSED_VARIABLE") val current = refresh
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text("Privacy and permissions", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        Text("Private by default", style = MaterialTheme.typography.titleLarge); Text("Memories and your Family Mosaic stay private until you explicitly choose what to share and confirm recipients.")
        listOf("Microphone" to Manifest.permission.RECORD_AUDIO, "Camera" to Manifest.permission.CAMERA, "Notifications" to if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else "").forEach { (label, permission) -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(if (permission.isEmpty()) "Allowed by system" else status(permission)) } } }
        Text("Narratrace asks for microphone or camera access only when you choose that capture method. This screen never triggers a prompt.", style = MaterialTheme.typography.bodySmall)
        Text("Photo selection", style = MaterialTheme.typography.titleLarge); Text("Narratrace uses the system picker so you choose individual files without granting access to the entire library.")
        Button({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, Modifier.fillMaxWidth()) { Text("Open device settings") }
    }
}

@Composable
private fun WebResourcesScreen(modifier: Modifier, close: () -> Unit) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text("Web resources", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        Text("Downloads stay on the web", style = MaterialTheme.typography.titleLarge); Text("The Android app never generates, receives, caches, saves, or shares downloadable files.")
        Button({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.narratrace.io/keepsake"))) }, Modifier.fillMaxWidth()) { Text("Open Keepsake books on the web") }
        Button({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.narratrace.io/account"))) }, Modifier.fillMaxWidth()) { Text("Open downloadable resources on the web") }
        Text("Any generation or download on the authenticated website remains your explicit choice.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActivityScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    var result by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.customer.ActivityPage>?>(null) }; var selected by remember { mutableStateOf<String?>(null) }; var refresh by remember { mutableStateOf(0) }
    if (selected != null) { ProcessingDetailScreen(container, selected!!, modifier) { selected = null; refresh++ }; return }
    LaunchedEffect(refresh) { result = container.customerRepository.activity() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text("Activity", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) } }
        when (val current = result) { null -> item { LoadingMessage("Refreshing Activity…") }; FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify Activity.", color = MaterialTheme.colorScheme.error) }; is FeatureResult.Unavailable -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }; is FeatureResult.Success -> if (current.value.items.isEmpty()) item { Text("Nothing needs your attention.") } else items(current.value.items, key = { "activity:${it.id}" }) { item -> Card(Modifier.fillMaxWidth().clickable(enabled = item.kind == "processing") { selected = item.id }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(item.title ?: item.announcement ?: "Narratrace update", style = MaterialTheme.typography.titleMedium); item.body?.let { Text(it) }; item.progress?.let { LinearProgressIndicator({ it.coerceIn(0,100) / 100f }, Modifier.fillMaxWidth()) }; if (item.kind == "processing") Text("Open processing details", style = MaterialTheme.typography.labelMedium) } } } }
    }
}

@Composable
private fun ProcessingDetailScreen(container: AppContainer, id: String, modifier: Modifier, close: () -> Unit) {
    var result by remember(id) { mutableStateOf<FeatureResult<ProcessingJob>?>(null) }; var refresh by remember { mutableStateOf(0) }; var retrying by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    LaunchedEffect(id, refresh) { result = container.supportRepository.processing(id) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Activity") }; Text("Processing details", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        when (val current = result) { null -> LoadingMessage("Checking processing state…"); FeatureResult.AuthenticationRequired -> Text("Sign in again to verify this processing item.", color = MaterialTheme.colorScheme.error); is FeatureResult.Unavailable -> Text(current.message, color = MaterialTheme.colorScheme.error); is FeatureResult.Success -> { val job = current.value; Text(job.state.replace('_',' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleLarge); Text(job.failureCategory ?: if (job.state == "preserved") "The original is preserved." else "The original remains protected while processing continues."); Text("Type: ${job.jobType.replace('_',' ').replaceFirstChar(Char::uppercase)}"); Text("Updated: ${job.updatedAt}"); job.progress?.let { LinearProgressIndicator({ it.coerceIn(0,100) / 100f }, Modifier.fillMaxWidth()) }; if (job.canRetry) { Button({ retrying = true; scope.launch { container.supportRepository.retryProcessing(id); retrying = false; refresh++ } }, enabled = !retrying, modifier = Modifier.fillMaxWidth()) { Text(if (retrying) "Retrying…" else "Try processing again") }; Text("Retrying optional processing does not replace or remove the preserved original.", style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable
private fun LoadingSurface(modifier: Modifier, title: String, message: String) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        LoadingMessage(message)
    }
}

@Composable
private fun LoadingMessage(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FailureSurface(modifier: Modifier, message: String) {
    Column(modifier.fillMaxSize().padding(24.dp)) { Text(message, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun RetrySurface(
    modifier: Modifier,
    title: String,
    message: String,
    supportReference: String,
    retry: () -> Unit,
) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(message, color = MaterialTheme.colorScheme.error)
        if (supportReference.isNotBlank()) Text("Support reference: $supportReference", style = MaterialTheme.typography.bodySmall)
        Button(onClick = retry) { Text("Try again") }
    }
}

@Composable
private fun CustomerLibraryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    var result by remember { mutableStateOf<CustomerMemoriesResult?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var selectedMemoryId by remember { mutableStateOf<String?>(null) }
    var selectedMediaId by remember { mutableStateOf<String?>(null) }
    var media by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.media.MediaList>?>(null) }

    if (selectedMediaId != null) {
        BackHandler { selectedMediaId = null; refreshKey++ }
        CustomerMediaDetailScreen(container, selectedMediaId!!, modifier) { selectedMediaId = null; refreshKey++ }
        return
    }

    if (selectedMemoryId != null) {
        BackHandler { selectedMemoryId = null; refreshKey++ }
        CustomerMemoryDetailScreen(
            container = container,
            memoryId = selectedMemoryId!!,
            modifier = modifier,
            onBack = { selectedMemoryId = null; refreshKey++ },
        )
        return
    }
    LaunchedEffect(refreshKey) { result = container.customerRepository.loadMemories(); media = container.mediaRepository.media() }

    when (val current = result) {
        null -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Library", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
            LoadingMessage("Loading your private Library…")
        }
        CustomerMemoriesResult.AuthenticationRequired -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
        ) { Text("Sign in again to verify your private Library.", color = MaterialTheme.colorScheme.error) }
        is CustomerMemoriesResult.Unavailable -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Library unavailable", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
            Text(current.message, color = MaterialTheme.colorScheme.error)
            if (current.supportReference.isNotBlank()) Text("Support reference: ${current.supportReference}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { result = null; refreshKey++ }) { Text("Try again") }
        }
        is CustomerMemoriesResult.Success -> VerifiedLibrary(
            container,
            current.value.mode,
            current.value.memories,
            (media as? FeatureResult.Success)?.value?.media.orEmpty(),
            open = { selectedMemoryId = it.id },
            openMedia = { selectedMediaId = it.id },
        )
    }
}

@Composable
private fun VerifiedLibrary(container: AppContainer, mode: String, memories: List<RemoteMemory>, media: List<MediaSummary>, open: (RemoteMemory) -> Unit, openMedia: (MediaSummary) -> Unit) {
    var query by remember { mutableStateOf("") }
    var search by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.customer.SearchResponse>?>(null) }
    var searching by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Library", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        }
        item { OutlinedTextField(query, { query = it.take(100); search = null }, Modifier.fillMaxWidth(), label = { Text("Search your archive") }, singleLine = true) }
        item { Button(onClick = { searching = true; scope.launch { search = container.customerRepository.search(query); searching = false } }, enabled = !searching && query.trim().length >= 2, modifier = Modifier.fillMaxWidth()) { Text("Search") } }
        when (val found = search) {
            is FeatureResult.Success -> if (found.value.results.isEmpty()) item { Text("No authorized archive results.") } else items(found.value.results, key = { "search:${it.id}" }) { result -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(result.title, style = MaterialTheme.typography.titleMedium); Text(result.subtitle); Text(result.kind.replace('_', ' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall) } } }
            is FeatureResult.Unavailable -> item { Text(found.message, color = MaterialTheme.colorScheme.error) }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to search your archive.", color = MaterialTheme.colorScheme.error) }
            null -> Unit
        }
        item {
            Text(
                if (mode == "mosaic") "Your Memories and explicitly shared family Memories." else "Your private Memories.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (memories.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No Memories yet. Capture remains private until you choose otherwise.", modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(memories, key = { it.id }) { memory -> MemoryCard(memory, open) }
        }
        item { Text("Audio, photos, and videos", style = MaterialTheme.typography.titleLarge) }
        if (media.isEmpty()) item { Text("No preserved media yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else items(media, key = { "media:${it.id}" }) { item -> Card(Modifier.fillMaxWidth().clickable { openMedia(item) }) { Column(Modifier.padding(16.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text("${item.kind.replaceFirstChar(Char::uppercase)} · ${item.state.replace('_', ' ')}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
    }
}

@Composable
private fun CustomerMediaDetailScreen(container: AppContainer, mediaId: String, modifier: Modifier, close: () -> Unit) {
    var result by remember(mediaId) { mutableStateOf<FeatureResult<io.narratrace.android.core.media.MediaDetailResponse>?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var delivering by remember { mutableStateOf(false) }
    var photoInsightsEnabled by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun refreshAfterSavedEdit(mediaKind: String, savedMessage: String): String {
        if (!shouldRefreshPhotoInsights(mediaKind, photoInsightsEnabled)) {
            saveError = false
            return savedMessage
        }
        return when (val refreshed = container.mediaRepository.refreshPhotoInsights(mediaId)) {
            is FeatureResult.Success -> { saveError = false; refreshed.value.message }
            is FeatureResult.Unavailable -> { saveError = true; refreshed.message }
            FeatureResult.AuthenticationRequired -> { saveError = true; "Sign in again before refreshing photo insights." }
        }
    }
    LaunchedEffect(mediaId) {
        val loaded = container.mediaRepository.mediaDetail(mediaId)
        photoInsightsEnabled = ((container.settingsRepository.mediaAiPreferences() as? FeatureResult.Success)
            ?.value?.preferences?.photoAiInsightsEnabled == true)
        result = loaded
        val detail = (loaded as? FeatureResult.Success)?.value?.media
        caption = detail?.caption.orEmpty(); tags = detail?.customTags?.joinToString(", ").orEmpty()
        if (detail?.kind == "photo" && detail.playbackUrl != null) photoBytes = container.mediaRepository.playback(detail.playbackUrl)
    }
    if (delivering) { ArtifactDeliveryComposer(container, mediaId, modifier) { delivering = false }; return }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Delete this media?") },
        text = { Text("This preserved original and its derived content will be permanently removed.") },
        confirmButton = { Button(onClick = { confirmDelete = false; busy = true; scope.launch { if (container.mediaRepository.deleteMedia(mediaId) is FeatureResult.Success) close(); busy = false } }) { Text("Delete permanently") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    when (val loaded = result) {
        null -> LoadingSurface(modifier, "Media", "Opening protected media…")
        FeatureResult.AuthenticationRequired -> FailureSurface(modifier, "Sign in again to verify this media.")
        is FeatureResult.Unavailable -> RetrySurface(modifier, "Media unavailable", loaded.message, loaded.supportReference) { result = null }
        is FeatureResult.Success -> {
            val detail = loaded.value.media
            Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Library") }; Text(detail.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
                if (detail.kind == "photo") photoBytes?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap -> Image(bitmap.asImageBitmap(), "Preserved photo", Modifier.fillMaxWidth()) } }
                else detail.playbackUrl?.let { url -> AndroidView(
                    factory = { ctx -> VideoView(ctx).apply {
                        contentDescription = "Protected ${detail.kind} player for ${detail.title}"
                        isFocusable = true
                        isFocusableInTouchMode = true
                        val controls = MediaController(ctx)
                        controls.setAnchorView(this)
                        setMediaController(controls)
                        setVideoPath(url)
                        setOnPreparedListener { start() }
                    } },
                    modifier = Modifier.fillMaxWidth().height(if (detail.kind == "video") 260.dp else 80.dp),
                ) }
                detail.caption?.takeIf(String::isNotBlank)?.let { Text(it) }
                detail.transcript?.takeIf(String::isNotBlank)?.let { Text("Transcript", style = MaterialTheme.typography.titleLarge); Text(it) }
                detail.summary?.takeIf(String::isNotBlank)?.let { Text("Summary", style = MaterialTheme.typography.titleLarge); Text(it) }
                if (detail.tags.isNotEmpty() || detail.customTags.isNotEmpty()) Text((detail.tags + detail.customTags).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                val clarifyingQuestions = visibleMediaClarifyingQuestions(detail.kind, photoInsightsEnabled, detail.clarifyingQuestions)
                if (clarifyingQuestions.isNotEmpty()) {
                    Text("Nia would like to clarify", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    clarifyingQuestions.forEach { question -> Text("• $question") }
                    Text("Add what you know to the caption or your tags. Nia will use it only when Photo insights are enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                saveMessage?.let { Text(it, Modifier.semantics { liveRegion = if (saveError) LiveRegionMode.Assertive else LiveRegionMode.Polite }, color = if (saveError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(caption, { caption = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Caption") })
                Button(onClick = { busy = true; scope.launch {
                    when (val saved = container.mediaRepository.updateCaption(mediaId, caption)) {
                        is FeatureResult.Success -> {
                            saveMessage = refreshAfterSavedEdit(detail.kind, "Caption saved.")
                            result = container.mediaRepository.mediaDetail(mediaId)
                        }
                        is FeatureResult.Unavailable -> { saveError = true; saveMessage = saved.message }
                        FeatureResult.AuthenticationRequired -> { saveError = true; saveMessage = "Sign in again before saving this caption." }
                    }
                    busy = false
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save caption") }
                OutlinedTextField(tags, { tags = it.take(320) }, Modifier.fillMaxWidth(), label = { Text("Custom tags") }, supportingText = { Text("Comma-separated; up to 10 tags") })
                Button(onClick = { busy = true; scope.launch {
                    when (val saved = container.mediaRepository.updateTags(mediaId, tags.split(','))) {
                        is FeatureResult.Success -> {
                            saveMessage = refreshAfterSavedEdit(detail.kind, "Tags saved.")
                            result = container.mediaRepository.mediaDetail(mediaId)
                        }
                        is FeatureResult.Unavailable -> { saveError = true; saveMessage = saved.message }
                        FeatureResult.AuthenticationRequired -> { saveError = true; saveMessage = "Sign in again before saving these tags." }
                    }
                    busy = false
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save tags") }
                Button(onClick = { delivering = true }, enabled = detail.state == "ready", modifier = Modifier.fillMaxWidth()) { Text("Deliver this ${detail.kind}") }
                if (detail.state != "ready") Text("Delivery becomes available after preservation and processing are complete.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Delete media", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ArtifactDeliveryComposer(container: AppContainer, uploadId: String, modifier: Modifier, close: () -> Unit) {
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var self by remember { mutableStateOf(false) }; var later by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope(); BackHandler(enabled = !busy, onBack = close)
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to media") }; Text("Deliver this memory", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        Text("The recipient cannot access this artifact before its delivery time. Their private review email identifies you as the creator but contains no artifact content. They can confirm or decline; Narratrace asks them to confirm again at delivery if the prior confirmation is more than 12 months old.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Recipient name") }, singleLine = true)
        Button(onClick = { self = !self }, Modifier.fillMaxWidth()) { Text(if (self) "Deliver to me ✓" else "Deliver to me") }
        if (!self) OutlinedTextField(email, { email = it.take(254) }, Modifier.fillMaxWidth(), label = { Text("Recipient email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { later = false }, Modifier.weight(1f)) { Text(if (!later) "Send now ✓" else "Send now") }; Button(onClick = { later = true }, Modifier.weight(1f)) { Text(if (later) "Deliver later ✓" else "Deliver later") } }
        if (later) OutlinedTextField(local, { local = it.take(16) }, Modifier.fillMaxWidth(), label = { Text("Local date and time") }, placeholder = { Text("2026-12-31T18:30") }, supportingText = { Text("Uses ${java.time.ZoneId.systemDefault().id}") }, singleLine = true)
        Button(onClick = { busy = true; scope.launch {
            val parsed = if (later) runCatching { LocalDateTime.parse(local) }.getOrNull() else null
            if (later && parsed == null) message = "Enter a valid local date and time."
            else when (val made = container.lettersRepository.createArtifactDelivery(uploadId, name, email.takeIf { !self }, self, if (later) DeliveryMode.LATER else DeliveryMode.NOW, parsed)) {
                is FeatureResult.Success -> message = if (self) "Delivery scheduled securely." else "Delivery created. Recipient verification is required before access."
                is FeatureResult.Unavailable -> message = made.message
                FeatureResult.AuthenticationRequired -> message = "Sign in again before creating delivery."
            }; busy = false
        } }, enabled = !busy && name.trim().isNotEmpty() && (self || email.trim().isNotEmpty()), modifier = Modifier.fillMaxWidth()) { Text("Create delivery") }
        message?.let { Text(it, color = if (it.startsWith("Delivery")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MemoryCard(memory: RemoteMemory, open: (RemoteMemory) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { open(memory) }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(memory.title, style = MaterialTheme.typography.titleMedium)
            Text(memory.excerpt, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    memory.status == "review_required" -> "Review required before sharing"
                    memory.visibility == "family" -> "Shared with family"
                    else -> "Private"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (memory.status == "review_required") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (!memory.isOwner) {
                Text("Shared with you · read only", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CustomerMemoryDetailScreen(
    container: AppContainer,
    memoryId: String,
    modifier: Modifier,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var result by remember(memoryId) { mutableStateOf<CustomerMemoryResult?>(null) }
    var refreshKey by remember(memoryId) { mutableStateOf(0) }
    var changing by remember { mutableStateOf(false) }
    var confirmFamilyShare by remember { mutableStateOf(false) }
    LaunchedEffect(memoryId, refreshKey) { result = container.customerRepository.loadMemory(memoryId) }

    if (confirmFamilyShare) {
        AlertDialog(
            onDismissRequest = { if (!changing) confirmFamilyShare = false },
            title = { Text("Share this Memory with family?") },
            text = { Text("Active family members will be able to read only this Memory. Your other Memories remain private.") },
            confirmButton = {
                Button(onClick = {
                    confirmFamilyShare = false
                    changing = true
                    scope.launch {
                        result = container.customerRepository.updateMemory(memoryId, visibility = "family")
                        changing = false
                    }
                }) { Text("Share this Memory") }
            },
            dismissButton = { TextButton(onClick = { confirmFamilyShare = false }) { Text("Cancel") } },
        )
    }

    when (val current = result) {
        null -> LoadingSurface(modifier, "Memory", "Verifying this private Memory…")
        CustomerMemoryResult.AuthenticationRequired -> FailureSurface(modifier, "Sign in again to verify this Memory.")
        is CustomerMemoryResult.Unavailable -> RetrySurface(
            modifier, "Memory unavailable", current.message, current.supportReference,
            retry = { result = null; refreshKey++ },
        )
        is CustomerMemoryResult.Success -> {
            val memory = current.value
            Column(
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, enabled = !changing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library")
                    }
                    Text(memory.title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                }
                Text(memory.excerpt, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        memory.status == "review_required" -> "Review required"
                        memory.visibility == "family" -> "Shared with family"
                        else -> "Private"
                    },
                    color = if (memory.status == "review_required") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                if (!memory.isOwner) {
                    Text("This Memory was shared with you. Only its owner can change sharing or review state.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (memory.status == "review_required") {
                        Button(
                            onClick = {
                                changing = true
                                scope.launch {
                                    result = container.customerRepository.updateMemory(memoryId, status = "active")
                                    changing = false
                                }
                            },
                            enabled = !changing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Keep this Memory") }
                        Text("Reviewing keeps the Memory private. Sharing remains a separate choice.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            changing = true
                            scope.launch {
                                result = container.customerRepository.updateMemory(memoryId, pinned = !memory.pinned)
                                changing = false
                            }
                        },
                        enabled = !changing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (memory.pinned) "Remove from favorites" else "Add to favorites") }
                    if (memory.visibility == "family") {
                        Button(
                            onClick = {
                                changing = true
                                scope.launch {
                                    result = container.customerRepository.updateMemory(memoryId, visibility = "private")
                                    changing = false
                                }
                            },
                            enabled = !changing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Make private") }
                    } else {
                        Button(
                            onClick = { confirmFamilyShare = true },
                            enabled = !changing && memory.status != "review_required",
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Share with family") }
                    }
                    Text("Changing this Memory never changes the privacy of any other Memory.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (changing) LoadingMessage("Saving this Memory’s privacy choice…")
            }
        }
    }
}

@Composable
private fun CustomerHomeScreen(container: AppContainer, modifier: Modifier = Modifier) {
    var result by remember { mutableStateOf<CustomerHomeResult?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var activity by remember { mutableStateOf<FeatureResult<io.narratrace.android.core.customer.ActivityPage>?>(null) }

    LaunchedEffect(refreshKey) { result = container.customerRepository.loadHome(); activity = container.customerRepository.activity() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Home", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        protectedUploadAttention(container.mediaRepository.queue.items())?.let {
            Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
        }
        when (val current = result) {
            null -> {
                LoadingMessage("Loading your private Home…")
            }
            CustomerHomeResult.AuthenticationRequired -> Text(
                "Sign in again to verify your private Home.",
                color = MaterialTheme.colorScheme.error,
            )
            is CustomerHomeResult.Unavailable -> {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                if (current.supportReference.isNotBlank()) {
                    Text("Support reference: ${current.supportReference}", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { result = null; refreshKey++ }) { Text("Try again") }
            }
            is CustomerHomeResult.Success -> VerifiedHome(current.value, activity)
        }
    }
}

@Composable
private fun VerifiedHome(customer: CustomerHome, activity: FeatureResult<io.narratrace.android.core.customer.ActivityPage>?) {
    val storage = customer.account.storage
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Protected storage", style = MaterialTheme.typography.titleMedium)
            Text("${storage.usedLabel} used · ${storage.availableLabel} available")
            LinearProgressIndicator(
                progress = { (storage.usedPercent.coerceIn(0, 100) / 100f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${customer.account.plan.planLabel()} · ${customer.account.status.statusLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text("Recent Memories", style = MaterialTheme.typography.titleLarge)
    if (customer.home.recentMemories.isEmpty()) {
        Text("Your recent private Memories will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        customer.home.recentMemories.forEach { memory ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(memory.title, style = MaterialTheme.typography.titleMedium)
                    Text(memory.excerpt, modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (memory.visibility == "family") "Shared with family" else "Private",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
    if (customer.home.attention.isNotEmpty()) {
        Text("Needs your attention", style = MaterialTheme.typography.titleLarge)
        customer.home.attention.forEach { item ->
            Text(item.title ?: item.announcement ?: "Narratrace update")
        }
    }
    Text("Activity", style = MaterialTheme.typography.titleLarge)
    when (activity) {
        null -> LoadingMessage("Refreshing Activity…")
        is FeatureResult.Success -> if (activity.value.items.isEmpty()) Text("No account activity yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) else activity.value.items.forEach { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(item.title ?: item.announcement ?: item.kind.replace('_', ' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium)
            item.body?.let { Text(it) }; item.state?.let { Text(it.replace('_', ' '), style = MaterialTheme.typography.bodySmall) }
            item.progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0, 100) / 100f }, Modifier.fillMaxWidth()) }
        } } }
        is FeatureResult.Unavailable -> Text(activity.message, color = MaterialTheme.colorScheme.error)
        FeatureResult.AuthenticationRequired -> Text("Sign in again to verify Activity.", color = MaterialTheme.colorScheme.error)
    }
}

private fun String?.planLabel(): String = when (this) {
    "vault" -> "Vault"
    "individual" -> "A Life"
    "family", "extended_family" -> "Family"
    else -> "Free guided interview"
}

private fun String.statusLabel(): String = when (this) {
    "trial_active", "trial_extended", "trial_expired" -> "Free guided interview"
    "subscription_active" -> "Active"
    "subscription_grace" -> "Payment grace period"
    "lapsed" -> "Archive only"
    "vault_only" -> "Vault access"
    "invited" -> "Invitation accepted"
    else -> "Access unavailable"
}

internal fun shouldRefreshAccountAfterExternalManagement(
    awaitingReturn: Boolean,
    event: Lifecycle.Event,
): Boolean = awaitingReturn && event == Lifecycle.Event.ON_RESUME
