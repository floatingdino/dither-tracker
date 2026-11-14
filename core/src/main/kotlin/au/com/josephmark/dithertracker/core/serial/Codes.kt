package au.com.josephmark.dithertracker.core.serial

import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.KoOverride
import au.com.josephmark.dithertracker.core.KoReason
import au.com.josephmark.dithertracker.core.Toggle

/**
 * Stable on-disk codes for every persisted enum. **Never reuse or renumber a code**; add new
 * constants with fresh numbers. The `toCode` functions use exhaustive `when`s, so adding an enum
 * constant without a code is a compile error. `fromCode` returns null for unknown codes and each
 * caller picks a safe fallback, so a stray code never crashes a load.
 */
internal object Codes {
    fun counter(c: Counter): Int = when (c) {
        Counter.POISON -> 1
        Counter.ENERGY -> 2
        Counter.EXPERIENCE -> 3
        Counter.RAD -> 4
        Counter.STORM -> 5
        Counter.TICKETS -> 6
        Counter.GENERIC -> 7
    }

    fun counter(code: Int): Counter? = Counter.entries.firstOrNull { counter(it) == code }

    fun toggle(t: Toggle): Int = when (t) {
        Toggle.MONARCH -> 1
        Toggle.INITIATIVE -> 2
        Toggle.CITYS_BLESSING -> 3
        Toggle.RING_BEARER -> 4
    }

    fun toggle(code: Int): Toggle? = Toggle.entries.firstOrNull { toggle(it) == code }

    /** Bit in the player flags byte; bit 0 is `partner`. */
    fun toggleBit(t: Toggle): Int = 1 shl toggle(t)

    fun dayNight(d: DayNight): Int = when (d) {
        DayNight.UNSET -> 0
        DayNight.DAY -> 1
        DayNight.NIGHT -> 2
    }

    fun dayNight(code: Int): DayNight? = DayNight.entries.firstOrNull { dayNight(it) == code }

    fun koOverride(k: KoOverride): Int = when (k) {
        KoOverride.NONE -> 0
        KoOverride.ALIVE -> 1
        KoOverride.DEAD -> 2
    }

    fun koOverride(code: Int): KoOverride? = KoOverride.entries.firstOrNull { koOverride(it) == code }

    fun koReason(k: KoReason): Int = when (k) {
        KoReason.LIFE -> 1
        KoReason.POISON -> 2
        KoReason.COMMANDER_DAMAGE -> 3
        KoReason.MANUAL -> 4
    }

    fun koReason(code: Int): KoReason? = KoReason.entries.firstOrNull { koReason(it) == code }

    fun orientation(o: Orientation): Int = when (o) {
        Orientation.AUTO -> 0
        Orientation.LANDSCAPE -> 1
        Orientation.PORTRAIT -> 2
    }

    fun orientation(code: Int): Orientation? = Orientation.entries.firstOrNull { orientation(it) == code }

    fun turnTimer(t: TurnTimer): Int = when (t) {
        TurnTimer.OFF -> 0
        TurnTimer.GAME -> 1
        TurnTimer.TURN -> 2
    }

    fun turnTimer(code: Int): TurnTimer? = TurnTimer.entries.firstOrNull { turnTimer(it) == code }

    fun tracker(t: Tracker): Int = when (t) {
        Tracker.COMMANDER_TAX -> 1
        Tracker.MONARCH -> 2
        Tracker.INITIATIVE -> 3
        Tracker.CITYS_BLESSING -> 4
        Tracker.RING_BEARER -> 5
        Tracker.DAY_NIGHT -> 6
    }

    fun tracker(code: Int): Tracker? = Tracker.entries.firstOrNull { tracker(it) == code }
}
