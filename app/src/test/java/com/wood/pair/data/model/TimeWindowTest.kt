package com.wood.pair.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a location rule's time window counts as active.
 *
 * The midnight wrap is the case worth testing: a window whose end is not after its start is
 * read as wrapping, and getting that wrong makes "22:00 to 06:00" never fire — a rule that
 * looks correct on screen and silently does nothing.
 */
class TimeWindowTest {

    private fun window(start: Int, end: Int, days: Set<Int> = emptySet()) =
        TimeWindow(startMinute = start, endMinute = end, daysOfWeek = days)

    @Test
    fun `a plain daytime window contains only its own hours`() {
        val nineToFive = window(9 * 60, 17 * 60)
        assertTrue(nineToFive.contains(9 * 60, dayOfWeek = 2))       // start is inclusive
        assertTrue(nineToFive.contains(16 * 60 + 59, dayOfWeek = 2))
        assertFalse(nineToFive.contains(17 * 60, dayOfWeek = 2))     // end is exclusive
        assertFalse(nineToFive.contains(8 * 60 + 59, dayOfWeek = 2))
    }

    @Test
    fun `a window that wraps midnight contains both sides`() {
        val overnight = window(22 * 60, 6 * 60)
        assertTrue(overnight.contains(22 * 60, dayOfWeek = 2))
        assertTrue(overnight.contains(23 * 60 + 30, dayOfWeek = 2))
        assertTrue(overnight.contains(5 * 60 + 59, dayOfWeek = 2))
        assertFalse(overnight.contains(6 * 60, dayOfWeek = 2))
        assertFalse(overnight.contains(12 * 60, dayOfWeek = 2))
    }

    @Test
    fun `an empty day set means every day`() {
        val anytime = window(9 * 60, 17 * 60, days = emptySet())
        (1..7).forEach { day ->
            assertTrue("day $day should be allowed", anytime.contains(12 * 60, dayOfWeek = day))
        }
    }

    @Test
    fun `a day outside the set is never contained`() {
        val weekdays = window(9 * 60, 17 * 60, days = setOf(2, 3, 4, 5, 6))
        assertTrue(weekdays.contains(12 * 60, dayOfWeek = 3))
        assertFalse(weekdays.contains(12 * 60, dayOfWeek = 1))
        assertFalse(weekdays.contains(12 * 60, dayOfWeek = 7))
    }

    @Test
    fun `the day test is applied before the time test`() {
        // A minute that is inside the hours but on a day the rule excludes must be false, and
        // the order matters: testing time first and then day would give the same answer, but
        // testing time first on a wrapping window would let the wrong side of midnight through.
        val weekdaysOnly = window(22 * 60, 6 * 60, days = setOf(3))
        assertFalse(weekdaysOnly.contains(2 * 60, dayOfWeek = 2))  // inside hours, wrong day
        assertTrue(weekdaysOnly.contains(2 * 60, dayOfWeek = 3))
    }

    @Test
    fun `out of range minutes are clamped rather than excluded`() {
        // A rule saved with a bad minute must not silently never match; clamping to the day
        // keeps the window meaning what it says.
        val full = window(-30, TimeWindow.MINUTES_PER_DAY + 30)
        assertTrue(full.contains(0, dayOfWeek = 2))
        assertTrue(full.contains(TimeWindow.MINUTES_PER_DAY - 1, dayOfWeek = 2))
    }

    @Test
    fun `a zero length window contains nothing`() {
        val empty = window(9 * 60, 9 * 60)
        assertFalse(empty.contains(9 * 60, dayOfWeek = 2))
    }
}
