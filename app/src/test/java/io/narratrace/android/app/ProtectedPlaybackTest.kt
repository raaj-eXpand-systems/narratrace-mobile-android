package io.narratrace.android.app

import org.junit.Assert.*
import org.junit.Test

class ProtectedPlaybackTest {
    @Test fun `only signed stream manifests use video player`() {
        assertTrue(allowedStreamPlayback("https://customer-123.cloudflarestream.com/token/manifest/video.m3u8"))
        listOf("http://customer-123.cloudflarestream.com/token/manifest/video.m3u8", "https://customer-123.cloudflarestream.com.evil.test/token/manifest/video.m3u8", "https://user:secret@customer-123.cloudflarestream.com/token/manifest/video.m3u8", "https://customer-123.cloudflarestream.com:444/token/manifest/video.m3u8", "https://customer-123.cloudflarestream.com/video.mp4").forEach { assertFalse(allowedStreamPlayback(it)) }
    }
}
