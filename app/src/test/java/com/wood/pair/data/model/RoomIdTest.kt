package com.wood.pair.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The rules that decide whether a room ID is accepted.
 *
 * These matter more than they look. A room ID is read aloud and retyped, so every rule here
 * exists to remove a way of getting it wrong; and [normalize] is the one place a typo becomes
 * either a helpful "that is not a room ID" or a baffling "room not found".
 */
class RoomIdTest {

    @Test
    fun `generated ids are the right length and use only the alphabet`() {
        repeat(500) {
            val id = RoomId.generate()
            assertEquals(RoomId.LENGTH, id.length)
            assertTrue("unexpected character in $id", id.all { it in RoomId.ALPHABET })
        }
    }

    @Test
    fun `the alphabet omits the four characters people mistype`() {
        listOf('I', 'O', '0', '1').forEach { ambiguous ->
            assertFalse(
                "$ambiguous must not be in the alphabet",
                ambiguous in RoomId.ALPHABET,
            )
        }
    }

    @Test
    fun `normalize accepts an id already in canonical form`() {
        val id = RoomId.generate()
        assertEquals(id, RoomId.normalize(id))
        assertTrue(RoomId.isValid(id))
    }

    @Test
    fun `normalize repairs the ways people actually retype an id`() {
        val expected = "A7K92P"
        // Separators pasted in, a leading hash, stray case, and surrounding whitespace.
        assertEquals(expected, RoomId.normalize("  a7k-92p "))
        assertEquals(expected, RoomId.normalize("#A7K92P"))
        assertEquals(expected, RoomId.normalize("A7 K9 2P"))
        assertEquals(expected, RoomId.normalize("a7k_92p"))
    }

    @Test
    fun `normalize rejects ids that cannot be a room id`() {
        // Wrong length, either side.
        assertNull(RoomId.normalize("A7K92"))
        assertNull(RoomId.normalize("A7K92PX"))
        assertNull(RoomId.normalize(""))
        // Right length, but a character the alphabet excludes: these are the typos that would
        // otherwise turn into a "room not found" the user cannot act on.
        assertNull(RoomId.normalize("A7K9IP"))
        assertNull(RoomId.normalize("A7K9OP"))
        assertNull(RoomId.normalize("A7K901"))
        assertNull(RoomId.normalize("A7K9!P"))
    }

    @Test
    fun `isValid requires the canonical form, not merely a repairable one`() {
        assertFalse(RoomId.isValid("a7k92p"))
        assertFalse(RoomId.isValid("A7K-92P"))
        assertTrue(RoomId.isValid("A7K92P"))
    }

    @Test
    fun `ids are not sequential`() {
        // Consecutive calls must not look related, or the sequence itself leaks information
        // about how many rooms exist.
        val ids = (1..200).map { RoomId.generate() }
        assertEquals("ids should not repeat", ids.size, ids.toSet().size)

        val sorted = ids.sorted()
        val adjacentPairsThatDifferByOne = (1 until sorted.size).count {
            sorted[it].first() - sorted[it - 1].first() == 1
        }
        // With 32 symbols, roughly one in sixteen adjacent pairs would differ by one in the
        // first character purely by chance; a counter would produce almost all of them.
        assertTrue(
            "first characters look sequential: $adjacentPairsThatDifferByOne of ${sorted.size - 1}",
            adjacentPairsThatDifferByOne < sorted.size / 4,
        )
    }

    @Test
    fun `generateUnique retries until it finds a free id`() = runBlocking {
        var attempts = 0
        // Collide on the first three attempts regardless of what is generated, so the retry path
        // is exercised rather than depending on which random id happens to come up.
        val result = RoomId.generateUnique {
            attempts++
            attempts <= 3
        }
        assertEquals(4, attempts)
        assertEquals(RoomId.LENGTH, result.length)
        assertTrue(RoomId.isValid(result))
    }

    @Test
    fun `generateUnique gives up rather than looping forever`() = runBlocking {
        var attempts = 0
        RoomId.generateUnique {
            attempts++
            true
        }
        // A hard bound is what stops a full database from hanging the create-room button.
        assertTrue("unbounded retrying: $attempts attempts", attempts in 1..16)
    }
}
