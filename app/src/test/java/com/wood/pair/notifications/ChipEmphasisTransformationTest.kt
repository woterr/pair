package com.wood.pair.notifications

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which characters of a status the Live Update chip will carry.
 *
 * The field bolds that run so the boundary is visible where the status is written, rather than
 * discovered later on a locked screen. The risk in a small feature like this is not that it looks
 * wrong — it is that the emphasis and the chip disagree, and the disagreement is invisible until
 * somebody's status silently loses its first word. So these assertions are written against
 * [LiveUpdateNotifier.compactText], the function that decides what the chip shows, rather than
 * against a number repeated here.
 */
class ChipEmphasisTransformationTest {

    /**
     * The emphatic run, as an index range into [text], or null when nothing is emphasised.
     *
     * Read from the public surface rather than from the applied spans: `getSpanStyles` is not
     * part of the API this module compiles against, and a test that reaches into a string's
     * internal layout is asserting an implementation detail of Compose rather than a property of
     * this code. The boundary is [ChipEmphasisTransformation.visibleLength] — the same number
     * the transformation styles to.
     */
    private fun emphaticRange(text: String): IntRange? =
        ChipEmphasisTransformation.emphasisedRange(text)

    @Test
    fun `the chip's characters are the emphatic ones`() {
        // The design's own example: "Headed" is what the chip carries, " to baker's street" is
        // not, and the weight change is the last place those two facts are both visible.
        val text = "Headed to baker's street"
        val emphatic = emphaticRange(text)

        assertEquals(0..5, emphatic)
        assertEquals("Headed", text.substring(emphatic!!.first, emphatic.last + 1))
    }

    @Test
    fun `a short status is emphatic in full`() {
        // Nothing is being hidden, so nothing should look truncated.
        assertEquals(0..3, emphaticRange("Home"))
    }

    @Test
    fun `a status at exactly the cap is entirely emphatic`() {
        val text = "Heading"
        val emphatic = emphaticRange(text)

        assertEquals(0..6, emphatic)
        assertEquals(text, text.substring(emphatic!!.first, emphatic.last + 1))
    }

    @Test
    fun `a status one character past the cap emphasises only the cap`() {
        val text = "Heading2"
        val emphatic = emphaticRange(text)

        assertEquals(LiveUpdateNotifier.MAX_SHORT_TEXT_CHARS, emphatic!!.count())
        assertEquals("Heading", text.substring(emphatic.first, emphatic.last + 1))
    }

    @Test
    fun `an empty status emphasises nothing, so the placeholder is left alone`() {
        assertEquals(null, emphaticRange(""))
        assertEquals(null, emphaticRange("   "))
    }

    @Test
    fun `the boundary never lands inside collapsed whitespace`() {
        // "A  b" compacts to "A b", so taking the compact length as an index into the original
        // would put the boundary at 3 — which is "A  ", a space and a half. The chip shows "A b"
        // in full, so the whole of it should be emphatic.
        val text = "A  b"
        val emphatic = emphaticRange(text)

        assertEquals(0..3, emphatic)
        assertEquals(text, text.substring(emphatic!!.first, emphatic.last + 1))
    }

    @Test
    fun `a leading space does not consume part of the budget`() {
        val text = "  Headed to baker's street"
        val emphatic = emphaticRange(text)

        // The chip shows "Headed" — the leading spaces collapse away entirely, so they must not
        // eat into the seven characters available for real text.
        assertEquals("Headed", text.substring(emphatic!!.first, emphatic.last + 1))
    }

    @Test
    fun `emphasis never overruns the text`() {
        for (text in listOf("", " ", "A", "Hi", "Heading to the shop", "Headed to baker's street", "   ")) {
            val emphatic = emphaticRange(text)
            if (emphatic != null) {
                assertTrue(
                    "emphasis $emphatic overruns \"$text\"",
                    emphatic.first >= 0 && emphatic.last < text.length,
                )
            }
        }
    }

    @Test
    fun `the value itself is never altered`() {
        // A VisualTransformation is a rendering concern. Anything that rewrote the text would
        // change what gets published, because the field's value is the status.
        for (text in listOf("Headed to baker's street", "  A  b  ", "Heading ")) {
            val result = ChipEmphasisTransformation.filter(AnnotatedString(text))
            assertEquals(text, result.text.text)
        }
    }

    @Test
    fun `the cursor offset mapping is the identity, so typing is unaffected`() {
        // Anything else would move the caret as the user types, which is the classic symptom of a
        // transformation that forgot to say where its offsets went.
        val result = ChipEmphasisTransformation.filter(AnnotatedString("Headed to baker's street"))
        val mapping = result.offsetMapping

        for (offset in 0.."Headed to baker's street".length) {
            assertEquals(offset, mapping.originalToTransformed(offset))
            assertEquals(offset, mapping.transformedToOriginal(offset))
        }
    }

    @Test
    fun `an incoming decoration is cleared on the emphatic run`() {
        // A status that arrived carrying a link underline would be legible on the notification
        // and absent from the chip, which is precisely the mismatch this feature exists to stop.
        val underlined = buildAnnotatedString {
            append("Headed out")
            addStyle(
                style = SpanStyle(
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                ),
                start = 0,
                end = 11,
            )
        }
        val result = ChipEmphasisTransformation.filter(underlined)

        // The plain text survives untouched, which is what the field publishes.
        assertEquals("Headed out", result.text.text)

        // "Headed out" is 10 characters, so the chip carries "Headed" plus the space that
        // `compactText` then trims away — six, not seven. The emphasis follows the chip, so
        // marking that seventh, invisible character would be marking something nobody ever sees.
        assertEquals(6, ChipEmphasisTransformation.visibleLength("Headed out"))
    }

    @Test
    fun `the boundary agrees with the text the chip will post`() {
        // The assertion that matters: for any status, everything emphasised is what the chip
        // shows, and everything the chip shows is emphasised. If the chip's rules change, this is
        // the test that fails rather than a status quietly losing its first word on a lock screen.
        val statuses = listOf(
            "Headed to baker's street",
            "On the bus",
            "Getting home soon",
            "Home",
            "Running late, sorry!",
            "At the gym",
            "  spaced   out  text  ",
            "Heading ",
        )
        for (status in statuses) {
            val emphatic = emphaticRange(status)!!
            val emphasised = status.substring(emphatic.first, emphatic.last + 1)
            val chip = LiveUpdateNotifier.compactText(status)

            assertEquals(
                "emphasis and chip disagree for \"$status\"",
                chip,
                emphasised.trim(),
            )
        }
    }
}
