package au.com.josephmark.dithertracker.drawer

import au.com.josephmark.dithertracker.commander.CommanderMode
import au.com.josephmark.dithertracker.dither.HaloGlyph
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
 *  upper      ├[imp]┬─────── ••• ────────┬ › ┤  bar (TAB dither): commander · close · page
 *  bodyTop    │ cell │ cell │ cell │ cell     │  body (DRAWER dither): [cols] × [rows] grid of
 *             │ cell │ cell │ cell │ cell     │  tray cells (commander tax + counters)
 *  panelH     └──────────────────────────────┘
 * ```
 * The drawer covers the lower part of the panel instead of overlaying the number: the number keeps
 * its closed-state glyph scale where the arithmetic allows (it does at S = 2..6 on the Nomad), and
 * moves up with the readout (at a smaller scale) above it. Choice order, by maths:
 * `(rows = 2, k = kMax)`, `(1, kMax)`, `(2, kMax − 1)`, … with the largest readout scale that fits;
 * fits means `V_MIN + readoutH + gap + numberH + barHitPad ≤ upper`, where the bar hit extension
 * ([PlayLayout.Chrome.tabHitPad]) stays clear of the number.
 *
 * Commander damage is a mode on the main tiles (T30, see `play.CommanderMode`); the drawer only
 * holds its entry point: the bar's left segment is the commander button ([cmdVis]: a solid ink card
 * with the imp icon in paper), which makes this player the target. The bar's right segment pages
 * the tray: `›` while there is a next page, `‹` (back to the first page) on the last page of
 * several, otherwise it closes like the middle. So the tray keeps the whole body (8 cells a page).
 *
 * Touch targets: every hit rect (each cell half, bar segments incl. their extension) is at
 * least [target] dither px (≈ 9 mm, [MIN_TOUCH_DEVICE_PX]) in its short dimension: columns are
 * `≥ 2 · target` wide and rows `≥ target` tall. The cell hit grid tiles the body exactly; the
 * visible card is the hit rect inset by [cellInset].
 *
 * Tray cell, player frame (T43: the halves' actions are drawn, like a tiny life total):
 * ```
 *  ┌──────────────────────────┐   stepper (a value that fits [trayValue]):
 *  │ −  [icon]   12        +  │   − [trayMinus] · icon [trayIcon] · value [trayValue] · + [trayPlus]
 *  └──────────────────────────┘
 *  ┌──────────────────────────┐   wide (a value too long for the stepper, i.e. 3 digits at S = 4):
 *  │[icon]  100               │   icon [trayIconWide] · value [trayValueWide]; no glyphs
 *  └──────────────────────────┘
 *     [icon]                      zero: no card, no glyphs; just the icon on a paper halo (− is inert)
 * ```
 * Toggle cell (T14), the same hit grid but one button (either half flips it):
 * ```
 *  ┌██████████████████████████┐   on: a solid ink card, the icon in paper, centred ([toggleIcon])
 *  ███████████ [icon] ██████████
 *  └██████████████████████████┘
 *            [icon]               off: no card; the icon on a paper halo on the drawer texture, centred
 *      [sun]  [moon]              day/night unset: both icons on their halos ([pairLeft] / [pairRight])
 * ```
 * The − sits in the player's left half and the + in the right, whichever way up the seat is. Tray
 * icons leave their outer columns empty ([ICON_MARGIN]), so the icon's footprint may run 1 px into
 * the gaps either side of its ink ([iconInk]).
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

    /** Hit rect → visible tray card, each side (the bar keeps [inset]). */
    val cellInset: Int = PlayLayout.Chrome.size(CELL_INSET, scale)

    /** The cells' − / + glyphs: [pm] square (the − is its middle [pmStroke] rows), [pmStroke]-px strokes. */
    val pm: Int = PM * tk
    val pmStroke: Int = PM_STROKE * tk

    /**
     * Card frame → glyph, − → icon ink, icon ink → value, value → +. The − may sit closer to the icon
     * than the value to the +: icon ink rarely reaches its outer columns, digits always do.
     */
    val edgeGap: Int = PlayLayout.Chrome.size(EDGE_GAP, scale)
    val pmGap: Int = PlayLayout.Chrome.size(PM_GAP, scale)
    val valueGap: Int = PlayLayout.Chrome.size(VALUE_GAP, scale)
    val plusGap: Int = PlayLayout.Chrome.size(PLUS_GAP, scale)

    /** Empty columns either side of a tray icon's ink, and its ink width. */
    val iconMargin: Int = ICON_MARGIN * tk
    val iconInkW: Int = iconSize - 2 * iconMargin
    /** Paper between the readout and the number: the closed tile's ([PlayLayout.READOUT_GAP] per hero k, T37). */
    val readoutGap: Int = PlayLayout.READOUT_GAP * play.kMax

    /** Paper rim around an icon/label drawn straight on the drawer texture (zero-valued cells). */
    val halo: Int = PlayLayout.Chrome.size(HALO, scale)

    /** Bar height: the tab's height, but never shorter than a chevron icon plus a 1-px rim. */
    val barH: Int = maxOf(play.chrome.tabH, iconSize + 2)
    val barHitPad: Int = play.chrome.tabHitPad

    /** Tallest cell content: an icon or a `num-m` value. */
    val contentH: Int = maxOf(iconSize, numFont.height(tk))

    /**
     * Widest tray-card content (frame included) the columns must hold: the stepper with two digits
     * at the face's tracking, and the wide layout with three digits at [TIGHT_TRACKING] (T37: the
     * bold 10-wide face makes `999` too wide at its own tracking; a value that does not fit closes
     * up to 1 px rather than clip or shrink the grid, see [valueTracking]).
     */
    val stepperW: Int = 2 * (FRAME + edgeGap + pm) + pmGap + iconInkW + valueGap + plusGap + numFont.widthOf(2, tk)
    val wideW: Int = 2 * (FRAME + edgeGap) + iconInkW + valueGap + numFont.widthOf(3, tk, TIGHT_TRACKING)
    val contentW: Int = maxOf(stepperW, wideW)

    val rowH: Int = maxOf(target, contentH + 2 * pad + 2 * inset)
    val cols: Int
    val rows: Int

    /** Cells per page. */
    val perPage: Int get() = cols * rows

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

    private val colX: IntArray

    // ---- screen rects, per seat ----------------------------------------------------------------
    private val P = play.playerCount
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

    /** Bar segments `seat * 3 + i` (0 commander button, 1 close, 2 page): visible part and hit (grown upwards). */
    val barSeg: Array<IRect> = Array(P * 3) { IRect() }
    val barHit: Array<IRect> = Array(P * 3) { IRect() }

    /** The commander button in the bar's left segment: solid ink card and its (paper) imp icon. */
    val cmdVis: Array<IRect> = Array(P) { IRect() }
    val cmdIcon: Array<IRect> = Array(P) { IRect() }

    /** The page chevron in the bar's right segment (› next, or ‹ back to the first page). */
    val pageIcon: Array<IRect> = Array(P) { IRect() }

    /** Close glyph on the bar: three dots (`seat * 3 + i`); [dotsBacking] is their old square backing's extent. */
    val dots: Array<IRect> = Array(P * 3) { IRect() }
    val dotsBacking: Array<IRect> = Array(P) { IRect() }

    /** Cells, index `seat * MAX_SLOTS + slot` (slot = position on the page). */
    val cellVis: Array<IRect> = Array(P * MS) { IRect() }
    val cellHit: Array<IRect> = Array(P * MS) { IRect() }

    /** Cell halves, index `(seat * MAX_SLOTS + slot) * 2 + half`. */
    val halfVis: Array<IRect> = Array(P * MS * 2) { IRect() }
    val halfHit: Array<IRect> = Array(P * MS * 2) { IRect() }

    /** Tray cell interior, stepper (and zero) layout: − bar, + box, icon footprint, value rect. */
    val trayMinus: Array<IRect> = Array(P * MS) { IRect() }
    val trayPlus: Array<IRect> = Array(P * MS) { IRect() }
    val trayIcon: Array<IRect> = Array(P * MS) { IRect() }
    val trayValue: Array<IRect> = Array(P * MS) { IRect() }

    /** Toggle cell (T14): the centred icon footprint, and the unset day/night pair's two footprints. */
    val toggleIcon: Array<IRect> = Array(P * MS) { IRect() }
    val pairLeft: Array<IRect> = Array(P * MS) { IRect() }
    val pairRight: Array<IRect> = Array(P * MS) { IRect() }

    /** Paper between the unset day/night pair's icons' footprints. */
    val pairGap: Int = PlayLayout.Chrome.size(PAIR_GAP, scale)

    /** Tray cell interior, wide layout (no glyphs): icon footprint and value rect. */
    val trayIconWide: Array<IRect> = Array(P * MS) { IRect() }
    val trayValueWide: Array<IRect> = Array(P * MS) { IRect() }

    init {
        val W = play.panelW
        val H = play.panelH

        // Columns: each half of a cell ≥ target wide, content fits.
        var c = (W / (2 * target)).coerceIn(1, DrawerZones.MAX_COLS)
        while (c > 1 && W / c - 2 * cellInset < contentW) c--
        cols = c
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
        // Reach of the diamond into the tile across each piece's columns (a far corner on a 2×2, the
        // middle of the far edge for some counts, T11; [PlayLayout.farReach] is 0 where it is clear).
        val roW = play.readoutFont.widthOf(PlayLayout.READOUT_CHARS, rk)
        val roX = (W - roW) / 2
        val rr = play.farReach(roX, roX + roW)
        if (rr > 0) t = maxOf(t, rr)
        val slotW = f.widthOf(PlayLayout.MAX_CHARS, k)
        val side = (W - slotW) / 2
        val nr = play.farReach(side, side + slotW)
        if (nr > 0) t = maxOf(t, nr - roH - readoutGap)
        val pmK = PlayLayout.pmK(k)
        val pmW = play.pmFont.glyphWidth * pmK
        val pmX = (side - pmW) / 2
        val pr = maxOf(play.farReach(pmX, pmX + pmW), play.farReach(W - pmX - pmW, W - pmX))
        if (pr > 0) t = maxOf(t, pr - roH - readoutGap - (play.pmCapAlignedY(0, k)))
        return if (t + stack <= bottom) t else -1
    }

    private fun layoutSeats(W: Int) {
        val pl = play
        val f = pl.font
        // Open-state number block (player frame).
        val slotW = f.widthOf(PlayLayout.MAX_CHARS, numberK)
        val slotH = f.height(numberK)
        val side = (W - slotW) / 2
        val pmK = PlayLayout.pmK(numberK)
        val pmW = pl.pmFont.glyphWidth * pmK
        val pmH = pl.pmFont.glyphHeight * pmK
        val pmX = (side - pmW) / 2
        val pmY = pl.pmCapAlignedY(numberTop, numberK)
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

            // Bar: commander button | close | page.
            val segX = intArrayOf(0, segW, W - segW, W)
            for (i in 0 until 3) {
                val x0 = segX[i]
                val w = segX[i + 1] - x0
                pl.mapRect(s, x0, upperH, w, barH, barSeg[s * 3 + i])
                pl.mapRect(s, x0, upperH - barHitPad, w, barH + barHitPad, barHit[s * 3 + i])
            }
            // The ink card fills the segment less `inset` at the sides and 1 px top and bottom.
            pl.mapRect(s, inset, upperH + 1, segW - 2 * inset, barH - 2, cmdVis[s])
            pl.mapRect(s, (segW - iconSize) / 2, iconY, iconSize, iconSize, cmdIcon[s])
            pl.mapRect(s, W - segW + (segW - iconSize) / 2, iconY, iconSize, iconSize, pageIcon[s])
            for (i in 0 until 3) pl.mapRect(s, dotX + i * (c.dot + c.dotGap), dotY, c.dot, c.dot, dots[s * 3 + i])
            pl.mapRect(s, dotX - c.dotHalo, dotY - c.dotHalo, dotsW + 2 * c.dotHalo, c.dot + 2 * c.dotHalo, dotsBacking[s])

            // Cells.
            for (slot in 0 until MS) {
                val i = s * MS + slot
                val g = slot
                if (slot >= perPage) {
                    // Unused at this size: empty rects (never hit, never drawn).
                    for (r in arrayOf(cellVis[i], cellHit[i], trayMinus[i], trayPlus[i], trayIcon[i], trayValue[i], trayIconWide[i], trayValueWide[i], toggleIcon[i], pairLeft[i], pairRight[i])) {
                        r.set(0, 0, 0, 0)
                    }
                    for (h in 0..1) { halfVis[i * 2 + h].set(0, 0, 0, 0); halfHit[i * 2 + h].set(0, 0, 0, 0) }
                    continue
                }
                val cx = colX[g % cols]
                val cw = colX[g % cols + 1] - cx
                val cy = bodyTop + (g / cols) * rowH
                pl.mapRect(s, cx, cy, cw, rowH, cellHit[i])
                val vx = cx + cellInset
                val vy = cy + cellInset
                val vw = cw - 2 * cellInset
                val vh = rowH - 2 * cellInset
                pl.mapRect(s, vx, vy, vw, vh, cellVis[i])
                val mid = vx + vw / 2
                pl.mapRect(s, vx, vy, mid - vx, vh, halfVis[i * 2])
                pl.mapRect(s, mid, vy, vx + vw - mid, vh, halfVis[i * 2 + 1])
                pl.mapRect(s, cx, cy, mid - cx, rowH, halfHit[i * 2])
                pl.mapRect(s, mid, cy, cx + cw - mid, rowH, halfHit[i * 2 + 1])

                // Stepper: − · icon · value · +, the glyphs against the frame.
                val inL = vx + FRAME + edgeGap
                val inR = vx + vw - FRAME - edgeGap
                val iconY = vy + (vh - iconSize) / 2
                val pmY = vy + (vh - pm) / 2
                pl.mapRect(s, inL, pmY + (pm - pmStroke) / 2, pm, pmStroke, trayMinus[i])
                pl.mapRect(s, inR - pm, pmY, pm, pm, trayPlus[i])
                val ink = inL + pm + pmGap
                pl.mapRect(s, ink - iconMargin, iconY, iconSize, iconSize, trayIcon[i])
                val valX = ink + iconInkW + valueGap
                pl.mapRect(s, valX, vy, inR - pm - plusGap - valX, vh, trayValue[i])
                // Toggle: one icon centred; the unset day/night pair centred as a group.
                val tcx = vx + (vw - iconSize) / 2
                pl.mapRect(s, tcx, iconY, iconSize, iconSize, toggleIcon[i])
                val pairW = 2 * iconSize + pairGap
                val px0 = vx + (vw - pairW) / 2
                pl.mapRect(s, px0, iconY, iconSize, iconSize, pairLeft[i])
                pl.mapRect(s, px0 + iconSize + pairGap, iconY, iconSize, iconSize, pairRight[i])
                // Wide: icon against the frame, the value takes the rest.
                pl.mapRect(s, inL - iconMargin, iconY, iconSize, iconSize, trayIconWide[i])
                val wideX = inL + iconInkW + valueGap
                pl.mapRect(s, wideX, vy, inR - wideX, vh, trayValueWide[i])
            }
        }
    }

    /** The bar's close glyph per seat: [dots] as one mask on its [halo]-px cut-out (T41; drawn instead of [dotsBacking]). */
    val dotsGlyph: Array<HaloGlyph> = Array(P) { HaloGlyph.of(dots, it * 3, 3, halo) }

    /** Whether cell [i] (`seat * MAX_SLOTS + slot`) shows [value] as a stepper (− icon value +) rather than wide. */
    fun showsSteps(i: Int, value: Int): Boolean =
        numFont.widthOf(Font.intLength(value), tk, TIGHT_TRACKING) <= trayValue[i].width

    /** The icon footprint and value rect cell [i] uses for [value]. */
    fun iconRect(i: Int, value: Int): IRect = if (value == 0 || showsSteps(i, value)) trayIcon[i] else trayIconWide[i]
    fun valueRect(i: Int, value: Int): IRect = if (showsSteps(i, value)) trayValue[i] else trayValueWide[i]

    /** [r] (an icon footprint) narrowed to the icon's ink columns. */
    fun iconInk(r: IRect, out: IRect): IRect {
        out.set(r.left + iconMargin, r.top, r.right - iconMargin, r.bottom)
        return out
    }

    /**
     * Tracking (font px) to draw [value] with in tray-value rect [valueW] wide: the face's own, or
     * tighter (never below [TIGHT_TRACKING]) when that is too wide. Only 3-digit values ever close up.
     */
    fun valueTracking(value: Int, valueW: Int): Int {
        val len = Font.intLength(value)
        var t = numFont.tracking
        while (t > TIGHT_TRACKING && numFont.widthOf(len, tk, t) > valueW) t--
        return t
    }

    // ---- number / readout footprints while open --------------------------------------------------

    /** Screen footprint of the life total for an open drawer; returns the glyph scale. */
    fun numberFootprint(seat: Int, value: Int, out: IRect): Int = play.numberFootprintAt(seat, value, numberK, numberTop, out)

    /** Screen footprint of the pending delta for an open drawer; returns the glyph scale. */
    fun readoutFootprint(seat: Int, delta: Int, out: IRect): Int = play.readoutFootprintAt(seat, delta, readoutK, readoutTop, out)

    override fun toString(): String =
        "DrawerLayout(S=$scale tk $tk target $target, cols $cols rows $rows rowH $rowH perPage $perPage, " +
            "drawerH $drawerH (bar $barH) upperH $upperH, numberK $numberK at $numberTop, readoutK $readoutK at $readoutTop" +
            "${if (comfortable) "" else ", UNCOMFORTABLE"})"

    companion object {
        /** 9 mm at 300 dpi (the Nomad): the fingertip minimum for every drawer hit rect. */
        const val MIN_TOUCH_DEVICE_PX = PlayLayout.MIN_TOUCH_DEVICE_PX

        /** Design values at S = 4 (converted per S like [PlayLayout.Chrome]). */
        const val ICON = 16
        const val INSET = 2
        const val PAD = 2
        const val HALO = 1
        const val CELL_INSET = 1
        const val EDGE_GAP = 1
        const val PM_GAP = 1
        const val VALUE_GAP = 1
        const val PLUS_GAP = 2

        /** Between the unset day/night cell's sun and moon footprints (T14). */
        const val PAIR_GAP = 2

        /** The cells' − / + glyphs (font px, × [tk]): 6 square, 2-px strokes (even, so they turn exactly). */
        const val PM = 6
        const val PM_STROKE = 2

        /** Empty columns either side of every tray icon's ink (font px; tested). */
        const val ICON_MARGIN = 1

        /** The card's ink frame (dither px). */
        const val FRAME = 1

        /** Closest a tray value's digits ever sit (font px): 1 keeps the 3-px stems apart. */
        const val TIGHT_TRACKING = 1
    }
}
