package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.peeks.PeekLayout
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.widgets.TextWrap

/**
 * Where the first-player highlight goes (T16, pure): an inked band in the chosen tile's under-number
 * row — the free middle between the counter-peek corners, the same place the T15 winner caption
 * uses (peek corners + [PlayLayout.V_MIN]) — with the caption in paper caps, wrapped to at most
 * [MAX_LINES] lines at the largest `ui` scale that fits, in the player's own orientation (mapped
 * through [PlayLayout.mapRect]). Without peeks (cramped S) it takes the panel's width less the edge margins.
 */
class FirstPlayerLayout(val play: PlayLayout) {
    val ui: Font = Fonts.UI
    private val s = play.grid.scale
    private val peek = PeekLayout(play)

    /** The band (player frame). */
    val left: Int = when {
        peek.enabled -> peek.leftX + peek.colW + peek.pad + PlayLayout.V_MIN
        else -> PlayLayout.EDGE_MIN
    }
    val right: Int = when {
        peek.enabled -> peek.rightX - peek.colW - peek.pad - PlayLayout.V_MIN
        else -> play.panelW - PlayLayout.EDGE_MIN
    }
    val top: Int = play.numberSlotY + play.font.height(play.kMax) + PlayLayout.V_MIN
    val bottom: Int = play.panelH - play.chrome.tabH - play.chrome.tabHitPad - PlayLayout.V_MIN
    val enabled: Boolean get() = right - left > 0 && bottom - top > 0

    /** Paper margin inside the band, across and down. */
    val padH: Int = Chrome.size(PAD_H, s)
    val padV: Int = Chrome.size(PAD_V, s)
    val lineGap: Int = Chrome.size(LINE_GAP, s)
    val kMax: Int = maxOf(1, play.tk)

    /** Arranged result ([arrange]). */
    val band = IRect()
    var k = 1
        private set
    var lines = 0
        private set
    val start = IntArray(MAX_LINES)
    val end = IntArray(MAX_LINES)
    val line: Array<IRect> = Array(MAX_LINES) { IRect() }
    var truncated = false
        private set

    private val wrap = TextWrap(MAX_LINES)

    /** Lays [caption] (caps) out on [seat]'s band. */
    fun arrange(seat: Int, caption: CharSequence) {
        bandRect(seat, band)
        lines = 0
        if (!enabled) return
        val w = right - left - 2 * padH
        val h = bottom - top - 2 * padV
        var kk = kMax
        while (true) {
            wrap.wrap(caption, ui, kk, w)
            if ((!wrap.truncated && blockH(kk, wrap.lines) <= h) || kk == 1) break
            kk--
        }
        var n = wrap.lines
        while (n > 1 && blockH(kk, n) > h) n--
        k = kk
        lines = n
        truncated = wrap.truncated || n < wrap.lines
        val capH = ui.capHeight * kk
        var y = top + (bottom - top - blockH(kk, n)) / 2 // cap top of line 0
        for (i in 0 until n) {
            start[i] = wrap.start[i]; end[i] = wrap.end[i]
            val tw = ui.widthOf(end[i] - start[i], kk)
            val boxTop = y - ui.capTop * kk
            play.mapRect(seat, left + (right - left - tw) / 2, boxTop, tw, ui.height(kk), line[i])
            y += capH + lineGap
        }
    }

    /** The whole band of [seat] (screen): what showing / clearing invalidates. */
    fun bandRect(seat: Int, out: IRect): IRect {
        play.mapRect(seat, left, top, maxOf(0, right - left), maxOf(0, bottom - top), out)
        return out
    }

    private fun blockH(k: Int, n: Int): Int = if (n <= 0) 0 else n * ui.capHeight * k + (n - 1) * lineGap

    companion object {
        const val MAX_LINES = 2
        const val PAD_H = 3
        const val PAD_V = 2
        const val LINE_GAP = 3
    }
}
