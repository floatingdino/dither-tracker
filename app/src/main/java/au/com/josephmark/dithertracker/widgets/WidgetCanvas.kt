package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.Font

/**
 * The 1-bit drawing surface [WidgetPainter] draws on, in *screen* dither px — the subset of
 * `DitherRenderer` + `TextRenderer` the widgets need. The app uses [RendererCanvas] (a thin forwarder);
 * JVM tests use a raster implementation, so painting is checked pixel by pixel without a device.
 *
 * Semantics are exactly `DitherRenderer`'s: fills are anchored to the global pattern phase; `ink`
 * means black (white when the surface is inverted); blits/text take a clockwise rotation and draw
 * `invert`ed / `opaque` like `DitherRenderer.blit` and `TextRenderer.draw`.
 */
interface WidgetCanvas {
    /** Whether [invert] works; otherwise pressed widgets are redrawn inside [beginInvertedClip]. */
    val canInvertInPlace: Boolean

    fun fill(role: Role, x: Int, y: Int, w: Int, h: Int)

    fun rect(x: Int, y: Int, w: Int, h: Int, ink: Boolean)

    /** Outline of thickness [t] inside the rect. */
    fun frame(x: Int, y: Int, w: Int, h: Int, t: Int, ink: Boolean)

    fun blit(img: MonoImage, x: Int, y: Int, rotation: Int, invert: Boolean, opaque: Boolean)

    /** Text with its footprint's top-left at (x, y) (see `TextRenderer.draw`). */
    fun text(font: Font, text: CharSequence, x: Int, y: Int, k: Int, rotation: Int, invert: Boolean, opaque: Boolean, pad: Int)

    /** Inverts what is drawn in the rect (press feedback). */
    fun invert(x: Int, y: Int, w: Int, h: Int)

    /** Clips to the rect and swaps ink/paper until [endInvertedClip] (the API < 29 press path). */
    fun beginInvertedClip(x: Int, y: Int, w: Int, h: Int)

    fun endInvertedClip()
}
