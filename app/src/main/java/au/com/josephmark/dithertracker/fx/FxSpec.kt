package au.com.josephmark.dithertracker.fx

/**
 * Everything about one effect, as generated from `design/fx/<name>/` by `:app:generateFx` (the
 * shipped [Fx] enum wraps one each; a test-only build of `design/fx/options/` produces more).
 *
 * Frame `i` (0 until [frameCount]) is held [holdMs]`(i)` ms; the last frame is always empty (the
 * clean end state). Each frame is split into disjoint layers by symbol: [ink] (`#`), [knockout]
 * (`o`), [cut] (`x`) and one layer per declared [tiles] entry ([tileLayer], digits `1`–`9`).
 * Because every pixel of a frame holds one symbol, the layers never overlap.
 */
class FxSpec(
    val id: String,
    val role: FxRole,
    /** Frame size in dither px, in the seat's upright frame. */
    val width: Int,
    val height: Int,
    val anchorRef: FxAnchorRef,
    val anchorDx: Int,
    val anchorDy: Int,
    private val holds: IntArray,
    /** The `tile` declarations, in ascending digit order (the order [FxRenderer] draws them). */
    val tiles: List<FxTileSpec>,
    private val inkLayers: Array<FxLayer>,
    private val knockoutLayers: Array<FxLayer>,
    private val cutLayers: Array<FxLayer>,
    /** `[tile index][frame]`, parallel to [tiles]. */
    private val tileLayers: Array<Array<FxLayer>>,
) {
    init {
        val n = holds.size
        require(inkLayers.size == n && knockoutLayers.size == n && cutLayers.size == n) { "$id: layer count != frames" }
        require(tileLayers.size == tiles.size && tileLayers.all { it.size == n }) { "$id: tile layers" }
    }

    /** Number of frames, including the final empty one. */
    val frameCount: Int get() = holds.size

    /** Unscaled hold of [frame] in ms (0 for the last, empty frame). */
    fun holdMs(frame: Int): Int = holds[frame]

    /** The black (`#`) layer of [frame]. */
    fun ink(frame: Int): FxLayer = inkLayers[frame]

    /** The knock-out (`o`, paper) layer of [frame]. */
    fun knockout(frame: Int): FxLayer = knockoutLayers[frame]

    /** The cut (`x`, invert what is under it) layer of [frame]. */
    fun cut(frame: Int): FxLayer = cutLayers[frame]

    /** The layer of [frame] painted by `tiles[index]`. */
    fun tileLayer(index: Int, frame: Int): FxLayer = tileLayers[index][frame]

    /** True when the frame centre is anchored to the seat tile (`anchor tile`), not the number. */
    val isTileScale: Boolean get() = anchorRef == FxAnchorRef.TILE

    override fun toString(): String = id
}
