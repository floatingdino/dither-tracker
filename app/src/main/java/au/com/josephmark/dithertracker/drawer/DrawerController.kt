package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.Player
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
 */
class DrawerController(val state: DrawerState, private val host: Host) {

    interface Host {
        /** The current game state. */
        val game: GameState

        /** Counters shown in the tray, in order (`AppSettings.drawerCounters`). */
        val drawerCounters: List<Counter>

        /** Applies [action] on behalf of [seat]'s drawer (the host attributes life changes to that seat's readout). */
        fun dispatch(seat: Int, action: Action)

        /** Redraw this dither rect. */
        fun invalidate(rect: IRect)

        /** Releases, without applying, every touch on zones `[from, until)` (their meaning is about to change). */
        fun cancelTouches(from: Int, until: Int)
    }

    var layout: DrawerLayout? = null
        set(value) {
            field = value
            refreshAll()
        }

    private val S = DrawerZones.PLAYERS
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
            DrawerState.TRAY -> DrawerItems.tray(g.players[seat], host.drawerCounters, items, off)
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
            host.cancelTouches(DrawerZones.seatFirst(s), DrawerZones.seatEnd(s))
            state.setOpen(s, false)
            if (l != null) host.invalidate(l.play.panels[s])
            any = true
        }
        return any
    }

    /** Whether holding [zone] (a drawer zone) repeats. */
    fun repeats(zone: Int): Boolean {
        if (!DrawerZones.isDrawer(zone)) return false
        val local = DrawerZones.localOf(zone)
        if (!DrawerZones.isCell(local)) return false
        val item = itemAt(DrawerZones.seatOf(zone), DrawerZones.slotOf(local))
        return item >= 0 && DrawerItems.repeats(item)
    }

    /** A tap (release) on drawer [zone]. Returns true if anything happened. */
    fun onTap(zone: Int): Boolean {
        if (!DrawerZones.isDrawer(zone)) return false
        val l = layout ?: return false
        val s = DrawerZones.seatOf(zone)
        val local = DrawerZones.localOf(zone)
        when {
            local == DrawerZones.TAB -> {
                host.cancelTouches(DrawerZones.seatFirst(s), DrawerZones.seatEnd(s))
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
            else -> {
                val item = itemAt(s, DrawerZones.slotOf(local))
                if (item < 0) return false
                return apply(s, item, DrawerItems.tapDelta(item, DrawerZones.halfOf(local)))
            }
        }
        return true
    }

    /** One hold repeat on drawer [zone]. */
    fun onRepeat(zone: Int): Boolean {
        if (!repeats(zone)) return false
        val s = DrawerZones.seatOf(zone)
        val local = DrawerZones.localOf(zone)
        val item = itemAt(s, DrawerZones.slotOf(local))
        return apply(s, item, DrawerItems.holdDelta(DrawerZones.halfOf(local)))
    }

    private fun apply(seat: Int, item: Int, delta: Int): Boolean {
        val g = host.game
        if (seat !in g.players.indices) return false
        val a = DrawerItems.action(item, g.players[seat], delta) ?: return false
        host.dispatch(seat, a)
        return true
    }

    private fun pageChanged(s: Int) {
        val l = layout ?: return
        // Cells change meaning; the bar's chevrons may appear/disappear.
        host.cancelTouches(DrawerZones.zone(s, DrawerZones.PREV), DrawerZones.seatEnd(s))
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
        for (s in 0 until minOf(S, np.size)) {
            if (structural) {
                if (state.isOpen(s)) {
                    cancelSeat(s)
                    refresh(s)
                    host.invalidate(l.drawer[s])
                } else refresh(s)
                continue
            }
            if (!state.isOpen(s) || s >= op.size || op[s] === np[s]) continue
            val n = state.itemsOnPage(s)
            for (slot in 0 until n) {
                val item = itemAt(s, slot)
                if (item < 0) continue
                val a = DrawerItems.value(item, op[s])
                val b = DrawerItems.value(item, np[s])
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

    private fun cancelSeat(s: Int) = host.cancelTouches(DrawerZones.zone(s, DrawerZones.PREV), DrawerZones.seatEnd(s))

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
