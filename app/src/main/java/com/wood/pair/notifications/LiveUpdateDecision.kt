package com.wood.pair.notifications

import com.wood.pair.data.repository.RoomObservation

/**
 * What the Live Update service should do about a room event.
 *
 * Extracted from [LiveUpdateService] so the decision is a value rather than a sequence of
 * side effects. It is the one place that answers "given what the room now says, does the chip
 * change?" and it is pure, which is what makes it testable at all — the service around it is
 * a `Service` full of notification-manager calls, none of which run on the JVM.
 */
sealed interface LiveUpdateDecision {

    /**
     * Take the chip down and stop the service.
     *
     * One outcome for all the reasons there is nothing to show — no longer a member, a status
     * that has gone blank, Live Updates switched off. They are the same act, and treating them
     * differently is how a chip ends up stranded on a status bar with nothing keeping it right.
     */
    data object Withdraw : LiveUpdateDecision

    /** Put [text] on screen, replacing whatever is there. */
    data class Post(val text: String) : LiveUpdateDecision

    /**
     * Leave things exactly as they are.
     *
     * Not an optimisation: it is what keeps a subscription's first emission from tearing down a
     * chip that is already correct.
     */
    data object Unchanged : LiveUpdateDecision
}

/**
 * Decides what a room event means for this device's Live Update.
 *
 * The displayed text itself is not chosen here. It comes from
 * [com.wood.pair.data.model.LiveTexts.forViewer], the same expression the room screen and the
 * notification's own reply receiver use, so the chip cannot drift from the app — that was the
 * point of not re-deciding "whose status is this?" in a second place.
 *
 * @param currentlyShown the text on screen now, or null if no chip is up. A room event that
 *   resolves to the same text must not rebuild the notification.
 */
fun decideLiveUpdate(
    observation: RoomObservation,
    uid: String?,
    liveUpdateEnabled: Boolean,
    currentlyShown: String?,
): LiveUpdateDecision = when (observation) {
    // The first emission of a fresh subscription, before the room has been read. It means "not
    // known yet" — emphatically not "nothing to show", which is how a chip that was just posted
    // would otherwise be withdrawn by its own subscription.
    is RoomObservation.Loading -> LiveUpdateDecision.Unchanged

    // The room is gone, or this device is no longer a member of it. There is nothing to show
    // and nothing to wait for.
    is RoomObservation.Unavailable -> LiveUpdateDecision.Withdraw

    is RoomObservation.Ready -> when {
        // No identity means no way to tell whose status this is, and no way to tell a
        // partner's from your own. Withdrawing is the only answer that cannot be wrong.
        uid == null -> LiveUpdateDecision.Withdraw

        !liveUpdateEnabled -> LiveUpdateDecision.Withdraw

        else -> {
            val room = observation.room
            val text = room.live.forViewer(viewerUid = uid, partnerUid = room.partnerOf(uid))
            when {
                // Paused. Withdraw rather than post an empty chip: a notification holding a
                // permanent place on someone's screen to say nothing is worse than none, and
                // this is the same rule the room screen's indicator follows.
                text.isBlank() -> LiveUpdateDecision.Withdraw

                text == currentlyShown -> LiveUpdateDecision.Unchanged

                else -> LiveUpdateDecision.Post(text)
            }
        }
    }
}
