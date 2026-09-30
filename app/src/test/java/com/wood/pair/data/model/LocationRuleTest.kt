package com.wood.pair.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which radii are allowed to become a geofence.
 *
 * The bounds are the platform's, not taste: a geofence below about 100m is silently dropped by
 * the geofencing API on most devices, and a very large circle costs battery for no benefit. A
 * rule outside the bounds would be accepted by the editor and then never fire, which is the
 * worst possible outcome for a feature whose entire point is to fire on arrival.
 */
class LocationRuleRadiusTest {

    @Test
    fun `the default radius is inside the allowed range`() {
        assertTrue(LocationRule.isValidRadius(LocationRule.DEFAULT_RADIUS_METERS))
    }

    @Test
    fun `radii at the bounds are allowed and just outside are not`() {
        assertTrue(LocationRule.isValidRadius(LocationRule.MIN_RADIUS_METERS))
        assertTrue(LocationRule.isValidRadius(LocationRule.MAX_RADIUS_METERS))
        assertFalse(LocationRule.isValidRadius(LocationRule.MIN_RADIUS_METERS - 1f))
        assertFalse(LocationRule.isValidRadius(LocationRule.MAX_RADIUS_METERS + 1f))
    }

    @Test
    fun `absurd radii are rejected`() {
        assertFalse(LocationRule.isValidRadius(0f))
        assertFalse(LocationRule.isValidRadius(-100f))
        assertFalse(LocationRule.isValidRadius(50_000f))
        assertFalse(LocationRule.isValidRadius(Float.NaN))
    }

    @Test
    fun `a new rule is active by default`() {
        val rule = LocationRule(
            id = LocationRule.newId(),
            label = "Home",
            latitude = 0.0,
            longitude = 0.0,
            radiusMeters = LocationRule.DEFAULT_RADIUS_METERS,
            timeWindow = null,
            wallpaperUri = "",
        )
        assertTrue("a rule the user just made should be armed", rule.isActive)
    }

    @Test
    fun `new ids are distinct`() {
        val ids = (1..200).map { LocationRule.newId() }
        assertTrue(ids.toSet().size == ids.size)
    }
}

/**
 * A room's membership rules.
 *
 * There is no admin and no invite list: a room has exactly two slots, and everything about
 * access follows from which one a uid occupies. That makes these predicates the whole of the
 * access model, and the interesting case is the one where a uid occupies neither.
 */
class RoomMembershipTest {

    private val room = Room(
        id = "YVXM2M",
        ownerUid = "owner",
        memberUid = "member",
        ownerName = "Ada",
        memberName = "Snoopy",
        live = LiveTexts.Empty,
        createdAt = 0L,
    )

    @Test
    fun `both slots are members, and a third uid is not`() {
        assertTrue(room.isMember("owner"))
        assertTrue(room.isMember("member"))
        assertFalse(room.isMember("stranger"))
        assertFalse(room.isMember(null))
    }

    @Test
    fun `a full room has a partner and no empty slot`() {
        assertTrue(room.hasPartner())
        assertTrue(room.isFull())
    }

    @Test
    fun `a room with one occupant is not full and has no partner`() {
        val waiting = room.copy(memberUid = null, memberName = null)
        assertFalse(waiting.hasPartner())
        assertFalse(waiting.isFull())
        assertTrue(waiting.isMember("owner"))
    }

    @Test
    fun `each slot maps to the right name and slot index`() {
        assertEquals(MemberSlot.Owner, room.slotOf("owner"))
        assertEquals(MemberSlot.Member, room.slotOf("member"))
        assertNull(room.slotOf("stranger"))
        assertEquals("Ada", room.nameOf("owner"))
        assertEquals("Snoopy", room.nameOf("member"))
        assertNull(room.nameOf("stranger"))
    }
}
