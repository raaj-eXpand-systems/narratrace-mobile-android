package io.narratrace.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.narratrace.android.core.customer.DeliveryContact
import io.narratrace.android.core.customer.DeliveryContactChallenge
import io.narratrace.android.core.media.FeatureResult
import kotlinx.coroutines.launch

@Composable
internal fun DeliveryContactScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repository = container.deliveryContactRepository
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf<DeliveryContact?>(null) }
    var challenge by remember { mutableStateOf<DeliveryContactChallenge?>(null) }
    var busy by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        busy = true; loaded = false; challenge = null; code = ""; contact = null; email = ""
        when (val result = repository.load()) {
            is FeatureResult.Success -> { contact = result.value.contact; email = result.value.contact.email.orEmpty(); loaded = true; message = if (contact?.status == "stale") "Please verify this address again before scheduling another delivery." else null }
            is FeatureResult.Unavailable -> message = result.message
            FeatureResult.AuthenticationRequired -> message = "Sign in again to verify your delivery email."
        }
        busy = false
    }
    BackHandler(onBack = close)
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text("Delivery email", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        Text("Use your own email address that you expect to keep. Narratrace uses it for important account notices and scheduled-delivery problems.")
        Text("This is not the Letter recipient and does not change how you sign in.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (contact?.status == "verified") Text("Verified: ${contact?.email}")
        OutlinedTextField(email, { email = it.take(254); challenge = null; code = ""; message = null }, Modifier.fillMaxWidth(), label = { Text("Your delivery email") }, singleLine = true, enabled = loaded && !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedButton(onClick = { busy = true; challenge = null; code = ""; message = null; scope.launch {
            when (val result = repository.request(email)) {
                is FeatureResult.Success -> { challenge = result.value; message = "Code sent. Check your inbox and junk folder." }
                is FeatureResult.Unavailable -> message = result.message
                FeatureResult.AuthenticationRequired -> message = "Sign in again before requesting a code."
            }; busy = false
        } }, enabled = loaded && !busy && email.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (challenge == null) "Send verification code" else "Send a new code") }
        challenge?.let { current ->
            Text("Enter the 6-digit code from your email. It expires at ${current.expiresAt}.")
            OutlinedTextField(code, { code = it.filter { char -> char in '0'..'9' }.take(6) }, Modifier.fillMaxWidth(), label = { Text("6-digit code") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Button(onClick = { busy = true; message = null; scope.launch {
                when (val result = repository.verify(current.challengeId, code)) {
                    is FeatureResult.Success -> { contact = result.value.contact; if (contact?.status == "verified") { challenge = null; code = ""; message = "Your delivery email is verified. Return to your draft and try scheduling it again." } else message = "Please request a new code and try again." }
                    is FeatureResult.Unavailable -> message = result.message
                    FeatureResult.AuthenticationRequired -> message = "Sign in again before verifying your email."
                }; busy = false
            } }, enabled = !busy && code.length == 6, modifier = Modifier.fillMaxWidth()) { Text("Verify email address") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (!loaded && !busy) Button({ refresh++ }) { Text("Retry") }
    }
}
