package com.wood.pair.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Pair's motion scheme.
 *
 * IMPORTANT / VERIFIED LIMITATION
 * -------------------------------
 * The brief asked for `MotionScheme.expressive()` and
 * `MaterialTheme(motionScheme = MotionScheme.expressive())`.
 * In the `androidx.compose.material3` version this project builds against (1.4.0, via
 * Compose BOM 2026.09.00) those symbols — and `ExperimentalMaterial3ExpressiveApi` itself —
 * are declared `internal` inside the material3 artifact, so they cannot be called from
 * application code. See docs/EXPRESSIVE_LIMITATION.md.
 *
 * Rather than approximate it blindly, this type reproduces Material's *actual* expressive
 * motion tokens, copied verbatim from
 * `androidx.compose.material3.tokens.ExpressiveMotionTokens` in the same artifact. This
 * object is the single source of motion in the app.
 *
 * ## How to choose a spec
 *
 * Material's physics system has two kinds of spring and three speeds, and the choice is not
 * taste — it is a property of *what is being animated*.
 *
 * **Spatial** springs move something: position, size, scale, rotation, corner radius. They
 * **overshoot** the target and bounce into place, so they must never drive an opacity or a
 * colour. Overshooting alpha below 0 or a colour past its value produces visible artefacts, and
 * Material's effects springs are deliberately critically damped for exactly that reason.
 *
 * **Effects** springs drive colour and opacity. No overshoot, ever.
 *
 * Speed follows the size of the thing moving:
 *
 * | Speed | Spatial | Effects |
 * |---|---|---|
 * | `fast` | small components — buttons, switches, chips, the nav pill | a small element's colour change |
 * | `default` | things that partly cover the screen — sheets, a rail, a panel | content opacity within one of those |
 * | `slow` | full-screen transitions — a destination swap, a full refresh | full-screen content |
 *
 * Every `*Content` transition in this app is a pair: a spatial spec for the movement and an
 * effects spec for the accompanying fade. Mixing them up is the most common way an otherwise
 * correct motion system stops feeling correct.
 *
 * The rules that follow from this are what a review of this file should be checking for:
 *
 * - Nothing animates with a bare `tween(...)`. A duration is not a spec; springs retarget when
 *   interrupted, and a status bar that is re-posted mid-animation is exactly that case.
 * - Nothing reaches for `MotionScheme.Expressive` by name at a call site. The scheme goes
 *   through [MaterialTheme.pairMotion] so that the reduced-motion preference in [reducedMotion]
 *   actually reaches it.
 * - Nothing overrides [reducedMotion] with its own branch. The specs already collapse to `snap()`
 *   when motion is reduced; a local branch either contradicts that or does nothing, and both are
 *   ways a control ends up animating for someone who asked it not to.
 * - A paired movement-and-fade uses the *same* speed on both axes. A `fast` spatial slide under a
 *   `default` effects fade is two animations of different lengths pretending to be one.
 * - An `indication` is bounded by a preceding `clip`. `ripple()` with no shape bounds itself to
 *   the layout rectangle, so on a circular or rounded target the press paints a hard-edged box
 *   over the shape. Clip first, then indicate.
 */
@Immutable
data class MotionScheme(
    val defaultSpatialDamping: Float,
    val defaultSpatialStiffness: Float,
    val fastSpatialDamping: Float,
    val fastSpatialStiffness: Float,
    val slowSpatialDamping: Float,
    val slowSpatialStiffness: Float,
    val defaultEffectsDamping: Float,
    val defaultEffectsStiffness: Float,
    val fastEffectsDamping: Float,
    val fastEffectsStiffness: Float,
    val slowEffectsDamping: Float,
    val slowEffectsStiffness: Float,
    /**
     * When true, every spec resolves to an instant [snap] instead of a spring.
     *
     * This is applied here, in the one place motion is defined, rather than at each call
     * site. That is deliberate: it makes it impossible to add an animation later that quietly
     * ignores the user's accessibility preference, which is a named release requirement in
     * Material 3's accessibility guidance.
     */
    val reducedMotion: Boolean = false,
) {
    /** Spring for layout/position/size change: the default Material expressive spatial spring. */
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(
            dampingRatio = defaultSpatialDamping,
            stiffness = defaultSpatialStiffness,
        )

    /** Spring for quick, snappy spatial change (chips appearing, sheet detents). */
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(
            dampingRatio = fastSpatialDamping,
            stiffness = fastSpatialStiffness,
        )

    /** Spring for large, slow spatial change (full-screen expand/collapse). */
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(
            dampingRatio = slowSpatialDamping,
            stiffness = slowSpatialStiffness,
        )

    /** Spring for colour/alpha. No overshoot: matches Material expressive effects spring. */
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(
            dampingRatio = defaultEffectsDamping,
            stiffness = defaultEffectsStiffness,
        )

    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(
            dampingRatio = fastEffectsDamping,
            stiffness = fastEffectsStiffness,
        )

    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(
            dampingRatio = slowEffectsDamping,
            stiffness = slowEffectsStiffness,
        )

    companion object {
        /**
         * Material 3 Expressive motion tokens, verbatim from the material3 artifact
         * (`ExpressiveMotionTokens`). Damping/stiffness pairs.
         */
        val Expressive = MotionScheme(
            defaultSpatialDamping = 0.8f,
            defaultSpatialStiffness = 380f,
            fastSpatialDamping = 0.6f,
            fastSpatialStiffness = 800f,
            slowSpatialDamping = 0.8f,
            slowSpatialStiffness = 200f,
            defaultEffectsDamping = 1.0f,
            defaultEffectsStiffness = 1600f,
            fastEffectsDamping = 1.0f,
            fastEffectsStiffness = 3800f,
            slowEffectsDamping = 1.0f,
            slowEffectsStiffness = 800f,
        )

        /**
         * Material 3 standard motion tokens (`StandardMotionTokens`), for places where a
         * calmer feel is wanted.
         */
        val Standard = MotionScheme(
            defaultSpatialDamping = 1.0f,
            defaultSpatialStiffness = 400f,
            fastSpatialDamping = 1.0f,
            fastSpatialStiffness = 800f,
            slowSpatialDamping = 1.0f,
            slowSpatialStiffness = 250f,
            defaultEffectsDamping = 1.0f,
            defaultEffectsStiffness = 1900f,
            fastEffectsDamping = 1.0f,
            fastEffectsStiffness = 3800f,
            slowEffectsDamping = 1.0f,
            slowEffectsStiffness = 900f,
        )

        /** The expressive scheme, with every animation removed when [reduced] is true. */
        fun ExpressiveRespecting(reduced: Boolean): MotionScheme =
            if (reduced) Expressive.copy(reducedMotion = true) else Expressive
    }
}

val LocalMotionScheme: ProvidableCompositionLocal<MotionScheme> =
    staticCompositionLocalOf { MotionScheme.Expressive }

/**
 * Pair's motion scheme at the current call site.
 *
 * Named `pairMotion` rather than `motionScheme` on purpose: material3 declares an *internal*
 * `MaterialTheme.motionScheme` member, and a member always beats an extension, so an
 * extension with the same name would be unreachable and confusing.
 */
val MaterialTheme.pairMotion: MotionScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalMotionScheme.current
