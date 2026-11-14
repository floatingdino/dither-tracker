package au.com.josephmark.dithertracker.core.serial

import au.com.josephmark.dithertracker.core.Counter

/** Screen orientation preference. [AUTO] follows the device sensor / system rotation. */
enum class Orientation { AUTO, LANDSCAPE, PORTRAIT }

/**
 * The trackers a player's drawer can offer besides the counters (T53), each of which the settings'
 * "Counters and trackers" page can hide: commander tax, the four player toggles and the table's
 * day/night. Hiding one is display only (see [AppSettings.hiddenTrackers]).
 */
enum class Tracker { COMMANDER_TAX, MONARCH, INITIATIVE, CITYS_BLESSING, RING_BEARER, DAY_NIGHT }

/** What the turn tab's minutes count (T17): nothing, since tracking started, or since this turn began. */
enum class TurnTimer { OFF, GAME, TURN }

/**
 * Non-rule UI settings (SPEC "Settings"). Rule settings (starting life, commander damage affects
 * life, auto-KO) live in [au.com.josephmark.dithertracker.core.GameSettings] inside the game state,
 * and the player count is `GameState.players.size` — neither is duplicated here.
 *
 * Plain data, persisted next to the game by [SaveCodec]. Constructing out-of-range values throws;
 * the decoder sanitises (clamps / falls back to defaults) before constructing, so loading never does.
 */
data class AppSettings(
    /** Device px per dither pixel: [SCALE_AUTO], or [SCALE_MIN]..[SCALE_MAX]. */
    val scale: Int = SCALE_AUTO,
    /** Dark mode: black and white swapped. */
    val inverted: Boolean = false,
    /** Amount applied per repeat while a ± button is held. */
    val holdRepeatStep: Int = 10,
    val keepScreenAwake: Boolean = true,
    /** Counters shown in the player drawer, in display order (distinct). Commander tax is always available. */
    val drawerCounters: List<Counter> = DEFAULT_DRAWER_COUNTERS,
    /** Force a full (flashing) e-ink refresh every N changes to clear ghosting; 0 = never. */
    val fullRefreshEvery: Int = 20,
    /** Haptic feedback on taps. Off by default (SPEC). */
    val haptics: Boolean = false,
    val orientation: Orientation = Orientation.LANDSCAPE,
    /**
     * Opaque layout id chosen for the current player count (interpreted by the layout engine, T11);
     * [DEFAULT_LAYOUT] means "default layout for the count".
     */
    val layout: String = DEFAULT_LAYOUT,
    /**
     * Seats for the next new game (T11), 2..6: chosen in settings, applied when a new game starts
     * (like starting life). The running game's own count is its player list.
     */
    val playerCount: Int = DEFAULT_PLAYER_COUNT,
    /**
     * Turn tracker (T17): the active seat's turn tab, once a starting player is picked. On by default
     * (it shows nothing until then); off hides it and its timer (core keeps tracking turns either way).
     */
    val turnTracker: Boolean = true,
    /** Minutes on the turn tab (T17): none, the game clock, or the time on the current turn. Off by default. */
    val turnTimer: TurnTimer = TurnTimer.OFF,
    /**
     * Trackers hidden from every drawer (T53): their cells are not listed (fewer pages), and their
     * tile badge / diamond mark / tax peek is not drawn. **Display only**: the game state keeps
     * whatever a hidden tracker held, so showing it again brings it back as it was. A set of the
     * hidden ones (not the shown ones) so a tracker added later starts shown on an old save.
     */
    val hiddenTrackers: Set<Tracker> = emptySet(),
) {
    /** [hiddenTrackers] as a bit mask (`1 shl Tracker.ordinal`), for allocation-free checks on draw paths. */
    val hiddenTrackerMask: Int = hiddenTrackers.fold(0) { m, t -> m or (1 shl t.ordinal) }

    /** Whether [t] is shown in the drawer (and its marks on the table). */
    fun shows(t: Tracker): Boolean = hiddenTrackerMask and (1 shl t.ordinal) == 0

    init {
        require(scale == SCALE_AUTO || scale in SCALE_MIN..SCALE_MAX) { "scale out of range: $scale" }
        require(holdRepeatStep in HOLD_STEP_RANGE) { "holdRepeatStep out of range: $holdRepeatStep" }
        require(fullRefreshEvery in FULL_REFRESH_RANGE) { "fullRefreshEvery out of range: $fullRefreshEvery" }
        require(drawerCounters.size == drawerCounters.toSet().size) { "drawerCounters must be distinct" }
        require(playerCount in PLAYER_COUNT_RANGE) { "playerCount out of range: $playerCount" }
    }

    companion object {
        const val SCALE_AUTO = 0
        const val SCALE_MIN = 2
        const val SCALE_MAX = 6
        const val DEFAULT_LAYOUT = ""
        const val DEFAULT_PLAYER_COUNT = 4
        val PLAYER_COUNT_RANGE = 2..6
        val HOLD_STEP_RANGE = 1..100
        val FULL_REFRESH_RANGE = 0..1000

        /** SPEC order: poison, energy, experience, rad, storm, tickets, generic. */
        val DEFAULT_DRAWER_COUNTERS: List<Counter> = listOf(
            Counter.POISON, Counter.ENERGY, Counter.EXPERIENCE, Counter.RAD,
            Counter.STORM, Counter.TICKETS, Counter.GENERIC,
        )
    }
}
