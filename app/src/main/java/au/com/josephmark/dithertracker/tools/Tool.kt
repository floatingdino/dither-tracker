package au.com.josephmark.dithertracker.tools

import au.com.josephmark.dithertracker.core.CoinFace

/**
 * The table tools on the dice panel's fold row (T16). [sides] is the die size (the coin is a
 * two-sided "die" whose value is a [CoinFace] ordinal + 1); [label] is the die label shown on each
 * face and on the fold button.
 */
enum class Tool(val sides: Int, val label: String) {
    D20(20, "d20"),
    D6(6, "d6"),
    COIN(2, "coin");

    val isCoin: Boolean get() = this == COIN

    companion object {
        /** The coin face of a [COIN] value (1 = heads, 2 = tails). */
        fun coinFace(value: Int): CoinFace = if (value == 1) CoinFace.HEADS else CoinFace.TAILS

        /** The [COIN] value of [face]. */
        fun coinValue(face: CoinFace): Int = face.ordinal + 1
    }
}

/**
 * Where tool results go besides the screen. Today nothing listens (core has no log-only action yet);
 * T18 makes [DiceOverlay.results] / [FirstPlayer.results] dispatch a log entry — a one-line change.
 * Primitives only, so reporting a result never allocates.
 */
interface ToolResults {
    /** [tool] rolled [value] (a die face, or a [Tool.coinValue]). */
    fun onRoll(tool: Tool, value: Int) {}

    /** Player [id] was picked to go first. */
    fun onStartingPlayer(id: Int) {}

    companion object {
        val NONE = object : ToolResults {}
    }
}
