package au.com.josephmark.dithertracker.commander

import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.CommanderSlot
import au.com.josephmark.dithertracker.core.Player
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.drawer.DrawerLayout
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.type.Font
import au.com.josephmark.dithertracker.type.Fonts
import au.com.josephmark.dithertracker.type.TextLayout

/**
 * Geometry of commander-damage mode (T30) on top of [PlayLayout]. Pure maths (no Android types),
 * JVM-tested; everything is laid out in the player's frame and mapped with [PlayLayout.mapRect], so
 * a top-row tile is the exact 180° image of an upright one.
 *
 * While player X is the target (designer pass 2):
 * - **Opponent tile** (source S): only the damage S's commander has dealt X, in the same hero face,
 *   at the same place and size, with the ± glyphs; −/+ are the tile's halves (the whole tile: tabs
 *   are hidden in the mode). When S has a partner, the tile splits into two halves, one per
 *   commander, each a 2-digit number slot centred in its half with its own − / + quarter zones and
 *   half-size ± glyphs when they fit ([partnerPmVisible]); a small commander / partner icon under
 *   each number ([partnerIcon], in [PlayLayout.underRow]) tells the two apart. No other labels.
 * - **Target tile** (X): a composed block, centred between the readout and the tab band
 *   ([targetTop]..[targetBottom]): the quip in ALL CAPS in the `ui` face ([quipFootprint]: one
 *   size below the largest that fits, but not below [quipKMin]), [quipGap] of paper, then X's life
 *   on paper — the life icon and the number in [lifeFont] at [lifeK] ([lifeFootprint]) — so the life
 *   lost to linked damage stays visible.
 * - Lethal damage (≥ 21 from one commander) is drawn on a solid ink card ([lethalPad] round the
 *   number) with the number in paper.
 * - The centre diamond shows a close X ([closeIcon]) instead of the menu bars.
 *
 * [lifeFont] is a parameter so the target's life can switch to a mini sticker face (T37) in one line.
 *
 * Hit-testing ([hitTest]) only claims what is the mode's; everything else is left to the drawer
 * layout ([DELEGATE]).
 */
class CommanderLayout(val play: PlayLayout, val lifeFont: Font = Fonts.NUM_M) {

    val ui: Font = Fonts.UI
    val tk: Int = play.tk
    val iconSize: Int = PlayLayout.ICON * tk

    /** Gap between an icon and its text in the under row. */
    val iconGap: Int = PlayLayout.Chrome.size(ICON_GAP, play.grid.scale)

    /** Paper between the quip's cap band and the life row on the target tile. */
    val quipGap: Int = PlayLayout.Chrome.size(QUIP_GAP, play.grid.scale)

    /** Smallest quip scale (a long line keeps this rather than dropping a size). */
    val quipKMin: Int = maxOf(1, PlayLayout.Chrome.size(QUIP_K_MIN, play.grid.scale))

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

    /** Player-frame band the target tile's block is centred in: below the readout, above the tab band. */
    val targetTop: Int
    val targetBottom: Int

    /** Glyph scale of the target's life ([lifeFont]): the readout's scale, stepped down to fit. */
    val lifeK: Int

    /** Height of the target's life row (icon or numerals, whichever is taller). */
    val lifeRowH: Int

    /** Full-width rect of the target's life row on each seat for the longest quip layout (invalidate on a life change). */
    val lifeRow: Array<IRect> = Array(P) { IRect() }

    /** The close X on the centre diamond while the mode is on (opaque icon, screen rect). */
    val closeIcon = IRect()

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
        // Target block region: V_MIN below the readout slot, V_MIN above the tab's hit band.
        val roH = play.readoutFont.height(play.readoutK)
        targetTop = TextLayout.centre(slotY, roH) + roH + PlayLayout.V_MIN
        targetBottom = H - play.chrome.tabH - play.chrome.tabHitPad - PlayLayout.V_MIN
        var lk = play.readoutK
        while (lk > 1 && maxOf(iconSize, lifeFont.height(lk)) + quipGap + ui.capHeight > targetBottom - targetTop) lk--
        lifeK = lk
        lifeRowH = maxOf(iconSize, lifeFont.height(lk))
        // The life row moves with the quip's height: cover every position it can take.
        for (s in 0 until P) play.mapRect(s, 0, targetTop, W, maxOf(0, targetBottom - targetTop), lifeRow[s])
        val cx = play.diamondCx
        val cy = play.diamondCy
        closeIcon.setXywh(cx - iconSize / 2, cy - iconSize / 2, iconSize, iconSize)
    }

    // ---- footprints (draw-time; write `out`, never allocate) -------------------------------------

    /**
     * Glyph scale of a quip of [len] chars (drawn in caps): one size below the largest ≤ [quipKMax]
     * that fits ([quipFits]), but never below [quipKMin] (nor above what fits). Designer: the
     * largest size felt shouty; long lines are already at the floor.
     */
    fun quipK(len: Int): Int {
        val fit = quipFitK(len)
        return maxOf(fit - 1, minOf(fit, quipKMin))
    }

    /** Largest scale ≤ [quipKMax] at which the quip and the life row fit the target block region. */
    fun quipFitK(len: Int): Int {
        var q = quipKMax
        while (q > 1 && !quipFits(len, q)) q--
        return q
    }

    private fun quipFits(len: Int, q: Int): Boolean =
        ui.widthOf(len, q) <= W - 2 * PlayLayout.EDGE_MIN && blockH(q) <= targetBottom - targetTop

    /** Height of the target block: quip caps, gap, life row. */
    private fun blockH(q: Int): Int = ui.capHeight * q + quipGap + lifeRowH

    /** Player-frame top of the target block (the quip's cap top) for quip scale [q]. */
    fun blockTop(q: Int): Int = targetTop + (targetBottom - targetTop - blockH(q)) / 2

    /** Footprint of a quip of [len] chars on [seat]'s tile, centred in the target block. Returns k. */
    fun quipFootprint(seat: Int, len: Int, out: IRect): Int {
        val q = quipK(len)
        val tw = ui.widthOf(len, q)
        play.mapRect(seat, (W - tw) / 2, blockTop(q) - ui.capTop * q, tw, ui.height(q), out)
        return q
    }

    /**
     * The target's life under a quip of [quipLen] chars: life icon then [life] in [lifeFont] at
     * [lifeK], centred as one unit, [quipGap] below the quip's caps. Returns the glyph scale.
     */
    fun lifeFootprint(seat: Int, quipLen: Int, life: Int, icon: IRect, number: IRect): Int {
        val q = quipK(quipLen)
        val y = blockTop(q) + ui.capHeight * q + quipGap
        val nw = lifeFont.measureInt(life, lifeK)
        val nh = lifeFont.height(lifeK)
        val unit = iconSize + iconGap + nw
        val x = (W - unit) / 2
        play.mapRect(seat, x, y + (lifeRowH - iconSize) / 2, iconSize, iconSize, icon)
        play.mapRect(seat, x + iconSize + iconGap, y + (lifeRowH - nh) / 2, nw, nh, number)
        return lifeK
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
     * [target] is the current target seat or [CommanderMode.NONE]: outside the mode everything is
     * [DELEGATE] (the commander button lives in the drawer bar, `DrawerLayout`). In the mode the
     * target's tile is [CommanderZones.TARGET], opponents' whole tiles are slot zones (tabs are
     * hidden; quarters for a partnered source) and the diamond is [CommanderZones.DIAMOND] (close).
     */
    fun hitTest(gx: Int, gy: Int, target: Int, players: List<Player>): Int {
        val pl = play
        if (target == CommanderMode.NONE) return DELEGATE
        if (pl.inDiamondHit(gx, gy)) return CommanderZones.DIAMOND
        for (s in 0 until P) {
            val p = pl.panels[s]
            if (!p.contains(gx, gy)) continue
            if (s == target) return CommanderZones.zone(s, CommanderZones.TARGET)
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
        return PlayLayout.NONE
    }

    /** The rect a pressed commander [zone] inverts. False for none. */
    fun feedbackRect(zone: Int, players: List<Player>, out: IRect): Boolean {
        if (!CommanderZones.isCommander(zone)) return false
        if (zone == CommanderZones.DIAMOND) { copy(closeIcon, out); return true }
        val s = CommanderZones.seatOf(zone)
        val local = CommanderZones.localOf(zone)
        when {
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
            "target block $targetTop..$targetBottom, life ${lifeFont.name} k$lifeK, quip k $quipKMin..$quipKMax)"

    companion object {
        /** "Not a commander zone": let the drawer layout hit-test it. */
        const val DELEGATE = Int.MIN_VALUE

        /** Digits a partner number slot is sized for (commander damage past 99 is not a real game). */
        const val PARTNER_CHARS = 2

        /** Design values at S = 4 (converted per S). */
        const val ICON_GAP = 3
        const val LETHAL_PAD = 3
        const val QUIP_GAP = 10
        const val QUIP_K_MAX = 4
        const val QUIP_K_MIN = 2

        /** Minimum paper between a partner number and its ± glyphs (tighter than [PlayLayout.GAP_MIN]: two numbers share a tile). */
        const val PARTNER_GAP = 2
    }
}
