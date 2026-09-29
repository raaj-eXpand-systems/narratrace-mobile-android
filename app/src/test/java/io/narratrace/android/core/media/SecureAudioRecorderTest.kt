package io.narratrace.android.core.media

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SecureAudioRecorderTest {
    private class Device : RecordingDevice {
        var stops = 0
        var releases = 0
        var failStart = false
        var failStop = false
        lateinit var event: (Boolean) -> Unit
        override fun start(file: File, maxSeconds: Int?, interrupted: (Boolean) -> Unit) {
            event = interrupted
            file.writeBytes(byteArrayOf(1, 2, 3))
            if (failStart) error("start")
        }
        override fun stop() { stops++; if (failStop) error("stop") }
        override fun release() { releases++ }
    }
    @Test fun interruptionRetainsTakeAndStopConsumesItExactlyOnce() {
        val file = File.createTempFile("audio-test", ".m4a")
        val device = Device()
        val recorder = SecureAudioRecorder({ file }, { device })
        assertTrue(recorder.start())
        device.event(false)
        recorder.interrupt()
        assertEquals(SecureAudioRecorder.State.Interrupted, recorder.state.value)
        assertTrue(file.exists())
        assertFalse(recorder.start())
        assertArrayEquals(byteArrayOf(1, 2, 3), recorder.stop())
        assertNull(recorder.stop())
        assertFalse(file.exists())
        assertEquals(1, device.stops)
        assertEquals(1, device.releases)
    }
    @Test fun maxDurationDoesNotStopAlreadyStoppedHardware() {
        val file = File.createTempFile("audio-test", ".m4a")
        val device = Device()
        val recorder = SecureAudioRecorder({ file }, { device })
        recorder.start(); device.event(true)
        assertNotNull(recorder.stop())
        assertEquals(0, device.stops)
        assertEquals(1, device.releases)
    }
    @Test fun failedStartupReleasesDeviceAndDeletesPlaintext() {
        val file = File.createTempFile("audio-test", ".m4a")
        val device = Device().apply { failStart = true }
        val recorder = SecureAudioRecorder({ file }, { device })
        assertFalse(recorder.start())
        assertFalse(file.exists())
        assertEquals(1, device.releases)
        assertEquals(SecureAudioRecorder.State.Idle, recorder.state.value)
    }
    @Test fun staleCallbackCannotInterruptReplacementAndDiscardDeletesTake() {
        val first = Device(); val second = Device(); var current = first
        var file: File? = null
        val recorder = SecureAudioRecorder({ File.createTempFile("audio-test", ".m4a").also { file = it } }, { current })
        recorder.start(); recorder.discard(); current = second; recorder.start()
        first.event(false)
        assertEquals(SecureAudioRecorder.State.Recording, recorder.state.value)
        assertEquals(0, second.stops)
        recorder.interrupt(); recorder.discard(); recorder.discard()
        assertFalse(file!!.exists())
        assertEquals(1, second.releases)
    }
    @Test fun microphoneFailureWithInvalidOutputReturnsNoAudioAndCleansUp() {
        val file = File.createTempFile("audio-test", ".m4a")
        val device = Device().apply { failStop = true }
        val recorder = SecureAudioRecorder({ file }, { device })
        recorder.start(); device.event(false)
        assertNull(recorder.stop())
        assertFalse(file.exists())
        assertEquals(1, device.releases)
    }
}
