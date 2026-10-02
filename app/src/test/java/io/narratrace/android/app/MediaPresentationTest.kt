package io.narratrace.android.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPresentationTest {
    @Test fun `photo titles hide filenames and preserve useful labels`() {
        assertEquals("Photo", mediaDisplayTitle("Narratrace-photo-ABC.jpg", "photo"))
        assertEquals("Photo", mediaDisplayTitle("IMG_123.HEIC", "photo"))
        assertEquals("Family picnic", mediaDisplayTitle("Family picnic", "photo"))
        assertEquals("Interview.m4a", mediaDisplayTitle("Interview.m4a", "audio"))
    }
    @Test fun `thumbnail sampling bounds landscape and portrait memory`() {
        for ((width, height) in listOf(1024 to 768, 768 to 1024, 120 to 80, 8000 to 6000)) {
            val sample = thumbnailSampleSize(width, height)
            assertTrue(maxOf(width, height) / sample <= 240)
            assertTrue(sample >= 1)
        }
    }
}
