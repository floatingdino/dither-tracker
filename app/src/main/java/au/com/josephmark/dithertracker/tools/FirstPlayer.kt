package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.core.flavour.RecentPicks
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.eink.DisplaySpeed
import au.com.josephmark.dithertracker.menu.MenuDestination
import au.com.josephmark.dithertracker.menu.MenuItem
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Cover
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Origin
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.ZoneSink
import au.com.josephmark.dithertracker.widgets.TextSlice
import kotlin.random.Random

/**
 * Who goes first (T16; re-imagined in T59 as a d20 roll-off): the menu's FIRST facet makes **every
 * live seat roll a d20 at once, on its own tile** ([FirstRoll]). KO'd seats sit it out.
 *
 * - **Take-over.** While the roll-off runs, a [Cover] hides the rolling seats' life totals, ±,
 *   peeks, badges, drawers, readouts, effects and turn tab, and the centre diamond; each rolling tile
 *   is drawn here instead, unmistakably not a life total: paper ground inside a **double ink frame**,
 *   "D20" where the readout sits, then the die tumbling across the tile (the dice panel's sprites,
 *   scaled to the tile and turned with the seat: 3 poses on a slow display, 5 on a fast one,
 *   `eink.DisplaySpeed`, [TileRollLayout]) and finally **the roll in the hero numerals**, where the
 *   life total was — every tile landing together.
 * - **Landing.** The highest roll goes first: its tile gets the **FIRST flag** (an ink pennant in the
 *   under-number row with a `Flavour.startingPlayer` line, e.g. "ON THE PLAY") and every roll stays up
 *   for [FirstRoll.LANDED_MS]. A tie at the top shows "TIE!" flags for [FirstRoll.TIE_MS] and only the
 *   tied seats roll again (at most [FirstRoll.MAX_ROUNDS] rounds, then a random pick among them).
 * - **Back to the game.** The tiles return to their life totals in one redraw; turn tracking starts
 *   from the winner ([Action.SetStartingPlayer], one undo step — dispatched only now) and
 *   [Events.FIRST_PLAYER] plays T55's `start-flag` effect on the winner's tile as **the sting**; the
 *   FIRST flag stays [SHOW_MS] longer (a beat after the losers' numbers went), or until the next
 *   touch anywhere on the table.
 * - **Input.** During the roll-off a full-screen zone swallows touches: a tap lands every roll at once
 *   (the landing is then held as usual); a tap on the landing goes straight back to the game.
 *   [Events.PAUSE] finishes it (the game is updated, nothing stays armed; the flag's clear timer waits
 *   for [Events.RESUME]).
 *
 * Timers: [FirstRoll]'s one step timer (only while rolling) and the flag's clear timer. Drawing
 * allocates nothing; the tile sprites are built at layout and prewarmed in the LATER phase.
 * [results] hears the winner (nothing logs the rolls themselves: core has no log-only action).
 */
class FirstPlayer(
    private val random: Random = Random(System.nanoTime()),
    private val flavour: Flavour = Flavour.quippy,
    /** Where the pick goes besides the screen (the future game log). */
    var results: ToolResults = ToolResults.NONE,
    val showMillis: Long = SHOW_MS,
    speed: DisplaySpeed.Source = DisplaySpeed.Source { DisplaySpeed.current() },
) : Component(Layer.OVERLAY, Kind.OVERLAY, zonesPerSeat = 1, timerCount = TIMERS), MenuDestination {

    var layout: FirstPlayerLayout? = null
        private set

    var tiles: TileRollLayout? = null
        private set

    /** The roll-off (public for tests and captures). */
    val roll = FirstRoll(subTimers(), random, { done -> changed(done) }, speed)

    /** The seat showing the FIRST flag after the roll-off (−1: none). */
    var seat = -1
        private set

    /** The flag's line (caps) for the current / last roll-off. */
    var caption = ""
        private set

    var recent: RecentPicks = RecentPicks.EMPTY
        private set

    private lateinit var cover: Cover
    private var paused = false
    private val band = IRect()
    private val tmp = IRect()
    private val screen = IRect()
    private val slice = TextSlice()

    /** [FirstRoll] runs on this component's timer [TIMER_STEP]. */
    private fun subTimers() = object : au.com.josephmark.dithertracker.play.Scheduler {
        override fun schedule(id: Int, delayMillis: Long) = timers.schedule(TIMER_STEP, delayMillis)
        override fun cancel(id: Int) = timers.cancel(TIMER_STEP)
    }

    override fun onAdded() {
        cover = stage.addCover(Cover(HIDES))
    }

    /** [MenuDestination]: FIRST starts the roll-off. */
    override fun open(item: MenuItem, facing: Int) {
        if (item == MenuItem.FIRST) pick()
    }

    /** Starts the roll-off among the live seats (or, paused, settles it at once). */
    fun pick() {
        val state = stage.session.state
        val l = layout ?: return
        if (state.players.isEmpty()) return
        clear()
        roll.cancel()
        var live = 0
        for ((i, p) in state.players.withIndex()) if (i < l.play.playerCount && !state.isKo(p)) live = live or (1 shl i)
        if (live == 0) for (i in 0 until minOf(state.players.size, l.play.playerCount)) live = live or (1 shl i)
        val p = flavour.startingPlayer(random, recent)
        recent = p.recent
        caption = p.value.uppercase()
        roll.start(live)
        stage.setCover(cover, live, true)
        if (paused) roll.finish()
    }

    private fun changed(done: Boolean) {
        val l = layout ?: return
        invalidateSeats(l, roll.seats)
        if (roll.phase == FirstRoll.LANDED && roll.winner >= 0) l.arrange(roll.winner, caption)
        if (done) land(l)
    }

    /** The roll-off is over: the table comes back, turn tracking starts from the winner, the sting plays. */
    private fun land(l: FirstPlayerLayout) {
        val s = roll.winner
        val players = stage.session.state.players
        roll.reset()
        stage.setCover(cover, 0, false)
        if (s < 0 || s >= players.size) return
        val id = players[s].id
        results.onStartingPlayer(id)
        stage.dispatch(Origin.EXTERNAL, Action.SetStartingPlayer(id))
        stage.send(Events.FIRST_PLAYER, s) // T55: `start-flag` on their tile — the winner's sting
        seat = s
        l.arrange(s, caption)
        invalidate(l.bandRect(s, band))
        if (!paused) timers.schedule(TIMER_CLEAR, showMillis)
    }

    /** Takes the FIRST flag down (one redraw of its band). */
    fun clear() {
        timers.cancel(TIMER_CLEAR)
        if (seat < 0) return
        layout?.let { invalidate(it.bandRect(seat, band)) }
        seat = -1
    }

    private fun invalidateSeats(l: FirstPlayerLayout, mask: Int) {
        for (i in 0 until l.play.playerCount) if ((mask ushr i) and 1 == 1) invalidate(l.play.panels[i])
    }

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val l = FirstPlayerLayout(play)
        layout = l
        val t = this.tiles
        this.tiles = if (t != null && t.play === play) t else TileRollLayout(play, t?.sprites?.takeIf { it.k == TileRollLayout.spriteK(play) })
        screen.set(0, 0, play.grid.width, play.grid.height)
        if (roll.active) roll.finish()
        if (seat >= play.playerCount) seat = -1
        if (seat >= 0) l.arrange(seat, caption)
    }

    override fun prewarm(f: Frame) {
        val l = layout ?: return
        for (rot in l.play.rotations) for (kk in 1..l.kMax) {
            for (line in flavour.lines(QuipKind.STARTING_PLAYER)) f.text.draw(l.ui, line.uppercase(), 0, 0, kk, rot, invert = true)
        }
        for (rot in l.play.rotations) for (kk in 1..l.askKMax) f.text.draw(l.ui, TIE, 0, 0, kk, rot, invert = true)
        for (rot in l.play.rotations) f.text.draw(l.ui, D20, 0, 0, l.play.tk, rot)
        tiles?.prewarm(f.r, l.play.rotations)
    }

    // ---- drawing ---------------------------------------------------------------------------------

    override fun draw(f: Frame, seat: Int) {
        val l = layout ?: return
        val t = tiles ?: return
        if (roll.active) {
            for (s in 0 until l.play.playerCount) if ((roll.seats ushr s) and 1 == 1) takeOver(f, l, t, s)
        }
        val s = this.seat
        if (s >= 0 && !roll.active) flag(f, l, s)
    }

    /** One rolling seat's tile: ground and frame, "D20", then the tumbling die or the roll, and its flag. */
    private fun takeOver(f: Frame, l: FirstPlayerLayout, t: TileRollLayout, s: Int) {
        val panel = l.play.panels[s]
        if (!f.hit(panel)) return
        val r = f.r
        val rot = l.play.rotation[s]
        r.rect(panel, ink = false)
        val a = t.frameOuter[s]
        r.frame(a, 1, ink = true)
        val b = t.frameInner[s]
        r.frame(b, 1, ink = true)
        val lab = t.label[s]
        f.text.draw(l.ui, D20, lab.left, lab.top, l.play.tk, rot)
        if (roll.phase == FirstRoll.ROLLING && (roll.rolling ushr s) and 1 == 1) {
            t.drawPose(r, s, roll.beats == FirstRoll.FAST_POSES, roll.beat, rot)
            return
        }
        val v = roll.value[s]
        if (v > 0) {
            val k = l.play.numberFootprint(s, v, tmp)
            f.text.drawInt(l.play.font, v, tmp.left, tmp.top, k, rot)
        }
        if (roll.phase == FirstRoll.TIE && (roll.tied ushr s) and 1 == 1) tieFlag(f, l, s)
        else if (roll.phase == FirstRoll.LANDED && roll.winner == s) flag(f, l, s)
    }

    /** The FIRST flag: an ink pennant (notched ends) with the line in paper caps, facing the seat. */
    private fun flag(f: Frame, l: FirstPlayerLayout, s: Int) {
        if (!l.enabled) return
        l.bandRect(s, band)
        if (!f.hit(band)) return
        val r = f.r
        r.rect(band, ink = true)
        notch(r, band, l.notch)
        val rot = l.play.rotation[s]
        for (i in 0 until l.lines) {
            val lr = l.line[i]
            f.text.draw(l.ui, slice.set(caption, l.start[i], l.end[i]), lr.left, lr.top, l.k, rot, invert = true)
        }
    }

    /** A tied seat: the band outlined in dots with "TIE!" — they roll again. */
    private fun tieFlag(f: Frame, l: FirstPlayerLayout, s: Int) {
        if (!l.enabled) return
        l.bandRect(s, band)
        val r = f.r
        r.rect(band, ink = false)
        r.dottedH(band.left, band.top, band.width)
        r.dottedH(band.left, band.bottom - 1, band.width)
        r.dottedV(band.left, band.top, band.height)
        r.dottedV(band.right - 1, band.top, band.height)
        val k = l.shortFootprint(s, TIE.length, tmp)
        if (k > 0) f.text.draw(l.ui, TIE, tmp.left, tmp.top, k, l.play.rotation[s])
    }

    /** Cuts a V notch [depth] deep into both short ends of [b] (paper), so the band reads as a pennant. */
    private fun notch(r: au.com.josephmark.dithertracker.dither.DitherRenderer, b: IRect, depth: Int) {
        if (depth <= 0) return
        if (b.width >= b.height) {
            val half = b.height / 2
            for (y in 0 until b.height) {
                val d = depth - depth * kotlin.math.abs(y - half) / maxOf(1, half)
                if (d <= 0) continue
                r.rect(b.left, b.top + y, d, 1, ink = false)
                r.rect(b.right - d, b.top + y, d, 1, ink = false)
            }
        } else {
            val half = b.width / 2
            for (x in 0 until b.width) {
                val d = depth - depth * kotlin.math.abs(x - half) / maxOf(1, half)
                if (d <= 0) continue
                r.rect(b.left + x, b.top, 1, d, ink = false)
                r.rect(b.left + x, b.bottom - d, 1, d, ink = false)
            }
        }
    }

    override fun bounds(out: IRect): Boolean {
        val l = layout ?: return false
        if (seat < 0) return false
        l.bandRect(seat, out)
        return true
    }

    // ---- touch -----------------------------------------------------------------------------------

    override fun zones(seat: Int, z: ZoneSink) {
        if (roll.active) z.add(ZONE_SKIP, screen)
    }

    override fun onTap(seat: Int, local: Int) {
        if (local == ZONE_SKIP) roll.skip()
    }

    // ---- notifications ---------------------------------------------------------------------------

    override fun onEvent(event: Int, seat: Int) {
        when (event) {
            // Any touch on the table (a tile's zone) or the centre diamond takes the flag down.
            Events.TOUCH_DOWN, Events.MENU_OPEN -> if (!roll.active) clear()
            Events.PAUSE -> {
                paused = true
                roll.finish()
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
            TIMER_STEP -> roll.onTimer(FirstRoll.TIMER_STEP)
        }
    }

    override fun onPlayersChanged() {
        if (roll.active) {
            roll.cancel()
            stage.setCover(cover, 0, false)
        }
        clear()
    }

    override fun onDetach() {
        roll.finish()
        clear()
    }

    companion object {
        const val TIMER_CLEAR = 0
        const val TIMER_STEP = 1
        const val TIMERS = 2

        const val ZONE_SKIP = 0

        /** How long the FIRST flag stays on the winner's tile after the tiles come back. */
        const val SHOW_MS = 4_000L

        const val D20 = "D20"
        const val TIE = "TIE!"

        /** What the roll-off hides on the rolling seats (and the diamond on the screen). */
        const val HIDES = Kind.CONTENT or Kind.CHROME or Kind.READOUT or Kind.EFFECT or Kind.STATUS
    }
}
