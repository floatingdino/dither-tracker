package au.com.josephmark.dithertracker.dither

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import java.nio.ByteBuffer

/**
 * 1-bit drawing on an [android.graphics.Canvas], in dither-pixel coordinates.
 *
 * Every primitive can only produce the two colours ink and paper (#000/#FFF, swapped when
 * [inverted]): paints have AA/filtering/dither off, every coordinate is `margin + n*S` (integer),
 * and all bitmaps are pre-scaled by S with nearest-neighbour so the GPU never resamples.
 *
 * Coordinates are dither pixels relative to the grid origin, which is also the global pattern
 * phase origin. The canvas must be in the root view's coordinates (the view at screen (0,0)) with
 * no scale/rotation applied — rotate content with [blit]'s `rotation`, not with `Canvas.rotate`.
 *
 * Usage: `setGrid()` on size/scale change; in `onDraw` call `begin(canvas)` then draw.
 * No allocations per draw call; tile shaders and scaled bitmaps are created lazily once per
 * (tile, S, inverted) / (image, S) and kept until the scale changes ([prewarm] builds tiles eagerly).
 *
 * Fill strategy: a [BitmapShader] (REPEAT) over a tiny pre-scaled ARGB tile, translated so
 * phase is anchored at the grid origin, drawn with one `drawRect`. That is a single display-list
 * op whatever the rect size; the GPU (or Skia's raster repeat sampler) does the tiling with no
 * per-pixel CPU work in our process. Pre-rendering full-screen pattern bitmaps instead would cost
 * ~10 MB and a texture upload per pattern/scale; drawing per-pixel rects would be thousands of ops.
 */
class DitherRenderer {

    /** Dark mode: swaps ink and paper for everything drawn after it is set. */
    var inverted: Boolean = false

    var grid: DitherGrid = DitherGrid(0, 0, 1)
        private set

    private var canvas: Canvas? = null

    private val ink get() = if (inverted) Color.WHITE else Color.BLACK
    private val paper get() = if (inverted) Color.BLACK else Color.WHITE

    private val solid = crisp()
    private val blitPaint = crisp()
    private val diffPaint = crisp().apply {
        color = Color.WHITE
        if (Build.VERSION.SDK_INT >= 29) blendMode = BlendMode.DIFFERENCE
    }

    // Tile cache: index = tile.id * 2 + (inverted ? 1 : 0).
    private val tilePaints = arrayOfNulls<Paint>(Patterns.all.size * 2)
    private val tileMatrix = Matrix()

    // Scaled 1-bit masks (ALPHA_8, tinted by paint colour at draw time) per image at current S.
    private val masks = HashMap<MonoImage, Bitmap>()

    /** Sets the grid; drops caches when the scale changes, re-anchors shaders when margins change. */
    fun setGrid(newGrid: DitherGrid) {
        val old = grid
        grid = newGrid
        if (old.scale != newGrid.scale) {
            for (i in tilePaints.indices) tilePaints[i] = null
            masks.values.forEach { it.recycle() }
            masks.clear()
        } else if (old.marginLeft != newGrid.marginLeft || old.marginTop != newGrid.marginTop) {
            tileMatrix.setTranslate(newGrid.marginLeft.toFloat(), newGrid.marginTop.toFloat())
            for (p in tilePaints) p?.shader?.setLocalMatrix(tileMatrix)
        }
        tileMatrix.setTranslate(newGrid.marginLeft.toFloat(), newGrid.marginTop.toFloat())
    }

    /** Builds every tile shader (both polarities) now, e.g. off the first frame. */
    fun prewarm() {
        for (t in Patterns.all) {
            tilePaint(t, false); tilePaint(t, true)
        }
    }

    fun begin(canvas: Canvas) {
        this.canvas = canvas
    }

    /** Runs [block] with [inverted] flipped (e.g. content on a SELECTED/ink background). */
    inline fun withInverted(block: () -> Unit) {
        inverted = !inverted
        try {
            block()
        } finally {
            inverted = !inverted
        }
    }

    // ---- fills ----------------------------------------------------------------------------

    /** Fills the whole canvas, margins included, with paper. */
    fun clear() {
        canvas!!.drawColor(paper)
    }

    /** Fills a dither rect with [tile], phase anchored to the grid origin. */
    fun fill(tile: Tile, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        when (tile.inkCount) {
            0 -> rect(x, y, w, h, ink = false)
            tile.width * tile.height -> rect(x, y, w, h, ink = true)
            else -> drawDev(x, y, w, h, tilePaint(tile, inverted))
        }
    }

    fun fill(role: Role, x: Int, y: Int, w: Int, h: Int) = fill(role.tile, x, y, w, h)
    fun fill(tile: Tile, r: IRect) = fill(tile, r.left, r.top, r.width, r.height)
    fun fill(role: Role, r: IRect) = fill(role.tile, r.left, r.top, r.width, r.height)

    /** Solid rect in ink (or paper when [ink] is false). */
    fun rect(x: Int, y: Int, w: Int, h: Int, ink: Boolean = true) {
        if (w <= 0 || h <= 0) return
        solid.color = if (ink) this.ink else paper
        drawDev(x, y, w, h, solid)
    }

    fun rect(r: IRect, ink: Boolean = true) = rect(r.left, r.top, r.width, r.height, ink)

    // ---- lines ----------------------------------------------------------------------------

    /** Horizontal 1-dither-px line. */
    fun hline(x: Int, y: Int, len: Int, ink: Boolean = true) = rect(x, y, len, 1, ink)

    /** Vertical 1-dither-px line. */
    fun vline(x: Int, y: Int, len: Int, ink: Boolean = true) = rect(x, y, 1, len, ink)

    /**
     * Dotted horizontal line, 1 on / 1 off. Phase is global (ink where x+y is even), so dotted
     * lines that cross or abut always line up.
     */
    fun dottedH(x: Int, y: Int, len: Int) = fill(Role.DOTTED.tile, x, y, len, 1)

    /** Dotted vertical line, 1 on / 1 off, globally phased. */
    fun dottedV(x: Int, y: Int, len: Int) = fill(Role.DOTTED.tile, x, y, 1, len)

    /** Outline of thickness [t] drawn inside the rect. */
    fun frame(x: Int, y: Int, w: Int, h: Int, t: Int = 1, ink: Boolean = true) {
        if (w <= 0 || h <= 0) return
        if (w <= 2 * t || h <= 2 * t) {
            rect(x, y, w, h, ink); return
        }
        rect(x, y, w, t, ink)
        rect(x, y + h - t, w, t, ink)
        rect(x, y + t, t, h - 2 * t, ink)
        rect(x + w - t, y + t, t, h - 2 * t, ink)
    }

    fun frame(r: IRect, t: Int = 1, ink: Boolean = true) = frame(r.left, r.top, r.width, r.height, t, ink)

    // ---- press feedback -------------------------------------------------------------------

    /**
     * Inverts what is already drawn in the rect (black↔white) — pressed feedback.
     * Uses blend mode DIFFERENCE with white: |255 − c| maps 0↔255 exactly, one draw op, no
     * redraw of the content. Requires API 29+ ([canInvertInPlace]); on older devices redraw the
     * region inside [withInverted] instead.
     */
    fun invertRect(x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0 || !canInvertInPlace) return
        drawDev(x, y, w, h, diffPaint)
    }

    fun invertRect(r: IRect) = invertRect(r.left, r.top, r.width, r.height)

    // ---- bitmaps --------------------------------------------------------------------------

    /**
     * Blits a 1-bit image with its top-left at dither (x, y), each image pixel S×S device px.
     *
     * @param rotation 0, 90, 180 or 270 degrees clockwise. For 90/270 the footprint is h×w.
     * @param invert draw the image's ink as paper (and its paper as ink when [opaque]).
     * @param opaque also paint the image's paper pixels (otherwise they are left untouched).
     */
    fun blit(
        img: MonoImage,
        x: Int,
        y: Int,
        rotation: Int = 0,
        invert: Boolean = false,
        opaque: Boolean = false,
    ) {
        val c = canvas!!
        val s = grid.scale
        val sideways = rotation == 90 || rotation == 270
        val fw = if (sideways) img.height else img.width
        val fh = if (sideways) img.width else img.height
        if (opaque) rect(x, y, fw, fh, ink = invert)
        val bmp = mask(img)
        blitPaint.color = if (invert) paper else ink
        val dx = grid.toDeviceX(x).toFloat()
        val dy = grid.toDeviceY(y).toFloat()
        if (rotation == 0) {
            c.drawBitmap(bmp, dx, dy, blitPaint)
            return
        }
        val bw = (img.width * s).toFloat()
        val bh = (img.height * s).toFloat()
        c.save()
        c.translate(dx, dy)
        when (rotation) {
            90 -> { c.translate(bh, 0f); c.rotate(90f) }
            180 -> { c.translate(bw, bh); c.rotate(180f) }
            270 -> { c.translate(0f, bw); c.rotate(270f) }
            else -> { c.restore(); throw IllegalArgumentException("rotation must be 0/90/180/270, was $rotation") }
        }
        c.drawBitmap(bmp, 0f, 0f, blitPaint)
        c.restore()
    }

    fun icon(icon: Icon, x: Int, y: Int, rotation: Int = 0, invert: Boolean = false, opaque: Boolean = false) =
        blit(icon, x, y, rotation, invert, opaque)

    // ---- internals ------------------------------------------------------------------------

    private fun drawDev(x: Int, y: Int, w: Int, h: Int, paint: Paint) {
        val g = grid
        canvas!!.drawRect(
            g.toDeviceX(x).toFloat(), g.toDeviceY(y).toFloat(),
            g.toDeviceX(x + w).toFloat(), g.toDeviceY(y + h).toFloat(),
            paint,
        )
    }

    private fun tilePaint(tile: Tile, inv: Boolean): Paint {
        val i = tile.id * 2 + if (inv) 1 else 0
        tilePaints[i]?.let { return it }
        val s = grid.scale
        val w = tile.width * s
        val h = tile.height * s
        val inkC = if (inv) Color.WHITE else Color.BLACK
        val paperC = if (inv) Color.BLACK else Color.WHITE
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            px[y * w + x] = if (tile.isInk(x / s, y / s)) inkC else paperC
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        shader.setLocalMatrix(tileMatrix)
        val p = crisp().apply { this.shader = shader }
        tilePaints[i] = p
        return p
    }

    private fun mask(img: MonoImage): Bitmap {
        masks[img]?.let { return it }
        val s = grid.scale
        val w = img.width * s
        val h = img.height * s
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val stride = bmp.rowBytes
        val buf = ByteBuffer.allocate(stride * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (img.isInk(x / s, y / s)) buf.put(y * stride + x, 0xFF.toByte())
        }
        bmp.copyPixelsFromBuffer(buf)
        masks[img] = bmp
        return bmp
    }

    companion object {
        /** Whether [invertRect] works on this device (blend mode DIFFERENCE, API 29+). */
        @JvmField val canInvertInPlace: Boolean = Build.VERSION.SDK_INT >= 29

        /** A paint with every smoothing feature off. */
        fun crisp(): Paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            isDither = false
            style = Paint.Style.FILL
        }
    }
}
