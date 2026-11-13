package au.com.josephmark.dithertracker.toggles

import au.com.josephmark.dithertracker.core.DayNight
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.TileSlots
import au.com.josephmark.dithertracker.stage.Component
import au.com.josephmark.dithertracker.stage.Frame
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer

/**
 * The table's day/night on the centre diamond (T14): the one spot every seat can see and that turns
 * with nobody. Unset leaves the menu diamond as it is (`MENU` dither, three bars). Once day/night has
 * started the diamond itself shows it, in place of the bars:
 * - **day**: a paper diamond with a 1-px ink staircase rim and the `day` sun in ink;
 * - **night**: a solid ink diamond with the `night` moon in paper.
 * Both icons sit on the diamond's centre at the chrome glyph scale, drawn unrotated (the sun is
 * symmetric; a moon has no up).
 *
 * Drawn over `chrome.MenuDiamond` (registered right after it, same layer), which keeps the diamond's
 * zone: a tap still opens the table menu and its press inverts this too. Display only, [Kind.CHROME]:
 * hidden with the diamond in commander mode and under the menu. Set from any seat's drawer (the
 * shared day/night cell, `drawer.DrawerItems.DAY_NIGHT`); a change redraws just the diamond.
 */
class DayNightMark : Component(Layer.SCREEN, Kind.CHROME) {

    private var l: PlayLayout? = null
    private val icon = IRect()

    override fun onLayout(play: PlayLayout, tiles: TileSlots) {
        l = play
        val s = PlayLayout.ICON * play.tk
        icon.setXywh(play.diamondCx - s / 2, play.diamondCy - s / 2, s, s)
    }

    override fun prewarm(f: Frame) {
        val l = l ?: return
        f.icons.prewarm(ICONS, l.tk)
    }

    override fun draw(f: Frame, seat: Int) {
        val l = l ?: return
        val dn = stage.session.state.dayNight
        if (dn == DayNight.UNSET || !f.hit(l.diamond)) return
        val night = dn == DayNight.NIGHT
        fillDiamond(f, l, 0, ink = true)
        if (!night) fillDiamond(f, l, RIM, ink = false)
        f.icons.icon(l.tk, if (night) Icon.NIGHT else Icon.DAY, icon, 0, invert = night, opaque = false)
    }

    /** The diamond's stepped shape shrunk by [inset] px on every edge, solid. */
    private fun fillDiamond(f: Frame, l: PlayLayout, inset: Int, ink: Boolean) {
        val cx = l.diamondCx
        val cy = l.diamondCy
        val h = l.diamondHalf - inset
        for (dy in -h..h) {
            val hw = l.diamondRowHalf(dy) - inset
            if (hw < 0) continue
            f.r.rect(cx - hw, cy + dy, 2 * hw + 1, 1, ink)
        }
    }

    override fun bounds(out: IRect): Boolean {
        val l = l ?: return false
        out.set(l.diamond.left, l.diamond.top, l.diamond.right, l.diamond.bottom)
        return true
    }

    override fun onStateChanged(old: GameState, new: GameState, origin: Int) {
        val l = l ?: return
        if (old.dayNight != new.dayNight) invalidate(l.diamond)
    }

    companion object {
        /** Day: the rim of ink left round the paper diamond. */
        const val RIM = 1

        val ICONS = arrayOf(Icon.DAY, Icon.NIGHT)
    }
}
