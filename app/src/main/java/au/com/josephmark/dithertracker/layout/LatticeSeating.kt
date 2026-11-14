package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.type.TextLayout
import kotlin.math.atan2

/**
 * A [Lattice] placed on a [gridW]×[gridH] dither grid (T60). Pure integer maths; built once per layout.
 *
 * **Cells.** The landscape table (`TW = max(w, h)` wide, `TH = min(w, h)` tall) is cut into `cols`
 * equal columns and `rows` equal rows with 1-px gaps between them; leftover px are margin, the
 * composition centred (odd px after). A seat's **tile** ([seats]) is the union of its cells, the gaps
 * between them included; an empty cell's rect is in [empties].
 *
 * **One content frame.** Seats may now differ in size, but every component lays its content out once,
 * in one player-frame tile (`PlayLayout.panelW` × `panelH`). That frame is the largest every seat holds:
 * the smallest seat width and the smallest seat depth (each in its own player's frame). A bigger seat
 * holds it against its **near edge** (the drawer tab, turn tab and partner tab stay on the edge the
 * player sits at), centred along that edge, with paper round it — the same numerals, drawer and tray
 * at every seat, a bigger seat just has more air. [panels] are those frames on the screen; the life
 * halves cover the whole tile (`PlayLayout.zones`).
 *
 * **Order and facing.** Seats are numbered clockwise from the far left (by the angle of each tile's
 * centre about the table's, starting from the left), as the band arrangements are; each faces the
 * edge its definition says.
 *
 * **Dividers** are the 1-px gaps between two different owners (a seat and another seat, or a seat
 * and an empty cell), crossings included where a line runs on; nothing between two empty cells or
 * inside a seat. **Diamond:** with two rows, on the long gap at the crossing nearest the middle where
 * a vertical divider meets it ([Arrangement.DiamondAt.JUNCTION]) or the middle of that gap
 * ([Arrangement.DiamondAt.CENTRE]); with one row, as a one-sided band arrangement: on the divider
 * nearest the middle, [diamondReach] in from the far edge.
 */
class LatticeSeating(
    val lattice: Lattice,
    diamond: Arrangement.DiamondAt,
    val gridW: Int,
    val gridH: Int,
    diamondReach: Int,
) {
    val players: Int = lattice.seats
    val portrait: Boolean = gridH > gridW
    val frameW: Int
    val frameH: Int
    val panels: Array<IRect> = Array(players) { IRect() }
    val seats: Array<IRect> = Array(players) { IRect() }
    val rotation = IntArray(players)
    val dividers: Array<IRect>
    val empties: Array<IRect>
    val diamondX: Int
    val diamondY: Int

    init {
        val l = lattice
        val tw = maxOf(gridW, gridH)
        val th = minOf(gridW, gridH)
        val cw = maxOf(1, (tw - (l.cols - 1)) / l.cols)
        val ch = maxOf(1, (th - (l.rows - 1)) / l.rows)
        val usedW = l.cols * cw + (l.cols - 1)
        val usedH = l.rows * ch + (l.rows - 1)
        val ox = maxOf(0, (tw - usedW) / 2)
        val oy = maxOf(0, (th - usedH) / 2)
        fun cx(c: Int) = ox + c * (cw + 1)
        fun cy(r: Int) = oy + r * (ch + 1)

        // ---- seats (table frame), then clockwise order ------------------------------------------------
        val n = l.seats
        val tile = Array(n) { i ->
            val c0 = l.span[4 * i]; val r0 = l.span[4 * i + 1]; val c1 = l.span[4 * i + 2]; val r1 = l.span[4 * i + 3]
            IRect().setXywh(cx(c0), cy(r0), (c1 - c0 + 1) * cw + (c1 - c0), (r1 - r0 + 1) * ch + (r1 - r0))
        }
        val midX2 = 2 * ox + usedW
        val midY2 = 2 * oy + usedH
        fun angle(i: Int): Double {
            val t = tile[i]
            val a = Math.toDegrees(atan2((t.top + t.bottom - midY2).toDouble(), (t.left + t.right - midX2).toDouble()))
            return ((a + 180.0 - 1e-6) % 360.0 + 360.0) % 360.0 // 0 = pointing left, then clockwise (screen y down)
        }
        val order = (0 until n).sortedBy { angle(it) }

        // ---- the content frame: the largest every seat holds -----------------------------------------
        var fw = Int.MAX_VALUE
        var fh = Int.MAX_VALUE
        for (i in 0 until n) {
            val t = tile[i]
            fw = minOf(fw, TextLayout.footprintW(l.facing[i], t.width, t.height))
            fh = minOf(fh, TextLayout.footprintH(l.facing[i], t.width, t.height))
        }
        frameW = fw
        frameH = fh

        val tp = Array(n) { IRect() }
        val ts = Array(n) { IRect() }
        for (s in 0 until n) {
            val i = order[s]
            val t = tile[i]
            val r = l.facing[i]
            ts[s].set(t.left, t.top, t.right, t.bottom)
            // Player frame of the whole tile: sw × sh; the content frame centred along the near edge.
            val sw = TextLayout.footprintW(r, t.width, t.height)
            val sh = TextLayout.footprintH(r, t.width, t.height)
            val lx = (sw - fw) / 2
            val ly = sh - fh
            tp[s].setXywh(
                t.left + TextLayout.mapX(r, sw, sh, lx, ly, fw, fh),
                t.top + TextLayout.mapY(r, sw, sh, lx, ly, fw, fh),
                TextLayout.footprintW(r, fw, fh),
                TextLayout.footprintH(r, fw, fh),
            )
            rotation[s] = if (portrait) (r + 90) % 360 else r
        }

        // ---- dividers: the gaps between different owners ---------------------------------------------
        fun own(c: Int, r: Int): Int = if (c < 0 || r < 0 || c >= l.cols || r >= l.rows) OUT else l.ownerAt(c, r)
        fun differ(a: Int, b: Int) = a != b && !(a == Lattice.EMPTY && b == Lattice.EMPTY) && a != OUT && b != OUT
        // Per gap pixel: is it a divider? A pixel beside a cell is when the cells either side differ; a
        // crossing pixel is when a segment on its own line touches it (so lines meet and turn corners).
        val divs = ArrayList<IRect>()
        fun runs(len: Int, on: (Int) -> Boolean, emit: (Int, Int) -> Unit) {
            var start = -1
            for (i in 0..len) {
                val v = i < len && on(i)
                if (v && start < 0) start = i
                if (!v && start >= 0) { emit(start, i); start = -1 }
            }
        }
        // Which row (or -1 for a gap) a table-frame y is in, and the same for x.
        fun rowAt(y: Int): Int { val k = (y - oy) / (ch + 1); return if ((y - oy) % (ch + 1) == ch) -1 else k }
        fun colAt(x: Int): Int { val k = (x - ox) / (cw + 1); return if ((x - ox) % (cw + 1) == cw) -1 else k }
        for (c in 1 until l.cols) { // vertical lines first (draw order as the band arrangements)
            val x = cx(c) - 1
            fun seg(r: Int) = r in 0 until l.rows && differ(own(c - 1, r), own(c, r))
            runs(usedH, { i ->
                val r = rowAt(oy + i)
                if (r >= 0) seg(r) else { val above = (i + 1) / (ch + 1) - 1; seg(above) || seg(above + 1) }
            }) { a0, a1 -> divs += IRect().set(x, oy + a0, x + 1, oy + a1) }
        }
        for (r in 1 until l.rows) {
            val y = cy(r) - 1
            fun seg(c: Int) = c in 0 until l.cols && differ(own(c, r - 1), own(c, r))
            runs(usedW, { i ->
                val c = colAt(ox + i)
                if (c >= 0) seg(c) else { val left = (i + 1) / (cw + 1) - 1; seg(left) || seg(left + 1) }
            }) { a0, a1 -> divs += IRect().set(ox + a0, y, ox + a1, y + 1) }
        }

        // ---- empty cells (an empty run's gaps are filled too, so a 2-cell hole is one ground) -----------
        val emp = ArrayList<IRect>()
        for (r in 0 until l.rows) for (c in 0 until l.cols) {
            if (own(c, r) != Lattice.EMPTY) continue
            val right = if (own(c + 1, r) == Lattice.EMPTY) 1 else 0
            val down = if (own(c, r + 1) == Lattice.EMPTY) 1 else 0
            emp += IRect().setXywh(cx(c), cy(r), cw + right, ch + down)
        }

        // ---- diamond -----------------------------------------------------------------------------------
        var dx = ox + usedW / 2
        var dy = oy + usedH / 2
        if (l.rows == 2) {
            dy = cy(1) - 1
            if (diamond == Arrangement.DiamondAt.JUNCTION) {
                var best = Int.MAX_VALUE
                for (c in 1 until l.cols) {
                    val x = cx(c) - 1
                    val meets = differ(own(c - 1, 0), own(c, 0)) || differ(own(c - 1, 1), own(c, 1))
                    if (!meets) continue
                    val d = Math.abs(2 * x - midX2)
                    if (d < best) { best = d; dx = x }
                }
            }
        } else if (l.rows == 1) {
            dy = maxOf(oy, diamondReach)
            var best = Int.MAX_VALUE
            for (c in 1 until l.cols) {
                val x = cx(c) - 1
                if (!differ(own(c - 1, 0), own(c, 0))) continue
                val d = Math.abs(2 * x - midX2)
                if (d < best) { best = d; dx = x }
            }
        }

        // ---- to the screen -------------------------------------------------------------------------------
        for (s in 0 until n) {
            toScreen(tp[s], th, panels[s])
            toScreen(ts[s], th, seats[s])
        }
        dividers = Array(divs.size) { toScreen(divs[it], th, IRect()) }
        empties = Array(emp.size) { toScreen(emp[it], th, IRect()) }
        if (portrait) { diamondX = th - 1 - dy; diamondY = dx } else { diamondX = dx; diamondY = dy }
    }

    private fun toScreen(r: IRect, th: Int, out: IRect): IRect =
        if (!portrait) out.set(r.left, r.top, r.right, r.bottom)
        else out.setXywh(th - r.top - r.height, r.left, r.height, r.width)

    private companion object {
        const val OUT = -2
    }
}
