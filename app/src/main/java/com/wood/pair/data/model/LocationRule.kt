package com.wood.pair.data.model

import android.app.WallpaperManager
import java.util.Calendar
import java.util.UUID

/**
 * Which wallpaper surfaces a rule changes.
 *
 * ## Why a rule needs to choose
 *
 * "Home screen wallpaper" and "lock screen wallpaper" are two separate things on Android, set
 * through two separate flags, and a person who cares about one very often does not want the
 * other. Setting both by default is how you end up with a lock screen nobody chose: the app
 * changes something the user never asked it to, on the surface they look at most, and there is
 * no per-rule way to undo it short of deleting the rule.
 *
 * [Both] exists because plenty of people do want the pair of them, and that should be one tap
 * rather than two rules fighting over the same place.
 *
 * The stored value is the [name] of one of these, never the ordinal. Ordinals are a wire format
 * for a list that has already been written down somewhere; renaming a case would silently
 * re-point every existing rule at a different surface.
 */
enum class WallpaperTarget(val storageKey: String) {
    /** The home and app drawer. */
    Home("home"),

    /** The lock screen and always-on display. */
    Lock("lock"),

    /** Both surfaces, set in one pass so they never briefly disagree. */
    Both("both"),
    ;

    /**
     * The [WallpaperManager] flags this target needs.
     *
     * [Both] is a single combined value rather than two calls, because two calls set the two
     * surfaces at slightly different moments and a person glancing at the device during the
     * second sees a mismatched pair.
     */
    val flags: Int
        get() = when (this) {
            Home -> WallpaperManager.FLAG_SYSTEM
            Lock -> WallpaperManager.FLAG_LOCK
            Both -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }

    companion object {
        /**
         * Reads a stored key, falling back to [Home].
         *
         * The fallback rather than a throw is deliberate: a rule written by an older build has no
         * target, and losing somebody's wallpaper rule because a new field was added would be a
         * far worse outcome than that rule landing on the default surface. [Home] is the default
         * because it is the less intrusive of the two and the one the app always did.
         */
        fun fromStorageKey(key: String?): WallpaperTarget =
            entries.firstOrNull { it.storageKey == key } ?: Home
    }
}

/**
 * An optional time condition on a location rule.
 *
 * A `null` [LocationRule.timeWindow] means "no time restriction", which is the default. The
 * window is stored as minutes from local midnight so it stays correct across timezone
 * changes, and [daysOfWeek] uses [Calendar] constants (1 = Sunday).
 */
data class TimeWindow(
    val startMinute: Int,
    val endMinute: Int,
    val daysOfWeek: Set<Int>,
) {
    /**
     * True when [minuteOfDay] / [dayOfWeek] fall inside the window.
     *
     * A window whose end is not after its start is treated as wrapping past midnight, so
     * "22:00 to 06:00" behaves the way a person would expect rather than never matching.
     */
    fun contains(minuteOfDay: Int, dayOfWeek: Int): Boolean {
        if (daysOfWeek.isNotEmpty() && dayOfWeek !in daysOfWeek) return false
        val start = startMinute.coerceIn(0, MINUTES_PER_DAY)
        val end = endMinute.coerceIn(0, MINUTES_PER_DAY)
        return when {
            // A window whose end equals its start is a zero-length window, and a zero-length
            // window is not a window: it is what a person gets when they pick the same time for
            // both ends. Treating it as a wrap would make it match every minute of the day, so a
            // rule that looks empty on screen would fire everywhere — the opposite of what
            // choosing the same time twice means.
            end == start -> false
            end > start -> minuteOfDay in start until end
            // Wraps midnight.
            else -> minuteOfDay >= start || minuteOfDay < end
        }
    }

    companion object {
        const val MINUTES_PER_DAY: Int = 24 * 60
    }
}

/**
 * "When I arrive at this place, and the time condition holds, set this wallpaper."
 *
 * Rules are device-local personal behaviour, so they live in DataStore and are never
 * uploaded. The repository boundary is designed so a future synced implementation can be
 * dropped in without touching the UI.
 */
data class LocationRule(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val timeWindow: TimeWindow?,
    val wallpaperUri: String,
    val isActive: Boolean = true,
    /**
     * Which surfaces this rule changes. See [WallpaperTarget] for why it is a choice rather than
     * a constant.
     *
     * Defaults to [WallpaperTarget.Home], which is what every rule did before this field
     * existed, so an existing rule keeps behaving exactly as it did.
     */
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.Home,
) {
    companion object {
        /** Android's practical minimum geofence radius, in metres. */
        const val MIN_RADIUS_METERS: Float = 200f

        /**
         * Ceiling on radius. Geofences are not meant for city-scale areas, and a very large
         * circle costs battery for little benefit.
         */
        const val MAX_RADIUS_METERS: Float = 1_000f

        /**
         * The radius a brand new rule starts at.
         *
         * 500m, not the slider's 200m floor. 200m is a *limit* - the smallest circle Android will
         * honour for a geofence - and starting there means every new rule is born at an extreme
         * of the range and has to be dragged inward before it is usable. 500m is the middle of the
         * range and the right first guess for "somewhere I go": a building or a park, not a
         * single desk. The slider still runs 200-1000m, so a tighter fence is one drag away.
         */
        val DEFAULT_RADIUS_METERS: Float = 500f

        fun newId(): String = UUID.randomUUID().toString()

        fun isValidRadius(radius: Float): Boolean =
            radius in MIN_RADIUS_METERS..MAX_RADIUS_METERS
    }
}
