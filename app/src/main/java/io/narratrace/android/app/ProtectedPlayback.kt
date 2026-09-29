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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import java.net.URI

/** One question player per interview; superseded and background loads cannot play. */
internal class QuestionSpeechPlayback(private val scope: CoroutineScope, private val recording: Boolean = false) {
    var messageId by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var failedMessageId by mutableStateOf<String?>(null)
        private set
    var preparing by mutableStateOf(false)
        private set
    private var player: MediaPlayer? = null
    private var audioSource: MediaDataSource? = null
    private var job: Job? = null
    private var generation = 0
    fun stop() { preparing = false; generation++; job?.cancel(); job = null; player?.release(); player = null; audioSource?.close(); audioSource = null; messageId = null }
    fun toggle(id: String, load: suspend () -> FeatureResult<ByteArray>) {
        val stopping = messageId == id
        stop(); error = null; failedMessageId = null
        if (stopping) return
        messageId = id
        preparing = true
        val current = generation
        job = scope.launch {
            when (val result = load()) {
                is FeatureResult.Success -> {
                    val bytes = result.value
                    if (current != generation) { bytes.fill(0); return@launch }
                    val source = object : MediaDataSource() {
                        override fun getSize() = bytes.size.toLong()
                        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                            if (position < 0 || position >= bytes.size) return -1
                            val count = minOf(size, bytes.size - position.toInt())
                            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
                            return count
                        }
                        override fun close() { bytes.fill(0) }
                    }
                    audioSource = source
                    try {
                        val active = MediaPlayer()
                        player = active
                        active.setAudioAttributes(android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                        active.setDataSource(source)
                        active.setOnPreparedListener { if (current == generation) { preparing = false; it.start() } }
                        active.setOnCompletionListener { if (current == generation) stop() }
                        active.setOnErrorListener { _, _, _ -> if (current == generation) { stop(); failedMessageId = id; error = if (recording) "Recording could not play. Try again." else "Read-aloud could not play. Tap the speaker to try again." }; true }
                        active.prepareAsync()
                    } catch (failure: Exception) {
                        source.close()
                        if (failure is CancellationException) throw failure
                        if (current == generation) { stop(); failedMessageId = id; error = if (recording) "Recording is unavailable. Try again." else "Read-aloud is unavailable. Tap the speaker to try again." }
                    }
                }
                is FeatureResult.Unavailable -> if (current == generation) { stop(); failedMessageId = id; error = result.message }
                FeatureResult.AuthenticationRequired -> if (current == generation) { stop(); failedMessageId = id; error = if (recording) "Sign in again to play this recording." else "Sign in again to read this question aloud." }
            }
        }
    }
}

@Composable
internal fun rememberQuestionSpeech(container: AppContainer, interviewId: String): QuestionSpeechPlayback {
    val scope = rememberCoroutineScope()
    val auth by container.sessionManager.state.collectAsState()
    val owner = (auth as? io.narratrace.android.core.auth.AuthState.Authenticated)?.session?.accountId
    val player = remember(interviewId, owner) { QuestionSpeechPlayback(scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.stop() }
    }
    return player
}

internal fun allowedStreamPlayback(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
        uri.host?.endsWith(".cloudflarestream.com") == true && uri.path.endsWith("/manifest/video.m3u8")
}.getOrDefault(false)

/** Originals remain in memory; leaving/backgrounding releases the player and buffer. */
@Composable
internal fun ProtectedAudioButton(label: String, load: suspend () -> FeatureResult<ByteArray>) {
    val scope = rememberCoroutineScope()
    val playback = remember { QuestionSpeechPlayback(scope, recording = true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(playback, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) playback.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); playback.stop() }
    }
    Button(onClick = { playback.toggle("recording", load) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (playback.preparing) "Cancel opening recording" else if (playback.messageId != null) "Stop playback" else label)
    }
    playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val recorder = remember { io.narratrace.android.core.media.SecureAudioRecorder(context) }
    val recordingState by recorder.state.collectAsState()
    val hasRecording = recordingState != io.narratrace.android.core.media.SecureAudioRecorder.State.Idle
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun begin() { if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !recorder.start(15 * 60)) message = "Recording could not start." }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else message = "Allow microphone access to record your voice."
    }
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(recorder)
        onDispose { lifecycle.removeObserver(recorder); recorder.discard(); bytes?.fill(0) }
    }
    if (recordingState == io.narratrace.android.core.media.SecureAudioRecorder.State.Interrupted) Text("Recording interrupted or finished. Stop recording to keep this take.")
    Text("Attach or replace your voice on this saved Letter. A delivery already sent is not recalled or sent again.")
    OutlinedButton(onClick = {
        if (hasRecording) { bytes?.fill(0); bytes = recorder.stop() }
        else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(android.Manifest.permission.RECORD_AUDIO)
    }, enabled = !busy) { Text(if (hasRecording) "Stop recording" else "Record attached voice") }
    if (bytes != null) Button(onClick = { busy = true; scope.launch {
        when (val result = container.lettersRepository.attachAudio(letterId, bytes!!)) {
            is FeatureResult.Success -> if (result.value.preserved) { bytes?.fill(0); bytes = null; message = "Voice attached to the saved Letter."; saved() } else message = "Attachment was not confirmed. Try again."
            is FeatureResult.Unavailable -> message = result.message
            FeatureResult.AuthenticationRequired -> message = "Sign in again before attaching your voice."
        }; busy = false
    } }, enabled = !busy && !hasRecording) { Text("Attach recording") }
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

@Composable
internal fun ProtectedStreamVideo(url: String, title: String) {
    var playing by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, url) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) playing = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!allowedStreamPlayback(url)) { Text("This protected video could not be verified.", color = MaterialTheme.colorScheme.error); return }
    Button({ playing = !playing; failed = false }, Modifier.fillMaxWidth()) { Text(if (playing) "Stop video" else "Play video") }
    if (playing) AndroidView(factory = { context -> VideoView(context).apply {
        contentDescription = "Video for $title"
        val controls = MediaController(context)
        controls.setAnchorView(this); setMediaController(controls); setVideoPath(url)
        setOnPreparedListener { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start() }
        setOnErrorListener { _, _, _ -> playing = false; failed = true; true }
    } }, modifier = Modifier.fillMaxWidth().height(260.dp), onRelease = { it.stopPlayback() })
    if (failed) Text("Video playback failed. Try again.", color = MaterialTheme.colorScheme.error)
}
