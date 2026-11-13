package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.layout.Arrangement
import au.com.josephmark.dithertracker.layout.Arrangements
import au.com.josephmark.dithertracker.layout.Seating
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * Pure layout maths for the Play screen, 2–6 players, landscape or portrait (no Android types;
 * JVM-tested). Every coordinate is in dither px on [grid] (grid origin = global pattern phase origin).
 *
 * Structure (T11; T06 for the 2×2):
 * - The seats come from an [Arrangement] placed by [Seating]: every seat gets the **same tile**,
 *   [panelW] × [panelH] in the player's frame; a seat at a short end has it turned sideways on the
 *   screen (rotation 90 / 270). The tiles are separated by 1-dither-px dotted [dividers]; leftover
 *   dither px are margin. For 4 players: a 2×2 block, `panelW = (gridW − 1) / 2`,
 *   `panelH = (gridH − 1) / 2`, seats clockwise from top-left (0 TL, 1 TR, 2 BR, 3 BL), the top row
 *   rotated 180° — exactly the T06 layout. Diagrams per count: `design/layouts/README.md`.
 * - Each panel is laid out once in the *player's* frame (upright: x to the player's right, y
 *   towards the player) and mapped to the screen with [TextLayout.mapX]/[TextLayout.mapY], so a
 *   rotated panel is the exact turned image of an upright one, whichever of 0/90/180/270 it is.
 * - The centre diamond sits where the arrangement says ([diamondCx], [diamondCy]): a far corner of
 *   some tiles, and for some counts the middle of one tile's far edge. Content near the far edge (the
 *   readout, the open drawer's number block, the KO emblem) keeps clear of it through [farReach].
 *
 * Panel content in the player's frame, left to right: edge, `−` glyph, gap, number slot, gap, `+`
 * glyph, edge. The number slot is exactly wide enough for [MAX_CHARS] glyphs (`−999`, `9999` — the
 * core life limits) at [kMax], so ± never move and a value never overlaps them; [numberK] still
 * drops k for anything wider (defensive, and for future faces). Above the number (towards the far
 * edge, the Lotus convention) sits the pending-delta [readoutSlot]; the drawer tab is on the near
 * edge; the centre diamond takes one far corner. T30/T32 add the [underRow] between number and tab, which
 * holds the commander-mode partner icons and the KO winner caption (counter peeks moved to the near
 * corners in T38, `peeks.PeekLayout`).
 *
 * Choice of k (by maths, not by eye; T27): the hero face is the sticker `num-l`, drawn natively at
 * k = 1 (32×56 box, `−999` = 140×56), and the ± are their own half-size face `num-l-pm` (20×20 box,
 * 17-px body) drawn at the *same* k, so `pmK == k`. [kMax] is the largest integer k such that
 * - horizontally `2·EDGE_MIN + 2·pmW(k) + 2·GAP_MIN + width(MAX_CHARS, k) ≤ panelW`
 *   (`= 32 + 180·k` dither px), and
 * - vertically `height(k) + 2·(tabH + V_MIN) ≤ panelH` (the number clears the drawer tab whichever
 *   way up the panel is).
 * On the Nomad (1872×1404) that gives k = 2 at S = 2 (panel 467 wide: 392 ≤ 467, k = 3 needs 572),
 * k = 1 at S = 3 and 4 (311 / 233 wide ≥ 212), and *nothing* at S = 5 and 6 (186 / 155 < 212): the
 * face has no smaller integer size, so those layouts fall back to k = 1; the ± glyphs are hidden
 * ([pmVisible] false) when they would touch the number. Falls back to k = 1 the same way on any tiny
 * screen.
 *
 * **Narrow tier (T11, 5 and 6 players).** A tile too narrow for ± beside `−999` at k = 1 but wide
 * enough for the number alone with [EDGE_NARROW] each side (and tall enough for the full vertical
 * stack) is still [comfortable]: the ± glyphs are not drawn (the halves still work), and features
 * that need the side room degrade on their own (peek corners off, two drawer columns, partner ±
 * hidden). [tier] reports which; [chooseScale] keeps the Nomad's S = 4 at every count.
 *
 * Chrome (tab, diamond, their glyphs) is sized by [Chrome.forScale]: designed in dither px at S = 4
 * and converted through device px, so its *physical* size stays roughly constant across S (a
 * fingertip target is a physical size). Every result is still a whole number of dither px.
 */
class PlayLayout private constructor(
    val grid: DitherGrid,
    val font: Font,
    /** Where the seats are (T11): the arrangement on this grid. */
    val seating: Seating,
    /** Face of the ± zone glyphs (half-size sticker `+`/`−`, T27). */
    val pmFont: Font,
    /** Tab / diamond sizes for this S. */
    val chrome: Chrome,
    /** Face of the pending-delta readout. */
    val readoutFont: Font,
) {
    /** Seats on this layout (2..6). Per-seat arrays here and in every component's layout are this long. */
    val playerCount: Int = seating.players

    val arrangement: Arrangement get() = seating.arrangement

    /** The player-frame tile, identical for every seat (a sideways seat's panel is it turned). */
    val panelW: Int = seating.frameW
    val panelH: Int = seating.frameH

    /** Top-left of the seats' bounding box inside the grid (leftover dither px before it). */
    val originX: Int
    val originY: Int

    /** Glyph scale of the hero numerals: the largest that fits ([maxK]), stepped down if the readout cannot clear the diamond. */
    val kMax: Int

    /** Glyph scale of the ± glyphs (face [pmFont]); equal to [kMax] since T27. */
    val pmK: Int

    /**
     * False when even k = 1 breaks the spacing minimums, or there is no room for the [underRow]
     * (screen too small at this S): content may touch, and peeks / partner icons are not drawn.
     */
    val comfortable: Boolean

    /** [TIER_FULL], [TIER_NARROW] or [TIER_NONE] (not [comfortable]). */
    val tier: Int

    /** False when the ± glyphs would touch the number (narrow tiles and uncomfortable layouts): they are not drawn. */
    val pmVisible: Boolean

    private val P = playerCount

    /** Screen rect of each panel (divider excluded). */
    val panels: Array<IRect> = Array(P) { IRect().apply { val p = seating.panels[it]; set(p.left, p.top, p.right, p.bottom) } }

    /** Rotation (degrees clockwise) of each seat's content: 180 far band, 0 near band, 90 / 270 the ends. */
    val rotation: IntArray = seating.rotation.copyOf()

    /** Rotations in use on this layout (distinct, ascending): what components prewarm their rotated glyphs for. */
    val rotations: IntArray = rotation.distinct().sorted().toIntArray()

    /** The table area each seat owns (an end seat's runs the bands' full height): the seat glyph's lattice (`seat/`). */
    val cells: Array<IRect> = Array(P) { IRect().apply { val p = seating.cells[it]; set(p.left, p.top, p.right, p.bottom) } }

    /**
     * The diamond's bounding box where it overlaps each seat's tile, in that seat's **player frame**
     * (empty when it does not touch the tile). See [farReach].
     */
    val diamondFrame: Array<IRect> = Array(P) { IRect() }

    /** Screen footprints of the `−` and `+` glyphs. */
    val minusGlyph: Array<IRect> = Array(P) { IRect() }
    val plusGlyph: Array<IRect> = Array(P) { IRect() }

    /** Screen rect the life total is drawn in (fits [MAX_CHARS] at [kMax]); invalidate this on a life change. */
    val numberSlot: Array<IRect> = Array(P) { IRect() }

    /**
     * Pending-delta readout (T07): screen rect fitting [READOUT_CHARS] of [readoutFont] at [readoutK],
     * centred above the number in the player's frame. Invalidate exactly this rect when the readout
     * changes or clears.
     */
    val readoutSlot: Array<IRect> = Array(P) { IRect() }

    /** Glyph scale of the readout (`num-m` at the hero's k: 1× beside the sticker, T37). */
    val readoutK: Int

    /** Player-frame top of [readoutSlot] (the readout's box, [READOUT_GAP]·k above the number). */
    val readoutY: Int

    /** Minimum hit size in dither px: ceil([MIN_TOUCH_DEVICE_PX] / S) (≈ 9 mm on the Nomad). */
    val target: Int = (MIN_TOUCH_DEVICE_PX + grid.scale - 1) / grid.scale

    /** Glyph/icon scale for small chrome content (icons, `ui`, `num-m`): physical size ≈ S = 4's. */
    val tk: Int = maxOf(1, Chrome.size(1, grid.scale))

    /** Paper rim around small content drawn on top of something else. */
    val halo: Int = Chrome.size(HALO, grid.scale)

    /**
     * The row under the number (player frame: between the number slot and the tab's hit area,
     * [V_MIN] clear of both), one icon tall plus a paper rim. (Counter peeks lived here until T38.) The commander
     * mode labels (T30) live here. Empty (zero size) when the band is too short for it.
     */
    val underRow: Array<IRect> = Array(P) { IRect() }

    /** Player-frame x / y / w / h of [underRow] (identical for every seat). */
    val underX: Int
    val underY: Int
    val underW: Int
    val underH: Int

    /** Drawer tab: visual rect and (larger) hit rect (the `drawer.DrawerTab` component's zone). */
    val tab: Array<IRect> = Array(P) { IRect() }
    val tabHit: Array<IRect> = Array(P) { IRect() }

    /** Tab glyph (`...`): three dots, screen rects. Index `seat * 3 + i`. */
    val tabDots: Array<IRect> = Array(P * 3) { IRect() }

    /** Paper backing behind each seat's three dots (one rect: the dots plus [Chrome.dotHalo] all round). */
    val tabDotsBacking: Array<IRect> = Array(P) { IRect() }

    /** Life halves, index [zone]`(seat, side)`: the player's left (`−`) or right (`+`) half of the panel. */
    val zones: Array<IRect> = Array(P * 2) { IRect() }

    /** Dotted dividers (1 dither px wide or tall), every one: drawn by `chrome.Dividers`. */
    val dividers: Array<IRect> = Array(seating.dividers.size) { val d = seating.dividers[it]; IRect(d.left, d.top, d.right, d.bottom) }

    /** The first vertical and the first horizontal divider (empty if none): the 2×2's two lines. */
    val dividerV: IRect = dividers.firstOrNull { it.width == 1 && it.height > 1 } ?: IRect()
    val dividerH: IRect = dividers.firstOrNull { it.height == 1 && it.width > 1 } ?: IRect()

    /** Centre diamond (menu button, T09): centre pixel, half-extent, tip half-width. */
    val diamondCx: Int = seating.diamondX
    val diamondCy: Int = seating.diamondY
    val diamondHalf: Int
    val diamondTip: Int

    /** Bounding rect of the diamond. */
    val diamond = IRect()

    /** Diamond glyph (3 vertical bars), screen rects, and their single paper backing. */
    val menuBars: Array<IRect> = Array(3) { IRect() }
    val menuBarsBacking = IRect()

    init {
        val c = chrome
        var ox = Int.MAX_VALUE
        var oy = Int.MAX_VALUE
        for (p in panels) { ox = minOf(ox, p.left); oy = minOf(oy, p.top) }
        originX = ox
        originY = oy

        // Diamond first: the readout keeps clear of it.
        diamondHalf = maxOf(1, minOf(c.diamondR - c.diamondCut, panelW, panelH))
        diamondTip = minOf(c.diamondCut, diamondHalf)
        diamond.set(diamondCx - diamondHalf, diamondCy - diamondHalf, diamondCx + diamondHalf + 1, diamondCy + diamondHalf + 1)
        for (s in 0 until P) toFrame(s, diamond, diamondFrame[s])

        // Readout (T37): the small numerals at the hero's own glyph scale (k = 1 beside a k = 1
        // sticker, so the same physical size at every S), attached to the number: its box sits
        // [READOUT_GAP]·k above the number slot's top (the hero's cap top), never closer than V_MIN to
        // the far edge. Stepped down only if its width would reach the diamond corner (GAP_MIN beyond
        // its reach) on either side, or its height would not fit above the number. T11: where the
        // diamond sits in the middle of a far edge, the readout must also clear its [farReach]
        // (V_MIN below it); failing every readout scale, the hero steps down a k. It also steps down
        // while the under row would not fit (a wide, short tile: 2 players at S = 2).
        var k = maxK(font, pmFont, panelW, panelH, c.tabH)
        var rk: Int
        var roY: Int
        var blocked: Boolean
        while (true) {
            val sy = (panelH - font.height(k)) / 2
            rk = k
            val clearW = panelW - 2 * (diamondHalf + GAP_MIN)
            while (rk > 1 && (readoutFont.height(rk) + 2 * V_MIN > sy || readoutFont.widthOf(READOUT_CHARS, rk) > clearW || readoutBlocked(k, rk, sy))) rk--
            roY = readoutTop(k, rk, sy)
            blocked = readoutBlocked(k, rk, sy)
            if ((!blocked && underFits(font, k, panelH, c, tk, halo)) || k == 1) break
            k--
        }
        kMax = k
        pmK = pmK(k)
        readoutK = rk
        readoutY = roY

        // Player-frame layout, identical for every seat.
        val pmW = pmFont.glyphWidth * pmK
        val pmH = pmFont.glyphHeight * pmK
        val slotW = font.widthOf(MAX_CHARS, kMax)
        val slotH = font.height(kMax)
        val side = (panelW - slotW) / 2 // space between panel edge and number slot
        val pmX = (side - pmW) / 2 // ± centred in that space
        val slotY = (panelH - slotH) / 2
        val pmY = pmCapAlignedY(slotY, kMax)
        pmVisible = pmX >= 0 && pmX + pmW <= side

        val roW = readoutFont.widthOf(READOUT_CHARS, rk)
        val roH = readoutFont.height(rk)
        val roX = (panelW - roW) / 2

        val tabW = minOf(c.tabW, panelW)
        val tabH = minOf(c.tabH, panelH)
        val tabX = (panelW - tabW) / 2
        val tabY = panelH - tabH
        val dotsW = 3 * c.dot + 2 * c.dotGap
        val dotX = tabX + (tabW - dotsW) / 2
        val dotY = tabY + (tabH - c.dot) / 2
        val half = panelW / 2

        // Under-number row: centred in the band between the number slot and the tab's hit top (V_MIN
        // clear of each). (The T30 commander button lives in the open drawer's bar, not on the tile.)
        val iconS = ICON * tk
        val rowH = iconS + 2 * halo
        val bandTop = slotY + slotH + V_MIN
        val bandBottom = tabY - c.tabHitPad - V_MIN
        if (bandBottom - bandTop >= rowH) {
            underH = rowH
            underY = bandTop + (bandBottom - bandTop - rowH) / 2
            underX = EDGE_MIN
            underW = panelW - 2 * EDGE_MIN
        } else {
            underH = 0; underY = 0; underX = 0; underW = 0
        }
        // The under row is required too: the partner slot icons live there.
        tier = when {
            underH <= 0 || blocked -> TIER_NONE
            fits(font, pmFont, kMax, panelW, panelH, c.tabH) -> TIER_FULL
            fitsNarrow(font, kMax, panelW, panelH, c.tabH) -> TIER_NARROW
            else -> TIER_NONE
        }
        comfortable = tier != TIER_NONE

        // The tab's hit pad, kept inside the tile (only a tile narrower than the padded tab clips it).
        val hitL = maxOf(0, tabX - c.tabHitPad)
        val hitR = minOf(panelW, tabX + tabW + c.tabHitPad)
        for (s in 0 until P) {
            map(s, pmX, pmY, pmW, pmH, minusGlyph[s])
            map(s, panelW - pmX - pmW, pmY, pmW, pmH, plusGlyph[s])
            map(s, side, slotY, slotW, slotH, numberSlot[s])
            map(s, roX, roY, roW, roH, readoutSlot[s])
            map(s, tabX, tabY, tabW, tabH, tab[s])
            map(s, hitL, tabY - c.tabHitPad, hitR - hitL, tabH + c.tabHitPad, tabHit[s])
            for (i in 0 until 3) map(s, dotX + i * (c.dot + c.dotGap), dotY, c.dot, c.dot, tabDots[s * 3 + i])
            map(s, dotX - c.dotHalo, dotY - c.dotHalo, dotsW + 2 * c.dotHalo, c.dot + 2 * c.dotHalo, tabDotsBacking[s])
            map(s, 0, 0, half, panelH, zones[zone(s, MINUS)])
            map(s, half, 0, panelW - half, panelH, zones[zone(s, PLUS)])
            map(s, underX, underY, underW, underH, underRow[s])
        }

        // Diamond: a stepped diamond, |dx| + |dy| ≤ R with the four tips cut flat (its box is set above).
        val barTop = diamondCy - c.barH / 2
        for (i in 0 until 3) menuBars[i].setXywh(diamondCx + (i - 1) * c.barPitch - c.barW / 2, barTop, c.barW, c.barH)
        menuBarsBacking.set(
            menuBars[0].left - c.barHalo, barTop - c.barHalo,
            menuBars[2].right + c.barHalo, barTop + c.barH + c.barHalo,
        )
    }

    /** Half-width (dither px each side of the centre column) of diamond row `dy` (−half..half). */
    fun diamondRowHalf(dy: Int): Int {
        val a = if (dy < 0) -dy else dy
        if (a > diamondHalf) return -1
        return minOf(diamondHalf + diamondTip - a, diamondHalf)
    }

    /**
     * How deep (player-frame y, from the far edge) the diamond reaches into any seat's tile across
     * player-frame columns `[x0, x1)`: the bottom of every [diamondFrame] box overlapping them, 0 if
     * none. The same for every seat because the tile is (content laid out once must clear it
     * wherever the diamond is: a far corner for a 2×2, the middle of a far edge for some counts).
     */
    fun farReach(x0: Int, x1: Int): Int {
        var r = 0
        for (d in diamondFrame) if (!d.isEmpty && d.left < x1 && x0 < d.right) r = maxOf(r, d.bottom)
        return r
    }

    /** Player-frame top of the readout box at readout scale [rk] above a number at [k] whose slot top is [slotY]. */
    private fun readoutTop(k: Int, rk: Int, slotY: Int): Int = maxOf(V_MIN, slotY - READOUT_GAP * k - readoutFont.height(rk))

    /** Whether that readout box comes within V_MIN (down) / GAP_MIN (across) of the diamond. */
    private fun readoutBlocked(k: Int, rk: Int, slotY: Int): Boolean {
        val w = readoutFont.widthOf(READOUT_CHARS, rk)
        val x = (panelW - w) / 2
        val reach = farReach(x - GAP_MIN, x + w + GAP_MIN)
        return reach > 0 && readoutTop(k, rk, slotY) < reach + V_MIN
    }

    /** Writes the part of screen rect [r] inside seat [s]'s panel, in that seat's player frame (empty if none). */
    fun toFrame(s: Int, r: IRect, out: IRect): IRect {
        val p = panels[s]
        val l = maxOf(r.left, p.left)
        val t = maxOf(r.top, p.top)
        val rr = minOf(r.right, p.right)
        val b = minOf(r.bottom, p.bottom)
        if (rr <= l || b <= t) return out.set(0, 0, 0, 0)
        val inv = (360 - rotation[s]) % 360
        val w = rr - l
        val h = b - t
        return out.setXywh(
            TextLayout.mapX(inv, p.width, p.height, l - p.left, t - p.top, w, h),
            TextLayout.mapY(inv, p.width, p.height, l - p.left, t - p.top, w, h),
            TextLayout.footprintW(inv, w, h),
            TextLayout.footprintH(inv, w, h),
        )
    }

    /** Top of the number slot in the player's frame (drawer closed). */
    val numberSlotY: Int get() = (panelH - font.height(kMax)) / 2

    /**
     * Player-frame y of the ± glyph box for a number slot at [slotY] sized for glyph scale [k]: the
     * ± cap band is centred on the number's cap band (both drawn at k).
     */
    fun pmCapAlignedY(slotY: Int, k: Int): Int {
        val capTop = slotY + font.capTop * k
        return capTop + TextLayout.centre(font.capHeight * k, pmFont.capHeight * k) - pmFont.capTop * k
    }

    /** Maps a player-frame rect of seat [s] to the screen (also used by the drawer layout, T08). */
    fun mapRect(s: Int, lx: Int, ly: Int, w: Int, h: Int, out: IRect) = map(s, lx, ly, w, h, out)

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

    /** Player-frame number slot width at glyph scale [k] (fits [MAX_CHARS]). */
    fun slotWidth(k: Int): Int = font.widthOf(MAX_CHARS, k)

    /** Largest glyph scale ≤ [kTop] at which [chars] glyphs fit a number slot sized for [kTop]. */
    fun numberK(chars: Int, kTop: Int = kMax): Int {
        val slotW = font.widthOf(MAX_CHARS, kTop)
        var k = kTop
        while (k > 1 && font.widthOf(chars, k) > slotW) k--
        return k
    }

    /**
     * Screen footprint (top-left + size) of [value] drawn for [seat]: centred in the number slot along
     * the player's reading direction, cap band centred across it. Writes [out] and returns the glyph
     * scale k to draw with. Draw it with `TextRenderer.drawInt(font, value, out.left, out.top, k, rotation[seat])`.
     */
    fun numberFootprint(seat: Int, value: Int, out: IRect): Int = numberFootprintAt(seat, value, kMax, numberSlotY, out)

    /**
     * [numberFootprint] for a number slot sized for glyph scale [kTop] whose top is at player-frame
     * y [slotY] (the drawer re-derives both when it is open, T08).
     */
    fun numberFootprintAt(seat: Int, value: Int, kTop: Int, slotY: Int, out: IRect): Int {
        val len = Font.intLength(value)
        val k = numberK(len, kTop)
        val tw = font.widthOf(len, k)
        val th = font.height(k)
        val slotW = font.widthOf(MAX_CHARS, kTop)
        val slotH = font.height(kTop)
        val side = (panelW - slotW) / 2
        val lx = side + TextLayout.centre(slotW, tw)
        // The box (not the cap band) is centred: the sticker's shadow rows below the cap band stay
        // inside the slot. With k == kTop it fills the slot exactly.
        val ly = slotY + TextLayout.centre(slotH, th)
        map(seat, lx, ly, tw, th, out)
        return k
    }

    /**
     * Screen footprint of the signed [delta] (`+12`, `-7`) in [seat]'s readout slot, centred the same
     * way as [numberFootprint]. Returns the glyph scale; draw with
     * `TextRenderer.drawInt(readoutFont, delta, out.left, out.top, k, rotation[seat], signed = true)`.
     */
    fun readoutFootprint(seat: Int, delta: Int, out: IRect): Int =
        readoutFootprintAt(seat, delta, readoutK, readoutY, out)

    /** [readoutFootprint] for a readout slot sized for [kTop] whose top is at player-frame y [slotY]. */
    fun readoutFootprintAt(seat: Int, delta: Int, kTop: Int, slotY: Int, out: IRect): Int {
        val f = readoutFont
        val len = Font.intLength(delta, signed = true)
        val slotW = f.widthOf(READOUT_CHARS, kTop)
        val slotH = f.height(kTop)
        var k = kTop
        while (k > 1 && f.widthOf(len, k) > slotW) k--
        val tw = f.widthOf(len, k)
        val th = f.height(k)
        val slotX = (panelW - slotW) / 2
        val lx = slotX + TextLayout.centre(slotW, tw)
        val ly = slotY + TextLayout.centre(slotH, f.capHeight * k) - f.capTop * k
        map(seat, lx, ly, tw, th, out)
        return k
    }

    /** True inside the diamond's touch area (the shape grown by [Chrome.diamondHitPad]; see [Diamond.hitBounds]). */
    fun inDiamondHit(gx: Int, gy: Int): Boolean {
        val dx = Math.abs(gx - diamondCx)
        val dy = Math.abs(gy - diamondCy)
        val pad = chrome.diamondHitPad
        val lim = diamondHalf + pad
        return dx <= lim && dy <= lim && dx + dy <= diamondHalf + diamondTip + pad
    }

    override fun toString(): String =
        "PlayLayout(${arrangement.id} grid ${grid.width}x${grid.height} S=${grid.scale}, origin $originX,$originY, panel ${panelW}x$panelH, " +
            "kMax $kMax, pmK $pmK${if (pmVisible) "" else " (hidden)"}, readoutK $readoutK, " +
            "under row $underX,$underY ${underW}x$underH, ${when (tier) { TIER_FULL -> ""; TIER_NARROW -> "NARROW, "; else -> "UNCOMFORTABLE, " }}$chrome)"

    /**
     * Sizes of the drawer tab and centre diamond for one S, in dither px. Designed at [BASE_SCALE]
     * (values in the companion) and converted via device px with rounding, so the physical size is
     * about the same at any S. At the Nomad's S = 4 they are exactly the base values.
     */
    class Chrome(
        val tabW: Int,
        val tabH: Int,
        /** Extra reserved touch margin around the tab (not on the panel edge side). */
        val tabHitPad: Int,
        /** `...` glyph: square dots [dot] wide, [dotGap] apart, on one paper backing [dotHalo] wider all round. */
        val dot: Int,
        val dotGap: Int,
        val dotHalo: Int,
        /** Diamond |dx|+|dy| ≤ R, tips cut flat by [diamondCut]. */
        val diamondR: Int,
        val diamondCut: Int,
        val diamondHitPad: Int,
        /** Menu glyph: three [barW]×[barH] bars, [barPitch] apart centre to centre, on one backing [barHalo] wider. */
        val barW: Int,
        val barH: Int,
        val barPitch: Int,
        val barHalo: Int,
    ) {
        override fun toString(): String =
            "Chrome(tab ${tabW}x$tabH +$tabHitPad, dots $dot/$dotGap/$dotHalo, diamond R$diamondR cut $diamondCut +$diamondHitPad, " +
                "bars ${barW}x$barH @$barPitch/$barHalo)"

        companion object {
            /** The S the base values are drawn at (the Nomad's auto scale). */
            const val BASE_SCALE = 4

            /** Tab ≈ 1.6× the T06 55×12 (Figma 220×47 device px); hit area 29 dither px deep ≈ 9.8 mm at 300 dpi. */
            const val TAB_W = 89
            const val TAB_H = 21
            const val TAB_HIT_PAD = 8
            const val DOT = 3
            const val DOT_GAP = 3
            const val DOT_HALO = 2

            /** Diamond: R 24, cut 4 → 41×41 with 9-px flat tips (T06: 23×23); hit area 49 across ≈ 16 mm. */
            const val DIAMOND_R = 24
            const val DIAMOND_CUT = 4
            const val DIAMOND_HIT_PAD = 4
            const val BAR_W = 3
            const val BAR_H = 15
            const val BAR_PITCH = 6
            const val BAR_HALO = 2

            /** [base] dither px at [BASE_SCALE] → nearest whole dither px at [s], at least [min]. */
            fun size(base: Int, s: Int, min: Int = 1): Int = maxOf(min, (base * BASE_SCALE + s / 2) / s)

            /** As [size], forced odd (so a bar centres exactly on a pixel column). */
            fun oddSize(base: Int, s: Int): Int = size(base, s).let { if (it % 2 == 0) it - 1 else it }.coerceAtLeast(1)

            fun forScale(s: Int): Chrome {
                val dot = size(DOT, s)
                val barW = oddSize(BAR_W, s)
                return Chrome(
                    tabW = size(TAB_W, s, 8),
                    tabH = size(TAB_H, s, 4),
                    tabHitPad = size(TAB_HIT_PAD, s),
                    dot = dot,
                    dotGap = size(DOT_GAP, s),
                    dotHalo = size(DOT_HALO, s),
                    diamondR = size(DIAMOND_R, s, 4),
                    diamondCut = size(DIAMOND_CUT, s),
                    diamondHitPad = size(DIAMOND_HIT_PAD, s),
                    barW = barW,
                    barH = size(BAR_H, s, 3),
                    barPitch = maxOf(barW + 1, size(BAR_PITCH, s)),
                    barHalo = size(BAR_HALO, s),
                )
            }
        }
    }

    companion object {
        /** The default player count (a fresh install; the T06 2×2). */
        const val PLAYERS = 4

        /** Most seats any layout has: per-seat capacity of components that are built once (zones, timers). */
        const val MAX_PLAYERS = Arrangements.MAX_PLAYERS

        /** [tier]: ± beside the number; ± not drawn (narrow tiles, T11); not comfortable. */
        const val TIER_FULL = 2
        const val TIER_NARROW = 1
        const val TIER_NONE = 0

        /** Paper each side of the number slot on a narrow tile (T11). */
        const val EDGE_NARROW = 4

        /** Widest life total: `−999` / `9999` (core `Limits` life range). */
        const val MAX_CHARS = 4

        /** Widest pending delta: a sign plus five digits (`+10998` is the largest life swing in core limits). */
        const val READOUT_CHARS = 6

        /** Minimum dither px between the panel edge and a ± glyph, and between ± and the number. */
        const val EDGE_MIN = 8
        const val GAP_MIN = 8

        /** Minimum dither px between the number and the tab band, above and below (and around the readout). */
        const val V_MIN = 4

        /** Paper between the readout's box and the hero number's cap top, per glyph scale k (T37). */
        const val READOUT_GAP = 6

        /** 9 mm at 300 dpi (the Nomad): the fingertip minimum for every hit rect. */
        const val MIN_TOUCH_DEVICE_PX = 106

        /** Icon art size (16×16) and small-chrome design values at S = 4 (converted per S like [Chrome]). */
        const val ICON = 16
        const val HALO = 1

        const val MINUS = 0
        const val PLUS = 1

        /** "No zone" (dividers, margins, blocked areas), as `stage.PlayInput` reports it. */
        const val NONE = -1

        /** Index into [zones] of [seat]'s life half [side] ([MINUS] / [PLUS]). */
        fun zone(seat: Int, side: Int): Int = seat * 2 + side

        /**
         * The layout for [players] seats (2..6) on [grid]: [layoutId] picks an alternate arrangement
         * ([Arrangements.of]; empty = the default for the count). Portrait grids get the table turned.
         */
        fun compute(
            grid: DitherGrid,
            players: Int = PLAYERS,
            layoutId: String = "",
            font: Font = Fonts.NUM_L,
            readoutFont: Font = Fonts.NUM_M,
            pmFont: Font = Fonts.NUM_L_PM,
        ): PlayLayout {
            val chrome = Chrome.forScale(grid.scale)
            val a = Arrangements.of(players, layoutId)
            val seating = Seating(a, grid.width, grid.height) { fw, fh -> tierOf(font, pmFont, chrome, grid.scale, fw, fh) }
            return PlayLayout(grid, font, seating, pmFont, chrome, readoutFont)
        }

        /**
         * The scale S for the Play screen: [DitherGrid.chooseScale] (the user's override wins), then
         * stepped down (not below [DitherGrid.MIN_SCALE]) while the layout for [players] would not be [comfortable].
         */
        fun chooseScale(deviceW: Int, deviceH: Int, densityDpi: Int, override: Int = DitherGrid.AUTO, players: Int = PLAYERS, layoutId: String = ""): Int {
            var s = DitherGrid.chooseScale(deviceW, deviceH, densityDpi, override)
            if (override != DitherGrid.AUTO) return s
            while (s > DitherGrid.MIN_SCALE && !compute(DitherGrid(deviceW, deviceH, s), players, layoutId).comfortable) s--
            return s
        }

        /**
         * The comfort tier of a [fw]×[fh] tile at scale [s] (what [Seating] maximises): [TIER_FULL] when
         * ± fit beside the number at its largest k and the under row fits, [TIER_NARROW] when the number
         * alone fits at k = 1 with the under row, else [TIER_NONE]. Ignores the diamond (placed later).
         */
        fun tierOf(font: Font, pmFont: Font, chrome: Chrome, s: Int, fw: Int, fh: Int): Int {
            val tk = maxOf(1, Chrome.size(1, s))
            val halo = Chrome.size(HALO, s)
            var k = maxK(font, pmFont, fw, fh, chrome.tabH)
            while (k > 1 && !underFits(font, k, fh, chrome, tk, halo)) k--
            return when {
                fits(font, pmFont, k, fw, fh, chrome.tabH) && underFits(font, k, fh, chrome, tk, halo) -> TIER_FULL
                fitsNarrow(font, 1, fw, fh, chrome.tabH) && underFits(font, 1, fh, chrome, tk, halo) -> TIER_NARROW
                else -> TIER_NONE
            }
        }

        /** Whether the under-number row (an icon plus its halo) fits between a number at [k] and the tab band. */
        internal fun underFits(font: Font, k: Int, fh: Int, c: Chrome, tk: Int, halo: Int): Boolean {
            val slotH = font.height(k)
            val slotY = (fh - slotH) / 2
            val tabY = fh - minOf(c.tabH, fh)
            return (tabY - c.tabHitPad - V_MIN) - (slotY + slotH + V_MIN) >= ICON * tk + 2 * halo
        }

        /** Glyph scale of the ± for a number at [k]: the same k (their face is already half-size, T27). */
        fun pmK(k: Int): Int = k

        /** Largest k that fits (see class doc), or 1. */
        fun maxK(font: Font, pmFont: Font, panelW: Int, panelH: Int, tabH: Int): Int {
            var best = 1
            for (k in 1..Font.MAX_K) if (fits(font, pmFont, k, panelW, panelH, tabH)) best = k else break
            return best
        }

        /** The narrow tier's fit: [MAX_CHARS] at [k] with [EDGE_NARROW] each side, the full vertical stack. */
        fun fitsNarrow(font: Font, k: Int, panelW: Int, panelH: Int, tabH: Int): Boolean =
            2 * EDGE_NARROW + font.widthOf(MAX_CHARS, k) <= panelW && font.height(k) + 2 * (tabH + V_MIN) <= panelH

        fun fits(font: Font, pmFont: Font, k: Int, panelW: Int, panelH: Int, tabH: Int): Boolean {
            val w = 2 * EDGE_MIN + 2 * pmFont.glyphWidth * pmK(k) + 2 * GAP_MIN + font.widthOf(MAX_CHARS, k)
            val h = font.height(k) + 2 * (tabH + V_MIN)
            return w <= panelW && h <= panelH
        }
    }
}
