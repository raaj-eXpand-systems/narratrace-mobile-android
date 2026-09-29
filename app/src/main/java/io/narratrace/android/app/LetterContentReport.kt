package io.narratrace.android.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.narratrace.android.core.media.FeatureResult
import kotlinx.coroutines.launch
import java.util.UUID

internal const val REPORTED_LETTER_MESSAGE = "This Letter has been reported. It is view-only until the report is resolved."

@Composable
internal fun LetterContentReportButton(container: AppContainer, id: String, onReported: () -> Unit) {
    var open by remember(id) { mutableStateOf(false) }
    var message by remember(id) { mutableStateOf("") }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var receipt by remember(id) { mutableStateOf<String?>(null) }
    val key = remember(id) { UUID.randomUUID().toString() }
    var sending by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    TextButton({ open = true }) { Text("Report content") }
    if (open) AlertDialog(
        onDismissRequest = { if (!sending) open = false },
        title = { Text("Report Letter content") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (receipt != null) { Text("Report submitted. Reference: $receipt"); Text(REPORTED_LETTER_MESSAGE) }
            else {
                Text("Describe your concern. This Letter and your report will be available to the support team for review. The Letter becomes view-only while the report is unresolved.")
                OutlinedTextField(message, { message = it.take(5_000) }, Modifier.fillMaxWidth(), enabled = !sending, label = { Text("Reason for reporting") }, minLines = 4, supportingText = { Text("${message.length} of 5,000 characters") })
                error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
            }
        } },
        confirmButton = {
            if (receipt != null) TextButton({ open = false }) { Text("Done") }
            else Button(onClick = { sending = true; error = null; scope.launch {
                when (val result = container.lettersRepository.report(id, message, key)) {
                    is FeatureResult.Success -> { receipt = result.value.reference; message = ""; onReported() }
                    is FeatureResult.Unavailable -> error = result.message
                    FeatureResult.AuthenticationRequired -> error = "Sign in again before reporting this Letter."
                }; sending = false
            } }, enabled = !sending && message.trim().isNotEmpty()) { Text(if (sending) "Submitting…" else "Submit report") }
        },
        dismissButton = { if (receipt == null) TextButton({ open = false }, enabled = !sending) { Text("Cancel") } },
    )
}
