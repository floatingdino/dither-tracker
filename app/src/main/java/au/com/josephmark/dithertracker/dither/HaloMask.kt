package au.com.josephmark.dithertracker.dither

/**
 * Halo cut-outs (T41, "halo 1"): a glyph on a dither fill sits on a paper cut-out that follows its
 * outline instead of a square backing. A glyph and its halo are drawn by [DitherRenderer.blitHaloed]
 * (the halo in paper, then the glyph in ink).
 *
 * **The rule** (one for icons, `ui` text and the rect glyphs): from the glyph's ink,
 * 1. fill its holes (paper not 4-connected to the glyph box's edge);
 * 2. dilate by [radius] px, 8-connected (a (2r+1)² square), into a frame [radius] px wider on every side
 *    — for r = 1, steps 1–2 are `design/icons/candidates/heavy/tools.py` `halo_mask(ink, 'f1')`;
 * 3. clean up until nothing changes: fill what the halo encloses (not 4-connected to the frame's edge),
 *    then every 1-px sliver (halo both left and right, or both above and below; judged on the state at
 *    the start of the pass), so no stray dither is trapped between the glyph's parts.
 *
 * Icons and the `ui` face get their halos **at build time** (`app/build.gradle.kts`, `Icon.halo`,
 * `Glyph.halo`: zero runtime work). This runtime twin builds the halos of glyphs whose shape depends on
 * the scale S (the diamond's menu bars, the tab dots) once per layout, off the draw path.
 */
object HaloMask {

    /** [src]'s halo with a [radius]-px rim: a [BitMask] of (w + 2r) × (h + 2r), centred on [src]. */
    fun of(src: MonoImage, radius: Int = 1): BitMask {
        require(radius >= 1) { "radius $radius" }
        val w = src.width
        val h = src.height
        val ink = BooleanArray(w * h) { src.isInk(it % w, it / w) }
        val solid = fillHoles(ink, w, h)
        val fw = w + 2 * radius
        val fh = h + 2 * radius
        var out = BooleanArray(fw * fh)
        for (y in 0 until h) for (x in 0 until w) {
            if (!solid[y * w + x]) continue
            for (dy in 0..2 * radius) for (dx in 0..2 * radius) out[(y + dy) * fw + x + dx] = true
        }
        while (true) {
            val a = fillHoles(out, fw, fh)
            var changed = !a.contentEquals(out)
            val b = a.copyOf()
            for (y in 0 until fh) for (x in 0 until fw) {
                val i = y * fw + x
                if (a[i]) continue
                val lr = x > 0 && x < fw - 1 && a[i - 1] && a[i + 1]
                val ud = y > 0 && y < fh - 1 && a[i - fw] && a[i + fw]
                if (lr || ud) { b[i] = true; changed = true }
            }
            out = b
            if (!changed) break
        }
        return BitMask(fw, fh, out)
    }

    /** [set] plus every unset pixel not 4-connected to the w×h box's edge through unset pixels. */
    fun fillHoles(set: BooleanArray, w: Int, h: Int): BooleanArray {
        val outside = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        fun seed(x: Int, y: Int) {
            val i = y * w + x
            if (!set[i] && !outside[i]) { outside[i] = true; stack[sp++] = i }
        }
        for (x in 0 until w) { seed(x, 0); seed(x, h - 1) }
        for (y in 0 until h) { seed(0, y); seed(w - 1, y) }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w
            val y = i / w
            if (x > 0) seed(x - 1, y)
            if (x < w - 1) seed(x + 1, y)
            if (y > 0) seed(x, y - 1)
            if (y < h - 1) seed(x, y + 1)
        }
        return BooleanArray(w * h) { !outside[it] }
    }

    /**
     * A glyph made of solid rects (screen dither px) as a mask whose top-left is the rects' bounding box
     * top-left ([bounds] receives that box). Used for the rect glyphs (menu bars, tab dots).
     */
    fun rects(rects: Array<IRect>, from: Int, count: Int, bounds: IRect): BitMask {
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = Int.MIN_VALUE; var b = Int.MIN_VALUE
        for (i in from until from + count) {
            val q = rects[i]
            l = minOf(l, q.left); t = minOf(t, q.top); r = maxOf(r, q.right); b = maxOf(b, q.bottom)
        }
        bounds.set(l, t, r, b)
        val w = r - l
        val h = b - t
        val bits = BooleanArray(w * h)
        for (i in from until from + count) {
            val q = rects[i]
            for (y in q.top until q.bottom) for (x in q.left until q.right) bits[(y - t) * w + (x - l)] = true
        }
        return BitMask(w, h, bits)
    }
}

/**
 * A glyph drawn as solid rects (the diamond's menu bars, the tab's dots) turned into a mask with its
 * halo, at a fixed screen position: built once per layout ([of]), drawn with [draw] — the same
 * [DitherRenderer.blitHaloed] path as icons and text.
 */
class HaloGlyph private constructor(val ink: BitMask, val halo: BitMask, val at: IRect) {

    /** The glyph on its cut-out (paper halo, ink glyph; swapped with [invert]). */
    fun draw(r: DitherRenderer, invert: Boolean = false) = r.blitHaloed(ink, halo, at.left, at.top, 0, invert)

    /** Builds both masks' bitmaps now (renderer begun on a scratch canvas). */
    fun prewarm(r: DitherRenderer) {
        r.blit(ink, 0, 0)
        r.blit(halo, 0, 0)
    }

    companion object {
        /** `rects[from until from + count]` (screen dither px) as one glyph with a [radius]-px halo. */
        fun of(rects: Array<IRect>, from: Int, count: Int, radius: Int): HaloGlyph {
            val at = IRect()
            val ink = HaloMask.rects(rects, from, count, at)
            return HaloGlyph(ink, HaloMask.of(ink, radius), at)
        }
    }
}

/**
 * An immutable 1-bit image of any size, with **value** equality: two masks with the same pixels are
 * equal, so a mask rebuilt by a relayout hits the renderer's bitmap cache instead of adding an entry.
 */
class BitMask(override val width: Int, override val height: Int, private val bits: BooleanArray) : MonoImage {
    init {
        require(width >= 1 && height >= 1 && bits.size == width * height) { "${width}x$height, ${bits.size} bits" }
    }

    private val hash = 31 * (31 * width + height) + bits.contentHashCode()

    override fun isInk(x: Int, y: Int): Boolean = bits[y * width + x]

    override fun equals(other: Any?): Boolean =
        other is BitMask && other.width == width && other.height == height && other.bits.contentEquals(bits)

    override fun hashCode(): Int = hash

    override fun toString(): String = "BitMask(${width}x$height)"
}
