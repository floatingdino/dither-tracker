package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.dither.DitherRenderer
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.dither.MonoImage
import au.com.josephmark.dithertracker.dither.Role
import au.com.josephmark.dithertracker.type.TextLayout
import au.com.josephmark.dithertracker.type.TextRenderer

/**
 * Draws one seat's open drawer through [DitherRenderer] / [TextRenderer], from [DrawerLayout] rects
 * (rotation via blit rotation, never `Canvas.rotate`). Allocation-free after the first draw of each
 * icon at the current glyph scale (see [prewarm]).
 *
 * Look:
 * - bar: `TAB` dither, the tab's three dots on a paper backing in the middle (= close), chevron
 *   icons (opaque, paper square) at the ends only when there is a page that way;
 * - body: `DRAWER` (sparse) dither;
 * - header: solid ink card, `ui` caps in paper — `COMMANDER DAMAGE` on the tray, `‹ COUNTERS` on
 *   the commander view (back);
 * - cell: paper card. Non-zero values get a 1-px ink frame and the value in `num-m`; at 0 the card
 *   is frameless and the value hidden, so active counters stand out. Lethal commander damage (≥ 21)
 *   is a solid ink card with paper content.
 */
class DrawerPainter(private val r: DitherRenderer, private val text: TextRenderer) {

    private val label = Label()
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
        if (st.hasPrev(seat)) icon(l, Icon.CHEVRON_LEFT, l.chevron[seat * 2], rot, invert = false, opaque = true)
        if (st.hasNext(seat)) icon(l, Icon.CHEVRON_RIGHT, l.chevron[seat * 2 + 1], rot, invert = false, opaque = true)

        // Body.
        r.fill(Role.DRAWER, l.body[seat])

        // Header button.
        val hv = l.headerVis[seat]
        if (hit(hv, clip)) {
            r.rect(hv, ink = true)
            if (st.mode(seat) == DrawerState.TRAY) {
                labelText(l, if (l.headerShort) CMD_DAMAGE_SHORT else CMD_DAMAGE, l.headerLabel[seat], rot, invert = true, align = TextLayout.CENTRE)
            } else {
                icon(l, Icon.CHEVRON_LEFT, l.headerIcon[seat], rot, invert = true, opaque = false)
                labelText(l, if (l.headerShort) BACK_SHORT else BACK, l.headerLabelAfterIcon[seat], rot, invert = true, align = TextLayout.CENTRE)
            }
        }

        // Cells.
        val n = minOf(st.itemsOnPage(seat), l.perPage)
        for (slot in 0 until n) {
            val i = seat * DrawerZones.MAX_SLOTS + slot
            val vis = l.cellVis[i]
            if (!hit(vis, clip)) continue
            val item = c.itemAt(seat, slot)
            if (item < 0) continue
            val v = DrawerItems.value(item, player, players)
            val lethal = DrawerItems.isLethal(item, v)
            if (v != 0 || lethal) {
                // Active: a paper card with an ink frame (or a solid ink card when lethal).
                r.rect(vis, ink = lethal)
                if (!lethal) r.frame(vis, 1, ink = true)
            }
            // At 0 there is no card: the drawer texture shows through and only the icon (and the
            // damage label) sit on small paper backings — quiet next to the active cards.
            if (DrawerItems.kind(item) == DrawerItems.DAMAGE) {
                val src = players[DrawerItems.damageSource(item)]
                label.set(src.name, l.labelChars)
                val lr = l.dmgLabel[i]
                if (v == 0) backing(l.ui.widthOf(label.length, l.tk), lr, rot, l)
                text.drawIn(l.ui, label, lr.left, lr.top, lr.width, lr.height, l.tk, rot, invert = lethal, align = TextLayout.START)
                val ic = if (DrawerItems.damageSlot(item) == Commander.PARTNER) Icon.PARTNER else Icon.COMMANDER
                if (v == 0) halo(l.dmgIcon[i], l)
                icon(l, ic, l.dmgIcon[i], rot, invert = lethal, opaque = false)
                if (v != 0) value(l, v, l.dmgValue[i], rot, lethal)
            } else {
                if (v == 0) halo(l.trayIcon[i], l)
                icon(l, iconFor(item), l.trayIcon[i], rot, invert = false, opaque = false)
                if (v != 0) value(l, v, l.trayValue[i], rot, false)
            }
        }
    }

    /** Paper behind [r] grown by the layout's halo. */
    private fun halo(rect: IRect, l: DrawerLayout) {
        val h = l.halo
        r.rect(rect.left - h, rect.top - h, rect.width + 2 * h, rect.height + 2 * h, ink = false)
    }

    /** Paper behind a START-aligned label of width [w] drawn in [lr] (player-frame start = screen right at 180°). */
    private fun backing(w: Int, lr: IRect, rot: Int, l: DrawerLayout) {
        val h = l.halo
        val x = if (rot == 180) lr.right - w else lr.left
        r.rect(x - h, lr.top - h, w + 2 * h, lr.height + 2 * h, ink = false)
    }

    private fun value(l: DrawerLayout, v: Int, rect: IRect, rot: Int, invert: Boolean) {
        text.drawIntIn(l.numFont, v, rect.left, rect.top, rect.width, rect.height, l.tk, rot, invert = invert)
    }

    private fun labelText(l: DrawerLayout, s: String, rect: IRect, rot: Int, invert: Boolean, align: Int) {
        text.drawIn(l.ui, s, rect.left, rect.top, rect.width, rect.height, l.tk, rot, invert = invert, align = align)
    }

    private fun icon(l: DrawerLayout, icon: Icon, at: IRect, rot: Int, invert: Boolean, opaque: Boolean) {
        r.blit(scaledIcon(icon, l.tk), at.left, at.top, rot, invert, opaque)
    }

    /** Builds every icon mask and ui glyph the drawer can draw at [l]'s scale (call off the draw path). */
    fun prewarm(l: DrawerLayout) {
        for (ic in WARM_ICONS) r.blit(scaledIcon(ic, l.tk), 0, 0)
        text.draw(l.ui, WARM_UI, 0, 0, l.tk)
        text.draw(l.numFont, "0123456789", 0, 0, l.tk)
    }

    private fun scaledIcon(icon: Icon, k: Int): MonoImage {
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

    /** Reusable upper-cased, truncated view of a name (no allocation per draw). */
    private class Label : CharSequence {
        private var src: String = ""
        private var n = 0
        fun set(s: String, max: Int) { src = s; n = minOf(s.length, max) }
        override val length: Int get() = n
        override fun get(index: Int): Char = src[index].uppercaseChar()
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = toString().subSequence(startIndex, endIndex)
        override fun toString(): String = src.substring(0, n).uppercase()
    }

    companion object {
        const val CMD_DAMAGE = "COMMANDER DAMAGE"
        const val BACK = "COUNTERS"
        const val CMD_DAMAGE_SHORT = "CMD DMG"
        const val BACK_SHORT = "BACK"

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
            Icon.COMMANDER_TAX, Icon.PARTNER, Icon.COMMANDER, Icon.CHEVRON_LEFT, Icon.CHEVRON_RIGHT,
        )
        private const val WARM_UI = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -'.&"
    }
}
