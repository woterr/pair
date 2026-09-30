package com.wood.pair.ui.shape

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wood.pair.R
import com.wood.pair.ui.motion.rememberRotation
import com.wood.pair.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * The design's indeterminate *circular* progress indicator, as drawn.
 *
 * Two paths from the comp: a heavy wavy arc with a lighter trail arc behind it. Reproduced as
 * artwork rather than as a procedural path, so the waviness is the designer's rather than an
 * approximation of it, and spun as one unit — which is what makes it read as the same indicator
 * Material animates.
 *
 * Rotation is the whole animation. The silhouette is already irregular, so easing the spin would
 * put a visible hitch at the loop point; it runs linear and seamless.
 */
@Composable
fun IndeterminateCircular(
    color: Color,
    modifier: Modifier = Modifier,
    trailColor: Color = color.copy(alpha = 0.26f),
    size: Dp = 176.dp,
    durationMillis: Int = 2_600,
) {
    val rotation by rememberRotation(durationMillis)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_pair_indicator_trail),
            contentDescription = null,
            colorFilter = ColorFilter.tint(trailColor),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
        )
        Image(
            painter = painterResource(R.drawable.ic_pair_indicator_arc),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * The design's indeterminate *linear* progress indicator, reduced to the wave.
 *
 * A chain of the designer's interlocking lens segments, undulating.
 *
 * The animation is a **travelling deformation, not a translation**: every segment sits at a
 * fixed x, and each is displaced vertically by one sine at its own phase along the chain. The
 * shape therefore ripples where it stands — it never slides, and nothing rotates. That is the
 * whole point of the mark: an in-place wave reads as "something is happening here", whereas a
 * bar sweeping across reads as "something is filling up", and Pair has no progress to report.
 *
 * One infinite transition drives every segment, and each segment reads it in its layer block, so
 * a wave is one animation and N cheap layer invalidations rather than N animations.
 *
 * @param width how much of the wave to show. Passed in rather than measured, because this is
 *   routinely placed inside a parent that asks for intrinsic widths, which the number of
 *   segments needed for a width makes awkward to express as an intrinsic measurement.
 * @param amplitude peak vertical travel, as a fraction of [height]. Past about a third the
 *   segments separate into disconnected blobs and stop reading as one wave.
 */
@Composable
fun IndeterminateWave(
    color: Color,
    width: Dp,
    modifier: Modifier = Modifier,
    height: Dp = 9.dp,
    amplitude: Float = 0.22f,
    durationMillis: Int = 1_600,
    /**
     * Whether the wave travels.
     *
     * Off freezes it at the crest. Under the room's partner line the wave is an underline, not
     * an activity signal: it says "this is a name", and a continuously moving element sitting
     * under a piece of text is a distraction that never resolves and never means anything. The
     * one place a travelling wave is correct is where it stands in for a progress indicator,
     * and this is not that.
     */
    animated: Boolean = true,
) {
    val animatedPhase = rememberWavePhase(durationMillis)
    val phase = if (animated) animatedPhase else 0f
    val segments = remember(width, height) { segmentCount(width, height) }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clipToBounds(),
    ) {
        val painter = painterResource(R.drawable.ic_pair_wave_segment)
        val segmentWidth = segmentWidth(height)

        repeat(segments) { index ->
            val fraction = index / segments.toFloat()
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    // Laid out at the design's own pitch, so the chain interlocks exactly as
                    // drawn rather than being spaced by eye.
                    .requiredWidth(segmentWidth)
                    .height(height)
                    .offset(x = (segmentWidth * SEGMENT_PITCH_FRACTION * index))
                    .graphicsLayer {
                        // One wavelength spans the whole chain, and the phase advances with it,
                        // so the crest travels at a constant speed however wide the bar is.
                        val travel = if (animated) phase else 0f
                        translationY =
                            size.height * amplitude * sin(TAU * (travel - fraction)).toFloat()
                    },
            )
        }
    }
}

/**
 * How the design's own numbers scale into dp.
 *
 * The source strip is 90.234 units wide by 24.117 tall, with segments 1000/12 = 83.333 units
 * apart. Carrying the ratio rather than a tuned dp value is what keeps the chain interlocking by
 * exactly the overlap the designer drew, at any bar height.
 */
private const val SOURCE_WIDTH = 90.234f
private const val SOURCE_HEIGHT = 24.117f
private const val SOURCE_PITCH = 1000f / 12f

/** Segment spacing, as a fraction of a segment's own width. */
private const val SEGMENT_PITCH_FRACTION = SOURCE_PITCH / SOURCE_WIDTH

private const val TAU = (2.0 * PI).toFloat()

/** Width of one segment at a given bar height. */
private fun segmentWidth(height: Dp): Dp = height * (SOURCE_WIDTH / SOURCE_HEIGHT)

/** How many segments cover [width], plus one so the last one is never clipped short. */
private fun segmentCount(width: Dp, height: Dp): Int {
    val pitch = segmentWidth(height) * SEGMENT_PITCH_FRACTION
    return (width.value / pitch.value).toInt().coerceAtLeast(2) + 1
}

/**
 * The wave's phase, advancing 0 → 1 once per cycle.
 *
 * Pinned to zero under reduced motion, which leaves the chain as a straight row of lenses: the
 * mark is still present and still says "live", it simply does not move.
 */
@Composable
private fun rememberWavePhase(durationMillis: Int): Float {
    val reduced = LocalReducedMotion.current
    if (reduced) return 0f

    val transition = rememberInfiniteTransition(label = "wavePhase")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Linear, so the crest moves at a constant speed. An eased phase would make the
            // wave appear to bunch up and stretch as it travels.
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhaseValue",
    )
    return animated
}
