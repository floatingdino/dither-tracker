package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.dither.IRect

/**
 * The roll presentation (T52): what the dice panel shows between a tap and the result — a few held
 * keyframes (SPEC rule 4), never inbetweens. Pure data; [PresentationLayout] places it on a
 * [DiceLayout], [DicePainter] draws it, [DiceState] times it.
 *
 * A [Presentation] is a sprite table (rows as in `DiceSprites`) and one [Script] per [Outcome]
 * (each natural and each coin face gets its own landing). A script is a list of [Beat]s; a beat is
 * held [Beat.holdMs] (× `ToolsDebug.holdScale`) and draws its [Place]d sprites on both faces (the
 * upper face is the lower one turned 180° about the crossing), plus the result when [Beat.result]
 * says so. After the last beat the panel shows the plain result, label and caption ([DiceState.SHOWN]).
 *
 * Shipped: [TUMBLE] (variant A, see `design/tools/README.md`). Variant B lives in the tests.
 */
class Presentation internal constructor(
    internal val sprites: Array<Array<String>>,
    /** Indexed by [Outcome]. */
    internal val scripts: Array<Script>,
) {
    init {
        require(scripts.size == Outcome.COUNT) { "${scripts.size} scripts" }
        for (s in scripts) {
            require(s.beats.isNotEmpty())
            for (b in s.beats) {
                require(b.holdMs >= MIN_HOLD_MS) { "${s.name}: hold ${b.holdMs} < $MIN_HOLD_MS" }
                for (p in b.places) require(p.sprite in sprites.indices) { "${s.name}: sprite ${p.sprite}" }
            }
        }
    }

    fun beats(outcome: Int): Int = scripts[outcome].beats.size

    fun holdMs(outcome: Int, beat: Int): Long = scripts[outcome].beats[beat].holdMs

    /** Whole presentation of [outcome], unscaled. */
    fun totalMs(outcome: Int): Long = scripts[outcome].beats.sumOf { it.holdMs }

    /** Longest presentation, unscaled. */
    val maxMs: Long = (0 until Outcome.COUNT).maxOf { totalMs(it) }

    companion object {
        /** SPEC rule 4: each keyframe held long enough for the panel to settle. */
        const val MIN_HOLD_MS = 250L

        /** Variant A, "tumble across the face" (the shipped one). */
        val TUMBLE: Presentation by lazy { tumble() }

        private const val TOSS = 250L
        private const val BOUNCE = 250L
        private const val SKID = 250L
        private const val LAND = 300L
        private const val LAND_BIG = 400L

        private fun tumble(): Presentation {
            val s = DiceSprites
            val table = arrayOf(
                s.D20_A, s.D20_B, s.D20_C, s.D6_A, s.D6_B, s.D6_C, s.COIN_A, s.COIN_B, s.COIN_C,
                s.TRAIL, s.DUST, s.IMPACT, s.TWINKLE, s.TWINKLE_S, s.SWEAT, s.PUFF, s.ARC_UP, s.ARC_DOWN, s.THUD,
            )
            val d20 = 0; val d6 = 3; val coin = 6
            val trail = 9; val dust = 10; val impact = 11; val twinkle = 12; val twinkleS = 13
            val sweat = 14; val puff = 15; val arcUp = 16; val arcDown = 17; val thud = 18
            val t = Region.TRACK
            val b = Region.BOX

            // The die enters at the far end of the face, bounces once mid-face, skids in beside the
            // result box and lands there (it travels towards the face's start, so the speed ticks trail
            // to its right).
            fun tumbleBeats(first: Int) = arrayOf(
                Beat(TOSS, Beat.NONE, Place(first, t, 1000, 0, 1000, 0, dx = -10), Place(trail, t, 1000, 0, 1000, 0, dy = 8)),
                Beat(BOUNCE, Beat.NONE, Place(first + 1, t, 560, 1000, 500, 1000), Place(dust, t, 560, 1000, 500, 1000, dy = 1)),
                Beat(SKID, Beat.NONE, Place(first + 2, t, 250, 300, 500, 300), Place(trail, t, 250, 300, 0, 300, dx = 15)),
            )
            fun land(hold: Long, result: Int, vararg extra: Place) = Beat(hold, result, *extra)
            val impactRight = Place(impact, b, 1000, 500, 0, 500, dx = 3)

            val d20Land = land(LAND, Beat.PLATE, impactRight)
            val nat20 = land(
                LAND_BIG, Beat.PLATE, impactRight,
                Place(twinkle, t, 470, 150, 500, 0), Place(twinkleS, t, 660, 1000, 500, 1000),
                Place(twinkle, t, 850, 400, 500, 500), Place(twinkleS, t, 1000, 0, 1000, 0),
            )
            // The anticlimax: no stamp, it just flops there with a sweat drop and a sad little puff.
            val nat1 = land(
                LAND_BIG, Beat.PLAIN,
                Place(sweat, b, 1000, 0, 0, 0, dx = 2, dy = 1), Place(puff, b, 1000, 1000, 0, 1000, dx = 2),
            )
            val coinBeats = arrayOf(
                Beat(TOSS, Beat.NONE, Place(coin, t, 1000, 500, 1000, 500, dx = -2),
                    Place(arcUp, t, 1000, 0, 1000, 0, dx = -6), Place(arcDown, t, 1000, 1000, 1000, 1000, dx = -6)),
                Beat(BOUNCE, Beat.NONE, Place(coin + 1, t, 600, 0, 500, 0, dy = 3),
                    Place(arcDown, t, 600, 0, 500, 0, dy = 10)),
                Beat(SKID, Beat.NONE, Place(coin + 2, t, 260, 1000, 500, 1000),
                    Place(trail, t, 260, 1000, 0, 1000, dx = 12, dy = -3)),
            )
            val heads = land(LAND, Beat.PLAIN, impactRight, Place(twinkle, b, 500, 0, 0, 0, dx = 8, dy = -1))
            // Tails lands with a thunk: a dust puff at its foot and the two flat thud lines.
            val tails = land(LAND, Beat.PLAIN, Place(puff, b, 500, 1000, 0, 1000, dx = 14), Place(thud, b, 500, 0, 0, 0, dx = 16, dy = 3))

            val scripts = arrayOfNulls<Script>(Outcome.COUNT)
            scripts[Outcome.D20] = Script("d20", *tumbleBeats(d20), d20Land)
            scripts[Outcome.NAT_20] = Script("nat 20", *tumbleBeats(d20), nat20)
            scripts[Outcome.NAT_1] = Script("nat 1", *tumbleBeats(d20), nat1)
            scripts[Outcome.D6] = Script("d6", *tumbleBeats(d6), d20Land)
            scripts[Outcome.HEADS] = Script("heads", *coinBeats, heads)
            scripts[Outcome.TAILS] = Script("tails", *coinBeats, tails)
            @Suppress("UNCHECKED_CAST")
            return Presentation(table, scripts as Array<Script>)
        }
    }
}

/** Which landing a roll gets. */
object Outcome {
    const val D20 = 0
    const val NAT_20 = 1
    const val NAT_1 = 2
    const val D6 = 3
    const val HEADS = 4
    const val TAILS = 5
    const val COUNT = 6

    fun of(tool: Tool, value: Int): Int = when (tool) {
        Tool.D20 -> when (value) { 20 -> NAT_20; 1 -> NAT_1; else -> D20 }
        Tool.D6 -> D6
        Tool.COIN -> if (value == 1) HEADS else TAILS
    }
}

/** One presentation for one outcome. */
class Script internal constructor(val name: String, vararg val beats: Beat)

/**
 * One held keyframe: its sprites, and [result] — [NONE] (still tumbling: the result box stays
 * empty, the text column too), [PLAIN] (the result as it will stay) or [PLATE] (a die result stamped:
 * paper numerals on an ink plate, the "landing with weight").
 */
class Beat internal constructor(val holdMs: Long, val result: Int, vararg val places: Place) {
    companion object {
        const val NONE = 0
        const val PLAIN = 1
        const val PLATE = 2
    }
}

/** Where a beat's sprite goes, in the lower face's frame (the upper face is its 180° image). */
object Region {
    /** The face less its side pads, and a little air above and below. */
    const val TRACK = 0

    /** The result box. */
    const val BOX = 1
}

/**
 * Sprite [sprite] (index into the presentation's table) placed so that its own point ([sx], [sy],
 * per mille of its size) sits on the region's point ([fx], [fy], per mille of the [region]), moved by
 * ([dx], [dy]) × the chrome glyph scale. Clamped inside the face.
 */
class Place internal constructor(
    val sprite: Int, val region: Int, val fx: Int, val fy: Int, val sx: Int, val sy: Int,
    val dx: Int = 0, val dy: Int = 0,
)

/**
 * A [Presentation] placed on a [DiceLayout] at sprite scale [k] (pure; built at layout, off the draw
 * path): every placed sprite's rect on both faces, [rect]`(outcome, beat, place, face)`.
 */
class PresentationLayout(val l: DiceLayout, val p: Presentation, val spriteW: IntArray, val spriteH: IntArray, val k: Int) {
    private val base = IntArray(Outcome.COUNT * MAX_BEATS + 1)
    private val rects: Array<IRect>

    init {
        var n = 0
        for (o in 0 until Outcome.COUNT) {
            val beats = p.scripts[o].beats
            require(beats.size <= MAX_BEATS)
            for (bi in beats.indices) {
                base[o * MAX_BEATS + bi] = n
                n += beats[bi].places.size * 2
            }
        }
        rects = Array(n) { IRect() }
        val face = l.faces[0]
        val m = maxOf(1, (face.height - l.resultBox[0].height) / 2 - 1)
        val track = IRect().set(face.left + l.padH, face.top + m, face.right - l.padH, face.bottom - m)
        for (o in 0 until Outcome.COUNT) {
            val beats = p.scripts[o].beats
            for (bi in beats.indices) for ((pi, pl) in beats[bi].places.withIndex()) {
                val reg = if (pl.region == Region.BOX) l.resultBox[0] else track
                val w = spriteW[pl.sprite]
                val h = spriteH[pl.sprite]
                var x = reg.left + reg.width * pl.fx / 1000 - w * pl.sx / 1000 + pl.dx * k
                var y = reg.top + reg.height * pl.fy / 1000 - h * pl.sy / 1000 + pl.dy * k
                x = x.coerceIn(face.left + 1, maxOf(face.left + 1, face.right - 1 - w))
                y = y.coerceIn(face.top + 1, maxOf(face.top + 1, face.bottom - 1 - h))
                val r0 = rects[base[o * MAX_BEATS + bi] + pi * 2]
                r0.setXywh(x, y, w, h)
                l.reflect(r0, rects[base[o * MAX_BEATS + bi] + pi * 2 + 1])
            }
        }
    }

    /** Placed sprite [place] of [outcome]'s [beat] on [face] (0 lower, 1 upper). */
    fun rect(outcome: Int, beat: Int, place: Int, face: Int): IRect = rects[base[outcome * MAX_BEATS + beat] + place * 2 + face]

    companion object {
        const val MAX_BEATS = 8
    }
}
