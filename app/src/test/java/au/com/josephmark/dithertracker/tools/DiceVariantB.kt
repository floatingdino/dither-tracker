package au.com.josephmark.dithertracker.tools

/**
 * Variant B of the roll presentation (T52, **not built**; test-only, drawn on the design sheet beside
 * the shipped [Presentation.TUMBLE]): "dust-up". The die never travels: a cartoon fight cloud boils
 * on the result box with a corner of the die and a "?" poking out (two poses), then the cloud is gone
 * and the result is stamped in its place; the coin spins on the spot (face, edge, the other face) and
 * lands. See `design/tools/README.md`.
 */
object DiceVariantB {
    val DUST_UP: Presentation by lazy { build() }

    private fun build(): Presentation {
        val b = DiceSpritesB
        val a = DiceSprites
        val table = arrayOf(
            b.CLOUD_A, b.CLOUD_B, b.QMARK, b.POKE_A, b.POKE_B, b.POKE6_A, b.POKE6_B, b.SPIN_A, b.SPIN_B, b.SPIN_C,
            a.IMPACT, a.TWINKLE, a.TWINKLE_S, a.SWEAT, a.PUFF,
        )
        val cloudA = 0; val cloudB = 1; val q = 2; val poke = 3; val poke6 = 5; val spin = 7
        val impact = 10; val twinkle = 11; val twinkleS = 12; val sweat = 13; val puff = 14
        val box = Region.BOX
        val t = Region.TRACK
        fun dust(p: Int) = arrayOf(
            Beat(300, Beat.NONE, Place(cloudA, box, 500, 500, 500, 500, dx = 4), Place(p, box, 1000, 0, 0, 0, dx = 8), Place(q, box, 1000, 1000, 0, 1000, dx = 14)),
            Beat(300, Beat.NONE, Place(cloudB, box, 500, 500, 500, 500, dx = 6), Place(p + 1, box, 1000, 1000, 0, 1000, dx = 10), Place(q, box, 1000, 0, 0, 0, dx = 20)),
        )
        val impactRight = Place(impact, box, 1000, 500, 0, 500, dx = 3)
        val land = Beat(350, Beat.PLATE, impactRight, Place(puff, box, 1000, 1000, 0, 1000, dx = 14))
        val nat20 = Beat(400, Beat.PLATE, impactRight, Place(twinkle, t, 470, 150, 500, 0), Place(twinkleS, t, 700, 1000, 500, 1000), Place(twinkle, t, 900, 300, 500, 500))
        val nat1 = Beat(400, Beat.PLAIN, Place(sweat, box, 1000, 0, 0, 0, dx = 2, dy = 1), Place(puff, box, 1000, 1000, 0, 1000, dx = 2))
        val spinBeats = arrayOf(
            Beat(250, Beat.NONE, Place(spin, box, 500, 500, 500, 500)),
            Beat(250, Beat.NONE, Place(spin + 1, box, 500, 500, 500, 500)),
            Beat(250, Beat.NONE, Place(spin + 2, box, 500, 500, 500, 500)),
        )
        val scripts = arrayOfNulls<Script>(Outcome.COUNT)
        scripts[Outcome.D20] = Script("d20", *dust(poke), land)
        scripts[Outcome.NAT_20] = Script("nat 20", *dust(poke), nat20)
        scripts[Outcome.NAT_1] = Script("nat 1", *dust(poke), nat1)
        scripts[Outcome.D6] = Script("d6", *dust(poke6), land)
        scripts[Outcome.HEADS] = Script("heads", *spinBeats, Beat(300, Beat.PLAIN, impactRight, Place(twinkle, box, 500, 0, 0, 0, dx = 8, dy = -1)))
        scripts[Outcome.TAILS] = Script("tails", *spinBeats, Beat(300, Beat.PLAIN, Place(puff, box, 500, 1000, 0, 1000, dx = 14)))
        @Suppress("UNCHECKED_CAST")
        return Presentation(table, scripts as Array<Script>)
    }
}
