package io.narratrace.android.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun DeletionVerificationDialog(container: AppContainer) {
    val verification = container.deletionVerification
    val prompt by verification.prompt.collectAsState()
    val auth by container.sessionManager.state.collectAsState()
    LaunchedEffect(auth) { verification.cancel() }
    DisposableEffect(verification) { onDispose { verification.cancel() } }
    prompt?.let { current ->
        var code by remember(current) { mutableStateOf("") }
        AlertDialog(onDismissRequest = verification::cancel, title = { Text("Confirm deletion") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (current.method == "authenticator") "Enter the current 6-digit code from your authenticator app." else "Enter the 6-digit code sent to ${current.maskedEmail}.")
                Text("This code confirms only the item you selected. Nothing is deleted until verification succeeds.")
                OutlinedTextField(code, { code = it.filter { char -> char in '0'..'9' }.take(6) }, label = { Text("Verification code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                current.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { Button({ verification.submit(code) }, enabled = code.length == 6) { Text("Confirm deletion") } }, dismissButton = { TextButton(verification::cancel) { Text("Cancel") } })
    }
}
