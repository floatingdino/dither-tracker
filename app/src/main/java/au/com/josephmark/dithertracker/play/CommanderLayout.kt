package au.com.josephmark.dithertracker.play

import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.CommanderSlot
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerState
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * Geometry of commander-damage mode (T30) on top of [PlayLayout]. Pure maths (no Android types),
 * JVM-tested; everything is laid out in the player's frame and mapped with [PlayLayout.mapRect], so
 * a top-row tile is the exact 180° image of an upright one.
 *
 * While player X is the target:
 * - **Opponent tile** (source S): where the life total was, the damage S's commander has dealt X,
 *   in the same hero face, at the same place and size; −/+ are the tile's halves as usual. When S
 *   has a partner, the tile splits into two halves, one per commander, each a 2-digit number slot
 *   centred in its half with its own − / + quarter zones and half-size ± glyphs when they fit
 *   ([partnerPmVisible]). Under the number, in [PlayLayout.underRow]: the commander icon and
 *   `TO <X's name>` (partner: an icon under each number, the text between them).
 * - **Target tile** (X): a quip in the `ui` face, as large as fits ([quipFootprint]), centred on
 *   the number's cap band; under it, in the under row, a solid ink band ([bandRect]) with the life
 *   icon and X's current life in `num-m` (paper), so the life lost to linked damage stays visible.
 * - Lethal damage (≥ 21 from one commander) is drawn on a solid ink card ([lethalPad] round the
 *   number) with the number in paper.
 *
 * Hit-testing ([hitTest]) only claims what is the mode's; everything else is left to the drawer
 * layout ([DELEGATE]).
 */
class CommanderLayout(val play: PlayLayout) {

    val ui: Font = Fonts.UI
    val num: Font = Fonts.NUM_M
    val tk: Int = play.tk
    val iconSize: Int = PlayLayout.ICON * tk

    /** Gap between an icon and its text in the under row. */
    val iconGap: Int = PlayLayout.Chrome.size(ICON_GAP, play.grid.scale)

    /** Paper-free margin inside the target's life band, each side of its content. */
    val bandPad: Int = PlayLayout.Chrome.size(BAND_PAD, play.grid.scale)

    /** Ink margin round a lethal number's footprint. */
    val lethalPad: Int = PlayLayout.Chrome.size(LETHAL_PAD, play.grid.scale)

    /** Largest `ui` glyph scale for the target quip (physical size ≈ S = 4's [QUIP_K_MAX]). */
    val quipKMax: Int = maxOf(1, PlayLayout.Chrome.size(QUIP_K_MAX, play.grid.scale))

    private val P = PlayLayout.PLAYERS
    private val W = play.panelW
    private val H = play.panelH

    /** Full-width band through the number slot: everything the mode draws there (invalidate this). */
    val content: Array<IRect> = Array(P) { IRect() }

    /** The four quarters of each tile (player frame, left to right), `seat * 4 + i`: partner zones. */
    val quarter: Array<IRect> = Array(P * 4) { IRect() }

    /** Partner number slots (`seat * 2 + slot`), sized for two digits, and their ± glyphs. */
    val partnerSlot: Array<IRect> = Array(P * 2) { IRect() }
    val partnerMinus: Array<IRect> = Array(P * 2) { IRect() }
    val partnerPlus: Array<IRect> = Array(P * 2) { IRect() }

    /** Commander/partner icon under each partner number, in the under row (`seat * 2 + slot`). */
    val partnerIcon: Array<IRect> = Array(P * 2) { IRect() }

    /** Whether the partner ± glyphs fit beside 2-digit numbers with [PARTNER_GAP] clear. */
    val partnerPmVisible: Boolean

    /** Max name chars after `TO ` in the under row: single source, and between partner icons. */
    val labelChars: Int
    val partnerLabelChars: Int

    private val slotY = play.numberSlotY
    private val k = play.kMax
    private val pW = play.font.widthOf(PARTNER_CHARS, k)
    private val pmW = play.pmFont.glyphWidth * play.pmK
    private val pmH = play.pmFont.glyphHeight * play.pmK
    private val pmY = play.pmCapAlignedY(slotY, k)
    private val half = W / 2

    init {
        val slotH = play.font.height(k)
        var pmOk = true
        for (i in 0..1) {
            val x0 = if (i == 0) 0 else half
            val hw = if (i == 0) half else W - half
            val nx = x0 + (hw - pW) / 2
            val mx = x0 + (nx - x0 - pmW) / 2
            val px = nx + pW + (x0 + hw - (nx + pW) - pmW) / 2
            if (mx - x0 < 0 || nx - (mx + pmW) < PARTNER_GAP || px - (nx + pW) < PARTNER_GAP || x0 + hw - (px + pmW) < 0) pmOk = false
            for (s in 0 until P) {
                play.mapRect(s, nx, slotY, pW, slotH, partnerSlot[s * 2 + i])
                play.mapRect(s, mx, pmY, pmW, pmH, partnerMinus[s * 2 + i])
                play.mapRect(s, px, pmY, pmW, pmH, partnerPlus[s * 2 + i])
                val ix = x0 + (hw - iconSize) / 2
                play.mapRect(s, ix, play.underY + (play.underH - iconSize) / 2, iconSize, iconSize, partnerIcon[s * 2 + i])
            }
        }
        partnerPmVisible = pmOk
        for (s in 0 until P) {
            play.mapRect(s, 0, slotY, W, slotH, content[s])
            val q = intArrayOf(0, W / 4, half, half + (W - half) / 2, W)
            for (i in 0 until 4) play.mapRect(s, q[i], 0, q[i + 1] - q[i], H, quarter[s * 4 + i])
        }
        val adv = ui.advance * tk
        // No under row (only on layouts that are not comfortable): no labels at all.
        val row = play.underH > 0
        labelChars = if (!row) 0 else maxOf(0, (play.underW - iconSize - iconGap + ui.tracking * tk) / adv - TO.length)
        // Between the two partner icons, iconGap clear of each.
        val iconA = half / 2 + iconSize / 2
        val iconB = half + (W - half) / 2 - iconSize / 2
        partnerLabelChars = if (!row) 0 else maxOf(0, (iconB - iconA - 2 * iconGap + ui.tracking * tk) / adv - TO.length)
    }

    // ---- footprints (draw-time; write `out`, never allocate) -------------------------------------

    /**
     * The `TO <name>` label (single source): icon then text as one unit, centred in the under row.
     * Writes the icon footprint to [icon] and the text footprint (for [chars] chars incl. `TO `) to [text].
     */
    fun labelFootprint(seat: Int, chars: Int, icon: IRect, text: IRect) {
        val tw = ui.widthOf(chars, tk)
        val unit = iconSize + iconGap + tw
        val x = play.underX + (play.underW - unit) / 2
        play.mapRect(seat, x, play.underY + (play.underH - iconSize) / 2, iconSize, iconSize, icon)
        play.mapRect(seat, x + iconSize + iconGap, textY(), tw, ui.height(tk), text)
    }

    /** The `TO <name>` label of a partner tile: text only, centred between the two icons. */
    fun partnerLabelFootprint(seat: Int, chars: Int, text: IRect) {
        val tw = ui.widthOf(chars, tk)
        play.mapRect(seat, (W - tw) / 2, textY(), tw, ui.height(tk), text)
    }

    private fun textY(): Int = play.underY + TextLayout.centre(play.underH, ui.capHeight * tk) - ui.capTop * tk

    /**
     * The target's life band: a solid ink pill in the under row, [bandPad] wider than its content
     * on each side, sized for the widest life (`−999`) so it never changes size as life changes.
     */
    fun bandRect(seat: Int, out: IRect) {
        val w = minOf(play.underW, iconSize + iconGap + num.widthOf(4, tk) + 2 * bandPad)
        play.mapRect(seat, play.underX + (play.underW - w) / 2, play.underY, w, play.underH, out)
    }

    /**
     * The target band's content: life icon then [life] in `num-m`, centred in the under row.
     * Returns the glyph scale ([tk]).
     */
    fun lifeFootprint(seat: Int, life: Int, icon: IRect, number: IRect): Int {
        val nw = num.measureInt(life, tk)
        val unit = iconSize + iconGap + nw
        val x = play.underX + (play.underW - unit) / 2
        play.mapRect(seat, x, play.underY + (play.underH - iconSize) / 2, iconSize, iconSize, icon)
        play.mapRect(seat, x + iconSize + iconGap, play.underY + (play.underH - num.height(tk)) / 2, nw, num.height(tk), number)
        return tk
    }

    /**
     * Glyph scale of a quip of [len] chars: the largest ≤ [quipKMax] whose box fits the number slot
     * — no wider than the tile less [PlayLayout.EDGE_MIN] each side, and, cap band centred on the
     * number's, its whole box (descenders included) inside the slot's height. Never below 1.
     */
    fun quipK(len: Int): Int {
        var q = quipKMax
        while (q > 1 && !quipFits(len, q)) q--
        return q
    }

    private fun quipFits(len: Int, q: Int): Boolean {
        if (ui.widthOf(len, q) > W - 2 * PlayLayout.EDGE_MIN) return false
        val top = quipTop(q)
        return top >= slotY && top + ui.height(q) <= slotY + play.font.height(k)
    }

    /** Player-frame top of a quip box at scale [q], its cap band centred on the number's. */
    private fun quipTop(q: Int): Int {
        val capMid2 = 2 * (slotY + play.font.capTop * k) + play.font.capHeight * k
        return (capMid2 - ui.capHeight * q) / 2 - ui.capTop * q
    }

    /** Footprint of a quip of [len] chars on [seat]'s tile: cap band centred on the number's. Returns k. */
    fun quipFootprint(seat: Int, len: Int, out: IRect): Int {
        val q = quipK(len)
        val tw = ui.widthOf(len, q)
        play.mapRect(seat, (W - tw) / 2, quipTop(q), tw, ui.height(q), out)
        return q
    }

    /**
     * Footprint of [value] in partner slot [slot] of [seat]: centred in the 2-digit slot (wider
     * values spill symmetrically; the ± are then skipped, see [partnerPmClear]). Returns k.
     */
    fun partnerNumberFootprint(seat: Int, slot: CommanderSlot, value: Int, out: IRect): Int {
        val i = if (slot == Commander.PARTNER) 1 else 0
        val f = play.font
        val tw = f.measureInt(value, k)
        val x0 = if (i == 0) 0 else half
        val hw = if (i == 0) half else W - half
        play.mapRect(seat, x0 + (hw - tw) / 2, slotY, tw, f.height(k), out)
        return k
    }

    /** Whether a partner number of [value] leaves its ± glyphs clear. */
    fun partnerPmClear(value: Int): Boolean = partnerPmVisible && Font.intLength(value) <= PARTNER_CHARS

    // ---- touch --------------------------------------------------------------------------------

    /**
     * Zone under dither (gx, gy) for the commander layer, or [DELEGATE] when it is not the mode's.
     * [target] is the current target seat or [CommanderMode.NONE]. Buttons work in and out of the
     * mode (not on a seat whose drawer is open); in the mode, tabs are inert ([PlayLayout.NONE]),
     * the target's tile is [CommanderZones.TARGET], opponents' tiles are slot zones (quarters for a
     * partnered source), and the diamond is [CommanderZones.DIAMOND].
     */
    fun hitTest(gx: Int, gy: Int, target: Int, players: List<Player>, drawers: DrawerState): Int {
        val pl = play
        val on = target != CommanderMode.NONE
        if (pl.inDiamondHit(gx, gy)) return if (on) CommanderZones.DIAMOND else DELEGATE
        for (s in 0 until P) {
            val p = pl.panels[s]
            if (!p.contains(gx, gy)) continue
            if (!drawers.isOpen(s) && pl.cmdHit[s].contains(gx, gy)) return CommanderZones.zone(s, CommanderZones.BUTTON)
            if (!on) return DELEGATE
            if (s == target) return CommanderZones.zone(s, CommanderZones.TARGET)
            if (pl.tabHit[s].contains(gx, gy)) return PlayLayout.NONE
            val lx = if (pl.rotation[s] == 180) p.right - 1 - gx else gx - p.left
            val partner = s < players.size && players[s].partner
            return if (partner) {
                val q = when {
                    lx < W / 4 -> CommanderZones.A_MINUS
                    lx < half -> CommanderZones.A_PLUS
                    lx < half + (W - half) / 2 -> CommanderZones.B_MINUS
                    else -> CommanderZones.B_PLUS
                }
                CommanderZones.zone(s, q)
            } else {
                CommanderZones.zone(s, if (lx < half) CommanderZones.A_MINUS else CommanderZones.A_PLUS)
            }
        }
        return if (on) PlayLayout.NONE else DELEGATE
    }

    /** The rect a pressed commander [zone] inverts. False for none. */
    fun feedbackRect(zone: Int, players: List<Player>, out: IRect): Boolean {
        if (!CommanderZones.isCommander(zone)) return false
        if (zone == CommanderZones.DIAMOND) { copy(play.menuBarsBacking, out); return true }
        val s = CommanderZones.seatOf(zone)
        val local = CommanderZones.localOf(zone)
        when {
            local == CommanderZones.BUTTON -> copy(play.cmdVis[s], out)
            local == CommanderZones.TARGET -> copy(play.panels[s], out)
            CommanderZones.isSlot(local) -> {
                val partner = s < players.size && players[s].partner
                if (partner) {
                    copy(quarter[s * 4 + (local - CommanderZones.A_MINUS)], out)
                } else {
                    if (local >= CommanderZones.B_MINUS) return false
                    copy(play.zones[PlayLayout.zone(s, CommanderZones.sideOf(local))], out)
                }
            }
            else -> return false
        }
        return true
    }

    private fun copy(src: IRect, out: IRect) = out.set(src.left, src.top, src.right, src.bottom)

    override fun toString(): String =
        "CommanderLayout(S=${play.grid.scale} tk $tk, partner slots ${pW}w pm ${if (partnerPmVisible) "shown" else "hidden"}, " +
            "labelChars $labelChars / partner $partnerLabelChars, quipKMax $quipKMax)"

    companion object {
        /** "Not a commander zone": let the drawer layout hit-test it. */
        const val DELEGATE = Int.MIN_VALUE

        const val TO = "TO "

        /** Digits a partner number slot is sized for (commander damage past 99 is not a real game). */
        const val PARTNER_CHARS = 2

        /** Design values at S = 4 (converted per S). */
        const val ICON_GAP = 3
        const val LETHAL_PAD = 3
        const val BAND_PAD = 8
        const val QUIP_K_MAX = 4

        /** Minimum paper between a partner number and its ± glyphs (tighter than [PlayLayout.GAP_MIN]: two numbers share a tile). */
        const val PARTNER_GAP = 2
    }
}
