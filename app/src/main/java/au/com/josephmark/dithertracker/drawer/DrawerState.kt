package au.com.josephmark.dithertracker.drawer

/**
 * Per-seat drawer UI state (T08): open/closed, which view ([mode]) and which page of each view.
 * Pure Kotlin, allocation-free, not persisted (UI state; the values it shows live in the game).
 *
 * Pages: the controller tells the state how many pages the current view has ([setPaging]) whenever
 * the items might have changed; the page index is clamped then, so it never points past the end.
 * Each mode remembers its own page, and a drawer reopens where it was left.
 */
class DrawerState(val seats: Int) {

    private val open = BooleanArray(seats)
    private val mode = IntArray(seats)
    private val page = IntArray(seats * MODES)
    private val pages = IntArray(seats) { 1 }
    private val onPage = IntArray(seats)
    private val itemCount = IntArray(seats)
    private val perPage = IntArray(seats)

    fun isOpen(seat: Int): Boolean = seat in 0 until seats && open[seat]
    fun mode(seat: Int): Int = mode[seat]
    fun page(seat: Int): Int = page[seat * MODES + mode[seat]]
    fun pageCount(seat: Int): Int = pages[seat]

    /** Number of items shown on the current page (cells beyond it are empty and not touchable). */
    fun itemsOnPage(seat: Int): Int = onPage[seat]

    fun hasPrev(seat: Int): Boolean = page(seat) > 0
    fun hasNext(seat: Int): Boolean = page(seat) < pages[seat] - 1

    fun toggle(seat: Int) { open[seat] = !open[seat] }
    fun setOpen(seat: Int, on: Boolean) { open[seat] = on }

    /** Switches the view; the new view starts on its remembered page. Returns true if it changed. */
    fun setMode(seat: Int, m: Int): Boolean {
        require(m in 0 until MODES) { "mode $m" }
        if (mode[seat] == m) return false
        mode[seat] = m
        return true
    }

    fun nextPage(seat: Int): Boolean {
        if (!hasNext(seat)) return false
        page[seat * MODES + mode[seat]]++
        setPaging(seat, itemCount[seat], perPage[seat])
        return true
    }

    /** Back to the first page (the bar's `‹` on the last page). Returns true if the page changed. */
    fun firstPage(seat: Int): Boolean {
        if (!hasPrev(seat)) return false
        page[seat * MODES + mode[seat]] = 0
        setPaging(seat, itemCount[seat], perPage[seat])
        return true
    }

    fun prevPage(seat: Int): Boolean {
        if (!hasPrev(seat)) return false
        page[seat * MODES + mode[seat]]--
        setPaging(seat, itemCount[seat], perPage[seat])
        return true
    }

    /** The current view has [itemCount] items, [perPage] per page: clamps the page and records the counts. */
    fun setPaging(seat: Int, itemCount: Int, perPage: Int) {
        this.itemCount[seat] = itemCount
        this.perPage[seat] = perPage
        val n = if (perPage <= 0) 1 else maxOf(1, (itemCount + perPage - 1) / perPage)
        pages[seat] = n
        val i = seat * MODES + mode[seat]
        if (page[i] >= n) page[i] = n - 1
        if (page[i] < 0) page[i] = 0
        onPage[seat] = if (perPage <= 0) 0 else (itemCount - page[i] * perPage).coerceIn(0, perPage)
    }

    fun closeAll() { open.fill(false) }

    companion object {
        /** Counters + commander tax. (Commander damage moved to the main tiles in T30.) */
        const val TRAY = 0

        /** Reserved (T13 partner setup?). T14's toggles are tray items after the counters, not a separate view. */
        const val TOGGLES = 1

        const val MODES = 2
    }
}
