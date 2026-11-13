package au.com.josephmark.dithertracker.chrome

import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer

/**
 * The dotted dividers between the panels (1 dither px, 1 on / 1 off, globally phased so segments
 * meet cleanly): every one in [PlayLayout.dividers], whatever the player count (T11). Display only.
 */
class Dividers : Component(Layer.BACKDROP, Kind.BACKDROP) {

    private var l: PlayLayout? = null

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
    }

    override fun draw(f: Frame, seat: Int) {
        val l = l ?: return
        val ds = l.dividers
        for (i in ds.indices) {
            val d = ds[i]
            if (!f.hit(d)) continue
            if (d.width == 1) f.r.dottedV(d.left, d.top, d.height) else f.r.dottedH(d.left, d.top, d.width)
        }
    }
}
