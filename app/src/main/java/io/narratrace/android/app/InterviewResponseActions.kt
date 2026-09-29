package io.narratrace.android.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.narratrace.android.core.media.FeatureResult
import io.narratrace.android.core.media.InterviewMessage
import kotlinx.coroutines.launch

@Composable
internal fun InterviewResponseActions(container: AppContainer, interviewId: String, response: InterviewMessage, changed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember(response.id) { mutableStateOf(false) }
    var removing by remember(response.id) { mutableStateOf(false) }
    var content by remember(response.id, response.content) { mutableStateOf(response.content) }
    var busy by remember(response.id) { mutableStateOf(false) }
    var failure by remember(response.id) { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ editing = true }, enabled = !busy) { Text("Correct transcript") }
        TextButton({ removing = true }, enabled = !busy) { Text("Remove from record", color = MaterialTheme.colorScheme.error) }
    }
    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (editing) AlertDialog(onDismissRequest = { if (!busy) editing = false }, title = { Text("Correct transcript") }, text = {
        Column { Text("Correct what was said. The original recording is kept; derived insights will be refreshed.")
            OutlinedTextField(content, { content = it.take(4_000) }, Modifier.fillMaxWidth(), enabled = !busy, minLines = 4, label = { Text("Transcript") })
        }
    }, confirmButton = { Button({ busy = true; scope.launch {
        when (val result = container.mediaRepository.correctTranscript(interviewId, response.id, content)) {
            is FeatureResult.Success -> { editing = false; failure = null; changed() }
            is FeatureResult.Unavailable -> failure = result.message
            FeatureResult.AuthenticationRequired -> failure = "Sign in again to correct this response."
        }; busy = false
    } }, enabled = !busy && content.isNotBlank()) { Text(if (busy) "Saving…" else "Save correction") } }, dismissButton = { TextButton({ editing = false }, enabled = !busy) { Text("Cancel") } })
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text("Remove this response?") }, text = { Text("This permanently removes this response, its recording, and the associated exchange. You will confirm with a verification code.") }, confirmButton = { TextButton({ removing = false; busy = true; scope.launch {
        when (val result = container.mediaRepository.removeResponse(interviewId, response.id)) {
            is FeatureResult.Success -> { failure = null; changed() }
            is FeatureResult.Unavailable -> failure = result.message
            FeatureResult.AuthenticationRequired -> failure = "Sign in again to remove this response."
        }; busy = false
    } }) { Text("Remove from record", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ removing = false }) { Text("Keep response") } })
}
