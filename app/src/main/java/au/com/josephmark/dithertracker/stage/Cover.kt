package au.com.josephmark.dithertracker.stage

/**
 * The mode/visibility mechanism: a cover hides components of the [Kind]s in [hides] on the seats in
 * [seats] (bit `1 shl seat`) and, when [screen] is set, on the screen layers. Hidden components are
 * neither drawn nor hit-tested there, so whatever owns the cover takes over input and display for
 * that region.
 *
 * Register covers once with [Stage.addCover]; turn one on, change it or turn it off with
 * [Stage.setCover]. Every change cancels every touch, calls [Component.onCoversChanged] on all
 * components (the readout commits, the drawer closes what is now hidden) and redraws the affected
 * panels and screen components — one frame.
 *
 * Commander-damage mode is a cover: `Cover(Kind.CONTENT or Kind.CHROME)` on every seat and the screen
 * hides life, peeks, drawers, tabs and the menu diamond; its own components ([Kind.MODE]) and the
 * readout ([Kind.READOUT]) stay. A full-screen overlay (T09 menu) would use
 * `Cover(Kind.ALL_BELOW_OVERLAY)` on every seat and the screen, or cover just the seats it sits on.
 */
class Cover(val hides: Int) {
    /** Covered seats, bit `1 shl seat`. */
    var seats: Int = 0
        internal set

    /** Whether the screen layers ([Layer.BACKDROP], [Layer.SCREEN], [Layer.OVERLAY]) are covered. */
    var screen: Boolean = false
        internal set

    val active: Boolean get() = seats != 0 || screen

    /** Whether this cover covers [seat] ([Stage.SCREEN] = the screen layers). */
    fun covers(seat: Int): Boolean = if (seat < 0) screen else (seats ushr seat) and 1 != 0

    companion object {
        /** Every seat. */
        const val ALL_SEATS = -1
    }
}

/**
 * The pending-delta readout as a service (provided by the readout component, used by features that
 * attribute their own changes, e.g. commander damage shows on the source seat's readout). All seats
 * are independent; see `readout.PendingDeltas`.
 */
interface Readouts {
    /** Running delta shown on [seat] (0 = nothing shown). */
    fun delta(seat: Int): Int

    /** Adds an applied [change] to [seat]'s open group (opens one; 0 is ignored). */
    fun add(seat: Int, change: Int)

    /** The core coalesce key [seat]'s open group closes on commit; null = that seat's life group. */
    fun group(seat: Int): String?

    fun setGroup(seat: Int, key: String?)

    /** Commits [seat]'s group now (readout clears, its core undo group closes). */
    fun commit(seat: Int)

    fun commitAll()

    companion object {
        /** No readout component on this stage. */
        val NONE: Readouts = object : Readouts {
            override fun delta(seat: Int) = 0
            override fun add(seat: Int, change: Int) {}
            override fun group(seat: Int): String? = null
            override fun setGroup(seat: Int, key: String?) {}
            override fun commit(seat: Int) {}
            override fun commitAll() {}
        }
    }
}
