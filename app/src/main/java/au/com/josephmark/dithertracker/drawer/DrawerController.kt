package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.core.coalesceKey
import au.com.josephmark.dithertracker.dither.IRect

/**
 * Drawer behaviour (T08): turns drawer-zone taps/repeats into [DrawerState] transitions and core
 * [Action]s, and works out the smallest set of rects to invalidate. Pure Kotlin (no Android); the
 * Play view is the [Host].
 *
 * Invalidation per interaction (each is one frame — the caller invalidates within one event):
 * - open/close: the whole panel (number block moves, drawer appears/disappears);
 * - page / view switch: the drawer (bar chevrons + body);
 * - a value change: only the cells whose value changed (see [onStateChanged]); the life total's
 *   slot is the view's own diff.
 *
 * Cells (T43): a tap on − / + steps the value (tax by 2); − does nothing at 0. Holding + on a
 * counter repeats +[DrawerItems.HOLD_STEP]; holding − (on a counter or a tax cell) resets it to 0 once
 * at the hold delay — one action dispatched without a coalesce key, so it is its own undo step and
 * undoing it restores the value — after which the hold stops repeating (the value is 0).
 *
 * Toggle cells (T14, [DrawerItems.isToggle]): a tap on either half flips the toggle (or steps
 * day/night) as one undo step of its own; no hold repeat, except a held day/night cell resets it to
 * unset once. A toggle change invalidates only the cells it changed — an exclusive toggle moving
 * between seats redraws the old holder's open cell too, and day/night every open drawer's cell.
 */
class DrawerController(val state: DrawerState, private val host: Host) {

    interface Host {
        /** The current game state. */
        val game: GameState

        /** Counters shown in the tray, in order (`AppSettings.drawerCounters`). */
        val drawerCounters: List<Counter>

        /** Trackers hidden from the tray (`AppSettings.hiddenTrackerMask`, T53). */
        val hiddenTrackers: Int get() = 0

        /**
         * Applies [action] on behalf of [seat]'s drawer (the host attributes life changes to that
         * seat's readout), folding into [key]'s open undo step (null: a step of its own).
         */
        fun dispatch(seat: Int, action: Action, key: String?)

        /** Redraw this dither rect. */
        fun invalidate(rect: IRect)

        /** Releases, without applying, every touch on [seat]'s drawer locals `[from, until)` ([DrawerZones]; their meaning is about to change). */
        fun cancelTouches(seat: Int, from: Int, until: Int)
    }

    var layout: DrawerLayout? = null
        set(value) {
            field = value
            refreshAll()
        }

    private val S = state.seats
    private val items = IntArray(S * DrawerItems.MAX_ITEMS)
    private val counts = IntArray(S)

    /** Item at position [slot] of [seat]'s current page, or -1. */
    fun itemAt(seat: Int, slot: Int): Int {
        val l = layout ?: return -1
        if (slot < 0 || slot >= state.itemsOnPage(seat)) return -1
        val i = state.page(seat) * l.perPage + slot
        return if (i < counts[seat]) items[seat * DrawerItems.MAX_ITEMS + i] else -1
    }

    /** Number of items in [seat]'s current view (all pages). */
    fun itemCount(seat: Int): Int = counts[seat]

    /** Rebuilds [seat]'s item list for its current view and re-clamps its paging. */
    fun refresh(seat: Int) {
        val l = layout ?: return
        val g = host.game
        if (seat !in g.players.indices) { counts[seat] = 0; state.setPaging(seat, 0, l.perPage); return }
        val off = seat * DrawerItems.MAX_ITEMS
        counts[seat] = when (state.mode(seat)) {
            DrawerState.TRAY -> DrawerItems.tray(g.players[seat], host.drawerCounters, items, off, withToggles = true, hidden = host.hiddenTrackers)
            else -> 0
        }
        state.setPaging(seat, counts[seat], l.perPage)
    }

    fun refreshAll() {
        for (s in 0 until S) refresh(s)
    }

    /**
     * Closes every open drawer (entering commander-damage mode, T30): cancels their touches and
     * invalidates their panels. Returns true if any was open.
     */
    fun closeAll(): Boolean {
        val l = layout
        var any = false
        for (s in 0 until S) {
            if (!state.isOpen(s)) continue
            close(s, l)
            any = true
        }
        return any
    }

    /** Closes [seat]'s drawer if open (cancels its touches, redraws the panel). Returns true if it was open. */
    fun close(seat: Int): Boolean {
        if (!state.isOpen(seat)) return false
        close(seat, layout)
        return true
    }

    private fun close(s: Int, l: DrawerLayout?) {
        host.cancelTouches(s, 0, DrawerZones.CELL_END)
        state.setOpen(s, false)
        if (l != null) host.invalidate(l.play.panels[s])
    }

    /** Whether holding [seat]'s drawer [local] repeats: a counter's +, or any non-zero cell's − (reset). */
    fun repeats(seat: Int, local: Int): Boolean {
        if (!DrawerZones.isCell(local)) return false
        val item = itemAt(seat, DrawerZones.slotOf(local))
        if (item < 0) return false
        if (DrawerItems.isToggle(item)) return DrawerItems.toggleRepeats(item, dayNight)
        return if (DrawerZones.halfOf(local) == DrawerZones.MINUS) valueAt(seat, item) > 0 else DrawerItems.repeats(item)
    }

    /** The table's day/night (the shared cell's value). */
    val dayNight: DayNight get() = host.game.dayNight

    /** Whether [seat]'s cell half [local] does anything now (− is inert at 0): no press feedback otherwise. */
    fun isLive(seat: Int, local: Int): Boolean {
        if (!DrawerZones.isCell(local)) return true
        val item = itemAt(seat, DrawerZones.slotOf(local))
        if (item < 0) return false
        return DrawerItems.isToggle(item) || DrawerZones.halfOf(local) == DrawerZones.PLUS || valueAt(seat, item) > 0
    }

    /** [item]'s current value in [seat]'s drawer (day/night: the table's, see [DrawerItems.value]). */
    fun valueAt(seat: Int, item: Int): Int {
        val g = host.game
        return if (seat in g.players.indices) DrawerItems.value(item, g.players[seat], g.dayNight) else 0
    }

    /** A tap (release) on [seat]'s drawer [local] ([DrawerZones]; not [DrawerZones.BUTTON]). Returns true if anything happened. */
    fun onTap(seat: Int, local: Int): Boolean {
        val l = layout ?: return false
        val s = seat
        when {
            local == DrawerZones.TAB -> {
                host.cancelTouches(s, 0, DrawerZones.CELL_END)
                state.toggle(s)
                if (state.isOpen(s)) refresh(s)
                host.invalidate(l.play.panels[s])
            }
            !state.isOpen(s) -> return false
            local == DrawerZones.PREV || local == DrawerZones.NEXT -> {
                val moved = if (local == DrawerZones.PREV) state.firstPage(s) else state.nextPage(s)
                if (!moved) return false
                pageChanged(s)
            }
            DrawerZones.isCell(local) -> {
                if (!isLive(s, local)) return false
                val item = itemAt(s, DrawerZones.slotOf(local))
                if (DrawerItems.isToggle(item)) {
                    val g = host.game
                    if (s !in g.players.indices) return false
                    val a = DrawerItems.toggleAction(item, g.players[s], g.dayNight) ?: return false
                    host.dispatch(s, a, null)
                    return true
                }
                return apply(s, item, DrawerItems.tapDelta(item, DrawerZones.halfOf(local)))
            }
            else -> return false
        }
        return true
    }

    /** One hold repeat on [seat]'s drawer [local]: + steps by [DrawerItems.HOLD_STEP], − resets to 0. */
    fun onRepeat(seat: Int, local: Int): Boolean {
        if (!repeats(seat, local)) return false
        val item = itemAt(seat, DrawerZones.slotOf(local))
        if (DrawerItems.isToggle(item)) {
            // Only a set day/night repeats: the hold takes it back to unset, once.
            host.dispatch(seat, Action.SetDayNight(DayNight.UNSET), null)
            return true
        }
        if (DrawerZones.halfOf(local) == DrawerZones.MINUS) {
            val a = DrawerItems.resetAction(item, host.game.players[seat]) ?: return false
            host.dispatch(seat, a, null)
            return true
        }
        return apply(seat, item, DrawerItems.holdDelta(DrawerZones.PLUS))
    }

    private fun apply(seat: Int, item: Int, delta: Int): Boolean {
        val g = host.game
        if (seat !in g.players.indices) return false
        val a = DrawerItems.action(item, g.players[seat], delta) ?: return false
        host.dispatch(seat, a, a.coalesceKey)
        return true
    }

    private fun pageChanged(s: Int) {
        val l = layout ?: return
        // Cells change meaning; the bar's chevrons may appear/disappear.
        host.cancelTouches(s, DrawerZones.PREV, DrawerZones.CELL_END)
        host.invalidate(l.drawer[s])
    }

    /**
     * The game changed: rebuild item lists where their shape may have changed (partner, player
     * count) and invalidate exactly the open cells whose shown value changed.
     */
    fun onStateChanged(old: GameState, new: GameState) {
        val l = layout ?: return
        val op = old.players
        val np = new.players
        val structural = op.size != np.size || anyShapeChange(op, np)
        val dayNightChanged = old.dayNight != new.dayNight
        for (s in 0 until minOf(S, np.size)) {
            if (structural) {
                if (state.isOpen(s)) {
                    cancelSeat(s)
                    refresh(s)
                    host.invalidate(l.drawer[s])
                } else refresh(s)
                continue
            }
            if (!state.isOpen(s) || s >= op.size || (op[s] === np[s] && !dayNightChanged)) continue
            val n = state.itemsOnPage(s)
            for (slot in 0 until n) {
                val item = itemAt(s, slot)
                if (item < 0) continue
                val a = DrawerItems.value(item, op[s], old.dayNight)
                val b = DrawerItems.value(item, np[s], new.dayNight)
                if (a != b) host.invalidate(l.cellVis[s * DrawerZones.MAX_SLOTS + slot])
            }
        }
    }

    /** Settings changed (e.g. which counters the tray shows). */
    fun onCountersChanged() {
        val l = layout ?: return
        for (s in 0 until S) {
            if (state.isOpen(s) && state.mode(s) == DrawerState.TRAY) {
                cancelSeat(s)
                refresh(s)
                host.invalidate(l.drawer[s])
            } else refresh(s)
        }
    }

    private fun cancelSeat(s: Int) = host.cancelTouches(s, DrawerZones.PREV, DrawerZones.CELL_END)

    private fun anyShapeChange(op: List<Player>, np: List<Player>): Boolean {
        for (i in np.indices) {
            val a = op[i]
            val b = np[i]
            if (a === b) continue
            if (a.partner != b.partner || a.name != b.name || a.id != b.id) return true
        }
        return false
    }
}
