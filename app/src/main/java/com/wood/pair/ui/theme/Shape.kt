package com.wood.pair.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Corner radii used across Pair.
 *
 * The design is deliberately shape-forward, so the scale is wider than Material's default and
 * each role is named for what it is, not for its size. Keeping the roles (rather than
 * scattering radii) is what lets the whole app be retuned from one place.
 */
@Immutable
data class PairShapes(
    /** Text fields and inline inputs. */
    val field: RoundedCornerShape,
    /** Cards: the location headers, the preset list, the live-text stage. */
    val card: RoundedCornerShape,
    /** Small cards, such as the "Location access required" panel. */
    val cardSmall: RoundedCornerShape,
    /** Buttons. Large, because the design's buttons are pill-like. */
    val button: RoundedCornerShape,
    /** Icon buttons and the gear action. */
    val iconButton: RoundedCornerShape,
    /** Pills: nav bar, room-code chip, avatar. */
    val pill: RoundedCornerShape,
    /** Wallpaper preview. */
    val image: RoundedCornerShape,
) {
    /** The M3 [Shapes] handed to `MaterialTheme`, mapped onto its own five steps. */
    fun toMaterialShapes(): Shapes = Shapes(
        extraSmall = field,
        small = cardSmall,
        medium = button,
        large = card,
        extraLarge = card,
    )

    companion object {
        /** Fully rounded, for anything the design draws as a stadium. */
        val Full = RoundedCornerShape(percent = 50)

        val Default = PairShapes(
            // Measured off the comps: the large cards ("Continue to room", "Location
            // Wallpaper", the live-text stage) carry roughly a 24dp radius, and the outlined
            // name fields match them so the two read as one family.
            field = RoundedCornerShape(24.dp),
            card = RoundedCornerShape(24.dp),
            cardSmall = RoundedCornerShape(20.dp),
            button = Full,
            // The edit and settings actions are rounded squares, not circles: about a third of
            // the 56dp box.
            iconButton = RoundedCornerShape(20.dp),
            pill = Full,
            image = RoundedCornerShape(16.dp),
        )
    }
}

val LocalPairShapes: ProvidableCompositionLocal<PairShapes> =
    staticCompositionLocalOf { PairShapes.Default }

/** Pair's shape roles at the current call site. */
val MaterialTheme.pairShapes: PairShapes
    @Composable
    @ReadOnlyComposable
    get() = LocalPairShapes.current
