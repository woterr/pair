package com.wood.pair.notifications

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/**
 * Bolds the part of a status that the Live Update chip will actually show.
 *
 * ## Why
 *
 * The chip is 96dp wide and the platform documents roughly seven characters of
 * [LiveUpdateNotifier.MAX_SHORT_TEXT_CHARS] before it ellipsizes. Everything past that is on the
 * notification but off the chip — and it is the chip that is glanced at, because the chip is what
 * is on a locked screen. Typing "Headed to baker's street" and seeing only "Headed t" in the one
 * place you will read it is a small surprise every single time, and the fix is to show the
 * boundary in the field itself: the characters the chip will carry are emphatic, the rest is not.
 *
 * ## The boundary is derived, not declared
 *
 * [visibleLength] is what decides where the weight changes, and it asks
 * [LiveUpdateNotifier.compactText] rather than comparing against a number. That is the whole
 * discipline here: the chip's rules — collapsing whitespace, the character cap, the trailing trim
 * — live in exactly one place, so the emphasis cannot quietly disagree with what gets posted. If
 * the cap changes there, the field follows it. A second copy of the number is a second thing to
 * forget.
 *
 * Note that the emphasised run is not always exactly
 * [LiveUpdateNotifier.MAX_SHORT_TEXT_CHARS] characters. Compact text trims a trailing space, so
 * "Heading " is eight characters long, compacts to the seven-character "Heading", and marks
 * seven. What is emphasised is what the chip shows, not what fits in a fixed budget.
 *
 * Nothing about the value changes. This is a [VisualTransformation] with an identity
 * [OffsetMapping], so the cursor, selection, IME composing region and autofill all behave exactly
 * as they do in a plain field — the emphasis is drawn, never stored.
 */
object ChipEmphasisTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val emphatic = emphasisedRange(text.text)
        if (emphatic == null || emphatic.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val transformed = buildAnnotatedString {
            // The un-emphasised remainder first, so a style on the incoming string is not lost.
            append(text)
            // The emphasised run is added as a *new* span over the existing characters rather
            // than by re-appending the substring, so a single string drives both the visible
            // text and the styling and the two cannot drift apart.
            addStyle(
                style = SpanStyle(
                    // Heavier than the field's own weight, which is
                    // [com.wood.pair.ui.theme.PairTypography.liveText] — Bold. This is the whole
                    // trap in this feature: the type is already Bold, so applying *Bold* to the
                    // chip's characters marks nothing at all and the feature is invisible while
                    // every test still passes. The emphasised run has to exceed the base weight,
                    // not match it.
                    //
                    // Black rather than a larger point size, because the brief is that the rest of
                    // the text stays exactly as it is; only the chip's characters change, and the
                    // emphasis has to come from weight alone.
                    fontWeight = EMPHASIS_WEIGHT,
                    // The chip is a foreground status surface, never a link, so this deliberately
                    // clears any decoration the caller's style carried. A status that arrived
                    // with a link underline would otherwise be legible on the notification and
                    // absent from the chip.
                    textDecoration = TextDecoration.None,
                ),
                start = emphatic.first,
                end = emphatic.last + 1,
            )
        }

        return TransformedText(transformed, OffsetMapping.Identity)
    }

    /**
     * The run of characters in [text] that the chip will carry, or null if it carries none.
     *
     * A range over the *original* text, not a count, because the two are not the same thing
     * whenever whitespace collapses. `"A  b"` compacts to `"A b"`: the compact form has length 3,
     * but index 3 in the original is the second space, so a length used directly as an index
     * would put the boundary inside a run of whitespace and emphasise nothing the chip shows.
     *
     * The walk mirrors [LiveUpdateNotifier.compactText] exactly — trim, collapse runs to a single
     * character, keep at most [LiveUpdateNotifier.MAX_SHORT_TEXT_CHARS], then trim the trailing
     * whitespace back off — and returns where that lands in the original. Null for text with
     * nothing in it, which is what leaves the placeholder alone.
     */
    fun emphasisedRange(text: String): IntRange? {
        // The start: `trim()` drops leading whitespace entirely rather than collapsing it, so the
        // first character the chip will ever show is the first non-space one.
        val start = text.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return null

        var kept = 0
        var index = start
        var inWhitespace = false
        while (index < text.length && kept < LiveUpdateNotifier.MAX_SHORT_TEXT_CHARS) {
            if (text[index].isWhitespace()) {
                if (!inWhitespace) kept++
                inWhitespace = true
            } else {
                kept++
                inWhitespace = false
            }
            index++
        }

        // The end: a boundary that landed on trailing whitespace would emphasise a space the
        // chip does not carry, so it is pulled back to the last real character.
        var end = index
        while (end > start && text[end - 1].isWhitespace()) end--

        // A chip carrying only whitespace — `"  "` — has nothing worth emphasising.
        return if (end <= start) null else start until end
    }

    /**
     * How many leading characters the chip will carry, for callers that want the count rather
     * than the range. Equivalent to `emphasisedRange(text)?.count() ?: 0`.
     */
    fun visibleLength(text: String): Int = emphasisedRange(text)?.count() ?: 0

    /**
     * The weight applied to the characters the chip will carry.
     *
     * Must be *heavier* than the field's own weight, which is Bold. Anything equal or lighter
     * produces no visible change at all, because every character is already Bold — the failure is
     * silent, the code reads correctly, and the tests that only assert on spans still pass. The
     * render is what catches it, and it is the only thing that did.
     */
    val EMPHASIS_WEIGHT: FontWeight = FontWeight.Black
}
