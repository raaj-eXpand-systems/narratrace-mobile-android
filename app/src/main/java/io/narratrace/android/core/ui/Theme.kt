package io.narratrace.android.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D4C3B),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF7B5B3A),
    background = Color(0xFFFFF9EE),
    surface = Color(0xFFFFF9EE),
    onSurface = Color(0xFF1D211F),
    onSurfaceVariant = Color(0xFF4F5752),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB1D3BE),
    onPrimary = Color(0xFF183527),
    secondary = Color(0xFFEBC08F),
    background = Color(0xFF111512),
    surface = Color(0xFF111512),
    onSurface = Color(0xFFE2E8E3),
    onSurfaceVariant = Color(0xFFC1C9C3),
    error = Color(0xFFFFB4AB),
)

// Customer preview of the cool Liquid Glass direction used across Narratrace.
// Compose content surfaces remain opaque and legible; navigation and transient
// controls can layer Material 3 tonal surfaces above this luminous canvas.
private val UpcomingColors = lightColorScheme(
    primary = Color(0xFF0866FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF102A56),
    secondary = Color(0xFF7559FF),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF00A7C7),
    background = Color(0xFFF4F8FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF1FF),
    onSurface = Color(0xFF101828),
    onSurfaceVariant = Color(0xFF475467),
    outline = Color(0xFF98A2B3),
    error = Color(0xFFD92D20),
)

private val ChaiLatteColors = lightColorScheme(
    primary = Color(0xFF8C2F43), onPrimary = Color.White,
    primaryContainer = Color(0xFFF3C3C9), onPrimaryContainer = Color(0xFF351017),
    secondary = Color(0xFF4E644B), onSecondary = Color.White,
    tertiary = Color(0xFFA85E13), background = Color(0xFFF3D4A5),
    surface = Color(0xFFFFF1D4), surfaceVariant = Color(0xFFE9BE80),
    onSurface = Color(0xFF201C18), onSurfaceVariant = Color(0xFF5E4B3A),
    outline = Color(0xFF766353), error = Color(0xFF9F293B),
)

enum class NarratraceAppearance {
    System,
    Light,
    Dark,
    UpcomingPreview,
    ChaiLatte,
}

@Composable
fun NarratraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearance: NarratraceAppearance = NarratraceAppearance.UpcomingPreview,
    content: @Composable () -> Unit,
) {
    val colors = when (appearance) {
        NarratraceAppearance.System -> if (darkTheme) DarkColors else LightColors
        NarratraceAppearance.Light -> LightColors
        NarratraceAppearance.Dark -> DarkColors
        NarratraceAppearance.UpcomingPreview -> UpcomingColors
        NarratraceAppearance.ChaiLatte -> ChaiLatteColors
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
