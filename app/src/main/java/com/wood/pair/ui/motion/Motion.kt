package com.wood.pair.ui.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.wood.pair.ui.theme.LocalMotionScheme
import com.wood.pair.ui.theme.LocalReducedMotion

/**
 * Material 3's expressive press feedback: a small, spring-damped shrink while a control is held.
 *
 * Material's guidance is that a button should acknowledge the touch *before* the action resolves,
 * because the action may be slow. The platform ripple alone is too small a signal to read on a
 * pill this large, so the press also scales the control — the same behaviour the M3 expressive
 * button and FAB apply.
 *
 * The scale is deliberately shallow (0.96) and the spring deliberately soft. A deeper squash
 * reads as a toy; a stiff one reads as a glitch. The lift back on release is what makes it feel
 * responsive rather than merely correct.
 *
 * The caller passes in the [MutableInteractionSource] the control already owns, because every
 * control in Pair is a Material component that supplies its own. Handing back a [Modifier]
 * rather than taking one keeps the wiring in one place at the call site.
 *
 * Respects reduced motion: with motion reduced the control does not scale and the platform ripple
 * carries the whole signal.
 */
@Composable
fun Modifier.rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalMotionScheme.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        // No reduced-motion branch here, deliberately. `fastSpatialSpec` already resolves to
        // `snap()` when motion is reduced, and this used to override it with its own critically
        // damped spring — which meant the control still animated for someone who had asked it not
        // to, while every other animation in the app correctly did not. The preference has to be
        // enforced in one place, and that place is the scheme.
        animationSpec = motion.fastSpatialSpec(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A continuous rotation, honouring reduced motion.
 *
 * Used for the design's indeterminate indicators. Under reduced motion the value is pinned to
 * zero, which leaves the mark in its resting orientation rather than hiding it — the indicator
 * still communicates "working", just without the movement.
 */
@Composable
fun rememberRotation(
    durationMillis: Int = 2_600,
): State<Float> {
    val reduced = LocalReducedMotion.current
    if (reduced) return remember { mutableFloatStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "rotation")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            // Linear, because the mark's own silhouette is already irregular: easing the
            // rotation would make the loop point visible as a hitch.
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotationValue",
    )
}
