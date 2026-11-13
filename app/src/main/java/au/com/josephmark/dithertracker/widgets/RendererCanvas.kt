package au.com.josephmark.dithertracker.widgets

import android.graphics.Canvas
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.TextRenderer

/**
 * [WidgetCanvas] over the app's [DitherRenderer] and [TextRenderer]: one forwarding call per
 * primitive, no allocations. Call [begin] each frame with the view's canvas (it also calls
 * `renderer.begin`); the canvas is only touched directly for the API < 29 inverted-clip path.
 */
class RendererCanvas(val renderer: DitherRenderer, val textRenderer: TextRenderer) : WidgetCanvas {

    private var canvas: Canvas? = null
    private var clipping = false
    private val d = IRect()
    private val dev = IRect()

    fun begin(canvas: Canvas) {
        this.canvas = canvas
        renderer.begin(canvas)
    }

    override val canInvertInPlace: Boolean get() = DitherRenderer.canInvertInPlace

    override fun fill(role: Role, x: Int, y: Int, w: Int, h: Int) = renderer.fill(role, x, y, w, h)

    override fun rect(x: Int, y: Int, w: Int, h: Int, ink: Boolean) = renderer.rect(x, y, w, h, ink)

    override fun frame(x: Int, y: Int, w: Int, h: Int, t: Int, ink: Boolean) = renderer.frame(x, y, w, h, t, ink)

    override fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, invert: Boolean, opaque: Boolean) =
        renderer.blit(img, x, y, rotation, invert, opaque)

    override fun text(font: Font, text: CharSequence, x: Int, y: Int, k: Int, rotation: Int, invert: Boolean, opaque: Boolean, pad: Int, halo: Boolean) =
        textRenderer.draw(font, text, x, y, k, rotation, invert, opaque && !halo, pad, halo = halo)

    override fun invert(x: Int, y: Int, w: Int, h: Int) = renderer.invertRect(x, y, w, h)

    override fun beginInvertedClip(x: Int, y: Int, w: Int, h: Int) {
        val c = canvas ?: return
        renderer.grid.toDevice(d.setXywh(x, y, w, h), dev)
        c.save()
        c.clipRect(dev.left, dev.top, dev.right, dev.bottom)
        renderer.inverted = !renderer.inverted
        clipping = true
    }

    override fun endInvertedClip() {
        if (!clipping) return
        clipping = false
        renderer.inverted = !renderer.inverted
        canvas?.restore()
    }
}

private var galleryGrid: DitherGrid? = null
private var galleryRotation = 0
private var galleryDpi = 0
private var galleryRenderer: DitherRenderer? = null
private var gallery: WidgetGallery? = null
private var galleryCanvas: RendererCanvas? = null
private var galleryPainter: WidgetPainter? = null
private val galleryScreen = IRect()

/**
 * Draws [WidgetGallery] (every widget once, in a panel centred on the screen and facing [rotation])
 * for an on-device visual check. Call from a view's `onDraw` after `renderer.setGrid(grid)` (and
 * usually `renderer.clear()`) with that view's [canvas]. Builds and caches the gallery per
 * (grid, rotation, renderer); later frames do not allocate. Debug aid only: not wired into the app.
 */
fun drawWidgetGallery(
    canvas: Canvas, renderer: DitherRenderer, text: TextRenderer, grid: DitherGrid,
    rotation: Int = 0, densityDpi: Int = WidgetMetrics.NOMAD_DPI,
) {
    var g = gallery
    var c = galleryCanvas
    if (g == null || c == null || grid != galleryGrid || rotation != galleryRotation || densityDpi != galleryDpi || renderer !== galleryRenderer) {
        g = WidgetGallery(WidgetMetrics(grid.scale, densityDpi))
        g.layout(galleryScreen.set(0, 0, grid.width, grid.height), rotation)
        c = RendererCanvas(renderer, text)
        gallery = g; galleryCanvas = c; galleryPainter = WidgetPainter(c)
        galleryGrid = grid; galleryRotation = rotation; galleryDpi = densityDpi; galleryRenderer = renderer
    }
    c.begin(canvas)
    g.draw(galleryPainter!!)
}
