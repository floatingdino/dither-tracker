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

    // Scaled 1-bit masks (ALPHA_8, tinted by paint colour at draw time) per image at current S.
    private val masks = HashMap<MonoImage, Bitmap>()

    /** Sets the grid; drops caches when the scale changes, re-anchors shaders when margins change. */
    override fun setGrid(newGrid: DitherGrid) {
        val old = grid
        super.setGrid(newGrid)
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
        c.drawBitmap(mask(mask), grid.toDeviceX(x).toFloat(), grid.toDeviceY(y).toFloat(), paint)
    }

    // ---- effect masks (T28e) --------------------------------------------------------------

    /**
     * One drawBitmap of the cached ALPHA_8 mask: [MaskOp.INVERT] with blend DIFFERENCE (like
     * [invertRect]); below API 29 the [underlay] goes into a layer clipped to the mask (DST_IN).
     * No allocations after the first call per (image, S) and (tile, colour, S).
     */
    override fun drawMask(img: MonoImage, x: Int, y: Int, op: MaskOp, tile: Tile, underlay: Runnable?) {
        val c = canvas!!
        val bmp = mask(img)
        val dx = grid.toDeviceX(x).toFloat()
        val dy = grid.toDeviceY(y).toFloat()
        val full = tile.width * tile.height
        val paint: Paint = when (op) {
            MaskOp.INVERT -> {
                if (!canInvertInPlace) {
                    if (underlay == null) return
                    val s = grid.scale
                    c.saveLayer(dx, dy, dx + img.width * s, dy + img.height * s, null)
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
        /** A paint with every smoothing feature off. */
        fun crisp(): Paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            isDither = false
            style = Paint.Style.FILL
        }
    }
}
