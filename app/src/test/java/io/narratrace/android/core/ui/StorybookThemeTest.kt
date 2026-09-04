package io.narratrace.android.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorybookThemeTest {
    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }

    @Test fun `storybook material text and controls meet AA across container surfaces`() {
        for (scheme in listOf(DaylightColors, MarigoldColors, LamplightColors)) {
            for (surface in listOf(scheme.background, scheme.surface, scheme.surfaceVariant,
                scheme.surfaceContainer, scheme.surfaceContainerLow, scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest, scheme.surfaceContainerLowest)) {
                for (text in listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, scheme.error)) {
                    assertTrue("text contrast ${contrast(text, surface)}", contrast(text, surface) >= 4.5)
                }
                assertTrue("outline contrast ${contrast(scheme.outline, surface)}", contrast(scheme.outline, surface) >= 3)
            }
            for ((foreground, background) in listOf(
                scheme.onPrimary to scheme.primary, scheme.onSecondary to scheme.secondary,
                scheme.onTertiary to scheme.tertiary, scheme.onError to scheme.error,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onErrorContainer to scheme.errorContainer,
                scheme.inverseOnSurface to scheme.inverseSurface,
            )) assertTrue("control text contrast", contrast(foreground, background) >= 4.5)
        }
    }

    @Test fun `appearance names survive existing local preference round trip`() {
        for (appearance in NarratraceAppearance.entries) {
            assertEquals(appearance, NarratraceAppearance.valueOf(appearance.name))
        }
        assertEquals("Narratrace Daylight", NarratraceAppearance.Daylight.displayName)
        assertEquals("Narratrace Marigold", NarratraceAppearance.Marigold.displayName)
        assertEquals("Narratrace Lamplight", NarratraceAppearance.Lamplight.displayName)
    }
}
