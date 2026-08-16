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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.heading
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
import io.narratrace.android.core.auth.RevokeScope
import io.narratrace.android.core.auth.RevocationResult
import io.narratrace.android.core.auth.SecuritySessionsResult
import io.narratrace.android.core.customer.CustomerHome
import io.narratrace.android.core.customer.CustomerHomeResult
import io.narratrace.android.core.customer.CustomerMemoriesResult
import io.narratrace.android.core.customer.RemoteMemory
import io.narratrace.android.core.customer.CustomerPeopleResult
import io.narratrace.android.core.customer.CustomerPersonResult
import io.narratrace.android.core.customer.RemotePerson
import io.narratrace.android.core.customer.RemotePersonDetail
import io.narratrace.android.core.customer.AccountResult
import io.narratrace.android.core.customer.WrittenMemoryResult
import io.narratrace.android.core.customer.CustomerMemoryResult
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.media.InterviewDetail
import io.narratrace.android.core.media.InterviewSummary
import io.narratrace.android.core.media.MediaSummary
import io.narratrace.android.core.media.PendingMediaKind
import io.narratrace.android.core.media.SecureAudioRecorder
import io.narratrace.android.core.media.ProtectedUploadWorker
import io.narratrace.android.core.letters.LetterSummary
import io.narratrace.android.core.delivery.DeliveryMode
import io.narratrace.android.core.support.FeedbackScreenshot
import io.narratrace.android.core.support.ProcessingJob
import java.time.LocalDateTime
import io.narratrace.android.core.ui.NarratraceAppearance
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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

@Composable
fun NarratraceApp(container: AppContainer) {
    val appContext = LocalContext.current.applicationContext
    var onboarded by remember { mutableStateOf(container.onboardingStore.completed()) }
    if (!onboarded) { OnboardingScreen { if (container.onboardingStore.complete()) onboarded = true }; return }
    val authState by container.sessionManager.state.collectAsStateWithLifecycle()
    LaunchedEffect(container) { container.sessionManager.restore(); container.offlineRepository.reconcile(); ProtectedUploadWorker.schedule(appContext) }

    when (authState) {
        AuthState.Restoring -> ProtectedLoadingScreen()
        AuthState.SignedOut -> SignInScreen(container = container, returning = false)
        is AuthState.Locked -> SignInScreen(container = container, returning = true)
        is AuthState.Authenticated -> AuthenticatedShell(
            container = container,
            onInteraction = container.sessionManager::touch,
            onSignOut = container.sessionManager::signOut,
        )
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
        CircularProgressIndicator()
        Text(
            text = "Checking your protected session…",
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignInScreen(container: AppContainer, returning: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mfaCode by remember { mutableStateOf("") }
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
        OutlinedTextField(
            value = mfaCode,
            onValueChange = { value ->
                mfaCode = value.uppercase().filter { it.isDigit() || it in 'A'..'F' || it == '-' }.take(19)
                message = null
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            label = { Text("Authenticator or recovery code (optional)") },
            supportingText = { Text("Leave blank unless you enabled authenticator protection.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true,
            enabled = !isSigningIn,
        )
        Button(
            onClick = {
                isSigningIn = true
                message = null
                supportReference = ""
                scope.launch {
                    when (val result = container.authenticationCoordinator(context).signIn(mfaCode)) {
                        SignInResult.Authenticated -> Unit
                        SignInResult.Cancelled -> Unit
                        is SignInResult.Failed -> {
                            message = result.message
                            supportReference = result.supportReference
                        }
                    }
                    isSigningIn = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
            enabled = !isSigningIn && container.isApiConfigured,
        ) {
            if (isSigningIn) CircularProgressIndicator() else Text("Continue with Google")
        }
        if (!container.isApiConfigured) {
            Text(
                text = "This build is not connected to the Narratrace service.",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        message?.let {
            Text(it, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
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
    if (familyOpen) { FamilySharingScreen(container, modifier) { familyOpen = false }; return }
    if (settingsOpen) { ProfileSettingsScreen(container, modifier) { settingsOpen = false }; return }
    if (feedbackOpen) { FeedbackSupportScreen(container, modifier) { feedbackOpen = false }; return }
    if (permissionsOpen) { PrivacyPermissionsScreen(modifier) { permissionsOpen = false }; return }
    if (activityOpen) { ActivityScreen(container, modifier) { activityOpen = false }; return }
    if (resourcesOpen) { WebResourcesScreen(modifier) { resourcesOpen = false }; return }
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
                Text(
                    if (revokeScope == RevokeScope.AllDevices) {
                        "Every Narratrace mobile session will be revoked. Each device must sign in again."
                    } else {
                        "This device's protected session will be revoked and removed."
                    },
                )
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
                ) { Text(if (revokeScope == RevokeScope.AllDevices) "Sign out everywhere" else "Sign out") }
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
            null -> item { CircularProgressIndicator() }
            AccountResult.AuthenticationRequired -> item { Text("Sign in again to verify account access.", color = MaterialTheme.colorScheme.error) }
            is AccountResult.Unavailable -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }
            is AccountResult.Success -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Plan", style = MaterialTheme.typography.titleMedium)
                        Text("${current.value.plan.planLabel()} · ${current.value.status.statusLabel()}")
                        current.value.billingCycle?.let { Text("Billing: ${it.replace('_', ' ').replaceFirstChar(Char::uppercase)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        when (current.value.trialLifecycleStage) {
                            "halfway" -> Text(if (current.value.activated == true) "You’re halfway through your free trial. You’ve started building your Narratrace." else "You’re halfway through your free trial. Preserve your first meaningful memory.", style = MaterialTheme.typography.bodySmall)
                            "billing_d1" -> Text("Your free trial ends tomorrow. Review or cancel your billing plan on the web.", style = MaterialTheme.typography.bodySmall)
                            "billing_d4", "billing_d2" -> Text("Your trial is still free. Review the exact upcoming charge date and plan details on the web.", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${current.value.storage.usedLabel} used · ${current.value.storage.availableLabel} available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text("Active mobile sessions", style = MaterialTheme.typography.titleLarge) }
        when (val current = sessions) {
            null -> item { CircularProgressIndicator() }
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
        when (val current = deliveries) {
            null -> item { CircularProgressIndicator() }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify deliveries.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> if (current.value.deliveries.isEmpty()) item { Text("No scheduled artifact deliveries.") }
            else items(current.value.deliveries, key = { "delivery:${it.id}" }) { delivery -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${delivery.artifactKind.replaceFirstChar(Char::uppercase)} for ${delivery.recipientName}", style = MaterialTheme.typography.titleMedium)
                Text(if (delivery.selfDelivery) "Delivery to you" else delivery.recipientEmail)
                Text(if (delivery.revokedAt != null) "Revoked" else "${delivery.state.replace('_', ' ')} · ${delivery.deliverAt}", style = MaterialTheme.typography.bodySmall)
                if (delivery.revokedAt == null && delivery.state !in setOf("delivered", "revoked")) TextButton(onClick = { revokeDeliveryId = delivery.id }) { Text("Revoke") }
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
        item { Card(Modifier.fillMaxWidth().clickable { resourcesOpen = true }) { Column(Modifier.padding(16.dp)) { Text("Yearbooks and downloadable resources", style = MaterialTheme.typography.titleMedium); Text("Open authenticated resources on the Narratrace website.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        if (container.latestSupportReference().isNotBlank()) item { TextButton(onClick = { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Narratrace support reference", container.latestSupportReference())) }, Modifier.fillMaxWidth()) { Text("Copy latest support reference") } }
        item { Text("Account data and closure", style = MaterialTheme.typography.titleLarge) }
        item { Card(Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.narratrace.io/account"))) }) { Column(Modifier.padding(16.dp)) {
            Text("Open secure account management", style = MaterialTheme.typography.titleMedium)
            Text("Request an archive, manage billing, or review closure and recovery on the authenticated website. Signing out does not delete your account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Closed accounts can be recovered for 30 days; inactive or terminated account data follows the 365-day retention policy.", style = MaterialTheme.typography.bodySmall)
        } } }
        revocationFailure?.let { failure -> item {
            Text(failure.message, color = MaterialTheme.colorScheme.error)
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
    LaunchedEffect(Unit) { profile = container.settingsRepository.profile(); preferences = container.settingsRepository.preferences(); (profile as? FeatureResult.Success)?.value?.profile?.let { name = it.displayName; birthYear = it.birthYear?.toString().orEmpty(); language = it.preferredLanguage } }
    val latestBirthYear = java.time.Year.now().value - 5
    val birthYearValid = birthYear.isEmpty() || (birthYear.length == 4 && birthYear.toIntOrNull()?.let { it in 1900..latestBirthYear } == true)
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to account") }; Text("Profile and preferences", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) } }
        item { OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true) }
        item { OutlinedTextField(birthYear, { birthYear = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Birth year (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
        item { Button(onClick = { language = if (language == "en") "hi" else "en" }, Modifier.fillMaxWidth()) { Text("Language: ${if (language == "hi") "हिन्दी" else "English"}") } }
        item { Button(onClick = { busy = true; scope.launch { val saved = container.settingsRepository.updateProfile(name, birthYear.toIntOrNull(), language); message = if (saved is FeatureResult.Success) "Profile saved." else (saved as? FeatureResult.Unavailable)?.message; busy = false } }, enabled = !busy && name.trim().isNotEmpty() && birthYearValid, modifier = Modifier.fillMaxWidth()) { Text("Save profile") } }
        item { Text("App appearance", style = MaterialTheme.typography.titleLarge) }
        items(listOf(NarratraceAppearance.System, NarratraceAppearance.Light, NarratraceAppearance.Dark), key = { it.name }) { appearance -> Button(onClick = { if (container.appearanceStore.save(appearance)) (context as? Activity)?.recreate() }, Modifier.fillMaxWidth()) { Text(appearance.name + if (container.appearanceStore.load() == appearance) " ✓" else "") } }
        item { Text("Upcoming themes", style = MaterialTheme.typography.titleMedium) }
        items(listOf(NarratraceAppearance.UpcomingPreview, NarratraceAppearance.ChaiLatte), key = { it.name }) { appearance -> Button(onClick = { if (container.appearanceStore.save(appearance)) (context as? Activity)?.recreate() }, Modifier.fillMaxWidth()) { Text(appearance.name.replace("UpcomingPreview", "Narratrace Blue").replace("ChaiLatte", "Chai Latte") + if (container.appearanceStore.load() == appearance) " ✓" else "") } }
        item { Text("Notification preferences", style = MaterialTheme.typography.titleLarge) }
        (preferences as? FeatureResult.Success)?.value?.preferences?.let { prefs ->
            val choices = listOf("processing_ready" to ("Processing ready" to prefs.processingReady), "invitations" to ("Invitations" to prefs.invitations), "letters" to ("Letters" to prefs.letters), "trial_and_billing" to ("Trial and billing" to prefs.trialAndBilling), "product_guidance" to ("Product guidance" to prefs.productGuidance))
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
        item { Text("Joining a family changes what can appear in Mosaic, but nothing is shared automatically. Individual Memory and Circle sharing choices remain explicit.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when (val loaded = family) {
            null -> item { CircularProgressIndicator() }
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
        item { Text("Extended Family Circles", style = MaterialTheme.typography.titleLarge) }
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
            null -> item { CircularProgressIndicator() }
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
            val queued = bytes?.let { container.mediaRepository.queue.enqueue(it, PendingMediaKind.Photo, "narratrace-${UUID.randomUUID()}.$extension", mime) }
            if (queued == null) photoMessage = "The photo could not be encrypted safely. The original was not uploaded."
            else photoMessage = if (container.mediaRepository.reconcile() == 0) "Photo preserved securely." else "Photo protected on this device and waiting for secure transfer."
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) captureScope.launch {
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (mime !in setOf("video/mp4", "video/quicktime")) { photoMessage = "Choose an MP4 or QuickTime video."; return@launch }
            val queued = runCatching { context.contentResolver.openInputStream(uri)?.use { container.mediaRepository.queue.enqueueVideoStream(it, PendingMediaKind.StandaloneVideo, "narratrace-${UUID.randomUUID()}.${if (mime == "video/quicktime") "mov" else "mp4"}", mime) } }.getOrNull()
            if (queued == null) photoMessage = "This video could not be encrypted safely or exceeds the 2 GB limit."
            else photoMessage = if (container.mediaRepository.reconcile() == 0) "Video preserved securely." else "Video protected on this device and waiting for secure transfer."
        }
    }
    LaunchedEffect(refreshKey) { accountResult = container.customerRepository.loadAccount() }

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
        AudioCaptureScreen(container, modifier, null, null) { recordingAudio = false }
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
            val permitted = current.value.capabilities.captureMemories
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Capture a Memory", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
                item { Text("Nothing is shared automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable(enabled = permitted) {
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
                    Card(Modifier.fillMaxWidth().clickable(enabled = permitted) { onInteraction(); recordingAudio = true }) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Record audio", style = MaterialTheme.typography.titleMedium)
                            Text("Encrypted on this device until preservation is verified.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Card(Modifier.fillMaxWidth().clickable(enabled = permitted) { photoPicker.launch("image/*") }) { Column(Modifier.padding(16.dp)) {
                    Text("Add a photo", style = MaterialTheme.typography.titleMedium)
                    Text("The selected photo is encrypted before retry staging.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    photoMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                } } }
                item { Card(Modifier.fillMaxWidth().clickable(enabled = permitted) { videoPicker.launch("video/*") }) { Column(Modifier.padding(16.dp)) {
                    Text("Record video", style = MaterialTheme.typography.titleMedium)
                    Text("Choose or record a clip for encrypted, resumable preservation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } } }
                item {
                    Card(Modifier.fillMaxWidth().clickable(enabled = permitted) { onInteraction(); interviews = true }) { Column(Modifier.padding(16.dp)) {
                        Text("Start a guided interview", style = MaterialTheme.typography.titleMedium)
                        Text("Use text or protected audio responses with Companion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
                if (!permitted) item {
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
        item { Text("Letters stay private until their delivery time and required recipient verification.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(onClick = { composing = true }, Modifier.fillMaxWidth()) { Text("Write a Letter") } }
        when (val loaded = result) {
            null -> item { CircularProgressIndicator() }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify Letters.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> if (loaded.value.letters.isEmpty()) item { Text("No Letters yet.") } else items(loaded.value.letters, key = { it.id }) { letter ->
                Card(Modifier.fillMaxWidth().clickable { selected = letter }) { Column(Modifier.padding(16.dp)) {
                    Text(letter.subject, style = MaterialTheme.typography.titleMedium)
                    Text("To ${letter.recipientName}")
                    Text(when { letter.delivered -> "Delivered"; !letter.recipientVerified -> "Recipient verification pending"; else -> "Private until ${letter.unlockAt}" }, style = MaterialTheme.typography.bodySmall)
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
        Text("Nothing is shared before delivery. External recipients must verify their address before a private Letter can be delivered.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("To ${letter.recipientName}"); Text(if (letter.delivered) "Delivered" else if (!letter.recipientVerified) "Recipient verification pending" else "Private until ${letter.unlockAt}")
            if (letter.unlocked) letter.body?.let { Text(it, style = MaterialTheme.typography.bodyLarge) } else Text("Letter content remains private until delivery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (letter.isOwner && !letter.recipientVerified && letter.canCancel) {
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
    BackHandler(enabled = !busy) { recorder.discard(); close() }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Capture") }
            Text(if (interviewId == null) "Record audio" else "Audio response", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        }
        Text("The recording stays private and encrypted until Narratrace verifies durable preservation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        )
                        if (queued == null) { message = "The recording could not be encrypted safely."; busy = false }
                        else scope.launch {
                            val remaining = container.mediaRepository.reconcile()
                            message = if (remaining == 0) "Saved securely. The local recording was removed after verified preservation."
                            else "Protected on this device. Narratrace will retry when secure transfer is available."
                            busy = false
                        }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
        ) { Text(if (recording) "Stop and preserve" else "Start recording") }
        if (recording) Text("%02d:%02d remaining".format(remainingSeconds / 60, remainingSeconds % 60), style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = if (it.startsWith("The recording could not") || it.startsWith("No recording")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        val waiting = container.mediaRepository.queue.items().size
        if (waiting > 0) {
            Text("$waiting protected upload${if (waiting == 1) "" else "s"} waiting.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { busy = true; scope.launch { container.mediaRepository.reconcile(); busy = false } }, enabled = !busy) { Text("Retry protected uploads") }
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
    var key by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) { result = container.mediaRepository.interviews(); legal = container.mediaRepository.legal() }
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Capture") }
            Text("Guided interviews", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        } }
        item { Text("Companion uses your responses to suggest thoughtful follow-up questions. AI may make mistakes; review generated material before relying on or sharing it.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (legal is FeatureResult.Success && !(legal as FeatureResult.Success<io.narratrace.android.core.media.LegalAcceptance>).value.aiNoticeAcknowledged) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Review required", style = MaterialTheme.typography.titleMedium)
                Text("By continuing, you accept the current Terms and Privacy Policy and acknowledge the AI notice.")
                Button(onClick = { accepting = true; scope.launch { legal = container.mediaRepository.acceptLegal(); accepting = false } }, enabled = !accepting) { Text("Accept and continue") }
            } } }
        }
        item { OutlinedTextField(name, { name = it.take(120); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Who is this story about?") }, singleLine = true) }
        item { OutlinedTextField(relation, { relation = it.take(120); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Relationship (optional)") }, singleLine = true) }
        item { Button(onClick = { creating = true; scope.launch {
            when (val made = container.mediaRepository.createInterview(name, relation, null, key)) {
                is FeatureResult.Success -> { name = ""; relation = ""; key = UUID.randomUUID().toString(); selected = made.value.interview }
                else -> Unit
            }; creating = false
        } }, modifier = Modifier.fillMaxWidth(), enabled = !creating && name.trim().isNotEmpty() && (legal as? FeatureResult.Success)?.value?.aiNoticeAcknowledged == true) { Text("Start interview") } }
        item { Text("Your interviews", style = MaterialTheme.typography.titleLarge) }
        when (val loaded = result) {
            null -> item { CircularProgressIndicator() }
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
    var videoMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (mime !in setOf("video/mp4", "video/quicktime")) { videoMessage = "Choose an MP4 or QuickTime video."; return@launch }
            val item = runCatching { context.contentResolver.openInputStream(uri)?.use { container.mediaRepository.queue.enqueueVideoStream(
                it, PendingMediaKind.InterviewVideo, "interview-${UUID.randomUUID()}.${if (mime == "video/quicktime") "mov" else "mp4"}", mime, summary.id,
            ) } }.getOrNull()
            if (item == null) videoMessage = "This video could not be encrypted safely or exceeds the 2 GB limit."
            else { videoMessage = if (container.mediaRepository.reconcile() == 0) "Video response preserved." else "Video protected on this device and waiting for secure transfer."; refresh++ }
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
        confirmButton = { Button(onClick = { confirmNarrativeAgreement = false; sending = true; scope.launch { container.mediaRepository.narrative(summary.id, true); sending = false; refresh++ } }) { Text("I agree — create faithful story") } },
        dismissButton = { TextButton(onClick = { confirmNarrativeAgreement = false }) { Text("Cancel") } },
    )
    if (audio) { AudioCaptureScreen(container, modifier, summary.id, (capacity as? FeatureResult.Success)?.value?.audioMaxSeconds) { audio = false; refresh++ }; return }
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to interviews") }
            Text(summary.subjectName, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        } }
        item { Text("This interview is private. Companion suggestions are AI-generated and should be reviewed.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when (val loaded = result) {
            null -> item { CircularProgressIndicator() }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify this interview.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Success -> {
                items(loaded.value.messages, key = { it.id }) { message -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(if (message.role == "assistant") "Companion" else "You", style = MaterialTheme.typography.labelMedium)
                    Text(message.content)
                    if (message.hasMedia) Text("Protected ${message.mediaType ?: "media"} response", style = MaterialTheme.typography.bodySmall)
                } } }
                if (loaded.value.interview.status != "complete") {
                    item { OutlinedTextField(response, { response = it.take(4000); key = UUID.randomUUID().toString() }, Modifier.fillMaxWidth(), label = { Text("Your response") }, minLines = 3) }
                    item { Button(onClick = { sending = true; scope.launch {
                        if (container.mediaRepository.respond(summary.id, response, key) is FeatureResult.Success) { response = ""; key = UUID.randomUUID().toString(); refresh++ }
                        sending = false
                    } }, enabled = !sending && response.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Send response") } }
                    item { Button(onClick = { audio = true }, enabled = (capacity as? FeatureResult.Success)?.value?.audioMaxSeconds?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth()) { Text("Record audio response") } }
                    item { Button(onClick = { videoPicker.launch("video/*") }, enabled = (capacity as? FeatureResult.Success)?.value?.videoMaxSeconds?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth()) { Text("Add video response") } }
                    videoMessage?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    item { when (val available = capacity) {
                        is FeatureResult.Success -> Text("${available.value.remainingLabel} remains · audio up to ${available.value.audioMaxSeconds / 60}m ${available.value.audioMaxSeconds % 60}s. Capacity is checked again before transfer.", style = MaterialTheme.typography.bodySmall)
                        else -> Text("Recording capacity is unavailable. Refresh before recording audio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } }
                    item { Button(onClick = { sending = true; scope.launch { container.mediaRepository.status(summary.id, "complete"); sending = false; refresh++ } },
                        enabled = !sending && loaded.value.messages.any { it.role != "assistant" }, modifier = Modifier.fillMaxWidth()) { Text("Mark interview complete") } }
                    if (loaded.value.messages.none { it.role != "assistant" }) item { Text("Add at least one response before marking this interview complete.", style = MaterialTheme.typography.bodySmall) }
                } else item { Button(onClick = { sending = true; scope.launch { container.mediaRepository.status(summary.id, "active"); sending = false; refresh++ } }, modifier = Modifier.fillMaxWidth()) { Text("Reopen interview") } }
                loaded.value.narrative?.let { narrative -> item { Text("Narrative", style = MaterialTheme.typography.titleLarge) }; item { Text(narrative) } }
                item { Text("Interview coverage", style = MaterialTheme.typography.titleLarge) }
                item { when (val value = insights) {
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
                item { TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete interview", color = MaterialTheme.colorScheme.error) } }
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
            if (saving) CircularProgressIndicator() else Text("Save securely")
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
                Text(if (letter.delivered) "Delivered" else "Private until delivery", style = MaterialTheme.typography.bodySmall)
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
        Button({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.narratrace.io/yearbook"))) }, Modifier.fillMaxWidth()) { Text("Open Yearbooks on the web") }
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
        when (val current = result) { null -> item { CircularProgressIndicator() }; FeatureResult.AuthenticationRequired -> item { Text("Sign in again to verify Activity.", color = MaterialTheme.colorScheme.error) }; is FeatureResult.Unavailable -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }; is FeatureResult.Success -> if (current.value.items.isEmpty()) item { Text("Nothing needs your attention.") } else items(current.value.items, key = { "activity:${it.id}" }) { item -> Card(Modifier.fillMaxWidth().clickable(enabled = item.kind == "processing") { selected = item.id }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(item.title ?: item.announcement ?: "Narratrace update", style = MaterialTheme.typography.titleMedium); item.body?.let { Text(it) }; item.progress?.let { LinearProgressIndicator({ it.coerceIn(0,100) / 100f }, Modifier.fillMaxWidth()) }; if (item.kind == "processing") Text("Open processing details", style = MaterialTheme.typography.labelMedium) } } } }
    }
}

@Composable
private fun ProcessingDetailScreen(container: AppContainer, id: String, modifier: Modifier, close: () -> Unit) {
    var result by remember(id) { mutableStateOf<FeatureResult<ProcessingJob>?>(null) }; var refresh by remember { mutableStateOf(0) }; var retrying by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    LaunchedEffect(id, refresh) { result = container.supportRepository.processing(id) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Activity") }; Text("Processing details", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        when (val current = result) { null -> CircularProgressIndicator(); FeatureResult.AuthenticationRequired -> Text("Sign in again to verify this processing item.", color = MaterialTheme.colorScheme.error); is FeatureResult.Unavailable -> Text(current.message, color = MaterialTheme.colorScheme.error); is FeatureResult.Success -> { val job = current.value; Text(job.state.replace('_',' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleLarge); Text(job.failureCategory ?: if (job.state == "preserved") "The original is preserved." else "The original remains protected while processing continues."); Text("Type: ${job.jobType.replace('_',' ').replaceFirstChar(Char::uppercase)}"); Text("Updated: ${job.updatedAt}"); job.progress?.let { LinearProgressIndicator({ it.coerceIn(0,100) / 100f }, Modifier.fillMaxWidth()) }; if (job.canRetry) { Button({ retrying = true; scope.launch { container.supportRepository.retryProcessing(id); retrying = false; refresh++ } }, enabled = !retrying, modifier = Modifier.fillMaxWidth()) { Text(if (retrying) "Retrying…" else "Try processing again") }; Text("Retrying optional processing does not replace or remove the preserved original.", style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable
private fun LoadingSurface(modifier: Modifier, title: String, message: String) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        CircularProgressIndicator()
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
            CircularProgressIndicator()
            Text("Loading your private Library…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var caption by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(mediaId) {
        result = container.mediaRepository.mediaDetail(mediaId)
        val detail = (result as? FeatureResult.Success)?.value?.media
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
                    factory = { ctx -> VideoView(ctx).apply { val controls = MediaController(ctx); controls.setAnchorView(this); setMediaController(controls); setVideoPath(url); setOnPreparedListener { start() } } },
                    modifier = Modifier.fillMaxWidth().height(if (detail.kind == "video") 260.dp else 80.dp),
                ) }
                detail.caption?.takeIf(String::isNotBlank)?.let { Text(it) }
                detail.transcript?.takeIf(String::isNotBlank)?.let { Text("Transcript", style = MaterialTheme.typography.titleLarge); Text(it) }
                detail.summary?.takeIf(String::isNotBlank)?.let { Text("Summary", style = MaterialTheme.typography.titleLarge); Text(it) }
                if (detail.tags.isNotEmpty() || detail.customTags.isNotEmpty()) Text((detail.tags + detail.customTags).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(caption, { caption = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Caption") })
                Button(onClick = { busy = true; scope.launch { if (container.mediaRepository.updateCaption(mediaId, caption) is FeatureResult.Success) result = container.mediaRepository.mediaDetail(mediaId); busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save caption") }
                OutlinedTextField(tags, { tags = it.take(320) }, Modifier.fillMaxWidth(), label = { Text("Custom tags") }, supportingText = { Text("Comma-separated; up to 10 tags") })
                Button(onClick = { busy = true; scope.launch { if (container.mediaRepository.updateTags(mediaId, tags.split(',')) is FeatureResult.Success) result = container.mediaRepository.mediaDetail(mediaId); busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save tags") }
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
        Text("The recipient cannot access this artifact before its delivery time. External recipients must verify their address first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if (changing) CircularProgressIndicator()
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
        when (val current = result) {
            null -> {
                CircularProgressIndicator()
                Text("Loading your private Home…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        null -> CircularProgressIndicator()
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
    "individual" -> "Individual"
    "family" -> "Family"
    "extended_family" -> "Extended Family"
    else -> "Trial"
}

private fun String.statusLabel(): String = when (this) {
    "trial_active" -> "Trial active"
    "trial_extended" -> "Extended trial active"
    "trial_expired" -> "Trial ended"
    "subscription_active" -> "Active"
    "subscription_grace" -> "Payment grace period"
    "lapsed" -> "Archive only"
    "vault_only" -> "Vault access"
    else -> "Access unavailable"
}
