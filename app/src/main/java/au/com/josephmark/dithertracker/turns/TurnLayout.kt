package au.com.josephmark.dithertracker.turns

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts

/**
 * Where the turn tab goes (T17). Pure maths, JVM-tested; laid out in each seat's player frame and
 * mapped with [PlayLayout.mapRect], so a seat turned 180° or sideways gets the same tab facing its player.
 *
 * ```
 *   far edge (towards the middle of the table; the centre diamond touches it somewhere)
 *  ┌──────────[▶| 12]──────────┐   the tab: the drawer tab's size, solid ink, flush with the far edge
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
 * - **Content** ([content]): the `turn` icon (▶|) in paper; with the timer on, the minutes in paper
 *   `num-m` beside it. Where the tab is too narrow for both (the 45 px runs), the minutes alone; the
 *   choice is made for three digits, so it never flips as the minutes grow. The group is centred.
 */
class TurnLayout(val play: PlayLayout) {

    val font: Font = Fonts.NUM_M
    val tk: Int = play.tk
    val iconSize: Int = PlayLayout.ICON * tk
    private val s = play.grid.scale
    val pad: Int = Chrome.size(PAD, s)
    val gap: Int = Chrome.size(GAP, s)

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

    /** The [NEAR] placement (a knocked-out seat): the drawer tab's rects, player-frame x / y / w / h. */
    val nearTab: Array<IRect> get() = play.tab
    val nearHit: Array<IRect> get() = play.tabHit
    private val nearW = minOf(play.chrome.tabW, W)
    private val nearH = minOf(play.chrome.tabH, play.panelH)
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
        hitPad = minOf(c.tabHitPad, maxOf(0, strip - c.tabH))
        tabH = minOf(c.tabH, strip - hitPad)
        var ok = tabH >= iconSize + 2 && tabH >= font.height(tk) + 2
        for (seat in 0 until P) {
            placeX(seat, minOf(c.tabW, W - 2 * EDGE))
            if (tabW[seat] < minContent()) ok = false
            play.mapRect(seat, tabX[seat], 0, tabW[seat], tabH, tab[seat])
            val hl = maxOf(0, tabX[seat] - hitPad)
            val hr = minOf(W, tabX[seat] + tabW[seat] + hitPad)
            play.mapRect(seat, hl, 0, hr - hl, tabH + hitPad, hit[seat])
        }
        enabled = ok && tabH > 0
    }

    /** Narrowest tab worth drawing: the icon, or three digits, with their pads. */
    private fun minContent(): Int = 2 * pad + minOf(iconSize, font.widthOf(MAX_DIGITS, tk))

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

    /** Width of [digits] minutes digits. */
    fun digitsWidth(digits: Int): Int = font.widthOf(digits, tk)

    private fun widthAt(place: Int, seat: Int) = if (place == NEAR) nearW else tabW[seat]

    /** Whether seat [seat]'s tab shows the icon when the timer is on (else the minutes alone). */
    fun iconWithTimer(seat: Int, place: Int = FAR): Boolean = 2 * pad + iconSize + gap + digitsWidth(MAX_DIGITS) <= widthAt(place, seat)

    /** Whether seat [seat]'s tab has room for the minutes at all (three digits). */
    fun timerFits(seat: Int, place: Int = FAR): Boolean = 2 * pad + digitsWidth(MAX_DIGITS) <= widthAt(place, seat)

    /**
     * Lays out [seat]'s content at [place]: the icon, and [digits] (1..3; 0 = timer off) minutes digits.
     * Writes the screen footprints into [icon] / [num] (top-left = where to draw) and returns [ICON] /
     * [NUM] bits.
     */
    fun content(seat: Int, digits: Int, icon: IRect, num: IRect, place: Int = FAR): Int {
        val timer = digits > 0 && timerFits(seat, place)
        val showIcon = !timer || iconWithTimer(seat, place)
        val nw = if (timer) digitsWidth(digits) else 0
        val group = (if (showIcon) iconSize else 0) + (if (showIcon && timer) gap else 0) + nw
        val near = place == NEAR
        val h = if (near) nearH else tabH
        val y0 = if (near) nearY else 0
        var x = (if (near) nearX else tabX[seat]) + (widthAt(place, seat) - group) / 2
        var out = 0
        if (showIcon) {
            play.mapRect(seat, x, y0 + (h - iconSize) / 2, iconSize, iconSize, icon)
            x += iconSize + gap
            out = out or ICON
        } else icon.set(0, 0, 0, 0)
        if (timer) {
            val nh = font.height(tk)
            play.mapRect(seat, x, y0 + (h - nh) / 2, nw, nh, num)
            out = out or NUM
        } else num.set(0, 0, 0, 0)
        return out
    }

    override fun toString(): String =
        "TurnLayout(S=$s ${if (enabled) "" else "DISABLED "}tab h $tabH +$hitPad, x/w ${(0 until P).joinToString { "${tabX[it]}/${tabW[it]}" }})"

    companion object {
        /** Paper inside the tab's ends, and between the icon and the minutes (design px at S = 4). */
        const val PAD = 2
        const val GAP = 3

        /** Paper between a tab pushed aside by the diamond and the tile's side edge. */
        const val EDGE = PlayLayout.V_MIN

        /** Most minutes digits shown (999; the clock stops counting there). */
        const val MAX_DIGITS = 3
        const val MAX_MINUTES = 999

        /** Placements: the far edge (normal), the near edge (a knocked-out seat). */
        const val FAR = 0
        const val NEAR = 1

        /** [content] bits. */
        const val ICON = 1
        const val NUM = 2
    }
}
