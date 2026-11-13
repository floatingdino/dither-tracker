package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.dither.HaloGlyph
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import au.com.josephmark.dithertracker.stage.ZoneSink

/**
 * The closed drawer's tab on the player's near edge (`TAB` dither, three dots on their paper cut-out, T41). In
 * [Layer.TILE_CHROME], so it is drawn over the life halves' press inversion and wins their touches.
 * One zone (local 0) while the seat's drawer is closed: a tap opens it. Part of the drawer feature:
 * it shares [drawer]'s controller. [Kind.CHROME]: commander mode hides it.
 */
class DrawerTab(private val drawer: DrawerComponent) : Component(Layer.TILE_CHROME, Kind.CHROME, zonesPerSeat = 1) {

    init {
        drawer.tab = this
    }

    private var l: PlayLayout? = null
    private var dots: Array<HaloGlyph> = emptyArray()

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        dots = Array(play.playerCount) { HaloGlyph.of(play.tabDots, it * 3, 3, play.halo) }
    }

    override fun prewarm(f: Frame) {
        for (d in dots) d.prewarm(f.r)
    }

    override fun draw(f: Frame, seat: Int) {
        val l = l ?: return
        if (drawer.state.isOpen(seat) || !f.hit(l.tab[seat])) return
        f.r.fill(Role.TAB, l.tab[seat])
        dots[seat].draw(f.r)
    }

    override fun zones(seat: Int, z: ZoneSink) {
        val l = l ?: return
        if (!drawer.state.isOpen(seat)) z.add(0, l.tabHit[seat])
    }

    override fun feedback(seat: Int, local: Int, out: IRect): Boolean {
        val l = l ?: return false
        val d = drawer.layout
        val r = if (drawer.state.isOpen(seat) && d != null) d.barSeg[seat * 3 + 1] else l.tab[seat]
        out.set(r.left, r.top, r.right, r.bottom)
        return true
    }

    override fun onTap(seat: Int, local: Int) {
        drawer.controller.onTap(seat, DrawerZones.TAB)
    }
}
