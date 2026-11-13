package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.TableTools
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.menu.MenuDestination
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Origin
import au.com.josephmark.dithertracker.widgets.TextSlice
import kotlin.random.Random

/**
 * Random starting player (T16): the menu's FIRST facet closes the menu, picks a player at random among
 * those not KO ([TableTools.pickStartingPlayer]), starts turn tracking from them
 * ([Action.SetStartingPlayer], undoable) and marks **their own tile** — no central panel: an inked
 * band in the under-number row with a `Flavour.startingPlayer` quip in caps, facing that player
 * ([FirstPlayerLayout]). Other tiles are untouched. One redraw shows it; one clears it after
 * [SHOW_MS] or at the next touch anywhere on the table (the touch still does its own thing).
 *
 * A screen-layer overlay (it draws over the tile's content but takes no input), so it needs no zones.
 * [results] hears the pick (nothing logs it yet; T18).
 */
class FirstPlayer(
    private val random: Random = Random(System.nanoTime()),
    private val flavour: Flavour = Flavour.quippy,
    /** Where the pick goes besides the screen (the future game log). */
    var results: ToolResults = ToolResults.NONE,
    val showMillis: Long = SHOW_MS,
) : Component(Layer.OVERLAY, Kind.OVERLAY, timerCount = TIMERS), MenuDestination {

    var layout: FirstPlayerLayout? = null
        private set

    /** The highlighted seat, or −1. */
    var seat = -1
        private set

    /** The caption (caps) on the highlighted seat. */
    var caption = ""
        private set

    var recent: RecentPicks = RecentPicks.EMPTY
        private set

    private val band = IRect()
    private val slice = TextSlice()

    /** [MenuDestination]: FIRST picks now. */
    override fun open(item: MenuItem, facing: Int) {
        if (item == MenuItem.FIRST) pick()
    }

    /** Picks the starting player, records it in the game and shows it on their tile. */
    fun pick() {
        val state = stage.session.state
        if (state.players.isEmpty()) return
        val id = TableTools.pickStartingPlayer(state, random)
        val s = state.players.indexOfFirst { it.id == id }
        val p = flavour.startingPlayer(random, recent)
        recent = p.recent
        results.onStartingPlayer(id)
        stage.dispatch(Origin.EXTERNAL, Action.SetStartingPlayer(id))
        show(s, p.value.uppercase())
    }

    private fun show(s: Int, text: String) {
        clear()
        val l = layout ?: return
        if (s < 0 || s >= PlayLayout.PLAYERS) return
        seat = s
        caption = text
        l.arrange(s, caption)
        invalidate(l.bandRect(s, band))
        timers.schedule(TIMER_CLEAR, showMillis)
    }

    /** Takes the highlight down (one redraw of its band). */
    fun clear() {
        timers.cancel(TIMER_CLEAR)
        if (seat < 0) return
        layout?.let { invalidate(it.bandRect(seat, band)) }
        seat = -1
        caption = ""
    }

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val l = FirstPlayerLayout(play)
        layout = l
        if (seat >= 0) l.arrange(seat, caption)
    }

    override fun prewarm(f: Frame) {
        val l = layout ?: return
        for (rot in ROTATIONS) for (kk in 1..l.kMax) {
            for (line in flavour.lines(QuipKind.STARTING_PLAYER)) f.text.draw(l.ui, line.uppercase(), 0, 0, kk, rot, invert = true)
        }
    }

    override fun draw(f: Frame, seat: Int) {
        val s = this.seat
        if (s < 0) return
        val l = layout ?: return
        if (!f.hit(l.band)) return
        val r = f.r
        r.rect(l.band, ink = true)
        // A paper keyline just inside the edge: the panels' double rule, so the band reads as a badge.
        val b = l.band
        if (b.width > 4 && b.height > 4) r.frame(b.left + 1, b.top + 1, b.width - 2, b.height - 2, 1, ink = false)
        val rot = l.play.rotation[s]
        for (i in 0 until l.lines) {
            val lr = l.line[i]
            f.text.draw(l.ui, slice.set(caption, l.start[i], l.end[i]), lr.left, lr.top, l.k, rot, invert = true)
        }
    }

    override fun bounds(out: IRect): Boolean {
        val l = layout ?: return false
        if (seat < 0) return false
        out.set(l.band.left, l.band.top, l.band.right, l.band.bottom)
        return true
    }

    override fun onEvent(event: Int, seat: Int) {
        // Any touch on the table (a tile's zone) or the centre diamond takes it down.
        if (event == Events.TOUCH_DOWN || event == Events.MENU_OPEN) clear()
    }

    override fun onTimer(id: Int) {
        if (id == TIMER_CLEAR) clear()
    }

    override fun onPlayersChanged() = clear()

    override fun onDetach() = clear()

    companion object {
        const val TIMER_CLEAR = 0
        const val TIMERS = 1

        /** How long the highlight stays without a touch. */
        const val SHOW_MS = 5_000L

        private val ROTATIONS = intArrayOf(0, 180)
    }
}
