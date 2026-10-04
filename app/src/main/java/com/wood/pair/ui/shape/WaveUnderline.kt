package com.wood.pair.ui.shape

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A single continuous wave, drawn frozen, for underlining a run of text.
 *
 * ## Why this is not [IndeterminateWave]
 *
 * `IndeterminateWave` is built from a repeated segment drawable where each segment is translated
 * by `sin(TAU * (phase - fraction))`. That staggering *is* the animation — it is what makes the
 * crest travel — so its "frozen" mode, which pins the phase to zero, leaves every segment at a
 * different height. The result is a row of disconnected squiggles rather than a wave, and under a
 * 34sp word at reading size it read as damage to the text rather than as a mark.
 *
 * The partner line gets away with the same mode because it is a wide, thin, low-contrast rule
 * where the stagger is not legible. Under a word it very much is.
 *
 * So this is drawn as what it actually needs to be: one path, one sine, no tiling and no phase.
 * [width] is whatever the caller measured, which is how the rule tracks the text above it at any
 * font scale.
 *
 * The proportions are the comp's: shallow, a long wavelength relative to its height, and rounded
 * ends so it finishes rather than stops. A tall or tight wave under text reads as an error
 * underline, which is the reading to avoid — this marks a boundary and corrects nothing.
 */
@Composable
fun WaveUnderline(
    color: Color,
    width: Dp,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    strokeWidth: Dp = 1.5.dp,
) {
    Box(modifier.width(width).height(height)) {
        Canvas(Modifier.width(width).height(height)) {
            val amplitude = size.height * 0.28f
            val midY = size.height / 2f
            // Two full wavelengths across the rule. Fewer reads as a series of humps; more reads
            // as a texture and stops being countable, which is the only thing it has to convey.
            val cycles = 2f
            val step = (size.width / 200f).coerceAtLeast(1f)

            val path = Path()
            var x = 0f
            path.moveTo(0f, midY)
            while (x <= size.width) {
                val phase = (x / size.width) * cycles * 2f * PI.toFloat()
                path.lineTo(x, midY + amplitude * sin(phase))
                x += step
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}
