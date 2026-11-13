package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Role
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
 * - cell: paper card. Non-zero values get a 1-px ink frame and the value in `num-m`; at 0 the card
 *   is frameless and the value hidden, so active counters stand out. Poison at 8+ is a solid ink
 *   card with paper content (nearly lethal).
 */
class DrawerPainter(private val r: DitherRenderer, private val text: TextRenderer) {

    private var iconK = 1
    private val scaled = arrayOfNulls<MonoImage>(Icon.entries.size)

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
                text.drawIntIn(l.numFont, v, vr.left, vr.top, vr.width, vr.height, l.tk, rot, invert = warn)
            }
        }
    }

    /** Paper behind [rect] grown by [h] all round. */
    fun halo(rect: IRect, h: Int) {
        r.rect(rect.left - h, rect.top - h, rect.width + 2 * h, rect.height + 2 * h, ink = false)
    }

    /** Blits [icon] at glyph scale [k] with its footprint's top-left at [at]. */
    fun icon(k: Int, icon: Icon, at: IRect, rot: Int, invert: Boolean, opaque: Boolean) {
        r.blit(scaledIcon(icon, k), at.left, at.top, rot, invert, opaque)
    }

    /** Builds every icon mask and ui glyph the drawer can draw at [l]'s scale (call off the draw path). */
    fun prewarm(l: DrawerLayout) {
        for (ic in WARM_ICONS) r.blit(scaledIcon(ic, l.tk), 0, 0)
        for (rot in intArrayOf(0, 180)) text.draw(l.numFont, "0123456789+-", 0, 0, l.tk, rot)
    }

    fun scaledIcon(icon: Icon, k: Int): MonoImage {
        if (k == 1) return icon
        if (k != iconK) {
            scaled.fill(null); iconK = k
        }
        return scaled[icon.ordinal] ?: Scaled(icon, k).also { scaled[icon.ordinal] = it }
    }

    private fun hit(a: IRect, clip: IRect): Boolean =
        a.left < clip.right && clip.left < a.right && a.top < clip.bottom && clip.top < a.bottom

    /** An icon enlarged k× (nearest neighbour): a stable instance so the renderer's mask cache hits. */
    private class Scaled(private val src: MonoImage, private val k: Int) : MonoImage {
        override val width: Int = src.width * k
        override val height: Int = src.height * k
        override fun isInk(x: Int, y: Int): Boolean = src.isInk(x / k, y / k)
    }

    companion object {
        private val COUNTER_ICONS = arrayOf(
            Icon.POISON, Icon.ENERGY, Icon.EXPERIENCE, Icon.RADS, Icon.STORM, Icon.TICKETS, Icon.GENERIC_COUNTER,
        )

        init {
            check(COUNTER_ICONS.size == Counter.entries.size)
        }

        fun iconFor(item: Int): Icon = when (DrawerItems.kind(item)) {
            DrawerItems.COUNTER -> COUNTER_ICONS[DrawerItems.arg(item)]
            DrawerItems.TAX -> if (DrawerItems.arg(item) == Commander.PARTNER) Icon.PARTNER else Icon.COMMANDER_TAX
            else -> Icon.COMMANDER
        }

        private val WARM_ICONS = COUNTER_ICONS + arrayOf(
            Icon.COMMANDER_TAX, Icon.PARTNER, Icon.COMMANDER, Icon.CHEVRON_LEFT, Icon.CHEVRON_RIGHT, Icon.LIFE, Icon.CLOSE,
        )
    }
}
