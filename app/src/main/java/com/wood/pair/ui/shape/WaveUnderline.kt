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
    /**
     * One crest-to-crest distance, in dp.
     *
     * Fixed rather than derived from [width], which is the whole point. The earlier version drew a
     * fixed *number* of cycles across whatever width it was given, so a short status got two
     * cramped humps and a long one got two stretched ones — the wavelength changed with the text,
     * and a wave whose shape depends on how much you have typed is not a wave, it is a texture
     * that happens to undulate. At a constant pitch the rule grows by adding crests as the status
     * fills the chip's budget, which is both prettier and reads as "one more, one more", rather
     * than "the same shape, squashed".
     */
    wavelength: Dp = 22.dp,
) {
    Box(modifier.width(width).height(height)) {
        Canvas(Modifier.width(width).height(height)) {
            val amplitude = size.height * 0.28f
            val midY = size.height / 2f
            val pitch = wavelength.toPx()
            if (pitch <= 0f) return@Canvas

            val path = Path()
            var x = 0f
            path.moveTo(0f, midY)
            while (x <= size.width) {
                path.lineTo(x, midY + amplitude * sin((x / pitch) * 2f * PI.toFloat()))
                x += (pitch / 16f).coerceAtLeast(1f)
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
