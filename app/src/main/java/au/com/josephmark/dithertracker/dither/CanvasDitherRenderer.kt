package au.com.josephmark.dithertracker.dither

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.os.Build
import java.nio.ByteBuffer

/**
 * [DitherRenderer] on an [android.graphics.Canvas]: the app's renderer.
 *
 * Paints have AA/filtering/dither off, every coordinate is `margin + n*S` (integer), and all bitmaps
 * are pre-scaled by S with nearest-neighbour so the GPU never resamples. The canvas must be in the
 * root view's coordinates (the view at screen (0,0)) with no scale/rotation applied.
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
class CanvasDitherRenderer : DitherRenderer() {

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

    /**
     * A scaled 1-bit mask (ALPHA_8, tinted by the paint at draw time) of an image's **ink box** only:
     * [bitmap] covers dither px ([x], [y]) .. + its size / S of the image; null when the image has no
     * ink. Pixels outside the box are paper in the image, and an ALPHA_8 mask draws nothing where its
     * alpha is 0 (blit, shader fill, DIFFERENCE alike), so cropping changes no pixel on screen (T20:
     * the effect masks went from ~30 MB to ~9 MB at S = 4).
     */
    private class Mask(val bitmap: Bitmap?, val x: Int, val y: Int)

    // Per image at the current S (identity-keyed: images are long-lived singletons).
    private val masks = HashMap<MonoImage, Mask>()

    /** Sets the grid; drops caches when the scale changes, re-anchors shaders when margins change. */
    override fun setGrid(newGrid: DitherGrid) {
        val old = grid
        super.setGrid(newGrid)
        if (old.scale != newGrid.scale) {
            for (i in tilePaints.indices) tilePaints[i] = null
            masks.values.forEach { it.bitmap?.recycle() }
            masks.clear()
        } else if (old.marginLeft != newGrid.marginLeft || old.marginTop != newGrid.marginTop) {
            tileMatrix.setTranslate(newGrid.marginLeft.toFloat(), newGrid.marginTop.toFloat())
            for (p in tilePaints) p?.shader?.setLocalMatrix(tileMatrix)
        }
        tileMatrix.setTranslate(newGrid.marginLeft.toFloat(), newGrid.marginTop.toFloat())
    }

    /** Builds every tile shader (both polarities) now, e.g. off the first frame. */
    override fun prewarm() {
        for (t in Patterns.all) {
            tilePaint(t, false); tilePaint(t, true)
        }
    }

    fun begin(canvas: Canvas) {
        this.canvas = canvas
    }

    // ---- fills ----------------------------------------------------------------------------

    override fun clear() {
        canvas!!.drawColor(paper)
    }

    override fun fillPattern(tile: Tile, x: Int, y: Int, w: Int, h: Int) = drawDev(x, y, w, h, tilePaint(tile, inverted))

    override fun solidRect(x: Int, y: Int, w: Int, h: Int, ink: Boolean) {
        solid.color = if (ink) this.ink else paper
        drawDev(x, y, w, h, solid)
    }

    // ---- press feedback -------------------------------------------------------------------

    /**
     * Uses blend mode DIFFERENCE with white: |255 − c| maps 0↔255 exactly, one draw op, no
     * redraw of the content. A no-op below API 29 ([canInvertInPlace]).
     */
    override fun invertRect(x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0 || !canInvertInPlace) return
        drawDev(x, y, w, h, diffPaint)
    }

    // ---- bitmaps --------------------------------------------------------------------------

    override fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, invert: Boolean, opaque: Boolean) {
        val c = canvas!!
        val s = grid.scale
        val sideways = rotation == 90 || rotation == 270
        val fw = if (sideways) img.height else img.width
        val fh = if (sideways) img.width else img.height
        if (opaque) rect(x, y, fw, fh, ink = invert)
        val m = mask(img)
        if (m.bitmap == null) return
        blitPaint.color = if (invert) paper else ink
        val dx = grid.toDeviceX(x).toFloat()
        val dy = grid.toDeviceY(y).toFloat()
        // The mask holds only the image's ink box, at (m.x, m.y) in the image (dither px).
        val ox = (m.x * s).toFloat()
        val oy = (m.y * s).toFloat()
        if (rotation == 0) {
            c.drawBitmap(m.bitmap, dx + ox, dy + oy, blitPaint)
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
        c.drawBitmap(m.bitmap, ox, oy, blitPaint)
        c.restore()
    }

    /**
     * One drawBitmap of the cached ALPHA_8 mask with the tile's shader paint: an alpha-only bitmap is
     * coloured by the paint's shader, which stays anchored to the grid origin because the canvas is
     * untransformed.
     */
    override fun fillMask(mask: MonoImage, x: Int, y: Int, tile: Tile, invert: Boolean) {
        val c = canvas!!
        val inv = inverted != invert
        val paint = when (tile.inkCount) {
            0 -> solid.also { it.color = if (inv) Color.BLACK else Color.WHITE }
            tile.width * tile.height -> solid.also { it.color = if (inv) Color.WHITE else Color.BLACK }
            else -> tilePaint(tile, inv)
        }
        val m = mask(mask)
        val bmp = m.bitmap ?: return
        c.drawBitmap(bmp, grid.toDeviceX(x + m.x).toFloat(), grid.toDeviceY(y + m.y).toFloat(), paint)
    }

    // ---- effect masks (T28e) --------------------------------------------------------------

    /**
     * One drawBitmap of the cached ALPHA_8 mask: [MaskOp.INVERT] with blend DIFFERENCE (like
     * [invertRect]); below API 29 the [underlay] goes into a layer clipped to the mask (DST_IN).
     * No allocations after the first call per (image, S) and (tile, colour, S).
     */
    override fun drawMask(img: MonoImage, x: Int, y: Int, op: MaskOp, tile: Tile, underlay: Runnable?) {
        val c = canvas!!
        val m = mask(img)
        val bmp = m.bitmap ?: return
        // The mask covers only the image's ink box (dither px m.x, m.y .. + its size).
        val dx = grid.toDeviceX(x + m.x).toFloat()
        val dy = grid.toDeviceY(y + m.y).toFloat()
        val full = tile.width * tile.height
        val paint: Paint = when (op) {
            MaskOp.INVERT -> {
                if (!canInvertInPlace) {
                    if (underlay == null) return
                    c.saveLayer(dx, dy, dx + bmp.width, dy + bmp.height, null)
                    try {
                        withInverted { underlay.run() }
                        c.drawBitmap(bmp, dx, dy, maskClipPaint)
                    } finally {
                        c.restore()
                    }
                    return
                }
                diffPaint
            }
            MaskOp.TILE_INK, MaskOp.TILE_PAPER -> {
                if (tile.inkCount == 0) return
                val white = inverted == (op == MaskOp.TILE_INK) // ink is white when inverted; paper otherwise
                if (tile.inkCount == full) solid.also { it.color = if (white) Color.WHITE else Color.BLACK }
                else sparseTilePaint(tile, white)
            }
            MaskOp.TILE_FILL -> when (tile.inkCount) {
                0 -> solid.also { it.color = paper }
                full -> solid.also { it.color = ink }
                else -> tilePaint(tile, inverted)
            }
        }
        c.drawBitmap(bmp, dx, dy, paint)
    }

    // drawMask's helpers: tile shaders whose paper is transparent, per (tile, white), rebuilt on a
    // scale change and re-anchored to the grid origin on every use.
    private val sparseTilePaints = arrayOfNulls<Paint>(Patterns.all.size * 2)
    private var sparseScale = 0
    private val maskClipPaint = crisp().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    private fun sparseTilePaint(tile: Tile, white: Boolean): Paint {
        val s = grid.scale
        if (sparseScale != s) {
            for (i in sparseTilePaints.indices) sparseTilePaints[i] = null
            sparseScale = s
        }
        val i = tile.id * 2 + if (white) 1 else 0
        val p = sparseTilePaints[i] ?: run {
            val w = tile.width * s
            val h = tile.height * s
            val inkC = if (white) Color.WHITE else Color.BLACK
            val px = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                px[y * w + x] = if (tile.isInk(x / s, y / s)) inkC else Color.TRANSPARENT
            }
            val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            crisp().apply { shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
                .also { sparseTilePaints[i] = it }
        }
        p.shader.setLocalMatrix(tileMatrix)
        return p
    }

    // ---- clip -----------------------------------------------------------------------------

    /** `Canvas.clipRect` inside a `save`. */
    override fun clipTo(r: IRect) {
        val c = canvas!!
        val g = grid
        c.save()
        c.clipRect(g.toDeviceX(r.left), g.toDeviceY(r.top), g.toDeviceX(r.right), g.toDeviceY(r.bottom))
    }

    override fun unclip() = canvas!!.restore()

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

    /** Scaled masks cached at the current S (trace / tests: memory accounting). */
    val maskCount: Int get() = masks.size

    /** Bytes held by the cached masks ([Bitmap.getAllocationByteCount]). */
    fun maskBytes(): Long {
        var n = 0L
        for (m in masks.values) n += m.bitmap?.allocationByteCount ?: 0
        return n
    }

    private fun mask(img: MonoImage): Mask {
        masks[img]?.let { return it }
        val m = buildMask(img, grid.scale)
        masks[img] = m
        return m
    }

    /**
     * Crops [img] to its ink box and scales it by [s], nearest neighbour: each dither row is expanded
     * once into a device row, which is then copied for the other s - 1 rows (no per-device-pixel
     * `isInk` calls: building every effect mask at layout was most of a warm start, T20).
     */
    private fun buildMask(img: MonoImage, s: Int): Mask {
        val w = img.width
        val h = img.height
        var x0 = w
        var y0 = h
        var x1 = -1
        var y1 = -1
        val row = BooleanArray(w)
        for (y in 0 until h) {
            var any = false
            for (x in 0 until w) if (img.isInk(x, y)) {
                any = true
                if (x < x0) x0 = x
                if (x > x1) x1 = x
            }
            if (any) {
                if (y < y0) y0 = y
                y1 = y
            }
        }
        if (x1 < 0) return Mask(null, 0, 0)
        val bw = (x1 - x0 + 1) * s
        val bh = (y1 - y0 + 1) * s
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
        val stride = bmp.rowBytes
        val bytes = ByteArray(stride * bh)
        for (y in y0..y1) {
            for (x in x0..x1) row[x] = img.isInk(x, y)
            val base = (y - y0) * s * stride
            for (x in x0..x1) if (row[x]) {
                val d = base + (x - x0) * s
                for (k in 0 until s) bytes[d + k] = 0xFF.toByte()
            }
            for (k in 1 until s) System.arraycopy(bytes, base, bytes, base + k * stride, stride)
        }
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return Mask(bmp, x0, y0)
    }

    companion object {
        /** A paint with every smoothing feature off. */
        fun crisp(): Paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            isDither = false
            style = Paint.Style.FILL
        }
    }
}
