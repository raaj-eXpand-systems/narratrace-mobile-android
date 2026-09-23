package io.narratrace.android.app

import io.narratrace.android.core.media.FeatureResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionSpeechPlaybackTest {
    @Test fun `superseded failure cannot clear current loading question`() = runTest {
        val player = QuestionSpeechPlayback(this)
        var old: Continuation<FeatureResult<ByteArray>>? = null
        var current: Continuation<FeatureResult<ByteArray>>? = null
        player.toggle("old") { suspendCoroutine { old = it } }
        runCurrent()
        player.toggle("new") { suspendCoroutine { current = it } }
        runCurrent()
        old!!.resume(FeatureResult.AuthenticationRequired)
        runCurrent()
        assertEquals("new", player.messageId)
        assertNull(player.error)
        current!!.resume(FeatureResult.AuthenticationRequired)
        runCurrent()
        assertNull(player.messageId)
        assertEquals("new", player.failedMessageId)
        assertNotNull(player.error)
    }

    @Test fun `stop discards and zeroes a late audio response`() = runTest {
        val player = QuestionSpeechPlayback(this)
        var reply: Continuation<FeatureResult<ByteArray>>? = null
        player.toggle("question") { suspendCoroutine { reply = it } }
        runCurrent()
        assertEquals("question", player.messageId)
        player.stop()
        val audio = byteArrayOf(1, 2, 3)
        reply!!.resume(FeatureResult.Success(audio))
        runCurrent()
        assertNull(player.messageId)
        assertNull(player.error)
        assertArrayEquals(byteArrayOf(0, 0, 0), audio)
    }
}
