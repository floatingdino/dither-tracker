package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Role

/**
 * The centre diamond's shape, shared by everything that sits on it (the menu button, the commander
 * close X, the table menu, T09). Geometry lives in [PlayLayout]; this draws its fill and gives its
 * touch area.
 */
object Diamond {
    /** Fills the stepped diamond with the `MENU` dither. */
    fun fill(r: DitherRenderer, l: PlayLayout) {
        val cx = l.diamondCx
        val cy = l.diamondCy
        for (dy in -l.diamondHalf..l.diamondHalf) {
            val hw = l.diamondRowHalf(dy)
            r.fill(Role.MENU, cx - hw, cy + dy, 2 * hw + 1, 1)
        }
    }

    /** Bounding rect of the diamond's touch area (refine with [PlayLayout.inDiamondHit]). */
    fun hitBounds(l: PlayLayout, out: IRect): IRect {
        val lim = l.diamondHalf + l.chrome.diamondHitPad
        return out.set(l.diamondCx - lim, l.diamondCy - lim, l.diamondCx + lim + 1, l.diamondCy + lim + 1)
    }
}
