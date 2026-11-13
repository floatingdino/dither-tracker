package au.com.josephmark.dithertracker.play

/**
 * The pending-delta readout state, per seat. Pure Kotlin, allocation-free.
 *
 * A seat's *group* opens on its first life change and accumulates every applied change ([add] takes
 * the actual change, after core clamping). Each change re-arms the seat's idle timer for
 * [windowMillis] (the core `History` coalesce window — one source of truth for "a burst of taps").
 * When the timer fires, or [commitAll] is called because some other kind of action happened, the
 * group commits: the readout disappears and [Sink.onGroupCommitted] lets the owner close the core
 * undo group.
 *
 * Seats are fully independent — four players tapping at once each see their own running delta, and
 * each seat's commit closes only that seat's core undo group (core groups per key since T29).
 *
 * The readout shows [delta] when it is non-zero; a group that nets to zero shows nothing (but stays
 * open, so a following tap continues it). [Sink.onReadoutChanged] fires exactly when the text shown
 * for a seat changes — that is the only invalidation the readout needs.
 */
class PendingDeltas(
    seats: Int,
    private val scheduler: Scheduler,
    private val sink: Sink,
    var windowMillis: Long,
) : Scheduler.Target {

    interface Sink {
        /** The readout text of [seat] changed (appeared, updated or cleared): redraw its slot. */
        fun onReadoutChanged(seat: Int)

        /** [seat]'s group committed (idle timeout or another action). */
        fun onGroupCommitted(seat: Int)
    }

    private val delta = IntArray(seats)
    private val open = BooleanArray(seats)

    /** Running delta of [seat]'s open group (0 when none). */
    fun delta(seat: Int): Int = if (seat in delta.indices) delta[seat] else 0

    fun isOpen(seat: Int): Boolean = seat in open.indices && open[seat]

    /** True while the readout for [seat] has text. */
    fun isShown(seat: Int): Boolean = delta(seat) != 0

    /** [seat]'s life changed by [change] (the applied amount). Zero is ignored (a clamped no-op). */
    fun add(seat: Int, change: Int) {
        if (change == 0 || seat !in delta.indices) return
        open[seat] = true
        delta[seat] += change
        scheduler.schedule(seat, windowMillis)
        sink.onReadoutChanged(seat)
    }

    override fun onTimer(id: Int) = commit(id)

    /** Commits [seat]'s group now (no-op when none is open). */
    fun commit(seat: Int) {
        if (!isOpen(seat)) return
        scheduler.cancel(seat)
        open[seat] = false
        val wasShown = delta[seat] != 0
        delta[seat] = 0
        sink.onGroupCommitted(seat)
        if (wasShown) sink.onReadoutChanged(seat)
    }

    /** Another kind of action happened: commit every open group. */
    fun commitAll() {
        for (s in delta.indices) commit(s)
    }
}
