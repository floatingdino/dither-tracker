package au.com.josephmark.dithertracker.chrome

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.Diamond
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.ZoneSink

/**
 * The centre diamond with the menu glyph (three bars on a paper backing). Until the table menu (T09)
 * exists, its touch area is a *block*: a touch there does nothing and reaches no tile below. T09
 * replaces the block with a zone (`zonesPerSeat = 1`, `z.add(0, …)`, feedback = the diamond) or
 * replaces this component with its own.
 *
 * [Kind.CHROME]: commander mode's cover hides it (the mode draws its close X on the same spot).
 */
class MenuDiamond : Component(Layer.SCREEN, Kind.CHROME) {

    private var l: PlayLayout? = null
    private val hit = IRect()

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        Diamond.hitBounds(play, hit)
    }

    override fun draw(f: Frame, seat: Int) {
        val l = l ?: return
        if (!f.hit(l.diamond)) return
        Diamond.fill(f.r, l)
        f.r.rect(l.menuBarsBacking, ink = false)
        for (b in l.menuBars) f.r.rect(b, ink = true)
    }

    override fun bounds(out: IRect): Boolean {
        val l = l ?: return false
        out.set(l.diamond.left, l.diamond.top, l.diamond.right, l.diamond.bottom)
        return true
    }

    override fun zones(seat: Int, z: ZoneSink) {
        if (l != null) z.block(hit)
    }

    override fun contains(seat: Int, local: Int, gx: Int, gy: Int): Boolean = l?.inDiamondHit(gx, gy) == true
}
