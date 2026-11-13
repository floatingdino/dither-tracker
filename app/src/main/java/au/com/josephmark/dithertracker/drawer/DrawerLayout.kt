package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts

/**
 * Pure layout maths for the per-player drawer (T08). No Android types; JVM-tested. All values are
 * dither px; everything is laid out once in the *player's* frame (x to the player's right, y towards
 * the player, origin at the panel's far-left corner) and mapped to the screen with
 * [PlayLayout.mapRect], so a top-row drawer is the exact 180° image of an upright one.
 *
 * Player frame of an open panel, top (far edge) to bottom (near edge):
 * ```
 *  0          ┌──────────────────────────────┐
 *             │ readout (num-m, [readoutK])  │  upper area: life total and ± still live here
 *             │ −   number ([numberK])    +  │  (left half = −, right half = +, hold works)
 *  upper      ├────┬───────── ••• ────────┬──┤  bar (TAB dither): ‹ prev · close · next ›
 *  bodyTop    │ COMMANDER DAMAGE │ cell │ cell │  body (DRAWER dither): [cols] × [rows] grid,
 *             │ cell │ cell │ cell │ cell     │  header button spans [headerSpan] columns
 *  panelH     └──────────────────────────────┘
 * ```
 * The drawer covers the lower part of the panel instead of overlaying the number: the number keeps
 * its closed-state glyph scale where the arithmetic allows (it does at S = 2..6 on the Nomad), and
 * moves up with the readout (at a smaller scale) above it. Choice order, by maths:
 * `(rows = 2, k = kMax)`, `(1, kMax)`, `(2, kMax − 1)`, … with the largest readout scale that fits;
 * fits means `V_MIN + readoutH + gap + numberH + barHitPad ≤ upper`, where the bar hit extension
 * ([PlayLayout.Chrome.tabHitPad]) stays clear of the number.
 *
 * Touch targets: every hit rect (each cell half, header, bar segments incl. their extension) is at
 * least [target] dither px (≈ 9 mm, [MIN_TOUCH_DEVICE_PX]) in its short dimension: columns are
 * `≥ 2 · target` wide and rows `≥ target` tall. The cell hit grid tiles the body exactly; the
 * visible card is the hit rect inset by [inset].
 */
class DrawerLayout(val play: PlayLayout) {

    val scale: Int = play.grid.scale

    /** Glyph/icon scale for drawer content (keeps physical size ≈ S = 4's). */
    val tk: Int = maxOf(1, PlayLayout.Chrome.size(1, scale))

    /** Minimum hit size in dither px (ceil of [MIN_TOUCH_DEVICE_PX] / S). */
    val target: Int = (MIN_TOUCH_DEVICE_PX + scale - 1) / scale

    val ui: Font = Fonts.UI
    val numFont: Font = Fonts.NUM_M

    val iconSize: Int = ICON * tk
    val inset: Int = PlayLayout.Chrome.size(INSET, scale)
    val pad: Int = PlayLayout.Chrome.size(PAD, scale)
    val labelGap: Int = PlayLayout.Chrome.size(LABEL_GAP, scale)
    val iconGap: Int = PlayLayout.Chrome.size(ICON_GAP, scale)
    val readoutGap: Int = PlayLayout.Chrome.size(READOUT_GAP, scale)

    /** Paper rim around an icon/label drawn straight on the drawer texture (zero-valued cells). */
    val halo: Int = PlayLayout.Chrome.size(HALO, scale)

    /** Bar height: the tab's height, but never shorter than a chevron icon plus a 1-px rim. */
    val barH: Int = maxOf(play.chrome.tabH, iconSize + 2)
    val barHitPad: Int = play.chrome.tabHitPad

    /** Tallest cell content: the commander-damage cell (label cap band, gap, icon row). */
    val contentH: Int = ui.capHeight * tk + labelGap + iconSize

    /** Widest tray-cell content: icon, gap, three digits of num-m. */
    val contentW: Int = iconSize + iconGap + numFont.widthOf(3, tk)

    val rowH: Int = maxOf(target, contentH + 2 * pad + 2 * inset)
    val cols: Int
    val rows: Int
    val headerSpan: Int

    /** Cells per page (grid minus the header button). */
    val perPage: Int get() = cols * rows - headerSpan

    /** Drawer height (bar + body) and the upper area left for the life total, player frame. */
    val drawerH: Int
    val upperH: Int

    /** Glyph scale of the life total / readout while open. */
    val numberK: Int
    val readoutK: Int

    /** Player-frame tops of the number slot and readout slot while open. */
    val numberTop: Int
    val readoutTop: Int

    /** False when no combination met the spacing minimums (content may touch; tiny screens only). */
    val comfortable: Boolean

    /** Max ui chars that fit a cell's label line. */
    val labelChars: Int

    private val colX: IntArray

    // ---- screen rects, per seat ----------------------------------------------------------------
    private val P = PlayLayout.PLAYERS
    private val MS = DrawerZones.MAX_SLOTS

    val upper: Array<IRect> = Array(P) { IRect() }
    val drawer: Array<IRect> = Array(P) { IRect() }
    val bar: Array<IRect> = Array(P) { IRect() }
    val body: Array<IRect> = Array(P) { IRect() }

    /** Open-state number slot, readout slot, ± glyphs, life zones (index `seat * 2 + side`). */
    val numberSlot: Array<IRect> = Array(P) { IRect() }
    val readoutSlot: Array<IRect> = Array(P) { IRect() }
    val minusGlyph: Array<IRect> = Array(P) { IRect() }
    val plusGlyph: Array<IRect> = Array(P) { IRect() }
    val lifeZones: Array<IRect> = Array(P * 2) { IRect() }

    /** Bar segments `seat * 3 + i` (0 prev, 1 close, 2 next): visible part and hit (grown upwards). */
    val barSeg: Array<IRect> = Array(P * 3) { IRect() }
    val barHit: Array<IRect> = Array(P * 3) { IRect() }

    /** Chevron icon footprints: `seat * 2 + 0` prev, `+ 1` next. */
    val chevron: Array<IRect> = Array(P * 2) { IRect() }

    /** Close glyph on the bar: three dots (`seat * 3 + i`) on one paper backing. */
    val dots: Array<IRect> = Array(P * 3) { IRect() }
    val dotsBacking: Array<IRect> = Array(P) { IRect() }

    /** Header button (black, spans [headerSpan] columns): visible card, hit, icon slot, label rect. */
    val headerVis: Array<IRect> = Array(P) { IRect() }
    val headerHit: Array<IRect> = Array(P) { IRect() }
    val headerIcon: Array<IRect> = Array(P) { IRect() }
    val headerLabelAfterIcon: Array<IRect> = Array(P) { IRect() }
    val headerLabel: Array<IRect> = Array(P) { IRect() }

    /** Cells, index `seat * MAX_SLOTS + slot` (slot = position on the page). */
    val cellVis: Array<IRect> = Array(P * MS) { IRect() }
    val cellHit: Array<IRect> = Array(P * MS) { IRect() }

    /** Cell halves, index `(seat * MAX_SLOTS + slot) * 2 + half`. */
    val halfVis: Array<IRect> = Array(P * MS * 2) { IRect() }
    val halfHit: Array<IRect> = Array(P * MS * 2) { IRect() }

    /** Tray cell interior: icon footprint and value rect. */
    val trayIcon: Array<IRect> = Array(P * MS) { IRect() }
    val trayValue: Array<IRect> = Array(P * MS) { IRect() }

    /** Commander-damage cell interior: label line, icon footprint, value rect. */
    val dmgLabel: Array<IRect> = Array(P * MS) { IRect() }
    val dmgIcon: Array<IRect> = Array(P * MS) { IRect() }
    val dmgValue: Array<IRect> = Array(P * MS) { IRect() }

    init {
        val W = play.panelW
        val H = play.panelH

        // Columns: each half of a cell ≥ target wide, content fits.
        var c = (W / (2 * target)).coerceIn(1, DrawerZones.MAX_COLS)
        while (c > 1 && W / c - 2 * inset - 2 * pad < contentW) c--
        cols = c
        // The header takes two columns where there are four or more (full label); on narrower grids
        // it takes one and the painter uses the short label ([headerShort]).
        headerSpan = if (cols >= 4) 2 else 1
        colX = IntArray(cols + 1) { it * W / cols }

        // Rows and number/readout scale: first combination that fits (see class doc).
        var chosenRows = 1
        var chosenK = 1
        var chosenRk = 1
        var top = -1
        val clearW = W - 2 * (play.diamondHalf + PlayLayout.GAP_MIN)
        search@ for (k in play.kMax downTo 1) {
            for (r in DrawerZones.MAX_ROWS downTo 1) {
                for (rk in play.readoutK downTo 1) {
                    if (play.readoutFont.widthOf(PlayLayout.READOUT_CHARS, rk) > clearW) continue
                    val t = stackTop(W, H - (barH + r * rowH), k, rk)
                    if (t >= 0) {
                        chosenRows = r; chosenK = k; chosenRk = rk; top = t
                        break@search
                    }
                }
            }
        }
        comfortable = top >= 0
        rows = chosenRows
        numberK = chosenK
        readoutK = chosenRk
        drawerH = minOf(H, barH + rows * rowH)
        upperH = H - drawerH
        readoutTop = if (top >= 0) top else PlayLayout.V_MIN
        numberTop = readoutTop + play.readoutFont.height(readoutK) + readoutGap

        val visW0 = colX[1] - colX[0] - 2 * inset
        labelChars = maxOf(1, (visW0 - 2 * pad + ui.tracking * tk) / (ui.advance * tk))

        layoutSeats(W)
    }

    /**
     * Player-frame top of the readout + number stack in an upper area [up] tall, with the number at
     * [k] and the readout at [rk]: centred between [PlayLayout.V_MIN] and the bar's hit extension,
     * pushed down (if needed) so nothing that reaches into a far corner overlaps the centre diamond.
     * -1 when it does not fit.
     */
    private fun stackTop(W: Int, up: Int, k: Int, rk: Int): Int {
        val f = play.font
        val roH = play.readoutFont.height(rk)
        val nH = f.height(k)
        val stack = roH + readoutGap + nH
        val bottom = up - barHitPad
        var t = PlayLayout.V_MIN + maxOf(0, (bottom - PlayLayout.V_MIN - stack) / 2)
        // Corner reach of the diamond into a panel (both far corners, whichever seat).
        val reach = play.diamondHalf
        val roW = play.readoutFont.widthOf(PlayLayout.READOUT_CHARS, rk)
        if ((W - roW) / 2 < reach) t = maxOf(t, reach)
        val side = (W - f.widthOf(PlayLayout.MAX_CHARS, k)) / 2
        if (side < reach) t = maxOf(t, reach - roH - readoutGap)
        val pmK = PlayLayout.pmK(k)
        val pmX = (side - f.glyphWidth * pmK) / 2
        if (pmX < reach) t = maxOf(t, reach - roH - readoutGap - (nH - f.glyphHeight * pmK) / 2)
        return if (t + stack <= bottom) t else -1
    }

    /** Narrow grids (header in one column) use the short header labels. */
    val headerShort: Boolean get() = headerSpan < 2

    private fun layoutSeats(W: Int) {
        val pl = play
        val f = pl.font
        // Open-state number block (player frame).
        val slotW = f.widthOf(PlayLayout.MAX_CHARS, numberK)
        val slotH = f.height(numberK)
        val side = (W - slotW) / 2
        val pmK = PlayLayout.pmK(numberK)
        val pmW = f.glyphWidth * pmK
        val pmH = f.glyphHeight * pmK
        val pmX = (side - pmW) / 2
        val pmY = numberTop + (slotH - pmH) / 2
        val roW = pl.readoutFont.widthOf(PlayLayout.READOUT_CHARS, readoutK)
        val roH = pl.readoutFont.height(readoutK)
        val roX = (W - roW) / 2
        val half = W / 2

        val bodyTop = upperH + barH
        val segW = if (cols >= 3) colX[1] else W / 3
        val c = pl.chrome
        val dotsW = 3 * c.dot + 2 * c.dotGap
        val dotX = (W - dotsW) / 2
        val dotY = upperH + (barH - c.dot) / 2
        val iconY = upperH + (barH - iconSize) / 2

        for (s in 0 until P) {
            pl.mapRect(s, 0, 0, W, upperH, upper[s])
            pl.mapRect(s, 0, upperH, W, drawerH, drawer[s])
            pl.mapRect(s, 0, upperH, W, barH, bar[s])
            pl.mapRect(s, 0, bodyTop, W, drawerH - barH, body[s])

            pl.mapRect(s, side, numberTop, slotW, slotH, numberSlot[s])
            pl.mapRect(s, roX, readoutTop, roW, roH, readoutSlot[s])
            pl.mapRect(s, pmX, pmY, pmW, pmH, minusGlyph[s])
            pl.mapRect(s, W - pmX - pmW, pmY, pmW, pmH, plusGlyph[s])
            pl.mapRect(s, 0, 0, half, upperH, lifeZones[s * 2 + PlayLayout.MINUS])
            pl.mapRect(s, half, 0, W - half, upperH, lifeZones[s * 2 + PlayLayout.PLUS])

            // Bar: prev | close | next.
            val segX = intArrayOf(0, segW, W - segW, W)
            for (i in 0 until 3) {
                val x0 = segX[i]
                val w = segX[i + 1] - x0
                pl.mapRect(s, x0, upperH, w, barH, barSeg[s * 3 + i])
                pl.mapRect(s, x0, upperH - barHitPad, w, barH + barHitPad, barHit[s * 3 + i])
            }
            pl.mapRect(s, (segW - iconSize) / 2, iconY, iconSize, iconSize, chevron[s * 2])
            pl.mapRect(s, W - segW + (segW - iconSize) / 2, iconY, iconSize, iconSize, chevron[s * 2 + 1])
            for (i in 0 until 3) pl.mapRect(s, dotX + i * (c.dot + c.dotGap), dotY, c.dot, c.dot, dots[s * 3 + i])
            pl.mapRect(s, dotX - c.dotHalo, dotY - c.dotHalo, dotsW + 2 * c.dotHalo, c.dot + 2 * c.dotHalo, dotsBacking[s])

            // Header button: grid (0..span-1, row 0).
            val hx = colX[0]
            val hw = colX[headerSpan] - hx
            pl.mapRect(s, hx, bodyTop, hw, rowH, headerHit[s])
            pl.mapRect(s, hx + inset, bodyTop + inset, hw - 2 * inset, rowH - 2 * inset, headerVis[s])
            val innerX = hx + inset + pad
            val innerW = hw - 2 * inset - 2 * pad
            val innerY = bodyTop + inset
            val innerH = rowH - 2 * inset
            pl.mapRect(s, innerX, innerY, innerW, innerH, headerLabel[s])
            pl.mapRect(s, innerX, innerY + (innerH - iconSize) / 2, iconSize, iconSize, headerIcon[s])
            pl.mapRect(s, innerX + iconSize + iconGap, innerY, innerW - iconSize - iconGap, innerH, headerLabelAfterIcon[s])

            // Cells.
            for (slot in 0 until MS) {
                val i = s * MS + slot
                val g = headerSpan + slot
                if (slot >= perPage || g >= cols * rows) {
                    // Unused at this size: empty rects (never hit, never drawn).
                    for (r in arrayOf(cellVis[i], cellHit[i], trayIcon[i], trayValue[i], dmgLabel[i], dmgIcon[i], dmgValue[i])) r.set(0, 0, 0, 0)
                    for (h in 0..1) { halfVis[i * 2 + h].set(0, 0, 0, 0); halfHit[i * 2 + h].set(0, 0, 0, 0) }
                    continue
                }
                val cx = colX[g % cols]
                val cw = colX[g % cols + 1] - cx
                val cy = bodyTop + (g / cols) * rowH
                pl.mapRect(s, cx, cy, cw, rowH, cellHit[i])
                val vx = cx + inset
                val vy = cy + inset
                val vw = cw - 2 * inset
                val vh = rowH - 2 * inset
                pl.mapRect(s, vx, vy, vw, vh, cellVis[i])
                val mid = vx + vw / 2
                pl.mapRect(s, vx, vy, mid - vx, vh, halfVis[i * 2])
                pl.mapRect(s, mid, vy, vx + vw - mid, vh, halfVis[i * 2 + 1])
                pl.mapRect(s, cx, cy, mid - cx, rowH, halfHit[i * 2])
                pl.mapRect(s, mid, cy, cx + cw - mid, rowH, halfHit[i * 2 + 1])

                val ix = vx + pad
                val valX = ix + iconSize + iconGap
                val valW = vx + vw - pad - valX
                pl.mapRect(s, ix, vy + (vh - iconSize) / 2, iconSize, iconSize, trayIcon[i])
                pl.mapRect(s, valX, vy, valW, vh, trayValue[i])

                val capH = ui.capHeight * tk
                val top = vy + (vh - contentH) / 2
                pl.mapRect(s, ix, top, vw - 2 * pad, capH, dmgLabel[i])
                val rowY = top + capH + labelGap
                pl.mapRect(s, ix, rowY, iconSize, iconSize, dmgIcon[i])
                pl.mapRect(s, valX, rowY, valW, iconSize, dmgValue[i])
            }
        }
    }

    // ---- number / readout footprints while open --------------------------------------------------

    /** Screen footprint of the life total for an open drawer; returns the glyph scale. */
    fun numberFootprint(seat: Int, value: Int, out: IRect): Int = play.numberFootprintAt(seat, value, numberK, numberTop, out)

    /** Screen footprint of the pending delta for an open drawer; returns the glyph scale. */
    fun readoutFootprint(seat: Int, delta: Int, out: IRect): Int = play.readoutFootprintAt(seat, delta, readoutK, readoutTop, out)

    // ---- touch ----------------------------------------------------------------------------------

    /**
     * Zone id ([DrawerZones]) under dither (gx, gy) given the drawers' [state]; [PlayLayout.RESERVED]
     * for the centre diamond (T09), [PlayLayout.NONE] for dividers, margins and empty drawer areas.
     * Priority inside an open panel: bar (incl. its upward extension) → header → cells → life halves.
     */
    fun hitTest(gx: Int, gy: Int, state: DrawerState): Int {
        val pl = play
        if (pl.inDiamondHit(gx, gy)) return PlayLayout.RESERVED
        for (s in 0 until P) {
            val p = pl.panels[s]
            if (!p.contains(gx, gy)) continue
            if (!state.isOpen(s)) {
                if (pl.tabHit[s].contains(gx, gy)) return DrawerZones.zone(s, DrawerZones.TAB)
                return lifeZone(s, gx, gy)
            }
            if (barHit[s * 3].contains(gx, gy)) {
                return DrawerZones.zone(s, if (state.hasPrev(s)) DrawerZones.PREV else DrawerZones.TAB)
            }
            if (barHit[s * 3 + 2].contains(gx, gy)) {
                return DrawerZones.zone(s, if (state.hasNext(s)) DrawerZones.NEXT else DrawerZones.TAB)
            }
            if (barHit[s * 3 + 1].contains(gx, gy)) return DrawerZones.zone(s, DrawerZones.TAB)
            if (headerHit[s].contains(gx, gy)) return DrawerZones.zone(s, DrawerZones.HEADER)
            val n = minOf(state.itemsOnPage(s), perPage)
            for (slot in 0 until n) {
                val i = (s * MS + slot) * 2
                if (halfHit[i].contains(gx, gy)) return DrawerZones.cellZone(s, slot, DrawerZones.MINUS)
                if (halfHit[i + 1].contains(gx, gy)) return DrawerZones.cellZone(s, slot, DrawerZones.PLUS)
            }
            if (drawer[s].contains(gx, gy)) return PlayLayout.NONE
            return lifeZone(s, gx, gy)
        }
        return PlayLayout.NONE
    }

    private fun lifeZone(s: Int, gx: Int, gy: Int): Int {
        val p = play.panels[s]
        val lx = if (play.rotation[s] == 180) p.right - 1 - gx else gx - p.left
        return PlayLayout.zone(s, if (lx < play.panelW / 2) PlayLayout.MINUS else PlayLayout.PLUS)
    }

    /**
     * The rect a pressed [zone] inverts (rule 6): life half (upper area when open), the closed tab,
     * a bar segment, the header card or a cell half card. Returns false (and leaves [out]) if none.
     */
    fun feedbackRect(zone: Int, state: DrawerState, out: IRect): Boolean {
        if (DrawerZones.isLife(zone)) {
            val s = PlayLayout.zoneSeat(zone)
            copy(if (state.isOpen(s)) lifeZones[zone] else play.zones[zone], out)
            return true
        }
        if (!DrawerZones.isDrawer(zone)) return false
        val s = DrawerZones.seatOf(zone)
        val local = DrawerZones.localOf(zone)
        val open = state.isOpen(s)
        when {
            local == DrawerZones.TAB -> copy(if (open) barSeg[s * 3 + 1] else play.tab[s], out)
            !open -> return false
            local == DrawerZones.PREV -> copy(barSeg[s * 3], out)
            local == DrawerZones.NEXT -> copy(barSeg[s * 3 + 2], out)
            local == DrawerZones.HEADER -> copy(headerVis[s], out)
            else -> {
                val slot = DrawerZones.slotOf(local)
                if (slot >= perPage) return false
                copy(halfVis[(s * MS + slot) * 2 + DrawerZones.halfOf(local)], out)
            }
        }
        return true
    }

    private fun copy(src: IRect, out: IRect) {
        out.set(src.left, src.top, src.right, src.bottom)
    }

    override fun toString(): String =
        "DrawerLayout(S=$scale tk $tk target $target, cols $cols rows $rows rowH $rowH header $headerSpan perPage $perPage, " +
            "drawerH $drawerH (bar $barH) upperH $upperH, numberK $numberK at $numberTop, readoutK $readoutK at $readoutTop, " +
            "labelChars $labelChars${if (comfortable) "" else ", UNCOMFORTABLE"})"

    companion object {
        /** 9 mm at 300 dpi (the Nomad): the fingertip minimum for every drawer hit rect. */
        const val MIN_TOUCH_DEVICE_PX = 106

        /** Design values at S = 4 (converted per S like [PlayLayout.Chrome]). */
        const val ICON = 16
        const val INSET = 2
        const val PAD = 2
        const val LABEL_GAP = 1
        const val ICON_GAP = 2
        const val READOUT_GAP = 6
        const val HALO = 1
    }
}
