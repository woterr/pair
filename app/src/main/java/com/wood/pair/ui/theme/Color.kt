package com.wood.pair.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/**
 * COLOUR POLICY — there are no brand colours in Pair.
 *
 * Every colour the app draws comes from one of two places:
 *
 *  1. **Material You (default).** On Android 12+ the palette is derived by the system from
 *     the user's wallpaper, via `dynamicLightColorScheme` / `dynamicDarkColorScheme`. Pair
 *     does not override it, and the user can switch it off in Settings.
 *
 *  2. **Monochrome, when there is no dynamic palette.** On Android 11 and below, or when the
 *     user turns Material You off, the fallback below is used. It is deliberately a neutral
 *     black-and-white scheme.
 *
 * The design comps were drawn on a purple wallpaper, and every purple in them maps onto a
 * *semantic role* (`primary`, `primaryContainer`, `surfaceContainerHigh`, …) rather than onto
 * a literal value. That is what lets the same layout sit on a purple phone, a blue phone, a
 * green phone and a greyscale one without a single hard-coded colour.
 */
object Mono {

    /** The light fallback: pure white surfaces with true-black content. */
    val Light: ColorScheme = lightColorScheme(
        primary = Color(0xFF000000),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE9E9EA),
        onPrimaryContainer = Color(0xFF000000),
        secondary = Color(0xFF3A3A3C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEDEDEE),
        onSecondaryContainer = Color(0xFF000000),
        tertiary = Color(0xFF5A5A5D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF1F1F2),
        onTertiaryContainer = Color(0xFF000000),
        error = Color(0xFF8C1D18),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFEDEDEE),
        onSurfaceVariant = Color(0xFF5A5A5D),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFF4F4F5),
        surfaceContainerHigh = Color(0xFFEDEDEE),
        surfaceContainerHighest = Color(0xFFE6E6E8),
        outline = Color(0xFF8A8A8E),
        outlineVariant = Color(0xFFD8D8DC),
        inverseSurface = Color(0xFF000000),
        inverseOnSurface = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFEDEDEE),
        scrim = Color(0xFF000000),
    )

    /** The dark fallback: true-black surfaces with white content. */
    val Dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF2A2A2C),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFB4B4B8),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF232325),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFF8E8E92),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF1D1D1F),
        onTertiaryContainer = Color(0xFFFFFFFF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF5C1512),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF232325),
        onSurfaceVariant = Color(0xFF8E8E92),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0D0D0E),
        surfaceContainer = Color(0xFF151517),
        surfaceContainerHigh = Color(0xFF1F1F21),
        surfaceContainerHighest = Color(0xFF2A2A2C),
        outline = Color(0xFF6E6E72),
        outlineVariant = Color(0xFF2E2E30),
        inverseSurface = Color(0xFFFFFFFF),
        inverseOnSurface = Color(0xFF000000),
        inversePrimary = Color(0xFF000000),
        scrim = Color(0xFF000000),
    )
}

/** True when the device can supply a Material You palette (Android 12+). */
val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Resolves the base scheme.
 *
 * Order of preference: the system's Material You palette when available and enabled,
 * otherwise the monochrome fallback. Never a fixed brand colour.
 */
@Composable
fun baseColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
): ColorScheme {
    val useDynamic = dynamicColor && supportsDynamicColor
    return if (useDynamic) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) Mono.Dark else Mono.Light
    }
}

/**
 * True-black treatment for OLED panels.
 *
 * Only the *large background* roles are taken to pure black: `background`, `surface` and the
 * lowest container step. Every other role keeps whatever tone the resolved scheme produced,
 * so tonal hierarchy, contrast and component affordances survive. This is a display
 * preference, not a separate palette.
 */
fun ColorScheme.toAmoledDark(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
)

/** True when the device is currently in dark theme. */
@Composable
fun systemIsDark(): Boolean = isSystemInDarkTheme()

/**
 * The surfaces every card and tonal control is painted with.
 *
 * This exists as a named role rather than being spelled `secondaryContainer` at each call site,
 * because the choice is not obvious and getting it wrong is invisible rather than ugly: in the
 * comps' own palette `primaryContainer` happens to equal the *background*, so a card painted with
 * it disappears completely instead of looking slightly wrong.
 *
 * The rule the comps follow is that the page is the lightest tone and every raised surface sits
 * one step below it. `secondaryContainer` is exactly that step in both the comps' palette and the
 * monochrome fallback, and — unlike `primaryContainer` — it is never the same value as
 * `background`, so a card drawn in it is always visible.
 */
object PairSurfaces {

    /** Cards, the live-text stage, chips: the one step below the page. */
    val card: Color
        @Composable get() = MaterialTheme.colorScheme.secondaryContainer

    /** Content on [card]. */
    val onCard: Color
        @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer

    /** Muted text on [card]; the supporting lines inside a card. */
    val onCardMuted: Color
        @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)

    /** The page itself, and the one surface that is allowed to match it. */
    val page: Color
        @Composable get() = MaterialTheme.colorScheme.background
}

/**
 * The tint for decorative background shapes.
 *
 * The comps draw their blobs, stars and hearts as a faint wash of the theme's own colour — a light
 * lavender in a light scheme. That is not one constant: a `primary` at low alpha is a light
 * lavender over a light background and a near-invisible dark shape over a dark one, which is the
 * opposite of what is wanted. So the wash is built from whichever of `primary` / `onSurface` is
 * *lighter* than the background it sits on, at an alpha low enough to stay background.
 *
 * One expression, both schemes, and no brand colour anywhere.
 */
@Composable
fun decorTint(alpha: Float = 0.14f): Color {
    val scheme = MaterialTheme.colorScheme
    val base = if (scheme.background.luminance() > 0.5f) scheme.primary else scheme.onSurface
    return base.copy(alpha = alpha)
}

/**
 * The surface the navigation bar sits on.
 *
 * The comps show the bar as a step *away* from the background in whichever direction "away" is:
 * lighter than the background in a light scheme, darker in a dark one. `surfaceContainerHigh`
 * only does the second, so the light case takes the lowest container instead.
 */
@Composable
fun navBarSurface(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() > 0.5f) {
        scheme.surfaceContainerLowest
    } else {
        scheme.surfaceContainerHigh
    }
}
