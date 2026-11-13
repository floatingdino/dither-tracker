package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * Every widget once, in one two-column [Panel] — a pure "screen" for an on-device visual check
 * (draw it with [drawWidgetGallery] from any view) and the reference for how a screen is built:
 * widgets → [layout] → [region] for hits → [onTap]/[onRepeat] apply the standard behaviours →
 * re-layout + re-collect.
 *
 * Not wired into the app.
 */
class WidgetGallery(val metrics: WidgetMetrics) {

    val newGame = Button(NEW_GAME, "NEW GAME")
    val reset = Button(RESET, "RESET", Icon.RESET, ButtonStyle.FILLED)
    val undo = Button(UNDO, "UNDO").also { it.enabled = false }
    val dice = IconButton(DICE, Icon.DICE)
    val history = ListRow(HISTORY, "History", icon = Icon.HISTORY, chevron = true)
    val scale = ListRow(SCALE, "Scale", value = "AUTO")
    val autoKo = Toggle(AUTO_KO, "Auto KO", checked = true)
    val quips = Toggle(QUIPS, "Quips")
    val life = Stepper(LIFE, "Starting life", 40, 1, 999)
    val players = Segmented(PLAYERS, "Players", arrayOf("2", "3", "4", "5", "6"), selected = 2)
    val log: PagedList
    val confirm = ConfirmPrompt(CONFIRM, "Reset the game? Everyone goes back to 40.", "RESET", "NO")
    val panel: Panel

    val frame = WidgetFrame()
    val region = HitRegion()

    private val logRows = Array(PagedList.MAX_SLOTS) { ListRow(Widget.NO_ID, "") }
    private val logText = Array(LOG_ITEMS) { "Turn ${it / 2 + 1}: seat ${it % 4 + 1} ${if (it % 3 == 0) "+" else "-"}${it % 7 + 1}" }

    init {
        log = PagedList(LOG, LOG_ITEMS, logRows, preferredRows = 3)
        log.binder = PagedList.Binder { row, index -> (row as ListRow).label = logText[index] }
        panel = Panel(
            arrayOf(
                ButtonRow(newGame, reset, undo, dice, align = TextLayout.START),
                history, scale, autoKo, quips, life, players,
                log, confirm,
            ),
            title = "Widgets",
            closeId = CLOSE,
            columnBreak = 7,
        )
    }

    /** Centres the panel on the screen rect (dither px), facing [rotation], and collects hits. */
    fun layout(screen: IRect, rotation: Int = 0) {
        val m = metrics
        val side = TextLayout.isSideways(rotation)
        val sw = if (side) screen.height else screen.width
        val sh = if (side) screen.width else screen.height
        val w = maxOf(1, sw - 2 * m.gap)
        val h = minOf(panel.naturalHeight(m, w), maxOf(1, sh - 2 * m.gap))
        panel.layout(m, 0, 0, w, h)
        frame.setCentred(screen, rotation, w, h)
        collect()
    }

    /** Re-lays out in place after a model change, and re-collects hits. */
    fun relayout() {
        panel.layout(metrics, 0, 0, panel.bounds.width, panel.bounds.height)
        collect()
    }

    private fun collect() {
        region.clear()
        panel.collectHits(region, frame)
    }

    fun draw(painter: WidgetPainter, pressed: PressTracker? = null) {
        painter.draw(frame, panel)
        if (pressed != null) painter.drawPressed(region, pressed)
    }

    /** Standard tap behaviour for [id]; true when something changed (redraw). */
    fun onTap(id: Int): Boolean {
        val changed = when {
            autoKo.owns(id) -> { autoKo.checked = autoKo.toggled(); true }
            quips.owns(id) -> { quips.checked = quips.toggled(); true }
            life.owns(id) -> life.apply(life.partOf(id), repeat = false)
            players.owns(id) -> players.select(players.partOf(id))
            log.owns(id) -> log.onTap(log.partOf(id))
            else -> false
        }
        if (changed) relayout()
        return changed
    }

    /** Hold-repeat step for [id] (the stepper); true when the value changed. */
    fun onRepeat(id: Int): Boolean {
        if (!life.owns(id)) return false
        val changed = life.apply(life.partOf(id), repeat = true)
        if (changed) relayout()
        return changed
    }

    companion object {
        const val NEW_GAME = 0
        const val RESET = 1
        const val UNDO = 2
        const val DICE = 3
        const val HISTORY = 4
        const val SCALE = 5
        const val AUTO_KO = 6
        const val QUIPS = 7
        const val LIFE = 8 // + 9
        const val PLAYERS = 10 // .. 14
        const val LOG = 15 // + 16
        const val CONFIRM = 17 // + 18
        const val CLOSE = 19
        const val ID_COUNT = 20

        const val LOG_ITEMS = 11
    }
}
