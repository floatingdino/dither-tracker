package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.peeks.PeekLayout
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.widgets.TextWrap

/**
 * Geometry of the knocked-out tile and the winner's tile (T15, reworked in T42: the quip replaces the
 * life total). Pure maths (no Android types), JVM tested; laid out in the player's frame and mapped
 * with [PlayLayout.mapRect], so a top-row tile is the exact 180° image of an upright one. Dead and
 * winner tiles share one layout, so a line looks the same on either:
 *
 * ```
 *  ┌──────────── far edge (the diamond sits in one far corner) ────────────┐
 *  │                         [emblem]       tombstone / crown, k = iconK    │
 *  │                                                                        │
 *  │                     THE QUIP, IN CAPS,          hero: `ui` × k, 1–4    │
 *  │                     CENTRED AS A BLOCK          lines, k by maths      │
 *  │ (peeks)                                                       (peeks)  │
 *  │ (peeks)              [♥ 0] / [☠ 10]          detail: cause / final life │
 *  │ (peeks)                 (tab)                                 (peeks)  │
 *  └─────────────────────────── near edge (the player) ─────────────────────┘
 * ```
 * - **Field** (dead only): `Role.DEAD` over the panel inset by a paper [gutter], with a paper ring
 *   [gutter] wide round the centre diamond ([fieldInk]), so dividers and the diamond keep a clean edge.
 * - **Emblem**: 16 × [iconK] px, centred, [PlayLayout.V_MIN] from the far edge ([emblem]).
 * - **Hero**: the quip block in [textLeft, textRight) × [textTop, textBottom): the largest k ≤ [kMax]
 *   at which it wraps into ≤ [MAX_LINES] lines without breaking a word and fits ([arrange]). Lines are
 *   9k apart (the face's full height), the block's cap bands centred vertically, each line centred.
 *   On a dead tile every word sits on a paper box grown by [halo] (cut out of the field).
 * - **Detail**: an icon + `num-m` value on a paper chip, centred in the under-number row between the
 *   counter-peek corners ([detail]).
 * - **Revive** (armed): an ink plate reading [REVIVE] in paper at [Block.reviveK], with a solid
 *   shadow down-right separated from it by a paper [keyline], centred in the hero area.
 * - **Winner cover** ([winnerCover]): paper over everything above the peek corners (the number, ±
 *   and readout of the live tile underneath).
 */
class KoLayout(val play: PlayLayout) {

    val ui: Font = Fonts.UI
    val causeFont: Font = Fonts.NUM_M
    private val s = play.grid.scale
    private val W = play.panelW
    private val H = play.panelH

    /** Paper between the dead field and the dividers / centre diamond. */
    val gutter: Int = Chrome.size(GUTTER, s)

    /** Paper round the emblem's silhouette, each word of the quip and the detail chip. */
    val halo: Int = Chrome.size(HALO, s)

    /** Revive plate: border-free ink; padding round its text; shadow offset and the paper keyline. */
    val padH: Int = Chrome.size(PAD_H, s)
    val padV: Int = Chrome.size(PAD_V, s)
    val shadow: Int = Chrome.size(SHADOW, s)
    val keyline: Int = Chrome.size(KEYLINE, s)
    val iconGap: Int = Chrome.size(ICON_GAP, s)

    /** Scale of the small detail icon and its number. */
    val causeK: Int = play.tk
    val causeIconSize: Int = PlayLayout.ICON * causeK

    /** Largest hero scale (physical size ≈ S = 4's [QUIP_K]). */
    val kMax: Int = Chrome.size(QUIP_K, s)

    // ---- bands (player frame) -------------------------------------------------------------------

    private val peek = PeekLayout(play)
    private val slotBottom = play.numberSlotY + play.font.height(play.kMax)

    /** Top of the peek corners (pads included), or the near edge's margin without peeks. */
    private val peekTop: Int =
        if (peek.enabled) peek.slotY(2 * (peek.rows - 1)) - peek.pad else H - PlayLayout.V_MIN

    /** Detail band: the under-number row between the peek corners (also the old winner caption band). */
    val detailLeft: Int = if (peek.enabled) peek.leftX + peek.colW + peek.pad + PlayLayout.V_MIN else play.underX
    val detailRight: Int = if (peek.enabled) peek.rightX - peek.colW - peek.pad - PlayLayout.V_MIN else play.underX + play.underW
    val detailTop: Int = slotBottom + PlayLayout.V_MIN
    val detailBottom: Int = H - play.chrome.tabH - play.chrome.tabHitPad - PlayLayout.V_MIN

    val emblemTop: Int = PlayLayout.V_MIN + gutter

    /** Hero text box (cap bands and descenders; word boxes grow [halo] beyond it). */
    val textLeft: Int = PlayLayout.EDGE_MIN
    val textRight: Int = W - PlayLayout.EDGE_MIN
    val textW: Int get() = textRight - textLeft
    val textBottom: Int = minOf(peekTop, detailTop) - halo

    /** Scale of the emblem (16 × [iconK] px): ≈ 2× chrome, stepped down to leave the hero two k = 2 lines. */
    val iconK: Int
    val iconSize: Int
    val textTop: Int
    val textH: Int get() = textBottom - textTop

    private val wrap = TextWrap(MAX_LINES)

    init {
        var ik = Chrome.size(ICON_K, s)
        val minText = 2 * ui.height(Chrome.size(2, s))
        fun top(k: Int) = maxOf(emblemTop + PlayLayout.ICON * k + 2 * halo, play.diamondHalf + gutter + halo + 1)
        while (ik > 1 && textBottom - top(ik) < minText) ik--
        iconK = ik
        iconSize = PlayLayout.ICON * ik
        textTop = top(ik)
    }

    /** The panels (screen), for the dead fill and the touch zone. */
    val panels: Array<IRect> get() = play.panels

    // ---- field --------------------------------------------------------------------------------------

    /** The dead dither's rect on [seat] (screen): the panel inset by [gutter]. */
    fun field(seat: Int, out: IRect): IRect {
        val p = play.panels[seat]
        return out.set(p.left + gutter, p.top + gutter, p.right - gutter, p.bottom - gutter)
    }

    /**
     * Half-width of the paper ring round the diamond at screen row offset `dy` from its centre (−1 =
     * the row is clear of it): the stepped diamond grown by [gutter] in every direction (its cut
     * edges by [gutter] across and down, so diagonally twice).
     */
    fun ringRowHalf(dy: Int): Int {
        val a = if (dy < 0) -dy else dy
        val lim = play.diamondHalf + gutter
        if (a > lim) return -1
        return minOf(play.diamondHalf + play.diamondTip + 2 * gutter - a, lim)
    }

    /** True where a dead [seat]'s field may put dither ink (screen px): inside [field], outside the ring. */
    fun fieldInk(seat: Int, gx: Int, gy: Int): Boolean {
        val p = play.panels[seat]
        if (gx < p.left + gutter || gx >= p.right - gutter || gy < p.top + gutter || gy >= p.bottom - gutter) return false
        val hw = ringRowHalf(gy - play.diamondCy)
        return hw < 0 || gx < play.diamondCx - hw || gx > play.diamondCx + hw
    }

    // ---- emblem and detail -----------------------------------------------------------------------------

    /** The emblem (tombstone / crown) footprint on [seat] (screen). */
    fun emblem(seat: Int, out: IRect) = play.mapRect(seat, (W - iconSize) / 2, emblemTop, iconSize, iconSize, out)

    /**
     * The detail on [seat]: icon then [value] in [causeFont], centred in the detail band. Writes the
     * icon, number and chip (paper backing = both grown by [halo]) footprints; returns false (all
     * empty) when the band has no room.
     */
    fun detail(seat: Int, value: Int, icon: IRect, number: IRect, chip: IRect): Boolean {
        val nw = causeFont.measureInt(value, causeK)
        val cw = causeIconSize + iconGap + nw
        val ch = maxOf(causeIconSize, causeFont.capHeight * causeK)
        // The chip's paper margin: [halo], or less on a cramped band.
        val g = minOf(halo, (detailRight - detailLeft - cw) / 2, (detailBottom - detailTop - ch) / 2)
        if (g < 0) {
            icon.set(0, 0, 0, 0); number.set(0, 0, 0, 0); chip.set(0, 0, 0, 0)
            return false
        }
        val x = detailLeft + (detailRight - detailLeft - cw) / 2
        val y = detailTop + (detailBottom - detailTop - ch) / 2
        play.mapRect(seat, x - g, y - g, cw + 2 * g, ch + 2 * g, chip)
        play.mapRect(seat, x, y + (ch - causeIconSize) / 2, causeIconSize, causeIconSize, icon)
        val capH = causeFont.capHeight * causeK
        play.mapRect(seat, x + causeIconSize + iconGap, y + (ch - capH) / 2 - causeFont.capTop * causeK, nw, causeFont.height(causeK), number)
        return true
    }

    /** The whole detail band on [seat] (screen): invalidate this when the detail's value changes. */
    fun detailBand(seat: Int, out: IRect) =
        play.mapRect(seat, detailLeft, detailTop, maxOf(0, detailRight - detailLeft), maxOf(0, detailBottom - detailTop), out)

    // ---- hero ----------------------------------------------------------------------------------------

    /** The hero area on [seat] (screen): the text box grown by [halo] — everything the quip or the revive plate touches. */
    fun heroArea(seat: Int, out: IRect) =
        play.mapRect(seat, textLeft - halo, textTop - halo, textW + 2 * halo, textH + 2 * halo, out)

    /** Height of [lines] lines at [k] (each line the face's full height, so descenders always fit). */
    fun blockH(k: Int, lines: Int): Int = lines * ui.height(k)

    /**
     * Lays out the hero [quip] (already in caps) on [seat] into [out]: the largest k ≤ [kMax] at which
     * it wraps into ≤ [MAX_LINES] lines of [textW] without breaking a word and fits [textH]; else
     * k = 1 with what fits. Also places the armed revive plate. Pure; allocation-free.
     */
    fun arrange(seat: Int, quip: CharSequence, out: Block) {
        var k = kMax
        while (true) {
            wrap.wrap(quip, ui, k, textW)
            if ((fitsWhole(quip) && blockH(k, wrap.lines) <= textH) || k == 1) break
            k--
        }
        var n = wrap.lines
        while (n > 1 && blockH(k, n) > textH) n--
        out.k = k
        out.lines = n
        val lh = ui.height(k)
        // Centre the cap bands (the last line's descender rows hang into the slack below), keeping
        // every glyph box inside the text box.
        val capBlock = blockH(k, n) - (lh - ui.capHeight * k - ui.capTop * k)
        val y0 = textTop + maxOf(0, minOf((textH - capBlock) / 2, textH - blockH(k, n)))
        var u = Int.MAX_VALUE; var v = Int.MAX_VALUE; var r = Int.MIN_VALUE; var b = Int.MIN_VALUE
        for (i in 0 until n) {
            out.start[i] = wrap.start[i]
            out.end[i] = wrap.end[i]
            val tw = ui.widthOf(wrap.end[i] - wrap.start[i], k)
            val x = (W - tw) / 2
            val y = y0 + i * lh
            out.lx[i] = x; out.ly[i] = y
            play.mapRect(seat, x, y, tw, lh, out.line[i])
            u = minOf(u, x); v = minOf(v, y); r = maxOf(r, x + tw); b = maxOf(b, y + lh)
        }
        if (n > 0) play.mapRect(seat, u - halo, v - halo, r - u + 2 * halo, b - v + 2 * halo, out.bounds)
        else out.bounds.set(0, 0, 0, 0)
        arrangeRevive(seat, out)
    }

    /** True when the last wrap kept every non-space char and broke lines only at spaces. */
    private fun fitsWhole(text: CharSequence): Boolean {
        if (wrap.truncated) return false
        for (i in 0 until wrap.lines) {
            val e = wrap.end[i]
            if (e < text.length && text[e] != ' ' && text[e] != '\n') return false
        }
        return true
    }

    private fun arrangeRevive(seat: Int, out: Block) {
        val n = REVIVE.length
        // The plate with its keyline (up-left) and shadow (down-right) inside the hero area.
        val aL = textLeft - halo
        val aT = textTop - halo
        val aW = textW + 2 * halo - keyline - shadow
        val aH = textH + 2 * halo - keyline - shadow
        var k = minOf(kMax, Chrome.size(REVIVE_K, s))
        while (k > 1 && (ui.widthOf(n, k) + 2 * padH > aW || ui.capHeight * k + 2 * padV > aH)) k--
        out.reviveK = k
        val tw = ui.widthOf(n, k)
        val pw = tw + 2 * padH
        val ph = ui.capHeight * k + 2 * padV
        val px = aL + keyline + (aW - pw) / 2
        val py = aT + keyline + (aH - ph) / 2
        play.mapRect(seat, px, py, pw, ph, out.plate)
        play.mapRect(seat, px - keyline, py - keyline, pw + 2 * keyline, ph + 2 * keyline, out.keyline)
        play.mapRect(seat, px + shadow, py + shadow, pw, ph, out.shadow)
        play.mapRect(seat, px + padH, py + padV - ui.capTop * k, tw, ui.height(k), out.reviveText)
    }

    /**
     * The paper box of the word `[from, until)` of line [i] in [b] on [seat] (screen): the glyphs'
     * cap band (and descender rows when [descends]) grown by [halo].
     */
    fun wordBox(seat: Int, b: Block, i: Int, from: Int, until: Int, descends: Boolean, out: IRect) {
        val k = b.k
        val step = ui.advance * k
        val x = b.lx[i] + (from - b.start[i]) * step
        val w = ui.widthOf(until - from, k)
        val h = (if (descends) ui.glyphHeight else ui.capTop + ui.capHeight) * k - ui.capTop * k
        play.mapRect(seat, x - halo, b.ly[i] + ui.capTop * k - halo, w + 2 * halo, h + 2 * halo, out)
    }

    // ---- winner ---------------------------------------------------------------------------------------

    /** Paper over the live tile above the peek corners on [seat] (screen): number, ±, readout. */
    fun winnerCover(seat: Int, out: IRect) = play.mapRect(seat, 0, 0, W, peekTop, out)

    /** One seat's hero (screen rects), filled by [arrange]. */
    class Block {
        var k = 1
        var lines = 0
        val start = IntArray(MAX_LINES)
        val end = IntArray(MAX_LINES)

        /** Player-frame top-left of each line's glyph box (for word boxes). */
        val lx = IntArray(MAX_LINES)
        val ly = IntArray(MAX_LINES)

        /** Each line's glyph box (full face height). */
        val line = Array(MAX_LINES) { IRect() }

        /** The block's words with their [halo]: what a press inverts. */
        val bounds = IRect()

        /** Armed: the ink plate, its paper keyline, its shadow, and [REVIVE] at [reviveK]. */
        var reviveK = 1
        val plate = IRect()
        val keyline = IRect()
        val shadow = IRect()
        val reviveText = IRect()
    }

    override fun toString(): String =
        "KoLayout(S=$s emblem k$iconK, hero k..$kMax in ${textW}x$textH at y $textTop, detail " +
            "$detailLeft..$detailRight x $detailTop..$detailBottom, gutter $gutter, halo $halo)"

    companion object {
        const val MAX_LINES = 4

        /** The armed plate's text. */
        const val REVIVE = "REVIVE?"

        /** Design values at S = 4 (converted per S like [PlayLayout.Chrome]). */
        const val ICON_K = 2
        const val QUIP_K = 5
        const val REVIVE_K = 4
        const val GUTTER = 2
        const val HALO = 2
        const val PAD_H = 6
        const val PAD_V = 5
        const val SHADOW = 3
        const val KEYLINE = 1
        const val ICON_GAP = 3
    }
}
