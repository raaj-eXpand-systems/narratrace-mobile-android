package io.narratrace.android.core.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.AudioRouting
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal interface RecordingDevice {
    fun start(file: File, maxSeconds: Int?, interrupted: (Boolean) -> Unit)
    fun stop()
    fun release()
}

/** Main-thread owned. Stopped takes stay private until the caller encrypts or discards them. */
class SecureAudioRecorder internal constructor(
    private val destination: () -> File,
    private val device: () -> RecordingDevice,
) : DefaultLifecycleObserver {
    constructor(context: Context) : this(
        { File.createTempFile("capture-", ".m4a", context.cacheDir) },
        { AndroidRecordingDevice(context.applicationContext) },
    )
    enum class State { Idle, Recording, Interrupted }
    private val mutableState = MutableStateFlow(State.Idle)
    val state = mutableState.asStateFlow()
    val hasRecording get() = mutableState.value != State.Idle
    private var recorder: RecordingDevice? = null
    private var file: File? = null
    private var generation = 0

    fun start(maxSeconds: Int? = null): Boolean {
        if (hasRecording) return false
        val token = ++generation
        return try {
            file = destination()
            val instance = device()
            recorder = instance
            instance.start(file!!, maxSeconds) { alreadyStopped ->
                if (token == generation) interrupt(alreadyStopped)
            }
            // A synchronous failure callback must not be overwritten by startup.
            if (recorder !== instance) false else {
                mutableState.value = State.Recording
                true
            }
        } catch (_: Exception) { discard(); false }
    }

    override fun onStop(owner: LifecycleOwner) = interrupt()

    fun interrupt(alreadyStopped: Boolean = false) {
        val active = recorder ?: return
        recorder = null
        generation += 1
        val valid = alreadyStopped || runCatching { active.stop() }.isSuccess
        runCatching { active.release() }
        if (!valid) { file?.delete(); file = null }
        mutableState.value = State.Interrupted
    }

    fun stop(): ByteArray? {
        if (!hasRecording) return null
        interrupt()
        val captured = file
        file = null
        mutableState.value = State.Idle
        return try { captured?.takeIf { it.length() > 0 }?.readBytes() }
        catch (_: Exception) { null }
        finally { captured?.delete() }
    }

    fun discard() {
        generation += 1
        val active = recorder
        recorder = null
        runCatching { active?.stop() }; runCatching { active?.release() }
        file?.delete(); file = null
        mutableState.value = State.Idle
    }
}

@Suppress("DEPRECATION")
private class AndroidRecordingDevice(private val context: Context) : RecordingDevice {
    private val recorder = MediaRecorder()
    private val handler = Handler(Looper.getMainLooper())
    private var configurationCallback: AudioManager.AudioRecordingCallback? = null
    private var routeCallback: AudioRouting.OnRoutingChangedListener? = null
    private var routeID: Int? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private var healthCheck: Runnable? = null
    private var devicesCallback: AudioDeviceCallback? = null

    override fun start(file: File, maxSeconds: Int?, interrupted: (Boolean) -> Unit) {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        check(!audioManager.isMicrophoneMute)
        // API 26/27 lack MediaRecorder routing callbacks. Watch input device changes there.
        val initialInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.id }.toSet()
        if (Build.VERSION.SDK_INT < 28) devicesCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) {
                if (devices.any { it.isSource && it.id !in initialInputs }) interrupted(false)
            }
            override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) {
                if (devices.any { it.isSource }) interrupted(false)
            }
        }.also { audioManager.registerAudioDeviceCallback(it, handler) }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setAudioSamplingRate(44_100)
        maxSeconds?.takeIf { it > 0 }?.let { recorder.setMaxDuration(it.coerceAtMost(Int.MAX_VALUE / 1_000) * 1_000) }
        recorder.setOnErrorListener { _, _, _ -> interrupted(false) }
        recorder.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) interrupted(true)
        }
        if (Build.VERSION.SDK_INT >= 29) configurationCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                // Registered on this recorder, not globally: another app's capture is not ours.
                if (configs.any { it.isClientSilenced }) interrupted(false)
            }
        }.also { recorder.registerAudioRecordingCallback(context.mainExecutor, it) }
        if (Build.VERSION.SDK_INT >= 28) routeCallback = AudioRouting.OnRoutingChangedListener { routing ->
            val next = routing.routedDevice?.id
            if (routeID != null && next != routeID) interrupted(false)
            else routeID = next
        }.also { recorder.addOnRoutingChangedListener(it, handler) }
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0) interrupted(false)
            }, handler).build()
        check(audioManager.requestAudioFocus(focus!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        recorder.start()
        if (Build.VERSION.SDK_INT >= 28) routeID = recorder.routedDevice?.id
        healthCheck = object : Runnable {
            override fun run() {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    audioManager.isMicrophoneMute || runCatching { recorder.maxAmplitude }.isFailure) {
                    interrupted(false)
                } else handler.postDelayed(this, 500)
            }
        }.also { handler.postDelayed(it, 500) }
    }

    override fun stop() = recorder.stop()
    override fun release() {
        devicesCallback?.let { runCatching { audioManager.unregisterAudioDeviceCallback(it) } }; devicesCallback = null
        healthCheck?.let { handler.removeCallbacks(it) }; healthCheck = null
        focus?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }; focus = null
        if (Build.VERSION.SDK_INT >= 29) configurationCallback?.let { runCatching { recorder.unregisterAudioRecordingCallback(it) } }
        if (Build.VERSION.SDK_INT >= 28) routeCallback?.let { runCatching { recorder.removeOnRoutingChangedListener(it) } }
        runCatching { recorder.setOnErrorListener(null) }
        runCatching { recorder.setOnInfoListener(null) }
        recorder.release()
    }
}
