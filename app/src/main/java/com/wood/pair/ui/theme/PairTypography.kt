package com.wood.pair.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.wood.pair.R

/**
 * The app's typefaces.
 *
 *  - **Google Sans Flex** carries every interface role. The 24pt optical instance is used for
 *    UI text; the 120pt instance is used for the wordmark, where the larger optical size
 *    keeps the joints and spacing correct at display scale.
 *  - **Roboto Serif Italic** carries the one serif role in the design — the "Partner"
 *    wordmark and the partner's name.
 */
object PairFonts {

    /** UI face. */
    val Sans: FontFamily = FontFamily(
        Font(R.font.gsf_regular, FontWeight.Normal),
        Font(R.font.gsf_medium, FontWeight.Medium),
        Font(R.font.gsf_semibold, FontWeight.SemiBold),
        Font(R.font.gsf_bold, FontWeight.Bold),
        Font(R.font.gsf_extrabold, FontWeight.ExtraBold),
        Font(R.font.gsf_black, FontWeight.Black),
    )

    /**
     * Display face for the wordmark.
     *
     * Only the two heavy weights are loaded, because only the wordmark uses this family.
     */
    val Display: FontFamily = FontFamily(
        Font(R.font.gsf_bold_display, FontWeight.Bold),
        Font(R.font.gsf_black_display, FontWeight.Black),
    )

    /** The serif face: the lightest weights only, per the brief. */
    val Serif: FontFamily = FontFamily(
        Font(R.font.rs_thinitalic, FontWeight.Thin, FontStyle.Italic),
        Font(R.font.rs_lightitalic, FontWeight.Light, FontStyle.Italic),
        Font(R.font.rs_italic, FontWeight.Normal, FontStyle.Italic),
    )
}

/**
 * Pair's type roles.
 *
 * Sizes and line heights are Material 3's own scale, taken from
 * `androidx.compose.material3.tokens.TypeScaleTokens`, so the rhythm matches the rest of
 * Material even though the typeface is Pair's. The two departures from the baseline are
 * deliberate and both come from the design:
 *
 *  - [wordmark] and [partnerWord] are display scale, because the wordmark is the app's one
 *    piece of large-scale identity.
 *  - [roomId] is display scale so a code can be read aloud from across a desk.
 *
 * Emphasis is used sparingly: the wordmark, the room code, the live text and the section
 * headers. Body copy, status and labels stay on the calm baseline.
 */
@Immutable
data class PairTypography(
    /** "Pair" — the app mark. Display scale, Black. */
    val wordmark: TextStyle,
    /** "with your" — the small line under the wordmark. */
    val wordmarkSub: TextStyle,
    /** "Partner" — the serif line. Roboto Serif Italic, very large. */
    val partnerWord: TextStyle,
    /** Screen and destination identity. */
    val screenTitle: TextStyle,
    /** The room code, display scale. */
    val roomId: TextStyle,
    /** The room code at pill / list size. */
    val roomIdCompact: TextStyle,
    /** The hero live text. */
    val liveText: TextStyle,
    /** The live text in a notification. */
    val liveTextCompact: TextStyle,
    /** A partner's name, set in the serif face. */
    val partnerName: TextStyle,
    /** The fixed half of the partner line: "You are paired with". */
    val pairedWith: TextStyle,
    /** The partner's name within that line, in the only Black on the screen. */
    val pairedWithName: TextStyle,
    /** The heading of the partner's status card: "*Name*'s status". */
    val statusHeading: TextStyle,
    /** The partner's message inside that card. One step below [statusHeading]. */
    val partnerMessage: TextStyle,
    /** Item and component titles. */
    val itemTitle: TextStyle,
    /** Section and group headers. */
    val sectionLabel: TextStyle,
    /** Card and sheet headings, one step down from display. */
    val cardTitle: TextStyle,
    /** Reading and descriptive content. */
    val body: TextStyle,
    /** Secondary, supporting copy. */
    val supporting: TextStyle,
    /** Metadata: connection state, coordinates, hints. */
    val status: TextStyle,
    /** Button and control labels. */
    val controlLabel: TextStyle,
) {
    companion object {
        private fun TextStyle.tight(): TextStyle = copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )

        private fun sans(
            size: Int,
            lineHeight: Int,
            weight: FontWeight,
            tracking: Double = 0.0,
        ) = TextStyle(
            fontFamily = PairFonts.Sans,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = lineHeight.sp,
            letterSpacing = tracking.sp,
        )

        /**
         * Sizes are derived from the comps by measuring cap height and dividing by the face's
         * cap-height ratio, rather than by eye. The comps are 1080 px wide, which is 411 dp on
         * the reference device, so every measurement was divided by 2.625.
         */
        val Default: PairTypography = PairTypography(
            // The wordmark is the heaviest thing in the app, and it is the only place the
            // 120pt optical instance is used.
            wordmark = TextStyle(
                fontFamily = PairFonts.Display,
                fontWeight = FontWeight.Black,
                fontSize = 50.sp,
                lineHeight = 54.sp,
                letterSpacing = (-1.6).sp,
            ).tight(),
            wordmarkSub = sans(size = 24, lineHeight = 30, weight = FontWeight.Bold, tracking = 0.0),
            // Roboto Serif Italic is a wider face per unit of height than the comp's Didone, so
            // matching the comp's cap height exactly would push "Partner" off the screen. 52sp
            // keeps it the visual anchor of the lockup while still fitting beside the wordmark.
            // Roboto Serif Italic is a wider face per unit of height than the comp's Didone, so
            // matching the comp's cap height exactly would push "Partner" off the screen. 52sp
            // gives the comp's 204dp width, which is what keeps the lockup on one line.
            partnerWord = TextStyle(
                fontFamily = PairFonts.Serif,
                // The brief asks for the lightest serif; Thin it is.
                fontWeight = FontWeight.Thin,
                fontStyle = FontStyle.Italic,
                fontSize = 52.sp,
                lineHeight = 56.sp,
                letterSpacing = 0.sp,
            ).tight(),

            screenTitle = sans(size = 24, lineHeight = 30, weight = FontWeight.Bold),
            roomId = sans(size = 30, lineHeight = 36, weight = FontWeight.Black, tracking = 0.5),
            roomIdCompact = sans(size = 15, lineHeight = 20, weight = FontWeight.Bold, tracking = 0.8),
            liveText = sans(size = 34, lineHeight = 44, weight = FontWeight.Bold, tracking = 0.0),
            liveTextCompact = sans(size = 17, lineHeight = 24, weight = FontWeight.Medium),
            partnerName = TextStyle(
                fontFamily = PairFonts.Serif,
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic,
                // 20sp, not 24 or 28. The name sits on the same line as "You are paired with" and
                // has to read as part of the same sentence at the same visual size. Roboto Serif
                // Italic carries a much larger apparent size per point than the sans beside it,
                // so the two only look level when the serif is set *smaller* on the page than the
                // sans — 20 here against the prefix's 17.
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
            /**
             * "You are paired with" — the fixed half of the partner line.
             *
             * Google Sans Flex at Medium, in the sans face, upright. This was an italic serif and
             * has been moved to the sans face for three reasons: the sentence now shares a line
             * with the room code chip and has to hold its own against it, an italic at small size
             * next to a UI chip reads as a quote rather than as a fact, and the serif's apparent
             * size at a given point size is much larger, so the same nominal size no longer means
             * the same thing across the two faces.
             *
             * 18sp. The line has to fit "You are paired with Snoopy" and the chip on one line at
             * 411dp, and 18 is what fits without ellipsising the name.
             */
            pairedWith = sans(size = 18, lineHeight = 26, weight = FontWeight.Medium, tracking = 0.1),
            /**
             * The partner's name, in the same line and the same face at Black.
             *
             * The only Black in the app outside the wordmark and the room code. It is the one word
             * on the screen that names a person, and the weight is what makes the sentence read as
             * "you are paired with *Snoopy*" rather than as an undifferentiated clause. Matching
             * the prefix's size exactly is the point: a larger name reads as a heading and breaks
             * the line.
             */
            pairedWithName = sans(size = 18, lineHeight = 26, weight = FontWeight.Black, tracking = 0.1),
            itemTitle = sans(size = 17, lineHeight = 24, weight = FontWeight.Medium, tracking = 0.15),
            // The partner's status is the single thing this screen exists to tell you, so its
            // heading is set at headline scale rather than at card-title scale. 28sp Bold in the
            // sans face: the sentence above it is already an italic serif, and a second italic
            // serif here would make two things compete for the same voice.
            statusHeading = sans(size = 28, lineHeight = 34, weight = FontWeight.Bold, tracking = 0.0),
            /**
             * The partner's message, one step below [statusHeading] and one step lighter.
             *
             * A step down, not a step up: the message is the content, the heading is the label,
             * and a message set larger than its own heading inverts the hierarchy. Medium rather
             * than Bold because this is something the partner said, not an announcement Pair is
             * making — at Bold it reads as a push notification that has spilled onto the page.
             * 24sp keeps it the largest thing on the screen after its own heading.
             */
            partnerMessage = sans(size = 24, lineHeight = 32, weight = FontWeight.Medium, tracking = 0.0),
            sectionLabel = sans(size = 18, lineHeight = 24, weight = FontWeight.Bold, tracking = 0.1),
            cardTitle = sans(size = 22, lineHeight = 28, weight = FontWeight.Bold, tracking = 0.0),
            body = sans(size = 17, lineHeight = 24, weight = FontWeight.Normal, tracking = 0.15),
            supporting = sans(size = 15, lineHeight = 21, weight = FontWeight.Normal, tracking = 0.2),
            status = sans(size = 15, lineHeight = 21, weight = FontWeight.Medium, tracking = 0.1),
            controlLabel = sans(size = 17, lineHeight = 22, weight = FontWeight.Medium, tracking = 0.1),
        )
    }
}

val LocalPairTypography: ProvidableCompositionLocal<PairTypography> =
    staticCompositionLocalOf { PairTypography.Default }

/** Pair's type roles at the current call site. */
val MaterialTheme.pairTypography: PairTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalPairTypography.current
