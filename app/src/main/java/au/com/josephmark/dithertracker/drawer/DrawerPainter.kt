package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.stage.IconPainter
import au.com.josephmark.dithertracker.type.TextRenderer

/**
 * Draws one seat's open drawer through [DitherRenderer] / [TextRenderer], from [DrawerLayout] rects
 * (rotation via blit rotation, never `Canvas.rotate`). Allocation-free after the first draw of each
 * icon at the current glyph scale (see [prewarm]).
 *
 * Look:
 * - bar: `TAB` dither; on the left the commander button (solid ink card, imp icon in paper, T30);
 *   the tab's three dots on a paper backing in the middle (= close); on the right the page chevron
 *   (opaque, paper square): `›` with a next page, `‹` on the last page of several;
 * - body: `DRAWER` (sparse) dither;
 * - cell: paper card. Non-zero values get a 1-px ink frame and the value in `num-m` (a 3-digit value
 *   closes its tracking to fit, [DrawerLayout.valueTracking]); at 0 the card
 *   is frameless and the value hidden, so active counters stand out. Poison at 8+ is a solid ink
 *   card with paper content (nearly lethal).
 */
class DrawerPainter(private val r: DitherRenderer, private val text: TextRenderer, private val icons: IconPainter) {

    /** Draws everything in [seat]'s open drawer that intersects [clip]. */
    fun draw(l: DrawerLayout, c: DrawerController, seat: Int, players: List<Player>, clip: IRect) {
        if (!hit(l.drawer[seat], clip)) return
        val rot = l.play.rotation[seat]
        val st = c.state
        val player = players[seat]

        // Bar.
        r.fill(Role.TAB, l.bar[seat])
        r.rect(l.dotsBacking[seat], ink = false)
        for (i in 0 until 3) r.rect(l.dots[seat * 3 + i], ink = true)
        r.rect(l.cmdVis[seat], ink = true)
        icon(l.tk, Icon.COMMANDER, l.cmdIcon[seat], rot, invert = true, opaque = false)
        if (st.hasNext(seat)) icon(l.tk, Icon.CHEVRON_RIGHT, l.pageIcon[seat], rot, invert = false, opaque = true)
        else if (st.hasPrev(seat)) icon(l.tk, Icon.CHEVRON_LEFT, l.pageIcon[seat], rot, invert = false, opaque = true)

        // Body.
        r.fill(Role.DRAWER, l.body[seat])

        // Cells.
        val n = minOf(st.itemsOnPage(seat), l.perPage)
        for (slot in 0 until n) {
            val i = seat * DrawerZones.MAX_SLOTS + slot
            val vis = l.cellVis[i]
            if (!hit(vis, clip)) continue
            val item = c.itemAt(seat, slot)
            if (item < 0) continue
            val v = DrawerItems.value(item, player)
            val warn = DrawerItems.isWarning(item, v)
            if (v != 0) {
                // Active: a paper card with an ink frame (or a solid ink card when nearly lethal).
                r.rect(vis, ink = warn)
                if (!warn) r.frame(vis, 1, ink = true)
            } else {
                // At 0 there is no card: the drawer texture shows through and only the icon sits on
                // a small paper backing — quiet next to the active cards.
                halo(l.trayIcon[i], l.halo)
            }
            icon(l.tk, iconFor(item), l.trayIcon[i], rot, invert = warn, opaque = false)
            if (v != 0) {
                val vr = l.trayValue[i]
                // The reading-direction width of the value rect (rotation is only ever 0 or 180 here).
                val tr = l.valueTracking(v, vr.width)
                text.drawIntIn(l.numFont, v, vr.left, vr.top, vr.width, vr.height, l.tk, rot, invert = warn, tracking = tr)
            }
        }
    }

    /** Paper behind [rect] grown by [h] all round. */
    fun halo(rect: IRect, h: Int) = icons.halo(rect, h)

    /** Blits [icon] at glyph scale [k] with its footprint's top-left at [at]. */
    fun icon(k: Int, icon: Icon, at: IRect, rot: Int, invert: Boolean, opaque: Boolean) = icons.icon(k, icon, at, rot, invert, opaque)

    /** Builds every icon mask and ui glyph the drawer can draw at [l]'s scale (call off the draw path). */
    fun prewarm(l: DrawerLayout) {
        icons.prewarm(WARM_ICONS, l.tk)
        for (rot in intArrayOf(0, 180)) text.draw(l.numFont, "0123456789+-", 0, 0, l.tk, rot)
    }

    private fun hit(a: IRect, clip: IRect): Boolean =
        a.left < clip.right && clip.left < a.right && a.top < clip.bottom && clip.top < a.bottom

    companion object {
        fun iconFor(item: Int): Icon = DrawerItems.iconFor(item)

        private val WARM_ICONS = DrawerItems.TRAY_ICONS + arrayOf(
            Icon.COMMANDER, Icon.CHEVRON_LEFT, Icon.CHEVRON_RIGHT, Icon.LIFE, Icon.CLOSE,
        )
    }
}
