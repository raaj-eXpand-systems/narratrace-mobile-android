package io.narratrace.android.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val LightColors = storybookColors(false, 0xFFFFF9EE, 0xFFFFFDF8, 0xFFF2E7D4,
    0xFF253A40, 0xFF515B58, 0xFF805018, 0xFFFFFFFF, 0xFF85705A, 0xFF982F30)
internal val DarkColors = storybookColors(true, 0xFF111512, 0xFF1D2520, 0xFF29372D,
    0xFFF4EDE1, 0xFFCBD1C8, 0xFFE7B86A, 0xFF241B0C, 0xFF9BAE9D, 0xFFFFAAA2)

// Web palette tokens adapted to opaque native surfaces. UpcomingPreview remains
// the stored identifier for Narratrace Blue so existing preferences keep working.
private val UpcomingColors = storybookColors(false, 0xFFF5F7FC, 0xFFFFFFFF, 0xFFEAF0FB, 0xFF101828, 0xFF475467, 0xFF1857D8, 0xFFFFFFFF, 0xFF667085, 0xFFD92D20)
private val WarmMemoryColors = storybookColors(false, 0xFFFAF6F0, 0xFFFFFDFC, 0xFFF3E8DA, 0xFF392A20, 0xFF69584B, 0xFF8C5632, 0xFFFFFFFF, 0xFF806D5E, 0xFFB5473C)
private val SageHeritageColors = storybookColors(false, 0xFFF3F6F2, 0xFFFFFFFF, 0xFFE4ECE5, 0xFF25352D, 0xFF4F6356, 0xFF3F684B, 0xFFFFFFFF, 0xFF5D7064, 0xFFB5473C)
private val LavenderStoryColors = storybookColors(false, 0xFFF7F4FA, 0xFFFFFFFF, 0xFFEEE8F4, 0xFF302A3E, 0xFF5D526D, 0xFF795CA5, 0xFFFFFFFF, 0xFF736780, 0xFFB5475A)
private val RoseKeepsakeColors = storybookColors(false, 0xFFFBF5F5, 0xFFFFFFFF, 0xFFF5E7E9, 0xFF412B32, 0xFF6F5059, 0xFF92434F, 0xFFFFFFFF, 0xFF84646C, 0xFFB42318)
private val MidnightArchiveColors = storybookColors(true, 0xFF101827, 0xFF182235, 0xFF22304A, 0xFFF4F7FF, 0xFFB8C3D8, 0xFF73A5FF, 0xFF101827, 0xFF93A2BD, 0xFFFF7A70)
private val HeirloomColors = storybookColors(false, 0xFFF7F3EC, 0xFFFFFCF7, 0xFFEEE4D3, 0xFF372C1E, 0xFF665945, 0xFF765520, 0xFFFFFFFF, 0xFF7A6A52, 0xFFAD493F)
private val ChaiLatteColors = storybookColors(false, 0xFFF3D4A5, 0xFFFFF1D4, 0xFFE9BE80, 0xFF201C18, 0xFF5E4B3A, 0xFF8C2F43, 0xFFFFFFFF, 0xFF705B47, 0xFF9F293B)

// Shared web tokens adapted to opaque Material surfaces. All foreground and
// container roles are explicit so Material defaults cannot introduce a new palette.
private fun storybookColors(dark: Boolean, bg: Long, surface: Long, variant: Long,
    text: Long, muted: Long, accent: Long, onAccent: Long, border: Long, error: Long,
): androidx.compose.material3.ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(accent), onPrimary = Color(onAccent),
        primaryContainer = Color(variant), onPrimaryContainer = Color(text),
        inversePrimary = Color(onAccent),
        secondary = Color(accent), onSecondary = Color(onAccent),
        secondaryContainer = Color(variant), onSecondaryContainer = Color(text),
        tertiary = Color(accent), onTertiary = Color(onAccent),
        tertiaryContainer = Color(variant), onTertiaryContainer = Color(text),
        background = Color(bg), onBackground = Color(text),
        surface = Color(surface), onSurface = Color(text),
        surfaceVariant = Color(variant), onSurfaceVariant = Color(muted),
        surfaceTint = Color(accent), inverseSurface = Color(text), inverseOnSurface = Color(bg),
        surfaceBright = Color(surface), surfaceDim = Color(bg),
        surfaceContainer = Color(surface), surfaceContainerLow = Color(bg),
        surfaceContainerLowest = Color(bg), surfaceContainerHigh = Color(variant),
        surfaceContainerHighest = Color(variant),
        outline = Color(border), outlineVariant = Color(border),
        error = Color(error), onError = Color(onAccent),
        errorContainer = Color(variant), onErrorContainer = Color(error),
    )
}

internal val DaylightColors = storybookColors(false, 0xFFEAF5E9, 0xFFFFFFFF, 0xFFE8F4EB,
    0xFF233C37, 0xFF425B4B, 0xFFAA352A, 0xFFFFFFFF, 0xFF5E7B68, 0xFF982F30)
internal val MarigoldColors = storybookColors(false, 0xFFFDF6EA, 0xFFFFFFFF, 0xFFF5EAD6,
    0xFF2A2115, 0xFF4A3B22, 0xFFA8391F, 0xFFFFFFFF, 0xFF87653F, 0xFF982F30)
internal val LamplightColors = storybookColors(true, 0xFF241A2B, 0xFF31243A, 0xFF3C2C46,
    0xFFF8EFE3, 0xFFD6C3B2, 0xFFE8A33D, 0xFF241A2B, 0xFFA184A5, 0xFFFFAAA2)

// Narratrace's primary storytellers are frequently older adults. Keep every
// body, supporting-label, and compact-title token at 16sp or larger while
// preserving Android's user-selected font scaling.
private val NarratraceTypography = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
)

enum class NarratraceAppearance {
    System,
    Light,
    Dark,
    UpcomingPreview,
    ChaiLatte,
    WarmMemory,
    SageHeritage,
    LavenderStory,
    RoseKeepsake,
    MidnightArchive,
    Heirloom,

    Daylight,
    Marigold,
    Lamplight;

    val displayName: String get() = when (this) {
        UpcomingPreview -> "Narratrace Blue"
        ChaiLatte -> "Chai Latte"
        WarmMemory -> "Warm Memory"
        SageHeritage -> "Sage Heritage"
        LavenderStory -> "Lavender Story"
        RoseKeepsake -> "Rose Keepsake"
        MidnightArchive -> "Midnight Archive"
        Heirloom -> "Heirloom"
        Daylight -> "Narratrace Daylight"
        Marigold -> "Narratrace Marigold"
        Lamplight -> "Narratrace Lamplight"
        else -> name
    }
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
        NarratraceAppearance.WarmMemory -> WarmMemoryColors
        NarratraceAppearance.SageHeritage -> SageHeritageColors
        NarratraceAppearance.LavenderStory -> LavenderStoryColors
        NarratraceAppearance.RoseKeepsake -> RoseKeepsakeColors
        NarratraceAppearance.MidnightArchive -> MidnightArchiveColors
        NarratraceAppearance.Heirloom -> HeirloomColors
        NarratraceAppearance.Daylight -> DaylightColors
        NarratraceAppearance.Marigold -> MarigoldColors
        NarratraceAppearance.Lamplight -> LamplightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = NarratraceTypography,
        content = content,
    )
}
