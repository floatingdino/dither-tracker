package au.com.josephmark.dithertracker.widgets

import au.com.josephmark.dithertracker.dither.IRect

/**
 * Base of every widget: a pure model + layout in its owner's *local* frame (see [WidgetFrame]).
 * No Android types; drawing is [WidgetPainter]'s job.
 *
 * Lifecycle: the host (or a container such as [Panel]) asks for [naturalWidth]/[naturalHeight], calls
 * [layout] with a local rect, then [collectHits] into a [HitRegion] with the frame the widget is
 * drawn in. Change a model field → lay out again (cheap, allocation-free) and re-collect hits.
 *
 * Ids: a widget with parts (stepper −/+, segments, confirm/cancel, prev/next) owns the consecutive ids
 * `id until id + idCount`; part `p` is `id + p`. Ids are local to the screen that builds the widgets;
 * the host maps them to its own zone space. A widget with [NO_ID] registers nothing.
 */
abstract class Widget(val id: Int) {

    /** Visible rect, local. Everything the widget draws stays inside it. */
    val bounds = IRect()

    /**
     * Touch rect for single-part widgets (local): [bounds] after [layout]; a container may grow it
     * (e.g. a row's button reaching the row's full height) so it meets the fingertip rule.
     */
    val hit = IRect()

    /** Disabled widgets draw on [au.com.josephmark.dithertracker.dither.Role.DISABLED] and register no hits. */
    var enabled: Boolean = true

    /** Metrics of the last [layout]. */
    lateinit var metrics: WidgetMetrics
        private set

    /** Number of consecutive ids this widget owns (0 for containers without an id). */
    open val idCount: Int get() = if (id >= 0) 1 else 0

    /** True when [someId] is one of this widget's ids. */
    fun owns(someId: Int): Boolean = id >= 0 && someId >= id && someId < id + idCount

    /** Part index of [someId] (`someId - id`). */
    fun partOf(someId: Int): Int = someId - id

    /** Width the widget would like, dither px. */
    open fun naturalWidth(m: WidgetMetrics): Int = m.target

    /** Height for a given [width], dither px. */
    open fun naturalHeight(m: WidgetMetrics, width: Int): Int = m.rowH

    /** Places the widget in the local rect (x, y, w, h) and lays out its parts. */
    fun layout(m: WidgetMetrics, x: Int, y: Int, w: Int, h: Int) {
        metrics = m
        bounds.setXywh(x, y, w, h)
        hit.setXywh(x, y, w, h)
        onLayout(m)
    }

    protected abstract fun onLayout(m: WidgetMetrics)

    /** Registers this widget's live parts (screen rects via [frame]). Default: one part = [hit]/[bounds]. */
    open fun collectHits(region: HitRegion, frame: WidgetFrame) {
        if (id >= 0 && enabled) region.addLocal(id, frame, hit, bounds, owner = this)
    }

    companion object {
        const val NO_ID = -1
    }
}
