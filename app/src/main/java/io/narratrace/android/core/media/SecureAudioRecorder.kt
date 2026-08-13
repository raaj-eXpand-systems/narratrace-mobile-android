package io.narratrace.android.core.media

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Records only into app-private cache; callers encrypt and delete immediately on stop. */
class SecureAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var durationLimitReached = false

    @Suppress("DEPRECATION")
    fun start(maxSeconds: Int? = null): Boolean = runCatching {
        discard()
        durationLimitReached = false
        val destination = File.createTempFile("capture-", ".m4a", context.cacheDir)
        val instance = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            maxSeconds?.takeIf { it > 0 }?.let { setMaxDuration(it * 1_000) }
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) durationLimitReached = true
            }
            setOutputFile(destination.absolutePath)
            prepare(); start()
        }
        file = destination; recorder = instance; true
    }.getOrDefault(false)

    fun stop(): ByteArray? {
        val active = recorder ?: return null
        val captured = file
        recorder = null; file = null
        return runCatching {
            if (!durationLimitReached) active.stop()
            active.release()
            captured?.readBytes()?.also { captured.delete() }
        }.getOrElse { active.release(); captured?.delete(); null }.also { durationLimitReached = false }
    }

    fun discard() {
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }
        recorder = null; file?.delete(); file = null
        durationLimitReached = false
    }
}
