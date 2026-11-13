package au.com.josephmark.dithertracker.history

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.menu.MenuDestination
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Cover
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.ZoneSink
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.widgets.RendererCanvas
import au.com.josephmark.dithertracker.widgets.WidgetMetrics
import au.com.josephmark.dithertracker.widgets.WidgetPainter
import kotlin.random.Random

/**
 * The game history overlay (T18): the menu's LOG destination. A square [HistoryCard] centred on the
 * table, facing the side the LOG facet faces (S, or N for the upper facets), newest entry first,
 * paged (never scrolled), with a rotate button (90° steps), an UNDO button (`session.undo()`, the
 * newest group) and close. While open a [Cover] hides everything below the overlay layer — the table
 * is blank paper around the card and takes no input; a tap outside the card closes it.
 *
 * Live: every game change while open (undo from here, or anything else) rebuilds the row index (only
 * when the log object changed) and re-lays the current page, clamped. Allocation: opening, rotating,
 * page turns and draws allocate nothing once laid out; a changed log rebuilds an int index whose
 * array only grows by doubling.
 */
class HistoryOverlay(
    private val random: Random = Random(System.nanoTime()),
    private val flavour: Flavour = Flavour.quippy,
) : Component(Layer.OVERLAY, Kind.OVERLAY, zonesPerSeat = ZONES), MenuDestination {

    lateinit var cover: Cover
        private set

    val card = HistoryCard()

    var isOpen = false
        private set

    /** Clockwise rotation the card faces: 0 (reads from S), 90, 180 (from N), 270. */
    var rotation = 0
        private set

    private var play: PlayLayout? = null
    private val screen = IRect()

    private var painter: WidgetPainter? = null
    private var painterFor: DitherRenderer? = null

    override fun onAdded() {
        cover = stage.addCover(Cover(Kind.ALL_BELOW_OVERLAY))
    }

    // ---- MenuDestination -------------------------------------------------------------------------

    override fun open(item: MenuItem, facing: Int) {
        rotation = when (facing) {
            90, 180, 270 -> facing
            else -> 0 // S, or the gem's two-faced fold
        }
        card.emptyLine = flavour.emptyHistory(random)
        card.index.reset()
        isOpen = true
        relayout()
        stage.setCover(cover, Cover.ALL_SEATS, screen = true)
        invalidate(screen)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        stage.setCover(cover, 0, screen = false)
        invalidate(screen)
    }

    /** Turns the card a quarter clockwise (to the next side). */
    fun rotateNext() {
        rotation = (rotation + 90) % 360
        relayout()
        invalidate(screen)
    }

    // ---- layout ----------------------------------------------------------------------------------

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        this.play = play
        screen.set(0, 0, play.grid.width, play.grid.height)
        if (isOpen) relayout()
    }

    private fun relayout() {
        val l = play ?: return
        val h = stage.session.history
        card.layout(l.grid.scale, screen, rotation, h.log, h.state, h.canUndo)
    }

    override fun prewarm(f: Frame) {
        val l = play ?: return
        val p = painterOf(f)
        val k = WidgetMetrics(l.grid.scale).k
        for (ic in HistoryFormat.ICONS) p.c.blit(p.scaledIcon(ic, k), 0, 0, 0, false, false)
        for (ic in PREWARM_ICONS) p.c.blit(p.scaledIcon(ic, k), 0, 0, 0, false, false)
        p.c.blit(HistoryArrow.of(k), 0, 0, 0, false, false)
        // Every glyph a row can show: the whole ui face (player names are free text) and num-m.
        f.text.draw(Fonts.UI, UI_CHARS, 0, 0, k)
        f.text.draw(Fonts.NUM_M, NUM_CHARS, 0, 0, k)
    }

    private fun painterOf(f: Frame): WidgetPainter {
        val p = painter
        if (p != null && painterFor === f.r) return p
        return WidgetPainter(RendererCanvas(f.r, f.text)).also { painter = it; painterFor = f.r }
    }

    // ---- drawing ---------------------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        if (!isOpen || play == null) return
        if (!f.hit(card.screenCard)) return
        card.paint(painterOf(f))
    }

    override fun bounds(out: IRect): Boolean {
        if (play == null) return false
        out.set(screen.left, screen.top, screen.right, screen.bottom)
        return true
    }

    // ---- touch -----------------------------------------------------------------------------------

    override fun zones(seat: Int, z: ZoneSink) {
        if (!isOpen || play == null) return
        z.add(DISMISS, screen)
        z.block(card.screenCard)
        for (id in 0 until HistoryCard.ZONES) if (card.live[id]) z.add(id, card.screenHit[id])
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        if (local !in 0 until HistoryCard.ZONES || !card.live[local]) return false
        val r = card.screenFeedback[local]
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    override fun onTap(seat: Int, local: Int) {
        if (!isOpen) return
        when (local) {
            DISMISS, HistoryCard.CLOSE -> close()
            HistoryCard.ROTATE -> rotateNext()
            HistoryCard.UNDO -> {
                val s = stage.session
                if (s.history.canUndo) s.undo() // onStateChanged refreshes the page
            }
            HistoryCard.PREV, HistoryCard.NEXT -> {
                val h = stage.session.history
                if (card.turnPage(local, h.state, h.canUndo)) invalidate(card.screenCard)
            }
        }
    }

    // ---- notifications ---------------------------------------------------------------------------

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) = refresh()

    override fun onPlayersChanged() = refresh()

    private fun refresh() {
        if (!isOpen || play == null) return
        val h = stage.session.history
        card.refresh(h.log, h.state, h.canUndo)
        invalidate(card.screenCard)
    }

    override fun onDetach() {
        close()
    }

    companion object {
        /** Zone locals: the card's ids ([HistoryCard.ROTATE]..[HistoryCard.NEXT]), then a tap outside. */
        const val DISMISS = HistoryCard.ZONES
        const val ZONES = HistoryCard.ZONES + 1

        private val PREWARM_ICONS = arrayOf(
            Icon.ROTATE,
            Icon.UNDO,
            Icon.CLOSE,
            Icon.CHEVRON_LEFT,
            Icon.CHEVRON_RIGHT,
        )
        private val UI_CHARS: String = Fonts.UI.glyphs.joinToString("") { it.char.toString() }
        private const val NUM_CHARS = "0123456789+-"
    }
}
