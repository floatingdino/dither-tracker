package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * Pure layout maths for the 4-player landscape Play screen (no Android types; JVM-tested).
 * Every coordinate is in dither px on [grid] (grid origin = global pattern phase origin).
 *
 * Structure:
 * - The grid is split into a 2×2 block of equal panels with a 1-dither-px dotted divider between
 *   them: `panelW = (gridW − 1) / 2`, `panelH = (gridH − 1) / 2`. Any dither px left over (odd
 *   remainders) become extra margin, split like [DitherGrid] does (odd one right/bottom).
 * - Seats run clockwise from top-left: 0 TL, 1 TR, 2 BR, 3 BL. The top row (0, 1) is rotated 180°
 *   to face the far side of the table.
 * - Each panel is laid out once in the *player's* frame (upright: x to the player's right, y
 *   towards the player) and mapped to the screen with [TextLayout.mapX]/[TextLayout.mapY], so a
 *   rotated panel is the exact 180° image of an upright one.
 *
 * Panel content in the player's frame, left to right: edge, `−` glyph, gap, number slot, gap, `+`
 * glyph, edge. The number slot is exactly wide enough for [MAX_CHARS] glyphs (`−999`, `9999` — the
 * core life limits) at [kMax], so ± never move and a value never overlaps them; [numberK] still
 * drops k for anything wider (defensive, and for future faces).
 *
 * Choice of k (by maths, not by eye): [kMax] is the largest integer glyph scale such that
 * - horizontally `2·EDGE_MIN + 2·pmW + 2·GAP_MIN + width(MAX_CHARS, k) ≤ panelW`, where the ±
 *   glyphs are the same face at `pmK = max(1, k / 2)` (half-size: the ± are targets, the number is
 *   the hero), and
 * - vertically `height(k) + 2·(TAB_H + V_MIN) ≤ panelH` (the number clears the drawer tab whichever
 *   way up the panel is).
 * Falls back to k = 1 when even that does not fit (tiny screens).
 */
class PlayLayout private constructor(
    val grid: DitherGrid,
    val font: Font,
    val panelW: Int,
    val panelH: Int,
    /** Top-left of the 2×2 block inside the grid (leftover dither px). */
    val originX: Int,
    val originY: Int,
    val kMax: Int,
    /** Glyph scale of the ± glyphs. */
    val pmK: Int,
) {
    val playerCount: Int get() = PLAYERS

    /** False when even k = 1 breaks the spacing minimums (screen too small at this S): content may touch. */
    val comfortable: Boolean = fits(font, kMax, panelW, panelH)

    /** Screen rect of each panel (divider excluded). */
    val panels: Array<IRect> = Array(PLAYERS) { IRect() }

    /** Rotation (degrees clockwise) of each seat's content: 180 for the top row. */
    val rotation: IntArray = IntArray(PLAYERS) { if (it == 0 || it == 1) 180 else 0 }

    /** Screen footprints of the `−` and `+` glyphs. */
    val minusGlyph: Array<IRect> = Array(PLAYERS) { IRect() }
    val plusGlyph: Array<IRect> = Array(PLAYERS) { IRect() }

    /** Screen rect the life total is drawn in (fits [MAX_CHARS] at [kMax]); invalidate this on a life change. */
    val numberSlot: Array<IRect> = Array(PLAYERS) { IRect() }

    /** Drawer tab: visual rect and (larger) reserved hit rect. Static in T06; behaviour is T08. */
    val tab: Array<IRect> = Array(PLAYERS) { IRect() }
    val tabHit: Array<IRect> = Array(PLAYERS) { IRect() }

    /** Tab glyph (`...`): three dots, screen rects. Index `seat * 3 + i`. */
    val tabDots: Array<IRect> = Array(PLAYERS * 3) { IRect() }

    /** Press zones, index = zone id ([zone]): the player's left (`−`) or right (`+`) half of the panel. */
    val zones: Array<IRect> = Array(PLAYERS * 2) { IRect() }

    /** Dotted dividers. */
    val dividerV = IRect()
    val dividerH = IRect()

    /** Centre diamond (menu button, T09): centre pixel, half-extent, tip half-width. */
    val diamondCx: Int get() = originX + panelW
    val diamondCy: Int get() = originY + panelH
    val diamondHalf: Int
    val diamondTip: Int

    /** Bounding rect of the diamond. */
    val diamond = IRect()

    /** Diamond glyph (3 vertical bars), screen rects. */
    val menuBars: Array<IRect> = Array(3) { IRect() }

    init {
        val w2 = 2 * panelW + 1
        val h2 = 2 * panelH + 1
        dividerV.setXywh(originX + panelW, originY, 1, h2)
        dividerH.setXywh(originX, originY + panelH, w2, 1)
        panels[0].setXywh(originX, originY, panelW, panelH)
        panels[1].setXywh(originX + panelW + 1, originY, panelW, panelH)
        panels[2].setXywh(originX + panelW + 1, originY + panelH + 1, panelW, panelH)
        panels[3].setXywh(originX, originY + panelH + 1, panelW, panelH)

        // Player-frame layout, identical for every seat.
        val pmW = font.glyphWidth * pmK
        val pmH = font.glyphHeight * pmK
        val slotW = font.widthOf(MAX_CHARS, kMax)
        val slotH = font.height(kMax)
        val side = (panelW - slotW) / 2 // space between panel edge and number slot
        val pmX = (side - pmW) / 2 // ± centred in that space
        val pmY = (panelH - pmH) / 2
        val slotY = (panelH - slotH) / 2
        val tabW = minOf(TAB_W, panelW)
        val tabX = (panelW - tabW) / 2
        val tabY = panelH - TAB_H
        val dotsW = 3 * DOT + 2 * DOT_GAP
        val dotX = tabX + (tabW - dotsW) / 2
        val dotY = tabY + (TAB_H - DOT) / 2
        val half = panelW / 2

        for (s in 0 until PLAYERS) {
            map(s, pmX, pmY, pmW, pmH, minusGlyph[s])
            map(s, panelW - pmX - pmW, pmY, pmW, pmH, plusGlyph[s])
            map(s, side, slotY, slotW, slotH, numberSlot[s])
            map(s, tabX, tabY, tabW, TAB_H, tab[s])
            map(s, tabX - TAB_HIT_PAD, tabY - TAB_HIT_PAD, tabW + 2 * TAB_HIT_PAD, TAB_H + TAB_HIT_PAD, tabHit[s])
            for (i in 0 until 3) map(s, dotX + i * (DOT + DOT_GAP), dotY, DOT, DOT, tabDots[s * 3 + i])
            map(s, 0, 0, half, panelH, zones[zone(s, MINUS)])
            map(s, half, 0, panelW - half, panelH, zones[zone(s, PLUS)])
        }

        // Diamond: a stepped octagon-ish diamond, |dx| + |dy| ≤ R with the four tips cut flat.
        diamondHalf = minOf(DIAMOND_R - DIAMOND_CUT, panelW, panelH)
        diamondTip = minOf(DIAMOND_CUT, diamondHalf)
        diamond.set(diamondCx - diamondHalf, diamondCy - diamondHalf, diamondCx + diamondHalf + 1, diamondCy + diamondHalf + 1)
        val barTop = diamondCy - BAR_H / 2
        for (i in 0 until 3) menuBars[i].setXywh(diamondCx + (i - 1) * BAR_PITCH, barTop, 1, BAR_H)
    }

    /** Half-width (dither px each side of the centre column) of diamond row `dy` (−half..half). */
    fun diamondRowHalf(dy: Int): Int {
        val a = if (dy < 0) -dy else dy
        if (a > diamondHalf) return -1
        return minOf(diamondHalf + diamondTip - a, diamondHalf)
    }

    /** Maps a player-frame rect of seat [s] to the screen. */
    private fun map(s: Int, lx: Int, ly: Int, w: Int, h: Int, out: IRect) {
        val r = rotation[s]
        val p = panels[s]
        out.setXywh(
            p.left + TextLayout.mapX(r, panelW, panelH, lx, ly, w, h),
            p.top + TextLayout.mapY(r, panelW, panelH, lx, ly, w, h),
            TextLayout.footprintW(r, w, h),
            TextLayout.footprintH(r, w, h),
        )
    }

    /** Largest glyph scale ≤ [kMax] at which [chars] glyphs fit the number slot. */
    fun numberK(chars: Int): Int {
        val slotW = font.widthOf(MAX_CHARS, kMax)
        var k = kMax
        while (k > 1 && font.widthOf(chars, k) > slotW) k--
        return k
    }

    /**
     * Screen footprint (top-left + size) of [value] drawn for [seat]: centred in the number slot along
     * the player's reading direction, cap band centred across it. Writes [out] and returns the glyph
     * scale k to draw with. Draw it with `TextRenderer.drawInt(font, value, out.left, out.top, k, rotation[seat])`.
     */
    fun numberFootprint(seat: Int, value: Int, out: IRect): Int {
        val len = Font.intLength(value)
        val k = numberK(len)
        val tw = font.widthOf(len, k)
        val th = font.height(k)
        val slotW = font.widthOf(MAX_CHARS, kMax)
        val slotH = font.height(kMax)
        val side = (panelW - slotW) / 2
        val slotY = (panelH - slotH) / 2
        val lx = side + TextLayout.centre(slotW, tw)
        val ly = slotY + TextLayout.centre(slotH, font.capHeight * k) - font.capTop * k
        map(seat, lx, ly, tw, th, out)
        return k
    }

    /**
     * What a touch at dither (gx, gy) hits: a zone id (`seat * 2 + side`, see [zoneSeat]/[zoneSide]),
     * [RESERVED] for the drawer tab and centre diamond (T08/T09 own those), or [NONE] (dividers, margins).
     */
    fun hitTest(gx: Int, gy: Int): Int {
        if (inDiamondHit(gx, gy)) return RESERVED
        for (s in 0 until PLAYERS) {
            val p = panels[s]
            if (!p.contains(gx, gy)) continue
            if (tabHit[s].contains(gx, gy)) return RESERVED
            val lx = if (rotation[s] == 180) p.right - 1 - gx else gx - p.left
            return zone(s, if (lx < panelW / 2) MINUS else PLUS)
        }
        return NONE
    }

    /** True inside the diamond's reserved hit area (the shape grown by [DIAMOND_HIT_PAD]). */
    fun inDiamondHit(gx: Int, gy: Int): Boolean {
        val dx = Math.abs(gx - diamondCx)
        val dy = Math.abs(gy - diamondCy)
        val lim = diamondHalf + DIAMOND_HIT_PAD
        return dx <= lim && dy <= lim && dx + dy <= diamondHalf + diamondTip + DIAMOND_HIT_PAD
    }

    override fun toString(): String =
        "PlayLayout(grid ${grid.width}x${grid.height} S=${grid.scale}, origin $originX,$originY, panel ${panelW}x$panelH, kMax $kMax, pmK $pmK)"

    companion object {
        const val PLAYERS = 4

        /** Widest life total: `−999` / `9999` (core `Limits` life range). */
        const val MAX_CHARS = 4

        /** Minimum dither px between the panel edge and a ± glyph, and between ± and the number. */
        const val EDGE_MIN = 8
        const val GAP_MIN = 8

        /** Minimum dither px between the number and the tab band, above and below. */
        const val V_MIN = 4

        /** Drawer tab size (Figma: 220×47 device px at S=4 ≈ 55×12). */
        const val TAB_W = 55
        const val TAB_H = 12

        /** Extra reserved touch margin around the tab (not on the panel edge side). */
        const val TAB_HIT_PAD = 4

        /** `...` glyph: 2×2 dots, 2 apart (their 1-px paper halos merge into one 12×4 backing). */
        const val DOT = 2
        const val DOT_GAP = 2

        /** Diamond: |dx|+|dy| ≤ R, tips cut by [DIAMOND_CUT] → 23×23 with 5-px flat tips (Figma ≈ 94 px). */
        const val DIAMOND_R = 13
        const val DIAMOND_CUT = 2
        const val DIAMOND_HIT_PAD = 4

        /** Menu glyph: three 1×[BAR_H] vertical bars, [BAR_PITCH] apart (Figma "III"); halos merge into a 9×11 backing. */
        const val BAR_H = 9
        const val BAR_PITCH = 3

        const val MINUS = 0
        const val PLUS = 1

        const val NONE = -1
        const val RESERVED = -2

        fun zone(seat: Int, side: Int): Int = seat * 2 + side
        fun zoneSeat(zone: Int): Int = zone / 2
        fun zoneSide(zone: Int): Int = zone % 2

        /** Life delta for a zone: −1 for [MINUS], +1 for [PLUS]. */
        fun zoneDelta(zone: Int): Int = if (zoneSide(zone) == PLUS) 1 else -1

        fun compute(grid: DitherGrid, font: Font = Fonts.NUM_L): PlayLayout {
            val panelW = maxOf(1, (grid.width - 1) / 2)
            val panelH = maxOf(1, (grid.height - 1) / 2)
            val spareX = maxOf(0, grid.width - (2 * panelW + 1))
            val spareY = maxOf(0, grid.height - (2 * panelH + 1))
            val k = maxK(font, panelW, panelH)
            return PlayLayout(grid, font, panelW, panelH, spareX / 2, spareY / 2, k, pmK(k))
        }

        /**
         * The scale S for the Play screen: [DitherGrid.chooseScale] (the user's override wins), then
         * stepped down (not below [DitherGrid.MIN_SCALE]) while the layout would not be [comfortable].
         */
        fun chooseScale(deviceW: Int, deviceH: Int, densityDpi: Int, override: Int = DitherGrid.AUTO): Int {
            var s = DitherGrid.chooseScale(deviceW, deviceH, densityDpi, override)
            if (override != DitherGrid.AUTO) return s
            while (s > DitherGrid.MIN_SCALE && !compute(DitherGrid(deviceW, deviceH, s)).comfortable) s--
            return s
        }

        fun pmK(k: Int): Int = maxOf(1, k / 2)

        /** Largest k that fits (see class doc), or 1. */
        fun maxK(font: Font, panelW: Int, panelH: Int): Int {
            var best = 1
            for (k in 1..Font.MAX_K) if (fits(font, k, panelW, panelH)) best = k else break
            return best
        }

        fun fits(font: Font, k: Int, panelW: Int, panelH: Int): Boolean {
            val w = 2 * EDGE_MIN + 2 * font.glyphWidth * pmK(k) + 2 * GAP_MIN + font.widthOf(MAX_CHARS, k)
            val h = font.height(k) + 2 * (TAB_H + V_MIN)
            return w <= panelW && h <= panelH
        }
    }
}
