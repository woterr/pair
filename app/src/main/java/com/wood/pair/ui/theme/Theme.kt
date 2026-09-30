package com.wood.pair.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/** How the app picks between light and dark. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: System
    }
}

/**
 * User-facing appearance preferences. Persisted in DataStore.
 *
 * [dynamicColor] is on by default so Pair borrows the user's Material You palette, which is
 * how a modern Android app is expected to look. [amoledDark] is offered separately because
 * pure black is a display preference, not a Material one.
 */
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val amoledDark: Boolean = true,
)

@Composable
fun PairTheme(
    appearance: Appearance = Appearance(),
    /**
     * Overrides colour resolution entirely.
     *
     * `null` in the running app — there, the scheme always comes from Material You or the
     * monochrome fallback. This exists so a preview or a screenshot test can render a screen
     * against a *known* palette and compare it with a comp drawn on that palette; Pair has no
     * brand colour, so the only way to check a screen against a specific-coloured design is to
     * supply that colour deliberately, in one place, for inspection only.
     */
    colorSchemeOverride: androidx.compose.material3.ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance.themeMode) {
        ThemeMode.System -> systemIsDark()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val base = colorSchemeOverride
        ?: baseColorScheme(darkTheme = darkTheme, dynamicColor = appearance.dynamicColor)
    val colorScheme = remember(base, darkTheme, appearance.amoledDark, colorSchemeOverride) {
        if (colorSchemeOverride == null && darkTheme && appearance.amoledDark) {
            base.toAmoledDark()
        } else {
            base
        }
    }

    val typography = remember { PairTypography.Default }
    val shapes = remember { PairShapes.Default }
    // Reduced motion follows the platform animator scale, so it tracks the system
    // accessibility setting rather than an app-local preference.
    val reducedMotion = rememberReducedMotion()
    val motion = remember(reducedMotion) { MotionScheme.ExpressiveRespecting(reducedMotion) }

    CompositionLocalProvider(
        LocalPairTypography provides typography,
        LocalPairShapes provides shapes,
        LocalMotionScheme provides motion,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes.toMaterialShapes(),
            content = content,
        )
    }
}


