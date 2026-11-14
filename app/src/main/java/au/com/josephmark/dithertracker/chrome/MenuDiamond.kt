package au.com.josephmark.dithertracker.chrome

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.HaloGlyph
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
 * The centre diamond with the menu glyph (three bars on their paper cut-out, T41): the table menu's button.
 * One zone ([OPEN], the diamond's shape grown by its hit pad); a tap sends [Events.MENU_OPEN] and the
 * menu (`menu.MenuOverlay`, an overlay drawn over this spot) opens. Press feedback inverts the diamond
 * — its own stepped shape, never the square round it (T51, Sam: the press "painted a larger square"
 * over the dividers and the tiles' corners): [draw] pre-inverts the square's corners outside the
 * shape, so the host's inversion of [feedback]'s rect leaves exactly the diamond inverted.
 *
 * [Kind.CHROME]: commander mode's cover hides it (the mode draws its close X on the same spot).
 */
class MenuDiamond : Component(Layer.SCREEN, Kind.CHROME, zonesPerSeat = 1) {

    private var l: PlayLayout? = null
    private val hit = IRect()
    private var bars: HaloGlyph? = null

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        Diamond.hitBounds(play, hit)
        // The bars sit [PlayLayout.halo] px inside their cut-out; the 3-px gaps between them would leave
        // 1-px dither slivers, so the cut-out closes into a tight plate around the three bars.
        bars = HaloGlyph.of(play.menuBars, 0, 3, play.halo)
    }

    override fun prewarm(f: Frame) {
        bars?.prewarm(f.r)
    }

    override fun draw(f: Frame, seat: Int) {
        val l = l ?: return
        if (!f.hit(l.diamond)) return
        Diamond.fill(f.r, l)
        bars?.draw(f.r)
        if (stage.isPressed(this, Stage.SCREEN, OPEN)) compensate(f.r, l)
    }

    /** Inverts [PlayLayout.diamond]'s rows outside the stepped shape (no-op without in-place inversion: API < 29). */
    private fun compensate(r: DitherRenderer, l: PlayLayout) {
        if (!r.invertsInPlace) return
        val box = l.diamond
        for (y in box.top until box.bottom) {
            val hw = l.diamondRowHalf(y - l.diamondCy)
            if (hw < 0) { r.invertRect(box.left, y, box.width, 1); continue }
            val x0 = l.diamondCx - hw
            val x1 = l.diamondCx + hw + 1
            if (x0 > box.left) r.invertRect(box.left, y, x0 - box.left, 1)
            if (x1 < box.right) r.invertRect(x1, y, box.right - x1, 1)
        }
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
