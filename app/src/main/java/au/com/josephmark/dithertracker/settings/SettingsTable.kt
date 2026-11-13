package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.Icon
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

    /** The footnote once starting life differs from when the card was opened (+ the value). */
    const val LIFE_HINT = "Next game starts at "

    const val TITLE = "SETTINGS"
    const val COUNTERS_TITLE = "COUNTERS"

    init {
        check(COUNTER_LABELS.size == COUNTERS.size && COUNTER_ICONS.size == COUNTERS.size)
    }

    /** The main page, top to bottom. Add T11/T13 rows here. */
    fun main(): SettingsPage = SettingsPage(
        TITLE,
        arrayOf(
            startingLife(),
            scale(),
            ToggleRow("Invert", { it.app.inverted }, { s, v -> s.updateApp(s.app.copy(inverted = v)) }),
            ChoiceRow("Hold step", labels(HOLD_STEPS), HOLD_STEPS, { it.app.holdRepeatStep }, { s, v -> s.updateApp(s.app.copy(holdRepeatStep = v)) }),
            ToggleRow("Keep awake", { it.app.keepScreenAwake }, { s, v -> s.updateApp(s.app.copy(keepScreenAwake = v)) }),
            ChoiceRow("Screen clean", arrayOf("OFF", "20", "50", "100"), CLEAN_EVERY, { it.app.fullRefreshEvery }, { s, v -> s.updateApp(s.app.copy(fullRefreshEvery = v)) }),
            LinkRow("Counters", counters()),
            lifeHint(), // last: appearing never moves a row under a finger
        ),
    )

    fun startingLife(): ChoiceRow = ChoiceRow(
        "Starting life", labels(STARTING_LIFE), STARTING_LIFE,
        { it.game.startingLife },
        { s, v -> s.updateGame(s.game.copy(startingLife = v)) },
    )

    /** "Next game starts at 30", once starting life differs from when the card was opened. */
    fun lifeHint(): HintRow = HintRow(
        { s, out ->
            for (i in LIFE_HINT.indices) out.append(LIFE_HINT[i])
            out.append(s.game.startingLife)
        },
        { s, opened -> opened != null && s.game.startingLife != opened.startingLife },
    )

    fun scale(): ScaleRow = ScaleRow({ it.app.scale }, { s, v -> s.updateApp(s.app.copy(scale = v)) })

    /** The drawer-counters sub-page: back, then one toggle per counter (with its icon). */
    fun counters(): SettingsPage = SettingsPage(
        COUNTERS_TITLE,
        Array(1 + COUNTERS.size) { i ->
            if (i == 0) {
                BackRow("Settings")
            } else {
                val c = COUNTERS[i - 1]
                ToggleRow(COUNTER_LABELS[i - 1], { it.app.drawerCounters.contains(c) }, { s, v -> s.updateApp(s.app.copy(drawerCounters = withCounter(s.app.drawerCounters, c, v))) }, COUNTER_ICONS[i - 1])
            }
        },
    )

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
