package com.wood.pair.ui.components

import androidx.annotation.DrawableRes
import com.wood.pair.R

/**
 * The avatar set offered in Settings.
 *
 * These are shipped with the app rather than fetched: an avatar is part of a person's identity
 * here — it is how the partner recognises who they are paired with — so it has to be available
 * on first launch, offline, and before any account exists.
 *
 * Selection is persisted as the [id], not the resource id, so the drawable names can change
 * without invalidating saved choices.
 */
object Avatars {

    data class Option(
        val id: String,
        @DrawableRes val resId: Int,
    )

    /** Ordered as the comps lay them out: two rows of five. */
    val all: List<Option> = listOf(
        Option("12", R.drawable.avatar_12),
        Option("13", R.drawable.avatar_13),
        Option("14", R.drawable.avatar_14),
        Option("15", R.drawable.avatar_15),
        Option("16", R.drawable.avatar_16),
        Option("17", R.drawable.avatar_17),
        Option("19", R.drawable.avatar_19),
        Option("20", R.drawable.avatar_20),
        Option("27", R.drawable.avatar_27),
        Option("3d21", R.drawable.avatar_3d_21),
    )

    private val byId: Map<String, Int> = all.associate { it.id to it.resId }

    /**
     * The avatar shown before anyone has chosen one.
     *
     * An avatar is part of a person's identity here, so the app never opens with a bare initial:
     * the first of the set is the default everywhere — top bar, and as the pre-selected choice
     * in the picker.
     */
    val defaultId: String = all.first().id

    /**
     * Resolves a stored id to a drawable, falling back to [defaultId].
     *
     * The fallback covers "never chose one" and "chose one from a build that had a different
     * set" alike; both should land on a real avatar rather than on a letter.
     */
    @DrawableRes
    fun resIdOf(id: String?): Int = byId[id] ?: byId.getValue(defaultId)

    /**
     * The id the picker should show as selected, which is the default when nothing is stored.
     *
     * Kept alongside [resIdOf] so the grid and the top bar can never disagree about which avatar
     * is current.
     */
    fun selectedId(stored: String?): String = if (byId.containsKey(stored)) stored!! else defaultId
}
