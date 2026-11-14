package au.com.josephmark.dithertracker.turns

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.TurnState
import au.com.josephmark.dithertracker.core.serial.AppSettings
import au.com.josephmark.dithertracker.core.serial.TurnTimer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Origin
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.ZoneSink

/**
 * The turn tracker (T17): whose turn it is, passing it, and the optional minutes.
 *
 * - **Starts** when a starting player is picked (the menu's FIRST tool dispatches
 *   `Action.SetStartingPlayer`); nothing shows before that, nor with the "Turns" setting OFF
 *   (`AppSettings.turnTracker`), nor once one player is left standing (the game is over).
 * - **Shows** as the *turn tab* on the active seat only: a solid ink tab the drawer tab's size on the
 *   tile's **far** edge — the side facing the middle of the table, where everyone looks — with the
 *   `turn` icon (▶|) in paper ([TurnLayout]). The other seats show nothing, so tables that ignore it
 *   see one small tab. Calm: no animation, no fill change on the tile, the numerals untouched.
 * - **Passes** with a tap on the tab (release-to-fire like every zone; a press inverts it):
 *   `Action.NextTurn`, which skips knocked-out seats and counts the turn. One redraw: the old tab's
 *   rect and the new one's, nothing else. Undo (the gem) takes a pass back.
 * - **Corrects** by holding the tab: after [CORRECT_AFTER] hold steps (1.5 s), and every second while
 *   held, the turn moves on one live seat **without counting** (`Action.SetActivePlayer`) — for a mark
 *   on the wrong seat. Letting go between 0.5 and 1.5 s does nothing (a held tap is never a pass).
 * - **Timer** (`AppSettings.turnTimer`, off by default): the minutes since tracking started (GAME) or
 *   since this turn began (TURN), beside the icon, from the stamps core keeps in `TurnState` (wall
 *   clock, persisted, so a restart or process death resumes the count). ONE timer, armed only while
 *   the timer is on, a tab is showing and the app is in the foreground ([Events.RESUME] /
 *   [Events.PAUSE]); it fires once at each minute boundary and redraws only the digits (the tab when
 *   the digit count changes). Re-synced from the wall clock on resume, attach, every state, settings
 *   and cover change. Minutes only, capped at [TurnLayout.MAX_MINUTES].
 *
 * [Layer.TILE_CHROME] (over the life halves' press inversion, winning their touches) and [Kind.STATUS]
 * so a knocked-out active seat keeps its tab (the KO tile's cover hides `CHROME`; someone still has to
 * pass): on the dead field, at the near edge where the hidden drawer tab was ([TurnLayout.NEAR]),
 * clear of the tombstone. Hidden by full overlays (they cover `STATUS`), in commander mode
 * (any cover over the screen's chrome) and while the active seat's drawer is open ([TileSlots.compact]).
 */
class TurnTab : Component(Layer.TILE_CHROME, Kind.STATUS, zonesPerSeat = 1, timerCount = TIMERS) {

    var layout: TurnLayout? = null
        private set
    private var t: TileSlots? = null

    /** Seat showing the tab now, or −1 ([sync]). */
    var shownSeat = -1
        private set

    /** Where it shows: [TurnLayout.FAR], or [TurnLayout.NEAR] on a seat the KO tile covers ([sync]). */
    var place = TurnLayout.FAR
        private set

    /** Minutes on the tab ([sync]), or −1 when the timer shows nothing. */
    var minutes = -1
        private set

    /** Whether the tick is armed (tests). */
    var ticking = false
        private set

    private var paused = false
    private val icon = IRect()
    private val num = IRect()

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        val l = TurnLayout(play)
        layout = l
        t = tiles
        stage.log?.invoke("$l")
        sync(invalidate = false)
    }

    override fun prewarm(f: Frame) {
        val l = layout ?: return
        f.icons.prewarm(ICONS, l.tk)
        for (rot in l.play.rotations) f.text.draw(l.font, DIGITS, 0, 0, l.tk, rot, invert = true)
    }

    // ---- what shows ---------------------------------------------------------------------------------

    /** The seat whose tab shows under [s] and the current settings and covers, or −1. */
    private fun seatFor(s: GameState): Int {
        val l = layout ?: return -1
        if (!l.enabled || !stage.settings.current.turnTracker || !s.turn.started) return -1
        val id = s.turn.activePlayer ?: return -1
        val seat = seatOf(s, id)
        if (seat < 0 || seat >= l.play.playerCount) return -1
        if (gameOver(s)) return -1
        if (!visible(seat) || stage.covered(Kind.CHROME, Stage.SCREEN)) return -1
        return seat
    }

    /** Seat of player [id], or −1 (an index loop: `GameState.indexOf` walks an iterator, 32 bytes a call). */
    private fun seatOf(s: GameState, id: Int): Int {
        val ps = s.players
        for (i in ps.indices) if (ps[i].id == id) return i
        return -1
    }

    /** One player (or none) left standing; cached per player list (a minute tick asks again, core's `isKo` allocates). */
    private fun gameOver(s: GameState): Boolean {
        if (s.players === overFor && s.settings === overRules) return over
        var alive = 0
        val ps = s.players
        for (i in ps.indices) if (!s.isKo(ps[i])) alive++
        overFor = ps
        overRules = s.settings
        over = alive <= 1
        return over
    }

    private var overFor: List<*>? = null
    private var overRules: Any? = null
    private var over = false

    /** Minutes for [s]'s clock under the settings at [now], or −1 when the timer shows none. */
    private fun minutesFor(s: GameState, now: Long): Int {
        val start = when (stage.settings.current.turnTimer) {
            TurnTimer.OFF -> return -1
            TurnTimer.GAME -> s.turn.startedAt
            TurnTimer.TURN -> s.turn.turnStartedAt
        }
        if (start == TurnState.NO_TIME) return -1 // a save from before the clock: nothing to count from
        val m = (now - start) / MINUTE
        return if (m < 0) 0 else if (m > TurnLayout.MAX_MINUTES) TurnLayout.MAX_MINUTES else m.toInt()
    }

    private fun digits(m: Int): Int = if (m < 0) 0 else if (m < 10) 1 else if (m < 100) 2 else 3

    /**
     * Re-reads what shows (seat, minutes) from the state, settings, covers and the wall clock;
     * redraws what changed ([invalidate]) and arms or drops the one tick.
     */
    private fun sync(invalidate: Boolean = true) {
        val l = layout ?: return
        val s = stage.session.state
        val seat = seatFor(s)
        val now = stage.now()
        val m = if (seat >= 0) minutesFor(s, now) else -1
        // A seat whose chrome a cover hides while the screen's is not (the KO tile): the near edge.
        val pl = if (seat >= 0 && stage.covered(Kind.CHROME, seat)) TurnLayout.NEAR else TurnLayout.FAR
        if (invalidate) {
            if (seat != shownSeat || pl != place) {
                if (shownSeat in 0 until l.play.playerCount) invalidate(l.tabAt(place, shownSeat))
                if (seat >= 0) invalidate(l.tabAt(pl, seat))
            } else if (seat >= 0 && m != minutes) {
                if (digits(m) == digits(minutes) && l.content(seat, digits(m), icon, num, pl) and TurnLayout.NUM != 0) invalidate(num)
                else invalidate(l.tabAt(pl, seat))
            }
        }
        shownSeat = seat
        place = pl
        minutes = m
        arm(s, now)
    }

    /**
     * The one timer: armed for the next minute boundary while the minutes show and can still change;
     * left alone when it is already armed for that boundary (a life tap does not re-arm it).
     */
    private fun arm(s: GameState, now: Long) {
        val want = !paused && shownSeat >= 0 && minutes in 0 until TurnLayout.MAX_MINUTES &&
            layout?.timerFits(shownSeat, place) == true
        if (!want) {
            if (ticking) timers.cancel(TICK)
            ticking = false
            return
        }
        val start = if (stage.settings.current.turnTimer == TurnTimer.GAME) s.turn.startedAt else s.turn.turnStartedAt
        // A start in the future (the clock was set back) shows 0 until a minute after it.
        val boundary = start + ((now - start).coerceAtLeast(0) / MINUTE + 1) * MINUTE
        if (ticking && boundary == armedFor) return
        timers.schedule(TICK, boundary - now)
        armedFor = boundary
        ticking = true
    }

    /** Wall-clock ms the armed tick is for. */
    private var armedFor = 0L

    // ---- drawing ------------------------------------------------------------------------------------

    /** Whether [seat]'s tab is on screen now (the active seat's drawer, when open, takes the far strip). */
    private fun showing(seat: Int): Boolean = seat == shownSeat && (place == TurnLayout.NEAR || t?.compact(seat) != true)

    override fun draw(f: Frame, seat: Int) {
        if (!showing(seat)) return
        val l = layout ?: return
        val tab = l.tabAt(place, seat)
        if (!f.hit(tab)) return
        f.r.rect(tab, ink = true)
        val m = minutes
        val bits = l.content(seat, digits(m), icon, num, place)
        val rot = l.play.rotation[seat]
        if (bits and TurnLayout.ICON != 0) f.icons.icon(l.tk, Icon.TURN, icon, rot, invert = true, opaque = false)
        if (bits and TurnLayout.NUM != 0) f.text.drawInt(l.font, m, num.left, num.top, l.tk, rot, invert = true)
    }

    // ---- touch --------------------------------------------------------------------------------------

    override fun zones(seat: Int, z: ZoneSink) {
        val l = layout ?: return
        if (showing(seat)) z.add(PASS, l.hitAt(place, seat))
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val l = layout ?: return false
        if (!showing(seat)) return false // a hold moved the turn away: nothing left to invert here
        val r = l.tabAt(place, seat)
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    override fun repeats(seat: Int, local: Int): Boolean = true

    /** Tap: pass the turn (counts it, skips knocked-out seats). */
    override fun onTap(seat: Int, local: Int) {
        if (seat != shownSeat) return
        stage.dispatch(Origin.EXTERNAL, Action.NextTurn)
        stage.send(Events.TURN_PASSED, Stage.SCREEN) // T55: `your-turn` on the new active seat
    }

    /** Hold step: from the [CORRECT_AFTER]th on, move the turn to the next live seat without counting it. */
    override fun onRepeat(seat: Int, local: Int) {
        if (stage.input.repeatCount(stage.zoneId(this, seat, local)) < CORRECT_AFTER) return
        val s = stage.session.state
        val id = s.turn.activePlayer ?: return
        val from = seatOf(s, id)
        if (from < 0) return
        val ps = s.players
        for (step in 1 until ps.size) {
            val p = ps[(from + step) % ps.size]
            if (!s.isKo(p)) {
                stage.dispatch(Origin.EXTERNAL, Action.SetActivePlayer(p.id))
                return
            }
        }
    }

    // ---- notifications ------------------------------------------------------------------------------

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        // The turn, or a player (a KO can end the game); [arm] keeps an already armed tick.
        if (old.turn !== new.turn || old.players !== new.players) sync()
    }

    override fun onPlayersChanged() = sync(invalidate = false) // the stage redraws everything

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        if (old.turnTracker == new.turnTracker && old.turnTimer == new.turnTimer) return
        val was = shownSeat
        sync()
        // Same seat, timer switched: the content changed shape (icon alone ↔ icon + minutes).
        val l = layout
        if (l != null && was >= 0 && was == shownSeat) invalidate(l.tabAt(place, was))
    }

    override fun onCoversChanged() = sync()

    override fun onEvent(event: Int, seat: Int) {
        when (event) {
            Events.PAUSE -> {
                paused = true
                if (ticking) timers.cancel(TICK)
                ticking = false
            }
            Events.RESUME -> {
                paused = false
                sync()
            }
        }
    }

    override fun onTimer(id: Int) {
        ticking = false
        if (id == TICK) sync()
    }

    override fun onAttach() = sync()

    override fun onDetach() {
        ticking = false // the stage cancels every timer after this
    }

    companion object {
        const val PASS = 0
        const val TICK = 0
        const val TIMERS = 1
        const val MINUTE = 60_000L

        /** Hold steps (500 ms, then one a second) before a hold starts correcting: 1.5 s. */
        const val CORRECT_AFTER = 2

        private val ICONS = arrayOf(Icon.TURN)
        private const val DIGITS = "0123456789"
    }
}
