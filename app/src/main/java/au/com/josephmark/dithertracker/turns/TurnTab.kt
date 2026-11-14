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
import au.com.josephmark.dithertracker.widgets.CharBuf

/**
 * The turn tracker (T17; T66 reports): whose turn it is, passing it, and — with a timer mode on — a
 * short report of the time after each pass. Nothing about it ever ticks.
 *
 * - **Starts** when a starting player is picked (`Action.SetStartingPlayer`); nothing shows before
 *   that, nor with the "Turns" setting OFF (`AppSettings.turnTracker`), nor once one player is left.
 * - **Shows** as the *turn tab* on the active seat only: a solid ink tab the drawer tab's size on the
 *   tile's **far** edge (the side facing the middle of the table) with the `turn` icon (▶|) in paper
 *   ([TurnLayout]). Calm: no animation, the numerals untouched.
 * - **Passes** with a tap on the tab: `Action.NextTurn` (skips knocked-out seats, counts the turn).
 *   One redraw: the old tab's rect and the new one's. Undo (the gem) takes a pass back.
 * - **Corrects** by holding the tab: from [CORRECT_AFTER] hold steps (1.5 s), and every second while
 *   held, the turn moves on one live seat **without counting** (`Action.SetActivePlayer`).
 * - **Reports** (T66, `AppSettings.turnTimer` GAME / TURN): right after a pass the new tab shows, in
 *   place of its icon, how long the turn that just ended took (TURN: `TurnState.lastTurnMillis`) or
 *   the game's time so far (GAME: `gameMillisAtLastPass`), as `m:ss` / `h:mm:ss` ([TimeFormat]),
 *   active time only (core's active clock), for [REPORT_MS]; then the icon returns. The value is
 *   frozen at the pass. It sits on the only tab on screen, but it appears **at the moment the
 *   previous player hands over** and goes again: it reads as "that turn took 2:07", not as a clock
 *   for the new player (nothing counts on it). One one-shot timer ([CLEAR]) armed only by a pass,
 *   replaced by the next pass, cancelled on pause (the report is simply cleared: on resume the tab
 *   shows its icon), on a settings change and on undo (an undone pass never re-shows an old report:
 *   the state is restored, the tab shows its icon). The clear redraws only the tab. A tap during
 *   the report passes as ever (and shows the next report).
 *
 * [Layer.TILE_CHROME] and [Kind.STATUS] so a knocked-out active seat keeps its tab: on the dead field,
 * at the near edge where the hidden drawer tab was ([TurnLayout.NEAR]). Hidden by full overlays, in
 * commander mode and while the active seat's drawer is open ([TileSlots.compact]).
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

    /** The report on the tab now ([REPORT_MS] after a pass), or empty. */
    val report = CharBuf(TimeFormat.MAX_CHARS)

    /** Whether the report's clear is armed (tests). */
    var reportArmed = false
        private set

    private val icon = IRect()
    private val text = IRect()

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
        for (rot in l.play.rotations) {
            f.text.draw(l.font, WARM, 0, 0, l.tk, rot, invert = true)
            f.text.draw(l.small, WARM, 0, 0, l.tk, rot, invert = true)
        }
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

    /** Seat of player [id], or −1 (an index loop: `GameState.indexOf` walks an iterator). */
    private fun seatOf(s: GameState, id: Int): Int {
        val ps = s.players
        for (i in ps.indices) if (ps[i].id == id) return i
        return -1
    }

    /** One player (or none) left standing; cached per player list (core's `isKo` allocates). */
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

    /** The report a pass to [s] makes under the settings, in ms, or [TurnState.NO_TIME] for none. */
    private fun reportFor(s: GameState): Long = when (stage.settings.current.turnTimer) {
        TurnTimer.OFF -> TurnState.NO_TIME
        TurnTimer.TURN -> s.turn.lastTurnMillis
        TurnTimer.GAME -> s.turn.gameMillisAtLastPass
    }

    /** Re-reads what shows (seat, placement) from the state, settings and covers; redraws what changed. */
    private fun sync(invalidate: Boolean = true) {
        val l = layout ?: return
        val s = stage.session.state
        val seat = seatFor(s)
        // A seat whose chrome a cover hides while the screen's is not (the KO tile): the near edge.
        val pl = if (seat >= 0 && stage.covered(Kind.CHROME, seat)) TurnLayout.NEAR else TurnLayout.FAR
        if (invalidate && (seat != shownSeat || pl != place)) {
            if (shownSeat in 0 until l.play.playerCount) invalidate(l.tabAt(place, shownSeat))
            if (seat >= 0) invalidate(l.tabAt(pl, seat))
        }
        shownSeat = seat
        place = pl
        if (seat < 0) clearReport(invalidate = false)
    }

    /** Takes the report down ([redraw]: the tab, if it shows). */
    private fun clearReport(invalidate: Boolean = true) {
        if (reportArmed) timers.cancel(CLEAR)
        reportArmed = false
        if (report.length == 0) return
        report.clear()
        val l = layout ?: return
        if (invalidate && shownSeat in 0 until l.play.playerCount) invalidate(l.tabAt(place, shownSeat))
    }

    // ---- drawing ------------------------------------------------------------------------------------

    /** Whether [seat]'s tab is on screen now (the active seat's drawer, when open, takes the far strip). */
    private fun showing(seat: Int): Boolean = seat == shownSeat && (place == TurnLayout.NEAR || t?.compact(seat) != true)

    override fun draw(f: Frame, seat: Int) {
        if (!showing(seat)) return
        val l = layout ?: return
        val tab = l.tabAt(place, seat)
        if (!f.hit(tab)) return
        f.r.rect(tab, ink = true)
        val rot = l.play.rotation[seat]
        val n = report.length
        if (n > 0) {
            val font = l.reportFont(seat, place, n)
            l.report(seat, place, n, font, text)
            f.text.draw(font, report, text.left, text.top, l.tk, rot, invert = true)
        } else {
            f.icons.icon(l.tk, Icon.TURN, l.icon(seat, place, icon), rot, invert = true, opaque = false)
        }
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
        if (old.turn === new.turn && old.players === new.players) return
        val o = old.turn
        val n = new.turn
        sync()
        when {
            // A pass (the same tracking, one more turn): the report for it, for REPORT_MS.
            n.started && o.started && n.number == o.number + 1 && n.startedAt == o.startedAt -> showReport(new)
            // Undo of a pass, a restart, a new game: never an old report.
            n.number != o.number || n.startedAt != o.startedAt -> clearReport()
        }
    }

    private fun showReport(s: GameState) {
        val ms = reportFor(s)
        val l = layout
        if (ms == TurnState.NO_TIME || shownSeat < 0 || l == null) {
            clearReport()
            return
        }
        TimeFormat.format(ms, report)
        invalidate(l.tabAt(place, shownSeat)) // (already in this pass's redraw: the new tab)
        timers.schedule(CLEAR, REPORT_MS)
        reportArmed = true
    }

    override fun onPlayersChanged() {
        clearReport(invalidate = false)
        sync(invalidate = false) // the stage redraws everything
    }

    override fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        if (old.turnTracker == new.turnTracker && old.turnTimer == new.turnTimer) return
        clearReport()
        sync()
    }

    override fun onCoversChanged() = sync()

    override fun onEvent(event: Int, seat: Int) {
        // Paused: the report is simply dropped (no remainder on resume; the tab shows its icon).
        if (event == Events.PAUSE) clearReport()
    }

    override fun onTimer(id: Int) {
        if (id != CLEAR) return
        reportArmed = false
        clearReport()
    }

    override fun onAttach() = sync()

    override fun onDetach() {
        reportArmed = false // the stage cancels every timer after this
        report.clear()
    }

    companion object {
        const val PASS = 0

        /** The one timer: takes a pass's report down. */
        const val CLEAR = 0
        const val TIMERS = 1

        /** How long a pass's report shows on the new tab (Sam, T66: "can disappear after 5 seconds"). */
        const val REPORT_MS = 5_000L

        /** Hold steps (500 ms, then one a second) before a hold starts correcting: 1.5 s. */
        const val CORRECT_AFTER = 2

        private val ICONS = arrayOf(Icon.TURN)
        private const val WARM = "0123456789:"
    }
}
