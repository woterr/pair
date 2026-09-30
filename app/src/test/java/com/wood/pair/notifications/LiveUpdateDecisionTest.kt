package com.wood.pair.notifications

import com.wood.pair.data.model.LiveTexts
import com.wood.pair.data.model.Room
import com.wood.pair.data.repository.RoomObservation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a room event means for the Live Update that is already on screen.
 *
 * This is the logic that makes a live status live with the app closed. The service watches the
 * room for as long as there is a chip, and every event it receives is turned into one of three
 * answers by [decideLiveUpdate]: change the chip, leave it alone, or take it down.
 *
 * Those three are not interchangeable, and the two easy mistakes are specific:
 *
 *  - Treating the subscription's first emission as "nothing to show" would withdraw the chip the
 *    instant it was posted, because the service subscribes *after* posting. [RoomObservation.Loading]
 *    therefore has to mean "not known yet".
 *  - Treating an unreadable room as an error to retry would leave a chip on the status bar that
 *    nothing is keeping correct, which is worse than no chip.
 */
class LiveUpdateDecisionTest {

    private fun room(
        ownerUid: String = "ada",
        memberUid: String? = "snoopy",
        live: LiveTexts = LiveTexts.Empty,
    ) = Room(
        id = "YVXM2M",
        ownerUid = ownerUid,
        memberUid = memberUid,
        ownerName = "Ada",
        memberName = memberUid?.let { "Snoopy" },
        live = live,
        createdAt = 0L,
    )

    private fun ready(room: Room) = RoomObservation.Ready(room)

    private fun decide(
        observation: RoomObservation,
        uid: String? = "ada",
        enabled: Boolean = true,
        shown: String? = null,
    ) = decideLiveUpdate(observation, uid, enabled, shown)

    // ------------------------------------------------------------------ posting

    @Test
    fun `a partner's new status replaces the chip`() {
        val decision = decide(
            ready(room(live = LiveTexts(byUid = mapOf("snoopy" to "On the bus")))),
            shown = "At the gym",
        )

        assertEquals(LiveUpdateDecision.Post("On the bus"), decision)
    }

    @Test
    fun `with no partner the chip shows your own status`() {
        val decision = decide(
            ready(room(memberUid = null, live = LiveTexts(byUid = mapOf("ada" to "At the gym")))),
        )

        assertEquals(LiveUpdateDecision.Post("At the gym"), decision)
    }

    @Test
    fun `a partner joining switches the chip from your status to theirs`() {
        val live = LiveTexts(byUid = mapOf("ada" to "At the gym", "snoopy" to "On the bus"))
        val decision = decide(ready(room(memberUid = "snoopy", live = live)), shown = "At the gym")

        // The chip was showing the solo fallback; the moment a partner exists the rule changes
        // whose status belongs there, and the watch is what makes that happen without the app.
        assertEquals(LiveUpdateDecision.Post("On the bus"), decision)
    }

    @Test
    fun `a partner leaving falls back to showing your own status again`() {
        val live = LiveTexts(byUid = mapOf("ada" to "At the gym", "snoopy" to "On the bus"))
        val decision = decide(ready(room(memberUid = null, live = live)), shown = "On the bus")

        assertEquals(LiveUpdateDecision.Post("At the gym"), decision)
    }

    // ------------------------------------------------------------------ withdrawing

    @Test
    fun `a partner clearing their status takes the chip down`() {
        val decision = decide(
            ready(room(live = LiveTexts(byUid = mapOf("ada" to "At the gym", "snoopy" to "")))),
            shown = "On the bus",
        )

        // Not the viewer's own status. Falling back to "mine" when the partner goes quiet is the
        // exact confusion the per-uid map was built to remove, and it is withdrawn rather than
        // substituted.
        assertEquals(LiveUpdateDecision.Withdraw, decision)
    }

    @Test
    fun `a room that can no longer be read takes the chip down`() {
        assertEquals(
            LiveUpdateDecision.Withdraw,
            decide(RoomObservation.Unavailable, shown = "On the bus"),
        )
    }

    @Test
    fun `turning live updates off takes the chip down`() {
        val decision = decide(
            ready(room(live = LiveTexts(byUid = mapOf("snoopy" to "On the bus")))),
            enabled = false,
            shown = "On the bus",
        )

        assertEquals(LiveUpdateDecision.Withdraw, decision)
    }

    @Test
    fun `no identity means the chip cannot be trusted, so it comes down`() {
        val decision = decide(
            ready(room(live = LiveTexts(byUid = mapOf("snoopy" to "On the bus")))),
            uid = null,
            shown = "On the bus",
        )

        // Without a uid there is no way to tell a partner's status from your own, and every one
        // of those possibilities is a different thing to show.
        assertEquals(LiveUpdateDecision.Withdraw, decision)
    }

    // ------------------------------------------------------------------ leaving alone

    @Test
    fun `the first emission of a subscription leaves an existing chip alone`() {
        // This is the one that would be invisible in manual testing. The service posts the chip
        // and *then* subscribes, so `Loading` arrives straight after a successful post. Reading it
        // as "nothing to show" would withdraw the chip the moment it appeared, every time.
        assertEquals(
            LiveUpdateDecision.Unchanged,
            decide(RoomObservation.Loading, shown = "On the bus"),
        )
    }

    @Test
    fun `an event that resolves to the text already shown is left alone`() {
        val decision = decide(
            ready(room(live = LiveTexts(byUid = mapOf("snoopy" to "On the bus")))),
            shown = "On the bus",
        )

        // The room fires on *both* members' writes, so most events do not concern this device.
        // Rebuilding the notification for those would be pure churn on a status bar surface.
        assertEquals(LiveUpdateDecision.Unchanged, decision)
    }

    @Test
    fun `an event from the viewer's own status does not change a chip showing their partner's`() {
        // The mirror of the case above, and the reason the chip is derived from the room rather
        // than from the write that triggered it: writing your own status must not put your words
        // in your own notification while a partner is present.
        val live = LiveTexts(byUid = mapOf("ada" to "At the gym", "snoopy" to "On the bus"))
        val decision = decide(ready(room(live = live)), shown = "On the bus")

        assertEquals(LiveUpdateDecision.Unchanged, decision)
    }
}
