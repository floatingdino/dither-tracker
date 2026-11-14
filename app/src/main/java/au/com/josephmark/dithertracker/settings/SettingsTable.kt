package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.serial.Tracker
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.eink.EinkConfig
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.widgets.Segmented

/**
 * What the settings card lists (SPEC "Settings", T12) — **the one table later tasks extend**: T11 adds
 * player count / layout / orientation rows, T13 a "Players" [LinkRow] to its names/patterns page, each
 * by adding an entry to [main]. The card ([SettingsCard]) sizes, pages, draws and hit-tests whatever
 * is here.
 *
 * Deliberately absent (SPEC "Not settings — always on"): commander damage reduces life, auto
 * knock-out, effects, quips. Haptics: the stored field stays, but the Nomad has nothing worth
 * exposing.
 */
object SettingsTable {

    /**
     * Turn tracker and its timer (T17), one row: `[OFF][ON][GAME][TURN]` — off; the turn tab only; the
     * tab with the game clock (minutes since the first player was picked); the tab with the minutes on
     * the current turn. Stored as `AppSettings.turnTracker` + `turnTimer` ([turnsValue] / [withTurns]).
     */
    val TURNS = intArrayOf(TURNS_OFF, TURNS_ON, TURNS_GAME, TURNS_TURN)
    val TURNS_LABELS = arrayOf("OFF", "ON", "GAME", "TURN")
    const val TURNS_OFF = 0
    const val TURNS_ON = 1
    const val TURNS_GAME = 2
    const val TURNS_TURN = 3

    /** Player count choices (T11): every count the layout engine seats; takes effect on the next new game. */
    val PLAYERS = intArrayOf(2, 3, 4, 5, 6)

    /** Starting life choices (Sam, 2026-11-13): a three-way segmented choice, no stepper. */
    val STARTING_LIFE = intArrayOf(40, 30, 20)

    /** Life per ± hold-repeat step. */
    val HOLD_STEPS = intArrayOf(5, 10, 20)

    /** Automatic e-ink cleanup cadence (`fullRefreshEvery`, changes between full refreshes; 0 = off). */
    val CLEAN_EVERY = intArrayOf(0, 20, 50, 100)

    /** The counters a player's drawer can show, in their fixed display order. */
    val COUNTERS: List<Counter> = AppSettings.DEFAULT_DRAWER_COUNTERS

    val COUNTER_LABELS = arrayOf("Poison", "Energy", "Experience", "Rad", "Storm", "Tickets", "Generic")
    val COUNTER_ICONS = arrayOf(Icon.POISON, Icon.ENERGY, Icon.EXPERIENCE, Icon.RADS, Icon.STORM, Icon.TICKETS, Icon.GENERIC_COUNTER)

    /**
     * The trackers (T53), in the page's order: commander tax first (it leads the tray), then — after
     * the counters — the four player toggles and day/night, as the drawer lists them.
     */
    val TRACKERS: Array<Tracker> = arrayOf(Tracker.COMMANDER_TAX, Tracker.MONARCH, Tracker.INITIATIVE, Tracker.CITYS_BLESSING, Tracker.RING_BEARER, Tracker.DAY_NIGHT)
    val TRACKER_LABELS = arrayOf("Commander tax", "Monarch", "Initiative", "City's blessing", "Ring-bearer", "Day/night")
    val TRACKER_ICONS = arrayOf(Icon.COMMANDER_TAX, Icon.MONARCH, Icon.INITIATIVE, Icon.CITYS_BLESSING, Icon.RING, Icon.DAY)

    /** The footnote once starting life differs from when the card was opened (+ the value). */
    const val LIFE_HINT = "Next game starts at "

    /** The footnote while the chosen player count differs from the table's (T11): + count + [PLAYERS_HINT_END] (+ `, ` life [LIFE_HINT_END]). */
    const val PLAYERS_HINT = "Next game: "
    const val PLAYERS_HINT_END = " players"
    const val LIFE_HINT_END = " life"

    const val TITLE = "SETTINGS"
    /** The sub-page (T53: "Counters and trackers", Sam's wording) and the main-page row that opens it. */
    const val COUNTERS_TITLE = "COUNTERS AND TRACKERS"
    const val COUNTERS_LABEL = "Counters and trackers"

    init {
        check(COUNTER_LABELS.size == COUNTERS.size && COUNTER_ICONS.size == COUNTERS.size)
        check(TRACKER_LABELS.size == TRACKERS.size && TRACKER_ICONS.size == TRACKERS.size && TRACKERS.toSet() == Tracker.entries.toSet())
    }

    /** The main page, top to bottom (T11 added Players, T17 Turns). Add rows here. */
    fun main(): SettingsPage = SettingsPage(
        TITLE,
        arrayOf(
            players(),
            startingLife(),
            turns(),
            scale(),
            ToggleRow("Invert", { it.app.inverted }, { s, v -> s.updateApp(s.app.copy(inverted = v)) }),
            ChoiceRow("Hold step", labels(HOLD_STEPS), HOLD_STEPS, { it.app.holdRepeatStep }, { s, v -> s.updateApp(s.app.copy(holdRepeatStep = v)) }),
            ToggleRow("Keep awake", { it.app.keepScreenAwake }, { s, v -> s.updateApp(s.app.copy(keepScreenAwake = v)) }),
            screenClean(),
            LinkRow(COUNTERS_LABEL, counters()),
            lifeHint(), // last: appearing never moves a row under a finger
        ),
    )

    /**
     * Seats for the next game (T11): `[2][3][4][5][6]`, stored in `AppSettings.playerCount`, applied by
     * NEW; its › (T53) opens the layouts for the count chosen here ([layouts]).
     */
    fun players(): PlayersRow = PlayersRow(
        "Players", labels(PLAYERS), PLAYERS,
        { it.app.playerCount },
        { s, v -> s.updateApp(s.app.copy(playerCount = v)) },
        layouts(),
    )

    /**
     * The layout picker (T53): back, then a thumbnail per arrangement for the Players row's count
     * ([LayoutPickerRow]); its title names the count.
     */
    fun layouts(): SettingsPage {
        val title = LayoutPickerRow.titleBuffer()
        return SettingsPage(title, arrayOf(BackRow("Settings"), LayoutPickerRow(title)))
    }

    fun turns(): ChoiceRow = ChoiceRow(
        "Turns", TURNS_LABELS, TURNS,
        { turnsValue(it.app) },
        { s, v -> s.updateApp(withTurns(s.app, v)) },
    )

    /** The [TURNS] choice [a] holds. */
    fun turnsValue(a: AppSettings): Int = when {
        !a.turnTracker -> TURNS_OFF
        a.turnTimer == TurnTimer.GAME -> TURNS_GAME
        a.turnTimer == TurnTimer.TURN -> TURNS_TURN
        else -> TURNS_ON
    }

    /** [a] with the [TURNS] choice [v] (OFF keeps the timer choice off too). */
    fun withTurns(a: AppSettings, v: Int): AppSettings = when (v) {
        TURNS_OFF -> a.copy(turnTracker = false, turnTimer = TurnTimer.OFF)
        TURNS_GAME -> a.copy(turnTracker = true, turnTimer = TurnTimer.GAME)
        TURNS_TURN -> a.copy(turnTracker = true, turnTimer = TurnTimer.TURN)
        else -> a.copy(turnTracker = true, turnTimer = TurnTimer.OFF)
    }

    fun startingLife(): ChoiceRow = ChoiceRow(
        "Starting life", labels(STARTING_LIFE), STARTING_LIFE,
        { it.game.startingLife },
        { s, v -> s.updateGame(s.game.copy(startingLife = v)) },
    )

    /**
     * The next-game footnote: "Next game starts at 30" once starting life differs from when the card
     * was opened; "Next game: 5 players" while the chosen count differs from the table's (T11); both
     * at once read "Next game: 5 players, 30 life".
     */
    fun lifeHint(): HintRow = HintRow(
        { s, opened, out ->
            val players = s.app.playerCount != s.players
            if (!players) {
                for (i in LIFE_HINT.indices) out.append(LIFE_HINT[i])
                out.append(s.game.startingLife)
            } else {
                for (i in PLAYERS_HINT.indices) out.append(PLAYERS_HINT[i])
                out.append(s.app.playerCount)
                for (i in PLAYERS_HINT_END.indices) out.append(PLAYERS_HINT_END[i])
                if (opened != null && s.game.startingLife != opened.startingLife) {
                    out.append(','); out.append(' ')
                    out.append(s.game.startingLife)
                    for (i in LIFE_HINT_END.indices) out.append(LIFE_HINT_END[i])
                }
            }
        },
        { s, opened -> (opened != null && s.game.startingLife != opened.startingLife) || s.app.playerCount != s.players },
    )

    /**
     * "Screen clean" (T19b). Where the per-view e-ink mode works (a Supernote, T58) it gets a › to the
     * [SCREEN_TITLE] page holding [display] — a row of its own would make the main page page at S=4.
     * (`PlayersRow` is T53's choice-plus-› row; nothing in it is about players but its name.)
     */
    fun screenClean(): ChoiceRow {
        val label = "Screen clean"
        val options = arrayOf("OFF", "20", "50", "100")
        val read = IntRead { it.app.fullRefreshEvery }
        val write = IntWrite { s, v -> s.updateApp(s.app.copy(fullRefreshEvery = v)) }
        if (!EinkConfig.fastDisplayAvailable) return ChoiceRow(label, options, CLEAN_EVERY, read, write)
        return PlayersRow(label, options, CLEAN_EVERY, read, write, SettingsPage(SCREEN_TITLE, arrayOf(BackRow("Settings"), display())))
    }

    /** The page behind Screen clean's › (T58). */
    const val SCREEN_TITLE = "SCREEN"

    /**
     * The e-ink waveform (T58): `[FAST][NORMAL]` (`AppSettings.fastDisplay`; FAST = the Nomad's DU
     * waveform, `eink/EinkDisplay`). Listed only where the per-view mode works (a Supernote).
     */
    fun display(): ChoiceRow = object : ChoiceRow(
        "Display", arrayOf("FAST", "NORMAL"), intArrayOf(1, 0),
        { if (it.app.fastDisplay) 1 else 0 },
        { s, v -> s.updateApp(s.app.copy(fastDisplay = v == 1)) },
    ) {
        override fun visible(s: SettingsAccess): Boolean = EinkConfig.fastDisplayAvailable
    }

    fun scale(): ScaleRow = ScaleRow({ it.app.scale }, { s, v -> s.updateApp(s.app.copy(scale = v)) })

    /**
     * The drawer's sub-page (T12; T53 added the trackers): back, then one toggle per item with its
     * icon, in the tray's order — commander tax, the seven counters, the four player toggles,
     * day/night. Fourteen rows do not fit the card, so the page uses the card's `‹ 1/2 ›` paging.
     */
    fun counters(): SettingsPage {
        val rows = ArrayList<SettingsRow>()
        rows += BackRow("Settings")
        rows += trackerRow(0)
        for (i in COUNTERS.indices) {
            val c = COUNTERS[i]
            rows += ToggleRow(COUNTER_LABELS[i], { it.app.drawerCounters.contains(c) }, { s, v -> s.updateApp(s.app.copy(drawerCounters = withCounter(s.app.drawerCounters, c, v))) }, COUNTER_ICONS[i])
        }
        for (i in 1 until TRACKERS.size) rows += trackerRow(i)
        return SettingsPage(COUNTERS_TITLE, rows.toTypedArray())
    }

    private fun trackerRow(i: Int): ToggleRow {
        val t = TRACKERS[i]
        return ToggleRow(TRACKER_LABELS[i], { it.app.shows(t) }, { s, v -> s.updateApp(s.app.copy(hiddenTrackers = withTracker(s.app.hiddenTrackers, t, v))) }, TRACKER_ICONS[i])
    }

    /** [hidden] with [t] shown ([on]) or hidden. */
    fun withTracker(hidden: Set<Tracker>, t: Tracker, on: Boolean): Set<Tracker> =
        Tracker.entries.filterTo(LinkedHashSet()) { if (it == t) !on else it in hidden }

    /** [current] with [c] shown or hidden, in the fixed [COUNTERS] order. */
    fun withCounter(current: List<Counter>, c: Counter, on: Boolean): List<Counter> =
        COUNTERS.filter { if (it == c) on else it in current }

    /**
     * Scale choices on a [deviceW]×[deviceH] screen: AUTO, then every S in 2..6 whose Play layout is
     * `comfortable` (at most four, the largest — a segmented bar holds five; at least the smallest S).
     */
    fun scaleValues(deviceW: Int, deviceH: Int): IntArray {
        val ok = ArrayList<Int>()
        for (s in DitherGrid.MIN_SCALE..DitherGrid.MAX_SCALE) {
            if (PlayLayout.compute(DitherGrid(deviceW, deviceH, s)).comfortable) ok += s
        }
        // AUTO falls back to the smallest S when nothing is comfortable; offer it rather than nothing.
        if (ok.isEmpty()) ok += DitherGrid.MIN_SCALE
        val keep = ok.takeLast(Segmented.MAX_OPTIONS - 1)
        return IntArray(1 + keep.size) { if (it == 0) AppSettings.SCALE_AUTO else keep[it - 1] }
    }

    private fun labels(vs: IntArray) = Array(vs.size) { vs[it].toString() }
}
