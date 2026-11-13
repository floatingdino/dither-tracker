package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.peeks.PeekLayout
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextLayout
import au.com.josephmark.dithertracker.widgets.TextWrap

/**
 * Geometry of a knocked-out tile (T15) on top of [PlayLayout]. Pure maths (no Android types), JVM
 * tested; laid out in the player's frame and mapped with [PlayLayout.mapRect], so a top-row tile is
 * the exact 180° image of an upright one.
 *
 * The dead tile, in bands of the player's frame:
 * - **Field**: the whole panel in `Role.DEAD` (crosshatch).
 * - **Top band** (above the number, where the readout lives): the `dead` tombstone at [iconK]
 *   (≈ 2× chrome icons) and, unless the player died of life loss, the **cause** — the small icon and
 *   the lethal number in `num-m` (poison 10, imp 21) — centred together ([topRow]).
 * - **Number slot**: the final life total in the hero face where it always is, knocked out of the
 *   hatch as a paper cut-out with an [outline] of ink (`KoComponent`; geometry = [PlayLayout.numberFootprint]).
 * - **Bottom band** (under the number; the tab is hidden on a dead tile): a paper **plate** with an
 *   ink [border] and a solid [shadow] down-right in the reader's frame (the numerals' sticker look),
 *   holding the KO quip in ALL CAPS in the `ui` face, wrapped to at most [MAX_LINES] lines at the
 *   largest scale ≤ [quipKMax] that fits ([arrange]). While a revive is armed the plate is inked and
 *   reads [REVIVE] in paper at the quip's scale.
 *
 * The winner caption ([arrangeWinner]) sits in the under-number row of the last player standing,
 * between the counter-peek corners.
 */
class KoLayout(val play: PlayLayout) {

    val ui: Font = Fonts.UI
    val causeFont: Font = Fonts.NUM_M
    private val s = play.grid.scale
    private val W = play.panelW
    private val H = play.panelH

    val border: Int = Chrome.size(BORDER, s)

    /** Solid drop shadow under the plate, down-right in the reader's frame. */
    val shadow: Int = Chrome.size(SHADOW, s)

    /** Ink ring round the cut-out number (dither px). */
    val outline: Int = Chrome.size(OUTLINE, s)

    /** Plate padding inside the border: across and down. */
    val padH: Int = Chrome.size(PAD_H, s)
    val padV: Int = Chrome.size(PAD_V, s)

    /** Paper round the tombstone's silhouette and the cause chip (cut out of the hatch). */
    val halo: Int = Chrome.size(HALO, s)
    val lineGap: Int = Chrome.size(LINE_GAP, s)
    val iconGap: Int = Chrome.size(ICON_GAP, s)
    val causeGap: Int = Chrome.size(CAUSE_GAP, s)

    /** Number slot (player frame) and the bands either side, [PlayLayout.V_MIN] clear of it and the edges. */
    private val slotY = play.numberSlotY
    private val slotH = play.font.height(play.kMax)
    val topBandTop: Int = PlayLayout.V_MIN
    val topBandBottom: Int = slotY - PlayLayout.V_MIN - outline
    val bottomBandTop: Int = slotY + slotH + PlayLayout.V_MIN + outline
    val bottomBandBottom: Int = H - PlayLayout.V_MIN

    /** Scale of the tombstone (16 × [iconK] dither px): ≈ 2× chrome, stepped down to fit the top band. */
    val iconK: Int
    val iconSize: Int

    /** Scale of the small cause icon and its number. */
    val causeK: Int = play.tk
    val causeIconSize: Int = PlayLayout.ICON * causeK

    /** Largest quip / plate / winner text scale (physical size ≈ S = 4's [QUIP_K]). */
    val quipKMax: Int = Chrome.size(QUIP_K, s)

    /** Widest plate (player frame): the panel less [PlayLayout.EDGE_MIN] each side and the shadow. */
    val maxPlateW: Int = W - 2 * PlayLayout.EDGE_MIN - shadow

    /** Widest quip line. */
    val maxTextW: Int = maxPlateW - 2 * (border + padH)

    /** Tallest plate: the bottom band less the shadow. */
    val maxPlateH: Int = bottomBandBottom - bottomBandTop - shadow

    /** Width the plate + its shadow are centred in. */
    private val CW = W - shadow

    private val wrap = TextWrap(MAX_LINES)

    init {
        var ik = Chrome.size(ICON_K, s)
        while (ik > 1 && PlayLayout.ICON * ik + 2 * halo > topBandBottom - topBandTop) ik--
        iconK = ik
        iconSize = PlayLayout.ICON * ik
    }

    /** The panels (screen), for the dead fill and the touch zone. */
    val panels: Array<IRect> get() = play.panels

    // ---- the plate ------------------------------------------------------------------------------

    /**
     * Lays out [seat]'s plate for [quip] (already in caps) into [out]: picks the quip scale, wraps
     * it, centres the plate in the bottom band. Pure; allocation-free.
     */
    fun arrange(seat: Int, quip: CharSequence, out: Block) {
        var k = quipKMax
        while (true) {
            wrap.wrap(quip, ui, k, maxTextW)
            if ((!wrap.truncated && plateH(k, wrap.lines) <= maxPlateH) || k == 1) break
            k--
        }
        out.k = k
        out.lines = wrap.lines
        for (i in 0 until wrap.lines) {
            out.start[i] = wrap.start[i]
            out.end[i] = wrap.end[i]
        }
        val textW = maxOf(wrap.widestWidth, ui.widthOf(REVIVE.length, k))
        val pw = minOf(maxPlateW, textW + 2 * (border + padH))
        val ph = plateH(k, wrap.lines)
        val px = (CW - pw) / 2
        // Centred in the bottom band; on a cramped (not `comfortable`) layout, kept inside the panel.
        val py = (bottomBandTop + (bottomBandBottom - bottomBandTop - shadow - ph) / 2).coerceIn(0, maxOf(0, H - shadow - ph))
        play.mapRect(seat, px, py, pw, ph, out.plate)
        play.mapRect(seat, px + shadow, py + shadow, pw, ph, out.shadow)
        val lh = ui.capHeight * k
        var y = py + border + padV
        for (i in 0 until wrap.lines) {
            val tw = ui.widthOf(wrap.end[i] - wrap.start[i], k)
            play.mapRect(seat, (CW - tw) / 2, y - ui.capTop * k, tw, ui.height(k), out.line[i])
            y += lh + lineGap
        }
        val rt = ui.widthOf(REVIVE.length, k)
        play.mapRect(seat, (CW - rt) / 2, py + (ph - lh) / 2 - ui.capTop * k, rt, ui.height(k), out.reviveText)
    }

    /** Plate height for quip scale [k] and [lines] lines. */
    fun plateH(k: Int, lines: Int): Int = 2 * (border + padV) + lines * ui.capHeight * k + (lines - 1) * lineGap

    // ---- top band --------------------------------------------------------------------------------

    /**
     * The top band of [seat]: the tombstone, then (when [withCause]) the cause icon and [value] in
     * [causeFont], centred together in the band. Writes the three footprints (cause ones empty
     * without a cause); returns the number's glyph scale.
     */
    fun topRow(seat: Int, withCause: Boolean, value: Int, tomb: IRect, icon: IRect, number: IRect): Int {
        val nw = if (withCause) causeFont.measureInt(value, causeK) else 0
        val unit = iconSize + if (withCause) causeGap + halo + causeIconSize + iconGap + nw + halo else 0
        val x = (W - unit) / 2
        val bandH = topBandBottom - topBandTop
        val cy = topBandTop + bandH / 2
        play.mapRect(seat, x, topBandTop + TextLayout.centre(bandH, iconSize), iconSize, iconSize, tomb)
        if (!withCause) {
            icon.set(0, 0, 0, 0); number.set(0, 0, 0, 0)
            return causeK
        }
        val ix = x + iconSize + causeGap + halo
        play.mapRect(seat, ix, cy - causeIconSize / 2, causeIconSize, causeIconSize, icon)
        val capH = causeFont.capHeight * causeK
        play.mapRect(seat, ix + causeIconSize + iconGap, cy - capH / 2 - causeFont.capTop * causeK, nw, causeFont.height(causeK), number)
        return causeK
    }

    /** The whole top band of [seat] (screen): invalidate this when the cause value changes. */
    fun topBand(seat: Int, out: IRect) = play.mapRect(seat, 0, topBandTop, W, maxOf(0, topBandBottom - topBandTop), out)

    // ---- winner caption ---------------------------------------------------------------------------

    /**
     * Where the winner caption may go (player frame): the under-number row, between the counter-peek
     * corners (T38, [PeekLayout.corner]) with [PlayLayout.V_MIN] clear of each, so it never sits on a
     * peek. Without peeks (disabled layout) it has the row's full width.
     */
    private val peek = PeekLayout(play)
    val winnerLeft: Int = if (peek.enabled) peek.leftX + peek.colW + peek.pad + PlayLayout.V_MIN else play.underX
    val winnerRight: Int = if (peek.enabled) peek.rightX - peek.colW - peek.pad - PlayLayout.V_MIN else play.underX + play.underW
    val winnerW: Int get() = maxOf(0, winnerRight - winnerLeft)
    private val winnerWrap = TextWrap(MAX_LINES)

    /**
     * Player-frame band the caption is centred in: the whole gap between the number slot and the
     * tab's hit area ([PlayLayout.V_MIN] clear of each) — the band the under-number row is centred in.
     */
    val winnerTop: Int = slotY + slotH + PlayLayout.V_MIN
    val winnerBottom: Int = H - play.chrome.tabH - play.chrome.tabHitPad - PlayLayout.V_MIN
    private val winnerH0: Int get() = winnerBottom - winnerTop

    /** The caption band on [seat] (screen): invalidate this when the winner changes. */
    fun winnerBand(seat: Int, out: IRect) =
        play.mapRect(seat, winnerLeft, winnerTop, winnerW, maxOf(0, winnerBottom - winnerTop), out)

    /**
     * Lays out a winner [caption] (caps) on [seat] into [out]: the largest scale ≤ [quipKMax] at which
     * it wraps into at most [MAX_LINES] lines of [winnerW] without cutting a word and fits the row's
     * height; if none does, scale 1 with what fits. `out.k == 0` = no room at all.
     */
    fun arrangeWinner(seat: Int, caption: CharSequence, out: Block) {
        out.k = 0; out.lines = 0
        val w = winnerW
        if (winnerH0 <= 0 || w <= 0 || winnerBottom <= winnerTop) return
        var k = quipKMax
        while (true) {
            winnerWrap.wrap(caption, ui, k, w)
            if ((!winnerWrap.truncated && winnerH(k, winnerWrap.lines) <= winnerH0) || k == 1) break
            k--
        }
        var n = winnerWrap.lines
        while (n > 1 && winnerH(k, n) > winnerH0) n-- // scale 1 still too tall: drop lines
        if (n == 0 || winnerH(k, n) > winnerH0) return
        out.k = k; out.lines = n
        val lh = ui.capHeight * k
        val bh = winnerH(k, n)
        var y = winnerTop + (winnerH0 - bh) / 2 + ui.capTop * k
        for (i in 0 until n) {
            out.start[i] = winnerWrap.start[i]; out.end[i] = winnerWrap.end[i]
            val tw = ui.widthOf(out.end[i] - out.start[i], k)
            // Box top from the cap top, kept inside the row (the face's descender band may hang below).
            val top = minOf(y - ui.capTop * k, winnerTop + winnerH0 - ui.height(k))
            play.mapRect(seat, winnerLeft + (w - tw) / 2, maxOf(winnerTop, top), tw, ui.height(k), out.line[i])
            y += lh + winnerGap(k)
        }
    }

    /** Gap between the caption's cap bands: [WINNER_GAP], and never less than the face's descent (boxes never overlap). */
    fun winnerGap(k: Int): Int = maxOf(Chrome.size(WINNER_GAP, s), ui.height(k) - ui.capHeight * k - ui.capTop * k)

    /** Height of a [lines]-line caption at scale [k]: cap bands, gaps and the last line's descent. */
    private fun winnerH(k: Int, lines: Int): Int =
        lines * ui.capHeight * k + (lines - 1) * winnerGap(k) + (ui.height(k) - ui.capHeight * k - ui.capTop * k)

    /** One seat's plate (screen rects), filled by [arrange]. */
    class Block {
        var k = 1
        var lines = 0
        val start = IntArray(MAX_LINES)
        val end = IntArray(MAX_LINES)
        val plate = IRect()

        /** The plate's drop shadow (drawn first; the plate covers all but its down-right edge). */
        val shadow = IRect()
        val line = Array(MAX_LINES) { IRect() }

        /** [REVIVE] centred on the plate (armed). */
        val reviveText = IRect()
    }

    override fun toString(): String =
        "KoLayout(S=$s tomb k$iconK, cause k$causeK, quip k..$quipKMax, top band $topBandTop..$topBandBottom, " +
            "bottom band $bottomBandTop..$bottomBandBottom, plate ≤ ${maxPlateW}x$maxPlateH, outline $outline)"

    companion object {
        const val MAX_LINES = 2

        /** The armed plate's text. */
        const val REVIVE = "REVIVE?"

        /** Design values at S = 4 (converted per S like [PlayLayout.Chrome]). */
        const val ICON_K = 2
        const val QUIP_K = 2
        const val BORDER = 1
        const val SHADOW = 2
        const val OUTLINE = 2
        const val PAD_H = 6
        const val PAD_V = 4
        const val HALO = 2
        const val LINE_GAP = 5
        const val ICON_GAP = 3
        const val CAUSE_GAP = 10
        const val WINNER_GAP = 3
    }
}
