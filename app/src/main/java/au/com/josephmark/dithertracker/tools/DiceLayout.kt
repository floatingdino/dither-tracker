package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts

/**
 * Pure geometry of the dice / coin panel (T16, re-composed in T46): a compact two-faced "tent" on
 * the crossing, a sibling of the gem's restart confirm (`menu.GemLayout`). Dither px on the play
 * grid; no Android types.
 *
 * ```
 *  ┌────────────────────────────┐  upper face: the lower face turned 180° about the crossing
 *  │ ʎɐɯ noʎ ʇɐɥʍ        ┃  (180°) │  (read from the N edge)
 *  ├────────────────────────────┤
 *  │ [d20][d6]  ◆  [coin][ × ]  │  fold row: two-faced buttons, the roll-again diamond on the crossing
 *  ├────────────────────────────┤
 *  │  17  ┃ d20                  │  lower face (read from S): the result in the small bold numerals
 *  │      ┃ caption, balanced    │  (`num-m` at [resultK], no shadow) or the coin, then the die label
 *  └────────────────────────────┘  and the quip in ≤ [MAX_LINES] balanced lines
 * ```
 *
 * Sizes: the result at [resultK] = [RESULT_K] × the chrome's glyph scale ([k], `play.tk`), the text
 * at [k]; spacing designed at S = 4 and converted per S like `PlayLayout.Chrome` (constant physical
 * size). Each face is exactly as tall as the taller of the result and the text block (label,
 * underline, two caption lines) + [PAD_V], and as wide as the result + a [TEXT_CHARS]-glyph column,
 * or the fold row if that is wider (it is, at every S: four buttons that hold "coin"). At S = 4 that
 * is 157×105 (163×111 with the rim) at 2×, 157×99 (163×105) at 1× (stacked, see [stacked]): clear of every life total up to four glyphs, the
 * readouts and every ± glyph (see `DiceLayoutTest`).
 *
 * The faces are point-symmetric about the crossing (each long side reads its own face upright), so
 * the panel is centred on [cx], [cy] with odd width and height and every upper-face rect is
 * [reflect]ed from its lower-face twin.
 */
class DiceLayout(val play: PlayLayout, resultScale: Int = RESULT_K) {
    val scale: Int = play.grid.scale

    /** Glyph scale of the text, buttons and icons. */
    val k: Int = play.tk

    /** Glyph scale of the result (and the coin and the "?" beat, which match its height). */
    val resultK: Int = resultScale * k

    val result: Font = Fonts.NUM_M
    val ui: Font = Fonts.UI

    val cx: Int = play.diamondCx
    val cy: Int = play.diamondCy

    /** Panel (1-px ink border included) and the whole thing with its `MENU` rim and outer line. */
    val panel = IRect()
    val panelOuter = IRect()
    val rim: Int = Chrome.size(RIM, scale)

    /** Faces inside the border: [faces]`[0]` the lower (read from S, rotation 0), `[1]` the upper (180°). */
    val faces: Array<IRect> = Array(2) { IRect() }
    val fold = IRect()

    /** Rotation of face i's content. */
    fun rotation(face: Int): Int = if (face == 0) 0 else 180

    /**
     * Per face: the result box (the result is centred in it: at least a two-digit [result] footprint)
     * and the caption column beside it.
     */
    val resultBox: Array<IRect> = Array(2) { IRect() }
    val textCol: Array<IRect> = Array(2) { IRect() }

    /**
     * The two compositions. **Beside** (a big result, `RESULT_K` = 2): the result on the left, the
     * label over the caption in the column beside it. **Stacked** (a small result, 1×): the label
     * sits under the result like a badge — [stackCol] — and the caption has the column to itself,
     * which makes the faces shorter. Stacked is used whenever that badge is no taller than the
     * label-over-caption block.
     */
    val stacked: Boolean

    /** Per face, stacked only: the result-and-label column (empty when beside). */
    val stackCol: Array<IRect> = Array(2) { IRect() }

    /** Fold buttons (visual) and their touch rects (full fold height, gaps split), by [BUTTONS] index. */
    val button: Array<IRect> = Array(BUTTONS) { IRect() }
    val buttonHit: Array<IRect> = Array(BUTTONS) { IRect() }

    /** The roll-again diamond on the crossing: radius, flat-tip half-height, bounds. */
    val foldR: Int
    val foldHalf: Int
    val foldRect = IRect()

    /** The close button's chunky X (`GemPainter.cross`). */
    val xHalf: Int = Chrome.size(X_HALF, scale)
    val xRun: Int = 2 * Chrome.size(X_RUN, scale) + 1

    val padH: Int = Chrome.size(PAD_H, scale)
    val padV: Int = Chrome.size(PAD_V, scale)
    val colGap: Int = Chrome.size(COL_GAP, scale)
    val lineGap: Int = Chrome.size(LINE_GAP, scale)
    val underlineGap: Int = Chrome.size(UNDERLINE_GAP, scale)
    val labelGap: Int = Chrome.size(LABEL_GAP, scale)
    val platePad: Int = Chrome.size(PLATE_PAD, scale)

    init {
        val resH = result.height(resultK)
        val capH = ui.capHeight * k
        val stackH = resH + labelGap + capH + underlineGap + 1
        stacked = stackH <= textBlockHeight(MAX_LINES)
        val resW = if (stacked) maxOf(result.widthOf(2, resultK), ui.widthOf(LABEL_CHARS, k)) else result.widthOf(2, resultK)
        val textH = if (stacked) maxOf(stackH, captionHeight(MAX_LINES)) else maxOf(resH, textBlockHeight(MAX_LINES))
        val b = 1
        // The pad also keeps the last caption line's descenders a pixel clear of the face's border.
        val padY = maxOf(padV, (ui.height(1) - ui.capTop - ui.capHeight) * k + 1)
        val faceH = textH + 2 * padY
        val foldH = odd(maxOf(Chrome.size(FOLD_H, scale), play.target))
        val gap = Chrome.size(BTN_GAP, scale)
        val margin = Chrome.size(BTN_MARGIN, scale)
        val r = foldH / 2 + 1
        // Wide enough for a [TEXT_CHARS]-glyph caption column and for four fingertip-sized fold
        // buttons (hit = button + both half-gaps ≥ target) beside the diamond; the caption takes any slack.
        val textW = ui.widthOf(TEXT_CHARS, k)
        // Each button holds its longest label ("coin") with [BTN_AIR] of paper inside the frame.
        val bwMin = maxOf(play.target - 2 * (gap / 2), ui.widthOf(LONGEST_BUTTON_LABEL, k) + 2 + 2 * Chrome.size(BTN_AIR, scale))
        val foldW = 2 * (margin + 2 * bwMin + 2 * gap + r) + 1
        val faceW = odd(maxOf(padH + resW + colGap + textW + padH, foldW))
        val w = faceW + 2 * b
        val h = b + faceH + b + foldH + b + faceH + b
        val x0 = cx - w / 2
        val foldTop = cy - foldH / 2
        val y0 = foldTop - b - faceH - b
        panel.setXywh(x0, y0, w, h)
        val o = rim + 1
        panelOuter.set(panel.left - o, panel.top - o, panel.right + o, panel.bottom + o)
        fold.setXywh(x0 + b, foldTop, faceW, foldH)
        faces[0].setXywh(x0 + b, foldTop + foldH + b, faceW, faceH)
        reflect(faces[0], faces[1])
        val f = faces[0]
        if (stacked) {
            stackCol[0].setXywh(f.left + padH, f.top + (faceH - stackH) / 2, resW, stackH)
            resultBox[0].setXywh(stackCol[0].left, stackCol[0].top, resW, resH)
            reflect(stackCol[0], stackCol[1])
        } else {
            resultBox[0].setXywh(f.left + padH, f.top + (faceH - resH) / 2, resW, resH)
        }
        textCol[0].set(f.left + padH + resW + colGap, f.top, f.right - padH, f.bottom)
        reflect(resultBox[0], resultBox[1])
        reflect(textCol[0], textCol[1])

        // Fold row: [d20][d6] ◆ [coin][×], the diamond on the crossing (as on the confirm).
        foldR = r
        foldHalf = minOf(foldR - Chrome.size(FOLD_CUT, scale), foldH / 2 - 1)
        foldRect.set(cx - foldR, cy - foldHalf, cx + foldR + 1, cy + foldHalf + 1)
        val area = (cx - foldR - gap) - (fold.left + margin) // each side of the diamond
        val bw = (area - gap) / 2
        val by = foldTop + 1
        val bh = foldH - 2
        val leftOuter = cx - foldR - gap - 2 * bw - gap
        button[D20].setXywh(leftOuter, by, bw, bh)
        button[D6].setXywh(leftOuter + bw + gap, by, bw, bh)
        button[COIN].setXywh(cx + foldR + gap + 1, by, bw, bh)
        button[CLOSE].setXywh(cx + foldR + gap + 1 + bw + gap, by, bw, bh)
        val half = gap / 2
        for (i in 0 until BUTTONS) {
            val bt = button[i]
            buttonHit[i].set(bt.left - half, fold.top, bt.right + half, fold.bottom)
        }
    }

    /**
     * The landing stamp's ink plate (T52) behind a die result of [value] on the lower face: the
     * numerals' footprint (centred in the result box, as drawn) plus [platePad] round it — less
     * above and below when the face has no room (it never touches the face's border).
     */
    fun plateFor(value: Int, out: IRect): IRect {
        val box = resultBox[0]
        val face = faces[0]
        val tw = result.measureInt(value, resultK)
        val padY = maxOf(0, minOf(platePad, box.top - face.top - 1, face.bottom - box.bottom - 1))
        val padX = platePad + 1
        val x = box.left + (box.width - tw) / 2 - padX
        return out.set(
            maxOf(face.left + 1, x), box.top - padY,
            minOf(face.right - 1, x + tw + 2 * padX), box.bottom + padY,
        )
    }

    /** Whether (gx, gy) is on the roll-again diamond. */
    fun onFoldDiamond(gx: Int, gy: Int): Boolean {
        val dx = Math.abs(gx - cx)
        val dy = Math.abs(gy - cy)
        return dy <= foldHalf && dx + dy <= foldR
    }

    /** Half-width of the diamond's row [dy]; −1 outside. */
    fun foldRowHalf(dy: Int): Int {
        val a = if (dy < 0) -dy else dy
        return if (a > foldHalf) -1 else foldR - a
    }

    /** [src] turned 180° about the crossing (the panel's centre) into [out]. */
    fun reflect(src: IRect, out: IRect): IRect {
        val sx = panel.left + panel.right
        val sy = panel.top + panel.bottom
        return out.set(sx - src.right, sy - src.bottom, sx - src.left, sy - src.top)
    }

    // ---- the text column: label, underline, wrapped caption --------------------------------------

    /** Arranged text (fill with [arrange]). Rects per face: `[face * MAX_LINES + i]` for caption lines. */
    val label: Array<IRect> = Array(2) { IRect() }
    val underline: Array<IRect> = Array(2) { IRect() }
    val line: Array<IRect> = Array(2 * MAX_LINES) { IRect() }
    var lines = 0
        private set
    val lineStart = IntArray(MAX_LINES)
    val lineEnd = IntArray(MAX_LINES)

    /** True when the last [arrange] had to drop words (never for core's tables at a comfortable S). */
    var truncated = false
        private set

    private val wrap = BalancedWrap(MAX_LINES)

    /** Cap-band height of the label, its underline and [n] caption lines (the beside block). */
    fun textBlockHeight(n: Int): Int {
        val capH = ui.capHeight * k
        return capH + underlineGap + 1 + (if (n > 0) labelGap + captionHeight(n) else 0)
    }

    /** Cap-band height of [n] caption lines. */
    fun captionHeight(n: Int): Int = if (n <= 0) 0 else n * ui.capHeight * k + (n - 1) * lineGap

    /**
     * Lays out [labelText] (underlined) and [caption] (balanced, ≤ [MAX_LINES] lines) in each face:
     * beside, the label over the caption in the text column, the block centred vertically; stacked,
     * the label centred under the result and the caption alone, centred, in the text column.
     * Allocation-free.
     */
    fun arrange(labelText: CharSequence, caption: CharSequence) {
        val col = textCol[0]
        val n = wrap.wrap(caption, ui, k, col.width)
        lines = n
        truncated = wrap.truncated
        val capH = ui.capHeight * k
        val capTop = ui.capTop * k
        val th = ui.height(k)
        val lw = ui.widthOf(labelText.length, k)
        var y: Int // cap top of the next caption line
        if (stacked) {
            val sc = stackCol[0]
            val ly = resultBox[0].bottom + labelGap // the label's cap top
            val lx = sc.left + (sc.width - lw) / 2
            label[0].setXywh(lx, ly - capTop, lw, th)
            underline[0].setXywh(lx, ly + capH + underlineGap, lw, 1)
            y = col.top + (col.height - captionHeight(n)) / 2
        } else {
            y = col.top + (col.height - textBlockHeight(n)) / 2
            label[0].setXywh(col.left, y - capTop, lw, th)
            underline[0].setXywh(col.left, y + capH + underlineGap, lw, 1)
            y += capH + underlineGap + 1 + labelGap
        }
        for (i in 0 until MAX_LINES) {
            if (i < n) {
                lineStart[i] = wrap.start[i]
                lineEnd[i] = wrap.end[i]
                line[i].setXywh(col.left, y - capTop, ui.widthOf(lineEnd[i] - lineStart[i], k), th)
                y += capH + lineGap
            } else {
                lineStart[i] = 0; lineEnd[i] = 0
                line[i].set(0, 0, 0, 0)
            }
            reflect(line[i], line[MAX_LINES + i])
        }
        reflect(label[0], label[1])
        reflect(underline[0], underline[1])
    }

    private fun odd(v: Int) = if (v % 2 == 0) v + 1 else v

    override fun toString(): String =
        "DiceLayout(S=$scale k=$k panel=$panel outer=$panelOuter fold=$fold faces=${faces[0]}/${faces[1]} " +
            "result=${resultBox[0]} text=${textCol[0]} buttons=${button.joinToString()} diamond R$foldR/$foldHalf)"

    companion object {
        /** Fold-row buttons, left to right on screen. */
        const val D20 = 0
        const val D6 = 1
        const val COIN = 2
        const val CLOSE = 3
        const val BUTTONS = 4

        /** The button rolling [t]. */
        fun buttonOf(t: Tool): Int = when (t) {
            Tool.D20 -> D20
            Tool.D6 -> D6
            Tool.COIN -> COIN
        }

        /** The tool of button [b] (null for [CLOSE]). */
        fun toolOf(b: Int): Tool? = when (b) {
            D20 -> Tool.D20
            D6 -> Tool.D6
            COIN -> Tool.COIN
            else -> null
        }

        /**
         * The result's `num-m` scale per chrome glyph scale: 2 → 28 px tall at S = 4 (~5 mm on the
         * Nomad), read at a glance from across the table; 1 is the readouts' size (T46).
         */
        const val RESULT_K = 2

        /** Caption lines at most: core's dice and coin quips all balance into two (`DiceLayoutTest`). */
        const val MAX_LINES = 2

        /** The longest face label ("nat 20", "HEADS"), in `ui` glyphs. */
        const val LABEL_CHARS = 6

        /** The text column holds at least this many `ui` glyphs a line (core's longest balanced line is 14). */
        const val TEXT_CHARS = 15

        // Design values at S = 4 (dither px), converted per S.
        const val RIM = 2
        const val PAD_H = 4
        const val PAD_V = 2
        const val COL_GAP = 5
        const val FOLD_H = 27
        const val FOLD_CUT = 2
        const val BTN_GAP = 2
        const val BTN_AIR = 2

        /** Glyphs in the longest fold-button label ("coin"). */
        const val LONGEST_BUTTON_LABEL = 4
        const val BTN_MARGIN = 1
        const val X_HALF = 5
        const val X_RUN = 1
        const val LINE_GAP = 3
        const val UNDERLINE_GAP = 2
        const val LABEL_GAP = 4
        const val PLATE_PAD = 2
    }
}
