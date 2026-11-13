package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.dither.IRect

/**
 * The shared, read-only tile context (T34): where a seat's life total, ± glyphs, readout and life
 * halves are *right now*, so tile components agree on them without knowing about each other.
 *
 * A seat is either **full** (the [PlayLayout] slots) or **compact**: something occupies the lower
 * tile (the open drawer) and the number block moves up into the remaining upper area at the
 * geometry that occupant [publishCompact]ed. Exactly one component may be the [lower] occupant; it
 * answers [LowerTile.occupies] from its own state, so there is nothing to keep in sync.
 *
 * Writers: the occupant (once per layout, [publishCompact]). Readers: life (number, ±, life halves),
 * readout (readout slot), peeks ([underRow], only while full), effects/KO (T28c/T15: [numberSlot],
 * [play]'s panels).
 */
class TileSlots(val play: PlayLayout) {

    /** Who occupies the lower part of a tile (the drawer). */
    fun interface LowerTile {
        fun occupies(seat: Int): Boolean
    }

    var lower: LowerTile? = null

    /** True while [seat]'s lower tile is occupied and its number block is compact. */
    fun compact(seat: Int): Boolean = lower?.occupies(seat) == true

    private val P = PlayLayout.PLAYERS

    // Compact geometry (published by the occupant).
    var compactNumberK: Int = play.kMax
        private set
    var compactNumberTop: Int = play.numberSlotY
        private set
    var compactReadoutK: Int = play.readoutK
        private set
    var compactReadoutTop: Int = 0
        private set
    private val cNumber = Array(P) { IRect() }
    private val cReadout = Array(P) { IRect() }
    private val cMinus = Array(P) { IRect() }
    private val cPlus = Array(P) { IRect() }
    private val cLife = Array(P * 2) { IRect() }

    /**
     * The occupant's compact number block: number at glyph scale [numberK] with its slot top at
     * player-frame y [numberTop], readout at [readoutK] / [readoutTop], and the screen rects per seat
     * (life halves indexed `seat * 2 + side`). Copied.
     */
    fun publishCompact(
        numberK: Int, numberTop: Int, readoutK: Int, readoutTop: Int,
        numberSlot: Array<IRect>, readoutSlot: Array<IRect>, minus: Array<IRect>, plus: Array<IRect>, lifeHalves: Array<IRect>,
    ) {
        compactNumberK = numberK
        compactNumberTop = numberTop
        compactReadoutK = readoutK
        compactReadoutTop = readoutTop
        for (s in 0 until P) {
            cNumber[s].copyFrom(numberSlot[s])
            cReadout[s].copyFrom(readoutSlot[s])
            cMinus[s].copyFrom(minus[s])
            cPlus[s].copyFrom(plus[s])
            cLife[s * 2].copyFrom(lifeHalves[s * 2])
            cLife[s * 2 + 1].copyFrom(lifeHalves[s * 2 + 1])
        }
    }

    /** Rect the life total is drawn in (invalidate this on a life change). */
    fun numberSlot(seat: Int): IRect = if (compact(seat)) cNumber[seat] else play.numberSlot[seat]

    /** Footprint of [value] as [seat]'s life total; returns the glyph scale (draw with `play.font`). */
    fun numberFootprint(seat: Int, value: Int, out: IRect): Int =
        if (compact(seat)) play.numberFootprintAt(seat, value, compactNumberK, compactNumberTop, out)
        else play.numberFootprint(seat, value, out)

    /** Rect of [seat]'s pending-delta readout (invalidate this when the readout changes). */
    fun readoutSlot(seat: Int): IRect = if (compact(seat)) cReadout[seat] else play.readoutSlot[seat]

    /** Footprint of the signed [delta] in [seat]'s readout slot; returns the glyph scale (draw with `play.readoutFont`). */
    fun readoutFootprint(seat: Int, delta: Int, out: IRect): Int =
        if (compact(seat)) play.readoutFootprintAt(seat, delta, compactReadoutK, compactReadoutTop, out)
        else play.readoutFootprint(seat, delta, out)

    fun minusGlyph(seat: Int): IRect = if (compact(seat)) cMinus[seat] else play.minusGlyph[seat]
    fun plusGlyph(seat: Int): IRect = if (compact(seat)) cPlus[seat] else play.plusGlyph[seat]

    /** Glyph scale of the ± (same face as the number since T27). */
    fun pmK(seat: Int): Int = if (compact(seat)) PlayLayout.pmK(compactNumberK) else play.pmK

    /** What a press on [seat]'s life half [side] ([PlayLayout.MINUS]/[PlayLayout.PLUS]) inverts. */
    fun lifeFeedback(seat: Int, side: Int): IRect =
        if (compact(seat)) cLife[seat * 2 + side] else play.zones[PlayLayout.zone(seat, side)]

    /** The row under the number (peeks, partner icons); only meaningful while the seat is full. */
    fun underRow(seat: Int): IRect = play.underRow[seat]

    private fun IRect.copyFrom(o: IRect) {
        set(o.left, o.top, o.right, o.bottom)
    }
}
