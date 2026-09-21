package io.narratrace.android.app

import android.media.MediaDataSource
import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.narratrace.android.core.media.FeatureResult
import kotlinx.coroutines.launch
import java.net.URI

internal fun allowedStreamPlayback(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
        uri.host?.endsWith(".cloudflarestream.com") == true && uri.path.endsWith("/manifest/video.m3u8")
}.getOrDefault(false)

/** Originals remain in memory; leaving/backgrounding releases the player and buffer. */
@Composable
internal fun ProtectedAudioButton(label: String, load: suspend () -> FeatureResult<ByteArray>) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun stop() { player?.release(); player = null }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    Button(onClick = {
        if (player != null) stop() else { busy = true; message = null; scope.launch {
            when (val result = load()) {
                is FeatureResult.Success -> {
                    val bytes = result.value
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { bytes.fill(0); busy = false; return@launch }
                    val source = object : MediaDataSource() {
                        override fun getSize() = bytes.size.toLong()
                        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                            if (position < 0 || position >= bytes.size) return -1
                            val count = minOf(size, bytes.size - position.toInt())
                            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count); return count
                        }
                        override fun close() { bytes.fill(0) }
                    }
                    val active = MediaPlayer()
                    player = active
                    runCatching {
                        active.setDataSource(source)
                        active.setOnPreparedListener { it.start(); busy = false }
                        active.setOnCompletionListener { stop() }
                        active.setOnErrorListener { _, _, _ -> message = "Playback failed. Try again."; busy = false; stop(); true }
                        active.prepareAsync()
                    }.onFailure { message = "Playback could not start. Try again."; busy = false; stop() }
                }
                is FeatureResult.Unavailable -> { message = result.message; busy = false }
                FeatureResult.AuthenticationRequired -> { message = "Sign in again to play this recording."; busy = false }
            }
        } }
    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Opening recording…" else if (player != null) "Stop playback" else label) }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
internal fun InterviewRecording(container: AppContainer, interviewId: String, messageId: String, kind: String?) {
    if (kind != "video") {
        ProtectedAudioButton("Play audio response") { container.mediaRepository.interviewAudio(interviewId, messageId) }
        return
    }
    var url by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) url = null }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Button(onClick = { busy = true; scope.launch {
        when (val result = container.mediaRepository.interviewVideo(interviewId, messageId)) {
            is FeatureResult.Success -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && allowedStreamPlayback(result.value.url)) { url = result.value.url; message = null } else message = "The protected video URL could not be verified."
            is FeatureResult.Unavailable -> message = result.message
            FeatureResult.AuthenticationRequired -> message = "Sign in again to play this video."
        }; busy = false
    } }, enabled = !busy) { Text(if (url == null) "Play video response" else "Reload video") }
    url?.let { location -> key(location) { AndroidView(
        factory = { ctx -> VideoView(ctx).apply {
            contentDescription = "Protected video response"
            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
            setVideoPath(location)
            setOnPreparedListener { start() }
            setOnErrorListener { _, _, _ -> message = "Video playback failed. Reload to try again."; true }
        } }, modifier = Modifier.fillMaxWidth().height(240.dp), onRelease = { it.stopPlayback() },
    ) } }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
internal fun AccountAccessNotice() {
    Text("Your trial includes one guided interview. This feature requires an eligible Narratrace plan. If your access has changed, tap Refresh access.")
}

@Composable
internal fun LetterVoiceAttachment(container: AppContainer, letterId: String, saved: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recorder = remember { io.narratrace.android.core.media.SecureAudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun begin() { recording = recorder.start(15 * 60); if (!recording) message = "Recording could not start." }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else message = "Allow microphone access to record your voice."
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && recording) { bytes = recorder.stop(); recording = false } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); recorder.discard(); bytes?.fill(0) }
    }
    Text("Attach or replace your voice on this saved Letter. A delivery already sent is not recalled or sent again.")
    OutlinedButton(onClick = {
        if (recording) { bytes?.fill(0); bytes = recorder.stop(); recording = false }
        else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(android.Manifest.permission.RECORD_AUDIO)
    }, enabled = !busy) { Text(if (recording) "Stop recording" else "Record attached voice") }
    if (bytes != null) Button(onClick = { busy = true; scope.launch {
        when (val result = container.lettersRepository.attachAudio(letterId, bytes!!)) {
            is FeatureResult.Success -> if (result.value.preserved) { bytes?.fill(0); bytes = null; message = "Voice attached to the saved Letter."; saved() } else message = "Attachment was not confirmed. Try again."
            is FeatureResult.Unavailable -> message = result.message
            FeatureResult.AuthenticationRequired -> message = "Sign in again before attaching your voice."
        }; busy = false
    } }, enabled = !busy && !recording) { Text("Attach recording") }
    message?.let { Text(it) }
}

@Composable
internal fun ContentReportButton(container: AppContainer, kind: String, id: String) {
    var open by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    TextButton(onClick = { open = true }) { Text("Report content") }
    if (open) AlertDialog(
        onDismissRequest = { if (!busy) open = false },
        title = { Text("Report content") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This sends the $kind reference and your explanation privately to Narratrace support. No recording, photo, transcript or Letter text is attached automatically.")
            OutlinedTextField(reason, { reason = it.take(5_000) }, label = { Text("Describe the concern") })
            message?.let { Text(it) }
        } },
        confirmButton = { TextButton(onClick = { busy = true; scope.launch {
            val profile = container.settingsRepository.profile()
            val name = (profile as? FeatureResult.Success)?.value?.profile?.displayName
            if (name == null) message = "Your profile could not be verified. Please try again."
            else when (val result = container.supportRepository.submitFeedback(name, "issue", reason, "Android $kind: ${id.take(120)}", null)) {
                is FeatureResult.Success -> if (result.value) { message = "Report sent privately."; reason = "" } else message = "Report was not confirmed. Try again."
                is FeatureResult.Unavailable -> message = result.message
                FeatureResult.AuthenticationRequired -> message = "Sign in again to send this report."
            }; busy = false
        } }, enabled = !busy && reason.isNotBlank()) { Text("Send report") } },
        dismissButton = { TextButton(onClick = { open = false }, enabled = !busy) { Text("Close") } },
    )
}

@Composable
internal fun TrialAccessActions(completed: Boolean, openInterview: () -> Unit, refreshAccess: () -> Unit) {
    if (completed) Text("Your complimentary interview is complete. You can still open your existing story.")
    AccountAccessNotice()
    Button(onClick = openInterview, modifier = Modifier.fillMaxWidth()) { Text(if (completed) "Open existing story" else "Open trial interview") }
    TextButton(onClick = refreshAccess, modifier = Modifier.fillMaxWidth()) { Text("Refresh access") }
}
