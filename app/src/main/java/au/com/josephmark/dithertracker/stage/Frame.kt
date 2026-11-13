package au.com.josephmark.dithertracker.stage

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.type.TextRenderer

/**
 * What a component draws with, for one `onDraw`: the renderer, the text renderer and the shared icon
 * painter (all in dither px, root coordinates), plus the dirty [clip] for culling. The host view owns
 * one Frame and reuses it every draw.
 *
 * [invert] is the host's press feedback (SPEC rule 6); components never invert for presses themselves.
 */
abstract class Frame {
    lateinit var r: DitherRenderer
    lateinit var text: TextRenderer
    lateinit var icons: IconPainter

    /** Dither-px rect covering the canvas clip. */
    val clip = IRect()

    /** True when [rect] intersects the clip: skip drawing it otherwise. */
    fun hit(rect: IRect): Boolean =
        rect.left < clip.right && clip.left < rect.right && rect.top < clip.bottom && clip.top < rect.bottom

    /**
     * Inverts [rect] (a pressed zone of a [layer] component, drawn up to that layer). Where in-place
     * inversion is unavailable (API < 29) the host redraws everything up to [layer] inverted inside
     * [rect] ([Stage.drawUpTo]).
     */
    abstract fun invert(rect: IRect, layer: Int)
}
