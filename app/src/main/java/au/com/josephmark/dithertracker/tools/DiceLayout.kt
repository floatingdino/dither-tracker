package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Chrome
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.widgets.TextWrap

/**
 * Pure geometry of the dice / coin panel (T16, `design/menu/concept-1-gem-dice.png`): a two-faced
 * "tent" on the crossing, a sibling of the gem's restart confirm (`menu.GemLayout`). Dither px on
 * the play grid; no Android types.
 *
 * ```
 *  ┌──────────────────────────────┐  upper face: the lower face turned 180° about the crossing
 *  │ caption ˙ʇɐɥʇ  ┃ result (180°) │  (read from the N edge)
 *  ├──────────────────────────────┤
 *  │ [d20][d6]  ◆  [coin][ × ]     │  fold row: two-faced buttons, the roll-again diamond on the crossing
 *  ├──────────────────────────────┤
 *  │ result   ┃ d20              │  lower face (read from the S edge): the hero number (`num-l`
 *  │ (num-l)  ┃ caption, wrapped │  with its shadow) or the coin, then the die label and the quip
 *  └──────────────────────────────┘
 * ```
 *
 * Sizes: glyphs at the chrome's glyph scale [k] (`play.tk`), spacing designed at S = 4 and converted
 * per S like `PlayLayout.Chrome` (constant physical size). The panel is sized from its content: each
 * face is exactly one hero numeral tall (+ [PAD_V]) and one two-digit hero + a [TEXT_W] text column
 * wide. At S = 4 that is 157×147 (163×153 with the rim), inside the free cross around the crossing:
 * clear of two-digit life totals, the readouts and every ± glyph (see `DiceLayoutTest`).
 *
 * The faces are point-symmetric about the crossing (each long side reads its own face upright), so
 * the panel is centred on [cx], [cy] with odd width and height and every upper-face rect is
 * [reflect]ed from its lower-face twin.
 */
class DiceLayout(val play: PlayLayout) {
    val scale: Int = play.grid.scale

    /** Glyph scale of everything on the panel (hero, ui, icons). */
    val k: Int = play.tk

    val hero: Font = Fonts.NUM_L
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

    /** Per face: the hero box (a two-digit `num-l` footprint) and the text column beside it. */
    val heroBox: Array<IRect> = Array(2) { IRect() }
    val textCol: Array<IRect> = Array(2) { IRect() }

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

    init {
        val heroW = hero.widthOf(2, k)
        val heroH = hero.height(k)
        val b = 1
        val faceH = heroH + 2 * padV
        val foldH = odd(maxOf(Chrome.size(FOLD_H, scale), play.target))
        val gap = Chrome.size(BTN_GAP, scale)
        val margin = Chrome.size(BTN_MARGIN, scale)
        val r = foldH / 2 + 1
        // Wide enough for the text column ([TEXT_W], never under [MIN_CHARS] glyphs) and for four
        // fingertip-sized fold buttons (hit = button + gap ≥ target) beside the diamond.
        val textW = maxOf(Chrome.size(TEXT_W, scale), ui.widthOf(MIN_CHARS, k))
        val bwMin = play.target - gap
        val foldW = 2 * (margin + 2 * bwMin + 2 * gap + r) + 1
        val faceW = odd(maxOf(padH + heroW + colGap + textW + padH, foldW))
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
        heroBox[0].setXywh(f.left + padH, f.top + padV, heroW, heroH)
        textCol[0].set(heroBox[0].right + colGap, f.top, f.right - padH, f.bottom)
        reflect(heroBox[0], heroBox[1])
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

    private val wrap = TextWrap(MAX_LINES)

    /**
     * Lays out [labelText] (underlined) over [caption] (wrapped, ≤ [MAX_LINES]) in each face's text
     * column, the block centred vertically. Allocation-free.
     */
    fun arrange(labelText: CharSequence, caption: CharSequence) {
        val col = textCol[0]
        val n = wrap.wrap(caption, ui, k, col.width)
        lines = n
        truncated = wrap.truncated
        val capH = ui.capHeight * k
        val capTop = ui.capTop * k
        val th = ui.height(k)
        val blockH = capH + underlineGap + 1 + (if (n > 0) labelGap + n * capH + (n - 1) * lineGap else 0)
        var y = col.top + (col.height - blockH) / 2 // cap top of the label
        val lw = ui.widthOf(labelText.length, k)
        label[0].setXywh(col.left, y - capTop, lw, th)
        underline[0].setXywh(col.left, y + capH + underlineGap, lw, 1)
        y += capH + underlineGap + 1 + labelGap
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
            "hero=${heroBox[0]} text=${textCol[0]} buttons=${button.joinToString()} diamond R$foldR/$foldHalf)"

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

        /** Caption lines at most (13 `ui` glyphs a line at S = 4; core's longest quips take 2). */
        const val MAX_LINES = 3

        // Design values at S = 4 (dither px), converted per S.
        const val RIM = 2
        const val TEXT_W = 77

        /** The text column holds at least this many `ui` glyphs a line (core's longest word is 10). */
        const val MIN_CHARS = 12
        const val PAD_H = 3
        const val PAD_V = 1
        const val COL_GAP = 4
        const val FOLD_H = 27
        const val FOLD_CUT = 2
        const val BTN_GAP = 3
        const val BTN_MARGIN = 1
        const val X_HALF = 5
        const val X_RUN = 1
        const val LINE_GAP = 3
        const val UNDERLINE_GAP = 2
        const val LABEL_GAP = 4
    }
}
