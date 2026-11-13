package au.com.josephmark.dithertracker.chrome

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.Diamond
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.Stage
import au.com.josephmark.dithertracker.stage.ZoneSink

/**
 * The centre diamond with the menu glyph (three bars on a paper backing): the table menu's button.
 * One zone ([OPEN], the diamond's shape grown by its hit pad); a tap sends [Events.MENU_OPEN] and the
 * menu (`menu.MenuOverlay`, an overlay drawn over this spot) opens. Press feedback inverts the diamond.
 *
 * [Kind.CHROME]: commander mode's cover hides it (the mode draws its close X on the same spot).
 */
class MenuDiamond : Component(Layer.SCREEN, Kind.CHROME, zonesPerSeat = 1) {

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
        if (l != null) z.add(OPEN, hit)
    }

    override fun contains(seat: Int, local: Int, gx: Int, gy: Int): Boolean = l?.inDiamondHit(gx, gy) == true

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val l = l ?: return false
        out.set(l.diamond.left, l.diamond.top, l.diamond.right, l.diamond.bottom)
        return true
    }

    override fun onTap(seat: Int, local: Int) {
        stage.send(Events.MENU_OPEN, Stage.SCREEN)
    }

    companion object {
        const val OPEN = 0
    }
}
