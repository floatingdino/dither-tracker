package au.com.josephmark.dithertracker.toggles

import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.peeks.PeekLayout
import au.com.josephmark.dithertracker.play.PlayLayout

/**
 * Where a seat's state-toggle badges go (T14). Pure maths, JVM-tested; laid out once in the player's
 * frame and mapped with [PlayLayout.mapRect], so every seat — upright, turned 180° or sideways — gets
 * the same row facing its player.
 *
 * ```
 *   far edge
 *  ┌───────────────────────────────────┐
 *  │             readout               │
 *  │  −          number             +  │
 *  │ [peek]   [♛][🔥][🏰][💍]    [peek] │  the under-number row, between the peek corners
 *  │ [peek]       ┌── tab ──┐    [peek] │
 *  └──────────────┴─────────┴──────────┘
 *   near edge (the player)
 * ```
 * The badges sit in [PlayLayout.underRow] (one icon plus a paper rim tall, [PlayLayout.V_MIN] clear
 * of the number above and the tab's hit band below; it exists on every comfortable tile), across the
 * middle the counter peeks leave free: from [PlayLayout.V_MIN] past the left peek column to as far
 * short of the right one (the KO detail / first-player band). On a narrow tile (5–6 players, no
 * peeks) that is the row less [PlayLayout.EDGE_MIN] each side. The badges that are on form one group
 * centred in it, in toggle order (monarch, initiative, city's blessing, ring-bearer), [gap] apart.
 *
 * Shared with: the first-player band (T16, a few seconds, drawn over it) and the winner's detail chip
 * (the component skips the sole survivor's badges). Commander mode's partner labels use the under row
 * too, but the mode covers this component.
 */
class BadgeLayout(val play: PlayLayout) {

    val tk: Int = play.tk
    val iconSize: Int = PlayLayout.ICON * tk
    val gap: Int = PlayLayout.Chrome.size(GAP, play.grid.scale)

    /** The band (player frame): x from [left] to [right], the icons' top at [iconY]. */
    val left: Int
    val right: Int
    val iconY: Int

    /** True when the row exists and holds [MAX] badges. */
    val enabled: Boolean

    private val P = play.playerCount

    /** Screen rect of each seat's whole band (the under row across [left, right)): what a change redraws. */
    val row: Array<IRect> = Array(P) { IRect() }

    init {
        val pk = PeekLayout(play)
        left = if (pk.enabled) pk.leftX + pk.colW + pk.pad + PlayLayout.V_MIN else play.underX
        right = if (pk.enabled) pk.rightX - pk.colW - pk.pad - PlayLayout.V_MIN else play.underX + play.underW
        iconY = play.underY + (play.underH - iconSize) / 2
        enabled = play.underH >= iconSize && groupWidth(MAX) <= right - left
        for (s in 0 until P) {
            if (enabled) play.mapRect(s, left, play.underY, right - left, play.underH, row[s]) else row[s].set(0, 0, 0, 0)
        }
    }

    /** Width of a group of [n] badges. */
    fun groupWidth(n: Int): Int = if (n <= 0) 0 else n * iconSize + (n - 1) * gap

    /** Player-frame left of badge [i] of a group of [n]. */
    fun badgeX(i: Int, n: Int): Int = left + (right - left - groupWidth(n)) / 2 + i * (iconSize + gap)

    /** Screen footprint of badge [i] of [n] on [seat] (draw the icon with its top-left here at [tk]). */
    fun badge(seat: Int, i: Int, n: Int, out: IRect) = play.mapRect(seat, badgeX(i, n), iconY, iconSize, iconSize, out)

    override fun toString(): String =
        "BadgeLayout(S=${play.grid.scale} ${if (enabled) "" else "DISABLED "}band $left..$right icons at y $iconY, ${iconSize}px gap $gap)"

    companion object {
        /** Badges per seat (the four per-player toggles). */
        const val MAX = 4

        /** Paper between two badges, design px at S = 4. */
        const val GAP = 3
    }
}
