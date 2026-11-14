package au.com.josephmark.dithertracker.ko

import au.com.josephmark.dithertracker.core.Action
import au.com.josephmark.dithertracker.core.Commander
import au.com.josephmark.dithertracker.core.Counter
import au.com.josephmark.dithertracker.core.GameState
import au.com.josephmark.dithertracker.core.History
import au.com.josephmark.dithertracker.core.KoReason
import au.com.josephmark.dithertracker.core.flavour.Flavour
import au.com.josephmark.dithertracker.core.flavour.QuipKind
import au.com.josephmark.dithertracker.dither.DitherGrid
import au.com.josephmark.dithertracker.dither.IRect
import au.com.josephmark.dithertracker.play.GameSession
import au.com.josephmark.dithertracker.play.PlayLayout
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.MINUS
import au.com.josephmark.dithertracker.play.PlayLayout.Companion.PLUS
import au.com.josephmark.dithertracker.play.PlayRig
import au.com.josephmark.dithertracker.play.PlayRig.Companion.mid
import au.com.josephmark.dithertracker.stage.Events
import au.com.josephmark.dithertracker.stage.Kind
import au.com.josephmark.dithertracker.stage.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The knocked-out tile (T15): layout, cover, revive, quips, effects hook, commander mode, winner. */
class KoTest {

    private val screens = listOf(1872 to 1404, 2560 to 1600, 1280 to 800, 1404 to 1053)

    private class Rig(session: GameSession = GameSession.newGame(4), s: Int = 4) {
        val rig = PlayRig(s = s, session = session)
        val ko = rig.stage.components.filterIsInstance<KoComponent>().single()
        val winner = rig.stage.components.filterIsInstance<KoWinner>().single()
        val l get() = rig.l
        val stage get() = rig.stage
        val session get() = rig.session
        val clock get() = rig.platform.clock
        private var pointer = 0

        fun hit(gx: Int, gy: Int): String {
            val id = stage.hitTest(gx, gy)
            if (id >= 0 && stage.zoneOwner(id) === ko) return "ko:${stage.zoneSeat(id)}"
            return rig.key(id)
        }

        fun hit(r: IRect) = mid(r).let { hit(it[0], it[1]) }

        fun tap(r: IRect) {
            val m = mid(r)
            val p = pointer++
            stage.down(p, m[0], m[1], clock.now)
            stage.up(p)
        }

        fun kill(seat: Int) = session.dispatch(Action.SetLife(rig.id(seat), 0), clock.now)
        fun heal(seat: Int) = session.dispatch(Action.SetLife(rig.id(seat), 40), clock.now)
        fun shown(seat: Int) = (ko.shown ushr seat) and 1 != 0
        fun armed(seat: Int) = (ko.armed ushr seat) and 1 != 0
    }

    // ---- layout -----------------------------------------------------------------------------------

    private fun inside(inner: IRect, outer: IRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun overlaps(a: IRect, b: IRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun heroLines(): List<String> =
        QuipKind.entries.filter { it.name.startsWith("KO_") || it == QuipKind.WINNER }
            .flatMap { Flavour.quippy.lines(it) + Flavour.plain.lines(it) }

    private val modes = intArrayOf(KoLayout.DEAD, KoLayout.DEAD_CAUSE, KoLayout.WINNER)

    @Test fun heroFitsEveryKoAndWinnerLineAtEveryScaleOnEverySeat() {
        val all = heroLines()
        for ((w, h) in screens) for (sc in 2..6) for (n in 2..6) {
            val l = PlayLayout.compute(DitherGrid(w, h, sc), n)
            val k = KoLayout(l)
            val peek = au.com.josephmark.dithertracker.peeks.PeekLayout(l)
            val b = KoLayout.Block()
            val g = k.gutter
            val diamond = IRect(l.diamond.left - g, l.diamond.top - g, l.diamond.right + g, l.diamond.bottom + g)
            for (s in 0 until n) {
                val p = l.panels[s]
                val cover = IRect().also { k.winnerCover(s, it) }
                val field = IRect().also { k.field(s, it) }
                val base = "$w×$h S=$sc ${n}p seat $s"
                if (peek.enabled) for (col in 0..1) {
                    val c = IRect().also { peek.corner(s, col, it) }
                    assertFalse("$base winner cover over peek corner $col", overlaps(cover, c))
                }
                val icon = IRect(); val num = IRect(); val chip = IRect()
                val causeArea = IRect().also { k.heroArea(s, KoLayout.DEAD_CAUSE, it) }
                // Cramped layouts (no detail band, hero over the emblem's row) only check the quip fits.
                if (l.comfortable) {
                    for (v in intArrayOf(10, 21, 99, 999)) {
                        assertTrue("$base detail $v fits", k.detail(s, v, icon, num, chip))
                        assertTrue("$base chip in panel", inside(chip, p))
                        assertTrue("$base chip holds icon + number", inside(icon, chip) && inside(num, chip))
                        assertFalse("$base chip/hero", overlaps(chip, causeArea))
                        assertFalse("$base chip/tab", overlaps(chip, l.tabHit[s]))
                        if (peek.enabled) for (col in 0..1) assertFalse("$base chip/peek $col", overlaps(chip, IRect().also { peek.corner(s, col, it) }))
                    }
                }
                val corners = if (peek.enabled) (0..1).map { col -> IRect().also { peek.corner(s, col, it) } } else emptyList()
                for (mode in modes) {
                    val area = IRect().also { k.heroArea(s, mode, it) }
                    assertTrue("$base m$mode hero area in panel", inside(area, p))
                    if (mode == KoLayout.WINNER) assertTrue("$base hero area in the winner cover", inside(area, cover))
                    else assertTrue("$base m$mode hero area in the field", inside(area, field))
                    for (q in all) {
                        val tag = "$base m$mode '$q'"
                        k.arrange(s, q.uppercase(), b, mode)
                        assertEquals(mode, b.mode)
                        val emblem = b.emblem
                        val emblemHalo = IRect(emblem.left - k.halo, emblem.top - k.halo, emblem.right + k.halo, emblem.bottom + k.halo)
                        assertTrue("$tag emblem in panel", inside(emblemHalo, p))
                        if (mode != KoLayout.WINNER) assertTrue("$tag emblem in field", inside(emblemHalo, field))
                        else assertTrue("$tag emblem in cover", inside(emblem, cover))
                        assertFalse("$tag emblem/diamond", overlaps(emblemHalo, diamond))
                        assertTrue("$tag emblem at or below its top", b.ey >= k.emblemTop)
                        assertTrue("$tag lines ${b.lines}", b.lines in 1..KoLayout.MAX_LINES)
                        // One size for every line wherever the hero has room (the Nomad and larger at any S).
                        if (roomy(w, h) && n <= 4) assertEquals("$tag one size", k.quipK, b.k) else assertTrue("$tag k ${b.k}", b.k in 1..k.quipK)
                        // Every line whole (more than four seats: on layouts Settings offers; a tiny 6-way split may truncate).
                        if (n <= 4 || l.comfortable) assertEquals("$tag whole line shown", q.trim().length, (0 until b.lines).sumOf { b.end[it] - b.start[it] } + b.lines - 1)
                        assertTrue("$tag hero touches in area", inside(b.area, area))
                        if (l.comfortable) assertFalse("$tag hero/emblem", overlaps(b.area, emblemHalo))
                        for (i in 0 until b.lines) {
                            if (i > 0 && (n <= 4 || l.comfortable)) assertTrue("$tag breaks at a space", q[b.start[i] - 1] == ' ')
                            val r = b.line[i]
                            assertTrue("$tag line $i $r in hero area $area", inside(r, area))
                            if (i > 0) assertFalse("$tag lines overlap", overlaps(r, b.line[i - 1]))
                            assertGround(k, q.uppercase(), b, i, tag, s)
                            val gr = b.ground[i]
                            assertTrue("$tag ground $i in hero area", inside(gr, area))
                            assertTrue("$tag ground $i in bounds", inside(gr, b.bounds))
                            if (!l.comfortable) continue
                            assertFalse("$tag ground/emblem", overlaps(gr, emblemHalo))
                            assertFalse("$tag ground/diamond ring", overlaps(gr, diamond))
                            if (mode != KoLayout.DEAD) {
                                assertFalse("$tag ground/tab", overlaps(gr, l.tabHit[s]))
                                for (c in corners) assertFalse("$tag ground/peek corner", overlaps(gr, c))
                            }
                            if (mode == KoLayout.DEAD_CAUSE) for (v in intArrayOf(10, 21, 999)) {
                                k.detail(s, v, icon, num, chip)
                                assertFalse("$tag ground/chip", overlaps(gr, chip))
                            }
                        }
                        assertMerged(b, tag)
                        if (roomy(w, h) && n <= 4) assertEquals("$tag revive plate at the quip size", k.quipK, b.reviveK)
                        for (r in listOf(b.plate, b.keyline, b.shadow)) {
                            assertTrue("$tag revive $r in hero area", inside(r, area))
                            if (l.comfortable) assertFalse("$tag revive/emblem", overlaps(r, emblemHalo))
                        }
                        val rt = b.reviveText
                        // The text box's cap band (it is the face's full height; the descender rows face away on a turned seat) sits on the plate.
                        assertTrue("$tag revive text", rt.left >= b.plate.left && rt.right <= b.plate.right &&
                            minOf(rt.bottom, b.plate.bottom) - maxOf(rt.top, b.plate.top) >= k.ui.capHeight * b.reviveK)
                    }
                }
            }
        }
    }

    /** Screens at least the Nomad's size: every shipped line fits at the one quip size there. */
    private fun roomy(w: Int, h: Int) = w >= 1872 && h >= 1404

    /**
     * Line [i]'s ground (player frame) runs from its first glyph to its last grown by the halo, and
     * from the cap band's top − halo to at least its baseline (descenders when it has any) + halo.
     */
    private fun assertGround(k: KoLayout, q: String, b: KoLayout.Block, i: Int, tag: String, seatOf: Int = 0) {
        val ui = k.ui
        val kk = b.k
        val tw = ui.widthOf(b.end[i] - b.start[i], kk)
        assertEquals("$tag ground $i left", b.lx[i] - k.halo, b.gx[i])
        assertEquals("$tag ground $i width", tw + 2 * k.halo, b.gw[i])
        assertEquals("$tag ground $i top", b.ly[i] + ui.capTop * kk - k.halo, b.gy[i])
        var desc = false
        for (j in b.start[i] until b.end[i]) for (y in ui.baseline until ui.glyphHeight) for (x in 0 until ui.glyphWidth) if (ui.glyph(q[j]).isInk(x, y)) desc = true
        val need = b.ly[i] + (if (desc) ui.glyphHeight else ui.baseline) * kk + k.halo
        assertTrue("$tag ground $i bottom", b.gy[i] + b.gh[i] >= need)
        // The screen rect is the player-frame box turned (same size on every seat; swapped on a sideways one).
        val side = k.play.rotation[seatOf] % 180 != 0
        assertEquals("$tag ground $i w", if (side) b.gh[i] else b.gw[i], b.ground[i].right - b.ground[i].left)
        assertEquals("$tag ground $i h", if (side) b.gw[i] else b.gh[i], b.ground[i].bottom - b.ground[i].top)
    }

    /**
     * The grounds join into one stepped shape: each reaches the next line's ground (no dither row
     * between lines) and overlaps it horizontally, so every row of the block is one paper run; the
     * bounding rect (the press feedback) is exactly their union's bounds.
     */
    private fun assertMerged(b: KoLayout.Block, tag: String) {
        var u = Int.MAX_VALUE; var v = Int.MAX_VALUE; var r = Int.MIN_VALUE; var d = Int.MIN_VALUE
        for (i in 0 until b.lines) {
            val g = b.ground[i]
            u = minOf(u, g.left); v = minOf(v, g.top); r = maxOf(r, g.right); d = maxOf(d, g.bottom)
            if (i == 0) continue
            assertTrue("$tag grounds $i touch", b.gy[i - 1] + b.gh[i - 1] >= b.gy[i])
            assertTrue("$tag grounds $i overlap across", b.gx[i - 1] < b.gx[i] + b.gw[i] && b.gx[i] < b.gx[i - 1] + b.gw[i - 1])
        }
        assertEquals("$tag bounds = grounds", IRect(u, v, r, d), b.bounds)
    }

    @Test fun oneQuipSizeAtEachScale() {
        // ui at k = 2 on the Nomad at S = 4 (the size the long lines had in T42), the same physical size elsewhere.
        val want = mapOf(2 to 4, 3 to 3, 4 to 2, 5 to 2, 6 to 1)
        for ((w, h) in screens) for ((sc, kk) in want) {
            val k = KoLayout(PlayLayout.compute(DitherGrid(w, h, sc)))
            assertEquals("$w×$h S=$sc", kk, k.quipK)
            assertTrue(k.warmK >= k.quipK)
        }
    }

    @Test fun heroAtS4OneSizeBalancedLinesStepGround() {
        val k = KoLayout(PlayLayout.compute(DitherGrid(1872, 1404, 4)))
        val b = KoLayout.Block()
        fun lines(q: String): List<String> {
            k.arrange(3, q, b, KoLayout.DEAD_CAUSE)
            assertEquals(q, 2, b.k)
            return (0 until b.lines).map { q.substring(b.start[it], b.end[it]) }
        }
        // Short lines are not scaled up; they are centred.
        assertEquals(listOf("OOF"), lines("OOF"))
        assertEquals((k.play.panelW - k.ui.widthOf(3, 2)) / 2, b.lx[0])
        assertEquals(listOf("GAME OVER"), lines("GAME OVER"))
        assertEquals(listOf("YOUR QUEST", "ENDS HERE"), lines("YOUR QUEST ENDS HERE"))
        // Balanced: the narrowest widest line for the fewest lines, not greedy.
        assertEquals(listOf("SOMEBODY SET UP", "US THE BOMB."), lines("SOMEBODY SET UP US THE BOMB."))
        assertEquals(listOf("TRUSTED A", "HANDSHAKE DEAL"), lines("TRUSTED A HANDSHAKE DEAL"))
        assertEquals(listOf("YOU HAVE COMPLETED", "A GREAT GAME."), lines("YOU HAVE COMPLETED A GREAT GAME."))
        // Two lines: one stepped paper shape 2 px proud of the glyphs, the lines 18 px apart.
        lines("STAY A WHILE... STAY FOREVER!")
        assertEquals(2, b.lines)
        assertEquals(18, b.ly[1] - b.ly[0])
        assertEquals(b.gy[0] + b.gh[0], b.gy[1])
        assertEquals(listOf(25, 62, 182, 18), listOf(b.gx[0], b.gy[0], b.gw[0], b.gh[0]))
        assertEquals(listOf(37, 80, 158, 18), listOf(b.gx[1], b.gy[1], b.gw[1], b.gh[1]))
        assertEquals(2, b.reviveK) // the armed plate matches (T47; it was k = 4)
        // T51: emblem and quip are centred together in each mode's room (margins within a px).
        for (mode in modes) {
            k.arrange(3, "STAY A WHILE... STAY FOREVER!", b, mode)
            val capBottom = b.ly[b.lines - 1] + (k.ui.capTop + k.ui.capHeight) * b.k
            val above = b.ey - k.emblemTop
            val below = k.textBottomOf(mode) - capBottom
            assertTrue("mode $mode: $above above, $below below", Math.abs(above - below) <= 1)
            assertEquals(b.ly[0] - b.ey - k.iconSize, k.emblemGap)
        }
        k.arrange(3, "OOF", b, KoLayout.DEAD)
        assertTrue("a short line sits lower without the chip", b.ey > k.emblemTop)
        // Layout numbers at S = 4.
        assertEquals(2, k.quipK)
        assertEquals(2, k.iconK)
        assertEquals(1, k.causeK)
        assertEquals(2, k.halo)
        assertEquals(217, k.textW)
        assertEquals(42, k.textTop)
        assertEquals(115, k.textBottom)
        assertEquals(64, k.detailLeft) // between the peek corners (T38): x 64..169 at S = 4
        assertEquals(169, k.detailRight)
    }

    @Test fun everyShippedLineTakesAtMostTwoLinesOnTheNomadAtS4() {
        val k = KoLayout(PlayLayout.compute(DitherGrid(1872, 1404, 4)))
        val b = KoLayout.Block()
        for (q in heroLines()) for (mode in modes) {
            k.arrange(0, q.uppercase(), b, mode)
            assertEquals(q, 2, b.k)
            assertTrue("$q: ${b.lines} lines", b.lines <= 2)
        }
    }

    @Test fun gutterKeepsTheFieldOffTheDividersAndTheDiamond() {
        for ((w, h) in screens) for (sc in 2..6) {
            val l = PlayLayout.compute(DitherGrid(w, h, sc))
            val k = KoLayout(l)
            val g = k.gutter
            assertTrue(g >= 1)
            for (s in 0 until 4) {
                val p = l.panels[s]
                for (y in p.top until p.bottom) for (x in p.left until p.right) {
                    val ink = k.fieldInk(s, x, y)
                    // Paper next to every divider and panel edge.
                    if (x < p.left + g || x >= p.right - g || y < p.top + g || y >= p.bottom - g) assertFalse("$w×$h S=$sc seat $s ($x,$y) edge", ink)
                    // Paper within the gutter of the diamond (Chebyshev distance to any diamond pixel).
                    var near = false
                    for (dy in -g..g) {
                        val hw = l.diamondRowHalf(y + dy - l.diamondCy)
                        if (hw >= 0 && x >= l.diamondCx - hw - g && x <= l.diamondCx + hw + g) near = true
                    }
                    if (near) assertFalse("$w×$h S=$sc seat $s ($x,$y) by the diamond", ink)
                }
                val m = mid(p)
                assertTrue(k.fieldInk(s, m[0], m[1]))
            }
        }
    }

    @Test fun haloFillsTheTombstoneAndGrowsIt() {
        // The emblem's cut-out is the shared runtime halo (T50: was ko's own `Halo`, pixel-identical).
        val icon = au.com.josephmark.dithertracker.dither.Icon.DEAD
        val h = au.com.josephmark.dithertracker.dither.HaloMask.of(icon, 2)
        assertEquals(20, h.width); assertEquals(20, h.height)
        for (y in 0 until 16) for (x in 0 until 16) if (icon.isInk(x, y)) assertTrue(h.isInk(x + 2, y + 2))
        assertTrue(h.isInk(4 + 2, 9 + 2)) // enclosed paper inside the outline
        assertFalse(h.isInk(0, 0)) // far corner stays outside
        // At the emblem's own scale it is the build-time icon halo scaled up (k px rim at k×).
        val h1 = au.com.josephmark.dithertracker.dither.HaloMask.of(icon, 1)
        for (y in 0 until 18) for (x in 0 until 18) assertEquals("($x,$y)", icon.halo.isInk(x, y), h1.isInk(x, y))
    }

    // ---- KO flip, cover, zones ---------------------------------------------------------------------

    @Test fun koFlipInvalidatesOnlyThatTile() {
        val r = Rig()
        r.rig.platform.invalidated.clear()
        r.kill(3)
        assertTrue(r.shown(3))
        val panel = IRect().also { r.l.grid.toDevice(r.l.panels[3], it) }
        assertTrue(r.rig.platform.invalidated.isNotEmpty())
        for (d in r.rig.platform.invalidated) assertTrue("$d in $panel", inside(d, panel))
        // Staying dead with another life change redraws nothing: no life total on the tile (T51).
        r.rig.platform.invalidated.clear()
        r.session.dispatch(Action.ChangeLife(r.rig.id(3), -2), 0)
        for (d in r.rig.platform.invalidated) assertFalse("$d", overlaps(d, panel))
        // A poison KO's count changing redraws only the cause chip's band.
        r.session.dispatch(Action.SetCounter(r.rig.id(2), Counter.POISON, 10), 0)
        assertEquals(KoLayout.DEAD_CAUSE, r.ko.block(2).mode)
        r.rig.platform.invalidated.clear()
        r.session.dispatch(Action.SetCounter(r.rig.id(2), Counter.POISON, 12), 0)
        val ring = IRect().also { r.l.grid.toDevice(IRect().also { b -> r.ko.layout!!.detailBand(2, b) }, it) }
        assertTrue(r.rig.platform.invalidated.isNotEmpty())
        for (d in r.rig.platform.invalidated) assertTrue("$d in $ring", inside(d, ring))
        // Others' changes leave the dead tile alone.
        r.rig.platform.invalidated.clear()
        r.session.dispatch(Action.ChangeLife(r.rig.id(1), -2), 0)
        for (d in r.rig.platform.invalidated) assertFalse(overlaps(d, panel))
    }

    @Test fun deadTileWinsLifeTabAndHidesContent() {
        val r = Rig()
        r.kill(2)
        val l = r.l
        assertEquals("ko:2", r.hit(l.zones[PlayLayout.zone(2, MINUS)]))
        assertEquals("ko:2", r.hit(l.zones[PlayLayout.zone(2, PLUS)]))
        assertEquals("ko:2", r.hit(l.tab[2].left + 2, l.tab[2].top + 2))
        assertEquals("ko:2", r.hit(l.numberSlot[2]))
        // Others untouched, the diamond still blocks.
        assertEquals("life:3:$MINUS", r.hit(l.zones[PlayLayout.zone(3, MINUS)]))
        assertEquals("tab:1", r.hit(l.tab[1].left + 2, l.tab[1].top + 2))
        assertFalse(r.hit(l.diamondCx, l.diamondCy).startsWith("ko")) // the diamond stays the menu's
        // Life, readout, tab hidden on the dead seat only; the KO component stays visible.
        assertFalse(r.rig.life.visible(2))
        assertFalse(r.rig.readout.visible(2))
        assertFalse(r.rig.tab.visible(2))
        assertTrue(r.rig.life.visible(3))
        assertTrue(r.ko.visible(2))
        assertEquals(Kind.STATUS, r.ko.kind)
        assertEquals(Layer.TILE, r.ko.layer)
        // Press feedback is the quip's words; once armed, the revive plate.
        assertEquals(r.ko.block(2).bounds, r.rig.feedback(r.ko, 2, KoComponent.ZONE))
        r.tap(l.panels[2])
        assertEquals(r.ko.block(2).plate, r.rig.feedback(r.ko, 2, KoComponent.ZONE))
    }

    @Test fun poisonAndCommanderDamageKoWithTheirOwnQuips() {
        val r = Rig()
        r.session.dispatch(Action.SetCounter(r.rig.id(1), Counter.POISON, 10), 0)
        assertTrue(r.shown(1))
        assertTrue(r.ko.quip(1) in Flavour.quippy.lines(QuipKind.KO_POISON).map { it.uppercase() })
        r.session.dispatch(Action.ChangeCommanderDamage(r.rig.id(0), r.rig.id(2), Commander.PRIMARY, 21, linkLife = false), 0)
        assertTrue(r.shown(0))
        assertTrue(r.ko.quip(0) in Flavour.quippy.lines(QuipKind.KO_COMMANDER_DAMAGE).map { it.uppercase() })
        // The cause keeps its chip (T51); a life or marked-dead KO has none.
        assertEquals(KoLayout.DEAD_CAUSE, r.ko.block(1).mode)
        assertEquals(KoLayout.DEAD_CAUSE, r.ko.block(0).mode)
        r.kill(2)
        assertEquals(KoLayout.DEAD, r.ko.block(2).mode)
        r.session.dispatch(Action.MarkDead(r.rig.id(3)), 0)
        assertEquals(KoLayout.DEAD, r.ko.block(3).mode)
    }

    /** [seat]'s panel as drawn by the KO and winner components alone, with every life moved by [lifeOffset]. */
    private fun koTile(rig: au.com.josephmark.dithertracker.purity.ScreenRig, seat: Int, lifeOffset: Int): ByteArray {
        val f = rig.frame
        f.players = rig.session.state.players.map { it.copy(life = it.life + lifeOffset) }
        rig.r.inverted = false
        rig.r.clear()
        rig.one<KoComponent>().draw(f, seat)
        rig.one<KoWinner>().draw(f, seat)
        val p = rig.l.panels[seat]
        val out = ByteArray(p.width * p.height)
        for (y in 0 until p.height) for (x in 0 until p.width) out[y * p.width + x] = rig.r.px[(p.top + y) * rig.r.width + p.left + x]
        return out
    }

    @Test fun noLifeTotalOnDeadOrWinnerTiles() {
        // T51 (Sam): the KO and winner tiles draw no life total — their pixels do not depend on it.
        val rig = au.com.josephmark.dithertracker.purity.ScreenRig(1872, 1404, 4)
        val winner = rig.one<KoWinner>()
        rig.dispatch(Action.SetLife(rig.id(0), 0))
        rig.dispatch(Action.MarkDead(rig.id(1)))
        rig.dispatch(Action.SetLife(rig.id(2), -7))
        rig.advance(10_000)
        assertTrue(winner.showing)
        assertEquals(3, winner.winner)
        for (seat in 0 until 4) {
            val a = koTile(rig, seat, 0)
            assertTrue("seat $seat draws", a.any { it.toInt() != 0 })
            for (d in intArrayOf(-13, 25, 999)) assertTrue("seat $seat life +$d", a.contentEquals(koTile(rig, seat, d)))
        }
        // A poison KO still shows its count: its tile changes with it.
        val rig2 = au.com.josephmark.dithertracker.purity.ScreenRig(1872, 1404, 4)
        rig2.dispatch(Action.SetCounter(rig2.id(2), Counter.POISON, 10))
        rig2.advance(10_000)
        assertEquals(KoLayout.DEAD_CAUSE, rig2.one<KoComponent>().block(2).mode)
        val ten = koTile(rig2, 2, 0)
        assertTrue(ten.contentEquals(koTile(rig2, 2, 30)))
        rig2.dispatch(Action.SetCounter(rig2.id(2), Counter.POISON, 13))
        assertFalse(ten.contentEquals(koTile(rig2, 2, 0)))
    }

    // ---- revive -------------------------------------------------------------------------------------

    @Test fun firstTapArmsSecondTapRevives() {
        val r = Rig()
        r.kill(3)
        val panel = r.l.panels[3]
        r.rig.platform.invalidated.clear()
        r.tap(panel)
        assertTrue(r.armed(3))
        // Arming redraws the hero area only (the plate replaces the quip there).
        val area = IRect().also { r.l.grid.toDevice(r.ko.block(3).area, it) }
        assertTrue(r.rig.platform.invalidated.isNotEmpty())
        for (d in r.rig.platform.invalidated) assertTrue("$d in $area", inside(d, area))
        assertTrue(r.session.state.isKo(r.session.state.players[3]))
        r.tap(panel)
        assertFalse(r.armed(3))
        val p = r.session.state.players[3]
        assertFalse(r.session.state.isKo(p))
        assertEquals(0, p.life) // revive keeps the number; the override shows the player alive
        assertFalse(r.shown(3))
        assertEquals("life:3:$MINUS", r.hit(r.l.zones[PlayLayout.zone(3, MINUS)]))
    }

    @Test fun armTimesOut() {
        val r = Rig()
        r.kill(0)
        r.tap(r.l.panels[0])
        assertTrue(r.armed(0))
        r.clock.advance(KoComponent.ARM_MS - 1)
        assertTrue(r.armed(0))
        r.clock.advance(1)
        assertFalse(r.armed(0))
        // The next tap only arms again.
        r.tap(r.l.panels[0])
        assertTrue(r.armed(0))
        assertTrue(r.session.state.isKo(r.session.state.players[0]))
    }

    @Test fun armIsPerSeat() {
        val r = Rig()
        r.kill(0); r.kill(2)
        r.tap(r.l.panels[0])
        r.tap(r.l.panels[2])
        assertTrue(r.armed(0)); assertTrue(r.armed(2))
        r.tap(r.l.panels[2])
        assertTrue(r.shown(0)); assertFalse(r.shown(2))
    }

    // ---- quips ---------------------------------------------------------------------------------------

    @Test fun quipPickedOnceNotRerolledAndNotRepeated() {
        val r = Rig()
        val seen = ArrayList<String>()
        repeat(12) {
            r.kill(3)
            val q = r.ko.quip(3)
            assertTrue(q.isNotEmpty())
            // Redraws, other seats' changes and the dead player's own changes keep it.
            r.session.dispatch(Action.ChangeLife(r.rig.id(1), -1), 0)
            r.session.dispatch(Action.ChangeLife(r.rig.id(3), -1), 0)
            r.tap(r.l.panels[3]) // arm
            r.clock.advance(KoComponent.ARM_MS)
            assertEquals(q, r.ko.quip(3))
            if (seen.isNotEmpty()) assertNotEquals("no repeat of the previous pick", seen.last(), q)
            seen += q
            r.heal(3)
            assertEquals("", r.ko.quip(3))
        }
        assertTrue(seen.toSet().size > 3)
    }

    @Test fun causeChangeRepicks() {
        val r = Rig()
        r.session.dispatch(Action.SetCounter(r.rig.id(3), Counter.POISON, 10), 0)
        assertTrue(r.ko.quip(3) in Flavour.quippy.lines(QuipKind.KO_POISON).map { it.uppercase() })
        r.kill(3) // life wins the reason
        assertTrue(r.ko.quip(3) in Flavour.quippy.lines(QuipKind.KO_LIFE).map { it.uppercase() })
    }

    @Test fun restoredGameShowsDeadAtLayout() {
        val st = GameState.create(4)
        val dead = st.copy(players = st.players.mapIndexed { i, p -> if (i == 1) p.copy(life = -3) else p })
        val r = Rig(GameSession(History(dead)))
        assertTrue(r.shown(1))
        assertEquals("ko:1", r.hit(r.l.panels[1]))
        assertTrue(r.ko.quip(1).isNotEmpty())
    }

    // ---- drawer -------------------------------------------------------------------------------------

    @Test fun openDrawerClosesWhenItsPlayerDies() {
        val r = Rig()
        r.tap(r.l.tab[2])
        assertTrue(r.rig.drawer.state.isOpen(2))
        r.tap(r.l.tab[1])
        assertTrue(r.rig.drawer.state.isOpen(1))
        r.kill(2)
        assertFalse(r.rig.drawer.state.isOpen(2))
        assertTrue(r.rig.drawer.state.isOpen(1))
    }

    // ---- effects hook ----------------------------------------------------------------------------------

    @Test fun holdDefersTheDeadTileUntilReleased() {
        val r = Rig()
        r.stage.send(Events.KO_HOLD, 3)
        r.kill(3)
        assertFalse(r.shown(3))
        assertEquals("life:3:$MINUS", r.hit(r.l.zones[PlayLayout.zone(3, MINUS)]))
        r.rig.platform.invalidated.clear()
        r.stage.send(Events.KO_RELEASE, 3)
        assertTrue(r.shown(3))
        assertTrue(r.rig.platform.invalidated.isNotEmpty())
    }

    @Test fun aHoldRepeatStopsAtTheKoEvenWhileAnEffectHoldsTheTile() {
        val r = Rig()
        r.session.dispatch(Action.SetLife(r.rig.id(3), 10), 0)
        r.stage.send(Events.KO_HOLD, 3)
        val m = mid(r.l.zones[PlayLayout.zone(3, MINUS)])
        r.stage.down(9, m[0], m[1], r.clock.now)
        r.clock.advance(500) // first repeat: 10 -> 0, KO
        assertEquals(0, r.session.state.players[3].life)
        r.clock.advance(3000)
        assertEquals(0, r.session.state.players[3].life)
        r.stage.up(9)
    }

    @Test fun holdSentAfterTheFlipTakesTheTileDownAndTimesOut() {
        val r = Rig()
        r.kill(3)
        assertTrue(r.shown(3))
        r.stage.send(Events.KO_HOLD, 3)
        assertFalse(r.shown(3))
        r.clock.advance(KoComponent.HOLD_MAX_MS)
        assertTrue(r.shown(3))
    }

    @Test fun touchOnAHeldSeatReleases() {
        val r = Rig()
        r.stage.send(Events.KO_HOLD, 0)
        r.kill(0)
        r.tap(r.l.zones[PlayLayout.zone(0, PLUS)])
        assertTrue(r.shown(0))
        assertEquals(0, r.session.state.players[0].life) // the tap was cancelled, not applied
    }

    // ---- commander mode ----------------------------------------------------------------------------

    @Test fun targetDyingInCommanderModeStaysDeadAndModeContinues() {
        val r = Rig()
        r.stage.send(Events.COMMANDER_TARGET, 3)
        assertTrue(r.rig.cmd.mode.active)
        val zone0 = r.l.zones[PlayLayout.zone(0, PLUS)]
        assertTrue(r.hit(zone0).startsWith("cmd:0:"))
        // Seat 0 deals 21 commander damage to seat 3 (hold steps would do the same).
        r.session.dispatch(Action.ChangeCommanderDamage(r.rig.id(3), r.rig.id(0), Commander.PRIMARY, 21, linkLife = null), 0)
        assertTrue(r.shown(3))
        assertTrue(r.rig.cmd.mode.active)
        assertEquals("ko:3", r.hit(r.l.panels[3]))
        assertFalse(r.rig.cmd.visible(3))
        // Seat 0 can still take a point back, which brings the target back to life.
        r.tap(r.l.zones[PlayLayout.zone(0, MINUS)])
        assertFalse(r.shown(3))
        assertTrue(r.hit(r.l.panels[3]).startsWith("cmd:3:"))
    }

    @Test fun deadOpponentStaysDeadInCommanderMode() {
        val r = Rig()
        r.kill(1)
        r.stage.send(Events.COMMANDER_TARGET, 3)
        assertTrue(r.rig.cmd.mode.active)
        assertTrue(r.shown(1))
        assertTrue(r.ko.visible(1))
        assertFalse(r.rig.cmd.visible(1))
        assertEquals("ko:1", r.hit(r.l.zones[PlayLayout.zone(1, PLUS)]))
        assertTrue(r.hit(r.l.zones[PlayLayout.zone(0, PLUS)]).startsWith("cmd:0:"))
        r.rig.cmd.mode.exit()
        assertTrue(r.shown(1))
        assertEquals("ko:1", r.hit(r.l.panels[1]))
    }

    // ---- winner -------------------------------------------------------------------------------------

    @Test fun winnerCaptionAppearsAndDisappears() {
        val r = Rig()
        r.kill(0); r.kill(1)
        assertEquals(-1, r.winner.winner)
        r.rig.platform.invalidated.clear()
        r.kill(2)
        assertEquals(3, r.winner.winner)
        assertTrue(r.winner.showing)
        assertEquals(r.winner.caption, r.winner.caption.uppercase())
        assertTrue(r.winner.caption in Flavour.quippy.lines(QuipKind.WINNER).map { it.uppercase() })
        val panel = IRect().also { r.l.grid.toDevice(r.l.panels[3], it) }
        assertTrue(r.rig.platform.invalidated.any { overlaps(it, panel) })
        // Revive one: no winner any more.
        r.tap(r.l.panels[2]); r.tap(r.l.panels[2])
        assertEquals(-1, r.winner.winner)
        assertEquals("", r.winner.caption)
        assertFalse(r.winner.showing)
        // Everyone dead: no winner either (seat 2 was revived at 0, so it has to be marked out).
        r.session.dispatch(Action.MarkDead(r.rig.id(2)), 0)
        r.kill(3)
        assertEquals(-1, r.winner.winner)
        assertEquals(KoReason.LIFE, r.session.state.koReason(r.session.state.players[3]))
    }

    @Test fun winnerTileStaysLiveAndALifeChangeDismissesTheCaptionUntilTheNextWin() {
        val r = Rig()
        r.kill(0); r.kill(1); r.kill(2)
        assertTrue(r.winner.showing)
        // The ± halves still belong to the life component underneath.
        assertEquals("life:3:$PLUS", r.hit(r.l.zones[PlayLayout.zone(3, PLUS)]))
        assertTrue(r.rig.life.visible(3))
        r.rig.platform.invalidated.clear()
        r.tap(r.l.zones[PlayLayout.zone(3, PLUS)])
        assertEquals(41, r.session.state.players[3].life)
        assertEquals(3, r.winner.winner)
        assertTrue(r.winner.dismissed)
        assertFalse(r.winner.showing)
        val panel = IRect().also { r.l.grid.toDevice(r.l.panels[3], it) }
        assertTrue(r.rig.platform.invalidated.any { overlaps(it, panel) })
        // Further changes keep it down; others' changes too.
        r.tap(r.l.zones[PlayLayout.zone(3, MINUS)])
        assertFalse(r.winner.showing)
        // Next win: someone revives, dies again -> a fresh caption.
        r.tap(r.l.panels[2]); r.tap(r.l.panels[2])
        assertEquals(-1, r.winner.winner)
        r.session.dispatch(Action.MarkDead(r.rig.id(2)), 0)
        assertEquals(3, r.winner.winner)
        assertTrue(r.winner.showing)
    }

    @Test fun winnerBlockFitsAndPeeksStayVisible() {
        val r = Rig()
        r.session.dispatch(Action.SetCounter(r.rig.id(3), Counter.POISON, 3), 0)
        r.kill(0); r.kill(1); r.kill(2)
        assertTrue(r.winner.showing)
        assertTrue(r.rig.stage.components.filterIsInstance<au.com.josephmark.dithertracker.peeks.PeeksComponent>().single().visible(3))
        val b = r.winner.block()
        assertTrue(b.lines >= 1)
        val area = IRect().also { r.ko.layout!!.heroArea(3, KoLayout.WINNER, it) }
        assertEquals(KoLayout.WINNER, b.mode)
        for (i in 0 until b.lines) assertTrue(inside(b.line[i], area))
    }

    @Test fun winnerIsDrawnBeforeEffectsSoTheFlourishPlaysOverIt() {
        val r = Rig()
        val cs = r.stage.components
        val fx = cs.indexOfFirst { it is au.com.josephmark.dithertracker.fx.FxComponent }
        assertTrue(cs.indexOf(r.winner) < fx)
    }

    @Test fun winnerHiddenInCommanderMode() {
        val r = Rig()
        r.kill(0); r.kill(1); r.kill(2)
        assertTrue(r.winner.visible(3))
        r.stage.send(Events.COMMANDER_TARGET, 3)
        assertFalse(r.winner.visible(3))
    }
}
