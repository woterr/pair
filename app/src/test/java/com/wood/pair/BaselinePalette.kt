package com.wood.pair

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material 3's *baseline* palette — the one the comps were drawn in.
 *
 * This lives in the test source set on purpose. Pair ships no brand colour: the app's palette
 * comes from Material You, and that is what has to be right in the running product. But a screen
 * can only be compared with a comp drawn on a specific palette if it is rendered on that same
 * palette, so the baseline is reproduced here as a fixture — in the tests, and never in the app.
 */
object BaselinePalette {

    /** The exact value of the comps' frame background. */
    const val BACKGROUND: Long = 0xFFEADDFF
    const val ON_BACKGROUND: Long = 0xFF1D1B20
    const val SURFACE: Long = 0xFFEADDFF
    const val ON_SURFACE: Long = 0xFF1D1B20
    const val SURFACE_DIM: Long = 0xFFDED8E1
    const val PRIMARY: Long = 0xFF6750A4
    const val ON_PRIMARY: Long = 0xFFFFFFFF
    const val PRIMARY_CONTAINER: Long = 0xFFEADDFF
    const val ON_PRIMARY_CONTAINER: Long = 0xFF21005D
    const val SECONDARY: Long = 0xFF625B71
    const val ON_SECONDARY: Long = 0xFFFFFFFF
    const val SECONDARY_CONTAINER: Long = 0xFFE8DEF8
    const val ON_SECONDARY_CONTAINER: Long = 0xFF1D192B
    const val TERTIARY: Long = 0xFF7D5260
    const val ON_TERTIARY: Long = 0xFFFFFFFF
    const val TERTIARY_CONTAINER: Long = 0xFFFFD8E4
    const val ON_TERTIARY_CONTAINER: Long = 0xFF31111D
    const val ERROR: Long = 0xFFB3261E
    const val ON_ERROR: Long = 0xFFFFFFFF
    const val ERROR_CONTAINER: Long = 0xFFF9DEDC
    const val ON_ERROR_CONTAINER: Long = 0xFF410E0B
    const val OUTLINE: Long = 0xFF7A757F
    const val OUTLINE_VARIANT: Long = 0xFFCAC4D0
    const val INVERSE_SURFACE: Long = 0xFF322F35
    const val INVERSE_ON_SURFACE: Long = 0xFFF5EFF7
    const val INVERSE_PRIMARY: Long = 0xFFD0BCFF

    /**
     * Sampled from the comp frames themselves.
     *
     * M3's *baseline* light palette cannot be used to compare against these comps: its surface is
     * near-white, so its container tones sit a hair away from each other. On a tinted surface —
     * which is what a strongly purple wallpaper produces, and what the comps show at #EADDFF —
     * the same roles separate properly, and the tonal buttons stop disappearing into the page.
     */
    val light: ColorScheme = lightColorScheme(
        primary = Color(PRIMARY),
        onPrimary = Color(ON_PRIMARY),
        primaryContainer = Color(0xFFD7BFFF),
        onPrimaryContainer = Color(0xFF2A1560),
        secondary = Color(SECONDARY),
        onSecondary = Color(ON_SECONDARY),
        // The comps' tonal buttons and live-text card: one step below the page.
        secondaryContainer = Color(0xFFD7BFFF),
        onSecondaryContainer = Color(0xFF2A1560),
        tertiary = Color(TERTIARY),
        onTertiary = Color(ON_TERTIARY),
        tertiaryContainer = Color(TERTIARY_CONTAINER),
        onTertiaryContainer = Color(ON_TERTIARY_CONTAINER),
        error = Color(ERROR),
        onError = Color(ON_ERROR),
        errorContainer = Color(ERROR_CONTAINER),
        onErrorContainer = Color(ON_ERROR_CONTAINER),
        background = Color(BACKGROUND),
        onBackground = Color(ON_BACKGROUND),
        surface = Color(SURFACE),
        onSurface = Color(ON_SURFACE),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        surfaceContainerLowest = Color(0xFFF3ECF4),
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainer = Color(0xFFF3EDF7),
        surfaceContainerHigh = Color(0xFFECE6F0),
        surfaceContainerHighest = Color(0xFFD0BCFF),
        outline = Color(OUTLINE),
        outlineVariant = Color(OUTLINE_VARIANT),
        inverseSurface = Color(INVERSE_SURFACE),
        inverseOnSurface = Color(INVERSE_ON_SURFACE),
        inversePrimary = Color(INVERSE_PRIMARY),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF492532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD8E4),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E0E9),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        surfaceContainerLowest = Color(0xFF0F0D13),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
        inverseSurface = Color(0xFFE6E0E9),
        inverseOnSurface = Color(0xFF322F35),
        inversePrimary = Color(0xFF6750A4),
    )
}
