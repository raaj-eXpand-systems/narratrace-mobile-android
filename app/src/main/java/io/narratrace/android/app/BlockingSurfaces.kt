package io.narratrace.android.app

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.narratrace.android.core.family.BlockSource
import io.narratrace.android.core.family.UserBlock
import io.narratrace.android.core.family.UserBlockList
import io.narratrace.android.core.media.FeatureResult
import kotlinx.coroutines.launch

internal const val BLOCK_EXPLANATION = "Blocking removes this person from groups you own. You leave other groups you share with them. Deliveries between you are revoked, and departing members’ shared Memories become private. Unblocking does not restore memberships, sharing, or deliveries."

@Composable
internal fun BlockPersonButton(container: AppContainer, source: BlockSource, label: String) {
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    TextButton(onClick = { confirming = true; error = null }, enabled = !busy) { Text("Block $label", color = MaterialTheme.colorScheme.error) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (confirming) AlertDialog(
        onDismissRequest = { if (!busy) confirming = false }, title = { Text("Block $label?") }, text = { Text(BLOCK_EXPLANATION) },
        confirmButton = { Button(enabled = !busy, onClick = { busy = true; scope.launch {
            error = when (val result = container.userBlocksRepository.block(source)) {
                is FeatureResult.Success -> { Toast.makeText(context, "Person blocked. Shared access refreshed.", Toast.LENGTH_LONG).show(); null }
                FeatureResult.AuthenticationRequired -> "Sign in again to block this person."
                is FeatureResult.Unavailable -> result.message
            }
            busy = false; confirming = false
        } }) { Text(if (busy) "Blocking…" else "Block person") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirming = false }) { Text("Cancel") } },
    )
}

@Composable
internal fun BlockedPeopleScreen(container: AppContainer, modifier: Modifier, close: () -> Unit) {
    var result by remember { mutableStateOf<FeatureResult<UserBlockList>?>(null) }
    var pending by remember { mutableStateOf<UserBlock?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(refresh) { result = null; result = container.userBlocksRepository.list() }
    BackHandler(onBack = close)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = close) { Text("Back to preferences") }; Text("Blocked people", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
        item { Text("Only people you blocked appear here. Account references protect their private contact information. Unblocking does not restore memberships, sharing, or deliveries.") }
        when (val loaded = result) {
            null -> item { Text("Loading blocked people…"); CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading blocked people" }) }
            FeatureResult.AuthenticationRequired -> item { Text("Sign in again to view blocked people.", color = MaterialTheme.colorScheme.error) }
            is FeatureResult.Unavailable -> item { Text(loaded.message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refresh++ }) { Text("Retry") } }
            is FeatureResult.Success -> {
                if (loaded.value.blocks.isEmpty()) item { Text("No blocked people") }
                items(loaded.value.blocks, key = { it.accountId }) { block -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text("Account reference: ${block.accountId}", style = MaterialTheme.typography.bodySmall)
                    Text("Blocked: ${block.createdAt}", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = { pending = block; error = null }) { Text("Unblock") }
                } } }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
    pending?.let { block -> AlertDialog(
        onDismissRequest = { if (!busy) pending = null }, title = { Text("Unblock this person?") },
        text = { Text("Memberships, sharing, and deliveries will not be restored. You may choose to reconnect separately.") },
        confirmButton = { Button(enabled = !busy, onClick = { busy = true; scope.launch {
            error = when (val changed = container.userBlocksRepository.unblock(block.accountId)) {
                is FeatureResult.Success -> { Toast.makeText(context, "Person unblocked. Previous sharing was not restored.", Toast.LENGTH_LONG).show(); null }
                FeatureResult.AuthenticationRequired -> "Sign in again to unblock this person."
                is FeatureResult.Unavailable -> changed.message
            }
            busy = false; pending = null; refresh++
        } }) { Text(if (busy) "Unblocking…" else "Unblock") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("Cancel") } },
    ) }
}
