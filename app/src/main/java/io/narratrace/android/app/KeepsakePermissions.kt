package io.narratrace.android.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.narratrace.android.core.media.*
import kotlinx.coroutines.launch

@Composable
private fun <T> ServerPanel(load: suspend () -> FeatureResult<T>, content: @Composable (T, Boolean, ((suspend () -> FeatureResult<PermissionSaved>) -> Unit)) -> Unit) {
    var value by remember { mutableStateOf<FeatureResult<T>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { value = load() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val result = value) {
            null -> Text("Loading permissions…")
            FeatureResult.AuthenticationRequired -> Text("Sign in again to manage permissions.")
            is FeatureResult.Unavailable -> {
                Text(result.message)
            }
            is FeatureResult.Success -> content(result.value, busy) { operation ->
                busy = true
                message = null
                scope.launch {
                    when (val saved = operation()) {
                        is FeatureResult.Success -> {
                            message = if (saved.value.ok) "Saved." else "This change could not be saved."
                            value = if (saved.value.ok) load() else FeatureResult.Unavailable("Refresh permissions before trying again.")
                        }
                        FeatureResult.AuthenticationRequired -> { message = "Sign in again to manage permissions."; value = FeatureResult.AuthenticationRequired }
                        is FeatureResult.Unavailable -> { message = saved.message; value = saved }
                    }
                    busy = false
                }
            }
        }
        TextButton(enabled = !busy && value != null, onClick = { scope.launch { value = null; value = load() } }) { Text("Refresh permissions") }
        message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

@Composable
internal fun PublicStoryLinksPanel(container: AppContainer) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Public story links", style = MaterialTheme.typography.titleLarge)
        Text("Anyone with an active link can read the story. Revoking it stops future access but cannot recall copies already saved.")
        ServerPanel(load = { container.mediaRepository.publicLinks() }) { inventory, busy, mutate ->
            if (inventory.links.isEmpty()) Text("You have no active public story links.")
            inventory.links.forEach { link ->
                Text(link.subject_name, style = MaterialTheme.typography.titleMedium)
                (link.share_expires_at?.let { "Expires: $it" } ?: link.created_at?.let { "Story created: $it" })?.let { Text(it) }
                TextButton(enabled = !busy, onClick = { mutate { container.mediaRepository.revokePublicLink(link.id) } }) { Text("Revoke link for ${link.subject_name}") }
            }
        }
    }
}

@Composable
internal fun KeepsakeConsentPanel(container: AppContainer, interviewId: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Keepsake permissions", style = MaterialTheme.typography.titleLarge)
        Text("Allow a family member to use this chapter or your uploaded photos in their keepsake books. Revoking permission cannot recall copies already downloaded.")
        key(interviewId) { ServerPanel(load = { container.mediaRepository.keepsakeMembers(interviewId) }) { inventory, busy, mutate ->
            if (inventory.members.isEmpty()) Text("No eligible family members.")
            inventory.members.forEach { member ->
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                listOf("chapter" to member.chapterConsent, "photos" to member.photoConsent).forEach { (permission, granted) ->
                    TextButton(enabled = !busy && member.canChange(permission), onClick = { mutate { container.mediaRepository.keepsakePermission(interviewId, member.accountId, permission, !granted) } }) {
                        Text("${if (granted) "Revoke" else "Allow"} ${if (permission == "photos") "photo use" else "chapter use"} for ${member.name}")
                    }
                }
            }
        } }
    }
}

@Composable
internal fun KeepsakeOmissionsPanel(container: AppContainer) {
    var requested by remember { mutableStateOf(setOf<String>()) }
    Text("Keepsake chapter permissions", style = MaterialTheme.typography.titleLarge)
    ServerPanel(load = { container.mediaRepository.keepsakeOmissions() }) { inventory, busy, mutate ->
        if (inventory.omitted == null) Text("Chapter permission information is unavailable. Refresh or check your keepsake book on the web.")
        else if (inventory.omitted.isEmpty()) Text("No storytellers’ chapters are waiting for permission.")
        inventory.omitted.orEmpty().forEach { storyteller ->
            Text("${storyteller.subjectName}: chapter left out until permission is granted.")
            TextButton(enabled = !busy && storyteller.id !in requested, onClick = {
                mutate {
                    val outcome = container.mediaRepository.requestKeepsakePermission(storyteller.id)
                    if (outcome is FeatureResult.Success && outcome.value.ok) requested = requested + storyteller.id
                    outcome
                }
            }) { Text(if (storyteller.id in requested) "Permission requested" else "Ask ${storyteller.subjectName} for permission") }
        }
    }
}
