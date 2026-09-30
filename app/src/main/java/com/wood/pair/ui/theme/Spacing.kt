package com.wood.pair.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale.
 *
 * ## Why this exists
 *
 * Card interiors were written as literals at each call site and had drifted: 12dp on the
 * live-text stage, 18dp on the location rule rows, 20dp on the rule editor's box, 16dp on the
 * peer panel. Four values for what the design treats as one kind of thing - the inset between a
 * card's edge and its content - and the drift was visible as cards that look like the same object
 * at different sizes.
 *
 * One [cardPadding] is what makes a card a card across the app: any two cards can sit next to each
 * other and the gap between edge and text will be identical. The rest of the scale exists for the
 * same reason. A gap that means one thing should be one named value, not five near-identical
 * literals a future reader has to reverse-engineer.
 *
 * Everything is a multiple of 4dp, which is the grid the comps are drawn on.
 */
@Immutable
data class PairSpacing(
    /**
     * The gap from a card's edge to its content.
     *
     * 24dp, not the 20dp the cards used to carry, because 24dp is also [pageGutter]. A card
     * inside the page gutter then lines its content up on a 48dp text column, and the gutter and
     * the inset read as one measure rather than two numbers that happen to be close.
     */
    val cardPadding: Dp,

    /** The horizontal inset of a page's content from the screen edge. */
    val pageGutter: Dp,

    /** The gap between two stacked blocks. */
    val blockGap: Dp,

    /** The gap between a block's label and the block it labels. */
    val labelGap: Dp,

    /** The gap between a block and the one after it, when the two are one thought. */
    val tightGap: Dp,

    /** The inset inside a small panel, such as the background-access notice. */
    val panelPadding: Dp,
) {
    companion object {
        val Default = PairSpacing(
            cardPadding = 24.dp,
            pageGutter = 24.dp,
            blockGap = 20.dp,
            labelGap = 10.dp,
            tightGap = 12.dp,
            panelPadding = 16.dp,
        )
    }
}

/**
 * Pair's spacing roles.
 *
 * Deliberately not a `CompositionLocal`. The shapes are a theme value because Material needs them
 * as one; spacing is not, and routing it through a composition local would imply it can be
 * retuned per theme, which is not true and not wanted.
 */
val Space: PairSpacing = PairSpacing.Default
