package io.narratrace.android

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.os.Build
import android.hardware.SensorPrivacyManager
import android.os.ParcelFileDescriptor
import org.junit.Assume
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import io.narratrace.android.core.auth.KeystoreCredentialCipher
import io.narratrace.android.core.media.PendingMediaKind
import io.narratrace.android.core.media.ProtectedMediaQueue
import io.narratrace.android.core.media.SecureAudioRecorder
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uses real MediaRecorder/codec/Keystore, with disposable local fixtures and no account or network. */
class SecureAudioRecorderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var recorder: SecureAudioRecorder
    private lateinit var initialFiles: Set<String>

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun captureFiles() = context.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("capture-") && it.extension == "m4a" }.map { it.name }.toSet()

    @Before fun prepare() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        compose.setContent { Text("Local audio regression test — no uploads") }
        initialFiles = captureFiles()
        main { recorder = SecureAudioRecorder(context); compose.activity.lifecycle.addObserver(recorder) }
    }

    @After fun cleanup() {
        if (::recorder.isInitialized) main {
            compose.activity.lifecycle.removeObserver(recorder)
            recorder.discard()
        }
        assertEquals("Recording temporary files must be removed", initialFiles, captureFiles())
    }

    private fun begin(seconds: Int = 15) {
        main { assertTrue("Real recorder failed to start", recorder.start(seconds)) }
        SystemClock.sleep(1_100)
    }
    private fun awaitInterrupted() {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (recorder.state.value == SecureAudioRecorder.State.Recording && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        assertEquals(SecureAudioRecorder.State.Interrupted, recorder.state.value)
    }
    private fun stopBytes(): ByteArray {
        var bytes: ByteArray? = null
        main { bytes = recorder.stop(); assertNull(recorder.stop()) }
        return requireNotNull(bytes) { "No finalized audio returned" }
    }

    @Test fun realCaptureDecodesAndEncryptedQueueRoundTrips() {
        begin()
        val bytes = stopBytes()
        try {
            assertDecodes(bytes)
            val folder = File(context.cacheDir, "audio-queue-test-${UUID.randomUUID()}")
            val cipher = KeystoreCredentialCipher("audio-device-test-${UUID.randomUUID()}")
            try {
                val queue = ProtectedMediaQueue(folder, cipher) { "audio-test-owner" }
                val item = requireNotNull(queue.enqueue(bytes, PendingMediaKind.StandaloneAudio, "fixture.m4a", "audio/mp4"))
                assertArrayEquals(bytes, queue.read(item))
                assertFalse(File(folder, item.encryptedFilename).readBytes().contentEquals(bytes))
                assertTrue(queue.purgeAccountData())
            } finally { folder.deleteRecursively(); assertTrue(cipher.destroyKey()) }
        } finally { bytes.fill(0) }
    }

    @Test fun realActivityStopRetainsAudioAndReturnDoesNotResume() {
        begin()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        awaitInterrupted()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(SecureAudioRecorder.State.Interrupted, recorder.state.value)
        val bytes = stopBytes()
        try { assertDecodes(bytes) } finally { bytes.fill(0) }
    }

    @Test fun realDurationCallbackFinalizesAudioWithoutDoubleStop() {
        begin(seconds = 1)
        awaitInterrupted()
        val bytes = stopBytes()
        try { assertDecodes(bytes) } finally { bytes.fill(0) }
    }

    @Test fun realAudioFocusLossInterruptsAndDoesNotResumeOnGain() {
        begin()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val competing = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            main { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competing)) }
            awaitInterrupted()
        } finally { main { audio.abandonAudioFocusRequest(competing) } }
        assertEquals(SecureAudioRecorder.State.Interrupted, recorder.state.value)
        val bytes = stopBytes()
        try { assertDecodes(bytes) } finally { bytes.fill(0) }
    }

    @Test fun interruptedDiscardRemovesFileAndFreshCaptureWorks() {
        begin()
        main { recorder.interrupt(); recorder.interrupt(); recorder.discard(); recorder.discard() }
        assertEquals(initialFiles, captureFiles())
        begin()
        val bytes = stopBytes()
        try { assertDecodes(bytes) } finally { bytes.fill(0) }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    @Test fun emulatorScreenSleepWakeRetainsTakeWithoutRestart() {
        Assume.assumeTrue("Screen control is confined to emulator", Build.MODEL.contains("sdk"))
        begin()
        try {
            shell("input keyevent KEYCODE_SLEEP")
            awaitInterrupted()
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        assertEquals(SecureAudioRecorder.State.Interrupted, recorder.state.value)
        val bytes = stopBytes()
        try { assertDecodes(bytes) } finally { bytes.fill(0) }
    }

    @Test fun emulatorMicrophonePrivacyInterruptsWithoutAutoResume() {
        Assume.assumeTrue("System setting test runs only on the emulator", Build.MODEL.contains("sdk") && Build.VERSION.SDK_INT >= 31)
        val privacy = context.getSystemService(SensorPrivacyManager::class.java)
        Assume.assumeTrue(privacy.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE))
        Assume.assumeFalse(context.getSystemService(AudioManager::class.java).isMicrophoneMute)
        begin()
        try {
            shell("cmd sensor_privacy enable 0 microphone")
            awaitInterrupted()
        } finally { shell("cmd sensor_privacy disable 0 microphone") }
        assertEquals(SecureAudioRecorder.State.Interrupted, recorder.state.value)
        val bytes = stopBytes()
        try { assertDecodes(bytes) } finally { bytes.fill(0) }
    }

    private fun assertDecodes(bytes: ByteArray) {
        val file = File.createTempFile("decoder-test-", ".m4a", context.cacheDir)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            file.writeBytes(bytes)
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            assertTrue(format.getLong(MediaFormat.KEY_DURATION) > 100_000)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec
            codec.configure(format, null, null, 0); codec.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false; var outputEnded = false; var decodedBytes = 0
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (!outputEnded && SystemClock.elapsedRealtime() < deadline) {
                if (!inputEnded) {
                    val input = codec.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        val buffer = codec.getInputBuffer(input)!!
                        val size = extractor.readSampleData(buffer, 0)
                        inputEnded = size < 0
                        codec.queueInputBuffer(input, 0, maxOf(0, size), if (inputEnded) 0 else extractor.sampleTime,
                            if (inputEnded) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (!inputEnded) extractor.advance()
                    }
                }
                val output = codec.dequeueOutputBuffer(info, 10_000)
                if (output >= 0) {
                    decodedBytes += info.size
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                }
            }
            assertTrue("AAC must decode to PCM frames", decodedBytes > 0)
            assertTrue("Decoder must reach end of stream", outputEnded)
        } finally { decoder?.release(); extractor.release(); file.delete() }
    }
}
