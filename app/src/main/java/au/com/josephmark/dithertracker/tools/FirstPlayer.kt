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
 * Random starting player (T16, "who will it be" beat T52): the menu's FIRST facet picks a player at
 * random among those not KO ([TableTools.pickStartingPlayer]) — fixed at the tap — and first holds
 * one **"ME?" keyframe** for [WHO_MS] (× `ToolsDebug.holdScale`): every live seat's badge band
 * outlined in dots with "ME?" in caps, facing its player, so the whole table wonders. Then the pick
 * lands: turn tracking starts from them ([Action.SetStartingPlayer], undoable; dispatched only now,
 * so the turn tab cannot give it away early) and **their own tile** gets the badge — an inked band in
 * the under-number row with a `Flavour.startingPlayer` quip in caps ([FirstPlayerLayout]); the
 * other bands clear in the same redraw. One more redraw clears the badge after [SHOW_MS] or at the
 * next touch anywhere on the table (the touch still does its own thing).
 *
 * A touch (or the menu) during the "ME?" beat lands the pick at once; [Events.PAUSE] lands it too and
 * leaves no timer armed (the badge's clear timer is re-armed on [Events.RESUME]).
 *
 * A screen-layer overlay (it draws over the tile's content but takes no input), so it needs no zones.
 * [results] hears the pick when it lands (nothing logs it yet; T18).
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

    /** Seats showing the "ME?" band (bit per seat) while the pick is pending; 0 otherwise. */
    var asking = 0
        private set

    /** The pending pick (player id, seat) while [asking]. */
    private var pendingId = -1
    private var pendingSeat = -1
    private var pendingCaption = ""
    private var paused = false

    private val band = IRect()
    private val slice = TextSlice()

    /** [MenuDestination]: FIRST picks now. */
    override fun open(item: MenuItem, facing: Int) {
        if (item == MenuItem.FIRST) pick()
    }

    /** Picks the starting player now; shows the "ME?" beat, then lands it ([land]). */
    fun pick() {
        val state = stage.session.state
        if (state.players.isEmpty()) return
        cancelAsk()
        clear()
        val id = TableTools.pickStartingPlayer(state, random)
        val p = flavour.startingPlayer(random, recent)
        recent = p.recent
        pendingId = id
        pendingSeat = state.players.indexOfFirst { it.id == id }
        pendingCaption = p.value.uppercase()
        val l = layout
        var mask = 0
        if (l != null && l.enabled && !paused) {
            for ((i, pl) in state.players.withIndex()) if (i < l.play.playerCount && !state.isKo(pl)) mask = mask or (1 shl i)
        }
        if (mask == 0) { land(); return }
        asking = mask
        invalidateAsking()
        timers.schedule(TIMER_LAND, ToolsDebug.hold(WHO_MS))
    }

    /** Lands the pending pick: records it in the game and shows it on their tile. No-op when none. */
    fun land() {
        val id = pendingId
        if (id < 0) return
        timers.cancel(TIMER_LAND)
        invalidateAsking()
        asking = 0
        pendingId = -1
        results.onStartingPlayer(id)
        stage.dispatch(Origin.EXTERNAL, Action.SetStartingPlayer(id))
        val st = stage.session.state
        val s = st.players.indexOfFirst { it.id == id }
        show(if (s >= 0) s else pendingSeat, pendingCaption)
    }

    /** Whether the "ME?" beat is showing. */
    val isAsking: Boolean get() = pendingId >= 0

    /** Drops a pending pick without recording it (the table changed under it). */
    private fun cancelAsk() {
        timers.cancel(TIMER_LAND)
        invalidateAsking()
        asking = 0
        pendingId = -1
    }

    private fun invalidateAsking() {
        val l = layout ?: return
        for (i in 0 until l.play.playerCount) if ((asking ushr i) and 1 == 1) invalidate(l.bandRect(i, band))
    }

    private fun show(s: Int, text: String) {
        clear()
        val l = layout ?: return
        if (s < 0 || s >= l.play.playerCount) return
        seat = s
        caption = text
        l.arrange(s, caption)
        invalidate(l.bandRect(s, band))
        if (!paused) timers.schedule(TIMER_CLEAR, showMillis)
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
        asking = asking and ((1 shl play.playerCount) - 1)
        if (seat >= play.playerCount) { seat = -1; caption = "" }
        if (seat >= 0) l.arrange(seat, caption)
    }

    override fun prewarm(f: Frame) {
        val l = layout ?: return
        for (rot in l.play.rotations) for (kk in 1..l.kMax) {
            for (line in flavour.lines(QuipKind.STARTING_PLAYER)) f.text.draw(l.ui, line.uppercase(), 0, 0, kk, rot, invert = true)
        }
        for (rot in l.play.rotations) for (kk in 1..l.askKMax) f.text.draw(l.ui, ME, 0, 0, kk, rot)
    }

    override fun draw(f: Frame, seat: Int) {
        val l = layout ?: return
        if (asking != 0) drawAsking(f, l)
        val s = this.seat
        if (s < 0) return
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

    /** The "ME?" keyframe: each asking seat's band outlined in dots, "ME?" centred, facing its player. */
    private fun drawAsking(f: Frame, l: FirstPlayerLayout) {
        val r = f.r
        for (i in 0 until l.play.playerCount) {
            if ((asking ushr i) and 1 == 0) continue
            l.bandRect(i, band)
            if (!f.hit(band)) continue
            r.dottedH(band.left, band.top, band.width)
            r.dottedH(band.left, band.bottom - 1, band.width)
            r.dottedV(band.left, band.top, band.height)
            r.dottedV(band.right - 1, band.top, band.height)
            val k = l.askFootprint(i, ME.length, band)
            if (k > 0) f.text.draw(l.ui, ME, band.left, band.top, k, l.play.rotation[i])
        }
    }

    override fun bounds(out: IRect): Boolean {
        val l = layout ?: return false
        if (seat < 0) return false
        out.set(l.band.left, l.band.top, l.band.right, l.band.bottom)
        return true
    }

    override fun onEvent(event: Int, seat: Int) {
        when (event) {
            // Any touch on the table (a tile's zone) or the centre diamond: during the "ME?" beat it
            // lands the pick (on the next loop: never a game change inside another dispatch); after
            // it, it takes the badge down.
            Events.TOUCH_DOWN, Events.MENU_OPEN -> if (isAsking) timers.schedule(TIMER_LAND, 0) else clear()
            Events.PAUSE -> {
                paused = true
                land()
                timers.cancel(TIMER_CLEAR)
            }
            Events.RESUME -> {
                paused = false
                if (this.seat >= 0) timers.schedule(TIMER_CLEAR, showMillis)
            }
        }
    }

    override fun onTimer(id: Int) {
        when (id) {
            TIMER_CLEAR -> clear()
            TIMER_LAND -> land()
        }
    }

    override fun onPlayersChanged() {
        cancelAsk()
        clear()
    }

    override fun onDetach() {
        land()
        clear()
    }

    companion object {
        const val TIMER_CLEAR = 0
        const val TIMER_LAND = 1
        const val TIMERS = 2

        /** The "ME?" beat (SPEC rule 4: one keyframe, held long enough to read across the table). */
        const val WHO_MS = 450L

        const val ME = "ME?"

        /** How long the highlight stays without a touch. */
        const val SHOW_MS = 5_000L

    }
}
