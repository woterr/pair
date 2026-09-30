package com.wood.pair.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The room's per-member statuses.
 *
 * The rule these tests exist to protect is the one the bug report turned on: **your Live Update
 * shows your partner's status, and only falls back to your own when you have no partner.** When
 * that was a single shared slot, a partner's message landed in the writer's own field instead,
 * because there was only one field and it was theirs by accident.
 */
class LiveTextsTest {

    @Test
    fun `each member's text is addressed by uid, not by who wrote last`() {
        val live = LiveTexts(byUid = mapOf("ada" to "At the gym", "snoopy" to "On the bus"))

        // The whole point. One map, two independent values, neither overwriting the other.
        assertEquals("At the gym", live.textOf("ada"))
        assertEquals("On the bus", live.textOf("snoopy"))
    }

    @Test
    fun `an unknown or absent uid reads as nothing rather than as someone else's`() {
        val live = LiveTexts(byUid = mapOf("ada" to "At the gym"))

        assertEquals("", live.textOf("snoopy"))
        assertEquals("", live.textOf(null))
    }

    @Test
    fun `a partner present means the notification carries the partner's status`() {
        val live = LiveTexts(byUid = mapOf("ada" to "At the gym", "snoopy" to "On the bus"))

        assertEquals("On the bus", live.forViewer(viewerUid = "ada", partnerUid = "snoopy"))
        assertEquals("At the gym", live.forViewer(viewerUid = "snoopy", partnerUid = "ada"))
    }

    @Test
    fun `no partner means the notification carries the viewer's own status`() {
        val live = LiveTexts(byUid = mapOf("ada" to "At the gym"))

        // "Whatever I write is shown to me until a partner joins" is the documented behaviour, so
        // it is a rule with a test rather than a fallback that happens to fall out of the code.
        assertEquals("At the gym", live.forViewer(viewerUid = "ada", partnerUid = null))
    }

    @Test
    fun `a partner who has said nothing leaves the viewer paused, not reading their own words`() {
        val live = LiveTexts(byUid = mapOf("ada" to "At the gym"))

        // The subtle one. Falling back to "my text" whenever the partner's is empty would mean
        // the status bar silently starts showing your own status the moment they clear theirs,
        // which is the exact confusion the per-uid map was introduced to remove.
        assertEquals("", live.forViewer(viewerUid = "ada", partnerUid = "snoopy"))
    }

    @Test
    fun `a cleared status and a never-set one are both nothing to say`() {
        val cleared = LiveTexts(byUid = mapOf("ada" to "", "snoopy" to "On the bus"))
        val neverSet = LiveTexts(byUid = mapOf("snoopy" to "On the bus"))

        assertFalse(cleared.hasTextFor("ada"))
        assertFalse(neverSet.hasTextFor("ada"))
        assertTrue(cleared.hasTextFor("snoopy"))
    }

    @Test
    fun `whitespace is not a status`() {
        val live = LiveTexts(byUid = mapOf("ada" to "   "))

        assertFalse(live.hasTextFor("ada"))
    }

    @Test
    fun `a room maps each member to the other member's uid and name`() {
        val room = Room(
            id = "YVXM2M",
            ownerUid = "ada",
            memberUid = "snoopy",
            ownerName = "Ada",
            memberName = "Snoopy",
            live = LiveTexts(byUid = mapOf("ada" to "At the gym", "snoopy" to "On the bus")),
            createdAt = 0L,
        )

        assertEquals("snoopy", room.partnerOf("ada"))
        assertEquals("Snoopy", room.partnerNameOf("ada"))
        assertEquals("ada", room.partnerOf("snoopy"))
        assertEquals("Ada", room.partnerNameOf("snoopy"))
    }

    @Test
    fun `being alone means there is no partner to name`() {
        val room = Room(
            id = "YVXM2M",
            ownerUid = "ada",
            memberUid = null,
            ownerName = "Ada",
            memberName = null,
            live = LiveTexts.Empty,
            createdAt = 0L,
        )

        assertNull(room.partnerOf("ada"))
        assertNull(room.partnerNameOf("ada"))
    }

    @Test
    fun `a stranger is nobody's partner, even in a full room`() {
        val room = Room(
            id = "YVXM2M",
            ownerUid = "ada",
            memberUid = "snoopy",
            ownerName = "Ada",
            memberName = "Snoopy",
            live = LiveTexts.Empty,
            createdAt = 0L,
        )

        // The security rules keep a stranger from reading the room at all, but the model must not
        // hand out a partner to a uid that is not in it if it is ever asked.
        assertNull(room.partnerOf("stranger"))
        assertNull(room.partnerNameOf("stranger"))
    }
}
