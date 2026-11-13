package au.com.josephmark.dithertracker.settings

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.GameSettings
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.menu.MenuDestination
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Cover
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Origin
import au.com.josephmark.dithertracker.stage.ZoneSink
import au.com.josephmark.dithertracker.widgets.PagedList
import au.com.josephmark.dithertracker.widgets.RendererCanvas
import au.com.josephmark.dithertracker.widgets.WidgetMetrics

/**
 * The settings overlay (T12): the gem's SETUP facet opens a square [SettingsCard] in the middle of
 * the table, facing the side that asked (0 or 180; a two-faced `GemLayout.FOLD` facet → 0, the S side). The card's
 * rotate button turns it a quarter clockwise (S → W → N → E), so any seat can bring it round; its
 * close returns to the play screen.
 *
 * **Cover:** while open, `Cover(ALL_BELOW_OVERLAY − BACKDROP)` on every seat and the screen — life
 * totals, peeks, drawers, tabs, effects, KO and the diamond are neither drawn nor hit-tested, so the
 * card sits alone on paper with only the dotted dividers running out to the edges (calm, few ink pixels
 * for the panel to flip, and nothing half-hidden behind the card). Taps off the card are swallowed (a
 * full-screen block): with players on every side a stray elbow must not close it.
 *
 * Rows come from [SettingsTable] (the extension point); this class only routes: a tap → the row →
 * a write through [SettingsAccess] (`SettingsHolder.update` / `Action.UpdateSettings`). Live effects
 * of the writes (invert, scale, hold step, keep-awake) are applied by their owners' listeners
 * (`PlayView`, `MainActivity`), not here; this overlay just re-reads the settings and redraws the
 * row that changed (or the card, when the rows themselves change).
 */
class SettingsOverlay private constructor(b: Built) :
    Component(Layer.OVERLAY, Kind.OVERLAY, zonesPerSeat = b.zones), MenuDestination {

    constructor(main: SettingsPage = SettingsTable.main()) : this(Built(main))

    private class Built(val main: SettingsPage) {
        /** [main] and every page reachable through its [LinkRow]s. */
        val pages: List<SettingsPage> = ArrayList<SettingsPage>().also { collect(main, it) }

        /** Ids: each page numbers its rows from [SettingsCard.ROW_BASE] (only one page is live at a time). */
        val zones: Int = pages.maxOf { p ->
            var id = SettingsCard.ROW_BASE
            for (r in p.rows) { r.create(id); id += r.maxIds }
            id
        }

        private fun collect(p: SettingsPage, out: MutableList<SettingsPage>) {
            if (out.any { it === p }) return
            out += p
            for (r in p.rows) if (r is LinkRow) collect(r.page, out)
        }
    }

    val main: SettingsPage = b.main
    val pages: List<SettingsPage> = b.pages

    val card = SettingsCard()

    lateinit var cover: Cover
        private set

    var isOpen = false
        private set

    /** Clockwise rotation of the card: 0 (reads from S), 90 (W), 180 (N), 270 (E). */
    var rotation = 0
        private set

    /** The page shown, and the pages it was reached from (a sub-page's back returns there). */
    var page: SettingsPage = b.main
        private set
    private val stack = arrayOfNulls<SettingsPage>(MAX_DEPTH)
    private var depth = 0

    private val screen = IRect()
    private val old = IRect()
    private val prevShown = arrayOfNulls<SettingsRow>(SettingsCard.MAX_ROWS)
    private var painter: SettingsPainter? = null
    private var laidOut = false

    /** True while a tap is being applied (its writes come back through the listeners). */
    private var tapping = false
    private var structureChanged = false

    /** The settings, as this overlay reads and writes them (both persist on change). */
    val access: SettingsAccess = object : SettingsAccess {
        override val app: AppSettings get() = stage.settings.current
        override val game: GameSettings get() = stage.session.state.settings
        override fun updateApp(new: AppSettings) = stage.settings.update(new)
        override fun updateGame(new: GameSettings) {
            if (new != game) stage.dispatch(Origin.EXTERNAL, Action.UpdateSettings(new))
        }
    }

    override fun onAdded() {
        cover = stage.addCover(Cover(COVER_HIDES))
    }

    // ---- open / close ------------------------------------------------------------------------------

    /** [MenuDestination]: opens facing the facet's side (180 → N; 0 and the two-faced fold → S). */
    override fun open(item: MenuItem, facing: Int) = open(if (facing == 180) 180 else 0)

    fun open(rotation: Int) {
        if (isOpen) return
        this.rotation = rotation
        page = main
        depth = 0
        card.list.setPage(0)
        for (p in pages) for (r in p.rows) r.onOpen(access)
        isOpen = true
        refresh()
        stage.setCover(cover, Cover.ALL_SEATS, screen = true)
        if (laidOut) invalidate(card.bounds)
    }

    fun close() {
        if (!isOpen) return
        if (laidOut) invalidate(card.bounds) // over the dividers too (the cover redraws the panels)
        isOpen = false
        stage.setCover(cover, 0, screen = false)
    }

    /** Turns the card a quarter clockwise, to face the next side. */
    fun rotate() {
        if (!isOpen) return
        old.set(card.bounds.left, card.bounds.top, card.bounds.right, card.bounds.bottom)
        rotation = (rotation + 90) % 360
        refresh()
        invalidate(old)
        invalidate(card.bounds)
    }

    // ---- layout / drawing ----------------------------------------------------------------------------

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val g = play.grid
        screen.set(0, 0, g.width, g.height)
        for (p in pages) for (r in p.rows) r.onScreen(g)
        card.measure(WidgetMetrics(g.scale), g.width, g.height, pages)
        laidOut = true
        refresh()
    }

    override fun prewarm(f: Frame) {
        if (!laidOut) return
        val p = painter ?: SettingsPainter(RendererCanvas(f.r, f.text)).also { painter = it }
        refresh()
        p.prewarm(card, pages)
        refresh() // prewarm laid rows out off-card
    }

    override fun draw(f: Frame, seat: Int) {
        if (!isOpen || !laidOut) return
        if (!f.hit(card.bounds)) return
        val p = painter ?: return
        p.draw(card)
    }

    override fun bounds(out: IRect): Boolean {
        if (!isOpen || !laidOut) return false
        out.set(card.bounds.left, card.bounds.top, card.bounds.right, card.bounds.bottom)
        return true
    }

    /** Re-reads the settings into the current page's rows and lays the card out. */
    private fun refresh() {
        if (!laidOut) return
        val n0 = card.shownCount
        for (i in 0 until n0) prevShown[i] = card.shown[i]
        for (r in page.rows) r.bind(access)
        card.show(page, access)
        card.layout(screen, rotation)
        var same = n0 == card.shownCount
        if (same) for (i in 0 until n0) if (prevShown[i] !== card.shown[i]) { same = false; break }
        if (!same) structureChanged = true
    }

    // ---- touch ---------------------------------------------------------------------------------------

    override fun zones(seat: Int, z: ZoneSink) {
        if (!isOpen || !laidOut) return
        z.block(screen)
        val r = card.region
        for (i in 0 until r.size) z.add(r.idAt(i), r.hitAt(i))
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean =
        isOpen && laidOut && card.region.feedbackRect(local, out)

    override fun onTap(seat: Int, local: Int) {
        if (!isOpen) return
        when (local) {
            SettingsCard.CLOSE -> close()
            SettingsCard.ROTATE -> rotate()
            SettingsCard.LIST + PagedList.PREV, SettingsCard.LIST + PagedList.NEXT -> {
                if (card.list.onTap(local - SettingsCard.LIST)) {
                    refresh()
                    invalidate(card.bounds)
                }
            }
            else -> tapRow(local)
        }
    }

    private fun tapRow(local: Int) {
        val row = card.rowOwning(local) ?: return
        tapping = true
        structureChanged = false
        val result: Int
        try {
            result = row.tap(row.widget.partOf(local), access)
        } finally {
            tapping = false
        }
        when (result) {
            SettingsRow.Tap.CHANGED -> {
                refresh()
                if (structureChanged || !card.rowRect(row, old)) invalidate(card.bounds) else invalidate(old)
            }
            SettingsRow.Tap.OPEN -> if (row is LinkRow && depth < MAX_DEPTH) {
                stack[depth++] = page
                show(row.page)
            }
            SettingsRow.Tap.BACK -> if (depth > 0) {
                val back = stack[--depth]!!
                stack[depth] = null
                show(back)
            }
        }
    }

    private fun show(p: SettingsPage) {
        page = p
        card.list.setPage(0)
        refresh()
        invalidate(card.bounds)
    }

    // ---- notifications -------------------------------------------------------------------------------

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) = settingsMoved()

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        if (old.settings != new.settings) settingsMoved()
    }

    /** A write (ours, mid-tap, or anyone's) landed: re-read; outside a tap, redraw the card. */
    private fun settingsMoved() {
        if (!isOpen) return
        refresh()
        if (!tapping) invalidate(card.bounds)
    }

    override fun onDetach() = close()

    companion object {
        /** Everything under the overlay except the dividers (see the class doc). */
        const val COVER_HIDES = Kind.ALL_BELOW_OVERLAY and Kind.BACKDROP.inv()

        /** Sub-page nesting (settings → counters is 1). */
        const val MAX_DEPTH = 4
    }
}
