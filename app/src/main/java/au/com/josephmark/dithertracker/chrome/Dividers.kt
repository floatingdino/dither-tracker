package au.com.josephmark.dithertracker.chrome

import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer

/** The dotted dividers between the four panels (1 dither px, 1 on / 1 off). Display only. */
class Dividers : Component(Layer.BACKDROP, Kind.BACKDROP) {

    private var l: PlayLayout? = null

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
    }

    override fun draw(f: Frame, seat: Int) {
        val l = l ?: return
        if (f.hit(l.dividerV)) f.r.dottedV(l.dividerV.left, l.dividerV.top, l.dividerV.height)
        if (f.hit(l.dividerH)) f.r.dottedH(l.dividerH.left, l.dividerH.top, l.dividerH.width)
    }
}
