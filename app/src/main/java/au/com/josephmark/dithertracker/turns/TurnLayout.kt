package au.com.josephmark.dithertracker.turns

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * Where the turn tab goes (T17). Pure maths, JVM-tested; laid out in each seat's player frame and
 * mapped with [PlayLayout.mapRect], so a seat turned 180° or sideways gets the same tab facing its player.
 *
 * ```
 *   far edge (towards the middle of the table; the centre diamond touches it somewhere)
 *  ┌───────────[ ▶| ]───────────┐   the tab: the drawer tab's size, solid ink, flush with the far edge
 *                [2:07]                for 5 s after a pass (a timer mode on): the report instead
 *  │          readout           │
 *  │  −       number         +  │
 *  │ [peek]  [badges]   [peek]  │
 *  │ [peek]   ┌─ tab ─┐  [peek] │
 *  └──────────┴───────┴─────────┘
 *   near edge (the player)
 * ```
 * - **Tab**: [PlayLayout.Chrome.tabW] × `tabH` (89 × 21 at S = 4, the drawer tab's size, so the two
 *   read as one family), flush with the far edge, in the strip between that edge and the readout
 *   ([PlayLayout.readoutY] less [PlayLayout.V_MIN], with its hit pad). Centred on the tile when that
 *   clears the diamond by [PlayLayout.GAP_MIN]; otherwise (the diamond mid far edge: 2 players, the
 *   3-player end seat, the middle seats at 5 and 6) it sits in the wider run beside the diamond,
 *   against the diamond's side, narrowed to the run (45 px on the 6-player narrow tile). Per seat,
 *   because the diamond touches each tile in a different place.
 * - **Hit**: the tab grown by the drawer tab's hit pad across and towards the tile (never past the
 *   tile's edges): 105 × 29 at S = 4, ≈ 35 × 10 mm.
 * - **On a knocked-out seat** ([NEAR]): the dead tile's emblem takes the far edge's middle, so the tab
 *   drops to the near edge, exactly where the drawer tab was (the KO tile hides that one): the
 *   drawer tab's own rect and hit ([PlayLayout.tab] / [PlayLayout.tabHit]).
 * - **Content**: the `turn` icon (▶|) in paper, centred ([icon]). For [TurnTab.REPORT_MS] after a
 *   pass with a timer mode on (T66), the report instead — `m:ss` / `h:mm:ss` ([TimeFormat]) alone on
 *   the tab, centred, in `num-m` where it fits and the `ui` face where it does not (the 45 px tabs)
 *   ([reportFont], [report]).
 */
class TurnLayout(val play: PlayLayout) {

    val font: Font = Fonts.NUM_M

    /** The report's face where `num-m` does not fit (T66). */
    val small: Font = Fonts.UI
    val tk: Int = play.tk
    val iconSize: Int = PlayLayout.ICON * tk
    private val s = play.grid.scale
    val pad: Int = Chrome.size(PAD, s)

    private val P = play.playerCount
    private val W = play.panelW

    /** Far tab height and its hit pad (player frame), the same on every seat. */
    val tabH: Int
    val hitPad: Int

    /** Player-frame x and width of each seat's far tab. */
    val tabX = IntArray(P)
    val tabW = IntArray(P)

    /** Screen rects of the far tab (draw and press feedback) and its touch area. */
    val tab: Array<IRect> = Array(P) { IRect() }
    val hit: Array<IRect> = Array(P) { IRect() }

    /**
     * The [NEAR] placement (a knocked-out seat): the drawer tab's place and touch area — its rect, made
     * at least the icon's height (S = 5, 6: the chrome tab is shorter than a 16 px icon).
     */
    private val nearW = minOf(play.chrome.tabW, W)
    private val nearH = minOf(maxOf(play.chrome.tabH, iconSize), play.panelH)
    val nearTab: Array<IRect> = Array(P) { IRect() }
    val nearHit: Array<IRect> get() = play.tabHit
    private val nearX = (W - nearW) / 2
    private val nearY = play.panelH - nearH

    /** Rect of [seat]'s tab at placement [place]. */
    fun tabAt(place: Int, seat: Int): IRect = if (place == NEAR) nearTab[seat] else tab[seat]
    fun hitAt(place: Int, seat: Int): IRect = if (place == NEAR) nearHit[seat] else hit[seat]

    /** False when the far strip cannot hold a tab with its content (never on a comfortable layout). */
    val enabled: Boolean

    init {
        val c = play.chrome
        val strip = play.readoutY - PlayLayout.V_MIN // the far strip's depth, readout excluded
        // The drawer tab's height, at least the icon's (S = 5, 6 make the chrome tab shorter than a
        // 16 px icon), and its hit pad from what the strip has left.
        tabH = minOf(maxOf(c.tabH, iconSize), strip)
        hitPad = minOf(c.tabHitPad, maxOf(0, strip - tabH))
        var ok = tabH >= iconSize && tabH >= font.height(tk)
        for (seat in 0 until P) {
            placeX(seat, minOf(c.tabW, W - 2 * EDGE))
            if (tabW[seat] < minContent()) ok = false
            play.mapRect(seat, tabX[seat], 0, tabW[seat], tabH, tab[seat])
            val hl = maxOf(0, tabX[seat] - hitPad)
            val hr = minOf(W, tabX[seat] + tabW[seat] + hitPad)
            play.mapRect(seat, hl, 0, hr - hl, tabH + hitPad, hit[seat])
        }
        for (seat in 0 until P) play.mapRect(seat, nearX, nearY, nearW, nearH, nearTab[seat])
        enabled = ok && tabH > 0
    }

    /** Narrowest tab worth drawing: the icon, and the longest report ([TimeFormat.MAX_CHARS]) in the small face, with their pads. */
    private fun minContent(): Int = 2 * pad + maxOf(iconSize, small.widthOf(TimeFormat.MAX_CHARS, tk))

    /** Centred when clear of the diamond; else against the diamond in the wider free run. */
    private fun placeX(seat: Int, want: Int) {
        val d = play.diamondFrame[seat]
        val cx = (W - want) / 2
        if (d.isEmpty || cx + want + PlayLayout.GAP_MIN <= d.left || d.right + PlayLayout.GAP_MIN <= cx) {
            tabX[seat] = cx; tabW[seat] = want
            return
        }
        val leftRun = (d.left - PlayLayout.GAP_MIN) - EDGE
        val rightRun = (W - EDGE) - (d.right + PlayLayout.GAP_MIN)
        if (rightRun >= leftRun) {
            tabW[seat] = maxOf(0, minOf(want, rightRun))
            tabX[seat] = d.right + PlayLayout.GAP_MIN
        } else {
            tabW[seat] = maxOf(0, minOf(want, leftRun))
            tabX[seat] = d.left - PlayLayout.GAP_MIN - tabW[seat]
        }
    }

    private fun widthAt(place: Int, seat: Int) = if (place == NEAR) nearW else tabW[seat]

    /** The icon's footprint on [seat]'s tab at [place] (screen), centred. */
    fun icon(seat: Int, place: Int, out: IRect): IRect {
        val near = place == NEAR
        val h = if (near) nearH else tabH
        val x = (if (near) nearX else tabX[seat]) + (widthAt(place, seat) - iconSize) / 2
        play.mapRect(seat, x, (if (near) nearY else 0) + (h - iconSize) / 2, iconSize, iconSize, out)
        return out
    }

    /**
     * The face a [chars]-glyph time report uses on [seat]'s tab at [place] (T66): `num-m`, the
     * readout's numerals, when it fits with its pads; else the small `ui` face (the 45 px tabs beside a
     * mid-edge diamond). The report replaces the icon for its few seconds, so it has the whole tab.
     */
    fun reportFont(seat: Int, place: Int, chars: Int): Font =
        if (2 * pad + font.widthOf(chars, tk) <= widthAt(place, seat)) font else small

    /** Writes the footprint of a [chars]-glyph report in [f] on [seat]'s tab at [place] (screen), centred. */
    fun report(seat: Int, place: Int, chars: Int, f: Font, out: IRect): IRect {
        val near = place == NEAR
        val h = if (near) nearH else tabH
        val w = f.widthOf(chars, tk)
        val x = (if (near) nearX else tabX[seat]) + (widthAt(place, seat) - w) / 2
        val y = (if (near) nearY else 0) + TextLayout.centre(h, f.capHeight * tk) - f.capTop * tk
        play.mapRect(seat, x, y, w, f.height(tk), out)
        return out
    }

    override fun toString(): String =
        "TurnLayout(S=$s ${if (enabled) "" else "DISABLED "}tab h $tabH +$hitPad, x/w ${(0 until P).joinToString { "${tabX[it]}/${tabW[it]}" }})"

    companion object {
        /** Paper inside the tab's ends (design px at S = 4). */
        const val PAD = 2

        /** Paper between a tab pushed aside by the diamond and the tile's side edge. */
        const val EDGE = PlayLayout.V_MIN

        /** Placements: the far edge (normal), the near edge (a knocked-out seat). */
        const val FAR = 0
        const val NEAR = 1

    }
}
