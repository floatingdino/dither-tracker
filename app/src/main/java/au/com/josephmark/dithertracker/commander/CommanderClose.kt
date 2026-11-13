package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.play.Diamond
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.ZoneSink

/**
 * The centre diamond while commander-damage mode is on: the diamond fill with a close X (on its 1-px
 * paper cut-out, T41) where the menu bars were. One zone (local 0, the diamond's shape); a tap leaves
 * the mode. Part of the commander feature: shares [tile]'s [CommanderMode]. The mode's cover hides
 * the menu diamond ([Kind.CHROME]) meanwhile; this is [Kind.MODE].
 */
class CommanderClose(private val tile: CommanderTile) : Component(Layer.SCREEN, Kind.MODE, zonesPerSeat = 1) {

    private var l: PlayLayout? = null
    private val hit = IRect()

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        Diamond.hitBounds(play, hit)
    }

    override fun draw(f: Frame, seat: Int) {
        if (!tile.mode.active) return
        val l = l ?: return
        val c = tile.layout ?: return
        if (!f.hit(l.diamond)) return
        Diamond.fill(f.r, l)
        f.icons.icon(c.tk, Icon.CLOSE, c.closeIcon, 0, invert = false, opaque = false, halo = true)
    }

    override fun bounds(out: IRect): Boolean {
        val l = l ?: return false
        out.set(l.diamond.left, l.diamond.top, l.diamond.right, l.diamond.bottom)
        return true
    }

    override fun zones(seat: Int, z: ZoneSink) {
        if (tile.mode.active && l != null) z.add(0, hit)
    }

    override fun contains(seat: Int, local: Int, gx: Int, gy: Int): Boolean = l?.inDiamondHit(gx, gy) == true

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val c = tile.layout ?: return false
        out.set(c.closeIcon.left, c.closeIcon.top, c.closeIcon.right, c.closeIcon.bottom)
        return true
    }

    override fun onTap(seat: Int, local: Int) {
        tile.mode.close()
    }
}
