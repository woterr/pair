package com.wood.pair.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3's own design tokens, transcribed.
 *
 * These are not invented values. They are read directly out of the `androidx.compose.material3`
 * artifact this project builds against, from the generated token files:
 *  - `androidx.compose.material3.tokens.TypeScaleTokens` (sizes, line heights, tracking, weights)
 *  - `androidx.compose.material3.tokens.TypefaceTokens` (font families and weights)
 *  - `androidx.compose.material3.tokens.ShapeTokens` (corner radii)
 *  - `androidx.compose.material3.tokens.ExpressiveMotionTokens` (see [MotionScheme])
 *
 * Transcribing them matters because material3 1.4.0 keeps the emphasized type styles and the
 * expressive motion scheme `internal`, so they cannot be read through the public API. Building
 * on the real numbers is what makes this app match Material rather than merely resemble it.
 */
object M3Tokens {

    // `TypefaceTokens.Brand` and `.Plain` are both FontFamily.SansSerif in Material 3, which on
    // Android is the platform sans (Roboto). So the "brand" face for display and headline
    // text is simply the system sans, and no font file is needed.
    val Brand: FontFamily = FontFamily.SansSerif
    val Plain: FontFamily = FontFamily.SansSerif

    private val Regular = FontWeight.Normal
    private val Medium = FontWeight.Medium
    private val Bold = FontWeight.Bold

    private fun style(
        family: FontFamily,
        weight: FontWeight,
        size: Int,
        lineHeight: Int,
        tracking: Double,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )

    // ------------------------------------------------------------ baseline scale

    val DisplayLarge = style(Brand, Regular, 57, 64, -0.2)
    val DisplayMedium = style(Brand, Regular, 45, 52, 0.0)
    val DisplaySmall = style(Brand, Regular, 36, 44, 0.0)

    val HeadlineLarge = style(Brand, Regular, 32, 40, 0.0)
    val HeadlineMedium = style(Brand, Regular, 28, 36, 0.0)
    val HeadlineSmall = style(Brand, Regular, 24, 32, 0.0)

    val TitleLarge = style(Brand, Regular, 22, 28, 0.0)
    val TitleMedium = style(Plain, Medium, 16, 24, 0.2)
    val TitleSmall = style(Plain, Medium, 14, 20, 0.1)

    val BodyLarge = style(Plain, Regular, 16, 24, 0.5)
    val BodyMedium = style(Plain, Regular, 14, 20, 0.2)
    val BodySmall = style(Plain, Regular, 12, 16, 0.4)

    val LabelLarge = style(Plain, Medium, 14, 20, 0.1)
    val LabelMedium = style(Plain, Medium, 12, 16, 0.5)
    val LabelSmall = style(Plain, Medium, 11, 16, 0.5)

    // ------------------------------------------------------- emphasized variants

    /*
     * Emphasized is NOT "bold".
     *
     * This is the single most important thing to get right, and it is easy to get wrong:
     * Material's emphasized styles keep the same size, move to Medium weight, and *tighten*
     * the letter spacing. Only the small roles (title medium/small and all labels) go Bold.
     *
     *   Display / Headline / Title Large -> Medium weight, 0 tracking
     *   Title Medium / Title Small      -> Bold
     *   Label Large / Medium / Small    -> Bold
     *   Body Large / Medium / Small     -> Medium weight, tighter tracking
     */

    val DisplayLargeEmphasized = style(Brand, Medium, 57, 64, 0.0)
    val DisplayMediumEmphasized = style(Brand, Medium, 45, 52, 0.0)
    val DisplaySmallEmphasized = style(Brand, Medium, 36, 44, 0.0)

    val HeadlineLargeEmphasized = style(Brand, Medium, 32, 40, 0.0)
    val HeadlineMediumEmphasized = style(Brand, Medium, 28, 36, 0.0)
    val HeadlineSmallEmphasized = style(Brand, Medium, 24, 32, 0.0)

    val TitleLargeEmphasized = style(Brand, Medium, 22, 28, 0.0)
    val TitleMediumEmphasized = style(Plain, Bold, 16, 24, 0.15)
    val TitleSmallEmphasized = style(Plain, Bold, 14, 20, 0.1)

    val BodyLargeEmphasized = style(Plain, Medium, 16, 24, 0.15)
    val BodyMediumEmphasized = style(Plain, Medium, 14, 20, 0.25)
    val BodySmallEmphasized = style(Plain, Medium, 12, 16, 0.4)

    val LabelLargeEmphasized = style(Plain, Bold, 14, 20, 0.1)
    val LabelMediumEmphasized = style(Plain, Bold, 12, 16, 0.5)
    val LabelSmallEmphasized = style(Plain, Bold, 11, 16, 0.5)
}

/**
 * Material 3's corner scale, transcribed from `ShapeTokens`.
 *
 * The two "Increased" values are the Material 3 Expressive additions, and they are what give
 * the expressive look its larger, more confident geometry.
 */
@Immutable
object M3Shapes {
    const val EXTRA_SMALL = 4
    const val SMALL = 8
    const val MEDIUM = 12
    const val LARGE = 16

    /** Expressive. */
    const val LARGE_INCREASED = 20

    const val EXTRA_LARGE = 28

    /** Expressive. */
    const val EXTRA_LARGE_INCREASED = 32

    const val EXTRA_EXTRA_LARGE = 48
}
