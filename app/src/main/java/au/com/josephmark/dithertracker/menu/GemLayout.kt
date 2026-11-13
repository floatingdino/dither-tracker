package au.com.josephmark.dithertracker.menu

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Fonts

/**
 * Pure geometry of the table menu (T09, concept 1 "gem", `design/menu/render.py`): the centre diamond
 * grown into a stepped diamond cut into a 3×3 lattice of diamond facets, plus the two-faced restart
 * confirm ("tent" panel) on the crossing. Dither px on the play grid; no Android types.
 *
 * **Lattice.** With `u = dx + dy` and `v = dx − dy` (dx, dy from the crossing), `|dx| + |dy| =
 * max(|u|, |v|)`: the gem is a square in (u, v) and its facets are the 3×3 squares of side `2·r`. So
 * facet `(iu, iv)` (each −1..1) is centred at `dx = (iu + iv)·r, dy = (iu − iv)·r`, every edge is a 45°
 * staircase (one px across per px down), and hit-testing is two floor divisions ([slotAt]). Slots are
 * numbered `(iv + 1)·3 + (iu + 1)`: [W] tip, [SW], [S] tip, [NW], [CENTRE], [SE], [N] tip, [NE], [E] tip.
 *
 * - A facet is drawn as `|dx'| + |dy'| ≤ rf` around its centre (`rf = r − gutter`): the gutters
 *   between facets and the rim show the `MENU` dither underneath; a 1-px staircase ring outlines the
 *   gem at radius [outerR].
 * - A facet's **hit cell** is its whole lattice square (gutters split between neighbours, the rim
 *   belongs to the outer facets): its inscribed axis-aligned square is `r + 1` px a side, ≥ the
 *   fingertip [PlayLayout.target] at every S.
 * - **Orientation** (occupied-edge rule, 2×2 table: seats on the two long edges only): the lower
 *   three facets face S (rotation 0), the upper three face N (180); the [W] and [E] tips straddle the
 *   divider and are two-faced ([FOLD]): they show one rotation-symmetric icon centred on the fold.
 * - Sizes are designed at S = 4 and converted per S like `PlayLayout.Chrome` (constant physical size).
 *   At S = 4: r 29, rf 27, outer R 90 (181×181 px, ~61 mm); confirm panel 191×93 + rim.
 */
class GemLayout(val play: PlayLayout) {
    val scale: Int = play.grid.scale

    /** Glyph/icon scale of the facet content (same as the play chrome's). */
    val k: Int = play.tk

    /** The crossing. */
    val cx: Int = play.diamondCx
    val cy: Int = play.diamondCy

    /** Lattice half-pitch: facet centres are `2·r` apart in u/v. */
    val r: Int = PlayLayout.Chrome.size(UNIT, scale, 4)

    /** Drawn facet radius (`|dx| + |dy| ≤ rf`). */
    val rf: Int = maxOf(2, r - PlayLayout.Chrome.size(GUTTER, scale))

    /** Outline radius: the 1-px staircase ring sits at `outerR` / `outerR − 1`. */
    val outerR: Int = 3 * r + PlayLayout.Chrome.size(RIM, scale)

    /** Bounding rect of the gem (what opening/closing redraws). */
    val bounds = IRect(cx - outerR, cy - outerR, cx + outerR + 1, cy + outerR + 1)

    /** Facet centres (screen). */
    val facetX = IntArray(SLOTS)
    val facetY = IntArray(SLOTS)

    /** Facet bounding rects (the drawn diamond): what a press inverts (shape-compensated, see `MenuOverlay`). */
    val facetRect: Array<IRect> = Array(SLOTS) { IRect() }

    /** Clockwise rotation of each facet's content: 0 (faces S), 180 (faces N) or [FOLD]. */
    val rotation = IntArray(SLOTS)

    /** True when the facets are big enough for icon + label (S ≤ 4 on the Nomad); else icons only. */
    val labelled: Boolean

    /** Per facet: icon footprint (16·k square) and label footprint (screen; empty when not labelled). */
    val iconRect: Array<IRect> = Array(SLOTS) { IRect() }
    val labelRect: Array<IRect> = Array(SLOTS) { IRect() }

    /** Centre close X: half-extent (rows −xHalf..xHalf) and odd horizontal run per row. */
    val xHalf: Int = PlayLayout.Chrome.size(X_HALF, scale)
    val xRun: Int = 2 * PlayLayout.Chrome.size(X_RUN, scale) + 1

    /** Gap between a tip's centred icon and the fold dots either side of it. */
    val foldGap: Int = k

    /** Optional bloom keyframe (closed → key1 → open): an empty doubled diamond of radius [key1R]. */
    val key1R: Int = maxOf(r, 2 * r - PlayLayout.Chrome.size(KEY1_SHRINK, scale))
    val key1Bounds: IRect

    init {
        for (s in 0 until SLOTS) {
            val iu = s % 3 - 1
            val iv = s / 3 - 1
            facetX[s] = cx + (iu + iv) * r
            facetY[s] = cy + (iu - iv) * r
            facetRect[s].set(facetX[s] - rf, facetY[s] - rf, facetX[s] + rf + 1, facetY[s] + rf + 1)
            val dy = iu - iv
            rotation[s] = when {
                dy > 0 -> 0
                dy < 0 -> 180
                else -> FOLD
            }
        }
        rotation[CENTRE] = 0
        // Labelled content (upright facet frame, c = facet centre): icon box rows c−16k..c−1, label cap
        // band rows c+2k..c+8k. It fits when the icon art's corner (1-px art margin: c ± 7k, c − 15k) and
        // the widest label's corner stay inside the facet with a 1-px paper margin.
        val ui = Fonts.UI
        var maxLen = 0
        for (it in MenuItem.entries) maxLen = maxOf(maxLen, it.label.length)
        val labelHalf = (ui.widthOf(maxLen, k) - 1) / 2
        labelled = (LABEL_TOP + ui.capHeight - 1) * k + labelHalf <= rf - 1 && (ICON - 1 + ICON / 2 - 1) * k <= rf - 1
        for (s in 0 until SLOTS) placeContent(s)
        val k1 = key1R + PlayLayout.Chrome.size(RIM, scale)
        key1Bounds = IRect(cx - k1, cy - k1, cx + k1 + 1, cy + k1 + 1)
    }

    private fun placeContent(s: Int) {
        val fx = facetX[s]
        val fy = facetY[s]
        val ic = ICON * k
        val half = ic / 2
        val item = ITEMS[s]
        val rot = rotation[s]
        if (item == null || rot == FOLD || !labelled) {
            iconRect[s].setXywh(fx - half, fy - half, ic, ic)
            labelRect[s].set(0, 0, 0, 0)
            return
        }
        // Upright offsets from the centre, turned 180° for N-facing facets (a point reflection).
        val ui = Fonts.UI
        val tw = ui.widthOf(item.label.length, k)
        val th = ui.height(k)
        val iconX = -half
        val iconY = -ic
        val labX = -(tw - 1) / 2
        val labY = LABEL_TOP * k
        if (rot == 0) {
            iconRect[s].setXywh(fx + iconX, fy + iconY, ic, ic)
            labelRect[s].setXywh(fx + labX, fy + labY, tw, th)
        } else {
            iconRect[s].setXywh(fx - iconX - ic + 1, fy - iconY - ic + 1, ic, ic)
            labelRect[s].setXywh(fx - labX - tw + 1, fy - labY - th + 1, tw, th)
        }
    }

    /** Half-width of facet row [dy] (−rf..rf) around the facet's centre column; −1 outside. */
    fun facetRowHalf(dy: Int): Int {
        val a = if (dy < 0) -dy else dy
        return if (a > rf) -1 else rf - a
    }

    /** Whether (gx, gy) is inside the gem (ring included). */
    fun inGem(gx: Int, gy: Int): Boolean = Math.abs(gx - cx) + Math.abs(gy - cy) <= outerR

    /** Whether (gx, gy) is on the drawn diamond of facet [s]. */
    fun onFacet(s: Int, gx: Int, gy: Int): Boolean = Math.abs(gx - facetX[s]) + Math.abs(gy - facetY[s]) <= rf

    /** The slot whose hit cell holds (gx, gy), or −1 outside the gem. */
    fun slotAt(gx: Int, gy: Int): Int {
        val dx = gx - cx
        val dy = gy - cy
        if (Math.abs(dx) + Math.abs(dy) > outerR) return -1
        val p = 2 * r
        val iu = Math.floorDiv(dx + dy + r, p).coerceIn(-1, 1)
        val iv = Math.floorDiv(dx - dy + r, p).coerceIn(-1, 1)
        return (iv + 1) * 3 + (iu + 1)
    }

    // ---- restart confirm: a two-faced tent panel on the crossing (concept-1-gem-confirm.png) ----------

    /** Panel interior (1-px ink border included) and the whole thing with its MENU rim and outer line. */
    val panel = IRect()
    val panelOuter = IRect()
    val panelRim: Int = PlayLayout.Chrome.size(PANEL_RIM, scale)

    /** The two faces (inside the border): the upper one is read from N (180°), the lower from S. */
    val upperFace = IRect()
    val lowerFace = IRect()

    /** The fold row between the faces (the divider runs along its middle row, [cy]). */
    val fold = IRect()

    /** Fold-row buttons (visual) and their touch rects (the full fold-row height). */
    val cancelBtn = IRect()
    val confirmBtn = IRect()
    val cancelHit = IRect()
    val confirmHit = IRect()

    /** The close diamond at the crossing on the fold (= cancel): radius, flat-tip half-height, bounds. */
    val foldR: Int
    val foldHalf: Int
    val foldRect = IRect()
    val foldXHalf: Int = PlayLayout.Chrome.size(FOLD_X_HALF, scale)
    val foldXRun: Int = 2 * PlayLayout.Chrome.size(FOLD_X_RUN, scale) + 1

    /** Prompt text: lines this wide at most, [lineGap] apart. */
    val faceTextW: Int
    val lineGap: Int = PlayLayout.Chrome.size(LINE_GAP, scale)

    init {
        val w = oddSize(PANEL_W)
        val faceH = PlayLayout.Chrome.size(FACE_H, scale)
        val foldH = oddSize(FOLD_H)
        val b = 1 // border line, 1 dither px like every outline
        val h = b + faceH + b + foldH + b + faceH + b
        val x0 = cx - w / 2
        val foldTop = cy - foldH / 2
        val y0 = foldTop - b - faceH - b
        panel.setXywh(x0, y0, w, h)
        val o = panelRim + 1
        panelOuter.set(panel.left - o, panel.top - o, panel.right + o, panel.bottom + o)
        upperFace.setXywh(x0 + b, y0 + b, w - 2 * b, faceH)
        fold.setXywh(x0 + b, foldTop, w - 2 * b, foldH)
        lowerFace.setXywh(x0 + b, foldTop + foldH + b, w - 2 * b, faceH)
        faceTextW = upperFace.width - 2 * PlayLayout.Chrome.size(FACE_PAD, scale)
        // Fold row: [cancel] ◆ [confirm], the diamond on the crossing.
        foldR = foldH / 2 + 1
        foldHalf = minOf(foldR - PlayLayout.Chrome.size(FOLD_CUT, scale), foldH / 2 - 1)
        foldRect.set(cx - foldR, cy - foldHalf, cx + foldR + 1, cy + foldHalf + 1)
        val gap = PlayLayout.Chrome.size(BTN_GAP, scale)
        val bw = minOf(PlayLayout.Chrome.size(BTN_W, scale), cx - foldR - gap - 1 - (x0 + b) - 1)
        val bh = foldH - 2
        val by = foldTop + 1
        cancelBtn.setXywh(cx - foldR - gap - bw, by, bw, bh)
        confirmBtn.setXywh(cx + foldR + gap + 1, by, bw, bh)
        cancelHit.set(cancelBtn.left, fold.top, cancelBtn.right, fold.bottom)
        confirmHit.set(confirmBtn.left, fold.top, confirmBtn.right, fold.bottom)
    }

    /** Whether (gx, gy) is on the fold's close diamond. */
    fun onFoldDiamond(gx: Int, gy: Int): Boolean {
        val dx = Math.abs(gx - cx)
        val dy = Math.abs(gy - cy)
        return dy <= foldHalf && dx + dy <= foldR
    }

    /** Half-width of fold-diamond row [dy]; −1 outside. */
    fun foldRowHalf(dy: Int): Int {
        val a = if (dy < 0) -dy else dy
        return if (a > foldHalf) -1 else foldR - a
    }

    private fun oddSize(base: Int): Int = PlayLayout.Chrome.size(base, scale).let { if (it % 2 == 0) it + 1 else it }

    override fun toString(): String =
        "GemLayout(S=$scale k=$k r=$r rf=$rf R=$outerR bounds=$bounds labelled=$labelled panel=$panel outer=$panelOuter)"

    companion object {
        const val SLOTS = 9
        const val W = 0
        const val SW = 1
        const val S = 2
        const val NW = 3
        const val CENTRE = 4
        const val SE = 5
        const val N = 6
        const val NE = 7
        const val E = 8

        /** [rotation] of a two-faced tip on the horizontal divider. */
        const val FOLD = -1

        /**
         * The facet assignment (T09; Sam: no icon-only undo/redo on the tips). The two divider tips hold
         * the rotation-symmetric icons (dice, refresh), which read the same from both long sides; the six
         * labelled facets sit in 180°-symmetric pairs so neither side owns the menu: UNDO (SW) | REDO
         * (NE), LOG (NW) | SETUP (SE), NEW (N) | FIRST (S). Each long side reads one of each pair upright
         * at the same place (its own lower-left is UNDO for S, REDO for N).
         */
        val ITEMS: Array<MenuItem?> = arrayOf(
            MenuItem.DICE, MenuItem.UNDO, MenuItem.FIRST,
            MenuItem.LOG, null, MenuItem.SETUP,
            MenuItem.NEW, MenuItem.REDO, MenuItem.CLEAN,
        )

        /** Slot of [item]. */
        fun slotOf(item: MenuItem): Int {
            for (i in ITEMS.indices) if (ITEMS[i] == item) return i
            throw IllegalArgumentException("$item has no slot")
        }

        // Design values at S = 4 (dither px), converted per S.
        const val UNIT = 29
        const val GUTTER = 2
        const val RIM = 3
        const val ICON = 16
        /** Label cap band top, in k, below the facet centre. */
        const val LABEL_TOP = 2
        const val X_HALF = 7
        const val X_RUN = 2
        const val KEY1_SHRINK = 6

        const val PANEL_W = 191
        const val FACE_H = 30
        const val FACE_PAD = 4
        const val FOLD_H = 29
        const val BTN_W = 70
        const val BTN_GAP = 4
        const val PANEL_RIM = 3
        const val FOLD_CUT = 2
        const val FOLD_X_HALF = 5
        const val FOLD_X_RUN = 1
        const val LINE_GAP = 4
    }
}
