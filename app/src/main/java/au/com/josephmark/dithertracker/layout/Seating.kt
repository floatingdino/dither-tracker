package au.com.josephmark.dithertracker.layout

import au.com.josephmark.dithertracker.dither.IRect

/**
 * An [Arrangement] placed on a grid of [gridW]×[gridH] dither px (T11): one tile size for every seat,
 * each seat's panel rect and rotation, the dotted dividers and the diamond's centre. Pure integer
 * maths, no Android types; built once per layout (allocates).
 *
 * **One tile per layout.** Every seat's content is laid out in the same player frame, [frameW] ×
 * [frameH] (x to the player's right, y towards the player). A long-edge seat's panel is that size on
 * screen; an end seat's panel is the same tile turned sideways ([frameH] wide, [frameW] tall). So
 * every component lays a tile out once and maps it per seat with the seat's [rotation] — exactly as the
 * 2×2 always did — and every player gets the same numerals, drawer and tray.
 *
 * **Table frame.** The arrangement is placed on the grid's landscape view (`TW = max(w, h)` wide,
 * `TH = min(w, h)` tall): from left to right the left end (if any), a 1-px divider, the bands region
 * [G] wide (the far band over the near band, a 1-px divider between them), a divider, the right end.
 * End panels are [frameW] tall, centred on the bands. Within a band the tiles sit side by side with
 * 1-px dividers, the group centred in [G]; the whole composition is centred in the grid, odd px
 * after (right / bottom), like `DitherGrid`.
 *
 * **Portrait.** A portrait grid (`gridH > gridW`) gets the same table turned 90° clockwise: rect
 * `(x, y, w, h)` → `(TH − y − h, x, h, w)`, every seat's rotation + 90. The players have not moved;
 * the tablet has. (The app is landscape-locked; this keeps unlocking a one-line change.)
 *
 * **Tile size.** For each candidate tile height `fh` the tile width `fw` is the largest that fits
 * (bands share the region left by the ends, ends are `fh` wide and at most `TH` tall); the chosen
 * `(fw, fh)` maximises [rank] (the caller's comfort tier for a tile that size), then area, then
 * height. For a 2×2 that is `fw = (TW − 1) / 2`, `fh = (TH − 1) / 2`: the T06 maths, unchanged.
 *
 * **Seat order:** clockwise from the far-left: far band left to right, right end, near band right
 * to left, left end. (4 seats: 0 TL, 1 TR, 2 BR, 3 BL, as before.)
 */
class Seating(
    val arrangement: Arrangement,
    val gridW: Int,
    val gridH: Int,
    /**
     * How far the centre control reaches from its centre once open (the table menu's gem,
     * `PlayLayout.menuReach`): a one-sided arrangement (T53) keeps the diamond at least this far from
     * the far edge of the grid, so the gem opens whole. The other arrangements put it on a long
     * divider or the middle of the table, which is always far enough in.
     */
    diamondReach: Int = 0,
    rank: (fw: Int, fh: Int) -> Int = { _, _ -> 0 },
) {
    val players: Int = arrangement.players

    /** True when the table is turned 90° to fit a portrait grid. */
    val portrait: Boolean = gridH > gridW

    /**
     * The player-frame content tile, identical for every seat: every component lays its content out in
     * it once (T60: on a lattice arrangement with seats of several sizes, the largest frame every seat
     * holds — see [LatticeSeating]).
     */
    val frameW: Int
    val frameH: Int

    /** Screen rect of each seat's content frame (turned for sideways seats); the seat's whole tile is [seats]. */
    val panels: Array<IRect> = Array(players) { IRect() }

    /** Clockwise rotation of each seat's content: 0 near band, 180 far band, 90 left end, 270 right end (+ 90 in portrait). */
    val rotation = IntArray(players)

    /**
     * The table area each seat owns (screen): the panel, except that an end seat's runs the whole
     * height of the bands (its leftover margin is its own). Used for the seat glyph (`seat/`).
     */
    val cells: Array<IRect> = Array(players) { IRect() }

    /** Dotted 1-px dividers (screen): those that run vertically in the table frame first, then the long one. */
    val dividers: Array<IRect>

    /** Diamond centre pixel (screen). */
    val diamondX: Int
    val diamondY: Int

    /** Region of the bands (table frame, then mapped): for tests and documentation. */
    val bandsWidth: Int

    /**
     * The whole rect of each seat's tile (screen, T60). With equal tiles it is [panels]; on a lattice
     * arrangement a bigger seat's tile holds the game's content frame ([panels]) against its near edge,
     * centred along it, with paper round it. Touch (the life halves) and the seat glyph use this.
     */
    val seats: Array<IRect> = Array(players) { IRect() }

    /** Empty cells of a lattice arrangement (screen, T60): part of the table with no seat. */
    val empties: Array<IRect>

    init {
        val lat = arrangement.lattice
        if (lat != null) {
            // T60: a lattice arrangement (seats of their own sizes, empty cells): see [LatticeSeating].
            val r = LatticeSeating(lat, arrangement.diamond, gridW, gridH, diamondReach)
            frameW = r.frameW
            frameH = r.frameH
            for (s in 0 until players) {
                r.panels[s].let { panels[s].set(it.left, it.top, it.right, it.bottom) }
                r.seats[s].let { seats[s].set(it.left, it.top, it.right, it.bottom); cells[s].set(it.left, it.top, it.right, it.bottom) }
                rotation[s] = r.rotation[s]
            }
            dividers = r.dividers
            empties = r.empties
            diamondX = r.diamondX
            diamondY = r.diamondY
            bandsWidth = 0
        } else {
            val a = arrangement
            val tw = maxOf(gridW, gridH)
            val th = minOf(gridW, gridH)
            val eL = if (a.left) 1 else 0
            val eR = if (a.right) 1 else 0
            val ends = eL + eR
            val rows = a.rows
            require(a.players >= 1) { "no seats" }

            // ---- tile size ------------------------------------------------------------------------------
            var bestW = 1
            var bestH = 1
            var bestRank = Int.MIN_VALUE
            var bestArea = -1L
            val fhMax = if (rows > 0) maxOf(1, (th - (rows - 1)) / rows) else maxOf(1, (tw - (ends - 1)) / ends)
            for (fh in fhMax downTo 1) {
                val fw = widthFor(a, tw, th, fh)
                if (fw < 1) continue
                val r = rank(fw, fh)
                val area = fw.toLong() * fh
                if (r > bestRank || (r == bestRank && area > bestArea)) {
                    bestRank = r; bestArea = area; bestW = fw; bestH = fh
                }
            }
            frameW = bestW
            frameH = bestH
            val fw = frameW
            val fh = frameH

            // ---- placement in the table frame -----------------------------------------------------------
            fun group(c: Int) = if (c > 0) c * fw + (c - 1) else 0
            val g = maxOf(group(a.top), group(a.bottom))
            bandsWidth = g
            val bandsH = if (rows > 0) rows * fh + (rows - 1) else 0
            val usedW = if (rows > 0) eL * (fh + 1) + g + eR * (fh + 1) else ends * fh + (ends - 1)
            val usedH = maxOf(bandsH, if (ends > 0) fw else 0)
            val ox = maxOf(0, (tw - usedW) / 2)
            val oy = maxOf(0, (th - usedH) / 2)
            val bandX = ox + eL * (fh + 1)
            val bandY = oy + (usedH - bandsH) / 2

            val tp = Array(players) { IRect() } // panels, table frame
            val tc = Array(players) { IRect() } // cells, table frame
            val rot = IntArray(players)
            var seat = 0
            val farY = bandY
            val nearY = bandY + if (a.top > 0) fh + 1 else 0
            // Far band, left to right.
            val farX = bandX + (g - group(a.top)) / 2
            for (i in 0 until a.top) {
                tp[seat].setXywh(farX + i * (fw + 1), farY, fw, fh); rot[seat] = 180; seat++
            }
            val endY = oy + (usedH - fw) / 2
            if (a.right) {
                val x = if (rows > 0) bandX + g + 1 else ox + eL * (fh + 1)
                tp[seat].setXywh(x, endY, fh, fw); rot[seat] = 270
                tc[seat].setXywh(x, oy, fh, usedH); seat++
            }
            // Near band, right to left.
            val nearX = bandX + (g - group(a.bottom)) / 2
            for (i in a.bottom - 1 downTo 0) {
                tp[seat].setXywh(nearX + i * (fw + 1), nearY, fw, fh); rot[seat] = 0; seat++
            }
            if (a.left) {
                tp[seat].setXywh(ox, endY, fh, fw); rot[seat] = 90
                tc[seat].setXywh(ox, oy, fh, usedH); seat++
            }
            for (s in 0 until players) if (tc[s].isEmpty) tc[s].set(tp[s].left, tp[s].top, tp[s].right, tp[s].bottom)

            // ---- dividers -------------------------------------------------------------------------------
            val divs = ArrayList<IRect>()
            val farXs = IntArray(maxOf(0, a.top - 1)) { farX + (it + 1) * (fw + 1) - 1 }
            val nearXs = IntArray(maxOf(0, a.bottom - 1)) { nearX + (it + 1) * (fw + 1) - 1 }
            for (x in farXs) {
                if (rows == 2 && x in nearXs) divs += IRect().setXywh(x, bandY, 1, bandsH) // one line through the crossing
                else divs += IRect().setXywh(x, farY, 1, fh)
            }
            for (x in nearXs) if (rows < 2 || x !in farXs) divs += IRect().setXywh(x, nearY, 1, fh)
            val endDivs = ArrayList<Int>()
            if (rows > 0) {
                if (a.left) endDivs += bandX - 1
                if (a.right) endDivs += bandX + g
            } else if (ends == 2) {
                endDivs += ox + fh
            }
            for (x in endDivs) divs += IRect().setXywh(x, oy, 1, usedH)
            val longY = bandY + fh
            if (rows == 2) divs += IRect().setXywh(bandX, longY, g, 1)

            // ---- diamond --------------------------------------------------------------------------------
            var dx: Int
            var dy: Int
            if (rows == 1 && a.top == 0 && a.bottom > 0) {
                // Everyone on the near side (T53): no long divider. The diamond sits on the band's far
                // edge — pushed down the divider until the open menu fits the grid — at the divider
                // nearest the middle (the far corners of the tiles either side).
                dx = bandX + g / 2
                dy = maxOf(bandY, diamondReach)
                if (a.diamond == Arrangement.DiamondAt.JUNCTION) {
                    val mid2 = 2 * bandX + g
                    var best = Int.MAX_VALUE
                    for (x in nearXs + endDivs.toIntArray()) {
                        val d = Math.abs(2 * x - mid2)
                        if (d < best || (d == best && x < dx)) { best = d; dx = x }
                    }
                }
            } else if (rows == 2) {
                dx = bandX + g / 2
                dy = longY
                if (a.diamond == Arrangement.DiamondAt.JUNCTION) {
                    val mid2 = 2 * bandX + g // twice the middle, to compare half-px exactly
                    var best = Int.MAX_VALUE
                    fun consider(x: Int) {
                        val d = Math.abs(2 * x - mid2)
                        if (d < best || (d == best && x < dx)) { best = d; dx = x }
                    }
                    for (x in farXs) consider(x)
                    for (x in nearXs) consider(x)
                    for (x in endDivs) consider(x)
                }
            } else {
                dx = ox + usedW / 2
                dy = oy + usedH / 2
            }

            // ---- to the screen --------------------------------------------------------------------------
            for (s in 0 until players) {
                toScreen(tp[s], th, panels[s])
                toScreen(tc[s], th, cells[s])
                rotation[s] = if (portrait) (rot[s] + 90) % 360 else rot[s]
            }
            dividers = Array(divs.size) { toScreen(divs[it], th, IRect()) }
            if (portrait) {
                diamondX = th - 1 - dy
                diamondY = dx
            } else {
                diamondX = dx
                diamondY = dy
            }
            for (s in 0 until players) panels[s].let { seats[s].set(it.left, it.top, it.right, it.bottom) }
            empties = arrayOf()
        }
    }

    private fun toScreen(r: IRect, th: Int, out: IRect): IRect =
        if (!portrait) out.set(r.left, r.top, r.right, r.bottom)
        else out.setXywh(th - r.top - r.height, r.left, r.height, r.width)

    override fun toString(): String =
        "Seating(${arrangement.id} on ${gridW}x$gridH${if (portrait) " portrait" else ""}: tile ${frameW}x$frameH, " +
            "diamond $diamondX,$diamondY, panels ${panels.joinToString { "${it.left},${it.top} ${it.width}x${it.height}@${rotation[panels.indexOf(it)]}" }})"

    companion object {
        /**
         * Widest tile for tile height [fh] (0 if none fits): bands share what the ends leave, an end
         * tile is [fh] wide and its width runs along the short side (at most [th]).
         */
        fun widthFor(a: Arrangement, tw: Int, th: Int, fh: Int): Int {
            val ends = a.ends
            if (a.rows > 0) {
                if (a.rows * fh + (a.rows - 1) > th) return 0
                val m = tw - ends * (fh + 1)
                if (m < 1) return 0
                var fw = Int.MAX_VALUE
                if (a.top > 0) fw = minOf(fw, (m - (a.top - 1)) / a.top)
                if (a.bottom > 0) fw = minOf(fw, (m - (a.bottom - 1)) / a.bottom)
                if (ends > 0) fw = minOf(fw, th)
                return maxOf(0, fw)
            }
            if (ends * fh + (ends - 1) > tw) return 0
            return th
        }
    }
}
