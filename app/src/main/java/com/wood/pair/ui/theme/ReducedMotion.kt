package com.wood.pair.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** True when the user has asked the system to reduce or remove animation. */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Whether the user has turned animations off.
 *
 * Reads the platform animator duration scale, which is the same signal the system uses: when
 * it is `0` the user has disabled animations in developer/accessibility options or via an
 * accessibility service, and every animation in the app must become instant.
 *
 * This is honoured in one place — [MotionScheme] — so no animation can be added later that
 * quietly ignores it.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
private fun <T> remember(key: Any?, calculation: () -> T): T =
    androidx.compose.runtime.remember(key) { calculation() }
