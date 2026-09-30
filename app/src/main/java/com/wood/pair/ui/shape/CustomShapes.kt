package com.wood.pair.ui.shape

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The scalloped "flower".
 *
 * It appears three times across the comps — as the badge behind the app mark in the Live
 * Update, as the background of a *selected* weekday chip, and in the small mark that sits over
 * the "i" of the wordmark — so it is one shape reused rather than three similar drawings.
 *
 * Built from a polar radius function, `r(θ) = R · (1 − d + d·cos(nθ))`, which gives smooth
 * petals. A polygon approximation would read as a cog, which is not what the comps show.
 *
 * @param petals petal count. The wordmark mark is 6; the Live Update badge is 12.
 * @param depth how far the petals dip inward, 0..0.5. Larger is more pronounced.
 */
class ScallopedShape(
    private val petals: Int = 12,
    private val depth: Float = 0.12f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(scallopedPath(size, petals, depth))
}

/**
 * Samples per petal. Twelve is the smallest count at which a 6-petal mark still reads as a
 * curve rather than a hexagon, and it scales up harmlessly for the 12-petal badge.
 */
private const val SAMPLES_PER_PETAL = 12

/** The path behind [ScallopedShape], shared with [ScallopedMark] which draws it directly. */
internal fun scallopedPath(size: Size, petals: Int, depth: Float): Path {
    val path = Path()
    val centreX = size.width / 2f
    val centreY = size.height / 2f
    val baseRadius = minOf(size.width, size.height) / 2f
    val steps = (petals * SAMPLES_PER_PETAL).coerceAtLeast(64)

    for (step in 0..steps) {
        val theta = (2.0 * PI * step / steps).toFloat()
        val radius = baseRadius * (1f - depth + depth * cos(petals * theta))
        val x = centreX + radius * cos(theta)
        val y = centreY + radius * sin(theta)
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * The scalloped mark, sized to a box rather than filled by a [Shape].
 *
 * `ScallopedShape` is the right tool when something needs to *clip* to the flower — a chip
 * background, a badge. This is for the cases where the flower is simply a mark to draw, and
 * where a caller may want a different depth from the default.
 */
@Composable
fun ScallopedMark(
    color: Color,
    modifier: Modifier = Modifier,
    petals: Int = 6,
    depth: Float = 0.22f,
) {
    Canvas(modifier = modifier) {
        drawPath(scallopedPath(size, petals, depth), color = color)
    }
}

/**
 * The sharp starburst behind the wordmark.
 *
 * Deliberately *not* a flower: the comps use a spiky multi-point star here and rounded petals
 * elsewhere, and one polar function cannot produce both, so the star is its own shape.
 */
class StarburstShape(
    private val points: Int = 12,
    private val innerRatio: Float = 0.74f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(starburstPath(size, points, innerRatio))

    private companion object {
        /** Straight edges, so only the two points per spike are emitted. */
        fun starburstPath(size: Size, points: Int, innerRatio: Float): Path {
            val path = Path()
            val centreX = size.width / 2f
            val centreY = size.height / 2f
            val outer = minOf(size.width, size.height) / 2f
            val inner = outer * innerRatio
            val steps = points * 2

            for (step in 0..steps) {
                val theta = (2.0 * PI * step / steps).toFloat()
                val radius = if (step % 2 == 0) outer else inner
                val x = centreX + radius * cos(theta)
                val y = centreY + radius * sin(theta)
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return path
        }
    }
}

/**
 * The soft organic blob used as a background accent behind the wordmark.
 *
 * The comps scatter a few of these at different sizes and rotations; one shape covers them and
 * the call site scales.
 */
class BlobShape(
    private val irregularity: Float = 0.07f,
    private val rotationDegrees: Float = 0f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(path(size))

    private fun path(size: Size): Path {
        val path = Path()
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        val rx = size.width / 2f
        val ry = size.height / 2f
        val rotation = Math.toRadians(rotationDegrees.toDouble())
        val steps = 96

        for (step in 0..steps) {
            val theta = 2.0 * PI * step / steps
            val wobble = 1f + irregularity *
                (0.6f * cos(3.0 * theta).toFloat() + 0.4f * sin(2.0 * theta).toFloat())
            val x = rx * wobble * cos(theta).toFloat()
            val y = ry * wobble * sin(theta).toFloat()
            val px = centreX + (x * cos(rotation) - y * sin(rotation)).toFloat()
            val py = centreY + (x * sin(rotation) + y * cos(rotation)).toFloat()
            if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }
}

/**
 * The decorative petal burst that sits behind the bottom navigation.
 *
 * Long, thin, rounded petals radiating from a point behind the bar. Drawn as repeated rotated
 * ellipses rather than one path, because each petal is a separate opaque shape and they
 * overlap in the comps — a single path would need a fill rule to reproduce that.
 */
@Composable
fun PetalBurst(
    color: Color,
    modifier: Modifier = Modifier,
    petals: Int = 16,
    /** Distance from the burst's origin to the tip of a petal, as a fraction of the radius. */
    reach: Float = 0.92f,
    /** Petal width as a fraction of the radius. */
    girth: Float = 0.20f,
) {
    Canvas(modifier = modifier) {
        val radius = minOf(size.width, size.height) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val petalLength = radius * 2f * reach
        val petalWidth = radius * 2f * girth
        // Petals start a little outside the origin, so the middle stays clear.
        val offset = petalLength * 0.18f

        for (index in 0 until petals) {
            val angle = 360f * index / petals
            withTransform({ rotate(degrees = angle, pivot = centre) }) {
                drawOval(
                    color = color,
                    topLeft = Offset(
                        centre.x - petalWidth / 2f,
                        centre.y - offset - petalLength,
                    ),
                    size = Size(petalWidth, petalLength),
                )
            }
        }
    }
}

/**
 * The hand-drawn wavy rule used between sections on the settings screen.
 *
 * Drawn rather than shaped: a [Shape] can only describe a fill outline, and this is a stroke.
 * A cubic per half-wavelength, alternating above and below the centre line, gives the loose
 * squiggle in the comps. No Material component produces this, so it is custom by necessity.
 */
@Composable
fun WavyDivider(
    color: Color,
    modifier: Modifier = Modifier,
    amplitude: Dp = 4.dp,
    wavelength: Dp = 40.dp,
    strokeWidth: Dp = 3.dp,
) {
    Canvas(modifier = modifier) {
        val amplitudePx = amplitude.toPx()
        val wavelengthPx = wavelength.toPx().coerceAtLeast(1f)
        val centreY = size.height / 2f
        val quarter = wavelengthPx / 4f

        val path = Path()
        path.moveTo(0f, centreY)
        var x = 0f
        var up = true
        while (x < size.width + wavelengthPx) {
            val next = x + wavelengthPx
            val offset = if (up) -amplitudePx else amplitudePx
            // Control points at a quarter of the way in rather than 0.55 of a quarter.
            //
            // The design's wave is the travelling lens in
            // `Linear-indeterminate progress indicator.svg`: a lens shape, which is steepest at
            // its crests and flattest at the centre line. A cubic with control points further
            // out than a quarter produces the opposite — flat at the extremes and steep in the
            // middle — which reads as a ripple, and as the wrong mark.
            path.cubicTo(
                x + quarter,
                centreY + offset,
                next - quarter,
                centreY + offset,
                next,
                centreY,
            )
            x = next
            up = !up
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * A wavy underline for a name set in the serif face — the squiggle under the partner's name on
 * the room screen.
 *
 * Wavelength is deliberately short and the amplitude shallow, which is what separates it from
 * [WavyDivider] as a rule: one reads as a divider, the other as a pen mark.
 */
@Composable
fun WavyUnderline(
    color: Color,
    modifier: Modifier = Modifier,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
    strokeWidth: Dp = 2.5.dp,
) {
    WavyDivider(
        color = color,
        modifier = modifier,
        amplitude = amplitude,
        wavelength = wavelength,
        strokeWidth = strokeWidth,
    )
}

/**
 * The rubbery loop that marks "no live text yet" on the room screen.
 *
 * The comps draw it as a heavy stroke that runs most of the way round a circle, wobbling in and
 * out, with a lighter companion arc picking up the rest — so the circle reads as *drawn* rather
 * than as a progress ring. The wobble is a second harmonic on the polar radius, which gives the
 * stroke the same organic quality as the scalloped mark.
 *
 * Angles follow the usual convention: 0° is 3 o'clock and they increase clockwise, so
 * [gapCenterDegrees] names the point on the dial the opening sits at.
 *
 * @param gapCenterDegrees where the opening sits, in degrees.
 * @param gapDegrees how wide the opening is.
 */
@Composable
fun SketchedLoop(
    color: Color,
    modifier: Modifier = Modifier,
    gapCenterDegrees: Float = 200f,
    gapDegrees: Float = 60f,
    strokeWidth: Dp = 15.dp,
    /** Colour of the lighter trailing arc. */
    trailColor: Color = color.copy(alpha = 0.3f),
) {
    Canvas(modifier = modifier) {
        val weight = strokeWidth.toPx()
        val radius = minOf(size.width, size.height) / 2f - weight / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        fun arc(startAngle: Float, sweep: Float, stroke: Float, brush: Color) {
            drawPath(
                path = loopPath(centre, radius, startAngle, sweep),
                color = brush,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // The circle is 360°. The gap takes `gapDegrees`, the lighter trailing arc takes a third
        // of what is left, and the heavy stroke takes the rest — so the two together always close
        // the ring and the opening is a deliberate, fixed size.
        val gapStart = gapCenterDegrees - gapDegrees / 2f
        val trailSweep = (360f - gapDegrees) * 0.28f
        val heavySweep = 360f - gapDegrees - trailSweep

        // The trailing arc first, so the heavy stroke overlaps its end rather than butting into
        // it — which is what makes the join look drawn rather than assembled.
        arc(startAngle = gapStart + trailSweep, sweep = trailSweep, stroke = weight * 0.8f, brush = trailColor)
        arc(startAngle = gapStart + heavySweep, sweep = heavySweep, stroke = weight, brush = color)
    }
}

/**
 * A closed, wobbling circle as a path.
 *
 * The wobble is deliberately strong — two harmonics, ±8% and ±4% of the radius. A gentler one
 * reads as a circle with a rendering artefact rather than as a drawn mark, which is the whole
 * point of the shape.
 *
 * @param startAngle degrees, 0 = 3 o'clock, increasing clockwise.
 * @param sweep degrees.
 */
private fun loopPath(
    centre: Offset,
    radius: Float,
    startAngle: Float,
    sweep: Float,
): Path {
    val path = Path()
    val steps = 90
    for (step in 0..steps) {
        val theta = Math.toRadians((startAngle + sweep * step / steps).toDouble())
        val wobble = 1f + 0.085f * sin(3.0 * theta).toFloat() + 0.04f * cos(2.0 * theta).toFloat()
        val r = radius * wobble
        val x = centre.x + r * cos(theta).toFloat()
        val y = centre.y + r * sin(theta).toFloat()
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/** The two bars of the pause glyph that sits inside [SketchedLoop]. */
@Composable
fun PauseGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 7.dp,
) {
    Canvas(modifier = modifier) {
        val bar = barWidth.toPx()
        val top = size.height * 0.2f
        val bottom = size.height * 0.8f
        val gap = size.width * 0.18f
        val centreX = size.width / 2f
        drawLine(
            color = color,
            start = Offset(centreX - gap, top),
            end = Offset(centreX - gap, bottom),
            strokeWidth = bar,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centreX + gap, top),
            end = Offset(centreX + gap, bottom),
            strokeWidth = bar,
            cap = StrokeCap.Round,
        )
    }
}
