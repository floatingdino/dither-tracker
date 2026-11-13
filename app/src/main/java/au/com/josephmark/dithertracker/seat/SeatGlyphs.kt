package au.com.josephmark.dithertracker.seat

import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.PlayerId
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * One seat glyph as a blittable 1-bit image (T44): a [SeatMap] with [seat] filled (or none, for
 * [SeatGlyphs.EMPTY]), every pixel [k]× and **counter-rotated** by [rotation], so that
 * `blit(glyph, x, y, rotation)` — the way every rotated overlay draws its content — puts it on the
 * screen in screen orientation: the miniature always lies the way the table does.
 *
 * [width]/[height] are the image's own (local, reader's-frame) size: for 90/270 they are the map's
 * height/width. Immutable; identity matters (the renderer caches its bitmap per instance), so get
 * instances from [SeatGlyphs], never construct them per draw.
 */
class SeatGlyph internal constructor(
    val map: SeatMap,
    val seat: Int,
    val rotation: Int,
    val k: Int,
    inset: Int,
) : MonoImage {
    private val sideways = TextLayout.isSideways(rotation)
    private val lw = if (sideways) map.height else map.width
    private val lh = if (sideways) map.width else map.height
    override val width: Int = lw * k
    override val height: Int = lh * k
    private val bits = BooleanArray(lw * lh)

    init {
        for (v in 0 until lh) for (u in 0 until lw) {
            // Where blit(rotation) puts local (u, v) on the screen: that miniature pixel is ours.
            val sx = TextLayout.pointX(rotation, lw, lh, u, v)
            val sy = TextLayout.pointY(rotation, lw, lh, u, v)
            bits[v * lw + u] = map.isLine(sx, sy) || map.isFill(seat, sx, sy, inset)
        }
    }

    override fun isInk(x: Int, y: Int): Boolean = bits[(y / k) * lw + x / k]

    /** ASCII of the image as stored (reader's frame), `#` / `.`; for tests and specimens. */
    fun ascii(): String = buildString {
        for (y in 0 until height) {
            for (x in 0 until width) append(if (isInk(x, y)) '#' else '.')
            append('\n')
        }
    }

    override fun toString(): String = "SeatGlyph(seat $seat rot $rotation k$k ${width}x$height)"
}

/**
 * Seat glyphs for one table layout (T44): "N panel rects in screen space → a miniature at a given
 * size with seat i filled", cached per (seat, rotation, size, k). Build one per layout
 * ([forPanels]; [sameLayout] tells whether a new layout needs a new one) and ask it for glyphs on the
 * draw path: [glyph] is an array lookup once that (size, k) has been used — call [prewarm] from
 * `Component.prewarm` with every (size, k) you draw at, and the draw path allocates nothing.
 *
 * Seat indices are layout seats (`PlayLayout.panels` order: clockwise from top-left). [EMPTY] is the
 * table outline with nothing filled — a player who no longer has a seat.
 */
class SeatGlyphs private constructor(private val panels: Array<IRect>) {

    val seats: Int get() = panels.size

    private var sizes = IntArray(0)
    private var ks = IntArray(0)
    private var maps = arrayOf<SeatMap>()
    private var glyphs = arrayOf<Array<SeatGlyph>>()

    /** True when [other] has the same panel rects (no rebuild needed). */
    fun sameLayout(other: Array<out IRect>): Boolean {
        if (other.size != panels.size) return false
        for (i in panels.indices) {
            val a = panels[i]; val b = other[i]
            if (a.left != b.left || a.top != b.top || a.right != b.right || a.bottom != b.bottom) return false
        }
        return true
    }

    /** The miniature at [size] (lattice size before k). */
    fun map(size: Int): SeatMap = slot(size, 1).let { maps[it] }

    /**
     * Seat [seat] (or [EMPTY]) drawn for a reader whose frame is turned [rotation] (0/90/180/270), fitting
     * a [size]×[size] art box, enlarged [k]× (the widget glyph scale, like icons). Blit it with that same
     * rotation. Seats outside the layout come back [EMPTY].
     */
    fun glyph(seat: Int, rotation: Int, size: Int = ICON, k: Int = 1): SeatGlyph {
        val i = slot(size, k)
        val s = if (seat in 0 until panels.size) seat else EMPTY
        return glyphs[i][(rotation / 90 and 3) * (panels.size + 1) + s + 1]
    }

    /** Builds every glyph for (size, k) now (allocates once per pair; later lookups don't). */
    fun prewarm(size: Int = ICON, k: Int = 1) { slot(size, k) }

    private fun slot(size: Int, k: Int): Int {
        for (i in sizes.indices) if (sizes[i] == size && ks[i] == k) return i
        val map = SeatMap.compute(panels, size)
        val n = panels.size + 1
        val set = Array(4 * n) { j -> SeatGlyph(map, j % n - 1, (j / n) * 90, k, INSET) }
        sizes += size
        ks += k
        maps += map
        glyphs += set
        return sizes.size - 1
    }

    companion object {
        /** No seat filled: the player is no longer at the table. */
        const val EMPTY = -1

        /** Default art box: the 16×16 icon slot. */
        const val ICON = 16

        /**
         * Paper between a filled cell and its lines. 0 (T44, chosen from the specimen): at 16 px a cell is
         * only 6×4 inside, and a 1-px inset leaves a 4×2 dash that reads as noise; flush, the filled
         * quadrant reads instantly as "that corner of the table".
         */
        const val INSET = 0

        /** Glyphs for these screen panel rects (copied). */
        fun forPanels(panels: Array<out IRect>): SeatGlyphs =
            SeatGlyphs(Array(panels.size) { IRect().set(panels[it].left, panels[it].top, panels[it].right, panels[it].bottom) })

        /** Layout seat of player [id] in [state] (`players[i].seat == i`), or [EMPTY] if no longer seated. */
        fun seatOf(state: GameState, id: PlayerId): Int {
            val ps = state.players
            for (i in ps.indices) if (ps[i].id == id) return ps[i].seat
            return EMPTY
        }
    }
}
