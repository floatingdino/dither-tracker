package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.dither.Icon
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.TextLayout

/*
 * Row widgets: full-width, [WidgetMetrics.rowH] tall, content inset [WidgetMetrics.padX] from the row's
 * left/right edges (a [Panel] stacks them edge to edge between dotted dividers). A label sits on the
 * left in the `ui` face, a control on the right. Labels cut cleanly ([TextFit]) when space runs out.
 * A disabled row is drawn on the [au.com.josephmark.dithertracker.dither.Role.DISABLED] hatch (label on
 * a paper backing) and registers no hits.
 */

/** Places a cap-band-centred icon footprint with its left edge at [x], vertically centred in [row]. */
private fun iconAt(m: WidgetMetrics, row: IRect, x: Int, out: IRect): IRect =
    out.setXywh(x, row.top + TextLayout.centre(row.height, m.icon), m.icon, m.icon)

/**
 * A tappable list row: optional leading [icon], [label], optional right-aligned secondary [value]
 * text and an optional [chevron] (›, "opens something"). The whole row is the hit and feedback rect.
 * When label and value do not both fit, the value keeps at most half of the room.
 */
class ListRow(
    id: Int,
    var label: CharSequence,
    var value: CharSequence? = null,
    var icon: Icon? = null,
    var chevron: Boolean = false,
) : Widget(id) {

    val iconRect = IRect()
    val labelRect = IRect()
    val valueRect = IRect()
    val chevronRect = IRect()
    var labelChars = 0
        private set
    var valueChars = 0
        private set

    override fun naturalWidth(m: WidgetMetrics): Int {
        var w = 2 * m.padX + m.textW(label.length)
        if (icon != null) w += m.icon + m.gap
        if (chevron) w += m.icon + m.gap
        val v = value
        if (v != null && v.isNotEmpty()) w += m.gap + m.textW(v.length)
        return w
    }

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        var x = b.left + m.padX
        var right = b.right - m.padX
        if (icon != null) { iconAt(m, b, x, iconRect); x += m.icon + m.gap } else iconRect.set(0, 0, 0, 0)
        if (chevron) { iconAt(m, b, right - m.icon, chevronRect); right -= m.icon + m.gap } else chevronRect.set(0, 0, 0, 0)
        val avail = maxOf(0, right - x)
        var labelAvail = avail
        valueChars = 0
        val v = value
        if (v != null && v.isNotEmpty()) {
            val valueNat = m.textW(v.length)
            val valueW = if (m.textW(label.length) + m.gap + valueNat <= avail) valueNat else minOf(valueNat, (avail - m.gap) / 2)
            valueChars = TextFit.fit(v, m.ui, m.k, maxOf(0, valueW))
            val vw = m.textW(valueChars)
            valueRect.setXywh(right - vw, b.top, vw, b.height)
            if (valueChars > 0) labelAvail = maxOf(0, valueRect.left - m.gap - x)
        } else {
            valueRect.set(0, 0, 0, 0)
        }
        labelChars = TextFit.fit(label, m.ui, m.k, labelAvail)
        labelRect.setXywh(x, b.top, labelAvail, b.height)
    }
}

/**
 * An on/off row: [label] and a check box on the right.
 *
 * The switch is a square box (an icon plus a stroke each side): **on = solid ink box with the check
 * knocked out in paper**, **off = empty outlined box**. On e-ink the black square reads at a glance
 * from across the table (the largest possible difference between the two states in a fingertip of
 * space, no "which side is the knob" puzzle), and the 1-px outline keeps "off" from looking absent.
 * The whole row is the hit and feedback rect; the host flips [checked] on tap ([toggled]).
 */
class Toggle(id: Int, var label: CharSequence, var checked: Boolean = false) : Widget(id) {

    val box = IRect()
    val checkRect = IRect()
    val labelRect = IRect()
    var labelChars = 0
        private set

    /** The value a tap sets. */
    fun toggled(): Boolean = !checked

    fun boxSize(m: WidgetMetrics): Int = m.icon + 2 * m.stroke

    override fun naturalWidth(m: WidgetMetrics): Int = 2 * m.padX + m.textW(label.length) + m.gap + boxSize(m)

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        val bs = boxSize(m)
        box.setXywh(b.right - m.padX - bs, b.top + TextLayout.centre(b.height, bs), bs, bs)
        checkRect.setXywh(box.left + m.stroke, box.top + m.stroke, m.icon, m.icon)
        val x = b.left + m.padX
        val avail = maxOf(0, box.left - m.gap - x)
        labelChars = TextFit.fit(label, m.ui, m.k, avail)
        labelRect.setXywh(x, b.top, avail, b.height)
    }
}

/**
 * A number row: [label], then `[−] value [+]` on the right with the value in `num-m`.
 *
 * Parts: [MINUS] (`id`) and [PLUS] (`id + 1`). Each button is an outlined square [WidgetMetrics.inset]
 * inside a fingertip column the full row height (touch rect = column, feedback = the square). A button
 * at its limit is hatched and registers no hit. The value slot is sized for the widest of [min]/[max],
 * so the row never reflows as the value changes.
 *
 * Hold-repeat hook: the parts register as repeating when [repeats] is set; feed
 * [PressTracker.Sink.onTap] to `apply(part, repeat = false)` (±[step]) and
 * [PressTracker.Sink.onRepeat] to `apply(part, repeat = true)` (±[holdStep]) — the play screen's model:
 * a hold never also applies the tap. The widget owns no timers.
 */
class Stepper(
    id: Int,
    var label: CharSequence,
    var value: Int,
    var min: Int,
    var max: Int,
    var step: Int = 1,
    var holdStep: Int = 10,
    var repeats: Boolean = true,
) : Widget(id) {
    init {
        require(min <= max) { "min $min > max $max" }
    }

    override val idCount: Int get() = 2

    val minusHit = IRect()
    val plusHit = IRect()
    val minusBtn = IRect()
    val plusBtn = IRect()
    val minusIcon = IRect()
    val plusIcon = IRect()
    val valueRect = IRect()
    val labelRect = IRect()
    var labelChars = 0
        private set

    val canDecrease: Boolean get() = value > min
    val canIncrease: Boolean get() = value < max

    /** Value after a tap ([repeat] false) or a hold step on [part], clamped to [min]..[max]. */
    fun valueAfter(part: Int, repeat: Boolean): Int {
        val d = if (repeat) holdStep else step
        val v = value.toLong() + if (part == PLUS) d else -d
        return v.coerceIn(min.toLong(), max.toLong()).toInt()
    }

    /** Applies a tap/hold step to [value]; true when it changed. */
    fun apply(part: Int, repeat: Boolean): Boolean {
        val v = valueAfter(part, repeat)
        if (v == value) return false
        value = v
        return true
    }

    /** Characters of the widest value (sign included). */
    fun valueChars(): Int = maxOf(Font.intLength(min), Font.intLength(max))

    private fun valueW(m: WidgetMetrics): Int = m.num.widthOf(valueChars(), m.k) + 2 * m.gap

    override fun naturalWidth(m: WidgetMetrics): Int =
        2 * m.padX + m.textW(label.length) + m.gap + 2 * m.rowH + valueW(m) - 2 * m.inset

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        val hw = b.height
        val clusterRight = b.right - m.padX + m.inset
        plusHit.setXywh(clusterRight - hw, b.top, hw, b.height)
        val vw = valueW(m)
        valueRect.setXywh(plusHit.left - vw, b.top, vw, b.height)
        minusHit.setXywh(valueRect.left - hw, b.top, hw, b.height)
        insetSquare(m, minusHit, minusBtn)
        insetSquare(m, plusHit, plusBtn)
        minusIcon.setXywh(minusBtn.left + TextLayout.centre(minusBtn.width, m.icon), minusBtn.top + TextLayout.centre(minusBtn.height, m.icon), m.icon, m.icon)
        plusIcon.setXywh(plusBtn.left + TextLayout.centre(plusBtn.width, m.icon), plusBtn.top + TextLayout.centre(plusBtn.height, m.icon), m.icon, m.icon)
        val x = b.left + m.padX
        val avail = maxOf(0, minusBtn.left - m.gap - x)
        labelChars = TextFit.fit(label, m.ui, m.k, avail)
        labelRect.setXywh(x, b.top, avail, b.height)
    }

    private fun insetSquare(m: WidgetMetrics, hit: IRect, out: IRect) {
        val side = hit.height - 2 * m.inset
        out.setXywh(hit.left + TextLayout.centre(hit.width, side), hit.top + m.inset, side, side)
    }

    override fun collectHits(region: HitRegion, frame: WidgetFrame) {
        if (id < 0 || !enabled) return
        if (canDecrease) region.addLocal(id + MINUS, frame, minusHit, minusBtn, repeats, this)
        if (canIncrease) region.addLocal(id + PLUS, frame, plusHit, plusBtn, repeats, this)
    }

    companion object {
        const val MINUS = 0
        const val PLUS = 1
    }
}

/**
 * A choice of 2–5 [options] (`ui` labels) as one outlined bar of equal segments; the [selected]
 * segment is **inverted** (solid ink, paper label) — the same visual language as press feedback and
 * as the Dark button, so "the black one is the current choice" needs no legend.
 *
 * Parts: option i is `id + i`. Touch rect per segment = its column, full row height; feedback = the
 * segment. With a [label], the bar sits on the right at its natural width (each segment ≥ fingertip
 * and wide enough for the longest option); without one, it fills the row. When space runs out the
 * label is cut, then dropped, and option labels are cut.
 */
class Segmented(
    id: Int,
    var label: CharSequence,
    val options: Array<out CharSequence>,
    var selected: Int = 0,
) : Widget(id) {
    init {
        require(options.size in 2..MAX_OPTIONS) { "2..$MAX_OPTIONS options, got ${options.size}" }
    }

    override val idCount: Int get() = options.size

    val bar = IRect()
    val segVis: Array<IRect> = Array(options.size) { IRect() }
    val segHit: Array<IRect> = Array(options.size) { IRect() }
    val segChars = IntArray(options.size)
    val labelRect = IRect()
    var labelChars = 0
        private set

    /** Selects option [part]; true when the selection changed. */
    fun select(part: Int): Boolean {
        if (part !in options.indices || part == selected) return false
        selected = part
        return true
    }

    fun segmentNatural(m: WidgetMetrics): Int {
        var longest = 0
        for (o in options) longest = maxOf(longest, o.length)
        return maxOf(m.target, m.textW(longest) + 2 * m.gap + m.stroke)
    }

    override fun naturalWidth(m: WidgetMetrics): Int {
        val lw = if (label.isEmpty()) 0 else m.textW(label.length) + m.gap
        return 2 * m.padX + lw + options.size * segmentNatural(m)
    }

    override fun onLayout(m: WidgetMetrics) {
        val b = bounds
        val n = options.size
        val x = b.left + m.padX
        val innerW = maxOf(0, b.width - 2 * m.padX)
        val nat = n * segmentNatural(m)
        val labelNat = m.textW(label.length)
        val barW = when {
            label.isEmpty() -> innerW
            nat <= innerW -> nat
            else -> innerW
        }
        val labelAvail = if (label.isEmpty() || barW >= innerW) 0 else maxOf(0, minOf(labelNat, innerW - barW - m.gap))
        labelChars = TextFit.fit(label, m.ui, m.k, labelAvail)
        labelRect.setXywh(x, b.top, labelAvail, b.height)
        bar.setXywh(x + innerW - barW, b.top + m.inset, barW, b.height - 2 * m.inset)
        for (i in 0 until n) {
            val x0 = bar.left + barW * i / n
            val x1 = bar.left + barW * (i + 1) / n
            segVis[i].set(x0, bar.top, x1, bar.bottom)
            segHit[i].set(x0, b.top, x1, b.bottom)
            segChars[i] = TextFit.fit(options[i], m.ui, m.k, maxOf(0, x1 - x0 - 2 * m.gap - m.stroke))
        }
    }

    override fun collectHits(region: HitRegion, frame: WidgetFrame) {
        if (id < 0 || !enabled) return
        for (i in options.indices) region.addLocal(id + i, frame, segHit[i], segVis[i], owner = this)
    }

    companion object {
        const val MAX_OPTIONS = 5
    }
}
