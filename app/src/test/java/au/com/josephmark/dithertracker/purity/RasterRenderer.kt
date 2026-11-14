package au.com.josephmark.dithertracker.purity

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Tile
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * A pure-JVM [DitherRenderer] (T22): one byte per dither px of the grid, [INK] or [PAPER], with the
 * same semantics as `CanvasDitherRenderer` on an API 29+ device (in-place inversion, global-phase
 * tiles, rotated blits, nested axis-aligned clips). Whole Play screens draw on it through the real
 * components, so pixels are checked without a device.
 *
 * Every pixel a draw would put **outside the grid** (and inside the current clip) is recorded in
 * [outside] instead of written: on the device that pixel lands in the margin (or off-screen), which
 * `tools/check_1bit.py --scale S` rejects. While [scratch] is set (the host's prewarm, which on the
 * device draws into a throwaway 1×1 canvas) nothing is drawn or recorded.
 */
class RasterRenderer(grid: DitherGrid) : DitherRenderer() {

    val width = grid.width
    val height = grid.height
    val px = ByteArray(width * height)

    /** Out-of-grid pixels drawn since the last [clear]: count, and the first few with the call that drew them. */
    var outside = 0
        private set
    val outsideNotes = ArrayList<String>()

    var scratch = false

    /**
     * What `CanvasDitherRenderer` caches per image / per sparse tile paint, split by when it is first
     * needed (T20): during the host's prewarm ([scratch] set) or only in a real draw, where the device
     * would build the scaled bitmap inside `onDraw`. [cold] must stay empty ([perf.PrewarmCoverageTest]).
     */
    val warm = java.util.IdentityHashMap<Any, Boolean>()
    val cold = java.util.IdentityHashMap<Any, String>()
    private val sparseKeys = Array(256) { Any() }

    private fun used(key: Any, what: String, tile: Tile? = null, white: Boolean = false) {
        if (scratch) {
            warm[key] = true
        } else if (!warm.containsKey(key) && !cold.containsKey(key)) {
            cold[key] = if (tile == null) what else "$what $tile white=$white" // formatted only when recorded
        }
    }

    // Saved clips, four ints each (no allocation per clipTo: the T20 allocation tests draw through this).
    private var clips = IntArray(4 * 8)
    private var clipDepth = 0
    // The current clip; unbounded at the base (an out-of-grid draw is recorded, not silently clipped).
    private var cl = Int.MIN_VALUE
    private var ct = Int.MIN_VALUE
    private var cr = Int.MAX_VALUE
    private var cb = Int.MAX_VALUE

    init {
        setGrid(grid)
    }

    operator fun get(x: Int, y: Int): Byte = px[y * width + x]

    private fun ink(logicalInk: Boolean): Byte = if (logicalInk != inverted) INK else PAPER

    private fun inClip(x: Int, y: Int) = x >= cl && y >= ct && x < cr && y < cb

    // The op label is a constant plus an optional tile, formatted only for a note: nothing is built
    // per pixel (the T20 allocation tests draw whole screens through this).
    private fun put(x: Int, y: Int, v: Byte, op: String, tile: Tile? = null) {
        if (scratch) return
        if (!inClip(x, y)) return
        if (x < 0 || y < 0 || x >= width || y >= height) {
            if (outside++ < 3) outsideNotes += "${if (tile == null) op else "$op($tile)"} wrote ($x,$y) outside the ${width}x$height grid"
            return
        }
        px[y * width + x] = v
    }

    private fun flip(x: Int, y: Int, op: String) {
        if (scratch || !inClip(x, y)) return
        if (x < 0 || y < 0 || x >= width || y >= height) {
            if (outside++ < 3) outsideNotes += "$op inverted ($x,$y) outside the ${width}x$height grid"
            return
        }
        val i = y * width + x
        px[i] = if (px[i] == INK) PAPER else INK
    }

    override fun clear() {
        if (scratch) return
        px.fill(ink(false))
        outside = 0
        outsideNotes.clear()
    }

    override fun fillPattern(tile: Tile, x: Int, y: Int, w: Int, h: Int) {
        if (scratch) return
        for (gy in y until y + h) for (gx in x until x + w) put(gx, gy, ink(tile.isInkAt(gx, gy)), "fill", tile)
    }

    override fun solidRect(x: Int, y: Int, w: Int, h: Int, ink: Boolean) {
        if (scratch) return
        val v = ink(ink)
        for (gy in y until y + h) for (gx in x until x + w) put(gx, gy, v, "rect")
    }

    /** API 29+ semantics: in-place inversion always works here. */
    override val invertsInPlace: Boolean get() = true

    override fun invertRect(x: Int, y: Int, w: Int, h: Int) {
        if (scratch) return
        for (gy in y until y + h) for (gx in x until x + w) flip(gx, gy, "invertRect")
    }

    override fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, invert: Boolean, opaque: Boolean) {
        used(img, "blit")
        if (scratch) return
        val side = rotation == 90 || rotation == 270
        val sw = img.width
        val sh = img.height
        val fw = if (side) sh else sw
        val fh = if (side) sw else sh
        if (opaque) rect(x, y, fw, fh, ink = invert)
        val v = ink(!invert)
        for (fy in 0 until fh) for (fx in 0 until fw) {
            val u: Int
            val w: Int
            when (rotation) {
                0 -> { u = fx; w = fy }
                90 -> { u = fy; w = sh - 1 - fx }
                180 -> { u = sw - 1 - fx; w = sh - 1 - fy }
                270 -> { u = sw - 1 - fy; w = fx }
                else -> throw IllegalArgumentException("rotation must be 0/90/180/270, was $rotation")
            }
            if (img.isInk(u, w)) put(x + fx, y + fy, v, "blit")
        }
    }

    override fun fillMask(mask: MonoImage, x: Int, y: Int, tile: Tile, invert: Boolean) {
        used(mask, "fillMask")
        if (scratch) return
        val inv = inverted != invert
        for (my in 0 until mask.height) for (mx in 0 until mask.width) {
            if (!mask.isInk(mx, my)) continue
            val gx = x + mx
            val gy = y + my
            put(gx, gy, if (tile.isInkAt(gx, gy) != inv) INK else PAPER, "fillMask", tile)
        }
    }

    override fun drawMask(img: MonoImage, x: Int, y: Int, op: MaskOp, tile: Tile, underlay: Runnable?) {
        // As CanvasDitherRenderer: an all-paper tile through TILE_INK/PAPER draws (and caches) nothing;
        // a mixed one needs a sparse tile paint per (tile, ink colour), ink colour = white in dark mode
        // for TILE_INK.
        val sparseOp = op == MaskOp.TILE_INK || op == MaskOp.TILE_PAPER
        if (!(sparseOp && tile.inkCount == 0)) {
            used(img, "drawMask")
            if (sparseOp && tile.inkCount != tile.width * tile.height) {
                val white = inverted == (op == MaskOp.TILE_INK)
                used(sparseKeys[(tile.id * 2 + if (white) 1 else 0) and 255], "sparse tile paint", tile, white)
            }
        }
        if (scratch) return
        for (my in 0 until img.height) for (mx in 0 until img.width) {
            if (!img.isInk(mx, my)) continue
            val gx = x + mx
            val gy = y + my
            val t = tile.isInkAt(gx, gy)
            when (op) {
                MaskOp.INVERT -> flip(gx, gy, "drawMask(INVERT)")
                MaskOp.TILE_INK -> if (t) put(gx, gy, ink(true), "drawMask TILE_INK", tile)
                MaskOp.TILE_PAPER -> if (t) put(gx, gy, ink(false), "drawMask TILE_PAPER", tile)
                MaskOp.TILE_FILL -> put(gx, gy, ink(t), "drawMask TILE_FILL", tile)
            }
        }
    }

    override fun clipTo(r: IRect) {
        if (4 * clipDepth == clips.size) clips = clips.copyOf(clips.size * 2)
        val i = 4 * clipDepth++
        clips[i] = cl; clips[i + 1] = ct; clips[i + 2] = cr; clips[i + 3] = cb
        cl = maxOf(cl, r.left); ct = maxOf(ct, r.top); cr = minOf(cr, r.right); cb = minOf(cb, r.bottom)
    }

    override fun unclip() {
        check(clipDepth > 0) { "unclip without clipTo" }
        val i = 4 * --clipDepth
        cl = clips[i]; ct = clips[i + 1]; cr = clips[i + 2]; cb = clips[i + 3]
    }

    /** No clip left open (every [clipTo] was paired with an [unclip]). */
    val clipBalanced: Boolean get() = clipDepth == 0

    /** Writes the raster as a PNG at [s] px per dither px (debug output). */
    fun writePng(file: File, s: Int = 1) {
        file.parentFile.mkdirs()
        val img = BufferedImage(width * s, height * s, BufferedImage.TYPE_BYTE_BINARY)
        for (y in 0 until height * s) for (x in 0 until width * s) {
            img.setRGB(x, y, if (this[x / s, y / s] == INK) 0x000000 else 0xFFFFFF)
        }
        ImageIO.write(img, "png", file)
    }

    companion object {
        const val PAPER: Byte = 0
        const val INK: Byte = 1
    }
}
